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

package com.ning.billing.osgi.api;

import javax.sql.DataSource;

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
import com.ning.billing.osgi.api.config.PluginConfigServiceApi;
import com.ning.billing.overdue.OverdueUserApi;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.tenant.api.TenantUserApi;
import com.ning.billing.usage.api.UsageUserApi;
import com.ning.billing.util.api.AuditUserApi;
import com.ning.billing.util.api.CustomFieldUserApi;
import com.ning.billing.util.api.ExportUserApi;
import com.ning.billing.util.api.TagUserApi;

/**
 * This interface encapsulates all the OSGI interfaces seen by the Killbill OSGI plugins
 */
public interface OSGIKillbill {

    /**
     * Used  by the OSGI bundles to interact with Killbill through APIs
     *
     * @return the matching API
     */
    public AccountUserApi getAccountUserApi();
    public AnalyticsSanityApi getAnalyticsSanityApi();
    public AnalyticsUserApi getAnalyticsUserApi();
    public CatalogUserApi getCatalogUserApi();
    public EntitlementMigrationApi getEntitlementMigrationApi();
    public EntitlementTimelineApi getEntitlementTimelineApi();
    public EntitlementTransferApi getEntitlementTransferApi();
    public EntitlementUserApi getEntitlementUserApi();
    public InvoiceMigrationApi getInvoiceMigrationApi();
    public InvoicePaymentApi getInvoicePaymentApi();
    public InvoiceUserApi getInvoiceUserApi();
    public OverdueUserApi getOverdueUserApi();
    public PaymentApi getPaymentApi();
    public TenantUserApi getTenantUserApi();
    public UsageUserApi getUsageUserApi();
    public AuditUserApi getAuditUserApi();
    public CustomFieldUserApi getCustomFieldUserApi();
    public ExportUserApi getExportUserApi();
    public TagUserApi getTagUserApi();

    /**
     * Used by the OSGI bundles to register interest into Killbill events
     *
     * @return the externalBus
     */
    public ExternalBus getExternalBus();


    /**
     * Used by the OSGI bundles to discover their configuration
     *
     * @return the PluginConfigServiceApi
     */
    public PluginConfigServiceApi getPluginConfigServiceApi();


    /**
     * Used by the OSGI bundles to be able to access their own sql tables
     * @return the dataSource for the OSGI bundles
     */
    public DataSource getDataSource();
}
