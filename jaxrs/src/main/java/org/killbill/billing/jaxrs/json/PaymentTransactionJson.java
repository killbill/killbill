/*
 * Copyright 2014 Groupon, Inc
 *
 * Groupon licenses this file to you under the Apache License, version 2.0
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
import org.killbill.billing.payment.api.PaymentTransaction;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.util.audit.AuditLog;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value="PaymentTransaction", parent = JsonBase.class)
public class PaymentTransactionJson extends JsonBase {

    private final UUID transactionId;
    private final String transactionExternalKey;
    @ApiModelProperty(value = "Associated payment id, required when notifying state transitions")
    private final UUID paymentId;
    private final String paymentExternalKey;
    @ApiModelProperty(dataType = "org.killbill.billing.payment.api.TransactionType")
    private final TransactionType transactionType;
    @ApiModelProperty(dataType = "org.joda.time.DateTime")
    private final DateTime effectiveDate;
    @ApiModelProperty(value = "Transaction status, required for state change notifications", dataType = "org.killbill.billing.payment.api.TransactionStatus")
    private final String status;
    @ApiModelProperty(value = "Transaction amount, required except for void operations")
    private final BigDecimal amount;
    @ApiModelProperty(value = "Amount currency (account currency unless specified)", dataType = "org.killbill.billing.catalog.api.Currency")
    private final Currency currency;
    private final BigDecimal processedAmount;
    private final Currency processedCurrency;
    private final String gatewayErrorCode;
    private final String gatewayErrorMsg;
    // Plugin specific fields
    private final String firstPaymentReferenceId;
    private final String secondPaymentReferenceId;
    private final List<PluginPropertyJson> properties;

    @JsonCreator
    public PaymentTransactionJson(@JsonProperty("transactionId") final UUID transactionId,
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
                                  @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(auditLogs);
        this.transactionId = transactionId;
        this.transactionExternalKey = transactionExternalKey;
        this.paymentId = paymentId;
        this.paymentExternalKey = paymentExternalKey;
        this.transactionType = transactionType;
        this.effectiveDate = effectiveDate;
        this.status = status;
        this.amount = amount;
        this.currency = currency;
        this.processedAmount = processedAmount;
        this.processedCurrency = processedCurrency;
        this.gatewayErrorCode = gatewayErrorCode;
        this.gatewayErrorMsg = gatewayErrorMsg;
        this.firstPaymentReferenceId = firstPaymentReferenceId;
        this.secondPaymentReferenceId = secondPaymentReferenceId;
        this.properties = properties;
    }

    public PaymentTransactionJson(final PaymentTransaction transaction, final String paymentExternalKey, @Nullable final List<AuditLog> transactionLogs) {
        this(transaction.getId(),
             transaction.getExternalKey(),
             transaction.getPaymentId(),
             paymentExternalKey,
             transaction.getTransactionType(),
             transaction.getAmount(),
             transaction.getCurrency() != null ? transaction.getCurrency() : null,
             transaction.getEffectiveDate(),
             transaction.getProcessedAmount(),
             transaction.getProcessedCurrency() != null ? transaction.getProcessedCurrency() : null,
             transaction.getTransactionStatus() != null ? transaction.getTransactionStatus().toString() : null,
             transaction.getGatewayErrorCode(),
             transaction.getGatewayErrorMsg(),
             transaction.getPaymentInfoPlugin() == null ? null : transaction.getPaymentInfoPlugin().getFirstPaymentReferenceId(),
             transaction.getPaymentInfoPlugin() == null ? null : transaction.getPaymentInfoPlugin().getSecondPaymentReferenceId(),
             transaction.getPaymentInfoPlugin() == null ? null : toPluginPropertyJson(transaction.getPaymentInfoPlugin().getProperties()),
             toAuditLogJson(transactionLogs));
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public String getTransactionExternalKey() {
        return transactionExternalKey;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    public String getStatus() {
        return status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public String getGatewayErrorCode() {
        return gatewayErrorCode;
    }

    public String getGatewayErrorMsg() {
        return gatewayErrorMsg;
    }

    public String getFirstPaymentReferenceId() {
        return firstPaymentReferenceId;
    }

    public String getSecondPaymentReferenceId() {
        return secondPaymentReferenceId;
    }

    public List<PluginPropertyJson> getProperties() {
        return properties;
    }

    public String getPaymentExternalKey() {
        return paymentExternalKey;
    }

    public BigDecimal getProcessedAmount() {
        return processedAmount;
    }

    public Currency getProcessedCurrency() {
        return processedCurrency;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("PaymentTransactionJson{");
        sb.append("transactionId='").append(transactionId).append('\'');
        sb.append(", paymentId='").append(paymentId).append('\'');
        sb.append(", transactionExternalKey='").append(transactionExternalKey).append('\'');
        sb.append(", transactionType='").append(transactionType).append('\'');
        sb.append(", effectiveDate=").append(effectiveDate);
        sb.append(", status='").append(status).append('\'');
        sb.append(", amount=").append(amount);
        sb.append(", currency='").append(currency).append('\'');
        sb.append(", processedAmount=").append(processedAmount);
        sb.append(", processedCurrency='").append(processedCurrency).append('\'');
        sb.append(", gatewayErrorCode='").append(gatewayErrorCode).append('\'');
        sb.append(", gatewayErrorMsg='").append(gatewayErrorMsg).append('\'');
        sb.append(", firstPaymentReferenceId='").append(firstPaymentReferenceId).append('\'');
        sb.append(", secondPaymentReferenceId='").append(secondPaymentReferenceId).append('\'');
        sb.append(", properties=").append(properties);
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

        final PaymentTransactionJson that = (PaymentTransactionJson) o;

        if (amount != null ? amount.compareTo(that.amount) != 0 : that.amount != null) {
            return false;
        }
        if (processedAmount != null ? processedAmount.compareTo(that.processedAmount) != 0 : that.processedAmount != null) {
            return false;
        }
        if (currency != null ? !currency.equals(that.currency) : that.currency != null) {
            return false;
        }
        if (processedCurrency != null ? !processedCurrency.equals(that.processedCurrency) : that.processedCurrency != null) {
            return false;
        }
        if (transactionExternalKey != null ? !transactionExternalKey.equals(that.transactionExternalKey) : that.transactionExternalKey != null) {
            return false;
        }
        if (paymentId != null ? !paymentId.equals(that.paymentId) : that.paymentId != null) {
            return false;
        }
        if (transactionId != null ? !transactionId.equals(that.transactionId) : that.transactionId != null) {
            return false;
        }
        if (effectiveDate != null ? effectiveDate.compareTo(that.effectiveDate) != 0 : that.effectiveDate != null) {
            return false;
        }
        if (firstPaymentReferenceId != null ? !firstPaymentReferenceId.equals(that.firstPaymentReferenceId) : that.firstPaymentReferenceId != null) {
            return false;
        }
        if (gatewayErrorCode != null ? !gatewayErrorCode.equals(that.gatewayErrorCode) : that.gatewayErrorCode != null) {
            return false;
        }
        if (gatewayErrorMsg != null ? !gatewayErrorMsg.equals(that.gatewayErrorMsg) : that.gatewayErrorMsg != null) {
            return false;
        }
        if (properties != null ? !properties.equals(that.properties) : that.properties != null) {
            return false;
        }
        if (secondPaymentReferenceId != null ? !secondPaymentReferenceId.equals(that.secondPaymentReferenceId) : that.secondPaymentReferenceId != null) {
            return false;
        }
        if (status != null ? !status.equals(that.status) : that.status != null) {
            return false;
        }
        if (transactionType != null ? !transactionType.equals(that.transactionType) : that.transactionType != null) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = transactionId != null ? transactionId.hashCode() : 0;
        result = 31 * result + (paymentId != null ? paymentId.hashCode() : 0);
        result = 31 * result + (transactionExternalKey != null ? transactionExternalKey.hashCode() : 0);
        result = 31 * result + (transactionType != null ? transactionType.hashCode() : 0);
        result = 31 * result + (effectiveDate != null ? effectiveDate.hashCode() : 0);
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (processedAmount != null ? processedAmount.hashCode() : 0);
        result = 31 * result + (processedCurrency != null ? processedCurrency.hashCode() : 0);
        result = 31 * result + (gatewayErrorCode != null ? gatewayErrorCode.hashCode() : 0);
        result = 31 * result + (gatewayErrorMsg != null ? gatewayErrorMsg.hashCode() : 0);
        result = 31 * result + (firstPaymentReferenceId != null ? firstPaymentReferenceId.hashCode() : 0);
        result = 31 * result + (secondPaymentReferenceId != null ? secondPaymentReferenceId.hashCode() : 0);
        result = 31 * result + (properties != null ? properties.hashCode() : 0);
        return result;
    }
}
