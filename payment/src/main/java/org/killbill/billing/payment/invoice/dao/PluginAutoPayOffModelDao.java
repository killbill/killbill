/*
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

package org.killbill.billing.payment.invoice.dao;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.Currency;

public class PluginAutoPayOffModelDao {

    private Long recordId;
    private UUID attemptId;
    private String paymentExternalKey;
    private String transactionExternalKey;
    private UUID accountId;
    private String pluginName;
    private UUID paymentId;
    private BigDecimal amount;
    private Currency currency;
    private String createdBy;
    private DateTime createdDate;

    public PluginAutoPayOffModelDao() { /* For the DAO mapper */
    }

    public PluginAutoPayOffModelDao(final UUID attemptId, final String paymentExternalKey, final String transactionExternalKey, final UUID accountId, final String pluginName,
                                    final UUID paymentId, final BigDecimal amount, final Currency currency, final String createdBy, final DateTime createdDate) {
        this(-1L, attemptId, paymentExternalKey, transactionExternalKey, accountId, pluginName, paymentId, amount, currency, createdBy, createdDate);
    }

    public PluginAutoPayOffModelDao(final Long recordId, UUID attemptId, final String paymentExternalKey, final String transactionExternalKey, final UUID accountId, final String pluginName,
                                    final UUID paymentId, final BigDecimal amount, final Currency currency, final String createdBy, final DateTime createdDate) {
        this.recordId = recordId;
        this.attemptId = attemptId;
        this.paymentExternalKey = paymentExternalKey;
        this.transactionExternalKey = transactionExternalKey;
        this.accountId = accountId;
        this.pluginName = pluginName;
        this.paymentId = paymentId;
        this.amount = amount;
        this.currency = currency;
        this.createdBy = createdBy;
        this.createdDate = createdDate;
    }

    public UUID getAttemptId() {
        return attemptId;
    }

    public void setAttemptId(final UUID attemptId) {
        this.attemptId = attemptId;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(final Long recordId) {
        this.recordId = recordId;
    }

    public String getPaymentExternalKey() {
        return paymentExternalKey;
    }

    public void setPaymentExternalKey(final String paymentExternalKey) {
        this.paymentExternalKey = paymentExternalKey;
    }

    public String getTransactionExternalKey() {
        return transactionExternalKey;
    }

    public void setTransactionExternalKey(final String transactionExternalKey) {
        this.transactionExternalKey = transactionExternalKey;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(final UUID accountId) {
        this.accountId = accountId;
    }

    public String getPluginName() {
        return pluginName;
    }

    public void setPluginName(final String pluginName) {
        this.pluginName = pluginName;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(final UUID paymentId) {
        this.paymentId = paymentId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(final BigDecimal amount) {
        this.amount = amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(final Currency currency) {
        this.currency = currency;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(final String createdBy) {
        this.createdBy = createdBy;
    }

    public DateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(final DateTime createdDate) {
        this.createdDate = createdDate;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("PluginAutoPayOffModelDao{");
        sb.append("recordId=").append(recordId);
        sb.append(", attemptId=").append(attemptId);
        sb.append(", paymentExternalKey='").append(paymentExternalKey).append('\'');
        sb.append(", transactionExternalKey='").append(transactionExternalKey).append('\'');
        sb.append(", accountId=").append(accountId);
        sb.append(", pluginName='").append(pluginName).append('\'');
        sb.append(", paymentId=").append(paymentId);
        sb.append(", amount=").append(amount);
        sb.append(", currency=").append(currency);
        sb.append(", createdBy='").append(createdBy).append('\'');
        sb.append(", createdDate=").append(createdDate);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PluginAutoPayOffModelDao)) {
            return false;
        }

        final PluginAutoPayOffModelDao that = (PluginAutoPayOffModelDao) o;

        if (attemptId != null ? !attemptId.equals(that.attemptId) : that.attemptId != null) {
            return false;
        }
        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (amount != null ? !amount.equals(that.amount) : that.amount != null) {
            return false;
        }
        if (createdBy != null ? !createdBy.equals(that.createdBy) : that.createdBy != null) {
            return false;
        }
        if (createdDate != null ? createdDate.compareTo(that.createdDate) == 0 : that.createdDate != null) {
            return false;
        }
        if (currency != that.currency) {
            return false;
        }
        if (paymentExternalKey != null ? !paymentExternalKey.equals(that.paymentExternalKey) : that.paymentExternalKey != null) {
            return false;
        }
        if (paymentId != null ? !paymentId.equals(that.paymentId) : that.paymentId != null) {
            return false;
        }
        if (pluginName != null ? !pluginName.equals(that.pluginName) : that.pluginName != null) {
            return false;
        }
        if (recordId != null ? !recordId.equals(that.recordId) : that.recordId != null) {
            return false;
        }
        if (transactionExternalKey != null ? !transactionExternalKey.equals(that.transactionExternalKey) : that.transactionExternalKey != null) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = recordId != null ? recordId.hashCode() : 0;
        result = 31 * result + (attemptId != null ? attemptId.hashCode() : 0);
        result = 31 * result + (paymentExternalKey != null ? paymentExternalKey.hashCode() : 0);
        result = 31 * result + (transactionExternalKey != null ? transactionExternalKey.hashCode() : 0);
        result = 31 * result + (accountId != null ? accountId.hashCode() : 0);
        result = 31 * result + (pluginName != null ? pluginName.hashCode() : 0);
        result = 31 * result + (paymentId != null ? paymentId.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (createdBy != null ? createdBy.hashCode() : 0);
        result = 31 * result + (createdDate != null ? createdDate.hashCode() : 0);
        return result;
    }
}
