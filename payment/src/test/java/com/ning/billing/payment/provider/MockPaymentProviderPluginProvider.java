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

import com.google.inject.Inject;
import com.google.inject.Provider;

import com.ning.billing.osgi.api.OSGIServiceDescriptor;
import com.ning.billing.osgi.api.OSGIServiceRegistration;
import com.ning.billing.payment.plugin.api.PaymentPluginApi;
import com.ning.billing.util.clock.Clock;

public class MockPaymentProviderPluginProvider implements Provider<MockPaymentProviderPlugin> {

    private OSGIServiceRegistration<PaymentPluginApi> registry;
    private final String instanceName;

    private Clock clock;

    public MockPaymentProviderPluginProvider(final String instanceName, Clock clock) {
        this.instanceName = instanceName;
        this.clock = clock;
    }

    @Inject
    public void setPaymentProviderPluginRegistry(final OSGIServiceRegistration<PaymentPluginApi> registry) {
        this.registry = registry;
    }

    @Override
    public MockPaymentProviderPlugin get() {
        final MockPaymentProviderPlugin plugin = new MockPaymentProviderPlugin(clock);

        final OSGIServiceDescriptor desc =  new OSGIServiceDescriptor() {
            @Override
            public String getPluginSymbolicName() {
                return null;
            }
            @Override
            public String getServiceName() {
                return instanceName;
            }
            @Override
            public String getServiceInfo() {
                return null;
            }
            @Override
            public String getServiceType() {
                return null;
            }
        };
        registry.registerService(desc, plugin);
        return plugin;
    }
}
