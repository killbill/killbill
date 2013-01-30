/*
 * Copyright 2010-2013 Ning, Inc.
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

import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.skife.jdbi.v2.IDBI;

import com.ning.billing.ObjectType;
import com.ning.billing.util.cache.CacheController;

public class DefaultNonEntityDao implements NonEntityDao {

    private final NonEntitySqlDao nonEntitySqlDao;
    private final WithCaching containedCall;


    @Inject
    public DefaultNonEntityDao(final IDBI dbi) {
        this.nonEntitySqlDao = dbi.onDemand(NonEntitySqlDao.class);
        this.containedCall = new WithCaching();
    }


    public Long retrieveRecordIdFromObject(@Nullable final UUID objectId, final ObjectType objectType, @Nullable final CacheController<Object, Object> cache) {

        return containedCall.withCaching(new OperationRetrieval<Long>() {
            @Override
            public Long doRetrieve(final UUID objectId, final ObjectType objectType) {
                final TableName tableName = TableName.fromObjectType(objectType);
                return nonEntitySqlDao.getRecordIdFromObject(objectId.toString(), tableName.getTableName());
            }
        }, objectId, objectType, cache);

    }

    public Long retrieveAccountRecordIdFromObject(@Nullable final UUID objectId, final ObjectType objectType, @Nullable final CacheController<Object, Object> cache) {


        return containedCall.withCaching(new OperationRetrieval<Long>() {
            @Override
            public Long doRetrieve(final UUID objectId, final ObjectType objectType) {
                final TableName tableName = TableName.fromObjectType(objectType);
                switch (tableName) {
                    case TENANT:
                    case TAG_DEFINITIONS:
                    case TAG_DEFINITION_HISTORY:
                        return null;

                    case ACCOUNT:
                        return nonEntitySqlDao.getAccountRecordIdFromAccount(objectId.toString());

                    default:
                        return nonEntitySqlDao.getAccountRecordIdFromObjectOtherThanAccount(objectId.toString(), tableName.getTableName());
                }
            }
        }, objectId, objectType, cache);
    }


    public Long retrieveTenantRecordIdFromObject(@Nullable final UUID objectId, final ObjectType objectType, @Nullable final CacheController<Object, Object> cache) {


        return containedCall.withCaching(new OperationRetrieval<Long>() {
            @Override
            public Long doRetrieve(final UUID objectId, final ObjectType objectType) {
                final TableName tableName = TableName.fromObjectType(objectType);
                switch (tableName) {
                    case TENANT:
                        return nonEntitySqlDao.getTenantRecordIdFromTenant(objectId.toString());

                    default:
                        return nonEntitySqlDao.getTenantRecordIdFromObjectOtherThanTenant(objectId.toString(), tableName.getTableName());
                }

            }
        }, objectId, objectType, cache);
    }

    @Override
    public Long retrieveLastHistoryRecordIdFromTransaction(@Nullable final Long targetRecordId, final TableName tableName, final NonEntitySqlDao transactional) {
        // There is no caching here because the value returned changes as we add more history records, and so we would need some cache invalidation
        return transactional.getLastHistoryRecordId(targetRecordId, tableName.getTableName());
    }

    @Override
    public Long retrieveHistoryTargetRecordId(@Nullable final Long recordId, final TableName tableName) {
        return nonEntitySqlDao.getHistoryTargetRecordId(recordId, tableName.getTableName());
    }


    private interface OperationRetrieval<T> {
        public T doRetrieve(final UUID objectId, final ObjectType objectType);
    }


    // 'cache' will be null for the CacheLoader classes -- or if cache is not configured.
    private class WithCaching {
        private Long withCaching(final OperationRetrieval<Long> op, @Nullable final UUID objectId, final ObjectType objectType, @Nullable final CacheController<Object, Object> cache) {

            if (objectId == null) {
                return null;
            }

            if (cache != null) {
                final Long cachedResult = (Long) cache.get(objectId.toString(), objectType);
                return cachedResult;
            }
            return op.doRetrieve(objectId, objectType);
        }
    }
}
