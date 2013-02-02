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

import java.util.UUID;

import org.mockito.Mockito;

import com.ning.billing.ObjectType;
import com.ning.billing.mock.glue.MockAccountModule;
import com.ning.billing.mock.glue.MockEntitlementModule;
import com.ning.billing.mock.glue.MockGlobalLockerModule;
import com.ning.billing.mock.glue.MockInvoiceModule;
import com.ning.billing.mock.glue.MockNotificationQueueModule;
import com.ning.billing.payment.TestPaymentHelper;
import com.ning.billing.payment.provider.MockPaymentProviderPluginModule;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.config.PaymentConfig;
import com.ning.billing.util.email.EmailModule;
import com.ning.billing.util.email.templates.TemplateModule;
import com.ning.billing.util.glue.BusModule;
import com.ning.billing.util.glue.BusModule.BusType;
import com.ning.billing.util.glue.CacheModule;
import com.ning.billing.util.glue.CustomFieldModule;
import com.ning.billing.util.glue.NotificationQueueModule;
import com.ning.billing.util.glue.TagStoreModule;
import com.ning.billing.util.svcapi.tag.TagInternalApi;
import com.ning.billing.util.tag.Tag;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertNotNull;

public class TestPaymentModule extends PaymentModule {


    @Override
    protected void installPaymentProviderPlugins(final PaymentConfig config) {
        install(new MockPaymentProviderPluginModule(TestPaymentHelper.PLUGIN_TEST_NAME));
    }

    private void installExternalApis() {
        final TagInternalApi tagUserApi = Mockito.mock(TagInternalApi.class);
        bind(TagInternalApi.class).toInstance(tagUserApi);
        Mockito.when(tagUserApi.getTags(Mockito.<UUID>any(), Mockito.<ObjectType>any(), Mockito.<InternalTenantContext>any())).thenReturn(ImmutableList.<Tag>of());
    }

    @Override
    protected void configure() {
        super.configure();
        install(new BusModule(BusType.MEMORY));
        install(new MockNotificationQueueModule());
        install(new MockInvoiceModule());
        install(new MockAccountModule());
        install(new MockEntitlementModule());
        install(new MockGlobalLockerModule());
        install(new CacheModule());
        installExternalApis();

        bind(TestPaymentHelper.class).asEagerSingleton();
    }
}
