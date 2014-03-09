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

package org.killbill.billing.payment.glue;

import java.util.UUID;

import org.killbill.billing.util.glue.MemoryGlobalLockerModule;
import org.mockito.Mockito;
import org.skife.config.ConfigSource;

import org.killbill.billing.ObjectType;
import org.killbill.billing.mock.glue.MockAccountModule;
import org.killbill.billing.mock.glue.MockSubscriptionModule;
import org.killbill.billing.mock.glue.MockInvoiceModule;
import org.killbill.billing.mock.glue.MockNotificationQueueModule;
import org.killbill.billing.payment.TestPaymentHelper;
import org.killbill.billing.payment.provider.MockPaymentProviderPlugin;
import org.killbill.billing.payment.provider.MockPaymentProviderPluginModule;
import org.killbill.billing.util.bus.InMemoryBusModule;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.clock.Clock;
import org.killbill.billing.util.config.PaymentConfig;
import org.killbill.billing.util.glue.CacheModule;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.tag.Tag;

import com.google.common.collect.ImmutableList;

public class TestPaymentModule extends PaymentModule {

    private final Clock clock;

    public TestPaymentModule(final ConfigSource configSource, final Clock clock) {
        super(configSource);
        this.clock = clock;
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
        install(new MockSubscriptionModule());
        install(new MemoryGlobalLockerModule());
        install(new CacheModule(configSource));
        installExternalApis();

        bind(TestPaymentHelper.class).asEagerSingleton();
    }
}
