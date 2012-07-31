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

package com.ning.billing.jaxrs.json;

import java.util.List;

import javax.annotation.Nullable;

import com.ning.billing.util.audit.AuditLog;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;

public abstract class JsonBase {

    protected List<AuditLogJson> auditLogs;

    public JsonBase(@Nullable final List<AuditLogJson> auditLogs) {
        this.auditLogs = auditLogs;
    }

    protected static ImmutableList<AuditLogJson> toAuditLogJson(@Nullable final List<AuditLog> auditLogs) {
        if (auditLogs == null) {
            return null;
        }

        return ImmutableList.<AuditLogJson>copyOf(Collections2.transform(auditLogs, new Function<AuditLog, AuditLogJson>() {
            @Override
            public AuditLogJson apply(@Nullable final AuditLog input) {
                return new AuditLogJson(input);
            }
        }));
    }

    protected static String reasonCodeFromAuditLogs(@Nullable final List<AuditLog> auditLogs) {
        if (auditLogs == null || auditLogs.size() == 0) {
            return null;
        }

        return auditLogs.get(0).getReasonCode();
    }

    public List<AuditLogJson> getAuditLogs() {
        return auditLogs;
    }
}
