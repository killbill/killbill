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

package com.ning.billing.analytics.dao;

import java.util.List;
import java.util.UUID;

import org.skife.jdbi.v2.IDBI;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.analytics.AnalyticsTestSuiteWithEmbeddedDB;
import com.ning.billing.analytics.model.BusinessSubscriptionTransitionFieldModelDao;

public class TestBusinessSubscriptionTransitionFieldSqlDao extends AnalyticsTestSuiteWithEmbeddedDB {

    private BusinessSubscriptionTransitionFieldSqlDao subscriptionTransitionFieldSqlDao;

    @BeforeMethod(groups = "slow")
    public void setUp() throws Exception {
        final IDBI dbi = helper.getDBI();
        subscriptionTransitionFieldSqlDao = dbi.onDemand(BusinessSubscriptionTransitionFieldSqlDao.class);
    }

    @Test(groups = "slow")
    public void testCRUD() throws Exception {
        final String accountKey = UUID.randomUUID().toString();
        final UUID bundleId = UUID.randomUUID();
        final String externalKey = UUID.randomUUID().toString();
        final String name = UUID.randomUUID().toString().substring(0, 30);
        final String value = UUID.randomUUID().toString();

        // Verify initial state
        Assert.assertEquals(subscriptionTransitionFieldSqlDao.getFieldsForBusinessSubscriptionTransitionByKey(externalKey, internalCallContext).size(), 0);
        Assert.assertEquals(subscriptionTransitionFieldSqlDao.removeField(bundleId.toString(), name, internalCallContext), 0);

        // Add an entry
        Assert.assertEquals(subscriptionTransitionFieldSqlDao.addField(accountKey, bundleId.toString(), externalKey, name, value, internalCallContext), 1);
        final List<BusinessSubscriptionTransitionFieldModelDao> fieldsForBusinessSubscriptionTransition = subscriptionTransitionFieldSqlDao.getFieldsForBusinessSubscriptionTransitionByKey(externalKey, internalCallContext);
        Assert.assertEquals(fieldsForBusinessSubscriptionTransition.size(), 1);

        // Retrieve it
        final BusinessSubscriptionTransitionFieldModelDao subscriptionTransitionField = fieldsForBusinessSubscriptionTransition.get(0);
        Assert.assertEquals(subscriptionTransitionField.getBundleId(), bundleId);
        Assert.assertEquals(subscriptionTransitionField.getExternalKey(), externalKey);
        Assert.assertEquals(subscriptionTransitionField.getName(), name);
        Assert.assertEquals(subscriptionTransitionField.getValue(), value);

        // Delete it
        Assert.assertEquals(subscriptionTransitionFieldSqlDao.removeField(bundleId.toString(), name, internalCallContext), 1);
        Assert.assertEquals(subscriptionTransitionFieldSqlDao.getFieldsForBusinessSubscriptionTransitionByKey(externalKey, internalCallContext).size(), 0);
    }

    @Test(groups = "slow")
    public void testSegmentation() throws Exception {
        final String accountKey = UUID.randomUUID().toString();
        final UUID bundleId1 = UUID.randomUUID();
        final String externalKey1 = UUID.randomUUID().toString();
        final String name1 = UUID.randomUUID().toString().substring(0, 30);
        final UUID bundleId2 = UUID.randomUUID();
        final String externalKey2 = UUID.randomUUID().toString();
        final String name2 = UUID.randomUUID().toString().substring(0, 30);

        // Add a field to both transitions
        Assert.assertEquals(subscriptionTransitionFieldSqlDao.addField(accountKey, bundleId1.toString(), externalKey1, name1, UUID.randomUUID().toString(), internalCallContext), 1);
        Assert.assertEquals(subscriptionTransitionFieldSqlDao.addField(accountKey, bundleId2.toString(), externalKey2, name2, UUID.randomUUID().toString(), internalCallContext), 1);

        Assert.assertEquals(subscriptionTransitionFieldSqlDao.getFieldsForBusinessSubscriptionTransitionByKey(externalKey1, internalCallContext).size(), 1);
        Assert.assertEquals(subscriptionTransitionFieldSqlDao.getFieldsForBusinessSubscriptionTransitionByKey(externalKey2, internalCallContext).size(), 1);

        // Remove the field for the first transition
        Assert.assertEquals(subscriptionTransitionFieldSqlDao.removeField(bundleId1.toString(), name1, internalCallContext), 1);

        Assert.assertEquals(subscriptionTransitionFieldSqlDao.getFieldsForBusinessSubscriptionTransitionByKey(externalKey1, internalCallContext).size(), 0);
        Assert.assertEquals(subscriptionTransitionFieldSqlDao.getFieldsForBusinessSubscriptionTransitionByKey(externalKey2, internalCallContext).size(), 1);
    }

    @Test(groups = "slow")
    public void testHealthCheck() throws Exception {
        // HealthCheck test to make sure MySQL is setup properly
        try {
            subscriptionTransitionFieldSqlDao.test(internalCallContext);
        } catch (Throwable t) {
            Assert.fail(t.toString());
        }
    }
}
