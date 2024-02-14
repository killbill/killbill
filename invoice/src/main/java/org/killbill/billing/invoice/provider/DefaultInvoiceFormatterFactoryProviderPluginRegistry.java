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

package org.killbill.billing.invoice.provider;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.killbill.billing.invoice.plugin.api.InvoiceFormatterFactory;
import org.killbill.billing.osgi.api.OSGIServiceDescriptor;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultInvoiceFormatterFactoryProviderPluginRegistry implements OSGIServiceRegistration<InvoiceFormatterFactory>  {

    private static final Logger log = LoggerFactory.getLogger(DefaultInvoiceFormatterFactoryProviderPluginRegistry.class);

    private final Map<String, InvoiceFormatterFactory> pluginsByName = new ConcurrentHashMap<String, InvoiceFormatterFactory>();

    @Override
    public void registerService(final OSGIServiceDescriptor desc, final InvoiceFormatterFactory service) {
        log.info("Registering service='{}'", desc.getRegistrationName());
        pluginsByName.put(desc.getRegistrationName(), service);
    }

    @Override
    public void unregisterService(final String serviceName) {
        log.info("Unregistering service='{}'", serviceName);
        pluginsByName.remove(serviceName);
    }

    @Override
    public InvoiceFormatterFactory getServiceForName(final String name) {
        if (name == null) {
            throw new IllegalArgumentException("Null invoice formatter factory API name");
        }

        return pluginsByName.get(name);
    }

    @Override
    public Set<String> getAllServices() {
        return pluginsByName.keySet();
    }

    @Override
    public Class<InvoiceFormatterFactory> getServiceType() {
        return InvoiceFormatterFactory.class;
    }
}

