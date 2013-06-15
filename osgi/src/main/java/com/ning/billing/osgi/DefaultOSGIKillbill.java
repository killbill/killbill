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

import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.catalog.api.CatalogUserApi;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApi;
import com.ning.billing.entitlement.api.timeline.EntitlementTimelineApi;
import com.ning.billing.entitlement.api.transfer.EntitlementTransferApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.invoice.api.InvoiceMigrationApi;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.junction.api.JunctionApi;
import com.ning.billing.osgi.api.OSGIKillbill;
import com.ning.billing.osgi.api.config.PluginConfigServiceApi;
import com.ning.billing.overdue.OverdueUserApi;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.tenant.api.TenantUserApi;
import com.ning.billing.usage.api.UsageUserApi;
import com.ning.billing.util.api.AuditUserApi;
import com.ning.billing.util.api.CustomFieldUserApi;
import com.ning.billing.util.api.ExportUserApi;
import com.ning.billing.util.api.RecordIdApi;
import com.ning.billing.util.api.TagUserApi;

public class DefaultOSGIKillbill implements OSGIKillbill {

    private final AccountUserApi accountUserApi;
    private final CatalogUserApi catalogUserApi;
    private final EntitlementMigrationApi entitlementMigrationApi;
    private final EntitlementTimelineApi entitlementTimelineApi;
    private final EntitlementTransferApi entitlementTransferApi;
    private final EntitlementUserApi entitlementUserApi;
    private final InvoiceMigrationApi invoiceMigrationApi;
    private final InvoicePaymentApi invoicePaymentApi;
    private final InvoiceUserApi invoiceUserApi;
    private final OverdueUserApi overdueUserApi;
    private final PaymentApi paymentApi;
    private final TenantUserApi tenantUserApi;
    private final UsageUserApi usageUserApi;
    private final AuditUserApi auditUserApi;
    private final CustomFieldUserApi customFieldUserApi;
    private final ExportUserApi exportUserApi;
    private final TagUserApi tagUserApi;
    private final JunctionApi junctionApi;
    private final RecordIdApi recordIdApi;

    private final PluginConfigServiceApi configServiceApi;

    @Inject
    public DefaultOSGIKillbill(final AccountUserApi accountUserApi,
                               final CatalogUserApi catalogUserApi,
                               final EntitlementMigrationApi entitlementMigrationApi,
                               final EntitlementTimelineApi entitlementTimelineApi,
                               final EntitlementTransferApi entitlementTransferApi,
                               final EntitlementUserApi entitlementUserApi,
                               final InvoiceMigrationApi invoiceMigrationApi,
                               final InvoicePaymentApi invoicePaymentApi,
                               final InvoiceUserApi invoiceUserApi,
                               final OverdueUserApi overdueUserApi,
                               final PaymentApi paymentApi,
                               final TenantUserApi tenantUserApi,
                               final UsageUserApi usageUserApi,
                               final AuditUserApi auditUserApi,
                               final CustomFieldUserApi customFieldUserApi,
                               final ExportUserApi exportUserApi,
                               final TagUserApi tagUserApi,
                               final JunctionApi junctionApi,
                               final RecordIdApi recordIdApi,
                               final PluginConfigServiceApi configServiceApi) {
        this.accountUserApi = accountUserApi;
        this.catalogUserApi = catalogUserApi;
        this.entitlementMigrationApi = entitlementMigrationApi;
        this.entitlementTimelineApi = entitlementTimelineApi;
        this.entitlementTransferApi = entitlementTransferApi;
        this.entitlementUserApi = entitlementUserApi;
        this.invoiceMigrationApi = invoiceMigrationApi;
        this.invoicePaymentApi = invoicePaymentApi;
        this.invoiceUserApi = invoiceUserApi;
        this.overdueUserApi = overdueUserApi;
        this.paymentApi = paymentApi;
        this.tenantUserApi = tenantUserApi;
        this.usageUserApi = usageUserApi;
        this.auditUserApi = auditUserApi;
        this.customFieldUserApi = customFieldUserApi;
        this.exportUserApi = exportUserApi;
        this.tagUserApi = tagUserApi;
        this.junctionApi = junctionApi;
        this.recordIdApi = recordIdApi;
        this.configServiceApi = configServiceApi;
    }

    @Override
    public AccountUserApi getAccountUserApi() {
        return accountUserApi;
    }

    @Override
    public CatalogUserApi getCatalogUserApi() {
        return catalogUserApi;
    }

    @Override
    public EntitlementMigrationApi getEntitlementMigrationApi() {
        return entitlementMigrationApi;
    }

    @Override
    public EntitlementTimelineApi getEntitlementTimelineApi() {
        return entitlementTimelineApi;
    }

    @Override
    public EntitlementTransferApi getEntitlementTransferApi() {
        return entitlementTransferApi;
    }

    @Override
    public EntitlementUserApi getEntitlementUserApi() {
        return entitlementUserApi;
    }

    @Override
    public InvoiceMigrationApi getInvoiceMigrationApi() {
        return invoiceMigrationApi;
    }

    @Override
    public InvoicePaymentApi getInvoicePaymentApi() {
        return invoicePaymentApi;
    }

    @Override
    public InvoiceUserApi getInvoiceUserApi() {
        return invoiceUserApi;
    }

    @Override
    public OverdueUserApi getOverdueUserApi() {
        return overdueUserApi;
    }

    @Override
    public PaymentApi getPaymentApi() {
        return paymentApi;
    }

    @Override
    public TenantUserApi getTenantUserApi() {
        return tenantUserApi;
    }

    @Override
    public UsageUserApi getUsageUserApi() {
        return usageUserApi;
    }

    @Override
    public AuditUserApi getAuditUserApi() {
        return auditUserApi;
    }

    @Override
    public CustomFieldUserApi getCustomFieldUserApi() {
        return customFieldUserApi;
    }

    @Override
    public ExportUserApi getExportUserApi() {
        return exportUserApi;
    }

    @Override
    public TagUserApi getTagUserApi() {
        return tagUserApi;
    }

    @Override
    public JunctionApi getJunctionApi() {
        return junctionApi;
    }

    @Override
    public RecordIdApi getRecordIdApi() {
        return recordIdApi;
    }

    @Override
    public PluginConfigServiceApi getPluginConfigServiceApi() {
        return configServiceApi;
    }
}
