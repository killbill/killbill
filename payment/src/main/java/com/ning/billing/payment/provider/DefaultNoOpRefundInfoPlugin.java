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

package com.ning.billing.payment.provider;

import java.math.BigDecimal;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.payment.plugin.api.RefundInfoPlugin;
import com.ning.billing.payment.plugin.api.RefundPluginStatus;

public class DefaultNoOpRefundInfoPlugin implements RefundInfoPlugin {

    private final BigDecimal amount;
    private final Currency currency;
    private final DateTime effectiveDate;
    private final DateTime createdDate;
    private final RefundPluginStatus status;
    private final String error;

    public DefaultNoOpRefundInfoPlugin(final BigDecimal amount, final Currency currency, final DateTime effectiveDate,
                                       final DateTime createdDate, final RefundPluginStatus status, final String error) {
        this.amount = amount;
        this.currency = currency;
        this.effectiveDate = effectiveDate;
        this.createdDate = createdDate;
        this.status = status;
        this.error = error;
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
    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    @Override
    public RefundPluginStatus getStatus() {
        return status;
    }

    @Override
    public DateTime getCreatedDate() {
        return createdDate;
    }

    @Override
    public String getGatewayError() {
        return error;
    }

    @Override
    public String getGatewayErrorCode() {
        return null;
    }

    @Override
    public String getReferenceId() {
        return null;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("DefaultNoOpRefundInfoPlugin");
        sb.append("{amount=").append(amount);
        sb.append(", effectiveDate=").append(effectiveDate);
        sb.append(", createdDate=").append(createdDate);
        sb.append(", status=").append(status);
        sb.append(", error='").append(error).append('\'');
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

        final DefaultNoOpRefundInfoPlugin that = (DefaultNoOpRefundInfoPlugin) o;

        if (amount != null ? !amount.equals(that.amount) : that.amount != null) {
            return false;
        }
        if (createdDate != null ? !createdDate.equals(that.createdDate) : that.createdDate != null) {
            return false;
        }
        if (effectiveDate != null ? !effectiveDate.equals(that.effectiveDate) : that.effectiveDate != null) {
            return false;
        }
        if (error != null ? !error.equals(that.error) : that.error != null) {
            return false;
        }
        if (status != that.status) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = amount != null ? amount.hashCode() : 0;
        result = 31 * result + (effectiveDate != null ? effectiveDate.hashCode() : 0);
        result = 31 * result + (createdDate != null ? createdDate.hashCode() : 0);
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (error != null ? error.hashCode() : 0);
        return result;
    }
}
