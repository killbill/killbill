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

package com.ning.billing.util.audit.dao;

import com.ning.billing.util.ChangeType;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallContextBinder;
import com.ning.billing.util.dao.ChangeTypeBinder;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlBatch;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;

import java.util.List;

@ExternalizedSqlViaStringTemplate3
public interface AuditSqlDao {
    @SqlUpdate
    public void insertAuditFromTransaction(@Bind("tableName") final String tableName,
                                           @Bind("recordId") final String recordId,
                                           @ChangeTypeBinder final ChangeType changeType,
                                           @CallContextBinder CallContext context);

    @SqlBatch(transactional = false)
    public void insertAuditFromTransaction(@Bind("tableName") final String tableName,
                                           @Bind("recordId") final List<String> recordIds,
                                           @ChangeTypeBinder final ChangeType changeType,
                                           @CallContextBinder CallContext context);
}
