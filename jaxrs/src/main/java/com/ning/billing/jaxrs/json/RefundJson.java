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

    private final String refundId;
    private final String paymentId;
    private final BigDecimal refundAmount;
    private final Boolean isAdjusted;

    public RefundJson(Refund input) {
        this(input.getId().toString(), input.getPaymentId().toString(), input.getRefundAmount(), input.isAdjusted());
    }

    @JsonCreator
    public RefundJson(@JsonProperty("refund_id") final String refundId,
            @JsonProperty("paymentId") String paymentId,
            @JsonProperty("refundAmount") BigDecimal refundAmount,
            @JsonProperty("adjusted") final Boolean isAdjusted) {
        this.refundId = refundId;
        this.paymentId = paymentId;
        this.refundAmount = refundAmount;
        this.isAdjusted = isAdjusted;
    }

    public RefundJson() {
        this(null, null, null, null);
    }

    public String getRefundId() {
        return refundId;
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

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((isAdjusted == null) ? 0 : isAdjusted.hashCode());
        result = prime * result
                + ((paymentId == null) ? 0 : paymentId.hashCode());
        result = prime * result
                + ((refundAmount == null) ? 0 : refundAmount.hashCode());
        result = prime * result
                + ((refundId == null) ? 0 : refundId.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (! this.equalsNoId(obj)) {
            return false;
        } else {
            RefundJson other = (RefundJson) obj;
            if (getRefundId() == null) {
                return other.getRefundId() == null;
            } else {
                return getRefundId().equals(other.getRefundId());
            }
        }
    }

    public boolean equalsNoId(final Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        RefundJson other = (RefundJson) obj;
        if (isAdjusted == null) {
            if (other.isAdjusted != null)
                return false;
        } else if (!isAdjusted.equals(other.isAdjusted))
            return false;
        if (paymentId == null) {
            if (other.paymentId != null)
                return false;
        } else if (!paymentId.equals(other.paymentId))
            return false;
        if (refundAmount == null) {
            if (other.refundAmount != null)
                return false;
        } else if (!refundAmount.equals(other.refundAmount)) {
            return false;
        }
        return true;
    }


}
