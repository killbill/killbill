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

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import com.ning.billing.util.cache.Cachable.CacheType;

// Kill Bill generic cache dispatcher
public class CacheControllerDispatcher {

    private final Map<CacheType, CacheController<Object, Object>> caches;

    @Inject
    public CacheControllerDispatcher(final Map<CacheType, CacheController<Object, Object>> caches) {
        this.caches = caches;
    }

    // Test only
    public CacheControllerDispatcher() {
        caches = new HashMap<CacheType, CacheController<Object, Object>>();
    }

    public CacheController<Object, Object> getCacheController(final CacheType cacheType) {
        return caches.get(cacheType);
    }

    public void clearAll() {
        for (final CacheController<Object, Object> cacheController : caches.values()) {
            cacheController.removeAll();
        }
    }
}
