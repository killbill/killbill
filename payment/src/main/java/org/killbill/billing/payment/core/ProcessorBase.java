/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.payment.core;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dao.PaymentMethodModelDao;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.billing.payment.dispatcher.PluginDispatcher.PluginDispatcherReturnType;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.api.TagApiException;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.config.PaymentConfig;
import org.killbill.billing.util.globallocker.LockerType;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.billing.util.tag.Tag;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.locker.LockFailedException;
import org.killbill.commons.request.Request;
import org.killbill.commons.request.RequestData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.collect.Collections2;

public abstract class ProcessorBase {

    protected final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry;
    protected final AccountInternalApi accountInternalApi;
    protected final GlobalLocker locker;
    protected final PaymentDao paymentDao;
    protected final InternalCallContextFactory internalCallContextFactory;
    protected final TagInternalApi tagInternalApi;
    protected final Clock clock;

    protected static final Logger log = LoggerFactory.getLogger(ProcessorBase.class);
    protected final InvoiceInternalApi invoiceApi;

    public ProcessorBase(final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry,
                         final AccountInternalApi accountInternalApi,
                         final PaymentDao paymentDao,
                         final TagInternalApi tagInternalApi,
                         final GlobalLocker locker,
                         final InternalCallContextFactory internalCallContextFactory,
                         final InvoiceInternalApi invoiceApi,
                         final Clock clock) {
        this.pluginRegistry = pluginRegistry;
        this.accountInternalApi = accountInternalApi;
        this.paymentDao = paymentDao;
        this.locker = locker;
        this.tagInternalApi = tagInternalApi;
        this.internalCallContextFactory = internalCallContextFactory;
        this.invoiceApi = invoiceApi;
        this.clock = clock;
    }

    protected boolean isAccountAutoPayOff(final UUID accountId, final InternalTenantContext context) {
        final List<Tag> accountTags = tagInternalApi.getTags(accountId, ObjectType.ACCOUNT, context);

        return ControlTagType.isAutoPayOff(Collections2.transform(accountTags, new Function<Tag, UUID>() {
            @Nullable
            @Override
            public UUID apply(@Nullable final Tag tag) {
                return tag.getTagDefinitionId();
            }
        }));
    }

    protected void setAccountAutoPayOff(final UUID accountId, final InternalCallContext context) throws PaymentApiException {
        try {
            tagInternalApi.addTag(accountId, ObjectType.ACCOUNT, ControlTagType.AUTO_PAY_OFF.getId(), context);
        } catch (TagApiException e) {
            log.error("Failed to add AUTO_PAY_OFF on account " + accountId, e);
            throw new PaymentApiException(ErrorCode.PAYMENT_INTERNAL_ERROR, "Failed to add AUTO_PAY_OFF on account " + accountId);
        }
    }

    public Set<String> getAvailablePlugins() {
        return pluginRegistry.getAllServices();
    }

    protected PaymentPluginApi getPaymentPluginApi(final String pluginName) throws PaymentApiException {
        final PaymentPluginApi pluginApi = pluginRegistry.getServiceForName(pluginName);
        if (pluginApi == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT_PLUGIN, pluginName);
        }
        return pluginApi;
    }

    protected PaymentPluginApi getPaymentProviderPlugin(final UUID paymentMethodId, final InternalTenantContext context) throws PaymentApiException {
        final PaymentMethodModelDao methodDao = paymentDao.getPaymentMethodIncludedDeleted(paymentMethodId, context);
        if (methodDao == null) {
            log.error("PaymentMethod does not exist", paymentMethodId);
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT_METHOD, paymentMethodId);
        }
        return getPaymentPluginApi(methodDao.getPluginName());
    }

    protected String getPaymentProviderPluginName(final UUID paymentMethodId, final InternalTenantContext context) throws PaymentApiException {
        final PaymentMethodModelDao methodDao = paymentDao.getPaymentMethodIncludedDeleted(paymentMethodId, context);
        if (methodDao == null) {
            log.error("PaymentMethod does not exist", paymentMethodId);
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT_METHOD, paymentMethodId);
        }
        return methodDao.getPluginName();
    }

    protected PaymentPluginApi getPaymentProviderPlugin(final Account account, final InternalTenantContext context) throws PaymentApiException {
        final UUID paymentMethodId = getDefaultPaymentMethodId(account);
        return getPaymentProviderPlugin(paymentMethodId, context);
    }

    protected UUID getDefaultPaymentMethodId(final Account account) throws PaymentApiException {
        final UUID paymentMethodId = account.getPaymentMethodId();
        if (paymentMethodId == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_DEFAULT_PAYMENT_METHOD, account.getId());
        }
        return paymentMethodId;
    }

    protected TenantContext buildTenantContext(final InternalTenantContext context) {
        return internalCallContextFactory.createTenantContext(context);
    }

    protected CallContext buildCallContext(final InternalCallContext context) {
        return internalCallContextFactory.createCallContext(context);
    }

    public interface DispatcherCallback<PluginDispatcherReturnType, ExceptionType extends Exception> {
        public PluginDispatcherReturnType doOperation() throws ExceptionType;
    }

    public static class CallableWithAccountLock<ReturnType, ExceptionType extends Exception> implements Callable<PluginDispatcherReturnType<ReturnType>> {

        private final GlobalLocker locker;
        private final String accountExternalKey;
        private final DispatcherCallback<PluginDispatcherReturnType<ReturnType>, ExceptionType> callback;
        private final PaymentConfig paymentConfig;

        public CallableWithAccountLock(final GlobalLocker locker,
                                       final String accountExternalKey,
                                       final PaymentConfig paymentConfig,
                                       final DispatcherCallback<PluginDispatcherReturnType<ReturnType>, ExceptionType> callback) {
            this.locker = locker;
            this.accountExternalKey = accountExternalKey;
            this.callback = callback;
            this.paymentConfig = paymentConfig;
        }

        @Override
        public PluginDispatcherReturnType<ReturnType> call() throws ExceptionType, LockFailedException {
            return new WithAccountLock<ReturnType, ExceptionType>(paymentConfig).processAccountWithLock(locker, accountExternalKey, callback);
        }
    }

    public static class WithAccountLock<ReturnType, ExceptionType extends Exception> {

        private final PaymentConfig paymentConfig;

        public WithAccountLock(final PaymentConfig paymentConfig) {
            this.paymentConfig = paymentConfig;
        }

        public PluginDispatcherReturnType<ReturnType> processAccountWithLock(final GlobalLocker locker, final String accountExternalKey, final DispatcherCallback<PluginDispatcherReturnType<ReturnType>, ExceptionType> callback)
                throws ExceptionType, LockFailedException {
            GlobalLock lock = null;
            try {
                lock = locker.lockWithNumberOfTries(LockerType.ACCNT_INV_PAY.toString(), accountExternalKey, paymentConfig.getMaxGlobalLockRetries());
                return callback.doOperation();
            } finally {
                if (lock != null) {
                    lock.release();
                }
            }
        }
    }

    protected static <ReturnType> ReturnType dispatchWithExceptionHandling(@Nullable final Account account, final String pluginName, final Callable<PluginDispatcherReturnType<ReturnType>> callable, PluginDispatcher<ReturnType> pluginFormDispatcher) throws PaymentApiException {
        final UUID accountId = account != null ? account.getId() : null;
        final String accountExternalKey = account != null ? account.getExternalKey() : "";

        final RequestData requestData = Request.getPerThreadRequestData();
        final String requestId;
        if (requestData != null) {
            requestId = requestData.getRequestId();
        } else {
            requestId = "notAvailabeRequestId";
        }

        try {
            log.debug("Calling plugin {} with requestId {}", pluginName, requestId);
            ReturnType result = pluginFormDispatcher.dispatchWithTimeout(callable);
            log.debug("Successful call of plugin {} for account {} with result {} and requestId {}", pluginName, account.getExternalKey(), result, requestId);
            return result;
        } catch (final TimeoutException e) {
            final String errorMessage = String.format("TimeoutException during the execution of plugin %s with requestId %s ", pluginName, requestId);
            log.error(errorMessage, e);
            throw new PaymentApiException(ErrorCode.PAYMENT_PLUGIN_TIMEOUT, accountId, errorMessage);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            final String errorMessage = String.format("InterruptedException during the execution of plugin %s with requestId %s ", pluginName, requestId);
            log.error(errorMessage, e);
            throw new PaymentApiException(ErrorCode.PAYMENT_INTERNAL_ERROR, Objects.firstNonNull(e.getMessage(), errorMessage));
        } catch (final ExecutionException e) {
            if (e.getCause() instanceof PaymentApiException) {
                throw (PaymentApiException) e.getCause();
            } else if (e.getCause() instanceof LockFailedException) {
                final String format = String.format("Failed to lock account %s", accountExternalKey);
                log.error(String.format(format), e);
                throw new PaymentApiException(ErrorCode.PAYMENT_INTERNAL_ERROR, format);
            } else {
                throw new PaymentApiException(e, ErrorCode.PAYMENT_INTERNAL_ERROR, Objects.firstNonNull(e.getMessage(), ""));
            }
        }
    }

}
