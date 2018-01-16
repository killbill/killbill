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

package org.killbill.billing.invoice.provider;

import org.killbill.billing.invoice.plugin.api.InvoicePluginApi;
import org.killbill.billing.osgi.api.OSGIServiceDescriptor;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.clock.Clock;

import com.google.inject.Inject;
import com.google.inject.Provider;

public class NoOpInvoiceProviderPluginProvider implements Provider<DefaultNoOpInvoiceProviderPlugin> {

    private final String instanceName;

    private Clock clock;
    private OSGIServiceRegistration<InvoicePluginApi> registry;

    public NoOpInvoiceProviderPluginProvider(final String instanceName) {
        this.instanceName = instanceName;

    }

    @Inject
    public void setPaymentProviderPluginRegistry(final OSGIServiceRegistration<InvoicePluginApi> registry, final Clock clock) {
        this.clock = clock;
        this.registry = registry;
    }

    @Override
    public DefaultNoOpInvoiceProviderPlugin get() {

        final DefaultNoOpInvoiceProviderPlugin plugin = new DefaultNoOpInvoiceProviderPlugin();
        final OSGIServiceDescriptor desc = new OSGIServiceDescriptor() {
            @Override
            public String getPluginSymbolicName() {
                return null;
            }

            @Override
            public String getPluginName() {
                return instanceName;
            }

            @Override
            public String getRegistrationName() {
                return instanceName;
            }
        };
        registry.registerService(desc, plugin);
        return plugin;
    }
}
