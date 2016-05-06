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

package org.killbill.billing.catalog.provider;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;

import org.killbill.billing.catalog.plugin.api.CatalogPluginApi;
import org.killbill.billing.osgi.api.OSGIServiceDescriptor;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultCatalogProviderPluginRegistry implements OSGIServiceRegistration<CatalogPluginApi> {

    private final static Logger log = LoggerFactory.getLogger(DefaultCatalogProviderPluginRegistry.class);

    private final Map<String, CatalogPluginApi> pluginsByName = new ConcurrentHashMap<String, CatalogPluginApi>();

    @Inject
    public DefaultCatalogProviderPluginRegistry() {
    }

    @Override
    public void registerService(final OSGIServiceDescriptor desc, final CatalogPluginApi service) {
        log.info("Registering service='{}'", desc.getRegistrationName());
        pluginsByName.put(desc.getRegistrationName(), service);
    }

    @Override
    public void unregisterService(final String serviceName) {
        log.info("Unregistering service='{}'", serviceName);
        pluginsByName.remove(serviceName);
    }

    @Override
    public CatalogPluginApi getServiceForName(final String serviceName) {
        if (serviceName == null) {
            throw new IllegalArgumentException("Null catalog plugin API name");
        }
        final CatalogPluginApi plugin = pluginsByName.get(serviceName);
        return plugin;
    }

    @Override
    public Set<String> getAllServices() {
        return pluginsByName.keySet();
    }

    @Override
    public Class<CatalogPluginApi> getServiceType() {
        return CatalogPluginApi.class;
    }
}
