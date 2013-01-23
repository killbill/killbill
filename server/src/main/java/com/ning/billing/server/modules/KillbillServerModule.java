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

package com.ning.billing.server.modules;

import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.IDBI;

import com.ning.billing.account.glue.DefaultAccountModule;
import com.ning.billing.analytics.setup.AnalyticsModule;
import com.ning.billing.beatrix.glue.BeatrixModule;
import com.ning.billing.catalog.glue.CatalogModule;
import com.ning.billing.entitlement.glue.DefaultEntitlementModule;
import com.ning.billing.invoice.glue.DefaultInvoiceModule;
import com.ning.billing.jaxrs.resources.AccountResource;
import com.ning.billing.jaxrs.resources.BundleResource;
import com.ning.billing.jaxrs.resources.CatalogResource;
import com.ning.billing.jaxrs.resources.InvoiceResource;
import com.ning.billing.jaxrs.resources.MeterResource;
import com.ning.billing.jaxrs.resources.PaymentMethodResource;
import com.ning.billing.jaxrs.resources.PaymentResource;
import com.ning.billing.jaxrs.resources.RefundResource;
import com.ning.billing.jaxrs.resources.SubscriptionResource;
import com.ning.billing.jaxrs.resources.TagResource;
import com.ning.billing.jaxrs.resources.TenantResource;
import com.ning.billing.jaxrs.util.KillbillEventHandler;
import com.ning.billing.junction.glue.DefaultJunctionModule;
import com.ning.billing.meter.glue.MeterModule;
import com.ning.billing.overdue.glue.DefaultOverdueModule;
import com.ning.billing.payment.glue.PaymentModule;
import com.ning.billing.server.DefaultServerService;
import com.ning.billing.server.ServerService;
import com.ning.billing.server.notifications.PushNotificationListener;
import com.ning.billing.tenant.glue.TenantModule;
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
import com.ning.billing.util.glue.NonEntityDaoModule;
import com.ning.billing.util.glue.NotificationQueueModule;
import com.ning.billing.util.glue.TagStoreModule;

import com.google.inject.AbstractModule;

public class KillbillServerModule extends AbstractModule {

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
        bind(RefundResource.class).asEagerSingleton();
        bind(TenantResource.class).asEagerSingleton();
        bind(MeterResource.class).asEagerSingleton();
        bind(KillbillEventHandler.class).asEagerSingleton();
    }

    protected void installClock() {
        install(new ClockModule());
    }

    protected void installKillbillModules() {
        install(new EmailModule());
        install(new CacheModule());
        install(new GlobalLockerModule());
        install(new CustomFieldModule());
        install(new AuditModule());
        install(new CatalogModule());
        install(new BusModule());
        install(new NotificationQueueModule());
        install(new CallContextModule());
        install(new DefaultAccountModule());
        install(new DefaultInvoiceModule());
        install(new TemplateModule());
        install(new DefaultEntitlementModule());
        install(new AnalyticsModule());
        install(new PaymentModule());
        install(new BeatrixModule());
        install(new DefaultJunctionModule());
        install(new DefaultOverdueModule());
        install(new TenantModule());
        install(new ExportModule());
        install(new MeterModule());
        install(new TagStoreModule());
        install(new NonEntityDaoModule());
        installClock();
    }
}
