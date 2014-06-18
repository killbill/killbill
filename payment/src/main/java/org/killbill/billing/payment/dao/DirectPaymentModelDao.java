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

import com.google.common.base.Objects;

public class DirectPaymentModelDao extends EntityBase implements EntityModelDao<DirectPayment> {

    public static final Integer INVALID_PAYMENT_NUMBER = new Integer(-17);

    private UUID accountId;
    private Integer paymentNumber;
    private UUID paymentMethodId;
    private String externalKey;
    private String currentStateName;
    private String extFirstPaymentRefId;
    private String extSecondPaymentRefId;


    public DirectPaymentModelDao() { /* For the DAO mapper */ }

    public DirectPaymentModelDao(final UUID id, @Nullable final DateTime createdDate, @Nullable final DateTime updatedDate, final UUID accountId,
                                 final UUID paymentMethodId, final Integer paymentNumber, @Nullable final String externalKey,
                                 @Nullable  final String extFirstPaymentRefId, @Nullable final String extSecondPaymentRefId) {
        super(id, createdDate, updatedDate);
        this.accountId = accountId;
        this.paymentMethodId = paymentMethodId;
        this.paymentNumber = paymentNumber;
        this.externalKey = Objects.firstNonNull(externalKey, id.toString());
        this.extFirstPaymentRefId = extFirstPaymentRefId;
        this.extSecondPaymentRefId = extSecondPaymentRefId;
    }

    public DirectPaymentModelDao(@Nullable final DateTime createdDate, @Nullable final DateTime updatedDate, final UUID accountId,
                                 final UUID paymentMethodId, @Nullable final String externalKey) {
        this(UUID.randomUUID(), createdDate, updatedDate, accountId, paymentMethodId, INVALID_PAYMENT_NUMBER, externalKey, null, null);
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

    public String getCurrentStateName() {
        return currentStateName;
    }

    public void setCurrentStateName(final String currentStateName) {
        this.currentStateName = currentStateName;
    }

    public String getExtFirstPaymentRefId() {
        return extFirstPaymentRefId;
    }

    public void setExtFirstPaymentRefId(final String extFirstPaymentRefId) {
        this.extFirstPaymentRefId = extFirstPaymentRefId;
    }

    public String getExtSecondPaymentRefId() {
        return extSecondPaymentRefId;
    }

    public void setExtSecondPaymentRefId(final String extSecondPaymentRefId) {
        this.extSecondPaymentRefId = extSecondPaymentRefId;
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

        final DirectPaymentModelDao that = (DirectPaymentModelDao) o;

        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (currentStateName != null ? !currentStateName.equals(that.currentStateName) : that.currentStateName != null) {
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
        if (extFirstPaymentRefId != null ? !extFirstPaymentRefId.equals(that.extFirstPaymentRefId) : that.extFirstPaymentRefId != null) {
            return false;
        }
        if (extSecondPaymentRefId != null ? !extSecondPaymentRefId.equals(that.extSecondPaymentRefId) : that.extSecondPaymentRefId != null) {
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
        result = 31 * result + (currentStateName != null ? currentStateName.hashCode() : 0);
        result = 31 * result + (extFirstPaymentRefId != null ? extFirstPaymentRefId.hashCode() : 0);
        result = 31 * result + (extSecondPaymentRefId != null ? extSecondPaymentRefId.hashCode() : 0);
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
