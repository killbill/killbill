/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

package org.killbill.billing.util.audit.dao;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.util.audit.AuditLog;
import org.killbill.billing.util.audit.ChangeType;
import org.killbill.billing.util.cache.ExternalizableInput;
import org.killbill.billing.util.cache.ExternalizableOutput;
import org.killbill.billing.util.cache.MapperHolder;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.dao.EntityAudit;
import org.killbill.billing.util.dao.TableName;
import org.killbill.billing.util.entity.dao.EntityModelDao;

public class AuditLogModelDao implements EntityModelDao<AuditLog>, Externalizable {

    private UUID id;
    private DateTime createdDate;
    private DateTime updatedDate;
    private TableName tableName;
    private Long targetRecordId;
    private ChangeType changeType;
    private CallContext callContext;

    private Long recordId;
    private Long accountRecordId;
    private Long tenantRecordId;

    // For deserialization
    public AuditLogModelDao() {}

    public AuditLogModelDao(final EntityAudit entityAudit, final CallContext callContext) {
        this.id = entityAudit.getId();
        this.tableName = entityAudit.getTableName();
        this.targetRecordId = entityAudit.getTargetRecordId();
        this.changeType = entityAudit.getChangeType();
        this.createdDate = entityAudit.getCreatedDate();
        this.updatedDate = null;
        this.callContext = callContext;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public DateTime getCreatedDate() {
        return createdDate;
    }

    @Override
    public DateTime getUpdatedDate() {
        return updatedDate;
    }

    @Override
    public TableName getTableName() {
        return tableName;
    }

    public Long getTargetRecordId() {
        return targetRecordId;
    }

    public ChangeType getChangeType() {
        return changeType;
    }

    public CallContext getCallContext() {
        return callContext;
    }

    @Override
    public Long getRecordId() {
        return recordId;
    }

    @Override
    public Long getAccountRecordId() {
        return accountRecordId;
    }

    @Override
    public Long getTenantRecordId() {
        return tenantRecordId;
    }

    @Override
    public TableName getHistoryTableName() {
        return null;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("AuditLogModelDao{");
        sb.append("id=").append(id);
        sb.append(", createdDate=").append(createdDate);
        sb.append(", updatedDate=").append(updatedDate);
        sb.append(", tableName=").append(tableName);
        sb.append(", targetRecordId=").append(targetRecordId);
        sb.append(", changeType=").append(changeType);
        sb.append(", callContext=").append(callContext);
        sb.append(", recordId=").append(recordId);
        sb.append(", accountRecordId=").append(accountRecordId);
        sb.append(", tenantRecordId=").append(tenantRecordId);
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

        final AuditLogModelDao that = (AuditLogModelDao) o;

        if (id != null ? !id.equals(that.id) : that.id != null) {
            return false;
        }
        if (createdDate != null ? createdDate.compareTo(that.createdDate) != 0 : that.createdDate != null) {
            return false;
        }
        if (updatedDate != null ? updatedDate.compareTo(that.updatedDate) != 0 : that.updatedDate != null) {
            return false;
        }
        if (tableName != that.tableName) {
            return false;
        }
        if (targetRecordId != null ? !targetRecordId.equals(that.targetRecordId) : that.targetRecordId != null) {
            return false;
        }
        if (changeType != that.changeType) {
            return false;
        }
        if (callContext != null ? !callContext.equals(that.callContext) : that.callContext != null) {
            return false;
        }
        if (recordId != null ? !recordId.equals(that.recordId) : that.recordId != null) {
            return false;
        }
        if (accountRecordId != null ? !accountRecordId.equals(that.accountRecordId) : that.accountRecordId != null) {
            return false;
        }
        return tenantRecordId != null ? tenantRecordId.equals(that.tenantRecordId) : that.tenantRecordId == null;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (createdDate != null ? createdDate.hashCode() : 0);
        result = 31 * result + (updatedDate != null ? updatedDate.hashCode() : 0);
        result = 31 * result + (tableName != null ? tableName.hashCode() : 0);
        result = 31 * result + (targetRecordId != null ? targetRecordId.hashCode() : 0);
        result = 31 * result + (changeType != null ? changeType.hashCode() : 0);
        result = 31 * result + (callContext != null ? callContext.hashCode() : 0);
        result = 31 * result + (recordId != null ? recordId.hashCode() : 0);
        result = 31 * result + (accountRecordId != null ? accountRecordId.hashCode() : 0);
        result = 31 * result + (tenantRecordId != null ? tenantRecordId.hashCode() : 0);
        return result;
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException {
        MapperHolder.mapper().readerForUpdating(this).readValue(new ExternalizableInput(in));
    }

    @Override
    public void writeExternal(final ObjectOutput oo) throws IOException {
        MapperHolder.mapper().writeValue(new ExternalizableOutput(oo), this);
    }
}
