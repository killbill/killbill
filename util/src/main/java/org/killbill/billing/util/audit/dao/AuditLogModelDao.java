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

package org.killbill.billing.util.audit.dao;

import org.killbill.billing.util.audit.AuditLog;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.dao.EntityAudit;
import org.killbill.billing.util.dao.TableName;
import org.killbill.billing.util.entity.dao.EntityModelDao;

public class AuditLogModelDao extends EntityAudit implements EntityModelDao<AuditLog> {

    private final CallContext callContext;

    public AuditLogModelDao(final EntityAudit entityAudit, final CallContext callContext) {
        super(entityAudit.getId(), entityAudit.getTableName(), entityAudit.getTargetRecordId(), entityAudit.getChangeType(), entityAudit.getCreatedDate());
        this.callContext = callContext;
    }

    public CallContext getCallContext() {
        return callContext;
    }

    @Override
    public TableName getHistoryTableName() {
        return null;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("AuditLogModelDao{");
        sb.append("callContext=").append(callContext);
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

        final AuditLogModelDao that = (AuditLogModelDao) o;

        if (callContext != null ? !callContext.equals(that.callContext) : that.callContext != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (callContext != null ? callContext.hashCode() : 0);
        return result;
    }
}
