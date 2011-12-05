package com.ning.billing.payment.provider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang.StringUtils;

import com.google.inject.Inject;
import com.ning.billing.payment.setup.PaymentConfig;

public class PaymentProviderPluginRegistry {
    private final String defaultPlugin;
    private final Map<String, PaymentProviderPlugin> pluginsByName = new ConcurrentHashMap<String, PaymentProviderPlugin>();

    @Inject
    public PaymentProviderPluginRegistry(PaymentConfig config) {
        this.defaultPlugin = config.getDefaultPaymentProvider();
    }

    public void register(PaymentProviderPlugin plugin, String name) {
        pluginsByName.put(name.toLowerCase(), plugin);
    }

    public PaymentProviderPlugin getPlugin(String name) {
        return pluginsByName.get(StringUtils.defaultIfEmpty(name, defaultPlugin).toLowerCase());
    }
}
