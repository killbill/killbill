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

package org.killbill.billing.jaxrs.json;

import java.math.BigDecimal;
import java.util.List;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.util.audit.AuditLog;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;

public class CreditJson extends JsonBase {

    @ApiModelProperty(required = true)
    private final BigDecimal creditAmount;
    @ApiModelProperty(dataType = "java.util.UUID")
    private final String invoiceId;
    private final String invoiceNumber;
    private final LocalDate effectiveDate;
    @ApiModelProperty(dataType = "java.util.UUID", required = true)
    private final String accountId;
    private final String description;
    private final String currency;


    @JsonCreator
    public CreditJson(@JsonProperty("creditAmount") final BigDecimal creditAmount,
                      @JsonProperty("currency") final String currency,
                      @JsonProperty("invoiceId") final String invoiceId,
                      @JsonProperty("invoiceNumber") final String invoiceNumber,
                      @JsonProperty("effectiveDate") final LocalDate effectiveDate,
                      @JsonProperty("accountId") final String accountId,
                      @JsonProperty("description") final String description,
                      @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(auditLogs);
        this.creditAmount = creditAmount;
        this.currency = currency;
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.effectiveDate = effectiveDate;
        this.description = description;
        this.accountId = accountId;
    }

    public CreditJson(final Invoice invoice, final InvoiceItem credit, final List<AuditLog> auditLogs) {
        super(toAuditLogJson(auditLogs));
        this.accountId = toString(credit.getAccountId());
        this.creditAmount = credit.getAmount();
        this.currency = credit.getCurrency().name();
        this.invoiceId = toString(credit.getInvoiceId());
        this.invoiceNumber = invoice.getInvoiceNumber().toString();
        this.effectiveDate = credit.getStartDate();
        this.description = credit.getDescription();
    }

    public CreditJson(final Invoice invoice, final InvoiceItem credit) {
        this(invoice, credit, null);
    }

    public BigDecimal getCreditAmount() {
        return creditAmount;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getDescription() {
        return description;
    }

    public String getCurrency() {
        return currency;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("CreditJson");
        sb.append("{creditAmount=").append(creditAmount);
        sb.append(", currency=").append(currency);
        sb.append(", invoiceId=").append(invoiceId);
        sb.append(", invoiceNumber='").append(invoiceNumber).append('\'');
        sb.append(", effectiveDate=").append(effectiveDate);
        sb.append(", description=").append(description);
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
        if (!((effectiveDate == null && that.effectiveDate == null) ||
              (effectiveDate != null && that.effectiveDate != null && effectiveDate.compareTo(that.effectiveDate) == 0))) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = creditAmount != null ? creditAmount.hashCode() : 0;
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (invoiceId != null ? invoiceId.hashCode() : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (invoiceNumber != null ? invoiceNumber.hashCode() : 0);
        result = 31 * result + (effectiveDate != null ? effectiveDate.hashCode() : 0);
        return result;
    }
}
