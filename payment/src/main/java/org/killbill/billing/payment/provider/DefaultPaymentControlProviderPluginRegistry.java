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

package org.killbill.billing.payment.provider;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.killbill.billing.osgi.api.OSGIServiceDescriptor;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.control.plugin.api.PaymentControlPluginApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

public class DefaultPaymentControlProviderPluginRegistry implements OSGIServiceRegistration<PaymentControlPluginApi> {

    private final static Logger log = LoggerFactory.getLogger(DefaultPaymentProviderPluginRegistry.class);

    private final Map<String, PaymentControlPluginApi> pluginsByName = new ConcurrentHashMap<String, PaymentControlPluginApi>();

    @Inject
    public DefaultPaymentControlProviderPluginRegistry() {
    }

    @Override
    public void registerService(final OSGIServiceDescriptor desc, final PaymentControlPluginApi service) {
        log.info("Registering service='{}'", desc.getRegistrationName());
        pluginsByName.put(desc.getRegistrationName(), service);
    }

    @Override
    public void unregisterService(final String serviceName) {
        log.info("Unregistering service='{}'", serviceName);
        pluginsByName.remove(serviceName);
    }

    @Override
    public PaymentControlPluginApi getServiceForName(final String name) {
        if (name == null) {
            throw new IllegalArgumentException("Null payment plugin APi name");
        }
        final PaymentControlPluginApi plugin = pluginsByName.get(name);
        return plugin;
    }

    @Override
    public Set<String> getAllServices() {
        return pluginsByName.keySet();
    }

    @Override
    public Class<PaymentControlPluginApi> getServiceType() {
        return PaymentControlPluginApi.class;
    }
}
