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

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.plugin.api.PaymentProviderPlugin;
import com.ning.billing.payment.provider.PaymentProviderPluginRegistry;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.bus.BusEvent;
import com.ning.billing.util.bus.Bus.EventBusException;
import com.ning.billing.util.globallocker.GlobalLock;
import com.ning.billing.util.globallocker.GlobalLocker;
import com.ning.billing.util.globallocker.LockFailedException;
import com.ning.billing.util.globallocker.GlobalLocker.LockerService;

public abstract class ProcessorBase {

    private final static int NB_LOCK_TRY = 5;
    
    protected final PaymentProviderPluginRegistry pluginRegistry;
    protected final AccountUserApi accountUserApi;
    protected final Bus eventBus;
    protected final GlobalLocker locker;
    protected final ExecutorService executor;

    private static final Logger log = LoggerFactory.getLogger(ProcessorBase.class);
    
    public ProcessorBase(final PaymentProviderPluginRegistry pluginRegistry,
            final AccountUserApi accountUserApi,
            final Bus eventBus,
            final GlobalLocker locker,
            final ExecutorService executor) {
        this.pluginRegistry = pluginRegistry;
        this.accountUserApi = accountUserApi;
        this.eventBus= eventBus;
        this.locker = locker;
        this.executor = executor;
    }
    
    
    protected PaymentProviderPlugin getPaymentProviderPlugin(String accountKey) {

        String paymentProviderName = null;
        if (accountKey != null) {
            Account account;
            try {
                account = accountUserApi.getAccountByKey(accountKey);
                return getPaymentProviderPlugin(account);
            } catch (AccountApiException e) {
                log.error("Error getting payment provider plugin.", e);
            }
        }
        return pluginRegistry.getPlugin(paymentProviderName);
    }
    
    protected PaymentProviderPlugin getPaymentProviderPlugin(Account account) {
        String paymentProviderName = null;

        if (account != null) {
            paymentProviderName = account.getPaymentProviderName();
        }

        return pluginRegistry.getPlugin(paymentProviderName);
    }

    protected void postPaymentEvent(BusEvent ev, UUID accountId) {
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
    
    

    public static class WithAccountLockAndTimeout<T> implements Callable<T> {
        
        private final GlobalLocker locker;
        private final String accountExternalKey;
        private final WithAccountLockCallback<T> callback;
        
        public WithAccountLockAndTimeout(final GlobalLocker locker,
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
                lock = locker.lockWithNumberOfTries(LockerService.PAYMENT, accountExternalKey, NB_LOCK_TRY);
                return callback.doOperation();
            } catch (LockFailedException e) {
                String format = String.format("Failed to lock account %s", accountExternalKey);
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
