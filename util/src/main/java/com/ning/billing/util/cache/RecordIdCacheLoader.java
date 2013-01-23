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

import java.util.UUID;

import javax.inject.Inject;

import org.skife.jdbi.v2.IDBI;

import com.ning.billing.ObjectType;
import com.ning.billing.util.dao.NonEntityDao;

import net.sf.ehcache.CacheException;
import net.sf.ehcache.loader.CacheLoader;

public class RecordIdCacheLoader extends BaseCacheLoader implements CacheLoader {

    @Inject
    public RecordIdCacheLoader(final IDBI dbi, final NonEntityDao nonEntityDao) {
        super(dbi, nonEntityDao);
    }

    @Override
    public Object load(final Object key, final Object argument) throws CacheException {

        checkCacheLoaderStatus();

        if (!(argument instanceof ObjectType)) {
            throw new IllegalArgumentException("Unexpected argument type of " +
                                               argument != null ? argument.getClass().getName() : "null");
        }
        if (!(key instanceof String)) {
            throw new IllegalArgumentException("Unexpected key type of " +
                                               key != null ? key.getClass().getName() : "null");

        }
        final String objectId = (String) key;
        final ObjectType objectType = (ObjectType) argument;
        Long value = nonEntityDao.retrieveRecordIdFromObject(UUID.fromString(objectId), objectType, null);
        return value;
    }
}
