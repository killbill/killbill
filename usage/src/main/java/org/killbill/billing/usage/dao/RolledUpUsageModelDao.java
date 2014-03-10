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

package org.killbill.billing.usage.dao;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;

public class RolledUpUsageModelDao {

    private UUID id;
    private UUID subscriptionId;
    private String unitType;
    private DateTime startTime;
    private DateTime endTime;
    private BigDecimal amount;

    public RolledUpUsageModelDao() { /* For the DAO mapper */ }

    public RolledUpUsageModelDao(final UUID subscriptionId, final String unitType, final DateTime startTime,
                                 final DateTime endTime, final BigDecimal amount) {
        this.id = UUID.randomUUID();
        this.subscriptionId = subscriptionId;
        this.unitType = unitType;
        this.startTime = startTime;
        this.endTime = endTime;
        this.amount = amount;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public String getUnitType() {
        return unitType;
    }

    public DateTime getStartTime() {
        return startTime;
    }

    public DateTime getEndTime() {
        return endTime;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setId(final UUID id) {
        this.id = id;
    }

    public void setSubscriptionId(final UUID subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public void setUnitType(final String unitType) {
        this.unitType = unitType;
    }

    public void setStartTime(final DateTime startTime) {
        this.startTime = startTime;
    }

    public void setEndTime(final DateTime endTime) {
        this.endTime = endTime;
    }

    public void setAmount(final BigDecimal amount) {
        this.amount = amount;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("RolledUpUsageModelDao");
        sb.append("{id=").append(id);
        sb.append(", subscriptionId=").append(subscriptionId);
        sb.append(", unitType='").append(unitType).append('\'');
        sb.append(", startTime=").append(startTime);
        sb.append(", endTime=").append(endTime);
        sb.append(", amount=").append(amount);
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

        final RolledUpUsageModelDao that = (RolledUpUsageModelDao) o;

        if (amount != null ? !amount.equals(that.amount) : that.amount != null) {
            return false;
        }
        if (endTime != null ? !endTime.equals(that.endTime) : that.endTime != null) {
            return false;
        }
        if (id != null ? !id.equals(that.id) : that.id != null) {
            return false;
        }
        if (unitType != null ? !unitType.equals(that.unitType) : that.unitType != null) {
            return false;
        }
        if (startTime != null ? !startTime.equals(that.startTime) : that.startTime != null) {
            return false;
        }
        if (subscriptionId != null ? !subscriptionId.equals(that.subscriptionId) : that.subscriptionId != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (subscriptionId != null ? subscriptionId.hashCode() : 0);
        result = 31 * result + (unitType != null ? unitType.hashCode() : 0);
        result = 31 * result + (startTime != null ? startTime.hashCode() : 0);
        result = 31 * result + (endTime != null ? endTime.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        return result;
    }
}
