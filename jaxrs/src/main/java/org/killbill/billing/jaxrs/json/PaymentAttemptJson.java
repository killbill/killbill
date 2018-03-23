/*
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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
import org.killbill.billing.payment.api.PaymentAttempt;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.util.audit.AuditLog;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value="PaymentAttempt", parent = JsonBase.class)
public class PaymentAttemptJson extends JsonBase {

    private final UUID accountId;
    private final UUID paymentMethodId;
    private final String paymentExternalKey;
    private final UUID transactionId;
    private final String transactionExternalKey;
    private final TransactionType transactionType;
    @ApiModelProperty(dataType = "org.joda.time.DateTime")
    private final DateTime effectiveDate;
    private final String stateName;
    @ApiModelProperty(value = "Transaction amount, required except for void operations")
    private final BigDecimal amount;
    @ApiModelProperty(value = "Amount currency (account currency unless specified)", dataType = "org.killbill.billing.catalog.api.Currency")
    private final Currency currency;
    // Plugin specific fields
    private final String pluginName;
    private final List<PluginPropertyJson> pluginProperties;

    @JsonCreator
    public PaymentAttemptJson(@JsonProperty("accountId") final UUID accountId,
                              @JsonProperty("paymentMethodId") final UUID paymentMethodId,
                              @JsonProperty("paymentExternalKey") final String paymentExternalKey,
                              @JsonProperty("transactionId") final UUID transactionId,
                              @JsonProperty("transactionExternalKey") final String transactionExternalKey,
                              @JsonProperty("transactionType") final TransactionType transactionType,
                              @JsonProperty("effectiveDate") final DateTime effectiveDate,
                              @JsonProperty("stateName") final String stateName,
                              @JsonProperty("amount") final BigDecimal amount,
                              @JsonProperty("currency") final Currency currency,
                              @JsonProperty("pluginName") final String pluginName,
                              @JsonProperty("pluginProperties") final List<PluginPropertyJson> pluginProperties,
                              @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(auditLogs);
        this.accountId = accountId;
        this.paymentMethodId = paymentMethodId;
        this.paymentExternalKey = paymentExternalKey;
        this.transactionId = transactionId;
        this.transactionExternalKey = transactionExternalKey;
        this.transactionType = transactionType;
        this.effectiveDate = effectiveDate;
        this.stateName = stateName;
        this.amount = amount;
        this.currency = currency;
        this.pluginName = pluginName;
        this.pluginProperties = pluginProperties;
    }

    public PaymentAttemptJson(final PaymentAttempt paymentAttempt, final String paymentExternalKey, @Nullable final List<AuditLog> attemptsLogs) {
        this(paymentAttempt.getAccountId(),
             // Could be null if aborted in the priorCall
             paymentAttempt.getPaymentMethodId(),
             paymentExternalKey,
             paymentAttempt.getTransactionId(),
             paymentAttempt.getTransactionExternalKey(),
             paymentAttempt.getTransactionType(),
             paymentAttempt.getEffectiveDate(),
             paymentAttempt.getStateName(),
             paymentAttempt.getAmount(),
             paymentAttempt.getCurrency() != null ? paymentAttempt.getCurrency() : null,
             paymentAttempt.getPluginName(),
             paymentAttempt.getPluginProperties() == null ? null : toPluginPropertyJson(paymentAttempt.getPluginProperties()),
             toAuditLogJson(attemptsLogs));
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getPaymentMethodId() {
        return paymentMethodId;
    }

    public String getPaymentExternalKey() {
        return paymentExternalKey;
    }

    public UUID getTransactionId() {
        return transactionId;
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

    public String getStateName() {
        return stateName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public String getPluginName() {
        return pluginName;
    }

    public List<PluginPropertyJson> getPluginProperties() {
        return pluginProperties;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("PaymentAttemptJson{");
        sb.append("accountId='").append(accountId).append('\'');
        sb.append(", paymentMethodId='").append(paymentMethodId).append('\'');
        sb.append(", paymentExternalKey='").append(paymentExternalKey).append('\'');
        sb.append(", transactionId='").append(transactionId).append('\'');
        sb.append(", transactionExternalKey='").append(transactionExternalKey).append('\'');
        sb.append(", transactionType='").append(transactionType).append('\'');
        sb.append(", effectiveDate=").append(effectiveDate);
        sb.append(", stateName='").append(stateName).append('\'');
        sb.append(", amount=").append(amount);
        sb.append(", currency='").append(currency).append('\'');
        sb.append(", pluginName='").append(pluginName).append('\'');
        sb.append(", pluginProperties=").append(pluginProperties);
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

        final PaymentAttemptJson that = (PaymentAttemptJson) o;

        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (paymentMethodId != null ? !paymentMethodId.equals(that.paymentMethodId) : that.paymentMethodId != null) {
            return false;
        }
        if (paymentExternalKey != null ? !paymentExternalKey.equals(that.paymentExternalKey) : that.paymentExternalKey != null) {
            return false;
        }
        if (transactionId != null ? !transactionId.equals(that.transactionId) : that.transactionId != null) {
            return false;
        }
        if (transactionExternalKey != null ? !transactionExternalKey.equals(that.transactionExternalKey) : that.transactionExternalKey != null) {
            return false;
        }
        if (transactionType != null ? !transactionType.equals(that.transactionType) : that.transactionType != null) {
            return false;
        }
        if (effectiveDate != null ? effectiveDate.compareTo(that.effectiveDate) != 0 : that.effectiveDate != null) {
            return false;
        }
        if (stateName != null ? !stateName.equals(that.stateName) : that.stateName != null) {
            return false;
        }
        if (amount != null ? amount.compareTo(that.amount) != 0 : that.amount != null) {
            return false;
        }
        if (currency != null ? !currency.equals(that.currency) : that.currency != null) {
            return false;
        }
        if (pluginName != null ? !pluginName.equals(that.pluginName) : that.pluginName != null) {
            return false;
        }
        if (pluginProperties != null ? !pluginProperties.equals(that.pluginProperties) : that.pluginProperties != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = accountId != null ? accountId.hashCode() : 0;
        result = 31 * result + (paymentMethodId != null ? paymentMethodId.hashCode() : 0);
        result = 31 * result + (paymentExternalKey != null ? paymentExternalKey.hashCode() : 0);
        result = 31 * result + (transactionId != null ? transactionId.hashCode() : 0);
        result = 31 * result + (transactionExternalKey != null ? transactionExternalKey.hashCode() : 0);
        result = 31 * result + (transactionType != null ? transactionType.hashCode() : 0);
        result = 31 * result + (effectiveDate != null ? effectiveDate.hashCode() : 0);
        result = 31 * result + (stateName != null ? stateName.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (pluginName != null ? pluginName.hashCode() : 0);
        result = 31 * result + (pluginProperties != null ? pluginProperties.hashCode() : 0);
        return result;
    }
}
