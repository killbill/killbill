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

package org.killbill.billing.server.modules;

import javax.servlet.ServletContext;
import javax.sql.DataSource;

import org.skife.config.ConfigSource;
import org.skife.config.SimplePropertyConfigSource;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.IDBI;

import org.killbill.billing.account.glue.DefaultAccountModule;
import org.killbill.billing.beatrix.glue.BeatrixModule;
import org.killbill.billing.catalog.glue.CatalogModule;
import org.killbill.clock.Clock;
import org.killbill.clock.ClockMock;
import org.killbill.billing.currency.glue.CurrencyModule;
import org.killbill.billing.entitlement.glue.DefaultEntitlementModule;
import org.killbill.billing.invoice.glue.DefaultInvoiceModule;
import org.killbill.billing.jaxrs.resources.AccountResource;
import org.killbill.billing.jaxrs.resources.BundleResource;
import org.killbill.billing.jaxrs.resources.CatalogResource;
import org.killbill.billing.jaxrs.resources.CustomFieldResource;
import org.killbill.billing.jaxrs.resources.ExportResource;
import org.killbill.billing.jaxrs.resources.InvoiceResource;
import org.killbill.billing.jaxrs.resources.PaymentMethodResource;
import org.killbill.billing.jaxrs.resources.PaymentResource;
import org.killbill.billing.jaxrs.resources.PluginResource;
import org.killbill.billing.jaxrs.resources.RefundResource;
import org.killbill.billing.jaxrs.resources.SubscriptionResource;
import org.killbill.billing.jaxrs.resources.TagDefinitionResource;
import org.killbill.billing.jaxrs.resources.TagResource;
import org.killbill.billing.jaxrs.resources.TenantResource;
import org.killbill.billing.jaxrs.resources.TestResource;
import org.killbill.billing.jaxrs.util.KillbillEventHandler;
import org.killbill.billing.junction.glue.DefaultJunctionModule;
import org.killbill.billing.osgi.glue.DefaultOSGIModule;
import org.killbill.billing.overdue.glue.DefaultOverdueModule;
import org.killbill.billing.payment.glue.PaymentModule;
import org.killbill.billing.server.DefaultServerService;
import org.killbill.billing.server.ServerService;
import org.killbill.billing.server.notifications.PushNotificationListener;
import org.killbill.billing.subscription.glue.DefaultSubscriptionModule;
import org.killbill.billing.tenant.glue.TenantModule;
import org.killbill.billing.usage.glue.UsageModule;
import org.killbill.billing.util.email.EmailModule;
import org.killbill.billing.util.email.templates.TemplateModule;
import org.killbill.billing.util.glue.AuditModule;
import org.killbill.billing.util.glue.BusModule;
import org.killbill.billing.util.glue.CacheModule;
import org.killbill.billing.util.glue.CallContextModule;
import org.killbill.billing.util.glue.ClockModule;
import org.killbill.billing.util.glue.CustomFieldModule;
import org.killbill.billing.util.glue.ExportModule;
import org.killbill.billing.util.glue.GlobalLockerModule;
import org.killbill.billing.util.glue.KillBillShiroAopModule;
import org.killbill.billing.util.glue.MetricsModule;
import org.killbill.billing.util.glue.NonEntityDaoModule;
import org.killbill.billing.util.glue.NotificationQueueModule;
import org.killbill.billing.util.glue.RecordIdModule;
import org.killbill.billing.util.glue.SecurityModule;
import org.killbill.billing.util.glue.TagStoreModule;

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
        bind(CustomFieldResource.class).asEagerSingleton();
        bind(TagResource.class).asEagerSingleton();
        bind(TagDefinitionResource.class).asEagerSingleton();
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
