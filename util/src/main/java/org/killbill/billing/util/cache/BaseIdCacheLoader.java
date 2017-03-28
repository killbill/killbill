/*
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

import org.killbill.billing.ObjectType;
import org.skife.jdbi.v2.Handle;

public abstract class BaseIdCacheLoader<V> extends BaseCacheLoader<String, V> {

    protected BaseIdCacheLoader() {
        super();
    }

    protected abstract V doRetrieveOperation(final String rawKey, final ObjectType objectType, final Handle handle);

    @Override
    public V compute(final String key, final CacheLoaderArgument cacheLoaderArgument) {
        final String rawKey;
        if (getCacheType().isKeyPrefixedWithTableName()) {
            final String[] parts = key.split(CacheControllerDispatcher.CACHE_KEY_SEPARATOR);
            rawKey = parts[1];
        } else {
            rawKey = key;
        }

        final ObjectType objectType = cacheLoaderArgument.getObjectType();
        final Handle handle = cacheLoaderArgument.getHandle();
        return doRetrieveOperation(rawKey, objectType, handle);
    }
}
