/*
 * Copyright 2010-2013 Ning, Inc.
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

package com.ning.billing.payment.glue;

import com.ning.billing.util.config.PaymentConfig;
import com.ning.billing.payment.provider.DefaultPaymentProviderPluginRegistry;
import com.ning.billing.payment.provider.ExternalPaymentProviderPlugin;

import com.google.inject.Inject;
import com.google.inject.Provider;

public class DefaultPaymentProviderPluginRegistryProvider implements Provider<DefaultPaymentProviderPluginRegistry> {

    private final PaymentConfig paymentConfig;
    private final ExternalPaymentProviderPlugin externalPaymentProviderPlugin;

    @Inject
    public DefaultPaymentProviderPluginRegistryProvider(final PaymentConfig paymentConfig, final ExternalPaymentProviderPlugin externalPaymentProviderPlugin) {
        this.paymentConfig = paymentConfig;
        this.externalPaymentProviderPlugin = externalPaymentProviderPlugin;
    }

    @Override
    public DefaultPaymentProviderPluginRegistry get() {
        final DefaultPaymentProviderPluginRegistry pluginRegistry = new DefaultPaymentProviderPluginRegistry(paymentConfig);

        // Make the external payment provider plugin available by default
        pluginRegistry.register(externalPaymentProviderPlugin, ExternalPaymentProviderPlugin.PLUGIN_NAME);

        return pluginRegistry;
    }
}
