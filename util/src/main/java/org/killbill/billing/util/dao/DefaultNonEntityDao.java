/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;

import org.killbill.billing.ObjectType;
import org.killbill.billing.util.cache.CacheController;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.cache.CacheLoaderArgument;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.entity.dao.DBRouter;
import org.killbill.commons.profiling.Profiling;
import org.killbill.commons.profiling.Profiling.WithProfilingCallback;
import org.killbill.commons.profiling.ProfilingFeature.ProfilingFeatureType;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.sqlobject.SqlObjectBuilder;

import com.google.common.base.Preconditions;

import static org.killbill.billing.util.glue.IDBISetup.MAIN_RO_IDBI_NAMED;

public class DefaultNonEntityDao implements NonEntityDao {

    private final DBRouter<NonEntitySqlDao> dbRouter;
    private final WithCaching<String, Long> withCachingObjectId;
    private final WithCaching<String, UUID> withCachingRecordId;

    @Inject
    public DefaultNonEntityDao(final IDBI dbi, @Named(MAIN_RO_IDBI_NAMED) final IDBI roDbi) {
        this.dbRouter = new DBRouter<NonEntitySqlDao>(dbi, roDbi, NonEntitySqlDao.class);
        this.withCachingObjectId = new WithCaching<String, Long>();
        this.withCachingRecordId = new WithCaching<String, UUID>();
    }

    @Override
    public Long retrieveRecordIdFromObject(@Nullable final UUID objectId, final ObjectType objectType, @Nullable final CacheController<String, Long> cache) {
        return retrieveRecordIdFromObjectInTransaction(objectId, objectType, cache, null);
    }

    public Long retrieveRecordIdFromObjectInTransaction(@Nullable final UUID objectId, final ObjectType objectType, @Nullable final CacheController<String, Long> cache, @Nullable final Handle handle) {
        if (objectId == null) {
            return null;
        }

        final TableName tableName = TableName.fromObjectType(objectType);
        Preconditions.checkNotNull(tableName, "%s is not a valid ObjectType", objectType);

        return withCachingObjectId.withCaching(new OperationRetrieval<Long>() {
            @Override
            public Long doRetrieve(final ObjectType objectType) {
                final NonEntitySqlDao inTransactionNonEntitySqlDao = handle == null ? dbRouter.onDemand(true) : SqlObjectBuilder.attach(handle, NonEntitySqlDao.class);
                return inTransactionNonEntitySqlDao.getRecordIdFromObject(objectId.toString(), tableName.getTableName());
            }
        }, objectId.toString(), objectType, tableName, cache);
    }

    @Override
    public Long retrieveAccountRecordIdFromObject(@Nullable final UUID objectId, final ObjectType objectType, @Nullable final CacheController<String, Long> cache) {
        return retrieveAccountRecordIdFromObjectInTransaction(objectId, objectType, cache, null);
    }

    @Override
    public Long retrieveAccountRecordIdFromObjectInTransaction(@Nullable final UUID objectId, final ObjectType objectType, @Nullable final CacheController<String, Long> cache, @Nullable final Handle handle) {
        final TableName tableName = TableName.fromObjectType(objectType);
        Preconditions.checkNotNull(tableName, "%s is not a valid ObjectType", objectType);

        final String objectIdOrNull = objectId != null ? objectId.toString() : null;
        return withCachingObjectId.withCaching(new OperationRetrieval<Long>() {
            @Override
            public Long doRetrieve(final ObjectType objectType) {
                final NonEntitySqlDao inTransactionNonEntitySqlDao = handle == null ? dbRouter.onDemand(true) : SqlObjectBuilder.attach(handle, NonEntitySqlDao.class);

                switch (tableName) {
                    case TENANT:
                    case TAG_DEFINITIONS:
                    case TAG_DEFINITION_HISTORY:
                        return null;

                    case ACCOUNT:
                        return inTransactionNonEntitySqlDao.getAccountRecordIdFromAccount(objectIdOrNull);

                    default:
                        return inTransactionNonEntitySqlDao.getAccountRecordIdFromObjectOtherThanAccount(objectIdOrNull, tableName.getTableName());
                }
            }
        }, objectIdOrNull, objectType, tableName, cache);
    }

    @Override
    public Long retrieveTenantRecordIdFromObject(@Nullable final UUID objectId, final ObjectType objectType, @Nullable final CacheController<String, Long> cache) {
        return retrieveTenantRecordIdFromObjectInTransaction(objectId, objectType, cache, null);
    }

    @Override
    public Long retrieveTenantRecordIdFromObjectInTransaction(@Nullable final UUID objectId, final ObjectType objectType, @Nullable final CacheController<String, Long> cache, @Nullable final Handle handle) {
        final TableName tableName = TableName.fromObjectType(objectType);
        Preconditions.checkNotNull(tableName, "%s is not a valid ObjectType", objectType);

        final String objectIdOrNull = objectId != null ? objectId.toString() : null;
        return withCachingObjectId.withCaching(new OperationRetrieval<Long>() {
            @Override
            public Long doRetrieve(final ObjectType objectType) {
                final NonEntitySqlDao inTransactionNonEntitySqlDao = handle == null ? dbRouter.onDemand(true) : SqlObjectBuilder.attach(handle, NonEntitySqlDao.class);

                switch (tableName) {
                    case TENANT:
                        // Explicit cast to Long to avoid NPE (unboxing to long)
                        return objectId == null ? (Long) 0L : inTransactionNonEntitySqlDao.getTenantRecordIdFromTenant(objectIdOrNull);

                    default:
                        return inTransactionNonEntitySqlDao.getTenantRecordIdFromObjectOtherThanTenant(objectIdOrNull, tableName.getTableName());
                }

            }
        }, objectIdOrNull, objectType, tableName, cache);
    }

    @Override
    public UUID retrieveIdFromObject(final Long recordId, final ObjectType objectType, @Nullable final CacheController<String, UUID> cache) {
        return retrieveIdFromObjectInTransaction(recordId, objectType, cache, null);
    }

    @Override
    public UUID retrieveIdFromObjectInTransaction(final Long recordId, final ObjectType objectType, @Nullable final CacheController<String, UUID> cache, @Nullable final Handle handle) {
        if (objectType == ObjectType.TENANT && InternalCallContextFactory.INTERNAL_TENANT_RECORD_ID.equals(recordId)) {
            return null;
        }

        final TableName tableName = TableName.fromObjectType(objectType);
        Preconditions.checkNotNull(tableName, "%s is not a valid ObjectType", objectType);

        return withCachingRecordId.withCaching(new OperationRetrieval<UUID>() {
            @Override
            public UUID doRetrieve(final ObjectType objectType) {
                final NonEntitySqlDao inTransactionNonEntitySqlDao = handle == null ? dbRouter.onDemand(true) : SqlObjectBuilder.attach(handle, NonEntitySqlDao.class);
                return inTransactionNonEntitySqlDao.getIdFromObject(recordId, tableName.getTableName());
            }
        }, String.valueOf(recordId), objectType, tableName, cache);
    }

    @Override
    public Long retrieveLastHistoryRecordIdFromTransaction(@Nullable final Long targetRecordId, final TableName tableName, final NonEntitySqlDao transactional) {
        // There is no caching here because the value returned changes as we add more history records, and so we would need some cache invalidation
        return transactional.getLastHistoryRecordId(targetRecordId, tableName.getTableName());
    }

    @Override
    public Long retrieveHistoryTargetRecordId(@Nullable final Long recordId, final TableName tableName) {
        return dbRouter.onDemand(true).getHistoryTargetRecordId(recordId, tableName.getTableName());
    }

    private interface OperationRetrieval<TypeOut> {

        public TypeOut doRetrieve(final ObjectType objectType);
    }

    // 'cache' will be null for the CacheLoader classes -- or if cache is not configured.
    private class WithCaching<TypeIn, TypeOut> {

        private TypeOut withCaching(final OperationRetrieval<TypeOut> op, @Nullable final TypeIn objectOrRecordId, final ObjectType objectType, final TableName tableName, @Nullable final CacheController<TypeIn, TypeOut> cache) {

            final Profiling<TypeOut, RuntimeException> prof = new Profiling<TypeOut, RuntimeException>();
            if (objectOrRecordId == null) {
                return null;
            }
            if (cache != null) {
                final TypeIn key = (cache.getCacheType().isKeyPrefixedWithTableName()) ?
                                   (TypeIn) (tableName + CacheControllerDispatcher.CACHE_KEY_SEPARATOR + objectOrRecordId.toString()) :
                                   objectOrRecordId;
                return cache.get(key, new CacheLoaderArgument(objectType));
            }
            final TypeOut result;
            result = prof.executeWithProfiling(ProfilingFeatureType.DAO_DETAILS, "NonEntityDao (type = " + objectType + ") cache miss", new WithProfilingCallback<TypeOut, RuntimeException>() {
                @Override
                public TypeOut execute() throws RuntimeException {
                    return op.doRetrieve(objectType);
                }
            });
            return result;
        }
    }
}
