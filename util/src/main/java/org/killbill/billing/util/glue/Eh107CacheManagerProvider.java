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

package org.killbill.billing.util.glue;

import java.net.URISyntaxException;
import java.util.Set;

import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.spi.CachingProvider;
import javax.inject.Inject;
import javax.inject.Provider;

import org.killbill.billing.util.cache.BaseCacheLoader;
import org.killbill.billing.util.config.definition.EhCacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;

// EhCache specific provider
public class Eh107CacheManagerProvider extends CacheProviderBase implements Provider<CacheManager> {

    private static final Logger logger = LoggerFactory.getLogger(Eh107CacheManagerProvider.class);

    private final Set<BaseCacheLoader> cacheLoaders;

    @Inject
    public Eh107CacheManagerProvider(final MetricRegistry metricRegistry,
                                     final EhCacheConfig cacheConfig,
                                     final Set<BaseCacheLoader> cacheLoaders) {
        super(metricRegistry, cacheConfig);
        this.cacheLoaders = cacheLoaders;
    }

    @Override
    public CacheManager get() {
        // JSR-107 registration, required for JMX integration
        final CachingProvider cachingProvider = Caching.getCachingProvider();

        CacheManager cacheManager;
        try {
            cacheManager = cachingProvider.getCacheManager(xmlConfigurationURL.toURI(), getClass().getClassLoader());
        } catch (final RuntimeException e) {
            logger.error("Unable to read ehcache.xml, using default configuration", e);
            cacheManager = cachingProvider.getCacheManager();
        } catch (final URISyntaxException e) {
            logger.error("Unable to read ehcache.xml, using default configuration", e);
            cacheManager = cachingProvider.getCacheManager();
        }

        for (final BaseCacheLoader<?, ?> cacheLoader : cacheLoaders) {
            createCache(cacheManager,
                        cacheLoader.getCacheType().getCacheName(),
                        cacheLoader.getCacheType().getKeyType(),
                        cacheLoader.getCacheType().getValueType());
        }

        return cacheManager;
    }
}
