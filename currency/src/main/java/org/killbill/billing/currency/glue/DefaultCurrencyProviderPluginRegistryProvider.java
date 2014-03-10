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

package org.killbill.billing.currency.glue;

import javax.inject.Inject;

import org.killbill.billing.currency.DefaultCurrencyProviderPluginRegistry;
import org.killbill.billing.currency.plugin.api.CurrencyPluginApi;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;

import com.google.inject.Provider;

public class DefaultCurrencyProviderPluginRegistryProvider implements Provider<OSGIServiceRegistration<CurrencyPluginApi>> {


    @Inject
    public DefaultCurrencyProviderPluginRegistryProvider() {
    }

    @Override
    public OSGIServiceRegistration<CurrencyPluginApi> get() {
        final DefaultCurrencyProviderPluginRegistry pluginRegistry = new DefaultCurrencyProviderPluginRegistry();
        return pluginRegistry;
    }
}
