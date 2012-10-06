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

package com.ning.billing.util.callcontext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.dao.ObjectType;
import com.ning.billing.util.dao.TableName;

public class InternalCallContextFactory {

    private static final Logger log = LoggerFactory.getLogger(InternalCallContextFactory.class);

    public static final UUID INTERNAL_TENANT_ID = new UUID(0L, 0L);
    public static final long INTERNAL_TENANT_RECORD_ID = 0L;

    private final IDBI dbi;
    private final CallContextSqlDao callContextSqlDao;
    private final Clock clock;

    @Inject
    public InternalCallContextFactory(final IDBI dbi, final Clock clock) {
        this.dbi = dbi;
        this.callContextSqlDao = dbi.onDemand(CallContextSqlDao.class);
        this.clock = clock;
    }

    // Internal use only (notification queue, etc.) - no tenant for now
    public InternalTenantContext createInternalTenantContext() {
        return createInternalTenantContext(INTERNAL_TENANT_RECORD_ID, null);
    }

    // Used for r/o operations - we don't need the account id in that case
    public InternalTenantContext createInternalTenantContext(final TenantContext context) {
        return createInternalTenantContext(getTenantRecordId(context), null);
    }

    // Used for r/w operations - we need the account id to populate the account_record_id field
    public InternalTenantContext createInternalTenantContext(final UUID accountId, final TenantContext context) {
        return createInternalTenantContext(getTenantRecordId(context), getAccountRecordId(accountId));
    }

    // Internal use only (notification queue, etc.) - no tenant for now
    public InternalCallContext createInternalCallContext(final String userName, final CallOrigin callOrigin, final UserType userType, @Nullable final UUID userToken) {
        return createInternalCallContext(INTERNAL_TENANT_RECORD_ID, null, new DefaultCallContext(INTERNAL_TENANT_ID, userName, callOrigin, userType, userToken, clock));
    }

    public InternalCallContext createInternalCallContext(final UUID objectId, final ObjectType objectType, final String userName,
                                                         final CallOrigin callOrigin, final UserType userType, @Nullable final UUID userToken) {
        // TODO retrieve the same way the tenant
        return createInternalCallContext(objectId, objectType, new DefaultCallContext(INTERNAL_TENANT_ID, userName, callOrigin, userType, userToken, clock));
    }

    public InternalCallContext createInternalCallContext(final UUID accountId, final String userName, final CallOrigin callOrigin, final UserType userType, @Nullable final UUID userToken) {
        // TODO retrieve the same way the tenant
        return createInternalCallContext(accountId, new DefaultCallContext(INTERNAL_TENANT_ID, userName, callOrigin, userType, userToken, clock));
    }

    /**
     * Crate an internal call context from a call context, and retrieving the account_record_id from another table
     *
     * @param objectId   the id of the row in the table pointed by object type where to look for account_record_id
     * @param objectType the object type pointed by this objectId
     * @param context    original call context
     * @return internal call context from context, with a non null account_record_id (if found)
     */
    public InternalCallContext createInternalCallContext(final UUID objectId, final ObjectType objectType, final CallContext context) {
        final Long accountRecordId;

        final TableName tableName = TableName.fromObjectType(objectType);
        if (tableName != null) {
            accountRecordId = dbi.withHandle(new HandleCallback<Long>() {
                @Override
                public Long withHandle(final Handle handle) throws Exception {
                    final String columnName;
                    if (TableName.TAG_DEFINITIONS.equals(tableName) || TableName.TAG_DEFINITION_HISTORY.equals(tableName)) {
                        // Not tied to an account
                        return null;
                    } else if (TableName.ACCOUNT.equals(tableName) || TableName.ACCOUNT_HISTORY.equals(tableName)) {
                        // Lookup the record_id directly
                        columnName = "record_id";
                    } else {
                        // The table should have an account_record_id column
                        columnName = "account_record_id";
                    }

                    final List<Map<String, Object>> values = handle.select(String.format("select %s from %s where id = ?;", columnName, tableName.getTableName()), objectId.toString());
                    if (values.size() == 0) {
                        return null;
                    } else {
                        return (Long) values.get(0).get(columnName);
                    }
                }
            });
        } else {
            accountRecordId = null;
        }

        return createInternalCallContext(getTenantRecordId(context), accountRecordId, context);
    }

    // Used for update/delete operations - we don't need the account id in that case
    // Used also when we don't have an account_record_id column (e.g. tenants, tag_definitions)
    public InternalCallContext createInternalCallContext(final CallContext context) {
        return createInternalCallContext(getTenantRecordId(context), null, context);
    }

    // Used for r/w operations - we need the account id to populate the account_record_id field
    public InternalCallContext createInternalCallContext(final UUID accountId, final CallContext context) {
        return createInternalCallContext(getTenantRecordId(context), getAccountRecordId(accountId), context);
    }

    private InternalTenantContext createInternalTenantContext(final Long tenantRecordId, @Nullable final Long accountRecordId) {
        return new InternalTenantContext(tenantRecordId, accountRecordId);
    }

    private InternalCallContext createInternalCallContext(final Long tenantRecordId, @Nullable final Long accountRecordId, final CallContext context) {
        return new InternalCallContext(tenantRecordId, accountRecordId, context);
    }

    // Used when we need to re-hydrate the context with the account_record_id (when creating the account)
    public InternalCallContext createInternalCallContext(final Long accountRecordId, final InternalCallContext context) {
        return new InternalCallContext(context.getTenantRecordId(), accountRecordId, context.getUserToken(), context.getUserName(),
                                       context.getCallOrigin(), context.getUserType(), context.getReasonCode(), context.getComment(),
                                       context.getCreatedDate(), context.getUpdatedDate());
    }

    private Long getTenantRecordId(final TenantContext context) {
        // Default to single default tenant (e.g. single tenant mode)
        if (context.getTenantId() == null) {
            return INTERNAL_TENANT_RECORD_ID;
        } else {
            return callContextSqlDao.getTenantRecordId(context.getTenantId().toString());
        }
    }

    private Long getAccountRecordId(final UUID accountId) {
        return callContextSqlDao.getAccountRecordId(accountId.toString());
    }
}
