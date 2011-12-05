package com.ning.billing.payment.api;

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
    public Either<PaymentError, PaymentMethodInfo> getPaymentMethodInfo(@Nullable String accountId, String paymentMethodId) {
        final String paymentProviderName;

        if (accountId == null) {
            // TODO: backwards compatible mode: get provider name from config
            paymentProviderName = null;
        }
        else {
            final IAccount account = accountUserApi.getAccountFromId(UUID.fromString(accountId));
            paymentProviderName = account.getFieldValue(RequestProcessor.PAYMENT_PROVIDER_KEY);
        }
        final PaymentProviderPlugin plugin = pluginRegistry.getPlugin(paymentProviderName);

        return plugin.getPaymentMethodInfo(paymentMethodId);
    }
}
