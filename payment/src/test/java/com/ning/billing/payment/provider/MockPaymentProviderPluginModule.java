package com.ning.billing.payment.provider;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

public class MockPaymentProviderPluginModule extends AbstractModule {
    private final String instanceName;

    public MockPaymentProviderPluginModule(String instanceName) {
        this.instanceName = instanceName;
    }

    @Override
    protected void configure() {
        bind(MockPaymentProviderPlugin.class)
            .annotatedWith(Names.named(instanceName))
            .toProvider(new MockPaymentProviderPluginProvider(instanceName))
            .asEagerSingleton();
    }
}
