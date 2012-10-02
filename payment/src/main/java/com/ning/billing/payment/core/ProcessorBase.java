/*
 * Copyright 2010-2011 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.billing.payment.core;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.dao.PaymentDao;
import com.ning.billing.payment.dao.PaymentMethodModelDao;
import com.ning.billing.payment.plugin.api.PaymentPluginApi;
import com.ning.billing.payment.provider.PaymentProviderPluginRegistry;
import com.ning.billing.util.api.TagApiException;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.bus.Bus.EventBusException;
import com.ning.billing.util.bus.BusEvent;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.dao.ObjectType;
import com.ning.billing.util.globallocker.GlobalLock;
import com.ning.billing.util.globallocker.GlobalLocker;
import com.ning.billing.util.globallocker.GlobalLocker.LockerType;
import com.ning.billing.util.globallocker.LockFailedException;
import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.Tag;

public abstract class ProcessorBase {

    private static final int NB_LOCK_TRY = 5;

    protected final PaymentProviderPluginRegistry pluginRegistry;
    protected final AccountUserApi accountUserApi;
    protected final Bus eventBus;
    protected final GlobalLocker locker;
    protected final ExecutorService executor;
    protected final PaymentDao paymentDao;
    protected final TagUserApi tagUserApi;

    private static final Logger log = LoggerFactory.getLogger(ProcessorBase.class);

    public ProcessorBase(final PaymentProviderPluginRegistry pluginRegistry,
                         final AccountUserApi accountUserApi,
                         final Bus eventBus,
                         final PaymentDao paymentDao,
                         final TagUserApi tagUserApi,
                         final GlobalLocker locker,
                         final ExecutorService executor) {
        this.pluginRegistry = pluginRegistry;
        this.accountUserApi = accountUserApi;
        this.eventBus = eventBus;
        this.paymentDao = paymentDao;
        this.locker = locker;
        this.executor = executor;
        this.tagUserApi = tagUserApi;
    }

    protected boolean isAccountAutoPayOff(final UUID accountId, final InternalTenantContext context) {
        final Map<String, Tag> accountTags = tagUserApi.getTags(accountId, ObjectType.ACCOUNT, context.toTenantContext());
        for (final Tag cur : accountTags.values()) {
            if (ControlTagType.AUTO_PAY_OFF.getId().equals(cur.getTagDefinitionId())) {
                return true;
            }
        }
        return false;
    }

    protected void setAccountAutoPayOff(final UUID accountId, final InternalCallContext context) throws PaymentApiException {
        try {
            tagUserApi.addTag(accountId, ObjectType.ACCOUNT, ControlTagType.AUTO_PAY_OFF.getId(), context.toCallContext());
        } catch (TagApiException e) {
            log.error("Failed to add AUTO_PAY_OFF on account " + accountId, e);
            throw new PaymentApiException(ErrorCode.PAYMENT_INTERNAL_ERROR, "Failed to add AUTO_PAY_OFF on account " + accountId);
        }
    }

    protected PaymentPluginApi getPaymentProviderPlugin(final UUID paymentMethodId, final InternalTenantContext context) throws PaymentApiException {
        final PaymentMethodModelDao methodDao = paymentDao.getPaymentMethodIncludedDeleted(paymentMethodId, context);
        if (methodDao == null) {
            log.error("PaymentMethod dpes not exist", paymentMethodId);
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT_METHOD, paymentMethodId);
        }
        return pluginRegistry.getPlugin(methodDao.getPluginName());
    }

    protected PaymentPluginApi getPaymentProviderPlugin(final String accountKey, final InternalTenantContext context)
            throws AccountApiException, PaymentApiException {

        final String paymentProviderName = null;
        if (accountKey != null) {
            final Account account = accountUserApi.getAccountByKey(accountKey, context.toTenantContext());
            return getPaymentProviderPlugin(account, context);
        }
        return pluginRegistry.getPlugin(paymentProviderName);
    }

    protected PaymentPluginApi getPaymentProviderPlugin(final Account account, final InternalTenantContext context) throws PaymentApiException {
        final UUID paymentMethodId = account.getPaymentMethodId();
        if (paymentMethodId == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_DEFAULT_PAYMENT_METHOD, account.getId());
        }
        return getPaymentProviderPlugin(paymentMethodId, context);
    }

    protected void postPaymentEvent(final BusEvent ev, final UUID accountId) {
        if (ev == null) {
            return;
        }
        try {
            eventBus.post(ev);
        } catch (EventBusException e) {
            log.error("Failed to post Payment event event for account {} ", accountId, e);
        }
    }

    public interface WithAccountLockCallback<T> {

        public T doOperation() throws PaymentApiException;
    }

    public static class CallableWithAccountLock<T> implements Callable<T> {

        private final GlobalLocker locker;
        private final String accountExternalKey;
        private final WithAccountLockCallback<T> callback;

        public CallableWithAccountLock(final GlobalLocker locker,
                                       final String accountExternalKey,
                                       final WithAccountLockCallback<T> callback) {
            this.locker = locker;
            this.accountExternalKey = accountExternalKey;
            this.callback = callback;
        }

        @Override
        public T call() throws Exception {
            return new WithAccountLock<T>().processAccountWithLock(locker, accountExternalKey, callback);
        }
    }

    public static class WithAccountLock<T> {

        public T processAccountWithLock(final GlobalLocker locker, final String accountExternalKey, final WithAccountLockCallback<T> callback)
                throws PaymentApiException {
            GlobalLock lock = null;
            try {
                lock = locker.lockWithNumberOfTries(LockerType.ACCOUNT, accountExternalKey, NB_LOCK_TRY);
                return callback.doOperation();
            } catch (LockFailedException e) {
                final String format = String.format("Failed to lock account %s", accountExternalKey);
                log.error(String.format(format), e);
                throw new PaymentApiException(ErrorCode.PAYMENT_INTERNAL_ERROR, format);
            } finally {
                if (lock != null) {
                    lock.release();
                }
            }
        }
    }
}
