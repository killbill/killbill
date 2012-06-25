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
import com.ning.billing.analytics.model.BusinessSubscriptionTransitionField;

public class TestBusinessSubscriptionTransitionFieldSqlDao extends TestWithEmbeddedDB {
    private BusinessSubscriptionTransitionFieldSqlDao subscriptionTransitionFieldSqlDao;

    @BeforeMethod(groups = "slow")
    public void setUp() throws Exception {
        final IDBI dbi = helper.getDBI();
        subscriptionTransitionFieldSqlDao = dbi.onDemand(BusinessSubscriptionTransitionFieldSqlDao.class);
    }

    @Test(groups = "slow")
    public void testCRUD() throws Exception {
        final String externalKey = UUID.randomUUID().toString();
        final String name = UUID.randomUUID().toString().substring(0, 30);
        final String value = UUID.randomUUID().toString();

        // Verify initial state
        Assert.assertEquals(subscriptionTransitionFieldSqlDao.getFieldsForBusinessSubscriptionTransition(externalKey).size(), 0);
        Assert.assertEquals(subscriptionTransitionFieldSqlDao.removeField(externalKey, name), 0);

        // Add an entry
        Assert.assertEquals(subscriptionTransitionFieldSqlDao.addField(externalKey, name, value), 1);
        final List<BusinessSubscriptionTransitionField> fieldsForBusinessSubscriptionTransition = subscriptionTransitionFieldSqlDao.getFieldsForBusinessSubscriptionTransition(externalKey);
        Assert.assertEquals(fieldsForBusinessSubscriptionTransition.size(), 1);

        // Retrieve it
        final BusinessSubscriptionTransitionField subscriptionTransitionField = fieldsForBusinessSubscriptionTransition.get(0);
        Assert.assertEquals(subscriptionTransitionField.getExternalKey(), externalKey);
        Assert.assertEquals(subscriptionTransitionField.getName(), name);
        Assert.assertEquals(subscriptionTransitionField.getValue(), value);

        // Delete it
        Assert.assertEquals(subscriptionTransitionFieldSqlDao.removeField(externalKey, name), 1);
        Assert.assertEquals(subscriptionTransitionFieldSqlDao.getFieldsForBusinessSubscriptionTransition(externalKey).size(), 0);
    }

    @Test(groups = "slow")
    public void testSegmentation() throws Exception {
        final String externalKey1 = UUID.randomUUID().toString();
        final String name1 = UUID.randomUUID().toString().substring(0, 30);
        final String externalKey2 = UUID.randomUUID().toString();
        final String name2 = UUID.randomUUID().toString().substring(0, 30);

        // Add a field to both transitions
        Assert.assertEquals(subscriptionTransitionFieldSqlDao.addField(externalKey1, name1, UUID.randomUUID().toString()), 1);
        Assert.assertEquals(subscriptionTransitionFieldSqlDao.addField(externalKey2, name2, UUID.randomUUID().toString()), 1);

        Assert.assertEquals(subscriptionTransitionFieldSqlDao.getFieldsForBusinessSubscriptionTransition(externalKey1).size(), 1);
        Assert.assertEquals(subscriptionTransitionFieldSqlDao.getFieldsForBusinessSubscriptionTransition(externalKey2).size(), 1);

        // Remove the field for the first transition
        Assert.assertEquals(subscriptionTransitionFieldSqlDao.removeField(externalKey1, name1), 1);

        Assert.assertEquals(subscriptionTransitionFieldSqlDao.getFieldsForBusinessSubscriptionTransition(externalKey1).size(), 0);
        Assert.assertEquals(subscriptionTransitionFieldSqlDao.getFieldsForBusinessSubscriptionTransition(externalKey2).size(), 1);
    }

    @Test(groups = "slow")
    public void testHealthCheck() throws Exception {
        // HealthCheck test to make sure MySQL is setup properly
        try {
            subscriptionTransitionFieldSqlDao.test();
        } catch (Throwable t) {
            Assert.fail(t.toString());
        }
    }
}
