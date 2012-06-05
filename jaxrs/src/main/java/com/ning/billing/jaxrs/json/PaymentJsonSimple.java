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
    public PaymentJsonSimple(@JsonProperty("amount") BigDecimal amount,
            @JsonProperty("paidAmount") BigDecimal paidAmount,
            @JsonProperty("invoiceId") UUID invoiceId,
            @JsonProperty("paymentId") UUID paymentId,
            @JsonProperty("requestedDate") DateTime requestedDate,
            @JsonProperty("effectiveDate") DateTime effectiveDate,
            @JsonProperty("retryCount") Integer retryCount,
            @JsonProperty("currency") String currency,
            @JsonProperty("status") String status,
            @JsonProperty("paymentId") UUID accountId) {
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
}
