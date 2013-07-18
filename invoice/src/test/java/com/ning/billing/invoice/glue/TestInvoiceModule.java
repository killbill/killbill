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

package com.ning.billing.invoice.glue;

import org.mockito.Mockito;
import org.skife.config.ConfigSource;

import com.ning.billing.catalog.glue.CatalogModule;
import com.ning.billing.invoice.TestInvoiceHelper;
import com.ning.billing.mock.glue.MockGlobalLockerModule;
import com.ning.billing.util.email.EmailModule;
import com.ning.billing.util.email.templates.TemplateModule;
import com.ning.billing.util.glue.CacheModule;
import com.ning.billing.util.glue.CustomFieldModule;
import com.ning.billing.util.glue.NotificationQueueModule;
import com.ning.billing.util.glue.TagStoreModule;
import com.ning.billing.util.svcapi.account.AccountInternalApi;
import com.ning.billing.util.svcapi.subscription.SubscriptionInternalApi;
import com.ning.billing.util.svcapi.junction.BillingInternalApi;


public class TestInvoiceModule extends DefaultInvoiceModule {

    public TestInvoiceModule(final ConfigSource configSource) {
        super(configSource);
    }

    private void installExternalApis() {
        bind(SubscriptionInternalApi.class).toInstance(Mockito.mock(SubscriptionInternalApi.class));
        bind(AccountInternalApi.class).toInstance(Mockito.mock(AccountInternalApi.class));
        bind(BillingInternalApi.class).toInstance(Mockito.mock(BillingInternalApi.class));
    }

    @Override
    protected void configure() {
        super.configure();

        install(new MockGlobalLockerModule());

        install(new CatalogModule(configSource));
        install(new CacheModule(configSource));
        install(new TemplateModule());
        install(new EmailModule(configSource));

        install(new NotificationQueueModule(configSource));
        install(new TagStoreModule());
        install(new CustomFieldModule());

        installExternalApis();

        bind(TestInvoiceHelper.class).asEagerSingleton();
    }
}
