/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.beatrix.integration;

import java.io.IOException;
import java.net.URL;
import java.util.Set;

import org.skife.config.ConfigurationObjectFactory;
import org.skife.jdbi.v2.IDBI;

import com.ning.billing.KillbillTestSuiteWithEmbeddedDB;
import com.ning.billing.account.api.AccountService;
import com.ning.billing.account.glue.DefaultAccountModule;
import com.ning.billing.analytics.setup.AnalyticsModule;
import com.ning.billing.beatrix.DefaultBeatrixService;
import com.ning.billing.beatrix.bus.api.ExternalBus;
import com.ning.billing.beatrix.extbus.BeatrixListener;
import com.ning.billing.beatrix.extbus.PersistentExternalBus;
import com.ning.billing.beatrix.integration.overdue.IntegrationTestOverdueModule;
import com.ning.billing.beatrix.lifecycle.DefaultLifecycle;
import com.ning.billing.beatrix.lifecycle.Lifecycle;
import com.ning.billing.beatrix.util.AccountChecker;
import com.ning.billing.beatrix.util.AuditChecker;
import com.ning.billing.beatrix.util.EntitlementChecker;
import com.ning.billing.beatrix.util.InvoiceChecker;
import com.ning.billing.beatrix.util.PaymentChecker;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.glue.CatalogModule;
import com.ning.billing.dbi.DBIProvider;
import com.ning.billing.dbi.DBTestingHelper;
import com.ning.billing.dbi.DbiConfig;
import com.ning.billing.entitlement.api.EntitlementService;
import com.ning.billing.entitlement.glue.DefaultEntitlementModule;
import com.ning.billing.invoice.api.InvoiceService;
import com.ning.billing.invoice.glue.DefaultInvoiceModule;
import com.ning.billing.junction.glue.DefaultJunctionModule;
import com.ning.billing.lifecycle.KillbillService;
import com.ning.billing.meter.glue.MeterModule;
import com.ning.billing.osgi.DefaultOSGIService;
import com.ning.billing.osgi.glue.DefaultOSGIModule;
import com.ning.billing.overdue.OverdueService;
import com.ning.billing.payment.api.PaymentService;
import com.ning.billing.payment.glue.PaymentModule;
import com.ning.billing.payment.provider.MockPaymentProviderPluginModule;
import com.ning.billing.tenant.glue.TenantModule;
import com.ning.billing.usage.glue.UsageModule;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.config.PaymentConfig;
import com.ning.billing.util.email.EmailModule;
import com.ning.billing.util.email.templates.TemplateModule;
import com.ning.billing.util.globallocker.TestGlobalLockerModule;
import com.ning.billing.util.glue.AuditModule;
import com.ning.billing.util.glue.BusModule;
import com.ning.billing.util.glue.CacheModule;
import com.ning.billing.util.glue.CallContextModule;
import com.ning.billing.util.glue.CustomFieldModule;
import com.ning.billing.util.glue.ExportModule;
import com.ning.billing.util.glue.NonEntityDaoModule;
import com.ning.billing.util.glue.NotificationQueueModule;
import com.ning.billing.util.glue.TagStoreModule;
import com.ning.billing.util.svcsapi.bus.BusService;

import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;

import static org.testng.Assert.assertNotNull;

public class BeatrixIntegrationModule extends AbstractModule {

    public static final String PLUGIN_NAME = "yoyo";

    @Override
    protected void configure() {

        loadSystemPropertiesFromClasspath("/resource.properties");

        bind(Clock.class).to(ClockMock.class).asEagerSingleton();
        bind(ClockMock.class).asEagerSingleton();
        bind(Lifecycle.class).to(SubsetDefaultLifecycle.class).asEagerSingleton();

        final DBTestingHelper helper = KillbillTestSuiteWithEmbeddedDB.getDBTestingHelper();
        final IDBI dbi;
        if (helper.isUsingLocalInstance()) {
            final DbiConfig config = new ConfigurationObjectFactory(System.getProperties()).build(DbiConfig.class);
            final DBIProvider provider = new DBIProvider(config);
            dbi = provider.get();
        } else {
            dbi = helper.getDBI();
        }
        bind(IDBI.class).toInstance(dbi);

        install(new CacheModule());
        install(new EmailModule());
        install(new CallContextModule());
        install(new TestGlobalLockerModule(helper));
        install(new BusModule());
        install(new NotificationQueueModule());
        install(new TagStoreModule());
        install(new CustomFieldModule());
        install(new DefaultAccountModule());
        install(new AnalyticsModule());
        install(new CatalogModule());
        install(new DefaultEntitlementModule());
        install(new DefaultInvoiceModule());
        install(new TemplateModule());
        install(new PaymentPluginMockModule());
        install(new DefaultJunctionModule());
        install(new IntegrationTestOverdueModule());
        install(new AuditModule());
        install(new MeterModule());
        install(new UsageModule());
        install(new TenantModule());
        install(new ExportModule());
        install(new DefaultOSGIModule());
        install(new NonEntityDaoModule());

        bind(AccountChecker.class).asEagerSingleton();
        bind(EntitlementChecker.class).asEagerSingleton();
        bind(InvoiceChecker.class).asEagerSingleton();
        bind(PaymentChecker.class).asEagerSingleton();
        bind(AuditChecker.class).asEagerSingleton();

        installPublicBus();
    }

    private void installPublicBus() {
        bind(ExternalBus.class).to(PersistentExternalBus.class).asEagerSingleton();
        bind(BeatrixListener.class).asEagerSingleton();
        bind(DefaultBeatrixService.class).asEagerSingleton();
    }

    private static final class PaymentPluginMockModule extends PaymentModule {

        @Override
        protected void installPaymentProviderPlugins(final PaymentConfig config) {
            install(new MockPaymentProviderPluginModule(PLUGIN_NAME));
        }
    }

    private static void loadSystemPropertiesFromClasspath(final String resource) {
        final URL url = TestIntegration.class.getResource(resource);
        assertNotNull(url);
        try {
            System.getProperties().load(url.openStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final class SubsetDefaultLifecycle extends DefaultLifecycle {

        @Inject
        public SubsetDefaultLifecycle(final Injector injector) {
            super(injector);
        }

        @Override
        protected Set<? extends KillbillService> findServices() {
            final ImmutableSet<? extends KillbillService> services = new ImmutableSet.Builder<KillbillService>()
                    .add(injector.getInstance(AccountService.class))
                    .add(injector.getInstance(BusService.class))
                    .add(injector.getInstance(CatalogService.class))
                    .add(injector.getInstance(EntitlementService.class))
                    .add(injector.getInstance(InvoiceService.class))
                    .add(injector.getInstance(PaymentService.class))
                    .add(injector.getInstance(OverdueService.class))
                    .add(injector.getInstance(DefaultBeatrixService.class))
                    .add(injector.getInstance(DefaultOSGIService.class))
                    .build();
            return services;
        }
    }
}
