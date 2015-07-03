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

package org.killbill.billing.entitlement.glue;

import javax.inject.Inject;

import org.killbill.billing.entitlement.plugin.api.EntitlementPluginApi;
import org.killbill.billing.entitlement.provider.DefaultEntitlementProviderPluginRegistry;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;

import com.google.inject.Provider;

public class DefaultEntitlementProviderPluginRegistryProvider implements Provider<OSGIServiceRegistration<EntitlementPluginApi>> {

    @Inject
    public DefaultEntitlementProviderPluginRegistryProvider() {
    }

    @Override
    public OSGIServiceRegistration<EntitlementPluginApi> get() {
        final DefaultEntitlementProviderPluginRegistry pluginRegistry = new DefaultEntitlementProviderPluginRegistry();
        return pluginRegistry;
    }
}
