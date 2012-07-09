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

import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.IDBI;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.analytics.AnalyticsTestSuiteWithEmbeddedDB;
import com.ning.billing.analytics.model.BusinessOverdueStatus;

public class TestBusinessOverdueStatusSqlDao extends AnalyticsTestSuiteWithEmbeddedDB {
    private BusinessOverdueStatusSqlDao overdueStatusSqlDao;

    @BeforeMethod(groups = "slow")
    public void setUp() throws Exception {
        final IDBI dbi = helper.getDBI();
        overdueStatusSqlDao = dbi.onDemand(BusinessOverdueStatusSqlDao.class);
    }

    @Test(groups = "slow")
    public void testCreate() throws Exception {
        final String accountKey = UUID.randomUUID().toString();
        final UUID bundleId = UUID.randomUUID();
        final String externalKey = UUID.randomUUID().toString();
        final BusinessOverdueStatus firstOverdueStatus = createOverdueStatus(accountKey, bundleId, externalKey);

        // Verify initial state
        Assert.assertEquals(overdueStatusSqlDao.getOverdueStatusesForBundleByKey(externalKey).size(), 0);

        // Add the overdue status
        Assert.assertEquals(overdueStatusSqlDao.createOverdueStatus(firstOverdueStatus), 1);

        // Retrieve it
        Assert.assertEquals(overdueStatusSqlDao.getOverdueStatusesForBundleByKey(externalKey).size(), 1);
        Assert.assertEquals(overdueStatusSqlDao.getOverdueStatusesForBundleByKey(externalKey).get(0), firstOverdueStatus);

        // Add a second one
        final BusinessOverdueStatus secondOverdueStatus = createOverdueStatus(accountKey, bundleId, externalKey);
        Assert.assertEquals(overdueStatusSqlDao.createOverdueStatus(secondOverdueStatus), 1);

        // Retrieve both
        Assert.assertEquals(overdueStatusSqlDao.getOverdueStatusesForBundleByKey(externalKey).size(), 2);
        Assert.assertEquals(overdueStatusSqlDao.getOverdueStatusesForBundleByKey(externalKey).get(0), firstOverdueStatus);
        Assert.assertEquals(overdueStatusSqlDao.getOverdueStatusesForBundleByKey(externalKey).get(1), secondOverdueStatus);
    }

    @Test(groups = "slow")
    public void testHealthCheck() throws Exception {
        // HealthCheck test to make sure MySQL is setup properly
        try {
            overdueStatusSqlDao.test();
        } catch (Throwable t) {
            Assert.fail(t.toString());
        }
    }

    private BusinessOverdueStatus createOverdueStatus(final String accountKey, final UUID bundleId, final String externalKey) {
        final DateTime endDate = new DateTime(DateTimeZone.UTC);
        final DateTime startDate = new DateTime(DateTimeZone.UTC);
        final String status = UUID.randomUUID().toString();

        return new BusinessOverdueStatus(accountKey, bundleId, endDate, externalKey, startDate, status);
    }
}
