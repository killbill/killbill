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

import com.ning.billing.analytics.TestWithEmbeddedDB;
import com.ning.billing.analytics.model.BusinessAccountField;

public class TestBusinessAccountFieldSqlDao extends TestWithEmbeddedDB {
    private BusinessAccountFieldSqlDao accountFieldSqlDao;

    @BeforeMethod(groups = "slow")
    public void setUp() throws Exception {
        final IDBI dbi = helper.getDBI();
        accountFieldSqlDao = dbi.onDemand(BusinessAccountFieldSqlDao.class);
    }

    @Test(groups = "slow")
    public void testCRUD() throws Exception {
        final UUID accountId = UUID.randomUUID();
        final String accountKey = UUID.randomUUID().toString();
        final String name = UUID.randomUUID().toString().substring(0, 30);
        final String value = UUID.randomUUID().toString();

        // Verify initial state
        Assert.assertEquals(accountFieldSqlDao.getFieldsForAccountByKey(accountKey).size(), 0);
        Assert.assertEquals(accountFieldSqlDao.removeField(accountId.toString(), name), 0);

        // Add an entry
        Assert.assertEquals(accountFieldSqlDao.addField(accountId.toString(), accountKey, name, value), 1);
        final List<BusinessAccountField> fieldsForAccount = accountFieldSqlDao.getFieldsForAccountByKey(accountKey);
        Assert.assertEquals(fieldsForAccount.size(), 1);

        // Retrieve it
        final BusinessAccountField accountField = fieldsForAccount.get(0);
        Assert.assertEquals(accountField.getAccountId(), accountId);
        Assert.assertEquals(accountField.getAccountKey(), accountKey);
        Assert.assertEquals(accountField.getName(), name);
        Assert.assertEquals(accountField.getValue(), value);

        // Delete it
        Assert.assertEquals(accountFieldSqlDao.removeField(accountId.toString(), name), 1);
        Assert.assertEquals(accountFieldSqlDao.getFieldsForAccountByKey(accountKey).size(), 0);
    }

    @Test(groups = "slow")
    public void testSegmentation() throws Exception {
        final UUID accountId1 = UUID.randomUUID();
        final String accountKey1 = UUID.randomUUID().toString();
        final String name1 = UUID.randomUUID().toString().substring(0, 30);
        final UUID accountId2 = UUID.randomUUID();
        final String accountKey2 = UUID.randomUUID().toString();
        final String name2 = UUID.randomUUID().toString().substring(0, 30);

        // Add a field to both accounts
        Assert.assertEquals(accountFieldSqlDao.addField(accountId1.toString(), accountKey1, name1, UUID.randomUUID().toString()), 1);
        Assert.assertEquals(accountFieldSqlDao.addField(accountId2.toString(), accountKey2, name2, UUID.randomUUID().toString()), 1);

        Assert.assertEquals(accountFieldSqlDao.getFieldsForAccountByKey(accountKey1).size(), 1);
        Assert.assertEquals(accountFieldSqlDao.getFieldsForAccountByKey(accountKey2).size(), 1);

        // Remove the field for the first account
        Assert.assertEquals(accountFieldSqlDao.removeField(accountId1.toString(), name1), 1);

        Assert.assertEquals(accountFieldSqlDao.getFieldsForAccountByKey(accountKey1).size(), 0);
        Assert.assertEquals(accountFieldSqlDao.getFieldsForAccountByKey(accountKey2).size(), 1);
    }

    @Test(groups = "slow")
    public void testHealthCheck() throws Exception {
        // HealthCheck test to make sure MySQL is setup properly
        try {
            accountFieldSqlDao.test();
        } catch (Throwable t) {
            Assert.fail(t.toString());
        }
    }
}
