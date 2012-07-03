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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ning.billing.payment.api.Refund;


public class RefundJson {

    private final String paymentId;
    private final BigDecimal refundAmount;
    private final Boolean isAdjusted;

    public RefundJson(Refund input) {
        this(input.getPaymentId().toString(), input.getRefundAmount(), input.isAdjusted());
    }

    @JsonCreator
    public RefundJson(@JsonProperty("paymentId") String paymentId,
            @JsonProperty("refundAmount") BigDecimal refundAmount,
            @JsonProperty("isAdjusted") final Boolean isAdjusted) {
        super();
        this.paymentId = paymentId;
        this.refundAmount = refundAmount;
        this.isAdjusted = isAdjusted;
    }

    public RefundJson() {
        this(null, null, null);
    }

    public String getPaymentId() {
        return paymentId;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public boolean isAdjusted() {
        return isAdjusted;
    }
}
