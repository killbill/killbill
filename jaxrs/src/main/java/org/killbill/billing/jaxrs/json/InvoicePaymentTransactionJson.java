/*
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

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.api.TransactionType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;

@ApiModel(value="InvoicePaymentTransaction")
public class InvoicePaymentTransactionJson extends PaymentTransactionJson {

    private final Boolean isAdjusted;
    private final List<InvoiceItemJson> adjustments;

    @JsonCreator
    public InvoicePaymentTransactionJson(@JsonProperty("transactionId") final UUID transactionId,
                                         @JsonProperty("transactionExternalKey") final String transactionExternalKey,
                                         @JsonProperty("paymentId") final UUID paymentId,
                                         @JsonProperty("paymentExternalKey") final String paymentExternalKey,
                                         @JsonProperty("transactionType") final TransactionType transactionType,
                                         @JsonProperty("amount") final BigDecimal amount,
                                         @JsonProperty("currency") final Currency currency,
                                         @JsonProperty("effectiveDate") final DateTime effectiveDate,
                                         @JsonProperty("processedAmount") final BigDecimal processedAmount,
                                         @JsonProperty("processedCurrency") final Currency processedCurrency,
                                         @JsonProperty("status") final String status,
                                         @JsonProperty("gatewayErrorCode") final String gatewayErrorCode,
                                         @JsonProperty("gatewayErrorMsg") final String gatewayErrorMsg,
                                         @JsonProperty("firstPaymentReferenceId") final String firstPaymentReferenceId,
                                         @JsonProperty("secondPaymentReferenceId") final String secondPaymentReferenceId,
                                         @JsonProperty("properties") final List<PluginPropertyJson> properties,
                                         @JsonProperty("isAdjusted") final Boolean isAdjusted,
                                         @JsonProperty("adjustments") final List<InvoiceItemJson> adjustments,
                                         @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(transactionId, transactionExternalKey, paymentId, paymentExternalKey, transactionType, amount, currency, effectiveDate, processedAmount, processedCurrency,
              status, gatewayErrorCode, gatewayErrorMsg, firstPaymentReferenceId, secondPaymentReferenceId, properties, auditLogs);
        this.isAdjusted = isAdjusted;
        this.adjustments = adjustments;
    }

    @JsonProperty("isAdjusted")
    public Boolean isAdjusted() {
        return isAdjusted;
    }

    public List<InvoiceItemJson> getAdjustments() {
        return adjustments;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InvoicePaymentTransactionJson)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        final InvoicePaymentTransactionJson that = (InvoicePaymentTransactionJson) o;

        if (adjustments != null ? !adjustments.equals(that.adjustments) : that.adjustments != null) {
            return false;
        }
        if (isAdjusted != null ? !isAdjusted.equals(that.isAdjusted) : that.isAdjusted != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (isAdjusted != null ? isAdjusted.hashCode() : 0);
        result = 31 * result + (adjustments != null ? adjustments.hashCode() : 0);
        return result;
    }
}
