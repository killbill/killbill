/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.jaxrs.json;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.entitlement.api.SubscriptionBundle;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.api.InvoicePayment;
import org.killbill.billing.jaxrs.resources.JaxRsResourceBase;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.util.audit.AccountAuditLogs;
import org.killbill.billing.util.audit.AuditLog;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;

@ApiModel(value="AccountTimeline")
public class AccountTimelineJson {

    private final AccountJson account;
    private final List<BundleJson> bundles;
    private final List<InvoiceJson> invoices;
    private final List<InvoicePaymentJson> payments;

    @JsonCreator
    public AccountTimelineJson(@JsonProperty("account") final AccountJson account,
                               @JsonProperty("bundles") final List<BundleJson> bundles,
                               @JsonProperty("invoices") final List<InvoiceJson> invoices,
                               @JsonProperty("payments") final List<InvoicePaymentJson> payments) {
        this.account = account;
        this.bundles = bundles;
        this.invoices = invoices;
        this.payments = payments;
    }

    public AccountTimelineJson(final Account account,
                               final List<Invoice> invoices,
                               final List<Payment> payments,
                               final List<InvoicePayment> invoicePayments,
                               final List<SubscriptionBundle> bundles,
                               final AccountAuditLogs accountAuditLogs) throws CatalogApiException {
        this.account = new AccountJson(account, null, null, accountAuditLogs);
        this.bundles = new LinkedList<BundleJson>();
        for (final SubscriptionBundle bundle : bundles) {
            final BundleJson jsonWithSubscriptions = new BundleJson(bundle, account.getCurrency(), accountAuditLogs);
            this.bundles.add(jsonWithSubscriptions);
        }

        this.invoices = new LinkedList<InvoiceJson>();
        // Extract the credits from the invoices first
        final List<CreditJson> credits = new ArrayList<CreditJson>();
        for (final Invoice invoice : invoices) {
            for (final InvoiceItem invoiceItem : invoice.getInvoiceItems()) {
                if (InvoiceItemType.CREDIT_ADJ.equals(invoiceItem.getInvoiceItemType())) {
                    final List<AuditLog> auditLogs = accountAuditLogs.getAuditLogsForInvoiceItem(invoiceItem.getId());
                    credits.add(new CreditJson(invoice, invoiceItem, auditLogs));
                }
            }
        }
        // Create now the invoice json objects
        for (final Invoice invoice : invoices) {
            final List<AuditLog> auditLogs = accountAuditLogs.getAuditLogsForInvoice(invoice.getId());
            this.invoices.add(new InvoiceJson(invoice,
                                              getBundleExternalKey(invoice, bundles),
                                              credits,
                                              auditLogs));
        }

        this.payments = new LinkedList<InvoicePaymentJson>();
        for (final Payment payment : payments) {
            final UUID invoiceId = JaxRsResourceBase.getInvoiceId(invoicePayments, payment);
            this.payments.add(new InvoicePaymentJson(payment, invoiceId, accountAuditLogs));
        }
    }

    public AccountJson getAccount() {
        return account;
    }

    public List<BundleJson> getBundles() {
        return bundles;
    }

    public List<InvoiceJson> getInvoices() {
        return invoices;
    }

    public List<InvoicePaymentJson> getPayments() {
        return payments;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("AccountTimelineJson");
        sb.append("{account=").append(account);
        sb.append(", bundles=").append(bundles);
        sb.append(", invoices=").append(invoices);
        sb.append(", payments=").append(payments);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final AccountTimelineJson that = (AccountTimelineJson) o;

        if (account != null ? !account.equals(that.account) : that.account != null) {
            return false;
        }
        if (bundles != null ? !bundles.equals(that.bundles) : that.bundles != null) {
            return false;
        }
        if (invoices != null ? !invoices.equals(that.invoices) : that.invoices != null) {
            return false;
        }
        if (payments != null ? !payments.equals(that.payments) : that.payments != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = account != null ? account.hashCode() : 0;
        result = 31 * result + (bundles != null ? bundles.hashCode() : 0);
        result = 31 * result + (invoices != null ? invoices.hashCode() : 0);
        result = 31 * result + (payments != null ? payments.hashCode() : 0);
        return result;
    }

    private String getBundleExternalKey(final UUID invoiceId, final List<Invoice> invoices, final List<SubscriptionBundle> bundles) {
        if (invoiceId == null) {
            return null;
        }
        for (final Invoice cur : invoices) {
            if (cur.getId().equals(invoiceId)) {
                return getBundleExternalKey(cur, bundles);
            }
        }
        return null;
    }

    private String getBundleExternalKey(final Invoice invoice, final List<SubscriptionBundle> bundles) {
        final Set<UUID> b = new HashSet<UUID>();
        for (final InvoiceItem cur : invoice.getInvoiceItems()) {
            b.add(cur.getBundleId());
        }
        boolean first = true;
        final StringBuilder tmp = new StringBuilder();
        for (final UUID cur : b) {
            for (final SubscriptionBundle bt : bundles) {
                if (bt.getId().equals(cur)) {
                    if (!first) {
                        tmp.append(",");
                    }
                    tmp.append(bt.getExternalKey());
                    first = false;
                    break;
                }
            }
        }
        return tmp.toString();
    }
}
