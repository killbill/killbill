/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.util.audit;

import org.joda.time.DateTime;

import com.ning.billing.util.ChangeType;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.dao.EntityAudit;

public class DefaultAuditLog implements AuditLog {

    private final EntityAudit entityAudit;
    private final CallContext callContext;

    public DefaultAuditLog(final EntityAudit entityAudit, final CallContext callContext) {
        this.entityAudit = entityAudit;
        this.callContext = callContext;
    }

    @Override
    public ChangeType getChangeType() {
        return entityAudit.getChangeType();
    }

    @Override
    public String getUserName() {
        return callContext.getUserName();
    }

    @Override
    public DateTime getCreatedDate() {
        return callContext.getCreatedDate();
    }

    @Override
    public String getReasonCode() {
        return callContext.getReasonCode();
    }

    @Override
    public String getUserToken() {
        if (callContext.getUserToken() == null) {
            return null;
        } else {
            return callContext.getUserToken().toString();
        }
    }

    @Override
    public String getComment() {
        return callContext.getComment();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("DefaultAuditLog");
        sb.append("{entityAudit=").append(entityAudit);
        sb.append(", callContext=").append(callContext);
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

        final DefaultAuditLog that = (DefaultAuditLog) o;

        if (callContext != null ? !callContext.equals(that.callContext) : that.callContext != null) {
            return false;
        }
        if (entityAudit != null ? !entityAudit.equals(that.entityAudit) : that.entityAudit != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = entityAudit != null ? entityAudit.hashCode() : 0;
        result = 31 * result + (callContext != null ? callContext.hashCode() : 0);
        return result;
    }
}
