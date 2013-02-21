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

package com.ning.billing.analytics.glue;

import java.util.Properties;

import org.mockito.Mockito;
import org.skife.config.ConfigSource;
import org.skife.config.SimplePropertyConfigSource;

import com.ning.billing.analytics.setup.AnalyticsModule;
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

    protected final ConfigSource configSource;

    public TestAnalyticsModule() {
        final Properties properties = new Properties(System.getProperties());
        // Speed up the bus
        properties.put("killbill.billing.util.persistent.bus.sleep", "10");
        properties.put("killbill.billing.util.persistent.bus.nbThreads", "1");
        configSource = new SimplePropertyConfigSource(properties);

        // Ignore ehcache checks. Unfortunately, ehcache looks at system properties directly...
        System.setProperty("net.sf.ehcache.skipUpdateCheck", "true");
    }

    @Override
    protected void configure() {
        super.configure();

        install(new AuditModule());
        install(new CacheModule());
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
        install(new NotificationQueueModule());
        install(new TagStoreModule());

        bind(InvoiceDao.class).toInstance(Mockito.mock(InvoiceDao.class));
        bind(PaymentDao.class).toInstance(Mockito.mock(PaymentDao.class));
    }
}
