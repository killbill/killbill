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
import java.util.UUID;

import org.joda.time.DateTime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ning.billing.util.clock.DefaultClock;

public class PaymentJsonSimple {
    private final BigDecimal paidAmount;

    private final BigDecimal amount;

    private final UUID invoiceId;

    private final UUID paymentId;

    private final DateTime requestedDate;

    private final DateTime effectiveDate;

    private final Integer retryCount;

    private final String currency;

    private final String status;

    private final UUID accountId;

    public PaymentJsonSimple() {
        this.amount = null;
        this.paidAmount = null;
        this.invoiceId = null;
        this.paymentId = null;
        this.requestedDate = null;
        this.effectiveDate = null;
        this.currency = null;
        this.retryCount = null;
        this.status = null;
        this.accountId = null;
    }

    @JsonCreator
    public PaymentJsonSimple(@JsonProperty("amount") final BigDecimal amount,
                             @JsonProperty("paidAmount") final BigDecimal paidAmount,
                             @JsonProperty("invoiceId") final UUID invoiceId,
                             @JsonProperty("paymentId") final UUID paymentId,
                             @JsonProperty("requestedDate") final DateTime requestedDate,
                             @JsonProperty("effectiveDate") final DateTime effectiveDate,
                             @JsonProperty("retryCount") final Integer retryCount,
                             @JsonProperty("currency") final String currency,
                             @JsonProperty("status") final String status,
                             @JsonProperty("accountId") final UUID accountId) {
        super();
        this.amount = amount;
        this.paidAmount = paidAmount;
        this.invoiceId = invoiceId;
        this.paymentId = paymentId;
        this.requestedDate = DefaultClock.toUTCDateTime(requestedDate);
        this.effectiveDate = DefaultClock.toUTCDateTime(effectiveDate);
        this.currency = currency;
        this.retryCount = retryCount;
        this.status = status;
        this.accountId = accountId;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public UUID getPaymentId() {
        return paymentId;
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

    public UUID getAccountId() {
        return accountId;
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
        if (amount != null ? !amount.equals(that.amount) : that.amount != null) {
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
        if (paidAmount != null ? !paidAmount.equals(that.paidAmount) : that.paidAmount != null) {
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
