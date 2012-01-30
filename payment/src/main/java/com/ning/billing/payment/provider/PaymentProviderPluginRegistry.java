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
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang.StringUtils;

import com.google.inject.Inject;
import com.ning.billing.payment.setup.PaymentConfig;

public class PaymentProviderPluginRegistry {
    private final String defaultPlugin;
    private final Map<String, PaymentProviderPlugin> pluginsByName = new ConcurrentHashMap<String, PaymentProviderPlugin>();

    @Inject
    public PaymentProviderPluginRegistry(PaymentConfig config) {
        this.defaultPlugin = config.getDefaultPaymentProvider();
    }

    public void register(PaymentProviderPlugin plugin, String name) {
        pluginsByName.put(name.toLowerCase(), plugin);
    }

    public PaymentProviderPlugin getPlugin(String name) {
        PaymentProviderPlugin plugin = pluginsByName.get(StringUtils.defaultIfEmpty(name, defaultPlugin).toLowerCase());

        if (plugin == null) {
            throw new IllegalArgumentException("No payment provider plugin is configured for " + name);
        }

        return plugin;
    }
}
