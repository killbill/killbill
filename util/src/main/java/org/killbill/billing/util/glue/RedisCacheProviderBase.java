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
import javax.cache.configuration.Configuration;
import javax.cache.configuration.MutableConfiguration;

import org.redisson.api.RedissonClient;
import org.redisson.jcache.configuration.RedissonConfiguration;

import com.codahale.metrics.MetricRegistry;

abstract class RedisCacheProviderBase extends CacheProviderBase {

    private final RedissonClient redissonClient;

    RedisCacheProviderBase(final MetricRegistry metricRegistry, final RedissonClient redissonClient) {
        super(metricRegistry);
        this.redissonClient = redissonClient;
    }

    <K, V> void createCache(final CacheManager cacheManager, final String cacheName, final Class<K> keyType, final Class<V> valueType) {
        final Configuration jcacheConfig = new MutableConfiguration().setTypes(keyType, valueType);

        final Configuration redissonConfiguration = RedissonConfiguration.fromInstance(redissonClient, jcacheConfig);

        createCache(cacheManager, cacheName, redissonConfiguration);
    }
}
