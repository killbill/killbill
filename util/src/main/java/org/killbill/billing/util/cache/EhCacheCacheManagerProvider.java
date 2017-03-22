/*
 * Copyright 2010-2012 Ning, Inc.
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

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Provider;

import org.killbill.billing.util.config.definition.EhCacheConfig;
import org.killbill.xmlloader.UriAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ehcache.InstrumentedEhcache;
import net.sf.ehcache.CacheException;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;

// EhCache specific provider
public class EhCacheCacheManagerProvider implements Provider<CacheManager> {

    private static final Logger logger = LoggerFactory.getLogger(EhCacheCacheManagerProvider.class);

    private final MetricRegistry metricRegistry;
    private final EhCacheConfig cacheConfig;
    private final Set<BaseCacheLoader> cacheLoaders;

    @Inject
    public EhCacheCacheManagerProvider(final MetricRegistry metricRegistry,
                                       final EhCacheConfig cacheConfig,
                                       final Set<BaseCacheLoader> cacheLoaders) {
        this.metricRegistry = metricRegistry;
        this.cacheConfig = cacheConfig;
        this.cacheLoaders = cacheLoaders;
    }

    @Override
    public CacheManager get() {
        final CacheManager cacheManager;
        try {
            final InputStream inputStream = UriAccessor.accessUri(cacheConfig.getCacheConfigLocation());
            cacheManager = CacheManager.create(inputStream);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        } catch (final URISyntaxException e) {
            throw new RuntimeException(e);
        }

        for (final BaseCacheLoader cacheLoader : cacheLoaders) {
            final Ehcache cache = cacheManager.getEhcache(cacheLoader.getCacheType().getCacheName());

            if (cache == null) {
                logger.warn("Cache for cacheName='{}' not configured - check your ehcache.xml", cacheLoader.getCacheType().getCacheName());
                continue;
            }

            // Instrument the cache
            final Ehcache decoratedCache = InstrumentedEhcache.instrument(metricRegistry, cache);
            try {
                cacheManager.replaceCacheWithDecoratedCache(cache, decoratedCache);
            } catch (final CacheException e) {
                logger.warn("Unable to instrument cache {}: {}", cache.getName(), e.getMessage());
            }
        }

        return cacheManager;
    }
}
