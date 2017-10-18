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

package org.killbill.billing.util.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.inject.Inject;
import javax.inject.Provider;

import org.killbill.billing.util.cache.Cachable.CacheType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

// Build the abstraction layer between EhCache and Kill Bill
public class CacheControllerDispatcherProvider implements Provider<CacheControllerDispatcher> {

    private static final Logger logger = LoggerFactory.getLogger(CacheControllerDispatcherProvider.class);

    private final CacheManager cacheManager;
    private final Set<BaseCacheLoader> cacheLoaders;

    @Inject
    public CacheControllerDispatcherProvider(final CacheManager cacheManager,
                                             final Set<BaseCacheLoader> cacheLoaders) {
        this.cacheManager = cacheManager;
        this.cacheLoaders = cacheLoaders;
    }

    @Override
    public CacheControllerDispatcher get() {
        final Map<CacheType, CacheController<Object, Object>> cacheControllers = new LinkedHashMap<CacheType, CacheController<Object, Object>>();
        for (final BaseCacheLoader cacheLoader : cacheLoaders) {
            final CacheType cacheType = cacheLoader.getCacheType();

            final Cache cache = cacheManager.getCache(cacheType.getCacheName(), cacheType.getKeyType(), cacheType.getValueType());
            if (cache == null) {
                logger.warn("Cache for cacheName='{}' not configured - check your ehcache.xml", cacheLoader.getCacheType().getCacheName());
                continue;
            }
            Preconditions.checkState(!cache.isClosed(), "Cache '%s' should not be closed", cacheType.getCacheName());

            final CacheController<Object, Object> ehCacheBasedCacheController = new EhCacheBasedCacheController<Object, Object>(cache, cacheLoader);
            cacheControllers.put(cacheType, ehCacheBasedCacheController);
        }

        return new CacheControllerDispatcher(cacheControllers);
    }
}
