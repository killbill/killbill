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

import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.dao.TableName;
import org.killbill.billing.util.entity.Entity;
import org.killbill.billing.util.entity.dao.EntityModelDao;
import org.killbill.billing.util.entity.dao.EntityModelDaoBase;

public class RolledUpUsageModelDao extends EntityModelDaoBase implements EntityModelDao<Entity> {

    private UUID subscriptionId;
    private String unitType;
    private LocalDate recordDate;
    private Long amount;

    public RolledUpUsageModelDao() { /* For the DAO mapper */ }

    public RolledUpUsageModelDao(final UUID id, final DateTime createdDate, final DateTime updatedDate, final UUID subscriptionId, final String unitType, final LocalDate recordDate, final Long amount) {
        super(id, createdDate, updatedDate);
        this.subscriptionId = subscriptionId;
        this.unitType = unitType;
        this.recordDate = recordDate;
        this.amount = amount;
    }

    public RolledUpUsageModelDao(final UUID subscriptionId, final String unitType, final LocalDate recordDate, final Long amount) {
        this(UUIDs.randomUUID(), null, null, subscriptionId, unitType, recordDate, amount);
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public String getUnitType() {
        return unitType;
    }

    public LocalDate getRecordDate() {
        return recordDate;
    }

    public void setRecordDate(final LocalDate recordDate) {
        this.recordDate = recordDate;
    }

    public Long getAmount() {
        return amount;
    }

    public void setSubscriptionId(final UUID subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public void setUnitType(final String unitType) {
        this.unitType = unitType;
    }

    public void setAmount(final Long amount) {
        this.amount = amount;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("RolledUpUsageModelDao");
        sb.append("{id=").append(id);
        sb.append(", subscriptionId=").append(subscriptionId);
        sb.append(", unitType='").append(unitType).append('\'');
        sb.append(", recordDate=").append(recordDate);
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
        if (recordDate != null ? !recordDate.equals(that.recordDate) : that.recordDate != null) {
            return false;
        }
        if (id != null ? !id.equals(that.id) : that.id != null) {
            return false;
        }
        if (unitType != null ? !unitType.equals(that.unitType) : that.unitType != null) {
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
        result = 31 * result + (recordDate != null ? recordDate.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        return result;
    }

    @Override
    public TableName getTableName() {
        return TableName.ROLLED_UP_USAGE;
    }

    @Override
    public TableName getHistoryTableName() {
        return null;
    }
}
