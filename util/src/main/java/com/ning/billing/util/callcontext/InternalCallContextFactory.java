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

import org.joda.time.DateTime;

import com.ning.billing.ObjectType;
import com.ning.billing.util.cache.Cachable.CacheType;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.dao.NonEntityDao;

import com.google.common.base.Objects;

public class InternalCallContextFactory {

    public static final long INTERNAL_TENANT_RECORD_ID = 0L;

    private final Clock clock;
    private final NonEntityDao nonEntityDao;
    private final CacheControllerDispatcher cacheControllerDispatcher;

    @Inject
    public InternalCallContextFactory(final Clock clock, final NonEntityDao nonEntityDao, final CacheControllerDispatcher cacheControllerDispatcher) {
        this.clock = clock;
        this.nonEntityDao = nonEntityDao;
        this.cacheControllerDispatcher = cacheControllerDispatcher;
    }


    /**
     * Create an internal tenant context from a tenant context
     * <p/>
     * This is used for r/o operations - we don't need the account id in that case
     *
     * @param context tenant context (tenantId can be null only if multi-tenancy is disabled)
     * @return internal tenant context
     */
    public InternalTenantContext createInternalTenantContext(final TenantContext context) {
        // If tenant id is null, this will default to the default tenant record id (multi-tenancy disabled)
        final Long tenantRecordId = getTenantRecordId(context);
        return createInternalTenantContext(tenantRecordId, null);
    }

    /**
     * Create an internal tenant context
     *
     * @param tenantRecordId  tenant_record_id (cannot be null)
     * @param accountRecordId account_record_id (cannot be null for INSERT operations)
     * @return internal tenant context
     */
    public InternalTenantContext createInternalTenantContext(final Long tenantRecordId, @Nullable final Long accountRecordId) {
        //Preconditions.checkNotNull(tenantRecordId, "tenantRecordId cannot be null");
        return new InternalTenantContext(tenantRecordId, accountRecordId);
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
        // The context may come from a user API - for security, check we're not doing cross-tenants operations
        //final Long tenantRecordIdFromObject = retrieveTenantRecordIdFromObject(objectId, objectType);
        //final Long tenantRecordIdFromContext = getTenantRecordId(context);
        //Preconditions.checkState(tenantRecordIdFromContext.equals(tenantRecordIdFromObject),
        //                         "tenant of the pointed object (%s) and the context (%s) don't match!", tenantRecordIdFromObject, tenantRecordIdFromContext);

        return createInternalCallContext(objectId, objectType, context.getUserName(), context.getCallOrigin(),
                                         context.getUserType(), context.getUserToken(), context.getReasonCode(), context.getComments(),
                                         context.getCreatedDate(), context.getUpdatedDate());
    }

    /**
     * Create an internal call context using an existing account to retrieve tenant and account record ids
     * <p/>
     * This is used for r/w operations - we need the account id to populate the account_record_id field
     *
     * @param accountId account id
     * @param context   original call context
     * @return internal call context
     */
    public InternalCallContext createInternalCallContext(final UUID accountId, final CallContext context) {
        return createInternalCallContext(accountId, ObjectType.ACCOUNT, context.getUserName(), context.getCallOrigin(),
                                         context.getUserType(), context.getUserToken(), context.getReasonCode(), context.getComments(),
                                         context.getCreatedDate(), context.getUpdatedDate());
    }

    /**
     * Create an internal call context using an existing account to retrieve tenant and account record ids
     *
     * @param accountId  account id
     * @param userName   user name
     * @param callOrigin call origin
     * @param userType   user type
     * @param userToken  user token, if any
     * @return internal call context
     */
    public InternalCallContext createInternalCallContext(final UUID accountId, final String userName, final CallOrigin callOrigin,
                                                         final UserType userType, @Nullable final UUID userToken) {
        return createInternalCallContext(accountId, ObjectType.ACCOUNT, userName, callOrigin, userType, userToken);
    }

    /**
     * Create an internal call context using an existing object to retrieve tenant and account record ids
     *
     * @param objectId   the id of the row in the table pointed by object type where to look for account_record_id
     * @param objectType the object type pointed by this objectId
     * @param userName   user name
     * @param callOrigin call origin
     * @param userType   user type
     * @param userToken  user token, if any
     * @return internal call context
     */
    public InternalCallContext createInternalCallContext(final UUID objectId, final ObjectType objectType, final String userName,
                                                         final CallOrigin callOrigin, final UserType userType, @Nullable final UUID userToken) {
        return createInternalCallContext(objectId, objectType, userName, callOrigin, userType, userToken, null, null, clock.getUTCNow(), clock.getUTCNow());
    }

    public InternalCallContext createInternalCallContext(final UUID objectId, final ObjectType objectType, final String userName,
                                                         final CallOrigin callOrigin, final UserType userType, @Nullable final UUID userToken,
                                                         @Nullable final String reasonCode, @Nullable final String comment, final DateTime createdDate,
                                                         final DateTime updatedDate) {
        final Long tenantRecordId = nonEntityDao.retrieveTenantRecordIdFromObject(objectId, objectType, cacheControllerDispatcher.getCacheController(CacheType.TENANT_RECORD_ID));
        final Long accountRecordId = nonEntityDao.retrieveAccountRecordIdFromObject(objectId, objectType, cacheControllerDispatcher.getCacheController(CacheType.ACCOUNT_RECORD_ID));
        return createInternalCallContext(tenantRecordId, accountRecordId, userName, callOrigin, userType, userToken,
                                         reasonCode, comment, createdDate, updatedDate);
    }

    /**
     * Create an internal call context
     * <p/>
     * This is used by notification queue and persistent bus - accountRecordId is expected to be non null
     *
     * @param tenantRecordId  tenant record id - if null, the default tenant record id value will be used
     * @param accountRecordId account record id (cannot be null)
     * @param userName        user name
     * @param callOrigin      call origin
     * @param userType        user type
     * @param userToken       user token, if any
     * @return internal call context
     */
    public InternalCallContext createInternalCallContext(@Nullable final Long tenantRecordId, final Long accountRecordId, final String userName,
                                                         final CallOrigin callOrigin, final UserType userType, @Nullable final UUID userToken) {
        return new InternalCallContext(tenantRecordId, accountRecordId, userToken, userName, callOrigin, userType, null, null,
                                       clock.getUTCNow(), clock.getUTCNow());
    }

    private InternalCallContext createInternalCallContext(@Nullable final Long tenantRecordId, final Long accountRecordId, final String userName,
                                                          final CallOrigin callOrigin, final UserType userType, @Nullable final UUID userToken,
                                                          @Nullable final String reasonCode, @Nullable final String comment, final DateTime createdDate,
                                                          final DateTime updatedDate) {
        //Preconditions.checkNotNull(accountRecordId, "accountRecordId cannot be null");
        final Long nonNulTenantRecordId = Objects.firstNonNull(tenantRecordId, INTERNAL_TENANT_RECORD_ID);

        return new InternalCallContext(nonNulTenantRecordId, accountRecordId, userToken, userName, callOrigin, userType, reasonCode, comment,
                                       createdDate, updatedDate);
    }

    /**
     * Create an internal call context without populating the account record id
     * <p/>
     * This is used for update/delete operations - we don't need the account id in that case - and
     * also when we don't have an account_record_id column (e.g. tenants, tag_definitions)
     *
     * @param context original call context
     * @return internal call context
     */
    public InternalCallContext createInternalCallContext(final CallContext context) {
        // If tenant id is null, this will default to the default tenant record id (multi-tenancy disabled)
        final Long tenantRecordId = getTenantRecordId(context);
        return new InternalCallContext(tenantRecordId, null, context);
    }

    // Used when we need to re-hydrate the context with the account_record_id (when creating the account)
    public InternalCallContext createInternalCallContext(final Long accountRecordId, final InternalCallContext context) {
        return new InternalCallContext(context.getTenantRecordId(), accountRecordId, context.getUserToken(), context.getCreatedBy(),
                                       context.getCallOrigin(), context.getContextUserType(), context.getReasonCode(), context.getComments(),
                                       context.getCreatedDate(), context.getUpdatedDate());
    }

    // Used when we need to re-hydrate the context with the tenant_record_id and account_record_id (when claiming bus events)
    public InternalCallContext createInternalCallContext(final Long tenantRecordId, final Long accountRecordId, final InternalCallContext context) {
        return new InternalCallContext(tenantRecordId, accountRecordId, context.getUserToken(), context.getCreatedBy(),
                                       context.getCallOrigin(), context.getContextUserType(), context.getReasonCode(), context.getComments(),
                                       context.getCreatedDate(), context.getUpdatedDate());
    }

    private Long getTenantRecordId(final TenantContext context) {
        // Default to single default tenant (e.g. single tenant mode)
        if (context.getTenantId() == null) {
            return INTERNAL_TENANT_RECORD_ID;
        } else {
            return nonEntityDao.retrieveTenantRecordIdFromObject(context.getTenantId(), ObjectType.TENANT, cacheControllerDispatcher.getCacheController(CacheType.TENANT_RECORD_ID));
        }
    }
}
