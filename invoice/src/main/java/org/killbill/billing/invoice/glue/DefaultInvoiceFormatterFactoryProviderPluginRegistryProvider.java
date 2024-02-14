/*
 * Copyright 2020-2024 Equinix, Inc
 * Copyright 2014-2024 The Billing Project, LLC
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

package org.killbill.billing.invoice.glue;

import javax.inject.Inject;
import javax.inject.Provider;

import org.killbill.billing.invoice.plugin.api.InvoiceFormatterFactory;
import org.killbill.billing.invoice.provider.DefaultInvoiceFormatterFactoryProviderPluginRegistry;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;

public class DefaultInvoiceFormatterFactoryProviderPluginRegistryProvider implements Provider<OSGIServiceRegistration<InvoiceFormatterFactory>> {

    @Inject
    public DefaultInvoiceFormatterFactoryProviderPluginRegistryProvider() {
    }

    @Override
    public OSGIServiceRegistration<InvoiceFormatterFactory> get() {
        return new DefaultInvoiceFormatterFactoryProviderPluginRegistry();
    }
}
