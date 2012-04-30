/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.entitlement.api.user;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import org.joda.time.DateTime;
import org.testng.Assert;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Duration;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.entitlement.api.ApiTestListener.NextEvent;
import com.ning.billing.entitlement.api.TestApiBase;
import com.ning.billing.entitlement.api.billing.EntitlementBillingApiException;
import com.ning.billing.util.clock.DefaultClock;

public abstract class TestUserApiCancel extends TestApiBase {

    protected void testCancelSubscriptionIMM() {

        log.info("Starting testCancelSubscriptionIMM");

        try {

            DateTime init = clock.getUTCNow();

            String prod = "Shotgun";
            BillingPeriod term = BillingPeriod.MONTHLY;
            String planSet = PriceListSet.DEFAULT_PRICELIST_NAME;

            // CREATE
            SubscriptionData subscription = createSubscription(prod, term, planSet);
            PlanPhase currentPhase = subscription.getCurrentPhase();
            assertEquals(currentPhase.getPhaseType(), PhaseType.TRIAL);

            // ADVANCE TIME still in trial
            Duration moveALittleInTime = getDurationDay(3);
            clock.setDeltaFromReality(moveALittleInTime, 0);

            DateTime future = clock.getUTCNow();
            testListener.pushNextApiExpectedEvent(NextEvent.CANCEL);

            // CANCEL in trial period to get IMM policy
            subscription.cancel(clock.getUTCNow(), false, context);
            currentPhase = subscription.getCurrentPhase();
            testListener.isApiCompleted(1000);

            assertNull(currentPhase);
            checkNextPhaseChange(subscription, 0, null);

        } catch (EntitlementUserApiException e) {
            Assert.fail(e.getMessage());
        }
    }


    protected void testCancelSubscriptionEOTWithChargeThroughDate() throws EntitlementBillingApiException {
        log.info("Starting testCancelSubscriptionEOTWithChargeThroughDate");

        try {

            String prod = "Shotgun";
            BillingPeriod term = BillingPeriod.MONTHLY;
            String planSet = PriceListSet.DEFAULT_PRICELIST_NAME;

            // CREATE
            SubscriptionData subscription = createSubscription(prod, term, planSet);
            PlanPhase trialPhase = subscription.getCurrentPhase();
            assertEquals(trialPhase.getPhaseType(), PhaseType.TRIAL);

            // NEXT PHASE
            DateTime expectedPhaseTrialChange = DefaultClock.addDuration(subscription.getStartDate(), trialPhase.getDuration());
            checkNextPhaseChange(subscription, 1, expectedPhaseTrialChange);

            // MOVE TO NEXT PHASE
            testListener.pushNextApiExpectedEvent(NextEvent.PHASE);
            clock.setDeltaFromReality(trialPhase.getDuration(), DAY_IN_MS);
            assertTrue(testListener.isApiCompleted(2000));
            trialPhase = subscription.getCurrentPhase();
            assertEquals(trialPhase.getPhaseType(), PhaseType.EVERGREEN);

            // SET CTD + RE READ SUBSCRIPTION + CHANGE PLAN
            Duration ctd = getDurationMonth(1);
            DateTime newChargedThroughDate = DefaultClock.addDuration(expectedPhaseTrialChange, ctd);
            billingApi.setChargedThroughDate(subscription.getId(), newChargedThroughDate, context);
            subscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(subscription.getId());

            testListener.pushNextApiExpectedEvent(NextEvent.CANCEL);

            // CANCEL
            subscription.cancel(clock.getUTCNow(), false, context);
            assertFalse(testListener.isApiCompleted(2000));

            // MOVE TO EOT + RECHECK
            clock.addDeltaFromReality(ctd);
            DateTime future = clock.getUTCNow();
            assertTrue(testListener.isApiCompleted(2000));

            PlanPhase currentPhase = subscription.getCurrentPhase();
            assertNull(currentPhase);
            checkNextPhaseChange(subscription, 0, null);

        } catch (EntitlementUserApiException e) {
            Assert.fail(e.getMessage());
        }
    }


    protected void testCancelSubscriptionEOTWithNoChargeThroughDate() {

        log.info("Starting testCancelSubscriptionEOTWithNoChargeThroughDate");

        try {

            String prod = "Shotgun";
            BillingPeriod term = BillingPeriod.MONTHLY;
            String planSet = PriceListSet.DEFAULT_PRICELIST_NAME;

            // CREATE
            SubscriptionData subscription = createSubscription(prod, term, planSet);
            PlanPhase trialPhase = subscription.getCurrentPhase();
            assertEquals(trialPhase.getPhaseType(), PhaseType.TRIAL);

            // NEXT PHASE
            DateTime expectedPhaseTrialChange = DefaultClock.addDuration(subscription.getStartDate(), trialPhase.getDuration());
            checkNextPhaseChange(subscription, 1, expectedPhaseTrialChange);

            // MOVE TO NEXT PHASE
            testListener.pushNextApiExpectedEvent(NextEvent.PHASE);
            clock.setDeltaFromReality(trialPhase.getDuration(), DAY_IN_MS);
            assertTrue(testListener.isApiCompleted(2000));
            trialPhase = subscription.getCurrentPhase();
            assertEquals(trialPhase.getPhaseType(), PhaseType.EVERGREEN);

            testListener.pushNextApiExpectedEvent(NextEvent.CANCEL);

            // CANCEL
            subscription.cancel(clock.getUTCNow(), false, context);
            assertTrue(testListener.isApiCompleted(2000));

            PlanPhase currentPhase = subscription.getCurrentPhase();
            assertNull(currentPhase);
            checkNextPhaseChange(subscription, 0, null);

        } catch (EntitlementUserApiException e) {
            Assert.fail(e.getMessage());
        }
    }

    // Similar test to testCancelSubscriptionEOTWithChargeThroughDate except we uncancel and check things
    // are as they used to be and we can move forward without hitting cancellation
    //
    protected void testUncancel() throws EntitlementBillingApiException {

        log.info("Starting testUncancel");

        try {

            String prod = "Shotgun";
            BillingPeriod term = BillingPeriod.MONTHLY;
            String planSet = PriceListSet.DEFAULT_PRICELIST_NAME;

            // CREATE
            SubscriptionData subscription = createSubscription(prod, term, planSet);
            PlanPhase trialPhase = subscription.getCurrentPhase();
            assertEquals(trialPhase.getPhaseType(), PhaseType.TRIAL);

            // NEXT PHASE
            DateTime expectedPhaseTrialChange = DefaultClock.addDuration(subscription.getStartDate(), trialPhase.getDuration());
            checkNextPhaseChange(subscription, 1, expectedPhaseTrialChange);

            // MOVE TO NEXT PHASE
            testListener.pushNextApiExpectedEvent(NextEvent.PHASE);
            clock.setDeltaFromReality(trialPhase.getDuration(), DAY_IN_MS);
            assertTrue(testListener.isApiCompleted(2000));
            PlanPhase currentPhase = subscription.getCurrentPhase();
            assertEquals(currentPhase.getPhaseType(), PhaseType.EVERGREEN);

            // SET CTD + RE READ SUBSCRIPTION + CHANGE PLAN
            Duration ctd = getDurationMonth(1);
            DateTime newChargedThroughDate = DefaultClock.addDuration(expectedPhaseTrialChange, ctd);
            billingApi.setChargedThroughDate(subscription.getId(), newChargedThroughDate, context);
            subscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(subscription.getId());

            testListener.pushNextApiExpectedEvent(NextEvent.CANCEL);

            // CANCEL
            subscription.cancel(clock.getUTCNow(), false, context);
            assertFalse(testListener.isApiCompleted(2000));

            subscription.uncancel(context);

            // MOVE TO EOT + RECHECK
            clock.addDeltaFromReality(ctd);
            DateTime future = clock.getUTCNow();
            assertFalse(testListener.isApiCompleted(2000));

            Plan currentPlan = subscription.getCurrentPlan();
            assertEquals(currentPlan.getProduct().getName(), prod);
            currentPhase = subscription.getCurrentPhase();
            assertEquals(currentPhase.getPhaseType(), PhaseType.EVERGREEN);

        } catch (EntitlementUserApiException e) {
            Assert.fail(e.getMessage());
        }
    }

}
