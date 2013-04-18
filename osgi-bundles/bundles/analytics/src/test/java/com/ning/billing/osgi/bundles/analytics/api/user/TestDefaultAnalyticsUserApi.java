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

package com.ning.billing.osgi.bundles.analytics.api.user;

import java.math.BigDecimal;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.osgi.bundles.analytics.AnalyticsTestSuiteWithEmbeddedDB;
import com.ning.billing.osgi.bundles.analytics.api.BusinessAccount;
import com.ning.billing.osgi.bundles.analytics.api.BusinessSnapshot;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessAccountModelDao;

public class TestDefaultAnalyticsUserApi extends AnalyticsTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testAccountSnapshot() throws Exception {
        final BusinessAccountModelDao accountModelDao = new BusinessAccountModelDao(account,
                                                                                    accountRecordId,
                                                                                    BigDecimal.ONE,
                                                                                    invoice,
                                                                                    payment,
                                                                                    3,
                                                                                    auditLog,
                                                                                    tenantRecordId,
                                                                                    reportGroup);
        analyticsSqlDao.create(accountModelDao.getTableName(), accountModelDao, callContext);

        final AnalyticsUserApi analyticsUserApi = new AnalyticsUserApi(logService, killbillAPI, killbillDataSource);
        final BusinessSnapshot businessSnapshot = analyticsUserApi.getBusinessSnapshot(account.getId(), callContext);
        Assert.assertEquals(businessSnapshot.getBusinessAccount(), new BusinessAccount(accountModelDao));
    }
}
