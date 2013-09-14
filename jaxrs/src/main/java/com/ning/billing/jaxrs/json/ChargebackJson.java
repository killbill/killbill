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
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;

import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.util.audit.AuditLog;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ChargebackJson extends JsonBase {

    private final String accountId;
    private final DateTime requestedDate;
    private final DateTime effectiveDate;
    private final BigDecimal amount;
    private final String paymentId;
    private final String currency;

    @JsonCreator
    public ChargebackJson(@JsonProperty("accountId") final String accountId,
                          @JsonProperty("requestedDate") final DateTime requestedDate,
                          @JsonProperty("effectiveDate") final DateTime effectiveDate,
                          @JsonProperty("amount") final BigDecimal chargebackAmount,
                          @JsonProperty("paymentId") final String paymentId,
                          @JsonProperty("currency") final String currency,
                          @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(auditLogs);
        this.accountId = accountId;
        this.requestedDate = requestedDate;
        this.effectiveDate = effectiveDate;
        this.amount = chargebackAmount;
        this.paymentId = paymentId;
        this.currency = currency;
    }

    public ChargebackJson(final UUID accountId, final InvoicePayment chargeback) {
        this(accountId, chargeback, null);
    }

    public ChargebackJson(final UUID accountId, final InvoicePayment chargeback, @Nullable final List<AuditLog> auditLogs) {
        this(accountId.toString(), chargeback.getPaymentDate(), chargeback.getPaymentDate(), chargeback.getAmount().negate(),
             chargeback.getPaymentId().toString(), chargeback.getCurrency().toString(), toAuditLogJson(auditLogs));
    }

    public String getAccountId() {
        return accountId;
    }

    public DateTime getRequestedDate() {
        return requestedDate;
    }

    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getPaymentId() {
        return paymentId;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final ChargebackJson that = (ChargebackJson) o;

        if (!((amount == null && that.amount == null) ||
              (amount != null && that.amount != null && amount.compareTo(that.amount) == 0))) {
            return false;
        }
        if (!((effectiveDate == null && that.effectiveDate == null) ||
              (effectiveDate != null && that.effectiveDate != null && effectiveDate.compareTo(that.effectiveDate) == 0))) {
            return false;
        }
        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (paymentId != null ? !paymentId.equals(that.paymentId) : that.paymentId != null) {
            return false;
        }
        if (currency != null ? !currency.equals(that.currency) : that.currency != null) {
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
        int result = requestedDate != null ? requestedDate.hashCode() : 0;
        result = 31 * result + (effectiveDate != null ? effectiveDate.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (accountId != null ? accountId.hashCode() : 0);
        result = 31 * result + (paymentId != null ? paymentId.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ChargebackJson{" +
               "accountId='" + accountId + '\'' +
               ", requestedDate=" + requestedDate +
               ", effectiveDate=" + effectiveDate +
               ", amount=" + amount +
               ", paymentId='" + paymentId + '\'' +
               ", currency='" + currency + '\'' +
               '}';
    }
}
