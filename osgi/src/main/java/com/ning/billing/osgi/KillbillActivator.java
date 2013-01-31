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

package com.ning.billing.osgi;

import javax.inject.Inject;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.analytics.api.sanity.AnalyticsSanityApi;
import com.ning.billing.analytics.api.user.AnalyticsUserApi;
import com.ning.billing.beatrix.bus.api.ExternalBus;
import com.ning.billing.catalog.api.CatalogUserApi;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApi;
import com.ning.billing.entitlement.api.timeline.EntitlementTimelineApi;
import com.ning.billing.entitlement.api.transfer.EntitlementTransferApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.invoice.api.InvoiceMigrationApi;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.meter.api.MeterUserApi;
import com.ning.billing.osgi.api.config.PluginConfigServiceApi;
import com.ning.billing.overdue.OverdueUserApi;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.tenant.api.TenantUserApi;
import com.ning.billing.usage.api.UsageUserApi;
import com.ning.billing.util.api.AuditUserApi;
import com.ning.billing.util.api.CustomFieldUserApi;
import com.ning.billing.util.api.ExportUserApi;
import com.ning.billing.util.api.TagUserApi;

public class KillbillActivator implements BundleActivator {

    private final AccountUserApi accountUserApi;
    private final AnalyticsSanityApi analyticsSanityApi;
    private final AnalyticsUserApi analyticsUserApi;
    private final CatalogUserApi catalogUserApi;
    private final EntitlementMigrationApi entitlementMigrationApi;
    private final EntitlementTimelineApi entitlementTimelineApi;
    private final EntitlementTransferApi entitlementTransferApi;
    private final EntitlementUserApi entitlementUserApi;
    private final InvoiceMigrationApi invoiceMigrationApi;
    private final InvoicePaymentApi invoicePaymentApi;
    private final InvoiceUserApi invoiceUserApi;
    private final MeterUserApi meterUserApi;
    private final OverdueUserApi overdueUserApi;
    private final PaymentApi paymentApi;
    private final TenantUserApi tenantUserApi;
    private final UsageUserApi usageUserApi;
    private final AuditUserApi auditUserApi;
    private final CustomFieldUserApi customFieldUserApi;
    private final ExportUserApi exportUserApi;
    private final TagUserApi tagUserApi;

    private final ExternalBus externalBus;
    private final PluginConfigServiceApi configServiceApi;

    private volatile ServiceRegistration accountUserApiRegistration = null;
    private volatile ServiceRegistration analyticsSanityApiRegistration = null;
    private volatile ServiceRegistration analyticsUserApiRegistration = null;
    private volatile ServiceRegistration catalogUserApiRegistration = null;
    private volatile ServiceRegistration entitlementMigrationApiRegistration = null;
    private volatile ServiceRegistration entitlementTimelineApiRegistration = null;
    private volatile ServiceRegistration entitlementTransferApiRegistration = null;
    private volatile ServiceRegistration entitlementUserApiRegistration = null;
    private volatile ServiceRegistration invoiceMigrationApiRegistration = null;
    private volatile ServiceRegistration invoicePaymentApiRegistration = null;
    private volatile ServiceRegistration invoiceUserApiRegistration = null;
    private volatile ServiceRegistration meterUserApiRegistration = null;
    private volatile ServiceRegistration overdueUserApiRegistration = null;
    private volatile ServiceRegistration paymentApiRegistration = null;
    private volatile ServiceRegistration tenantUserApiRegistration = null;
    private volatile ServiceRegistration usageUserApiRegistration = null;
    private volatile ServiceRegistration auditUserApiRegistration = null;
    private volatile ServiceRegistration customFieldUserApiRegistration = null;
    private volatile ServiceRegistration exportUserApiRegistration = null;
    private volatile ServiceRegistration tagUserApiRegistration = null;

    private volatile ServiceRegistration externalBusRegistration = null;
    private volatile ServiceRegistration configServiceApiRegistration = null;

    @Inject
    public KillbillActivator(final AccountUserApi accountUserApi,
                             final AnalyticsSanityApi analyticsSanityApi,
                             final AnalyticsUserApi analyticsUserApi,
                             final CatalogUserApi catalogUserApi,
                             final EntitlementMigrationApi entitlementMigrationApi,
                             final EntitlementTimelineApi entitlementTimelineApi,
                             final EntitlementTransferApi entitlementTransferApi,
                             final EntitlementUserApi entitlementUserApi,
                             final InvoiceMigrationApi invoiceMigrationApi,
                             final InvoicePaymentApi invoicePaymentApi,
                             final InvoiceUserApi invoiceUserApi,
                             final MeterUserApi meterUserApi,
                             final OverdueUserApi overdueUserApi,
                             final PaymentApi paymentApi,
                             final TenantUserApi tenantUserApi,
                             final UsageUserApi usageUserApi,
                             final AuditUserApi auditUserApi,
                             final CustomFieldUserApi customFieldUserApi,
                             final ExportUserApi exportUserApi,
                             final TagUserApi tagUserApi,
                             final ExternalBus externalBus,
                             final PluginConfigServiceApi configServiceApi) {
        this.accountUserApi = accountUserApi;
        this.analyticsSanityApi = analyticsSanityApi;
        this.analyticsUserApi = analyticsUserApi;
        this.catalogUserApi = catalogUserApi;
        this.entitlementMigrationApi = entitlementMigrationApi;
        this.entitlementTimelineApi = entitlementTimelineApi;
        this.entitlementTransferApi = entitlementTransferApi;
        this.entitlementUserApi = entitlementUserApi;
        this.invoiceMigrationApi = invoiceMigrationApi;
        this.invoicePaymentApi = invoicePaymentApi;
        this.invoiceUserApi = invoiceUserApi;
        this.meterUserApi = meterUserApi;
        this.overdueUserApi = overdueUserApi;
        this.paymentApi = paymentApi;
        this.tenantUserApi = tenantUserApi;
        this.usageUserApi = usageUserApi;
        this.auditUserApi = auditUserApi;
        this.customFieldUserApi = customFieldUserApi;
        this.exportUserApi = exportUserApi;
        this.tagUserApi = tagUserApi;
        this.externalBus = externalBus;
        this.configServiceApi = configServiceApi;
    }

    @Override
    public void start(final BundleContext context) throws Exception {
        registerServices(context);
    }

    private void registerServices(final BundleContext context) {
        accountUserApiRegistration = context.registerService(AccountUserApi.class.getName(), accountUserApi, null);
        analyticsSanityApiRegistration = context.registerService(AnalyticsSanityApi.class.getName(), analyticsSanityApi, null);
        analyticsUserApiRegistration = context.registerService(AnalyticsUserApi.class.getName(), analyticsUserApi, null);
        catalogUserApiRegistration = context.registerService(CatalogUserApi.class.getName(), catalogUserApi, null);
        entitlementMigrationApiRegistration = context.registerService(EntitlementMigrationApi.class.getName(), entitlementMigrationApi, null);
        entitlementTimelineApiRegistration = context.registerService(EntitlementTimelineApi.class.getName(), entitlementTimelineApi, null);
        entitlementTransferApiRegistration = context.registerService(EntitlementTransferApi.class.getName(), entitlementTransferApi, null);
        entitlementUserApiRegistration = context.registerService(EntitlementUserApi.class.getName(), entitlementUserApi, null);
        invoiceMigrationApiRegistration = context.registerService(InvoiceMigrationApi.class.getName(), invoiceMigrationApi, null);
        invoicePaymentApiRegistration = context.registerService(InvoicePaymentApi.class.getName(), invoicePaymentApi, null);
        invoiceUserApiRegistration = context.registerService(InvoiceUserApi.class.getName(), invoiceUserApi, null);
        meterUserApiRegistration = context.registerService(MeterUserApi.class.getName(), meterUserApi, null);
        overdueUserApiRegistration = context.registerService(OverdueUserApi.class.getName(), overdueUserApi, null);
        paymentApiRegistration = context.registerService(PaymentApi.class.getName(), paymentApi, null);
        tenantUserApiRegistration = context.registerService(TenantUserApi.class.getName(), tenantUserApi, null);
        usageUserApiRegistration = context.registerService(UsageUserApi.class.getName(), usageUserApi, null);
        auditUserApiRegistration = context.registerService(AuditUserApi.class.getName(), auditUserApi, null);
        customFieldUserApiRegistration = context.registerService(CustomFieldUserApi.class.getName(), customFieldUserApi, null);
        exportUserApiRegistration = context.registerService(ExportUserApi.class.getName(), exportUserApi, null);
        tagUserApiRegistration = context.registerService(TagUserApi.class.getName(), tagUserApi, null);

        externalBusRegistration = context.registerService(ExternalBus.class.getName(), externalBus, null);
        configServiceApiRegistration = context.registerService(PluginConfigServiceApi.class.getName(), configServiceApi, null);
    }

    @Override
    public void stop(final BundleContext context) throws Exception {
        if (accountUserApiRegistration != null) {
            accountUserApiRegistration.unregister();
            accountUserApiRegistration = null;
        }
        if (analyticsSanityApiRegistration != null) {
            analyticsSanityApiRegistration.unregister();
            analyticsSanityApiRegistration = null;
        }
        if (analyticsUserApiRegistration != null) {
            analyticsUserApiRegistration.unregister();
            analyticsUserApiRegistration = null;
        }
        if (catalogUserApiRegistration != null) {
            catalogUserApiRegistration.unregister();
            catalogUserApiRegistration = null;
        }
        if (entitlementMigrationApiRegistration != null) {
            entitlementMigrationApiRegistration.unregister();
            entitlementMigrationApiRegistration = null;
        }
        if (entitlementTimelineApiRegistration != null) {
            entitlementTimelineApiRegistration.unregister();
            entitlementTimelineApiRegistration = null;
        }
        if (entitlementTransferApiRegistration != null) {
            entitlementTransferApiRegistration.unregister();
            entitlementTransferApiRegistration = null;
        }
        if (entitlementUserApiRegistration != null) {
            entitlementUserApiRegistration.unregister();
            entitlementUserApiRegistration = null;
        }
        if (invoiceMigrationApiRegistration != null) {
            invoiceMigrationApiRegistration.unregister();
            invoiceMigrationApiRegistration = null;
        }
        if (invoicePaymentApiRegistration != null) {
            invoicePaymentApiRegistration.unregister();
            invoicePaymentApiRegistration = null;
        }
        if (invoiceUserApiRegistration != null) {
            invoiceUserApiRegistration.unregister();
            invoiceUserApiRegistration = null;
        }
        if (meterUserApiRegistration != null) {
            meterUserApiRegistration.unregister();
            meterUserApiRegistration = null;
        }
        if (overdueUserApiRegistration != null) {
            overdueUserApiRegistration.unregister();
            overdueUserApiRegistration = null;
        }
        if (paymentApiRegistration != null) {
            paymentApiRegistration.unregister();
            paymentApiRegistration = null;
        }
        if (tenantUserApiRegistration != null) {
            tenantUserApiRegistration.unregister();
            tenantUserApiRegistration = null;
        }
        if (usageUserApiRegistration != null) {
            usageUserApiRegistration.unregister();
            usageUserApiRegistration = null;
        }
        if (auditUserApiRegistration != null) {
            auditUserApiRegistration.unregister();
            auditUserApiRegistration = null;
        }
        if (customFieldUserApiRegistration != null) {
            customFieldUserApiRegistration.unregister();
            customFieldUserApiRegistration = null;
        }
        if (exportUserApiRegistration != null) {
            exportUserApiRegistration.unregister();
            exportUserApiRegistration = null;
        }
        if (tagUserApiRegistration != null) {
            tagUserApiRegistration.unregister();
            tagUserApiRegistration = null;
        }
        if (externalBusRegistration != null) {
            externalBusRegistration.unregister();
            externalBusRegistration = null;
        }

        if (configServiceApiRegistration != null) {
            configServiceApiRegistration.unregister();
            configServiceApiRegistration = null;
        }
    }

    //    public PaymentPluginApi getPaymentPluginApiForPlugin(final String pluginName) {
    //        try {
    //            final ServiceReference<PaymentPluginApi>[] paymentApiReferences = (ServiceReference<PaymentPluginApi>[]) context.getServiceReferences(PaymentPluginApi.class.getName(), "(name=hello)");
    //            final PaymentPluginApi pluginApi = context.getService(paymentApiReferences[0]);
    //            return pluginApi;
    //        } catch (InvalidSyntaxException e) {
    //            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
    //        } finally {
    //            //context.ungetService(paymentApiReferences[0]);
    //            // STEPH TODO leak reference here
    //        }
    //        return null;
    //    }
}
