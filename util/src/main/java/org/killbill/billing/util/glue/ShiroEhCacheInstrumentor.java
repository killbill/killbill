/*
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

import org.apache.shiro.cache.CacheManager;
import org.apache.shiro.realm.AuthenticatingRealm;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.realm.Realm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ehcache.InstrumentedEhcache;
import net.sf.ehcache.CacheException;
import net.sf.ehcache.Ehcache;

public class ShiroEhCacheInstrumentor {

    private static final Logger logger = LoggerFactory.getLogger(ShiroEhCacheInstrumentor.class);

    private final MetricRegistry metricRegistry;
    private final CacheManager shiroEhCacheManager;
    private final net.sf.ehcache.CacheManager ehCacheCacheManager;

    @Inject
    public ShiroEhCacheInstrumentor(final MetricRegistry metricRegistry, final CacheManager shiroEhCacheManager, final net.sf.ehcache.CacheManager ehCacheCacheManager) {
        this.metricRegistry = metricRegistry;
        this.shiroEhCacheManager = shiroEhCacheManager;
        this.ehCacheCacheManager = ehCacheCacheManager;
    }

    public void instrument(final Realm realm) {
        if (realm instanceof AuthorizingRealm) {
            instrument((AuthorizingRealm) realm);
        } else if (realm instanceof AuthenticatingRealm) {
            instrument((AuthenticatingRealm) realm);
        }
    }

    public void instrument(final AuthorizingRealm realm) {
        instrument(realm.getAuthenticationCacheName());
        instrument(realm.getAuthorizationCacheName());
    }

    public void instrument(final AuthenticatingRealm realm) {
        instrument(realm.getAuthenticationCacheName());
    }

    public void instrument(final String cacheName) {
        // Initialize the cache, if it doesn't exist yet
        // Note: Shiro's cache manager is not thread safe. Concurrent requests on startup
        // can throw org.apache.shiro.cache.CacheException: net.sf.ehcache.ObjectExistsException: Cache shiro-activeSessionCache already exists
        shiroEhCacheManager.getCache(cacheName);

        final Ehcache shiroEhcache = ehCacheCacheManager.getEhcache(cacheName);
        final Ehcache decoratedCache = InstrumentedEhcache.instrument(metricRegistry, shiroEhcache);
        try {
            ehCacheCacheManager.replaceCacheWithDecoratedCache(shiroEhcache, decoratedCache);
        } catch (final CacheException e) {
            logger.warn("Unable to instrument cache {}: {}", shiroEhcache.getName(), e.getMessage());
        }
    }
}
