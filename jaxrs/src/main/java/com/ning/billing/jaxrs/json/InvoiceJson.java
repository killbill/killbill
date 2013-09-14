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

package com.ning.billing.jaxrs.json;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;

import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.util.audit.AuditLog;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class InvoiceJson extends JsonBase {

    private final BigDecimal amount;
    private final String currency;
    private final String invoiceId;
    private final LocalDate invoiceDate;
    private final LocalDate targetDate;
    private final String invoiceNumber;
    private final BigDecimal balance;
    private final BigDecimal creditAdj;
    private final BigDecimal refundAdj;
    private final String accountId;
    private final List<InvoiceItemJson> items;
    private final String bundleKeys;
    private final List<CreditJson> credits;

    @JsonCreator
    public InvoiceJson(@JsonProperty("amount") final BigDecimal amount,
                       @JsonProperty("currency") final String currency,
                       @JsonProperty("creditAdj") final BigDecimal creditAdj,
                       @JsonProperty("refundAdj") final BigDecimal refundAdj,
                       @JsonProperty("invoiceId") final String invoiceId,
                       @JsonProperty("invoiceDate") final LocalDate invoiceDate,
                       @JsonProperty("targetDate") final LocalDate targetDate,
                       @JsonProperty("invoiceNumber") final String invoiceNumber,
                       @JsonProperty("balance") final BigDecimal balance,
                       @JsonProperty("accountId") final String accountId,
                       @JsonProperty("externalBundleKeys") final String bundleKeys,
                       @JsonProperty("credits") final List<CreditJson> credits,
                       @JsonProperty("items") final List<InvoiceItemJson> items,
                       @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(auditLogs);
        this.amount = amount;
        this.currency = currency;
        this.creditAdj = creditAdj;
        this.refundAdj = refundAdj;
        this.invoiceId = invoiceId;
        this.invoiceDate = invoiceDate;
        this.targetDate = targetDate;
        this.invoiceNumber = invoiceNumber;
        this.balance = balance;
        this.accountId = accountId;
        this.bundleKeys = bundleKeys;
        this.credits = credits;
        this.items = items;
    }


    public InvoiceJson(final Invoice input, @Nullable final List<AuditLog> auditLogs) {
        this(input, null, null, auditLogs);
    }

    public InvoiceJson(final Invoice input, final String bundleKeys, final List<CreditJson> credits, final List<AuditLog> auditLogs) {
        this(input.getChargedAmount(), input.getCurrency().toString(), input.getCreditedAmount(), input.getRefundedAmount(),
             input.getId().toString(), input.getInvoiceDate(), input.getTargetDate(), String.valueOf(input.getInvoiceNumber()),
             input.getBalance(), input.getAccountId().toString(), bundleKeys, credits, null, toAuditLogJson(auditLogs));
    }

    public InvoiceJson(final Invoice input, @Nullable final List<AuditLog> invoiceAuditLogs, @Nullable final Map<UUID, List<AuditLog>> invoiceItemsAuditLogs) {
        super(toAuditLogJson(invoiceAuditLogs));
        this.items = new ArrayList<InvoiceItemJson>(input.getInvoiceItems().size());
        for (final InvoiceItem item : input.getInvoiceItems()) {
            this.items.add(new InvoiceItemJson(item, invoiceItemsAuditLogs == null ? null : invoiceItemsAuditLogs.get(item.getId())));
        }
        this.amount = input.getChargedAmount();
        this.currency = input.getCurrency().toString();
        this.creditAdj = input.getCreditedAmount();
        this.refundAdj = input.getRefundedAmount();
        this.invoiceId = input.getId().toString();
        this.invoiceDate = input.getInvoiceDate();
        this.targetDate = input.getTargetDate();
        this.invoiceNumber = String.valueOf(input.getInvoiceNumber());
        this.balance = input.getBalance();
        this.accountId = input.getAccountId().toString();
        this.bundleKeys = null;
        this.credits = null;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public LocalDate getInvoiceDate() {
        return invoiceDate;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public BigDecimal getCreditAdj() {
        return creditAdj;
    }

    public BigDecimal getRefundAdj() {
        return refundAdj;
    }

    public String getAccountId() {
        return accountId;
    }

    public List<InvoiceItemJson> getItems() {
        return items;
    }

    public String getBundleKeys() {
        return bundleKeys;
    }

    public List<CreditJson> getCredits() {
        return credits;
    }

    @Override
    public String toString() {
        return "InvoiceJson{" +
               "amount=" + amount +
               ", currency='" + currency + '\'' +
               ", invoiceId='" + invoiceId + '\'' +
               ", invoiceDate=" + invoiceDate +
               ", targetDate=" + targetDate +
               ", invoiceNumber='" + invoiceNumber + '\'' +
               ", balance=" + balance +
               ", creditAdj=" + creditAdj +
               ", refundAdj=" + refundAdj +
               ", accountId='" + accountId + '\'' +
               ", items=" + items +
               ", bundleKeys='" + bundleKeys + '\'' +
               ", credits=" + credits +
               '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final InvoiceJson that = (InvoiceJson) o;

        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (amount != null ? amount.compareTo(that.amount) != 0 : that.amount != null) {
            return false;
        }
        if (balance != null ? balance.compareTo(that.balance) != 0: that.balance != null) {
            return false;
        }
        if (bundleKeys != null ? !bundleKeys.equals(that.bundleKeys) : that.bundleKeys != null) {
            return false;
        }
        if (creditAdj != null ? creditAdj.compareTo(that.creditAdj) != 0 : that.creditAdj != null) {
            return false;
        }
        if (credits != null ? !credits.equals(that.credits) : that.credits != null) {
            return false;
        }
        if (currency != null ? !currency.equals(that.currency) : that.currency != null) {
            return false;
        }
        if (invoiceDate != null ? invoiceDate.compareTo(that.invoiceDate) != 0 : that.invoiceDate != null) {
            return false;
        }
        if (invoiceId != null ? !invoiceId.equals(that.invoiceId) : that.invoiceId != null) {
            return false;
        }
        if (invoiceNumber != null ? !invoiceNumber.equals(that.invoiceNumber) : that.invoiceNumber != null) {
            return false;
        }
        if (items != null ? !items.equals(that.items) : that.items != null) {
            return false;
        }
        if (refundAdj != null ? refundAdj.compareTo(that.refundAdj) != 0: that.refundAdj != null) {
            return false;
        }
        if (targetDate != null ? targetDate.compareTo(that.targetDate) != 0 : that.targetDate != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = amount != null ? amount.hashCode() : 0;
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (invoiceId != null ? invoiceId.hashCode() : 0);
        result = 31 * result + (invoiceDate != null ? invoiceDate.hashCode() : 0);
        result = 31 * result + (targetDate != null ? targetDate.hashCode() : 0);
        result = 31 * result + (invoiceNumber != null ? invoiceNumber.hashCode() : 0);
        result = 31 * result + (balance != null ? balance.hashCode() : 0);
        result = 31 * result + (creditAdj != null ? creditAdj.hashCode() : 0);
        result = 31 * result + (refundAdj != null ? refundAdj.hashCode() : 0);
        result = 31 * result + (accountId != null ? accountId.hashCode() : 0);
        result = 31 * result + (items != null ? items.hashCode() : 0);
        result = 31 * result + (bundleKeys != null ? bundleKeys.hashCode() : 0);
        result = 31 * result + (credits != null ? credits.hashCode() : 0);
        return result;
    }
}
