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

package com.ning.billing.payment.api;

import java.math.BigDecimal;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.util.entity.EntityBase;

public class DefaultRefund extends EntityBase implements Refund {

    private final UUID paymentId;
    private final BigDecimal amount;
    private final Currency currency;
    private final boolean isAdjusted;
    private final DateTime effectiveDate;

    public DefaultRefund(final UUID id, @Nullable final DateTime createdDate, @Nullable final DateTime updatedDate,
                         final UUID paymentId, final BigDecimal amount,
                         final Currency currency, final boolean isAdjusted, final DateTime effectiveDate) {
        super(id, createdDate, updatedDate);
        this.paymentId = paymentId;
        this.amount = amount;
        this.currency = currency;
        this.isAdjusted = isAdjusted;
        this.effectiveDate = effectiveDate;
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
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("DefaultRefund");
        sb.append("{paymentId=").append(paymentId);
        sb.append(", amount=").append(amount);
        sb.append(", currency=").append(currency);
        sb.append(", isAdjusted=").append(isAdjusted);
        sb.append(", effectiveDate=").append(effectiveDate);
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

        final DefaultRefund that = (DefaultRefund) o;

        if (isAdjusted != that.isAdjusted) {
            return false;
        }
        if (amount != null ? !amount.equals(that.amount) : that.amount != null) {
            return false;
        }
        if (effectiveDate != null ? !effectiveDate.equals(that.effectiveDate) : that.effectiveDate != null) {
            return false;
        }
        if (currency != that.currency) {
            return false;
        }
        if (paymentId != null ? !paymentId.equals(that.paymentId) : that.paymentId != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = paymentId != null ? paymentId.hashCode() : 0;
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (isAdjusted ? 1 : 0);
        result = 31 * result + (effectiveDate != null ? effectiveDate.hashCode() : 0);
        return result;
    }
}
