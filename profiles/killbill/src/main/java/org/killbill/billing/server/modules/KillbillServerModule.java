/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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
import org.killbill.billing.jaxrs.glue.DefaultJaxrsModule;
import org.killbill.billing.jaxrs.resources.AccountResource;
import org.killbill.billing.jaxrs.resources.AdminResource;
import org.killbill.billing.jaxrs.resources.BundleResource;
import org.killbill.billing.jaxrs.resources.CatalogResource;
import org.killbill.billing.jaxrs.resources.CreditResource;
import org.killbill.billing.jaxrs.resources.CustomFieldResource;
import org.killbill.billing.jaxrs.resources.ExportResource;
import org.killbill.billing.jaxrs.resources.InvoiceItemResource;
import org.killbill.billing.jaxrs.resources.InvoicePaymentResource;
import org.killbill.billing.jaxrs.resources.InvoiceResource;
import org.killbill.billing.jaxrs.resources.NodesInfoResource;
import org.killbill.billing.jaxrs.resources.OverdueResource;
import org.killbill.billing.jaxrs.resources.PaymentGatewayResource;
import org.killbill.billing.jaxrs.resources.PaymentMethodResource;
import org.killbill.billing.jaxrs.resources.PaymentResource;
import org.killbill.billing.jaxrs.resources.PluginInfoResource;
import org.killbill.billing.jaxrs.resources.PluginResource;
import org.killbill.billing.jaxrs.resources.SecurityResource;
import org.killbill.billing.jaxrs.resources.SubscriptionResource;
import org.killbill.billing.jaxrs.resources.TagDefinitionResource;
import org.killbill.billing.jaxrs.resources.TagResource;
import org.killbill.billing.jaxrs.resources.TenantResource;
import org.killbill.billing.jaxrs.resources.TestResource;
import org.killbill.billing.jaxrs.resources.TransactionResource;
import org.killbill.billing.jaxrs.resources.UsageResource;
import org.killbill.billing.jaxrs.util.KillbillEventHandler;
import org.killbill.billing.junction.glue.DefaultJunctionModule;
import org.killbill.billing.osgi.api.Healthcheck;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.overdue.glue.DefaultOverdueModule;
import org.killbill.billing.payment.glue.PaymentModule;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.server.DefaultServerService;
import org.killbill.billing.server.ServerService;
import org.killbill.billing.server.config.KillbillServerConfig;
import org.killbill.billing.server.config.MultiTenantNotificationConfig;
import org.killbill.billing.server.filters.ResponseCorsFilter;
import org.killbill.billing.server.notifications.PushNotificationListener;
import org.killbill.billing.server.notifications.PushNotificationRetryService;
import org.killbill.billing.subscription.glue.DefaultSubscriptionModule;
import org.killbill.billing.tenant.glue.DefaultTenantModule;
import org.killbill.billing.usage.glue.UsageModule;
import org.killbill.billing.util.config.definition.NotificationConfig;
import org.killbill.billing.util.email.templates.TemplateModule;
import org.killbill.billing.util.glue.AuditModule;
import org.killbill.billing.util.glue.BroadcastModule;
import org.killbill.billing.util.glue.CacheModule;
import org.killbill.billing.util.glue.CallContextModule;
import org.killbill.billing.util.glue.ClockModule;
import org.killbill.billing.util.glue.ConfigModule;
import org.killbill.billing.util.glue.CustomFieldModule;
import org.killbill.billing.util.glue.ExportModule;
import org.killbill.billing.util.glue.GlobalLockerModule;
import org.killbill.billing.util.glue.IDBISetup;
import org.killbill.billing.util.glue.KillBillShiroAopModule;
import org.killbill.billing.util.glue.KillbillApiAopModule;
import org.killbill.billing.util.glue.NodesModule;
import org.killbill.billing.util.glue.NonEntityDaoModule;
import org.killbill.billing.util.glue.RecordIdModule;
import org.killbill.billing.util.glue.SecurityModule;
import org.killbill.billing.util.glue.TagStoreModule;
import org.killbill.clock.Clock;
import org.killbill.clock.ClockMock;
import org.killbill.commons.embeddeddb.EmbeddedDB;
import org.skife.config.ConfigurationObjectFactory;
import org.skife.jdbi.v2.ResultSetMapperFactory;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import ch.qos.logback.classic.helpers.MDCInsertingServletFilter;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;

public class KillbillServerModule extends KillbillPlatformModule {

    public static final String STATIC_CONFIG = "StaticConfig";

    public KillbillServerModule(final ServletContext servletContext, final KillbillServerConfig serverConfig, final KillbillConfigSource configSource) {
        super(servletContext, serverConfig, configSource);
    }

    @Override
    protected void configure() {
        super.configure();

        installKillbillModules();

        configureResources();
        configureFilters();
        configurePushNotification();

        bind(new TypeLiteral<OSGIServiceRegistration<Healthcheck>>() {}).to(DefaultHealthcheckPluginRegistry.class).asEagerSingleton();
    }

    @Override
    protected void configureDao() {
        super.configureDao();

        final Multibinder<ResultSetMapperFactory> resultSetMapperFactorySetBinder = Multibinder.newSetBinder(binder(), ResultSetMapperFactory.class);
        for (final ResultSetMapperFactory resultSetMapperFactory : IDBISetup.mapperFactoriesToRegister()) {
            resultSetMapperFactorySetBinder.addBinding().toInstance(resultSetMapperFactory);
        }

        final Multibinder<ResultSetMapper> resultSetMapperSetBinder = Multibinder.newSetBinder(binder(), ResultSetMapper.class);
        for (final ResultSetMapper resultSetMapper : IDBISetup.mappersToRegister()) {
            resultSetMapperSetBinder.addBinding().toInstance(resultSetMapper);
        }
    }

    @Override
    protected void configureEmbeddedDBs() {
        mainEmbeddedDB = new KillBillEmbeddedDBProvider(daoConfig).get();
        bind(EmbeddedDB.class).toInstance(mainEmbeddedDB);

        // Same database, but different pool: clone the object so the shutdown sequence cleans the pool properly
        shiroEmbeddedDB = new KillBillEmbeddedDBProvider(daoConfig).get();
        bind(EmbeddedDB.class).annotatedWith(Names.named(SHIRO_DATA_SOURCE_ID)).toInstance(shiroEmbeddedDB);

        if (mainRoDataSourceConfig.isEnabled()) {
            mainRoEmbeddedDB = new KillBillEmbeddedDBProvider(mainRoDataSourceConfig).get();
        } else {
            mainRoEmbeddedDB = mainEmbeddedDB;
        }
        bind(EmbeddedDB.class).annotatedWith(Names.named(MAIN_RO_DATA_SOURCE_ID)).toInstance(mainRoEmbeddedDB);
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
        install(new NodesModule(configSource));
        install(new BroadcastModule(configSource));
        install(new BeatrixModule(configSource));
        install(new CacheModule(configSource));
        install(new ConfigModule(configSource));
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
        install(new ExportModule(configSource));
        install(new GlobalLockerModule(configSource));
        install(new KillBillShiroAopModule());
        install(new KillbillApiAopModule());
        install(new JaxRSAopModule());
        install(new KillBillShiroWebModule(servletContext, skifeConfigSource));
        install(new NonEntityDaoModule(configSource));
        install(new PaymentModule(configSource));
        install(new RecordIdModule(configSource));
        install(new SecurityModule(configSource));
        install(new TagStoreModule(configSource));
        install(new TemplateModule(configSource));
        install(new DefaultTenantModule(configSource));
        install(new UsageModule(configSource));
        install(new DefaultJaxrsModule(configSource));
    }

    protected void configureResources() {
        bind(AccountResource.class).asEagerSingleton();
        bind(AdminResource.class).asEagerSingleton();
        bind(BundleResource.class).asEagerSingleton();
        bind(CatalogResource.class).asEagerSingleton();
        bind(CreditResource.class).asEagerSingleton();
        bind(CustomFieldResource.class).asEagerSingleton();
        bind(ExportResource.class).asEagerSingleton();
        bind(InvoicePaymentResource.class).asEagerSingleton();
        bind(InvoiceResource.class).asEagerSingleton();
        bind(NodesInfoResource.class).asEagerSingleton();
        bind(OverdueResource.class).asEagerSingleton();
        bind(PaymentGatewayResource.class).asEagerSingleton();
        bind(PaymentMethodResource.class).asEagerSingleton();
        bind(PaymentResource.class).asEagerSingleton();
        bind(PluginInfoResource.class).asEagerSingleton();
        bind(PluginResource.class).asEagerSingleton();
        bind(SecurityResource.class).asEagerSingleton();
        bind(SubscriptionResource.class).asEagerSingleton();
        bind(TagDefinitionResource.class).asEagerSingleton();
        bind(TagResource.class).asEagerSingleton();
        bind(TenantResource.class).asEagerSingleton();
        bind(TransactionResource.class).asEagerSingleton();
        bind(UsageResource.class).asEagerSingleton();
        bind(InvoiceItemResource.class).asEagerSingleton();

        bind(KillbillEventHandler.class).asEagerSingleton();
    }

    protected void configureFilters() {
        bind(ResponseCorsFilter.class).asEagerSingleton();
        bind(MDCInsertingServletFilter.class).asEagerSingleton();
    }

    protected void configurePushNotification() {
        final ConfigurationObjectFactory factory = new ConfigurationObjectFactory(skifeConfigSource);
        final NotificationConfig notificationConfig = factory.build(NotificationConfig.class);
        bind(NotificationConfig.class).annotatedWith(Names.named(STATIC_CONFIG)).toInstance(notificationConfig);
        bind(NotificationConfig.class).to(MultiTenantNotificationConfig.class).asEagerSingleton();
        bind(PushNotificationListener.class).asEagerSingleton();
        bind(PushNotificationRetryService.class).asEagerSingleton();
        bind(ServerService.class).to(DefaultServerService.class).asEagerSingleton();
    }
}
