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

package org.killbill.billing.beatrix.integration;

import java.io.IOException;
import java.net.URL;
import java.util.Set;

import org.killbill.billing.DBTestingHelper;
import org.killbill.billing.GuicyKillbillTestWithEmbeddedDBModule;
import org.killbill.billing.account.api.AccountService;
import org.killbill.billing.account.glue.DefaultAccountModule;
import org.killbill.billing.api.TestApiListener;
import org.killbill.billing.beatrix.DefaultBeatrixService;
import org.killbill.billing.beatrix.glue.BeatrixModule;
import org.killbill.billing.beatrix.integration.overdue.IntegrationTestOverdueModule;
import org.killbill.billing.beatrix.lifecycle.DefaultLifecycle;
import org.killbill.billing.beatrix.lifecycle.Lifecycle;
import org.killbill.billing.beatrix.util.AccountChecker;
import org.killbill.billing.beatrix.util.AuditChecker;
import org.killbill.billing.beatrix.util.InvoiceChecker;
import org.killbill.billing.beatrix.util.PaymentChecker;
import org.killbill.billing.beatrix.util.RefundChecker;
import org.killbill.billing.beatrix.util.SubscriptionChecker;
import org.killbill.billing.catalog.api.CatalogService;
import org.killbill.billing.catalog.glue.CatalogModule;
import org.killbill.billing.currency.glue.CurrencyModule;
import org.killbill.billing.entitlement.EntitlementService;
import org.killbill.billing.entitlement.glue.DefaultEntitlementModule;
import org.killbill.billing.invoice.api.InvoiceService;
import org.killbill.billing.invoice.generator.DefaultInvoiceGenerator;
import org.killbill.billing.invoice.generator.InvoiceGenerator;
import org.killbill.billing.invoice.glue.DefaultInvoiceModule;
import org.killbill.billing.junction.glue.DefaultJunctionModule;
import org.killbill.billing.lifecycle.KillbillService;
import org.killbill.billing.osgi.DefaultOSGIService;
import org.killbill.billing.osgi.glue.DefaultOSGIModule;
import org.killbill.billing.overdue.OverdueService;
import org.killbill.billing.payment.api.PaymentService;
import org.killbill.billing.payment.glue.PaymentModule;
import org.killbill.billing.payment.provider.MockPaymentProviderPluginModule;
import org.killbill.billing.subscription.api.SubscriptionBaseService;
import org.killbill.billing.subscription.glue.DefaultSubscriptionModule;
import org.killbill.billing.tenant.glue.TenantModule;
import org.killbill.billing.usage.glue.UsageModule;
import org.killbill.billing.util.config.PaymentConfig;
import org.killbill.billing.util.email.EmailModule;
import org.killbill.billing.util.email.templates.TemplateModule;
import org.killbill.billing.util.glue.AuditModule;
import org.killbill.billing.util.glue.BusModule;
import org.killbill.billing.util.glue.CacheModule;
import org.killbill.billing.util.glue.CallContextModule;
import org.killbill.billing.util.glue.CustomFieldModule;
import org.killbill.billing.util.glue.ExportModule;
import org.killbill.billing.util.glue.GlobalLockerModule;
import org.killbill.billing.util.glue.MetricsModule;
import org.killbill.billing.util.glue.NonEntityDaoModule;
import org.killbill.billing.util.glue.NotificationQueueModule;
import org.killbill.billing.util.glue.RecordIdModule;
import org.killbill.billing.util.glue.TagStoreModule;
import org.killbill.billing.util.svcsapi.bus.BusService;
import org.skife.config.ConfigSource;

import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;

import static org.testng.Assert.assertNotNull;

public class BeatrixIntegrationModule extends AbstractModule {

    public static final String NON_OSGI_PLUGIN_NAME = "yoyo";

    // Same name the osgi-payment-test plugin uses to register its service
    public static final String OSGI_PLUGIN_NAME = "osgi-payment-plugin";

    private final ConfigSource configSource;

    public BeatrixIntegrationModule(final ConfigSource configSource) {
        this.configSource = configSource;
    }

    @Override
    protected void configure() {
        loadSystemPropertiesFromClasspath("/beatrix.properties");

        install(new GuicyKillbillTestWithEmbeddedDBModule());
        install(new GlobalLockerModule(DBTestingHelper.get().getDBEngine()));
        install(new CacheModule(configSource));
        install(new EmailModule(configSource));
        install(new CallContextModule());
        install(new MetricsModule());
        install(new BusModule(configSource));
        install(new NotificationQueueModule(configSource));
        install(new TagStoreModule());
        install(new CustomFieldModule());
        install(new DefaultAccountModule(configSource));
        install(new CatalogModule(configSource));
        install(new DefaultSubscriptionModule(configSource));
        install(new DefaultEntitlementModule(configSource));
        install(new DefaultInvoiceModuleWithSwitchRepairLogic(configSource));
        install(new TemplateModule());
        install(new PaymentPluginMockModule(configSource));
        install(new DefaultJunctionModule(configSource));
        install(new IntegrationTestOverdueModule(configSource));
        install(new AuditModule());
        install(new CurrencyModule(configSource));
        install(new UsageModule(configSource));
        install(new TenantModule(configSource));
        install(new ExportModule());
        install(new DefaultOSGIModule(configSource));
        install(new NonEntityDaoModule());
        install(new RecordIdModule());
        install(new BeatrixModuleWithSubsetLifecycle(configSource));

        bind(AccountChecker.class).asEagerSingleton();
        bind(SubscriptionChecker.class).asEagerSingleton();
        bind(InvoiceChecker.class).asEagerSingleton();
        bind(PaymentChecker.class).asEagerSingleton();
        bind(RefundChecker.class).asEagerSingleton();
        bind(AuditChecker.class).asEagerSingleton();

        bind(TestApiListener.class).asEagerSingleton();
    }

    private static final class DefaultInvoiceModuleWithSwitchRepairLogic extends DefaultInvoiceModule {

        public DefaultInvoiceModuleWithSwitchRepairLogic(final ConfigSource configSource) {
            super(configSource);
        }

        protected void installInvoiceGenerator() {
            bind(InvoiceGenerator.class).to(DefaultInvoiceGenerator.class).asEagerSingleton();
        }
    }

    private static final class PaymentPluginMockModule extends PaymentModule {

        public PaymentPluginMockModule(final ConfigSource configSource) {
            super(configSource);
        }

        @Override
        protected void installPaymentProviderPlugins(final PaymentConfig config) {
            install(new MockPaymentProviderPluginModule(NON_OSGI_PLUGIN_NAME, TestIntegrationBase.getClock()));
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
                    .add(injector.getInstance(SubscriptionBaseService.class))
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

    private static final class BeatrixModuleWithSubsetLifecycle extends BeatrixModule {

        public BeatrixModuleWithSubsetLifecycle(final ConfigSource configSource) {
            super(configSource);
        }

        @Override
        protected void installLifecycle() {
            bind(Lifecycle.class).to(SubsetDefaultLifecycle.class).asEagerSingleton();
        }
    }
}
