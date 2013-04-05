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

package com.ning.billing.osgi.bundles.analytics.api;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.ObjectType;
import com.ning.billing.osgi.bundles.analytics.AnalyticsTestSuiteNoDB;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessOverdueStatusModelDao;

public class TestBusinessOverdueStatus extends AnalyticsTestSuiteNoDB {

    @Test(groups = "fast")
    public void testConstructor() throws Exception {
        final DateTime endDate = new DateTime(2005, 6, 5, 4, 5, 6, DateTimeZone.UTC);
        final BusinessOverdueStatusModelDao businessOverdueStatusModelDao = new BusinessOverdueStatusModelDao(account,
                                                                                                              accountRecordId,
                                                                                                              bundle,
                                                                                                              blockingState,
                                                                                                              blockingStateRecordId,
                                                                                                              endDate,
                                                                                                              auditLog,
                                                                                                              tenantRecordId);
        final BusinessOverdueStatus businessOverdueStatus = new BusinessOverdueStatus(businessOverdueStatusModelDao);
        verifyBusinessEntityBase(businessOverdueStatus);
        Assert.assertEquals(businessOverdueStatus.getObjectType(), ObjectType.BUNDLE.toString());
        Assert.assertEquals(businessOverdueStatus.getStatus(), businessOverdueStatusModelDao.getStatus());
        Assert.assertEquals(businessOverdueStatus.getStartDate(), businessOverdueStatusModelDao.getStartDate());
        Assert.assertEquals(businessOverdueStatus.getEndDate(), businessOverdueStatusModelDao.getEndDate());
    }
}
