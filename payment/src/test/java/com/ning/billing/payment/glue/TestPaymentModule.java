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

package com.ning.billing.payment.glue;

import java.io.IOException;
import java.net.URL;
import java.util.Properties;
import java.util.UUID;

import org.mockito.Mockito;
import org.skife.config.ConfigSource;
import org.skife.config.SimplePropertyConfigSource;
import org.testng.Assert;

import com.ning.billing.ObjectType;
import com.ning.billing.mock.glue.MockAccountModule;
import com.ning.billing.mock.glue.MockEntitlementModule;
import com.ning.billing.mock.glue.MockGlobalLockerModule;
import com.ning.billing.mock.glue.MockInvoiceModule;
import com.ning.billing.mock.glue.MockNotificationQueueModule;
import com.ning.billing.payment.PaymentTestSuiteNoDB;
import com.ning.billing.payment.TestPaymentHelper;
import com.ning.billing.payment.provider.MockPaymentProviderPlugin;
import com.ning.billing.payment.provider.MockPaymentProviderPluginModule;
import com.ning.billing.util.bus.InMemoryBusModule;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.config.PaymentConfig;
import com.ning.billing.util.glue.CacheModule;
import com.ning.billing.util.svcapi.tag.TagInternalApi;
import com.ning.billing.util.tag.Tag;

import com.google.common.collect.ImmutableList;

public class TestPaymentModule extends PaymentModule {

    protected final ConfigSource configSource;

    private final Clock clock;

    public TestPaymentModule(final ConfigSource configSource, final Clock clock) {
        super(configSource);
        this.clock = clock;
        this.configSource = loadSystemPropertiesFromClasspath("/resource.properties");
    }

    private ConfigSource loadSystemPropertiesFromClasspath(final String resource) {
        final URL url = PaymentTestSuiteNoDB.class.getResource(resource);
        Assert.assertNotNull(url);

        try {
            final Properties properties = System.getProperties();
            properties.load(url.openStream());

            properties.setProperty("killbill.payment.provider.default", MockPaymentProviderPlugin.PLUGIN_NAME);
            properties.setProperty("killbill.payment.engine.events.off", "false");

            return new SimplePropertyConfigSource(properties);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void installPaymentProviderPlugins(final PaymentConfig config) {
        install(new MockPaymentProviderPluginModule(MockPaymentProviderPlugin.PLUGIN_NAME, clock));
    }

    private void installExternalApis() {
        final TagInternalApi tagUserApi = Mockito.mock(TagInternalApi.class);
        bind(TagInternalApi.class).toInstance(tagUserApi);
        Mockito.when(tagUserApi.getTags(Mockito.<UUID>any(), Mockito.<ObjectType>any(), Mockito.<InternalTenantContext>any())).thenReturn(ImmutableList.<Tag>of());
    }

    @Override
    protected void configure() {
        super.configure();
        install(new InMemoryBusModule(configSource));
        install(new MockNotificationQueueModule(configSource));
        install(new MockInvoiceModule());
        install(new MockAccountModule());
        install(new MockEntitlementModule());
        install(new MockGlobalLockerModule());
        install(new CacheModule(configSource));
        installExternalApis();

        bind(TestPaymentHelper.class).asEagerSingleton();
    }
}
