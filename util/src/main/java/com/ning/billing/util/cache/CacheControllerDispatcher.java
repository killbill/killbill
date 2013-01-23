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

package com.ning.billing.util.cache;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;

import com.ning.billing.util.cache.Cachable.CacheType;

import com.google.inject.name.Named;

public class CacheControllerDispatcher {

    private final Map<CacheType, CacheController<?,?>> caches;

    @Inject
    public CacheControllerDispatcher(@Named(Cachable.RECORD_ID_CACHE_NAME) final CacheController<UUID, Long> recordIdCacheController,
                                     @Named(Cachable.ACCOUNT_RECORD_ID_CACHE_NAME) final CacheController<UUID, Long> accountRecordIdCacheController,
                                     @Named(Cachable.TENANT_RECORD_ID_CACHE_NAME) final CacheController<UUID, Long> tenantRecordIdCacheController) {
        caches = new HashMap<CacheType, CacheController<?, ?>>();
        caches.put(recordIdCacheController.getType(), recordIdCacheController);
        caches.put(accountRecordIdCacheController.getType(), accountRecordIdCacheController);
        caches.put(tenantRecordIdCacheController.getType(), tenantRecordIdCacheController);
    }

    // Test only
    public CacheControllerDispatcher() {
        caches = new HashMap<CacheType, CacheController<?, ?>>();
    }

    public <K,V> CacheController<K, V> getCacheController(CacheType cacheType) {
        // STEPH Not the prettiest thing..
        return  CacheController.class.cast(caches.get(cacheType));
    }
}
