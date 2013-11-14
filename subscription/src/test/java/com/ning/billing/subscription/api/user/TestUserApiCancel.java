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

package com.ning.billing.subscription.api.user;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Duration;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.subscription.SubscriptionTestSuiteWithEmbeddedDB;
import com.ning.billing.subscription.api.SubscriptionBillingApiException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestUserApiCancel extends SubscriptionTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testCancelSubscriptionIMM() {
        try {
            final DateTime init = clock.getUTCNow();

            final String prod = "Shotgun";
            final BillingPeriod term = BillingPeriod.MONTHLY;
            final String planSet = PriceListSet.DEFAULT_PRICELIST_NAME;

            // CREATE
            final DefaultSubscriptionBase subscription = testUtil.createSubscription(bundle, prod, term, planSet);
            PlanPhase currentPhase = subscription.getCurrentPhase();
            assertEquals(currentPhase.getPhaseType(), PhaseType.TRIAL);

            // ADVANCE TIME still in trial
            final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(3));
            clock.addDeltaFromReality(it.toDurationMillis());

            final DateTime future = clock.getUTCNow();
            testListener.pushExpectedEvent(NextEvent.CANCEL);

            assertEquals(subscription.getLastActiveProduct().getName(), prod);
            assertEquals(subscription.getLastActivePriceList().getName(), planSet);
            assertEquals(subscription.getLastActiveBillingPeriod(), term);
            assertEquals(subscription.getLastActiveCategory(), ProductCategory.BASE);


            // CANCEL in trial period to get IMM policy
            subscription.cancel(callContext);
            currentPhase = subscription.getCurrentPhase();
            testListener.isCompleted(3000);

            assertEquals(subscription.getLastActiveProduct().getName(), prod);
            assertEquals(subscription.getLastActivePriceList().getName(), planSet);
            assertEquals(subscription.getLastActiveBillingPeriod(), term);
            assertEquals(subscription.getLastActiveCategory(), ProductCategory.BASE);


            assertNull(currentPhase);
            testUtil.checkNextPhaseChange(subscription, 0, null);

            assertListenerStatus();
        } catch (SubscriptionBaseApiException e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test(groups = "slow")
    public void testCancelSubscriptionEOTWithChargeThroughDate() throws SubscriptionBillingApiException {
        try {
            final String prod = "Shotgun";
            final BillingPeriod term = BillingPeriod.MONTHLY;
            final String planSet = PriceListSet.DEFAULT_PRICELIST_NAME;

            // CREATE
            DefaultSubscriptionBase subscription = testUtil.createSubscription(bundle, prod, term, planSet);
            PlanPhase trialPhase = subscription.getCurrentPhase();
            assertEquals(trialPhase.getPhaseType(), PhaseType.TRIAL);

            // NEXT PHASE
            final DateTime expectedPhaseTrialChange = TestSubscriptionHelper.addDuration(subscription.getStartDate(), trialPhase.getDuration());
            testUtil.checkNextPhaseChange(subscription, 1, expectedPhaseTrialChange);

            // MOVE TO NEXT PHASE
            testListener.pushExpectedEvent(NextEvent.PHASE);
            Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(31));
            clock.addDeltaFromReality(it.toDurationMillis());

            assertListenerStatus();
            trialPhase = subscription.getCurrentPhase();
            assertEquals(trialPhase.getPhaseType(), PhaseType.EVERGREEN);

            // SET CTD + RE READ SUBSCRIPTION + CHANGE PLAN
            final Duration ctd = testUtil.getDurationMonth(1);
            final DateTime newChargedThroughDate = TestSubscriptionHelper.addDuration(expectedPhaseTrialChange, ctd);
            subscriptionInternalApi.setChargedThroughDate(subscription.getId(), newChargedThroughDate, internalCallContext);
            subscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(subscription.getId(), internalCallContext);

            assertEquals(subscription.getLastActiveProduct().getName(), prod);
            assertEquals(subscription.getLastActivePriceList().getName(), planSet);
            assertEquals(subscription.getLastActiveBillingPeriod(), term);
            assertEquals(subscription.getLastActiveCategory(), ProductCategory.BASE);

            // CANCEL
            testListener.setNonExpectedMode();
            testListener.pushExpectedEvent(NextEvent.CANCEL);
            subscription.cancel(callContext);
            assertFalse(testListener.isCompleted(3000));
            testListener.reset();


            assertEquals(subscription.getLastActiveProduct().getName(), prod);
            assertEquals(subscription.getLastActivePriceList().getName(), planSet);
            assertEquals(subscription.getLastActiveBillingPeriod(), term);
            assertEquals(subscription.getLastActiveCategory(), ProductCategory.BASE);

            final DateTime futureEndDate = subscription.getFutureEndDate();
            Assert.assertNotNull(futureEndDate);

            // MOVE TO EOT + RECHECK
            testListener.pushExpectedEvent(NextEvent.CANCEL);
            it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(1));
            clock.addDeltaFromReality(it.toDurationMillis());
            final DateTime future = clock.getUTCNow();
            assertListenerStatus();

            assertTrue(futureEndDate.compareTo(subscription.getEndDate()) == 0);

            final PlanPhase currentPhase = subscription.getCurrentPhase();
            assertNull(currentPhase);
            testUtil.checkNextPhaseChange(subscription, 0, null);

            assertEquals(subscription.getLastActiveProduct().getName(), prod);
            assertEquals(subscription.getLastActivePriceList().getName(), planSet);
            assertEquals(subscription.getLastActiveBillingPeriod(), term);
            assertEquals(subscription.getLastActiveCategory(), ProductCategory.BASE);


            assertListenerStatus();
        } catch (SubscriptionBaseApiException e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test(groups = "slow")
    public void testCancelSubscriptionEOTWithNoChargeThroughDate() {
        try {
            final String prod = "Shotgun";
            final BillingPeriod term = BillingPeriod.MONTHLY;
            final String planSet = PriceListSet.DEFAULT_PRICELIST_NAME;

            // CREATE
            final DefaultSubscriptionBase subscription = testUtil.createSubscription(bundle, prod, term, planSet);
            PlanPhase trialPhase = subscription.getCurrentPhase();
            assertEquals(trialPhase.getPhaseType(), PhaseType.TRIAL);

            // NEXT PHASE
            final DateTime expectedPhaseTrialChange = TestSubscriptionHelper.addDuration(subscription.getStartDate(), trialPhase.getDuration());
            testUtil.checkNextPhaseChange(subscription, 1, expectedPhaseTrialChange);

            // MOVE TO NEXT PHASE
            testListener.pushExpectedEvent(NextEvent.PHASE);

            final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(31));
            clock.addDeltaFromReality(it.toDurationMillis());
            assertListenerStatus();
            trialPhase = subscription.getCurrentPhase();
            assertEquals(trialPhase.getPhaseType(), PhaseType.EVERGREEN);

            testListener.pushExpectedEvent(NextEvent.CANCEL);

            // CANCEL
            subscription.cancel(callContext);
            assertListenerStatus();

            final PlanPhase currentPhase = subscription.getCurrentPhase();
            assertNull(currentPhase);
            testUtil.checkNextPhaseChange(subscription, 0, null);

            assertListenerStatus();
        } catch (SubscriptionBaseApiException e) {
            Assert.fail(e.getMessage());
        }
    }

    // Similar test to testCancelSubscriptionEOTWithChargeThroughDate except we uncancel and check things
    // are as they used to be and we can move forward without hitting cancellation
    //
    @Test(groups = "slow")
    public void testUncancel() throws SubscriptionBillingApiException {
        try {
            final String prod = "Shotgun";
            final BillingPeriod term = BillingPeriod.MONTHLY;
            final String planSet = PriceListSet.DEFAULT_PRICELIST_NAME;

            // CREATE
            DefaultSubscriptionBase subscription = testUtil.createSubscription(bundle, prod, term, planSet);
            final PlanPhase trialPhase = subscription.getCurrentPhase();
            assertEquals(trialPhase.getPhaseType(), PhaseType.TRIAL);

            // NEXT PHASE
            final DateTime expectedPhaseTrialChange = TestSubscriptionHelper.addDuration(subscription.getStartDate(), trialPhase.getDuration());
            testUtil.checkNextPhaseChange(subscription, 1, expectedPhaseTrialChange);

            // MOVE TO NEXT PHASE
            testListener.pushExpectedEvent(NextEvent.PHASE);
            Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(31));
            clock.addDeltaFromReality(it.toDurationMillis());
            assertListenerStatus();
            PlanPhase currentPhase = subscription.getCurrentPhase();
            assertEquals(currentPhase.getPhaseType(), PhaseType.EVERGREEN);

            // SET CTD + RE READ SUBSCRIPTION + CHANGE PLAN
            final Duration ctd = testUtil.getDurationMonth(1);
            final DateTime newChargedThroughDate = TestSubscriptionHelper.addDuration(expectedPhaseTrialChange, ctd);
            subscriptionInternalApi.setChargedThroughDate(subscription.getId(), newChargedThroughDate, internalCallContext);
            subscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(subscription.getId(), internalCallContext);

            // CANCEL EOT
            subscription.cancel(callContext);

            subscription.uncancel(callContext);

            // MOVE TO EOT + RECHECK
            testListener.pushExpectedEvent(NextEvent.UNCANCEL);
            it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(1));
            clock.addDeltaFromReality(it.toDurationMillis());
            assertListenerStatus();

            final Plan currentPlan = subscription.getCurrentPlan();
            assertEquals(currentPlan.getProduct().getName(), prod);
            currentPhase = subscription.getCurrentPhase();
            assertEquals(currentPhase.getPhaseType(), PhaseType.EVERGREEN);

            assertListenerStatus();
        } catch (SubscriptionBaseApiException e) {
            Assert.fail(e.getMessage());
        }
    }
}
