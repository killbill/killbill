/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.util.audit.AuditLog;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value="Credit", parent = JsonBase.class)
public class CreditJson extends JsonBase {

    private final UUID creditId;
    @ApiModelProperty(required = true)
    private final BigDecimal creditAmount;
    private final UUID invoiceId;
    private final String invoiceNumber;
    private final LocalDate effectiveDate;
    @ApiModelProperty(required = true)
    private final UUID accountId;
    private final String description;
    private final Currency currency;
    private final String itemDetails;

    @JsonCreator
    public CreditJson(@JsonProperty("creditId") final UUID creditId,
                      @JsonProperty("creditAmount") final BigDecimal creditAmount,
                      @JsonProperty("currency") final Currency currency,
                      @JsonProperty("invoiceId") final UUID invoiceId,
                      @JsonProperty("invoiceNumber") final String invoiceNumber,
                      @JsonProperty("effectiveDate") final LocalDate effectiveDate,
                      @JsonProperty("accountId") final UUID accountId,
                      @JsonProperty("description") final String description,
                      @JsonProperty("itemDetails") final String itemDetails,
                      @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(auditLogs);
        this.creditId = creditId;
        this.creditAmount = creditAmount;
        this.currency = currency;
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.effectiveDate = effectiveDate;
        this.description = description;
        this.itemDetails = itemDetails;
        this.accountId = accountId;
    }

    public CreditJson(final Invoice invoice, final InvoiceItem credit, final List<AuditLog> auditLogs) {
        super(toAuditLogJson(auditLogs));
        this.creditId = credit.getId();
        this.accountId = credit.getAccountId();
        this.creditAmount = credit.getAmount();
        this.currency = credit.getCurrency();
        this.invoiceId = credit.getInvoiceId();
        this.invoiceNumber = invoice.getInvoiceNumber().toString();
        this.effectiveDate = credit.getStartDate();
        this.description = credit.getDescription();
        this.itemDetails = credit.getItemDetails();
    }

    public CreditJson(final Invoice invoice, final InvoiceItem credit) {
        this(invoice, credit, null);
    }

    public BigDecimal getCreditAmount() {
        return creditAmount;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getDescription() {
        return description;
    }

    public Currency getCurrency() {
        return currency;
    }

    public String getItemDetails() {
        return itemDetails;
    }

    public UUID getCreditId() {
        return creditId;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("CreditJson");
        sb.append("{creditId=").append(creditId);
        sb.append(", creditAmount=").append(creditAmount);
        sb.append(", currency=").append(currency);
        sb.append(", invoiceId=").append(invoiceId);
        sb.append(", invoiceNumber='").append(invoiceNumber).append('\'');
        sb.append(", effectiveDate=").append(effectiveDate);
        sb.append(", description=").append(description);
        sb.append(", itemDetails=").append(itemDetails);
        sb.append(", accountId=").append(accountId);
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

        final CreditJson that = (CreditJson) o;

        if (creditId != null ? !creditId.equals(that.creditId) : that.creditId != null) {
            return false;
        }
        if (!((creditAmount == null && that.creditAmount == null) ||
              (creditAmount != null && that.creditAmount != null && creditAmount.compareTo(that.creditAmount) == 0))) {
            return false;
        }
        if (currency != null ? !currency.equals(that.currency) : that.currency != null) {
            return false;
        }
        if (invoiceId != null ? !invoiceId.equals(that.invoiceId) : that.invoiceId != null) {
            return false;
        }
        if (invoiceNumber != null ? !invoiceNumber.equals(that.invoiceNumber) : that.invoiceNumber != null) {
            return false;
        }
        if (description != null ? !description.equals(that.description) : that.description != null) {
            return false;
        }
        if (itemDetails != null ? !itemDetails.equals(that.itemDetails) : that.itemDetails != null) {
            return false;
        }
        if (!((effectiveDate == null && that.effectiveDate == null) ||
              (effectiveDate != null && that.effectiveDate != null && effectiveDate.compareTo(that.effectiveDate) == 0))) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = creditAmount != null ? creditAmount.hashCode() : 0;
        result = 31 * result + (creditId != null ? creditId.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (invoiceId != null ? invoiceId.hashCode() : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (itemDetails != null ? itemDetails.hashCode() : 0);
        result = 31 * result + (invoiceNumber != null ? invoiceNumber.hashCode() : 0);
        result = 31 * result + (effectiveDate != null ? effectiveDate.hashCode() : 0);
        return result;
    }
}
