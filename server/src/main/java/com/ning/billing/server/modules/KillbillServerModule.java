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

package com.ning.billing.server.modules;

import javax.servlet.ServletContext;
import javax.sql.DataSource;

import com.ning.billing.clock.Clock;
import com.ning.billing.clock.ClockMock;
import com.ning.billing.currency.glue.CurrencyModule;
import com.ning.billing.entitlement.glue.DefaultEntitlementModule;
import org.skife.config.ConfigSource;
import org.skife.config.SimplePropertyConfigSource;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.IDBI;

import com.ning.billing.account.glue.DefaultAccountModule;
import com.ning.billing.beatrix.glue.BeatrixModule;
import com.ning.billing.catalog.glue.CatalogModule;
import com.ning.billing.jaxrs.resources.SubscriptionResource;
import com.ning.billing.jaxrs.resources.TestResource;
import com.ning.billing.subscription.glue.DefaultSubscriptionModule;
import com.ning.billing.invoice.glue.DefaultInvoiceModule;
import com.ning.billing.jaxrs.resources.AccountResource;
import com.ning.billing.jaxrs.resources.BundleResource;
import com.ning.billing.jaxrs.resources.CatalogResource;
import com.ning.billing.jaxrs.resources.ExportResource;
import com.ning.billing.jaxrs.resources.InvoiceResource;
import com.ning.billing.jaxrs.resources.PaymentMethodResource;
import com.ning.billing.jaxrs.resources.PaymentResource;
import com.ning.billing.jaxrs.resources.PluginResource;
import com.ning.billing.jaxrs.resources.RefundResource;
import com.ning.billing.jaxrs.resources.TagResource;
import com.ning.billing.jaxrs.resources.TenantResource;
import com.ning.billing.jaxrs.util.KillbillEventHandler;
import com.ning.billing.junction.glue.DefaultJunctionModule;
import com.ning.billing.osgi.glue.DefaultOSGIModule;
import com.ning.billing.overdue.glue.DefaultOverdueModule;
import com.ning.billing.payment.glue.PaymentModule;
import com.ning.billing.server.DefaultServerService;
import com.ning.billing.server.ServerService;
import com.ning.billing.server.notifications.PushNotificationListener;
import com.ning.billing.tenant.glue.TenantModule;
import com.ning.billing.usage.glue.UsageModule;
import com.ning.billing.util.email.EmailModule;
import com.ning.billing.util.email.templates.TemplateModule;
import com.ning.billing.util.glue.AuditModule;
import com.ning.billing.util.glue.BusModule;
import com.ning.billing.util.glue.CacheModule;
import com.ning.billing.util.glue.CallContextModule;
import com.ning.billing.util.glue.ClockModule;
import com.ning.billing.util.glue.CustomFieldModule;
import com.ning.billing.util.glue.ExportModule;
import com.ning.billing.util.glue.GlobalLockerModule;
import com.ning.billing.util.glue.KillBillShiroAopModule;
import com.ning.billing.util.glue.MetricsModule;
import com.ning.billing.util.glue.NonEntityDaoModule;
import com.ning.billing.util.glue.NotificationQueueModule;
import com.ning.billing.util.glue.RecordIdModule;
import com.ning.billing.util.glue.SecurityModule;
import com.ning.billing.util.glue.TagStoreModule;

import com.google.inject.AbstractModule;

public class KillbillServerModule extends AbstractModule {

    protected final ServletContext servletContext;
    private final boolean isTestModeEnabled;

    public KillbillServerModule(final ServletContext servletContext, final boolean testModeEnabled) {
        this.servletContext = servletContext;
        this.isTestModeEnabled = testModeEnabled;
    }

    @Override
    protected void configure() {
        configureDao();
        configureResources();
        installKillbillModules();
        configurePushNotification();
    }

    protected void configurePushNotification() {
        bind(ServerService.class).to(DefaultServerService.class).asEagerSingleton();
        bind(PushNotificationListener.class).asEagerSingleton();
    }

    protected void configureDao() {
        // Load mysql driver if needed
        try {
            Class.forName("com.mysql.jdbc.Driver").newInstance();
        } catch (final Exception ignore) {
        }
        bind(IDBI.class).to(DBI.class).asEagerSingleton();
        bind(DataSource.class).toProvider(DataSourceProvider.class).asEagerSingleton();
        bind(DBI.class).toProvider(DBIProvider.class).asEagerSingleton();
    }

    protected void configureResources() {
        bind(AccountResource.class).asEagerSingleton();
        bind(BundleResource.class).asEagerSingleton();
        bind(SubscriptionResource.class).asEagerSingleton();
        bind(InvoiceResource.class).asEagerSingleton();
        bind(TagResource.class).asEagerSingleton();
        bind(CatalogResource.class).asEagerSingleton();
        bind(PaymentMethodResource.class).asEagerSingleton();
        bind(PaymentResource.class).asEagerSingleton();
        bind(PluginResource.class).asEagerSingleton();
        bind(RefundResource.class).asEagerSingleton();
        bind(TenantResource.class).asEagerSingleton();
        bind(ExportResource.class).asEagerSingleton();
        bind(PluginResource.class).asEagerSingleton();
        bind(TenantResource.class).asEagerSingleton();
        bind(KillbillEventHandler.class).asEagerSingleton();
    }

    protected void installClock() {
        if (isTestModeEnabled) {
            bind(Clock.class).to(ClockMock.class).asEagerSingleton();
            bind(TestResource.class).asEagerSingleton();
        } else {
            install(new ClockModule());
        }
    }

    protected void installKillbillModules() {
        final ConfigSource configSource = new SimplePropertyConfigSource(System.getProperties());

        install(new EmailModule(configSource));
        install(new CacheModule(configSource));
        install(new GlobalLockerModule());
        install(new CustomFieldModule());
        install(new AuditModule());
        install(new CatalogModule(configSource));
        install(new MetricsModule());
        install(new BusModule(configSource));
        install(new NotificationQueueModule(configSource));
        install(new CallContextModule());
        install(new DefaultAccountModule(configSource));
        install(new DefaultInvoiceModule(configSource));
        install(new TemplateModule());
        install(new DefaultSubscriptionModule(configSource));
        install(new DefaultEntitlementModule(configSource));
        install(new PaymentModule(configSource));
        install(new BeatrixModule(configSource));
        install(new DefaultJunctionModule(configSource));
        install(new DefaultOverdueModule(configSource));
        install(new CurrencyModule(configSource));
        install(new TenantModule(configSource));
        install(new ExportModule());
        install(new TagStoreModule());
        install(new NonEntityDaoModule());
        install(new DefaultOSGIModule(configSource));
        install(new UsageModule(configSource));
        install(new RecordIdModule());
        install(new KillBillShiroWebModule(servletContext, configSource));
        install(new KillBillShiroAopModule());
        install(new SecurityModule());
        installClock();
    }
}
