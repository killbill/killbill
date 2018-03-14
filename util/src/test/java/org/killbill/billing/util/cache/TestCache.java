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

import java.util.UUID;

import org.killbill.billing.ObjectType;
import org.killbill.billing.util.UtilTestSuiteWithEmbeddedDB;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.killbill.billing.util.dao.TableName;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import org.killbill.billing.util.tag.dao.TagModelDao;
import org.killbill.billing.util.tag.dao.TagSqlDao;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestCache extends UtilTestSuiteWithEmbeddedDB {

    private EntitySqlDaoTransactionalJdbiWrapper transactionalSqlDao;

    private Long getTagRecordId(final UUID tagId) {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<Long>() {
            @Override
            public Long inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(TagSqlDao.class).getRecordId(tagId.toString(), internalCallContext);
            }
        });
    }

    private int getCacheSize(CacheType cacheType) {
        final CacheController<Object, Object> cache = controlCacheDispatcher.getCacheController(cacheType);
        return cache != null ? cache.size() : 0;
    }

    private Long retrieveRecordIdFromCache(final UUID tagId) {
        final CacheController<Object, Object> cache = controlCacheDispatcher.getCacheController(CacheType.RECORD_ID);
        Object result = null;
        if (cache != null) {
            // Keys are upper cased by convention
            result = cache.get(tagId.toString().toUpperCase(), new CacheLoaderArgument(ObjectType.TAG));
        }
        return (Long) result;
    }

    @Test(groups = "slow")
    public void testCacheRecordId() throws Exception {
        this.transactionalSqlDao = new EntitySqlDaoTransactionalJdbiWrapper(dbi, roDbi, clock, controlCacheDispatcher, nonEntityDao, internalCallContextFactory);
        final TagModelDao tag = new TagModelDao(clock.getUTCNow(), UUID.randomUUID(), UUID.randomUUID(), ObjectType.TAG);

        // Verify we start with nothing in the cache
        Assert.assertEquals(getCacheSize(CacheType.RECORD_ID), 0);
        insertTag(tag);

        // Verify we still have nothing after insert in the cache
        Assert.assertEquals(getCacheSize(CacheType.RECORD_ID), 0);

        final Long tagRecordId = getTagRecordId(tag.getId());
        // Verify we now have something  in the cache
        Assert.assertEquals(getCacheSize(CacheType.RECORD_ID), 1);

        final Long recordIdFromCache = retrieveRecordIdFromCache(tag.getId());
        Assert.assertNotNull(recordIdFromCache);

        Assert.assertEquals(recordIdFromCache, tagRecordId);
        // We cannot assume the number to be 1 here as the auto_increment implementation
        // depends on the database.
        // See also http://h2database.com/html/grammar.html#create_sequence
        Assert.assertTrue(recordIdFromCache > 0);

        Assert.assertEquals(getCacheSize(CacheType.RECORD_ID), 1);
    }

    @Test(groups = "slow")
    public void testAllCachesAfterGetById() throws Exception {
        this.transactionalSqlDao = new EntitySqlDaoTransactionalJdbiWrapper(dbi, roDbi, clock, controlCacheDispatcher, nonEntityDao, internalCallContextFactory);
        final TagModelDao tag = new TagModelDao(clock.getUTCNow(), UUID.randomUUID(), UUID.randomUUID(), ObjectType.TAG);

        insertTag(tag);

        // Verify we start with nothing in the cache
        Assert.assertEquals(getCacheSize(CacheType.RECORD_ID), 0);
        Assert.assertEquals(getCacheSize(CacheType.ACCOUNT_RECORD_ID), 0);
        Assert.assertEquals(getCacheSize(CacheType.TENANT_RECORD_ID), 0);
        Assert.assertEquals(getCacheSize(CacheType.OBJECT_ID), 0);

        final TagModelDao result = getById(tag.getId());

        Assert.assertEquals(getCacheSize(CacheType.RECORD_ID), 1);
        Assert.assertEquals(getCacheSize(CacheType.ACCOUNT_RECORD_ID), 1);
        Assert.assertEquals(getCacheSize(CacheType.TENANT_RECORD_ID), 1);
        Assert.assertEquals(getCacheSize(CacheType.OBJECT_ID), 1);

        final Long recordId = (Long) controlCacheDispatcher.getCacheController(CacheType.RECORD_ID).get(tag.getId().toString(), new CacheLoaderArgument(ObjectType.TAG));
        Assert.assertEquals(recordId, result.getRecordId());

        final Long tenantRecordId = (Long) controlCacheDispatcher.getCacheController(CacheType.TENANT_RECORD_ID).get(tag.getId().toString(), new CacheLoaderArgument(ObjectType.TAG));
        Assert.assertEquals(tenantRecordId, result.getTenantRecordId());

        final UUID objectId = (UUID) controlCacheDispatcher.getCacheController(CacheType.OBJECT_ID).get(TableName.TAG + CacheControllerDispatcher.CACHE_KEY_SEPARATOR  + recordId, new CacheLoaderArgument(ObjectType.TAG));
        Assert.assertEquals(objectId, result.getId());

        final Long accountRecordId = (Long) controlCacheDispatcher.getCacheController(CacheType.ACCOUNT_RECORD_ID).get(tag.getId().toString(), new CacheLoaderArgument(ObjectType.TAG));
        Assert.assertEquals(accountRecordId, result.getAccountRecordId());

    }

    private void insertTag(final TagModelDao modelDao) {
        transactionalSqlDao.execute(false, new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                entitySqlDaoWrapperFactory.become(TagSqlDao.class).create(modelDao, internalCallContext);
                return null;
            }
        });
    }

    private TagModelDao getById(final UUID id) {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<TagModelDao>() {
            @Override
            public TagModelDao inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(TagSqlDao.class).getById(id.toString(), internalCallContext);
            }
        });
    }

}
