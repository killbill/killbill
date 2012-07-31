/*
 * Copyright 2010-2011 Ning, Inc.
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
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.util.audit.AuditLog;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CreditJson extends JsonBase {

    private final BigDecimal creditAmount;
    private final UUID invoiceId;
    private final String invoiceNumber;
    private final DateTime requestedDate;
    private final DateTime effectiveDate;
    private final String reason;
    private final UUID accountId;

    @JsonCreator
    public CreditJson(@JsonProperty("creditAmount") final BigDecimal creditAmount,
                      @JsonProperty("invoiceId") final UUID invoiceId,
                      @JsonProperty("invoiceNumber") final String invoiceNumber,
                      @JsonProperty("requestedDate") final DateTime requestedDate,
                      @JsonProperty("effectiveDate") final DateTime effectiveDate,
                      @JsonProperty("reason") final String reason,
                      @JsonProperty("accountId") final UUID accountId,
                      @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(auditLogs);
        this.creditAmount = creditAmount;
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.requestedDate = requestedDate;
        this.effectiveDate = effectiveDate;
        this.reason = reason;
        this.accountId = accountId;
    }

    public CreditJson(final InvoiceItem credit, final DateTimeZone accountTimeZone, final List<AuditLog> auditLogs) {
        super(toAuditLogJson(auditLogs));
        this.creditAmount = credit.getAmount();
        this.invoiceId = credit.getInvoiceId();
        this.invoiceNumber = null;
        this.requestedDate = null;
        this.effectiveDate = credit.getStartDate().toDateTimeAtStartOfDay(accountTimeZone);
        this.reason = null;
        this.accountId = credit.getAccountId();
    }

    public CreditJson(final InvoiceItem credit, final DateTimeZone timeZone) {
        this(credit, timeZone, null);
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

    public DateTime getRequestedDate() {
        return requestedDate;
    }

    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    public String getReason() {
        return reason;
    }

    public UUID getAccountId() {
        return accountId;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("CreditJson");
        sb.append("{creditAmount=").append(creditAmount);
        sb.append(", invoiceId=").append(invoiceId);
        sb.append(", invoiceNumber='").append(invoiceNumber).append('\'');
        sb.append(", requestedDate=").append(requestedDate);
        sb.append(", effectiveDate=").append(effectiveDate);
        sb.append(", reason='").append(reason).append('\'');
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
        if (!((effectiveDate == null && that.effectiveDate == null) ||
              (effectiveDate != null && that.effectiveDate != null && effectiveDate.compareTo(that.effectiveDate) == 0))) {
            return false;
        }
        if (invoiceId != null ? !invoiceId.equals(that.invoiceId) : that.invoiceId != null) {
            return false;
        }
        if (invoiceNumber != null ? !invoiceNumber.equals(that.invoiceNumber) : that.invoiceNumber != null) {
            return false;
        }
        if (reason != null ? !reason.equals(that.reason) : that.reason != null) {
            return false;
        }
        if (!((requestedDate == null && that.requestedDate == null) ||
              (requestedDate != null && that.requestedDate != null && requestedDate.compareTo(that.requestedDate) == 0))) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = creditAmount != null ? creditAmount.hashCode() : 0;
        result = 31 * result + (invoiceId != null ? invoiceId.hashCode() : 0);
        result = 31 * result + (invoiceNumber != null ? invoiceNumber.hashCode() : 0);
        result = 31 * result + (requestedDate != null ? requestedDate.hashCode() : 0);
        result = 31 * result + (effectiveDate != null ? effectiveDate.hashCode() : 0);
        result = 31 * result + (reason != null ? reason.hashCode() : 0);
        return result;
    }
}
