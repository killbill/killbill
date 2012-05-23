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
import org.joda.time.DateTime;

public class PaymentJsonWithBundleKeys extends PaymentJsonSimple {

    private final String bundleKeys;
    
    public PaymentJsonWithBundleKeys() {
        super();
        this.bundleKeys = null;
    }

    @JsonCreator
    public PaymentJsonWithBundleKeys(@JsonProperty("amount") BigDecimal amount,
            @JsonProperty("paidAmount") BigDecimal paidAmount,
            @JsonProperty("accountId") String accountId,
            @JsonProperty("invoiceId") String invoiceId,
            @JsonProperty("paymentId") String paymentId,
            @JsonProperty("requestedDt") DateTime requestedDate,
            @JsonProperty("effectiveDt") DateTime effectiveDate,
            @JsonProperty("retryCount") Integer retryCount,
            @JsonProperty("currency") String currency,            
            @JsonProperty("status") String status,
            @JsonProperty("externalBundleKeys") String bundleKeys) {
        super(amount, paidAmount, accountId, invoiceId, paymentId, requestedDate, effectiveDate, retryCount, currency, status);
        this.bundleKeys = bundleKeys;
    }
    
    public String getBundleKeys() {
        return bundleKeys;
    }
}
