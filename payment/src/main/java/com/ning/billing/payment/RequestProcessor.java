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

package com.ning.billing.payment;

import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.payment.api.Either;
import com.ning.billing.payment.api.PaymentError;
import com.ning.billing.payment.provider.PaymentProviderPlugin;
import com.ning.billing.payment.provider.PaymentProviderPluginRegistry;
import com.ning.billing.util.eventbus.EventBus;
import com.ning.billing.util.eventbus.EventBus.EventBusException;

public class RequestProcessor {
    public static final String PAYMENT_PROVIDER_KEY = "paymentProvider";
    private final AccountUserApi accountUserApi;
    private final PaymentProviderPluginRegistry pluginRegistry;
    private final EventBus eventBus;

    @Inject
    public RequestProcessor(AccountUserApi accountUserApi,
                            PaymentProviderPluginRegistry pluginRegistry,
                            EventBus eventBus) {
        this.accountUserApi = accountUserApi;
        this.pluginRegistry = pluginRegistry;
        this.eventBus = eventBus;
    }

    @Subscribe
    public void receiveInvoice(Invoice invoice) throws EventBusException {
        final Account account = accountUserApi.getAccountById(invoice.getAccountId());
        final String paymentProviderName = account.getFieldValue(PAYMENT_PROVIDER_KEY);
        final PaymentProviderPlugin plugin = pluginRegistry.getPlugin(paymentProviderName);

        Either<PaymentError, PaymentInfo> result = plugin.processInvoice(account, invoice);

        eventBus.post(result.isLeft() ? result.getLeft() : result.getRight());
    }

    @Subscribe
    public void receivePaymentInfoRequest(PaymentInfoRequest paymentInfoRequest) throws EventBusException {
        final Account account = accountUserApi.getAccountById(paymentInfoRequest.getAccountId());
        final String paymentProviderName = account.getFieldValue(PAYMENT_PROVIDER_KEY);
        final PaymentProviderPlugin plugin = pluginRegistry.getPlugin(paymentProviderName);

        Either<PaymentError, PaymentInfo> result = plugin.getPaymentInfo(paymentInfoRequest.getPaymentId());

        eventBus.post(result.isLeft() ? result.getLeft() : result.getRight());
    }
}
