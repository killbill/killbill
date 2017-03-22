/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.util.dao;

import java.util.UUID;

import javax.annotation.Nullable;

import org.killbill.billing.ObjectType;
import org.killbill.billing.util.cache.CacheController;
import org.skife.jdbi.v2.Handle;

// This should only be used for internal operations (trusted code, not API), because the context will not be validated!
public interface NonEntityDao {

    public Long retrieveRecordIdFromObject(final UUID objectId, final ObjectType objectType, @Nullable final CacheController<String, Long> cache);

    public Long retrieveRecordIdFromObjectInTransaction(final UUID objectId, final ObjectType objectType, @Nullable final CacheController<String, Long> cache, @Nullable final Handle handle);

    public Long retrieveAccountRecordIdFromObject(final UUID objectId, final ObjectType objectType, @Nullable final CacheController<String, Long> cache);

    public Long retrieveAccountRecordIdFromObjectInTransaction(final UUID objectId, final ObjectType objectType, @Nullable final CacheController<String, Long> cache, @Nullable final Handle handle);

    public Long retrieveTenantRecordIdFromObject(final UUID objectId, final ObjectType objectType, @Nullable final CacheController<String, Long> cache);

    public Long retrieveTenantRecordIdFromObjectInTransaction(final UUID objectId, final ObjectType objectType, @Nullable final CacheController<String, Long> cache, @Nullable final Handle handle);

    public UUID retrieveIdFromObject(final Long recordId, final ObjectType objectType, @Nullable final CacheController<String, UUID> cache);

    public UUID retrieveIdFromObjectInTransaction(final Long recordId, final ObjectType objectType, @Nullable final CacheController<String, UUID> cache, @Nullable final Handle handle);

    // This retrieves from the history table the latest record for which targetId matches the one we are passing
    public Long retrieveLastHistoryRecordIdFromTransaction(final Long targetRecordId, final TableName tableName, final NonEntitySqlDao transactional);

    // This is the reverse from retrieveLastHistoryRecordIdFromTransaction; this retrieves the record_id of the object matching a given history row
    public Long retrieveHistoryTargetRecordId(final Long recordId, final TableName tableName);
}
