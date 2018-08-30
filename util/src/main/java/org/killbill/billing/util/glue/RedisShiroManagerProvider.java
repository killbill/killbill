/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

import javax.cache.CacheManager;
import javax.inject.Inject;
import javax.inject.Provider;

import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.mgt.SecurityManager;
import org.killbill.billing.util.config.definition.RedisCacheConfig;

import com.codahale.metrics.MetricRegistry;

public class RedisShiroManagerProvider implements Provider<RedisShiroManager> {

    private final CacheManager eh107CacheManager;
    private final SecurityManager securityManager;
    private final MetricRegistry metricRegistry;
    private final RedisCacheConfig cacheConfig;

    @Inject
    public RedisShiroManagerProvider(final SecurityManager securityManager,
                                     final CacheManager eh107CacheManager,
                                     final MetricRegistry metricRegistry,
                                     final RedisCacheConfig cacheConfig) {
        this.securityManager = securityManager;
        this.eh107CacheManager = eh107CacheManager;
        this.metricRegistry = metricRegistry;
        this.cacheConfig = cacheConfig;
    }

    @Override
    public RedisShiroManager get() {
        // Same Redis manager instance as the rest of the system
        final RedisShiroManager shiroRedisManager = new RedisShiroManager(eh107CacheManager, metricRegistry, cacheConfig);

        if (securityManager instanceof DefaultSecurityManager) {
            // For RBAC only (see also KillbillJdbcTenantRealmProvider)
            final DefaultSecurityManager securityManager = (DefaultSecurityManager) this.securityManager;
            securityManager.setCacheManager(shiroRedisManager);
            securityManager.setSubjectDAO(new KillBillSubjectDAO());
        }

        return shiroRedisManager;
    }
}
