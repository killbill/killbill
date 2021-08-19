/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
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
import org.killbill.billing.util.config.definition.CacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

// Build the abstraction layer between JCache and Kill Bill
public class CacheControllerDispatcherProvider implements Provider<CacheControllerDispatcher> {

    private static final Logger logger = LoggerFactory.getLogger(CacheControllerDispatcherProvider.class);

    private final CacheManager cacheManager;
    private final Set<BaseCacheLoader> cacheLoaders;
    private final CacheConfig cacheConfig;

    @Inject
    public CacheControllerDispatcherProvider(final CacheManager cacheManager,
                                             final Set<BaseCacheLoader> cacheLoaders,
                                             final CacheConfig cacheConfig) {
        this.cacheManager = cacheManager;
        this.cacheLoaders = cacheLoaders;
        this.cacheConfig = cacheConfig;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public CacheControllerDispatcher get() {
        final Map<CacheType, CacheController<Object, Object>> cacheControllers = new LinkedHashMap<CacheType, CacheController<Object, Object>>();
        for (final BaseCacheLoader cacheLoader : cacheLoaders) {
            final CacheType cacheType = cacheLoader.getCacheType();

            final CacheController<Object, Object> cacheController;
            if (cacheConfig.getDisabledCaches() != null && cacheConfig.getDisabledCaches().contains(cacheType.getCacheName())) {
                logger.info("Disabling cache for cacheName='{}'", cacheLoader.getCacheType().getCacheName());
                cacheController = new NoOpCacheController(cacheLoader);
            } else {
                final Cache cache = cacheManager.getCache(cacheType.getCacheName(), cacheType.getKeyType(), cacheType.getValueType());
                if (cache == null) {
                    logger.warn("Cache for cacheName='{}' not configured", cacheLoader.getCacheType().getCacheName());
                    continue;
                }
                Preconditions.checkState(!cache.isClosed(), "Cache '%s' should not be closed", cacheType.getCacheName());

                cacheController = new KillBillCacheController<Object, Object>(cache, cacheLoader);
            }

            cacheControllers.put(cacheType, cacheController);
        }

        return new CacheControllerDispatcher(cacheControllers);
    }
}
