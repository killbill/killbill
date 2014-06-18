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

import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.billing.entity.EntityBase;
import org.killbill.billing.payment.api.PaymentAttempt;
import org.killbill.billing.util.dao.TableName;
import org.killbill.billing.util.entity.dao.EntityModelDao;

public class PaymentAttemptModelDao extends EntityBase implements EntityModelDao<PaymentAttempt> {

    private String paymentExternalKey;
    private UUID directTransactionId;
    private String transactionExternalKey;
    private String stateName;
    private String operationName;
    private String pluginName;

    public PaymentAttemptModelDao() { /* For the DAO mapper */ }

    public PaymentAttemptModelDao(final UUID id, @Nullable final DateTime createdDate, @Nullable final DateTime updatedDate,
                                  final String paymentExternalKey, final UUID directTransactionId, final String externalKey, final String stateName, final String operationName,
                                  final String pluginName) {
        super(id, createdDate, updatedDate);
        this.paymentExternalKey = paymentExternalKey;
        this.directTransactionId = directTransactionId;
        this.transactionExternalKey = externalKey;
        this.stateName = stateName;
        this.operationName = operationName;
        this.pluginName = pluginName;
    }

    public PaymentAttemptModelDao(@Nullable final DateTime createdDate, @Nullable final DateTime updatedDate,
                                  final String paymentExternalKey, final UUID directTransactionId, final String externalKey, final String stateName, final String operationName,
                                  final String pluginName) {
        this(UUID.randomUUID(), createdDate, updatedDate, paymentExternalKey, directTransactionId, externalKey, stateName, operationName, pluginName);
    }

    public String getPaymentExternalKey() {
        return paymentExternalKey;
    }

    public void setPaymentExternalKey(final String paymentExternalKey) {
        this.paymentExternalKey = paymentExternalKey;
    }

    public UUID getDirectTransactionId() {
        return directTransactionId;
    }

    public void setDirectTransactionId(final UUID directTransactionId) {
        this.directTransactionId = directTransactionId;
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

    public String getOperationName() {
        return operationName;
    }

    public void setOperationName(final String operationName) {
        this.operationName = operationName;
    }

    public String getPluginName() {
        return pluginName;
    }

    public void setPluginName(final String pluginName) {
        this.pluginName = pluginName;
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

        if (directTransactionId != null ? !directTransactionId.equals(that.directTransactionId) : that.directTransactionId != null) {
            return false;
        }
        if (paymentExternalKey != null ? !paymentExternalKey.equals(that.paymentExternalKey) : that.paymentExternalKey != null) {
            return false;
        }
        if (transactionExternalKey != null ? !transactionExternalKey.equals(that.transactionExternalKey) : that.transactionExternalKey != null) {
            return false;
        }
        if (operationName != null ? !operationName.equals(that.operationName) : that.operationName != null) {
            return false;
        }
        if (pluginName != null ? !pluginName.equals(that.pluginName) : that.pluginName != null) {
            return false;
        }
        if (stateName != null ? !stateName.equals(that.stateName) : that.stateName != null) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (directTransactionId != null ? directTransactionId.hashCode() : 0);
        result = 31 * result + (paymentExternalKey != null ? paymentExternalKey.hashCode() : 0);
        result = 31 * result + (transactionExternalKey != null ? transactionExternalKey.hashCode() : 0);
        result = 31 * result + (stateName != null ? stateName.hashCode() : 0);
        result = 31 * result + (operationName != null ? operationName.hashCode() : 0);
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

}
