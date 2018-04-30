/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

import javax.annotation.Nullable;

import org.killbill.billing.GuicyKillbillTestWithEmbeddedDBModule;
import org.killbill.billing.account.glue.DefaultAccountModule;
import org.killbill.billing.api.TestApiListener;
import org.killbill.billing.beatrix.glue.BeatrixModule;
import org.killbill.billing.beatrix.integration.db.TestDBRouterAPI;
import org.killbill.billing.beatrix.integration.overdue.IntegrationTestOverdueModule;
import org.killbill.billing.beatrix.util.AccountChecker;
import org.killbill.billing.beatrix.util.AuditChecker;
import org.killbill.billing.beatrix.util.InvoiceChecker;
import org.killbill.billing.beatrix.util.PaymentChecker;
import org.killbill.billing.beatrix.util.RefundChecker;
import org.killbill.billing.beatrix.util.SubscriptionChecker;
import org.killbill.billing.catalog.glue.CatalogModule;
import org.killbill.billing.currency.glue.CurrencyModule;
import org.killbill.billing.entitlement.glue.DefaultEntitlementModule;
import org.killbill.billing.invoice.generator.DefaultInvoiceGenerator;
import org.killbill.billing.invoice.generator.InvoiceGenerator;
import org.killbill.billing.invoice.glue.DefaultInvoiceModule;
import org.killbill.billing.junction.glue.DefaultJunctionModule;
import org.killbill.billing.payment.glue.PaymentModule;
import org.killbill.billing.payment.provider.MockPaymentProviderPluginModule;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.subscription.glue.DefaultSubscriptionModule;
import org.killbill.billing.tenant.glue.DefaultTenantModule;
import org.killbill.billing.usage.glue.UsageModule;
import org.killbill.billing.util.config.definition.InvoiceConfig;
import org.killbill.billing.util.config.definition.PaymentConfig;
import org.killbill.billing.util.email.templates.TemplateModule;
import org.killbill.billing.util.glue.AuditModule;
import org.killbill.billing.util.glue.BroadcastModule;
import org.killbill.billing.util.glue.CacheModule;
import org.killbill.billing.util.glue.CallContextModule;
import org.killbill.billing.util.glue.ConfigModule;
import org.killbill.billing.util.glue.CustomFieldModule;
import org.killbill.billing.util.glue.ExportModule;
import org.killbill.billing.util.glue.GlobalLockerModule;
import org.killbill.billing.util.glue.KillBillModule;
import org.killbill.billing.util.glue.KillBillShiroModule;
import org.killbill.billing.util.glue.KillbillApiAopModule;
import org.killbill.billing.util.glue.NodesModule;
import org.killbill.billing.util.glue.NonEntityDaoModule;
import org.killbill.billing.util.glue.RecordIdModule;
import org.killbill.billing.util.glue.SecurityModule;
import org.killbill.billing.util.glue.TagStoreModule;

public class BeatrixIntegrationModule extends KillBillModule {

    public static final String NON_OSGI_PLUGIN_NAME = "yoyo";

    // Same name the osgi-payment-test plugin uses to register its service
    public static final String OSGI_PLUGIN_NAME = "osgi-payment-plugin";

    private final InvoiceConfig invoiceConfig;

    public BeatrixIntegrationModule(final KillbillConfigSource configSource) {
        this(configSource, null);
    }

    public BeatrixIntegrationModule(final KillbillConfigSource configSource, @Nullable final InvoiceConfig invoiceConfig) {
        super(configSource);
        this.invoiceConfig = invoiceConfig;
    }

    @Override
    protected void configure() {
        install(new GuicyKillbillTestWithEmbeddedDBModule(true, configSource));
        install(new GlobalLockerModule(configSource));
        install(new CacheModule(configSource));
        install(new ConfigModule(configSource));
        install(new CallContextModule(configSource));
        install(new TagStoreModule(configSource));
        install(new CustomFieldModule(configSource));
        install(new DefaultAccountModule(configSource));
        install(new CatalogModule(configSource));
        install(new DefaultSubscriptionModule(configSource));
        install(new DefaultEntitlementModule(configSource));
        install(new DefaultInvoiceModuleWithSwitchRepairLogic(configSource));
        install(new TemplateModule(configSource));
        install(new PaymentPluginMockModule(configSource));
        install(new DefaultJunctionModule(configSource));
        install(new IntegrationTestOverdueModule(configSource));
        install(new AuditModule(configSource));
        install(new CurrencyModule(configSource));
        install(new DefaultTenantModule(configSource));
        install(new ExportModule(configSource));
        install(new NonEntityDaoModule(configSource));
        install(new RecordIdModule(configSource));
        install(new UsageModule(configSource));
        install(new SecurityModule(configSource));
        install(new NodesModule(configSource));
        install(new BroadcastModule(configSource));
        install(new KillBillShiroModuleOnlyIniRealm(configSource));
        install(new BeatrixModule(configSource));
        install(new KillbillApiAopModule());

        bind(AccountChecker.class).asEagerSingleton();
        bind(SubscriptionChecker.class).asEagerSingleton();
        bind(InvoiceChecker.class).asEagerSingleton();
        bind(PaymentChecker.class).asEagerSingleton();
        bind(RefundChecker.class).asEagerSingleton();
        bind(AuditChecker.class).asEagerSingleton();
        bind(TestApiListener.class).asEagerSingleton();
        bind(TestDBRouterAPI.class).asEagerSingleton();
    }

    private final class DefaultInvoiceModuleWithSwitchRepairLogic extends DefaultInvoiceModule {

        private DefaultInvoiceModuleWithSwitchRepairLogic(final KillbillConfigSource configSource) {
            super(configSource);
        }

        protected void installInvoiceGenerator() {
            bind(InvoiceGenerator.class).to(DefaultInvoiceGenerator.class).asEagerSingleton();
        }

        @Override
        protected void installConfig() {
            if (invoiceConfig != null) {
                super.installConfig(invoiceConfig);
            } else {
                super.installConfig();
            }
        }
    }

    private static final class PaymentPluginMockModule extends PaymentModule {

        private PaymentPluginMockModule(final KillbillConfigSource configSource) {
            super(configSource);
        }

        @Override
        protected void installPaymentProviderPlugins(final PaymentConfig config) {
            install(new MockPaymentProviderPluginModule(NON_OSGI_PLUGIN_NAME, TestIntegrationBase.getClock(), configSource));
        }
    }

    private static class KillBillShiroModuleOnlyIniRealm extends KillBillShiroModule {

        public KillBillShiroModuleOnlyIniRealm(final KillbillConfigSource configSource) {
            super(configSource);
        }
        protected void configureJDBCRealm() {
        }
        protected void configureLDAPRealm() {
        }

    }
}
