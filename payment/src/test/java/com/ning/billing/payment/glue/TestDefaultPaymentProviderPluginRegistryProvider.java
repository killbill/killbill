/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.payment.glue;

import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.util.config.PaymentConfig;
import com.ning.billing.payment.PaymentTestSuite;
import com.ning.billing.payment.provider.ExternalPaymentProviderPlugin;
import com.ning.billing.payment.provider.PaymentProviderPluginRegistry;
import com.ning.billing.util.clock.Clock;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

public class TestDefaultPaymentProviderPluginRegistryProvider extends PaymentTestSuite {

    @Test(groups = "fast")
    public void testInjection() throws Exception {
        final Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(PaymentConfig.class).toInstance(Mockito.mock(PaymentConfig.class));
                bind(Clock.class).toInstance(Mockito.mock(Clock.class));

                bind(PaymentProviderPluginRegistry.class)
                        .toProvider(DefaultPaymentProviderPluginRegistryProvider.class)
                        .asEagerSingleton();
            }
        });

        final PaymentProviderPluginRegistry registry = injector.getInstance(PaymentProviderPluginRegistry.class);
        Assert.assertNotNull(registry.getPlugin(ExternalPaymentProviderPlugin.PLUGIN_NAME));
    }
}
