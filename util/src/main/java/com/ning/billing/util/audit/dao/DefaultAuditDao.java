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

package com.ning.billing.util.audit.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import org.skife.jdbi.v2.IDBI;

import com.ning.billing.util.api.AuditLevel;
import com.ning.billing.util.audit.AuditLog;
import com.ning.billing.util.dao.AuditSqlDao;
import com.ning.billing.util.dao.TableName;

import com.google.common.collect.ImmutableList;

public class DefaultAuditDao implements AuditDao {

    private final AuditSqlDao auditSqlDao;

    @Inject
    public DefaultAuditDao(final IDBI dbi) {
        this.auditSqlDao = dbi.onDemand(AuditSqlDao.class);
    }

    @Override
    public List<AuditLog> getAuditLogsForId(final TableName tableName, final UUID objectId, final AuditLevel auditLevel) {
        if (tableName.hasHistoryTable()) {
            return doGetAuditLogsViaHistoryForId(tableName, objectId, auditLevel);
        } else {
            return doGetAuditLogsForId(tableName, objectId, auditLevel);
        }
    }

    private List<AuditLog> doGetAuditLogsForId(final TableName tableName, final UUID objectId, final AuditLevel auditLevel) {
        // Look at the table and gather all record_id for that objectId
        final Long recordId = auditSqlDao.getRecordIdForTable(tableName.getTableName().toLowerCase(), objectId.toString());
        if (recordId == null) {
            return ImmutableList.<AuditLog>of();
        } else {
            return getAuditLogsForRecordId(tableName, recordId, auditLevel);
        }
    }

    private List<AuditLog> doGetAuditLogsViaHistoryForId(final TableName tableName, final UUID objectId, final AuditLevel auditLevel) {
        final List<AuditLog> auditLogs = new ArrayList<AuditLog>();

        // Look at the history table and gather all the history_record_id for that objectId
        final List<Long> recordIds = auditSqlDao.getHistoryRecordIdsForTable(tableName.getHistoryTableName().getTableName().toLowerCase(),
                                                                             objectId.toString());
        if (recordIds == null) {
            return auditLogs;
        } else {
            for (final Long recordId : recordIds) {
                auditLogs.addAll(getAuditLogsForRecordId(tableName.getHistoryTableName(), recordId, auditLevel));
            }

            return auditLogs;
        }
    }

    private List<AuditLog> getAuditLogsForRecordId(final TableName tableName, final Long recordId, final AuditLevel auditLevel) {
        final List<AuditLog> allAuditLogs = auditSqlDao.getAuditLogsForRecordId(tableName, recordId);
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
}
