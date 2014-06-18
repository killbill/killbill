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

package org.killbill.billing.util.cache;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.LinkedList;

import javax.inject.Inject;
import javax.inject.Provider;

import org.killbill.billing.util.config.CacheConfig;
import org.killbill.xmlloader.UriAccessor;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.loader.CacheLoader;

// EhCache specific provider
public class EhCacheCacheManagerProvider implements Provider<CacheManager> {

    private final CacheConfig cacheConfig;
    private final Collection<BaseCacheLoader> cacheLoaders = new LinkedList<BaseCacheLoader>();

    @Inject
    public EhCacheCacheManagerProvider(final CacheConfig cacheConfig,
                                       final RecordIdCacheLoader recordIdCacheLoader,
                                       final AccountRecordIdCacheLoader accountRecordIdCacheLoader,
                                       final TenantRecordIdCacheLoader tenantRecordIdCacheLoader,
                                       final AuditLogCacheLoader auditLogCacheLoader,
                                       final AuditLogViaHistoryCacheLoader auditLogViaHistoryCacheLoader) {
        this.cacheConfig = cacheConfig;
        cacheLoaders.add(recordIdCacheLoader);
        cacheLoaders.add(accountRecordIdCacheLoader);
        cacheLoaders.add(tenantRecordIdCacheLoader);
        cacheLoaders.add(auditLogCacheLoader);
        cacheLoaders.add(auditLogViaHistoryCacheLoader);
    }

    @Override
    public CacheManager get() {
        final CacheManager cacheManager;
        try {
            final InputStream inputStream = UriAccessor.accessUri(cacheConfig.getCacheConfigLocation());
            cacheManager = CacheManager.create(inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        for (final BaseCacheLoader cacheLoader : cacheLoaders) {
            cacheLoader.init();

            final Cache cache = cacheManager.getCache(cacheLoader.getCacheType().getCacheName());

            // Make sure we start from a clean state - this is mainly useful for tests
            for (final CacheLoader existingCacheLoader : cache.getRegisteredCacheLoaders()) {
                cache.unregisterCacheLoader(existingCacheLoader);
            }

            cache.registerCacheLoader(cacheLoader);
        }

        return cacheManager;
    }
}
