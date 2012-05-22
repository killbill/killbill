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
    private final UUID paymentId;
    private final DateTime invoiceDate;
    private final DateTime paymentAttemptDate;
    private final Integer retryCount;
    private final DateTime createdDate;
    private final DateTime updatedDate;
    private final PaymentAttemptStatus paymentAttemptStatus;

    public DefaultPaymentAttempt(UUID id,
                          UUID invoiceId,
                          UUID accountId,
                          BigDecimal amount,
                          Currency currency,
                          DateTime invoiceDate,
                          DateTime paymentAttemptDate,
                          UUID paymentId,
                          Integer retryCount,
                          DateTime createdDate,
                          DateTime updatedDate,
                          PaymentAttemptStatus paymentAttemptStatus) {
        super(id);
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
        this.paymentAttemptStatus = paymentAttemptStatus;
    }

    public DefaultPaymentAttempt(UUID paymentAttemptId, Invoice invoice) {
        this(paymentAttemptId, invoice.getId(), invoice.getAccountId(), invoice.getBalance(), invoice.getCurrency(), invoice.getInvoiceDate(), null, null, null, null, null, null);
    }
    
    public DefaultPaymentAttempt(UUID paymentAttemptId, Invoice invoice, PaymentAttemptStatus paymentAttemptStatus) {
        this(paymentAttemptId, invoice.getId(), invoice.getAccountId(), invoice.getBalance(), invoice.getCurrency(), invoice.getInvoiceDate(), null, null, null, null, null, paymentAttemptStatus);
    }

    public DefaultPaymentAttempt(PaymentAttempt input, PaymentAttemptStatus paymentAttemptStatus) {
        this(input.getId(), input.getInvoiceId(), input.getAccountId(), input.getAmount(), input.getCurrency(), input.getInvoiceDate(), null, null, null, null, null, paymentAttemptStatus);
    }


    @Override public DateTime getInvoiceDate() {
        return invoiceDate;
    }

    @Override public UUID getId() {
        return id;
    }

    @Override public UUID getPaymentId() {
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

    @Override public DateTime getCreatedDate() {
        return createdDate;
    }

    @Override public DateTime getUpdatedDate() {
        return updatedDate;
    }
    
    @Override
    public PaymentAttemptStatus getPaymentAttemptStatus() {
        return paymentAttemptStatus;
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
                                retryCount,
                                createdDate, updatedDate);
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
        if (createdDate.compareTo(that.getCreatedDate()) != 0) return false;
        if (updatedDate.compareTo(that.getUpdatedDate()) != 0) return false;

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
        private UUID paymentId;
        private Integer retryCount;
        private DateTime createdDate;
        private DateTime updatedDate;
        private PaymentAttemptStatus paymentAttemptStatus;
        
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
            this.createdDate = src.getCreatedDate();
            this.updatedDate = src.getUpdatedDate();
            this.paymentAttemptStatus = src.getPaymentAttemptStatus();
        }

        public Builder setPaymentAttemptId(UUID paymentAttemptId) {
            this.id = paymentAttemptId;
            return this;
        }

        public Builder setInvoiceId(UUID invoiceId) {
            this.invoiceId = invoiceId;
            return this;
        }

        public Builder setPaymentAttemptStatus(PaymentAttemptStatus paymentAttemptStatus) {
            this.paymentAttemptStatus = paymentAttemptStatus;
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

        public Builder setPaymentId(UUID paymentId) {
            this.paymentId = paymentId;
            return this;
        }

        public Builder setRetryCount(Integer retryCount) {
            this.retryCount = retryCount;
            return this;
        }

        public Builder setCreatedDate(DateTime createdDate) {
            this.createdDate = createdDate;
            return this;
        }

        public Builder setUpdateDate(DateTime updateDate) {
            this.updatedDate = updateDate;
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
                                      retryCount,
                                      createdDate,
                                      updatedDate,
                                      paymentAttemptStatus);
        }
    }
}
