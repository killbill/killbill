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

package org.killbill.billing.payment.dao;

import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.billing.entity.EntityBase;
import org.killbill.billing.payment.api.DirectPayment;
import org.killbill.billing.util.dao.TableName;
import org.killbill.billing.util.entity.dao.EntityModelDao;

public class DirectPaymentModelDao extends EntityBase implements EntityModelDao<DirectPayment> {

    public static final Integer INVALID_PAYMENT_NUMBER = new Integer(-17);

    private UUID accountId;
    private Integer paymentNumber;
    private UUID paymentMethodId;
    private String externalKey;

    public DirectPaymentModelDao() { /* For the DAO mapper */ }

    public DirectPaymentModelDao(final UUID id, @Nullable final DateTime createdDate, @Nullable final DateTime updatedDate, final UUID accountId,
                                 final UUID paymentMethodId, final Integer paymentNumber, final String externalKey) {
        super(id, createdDate, updatedDate);
        this.accountId = accountId;
        this.paymentMethodId = paymentMethodId;
        this.paymentNumber = paymentNumber;
        this.externalKey = externalKey;
    }

    public DirectPaymentModelDao(@Nullable final DateTime createdDate, @Nullable final DateTime updatedDate, final UUID accountId,
                                 final UUID paymentMethodId, final String externalKey) {
        this(UUID.randomUUID(), createdDate, updatedDate, accountId, paymentMethodId, INVALID_PAYMENT_NUMBER, externalKey);
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

    @Override
    public String toString() {
        return "DirectPaymentModelDao{" +
               "accountId=" + accountId +
               ", paymentNumber=" + paymentNumber +
               ", paymentMethodId=" + paymentMethodId +
               '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DirectPaymentModelDao)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        final DirectPaymentModelDao that = (DirectPaymentModelDao) o;

        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
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
        return result;
    }

    @Override
    public TableName getTableName() {
        return TableName.DIRECT_PAYMENTS;
    }

    @Override
    public TableName getHistoryTableName() {
        return TableName.DIRECT_PAYMENT_HISTORY;
    }
}
