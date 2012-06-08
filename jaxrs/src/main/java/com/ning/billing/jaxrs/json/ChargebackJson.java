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

import org.joda.time.DateTime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ning.billing.invoice.api.InvoicePayment;

// TODO: populate reason code, requested date from audit log
public class ChargebackJson {
    private final DateTime   requestedDate;
    private final DateTime   effectiveDate;
    private final BigDecimal chargebackAmount;
    private final String     paymentId;
    private final String     reason;

    @JsonCreator
    public ChargebackJson(@JsonProperty("requestedDate") final DateTime requestedDate,
                          @JsonProperty("effectiveDate") final DateTime effectiveDate,
                          @JsonProperty("chargebackAmount") final BigDecimal chargebackAmount,
                          @JsonProperty("paymentId") final String paymentId,
                          @JsonProperty("reason") final String reason) {
        this.requestedDate = requestedDate;
        this.effectiveDate = effectiveDate;
        this.chargebackAmount = chargebackAmount;
        this.paymentId = paymentId;
        this.reason = reason;
    }

    public ChargebackJson(final InvoicePayment chargeback) {
        this.requestedDate = null;
        this.effectiveDate = chargeback.getPaymentAttemptDate();
        this.chargebackAmount = chargeback.getAmount().negate();
        this.paymentId = chargeback.getReversedInvoicePaymentId().toString();
        this.reason = null;

    }

    public DateTime getRequestedDate() {
        return requestedDate;
    }

    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    public BigDecimal getChargebackAmount() {
        return chargebackAmount;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public String getReason() {
        return reason;
    }
}
