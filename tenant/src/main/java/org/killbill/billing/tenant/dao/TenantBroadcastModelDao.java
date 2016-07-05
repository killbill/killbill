/*
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

package org.killbill.billing.tenant.dao;

import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.dao.TableName;
import org.killbill.billing.util.entity.Entity;
import org.killbill.billing.util.entity.dao.EntityModelDao;
import org.killbill.billing.util.entity.dao.EntityModelDaoBase;

public class TenantBroadcastModelDao extends EntityModelDaoBase implements EntityModelDao<Entity> {

    private String type;
    private Long targetRecordId;
    private TableName targetTableName;
    private UUID userToken;

    public TenantBroadcastModelDao() { /* For the DAO mapper */ }

    public TenantBroadcastModelDao(final Long targetRecordId, final String type, final UUID userToken) {
        this(UUIDs.randomUUID(), null, null, type, userToken);
        this.targetRecordId = targetRecordId;
    }

    public TenantBroadcastModelDao(final UUID id, final DateTime createdDate, final DateTime updatedDate,
                                   final String type, final UUID userToken) {
        super(id, createdDate, updatedDate);
        this.type = type;
        this.userToken = userToken;
        this.targetTableName = TableName.TENANT_KVS; // Only one supported now
    }

    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    public Long getTargetRecordId() {
        return targetRecordId;
    }

    public void setTargetRecordId(final Long targetRecordId) {
        this.targetRecordId = targetRecordId;
    }

    public UUID getUserToken() {
        return userToken;
    }

    public void setUserToken(final UUID userToken) {
        this.userToken = userToken;
    }

    public TableName getTargetTableName() {
        return targetTableName;
    }

    public void setTargetTableName(final TableName targetTableName) {
        this.targetTableName = targetTableName;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("TenantBroadcastModelDao{");
        sb.append("recordId=").append(getRecordId());
        sb.append(", accountRecordId=").append(getAccountRecordId());
        sb.append(", tenantRecordId=").append(getTenantRecordId());
        sb.append(", type='").append(type).append('\'');
        sb.append(", targetRecordId=").append(targetRecordId);
        sb.append(", targetTableName=").append(targetTableName);
        sb.append(", userToken=").append(userToken);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TenantBroadcastModelDao)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        final TenantBroadcastModelDao that = (TenantBroadcastModelDao) o;

        if (targetTableName != that.targetTableName) {
            return false;
        }
        if (targetRecordId != null ? !targetRecordId.equals(that.targetRecordId) : that.targetRecordId != null) {
            return false;
        }
        if (type != null ? !type.equals(that.type) : that.type != null) {
            return false;
        }
        if (userToken != null ? !userToken.equals(that.userToken) : that.userToken != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (targetRecordId != null ? targetRecordId.hashCode() : 0);
        result = 31 * result + (targetTableName != null ? targetTableName.hashCode() : 0);
        result = 31 * result + (userToken != null ? userToken.hashCode() : 0);
        return result;
    }

    @Override
    public TableName getTableName() {
        return TableName.TENANT_BROADCASTS;
    }

    @Override
    public TableName getHistoryTableName() {
        return null;
    }
}
