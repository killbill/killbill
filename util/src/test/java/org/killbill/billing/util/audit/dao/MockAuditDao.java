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

package org.killbill.billing.util.audit.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.audit.AuditLog;
import org.killbill.billing.util.audit.AuditLogWithHistory;
import org.killbill.billing.util.audit.DefaultAccountAuditLogs;
import org.killbill.billing.util.audit.DefaultAccountAuditLogsForObjectType;
import org.killbill.billing.util.dao.HistorySqlDao;
import org.killbill.billing.util.dao.TableName;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

public class MockAuditDao implements AuditDao {

    private final Map<TableName, Map<UUID, List<AuditLog>>> auditLogsForTables = new HashMap<TableName, Map<UUID, List<AuditLog>>>();

    public synchronized void addAuditLogForId(final TableName tableName, final UUID objectId, final AuditLog auditLog) {
        addAuditLogsForId(tableName, objectId, ImmutableList.<AuditLog>of(auditLog));
    }

    public synchronized void addAuditLogsForId(final TableName tableName, final UUID objectId, final List<AuditLog> auditLogs) {
        if (auditLogsForTables.get(tableName) == null) {
            auditLogsForTables.put(tableName, new HashMap<UUID, List<AuditLog>>());
        }

        if (auditLogsForTables.get(tableName).get(objectId) == null) {
            auditLogsForTables.get(tableName).put(objectId, new ArrayList<AuditLog>());
        }

        auditLogsForTables.get(tableName).get(objectId).addAll(auditLogs);
    }

    @Override
    public DefaultAccountAuditLogs getAuditLogsForAccountRecordId(final AuditLevel auditLevel, final InternalTenantContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DefaultAccountAuditLogsForObjectType getAuditLogsForAccountRecordId(final TableName tableName, final AuditLevel auditLevel, final InternalTenantContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<AuditLog> getAuditLogsForId(final TableName tableName, final UUID objectId, final AuditLevel auditLevel, final InternalTenantContext context) {
        final Map<UUID, List<AuditLog>> auditLogsForTableName = auditLogsForTables.get(tableName);
        if (auditLogsForTableName == null) {
            return ImmutableList.<AuditLog>of();
        }

        final List<AuditLog> auditLogsForObjectId = auditLogsForTableName.get(objectId);
        final List<AuditLog> allAuditLogs = MoreObjects.firstNonNull(auditLogsForObjectId, ImmutableList.<AuditLog>of());
        if (AuditLevel.FULL.equals(auditLevel)) {
            return allAuditLogs;
        } else if (AuditLevel.MINIMAL.equals(auditLevel) && allAuditLogs.size() > 0) {
            return ImmutableList.<AuditLog>of(allAuditLogs.get(0));
        } else if (AuditLevel.NONE.equals(auditLevel)) {
            return ImmutableList.<AuditLog>of();
        } else {
            return allAuditLogs;
        }
    }

    @Override
    public List<AuditLogWithHistory> getAuditLogsWithHistoryForId(final HistorySqlDao sqlDao, final TableName tableName, final UUID objectId, final AuditLevel auditLevel, final InternalTenantContext context) {
        throw new UnsupportedOperationException();
    }
}
