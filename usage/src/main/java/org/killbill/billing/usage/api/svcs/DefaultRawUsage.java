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

package org.killbill.billing.usage.api.svcs;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.usage.api.RawUsageRecord;

public class DefaultRawUsage implements RawUsageRecord {

    private final UUID subscriptionId;
    private final DateTime recordDate;
    private final String unitType;
    private final BigDecimal amount;
    private final String trackingId;

    public DefaultRawUsage(final UUID subscriptionId, final DateTime recordDate, final String unitType, final BigDecimal amount, final String trackingId) {
        this.subscriptionId = subscriptionId;
        this.recordDate = recordDate;
        this.unitType = unitType;
        this.amount = amount;
        this.trackingId = trackingId;
    }

    @Override
    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    @Override
    public DateTime getDate() {
        return recordDate;
    }

    @Override
    public String getUnitType() {
        return unitType;
    }

    @Override
    public BigDecimal getAmount() {
        return amount;
    }

    @Override
    public String getTrackingId() {
        return trackingId;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultRawUsage)) {
            return false;
        }

        final DefaultRawUsage that = (DefaultRawUsage) o;

        if (subscriptionId != null ? !subscriptionId.equals(that.subscriptionId) : that.subscriptionId != null) {
            return false;
        }
        if (recordDate != null ? !recordDate.equals(that.recordDate) : that.recordDate != null) {
            return false;
        }
        if (unitType != null ? !unitType.equals(that.unitType) : that.unitType != null) {
            return false;
        }
        if (amount != null ? !amount.equals(that.amount) : that.amount != null) {
            return false;
        }
        return trackingId != null ? trackingId.equals(that.trackingId) : that.trackingId == null;
    }

    @Override
    public int hashCode() {
        int result = subscriptionId != null ? subscriptionId.hashCode() : 0;
        result = 31 * result + (recordDate != null ? recordDate.hashCode() : 0);
        result = 31 * result + (unitType != null ? unitType.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (trackingId != null ? trackingId.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DefaultRawUsage{");
        sb.append("subscriptionId=").append(subscriptionId);
        sb.append(", recordDate=").append(recordDate);
        sb.append(", unitType='").append(unitType).append('\'');
        sb.append(", amount=").append(amount);
        sb.append(", trackingId=").append(trackingId);
        sb.append('}');
        return sb.toString();
    }
}
