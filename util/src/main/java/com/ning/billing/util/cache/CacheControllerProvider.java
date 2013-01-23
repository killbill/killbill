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


import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.util.cache.Cachable.CacheType;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.name.Names;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.loader.CacheLoader;

public class CacheControllerProvider<K,V> implements Provider<CacheController<K, V>> {

    private final static Logger logger = LoggerFactory.getLogger(CacheControllerProvider.class);

    private CacheManager cacheManager;
    private final String name;

    private Injector injector;

    public CacheControllerProvider(final String name) {
        this.name = name;
    }

    @Inject
    public void configure(final Injector injector, final CacheManager cacheManager) {
        this.injector = injector;
        this.cacheManager = cacheManager;
    }

    @Override
    public CacheController<K, V> get() {

        final Cache cache = cacheManager.getCache(name);
        final Key<CacheLoader> cacheLoaderKey = Key.get(CacheLoader.class, Names.named(name));
        final CacheLoader cacheLoader = injector.getInstance(cacheLoaderKey);

        cacheLoader.init();

        cache.registerCacheLoader(cacheLoader);

        return new EhCacheBasedCacheController<K, V>(cache, CacheType.findByName(name));
    }
}
