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
import org.apache.shiro.mgt.SubjectDAO;
import org.redisson.api.RedissonClient;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.name.Named;

import static org.killbill.billing.util.glue.CacheModule.REDIS_CACHE_CLIENT;

public class RedisShiroManagerProvider implements Provider<RedisShiroManager> {

    private final SecurityManager securityManager;
    private final SubjectDAO subjectDAO;
    private final CacheManager eh107CacheManager;
    private final MetricRegistry metricRegistry;
    private final RedissonClient redissonClient;

    @Inject
    public RedisShiroManagerProvider(final SecurityManager securityManager,
                                     final SubjectDAO subjectDAO,
                                     final CacheManager eh107CacheManager,
                                     final MetricRegistry metricRegistry,
                                     @Named(REDIS_CACHE_CLIENT) final RedissonClient redissonClient) {
        this.securityManager = securityManager;
        this.subjectDAO = subjectDAO;
        this.eh107CacheManager = eh107CacheManager;
        this.metricRegistry = metricRegistry;
        this.redissonClient = redissonClient;
    }

    @Override
    public RedisShiroManager get() {
        // Same Redis manager instance as the rest of the system
        final RedisShiroManager shiroRedisManager = new RedisShiroManager(eh107CacheManager, metricRegistry, redissonClient);

        if (securityManager instanceof DefaultSecurityManager) {
            // For RBAC only (see also KillbillJdbcTenantRealmProvider)
            final DefaultSecurityManager securityManager = (DefaultSecurityManager) this.securityManager;
            securityManager.setCacheManager(shiroRedisManager);
            securityManager.setSubjectDAO(subjectDAO);
        }

        return shiroRedisManager;
    }
}
