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
import com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;
import com.ning.billing.osgi.bundles.analytics.AnalyticsTestSuiteNoDB;

public class TestBusinessSubscriptionTransitionModelDao extends AnalyticsTestSuiteNoDB {

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
                                                                                                                                 tenantRecordId);
        verifyBusinessModelDaoBase(subscriptionTransitionModelDao, accountRecordId, tenantRecordId);
        Assert.assertEquals(subscriptionTransitionModelDao.getCreatedDate(), subscriptionTransition.getNextEventCreatedDate());
        Assert.assertEquals(subscriptionTransitionModelDao.getSubscriptionEventRecordId(), subscriptionEventRecordId);
        Assert.assertEquals(subscriptionTransitionModelDao.getBundleId(), bundle.getId());
        Assert.assertEquals(subscriptionTransitionModelDao.getBundleExternalKey(), bundle.getExternalKey());
        Assert.assertEquals(subscriptionTransitionModelDao.getSubscriptionId(), subscriptionTransition.getSubscriptionId());
        Assert.assertEquals(subscriptionTransitionModelDao.getRequestedTimestamp(), requestedTimestamp);
        Assert.assertEquals(subscriptionTransitionModelDao.getEventType(), event.getEventType().toString());
        Assert.assertEquals(subscriptionTransitionModelDao.getCategory(), event.getCategory().toString());

        Assert.assertNull(subscriptionTransitionModelDao.getPrevProductName());
        Assert.assertNull(subscriptionTransitionModelDao.getPrevProductType());
        Assert.assertNull(subscriptionTransitionModelDao.getPrevProductCategory());
        Assert.assertNull(subscriptionTransitionModelDao.getPrevSlug());
        Assert.assertNull(subscriptionTransitionModelDao.getPrevPhase());
        Assert.assertNull(subscriptionTransitionModelDao.getPrevBillingPeriod());
        Assert.assertNull(subscriptionTransitionModelDao.getPrevPrice());
        Assert.assertNull(subscriptionTransitionModelDao.getPrevPriceList());
        Assert.assertNull(subscriptionTransitionModelDao.getPrevMrr());
        Assert.assertNull(subscriptionTransitionModelDao.getPrevCurrency());
        Assert.assertNull(subscriptionTransitionModelDao.getPrevBusinessActive());
        Assert.assertNull(subscriptionTransitionModelDao.getPrevStartDate());
        Assert.assertNull(subscriptionTransitionModelDao.getPrevState());

        Assert.assertEquals(subscriptionTransitionModelDao.getNextProductName(), subscriptionTransitionModelDao.getNextProductName());
        Assert.assertEquals(subscriptionTransitionModelDao.getNextProductType(), subscriptionTransitionModelDao.getNextProductType());
        Assert.assertEquals(subscriptionTransitionModelDao.getNextProductCategory(), subscriptionTransitionModelDao.getNextProductCategory());
        Assert.assertEquals(subscriptionTransitionModelDao.getNextSlug(), subscriptionTransitionModelDao.getNextSlug());
        Assert.assertEquals(subscriptionTransitionModelDao.getNextPhase(), subscriptionTransitionModelDao.getNextPhase());
        Assert.assertEquals(subscriptionTransitionModelDao.getNextBillingPeriod(), subscriptionTransitionModelDao.getNextBillingPeriod());
        Assert.assertEquals(subscriptionTransitionModelDao.getNextPrice(), subscriptionTransitionModelDao.getNextPrice());
        Assert.assertEquals(subscriptionTransitionModelDao.getNextPriceList(), subscriptionTransitionModelDao.getNextPriceList());
        Assert.assertEquals(subscriptionTransitionModelDao.getNextMrr(), subscriptionTransitionModelDao.getNextMrr());
        Assert.assertEquals(subscriptionTransitionModelDao.getNextCurrency(), subscriptionTransitionModelDao.getNextCurrency());
        Assert.assertEquals(subscriptionTransitionModelDao.getNextBusinessActive(), subscriptionTransitionModelDao.getNextBusinessActive());
        Assert.assertEquals(subscriptionTransitionModelDao.getNextStartDate(), subscriptionTransitionModelDao.getNextStartDate());
        Assert.assertEquals(subscriptionTransitionModelDao.getNextEndDate(), subscriptionTransitionModelDao.getNextEndDate());
        Assert.assertEquals(subscriptionTransitionModelDao.getNextState(), subscriptionTransitionModelDao.getNextState());
    }
}
