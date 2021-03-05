/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.billing.payment.glue;

import java.util.UUID;

import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.mock.glue.MockInvoiceModule;
import org.killbill.billing.mock.glue.MockSubscriptionModule;
import org.killbill.billing.mock.glue.MockTenantModule;
import org.killbill.billing.payment.TestPaymentHelper;
import org.killbill.billing.payment.provider.MockPaymentProviderPlugin;
import org.killbill.billing.payment.provider.MockPaymentProviderPluginModule;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.config.definition.PaymentConfig;
import org.killbill.billing.util.glue.CacheModule;
import org.killbill.billing.util.glue.CallContextModule;
import org.killbill.billing.util.glue.ConfigModule;
import org.killbill.billing.util.glue.EventModule;
import org.killbill.billing.util.tag.Tag;
import org.killbill.clock.Clock;
import org.mockito.Mockito;

import com.google.common.collect.ImmutableList;

public class TestPaymentModule extends PaymentModule {

    private final Clock clock;

    public TestPaymentModule(final KillbillConfigSource configSource, final Clock clock) {
        super(configSource);
        this.clock = clock;
    }

    @Override
    protected void installPaymentProviderPlugins(final PaymentConfig config) {
        install(new MockPaymentProviderPluginModule(MockPaymentProviderPlugin.PLUGIN_NAME, clock, configSource));
        // Install a second instance, to test codepaths with multiple plugins (e.g. search)
        install(new MockPaymentProviderPluginModule(MockPaymentProviderPlugin.PLUGIN_NAME + "2", clock, configSource));
    }

    private void installExternalApis() {
        final TagInternalApi tagInternalApi = Mockito.mock(TagInternalApi.class);
        bind(TagInternalApi.class).toInstance(tagInternalApi);
        Mockito.when(tagInternalApi.getTags(Mockito.<UUID>any(), Mockito.<ObjectType>any(), Mockito.<InternalTenantContext>any())).thenReturn(ImmutableList.<Tag>of());

        final TagUserApi tagUserApi = Mockito.mock(TagUserApi.class);
        bind(TagUserApi.class).toInstance(tagUserApi);
    }

    @Override
    protected void configure() {
        super.configure();
        install(new MockInvoiceModule(configSource));
        install(new MockSubscriptionModule(configSource));
        install(new MockTenantModule(configSource));
        install(new CacheModule(configSource));
        install(new ConfigModule(configSource));
        install(new EventModule(configSource));
        install(new CallContextModule(configSource));

        installExternalApis();
        bind(TestPaymentHelper.class).asEagerSingleton();
    }
}
