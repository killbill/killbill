/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Provider;

import org.killbill.billing.util.cache.Cachable.CacheType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.loader.CacheLoader;

// Build the abstraction layer between EhCache and Kill Bill
public class CacheControllerDispatcherProvider implements Provider<CacheControllerDispatcher> {

    private static final Logger logger = LoggerFactory.getLogger(CacheControllerDispatcherProvider.class);

    private final CacheManager cacheManager;

    @Inject
    public CacheControllerDispatcherProvider(final CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public CacheControllerDispatcher get() {
        final Map<CacheType, CacheController<Object, Object>> cacheControllers = new LinkedHashMap<CacheType, CacheController<Object, Object>>();
        for (final String cacheName : cacheManager.getCacheNames()) {
            final CacheType cacheType = CacheType.findByName(cacheName);

            final Collection<EhCacheBasedCacheController<Object, Object>> cacheControllersForCacheName = getCacheControllersForCacheName(cacheName, cacheType);
            // EhCache supports multiple cache loaders per type, but not Kill Bill - take the first one
            if (cacheControllersForCacheName.size() > 0) {
                final EhCacheBasedCacheController<Object, Object> ehCacheBasedCacheController = cacheControllersForCacheName.iterator().next();
                cacheControllers.put(cacheType, ehCacheBasedCacheController);
            }
        }
        return new CacheControllerDispatcher(cacheControllers);
    }

    private Collection<EhCacheBasedCacheController<Object, Object>> getCacheControllersForCacheName(final String name, final CacheType cacheType) {
        final Ehcache cache = cacheManager.getEhcache(name);
        if (cache == null) {
            logger.warn("No cache configured for name {}", name);
            return ImmutableList.<EhCacheBasedCacheController<Object, Object>>of();
        }
        // The CacheLoaders were registered in EhCacheCacheManagerProvider
        return Collections2.transform(cache.getRegisteredCacheLoaders(), new Function<CacheLoader, EhCacheBasedCacheController<Object, Object>>() {
            @Override
            public EhCacheBasedCacheController<Object, Object> apply(final CacheLoader input) {
                return new EhCacheBasedCacheController<Object, Object>(cache, cacheType);
            }
        });
    }
}
