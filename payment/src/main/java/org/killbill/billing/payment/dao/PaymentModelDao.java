/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.billing.payment.dao;

import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.util.dao.TableName;
import org.killbill.billing.util.entity.dao.EntityModelDao;
import org.killbill.billing.util.entity.dao.EntityModelDaoBase;
import org.killbill.billing.util.UUIDs;

import com.google.common.base.MoreObjects;

public class PaymentModelDao extends EntityModelDaoBase implements EntityModelDao<Payment> {

    public static final Integer INVALID_PAYMENT_NUMBER = new Integer(-17);

    private UUID accountId;
    private Integer paymentNumber;
    private UUID paymentMethodId;
    private String externalKey;
    private String stateName;
    private String lastSuccessStateName;

    public PaymentModelDao() { /* For the DAO mapper */ }

    public PaymentModelDao(final UUID id, @Nullable final DateTime createdDate, @Nullable final DateTime updatedDate, final UUID accountId,
                           final UUID paymentMethodId, final Integer paymentNumber, @Nullable final String externalKey) {
        super(id, createdDate, updatedDate);
        this.accountId = accountId;
        this.paymentMethodId = paymentMethodId;
        this.paymentNumber = paymentNumber;
        this.externalKey = MoreObjects.firstNonNull(externalKey, id.toString());
    }

    public PaymentModelDao(final UUID id, @Nullable final DateTime createdDate, @Nullable final DateTime updatedDate, final UUID accountId,
                           final UUID paymentMethodId, @Nullable final String externalKey) {
        this(id, createdDate, updatedDate, accountId, paymentMethodId, INVALID_PAYMENT_NUMBER, externalKey);
    }

    public PaymentModelDao(@Nullable final DateTime createdDate, @Nullable final DateTime updatedDate, final UUID accountId,
                           final UUID paymentMethodId, @Nullable final String externalKey) {
        this(UUIDs.randomUUID(), createdDate, updatedDate, accountId, paymentMethodId, externalKey);
    }

    public UUID getAccountId() { return accountId; }

    public void setAccountId(final UUID accountId) {
        this.accountId = accountId;
    }

    public Integer getPaymentNumber() {
        return paymentNumber;
    }

    public void setPaymentNumber(final Integer paymentNumber) {
        this.paymentNumber = paymentNumber;
    }

    public UUID getPaymentMethodId() {
        return paymentMethodId;
    }

    public void setPaymentMethodId(final UUID paymentMethodId) {
        this.paymentMethodId = paymentMethodId;
    }

    public String getExternalKey() {
        return externalKey;
    }

    public void setExternalKey(final String externalKey) {
        this.externalKey = externalKey;
    }

    public String getStateName() {
        return stateName;
    }

    public void setStateName(final String stateName) {
        this.stateName = stateName;
    }

    public String getLastSuccessStateName() {
        return lastSuccessStateName;
    }

    public void setLastSuccessStateName(final String lastSuccessStateName) {
        this.lastSuccessStateName = lastSuccessStateName;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("PaymentModelDao{");
        sb.append("accountId=").append(accountId);
        sb.append(", paymentNumber=").append(paymentNumber);
        sb.append(", paymentMethodId=").append(paymentMethodId);
        sb.append(", externalKey='").append(externalKey).append('\'');
        sb.append(", stateName='").append(stateName).append('\'');
        sb.append(", lastSuccessStateName='").append(lastSuccessStateName).append('\'');
        sb.append(", createdDate=").append(createdDate);
        sb.append(", updatedDate=").append(updatedDate);
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
        if (!super.equals(o)) {
            return false;
        }

        final PaymentModelDao that = (PaymentModelDao) o;

        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (stateName != null ? !stateName.equals(that.stateName) : that.stateName != null) {
            return false;
        }
        if (lastSuccessStateName != null ? !lastSuccessStateName.equals(that.lastSuccessStateName) : that.lastSuccessStateName != null) {
            return false;
        }
        if (externalKey != null ? !externalKey.equals(that.externalKey) : that.externalKey != null) {
            return false;
        }
        if (paymentMethodId != null ? !paymentMethodId.equals(that.paymentMethodId) : that.paymentMethodId != null) {
            return false;
        }
        if (paymentNumber != null ? !paymentNumber.equals(that.paymentNumber) : that.paymentNumber != null) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (accountId != null ? accountId.hashCode() : 0);
        result = 31 * result + (paymentNumber != null ? paymentNumber.hashCode() : 0);
        result = 31 * result + (paymentMethodId != null ? paymentMethodId.hashCode() : 0);
        result = 31 * result + (externalKey != null ? externalKey.hashCode() : 0);
        result = 31 * result + (stateName != null ? stateName.hashCode() : 0);
        result = 31 * result + (lastSuccessStateName != null ? lastSuccessStateName.hashCode() : 0);
        return result;
    }

    @Override
    public TableName getTableName() {
        return TableName.PAYMENTS;
    }

    @Override
    public TableName getHistoryTableName() {
        return TableName.PAYMENT_HISTORY;
    }
}
