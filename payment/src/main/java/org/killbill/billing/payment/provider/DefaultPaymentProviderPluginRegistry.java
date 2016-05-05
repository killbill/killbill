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

package org.killbill.billing.payment.provider;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.killbill.billing.osgi.api.OSGIServiceDescriptor;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.util.config.definition.PaymentConfig;

import com.google.inject.Inject;

public class DefaultPaymentProviderPluginRegistry implements OSGIServiceRegistration<PaymentPluginApi> {

    private final static Logger log = LoggerFactory.getLogger(DefaultPaymentProviderPluginRegistry.class);

    private final String defaultPlugin;
    private final Map<String, PaymentPluginApi> pluginsByName = new ConcurrentHashMap<String, PaymentPluginApi>();

    @Inject
    public DefaultPaymentProviderPluginRegistry(final PaymentConfig config) {
        this.defaultPlugin = config.getDefaultPaymentProvider();
    }


    @Override
    public void registerService(final OSGIServiceDescriptor desc, final PaymentPluginApi service) {
        log.info("Registering service='{}'", desc.getRegistrationName());
        pluginsByName.put(desc.getRegistrationName(), service);
    }

    @Override
    public void unregisterService(final String serviceName) {
        log.info("Unregistering service='{}'", serviceName);
        pluginsByName.remove(serviceName);
    }

    @Override
    public PaymentPluginApi getServiceForName(final String name) {
        if (name == null) {
            throw new IllegalArgumentException("Null payment plugin APi name");
        }
        final PaymentPluginApi plugin = pluginsByName.get(name);
        return plugin;
    }

    @Override
    public Set<String> getAllServices() {
        return pluginsByName.keySet();
    }

    @Override
    public Class<PaymentPluginApi> getServiceType() {
        return PaymentPluginApi.class;
    }
}
