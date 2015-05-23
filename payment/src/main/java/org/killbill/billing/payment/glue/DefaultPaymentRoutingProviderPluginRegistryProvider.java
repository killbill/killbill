/*
 * Copyright 2014 Groupon, Inc
 *
 * Groupon licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.payment.glue;

import org.killbill.billing.osgi.api.OSGIServiceDescriptor;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.invoice.InvoicePaymentRoutingPluginApi;
import org.killbill.billing.payment.provider.DefaultPaymentRoutingProviderPlugin;
import org.killbill.billing.payment.provider.DefaultPaymentRoutingProviderPluginRegistry;
import org.killbill.billing.routing.plugin.api.PaymentRoutingPluginApi;
import org.killbill.billing.util.config.PaymentConfig;

import com.google.inject.Inject;
import com.google.inject.Provider;

public class DefaultPaymentRoutingProviderPluginRegistryProvider implements Provider<OSGIServiceRegistration<PaymentRoutingPluginApi>> {

    private final PaymentConfig paymentConfig;
    private final DefaultPaymentRoutingProviderPlugin externalPaymentControlProviderPlugin;
    private final InvoicePaymentRoutingPluginApi invoicePaymentControlPlugin;

    @Inject
    public DefaultPaymentRoutingProviderPluginRegistryProvider(final PaymentConfig paymentConfig,
                                                               final DefaultPaymentRoutingProviderPlugin externalPaymentControlProviderPlugin,
                                                               final InvoicePaymentRoutingPluginApi invoicePaymentControlPlugin) {
        this.paymentConfig = paymentConfig;
        this.externalPaymentControlProviderPlugin = externalPaymentControlProviderPlugin;
        this.invoicePaymentControlPlugin = invoicePaymentControlPlugin;
    }

    @Override
    public OSGIServiceRegistration<PaymentRoutingPluginApi> get() {
        final DefaultPaymentRoutingProviderPluginRegistry pluginRegistry = new DefaultPaymentRoutingProviderPluginRegistry();

        // Make the external payment provider plugin available by default
        final OSGIServiceDescriptor desc = new OSGIServiceDescriptor() {
            @Override
            public String getPluginSymbolicName() {
                return null;
            }

            @Override
            public String getRegistrationName() {
                return DefaultPaymentRoutingProviderPlugin.PLUGIN_NAME;
            }
        };
        pluginRegistry.registerService(desc, externalPaymentControlProviderPlugin);

        // Hack, because this is not a real plugin, so it can't register itself during lifecycle as it should.
        final OSGIServiceDescriptor desc2 = new OSGIServiceDescriptor() {
            @Override
            public String getPluginSymbolicName() {
                return null;
            }

            @Override
            public String getRegistrationName() {
                return InvoicePaymentRoutingPluginApi.PLUGIN_NAME;
            }
        };
        pluginRegistry.registerService(desc2, invoicePaymentControlPlugin);

        return pluginRegistry;
    }

}
