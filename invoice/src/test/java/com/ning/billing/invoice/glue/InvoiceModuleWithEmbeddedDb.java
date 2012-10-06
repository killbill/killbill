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

package com.ning.billing.invoice.glue;

import static org.testng.Assert.assertNotNull;

import java.io.IOException;
import java.net.URL;

import org.mockito.Mockito;
import org.skife.jdbi.v2.IDBI;

import com.ning.billing.KillbillTestSuiteWithEmbeddedDB;
import com.ning.billing.catalog.glue.CatalogModule;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.invoice.api.InvoiceNotifier;
import com.ning.billing.invoice.notification.MockNextBillingDateNotifier;
import com.ning.billing.invoice.notification.MockNextBillingDatePoster;
import com.ning.billing.invoice.notification.NextBillingDateNotifier;
import com.ning.billing.invoice.notification.NextBillingDatePoster;
import com.ning.billing.invoice.notification.NullInvoiceNotifier;
import com.ning.billing.mock.glue.MockEntitlementModule;
import com.ning.billing.util.callcontext.CallContextFactory;
import com.ning.billing.util.callcontext.DefaultCallContextFactory;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.DefaultClock;
import com.ning.billing.util.email.templates.TemplateModule;
import com.ning.billing.util.glue.BusModule;
import com.ning.billing.util.glue.CustomFieldModule;
import com.ning.billing.util.glue.GlobalLockerModule;
import com.ning.billing.util.glue.TagStoreModule;
import com.ning.billing.util.notificationq.MockNotificationQueueService;
import com.ning.billing.util.notificationq.NotificationQueueService;
import com.ning.billing.util.svcapi.account.AccountInternalApi;
import com.ning.billing.util.svcapi.junction.BillingInternalApi;

public class InvoiceModuleWithEmbeddedDb extends DefaultInvoiceModule {
    private final MysqlTestingHelper helper = KillbillTestSuiteWithEmbeddedDB.getMysqlTestingHelper();

    private void installNotificationQueue() {
        bind(NotificationQueueService.class).to(MockNotificationQueueService.class).asEagerSingleton();
    }

    @Override
    protected void installNotifiers() {
        bind(NextBillingDateNotifier.class).to(MockNextBillingDateNotifier.class).asEagerSingleton();
        bind(NextBillingDatePoster.class).to(MockNextBillingDatePoster.class).asEagerSingleton();
        bind(InvoiceNotifier.class).to(NullInvoiceNotifier.class).asEagerSingleton();
    }

    @Override
    public void configure() {
        loadSystemPropertiesFromClasspath("/resource.properties");

        final IDBI dbi = helper.getDBI();
        bind(IDBI.class).toInstance(dbi);

        bind(Clock.class).to(DefaultClock.class).asEagerSingleton();
        bind(CallContextFactory.class).to(DefaultCallContextFactory.class).asEagerSingleton();
        install(new CustomFieldModule());
        install(new TagStoreModule());

        installNotificationQueue();
        bind(AccountInternalApi.class).toInstance(Mockito.mock(AccountInternalApi.class));

        final BillingInternalApi billingApi = Mockito.mock(BillingInternalApi.class);
        bind(BillingInternalApi.class).toInstance(billingApi);

        install(new CatalogModule());
        install(new MockEntitlementModule());
        install(new GlobalLockerModule());

        super.configure();

        install(new BusModule());
        install(new TemplateModule());
    }

    private static void loadSystemPropertiesFromClasspath(final String resource) {
        final URL url = InvoiceModuleWithEmbeddedDb.class.getResource(resource);
        assertNotNull(url);
        try {
            System.getProperties().load(url.openStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
