/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.jaxrs.json;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentAttempt;
import org.killbill.billing.util.audit.AccountAuditLogs;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value="InvoicePayment")
public class InvoicePaymentJson extends PaymentJson {

    private final UUID targetInvoiceId;


    @JsonCreator
    public InvoicePaymentJson(@JsonProperty("targetInvoiceId") final UUID targetInvoiceId,
                              @JsonProperty("accountId") final UUID accountId,
                              @JsonProperty("paymentId") final UUID paymentId,
                              @JsonProperty("paymentNumber") final String paymentNumber,
                              @JsonProperty("paymentExternalKey") final String paymentExternalKey,
                              @JsonProperty("authAmount") final BigDecimal authAmount,
                              @JsonProperty("capturedAmount") final BigDecimal capturedAmount,
                              @JsonProperty("purchasedAmount") final BigDecimal purchasedAmount,
                              @JsonProperty("refundedAmount") final BigDecimal refundedAmount,
                              @JsonProperty("creditedAmount") final BigDecimal creditedAmount,
                              @JsonProperty("currency") final Currency currency,
                              @JsonProperty("paymentMethodId") final UUID paymentMethodId,
                              @JsonProperty("transactions") final List<? extends PaymentTransactionJson> transactions,
                              @JsonProperty("paymentAttempts") final List<PaymentAttemptJson> paymentAttempts,
                              @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(accountId, paymentId, paymentNumber, paymentExternalKey, authAmount, capturedAmount, purchasedAmount, refundedAmount, creditedAmount, currency, paymentMethodId, transactions, paymentAttempts, auditLogs);
        this.targetInvoiceId = targetInvoiceId;
    }

    public InvoicePaymentJson(final Payment payment, @Nullable final UUID invoiceId, @Nullable final AccountAuditLogs accountAuditLogs) {
        super(payment, accountAuditLogs);
        //  TODO should build InvoicePaymentTransactionJson instead of PaymentTransactionJson here.
        this.targetInvoiceId = invoiceId;
    }


        public UUID getTargetInvoiceId() {
        return targetInvoiceId;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InvoicePaymentJson)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        final InvoicePaymentJson that = (InvoicePaymentJson) o;

        if (targetInvoiceId != null ? !targetInvoiceId.equals(that.targetInvoiceId) : that.targetInvoiceId != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (targetInvoiceId != null ? targetInvoiceId.hashCode() : 0);
        return result;
    }
}
