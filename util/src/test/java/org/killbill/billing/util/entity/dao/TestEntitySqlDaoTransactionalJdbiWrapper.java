/*
 * Copyright 2017 Groupon, Inc
 * Copyright 2017 The Billing Project, LLC
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

package org.killbill.billing.util.entity.dao;

import java.util.UUID;

import org.killbill.billing.ObjectType;
import org.killbill.billing.util.UtilTestSuiteWithEmbeddedDB;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.killbill.billing.util.cache.CacheController;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.billing.util.tag.Tag;
import org.killbill.billing.util.tag.dao.TagModelDao;
import org.killbill.billing.util.tag.dao.TagSqlDao;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.Iterables;

public class TestEntitySqlDaoTransactionalJdbiWrapper extends UtilTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testCachingAndCommit() throws Exception {
        final CacheController<Object, Object> recordIdCacheController = controlCacheDispatcher.getCacheController(CacheType.RECORD_ID);
        final EntitySqlDaoTransactionalJdbiWrapper transactionalSqlDao = new EntitySqlDaoTransactionalJdbiWrapper(dbi,
                                                                                                                  databaseTransactionNotificationApi,
                                                                                                                  clock,
                                                                                                                  controlCacheDispatcher,
                                                                                                                  nonEntityDao,
                                                                                                                  internalCallContextFactory);

        final TagModelDao tagModelDao = new TagModelDao(UUID.randomUUID(), clock.getUTCNow(), clock.getUTCNow(), ControlTagType.AUTO_INVOICING_OFF.getId(), UUID.randomUUID(), ObjectType.ACCOUNT);

        Assert.assertEquals(Iterables.<Tag>size(tagUserApi.getTags(0L, 100L, callContext)), 0);
        Assert.assertFalse(recordIdCacheController.isKeyInCache(tagModelDao.getId().toString()));

        final Long tagRecordId = transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Long>() {
            @Override
            public Long inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final TagSqlDao tagSqlDao = entitySqlDaoWrapperFactory.become(TagSqlDao.class);

                final TagModelDao reHydratedTagModelDao = (TagModelDao) tagSqlDao.create(tagModelDao, internalCallContext);
                Assert.assertNotNull(reHydratedTagModelDao);

                // Trigger a "get" call and verify the mapping isn't cached yet
                //noinspection unused
                final TagModelDao reHydratedTagModelDao2 = tagSqlDao.getById(tagModelDao.getId().toString(), internalCallContext);
                Assert.assertFalse(recordIdCacheController.isKeyInCache(tagModelDao.getId().toString()));

                return reHydratedTagModelDao.getRecordId();
            }
        });

        Assert.assertEquals(Iterables.<Tag>size(tagUserApi.getTags(0L, 100L, callContext)), 1);
        Assert.assertTrue(recordIdCacheController.isKeyInCache(tagModelDao.getId().toString()));
        Assert.assertEquals(recordIdCacheController.get(tagModelDao.getId().toString(), null), tagRecordId);
    }

    @Test(groups = "slow")
    public void testCachingAndRollback() throws Exception {
        final CacheController<Object, Object> recordIdCacheController = controlCacheDispatcher.getCacheController(CacheType.RECORD_ID);
        final EntitySqlDaoTransactionalJdbiWrapper transactionalSqlDao = new EntitySqlDaoTransactionalJdbiWrapper(dbi,
                                                                                                                  databaseTransactionNotificationApi,
                                                                                                                  clock,
                                                                                                                  controlCacheDispatcher,
                                                                                                                  nonEntityDao,
                                                                                                                  internalCallContextFactory);

        final TagModelDao tagModelDao = new TagModelDao(UUID.randomUUID(), clock.getUTCNow(), clock.getUTCNow(), ControlTagType.AUTO_INVOICING_OFF.getId(), UUID.randomUUID(), ObjectType.ACCOUNT);

        Assert.assertEquals(Iterables.<Tag>size(tagUserApi.getTags(0L, 100L, callContext)), 0);
        Assert.assertFalse(recordIdCacheController.isKeyInCache(tagModelDao.getId().toString()));

        try {
            transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {
                @Override
                public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                    final TagSqlDao tagSqlDao = entitySqlDaoWrapperFactory.become(TagSqlDao.class);

                    final TagModelDao reHydratedTagModelDao = (TagModelDao) tagSqlDao.create(tagModelDao, internalCallContext);
                    Assert.assertNotNull(reHydratedTagModelDao);

                    // Trigger a "get" call and verify the mapping isn't cached
                    //noinspection unused
                    final TagModelDao reHydratedTagModelDao2 = tagSqlDao.getById(tagModelDao.getId().toString(), internalCallContext);
                    Assert.assertFalse(recordIdCacheController.isKeyInCache(tagModelDao.getId().toString()));

                    throw new RuntimeException("expected");
                }
            });
            Assert.fail();
        } catch (final Exception e) {
            Assert.assertEquals(e.getMessage(), "expected");
        }

        Assert.assertEquals(Iterables.<Tag>size(tagUserApi.getTags(0L, 100L, callContext)), 0);
        Assert.assertFalse(recordIdCacheController.isKeyInCache(tagModelDao.getId().toString()));
    }
}
