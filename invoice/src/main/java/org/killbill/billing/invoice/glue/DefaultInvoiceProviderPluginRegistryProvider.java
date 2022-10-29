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

package org.killbill.billing.invoice.glue;

import javax.inject.Inject;
import javax.inject.Provider;

import org.killbill.billing.invoice.plugin.api.InvoicePluginApi;
import org.killbill.billing.invoice.provider.DefaultInvoiceProviderPluginRegistry;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;

public class DefaultInvoiceProviderPluginRegistryProvider implements Provider<OSGIServiceRegistration<InvoicePluginApi>> {

    @Inject
    public DefaultInvoiceProviderPluginRegistryProvider() {
    }

    @Override
    public OSGIServiceRegistration<InvoicePluginApi> get() {
        final DefaultInvoiceProviderPluginRegistry pluginRegistry = new DefaultInvoiceProviderPluginRegistry();

        return pluginRegistry;
    }
}
