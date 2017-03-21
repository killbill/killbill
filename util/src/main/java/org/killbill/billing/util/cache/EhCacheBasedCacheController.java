/*
 * Copyright 2010-2012 Ning, Inc.
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

package org.killbill.billing.util.cache;

import java.util.Collection;
import java.util.HashSet;

import org.killbill.billing.util.cache.Cachable.CacheType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import net.sf.ehcache.CacheException;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;

public class EhCacheBasedCacheController<K, V> implements CacheController<K, V> {

    private static final Logger logger = LoggerFactory.getLogger(EhCacheBasedCacheController.class);

    private final Ehcache cache;
    private final BaseCacheLoader<K, V> baseCacheLoader;

    public EhCacheBasedCacheController(final Ehcache cache, final BaseCacheLoader<K, V> baseCacheLoader) {
        this.cache = cache;
        this.baseCacheLoader = baseCacheLoader;
    }

    @Override
    public V get(final K key, final CacheLoaderArgument cacheLoaderArgument) {
        checkKey(key);

        final V value;
        if (!cache.isKeyInCache(key)) {
            value = computeAndCacheValue(key, cacheLoaderArgument);
        } else {
            final Element element = cache.get(key);
            if (element == null) {
                value = null;
            } else if (element.isExpired()) {
                value = computeAndCacheValue(key, cacheLoaderArgument);
            } else {
                value = (V) element.getObjectValue();
            }
        }

        if (value == null || value.equals(BaseCacheLoader.EMPTY_VALUE_PLACEHOLDER)) {
            return null;
        } else {
            checkValue(value);
            return value;
        }
    }

    @Override
    public void putIfAbsent(final K key, final V value) {
        checkKey(key);
        checkValue(value);
        cache.putIfAbsent(new Element(key, value));
    }

    @Override
    public boolean remove(final K key) {
        checkKey(key);
        if (cache.isKeyInCache(key)) {
            cache.remove(key);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void remove(final Function<K, Boolean> keyMatcher) {
        final Collection<K> toRemove = new HashSet<K>();
        for (final Object key : cache.getKeys()) {
            if (keyMatcher.apply((K) key) == Boolean.TRUE) {
                toRemove.add((K) key);
            }
        }
        cache.removeAll(toRemove);
    }

    @Override
    public void removeAll() {
        cache.removeAll();
    }

    @Override
    public int size() {
        return cache.getSize();
    }

    @Override
    public CacheType getCacheType() {
        return baseCacheLoader.getCacheType();
    }

    private V computeAndCacheValue(final K key, final CacheLoaderArgument cacheLoaderArgument) {
        checkKey(key);

        final V value;
        try {
            value = baseCacheLoader.compute(key, cacheLoaderArgument);
        } catch (final Exception e) {
            logger.warn("Unable to compute cached value for key='{}' and cacheLoaderArgument='{}'", key, cacheLoaderArgument);
            throw new CacheException(e);
        }

        if (value == null) {
            return null;
        }

        checkValue(value);

        // Race condition, we may compute it for nothing
        cache.putIfAbsent(new Element(key, value));

        return value;
    }

    private void checkKey(final K keyObject) {
        if (keyObject == null) {
            throw new NullPointerException();
        }
        if (!getCacheType().getKeyType().isAssignableFrom(keyObject.getClass())) {
            throw new ClassCastException("Invalid key type, expected : " + getCacheType().getKeyType().getName() + " but was : " + keyObject.getClass().getName());
        }
    }

    private void checkValue(final V valueObject) {
        if (valueObject == null) {
            throw new NullPointerException();
        }
        if (!getCacheType().getValueType().isAssignableFrom(valueObject.getClass())) {
            throw new ClassCastException("Invalid value type, expected : " + getCacheType().getValueType().getName() + " but was : " + valueObject.getClass().getName());
        }
    }
}
