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

package com.ning.billing.util.customfield.dao;

import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallContextBinder;
import com.ning.billing.util.customfield.CustomFieldHistory;
import com.ning.billing.util.customfield.CustomFieldHistoryBinder;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlBatch;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;

import java.util.List;

@ExternalizedSqlViaStringTemplate3
public interface CustomFieldAuditSqlDao extends Transactional<CustomFieldAuditSqlDao> {
    @SqlBatch(transactional=false)
    public void batchInsertAuditLogFromTransaction(@Bind("objectId") final String objectId,
                                                   @Bind("objectType") final String objectType,
                                                   @CustomFieldHistoryBinder final List<CustomFieldHistory> entities,
                                                   @CallContextBinder final CallContext context);
}
