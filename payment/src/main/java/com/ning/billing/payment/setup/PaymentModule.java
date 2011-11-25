package com.ning.billing.payment.setup;

import java.util.Properties;

import org.skife.config.ConfigurationObjectFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.ning.billing.payment.provider.PaymentProviderPlugin;

public class PaymentModule extends AbstractModule {
    private final Properties props;

    public PaymentModule() {
        this.props = System.getProperties();
    }

    public PaymentModule(Properties props) {
        this.props = props;
    }

    @SuppressWarnings("unchecked")
    protected void installPaymentProviderPlugin(PaymentConfig config) {
        String pluginClassName = config.getProviderPluginClass();

        if (pluginClassName == null) {
            throw new IllegalArgumentException("No payment provider plugin class configured");
        }
        Class<? extends PaymentProviderPlugin> pluginClass = null;

        try {
            pluginClass = (Class<? extends PaymentProviderPlugin>)Class.forName(pluginClassName);
        }
        catch (Exception ex) {
            throw new IllegalArgumentException("Illegal payment provider plugin class configured", ex);
        }
        bind(PaymentProviderPlugin.class).to(Key.get(pluginClass));
    }

    @Override
    protected void configure() {
        final PaymentConfig config = new ConfigurationObjectFactory(props).build(PaymentConfig.class);

        installPaymentProviderPlugin(config);
    }
}
