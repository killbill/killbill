/*
 * Copyright 2010-2013 Ning, Inc.
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

import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.skife.jdbi.v2.IDBI;

import org.killbill.billing.ObjectType;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.killbill.billing.util.dao.NonEntityDao;

import net.sf.ehcache.loader.CacheLoader;

@Singleton
public class AccountRecordIdCacheLoader extends BaseCacheLoader implements CacheLoader {

    @Inject
    public AccountRecordIdCacheLoader(final IDBI dbi, final NonEntityDao nonEntityDao) {
        super(dbi, nonEntityDao);
    }

    @Override
    public CacheType getCacheType() {
        return CacheType.ACCOUNT_RECORD_ID;
    }

    @Override
    public Object load(final Object key, final Object argument) {
        checkCacheLoaderStatus();

        if (!(key instanceof String)) {
            throw new IllegalArgumentException("Unexpected key type of " + key.getClass().getName());
        }
        if (!(argument instanceof CacheLoaderArgument)) {
            throw new IllegalArgumentException("Unexpected key type of " + argument.getClass().getName());
        }

        final String objectId = (String) key;
        final ObjectType objectType = ((CacheLoaderArgument) argument).getObjectType();

        return nonEntityDao.retrieveAccountRecordIdFromObject(UUID.fromString(objectId), objectType, null);
    }
}
