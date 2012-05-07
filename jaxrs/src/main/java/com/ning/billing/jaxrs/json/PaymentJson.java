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

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonView;
import org.joda.time.DateTime;

import com.ning.billing.util.clock.DefaultClock;

public class PaymentJson {

    private final BigDecimal paidAmount;

    private final BigDecimal amount;

    private final String invoiceId;
    
    private final String paymentId;
    
    private final DateTime requestedDate;
    
    private final DateTime effectiveDate;
    
    private final Integer retryCount;
    
    private final String currency;
    
    private final String status;
      
    public PaymentJson() {
        this.amount = null;
        this.paidAmount = null;
        this.invoiceId = null;
        this.paymentId = null;
        this.requestedDate = null;
        this.effectiveDate = null;
        this.currency = null;
        this.retryCount = null;
        this.status = null;
    }

    @JsonCreator
    public PaymentJson(@JsonProperty("amount") BigDecimal amount,
            @JsonProperty("paid_amount") BigDecimal paidAmount,
            @JsonProperty("invoice_id") String invoiceId,
            @JsonProperty("payment_id") String paymentId,
            @JsonProperty("requested_dt") DateTime requestedDate,
            @JsonProperty("effective_dt") DateTime effectiveDate,
            @JsonProperty("retry_count") Integer retryCount,
            @JsonProperty("currency") String currency,            
            @JsonProperty("status") String status) {
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
}
