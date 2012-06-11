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
import org.skife.jdbi.v2.sqlobject.SqlBatch;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;

import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallContextBinder;

@ExternalizedSqlViaStringTemplate3
public interface AuditSqlDao {
    @SqlUpdate
    public void insertAuditFromTransaction(@AuditBinder final EntityAudit audit,
                                           @CallContextBinder final CallContext context);

    @SqlBatch(transactional = false)
    public void insertAuditFromTransaction(@AuditBinder final List<EntityAudit> audit,
                                           @CallContextBinder final CallContext context);

    @SqlQuery
    public Long getRecordId(@Bind("id") final String id);

    @SqlQuery
    public Long getHistoryRecordId(@Bind("recordId") final Long recordId);

}
