/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.util.dao;

import java.util.Iterator;
import java.util.List;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.Define;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.commons.jdbi.statement.SmartFetchSize;
import org.killbill.billing.util.audit.dao.AuditLogModelDao;
import org.killbill.billing.util.cache.Cachable;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.killbill.billing.util.cache.CachableKey;
import org.killbill.billing.util.entity.dao.EntitySqlDaoStringTemplate;

/**
 * Note 1: cache invalidation has to happen for audit logs (which is tricky in the multi-nodes scenario).
 * For now, we're using a time-based eviction strategy (see ehcache.xml)
 * which is good enough: the cache will always get at least the initial CREATION audit log entry, which is the one
 * we really care about (both for Analytics and for Kaui's endpoints). Besides, we do cache invalidation properly
 * on our own node (see EntitySqlDaoWrapperInvocationHandler).
 * <p/>
 * Note 2: in the queries below, tableName always refers to the TableName enum, not the actual table name (TableName.getTableName()).
 */
@EntitySqlDaoStringTemplate("/org/killbill/billing/util/entity/dao/EntitySqlDao.sql.stg")
// Note: @RegisterMapper annotation won't work here as we build the SqlObject via EntitySqlDao (annotations won't be inherited for JDBI)
public interface AuditSqlDao {

    @SqlUpdate
    public void insertAuditFromTransaction(@BindBean final EntityAudit audit,
                                           @BindBean final InternalCallContext context);

    @SqlQuery
    @SmartFetchSize(shouldStream = true)
    public Iterator<AuditLogModelDao> getAuditLogsForAccountRecordId(@BindBean final InternalTenantContext context);

    @SqlQuery
    @SmartFetchSize(shouldStream = true)
    public Iterator<AuditLogModelDao> getAuditLogsForTableNameAndAccountRecordId(@Bind("tableName") final String tableName,
                                                                                 @BindBean final InternalTenantContext context);

    @SqlQuery
    @Cachable(CacheType.AUDIT_LOG)
    public List<AuditLogModelDao> getAuditLogsForTargetRecordId(@CachableKey(1) @Bind("tableName") final String tableName,
                                                                @CachableKey(2) @Bind("targetRecordId") final long targetRecordId,
                                                                @BindBean final InternalTenantContext context);

    @SqlQuery
    @Cachable(CacheType.AUDIT_LOG_VIA_HISTORY)
    public List<AuditLogModelDao> getAuditLogsViaHistoryForTargetRecordId(@CachableKey(1) @Bind("tableName") final String historyTableName, /* Uppercased - used to find entries in audit_log table */
                                                                          @CachableKey(2) @Define("historyTableName") final String actualHistoryTableName, /* Actual table name, used in the inner join query */
                                                                          @CachableKey(3) @Bind("targetRecordId") final long targetRecordId,
                                                                          @BindBean final InternalTenantContext context);
}
