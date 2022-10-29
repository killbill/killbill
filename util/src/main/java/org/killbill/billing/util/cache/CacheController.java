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

import java.util.List;
import java.util.function.Function;

import org.killbill.billing.util.cache.Cachable.CacheType;

public interface CacheController<K, V> {

    List<K> getKeys();

    boolean isKeyInCache(K key);

    V get(K key, CacheLoaderArgument objectType);

    boolean remove(K key);

    void remove(Function<K, Boolean> keyMatcher);

    void putIfAbsent(final K key, V value);

    int size();

    void removeAll();

    CacheType getCacheType();
}
