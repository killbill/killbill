package com.ning.billing.payment.setup;

import org.skife.config.Config;
import org.skife.config.DefaultNull;

public interface PaymentConfig
{
    @Config("killbill.payment.providerPluginClass")
    @DefaultNull
    public String getProviderPluginClass();
}
