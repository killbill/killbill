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

import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import org.skife.jdbi.v2.IDBI;

import com.ning.billing.util.api.AuditLevel;
import com.ning.billing.util.audit.AuditLog;
import com.ning.billing.util.audit.ChangeType;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.callcontext.InternalTenantContext;
import com.ning.billing.clock.Clock;
import com.ning.billing.util.dao.NonEntityDao;
import com.ning.billing.util.dao.NonEntitySqlDao;
import com.ning.billing.util.dao.TableName;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoTransactionWrapper;
import com.ning.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import com.ning.billing.util.entity.dao.EntitySqlDaoWrapperFactory;

import com.google.common.collect.ImmutableList;

public class DefaultAuditDao implements AuditDao {

    private final NonEntitySqlDao nonEntitySqlDao;
    private final EntitySqlDaoTransactionalJdbiWrapper transactionalSqlDao;

    @Inject
    public DefaultAuditDao(final IDBI dbi, final Clock clock, final CacheControllerDispatcher cacheControllerDispatcher, final NonEntityDao nonEntityDao) {
        this.nonEntitySqlDao = dbi.onDemand(NonEntitySqlDao.class);
        this.transactionalSqlDao = new EntitySqlDaoTransactionalJdbiWrapper(dbi, clock, cacheControllerDispatcher, nonEntityDao);
    }

    @Override
    public List<AuditLog> getAuditLogsForId(final TableName tableName, final UUID objectId, final AuditLevel auditLevel, final InternalTenantContext context) {
        if (tableName.hasHistoryTable()) {
            return doGetAuditLogsViaHistoryForId(tableName, objectId, auditLevel, context);
        } else {
            return doGetAuditLogsForId(tableName, objectId, auditLevel, context);
        }
    }

    private List<AuditLog> doGetAuditLogsForId(final TableName tableName, final UUID objectId, final AuditLevel auditLevel, final InternalTenantContext context) {
        final Long recordId = nonEntitySqlDao.getRecordIdFromObject(objectId.toString(), tableName.getTableName());
        if (recordId == null) {
            return ImmutableList.<AuditLog>of();
        } else {
            return getAuditLogsForRecordId(tableName, recordId, auditLevel, context);
        }
    }

    private List<AuditLog> doGetAuditLogsViaHistoryForId(final TableName tableName, final UUID objectId, final AuditLevel auditLevel, final InternalTenantContext context) {
        final TableName historyTableName = tableName.getHistoryTableName();
        if (historyTableName == null) {
            throw new IllegalStateException("History table shouldn't be null for " + tableName);
        }

        final Long targetRecordId = nonEntitySqlDao.getRecordIdFromObject(objectId.toString(), tableName.getTableName());
        final List<AuditLog> allAuditLogs = transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<AuditLog>>() {
            @Override
            public List<AuditLog> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(EntitySqlDao.class).getAuditLogsViaHistoryForTargetRecordId(historyTableName.name(),
                                                                                                                     historyTableName.getTableName().toLowerCase(),
                                                                                                                     targetRecordId,
                                                                                                                     context);
            }
        });
        return buildAuditLogs(auditLevel, allAuditLogs);
    }

    private List<AuditLog> getAuditLogsForRecordId(final TableName tableName, final Long targetRecordId, final AuditLevel auditLevel, final InternalTenantContext context) {
        final List<AuditLog> allAuditLogs = transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<AuditLog>>() {
            @Override
            public List<AuditLog> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(EntitySqlDao.class).getAuditLogsForTargetRecordId(tableName.name(),
                                                                                                           targetRecordId,
                                                                                                           context);
            }
        });
        return buildAuditLogs(auditLevel, allAuditLogs);
    }

    private List<AuditLog> buildAuditLogs(final AuditLevel auditLevel, final List<AuditLog> auditLogs) {
        // TODO Do the filtering in the query
        if (AuditLevel.FULL.equals(auditLevel)) {
            return auditLogs;
        } else if (AuditLevel.MINIMAL.equals(auditLevel) && auditLogs.size() > 0) {
            if (ChangeType.INSERT.equals(auditLogs.get(0).getChangeType())) {
                return ImmutableList.<AuditLog>of(auditLogs.get(0));
            } else {
                // We may be coming here via the history code path - only a single mapped history record id
                // will be for the initial INSERT
                return ImmutableList.<AuditLog>of();
            }
        } else if (AuditLevel.NONE.equals(auditLevel)) {
            return ImmutableList.<AuditLog>of();
        } else {
            return auditLogs;
        }
    }
}
