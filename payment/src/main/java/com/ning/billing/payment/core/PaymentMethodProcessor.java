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

import java.util.List;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import com.google.inject.name.Named;
import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.api.PaymentMethodInfo;
import com.ning.billing.payment.plugin.api.PaymentPluginApiException;
import com.ning.billing.payment.plugin.api.PaymentProviderPlugin;
import com.ning.billing.payment.provider.PaymentProviderPluginRegistry;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.globallocker.GlobalLocker;

public class PaymentMethodProcessor extends ProcessorBase {

    @Inject
    public PaymentMethodProcessor(final PaymentProviderPluginRegistry pluginRegistry,
            final AccountUserApi accountUserApi,
            final Bus eventBus,
            final GlobalLocker locker,
            @Named(PLUGIN_EXECUTOR_NAMED)  final ExecutorService executor) {
        super(pluginRegistry, accountUserApi, eventBus, locker, executor);
    }
    
    //@Override
    public PaymentMethodInfo getPaymentMethod(String accountKey, String paymentMethodId) 
        throws PaymentApiException {
            try {
                final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
                return plugin.getPaymentMethodInfo(paymentMethodId);
            } catch (PaymentPluginApiException e) {
                throw new PaymentApiException(e, ErrorCode.PAYMENT_NO_SUCH_PAYMENT_METHOD, accountKey, paymentMethodId);            
            }

        }

    //@Override
    public List<PaymentMethodInfo> getPaymentMethods(String accountKey)
    throws PaymentApiException {
        try {
            final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
            return plugin.getPaymentMethods(accountKey);
        } catch (PaymentPluginApiException e) {
            throw new PaymentApiException(e, ErrorCode.PAYMENT_NO_PAYMENT_METHODS, accountKey);
        }
    }

    //@Override
    public void updatePaymentGateway(final String accountKey, final CallContext context) 
    throws PaymentApiException {

        new WithAccountLock<Void>().processAccountWithLock(locker, accountKey, new WithAccountLockCallback<Void>() {
            @Override
            public Void doOperation() throws PaymentApiException {

                try {
                    final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
                    plugin.updatePaymentGateway(accountKey);
                    return null;
                } catch (PaymentPluginApiException e) {
                    throw new PaymentApiException(e, ErrorCode.PAYMENT_UPD_GATEWAY_FAILED, accountKey, e.getMessage());
                }
            }
        });
    }


    //@Override
    public String addPaymentMethod(final String accountKey, final PaymentMethodInfo paymentMethod, final CallContext context) 
    throws PaymentApiException {
        
        return new WithAccountLock<String>().processAccountWithLock(locker, accountKey, new WithAccountLockCallback<String>() {

            @Override
            public String doOperation() throws PaymentApiException {

                try {
                final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
                return plugin.addPaymentMethod(accountKey, paymentMethod);
                } catch (PaymentPluginApiException e) {
                    throw new PaymentApiException(e, ErrorCode.PAYMENT_ADD_PAYMENT_METHOD, accountKey, e.getMessage());
                }
            }
        });
    }


    //@Override
    public void deletePaymentMethod(final String accountKey, final String paymentMethodId, final CallContext context) 
    throws PaymentApiException {
        
        new WithAccountLock<Void>().processAccountWithLock(locker, accountKey, new WithAccountLockCallback<Void>() {

            @Override
            public Void doOperation() throws PaymentApiException {
                
                try {
                final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
                plugin.deletePaymentMethod(accountKey, paymentMethodId);
                return null;
                } catch (PaymentPluginApiException e) {
                    throw new PaymentApiException(e, ErrorCode.PAYMENT_DEL_PAYMENT_METHOD, accountKey, e.getMessage());
                }
            }
        });
    }

    //@Override
    public PaymentMethodInfo updatePaymentMethod(final String accountKey, final PaymentMethodInfo paymentMethodInfo, final CallContext context) 
    throws PaymentApiException {

        return new WithAccountLock<PaymentMethodInfo>().processAccountWithLock(locker, accountKey, new WithAccountLockCallback<PaymentMethodInfo>() {

            @Override
            public PaymentMethodInfo doOperation() throws PaymentApiException {
                try {
                    final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
                    return plugin.updatePaymentMethod(accountKey, paymentMethodInfo);
                }  catch (PaymentPluginApiException e) {
                    throw new PaymentApiException(e, ErrorCode.PAYMENT_UPD_PAYMENT_METHOD, accountKey, e.getMessage());
                }
            }
        });
    }
}
