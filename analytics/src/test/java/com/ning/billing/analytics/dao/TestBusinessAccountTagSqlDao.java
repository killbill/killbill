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

package com.ning.billing.analytics.dao;

import java.util.List;
import java.util.UUID;

import org.skife.jdbi.v2.IDBI;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.analytics.AnalyticsTestSuiteWithEmbeddedDB;
import com.ning.billing.analytics.model.BusinessAccountTagModelDao;

public class TestBusinessAccountTagSqlDao extends AnalyticsTestSuiteWithEmbeddedDB {

    private BusinessAccountTagSqlDao accountTagSqlDao;

    @BeforeMethod(groups = "slow")
    public void setUp() throws Exception {
        final IDBI dbi = helper.getDBI();
        accountTagSqlDao = dbi.onDemand(BusinessAccountTagSqlDao.class);
    }

    @Test(groups = "slow")
    public void testCRUD() throws Exception {
        final UUID accountId = UUID.randomUUID();
        final String accountKey = UUID.randomUUID().toString();
        final String name = UUID.randomUUID().toString().substring(0, 20);

        // Verify initial state
        Assert.assertEquals(accountTagSqlDao.getTagsForAccountByKey(accountKey, internalCallContext).size(), 0);
        Assert.assertEquals(accountTagSqlDao.removeTag(accountId.toString(), name, internalCallContext), 0);

        // Add an entry
        Assert.assertEquals(accountTagSqlDao.addTag(accountId.toString(), accountKey, name, internalCallContext), 1);
        final List<BusinessAccountTagModelDao> tagsForAccount = accountTagSqlDao.getTagsForAccountByKey(accountKey, internalCallContext);
        Assert.assertEquals(tagsForAccount.size(), 1);

        // Retrieve it
        final BusinessAccountTagModelDao accountTag = tagsForAccount.get(0);
        Assert.assertEquals(accountTag.getAccountId(), accountId);
        Assert.assertEquals(accountTag.getAccountKey(), accountKey);
        Assert.assertEquals(accountTag.getName(), name);

        // Delete it
        Assert.assertEquals(accountTagSqlDao.removeTag(accountId.toString(), name, internalCallContext), 1);
        Assert.assertEquals(accountTagSqlDao.getTagsForAccountByKey(accountKey, internalCallContext).size(), 0);
    }

    @Test(groups = "slow")
    public void testSegmentation() throws Exception {
        final UUID accountId1 = UUID.randomUUID();
        final String accountKey1 = UUID.randomUUID().toString();
        final String name1 = UUID.randomUUID().toString().substring(0, 20);
        final UUID accountId2 = UUID.randomUUID();
        final String accountKey2 = UUID.randomUUID().toString();
        final String name2 = UUID.randomUUID().toString().substring(0, 20);

        // Add a tag to both accounts
        Assert.assertEquals(accountTagSqlDao.addTag(accountId1.toString(), accountKey1, name1, internalCallContext), 1);
        Assert.assertEquals(accountTagSqlDao.addTag(accountId2.toString(), accountKey2, name2, internalCallContext), 1);

        Assert.assertEquals(accountTagSqlDao.getTagsForAccountByKey(accountKey1, internalCallContext).size(), 1);
        Assert.assertEquals(accountTagSqlDao.getTagsForAccountByKey(accountKey2, internalCallContext).size(), 1);

        // Remove the tag for the first account
        Assert.assertEquals(accountTagSqlDao.removeTag(accountId1.toString(), name1, internalCallContext), 1);

        Assert.assertEquals(accountTagSqlDao.getTagsForAccountByKey(accountKey1, internalCallContext).size(), 0);
        Assert.assertEquals(accountTagSqlDao.getTagsForAccountByKey(accountKey2, internalCallContext).size(), 1);
    }

    @Test(groups = "slow")
    public void testHealthCheck() throws Exception {
        // HealthCheck test to make sure MySQL is setup properly
        try {
            accountTagSqlDao.test(internalCallContext);
        } catch (Throwable t) {
            Assert.fail(t.toString());
        }
    }
}
