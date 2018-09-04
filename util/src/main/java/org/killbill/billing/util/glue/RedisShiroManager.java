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

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import javax.cache.Cache.Entry;

import org.apache.shiro.cache.Cache;
import org.apache.shiro.cache.CacheException;
import org.apache.shiro.cache.CacheManager;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;

public class RedisShiroManager extends RedisCacheProviderBase implements CacheManager {

    private static final Logger log = LoggerFactory.getLogger(RedisShiroManager.class);

    private final javax.cache.CacheManager eh107CacheManager;

    public RedisShiroManager(final javax.cache.CacheManager eh107CacheManager,
                             final MetricRegistry metricRegistry,
                             final RedissonClient redissonClient) {
        super(metricRegistry, redissonClient);
        this.eh107CacheManager = eh107CacheManager;
    }

    @Override
    public <K, V> Cache<K, V> getCache(final String name) throws CacheException {
        log.trace("Acquiring RedisShiro instance named [{}]", name);

        javax.cache.Cache<Object, Object> cache = eh107CacheManager.getCache(name, Object.class, Object.class);

        if (cache == null) {
            log.info("Cache with name {} does not yet exist.  Creating now.", name);
            createCache(eh107CacheManager, name, Object.class, Object.class);
            cache = eh107CacheManager.getCache(name, Object.class, Object.class);
            log.info("Added RedisShiro named [{}]", name);
        } else {
            log.info("Using existing RedisShiro named [{}]", name);
        }

        return new RedisCache<K, V>(cache);
    }

    private static final class RedisCache<K, V> implements Cache<K, V> {

        private final javax.cache.Cache<K, V> cache;

        public RedisCache(final javax.cache.Cache cache) {
            this.cache = cache;

        }

        @Override
        public V get(final K key) throws CacheException {
            return cache.get(key);
        }

        @Override
        public V put(final K key, final V value) throws CacheException {
            V previousValue;
            while (true) {
                previousValue = cache.get(key);
                if (previousValue == null) {
                    if (cache.putIfAbsent(key, value)) {
                        break;
                    }
                } else {
                    if (cache.replace(key, value)) {
                        break;
                    }
                }
            }

            return previousValue;
        }

        @Override
        public V remove(final K key) throws CacheException {
            V previousValue;
            while (true) {
                previousValue = cache.get(key);
                if (previousValue == null) {
                    break;
                } else {
                    if (cache.remove(key)) {
                        break;
                    }
                }
            }

            return previousValue;
        }

        @Override
        public void clear() throws CacheException {
            cache.clear();
        }

        @Override
        public int size() {
            return keys().size();
        }

        @Override
        public Set<K> keys() {
            final Set<K> result = new HashSet<K>();
            for (final Entry<K, V> entry : cache) {
                result.add(entry.getKey());
            }
            return result;
        }

        @Override
        public Collection<V> values() {
            final Collection<V> result = new LinkedList<V>();
            for (final Entry<K, V> entry : cache) {
                result.add(entry.getValue());
            }
            return result;
        }
    }
}
