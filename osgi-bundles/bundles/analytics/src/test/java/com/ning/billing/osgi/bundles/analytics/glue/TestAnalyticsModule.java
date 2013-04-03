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

package com.ning.billing.osgi.bundles.analytics.glue;

import org.mockito.Mockito;
import org.skife.config.ConfigSource;

import com.ning.billing.catalog.MockCatalogModule;
import com.ning.billing.invoice.dao.InvoiceDao;
import com.ning.billing.mock.glue.MockAccountModule;
import com.ning.billing.mock.glue.MockEntitlementModule;
import com.ning.billing.mock.glue.MockGlobalLockerModule;
import com.ning.billing.mock.glue.MockInvoiceModule;
import com.ning.billing.mock.glue.MockJunctionModule;
import com.ning.billing.mock.glue.MockOverdueModule;
import com.ning.billing.mock.glue.MockPaymentModule;
import com.ning.billing.payment.dao.PaymentDao;
import com.ning.billing.util.glue.AuditModule;
import com.ning.billing.util.glue.CacheModule;
import com.ning.billing.util.glue.CallContextModule;
import com.ning.billing.util.glue.CustomFieldModule;
import com.ning.billing.util.glue.NotificationQueueModule;
import com.ning.billing.util.glue.TagStoreModule;

public class TestAnalyticsModule extends AnalyticsModule {

    public TestAnalyticsModule(final ConfigSource configSource) {
        super(configSource);
    }

    @Override
    protected void configure() {
        super.configure();

        install(new AuditModule());
        install(new CacheModule(configSource));
        install(new CallContextModule());
        install(new CustomFieldModule());
        install(new MockAccountModule());
        install(new MockCatalogModule());
        install(new MockEntitlementModule());
        install(new MockInvoiceModule());
        install(new MockJunctionModule());
        install(new MockOverdueModule());
        install(new MockPaymentModule());
        install(new MockGlobalLockerModule());
        install(new NotificationQueueModule(configSource));
        install(new TagStoreModule());

        bind(InvoiceDao.class).toInstance(Mockito.mock(InvoiceDao.class));
        bind(PaymentDao.class).toInstance(Mockito.mock(PaymentDao.class));
    }
}
