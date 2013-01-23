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

import org.testng.Assert;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.ning.billing.ObjectType;
import com.ning.billing.mock.glue.MockDbHelperModule;
import com.ning.billing.util.UtilTestSuiteWithEmbeddedDB;
import com.ning.billing.util.cache.Cachable.CacheType;
import com.ning.billing.util.dao.NonEntityDao;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoTransactionWrapper;
import com.ning.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import com.ning.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import com.ning.billing.util.glue.CacheModule;
import com.ning.billing.util.glue.ClockModule;
import com.ning.billing.util.glue.NonEntityDaoModule;
import com.ning.billing.util.tag.dao.TagModelDao;
import com.ning.billing.util.tag.dao.TagSqlDao;

@Guice(modules = {ClockModule.class, CacheModule.class, MockDbHelperModule.class, NonEntityDaoModule.class } )
public class TestCache extends UtilTestSuiteWithEmbeddedDB {

    @Inject
    private CacheControllerDispatcher controlCacheDispatcher;

    @Inject
    private NonEntityDao nonEntityDao;

    private  EntitySqlDaoTransactionalJdbiWrapper transactionalSqlDao;


    private void insertTag(final TagModelDao modelDao) {
        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                entitySqlDaoWrapperFactory.become(TagSqlDao.class).create(modelDao, internalCallContext);
                return null;
            }
        });
    }

    private Long getTagRecordId(final UUID tagId) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Long>() {
            @Override
            public Long inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(TagSqlDao.class).getRecordId(tagId.toString(), internalCallContext);
            }
        });
    }

    private int getCacheSize() {
        final CacheController<Object, Object> cache = controlCacheDispatcher.getCacheController(CacheType.RECORD_ID);
        return cache != null ? cache.size() : 0;
    }

    private Long retrieveRecordIdFromCache(UUID tagId) {
        final CacheController<Object, Object> cache = controlCacheDispatcher.getCacheController(CacheType.RECORD_ID);
        Object result = null;
        if (cache != null) {
            result =  cache.get(tagId.toString(), ObjectType.TAG);
        }
        return (Long) result;
    }

    @Test(groups = "slow")
    public void testCacheRecordId() throws Exception {

        this.transactionalSqlDao = new EntitySqlDaoTransactionalJdbiWrapper(getDBI(), clock, controlCacheDispatcher, nonEntityDao);
        final TagModelDao tag = new TagModelDao(clock.getUTCNow(), UUID.randomUUID(), UUID.randomUUID(), ObjectType.TAG);

        // Verify we start with nothing in the cache
        Assert.assertEquals(getCacheSize(), 0);
        insertTag(tag);

        // Verify we still have nothing after insert in the cache
        Assert.assertEquals(getCacheSize(), 0);

        final Long tagRecordId = getTagRecordId(tag.getId());
        // Verify we now have something  in the cache
        Assert.assertEquals(getCacheSize(), 1);

        final Long recordIdFromCache = retrieveRecordIdFromCache(tag.getId());
        Assert.assertNotNull(recordIdFromCache);

        Assert.assertEquals(recordIdFromCache, new Long(1));
        Assert.assertEquals(tagRecordId, new Long(1));

        Assert.assertEquals(getCacheSize(), 1);
    }
}
