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

package org.killbill.billing.util.glue;

import java.lang.reflect.Field;

import javax.cache.CacheManager;
import javax.inject.Inject;
import javax.inject.Provider;

import org.apache.shiro.cache.Cache;
import org.apache.shiro.cache.CacheException;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.mgt.SecurityManager;
import org.ehcache.integrations.shiro.EhcacheShiro;
import org.ehcache.integrations.shiro.EhcacheShiroManager;
import org.killbill.billing.util.config.definition.EhCacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;

public class EhcacheShiroManagerProvider extends CacheProviderBase implements Provider<EhcacheShiroManager> {

    private final SecurityManager securityManager;
    private final CacheManager eh107CacheManager;
    private final org.ehcache.CacheManager ehcacheCacheManager;

    @Inject
    public EhcacheShiroManagerProvider(final SecurityManager securityManager,
                                       final CacheManager eh107CacheManager,
                                       final MetricRegistry metricRegistry,
                                       final EhCacheConfig cacheConfig) {
        super(metricRegistry, cacheConfig);
        this.securityManager = securityManager;
        this.eh107CacheManager = eh107CacheManager;
        this.ehcacheCacheManager = getEhcacheManager();
    }

    @Override
    public EhcacheShiroManager get() {
        final EhcacheShiroManager shiroEhCacheManager = new EhcacheShiroManagerWrapper(this);
        // Same EhCache manager instance as the rest of the system
        shiroEhCacheManager.setCacheManager(ehcacheCacheManager);

        if (securityManager instanceof DefaultSecurityManager) {
            // For RBAC only (see also KillbillJdbcTenantRealmProvider)
            final DefaultSecurityManager securityManager = (DefaultSecurityManager) this.securityManager;
            securityManager.setCacheManager(shiroEhCacheManager);
            securityManager.setSubjectDAO(new KillBillSubjectDAO());
        }

        return shiroEhCacheManager;
    }

    // Shiro isn't JCache compatible
    private org.ehcache.CacheManager getEhcacheManager() {
        try {
            final Field f = eh107CacheManager.getClass().getDeclaredField("ehCacheManager");
            f.setAccessible(true);

            return (org.ehcache.CacheManager) f.get(eh107CacheManager);
        } catch (final IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (final NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    // Custom createCache implementation going through JCache layer to enable stats, etc.
    private final class EhcacheShiroManagerWrapper extends EhcacheShiroManager {

        private final Logger log = LoggerFactory.getLogger(EhcacheShiroManagerWrapper.class);

        private final EhcacheShiroManagerProvider ehcacheShiroManagerProvider;

        EhcacheShiroManagerWrapper(final EhcacheShiroManagerProvider ehcacheShiroManagerProvider) {
            this.ehcacheShiroManagerProvider = ehcacheShiroManagerProvider;
        }

        public <K, V> Cache<K, V> getCache(final String name) throws CacheException {
            log.trace("Acquiring EhcacheShiro instance named [{}]", name);

            org.ehcache.Cache<Object, Object> cache = getCacheManager().getCache(name, Object.class, Object.class);

            if (cache == null) {
                log.info("Cache with name {} does not yet exist.  Creating now.", name);
                ehcacheShiroManagerProvider.createCache(eh107CacheManager, name, Object.class, Object.class);
                cache = getCacheManager().getCache(name, Object.class, Object.class);
                log.info("Added EhcacheShiro named [{}]", name);
            } else {
                log.info("Using existing EhcacheShiro named [{}]", name);
            }

            return new EhcacheShiro<K, V>(cache);
        }
    }
}
