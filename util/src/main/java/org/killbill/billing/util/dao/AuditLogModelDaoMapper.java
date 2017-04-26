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

package org.killbill.billing.util.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import org.killbill.billing.callcontext.DefaultCallContext;
import org.killbill.billing.util.audit.ChangeType;
import org.killbill.billing.util.audit.dao.AuditLogModelDao;
import org.killbill.billing.util.callcontext.CallContext;

public class AuditLogModelDaoMapper extends MapperBase implements ResultSetMapper<AuditLogModelDao> {

    @Override
    public AuditLogModelDao map(final int index, final ResultSet r, final StatementContext ctx) throws SQLException {
        final UUID id = getUUID(r, "id");
        final String tableName = r.getString("table_name");
        final long targetRecordId = r.getLong("target_record_id");
        final String changeType = r.getString("change_type");
        final DateTime createdDate = getDateTime(r, "created_date");
        final String createdBy = r.getString("created_by");
        final String reasonCode = r.getString("reason_code");
        final String comments = r.getString("comments");
        final UUID userToken = getUUID(r, "user_token");

        final EntityAudit entityAudit = new EntityAudit(id, TableName.valueOf(tableName), targetRecordId, ChangeType.valueOf(changeType), createdDate);
        // TODO - we have the tenant_record_id but not the tenant id here
        final CallContext callContext = new DefaultCallContext(null, null, createdBy, createdDate, reasonCode, comments, userToken);
        return new AuditLogModelDao(entityAudit, callContext);
    }
}
