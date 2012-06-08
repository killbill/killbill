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

public class PaymentJsonWithBundleKeys extends PaymentJsonSimple {
    private final String bundleKeys;

    public PaymentJsonWithBundleKeys() {
        super();
        this.bundleKeys = null;
    }

    @JsonCreator
    public PaymentJsonWithBundleKeys(@JsonProperty("amount") final BigDecimal amount,
                                     @JsonProperty("paidAmount") final BigDecimal paidAmount,
                                     @JsonProperty("invoiceId") final UUID invoiceId,
                                     @JsonProperty("paymentId") final UUID paymentId,
                                     @JsonProperty("requestedDt") final DateTime requestedDate,
                                     @JsonProperty("effectiveDt") final DateTime effectiveDate,
                                     @JsonProperty("retryCount") final Integer retryCount,
                                     @JsonProperty("currency") final String currency,
                                     @JsonProperty("status") final String status,
                                     @JsonProperty("accountId") final UUID accountId,
                                     @JsonProperty("externalBundleKeys") final String bundleKeys) {
        super(amount, paidAmount, invoiceId, paymentId, requestedDate, effectiveDate, retryCount, currency, status, accountId);
        this.bundleKeys = bundleKeys;
    }

    public String getBundleKeys() {
        return bundleKeys;
    }
}
