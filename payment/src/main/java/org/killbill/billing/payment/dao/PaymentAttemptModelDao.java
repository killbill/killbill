/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.payment.dao;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.dao.TableName;
import org.killbill.billing.util.entity.Entity;
import org.killbill.billing.util.entity.dao.EntityModelDao;
import org.killbill.billing.util.entity.dao.EntityModelDaoBase;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

public class PaymentAttemptModelDao extends EntityModelDaoBase implements EntityModelDao<Entity> {

    private static final Joiner JOINER = Joiner.on(",");

    private UUID accountId;
    private UUID paymentMethodId;
    private String paymentExternalKey;
    private UUID transactionId;
    private String transactionExternalKey;
    private TransactionType transactionType;
    private String stateName;
    private BigDecimal amount;
    private Currency currency;
    private String pluginName;
    private byte[] pluginProperties;

    public PaymentAttemptModelDao() { /* For the DAO mapper */ }

    public PaymentAttemptModelDao(final UUID accountId, final UUID paymentMethodId, final UUID id, @Nullable final DateTime createdDate, @Nullable final DateTime updatedDate,
                                  final String paymentExternalKey, final UUID transactionId, final String transactionExternalKey, final TransactionType transactionType,
                                  final String stateName, final BigDecimal amount, final Currency currency, final String pluginName, final byte[] pluginProperties) {
        super(id, createdDate, updatedDate);
        this.accountId = accountId;
        this.paymentMethodId = paymentMethodId;
        this.paymentExternalKey = paymentExternalKey;
        this.transactionId = transactionId;
        this.transactionExternalKey = transactionExternalKey;
        this.transactionType = transactionType;
        this.stateName = stateName;
        this.amount = amount;
        this.currency = currency;
        this.pluginName = pluginName;
        this.pluginProperties = pluginProperties;
    }

    public PaymentAttemptModelDao(final UUID accountId, final UUID paymentMethodId, @Nullable final DateTime createdDate, @Nullable final DateTime updatedDate,
                                  final String paymentExternalKey, final UUID transactionId, final String transactionExternalKey, final TransactionType transactionType, final String stateName,
                                  final BigDecimal amount, final Currency currency, final List<String> paymentControlPluginNames, final byte[] pluginProperties) {
        this(accountId, paymentMethodId, UUIDs.randomUUID(), createdDate, updatedDate, paymentExternalKey, transactionId, transactionExternalKey, transactionType, stateName,
             amount, currency, toPluginNames(paymentControlPluginNames), pluginProperties);
    }

    public String getPaymentExternalKey() {
        return paymentExternalKey;
    }

    public void setPaymentExternalKey(final String paymentExternalKey) {
        this.paymentExternalKey = paymentExternalKey;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(final UUID transactionId) {
        this.transactionId = transactionId;
    }

    public String getTransactionExternalKey() {
        return transactionExternalKey;
    }

    public void setTransactionExternalKey(final String transactionExternalKey) {
        this.transactionExternalKey = transactionExternalKey;
    }

    public String getStateName() {
        return stateName;
    }

    public void setStateName(final String stateName) {
        this.stateName = stateName;
    }

    public String getPluginName() {
        return pluginName;
    }

    public void setPluginName(final String pluginName) {
        this.pluginName = pluginName;
    }

    public byte[] getPluginProperties() {
        return pluginProperties;
    }

    public void setPluginProperties(final byte[] pluginProperties) {
        this.pluginProperties = pluginProperties;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getPaymentMethodId() {
        return paymentMethodId;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public void setAccountId(final UUID accountId) {
        this.accountId = accountId;
    }

    public void setPaymentMethodId(final UUID paymentMethodId) {
        this.paymentMethodId = paymentMethodId;
    }

    public void setTransactionType(final TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public void setAmount(final BigDecimal amount) {
        this.amount = amount;
    }

    public void setCurrency(final Currency currency) {
        this.currency = currency;
    }

    public final List<String> toPaymentControlPluginNames() {
        if (pluginName == null) {
            return ImmutableList.<String>of();
        }
        final String[] parts = pluginName.split(",");
        return ImmutableList.<String>copyOf(parts);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PaymentAttemptModelDao)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        final PaymentAttemptModelDao that = (PaymentAttemptModelDao) o;

        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (amount != null ? amount.compareTo(that.amount) != 0 : that.amount != null) {
            return false;
        }
        if (currency != that.currency) {
            return false;
        }
        if (paymentExternalKey != null ? !paymentExternalKey.equals(that.paymentExternalKey) : that.paymentExternalKey != null) {
            return false;
        }
        if (paymentMethodId != null ? !paymentMethodId.equals(that.paymentMethodId) : that.paymentMethodId != null) {
            return false;
        }
        if (pluginName != null ? !pluginName.equals(that.pluginName) : that.pluginName != null) {
            return false;
        }
        if (stateName != null ? !stateName.equals(that.stateName) : that.stateName != null) {
            return false;
        }
        if (transactionExternalKey != null ? !transactionExternalKey.equals(that.transactionExternalKey) : that.transactionExternalKey != null) {
            return false;
        }
        if (transactionId != null ? !transactionId.equals(that.transactionId) : that.transactionId != null) {
            return false;
        }
        if (transactionType != that.transactionType) {
            return false;
        }

        return true;
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
        result = 31 * result + (stateName != null ? stateName.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (pluginName != null ? pluginName.hashCode() : 0);
        return result;
    }

    @Override
    public TableName getTableName() {
        return TableName.PAYMENT_ATTEMPTS;
    }

    @Override
    public TableName getHistoryTableName() {
        return TableName.PAYMENT_ATTEMPT_HISTORY;
    }

    private static final String toPluginNames(final List<String> paymentControlPluginNames) {
        return paymentControlPluginNames == null || paymentControlPluginNames.size() == 0 ? null : JOINER.join(paymentControlPluginNames);
    }
}
