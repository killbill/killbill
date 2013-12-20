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

package com.ning.billing.util.audit.api;

import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import com.ning.billing.ObjectType;
import com.ning.billing.util.api.AuditLevel;
import com.ning.billing.util.api.AuditUserApi;
import com.ning.billing.util.audit.AccountAuditLogs;
import com.ning.billing.util.audit.AccountAuditLogsForObjectType;
import com.ning.billing.util.audit.AuditLog;
import com.ning.billing.util.audit.DefaultAccountAuditLogs;
import com.ning.billing.util.audit.DefaultAccountAuditLogsForObjectType;
import com.ning.billing.util.audit.dao.AuditDao;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.billing.util.dao.TableName;

import com.google.common.collect.ImmutableList;

public class DefaultAuditUserApi implements AuditUserApi {

    private final AuditDao auditDao;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public DefaultAuditUserApi(final AuditDao auditDao, final InternalCallContextFactory internalCallContextFactory) {
        this.auditDao = auditDao;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public AccountAuditLogs getAccountAuditLogs(final UUID accountId, final AuditLevel auditLevel, final TenantContext tenantContext) {
        // Optimization - bail early
        if (AuditLevel.NONE.equals(auditLevel)) {
            return new DefaultAccountAuditLogs(accountId);
        }

        return auditDao.getAuditLogsForAccountRecordId(auditLevel, internalCallContextFactory.createInternalTenantContext(accountId, tenantContext));
    }

    @Override
    public AccountAuditLogsForObjectType getAccountAuditLogs(final UUID accountId, final ObjectType objectType, final AuditLevel auditLevel, final TenantContext tenantContext) {
        // Optimization - bail early
        if (AuditLevel.NONE.equals(auditLevel)) {
            return new DefaultAccountAuditLogsForObjectType(auditLevel);
        }

        final TableName tableName = getTableNameFromObjectType(objectType);
        if (tableName == null) {
            return new DefaultAccountAuditLogsForObjectType(auditLevel);
        }

        return auditDao.getAuditLogsForAccountRecordId(tableName, auditLevel, internalCallContextFactory.createInternalTenantContext(accountId, tenantContext));
    }

    @Override
    public List<AuditLog> getAuditLogs(final UUID objectId, final ObjectType objectType, final AuditLevel auditLevel, final TenantContext context) {
        // Optimization - bail early
        if (AuditLevel.NONE.equals(auditLevel)) {
            return ImmutableList.<AuditLog>of();
        }

        final TableName tableName = getTableNameFromObjectType(objectType);
        if (tableName == null) {
            return ImmutableList.<AuditLog>of();
        }

        return auditDao.getAuditLogsForId(tableName, objectId, auditLevel, internalCallContextFactory.createInternalTenantContext(context));
    }

    private TableName getTableNameFromObjectType(final ObjectType objectType) {
        for (final TableName tableName : TableName.values()) {
            if (objectType.equals(tableName.getObjectType())) {
                return tableName;
            }
        }

        return null;
    }
}
