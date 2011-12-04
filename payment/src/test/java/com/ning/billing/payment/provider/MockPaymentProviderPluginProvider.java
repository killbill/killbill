package com.ning.billing.payment.provider;

import com.google.inject.Inject;
import com.google.inject.Provider;

public class MockPaymentProviderPluginProvider implements Provider<MockPaymentProviderPlugin> {
    private PaymentProviderPluginRegistry registry;
    private final String instanceName;

    public MockPaymentProviderPluginProvider(String instanceName) {
        this.instanceName = instanceName;
    }

    @Inject
    public void setPaymentProviderPluginRegistry(PaymentProviderPluginRegistry registry) {
        this.registry = registry;
    }

    @Override
    public MockPaymentProviderPlugin get() {
        MockPaymentProviderPlugin plugin = new MockPaymentProviderPlugin();

        registry.register(plugin, instanceName);
        return plugin;
    }
}
