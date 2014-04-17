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
import org.killbill.billing.payment.api.DirectPaymentTransaction;
import org.killbill.billing.util.audit.AuditLog;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DirectTransactionJson extends JsonBase {

    private final String directTransactionId;
    private final String directPaymentId;
    private final String transactionType;
    private final DateTime effectiveDate;
    private final Integer retryCount;
    private final String status;
    private final BigDecimal amount;
    private final String currency;
    private final String externalKey;
    private final String gatewayErrorCode;
    private final String gatewayErrorMsg;

    @JsonCreator
    public DirectTransactionJson(@JsonProperty("directTransactionId") final String directTransactionId,
                                 @JsonProperty("directPaymentId") final String directPaymentId,
                                 @JsonProperty("transactionType") final String transactionType,
                                 @JsonProperty("amount") final BigDecimal amount,
                                 @JsonProperty("currency") final String currency,
                                 @JsonProperty("effectiveDate") final DateTime effectiveDate,
                                 @JsonProperty("status") final String status,
                                 @JsonProperty("retryCount") final Integer retryCount,
                                 @JsonProperty("externalKey") final String externalKey,
                                 @JsonProperty("gatewayErrorCode") final String gatewayErrorCode,
                                 @JsonProperty("gatewayErrorMsg") final String gatewayErrorMsg,
                                 @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(auditLogs);
        this.directTransactionId = directTransactionId;
        this.directPaymentId = directPaymentId;
        this.transactionType = transactionType;
        this.effectiveDate = effectiveDate;
        this.retryCount = retryCount;
        this.externalKey = externalKey;
        this.status = status;
        this.amount = amount;
        this.currency = currency;
        this.gatewayErrorCode = gatewayErrorCode;
        this.gatewayErrorMsg = gatewayErrorMsg;
    }

    public DirectTransactionJson(final DirectPaymentTransaction dpt, final UUID directPaymentId, final String externalKey, @Nullable final List<AuditLog> directTransactionLogs) {
        this(dpt.getId().toString(),
             directPaymentId.toString(),
             dpt.getTransactionType().toString(),
             dpt.getAmount(),
             dpt.getCurrency().toString(),
             dpt.getEffectiveDate(),
             dpt.getPaymentStatus().toString(),
             1,
             externalKey,
             dpt.getGatewayErrorCode(),
             dpt.getGatewayErrorMsg(),
             toAuditLogJson(directTransactionLogs));
    }

    public String getDirectTransactionId() {
        return directTransactionId;
    }

    public String getDirectPaymentId() {
        return directPaymentId;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    public Integer getRetryCount() {
        return retryCount;
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

    public String getExternalKey() {
        return externalKey;
    }

    @Override
    public String toString() {
        return "DirectTransactionJson{" +
               "directPaymentId=" + directPaymentId +
               "directTransactionId=" + directTransactionId +
               "transactionType=" + transactionType +
               ", effectiveDate=" + effectiveDate +
               ", retryCount=" + retryCount +
               ", status='" + status + '\'' +
               ", externalKey='" + externalKey + '\'' +
               ", amount=" + amount +
               ", currency='" + currency + '\'' +
               ", gatewayErrorCode='" + gatewayErrorCode + '\'' +
               ", gatewayErrorMsg='" + gatewayErrorMsg + '\'' +
               '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DirectTransactionJson)) {
            return false;
        }

        final DirectTransactionJson that = (DirectTransactionJson) o;

        if (directPaymentId != null ? !directPaymentId.equals(that.directPaymentId) : that.directPaymentId != null) {
            return false;
        }
        if (directTransactionId != null ? !directTransactionId.equals(that.directTransactionId) : that.directTransactionId != null) {
            return false;
        }
        if (amount != null ? amount.compareTo(that.amount) != 0 : that.amount != null) {
            return false;
        }
        if (currency != null ? !currency.equals(that.currency) : that.currency != null) {
            return false;
        }
        if (effectiveDate != null ? effectiveDate.compareTo(that.effectiveDate) != 0 : that.effectiveDate != null) {
            return false;
        }
        if (gatewayErrorCode != null ? !gatewayErrorCode.equals(that.gatewayErrorCode) : that.gatewayErrorCode != null) {
            return false;
        }
        if (gatewayErrorMsg != null ? !gatewayErrorMsg.equals(that.gatewayErrorMsg) : that.gatewayErrorMsg != null) {
            return false;
        }
        if (retryCount != null ? !retryCount.equals(that.retryCount) : that.retryCount != null) {
            return false;
        }
        if (externalKey != null ? !externalKey.equals(that.externalKey) : that.externalKey != null) {
            return false;
        }
        if (status != null ? !status.equals(that.status) : that.status != null) {
            return false;
        }
        if (transactionType.equals(that.transactionType)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = transactionType != null ? transactionType.hashCode() : 0;
        result = 31 * result + (directPaymentId != null ? directPaymentId.hashCode() : 0);
        result = 31 * result + (directTransactionId != null ? directTransactionId.hashCode() : 0);
        result = 31 * result + (effectiveDate != null ? effectiveDate.hashCode() : 0);
        result = 31 * result + (retryCount != null ? retryCount.hashCode() : 0);
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (externalKey != null ? externalKey.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (gatewayErrorCode != null ? gatewayErrorCode.hashCode() : 0);
        result = 31 * result + (gatewayErrorMsg != null ? gatewayErrorMsg.hashCode() : 0);
        return result;
    }
}
