/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.payment.dao;

import java.math.BigDecimal;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.payment.api.Refund;
import com.ning.billing.util.dao.TableName;
import com.ning.billing.util.entity.EntityBase;
import com.ning.billing.util.entity.dao.EntityModelDao;

public class RefundModelDao extends EntityBase implements EntityModelDao<Refund> {

    private UUID accountId;
    private UUID paymentId;
    private BigDecimal amount;
    private Currency currency;
    private boolean isAdjusted;
    private RefundStatus refundStatus;

    public RefundModelDao() { /* For the DAO mapper */ }

    public RefundModelDao(final UUID accountId, final UUID paymentId, final BigDecimal amount,
                          final Currency currency, final boolean isAdjusted) {
        this(UUID.randomUUID(), accountId, paymentId, amount, currency, isAdjusted, RefundStatus.CREATED, null, null);
    }

    public RefundModelDao(final UUID id, final UUID accountId, final UUID paymentId, final BigDecimal amount,
                          final Currency currency, final boolean isAdjusted, final RefundStatus refundStatus,
                          @Nullable final DateTime createdDate, @Nullable final DateTime updatedDate) {
        super(id, createdDate, updatedDate);
        this.accountId = accountId;
        this.paymentId = paymentId;
        this.amount = amount;
        this.currency = currency;
        this.refundStatus = refundStatus;
        this.isAdjusted = isAdjusted;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public RefundStatus getRefundStatus() {
        return refundStatus;
    }

    // TODO Required for making the BindBeanFactory with Introspector work
    // see Introspector line 571; they look at public method.
    public boolean getIsAdjusted() {
        return isAdjusted;
    }

    public boolean isAdjusted() {
        return isAdjusted;
    }

    public enum RefundStatus {
        CREATED,
        PLUGIN_COMPLETED,
        COMPLETED,
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("RefundModelDao");
        sb.append("{accountId=").append(accountId);
        sb.append(", paymentId=").append(paymentId);
        sb.append(", amount=").append(amount);
        sb.append(", currency=").append(currency);
        sb.append(", isAdjusted=").append(isAdjusted);
        sb.append(", refundStatus=").append(refundStatus);
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

        final RefundModelDao that = (RefundModelDao) o;

        if (isAdjusted != that.isAdjusted) {
            return false;
        }
        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (amount != null ? !amount.equals(that.amount) : that.amount != null) {
            return false;
        }
        if (createdDate != null ? !createdDate.equals(that.createdDate) : that.createdDate != null) {
            return false;
        }
        if (currency != that.currency) {
            return false;
        }
        if (paymentId != null ? !paymentId.equals(that.paymentId) : that.paymentId != null) {
            return false;
        }
        if (refundStatus != that.refundStatus) {
            return false;
        }
        if (updatedDate != null ? !updatedDate.equals(that.updatedDate) : that.updatedDate != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = accountId != null ? accountId.hashCode() : 0;
        result = 31 * result + (paymentId != null ? paymentId.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (isAdjusted ? 1 : 0);
        result = 31 * result + (refundStatus != null ? refundStatus.hashCode() : 0);
        result = 31 * result + (createdDate != null ? createdDate.hashCode() : 0);
        result = 31 * result + (updatedDate != null ? updatedDate.hashCode() : 0);
        return result;
    }

    @Override
    public TableName getTableName() {
        return TableName.REFUNDS;
    }

    @Override
    public TableName getHistoryTableName() {
        return TableName.REFUND_HISTORY;
    }

}
