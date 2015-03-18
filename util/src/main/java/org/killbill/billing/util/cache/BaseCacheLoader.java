/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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
import java.util.Map;

import javax.inject.Inject;

import org.killbill.billing.util.cache.Cachable.CacheType;

import net.sf.ehcache.CacheException;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Status;
import net.sf.ehcache.loader.CacheLoader;

public abstract class BaseCacheLoader implements CacheLoader {

    static final String EMPTY_VALUE_PLACEHOLDER = "__#VALEUR!__";

    private Status cacheLoaderStatus;

    @Inject
    public BaseCacheLoader() {
        this.cacheLoaderStatus = Status.STATUS_UNINITIALISED;
    }

    public abstract CacheType getCacheType();

    @Override
    public abstract Object load(final Object key, final Object argument);

    @Override
    public Object load(final Object key) throws CacheException {
        throw new IllegalStateException("Method load is not implemented ");
    }

    @Override
    public Map loadAll(final Collection keys) {
        throw new IllegalStateException("Method loadAll is not implemented ");
    }

    @Override
    public Map loadAll(final Collection keys, final Object argument) {
        throw new IllegalStateException("Method loadAll is not implemented ");
    }

    @Override
    public String getName() {
        return this.getClass().getName();
    }

    @Override
    public CacheLoader clone(final Ehcache cache) throws CloneNotSupportedException {
        throw new IllegalStateException("Method clone is not implemented ");
    }

    @Override
    public void init() {
        this.cacheLoaderStatus = Status.STATUS_ALIVE;
    }

    @Override
    public void dispose() throws CacheException {
        cacheLoaderStatus = Status.STATUS_SHUTDOWN;
    }

    @Override
    public Status getStatus() {
        return cacheLoaderStatus;
    }

    protected void checkCacheLoaderStatus() {
        if (getStatus() != Status.STATUS_ALIVE) {
            throw new CacheException("CacheLoader is not available!");
        }
    }
}
