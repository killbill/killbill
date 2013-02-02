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

import org.joda.time.DateTime;

import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.util.audit.AuditLog;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ChargebackJson extends JsonBase {

    private final DateTime requestedDate;
    private final DateTime effectiveDate;
    private final BigDecimal chargebackAmount;
    private final String paymentId;
    private final String reason;

    @JsonCreator
    public ChargebackJson(@JsonProperty("requestedDate") final DateTime requestedDate,
                          @JsonProperty("effectiveDate") final DateTime effectiveDate,
                          @JsonProperty("chargebackAmount") final BigDecimal chargebackAmount,
                          @JsonProperty("paymentId") final String paymentId,
                          @JsonProperty("reason") final String reason,
                          @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(auditLogs);
        this.requestedDate = requestedDate;
        this.effectiveDate = effectiveDate;
        this.chargebackAmount = chargebackAmount;
        this.paymentId = paymentId;
        this.reason = reason;
    }

    public ChargebackJson(final InvoicePayment chargeback) {
        this(chargeback, null);
    }

    public ChargebackJson(final InvoicePayment chargeback, @Nullable final List<AuditLog> auditLogs) {
        this(chargeback.getPaymentDate(), chargeback.getPaymentDate(), chargeback.getAmount().negate(),
             chargeback.getPaymentId().toString(), reasonCodeFromAuditLogs(auditLogs), toAuditLogJson(auditLogs));
    }

    public DateTime getRequestedDate() {
        return requestedDate;
    }

    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    public BigDecimal getChargebackAmount() {
        return chargebackAmount;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public String getReason() {
        return reason;
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

        if (!((chargebackAmount == null && that.chargebackAmount == null) ||
              (chargebackAmount != null && that.chargebackAmount != null && chargebackAmount.compareTo(that.chargebackAmount) == 0))) {
            return false;
        }
        if (!((effectiveDate == null && that.effectiveDate == null) ||
              (effectiveDate != null && that.effectiveDate != null && effectiveDate.compareTo(that.effectiveDate) == 0))) {
            return false;
        }
        if (paymentId != null ? !paymentId.equals(that.paymentId) : that.paymentId != null) {
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
        int result = requestedDate != null ? requestedDate.hashCode() : 0;
        result = 31 * result + (effectiveDate != null ? effectiveDate.hashCode() : 0);
        result = 31 * result + (chargebackAmount != null ? chargebackAmount.hashCode() : 0);
        result = 31 * result + (paymentId != null ? paymentId.hashCode() : 0);
        result = 31 * result + (reason != null ? reason.hashCode() : 0);
        return result;
    }
}
