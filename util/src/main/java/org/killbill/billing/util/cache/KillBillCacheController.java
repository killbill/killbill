/*
 * Copyright 2010-2012 Ning, Inc.
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

package org.killbill.billing.util.cache;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.cache.Cache;
import javax.cache.Cache.Entry;
import javax.cache.CacheException;

import org.killbill.billing.util.cache.Cachable.CacheType;
import org.killbill.commons.utils.collect.Iterables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KillBillCacheController<K, V> implements CacheController<K, V> {

    private static final Logger logger = LoggerFactory.getLogger(KillBillCacheController.class);

    private final Cache<K, V> cache;
    private final BaseCacheLoader<K, V> baseCacheLoader;

    public KillBillCacheController(final Cache<K, V> cache, final BaseCacheLoader<K, V> baseCacheLoader) {
        this.cache = cache;
        this.baseCacheLoader = baseCacheLoader;
    }

    @Override
    public List<K> getKeys() {
        return Iterables.toStream(cache)
                .map(Entry::getKey)
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public boolean isKeyInCache(final K key) {
        return cache.containsKey(key);
    }

    @Override
    public V get(final K key, final CacheLoaderArgument cacheLoaderArgument) {
        if (key == null) {
            return null;
        }

        V value;
        try {
            if (!isKeyInCache(key)) {
                synchronized (this) {
                    if (!isKeyInCache(key)) {
                        value = computeAndCacheValue(key, cacheLoaderArgument);
                    } else {
                        value = cache.get(key);
                    }
                }
            } else {
                value = cache.get(key);
            }
        } catch (final CacheException e) {
            logger.warn("Unable to retrieve cached value for key='{}' and cacheLoaderArgument='{}'", key, cacheLoaderArgument, e);
            value = computeValue(key, cacheLoaderArgument);
        }

        if (value == null || value.equals(BaseCacheLoader.EMPTY_VALUE_PLACEHOLDER)) {
            return null;
        } else {
            return value;
        }
    }

    @Override
    public void putIfAbsent(final K key, final V value) {
        cache.putIfAbsent(key, value);
    }

    @Override
    public boolean remove(final K key) {
        if (isKeyInCache(key)) {
            cache.remove(key);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void remove(final Function<K, Boolean> keyMatcher) {
        final Set<K> toRemove = new HashSet<K>();
        for (final K key : getKeys()) {
            if (keyMatcher.apply(key) == Boolean.TRUE) {
                toRemove.add(key);
            }
        }
        cache.removeAll(toRemove);
    }

    @Override
    public void removeAll() {
        cache.clear();
    }

    @Override
    public int size() {
        return Iterables.size(cache);
    }

    @Override
    public CacheType getCacheType() {
        return baseCacheLoader.getCacheType();
    }

    private V computeAndCacheValue(final K key, final CacheLoaderArgument cacheLoaderArgument) {
        final V value = computeValue(key, cacheLoaderArgument);
        if (value == null) {
            return null;
        }

        cache.put(key, value);

        return value;
    }

    private V computeValue(final K key, final CacheLoaderArgument cacheLoaderArgument) {
        final V value;
        try {
            value = baseCacheLoader.compute(key, cacheLoaderArgument);
        } catch (final Exception e) {
            // Remove noisy log (might be expected, see https://github.com/killbill/killbill/issues/842)
            //logger.warn("Unable to compute cached value for key='{}' and cacheLoaderArgument='{}'", key, cacheLoaderArgument, e);
            throw new RuntimeException(e);
        }
        return value;
    }
}
