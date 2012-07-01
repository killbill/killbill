/*
 * Copyright 2010-2011 Ning, Inc.
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

import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.ning.billing.config.PaymentConfig;
import com.ning.billing.payment.plugin.api.PaymentPluginApi;

public class DefaultPaymentProviderPluginRegistry implements PaymentProviderPluginRegistry {

    private final String defaultPlugin;
    private final Map<String, PaymentPluginApi> pluginsByName = new ConcurrentHashMap<String, PaymentPluginApi>();

    @Inject
    public DefaultPaymentProviderPluginRegistry(final PaymentConfig config) {
        this.defaultPlugin = config.getDefaultPaymentProvider();
    }

    @Override
    public void register(final PaymentPluginApi plugin, final String name) {
        pluginsByName.put(name.toLowerCase(), plugin);
    }

    @Override
    public PaymentPluginApi getPlugin(final String name) {
        final PaymentPluginApi plugin = pluginsByName.get((Strings.emptyToNull(name) == null ? defaultPlugin : name).toLowerCase());

        if (plugin == null) {
            throw new IllegalArgumentException("No payment provider plugin is configured for " + name);
        }

        return plugin;
    }

    @Override
    public Set<String> getRegisteredPluginNames() {
        return pluginsByName.keySet();
    }
}
