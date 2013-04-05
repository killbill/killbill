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

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;
import com.ning.billing.osgi.bundles.analytics.AnalyticsTestSuiteNoDB;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessSubscription;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessSubscriptionEvent;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessSubscriptionTransitionModelDao;

public class TestBusinessSubscriptionTransition extends AnalyticsTestSuiteNoDB {

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
        final BusinessSubscriptionTransition businessSubscriptionTransition = new BusinessSubscriptionTransition(subscriptionTransitionModelDao);

        verifyBusinessEntityBase(businessSubscriptionTransition);
        Assert.assertEquals(businessSubscriptionTransition.getBundleId(), subscriptionTransitionModelDao.getBundleId());
        Assert.assertEquals(businessSubscriptionTransition.getBundleExternalKey(), subscriptionTransitionModelDao.getBundleExternalKey());
        Assert.assertEquals(businessSubscriptionTransition.getSubscriptionId(), subscriptionTransitionModelDao.getSubscriptionId());
        Assert.assertEquals(businessSubscriptionTransition.getRequestedTimestamp(), subscriptionTransitionModelDao.getRequestedTimestamp());
        Assert.assertEquals(businessSubscriptionTransition.getEventType(), subscriptionTransitionModelDao.getEvent().getEventType().toString());
        Assert.assertEquals(businessSubscriptionTransition.getCategory(), subscriptionTransitionModelDao.getEvent().getCategory().toString());

        Assert.assertNull(businessSubscriptionTransition.getPrevProductName());
        Assert.assertNull(businessSubscriptionTransition.getPrevProductType());
        Assert.assertNull(businessSubscriptionTransition.getPrevProductCategory());
        Assert.assertNull(businessSubscriptionTransition.getPrevSlug());
        Assert.assertNull(businessSubscriptionTransition.getPrevPhase());
        Assert.assertNull(businessSubscriptionTransition.getPrevBillingPeriod());
        Assert.assertNull(businessSubscriptionTransition.getPrevPrice());
        Assert.assertNull(businessSubscriptionTransition.getPrevPriceList());
        Assert.assertNull(businessSubscriptionTransition.getPrevMrr());
        Assert.assertNull(businessSubscriptionTransition.getPrevCurrency());
        Assert.assertNull(businessSubscriptionTransition.getPrevBusinessActive());
        Assert.assertNull(businessSubscriptionTransition.getPrevStartDate());
        Assert.assertNull(businessSubscriptionTransition.getPrevState());

        Assert.assertEquals(businessSubscriptionTransition.getNextProductName(), subscriptionTransitionModelDao.getNextSubscription().getProductName());
        Assert.assertEquals(businessSubscriptionTransition.getNextProductType(), subscriptionTransitionModelDao.getNextSubscription().getProductType());
        Assert.assertEquals(businessSubscriptionTransition.getNextProductCategory(), subscriptionTransitionModelDao.getNextSubscription().getProductCategory());
        Assert.assertEquals(businessSubscriptionTransition.getNextSlug(), subscriptionTransitionModelDao.getNextSubscription().getSlug());
        Assert.assertEquals(businessSubscriptionTransition.getNextPhase(), subscriptionTransitionModelDao.getNextSubscription().getPhase());
        Assert.assertEquals(businessSubscriptionTransition.getNextBillingPeriod(), subscriptionTransitionModelDao.getNextSubscription().getBillingPeriod());
        Assert.assertEquals(businessSubscriptionTransition.getNextPrice(), subscriptionTransitionModelDao.getNextSubscription().getPrice());
        Assert.assertEquals(businessSubscriptionTransition.getNextPriceList(), subscriptionTransitionModelDao.getNextSubscription().getPriceList());
        Assert.assertEquals(businessSubscriptionTransition.getNextMrr(), subscriptionTransitionModelDao.getNextSubscription().getMrr());
        Assert.assertEquals(businessSubscriptionTransition.getNextCurrency(), subscriptionTransitionModelDao.getNextSubscription().getCurrency());
        Assert.assertEquals(businessSubscriptionTransition.getNextBusinessActive(), subscriptionTransitionModelDao.getNextSubscription().getBusinessActive());
        Assert.assertEquals(businessSubscriptionTransition.getNextStartDate(), subscriptionTransitionModelDao.getNextSubscription().getStartDate());
        Assert.assertEquals(businessSubscriptionTransition.getNextEndDate(), subscriptionTransitionModelDao.getNextSubscription().getEndDate());
        Assert.assertEquals(businessSubscriptionTransition.getNextState(), subscriptionTransitionModelDao.getNextSubscription().getState());
    }
}
