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
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;

import com.ning.billing.payment.api.Payment;
import com.ning.billing.util.audit.AuditLog;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class PaymentJsonWithBundleKeys extends PaymentJsonSimple {

    private final String bundleKeys;
    private final List<RefundJson> refunds;
    private final List<ChargebackJson> chargebacks;

    @JsonCreator
    public PaymentJsonWithBundleKeys(@JsonProperty("amount") final BigDecimal amount,
                                     @JsonProperty("paidAmount") final BigDecimal paidAmount,
                                     @JsonProperty("accountId") final String accountId,
                                     @JsonProperty("invoiceId") final String invoiceId,
                                     @JsonProperty("paymentId") final String paymentId,
                                     @JsonProperty("paymentMethodId") final String paymentMethodId,
                                     @JsonProperty("requestedDate") final DateTime requestedDate,
                                     @JsonProperty("effectiveDate") final DateTime effectiveDate,
                                     @JsonProperty("retryCount") final Integer retryCount,
                                     @JsonProperty("currency") final String currency,
                                     @JsonProperty("status") final String status,
                                     @JsonProperty("gatewayErrorCode") final String gatewayErrorCode,
                                     @JsonProperty("gatewayErrorMsg") final String gatewayErrorMsg,
                                     @JsonProperty("extFirstPaymentIdRef") final String extFirstPaymentIdRef,
                                     @JsonProperty("extSecondPaymentIdRef") final String extSecondPaymentIdRef,
                                     @JsonProperty("externalBundleKeys") final String bundleKeys,
                                     @JsonProperty("refunds") final List<RefundJson> refunds,
                                     @JsonProperty("chargebacks") final List<ChargebackJson> chargebacks,
                                     @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(amount, paidAmount, accountId, invoiceId, paymentId, paymentMethodId, requestedDate, effectiveDate,
              retryCount, currency, status, gatewayErrorCode, gatewayErrorMsg, extFirstPaymentIdRef,
              extSecondPaymentIdRef, auditLogs);
        this.bundleKeys = bundleKeys;
        this.refunds = refunds;
        this.chargebacks = chargebacks;
    }

    public PaymentJsonWithBundleKeys(final Payment payment, final String status, final int nbOfPaymentAttempts, final String bundleExternalKey,
                                     final UUID accountId, final List<RefundJson> refunds, final List<ChargebackJson> chargebacks) {
        this(payment, status, nbOfPaymentAttempts, bundleExternalKey, accountId, refunds, chargebacks, null);
    }

    public PaymentJsonWithBundleKeys(final Payment payment, final String status, final int nbOfPaymentAttempts, final String bundleExternalKey,
                                     final UUID accountId, final List<RefundJson> refunds, final List<ChargebackJson> chargebacks,
                                     @Nullable final List<AuditLog> auditLogs) {
        this(payment.getAmount(), payment.getPaidAmount(), accountId.toString(),
             payment.getInvoiceId().toString(), payment.getId().toString(),
             payment.getPaymentMethodId().toString(),
             payment.getEffectiveDate(), payment.getEffectiveDate(),
             nbOfPaymentAttempts, payment.getCurrency().toString(), status,
             payment.getAttempts().get(nbOfPaymentAttempts - 1).getGatewayErrorCode(),
             payment.getAttempts().get(nbOfPaymentAttempts - 1).getGatewayErrorMsg(),
             payment.getExtFirstPaymentIdRef(), payment.getExtSecondPaymentIdRef(),
             bundleExternalKey, refunds, chargebacks, toAuditLogJson(auditLogs));
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
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        final PaymentJsonWithBundleKeys that = (PaymentJsonWithBundleKeys) o;

        if (bundleKeys != null ? !bundleKeys.equals(that.bundleKeys) : that.bundleKeys != null) {
            return false;
        }
        if (chargebacks != null ? !chargebacks.equals(that.chargebacks) : that.chargebacks != null) {
            return false;
        }
        if (refunds != null ? !refunds.equals(that.refunds) : that.refunds != null) {
            return false;
        }

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
