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

import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.dao.EntityAudit;

public class DefaultAuditLog extends EntityAudit implements AuditLog {

    private final CallContext callContext;

    public DefaultAuditLog(final EntityAudit entityAudit, final CallContext callContext) {
        super(entityAudit.getId(), entityAudit.getTableName(), entityAudit.getTargetRecordId(), entityAudit.getChangeType(), entityAudit.getCreatedDate());
        this.callContext = callContext;
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
        sb.append("DefaultAuditLog {");
        sb.append(super.toString());
        sb.append(", callContext=").append(callContext);
        sb.append('}');
        return sb.toString();
    }
}
