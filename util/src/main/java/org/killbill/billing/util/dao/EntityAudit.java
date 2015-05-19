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

package org.killbill.billing.util.dao;

import java.util.UUID;

import org.joda.time.DateTime;

import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.audit.ChangeType;
import org.killbill.billing.util.entity.dao.EntityModelDaoBase;

public class EntityAudit extends EntityModelDaoBase {

    private final TableName tableName;
    private final Long targetRecordId;
    private final ChangeType changeType;

    public EntityAudit(final UUID entityId, final TableName tableName, final Long targetRecordId, final ChangeType changeType, final DateTime createdDate) {
        super(entityId, createdDate, null);
        this.tableName = tableName;
        this.targetRecordId = targetRecordId;
        this.changeType = changeType;

    }
    public EntityAudit(final TableName tableName, final Long targetRecordId, final ChangeType changeType, final DateTime createdDate) {
        this(UUIDs.randomUUID(), tableName, targetRecordId, changeType, createdDate);
    }

    public TableName getTableName() {
        return tableName;
    }

    public Long getTargetRecordId() {
        return targetRecordId;
    }

    public ChangeType getChangeType() {
        return changeType;
    }

    @Override
    public String toString() {
        return "EntityAudit{" +
               "tableName=" + tableName +
               ", targetRecordId=" + targetRecordId +
               ", changeType=" + changeType +
               '}';
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

        final EntityAudit that = (EntityAudit) o;

        if (changeType != that.changeType) {
            return false;
        }
        if (tableName != that.tableName) {
            return false;
        }
        if (targetRecordId != null ? !targetRecordId.equals(that.targetRecordId) : that.targetRecordId != null) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (tableName != null ? tableName.hashCode() : 0);
        result = 31 * result + (targetRecordId != null ? targetRecordId.hashCode() : 0);
        result = 31 * result + (changeType != null ? changeType.hashCode() : 0);
        return result;
    }
}
