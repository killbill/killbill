/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.payment.provider;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.plugin.api.RefundInfoPlugin;
import org.killbill.billing.payment.plugin.api.RefundPluginStatus;

import com.google.common.collect.ImmutableList;

public class DefaultNoOpRefundInfoPlugin implements RefundInfoPlugin {

    private final UUID kbPaymentId;
    private final BigDecimal amount;
    private final Currency currency;
    private final DateTime effectiveDate;
    private final DateTime createdDate;
    private final RefundPluginStatus status;
    private final String error;

    public DefaultNoOpRefundInfoPlugin(final UUID kbPaymentId, final BigDecimal amount, final Currency currency, final DateTime effectiveDate,
                                       final DateTime createdDate, final RefundPluginStatus status, final String error) {
        this.kbPaymentId = kbPaymentId;
        this.amount = amount;
        this.currency = currency;
        this.effectiveDate = effectiveDate;
        this.createdDate = createdDate;
        this.status = status;
        this.error = error;
    }

    @Override
    public UUID getKbPaymentId() {
        return kbPaymentId;
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
    public String getFirstRefundReferenceId() {
        return null;
    }

    @Override
    public String getSecondRefundReferenceId() {
        return null;
    }

    @Override
    public List<PluginProperty> getProperties() {
        return ImmutableList.<PluginProperty>of();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DefaultNoOpRefundInfoPlugin{");
        sb.append("kbPaymentId=").append(kbPaymentId);
        sb.append(", amount=").append(amount);
        sb.append(", currency=").append(currency);
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

        if (amount != null ? amount.compareTo(that.amount) != 0 : that.amount != null) {
            return false;
        }
        if (createdDate != null ? createdDate.compareTo(that.createdDate) != 0 : that.createdDate != null) {
            return false;
        }
        if (currency != that.currency) {
            return false;
        }
        if (effectiveDate != null ? effectiveDate.compareTo(that.effectiveDate) != 0 : that.effectiveDate != null) {
            return false;
        }
        if (error != null ? !error.equals(that.error) : that.error != null) {
            return false;
        }
        if (kbPaymentId != null ? !kbPaymentId.equals(that.kbPaymentId) : that.kbPaymentId != null) {
            return false;
        }
        if (status != that.status) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = kbPaymentId != null ? kbPaymentId.hashCode() : 0;
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (effectiveDate != null ? effectiveDate.hashCode() : 0);
        result = 31 * result + (createdDate != null ? createdDate.hashCode() : 0);
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (error != null ? error.hashCode() : 0);
        return result;
    }
}
