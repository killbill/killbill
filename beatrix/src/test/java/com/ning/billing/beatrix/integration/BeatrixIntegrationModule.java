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

package com.ning.billing.beatrix.integration;

import java.io.IOException;
import java.net.URL;
import java.util.Set;

import com.ning.billing.currency.glue.CurrencyModule;
import com.ning.billing.entitlement.glue.DefaultEntitlementModule;
import org.skife.config.ConfigSource;

import com.ning.billing.GuicyKillbillTestWithEmbeddedDBModule;
import com.ning.billing.account.api.AccountService;
import com.ning.billing.account.glue.DefaultAccountModule;
import com.ning.billing.beatrix.DefaultBeatrixService;
import com.ning.billing.beatrix.glue.BeatrixModule;
import com.ning.billing.beatrix.integration.overdue.IntegrationTestOverdueModule;
import com.ning.billing.beatrix.lifecycle.DefaultLifecycle;
import com.ning.billing.beatrix.lifecycle.Lifecycle;
import com.ning.billing.beatrix.util.AccountChecker;
import com.ning.billing.beatrix.util.AuditChecker;
import com.ning.billing.beatrix.util.SubscriptionChecker;
import com.ning.billing.beatrix.util.InvoiceChecker;
import com.ning.billing.beatrix.util.PaymentChecker;
import com.ning.billing.beatrix.util.RefundChecker;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.glue.CatalogModule;
import com.ning.billing.subscription.api.SubscriptionBaseService;
import com.ning.billing.invoice.api.InvoiceService;
import com.ning.billing.invoice.generator.DefaultInvoiceGeneratorWithSwitchRepairLogic;
import com.ning.billing.invoice.generator.InvoiceGenerator;
import com.ning.billing.invoice.glue.DefaultInvoiceModule;
import com.ning.billing.junction.glue.DefaultJunctionModule;
import com.ning.billing.lifecycle.KillbillService;
import com.ning.billing.osgi.DefaultOSGIService;
import com.ning.billing.osgi.glue.DefaultOSGIModule;
import com.ning.billing.overdue.OverdueService;
import com.ning.billing.payment.api.PaymentService;
import com.ning.billing.payment.glue.PaymentModule;
import com.ning.billing.payment.provider.MockPaymentProviderPluginModule;
import com.ning.billing.subscription.glue.DefaultSubscriptionModule;
import com.ning.billing.tenant.glue.TenantModule;
import com.ning.billing.usage.glue.UsageModule;
import com.ning.billing.util.config.PaymentConfig;
import com.ning.billing.util.email.EmailModule;
import com.ning.billing.util.email.templates.TemplateModule;
import com.ning.billing.util.glue.AuditModule;
import com.ning.billing.util.glue.BusModule;
import com.ning.billing.util.glue.CacheModule;
import com.ning.billing.util.glue.CallContextModule;
import com.ning.billing.util.glue.CustomFieldModule;
import com.ning.billing.util.glue.ExportModule;
import com.ning.billing.util.glue.GlobalLockerModule;
import com.ning.billing.util.glue.MetricsModule;
import com.ning.billing.util.glue.NonEntityDaoModule;
import com.ning.billing.util.glue.NotificationQueueModule;
import com.ning.billing.util.glue.RecordIdModule;
import com.ning.billing.util.glue.TagStoreModule;
import com.ning.billing.util.svcsapi.bus.BusService;

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

        install(new GlobalLockerModule());
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
    }


    private static final class DefaultInvoiceModuleWithSwitchRepairLogic extends DefaultInvoiceModule {

        public DefaultInvoiceModuleWithSwitchRepairLogic(final ConfigSource configSource) {
            super(configSource);
        }

        protected void installInvoiceGenerator() {
            bind(InvoiceGenerator.class).to(DefaultInvoiceGeneratorWithSwitchRepairLogic.class).asEagerSingleton();
            bind(DefaultInvoiceGeneratorWithSwitchRepairLogic.class).asEagerSingleton();
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
