/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.analytics;

import org.mockito.Mockito;
import org.skife.jdbi.v2.IDBI;

import com.ning.billing.KillbillTestSuiteWithEmbeddedDB;
import com.ning.billing.account.glue.DefaultAccountModule;
import com.ning.billing.analytics.setup.AnalyticsModule;
import com.ning.billing.catalog.api.Catalog;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.entitlement.glue.DefaultEntitlementModule;
import com.ning.billing.invoice.glue.DefaultInvoiceModule;
import com.ning.billing.junction.glue.DefaultJunctionModule;
import com.ning.billing.payment.glue.PaymentModule;
import com.ning.billing.util.email.EmailModule;
import com.ning.billing.util.email.templates.TemplateModule;
import com.ning.billing.util.globallocker.TestGlobalLockerModule;
import com.ning.billing.util.glue.BusModule;
import com.ning.billing.util.glue.CacheModule;
import com.ning.billing.util.glue.CallContextModule;
import com.ning.billing.util.glue.ClockModule;
import com.ning.billing.util.glue.CustomFieldModule;
import com.ning.billing.util.glue.NonEntityDaoModule;
import com.ning.billing.util.glue.NotificationQueueModule;
import com.ning.billing.util.glue.TagStoreModule;
import com.ning.billing.util.tag.dao.TagDefinitionSqlDao;

public class AnalyticsTestModule extends AnalyticsModule {

    @Override
    protected void configure() {
        super.configure();

        // Need to configure a few more things for the EventBus
        install(new EmailModule());
        install(new TestGlobalLockerModule(KillbillTestSuiteWithEmbeddedDB.getDBTestingHelper()));
        install(new ClockModule());
        install(new CallContextModule());
        install(new CustomFieldModule());
        install(new DefaultAccountModule());
        install(new BusModule());
        install(new DefaultEntitlementModule());
        install(new DefaultInvoiceModule());
        install(new TemplateModule());
        install(new PaymentModule());
        install(new TagStoreModule());
        install(new NotificationQueueModule());
        install(new DefaultJunctionModule());
        install(new CacheModule());
        install(new NonEntityDaoModule());

        // Install the Dao layer
        final IDBI dbi = KillbillTestSuiteWithEmbeddedDB.getDBI();
        bind(IDBI.class).toInstance(dbi);

        bind(TagDefinitionSqlDao.class).toInstance(dbi.onDemand(TagDefinitionSqlDao.class));

        // Install a mock catalog
        final CatalogService catalogService = Mockito.mock(CatalogService.class);
        final Catalog catalog = Mockito.mock(Catalog.class);
        Mockito.when(catalogService.getFullCatalog()).thenReturn(catalog);
        bind(CatalogService.class).toInstance(catalogService);
    }
}
