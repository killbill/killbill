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

package com.ning.billing.analytics.model;

import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.analytics.AnalyticsTestSuite;

public class TestBusinessOverdueStatus extends AnalyticsTestSuite {

    @Test(groups = "fast")
    public void testEquals() throws Exception {
        final String accountKey = UUID.randomUUID().toString();
        final UUID bundleId = UUID.randomUUID();
        final DateTime endDate = new DateTime(DateTimeZone.UTC);
        final String externalKey = UUID.randomUUID().toString();
        final DateTime startDate = new DateTime(DateTimeZone.UTC);
        final String status = UUID.randomUUID().toString();
        final BusinessOverdueStatusModelDao overdueStatus = new BusinessOverdueStatusModelDao(accountKey, bundleId, endDate, externalKey, startDate, status);
        Assert.assertSame(overdueStatus, overdueStatus);
        Assert.assertEquals(overdueStatus, overdueStatus);
        Assert.assertTrue(overdueStatus.equals(overdueStatus));
        Assert.assertEquals(overdueStatus.getAccountKey(), accountKey);
        Assert.assertEquals(overdueStatus.getBundleId(), bundleId);
        Assert.assertEquals(overdueStatus.getEndDate(), endDate);
        Assert.assertEquals(overdueStatus.getExternalKey(), externalKey);
        Assert.assertEquals(overdueStatus.getStartDate(), startDate);
        Assert.assertEquals(overdueStatus.getStatus(), status);

        final BusinessOverdueStatusModelDao otherOverdueStatus = new BusinessOverdueStatusModelDao(UUID.randomUUID().toString(),
                                                                                                   UUID.randomUUID(),
                                                                                                   new DateTime(DateTimeZone.UTC),
                                                                                                   UUID.randomUUID().toString(),
                                                                                                   new DateTime(DateTimeZone.UTC),
                                                                                                   UUID.randomUUID().toString());
        Assert.assertFalse(overdueStatus.equals(otherOverdueStatus));
    }
}
