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

package org.killbill.billing.util.glue;

import javax.inject.Inject;
import javax.inject.Provider;

import org.apache.shiro.cache.ehcache.EhCacheManager;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.session.mgt.eis.CachingSessionDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ehcache.InstrumentedEhcache;
import net.sf.ehcache.CacheException;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;

public class EhCacheManagerProvider implements Provider<EhCacheManager> {

    private static final Logger logger = LoggerFactory.getLogger(EhCacheManagerProvider.class);

    private final MetricRegistry metricRegistry;
    private final SecurityManager securityManager;
    private final CacheManager ehCacheCacheManager;

    @Inject
    public EhCacheManagerProvider(final MetricRegistry metricRegistry, final SecurityManager securityManager, final CacheManager ehCacheCacheManager) {
        this.metricRegistry = metricRegistry;
        this.securityManager = securityManager;
        this.ehCacheCacheManager = ehCacheCacheManager;
    }

    @Override
    public EhCacheManager get() {
        final EhCacheManager shiroEhCacheManager = new EhCacheManager();
        // Same EhCache manager instance as the rest of the system
        shiroEhCacheManager.setCacheManager(ehCacheCacheManager);

        // It looks like Shiro's cache manager is not thread safe. Concurrent requests on startup
        // can throw org.apache.shiro.cache.CacheException: net.sf.ehcache.ObjectExistsException: Cache shiro-activeSessionCache already exists
        // As a workaround, create the cache manually here
        shiroEhCacheManager.getCache(CachingSessionDAO.ACTIVE_SESSION_CACHE_NAME);

        // Instrument the cache
        final Ehcache shiroActiveSessionEhcache = ehCacheCacheManager.getEhcache(CachingSessionDAO.ACTIVE_SESSION_CACHE_NAME);
        final Ehcache decoratedCache = InstrumentedEhcache.instrument(metricRegistry, shiroActiveSessionEhcache);
        try {
            ehCacheCacheManager.replaceCacheWithDecoratedCache(shiroActiveSessionEhcache, decoratedCache);
        } catch (final CacheException e) {
            logger.warn("Unable to instrument cache {}: {}", shiroActiveSessionEhcache.getName(), e.getMessage());
        }

        if (securityManager instanceof DefaultSecurityManager) {
            ((DefaultSecurityManager) securityManager).setCacheManager(shiroEhCacheManager);
        }

        return shiroEhCacheManager;
    }
}
