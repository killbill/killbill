/*
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
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

import java.util.List;

import org.killbill.billing.util.cache.Cachable.CacheType;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;

// No support to turn off a cache in Ehcache 3 / JCache (JSR 107) unfortunately
public class NoOpCacheController<K, V> implements CacheController<K, V> {

    private final BaseCacheLoader<K, V> baseCacheLoader;

    public NoOpCacheController(final BaseCacheLoader<K, V> baseCacheLoader) {
        this.baseCacheLoader = baseCacheLoader;
    }

    @Override
    public List<K> getKeys() {
        return ImmutableList.<K>of();
    }

    @Override
    public boolean isKeyInCache(final K key) {
        return false;
    }

    @Override
    public V get(final K key, final CacheLoaderArgument cacheLoaderArgument) {
        if (key == null) {
            return null;
        }

        final V value = computeValue(key, cacheLoaderArgument);
        if (BaseCacheLoader.EMPTY_VALUE_PLACEHOLDER.equals(value)) {
            return null;
        } else {
            return value;
        }
    }

    @Override
    public boolean remove(final K key) {
        return false;
    }

    @Override
    public void remove(final Function<K, Boolean> keyMatcher) {
    }

    @Override
    public void putIfAbsent(final K key, final V value) {
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public void removeAll() {
    }

    @Override
    public CacheType getCacheType() {
        return baseCacheLoader.getCacheType();
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
