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

package com.ning.billing.payment.api;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.google.common.base.Objects;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;

public class PaymentAttempt {
    private final UUID paymentAttemptId;
    private final UUID invoiceId;
    private final UUID accountId;
    private final BigDecimal amount;
    private final Currency currency;
    private final String paymentId;
    private final DateTime invoiceDate;
    private final DateTime paymentAttemptDate;
    private final Integer retryCount;
    private final DateTime createdDate;
    private final DateTime updatedDate;

    public PaymentAttempt(UUID paymentAttemptId,
                          UUID invoiceId,
                          UUID accountId,
                          BigDecimal amount,
                          Currency currency,
                          DateTime invoiceDate,
                          DateTime paymentAttemptDate,
                          String paymentId,
                          Integer retryCount,
                          DateTime createdDate,
                          DateTime updatedDate) {
        this.paymentAttemptId = paymentAttemptId;
        this.invoiceId = invoiceId;
        this.accountId = accountId;
        this.amount = amount;
        this.currency = currency;
        this.invoiceDate = invoiceDate;
        this.paymentAttemptDate = paymentAttemptDate == null ? new DateTime(DateTimeZone.UTC) : paymentAttemptDate;
        this.paymentId = paymentId;
        this.retryCount = retryCount == null ? 0 : retryCount;
        this.createdDate = createdDate;
        this.updatedDate = updatedDate;
    }

    public PaymentAttempt(UUID paymentAttemptId,
                          UUID invoiceId,
                          UUID accountId,
                          BigDecimal amount,
                          Currency currency,
                          DateTime invoiceDate,
                          DateTime paymentAttemptDate,
                          String paymentId,
                          Integer retryCount) {
        this(paymentAttemptId,
             invoiceId,
             accountId,
             amount,
             currency,
             invoiceDate,
             paymentAttemptDate,
             paymentId,
             retryCount,
             null,
             null);
    }

    public PaymentAttempt(UUID paymentAttemptId, UUID invoiceId, UUID accountId, BigDecimal amount, Currency currency, DateTime invoiceDate, DateTime paymentAttemptDate) {
        this(paymentAttemptId, invoiceId, accountId, amount, currency, invoiceDate, paymentAttemptDate, null, null);
    }

    public PaymentAttempt(UUID paymentAttemptId, UUID invoiceId, UUID accountId, DateTime invoiceDate, DateTime paymentAttemptDate) {
        this(paymentAttemptId, invoiceId, accountId, null, null, invoiceDate, paymentAttemptDate, null, null);
    }

    public PaymentAttempt(UUID paymentAttemptId, Invoice invoice) {
        this(paymentAttemptId, invoice.getId(), invoice.getAccountId(), invoice.getBalance(), invoice.getCurrency(), invoice.getInvoiceDate(), null, null, null);
    }

    public DateTime getInvoiceDate() {
        return invoiceDate;
    }

    public UUID getPaymentAttemptId() {
        return paymentAttemptId;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public DateTime getPaymentAttemptDate() {
        return paymentAttemptDate;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public DateTime getCreatedDate() {
        return createdDate;
    }

    public DateTime getUpdatedDate() {
        return updatedDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    @Override
    public String toString() {
        return "PaymentAttempt [paymentAttemptId=" + paymentAttemptId + ", invoiceId=" + invoiceId + ", accountId=" + accountId + ", amount=" + amount + ", currency=" + currency + ", paymentId=" + paymentId + ", invoiceDate=" + invoiceDate + ", paymentAttemptDate=" + paymentAttemptDate + ", retryCount=" + retryCount + ", createdDate=" + createdDate + ", updatedDate=" + updatedDate + "]";
    }

    public Builder cloner() {
        return new Builder(this);
    }

    public static class Builder {
        private UUID paymentAttemptId;
        private UUID invoiceId;
        private UUID accountId;
        private BigDecimal amount;
        private Currency currency;
        private DateTime invoiceDate;
        private DateTime paymentAttemptDate;
        private String paymentId;
        private Integer retryCount;
        private DateTime createdDate;
        private DateTime updatedDate;

        public Builder() {
        }

        public Builder(PaymentAttempt src) {
            this.paymentAttemptId = src.paymentAttemptId;
            this.invoiceId = src.invoiceId;
            this.accountId = src.accountId;
            this.amount = src.amount;
            this.currency = src.currency;
            this.invoiceDate = src.invoiceDate;
            this.paymentAttemptDate = src.paymentAttemptDate;
            this.paymentId = src.paymentId;
            this.retryCount = src.retryCount;
            this.createdDate = src.createdDate;
            this.updatedDate = src.updatedDate;
        }

        public Builder setPaymentAttemptId(UUID paymentAttemptId) {
            this.paymentAttemptId = paymentAttemptId;
            return this;
        }

        public Builder setInvoiceId(UUID invoiceId) {
            this.invoiceId = invoiceId;
            return this;
        }

        public Builder setAccountId(UUID accountId) {
            this.accountId = accountId;
            return this;
        }

        public Builder setAmount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public Builder setCurrency(Currency currency) {
            this.currency = currency;
            return this;
        }

        public Builder setCreatedDate(DateTime createdDate) {
            this.createdDate = createdDate;
            return this;
        }

        public Builder setUpdatedDate(DateTime updatedDate) {
            this.updatedDate = updatedDate;
            return this;
        }

        public Builder setInvoiceDate(DateTime invoiceDate) {
            this.invoiceDate = invoiceDate;
            return this;
        }

        public Builder setPaymentAttemptDate(DateTime paymentAttemptDate) {
            this.paymentAttemptDate = paymentAttemptDate;
            return this;
        }

        public Builder setPaymentId(String paymentId) {
            this.paymentId = paymentId;
            return this;
        }

        public Builder setRetryCount(Integer retryCount) {
            this.retryCount = retryCount;
            return this;
        }

        public PaymentAttempt build() {
            return new PaymentAttempt(paymentAttemptId,
                                      invoiceId,
                                      accountId,
                                      amount,
                                      currency,
                                      invoiceDate,
                                      paymentAttemptDate,
                                      paymentId,
                                      retryCount,
                                      createdDate,
                                      updatedDate);
        }

    }

    @Override
    public int hashCode() {
        return Objects.hashCode(paymentAttemptId,
                                invoiceId,
                                accountId,
                                amount,
                                currency,
                                invoiceDate,
                                paymentAttemptDate,
                                paymentId,
                                retryCount,
                                createdDate,
                                updatedDate);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final PaymentAttempt that = (PaymentAttempt) o;

        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) return false;
        if (amount != null ? !(amount.compareTo(that.amount) == 0) : that.amount != null) return false;
        if (createdDate != null ? !(getUnixTimestamp(createdDate) == getUnixTimestamp(that.createdDate)) : that.createdDate != null) return false;
        if (currency != that.currency) return false;
        if (invoiceDate != null ? !(getUnixTimestamp(invoiceDate) == getUnixTimestamp(that.invoiceDate)) : that.invoiceDate != null) return false;
        if (invoiceId != null ? !invoiceId.equals(that.invoiceId) : that.invoiceId != null) return false;
        if (paymentAttemptDate != null ? !(getUnixTimestamp(paymentAttemptDate) == getUnixTimestamp(that.paymentAttemptDate)) : that.paymentAttemptDate != null)
            return false;
        if (paymentAttemptId != null ? !paymentAttemptId.equals(that.paymentAttemptId) : that.paymentAttemptId != null)
            return false;
        if (paymentId != null ? !paymentId.equals(that.paymentId) : that.paymentId != null) return false;
        if (retryCount != null ? !retryCount.equals(that.retryCount) : that.retryCount != null) return false;
        if (updatedDate != null ? !(getUnixTimestamp(updatedDate) == getUnixTimestamp(that.updatedDate)) : that.updatedDate != null) return false;

        return true;
    }

    private static long getUnixTimestamp(final DateTime dateTime) {
        return dateTime.getMillis() / 1000;
    }
}
