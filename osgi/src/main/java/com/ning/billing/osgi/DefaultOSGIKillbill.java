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

import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.catalog.api.CatalogUserApi;
import com.ning.billing.invoice.api.InvoiceMigrationApi;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.entitlement.api.EntitlementApi;
import com.ning.billing.osgi.api.OSGIKillbill;
import com.ning.billing.osgi.api.config.PluginConfigServiceApi;
import com.ning.billing.overdue.OverdueUserApi;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.subscription.api.migration.SubscriptionMigrationApi;
import com.ning.billing.subscription.api.timeline.SubscriptionTimelineApi;
import com.ning.billing.subscription.api.transfer.SubscriptionTransferApi;
import com.ning.billing.subscription.api.user.SubscriptionUserApi;
import com.ning.billing.tenant.api.TenantUserApi;
import com.ning.billing.usage.api.UsageUserApi;
import com.ning.billing.util.api.AuditUserApi;
import com.ning.billing.util.api.CustomFieldUserApi;
import com.ning.billing.util.api.ExportUserApi;
import com.ning.billing.util.api.RecordIdApi;
import com.ning.billing.util.api.TagUserApi;

import javax.inject.Inject;

public class DefaultOSGIKillbill implements OSGIKillbill {

    private final AccountUserApi accountUserApi;
    private final CatalogUserApi catalogUserApi;
    private final SubscriptionMigrationApi subscriptionMigrationApi;
    private final SubscriptionTimelineApi subscriptionTimelineApi;
    private final SubscriptionTransferApi subscriptionTransferApi;
    private final SubscriptionUserApi subscriptionUserApi;
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
    private final EntitlementApi entitlementApi;
    private final RecordIdApi recordIdApi;

    private final PluginConfigServiceApi configServiceApi;

    @Inject
    public DefaultOSGIKillbill(final AccountUserApi accountUserApi,
                               final CatalogUserApi catalogUserApi,
                               final SubscriptionMigrationApi subscriptionMigrationApi,
                               final SubscriptionTimelineApi subscriptionTimelineApi,
                               final SubscriptionTransferApi subscriptionTransferApi,
                               final SubscriptionUserApi subscriptionUserApi,
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
                               final EntitlementApi entitlementApi,
                               final RecordIdApi recordIdApi,
                               final PluginConfigServiceApi configServiceApi) {
        this.accountUserApi = accountUserApi;
        this.catalogUserApi = catalogUserApi;
        this.subscriptionMigrationApi = subscriptionMigrationApi;
        this.subscriptionTimelineApi = subscriptionTimelineApi;
        this.subscriptionTransferApi = subscriptionTransferApi;
        this.subscriptionUserApi = subscriptionUserApi;
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
        this.entitlementApi = entitlementApi;
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
    public SubscriptionTimelineApi getSubscriptionTimelineApi() {
        return subscriptionTimelineApi;
    }

    @Override
    public SubscriptionTransferApi getSubscriptionTransferApi() {
        return subscriptionTransferApi;
    }

    @Override
    public SubscriptionUserApi getSubscriptionUserApi() {
        return subscriptionUserApi;
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
    public EntitlementApi getEntitlementApi() {
        return entitlementApi;
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
