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

import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.util.clock.Clock;

public class InternalCallContextFactory {

    private static final Logger log = LoggerFactory.getLogger(InternalCallContextFactory.class);

    public static final UUID INTERNAL_TENANT_ID = new UUID(0L, 0L);
    public static final long INTERNAL_TENANT_RECORD_ID = 0L;

    private final CallContextSqlDao callContextSqlDao;
    private final Clock clock;

    @Inject
    public InternalCallContextFactory(final IDBI dbi, final Clock clock) {
        this.callContextSqlDao = dbi.onDemand(CallContextSqlDao.class);
        this.clock = clock;
    }

    // Internal use only (notification queue, etc.) - no tenant for now
    public InternalTenantContext createInternalTenantContext() {
        return createInternalTenantContext(INTERNAL_TENANT_RECORD_ID, null);
    }

    // Used for r/o or update/delete operations - we don't need the account id in that case
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

    // Used for r/o or update/delete operations - we don't need the account id in that case
    // TODO - more work is needed for this statement to hold (especially for junction, overdue, custom fields and tags)
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
