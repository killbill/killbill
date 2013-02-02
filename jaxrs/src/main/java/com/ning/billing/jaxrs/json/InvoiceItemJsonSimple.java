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
import java.util.List;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.InvoiceItem;

import com.fasterxml.jackson.annotation.JsonProperty;

public class InvoiceItemJsonSimple extends JsonBase {

    private final String invoiceItemId;
    private final String invoiceId;
    private final String linkedInvoiceItemId;
    private final String accountId;
    private final String bundleId;
    private final String subscriptionId;
    private final String planName;
    private final String phaseName;
    private final String description;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final BigDecimal amount;
    private final Currency currency;

    public InvoiceItemJsonSimple(@JsonProperty("invoiceItemId") final String invoiceItemId,
                                 @JsonProperty("invoiceId") final String invoiceId,
                                 @JsonProperty("linkedInvoiceItemId") final String linkedInvoiceItemId,
                                 @JsonProperty("accountId") final String accountId,
                                 @JsonProperty("bundleId") final String bundleId,
                                 @JsonProperty("subscriptionId") final String subscriptionId,
                                 @JsonProperty("planName") final String planName,
                                 @JsonProperty("phaseName") final String phaseName,
                                 @JsonProperty("description") final String description,
                                 @JsonProperty("startDate") final LocalDate startDate,
                                 @JsonProperty("endDate") final LocalDate endDate,
                                 @JsonProperty("amount") final BigDecimal amount,
                                 @JsonProperty("currency") final Currency currency,
                                 @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(auditLogs);
        this.invoiceItemId = invoiceItemId;
        this.invoiceId = invoiceId;
        this.linkedInvoiceItemId = linkedInvoiceItemId;
        this.accountId = accountId;
        this.bundleId = bundleId;
        this.subscriptionId = subscriptionId;
        this.planName = planName;
        this.phaseName = phaseName;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
        this.amount = amount;
        this.currency = currency;
    }

    public InvoiceItemJsonSimple(final InvoiceItem item) {
        this(toString(item.getId()), toString(item.getInvoiceId()), toString(item.getLinkedItemId()),
             toString(item.getAccountId()), toString(item.getBundleId()), toString(item.getSubscriptionId()),
             item.getPlanName(), item.getPhaseName(), item.getDescription(), item.getStartDate(), item.getEndDate(),
             item.getAmount(), item.getCurrency(), null);
    }

    public String getInvoiceItemId() {
        return invoiceItemId;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public String getLinkedInvoiceItemId() {
        return linkedInvoiceItemId;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getBundleId() {
        return bundleId;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public String getPlanName() {
        return planName;
    }

    public String getPhaseName() {
        return phaseName;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("InvoiceItemJsonSimple");
        sb.append("{invoiceItemId='").append(invoiceItemId).append('\'');
        sb.append(", invoiceId='").append(invoiceId).append('\'');
        sb.append(", linkedInvoiceItemId='").append(linkedInvoiceItemId).append('\'');
        sb.append(", accountId='").append(accountId).append('\'');
        sb.append(", bundleId='").append(bundleId).append('\'');
        sb.append(", subscriptionId='").append(subscriptionId).append('\'');
        sb.append(", planName='").append(planName).append('\'');
        sb.append(", phaseName='").append(phaseName).append('\'');
        sb.append(", description='").append(description).append('\'');
        sb.append(", startDate=").append(startDate);
        sb.append(", endDate=").append(endDate);
        sb.append(", amount=").append(amount);
        sb.append(", currency=").append(currency);
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

        final InvoiceItemJsonSimple that = (InvoiceItemJsonSimple) o;

        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (!((amount == null && that.amount == null) ||
              (amount != null && that.amount != null && amount.compareTo(that.amount) == 0))) {
            return false;
        }
        if (bundleId != null ? !bundleId.equals(that.bundleId) : that.bundleId != null) {
            return false;
        }
        if (currency != that.currency) {
            return false;
        }
        if (description != null ? !description.equals(that.description) : that.description != null) {
            return false;
        }
        if (!((endDate == null && that.endDate == null) ||
              (endDate != null && that.endDate != null && endDate.compareTo(that.endDate) == 0))) {
            return false;
        }
        if (invoiceItemId != null ? !invoiceItemId.equals(that.invoiceItemId) : that.invoiceItemId != null) {
            return false;
        }
        if (invoiceId != null ? !invoiceId.equals(that.invoiceId) : that.invoiceId != null) {
            return false;
        }
        if (linkedInvoiceItemId != null ? !linkedInvoiceItemId.equals(that.linkedInvoiceItemId) : that.linkedInvoiceItemId != null) {
            return false;
        }
        if (phaseName != null ? !phaseName.equals(that.phaseName) : that.phaseName != null) {
            return false;
        }
        if (planName != null ? !planName.equals(that.planName) : that.planName != null) {
            return false;
        }
        if (!((startDate == null && that.startDate == null) ||
              (startDate != null && that.startDate != null && startDate.compareTo(that.startDate) == 0))) {
            return false;
        }
        if (subscriptionId != null ? !subscriptionId.equals(that.subscriptionId) : that.subscriptionId != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = invoiceId != null ? invoiceId.hashCode() : 0;
        result = 31 * result + (invoiceItemId != null ? invoiceItemId.hashCode() : 0);
        result = 31 * result + (linkedInvoiceItemId != null ? linkedInvoiceItemId.hashCode() : 0);
        result = 31 * result + (accountId != null ? accountId.hashCode() : 0);
        result = 31 * result + (bundleId != null ? bundleId.hashCode() : 0);
        result = 31 * result + (subscriptionId != null ? subscriptionId.hashCode() : 0);
        result = 31 * result + (planName != null ? planName.hashCode() : 0);
        result = 31 * result + (phaseName != null ? phaseName.hashCode() : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (startDate != null ? startDate.hashCode() : 0);
        result = 31 * result + (endDate != null ? endDate.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        return result;
    }
}
