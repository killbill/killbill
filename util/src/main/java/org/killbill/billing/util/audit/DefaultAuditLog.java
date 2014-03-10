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

package org.killbill.billing.util.audit;

import java.util.UUID;

import org.joda.time.DateTime;

import org.killbill.billing.ObjectType;
import org.killbill.billing.entity.EntityBase;
import org.killbill.billing.util.audit.dao.AuditLogModelDao;

public class DefaultAuditLog extends EntityBase implements AuditLog {

    private final AuditLogModelDao auditLogModelDao;
    private final ObjectType objectType;
    private final UUID auditedEntityId;

    public DefaultAuditLog(final AuditLogModelDao auditLogModelDao, final ObjectType objectType, final UUID auditedEntityId) {
        super(auditLogModelDao);
        this.auditLogModelDao = auditLogModelDao;
        this.objectType = objectType;
        this.auditedEntityId = auditedEntityId;
    }

    @Override
    public UUID getAuditedEntityId() {
        return auditedEntityId;
    }

    @Override
    public ObjectType getAuditedObjectType() {
        return objectType;
    }

    @Override
    public ChangeType getChangeType() {
        return auditLogModelDao.getChangeType();
    }

    @Override
    public String getUserName() {
        return auditLogModelDao.getCallContext().getUserName();
    }

    @Override
    public DateTime getCreatedDate() {
        return auditLogModelDao.getCallContext().getCreatedDate();
    }

    @Override
    public String getReasonCode() {
        return auditLogModelDao.getCallContext().getReasonCode();
    }

    @Override
    public String getUserToken() {
        if (auditLogModelDao.getCallContext().getUserToken() == null) {
            return null;
        } else {
            return auditLogModelDao.getCallContext().getUserToken().toString();
        }
    }

    @Override
    public String getComment() {
        return auditLogModelDao.getCallContext().getComments();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DefaultAuditLog{");
        sb.append("auditLogModelDao=").append(auditLogModelDao);
        sb.append(", auditedEntityId=").append(auditedEntityId);
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

        final DefaultAuditLog that = (DefaultAuditLog) o;

        if (auditLogModelDao != null ? !auditLogModelDao.equals(that.auditLogModelDao) : that.auditLogModelDao != null) {
            return false;
        }
        if (auditedEntityId != null ? !auditedEntityId.equals(that.auditedEntityId) : that.auditedEntityId != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (auditLogModelDao != null ? auditLogModelDao.hashCode() : 0);
        result = 31 * result + (auditedEntityId != null ? auditedEntityId.hashCode() : 0);
        return result;
    }
}
