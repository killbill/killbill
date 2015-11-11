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

import net.sf.ehcache.CacheManager;

public class EhCacheManagerProvider implements Provider<EhCacheManager> {

    private final SecurityManager securityManager;
    private final CacheManager ehCacheCacheManager;

    @Inject
    public EhCacheManagerProvider(final SecurityManager securityManager, final CacheManager ehCacheCacheManager) {
        this.securityManager = securityManager;
        this.ehCacheCacheManager = ehCacheCacheManager;
    }

    @Override
    public EhCacheManager get() {
        final EhCacheManager shiroEhCacheManager = new EhCacheManager();
        // Same EhCache manager instance as the rest of the system
        shiroEhCacheManager.setCacheManager(ehCacheCacheManager);

        if (securityManager instanceof DefaultSecurityManager) {
            // For RBAC only (see also KillbillJdbcTenantRealmProvider)
            ((DefaultSecurityManager) securityManager).setCacheManager(shiroEhCacheManager);
        }

        return shiroEhCacheManager;
    }
}
