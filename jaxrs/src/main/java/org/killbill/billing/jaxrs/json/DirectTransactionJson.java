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

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.billing.payment.api.DirectPaymentTransaction;
import org.killbill.billing.util.audit.AuditLog;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DirectTransactionJson extends JsonBase {

    private final String directTransactionId;
    private final String directTransactionExternalKey;
    private final String directPaymentId;
    private final String directPaymentExternalKey;
    private final String transactionType;
    private final DateTime effectiveDate;
    private final String status;
    private final BigDecimal amount;
    private final String currency;
    private final String gatewayErrorCode;
    private final String gatewayErrorMsg;
    // Plugin specific fields
    private final String firstPaymentReferenceId;
    private final String secondPaymentReferenceId;
    private final List<PluginPropertyJson> properties;

    @JsonCreator
    public DirectTransactionJson(@JsonProperty("directTransactionId") final String directTransactionId,
                                 @JsonProperty("directTransactionExternalKey") final String directTransactionExternalKey,
                                 @JsonProperty("directPaymentId") final String directPaymentId,
                                 @JsonProperty("directPaymentExternalKey") final String directPaymentExternalKey,
                                 @JsonProperty("transactionType") final String transactionType,
                                 @JsonProperty("amount") final BigDecimal amount,
                                 @JsonProperty("currency") final String currency,
                                 @JsonProperty("effectiveDate") final DateTime effectiveDate,
                                 @JsonProperty("status") final String status,
                                 @JsonProperty("gatewayErrorCode") final String gatewayErrorCode,
                                 @JsonProperty("gatewayErrorMsg") final String gatewayErrorMsg,
                                 @JsonProperty("firstPaymentReferenceId") final String firstPaymentReferenceId,
                                 @JsonProperty("secondPaymentReferenceId") final String secondPaymentReferenceId,
                                 @JsonProperty("properties") final List<PluginPropertyJson> properties,
                                 @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(auditLogs);
        this.directTransactionId = directTransactionId;
        this.directTransactionExternalKey = directTransactionExternalKey;
        this.directPaymentId = directPaymentId;
        this.directPaymentExternalKey = directPaymentExternalKey;
        this.transactionType = transactionType;
        this.effectiveDate = effectiveDate;
        this.status = status;
        this.amount = amount;
        this.currency = currency;
        this.gatewayErrorCode = gatewayErrorCode;
        this.gatewayErrorMsg = gatewayErrorMsg;
        this.firstPaymentReferenceId = firstPaymentReferenceId;
        this.secondPaymentReferenceId = secondPaymentReferenceId;
        this.properties = properties;
    }

    public DirectTransactionJson(final DirectPaymentTransaction dpt, final String directPaymentExternalKey, @Nullable final List<AuditLog> directTransactionLogs) {
        this(dpt.getId().toString(),
             dpt.getExternalKey(),
             dpt.getDirectPaymentId().toString(),
             directPaymentExternalKey,
             dpt.getTransactionType().toString(),
             dpt.getAmount(),
             dpt.getCurrency() != null ? dpt.getCurrency().toString() : null,
             dpt.getEffectiveDate(),
             dpt.getTransactionStatus() != null ? dpt.getTransactionStatus().toString() : null,
             dpt.getGatewayErrorCode(),
             dpt.getGatewayErrorMsg(),
             dpt.getPaymentInfoPlugin() == null ? null : dpt.getPaymentInfoPlugin().getFirstPaymentReferenceId(),
             dpt.getPaymentInfoPlugin() == null ? null : dpt.getPaymentInfoPlugin().getSecondPaymentReferenceId(),
             dpt.getPaymentInfoPlugin() == null ? null : toPluginPropertyJson(dpt.getPaymentInfoPlugin().getProperties()),
             toAuditLogJson(directTransactionLogs));
    }

    public String getDirectTransactionId() {
        return directTransactionId;
    }

    public String getDirectTransactionExternalKey() {
        return directTransactionExternalKey;
    }

    public String getDirectPaymentId() {
        return directPaymentId;
    }

    public String getDirectPaymentExternalKey() {
        return directPaymentExternalKey;
    }

    public String getTransactionType() {
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

    public String getCurrency() {
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

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DirectTransactionJson{");
        sb.append("directTransactionId='").append(directTransactionId).append('\'');
        sb.append(", directTransactionExternalKey='").append(directTransactionExternalKey).append('\'');
        sb.append(", directPaymentId='").append(directPaymentId).append('\'');
        sb.append(", directPaymentExternalKey='").append(directPaymentExternalKey).append('\'');
        sb.append(", transactionType='").append(transactionType).append('\'');
        sb.append(", effectiveDate=").append(effectiveDate);
        sb.append(", status='").append(status).append('\'');
        sb.append(", amount=").append(amount);
        sb.append(", currency='").append(currency).append('\'');
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

        final DirectTransactionJson that = (DirectTransactionJson) o;

        if (amount != null ? amount.compareTo(that.amount) != 0 : that.amount != null) {
            return false;
        }
        if (currency != null ? !currency.equals(that.currency) : that.currency != null) {
            return false;
        }
        if (directPaymentExternalKey != null ? !directPaymentExternalKey.equals(that.directPaymentExternalKey) : that.directPaymentExternalKey != null) {
            return false;
        }
        if (directPaymentId != null ? !directPaymentId.equals(that.directPaymentId) : that.directPaymentId != null) {
            return false;
        }
        if (directTransactionExternalKey != null ? !directTransactionExternalKey.equals(that.directTransactionExternalKey) : that.directTransactionExternalKey != null) {
            return false;
        }
        if (directTransactionId != null ? !directTransactionId.equals(that.directTransactionId) : that.directTransactionId != null) {
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
        int result = directTransactionId != null ? directTransactionId.hashCode() : 0;
        result = 31 * result + (directTransactionExternalKey != null ? directTransactionExternalKey.hashCode() : 0);
        result = 31 * result + (directPaymentId != null ? directPaymentId.hashCode() : 0);
        result = 31 * result + (directPaymentExternalKey != null ? directPaymentExternalKey.hashCode() : 0);
        result = 31 * result + (transactionType != null ? transactionType.hashCode() : 0);
        result = 31 * result + (effectiveDate != null ? effectiveDate.hashCode() : 0);
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (gatewayErrorCode != null ? gatewayErrorCode.hashCode() : 0);
        result = 31 * result + (gatewayErrorMsg != null ? gatewayErrorMsg.hashCode() : 0);
        result = 31 * result + (firstPaymentReferenceId != null ? firstPaymentReferenceId.hashCode() : 0);
        result = 31 * result + (secondPaymentReferenceId != null ? secondPaymentReferenceId.hashCode() : 0);
        result = 31 * result + (properties != null ? properties.hashCode() : 0);
        return result;
    }
}
