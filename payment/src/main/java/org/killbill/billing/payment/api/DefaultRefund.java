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

package org.killbill.billing.payment.api;

import java.math.BigDecimal;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;

import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.entity.EntityBase;
import org.killbill.billing.payment.dao.RefundModelDao;
import org.killbill.billing.payment.plugin.api.RefundInfoPlugin;

public class DefaultRefund extends EntityBase implements Refund {

    private final UUID paymentId;
    private final BigDecimal amount;
    private final Currency currency;
    private final boolean isAdjusted;
    private final DateTime effectiveDate;
    private final RefundStatus refundStatus;
    private final RefundInfoPlugin refundInfoPlugin;

    public DefaultRefund(final UUID id, @Nullable final DateTime createdDate, @Nullable final DateTime updatedDate,
                         final UUID paymentId, final BigDecimal amount,
                         final Currency currency, final boolean isAdjusted, final DateTime effectiveDate,
                         final RefundStatus refundStatus, final RefundInfoPlugin refundInfoPlugin) {
        super(id, createdDate, updatedDate);
        this.paymentId = paymentId;
        this.amount = amount;
        this.currency = currency;
        this.isAdjusted = isAdjusted;
        this.effectiveDate = effectiveDate;
        this.refundStatus = refundStatus;
        this.refundInfoPlugin = refundInfoPlugin;
    }

    public DefaultRefund(final RefundModelDao refundModelDao, @Nullable final RefundInfoPlugin refundInfoPlugin) {
        this(refundModelDao.getId(), refundModelDao.getCreatedDate(), refundModelDao.getUpdatedDate(),
             refundModelDao.getPaymentId(), refundModelDao.getAmount(), refundModelDao.getCurrency(),
             refundModelDao.isAdjusted(), refundModelDao.getCreatedDate(), refundModelDao.getRefundStatus(), refundInfoPlugin);
    }

    public DefaultRefund(final UUID id, @Nullable final DateTime createdDate, @Nullable final DateTime updatedDate,
                         final UUID paymentId, final BigDecimal amount,
                         final Currency currency, final boolean isAdjusted, final DateTime effectiveDate, final RefundStatus refundStatus) {
        this(id, createdDate, updatedDate, paymentId, amount, currency, isAdjusted, effectiveDate, refundStatus, null);
    }

    @Override
    public UUID getPaymentId() {
        return paymentId;
    }

    @Override
    public BigDecimal getRefundAmount() {
        return amount;
    }

    @Override
    public Currency getCurrency() {
        return currency;
    }

    @Override
    public boolean isAdjusted() {
        return isAdjusted;
    }

    @Override
    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    @Override
    public RefundStatus getRefundStatus() {
        return refundStatus;
    }

    @Override
    public RefundInfoPlugin getRefundInfoPlugin() {
        return refundInfoPlugin;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DefaultRefund{");
        sb.append("paymentId=").append(paymentId);
        sb.append(", amount=").append(amount);
        sb.append(", currency=").append(currency);
        sb.append(", isAdjusted=").append(isAdjusted);
        sb.append(", effectiveDate=").append(effectiveDate);
        sb.append(", refundStatus=").append(refundStatus);
        sb.append(", refundInfoPlugin=").append(refundInfoPlugin);
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

        final DefaultRefund that = (DefaultRefund) o;

        if (isAdjusted != that.isAdjusted) {
            return false;
        }
        if (amount != null ? amount.compareTo(that.amount) != 0 : that.amount != null) {
            return false;
        }
        if (currency != that.currency) {
            return false;
        }
        if (effectiveDate != null ? effectiveDate.compareTo(that.effectiveDate) != 0 : that.effectiveDate != null) {
            return false;
        }
        if (paymentId != null ? !paymentId.equals(that.paymentId) : that.paymentId != null) {
            return false;
        }
        if (refundInfoPlugin != null ? !refundInfoPlugin.equals(that.refundInfoPlugin) : that.refundInfoPlugin != null) {
            return false;
        }
        if (refundStatus != that.refundStatus) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (paymentId != null ? paymentId.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (isAdjusted ? 1 : 0);
        result = 31 * result + (effectiveDate != null ? effectiveDate.hashCode() : 0);
        result = 31 * result + (refundStatus != null ? refundStatus.hashCode() : 0);
        result = 31 * result + (refundInfoPlugin != null ? refundInfoPlugin.hashCode() : 0);
        return result;
    }
}
