/*
 * Copyright 2016 The Billing Project, LLC
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

package org.killbill.billing.payment.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.entity.EntityBase;

public class DefaultPaymentAttempt extends EntityBase implements PaymentAttempt {

    private final UUID accountId;
    private final UUID paymentMethodId;
    private final String paymentExternalKey;
    private final UUID transactionId;
    private final String transactionExternalKey;
    private final TransactionType transactionType;
    private final DateTime effectiveDate;
    private final String stateName;
    private final BigDecimal amount;
    private final Currency currency;
    private final String pluginName;
    private final List<PluginProperty> pluginProperties;

    public DefaultPaymentAttempt(final UUID accountId, final UUID paymentMethodId, final UUID id, final DateTime createdDate, final DateTime updatedDate,
                                 final DateTime effectiveDate, final String paymentExternalKey, final UUID transactionId, final String transactionExternalKey,
                                 final TransactionType transactionType, final String stateName, final BigDecimal amount, final Currency currency,
                                 final String pluginName, final List<PluginProperty> pluginProperties) {
        super(id, createdDate, updatedDate);
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

    @Override
    public UUID getAccountId() {
        return accountId;
    }

    @Override
    public UUID getPaymentMethodId() {
        return paymentMethodId;
    }

    @Override
    public String getPaymentExternalKey() {
        return paymentExternalKey;
    }

    @Override
    public UUID getTransactionId() {
        return transactionId;
    }

    @Override
    public String getTransactionExternalKey() {
        return transactionExternalKey;
    }

    @Override
    public TransactionType getTransactionType() {
        return transactionType;
    }

    @Override
    public DateTime getEffectiveDate() { return effectiveDate; }

    @Override
    public String getStateName() {
        return stateName;
    }

    @Override
    public BigDecimal getAmount() {
        return amount;
    }

    @Override
    public Currency getCurrency() {
        return currency;
    }

    @Override
    public String getPluginName() {
        return pluginName;
    }

    @Override
    public List<PluginProperty> getPluginProperties() {
        return pluginProperties;
    }

    @Override
    public String toString() {
        return "DefaultPaymentAttempt{" +
               "accountId=" + accountId +
               ", paymentMethodId=" + paymentMethodId +
               ", paymentExternalKey='" + paymentExternalKey + '\'' +
               ", transactionId=" + transactionId +
               ", transactionExternalKey='" + transactionExternalKey + '\'' +
               ", transactionType=" + transactionType +
               ", effectiveDate=" + effectiveDate +
               ", stateName=" + stateName +
               ", amount=" + amount +
               ", currency=" + currency +
               ", pluginName='" + pluginName + '\'' +
               ", pluginProperties=" + pluginProperties +
               '}';
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

        final DefaultPaymentAttempt that = (DefaultPaymentAttempt) o;

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
        if (transactionType != that.transactionType) {
            return false;
        }
        if (effectiveDate != null ? !effectiveDate.equals(that.effectiveDate) : that.effectiveDate != null) {
            return false;
        }
        if (stateName != null ? !stateName.equals(that.stateName) : that.stateName != null) {
            return false;
        }
        if (amount != null ? !amount.equals(that.amount) : that.amount != null) {
            return false;
        }
        if (currency != that.currency) {
            return false;
        }
        if (pluginName != null ? !pluginName.equals(that.pluginName) : that.pluginName != null) {
            return false;
        }
        return pluginProperties != null ? pluginProperties.equals(that.pluginProperties) : that.pluginProperties == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (accountId != null ? accountId.hashCode() : 0);
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
