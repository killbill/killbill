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

package org.killbill.billing.currency;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.killbill.billing.currency.plugin.api.CurrencyPluginApi;
import org.killbill.billing.osgi.api.OSGIServiceDescriptor;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;

import com.google.inject.Inject;

public class DefaultCurrencyProviderPluginRegistry implements OSGIServiceRegistration<CurrencyPluginApi> {

    private final static Logger log = LoggerFactory.getLogger(DefaultCurrencyProviderPluginRegistry.class);

    private final Map<String, CurrencyPluginApi> pluginsByName = new ConcurrentHashMap<String, CurrencyPluginApi>();

    @Inject
    public DefaultCurrencyProviderPluginRegistry() {
    }

    @Override
    public void registerService(final OSGIServiceDescriptor desc, final CurrencyPluginApi service) {
        log.info("Registering service='{}'", desc.getRegistrationName());
        pluginsByName.put(desc.getRegistrationName(), service);
    }

    @Override
    public void unregisterService(final String serviceName) {
        log.info("Unregistering service='{}'", serviceName);
        pluginsByName.remove(serviceName);
    }

    @Override
    public CurrencyPluginApi getServiceForName(final String serviceName) {
        return pluginsByName.get(serviceName);
    }

    @Override
    public Set<String> getAllServices() {
        return pluginsByName.keySet();
    }

    @Override
    public Class<CurrencyPluginApi> getServiceType() {
        return CurrencyPluginApi.class;
    }
}
