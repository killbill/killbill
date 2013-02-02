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

import com.ning.billing.payment.api.Payment;
import com.ning.billing.util.audit.AuditLog;
import com.ning.billing.util.clock.DefaultClock;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class PaymentJsonSimple extends JsonBase {

    private final BigDecimal paidAmount;
    private final BigDecimal amount;
    private final String accountId;
    private final String invoiceId;
    private final String paymentId;
    private final DateTime requestedDate;
    private final DateTime effectiveDate;
    private final Integer retryCount;
    private final String currency;
    private final String status;
    private final String gatewayErrorCode;
    private final String gatewayErrorMsg;
    private final String paymentMethodId;
    private final String extFirstPaymentIdRef;
    private final String extSecondPaymentIdRef;

    @JsonCreator
    public PaymentJsonSimple(@JsonProperty("amount") final BigDecimal amount,
                             @JsonProperty("paidAmount") final BigDecimal paidAmount,
                             @JsonProperty("accountId") final String accountId,
                             @JsonProperty("invoiceId") final String invoiceId,
                             @JsonProperty("paymentId") final String paymentId,
                             @JsonProperty("paymentMethodId") final String paymentMethodId,
                             @JsonProperty("requestedDate") final DateTime requestedDate,
                             @JsonProperty("effectiveDate") final DateTime effectiveDate,
                             @JsonProperty("retryCount") final Integer retryCount,
                             @JsonProperty("currency") final String currency,
                             @JsonProperty("status") final String status,
                             @JsonProperty("gatewayErrorCode") final String gatewayErrorCode,
                             @JsonProperty("gatewayErrorMsg") final String gatewayErrorMsg,
                             @JsonProperty("extFirstPaymentIdRef") final String extFirstPaymentIdRef,
                             @JsonProperty("extSecondPaymentIdRef") final String extSecondPaymentIdRef,
                             @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(auditLogs);
        this.amount = amount;
        this.paidAmount = paidAmount;
        this.invoiceId = invoiceId;
        this.accountId = accountId;
        this.paymentId = paymentId;
        this.paymentMethodId = paymentMethodId;
        this.requestedDate = DefaultClock.toUTCDateTime(requestedDate);
        this.effectiveDate = DefaultClock.toUTCDateTime(effectiveDate);
        this.currency = currency;
        this.retryCount = retryCount;
        this.status = status;
        this.gatewayErrorCode = gatewayErrorCode;
        this.gatewayErrorMsg = gatewayErrorMsg;
        this.extFirstPaymentIdRef = extFirstPaymentIdRef;
        this.extSecondPaymentIdRef = extSecondPaymentIdRef;
    }

    public PaymentJsonSimple(final Payment src, final List<AuditLog> auditLogs) {
        this(src.getAmount(), src.getPaidAmount(), src.getAccountId().toString(), src.getInvoiceId().toString(),
             src.getId().toString(), src.getPaymentMethodId().toString(), src.getEffectiveDate(), src.getEffectiveDate(),
             src.getAttempts().size(), src.getCurrency().toString(), src.getPaymentStatus().toString(),
             src.getAttempts().get(src.getAttempts().size() - 1).getGatewayErrorCode(), src.getAttempts().get(src.getAttempts().size() - 1).getGatewayErrorMsg(),
             src.getExtFirstPaymentIdRef(), src.getExtSecondPaymentIdRef(), toAuditLogJson(auditLogs));
    }

    public PaymentJsonSimple(final Payment payment) {
        this(payment, null);
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public String getPaymentMethodId() {
        return paymentMethodId;
    }

    public DateTime getRequestedDate() {
        return DefaultClock.toUTCDateTime(requestedDate);
    }

    public DateTime getEffectiveDate() {
        return DefaultClock.toUTCDateTime(effectiveDate);
    }

    public String getStatus() {
        return status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getGatewayErrorCode() {
        return gatewayErrorCode;
    }

    public String getGatewayErrorMsg() {
        return gatewayErrorMsg;
    }

    public String getExtFirstPaymentIdRef() {
        return extFirstPaymentIdRef;
    }

    public String getExtSecondPaymentIdRef() {
        return extSecondPaymentIdRef;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final PaymentJsonSimple that = (PaymentJsonSimple) o;

        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (!((amount == null && that.amount == null) ||
              (amount != null && that.amount != null && amount.compareTo(that.amount) == 0))) {
            return false;
        }
        if (currency != null ? !currency.equals(that.currency) : that.currency != null) {
            return false;
        }
        if (!((effectiveDate == null && that.effectiveDate == null) ||
              (effectiveDate != null && that.effectiveDate != null && effectiveDate.compareTo(that.effectiveDate) == 0))) {
            return false;
        }
        if (invoiceId != null ? !invoiceId.equals(that.invoiceId) : that.invoiceId != null) {
            return false;
        }
        if (!((paidAmount == null && that.paidAmount == null) ||
              (paidAmount != null && that.paidAmount != null && paidAmount.compareTo(that.paidAmount) == 0))) {
            return false;
        }
        if (paymentId != null ? !paymentId.equals(that.paymentId) : that.paymentId != null) {
            return false;
        }
        if (!((requestedDate == null && that.requestedDate == null) ||
              (requestedDate != null && that.requestedDate != null && requestedDate.compareTo(that.requestedDate) == 0))) {
            return false;
        }
        if (retryCount != null ? !retryCount.equals(that.retryCount) : that.retryCount != null) {
            return false;
        }
        if (status != null ? !status.equals(that.status) : that.status != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = paidAmount != null ? paidAmount.hashCode() : 0;
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (invoiceId != null ? invoiceId.hashCode() : 0);
        result = 31 * result + (paymentId != null ? paymentId.hashCode() : 0);
        result = 31 * result + (requestedDate != null ? requestedDate.hashCode() : 0);
        result = 31 * result + (effectiveDate != null ? effectiveDate.hashCode() : 0);
        result = 31 * result + (retryCount != null ? retryCount.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (accountId != null ? accountId.hashCode() : 0);
        return result;
    }
}
