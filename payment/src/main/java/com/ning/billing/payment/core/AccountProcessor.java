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

import static com.ning.billing.payment.glue.PaymentModule.PLUGIN_EXECUTOR_NAMED;

import java.util.concurrent.ExecutorService;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.plugin.api.PaymentPluginApiException;
import com.ning.billing.payment.plugin.api.PaymentProviderAccount;
import com.ning.billing.payment.plugin.api.PaymentProviderPlugin;
import com.ning.billing.payment.provider.PaymentProviderPluginRegistry;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.globallocker.GlobalLocker;


public class AccountProcessor extends ProcessorBase {

    @Inject
    public AccountProcessor(final PaymentProviderPluginRegistry pluginRegistry,
            final AccountUserApi accountUserApi,
            final Bus eventBus,
            final GlobalLocker locker,
            @Named(PLUGIN_EXECUTOR_NAMED) final ExecutorService executor) {
        super(pluginRegistry, accountUserApi, eventBus, locker, executor);        
    }
    
    public String createPaymentProviderAccount(Account account, CallContext context) 
    throws PaymentApiException {
        try {
            final PaymentProviderPlugin plugin = getPaymentProviderPlugin((Account)null);
            return plugin.createPaymentProviderAccount(account);
        } catch (PaymentPluginApiException e) {
            throw new PaymentApiException(ErrorCode.PAYMENT_CREATE_PAYMENT_PROVIDER_ACCOUNT, account.getId(), e.getMessage());
        }
    }

    public void updatePaymentProviderAccountContact(String externalKey, CallContext context) 
        throws PaymentApiException {
        
        Account account = null;
        try {
            account = accountUserApi.getAccountByKey(externalKey);
            final PaymentProviderPlugin plugin = getPaymentProviderPlugin(account);
            plugin.updatePaymentProviderAccountExistingContact(account);
        } catch (AccountApiException e) {
            throw new PaymentApiException(e);
        } catch (PaymentPluginApiException e) {
            throw new PaymentApiException(ErrorCode.PAYMENT_UPD_PAYMENT_PROVIDER_ACCOUNT, account.getId(), e.getMessage());
        }
    }
    
    public PaymentProviderAccount getPaymentProviderAccount(String externalKey)
        throws PaymentApiException {
        Account account = null;
        try {
            account = accountUserApi.getAccountByKey(externalKey);
            final PaymentProviderPlugin plugin = getPaymentProviderPlugin(account);
            return plugin.getPaymentProviderAccount(externalKey);
        } catch (AccountApiException e) {
            throw new PaymentApiException(e);
        } catch (PaymentPluginApiException e) {
            throw new PaymentApiException(ErrorCode.PAYMENT_GET_PAYMENT_PROVIDER_ACCOUNT, account.getId(), e.getMessage());
        }
    }
}
