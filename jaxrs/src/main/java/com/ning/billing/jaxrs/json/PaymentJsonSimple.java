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

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonView;
import org.joda.time.DateTime;

import com.ning.billing.payment.api.PaymentInfoEvent;
import com.ning.billing.util.clock.DefaultClock;

public class PaymentJsonSimple {

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
      
    public PaymentJsonSimple() {
        this.amount = null;
        this.paidAmount = null;
        this.invoiceId = null;
        this.accountId = null;
        this.paymentId = null;
        this.requestedDate = null;
        this.effectiveDate = null;
        this.currency = null;
        this.retryCount = null;
        this.status = null;
    }

    @JsonCreator
    public PaymentJsonSimple(@JsonProperty("amount") BigDecimal amount,
            @JsonProperty("paidAmount") BigDecimal paidAmount,
            @JsonProperty("accountId") String accountId,            
            @JsonProperty("invoiceId") String invoiceId,
            @JsonProperty("paymentId") String paymentId,
            @JsonProperty("requestedDate") DateTime requestedDate,
            @JsonProperty("effectiveDate") DateTime effectiveDate,
            @JsonProperty("retryCount") Integer retryCount,
            @JsonProperty("currency") String currency,            
            @JsonProperty("status") String status) {
        super();
        this.amount = amount;
        this.paidAmount = paidAmount;
        this.invoiceId = invoiceId;
        this.accountId = accountId;        
        this.paymentId = paymentId;
        this.requestedDate = DefaultClock.toUTCDateTime(requestedDate);
        this.effectiveDate = DefaultClock.toUTCDateTime(effectiveDate);
        this.currency = currency;
        this.retryCount = retryCount;
        this.status = status;
    }

    public PaymentJsonSimple(PaymentInfoEvent src) {
        this.amount = src.getAmount();
        this.paidAmount = src.getAmount(); // STEPH ?
        this.invoiceId = src.getInvoiceId().toString();
        this.accountId = src.getAccountId().toString();
        this.paymentId = src.getId().toString();
        this.requestedDate = src.getCreatedDate();
        this.effectiveDate = src.getEffectiveDate();
        this.currency = null; // Should it really be part of the payment object since this is per account?
        this.retryCount = null; // do we need that?
        this.status = src.getStatus();
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public String getAccountId() {
        return accountId;
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
