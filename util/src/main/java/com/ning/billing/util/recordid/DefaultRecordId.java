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

package com.ning.billing.util.recordid;

import java.util.UUID;

import javax.inject.Inject;

import com.ning.billing.ObjectType;
import com.ning.billing.util.api.RecordIdApi;
import com.ning.billing.util.cache.Cachable.CacheType;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.dao.NonEntityDao;

public class DefaultRecordId implements RecordIdApi  {

    private final NonEntityDao nonEntityDao;
    private final CacheControllerDispatcher cacheControllerDispatcher;

    @Inject
    public DefaultRecordId(final NonEntityDao nonEntityDao, final CacheControllerDispatcher cacheControllerDispatcher) {
        this.nonEntityDao = nonEntityDao;
        this.cacheControllerDispatcher = cacheControllerDispatcher;
    }


    @Override
    public Long getRecordId(final UUID objectId, final ObjectType objectType) {
        return nonEntityDao.retrieveRecordIdFromObject(objectId, objectType, cacheControllerDispatcher.getCacheController(CacheType.RECORD_ID));
    }
}
