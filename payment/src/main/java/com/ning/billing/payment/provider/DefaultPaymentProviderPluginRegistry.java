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

package com.ning.billing.payment.provider;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.ning.billing.osgi.api.OSGIServiceDescriptor;
import com.ning.billing.osgi.api.OSGIServiceRegistration;
import com.ning.billing.payment.plugin.api.PaymentPluginApi;
import com.ning.billing.util.config.PaymentConfig;

import com.google.common.base.Strings;
import com.google.inject.Inject;

public class DefaultPaymentProviderPluginRegistry implements OSGIServiceRegistration<PaymentPluginApi> {

    private final String defaultPlugin;
    private final Map<String, PaymentPluginApi> pluginsByName = new ConcurrentHashMap<String, PaymentPluginApi>();

    @Inject
    public DefaultPaymentProviderPluginRegistry(final PaymentConfig config) {
        this.defaultPlugin = config.getDefaultPaymentProvider();
    }


    @Override
    public void registerService(final OSGIServiceDescriptor desc, final PaymentPluginApi service) {
        pluginsByName.put(desc.getServiceName().toLowerCase(), service);
    }

    @Override
    public void unregisterService(final String serviceName) {
        pluginsByName.remove(serviceName.toLowerCase());
    }

    @Override
    public PaymentPluginApi getServiceForName(final String name) {
        final PaymentPluginApi plugin = pluginsByName.get((Strings.emptyToNull(name) == null ? defaultPlugin : name).toLowerCase());

        if (plugin == null) {
            throw new IllegalArgumentException("No payment provider plugin is configured for " + name);
        }

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
