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

package org.killbill.billing.osgi;

import javax.inject.Inject;

import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.catalog.api.CatalogUserApi;
import org.killbill.billing.currency.api.CurrencyConversionApi;
import org.killbill.billing.entitlement.api.EntitlementApi;
import org.killbill.billing.entitlement.api.SubscriptionApi;
import org.killbill.billing.invoice.api.InvoicePaymentApi;
import org.killbill.billing.invoice.api.InvoiceUserApi;
import org.killbill.billing.osgi.api.OSGIKillbill;
import org.killbill.billing.osgi.api.config.PluginConfigServiceApi;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.security.api.SecurityApi;
import org.killbill.billing.tenant.api.TenantUserApi;
import org.killbill.billing.usage.api.UsageUserApi;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.ExportUserApi;
import org.killbill.billing.util.api.RecordIdApi;
import org.killbill.billing.util.api.TagUserApi;

public class DefaultOSGIKillbill implements OSGIKillbill {

    private final AccountUserApi accountUserApi;
    private final CatalogUserApi catalogUserApi;
    private final InvoicePaymentApi invoicePaymentApi;
    private final InvoiceUserApi invoiceUserApi;
    private final PaymentApi paymentApi;
    private final TenantUserApi tenantUserApi;
    private final UsageUserApi usageUserApi;
    private final AuditUserApi auditUserApi;
    private final CustomFieldUserApi customFieldUserApi;
    private final ExportUserApi exportUserApi;
    private final TagUserApi tagUserApi;
    private final EntitlementApi entitlementApi;
    private final SubscriptionApi subscriptionApi;
    private final CurrencyConversionApi currencyConversionApi;
    private final RecordIdApi recordIdApi;
    private final SecurityApi securityApi;

    private final PluginConfigServiceApi configServiceApi;

    @Inject
    public DefaultOSGIKillbill(final AccountUserApi accountUserApi,
                               final CatalogUserApi catalogUserApi,
                               final InvoicePaymentApi invoicePaymentApi,
                               final InvoiceUserApi invoiceUserApi,
                               final PaymentApi paymentApi,
                               final TenantUserApi tenantUserApi,
                               final UsageUserApi usageUserApi,
                               final AuditUserApi auditUserApi,
                               final CustomFieldUserApi customFieldUserApi,
                               final ExportUserApi exportUserApi,
                               final TagUserApi tagUserApi,
                               final EntitlementApi entitlementApi,
                               final SubscriptionApi subscriptionApi,
                               final RecordIdApi recordIdApi,
                               final CurrencyConversionApi currencyConversionApi,
                               final PluginConfigServiceApi configServiceApi,
                               final SecurityApi securityApi) {
        this.accountUserApi = accountUserApi;
        this.catalogUserApi = catalogUserApi;
        this.invoicePaymentApi = invoicePaymentApi;
        this.invoiceUserApi = invoiceUserApi;
        this.paymentApi = paymentApi;
        this.tenantUserApi = tenantUserApi;
        this.usageUserApi = usageUserApi;
        this.auditUserApi = auditUserApi;
        this.customFieldUserApi = customFieldUserApi;
        this.exportUserApi = exportUserApi;
        this.tagUserApi = tagUserApi;
        this.entitlementApi = entitlementApi;
        this.subscriptionApi = subscriptionApi;
        this.currencyConversionApi = currencyConversionApi;
        this.recordIdApi = recordIdApi;
        this.configServiceApi = configServiceApi;
        this.securityApi = securityApi;
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
    public SubscriptionApi getSubscriptionApi() {
        return subscriptionApi;
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
    public CurrencyConversionApi getCurrencyConversionApi() {
        return currencyConversionApi;
    }

    @Override
    public PluginConfigServiceApi getPluginConfigServiceApi() {
        return configServiceApi;
    }

    @Override
    public SecurityApi getSecurityApi() {
        return securityApi;
    }
}
