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

package com.ning.billing.jaxrs;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.jaxrs.json.AuditLogJson;

public abstract class JaxrsTestUtils {

    public static List<AuditLogJson> createAuditLogsJson(final DateTime changeDate) {
        final List<AuditLogJson> auditLogs = new ArrayList<AuditLogJson>();
        for (int i = 0; i < 20; i++) {
            auditLogs.add(new AuditLogJson(UUID.randomUUID().toString(), changeDate, UUID.randomUUID().toString(),
                                           UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString()));
        }

        return auditLogs;
    }
}
