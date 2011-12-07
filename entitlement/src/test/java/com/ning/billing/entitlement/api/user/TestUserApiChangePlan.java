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

import com.ning.billing.catalog.api.*;
import com.ning.billing.entitlement.api.ApiTestListener.NextEvent;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.events.user.ApiEvent;
import com.ning.billing.util.clock.DefaultClock;
import org.joda.time.DateTime;
import org.testng.Assert;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.*;

public abstract class TestUserApiChangePlan extends TestUserApiBase {



    private void checkChangePlan(SubscriptionData subscription, String expProduct, ProductCategory expCategory,
            BillingPeriod expBillingPeriod, PhaseType expPhase) {

        Plan currentPlan = subscription.getCurrentPlan();
        assertNotNull(currentPlan);
        assertEquals(currentPlan.getProduct().getName(),expProduct);
        assertEquals(currentPlan.getProduct().getCategory(), expCategory);
        assertEquals(currentPlan.getBillingPeriod(), expBillingPeriod);

        PlanPhase currentPhase = subscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), expPhase);
    }



    protected void testChangePlanBundleAlignEOTWithNoChargeThroughDateReal() {
        tChangePlanBundleAlignEOTWithNoChargeThroughDate("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, "Pistol", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);
    }


    private void tChangePlanBundleAlignEOTWithNoChargeThroughDate(String fromProd, BillingPeriod fromTerm, String fromPlanSet,
        String toProd, BillingPeriod toTerm, String toPlanSet) {

        log.info("Starting testChangePlanBundleAlignEOTWithNoChargeThroughDateReal");

        try {

            // CREATE
            SubscriptionData subscription = createSubscription(fromProd, fromTerm, fromPlanSet);

            // MOVE TO NEXT PHASE
            PlanPhase currentPhase = subscription.getCurrentPhase();
            testListener.pushExpectedEvent(NextEvent.PHASE);
            clock.setDeltaFromReality(currentPhase.getDuration(), DAY_IN_MS);
            DateTime futureNow = clock.getUTCNow();
            DateTime nextExpectedPhaseChange = DefaultClock.addDuration(subscription.getStartDate(), currentPhase.getDuration());
            assertTrue(futureNow.isAfter(nextExpectedPhaseChange));
            assertTrue(testListener.isCompleted(3000));

            // CHANGE PLAN
            testListener.pushExpectedEvent(NextEvent.CHANGE);
            subscription.changePlan(toProd, toTerm, toPlanSet, clock.getUTCNow());
            assertTrue(testListener.isCompleted(2000));

            // CHECK CHANGE PLAN
            currentPhase = subscription.getCurrentPhase();
            checkChangePlan(subscription, toProd, ProductCategory.BASE, toTerm, PhaseType.EVERGREEN);

        } catch (EntitlementUserApiException e) {
            Assert.fail(e.getMessage());
        }
    }


    protected void testChangePlanBundleAlignEOTWithChargeThroughDateReal() {
        testChangePlanBundleAlignEOTWithChargeThroughDate("Shotgun", BillingPeriod.ANNUAL, "gunclubDiscount", "Pistol", BillingPeriod.ANNUAL, "gunclubDiscount");
    }

    private void testChangePlanBundleAlignEOTWithChargeThroughDate(String fromProd, BillingPeriod fromTerm, String fromPlanSet,
            String toProd, BillingPeriod toTerm, String toPlanSet) {

        log.info("Starting testChangeSubscriptionEOTWithChargeThroughDate");
        try {

            // CREATE
            SubscriptionData subscription = createSubscription(fromProd, fromTerm, fromPlanSet);
            PlanPhase trialPhase = subscription.getCurrentPhase();
            DateTime expectedPhaseTrialChange = DefaultClock.addDuration(subscription.getStartDate(), trialPhase.getDuration());
            assertEquals(trialPhase.getPhaseType(), PhaseType.TRIAL);


            // MOVE TO NEXT PHASE
            testListener.pushExpectedEvent(NextEvent.PHASE);
            clock.setDeltaFromReality(trialPhase.getDuration(), DAY_IN_MS);
            assertTrue(testListener.isCompleted(2000));
            PlanPhase currentPhase = subscription.getCurrentPhase();
            assertEquals(currentPhase.getPhaseType(), PhaseType.DISCOUNT);


            // SET CTD
            Duration ctd = getDurationMonth(1);
            DateTime newChargedThroughDate = DefaultClock.addDuration(expectedPhaseTrialChange, ctd);
            billingApi.setChargedThroughDate(subscription.getId(), newChargedThroughDate);

            // RE READ SUBSCRIPTION + CHANGE PLAN
            testListener.pushExpectedEvent(NextEvent.CHANGE);
            subscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(subscription.getId());
            subscription.changePlan(toProd, toTerm, toPlanSet, clock.getUTCNow());
            assertFalse(testListener.isCompleted(2000));
            testListener.reset();

            // CHECK CHANGE PLAN
            currentPhase = subscription.getCurrentPhase();
            checkChangePlan(subscription, fromProd, ProductCategory.BASE, fromTerm, PhaseType.DISCOUNT);

            // NEXT PHASE
            DateTime nextExpectedPhaseChange = DefaultClock.addDuration(expectedPhaseTrialChange, currentPhase.getDuration());
            checkNextPhaseChange(subscription, 2, nextExpectedPhaseChange);

            // ALSO VERIFY PENDING CHANGE EVENT
            List<EntitlementEvent> events = dao.getPendingEventsForSubscription(subscription.getId());
            assertTrue(events.get(0) instanceof ApiEvent);


            // MOVE TO EOT
            testListener.pushExpectedEvent(NextEvent.CHANGE);
            clock.addDeltaFromReality(ctd);
            assertTrue(testListener.isCompleted(5000));

            subscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(subscription.getId());
            currentPhase = subscription.getCurrentPhase();
            checkChangePlan(subscription, toProd, ProductCategory.BASE, toTerm, PhaseType.DISCOUNT);

        } catch (EntitlementUserApiException e) {
            Assert.fail(e.getMessage());
        }
    }


    protected void testChangePlanBundleAlignIMMReal() {
        tChangePlanBundleAlignIMM("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, "Assault-Rifle", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);
    }


    private void tChangePlanBundleAlignIMM(String fromProd, BillingPeriod fromTerm, String fromPlanSet,
            String toProd, BillingPeriod toTerm, String toPlanSet) {

        log.info("Starting testChangePlanBundleAlignIMM");

        try {

            SubscriptionData subscription = createSubscription(fromProd, fromTerm, fromPlanSet);

            testListener.pushExpectedEvent(NextEvent.CHANGE);

            Duration moveALittleInTime = getDurationDay(3);
            clock.setDeltaFromReality(moveALittleInTime, 0);

            // CHANGE PLAN IMM
            subscription.changePlan(toProd, toTerm, toPlanSet, clock.getUTCNow());
            checkChangePlan(subscription, toProd, ProductCategory.BASE, toTerm, PhaseType.TRIAL);

            assertTrue(testListener.isCompleted(2000));

            PlanPhase currentPhase = subscription.getCurrentPhase();
            DateTime nextExpectedPhaseChange = DefaultClock.addDuration(subscription.getStartDate(), currentPhase.getDuration());
            checkNextPhaseChange(subscription, 1, nextExpectedPhaseChange);

            // NEXT PHASE
            testListener.pushExpectedEvent(NextEvent.PHASE);
            clock.addDeltaFromReality(currentPhase.getDuration());
            DateTime futureNow = clock.getUTCNow();
            assertTrue(futureNow.isAfter(nextExpectedPhaseChange));
            assertTrue(testListener.isCompleted(3000));

        } catch (EntitlementUserApiException e) {
            Assert.fail(e.getMessage());
        }
    }


    protected void testChangePlanChangePlanAlignEOTWithChargeThroughDateReal() {
        tChangePlanChangePlanAlignEOTWithChargeThroughDate("Shotgun", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, "Assault-Rifle", BillingPeriod.ANNUAL, "rescue");
    }

    private void tChangePlanChangePlanAlignEOTWithChargeThroughDate(String fromProd, BillingPeriod fromTerm, String fromPlanSet,
            String toProd, BillingPeriod toTerm, String toPlanSet) {

        log.info("Starting testChangePlanBundleAlignEOTWithChargeThroughDate");

        try {

            DateTime currentTime = clock.getUTCNow();

            SubscriptionData subscription = createSubscription(fromProd, fromTerm, fromPlanSet);
            PlanPhase trialPhase = subscription.getCurrentPhase();
            DateTime expectedPhaseTrialChange = DefaultClock.addDuration(subscription.getStartDate(), trialPhase.getDuration());
            assertEquals(trialPhase.getPhaseType(), PhaseType.TRIAL);

            // MOVE TO NEXT PHASE
            testListener.pushExpectedEvent(NextEvent.PHASE);
            currentTime = clock.getUTCNow();
            clock.setDeltaFromReality(trialPhase.getDuration(), DAY_IN_MS);
            currentTime = clock.getUTCNow();
            assertTrue(testListener.isCompleted(2000));

            // SET CTD
            Duration ctd = getDurationMonth(1);
            DateTime newChargedThroughDate = DefaultClock.addDuration(expectedPhaseTrialChange, ctd);
            billingApi.setChargedThroughDate(subscription.getId(), newChargedThroughDate);

            // RE READ SUBSCRIPTION + CHECK CURRENT PHASE
            subscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(subscription.getId());
            PlanPhase currentPhase = subscription.getCurrentPhase();
            assertNotNull(currentPhase);
            assertEquals(currentPhase.getPhaseType(), PhaseType.EVERGREEN);

            // CHANGE PLAN
            currentTime = clock.getUTCNow();

            testListener.pushExpectedEvent(NextEvent.CHANGE);
            subscription.changePlan(toProd, toTerm, toPlanSet, clock.getUTCNow());

            checkChangePlan(subscription, fromProd, ProductCategory.BASE, fromTerm, PhaseType.EVERGREEN);

            // CHECK CHANGE DID NOT KICK IN YET
            assertFalse(testListener.isCompleted(2000));

            // MOVE TO AFTER CTD
            clock.addDeltaFromReality(ctd);
            currentTime = clock.getUTCNow();
            assertTrue(testListener.isCompleted(2000));

            // CHECK CORRECT PRODUCT, PHASE, PLAN SET
            String currentProduct =  subscription.getCurrentPlan().getProduct().getName();
            assertNotNull(currentProduct);
            assertEquals(currentProduct, toProd);
            currentPhase = subscription.getCurrentPhase();
            assertNotNull(currentPhase);
            assertEquals(currentPhase.getPhaseType(), PhaseType.DISCOUNT);

            testListener.pushExpectedEvent(NextEvent.PHASE);

            // MOVE TIME ABOUT ONE MONTH BEFORE NEXT EXPECTED PHASE CHANGE
            clock.addDeltaFromReality(getDurationMonth(11));

            currentTime = clock.getUTCNow();
            assertFalse(testListener.isCompleted(2000));

            DateTime nextExpectedPhaseChange = DefaultClock.addDuration(newChargedThroughDate, currentPhase.getDuration());
            checkNextPhaseChange(subscription, 1, nextExpectedPhaseChange);

            // MOVE TIME RIGHT AFTER NEXT EXPECTED PHASE CHANGE
            clock.addDeltaFromReality(getDurationMonth(1));
            currentTime = clock.getUTCNow();
            assertTrue(testListener.isCompleted(2000));

        } catch (EntitlementUserApiException e) {
            Assert.fail(e.getMessage());
        }
    }

    protected void testMultipleChangeLastIMMReal() {

        try {
            SubscriptionData subscription = createSubscription("Assault-Rifle", BillingPeriod.MONTHLY, "gunclubDiscount");
            PlanPhase trialPhase = subscription.getCurrentPhase();
            assertEquals(trialPhase.getPhaseType(), PhaseType.TRIAL);

            // MOVE TO NEXT PHASE
            testListener.pushExpectedEvent(NextEvent.PHASE);
            clock.setDeltaFromReality(trialPhase.getDuration(), DAY_IN_MS);
            assertTrue(testListener.isCompleted(2000));

            // SET CTD
            List<Duration> durationList = new ArrayList<Duration>();
            durationList.add(trialPhase.getDuration());
            //durationList.add(subscription.getCurrentPhase().getDuration());
            DateTime startDiscountPhase = DefaultClock.addDuration(subscription.getStartDate(), durationList);
            Duration ctd = getDurationMonth(1);
            DateTime newChargedThroughDate = DefaultClock.addDuration(startDiscountPhase, ctd);
            billingApi.setChargedThroughDate(subscription.getId(), newChargedThroughDate);
            subscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(subscription.getId());

            // CHANGE EOT
            testListener.pushExpectedEvent(NextEvent.CHANGE);
            subscription.changePlan("Pistol", BillingPeriod.MONTHLY, "gunclubDiscount", clock.getUTCNow());
            assertFalse(testListener.isCompleted(2000));

            // CHANGE
            testListener.pushExpectedEvent(NextEvent.CHANGE);
            subscription.changePlan("Assault-Rifle", BillingPeriod.ANNUAL, "gunclubDiscount", clock.getUTCNow());
            assertFalse(testListener.isCompleted(2000));

            Plan currentPlan = subscription.getCurrentPlan();
            assertNotNull(currentPlan);
            assertEquals(currentPlan.getProduct().getName(), "Assault-Rifle");
            assertEquals(currentPlan.getProduct().getCategory(), ProductCategory.BASE);
            assertEquals(currentPlan.getBillingPeriod(), BillingPeriod.ANNUAL);

            PlanPhase currentPhase = subscription.getCurrentPhase();
            assertNotNull(currentPhase);
            assertEquals(currentPhase.getPhaseType(), PhaseType.DISCOUNT);

        } catch (EntitlementUserApiException e) {
            Assert.fail(e.getMessage());
        }
    }

    protected void testMultipleChangeLastEOTReal() {

        try {

            SubscriptionData subscription = createSubscription("Assault-Rifle", BillingPeriod.ANNUAL, "gunclubDiscount");
            PlanPhase trialPhase = subscription.getCurrentPhase();
            assertEquals(trialPhase.getPhaseType(), PhaseType.TRIAL);

            testListener.pushExpectedEvent(NextEvent.PHASE);
            clock.setDeltaFromReality(trialPhase.getDuration(), DAY_IN_MS);
            assertTrue(testListener.isCompleted(2000));

            // SET CTD
            List<Duration> durationList = new ArrayList<Duration>();
            durationList.add(trialPhase.getDuration());
            DateTime startDiscountPhase = DefaultClock.addDuration(subscription.getStartDate(), durationList);
            Duration ctd = getDurationMonth(1);
            DateTime newChargedThroughDate = DefaultClock.addDuration(startDiscountPhase, ctd);
            billingApi.setChargedThroughDate(subscription.getId(), newChargedThroughDate);
            subscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(subscription.getId());

            // CHANGE EOT
            testListener.pushExpectedEvent(NextEvent.CHANGE);
            subscription.changePlan("Shotgun", BillingPeriod.MONTHLY, "gunclubDiscount", clock.getUTCNow());
            assertFalse(testListener.isCompleted(2000));
            testListener.reset();

            // CHANGE EOT
            testListener.pushExpectedEvent(NextEvent.CHANGE);
            subscription.changePlan("Pistol", BillingPeriod.ANNUAL, "gunclubDiscount", clock.getUTCNow());
            assertFalse(testListener.isCompleted(2000));
            testListener.reset();

            // CHECK NO CHANGE OCCURED YET
            Plan currentPlan = subscription.getCurrentPlan();
            assertNotNull(currentPlan);
            assertEquals(currentPlan.getProduct().getName(), "Assault-Rifle");
            assertEquals(currentPlan.getProduct().getCategory(), ProductCategory.BASE);
            assertEquals(currentPlan.getBillingPeriod(), BillingPeriod.ANNUAL);

            PlanPhase currentPhase = subscription.getCurrentPhase();
            assertNotNull(currentPhase);
            assertEquals(currentPhase.getPhaseType(), PhaseType.DISCOUNT);

            // ACTIVATE CHNAGE BY MOVING AFTER CTD
            testListener.pushExpectedEvent(NextEvent.CHANGE);
            clock.addDeltaFromReality(ctd);
            assertTrue(testListener.isCompleted(3000));

            currentPlan = subscription.getCurrentPlan();
            assertNotNull(currentPlan);
            assertEquals(currentPlan.getProduct().getName(), "Pistol");
            assertEquals(currentPlan.getProduct().getCategory(), ProductCategory.BASE);
            assertEquals(currentPlan.getBillingPeriod(), BillingPeriod.ANNUAL);

            currentPhase = subscription.getCurrentPhase();
            assertNotNull(currentPhase);
            assertEquals(currentPhase.getPhaseType(), PhaseType.DISCOUNT);



            // MOVE TO NEXT PHASE
            testListener.pushExpectedEvent(NextEvent.PHASE);
            clock.addDeltaFromReality(currentPhase.getDuration());
            assertTrue(testListener.isCompleted(3000));
            subscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(subscription.getId());

            currentPlan = subscription.getCurrentPlan();
            assertNotNull(currentPlan);
            assertEquals(currentPlan.getProduct().getName(), "Pistol");
            assertEquals(currentPlan.getProduct().getCategory(), ProductCategory.BASE);
            assertEquals(currentPlan.getBillingPeriod(), BillingPeriod.ANNUAL);

            currentPhase = subscription.getCurrentPhase();
            assertNotNull(currentPhase);
            assertEquals(currentPhase.getPhaseType(), PhaseType.EVERGREEN);


        } catch (EntitlementUserApiException e) {
            Assert.fail(e.getMessage());
        }
    }

}
