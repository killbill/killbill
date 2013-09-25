/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.util.dao;

import java.util.List;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.Define;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;

import com.ning.billing.util.audit.AuditLog;
import com.ning.billing.util.cache.Cachable;
import com.ning.billing.util.cache.Cachable.CacheType;
import com.ning.billing.util.cache.CachableKey;
import com.ning.billing.callcontext.InternalCallContext;
import com.ning.billing.callcontext.InternalTenantContext;
import com.ning.billing.util.entity.dao.EntitySqlDaoStringTemplate;

/**
 * Note: cache invalidation has to happen for audit logs (which is tricky in the multi-nodes scenario).
 * For now, we're using a time-based eviction strategy (see timeToIdleSeconds and timeToLiveSeconds in ehcache.xml)
 * which is good enough: the cache will always get at least the initial CREATION audit log entry, which is the one
 * we really care about (both for Analytics and for Kaui's endpoints). Besides, we do cache invalidation properly
 * on our own node (see EntitySqlDaoWrapperInvocationHandler).
 */
@EntitySqlDaoStringTemplate("/com/ning/billing/util/entity/dao/EntitySqlDao.sql.stg")
@RegisterMapper(AuditLogMapper.class)
public interface AuditSqlDao {

    @SqlUpdate
    public void insertAuditFromTransaction(@BindBean final EntityAudit audit,
                                           @BindBean final InternalCallContext context);

    @SqlQuery
    @Cachable(CacheType.AUDIT_LOG)
    public List<AuditLog> getAuditLogsForTargetRecordId(@CachableKey(1) @BindBean final TableName tableName,
                                                        @CachableKey(2) @Bind("targetRecordId") final long targetRecordId,
                                                        @BindBean final InternalTenantContext context);

    @SqlQuery
    @Cachable(CacheType.AUDIT_LOG_VIA_HISTORY)
    public List<AuditLog> getAuditLogsViaHistoryForTargetRecordId(@CachableKey(1) @BindBean final TableName historyTableName, /* Uppercased - used to find entries in audit_log table */
                                                                  @CachableKey(2) @Define("historyTableName") final String actualHistoryTableName, /* Actual table name, used in the inner join query */
                                                                  @CachableKey(3) @Bind("targetRecordId") final long targetRecordId,
                                                                  @BindBean final InternalTenantContext context);
}
