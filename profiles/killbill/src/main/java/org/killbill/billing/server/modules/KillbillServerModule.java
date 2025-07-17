/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2014-2020 The Billing Project, LLC
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

import java.util.Collections;
import java.util.Set;

import javax.servlet.ServletContext;

import org.killbill.billing.account.glue.DefaultAccountModule;
import org.killbill.billing.beatrix.glue.BeatrixModule;
import org.killbill.billing.catalog.glue.CatalogModule;
import org.killbill.billing.currency.glue.CurrencyModule;
import org.killbill.billing.entitlement.glue.DefaultEntitlementModule;
import org.killbill.billing.invoice.glue.DefaultInvoiceModule;
import org.killbill.billing.jaxrs.glue.DefaultJaxrsModule;
import org.killbill.billing.jaxrs.resources.TestResource;
import org.killbill.billing.jaxrs.util.KillbillEventHandler;
import org.killbill.billing.junction.glue.DefaultJunctionModule;
import org.killbill.billing.osgi.api.Healthcheck;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.osgi.api.ServiceDiscoveryRegistry;
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
import org.killbill.billing.util.config.definition.JaxrsConfig;
import org.killbill.billing.util.config.definition.NotificationConfig;
import org.killbill.billing.util.email.templates.TemplateModule;
import org.killbill.billing.util.features.KillbillFeatures;
import org.killbill.billing.util.glue.AuditModule;
import org.killbill.billing.util.glue.BroadcastModule;
import org.killbill.billing.util.glue.CacheModule;
import org.killbill.billing.util.glue.CallContextModule;
import org.killbill.billing.util.glue.ClockModule;
import org.killbill.billing.util.glue.ConfigModule;
import org.killbill.billing.util.glue.CustomFieldModule;
import org.killbill.billing.util.glue.EventModule;
import org.killbill.billing.util.glue.ExportModule;
import org.killbill.billing.util.glue.GlobalLockerModule;
import org.killbill.billing.util.glue.IDBISetup;
import org.killbill.billing.util.glue.KillBillModule;
import org.killbill.billing.util.glue.KillBillShiroAopModule;
import org.killbill.billing.util.glue.KillbillApiAopModule;
import org.killbill.billing.util.glue.NodesModule;
import org.killbill.billing.util.glue.NonEntityDaoModule;
import org.killbill.billing.util.glue.RecordIdModule;
import org.killbill.billing.util.glue.SecurityModule;
import org.killbill.billing.util.glue.TagStoreModule;
import org.killbill.billing.util.optimizer.BusDispatcherOptimizer;
import org.killbill.billing.util.optimizer.BusDispatcherOptimizerNoop;
import org.killbill.billing.util.optimizer.BusDispatcherOptimizerOn;
import org.killbill.billing.util.optimizer.BusOptimizer;
import org.killbill.billing.util.optimizer.BusOptimizerNoop;
import org.killbill.billing.util.optimizer.BusOptimizerOn;
import org.killbill.clock.Clock;
import org.killbill.clock.ClockMock;
import org.killbill.commons.embeddeddb.EmbeddedDB;
import org.skife.config.AugmentedConfigurationObjectFactory;
import org.skife.jdbi.v2.ResultSetMapperFactory;
import org.skife.jdbi.v2.tweak.ArgumentFactory;
import org.skife.jdbi.v2.tweak.ResultSetMapper;
import org.skife.jdbi.v2.tweak.SQLLog;
import org.skife.jdbi.v2.tweak.StatementBuilderFactory;
import org.skife.jdbi.v2.tweak.StatementRewriter;

import ch.qos.logback.classic.helpers.MDCInsertingServletFilter;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.OptionalBinder;
import com.google.inject.name.Names;
import com.google.inject.util.Providers;

public class KillbillServerModule extends KillbillPlatformModule {

    private final KillbillFeatures killbillFeatures;

    public KillbillServerModule(final ServletContext servletContext, final KillbillServerConfig serverConfig, final KillbillConfigSource configSource) {
        super(servletContext, serverConfig, configSource);
        this.killbillFeatures = new KillbillFeatures();
    }

    @Override
    protected void configure() {
        super.configure();

        installKillbillModules();

        configureResources();
        configureFilters();
        configurePushNotification();

        bind(new TypeLiteral<OSGIServiceRegistration<Healthcheck>>() {}).to(DefaultHealthcheckPluginRegistry.class).asEagerSingleton();

        // Needed because changes in KillbillHealthcheck from Guice @Inject(optional=true) to javax @Inject + @Nullable
        OptionalBinder.newOptionalBinder(binder(), new TypeLiteral<Set<ServiceDiscoveryRegistry>>() {}).setDefault().toInstance(Collections.emptySet());

        // Needed because changes in DBIProvider from Guice @Inject(optional=true) to javax @Inject + @Nullable
        OptionalBinder.newOptionalBinder(binder(), new TypeLiteral<Set<ArgumentFactory>>() {}).setDefault().toInstance(Collections.emptySet());
        OptionalBinder.newOptionalBinder(binder(), SQLLog.class).setDefault().toProvider(Providers.of(null));
        OptionalBinder.newOptionalBinder(binder(), StatementBuilderFactory.class).setDefault().toProvider(Providers.of(null));
        OptionalBinder.newOptionalBinder(binder(), StatementRewriter.class).setDefault().toProvider(Providers.of(null));
    }

    @Override
    protected void configureBuses() {
        super.configureBuses();
        if (killbillFeatures.isBusOptimizationOn()) {
            this.bind(BusOptimizer.class).to(BusOptimizerOn.class).asEagerSingleton();
            this.bind(BusDispatcherOptimizer.class).to(BusDispatcherOptimizerOn.class).asEagerSingleton();
        } else {
            this.bind(BusOptimizer.class).to(BusOptimizerNoop.class).asEagerSingleton();
            this.bind(BusDispatcherOptimizer.class).to(BusDispatcherOptimizerNoop.class).asEagerSingleton();
        }
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
        install(new EventModule(configSource));
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
        install(new KillBillShiroAopModule(configSource));

        final AugmentedConfigurationObjectFactory factory = new AugmentedConfigurationObjectFactory(skifeConfigSource);
        final JaxrsConfig jaxrsConfig = factory.build(JaxrsConfig.class);
        install(new KillbillApiAopModule(jaxrsConfig));
        install(new JaxRSAopModule(jaxrsConfig));

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
        bind(KillbillEventHandler.class).asEagerSingleton();
    }

    protected void configureFilters() {
        bind(ResponseCorsFilter.class).asEagerSingleton();
        bind(MDCInsertingServletFilter.class).asEagerSingleton();
    }

    protected void configurePushNotification() {
        final AugmentedConfigurationObjectFactory factory = new AugmentedConfigurationObjectFactory(skifeConfigSource);
        final NotificationConfig notificationConfig = factory.build(NotificationConfig.class);
        bind(NotificationConfig.class).annotatedWith(Names.named(KillBillModule.STATIC_CONFIG)).toInstance(notificationConfig);
        bind(NotificationConfig.class).to(MultiTenantNotificationConfig.class).asEagerSingleton();
        bind(PushNotificationListener.class).asEagerSingleton();
        bind(PushNotificationRetryService.class).asEagerSingleton();
        bind(ServerService.class).to(DefaultServerService.class).asEagerSingleton();
    }
}
