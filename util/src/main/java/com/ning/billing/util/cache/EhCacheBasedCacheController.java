/*
 * Copyright 2010-2012 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

package com.ning.billing.util.cache;

import java.util.Collection;
import java.util.Map;

import com.ning.billing.ObjectType;
import com.ning.billing.util.cache.Cachable.CacheType;

import net.sf.ehcache.Cache;
import net.sf.ehcache.Element;

public class EhCacheBasedCacheController<K, V> implements CacheController<K, V> {

    private final Cache cache;
    private final CacheType cacheType;

    public EhCacheBasedCacheController(final Cache cache, final CacheType cacheType) {
        this.cache = cache;
        this.cacheType = cacheType;
    }

    @Override
    public CacheType getType() {
        return cacheType;
    }

    @Override
    public V get(final K key, final ObjectType objectType) {
        final Element element = cache.getWithLoader(key, null, objectType);
        if (element == null) {
            return null;
        }
        return (V) element.getObjectValue();
    }

    @Override
    public boolean remove(final K key) {
        return cache.remove(key);
    }

    @Override
    public int size() {
        return cache.getSize();
    }
}
