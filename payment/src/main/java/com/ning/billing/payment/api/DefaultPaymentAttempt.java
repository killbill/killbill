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

import com.ning.billing.util.entity.EntityBase;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.google.common.base.Objects;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;

public class DefaultPaymentAttempt extends EntityBase implements PaymentAttempt  {
    private final UUID invoiceId;
    private final UUID accountId;
    private final BigDecimal amount;
    private final Currency currency;
    private final String paymentId;
    private final DateTime invoiceDate;
    private final DateTime paymentAttemptDate;
    private final Integer retryCount;

    public DefaultPaymentAttempt(UUID id,
                          UUID invoiceId,
                          UUID accountId,
                          BigDecimal amount,
                          Currency currency,
                          DateTime invoiceDate,
                          DateTime paymentAttemptDate,
                          String paymentId,
                          Integer retryCount) {
        super(id);
        this.invoiceId = invoiceId;
        this.accountId = accountId;
        this.amount = amount;
        this.currency = currency;
        this.invoiceDate = invoiceDate;
        this.paymentAttemptDate = paymentAttemptDate == null ? new DateTime(DateTimeZone.UTC) : paymentAttemptDate;
        this.paymentId = paymentId;
        this.retryCount = retryCount == null ? 0 : retryCount;
    }

    public DefaultPaymentAttempt(UUID paymentAttemptId, UUID invoiceId, UUID accountId, BigDecimal amount, Currency currency, DateTime invoiceDate, DateTime paymentAttemptDate) {
        this(paymentAttemptId, invoiceId, accountId, amount, currency, invoiceDate, paymentAttemptDate, null, null);
    }

    public DefaultPaymentAttempt(UUID paymentAttemptId, UUID invoiceId, UUID accountId, DateTime invoiceDate, DateTime paymentAttemptDate) {
        this(paymentAttemptId, invoiceId, accountId, null, null, invoiceDate, paymentAttemptDate, null, null);
    }

    public DefaultPaymentAttempt(UUID paymentAttemptId, Invoice invoice) {
        this(paymentAttemptId, invoice.getId(), invoice.getAccountId(), invoice.getBalance(), invoice.getCurrency(), invoice.getInvoiceDate(), null, null, null);
    }

    @Override public DateTime getInvoiceDate() {
        return invoiceDate;
    }

    @Override public UUID getId() {
        return id;
    }

    @Override public String getPaymentId() {
        return paymentId;
    }

    @Override public DateTime getPaymentAttemptDate() {
        return paymentAttemptDate;
    }

    @Override public UUID getInvoiceId() {
        return invoiceId;
    }

    @Override public UUID getAccountId() {
        return accountId;
    }

    @Override public BigDecimal getAmount() {
        return amount;
    }

    @Override public Currency getCurrency() {
        return currency;
    }

    @Override public Integer getRetryCount() {
        return retryCount;
    }

    @Override
    public String toString() {
        return "PaymentAttempt [paymentAttemptId=" + id + ", invoiceId=" + invoiceId + ", accountId=" + accountId + ", amount=" + amount + ", currency=" + currency + ", paymentId=" + paymentId + ", invoiceDate=" + invoiceDate + ", paymentAttemptDate=" + paymentAttemptDate + ", retryCount=" + retryCount + "]";
    }

    public Builder cloner() {
        return new Builder(this);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id,
                                invoiceId,
                                accountId,
                                amount,
                                currency,
                                invoiceDate,
                                paymentAttemptDate,
                                paymentId,
                                retryCount);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final PaymentAttempt that = (PaymentAttempt) o;

        if (accountId != null ? !accountId.equals(that.getAccountId()) : that.getAccountId() != null) return false;
        if (amount != null ? !(amount.compareTo(that.getAmount()) == 0) : that.getAmount() != null) return false;
        if (currency != that.getCurrency()) return false;
        if (invoiceDate == null ? that.getInvoiceDate() != null : invoiceDate.compareTo(that.getInvoiceDate()) != 0) return false;
        if (invoiceId != null ? !invoiceId.equals(that.getInvoiceId()) : that.getInvoiceId() != null) return false;
        if (paymentAttemptDate == null ? that.getPaymentAttemptDate() != null : paymentAttemptDate.compareTo(that.getPaymentAttemptDate()) != 0) return false;
        if (id != null ? !id.equals(that.getId()) : that.getId() != null)
            return false;
        if (paymentId != null ? !paymentId.equals(that.getPaymentId()) : that.getPaymentId() != null) return false;
        if (retryCount != null ? !retryCount.equals(that.getRetryCount()) : that.getRetryCount() != null) return false;

        return true;
    }

        public static class Builder {
        private UUID id;
        private UUID invoiceId;
        private UUID accountId;
        private BigDecimal amount;
        private Currency currency;
        private DateTime invoiceDate;
        private DateTime paymentAttemptDate;
        private String paymentId;
        private Integer retryCount;

        public Builder() {
        }

        public Builder(PaymentAttempt src) {
            this.id = src.getId();
            this.invoiceId = src.getInvoiceId();
            this.accountId = src.getAccountId();
            this.amount = src.getAmount();
            this.currency = src.getCurrency();
            this.invoiceDate = src.getInvoiceDate();
            this.paymentAttemptDate = src.getPaymentAttemptDate();
            this.paymentId = src.getPaymentId();
            this.retryCount = src.getRetryCount();
        }

        public Builder setPaymentAttemptId(UUID paymentAttemptId) {
            this.id = paymentAttemptId;
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
            return new DefaultPaymentAttempt(id,
                                      invoiceId,
                                      accountId,
                                      amount,
                                      currency,
                                      invoiceDate,
                                      paymentAttemptDate,
                                      paymentId,
                                      retryCount);
        }

    }
}
