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

package com.ning.billing.invoice;

import java.io.IOException;
import java.net.URL;

import org.mockito.Mockito;
import org.skife.config.ConfigurationObjectFactory;
import org.skife.jdbi.v2.IDBI;

import com.ning.billing.KillbillTestSuiteWithEmbeddedDB;
import com.ning.billing.catalog.glue.CatalogModule;
import com.ning.billing.dbi.DBIProvider;
import com.ning.billing.dbi.DBTestingHelper;
import com.ning.billing.dbi.DbiConfig;
import com.ning.billing.invoice.api.formatters.InvoiceFormatterFactory;
import com.ning.billing.invoice.api.migration.TestDefaultInvoiceMigrationApi;
import com.ning.billing.invoice.glue.DefaultInvoiceModule;
import com.ning.billing.invoice.template.formatters.DefaultInvoiceFormatterFactory;
import com.ning.billing.mock.glue.MockJunctionModule;
import com.ning.billing.util.callcontext.CallContextFactory;
import com.ning.billing.util.callcontext.DefaultCallContextFactory;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.email.EmailModule;
import com.ning.billing.util.email.templates.TemplateModule;
import com.ning.billing.util.globallocker.TestGlobalLockerModule;
import com.ning.billing.util.glue.BusModule;
import com.ning.billing.util.glue.CacheModule;
import com.ning.billing.util.glue.CustomFieldModule;
import com.ning.billing.util.glue.NonEntityDaoModule;
import com.ning.billing.util.glue.NotificationQueueModule;
import com.ning.billing.util.glue.TagStoreModule;
import com.ning.billing.util.svcapi.account.AccountInternalApi;
import com.ning.billing.util.svcapi.entitlement.EntitlementInternalApi;

import com.google.inject.AbstractModule;

import static org.testng.Assert.assertNotNull;

public class MockModule extends AbstractModule {

    @Override
    protected void configure() {
        loadSystemPropertiesFromClasspath("/resource.properties");

        final ClockMock clock = new ClockMock();
        bind(Clock.class).toInstance(clock);
        bind(ClockMock.class).toInstance(clock);
        bind(CallContextFactory.class).to(DefaultCallContextFactory.class).asEagerSingleton();
        install(new TagStoreModule());
        install(new CustomFieldModule());
        install(new CacheModule());
        install(new NonEntityDaoModule());

        final DBTestingHelper helper = KillbillTestSuiteWithEmbeddedDB.getDBTestingHelper();
        if (helper.isUsingLocalInstance()) {
            bind(IDBI.class).toProvider(DBIProvider.class).asEagerSingleton();
            final DbiConfig config = new ConfigurationObjectFactory(System.getProperties()).build(DbiConfig.class);
            bind(DbiConfig.class).toInstance(config);
        } else {
            final IDBI dbi = helper.getDBI();
            bind(IDBI.class).toInstance(dbi);
        }

        /*
        final InternalCallContextFactory internalCallContextFactory = new InternalCallContextFactory(helper.getDBI(), clock, nonEntityDao);
        bind(InternalCallContextFactory.class).toInstance(internalCallContextFactory);
*/
        bind(InvoiceFormatterFactory.class).to(DefaultInvoiceFormatterFactory.class).asEagerSingleton();

        bind(AccountInternalApi.class).toInstance(Mockito.mock(AccountInternalApi.class));
        bind(EntitlementInternalApi.class).toInstance(Mockito.mock(EntitlementInternalApi.class));

        install(new EmailModule());
        install(new TestGlobalLockerModule(helper));
        install(new NotificationQueueModule());
        install(new CatalogModule());
        install(new BusModule());
        installInvoiceModule();
        install(new MockJunctionModule());
        install(new TemplateModule());
    }

    protected void installInvoiceModule() {
        install(new DefaultInvoiceModule());
    }

    private static void loadSystemPropertiesFromClasspath(final String resource) {
        final URL url = TestDefaultInvoiceMigrationApi.class.getResource(resource);
        assertNotNull(url);
        try {
            System.getProperties().load(url.openStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
