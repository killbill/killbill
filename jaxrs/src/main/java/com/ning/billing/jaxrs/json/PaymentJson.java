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

    @JsonView(BundleTimelineViews.Base.class)
    private final BigDecimal paidAmount;
    
    @JsonView(BundleTimelineViews.Base.class)
    private final String invoiceId;
    
    @JsonView(BundleTimelineViews.Base.class)
    private final String paymentId;
    
    @JsonView(BundleTimelineViews.Base.class)
    private final DateTime requestedDate;
    
    @JsonView(BundleTimelineViews.Base.class)
    private final DateTime effectiveDate;
    
    @JsonView(BundleTimelineViews.Base.class)
    private final String status;

    @JsonCreator
    public PaymentJson(@JsonProperty("paid_amount") BigDecimal paidAmount,
            @JsonProperty("invoice_id") String invoiceId,
            @JsonProperty("payment_id") String paymentId,
            @JsonProperty("requested_dt") DateTime requestedDate,
            @JsonProperty("effective_dt") DateTime effectiveDate,
            @JsonProperty("status") String status) {
        super();
        this.paidAmount = paidAmount;
        this.invoiceId = invoiceId;
        this.paymentId = paymentId;
        this.requestedDate = DefaultClock.toUTCDateTime(requestedDate);
        this.effectiveDate = DefaultClock.toUTCDateTime(effectiveDate);
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
}
