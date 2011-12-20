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

package com.ning.billing.payment.api;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.payment.RequestProcessor;
import com.ning.billing.payment.provider.PaymentProviderPlugin;
import com.ning.billing.payment.provider.PaymentProviderPluginRegistry;

public class DefaultPaymentApi implements PaymentApi {
    private final PaymentProviderPluginRegistry pluginRegistry;
    private final AccountUserApi accountUserApi;
    private final InvoicePaymentApi invoicePaymentApi;

    @Inject
    public DefaultPaymentApi(PaymentProviderPluginRegistry pluginRegistry,
                             AccountUserApi accountUserApi,
                             InvoicePaymentApi invoicePaymentApi) {
        this.pluginRegistry = pluginRegistry;
        this.accountUserApi = accountUserApi;
        this.invoicePaymentApi = invoicePaymentApi;
    }

    @Override
    public Either<PaymentError, PaymentMethodInfo> getPaymentMethod(@Nullable String accountId, String paymentMethodId) {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountId);
        return plugin.getPaymentMethodInfo(paymentMethodId);
    }

    private PaymentProviderPlugin getPaymentProviderPlugin(String accountKey) {
        final String paymentProviderName;

        if (accountKey == null) {
            // TODO: get provider name from config to support null
            paymentProviderName = null;
        }
        else {
            final Account account = accountUserApi.getAccountById(UUID.fromString(accountKey));
            paymentProviderName = account.getFieldValue(RequestProcessor.PAYMENT_PROVIDER_KEY);
        }

        return pluginRegistry.getPlugin(paymentProviderName);
    }

    @Override
    public Either<PaymentError, List<PaymentMethodInfo>> getPaymentMethods(String accountKey) {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
        return plugin.getPaymentMethods(accountKey);
    }

    @Override
    public Either<PaymentError, Void> updatePaymentGateway(String accountKey) {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
        return plugin.updatePaymentGateway(accountKey);
    }

    @Override
    public Either<PaymentError, PaymentProviderAccount> getPaymentProviderAccount(String accountKey) {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
        return plugin.getPaymentProviderAccount(accountKey);
    }

    @Override
    public Either<PaymentError, String> addPaypalPaymentMethod(@Nullable String accountKey, PaypalPaymentMethodInfo paypalPaymentMethod) {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
        return plugin.addPaypalPaymentMethod(accountKey, paypalPaymentMethod);
    }

    @Override
    public Either<PaymentError, Void> deletePaymentMethod(String accountKey, String paymentMethodId) {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
        return plugin.deletePaypalPaymentMethod(accountKey, paymentMethodId);
    }

    @Override
    public Either<PaymentError, PaymentMethodInfo> updatePaymentMethod(String accountKey, PaymentMethodInfo paymentMethodInfo) {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
        return plugin.updatePaypalPaymentMethod(accountKey, paymentMethodInfo);
    }

    @Override
    public List<Either<PaymentError, PaymentInfo>> createPayment(String accountKey, List<String> invoiceIds) {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
        final Account account = accountUserApi.getAccountByKey(accountKey);
        List<Either<PaymentError, PaymentInfo>> processedPaymentsOrErrors = new ArrayList<Either<PaymentError, PaymentInfo>>(invoiceIds.size());

        for (String invoiceId : invoiceIds) {
            Invoice invoice = invoicePaymentApi.getInvoice(UUID.fromString(invoiceId));
            Either<PaymentError, PaymentInfo> paymentOrError = plugin.processInvoice(account, invoice);
            processedPaymentsOrErrors.add(paymentOrError);
        }

        return processedPaymentsOrErrors;
    }

    @Override
    public Either<PaymentError, PaymentProviderAccount> createPaymentProviderAccount(PaymentProviderAccount account) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Either<PaymentError, PaymentProviderAccount> updatePaymentProviderAccount(PaymentProviderAccount account) {
        // TODO Auto-generated method stub
        return null;
    }
}
