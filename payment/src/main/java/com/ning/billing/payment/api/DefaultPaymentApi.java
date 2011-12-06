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

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.google.inject.Inject;
import com.ning.billing.account.api.IAccount;
import com.ning.billing.account.api.IAccountUserApi;
import com.ning.billing.payment.RequestProcessor;
import com.ning.billing.payment.provider.PaymentProviderPlugin;
import com.ning.billing.payment.provider.PaymentProviderPluginRegistry;

public class DefaultPaymentApi implements PaymentApi {
    private final PaymentProviderPluginRegistry pluginRegistry;
    private final IAccountUserApi accountUserApi;

    @Inject
    public DefaultPaymentApi(PaymentProviderPluginRegistry pluginRegistry, IAccountUserApi accountUserApi) {
        this.pluginRegistry = pluginRegistry;
        this.accountUserApi = accountUserApi;
    }

    @Override
    public Either<PaymentError, PaymentMethodInfo> getPaymentMethod(@Nullable String accountId, String paymentMethodId) {
        final String paymentProviderName;

        if (accountId == null) {
            // TODO: get provider name from config to support null
            paymentProviderName = null;
        }
        else {
            final IAccount account = accountUserApi.getAccountFromId(UUID.fromString(accountId));
            paymentProviderName = account.getFieldValue(RequestProcessor.PAYMENT_PROVIDER_KEY);
        }
        final PaymentProviderPlugin plugin = pluginRegistry.getPlugin(paymentProviderName);

        return plugin.getPaymentMethodInfo(paymentMethodId);
    }

    @Override
    public Either<PaymentError, List<PaymentMethodInfo>> getPaymentMethods(String accountId) {
        final String paymentProviderName;

        final IAccount account = accountUserApi.getAccountFromId(UUID.fromString(accountId));
        paymentProviderName = account.getFieldValue(RequestProcessor.PAYMENT_PROVIDER_KEY);

        final PaymentProviderPlugin plugin = pluginRegistry.getPlugin(paymentProviderName);

        return plugin.getPaymentMethods(accountId);
    }
}
