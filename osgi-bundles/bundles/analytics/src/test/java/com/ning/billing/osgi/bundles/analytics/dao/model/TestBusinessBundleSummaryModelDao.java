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

package com.ning.billing.osgi.bundles.analytics.dao.model;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.entitlement.api.user.SubscriptionState;
import com.ning.billing.osgi.bundles.analytics.AnalyticsTestSuiteNoDB;

public class TestBusinessBundleSummaryModelDao extends AnalyticsTestSuiteNoDB {

    @Test(groups = "fast")
    public void testConstructor() throws Exception {
        final DateTime startDate = new DateTime(2012, 6, 5, 4, 3, 12, DateTimeZone.UTC);
        final DateTime requestedTimestamp = new DateTime(2012, 7, 21, 10, 10, 10, DateTimeZone.UTC);

        final BusinessSubscriptionEvent event = BusinessSubscriptionEvent.valueOf("ADD_BASE");
        final BusinessSubscription previousSubscription = null;
        final BusinessSubscription nextSubscription = new BusinessSubscription(null, null, null, Currency.GBP, startDate, SubscriptionState.ACTIVE);
        final BusinessSubscriptionTransitionModelDao subscriptionTransitionModelDao = new BusinessSubscriptionTransitionModelDao(account,
                                                                                                                                 accountRecordId,
                                                                                                                                 bundle,
                                                                                                                                 subscriptionTransition,
                                                                                                                                 subscriptionEventRecordId,
                                                                                                                                 requestedTimestamp,
                                                                                                                                 event,
                                                                                                                                 previousSubscription,
                                                                                                                                 nextSubscription,
                                                                                                                                 auditLog,
                                                                                                                                 tenantRecordId,
                                                                                                                                 reportGroup);

        final BusinessBundleSummaryModelDao bundleSummaryModelDao = new BusinessBundleSummaryModelDao(account,
                                                                                                      accountRecordId,
                                                                                                      bundle,
                                                                                                      bundleRecordId,
                                                                                                      3,
                                                                                                      subscriptionTransitionModelDao,
                                                                                                      auditLog,
                                                                                                      tenantRecordId,
                                                                                                      reportGroup);
        verifyBusinessModelDaoBase(bundleSummaryModelDao, accountRecordId, tenantRecordId);
        Assert.assertEquals(bundleSummaryModelDao.getBundleRecordId(), bundleRecordId);
        Assert.assertEquals(bundleSummaryModelDao.getBundleId(), bundle.getId());
        Assert.assertEquals(bundleSummaryModelDao.getBundleExternalKey(), bundle.getExternalKey());
        Assert.assertEquals(bundleSummaryModelDao.getSubscriptionId(), subscriptionTransition.getSubscriptionId());
        Assert.assertEquals(bundleSummaryModelDao.getBundleAccountRank(), (Integer) 3);
        Assert.assertEquals(bundleSummaryModelDao.getCurrentProductName(), subscriptionTransitionModelDao.getNextProductName());
        Assert.assertEquals(bundleSummaryModelDao.getCurrentProductType(), subscriptionTransitionModelDao.getNextProductType());
        Assert.assertEquals(bundleSummaryModelDao.getCurrentProductCategory(), subscriptionTransitionModelDao.getNextProductCategory());
        Assert.assertEquals(bundleSummaryModelDao.getCurrentSlug(), subscriptionTransitionModelDao.getNextSlug());
        Assert.assertEquals(bundleSummaryModelDao.getCurrentPhase(), subscriptionTransitionModelDao.getNextPhase());
        Assert.assertEquals(bundleSummaryModelDao.getCurrentBillingPeriod(), subscriptionTransitionModelDao.getNextBillingPeriod());
        Assert.assertEquals(bundleSummaryModelDao.getCurrentPrice(), subscriptionTransitionModelDao.getNextPrice());
        Assert.assertEquals(bundleSummaryModelDao.getCurrentPriceList(), subscriptionTransitionModelDao.getNextPriceList());
        Assert.assertEquals(bundleSummaryModelDao.getCurrentMrr(), subscriptionTransitionModelDao.getNextMrr());
        Assert.assertEquals(bundleSummaryModelDao.getCurrentCurrency(), subscriptionTransitionModelDao.getNextCurrency());
        Assert.assertEquals(bundleSummaryModelDao.getCurrentBusinessActive(), subscriptionTransitionModelDao.getNextBusinessActive());
        Assert.assertEquals(bundleSummaryModelDao.getCurrentStartDate(), subscriptionTransitionModelDao.getNextStartDate());
        Assert.assertEquals(bundleSummaryModelDao.getCurrentEndDate(), subscriptionTransitionModelDao.getNextEndDate());
        Assert.assertEquals(bundleSummaryModelDao.getCurrentState(), subscriptionTransitionModelDao.getNextState());
    }
}
