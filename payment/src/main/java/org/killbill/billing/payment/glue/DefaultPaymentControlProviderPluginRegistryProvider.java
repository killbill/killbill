/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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
import org.killbill.billing.payment.invoice.InvoicePaymentControlPluginApi;
import org.killbill.billing.payment.provider.DefaultPaymentControlProviderPlugin;
import org.killbill.billing.payment.provider.DefaultPaymentControlProviderPluginRegistry;
import org.killbill.billing.control.plugin.api.PaymentControlPluginApi;

import com.google.inject.Inject;
import com.google.inject.Provider;

public class DefaultPaymentControlProviderPluginRegistryProvider implements Provider<OSGIServiceRegistration<PaymentControlPluginApi>> {

    private final DefaultPaymentControlProviderPlugin externalPaymentControlProviderPlugin;
    private final InvoicePaymentControlPluginApi invoicePaymentControlPlugin;

    @Inject
    public DefaultPaymentControlProviderPluginRegistryProvider(final DefaultPaymentControlProviderPlugin externalPaymentControlProviderPlugin,
                                                               final InvoicePaymentControlPluginApi invoicePaymentControlPlugin) {
        this.externalPaymentControlProviderPlugin = externalPaymentControlProviderPlugin;
        this.invoicePaymentControlPlugin = invoicePaymentControlPlugin;
    }

    @Override
    public OSGIServiceRegistration<PaymentControlPluginApi> get() {
        final DefaultPaymentControlProviderPluginRegistry pluginRegistry = new DefaultPaymentControlProviderPluginRegistry();

        // Make the external payment provider plugin available by default
        final OSGIServiceDescriptor desc = new OSGIServiceDescriptor() {
            @Override
            public String getPluginSymbolicName() {
                return null;
            }

            @Override
            public String getPluginName() {
                return DefaultPaymentControlProviderPlugin.PLUGIN_NAME;
            }

            @Override
            public String getRegistrationName() {
                return DefaultPaymentControlProviderPlugin.PLUGIN_NAME;
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
            public String getPluginName() {
                return InvoicePaymentControlPluginApi.PLUGIN_NAME;
            }

            @Override
            public String getRegistrationName() {
                return InvoicePaymentControlPluginApi.PLUGIN_NAME;
            }
        };
        pluginRegistry.registerService(desc2, invoicePaymentControlPlugin);

        return pluginRegistry;
    }
}
