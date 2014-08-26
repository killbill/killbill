/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

import org.killbill.billing.ObjectType;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.killbill.billing.util.dao.NonEntityDao;
import org.skife.jdbi.v2.IDBI;

public abstract class BaseIdCacheLoader extends BaseCacheLoader {

    protected BaseIdCacheLoader(final IDBI dbi, final NonEntityDao nonEntityDao) {
        super(dbi, nonEntityDao);
    }

    @Override
    public abstract CacheType getCacheType();


    protected abstract Object doRetrieveOperation(final String rawKey, final ObjectType objectType);

    @Override
    public Object load(final Object key, final Object argument) {
        checkCacheLoaderStatus();

        if (!(key instanceof String)) {
            throw new IllegalArgumentException("Unexpected key type of " + key.getClass().getName());
        }
        if (!(argument instanceof CacheLoaderArgument)) {
            throw new IllegalArgumentException("Unexpected key type of " + argument.getClass().getName());
        }

        final String rawKey;
        if (getCacheType().isKeyPrefixedWithTableName()) {
            String [] parts = ((String) key).split(CacheControllerDispatcher.CACHE_KEY_SEPARATOR);
            rawKey = parts[1];
        } else {
            rawKey = (String) key;
        }
        final ObjectType objectType = ((CacheLoaderArgument) argument).getObjectType();
        return doRetrieveOperation(rawKey, objectType);
    }
}
