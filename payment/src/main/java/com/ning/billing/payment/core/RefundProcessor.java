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

import java.util.UUID;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import com.google.inject.name.Named;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.api.Refund;
import com.ning.billing.payment.provider.PaymentProviderPluginRegistry;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.globallocker.GlobalLocker;

public class RefundProcessor extends ProcessorBase {

    @Inject
    public RefundProcessor(final PaymentProviderPluginRegistry pluginRegistry,
            final AccountUserApi accountUserApi,
            final Bus eventBus,
            final GlobalLocker locker,
            @Named(PLUGIN_EXECUTOR_NAMED)  final ExecutorService executor) {
        super(pluginRegistry, accountUserApi, eventBus, locker, executor);        
    }
    
    public Refund createRefund(Account account, UUID paymentId, CallContext context)
    throws PaymentApiException {
        /*
        try {
            
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(account);
        List<PaymentInfoPlugin> result = plugin.processRefund(account);
        List<PaymentInfoEvent> info =  new LinkedList<PaymentInfoEvent>();
        int i = 0;
        for (PaymentInfoPlugin cur : result) {
            // STEPH
            //info.add(new DefaultPaymentInfoEvent(cur, account.getId(), invoiceIds.get(i)));
        }
        return info;
        } catch (PaymentPluginApiException e) {
            throw new PaymentApiException(ErrorCode.PAYMENT_CREATE_REFUND, account.getId(), e.getMessage());
        }
        */
        return null;
    }
}
