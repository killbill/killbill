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

package org.killbill.billing.util.cache;

import net.sf.ehcache.Cache;
import net.sf.ehcache.Element;

public class EhCacheBasedCacheController<K, V> implements CacheController<K, V> {

    private final Cache cache;

    public EhCacheBasedCacheController(final Cache cache) {
        this.cache = cache;
    }

    @Override
    public V get(final K key, final CacheLoaderArgument cacheLoaderArgument) {
        final Element element = cache.getWithLoader(key, null, cacheLoaderArgument);
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

    @Override
    public void removeAll() {
        cache.removeAll();
    }
}
