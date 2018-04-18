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

package org.killbill.billing.dao;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.cache.CacheController;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.dao.NonEntitySqlDao;
import org.killbill.billing.util.dao.TableName;
import org.skife.jdbi.v2.Handle;

import static org.killbill.billing.ObjectType.ACCOUNT;

public class MockNonEntityDao implements NonEntityDao {

    private final Map<UUID, Long> tenantRecordIdMappings = new HashMap<UUID, Long>();
    private final Map<UUID, Long> accountRecordIdMappings = new HashMap<UUID, Long>();
    private final Map<Long, UUID> accountIdMappings = new HashMap<Long, UUID>();

    public void addTenantRecordIdMapping(final UUID objectId, final InternalTenantContext context) {
        tenantRecordIdMappings.put(objectId, context.getTenantRecordId());
    }

    public void addAccountRecordIdMapping(final UUID objectId, final InternalTenantContext context) {
        accountRecordIdMappings.put(objectId, context.getAccountRecordId());
    }

    public void addAccountIdMapping(final Long objectRecordId, final UUID objectId) {
        accountIdMappings.put(objectRecordId, objectId);
    }

    @Override
    public Long retrieveRecordIdFromObject(final UUID objectId, final ObjectType objectType, @Nullable final CacheController<String, Long> cache) {
        return null;
    }

    @Override
    public Long retrieveRecordIdFromObjectInTransaction(final UUID objectId, final ObjectType objectType, @Nullable final CacheController<String, Long> cache, @Nullable final Handle handle) {
        return null;
    }

    @Override
    public Long retrieveAccountRecordIdFromObject(final UUID objectId, final ObjectType objectType, @Nullable final CacheController<String, Long> cache) {
        return accountRecordIdMappings.get(objectId);
    }

    @Override
    public Long retrieveAccountRecordIdFromObjectInTransaction(final UUID objectId, final ObjectType objectType, @Nullable final CacheController<String, Long> cache, @Nullable final Handle handle) {
        return null;
    }

    @Override
    public Long retrieveTenantRecordIdFromObject(final UUID objectId, final ObjectType objectType, @Nullable final CacheController<String, Long> cache) {
        return tenantRecordIdMappings.get(objectId);
    }

    @Override
    public Long retrieveTenantRecordIdFromObjectInTransaction(final UUID objectId, final ObjectType objectType, @Nullable final CacheController<String, Long> cache, @Nullable final Handle handle) {
        return null;
    }

    @Override
    public UUID retrieveIdFromObject(final Long recordId, final ObjectType objectType, @Nullable final CacheController<String, UUID> cache) {
        if (objectType == ACCOUNT) {
            return accountIdMappings.get(recordId);
        } else {
            return null;
        }
    }

    @Override
    public UUID retrieveIdFromObjectInTransaction(final Long recordId, final ObjectType objectType, @Nullable final CacheController<String, UUID> cache, @Nullable final Handle handle) {
        return null;
    }

    @Override
    public Long retrieveLastHistoryRecordIdFromTransaction(final Long targetRecordId, final TableName tableName, final NonEntitySqlDao transactional) {
        return null;
    }

    @Override
    public Long retrieveHistoryTargetRecordId(final Long recordId, final TableName tableName) {
        return null;
    }
}
