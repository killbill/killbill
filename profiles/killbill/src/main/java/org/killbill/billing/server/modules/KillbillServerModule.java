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

package org.killbill.billing.server.modules;

import javax.servlet.ServletContext;

import org.killbill.billing.account.glue.DefaultAccountModule;
import org.killbill.billing.beatrix.glue.BeatrixModule;
import org.killbill.billing.catalog.glue.CatalogModule;
import org.killbill.billing.currency.glue.CurrencyModule;
import org.killbill.billing.entitlement.glue.DefaultEntitlementModule;
import org.killbill.billing.invoice.glue.DefaultInvoiceModule;
import org.killbill.billing.jaxrs.resources.AccountResource;
import org.killbill.billing.jaxrs.resources.BundleResource;
import org.killbill.billing.jaxrs.resources.CatalogResource;
import org.killbill.billing.jaxrs.resources.CustomFieldResource;
import org.killbill.billing.jaxrs.resources.ExportResource;
import org.killbill.billing.jaxrs.resources.InvoicePaymentResource;
import org.killbill.billing.jaxrs.resources.InvoiceResource;
import org.killbill.billing.jaxrs.resources.PaymentMethodResource;
import org.killbill.billing.jaxrs.resources.PaymentResource;
import org.killbill.billing.jaxrs.resources.PluginResource;
import org.killbill.billing.jaxrs.resources.SubscriptionResource;
import org.killbill.billing.jaxrs.resources.TagDefinitionResource;
import org.killbill.billing.jaxrs.resources.TagResource;
import org.killbill.billing.jaxrs.resources.TenantResource;
import org.killbill.billing.jaxrs.resources.TestResource;
import org.killbill.billing.jaxrs.resources.TransactionResource;
import org.killbill.billing.jaxrs.util.KillbillEventHandler;
import org.killbill.billing.junction.glue.DefaultJunctionModule;
import org.killbill.billing.overdue.glue.DefaultOverdueModule;
import org.killbill.billing.payment.glue.PaymentModule;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.server.DefaultServerService;
import org.killbill.billing.server.ServerService;
import org.killbill.billing.server.config.KillbillServerConfig;
import org.killbill.billing.server.notifications.PushNotificationListener;
import org.killbill.billing.subscription.glue.DefaultSubscriptionModule;
import org.killbill.billing.tenant.glue.TenantModule;
import org.killbill.billing.usage.glue.UsageModule;
import org.killbill.billing.util.dao.AuditLogModelDaoMapper;
import org.killbill.billing.util.dao.RecordIdIdMappingsMapper;
import org.killbill.billing.util.email.EmailModule;
import org.killbill.billing.util.email.templates.TemplateModule;
import org.killbill.billing.util.glue.AuditModule;
import org.killbill.billing.util.glue.CacheModule;
import org.killbill.billing.util.glue.CallContextModule;
import org.killbill.billing.util.glue.ClockModule;
import org.killbill.billing.util.glue.CustomFieldModule;
import org.killbill.billing.util.glue.ExportModule;
import org.killbill.billing.util.glue.GlobalLockerModule;
import org.killbill.billing.util.glue.KillBillShiroAopModule;
import org.killbill.billing.util.glue.KillbillApiAopModule;
import org.killbill.billing.util.glue.NonEntityDaoModule;
import org.killbill.billing.util.glue.RecordIdModule;
import org.killbill.billing.util.glue.SecurityModule;
import org.killbill.billing.util.glue.TagStoreModule;
import org.killbill.clock.Clock;
import org.killbill.clock.ClockMock;
import org.killbill.commons.embeddeddb.EmbeddedDB;

public class KillbillServerModule extends KillbillPlatformModule {

    public KillbillServerModule(final ServletContext servletContext, final KillbillServerConfig serverConfig, final KillbillConfigSource configSource) {
        super(servletContext, serverConfig, configSource);
    }

    @Override
    protected void configure() {
        super.configure();

        installKillbillModules();

        configureResources();
        configurePushNotification();
    }

    @Override
    protected void configureDao() {
        super.configureDao();

        dbi.registerMapper(new AuditLogModelDaoMapper());
        dbi.registerMapper(new RecordIdIdMappingsMapper());
        queueDbi.registerMapper(new AuditLogModelDaoMapper());
        queueDbi.registerMapper(new RecordIdIdMappingsMapper());
    }

    @Override
    protected void configureEmbeddedDB() {
        final EmbeddedDBProvider embeddedDBProvider = new KillBillEmbeddedDBProvider(daoConfig);
        embeddedDB = embeddedDBProvider.get();
        bind(EmbeddedDB.class).toInstance(embeddedDB);
    }

    @Override
    protected void configureClock() {
        if (serverConfig.isTestModeEnabled()) {
            bind(Clock.class).to(ClockMock.class).asEagerSingleton();
            bind(TestResource.class).asEagerSingleton();
        } else {
            install(new ClockModule(configSource));
        }
    }

    protected void installKillbillModules() {
        install(new AuditModule(configSource));
        install(new BeatrixModule(configSource));
        install(new CacheModule(configSource));
        install(new CallContextModule(configSource));
        install(new CatalogModule(configSource));
        install(new CurrencyModule(configSource));
        install(new CustomFieldModule(configSource));
        install(new DefaultAccountModule(configSource));
        install(new DefaultEntitlementModule(configSource));
        install(new DefaultInvoiceModule(configSource));
        install(new DefaultJunctionModule(configSource));
        install(new DefaultOverdueModule(configSource));
        install(new DefaultSubscriptionModule(configSource));
        install(new EmailModule(configSource));
        install(new ExportModule(configSource));
        install(new GlobalLockerModule(embeddedDB.getDBEngine(), configSource));
        install(new KillBillShiroAopModule());
        install(new KillbillApiAopModule());
        install(new KillBillShiroWebModule(servletContext, skifeConfigSource));
        install(new NonEntityDaoModule(configSource));
        install(new PaymentModule(configSource));
        install(new RecordIdModule(configSource));
        install(new SecurityModule(configSource));
        install(new TagStoreModule(configSource));
        install(new TemplateModule(configSource));
        install(new TenantModule(configSource));
        install(new UsageModule(configSource));
    }

    protected void configureResources() {
        bind(AccountResource.class).asEagerSingleton();
        bind(BundleResource.class).asEagerSingleton();
        bind(CatalogResource.class).asEagerSingleton();
        bind(CustomFieldResource.class).asEagerSingleton();
        bind(ExportResource.class).asEagerSingleton();
        bind(InvoiceResource.class).asEagerSingleton();
        bind(KillbillEventHandler.class).asEagerSingleton();
        bind(PaymentMethodResource.class).asEagerSingleton();
        bind(InvoicePaymentResource.class).asEagerSingleton();
        bind(PaymentResource.class).asEagerSingleton();
        bind(TransactionResource.class).asEagerSingleton();
        bind(PluginResource.class).asEagerSingleton();
        bind(PluginResource.class).asEagerSingleton();
        bind(SubscriptionResource.class).asEagerSingleton();
        bind(TagDefinitionResource.class).asEagerSingleton();
        bind(TagResource.class).asEagerSingleton();
        bind(TenantResource.class).asEagerSingleton();
    }

    protected void configurePushNotification() {
        bind(ServerService.class).to(DefaultServerService.class).asEagerSingleton();
        bind(PushNotificationListener.class).asEagerSingleton();
    }
}
