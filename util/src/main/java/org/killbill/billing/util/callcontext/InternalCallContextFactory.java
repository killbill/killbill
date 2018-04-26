/*
 * Copyright 2010-2012 Ning, Inc.
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

package org.killbill.billing.util.callcontext;

import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.account.api.ImmutableAccountInternalApi;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.account.AccountDateTimeUtils;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.killbill.billing.util.cache.CacheController;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.entity.dao.TimeZoneAwareEntity;
import org.killbill.clock.Clock;
import org.slf4j.MDC;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

// Internal contexts almost always expect accountRecordId and tenantRecordId to be populated
public class InternalCallContextFactory {

    // Long, not long, to avoid NPE with ==
    public static final Long INTERNAL_TENANT_RECORD_ID = 0L;

    public static final String MDC_KB_ACCOUNT_RECORD_ID = "kb.accountRecordId";
    public static final String MDC_KB_TENANT_RECORD_ID = "kb.tenantRecordId";
    public static final String MDC_KB_USER_TOKEN = "kb.userToken";

    private final ImmutableAccountInternalApi accountInternalApi;
    private final Clock clock;
    private final NonEntityDao nonEntityDao;
    private final CacheController<String, UUID> objectIdCacheController;
    private final CacheController<String, Long> recordIdCacheController;
    private final CacheController<String, Long> accountRecordIdCacheController;
    private final CacheController<String, Long> tenantRecordIdCacheController;

    @Inject
    public InternalCallContextFactory(@Nullable final ImmutableAccountInternalApi accountInternalApi,
                                      final Clock clock,
                                      final NonEntityDao nonEntityDao,
                                      @Nullable final CacheControllerDispatcher cacheControllerDispatcher) {
        this.accountInternalApi = accountInternalApi;
        this.clock = clock;
        this.nonEntityDao = nonEntityDao;
        if (cacheControllerDispatcher == null) {
            this.objectIdCacheController = null;
            this.recordIdCacheController = null;
            this.accountRecordIdCacheController = null;
            this.tenantRecordIdCacheController = null;
        } else {
            this.objectIdCacheController = cacheControllerDispatcher.getCacheController(CacheType.OBJECT_ID);
            this.recordIdCacheController = cacheControllerDispatcher.getCacheController(CacheType.RECORD_ID);
            this.accountRecordIdCacheController = cacheControllerDispatcher.getCacheController(CacheType.ACCOUNT_RECORD_ID);
            this.tenantRecordIdCacheController = cacheControllerDispatcher.getCacheController(CacheType.TENANT_RECORD_ID);
        }
    }

    //
    // Create contexts from internal contexts
    //

    public TenantContext createTenantContext(final InternalTenantContext context) {
        final UUID accountId = getAccountIdSafe(context);
        final UUID tenantId = getTenantIdSafe(context);
        return context.toTenantContext(accountId, tenantId);
    }

    public CallContext createCallContext(final InternalCallContext context) {
        final UUID accountId = getAccountIdSafe(context);
        final UUID tenantId = getTenantIdSafe(context);
        return context.toCallContext(accountId, tenantId);
    }

    //
    // Create InternalTenantContext
    //

    /**
     * Create an internal tenant callcontext from a tenant callcontext
     * <p/>
     * This is used for r/o operations - we don't need the account id in that case.
     * You should almost never use that one, you always want to populate the accountRecordId
     *
     * @param context tenant callcontext (tenantId can be null only if multi-tenancy is disabled)
     * @return internal tenant callcontext
     */
    public InternalTenantContext createInternalTenantContextWithoutAccountRecordId(final TenantContext context) {
        // If tenant id is null, this will default to the default tenant record id (multi-tenancy disabled)
        final Long tenantRecordId = getTenantRecordIdSafe(context);
        return createInternalTenantContext(tenantRecordId, null);
    }

    public InternalTenantContext createInternalTenantContext(final UUID accountId, final TenantContext context) {
        return createInternalTenantContext(accountId, ObjectType.ACCOUNT, context);
    }

    public InternalTenantContext createInternalTenantContext(final UUID accountId, final InternalTenantContext context) {
        final Long tenantRecordId = context.getTenantRecordId();
        final Long accountRecordId = getAccountRecordIdSafe(accountId, ObjectType.ACCOUNT, context.getTenantRecordId());
        return createInternalTenantContext(tenantRecordId, accountRecordId);
    }

    /**
     * Crate an internal tenant callcontext from a tenant callcontext, and retrieving the account_record_id from another table
     *
     * @param objectId   the id of the row in the table pointed by object type where to look for account_record_id
     * @param objectType the object type pointed by this objectId
     * @param context    original tenant callcontext
     * @return internal tenant callcontext from callcontext, with a non null account_record_id (if found)
     */
    public InternalTenantContext createInternalTenantContext(final UUID objectId, final ObjectType objectType, final TenantContext context) {
        // The callcontext may come from a user API - for security, check we're not doing cross-tenants operations
        //final Long tenantRecordIdFromObject = retrieveTenantRecordIdFromObject(objectId, objectType);
        //final Long tenantRecordIdFromContext = getTenantRecordIdSafe(callcontext);
        //Preconditions.checkState(tenantRecordIdFromContext.equals(tenantRecordIdFromObject),
        //                         "tenant of the pointed object (%s) and the callcontext (%s) don't match!", tenantRecordIdFromObject, tenantRecordIdFromContext);
        final Long tenantRecordId = getTenantRecordIdSafe(context);
        final Long accountRecordId = getAccountRecordIdSafe(objectId, objectType, context);
        return createInternalTenantContext(tenantRecordId, accountRecordId);
    }

    public InternalTenantContext recreateInternalTenantContextWithAccountRecordId(final UUID objectId, final ObjectType objectType, final InternalTenantContext inputContext) {
        final Long tenantRecordId = inputContext.getTenantRecordId();
        final Long accountRecordId = getAccountRecordIdSafe(objectId, objectType, tenantRecordId);
        return createInternalTenantContext(tenantRecordId, accountRecordId);
    }

        /**
         * Create an internal tenant callcontext
         *
         * @param tenantRecordId  tenant_record_id (cannot be null)
         * @param accountRecordId account_record_id (cannot be null for INSERT operations)
         * @return internal tenant callcontext
         */
    public InternalTenantContext createInternalTenantContext(final Long tenantRecordId, @Nullable final Long accountRecordId) {
        populateMDCContext(null, accountRecordId, tenantRecordId);

        if (accountRecordId == null) {
            return new InternalTenantContext(tenantRecordId);
        } else {
            final ImmutableAccountData immutableAccountData = getImmutableAccountData(accountRecordId, tenantRecordId);
            final DateTimeZone fixedOffsetTimeZone = immutableAccountData.getFixedOffsetTimeZone();
            final DateTime referenceTime = immutableAccountData.getReferenceTime();
            return new InternalTenantContext(tenantRecordId, accountRecordId, fixedOffsetTimeZone, referenceTime);
        }
    }

    //
    // Create InternalCallContext
    //

    /**
     * Create an internal call callcontext using an existing account to retrieve tenant and account record ids
     * <p/>
     * This is used for r/w operations - we need the account id to populate the account_record_id field
     *
     * @param accountId account id
     * @param context   original call callcontext
     * @return internal call callcontext
     */
    public InternalCallContext createInternalCallContext(final UUID accountId, final CallContext context) {
        return createInternalCallContext(accountId, ObjectType.ACCOUNT, context);
    }

    /**
     * Create an internal call callcontext from a call callcontext, and retrieving the account_record_id from another table
     *
     * @param objectId   the id of the row in the table pointed by object type where to look for account_record_id
     * @param objectType the object type pointed by this objectId
     * @param context    original call callcontext
     * @return internal call callcontext from callcontext, with a non null account_record_id (if found)
     */
    public InternalCallContext createInternalCallContext(final UUID objectId, final ObjectType objectType, final CallContext context) {
        // The callcontext may come from a user API - for security, check we're not doing cross-tenants operations
        //final Long tenantRecordIdFromObject = retrieveTenantRecordIdFromObject(objectId, objectType);
        //final Long tenantRecordIdFromContext = getTenantRecordIdSafe(callcontext);
        //Preconditions.checkState(tenantRecordIdFromContext.equals(tenantRecordIdFromObject),
        //                         "tenant of the pointed object (%s) and the callcontext (%s) don't match!", tenantRecordIdFromObject, tenantRecordIdFromContext);

        return createInternalCallContext(objectId, objectType, context.getUserName(), context.getCallOrigin(),
                                         context.getUserType(), context.getUserToken(), context.getReasonCode(), context.getComments(),
                                         context);
    }

    // Used by the payment retry service
    public InternalCallContext createInternalCallContext(final UUID objectId, final ObjectType objectType, final String userName,
                                                         final CallOrigin callOrigin, final UserType userType, @Nullable final UUID userToken, final Long tenantRecordId) {
        final Long accountRecordId = getAccountRecordIdSafe(objectId, objectType, tenantRecordId);
        return createInternalCallContext(tenantRecordId, accountRecordId, userName, callOrigin, userType, userToken, null, null);
    }

    /**
     * Create an internal call callcontext
     * <p/>
     * This is used by notification queue and persistent bus - accountRecordId is expected to be non null
     *
     * @param tenantRecordId  tenant record id - if null, the default tenant record id value will be used
     * @param accountRecordId account record id (can be null in specific use-cases, e.g. config change events in BeatrixListener)
     * @param userName        user name
     * @param callOrigin      call origin
     * @param userType        user type
     * @param userToken       user token, if any
     * @return internal call callcontext
     */
    public InternalCallContext createInternalCallContext(@Nullable final Long tenantRecordId, @Nullable final Long accountRecordId, final String userName,
                                                         final CallOrigin callOrigin, final UserType userType, @Nullable final UUID userToken) {
        return createInternalCallContext(tenantRecordId, accountRecordId, userName, callOrigin, userType, userToken, null, null);
    }

    /**
     * Create an internal call callcontext without populating the account record id
     * <p/>
     * This is used for update/delete operations - we don't need the account id in that case - and
     * also when we don't have an account_record_id column (e.g. tenants, tag_definitions)
     *
     * @param context original call callcontext
     * @return internal call callcontext
     */
    public InternalCallContext createInternalCallContextWithoutAccountRecordId(final CallContext context) {
        // If tenant id is null, this will default to the default tenant record id (multi-tenancy disabled)
        final Long tenantRecordId = getTenantRecordIdSafe(context);
        populateMDCContext(context.getUserToken(), null, tenantRecordId);
        return new InternalCallContext(tenantRecordId, context, context.getCreatedDate());
    }

    // Used when we need to re-hydrate the callcontext with the account_record_id (when creating the account)
    public InternalCallContext createInternalCallContext(final Long accountRecordId, final InternalCallContext context) {
        final ImmutableAccountData immutableAccountData = getImmutableAccountData(accountRecordId, context.getTenantRecordId());
        final DateTimeZone fixedOffsetTimeZone = immutableAccountData.getFixedOffsetTimeZone();
        final DateTime referenceTime = immutableAccountData.getReferenceTime();
        populateMDCContext(context.getUserToken(), accountRecordId, context.getTenantRecordId());
        return new InternalCallContext(context, accountRecordId, fixedOffsetTimeZone, referenceTime, context.getCreatedDate());
    }

    // Used during the account creation transaction (account not visible outside of the transaction yet)
    public InternalCallContext createInternalCallContext(final TimeZoneAwareEntity accountModelDao, final Long accountRecordId, final InternalCallContext context) {
        // See DefaultImmutableAccountData implementation
        final DateTimeZone fixedOffsetTimeZone = AccountDateTimeUtils.getFixedOffsetTimeZone(accountModelDao);
        final DateTime referenceTime = accountModelDao.getReferenceTime();
        populateMDCContext(context.getUserToken(), accountRecordId, context.getTenantRecordId());
        return new InternalCallContext(context, accountRecordId, fixedOffsetTimeZone, referenceTime, context.getCreatedDate());
    }

    public InternalCallContext createInternalCallContext(final DateTimeZone fixedOffsetTimeZone, final DateTime referenceTime, final Long accountRecordId, final InternalCallContext context) {
        populateMDCContext(context.getUserToken(), accountRecordId, context.getTenantRecordId());
        return new InternalCallContext(context, accountRecordId, fixedOffsetTimeZone, referenceTime, context.getCreatedDate());
    }

    private InternalCallContext createInternalCallContext(final UUID objectId, final ObjectType objectType, final String userName,
                                                          final CallOrigin callOrigin, final UserType userType, @Nullable final UUID userToken,
                                                          @Nullable final String reasonCode, @Nullable final String comment,
                                                          final TenantContext tenantContext) {
        final Long tenantRecordId = getTenantRecordIdSafe(tenantContext);
        final Long accountRecordId = getAccountRecordIdSafe(objectId, objectType, tenantContext);
        return createInternalCallContext(tenantRecordId, accountRecordId, userName, callOrigin, userType, userToken,
                                         reasonCode, comment);
    }

    private InternalCallContext createInternalCallContext(@Nullable final Long tenantRecordId, @Nullable final Long accountRecordId, final String userName,
                                                          final CallOrigin callOrigin, final UserType userType, @Nullable final UUID userToken,
                                                          @Nullable final String reasonCode, @Nullable final String comment) {
        final Long nonNulTenantRecordId = MoreObjects.firstNonNull(tenantRecordId, INTERNAL_TENANT_RECORD_ID);

        final DateTimeZone fixedOffsetTimeZone;
        final DateTime referenceTime;
        if (accountRecordId == null) {
            // TENANT_CONFIG_CHANGE event for instance
            fixedOffsetTimeZone = null;
            referenceTime = null;
        } else {
            final ImmutableAccountData immutableAccountData = getImmutableAccountData(accountRecordId, nonNulTenantRecordId);
            fixedOffsetTimeZone = immutableAccountData.getFixedOffsetTimeZone();
            referenceTime = immutableAccountData.getReferenceTime();
        }

        populateMDCContext(userToken, accountRecordId, nonNulTenantRecordId);

        return new InternalCallContext(nonNulTenantRecordId,
                                       accountRecordId,
                                       fixedOffsetTimeZone,
                                       referenceTime,
                                       userToken,
                                       userName,
                                       callOrigin,
                                       userType,
                                       reasonCode,
                                       comment,
                                       clock.getUTCNow(),
                                       clock.getUTCNow());
    }

    private ImmutableAccountData getImmutableAccountData(final Long accountRecordId, final Long tenantRecordId) {
        Preconditions.checkNotNull(accountRecordId, "Missing accountRecordId");
        final InternalTenantContext tmp = new InternalTenantContext(tenantRecordId, accountRecordId, null, null);
        try {
            final ImmutableAccountData immutableAccountData = accountInternalApi.getImmutableAccountDataByRecordId(accountRecordId, tmp);
            Preconditions.checkNotNull(immutableAccountData, "Unable to retrieve immutableAccountData");
            return immutableAccountData;
        } catch (final AccountApiException e) {
            throw new RuntimeException(e);
        }
    }

    private void populateMDCContext(@Nullable final UUID userToken, @Nullable final Long accountRecordId, final Long tenantRecordId) {
        if (accountRecordId != null) {
            MDC.put(MDC_KB_ACCOUNT_RECORD_ID, String.valueOf(accountRecordId));
        }
        MDC.put(MDC_KB_TENANT_RECORD_ID, String.valueOf(tenantRecordId));
        MDC.put(MDC_KB_USER_TOKEN, userToken != null ? userToken.toString() : null);
    }

    //
    // Safe NonEntityDao public wrappers
    //

    // Safe method to retrieve the account id from any object
    public UUID getAccountId(final UUID objectId, final ObjectType objectType, final TenantContext context) {
        final Long accountRecordId = getAccountRecordIdSafe(objectId, objectType, context);
        if (accountRecordId != null) {
            return nonEntityDao.retrieveIdFromObject(accountRecordId, ObjectType.ACCOUNT, objectIdCacheController);
        } else {
            return null;
        }
    }

    // Safe method to retrieve the record id from any object (should only be used by DefaultRecordIdApi)
    public Long getRecordIdFromObject(final UUID objectId, final ObjectType objectType, final TenantContext context) {
        try {
            if (objectBelongsToTheRightTenant(objectId, objectType, context)) {
                return nonEntityDao.retrieveRecordIdFromObject(objectId, objectType, recordIdCacheController);
            } else {
                return null;
            }
        } catch (final ObjectDoesNotExist e) {
            return null;
        }
    }

    //
    // Safe NonEntityDao private wrappers
    //

    private Long getAccountRecordIdSafe(final UUID objectId, final ObjectType objectType, final TenantContext context) {
        if (objectBelongsToTheRightTenant(objectId, objectType, context)) {
            return getAccountRecordIdUnsafe(objectId, objectType);
        } else {
            throw new IllegalStateException(String.format("Object id=%s type=%s doesn't belong to tenant id=%s", objectId, objectType, context.getTenantId()));
        }
    }

    private Long getAccountRecordIdSafe(final UUID objectId, final ObjectType objectType, final Long tenantRecordId) throws ObjectDoesNotExist {
        if (objectBelongsToTheRightTenant(objectId, objectType, tenantRecordId)) {
            return getAccountRecordIdUnsafe(objectId, objectType);
        } else {
            throw new IllegalStateException(String.format("Object id=%s type=%s doesn't belong to tenant recordId=%s", objectId, objectType, tenantRecordId));
        }
    }

    private Long getTenantRecordIdSafe(final TenantContext context) {
        // Default to single default tenant (e.g. single tenant mode)
        // TODO Extract this convention (e.g. BusinessAnalyticsBase needs to know about it)
        if (context.getTenantId() == null) {
            return INTERNAL_TENANT_RECORD_ID;
        } else {
            // This is always safe (the tenant context was created from the api key and secret)
            return getTenantRecordIdUnsafe(context.getTenantId(), ObjectType.TENANT);
        }
    }

    private UUID getTenantIdSafe(final InternalTenantContext context) {
        return nonEntityDao.retrieveIdFromObject(context.getTenantRecordId(), ObjectType.TENANT, objectIdCacheController);
    }

    private UUID getAccountIdSafe(final InternalTenantContext context) {
        return context.getAccountRecordId() != null ? nonEntityDao.retrieveIdFromObject(context.getAccountRecordId(), ObjectType.ACCOUNT, objectIdCacheController) : null;
    }

    //
    // In-code tenant checkers
    //

    private boolean objectBelongsToTheRightTenant(final UUID objectId, final ObjectType objectType, final TenantContext context) throws ObjectDoesNotExist {
        final Long realTenantRecordId = getTenantRecordIdSafe(context);
        if (realTenantRecordId == null) {
            throw new ObjectDoesNotExist(String.format("Tenant id=%s doesn't exist!", context.getTenantId()));
        }
        return objectBelongsToTheRightTenant(objectId, objectType, realTenantRecordId);
    }

    private boolean objectBelongsToTheRightTenant(final UUID objectId, final ObjectType objectType, final Long realTenantRecordId) throws ObjectDoesNotExist {
        final Long objectTenantRecordId = getTenantRecordIdUnsafe(objectId, objectType);
        if (objectTenantRecordId == null) {
            throw new ObjectDoesNotExist(String.format("Object id=%s type=%s doesn't exist!", objectId, objectType));
        }
        return objectTenantRecordId.equals(realTenantRecordId);
    }

    //
    // Unsafe methods - no context is validated
    //

    private Long getAccountRecordIdUnsafe(final UUID objectId, final ObjectType objectType) {
        return nonEntityDao.retrieveAccountRecordIdFromObject(objectId, objectType, accountRecordIdCacheController);
    }

    private Long getTenantRecordIdUnsafe(final UUID objectId, final ObjectType objectType) {
        return nonEntityDao.retrieveTenantRecordIdFromObject(objectId, objectType, tenantRecordIdCacheController);
    }

    public static final class ObjectDoesNotExist extends IllegalStateException {

        public ObjectDoesNotExist(final String s) {
            super(s);
        }
    }
}
