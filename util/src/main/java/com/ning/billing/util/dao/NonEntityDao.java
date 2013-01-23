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

import com.ning.billing.ObjectType;
import com.ning.billing.util.cache.CacheController;
import com.ning.billing.util.entity.dao.EntitySqlDaoWrapperFactory;

public interface NonEntityDao {

    //
    // TODO should we check for InternalCallContext?
    // That seems difficult because those APIs are called when creating a context or from the cache loaders which also dpn't know anything about context
    //
    public Long retrieveRecordIdFromObject(final UUID objectId, final ObjectType objectType, @Nullable final CacheController<Object, Object> cache);

    public Long retrieveAccountRecordIdFromObject(final UUID objectId, final ObjectType objectType, @Nullable final CacheController<Object, Object> cache);

    public Long retrieveTenantRecordIdFromObject(final UUID objectId, final ObjectType objectType, @Nullable final CacheController<Object, Object> cache);

    // This retrieves from the history table the latest record for which targetId matches the one we are passing
    public Long retrieveLastHistoryRecordIdFromTransaction(final Long targetRecordId, final TableName tableName, final NonEntitySqlDao transactional);

    //This is the reverse from retrieveLastHistoryRecordIdFromTransaction; this retrieves the record_id of the object matching a given history row
    public Long retrieveHistoryTargetRecordId(final Long recordId, final TableName tableName);
}
