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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.killbill.billing.invoice.plugin.api.InvoicePluginApi;
import org.killbill.billing.osgi.api.OSGIServiceDescriptor;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

public class DefaultInvoiceProviderPluginRegistry implements OSGIServiceRegistration<InvoicePluginApi> {

    private final static Logger log = LoggerFactory.getLogger(DefaultInvoiceProviderPluginRegistry.class);

    private final Map<String, InvoicePluginApi> pluginsByName = new ConcurrentHashMap<String, InvoicePluginApi>();

    @Inject
    public DefaultInvoiceProviderPluginRegistry() {
    }


    @Override
    public void registerService(final OSGIServiceDescriptor desc, final InvoicePluginApi service) {
        log.info("Registering service='{}'", desc.getRegistrationName());
        pluginsByName.put(desc.getRegistrationName(), service);
    }

    @Override
    public void unregisterService(final String serviceName) {
        log.info("Unregistering service='{}'", serviceName);
        pluginsByName.remove(serviceName);
    }

    @Override
    public InvoicePluginApi getServiceForName(final String name) {
        if (name == null) {
            throw new IllegalArgumentException("Null invoice plugin API name");
        }
        final InvoicePluginApi plugin = pluginsByName.get(name);
        return plugin;
    }

    @Override
    public Set<String> getAllServices() {
        return pluginsByName.keySet();
    }

    @Override
    public Class<InvoicePluginApi> getServiceType() {
        return InvoicePluginApi.class;
    }}
