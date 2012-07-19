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
import java.util.List;

import org.joda.time.DateTime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;

public class PaymentJsonWithBundleKeys extends PaymentJsonSimple {

    private final String bundleKeys;
    private final List<RefundJson> refunds;
    private final List<ChargebackJson> chargebacks;

    public PaymentJsonWithBundleKeys() {
        super();
        this.bundleKeys = null;
        this.refunds = ImmutableList.<RefundJson>of();
        this.chargebacks = ImmutableList.<ChargebackJson>of();
    }

    @JsonCreator
    public PaymentJsonWithBundleKeys(@JsonProperty("amount") final BigDecimal amount,
                                     @JsonProperty("paidAmount") final BigDecimal paidAmount,
                                     @JsonProperty("accountId") final String accountId,
                                     @JsonProperty("invoiceId") final String invoiceId,
                                     @JsonProperty("paymentId") final String paymentId,
                                     @JsonProperty("paymentMethodId") final String paymentMethodId,
                                     @JsonProperty("requestedDt") final DateTime requestedDate,
                                     @JsonProperty("effectiveDt") final DateTime effectiveDate,
                                     @JsonProperty("retryCount") final Integer retryCount,
                                     @JsonProperty("currency") final String currency,
                                     @JsonProperty("status") final String status,
                                     @JsonProperty("externalBundleKeys") final String bundleKeys,
                                     @JsonProperty("refunds") final List<RefundJson> refunds,
                                     @JsonProperty("chargebacks") final List<ChargebackJson> chargebacks) {
        super(amount, paidAmount, accountId, invoiceId, paymentId, paymentMethodId, requestedDate, effectiveDate, retryCount, currency, status);
        this.bundleKeys = bundleKeys;
        this.refunds = refunds;
        this.chargebacks = chargebacks;
    }

    public String getBundleKeys() {
        return bundleKeys;
    }

    public List<RefundJson> getRefunds() {
        return refunds;
    }

    public List<ChargebackJson> getChargebacks() {
        return chargebacks;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("PaymentJsonWithBundleKeys");
        sb.append("{bundleKeys='").append(bundleKeys).append('\'');
        sb.append(", refunds=").append(refunds);
        sb.append(", chargebacks=").append(chargebacks);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        final PaymentJsonWithBundleKeys that = (PaymentJsonWithBundleKeys) o;

        if (bundleKeys != null ? !bundleKeys.equals(that.bundleKeys) : that.bundleKeys != null) return false;
        if (chargebacks != null ? !chargebacks.equals(that.chargebacks) : that.chargebacks != null) return false;
        if (refunds != null ? !refunds.equals(that.refunds) : that.refunds != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (bundleKeys != null ? bundleKeys.hashCode() : 0);
        result = 31 * result + (refunds != null ? refunds.hashCode() : 0);
        result = 31 * result + (chargebacks != null ? chargebacks.hashCode() : 0);
        return result;
    }
}
