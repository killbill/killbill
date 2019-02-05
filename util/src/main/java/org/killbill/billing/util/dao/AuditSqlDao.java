/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.audit.dao.AuditLogModelDao;
import org.killbill.commons.jdbi.binder.SmartBindBean;
import org.killbill.commons.jdbi.statement.SmartFetchSize;
import org.killbill.commons.jdbi.template.KillBillSqlDaoStringTemplate;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlBatch;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.customizers.BatchChunkSize;
import org.skife.jdbi.v2.sqlobject.customizers.Define;

/**
 * Note: in the queries below, tableName always refers to the TableName enum, not the actual table name (TableName.getTableName()).
 */
@KillBillSqlDaoStringTemplate("/org/killbill/billing/util/entity/dao/EntitySqlDao.sql.stg")
// Note: @RegisterMapper annotation won't work here as we build the SqlObject via EntitySqlDao (annotations won't be inherited for JDBI)
public interface AuditSqlDao {

    @SqlBatch
    @BatchChunkSize(1000) // Arbitrary value, just a safety mechanism in case of very large datasets
    public void insertAuditsFromTransaction(@SmartBindBean final Iterable<EntityAudit> audits,
                                            @SmartBindBean final InternalCallContext context);

    @SqlQuery
    @SmartFetchSize(shouldStream = true)
    public Iterator<AuditLogModelDao> getAuditLogsForAccountRecordId(@SmartBindBean final InternalTenantContext context);

    @SqlQuery
    @SmartFetchSize(shouldStream = true)
    public Iterator<AuditLogModelDao> getAuditLogsForTableNameAndAccountRecordId(@Bind("tableName") final String tableName,
                                                                                 @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    public List<AuditLogModelDao> getAuditLogsForTargetRecordId(@Bind("tableName") final String tableName,
                                                                @Bind("targetRecordId") final long targetRecordId,
                                                                @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    public List<AuditLogModelDao> getAuditLogsViaHistoryForTargetRecordId(@Bind("tableName") final String historyTableName, /* Uppercased - used to find entries in audit_log table */
                                                                          @Define("historyTableName") final String actualHistoryTableName, /* Actual table name, used in the inner join query */
                                                                          @Bind("targetRecordId") final long targetRecordId,
                                                                          @SmartBindBean final InternalTenantContext context);
}
