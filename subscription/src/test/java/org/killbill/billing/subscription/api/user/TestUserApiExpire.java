/*
 * Copyright 2014-2022 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.subscription.api.user;

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.subscription.SubscriptionTestSuiteWithEmbeddedDB;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent;
import org.killbill.billing.subscription.events.expired.ExpiredEvent;
import org.killbill.billing.subscription.events.phase.PhaseEvent;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class TestUserApiExpire extends SubscriptionTestSuiteWithEmbeddedDB {

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1533")
    public void testCreateSubscriptionWithOnlyFixedTermPhase() throws SubscriptionBaseApiException {
        final String planName = "pistol-biennial-fixedterm";

        DefaultSubscriptionBase subscription = testUtil.createSubscription(bundle, planName);
        assertNotNull(subscription);
        assertEquals(subscription.getState(), EntitlementState.ACTIVE);

        final Plan currentPlan = subscription.getCurrentPlan();
        assertNotNull(currentPlan);
        assertEquals(currentPlan.getProduct().getCategory(), ProductCategory.BASE);
        assertEquals(currentPlan.getRecurringBillingPeriod(), BillingPeriod.BIENNIAL);

        final PlanPhase currentPhase = subscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.FIXEDTERM);

        final List<SubscriptionBaseEvent> events = dao.getPendingEventsForSubscription(subscription.getId(), internalCallContext);
        assertNotNull(events);
        testUtil.printEvents(events);
        assertTrue(events.size() == 1);
        assertTrue(events.get(0) instanceof ExpiredEvent);
        final DateTime expiryDate = events.get(0).getEffectiveDate();
        final DateTime expectedExpiryDate = TestSubscriptionHelper.addDuration(subscription.getStartDate(), currentPhase.getDuration());
        assertEquals(expiryDate, expectedExpiryDate);

        //MOVE PAST FIXEDTERM PHASE
        testListener.pushExpectedEvent(NextEvent.EXPIRED);
        final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(36));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertListenerStatus();

        // REFETCH SUBSCRIPTION AND CHECK THAT IT IS EXPIRED
        subscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(subscription.getId(), false, internalCallContext);
        assertEquals(subscription.getState(), EntitlementState.EXPIRED);
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1533")
    public void testCreateSubscriptionTransitioningToFixedTerm() throws SubscriptionBaseApiException {
        final String planName = "pistol-monthly-fixedterm";

        // CREATE SUBSCRIPTION
        DefaultSubscriptionBase subscription = testUtil.createSubscription(bundle, planName);
        assertNotNull(subscription);
        assertEquals(subscription.getState(), EntitlementState.ACTIVE);

        PlanPhase currentPhase = subscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.TRIAL);

        List<SubscriptionBaseEvent> events = dao.getPendingEventsForSubscription(subscription.getId(), internalCallContext);
        assertNotNull(events);
        testUtil.printEvents(events);
        assertTrue(events.size() == 1);
        assertTrue(events.get(0) instanceof PhaseEvent);

        // MOVE TO FIXEDTERM PHASE
        testListener.pushExpectedEvents(NextEvent.PHASE);
        Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(30));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertListenerStatus();

        subscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(subscription.getId(), false, internalCallContext);
        currentPhase = subscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.FIXEDTERM);
        assertEquals(subscription.getState(), EntitlementState.ACTIVE);

        events = dao.getPendingEventsForSubscription(subscription.getId(), internalCallContext);
        assertNotNull(events);
        testUtil.printEvents(events);
        assertTrue(events.size() == 1);
        assertTrue(events.get(0) instanceof ExpiredEvent);
        final DateTime expiryDate = events.get(0).getEffectiveDate();
        final DateTime expectedExpiryDate = TestSubscriptionHelper.addDuration(subscription.getStartDate().plusDays(30), currentPhase.getDuration());
        assertEquals(expiryDate, expectedExpiryDate);

        //MOVE PAST FIXEDTERM PHASE
        testListener.pushExpectedEvents(NextEvent.EXPIRED);
        it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(12));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertListenerStatus();

        subscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(subscription.getId(), false, internalCallContext);
        assertEquals(subscription.getState(), EntitlementState.EXPIRED);
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1533")
    public void testCreateSubscriptionWithMultiplePhasesTransitioningToFixedTerm() throws SubscriptionBaseApiException {
        final String planName = "pistol-monthly-discount-and-fixedterm";
        // CREATE SUBSCRIPTION
        DefaultSubscriptionBase subscription = testUtil.createSubscription(bundle, planName);
        assertNotNull(subscription);
        assertEquals(subscription.getState(), EntitlementState.ACTIVE);

        PlanPhase currentPhase = subscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.TRIAL);

        List<SubscriptionBaseEvent> events = dao.getPendingEventsForSubscription(subscription.getId(), internalCallContext);
        assertNotNull(events);
        testUtil.printEvents(events);
        assertTrue(events.size() == 1);
        assertTrue(events.get(0) instanceof PhaseEvent);

        // MOVE TO DISCOUNT PHASE
        testListener.pushExpectedEvents(NextEvent.PHASE);
        Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(31));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertListenerStatus();

        subscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(subscription.getId(), false, internalCallContext);
        currentPhase = subscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.DISCOUNT);
        assertEquals(subscription.getState(), EntitlementState.ACTIVE);

        events = dao.getPendingEventsForSubscription(subscription.getId(), internalCallContext);
        assertNotNull(events);
        testUtil.printEvents(events);
        assertTrue(events.size() == 1);
        assertTrue(events.get(0) instanceof PhaseEvent);

        //MOVE TO FIXEDTERM PHASE
        testListener.pushExpectedEvents(NextEvent.PHASE);
        it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(6));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertListenerStatus();

        subscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(subscription.getId(), false, internalCallContext); 
        currentPhase = subscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.FIXEDTERM);
        assertEquals(subscription.getState(), EntitlementState.ACTIVE);

        events = dao.getPendingEventsForSubscription(subscription.getId(), internalCallContext);
        assertNotNull(events);
        testUtil.printEvents(events);
        assertTrue(events.size() == 1);
        assertTrue(events.get(0) instanceof ExpiredEvent);
        final DateTime expiryDate = events.get(0).getEffectiveDate();
        final DateTime expectedExpiryDate = TestSubscriptionHelper.addDuration(subscription.getStartDate().plusDays(30).plusMonths(6), currentPhase.getDuration());
        assertEquals(expiryDate, expectedExpiryDate);

        //MOVE PAST FIXEDTERM PHASE
        testListener.pushExpectedEvents(NextEvent.EXPIRED);
        it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(12));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertListenerStatus();

        subscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(subscription.getId(), false, internalCallContext);
        assertEquals(subscription.getState(), EntitlementState.EXPIRED);
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1533")
    public void testCreateSubscriptionWithFixedTermInBetween() throws SubscriptionBaseApiException {
        final String planName = "pistol-monthly-fixedterm-and-evergreen";
        // CREATE SUBSCRIPTION
        DefaultSubscriptionBase subscription = testUtil.createSubscription(bundle, planName);
        assertNotNull(subscription);
        assertEquals(subscription.getState(), EntitlementState.ACTIVE);

        PlanPhase currentPhase = subscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.TRIAL);

        List<SubscriptionBaseEvent> events = dao.getPendingEventsForSubscription(subscription.getId(), internalCallContext);
        assertNotNull(events);
        testUtil.printEvents(events);
        assertTrue(events.size() == 1);
        assertTrue(events.get(0) instanceof PhaseEvent);

        // MOVE TO FIXEDTERM PHASE
        testListener.pushExpectedEvents(NextEvent.PHASE);
        Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(31));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertListenerStatus();

        subscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(subscription.getId(), false, internalCallContext);
        currentPhase = subscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.FIXEDTERM);
        assertEquals(subscription.getState(), EntitlementState.ACTIVE);

        events = dao.getPendingEventsForSubscription(subscription.getId(), internalCallContext);
        assertNotNull(events);
        testUtil.printEvents(events);
        assertTrue(events.size() == 1);
        assertTrue(events.get(0) instanceof PhaseEvent); //Since FIXEDTERM is not the last phase, a PHASE event occurs and not EXPIRED event

        //MOVE TO EVERGREEN PHASE
        testListener.pushExpectedEvents(NextEvent.PHASE);
        it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(6));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertListenerStatus();

        subscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(subscription.getId(), false, internalCallContext);
        currentPhase = subscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.EVERGREEN);
        assertEquals(subscription.getState(), EntitlementState.ACTIVE);

        events = dao.getPendingEventsForSubscription(subscription.getId(), internalCallContext);
        assertNotNull(events);
        testUtil.printEvents(events);
        assertTrue(events.size() == 0);
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1533")
    public void testCreateStandAloneSubscriptionWithOnlyFixedTermPhase() throws SubscriptionBaseApiException {
        // CREATE SUBSCRIPTION
        final String planName = "knife-monthly-fixedterm";

        DefaultSubscriptionBase subscription = testUtil.createSubscription(bundle, planName);
        assertNotNull(subscription);
        assertEquals(subscription.getState(), EntitlementState.ACTIVE);

        final Plan currentPlan = subscription.getCurrentPlan();
        assertNotNull(currentPlan);
        assertEquals(currentPlan.getProduct().getCategory(), ProductCategory.STANDALONE);
        assertEquals(currentPlan.getRecurringBillingPeriod(), BillingPeriod.MONTHLY);

        final PlanPhase currentPhase = subscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.FIXEDTERM);

        final List<SubscriptionBaseEvent> events = dao.getPendingEventsForSubscription(subscription.getId(), internalCallContext);
        assertNotNull(events);
        testUtil.printEvents(events);
        assertTrue(events.size() == 1);
        assertTrue(events.get(0) instanceof ExpiredEvent);
        final DateTime expiryDate = events.get(0).getEffectiveDate();
        final DateTime expectedExpiryDate = TestSubscriptionHelper.addDuration(subscription.getStartDate(), currentPhase.getDuration());
        assertEquals(expiryDate, expectedExpiryDate);

        //MOVE PAST FIXEDTERM PHASE
        testListener.pushExpectedEvent(NextEvent.EXPIRED);
        final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(6));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertListenerStatus();

        // REFETCH SUBSCRIPTION AND CHECK THAT IT IS EXPIRED
        subscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(subscription.getId(), false, internalCallContext);
        assertEquals(subscription.getState(), EntitlementState.EXPIRED);
    }

    @Test(groups = "slow")
    public void testCreateFixedTermBPWithFixedTermAddonAddOnExpiryBeforeBasePlan() throws SubscriptionBaseApiException {
        final String basePlanName = "pistol-monthly-fixedterm-no-trial";
        // CREATE BP
        DefaultSubscriptionBase baseSubscription = testUtil.createSubscription(bundle, basePlanName);
        assertNotNull(baseSubscription);
        assertEquals(baseSubscription.getState(), EntitlementState.ACTIVE);

        Plan currentPlan = baseSubscription.getCurrentPlan();
        assertNotNull(currentPlan);
        assertEquals(currentPlan.getProduct().getCategory(), ProductCategory.BASE);
        assertEquals(currentPlan.getRecurringBillingPeriod(), BillingPeriod.MONTHLY);

        PlanPhase currentPhase = baseSubscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.FIXEDTERM);

        //CREATE AO PLAN
        final String aoPlanName = "refurbish-maintenance-6-months";
        DefaultSubscriptionBase aoSubscription = testUtil.createSubscription(bundle, aoPlanName);
        assertNotNull(aoSubscription);
        assertEquals(aoSubscription.getState(), EntitlementState.ACTIVE);

        currentPlan = aoSubscription.getCurrentPlan();
        assertNotNull(currentPlan);
        assertEquals(currentPlan.getProduct().getCategory(), ProductCategory.ADD_ON);
        assertEquals(currentPlan.getRecurringBillingPeriod(), BillingPeriod.MONTHLY);

        currentPhase = aoSubscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.FIXEDTERM);

        //MOVE PAST ADDON FIXEDTERM PHASE AND CHECK THAT ADDON IS EXPIRED BUT BASE SUBSCRIPTION IS ACTIVE
        testListener.pushExpectedEvents(NextEvent.EXPIRED);
        Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(6));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertListenerStatus();

        aoSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(aoSubscription.getId(), false, internalCallContext);
        assertEquals(aoSubscription.getState(), EntitlementState.EXPIRED);
        baseSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(baseSubscription.getId(), false, internalCallContext);
        assertEquals(baseSubscription.getState(), EntitlementState.ACTIVE);

        //MOVE PAST BASE SUBSCRIPTION FIXEDTERM PHASE AND CHECK THAT IT IS EXPIRED
        testListener.pushExpectedEvents(NextEvent.EXPIRED);
        it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(6));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertListenerStatus();

        baseSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(baseSubscription.getId(), false, internalCallContext);
        assertEquals(baseSubscription.getState(), EntitlementState.EXPIRED);
    }

    @Test(groups = "slow")
    public void testCreateFixedTermBPWithFixedTermAddonAddOnExpiryAfterBasePlan() throws SubscriptionBaseApiException {
        final String basePlanName = "pistol-monthly-fixedterm-no-trial";
        // CREATE BP
        DefaultSubscriptionBase baseSubscription = testUtil.createSubscription(bundle, basePlanName);
        assertNotNull(baseSubscription);
        assertEquals(baseSubscription.getState(), EntitlementState.ACTIVE);

        Plan currentPlan = baseSubscription.getCurrentPlan();
        assertNotNull(currentPlan);
        assertEquals(currentPlan.getProduct().getCategory(), ProductCategory.BASE);
        assertEquals(currentPlan.getRecurringBillingPeriod(), BillingPeriod.MONTHLY);

        PlanPhase currentPhase = baseSubscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.FIXEDTERM);

        //CREATE AO PLAN
        final String aoPlanName = "refurbish-maintenance-15-months";
        DefaultSubscriptionBase aoSubscription = testUtil.createSubscription(bundle, aoPlanName);
        assertNotNull(aoSubscription);
        assertEquals(aoSubscription.getState(), EntitlementState.ACTIVE);

        currentPlan = aoSubscription.getCurrentPlan();
        assertNotNull(currentPlan);
        assertEquals(currentPlan.getProduct().getCategory(), ProductCategory.ADD_ON);
        assertEquals(currentPlan.getRecurringBillingPeriod(), BillingPeriod.MONTHLY);

        currentPhase = aoSubscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.FIXEDTERM);

        //MOVE PAST BASE FIXEDTERM PHASE AND CHECK THAT BOTH SUBSCRIPTIONS ARE EXPIRED
        testListener.pushExpectedEvents(NextEvent.EXPIRED, NextEvent.EXPIRED);
        Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(12));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertListenerStatus();

        baseSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(baseSubscription.getId(), false, internalCallContext);
        assertEquals(baseSubscription.getState(), EntitlementState.EXPIRED);
        aoSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(aoSubscription.getId(), false, internalCallContext);
        assertEquals(aoSubscription.getState(), EntitlementState.EXPIRED);

        //MOVE PAST ADDON FIXEDTERM PHASE AND CHECK THAT THERE ARE NO MORE EVENTS WHEN ADDON EXPIRES
        it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(3));
        clock.addDeltaFromReality(it.toDurationMillis());
        final List<SubscriptionBaseEvent> events = dao.getPendingEventsForSubscription(baseSubscription.getId(), internalCallContext);
        assertNotNull(events);
        testUtil.printEvents(events);
        assertTrue(events.size() == 0);
    }

    @Test(groups = "slow")
    public void testCreateFixedTermBPWithFixedTermAddonAddOnExpiryOnSameDayAsBasePlan() throws SubscriptionBaseApiException {
        final String basePlanName = "pistol-monthly-fixedterm-no-trial";
        // CREATE BP
        DefaultSubscriptionBase baseSubscription = testUtil.createSubscription(bundle, basePlanName);
        assertNotNull(baseSubscription);
        assertEquals(baseSubscription.getState(), EntitlementState.ACTIVE);

        Plan currentPlan = baseSubscription.getCurrentPlan();
        assertNotNull(currentPlan);
        assertEquals(currentPlan.getProduct().getCategory(), ProductCategory.BASE);
        assertEquals(currentPlan.getRecurringBillingPeriod(), BillingPeriod.MONTHLY);

        PlanPhase currentPhase = baseSubscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.FIXEDTERM);

        //CREATE AO PLAN
        final String aoPlanName = "refurbish-maintenance";
        DefaultSubscriptionBase aoSubscription = testUtil.createSubscription(bundle, aoPlanName);
        assertNotNull(aoSubscription);
        assertEquals(aoSubscription.getState(), EntitlementState.ACTIVE);

        currentPlan = aoSubscription.getCurrentPlan();
        assertNotNull(currentPlan);
        assertEquals(currentPlan.getProduct().getCategory(), ProductCategory.ADD_ON);
        assertEquals(currentPlan.getRecurringBillingPeriod(), BillingPeriod.MONTHLY);

        currentPhase = aoSubscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.FIXEDTERM);

        //MOVE PAST FIXEDTERM PHASE OF BOTH PLANS AND CHECK THAT THEY ARE EXPIRED
        testListener.pushExpectedEvents(NextEvent.EXPIRED, NextEvent.EXPIRED);
        final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(12));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertListenerStatus();

        baseSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(baseSubscription.getId(), false, internalCallContext);
        assertEquals(baseSubscription.getState(), EntitlementState.EXPIRED);
        aoSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(aoSubscription.getId(), false, internalCallContext);
        assertEquals(aoSubscription.getState(), EntitlementState.EXPIRED);
    }

    @Test(groups = "slow")
    public void testCancelSubscriptionBeforeExpiry() throws SubscriptionBaseApiException {

        //CREATE FIXEDTERM SUBCRIPTION AND VERIFY THAT EXPIRED EVENT IS PRESENT
        final String planName = "pistol-biennial-fixedterm";

        DefaultSubscriptionBase subscription = testUtil.createSubscription(bundle, planName);
        assertNotNull(subscription);
        assertEquals(subscription.getState(), EntitlementState.ACTIVE);

        final PlanPhase currentPhase = subscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.FIXEDTERM);

        List<SubscriptionBaseEvent> events = dao.getPendingEventsForSubscription(subscription.getId(), internalCallContext);
        assertNotNull(events);
        testUtil.printEvents(events);
        assertTrue(events.size() == 1);
        assertTrue(events.get(0) instanceof ExpiredEvent);
        final DateTime expiryDate = events.get(0).getEffectiveDate();
        final DateTime expectedExpiryDate = TestSubscriptionHelper.addDuration(subscription.getStartDate(), currentPhase.getDuration());
        assertEquals(expiryDate, expectedExpiryDate);

        //MOVE CLOCK BY FEW MONTHS
        final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(12));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertEquals(subscription.getState(), EntitlementState.ACTIVE);

        //CANCEL SUBSCRIPTION AND VERIFY THAT EXPIRED EVENT IS NOT PRESENT
        testListener.pushExpectedEvents(NextEvent.CANCEL);
        subscription.cancel(callContext);

        subscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(subscription.getId(), false, internalCallContext);
        assertEquals(subscription.getState(), EntitlementState.CANCELLED);
        assertListenerStatus();

        events = dao.getPendingEventsForSubscription(subscription.getId(), internalCallContext);
        assertNotNull(events);
        testUtil.printEvents(events);
        assertTrue(events.size() == 0);
    }

    @Test(groups = "slow")
    public void testCancelSubscriptionBeforeExpiryWithDatePastExpiryDate() throws SubscriptionBaseApiException {

        //CREATE FIXEDTERM SUBCRIPTION AND VERIFY THAT EXPIRED EVENT IS PRESENT
        final String planName = "pistol-monthly-fixedterm-no-trial";

        final DefaultSubscriptionBase subscription = testUtil.createSubscription(bundle, planName);
        assertNotNull(subscription);
        assertEquals(subscription.getState(), EntitlementState.ACTIVE);

        final PlanPhase currentPhase = subscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.FIXEDTERM);

        final List<SubscriptionBaseEvent> events = dao.getPendingEventsForSubscription(subscription.getId(), internalCallContext);
        assertNotNull(events);
        testUtil.printEvents(events);
        assertTrue(events.size() == 1);
        assertTrue(events.get(0) instanceof ExpiredEvent);
        final DateTime expiryDate = events.get(0).getEffectiveDate();
        final DateTime expectedExpiryDate = TestSubscriptionHelper.addDuration(subscription.getStartDate(), currentPhase.getDuration());
        assertEquals(expiryDate, expectedExpiryDate);

        //CANCEL SUBSCRIPTION WITH DATE AFTER THE EXPIRY DATE AND VERIFY THAT IT FAILS 
        try {
            subscription.cancelWithDate(clock.getUTCNow().plusMonths(13), callContext);
            Assert.fail("Cancellation should fail as subscription is expired");
        } catch (final SubscriptionBaseApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.SUB_CANCEL_BAD_STATE.getCode());
        }
    }

    @Test(groups = "slow")
    public void testCancelSubscriptionBeforeExpiryWithPolicy() throws SubscriptionBaseApiException {

        //CREATE FIXEDTERM SUBCRIPTION AND VERIFY THAT EXPIRED EVENT IS PRESENT
        final String planName = "pistol-biennial-fixedterm";

        DefaultSubscriptionBase subscription = testUtil.createSubscription(bundle, planName);
        assertNotNull(subscription);
        assertEquals(subscription.getState(), EntitlementState.ACTIVE);

        final PlanPhase currentPhase = subscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.FIXEDTERM);

        List<SubscriptionBaseEvent> events = dao.getPendingEventsForSubscription(subscription.getId(), internalCallContext);
        assertNotNull(events);
        testUtil.printEvents(events);
        assertTrue(events.size() == 1);
        assertTrue(events.get(0) instanceof ExpiredEvent);
        final DateTime expiryDate = events.get(0).getEffectiveDate();
        final DateTime expectedExpiryDate = TestSubscriptionHelper.addDuration(subscription.getStartDate(), currentPhase.getDuration());
        assertEquals(expiryDate, expectedExpiryDate);

        //MOVE CLOCK BY FEW MONTHS
        final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(12));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertEquals(subscription.getState(), EntitlementState.ACTIVE);

        //CANCEL SUBSCRIPTION WITH POLICY AND VERIFY THAT EXPIRED EVENT IS NOT PRESENT
        testListener.pushExpectedEvents(NextEvent.CANCEL);
        subscription.cancelWithPolicy(BillingActionPolicy.END_OF_TERM, callContext);

        subscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(subscription.getId(), false, internalCallContext);
        assertEquals(subscription.getState(), EntitlementState.CANCELLED);
        assertListenerStatus();

        events = dao.getPendingEventsForSubscription(subscription.getId(), internalCallContext);
        assertNotNull(events);
        testUtil.printEvents(events);
        assertTrue(events.size() == 0);
    }

    @Test(groups = "slow")
    public void testCancelSubscriptionAfterExpiry() throws SubscriptionBaseApiException {

        //CREATE FIXEDTERM SUBCRIPTION AND VERIFY THAT EXPIRED EVENT IS PRESENT
        final String planName = "pistol-biennial-fixedterm";

        DefaultSubscriptionBase subscription = testUtil.createSubscription(bundle, planName);
        assertNotNull(subscription);
        assertEquals(subscription.getState(), EntitlementState.ACTIVE);

        final PlanPhase currentPhase = subscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.FIXEDTERM);

        final List<SubscriptionBaseEvent> events = dao.getPendingEventsForSubscription(subscription.getId(), internalCallContext);
        assertNotNull(events);
        testUtil.printEvents(events);
        assertTrue(events.size() == 1);
        assertTrue(events.get(0) instanceof ExpiredEvent);
        final DateTime expiryDate = events.get(0).getEffectiveDate();
        final DateTime expectedExpiryDate = TestSubscriptionHelper.addDuration(subscription.getStartDate(), currentPhase.getDuration());
        assertEquals(expiryDate, expectedExpiryDate);

        //MOVE PAST FIXEDTERM PHASE
        testListener.pushExpectedEvent(NextEvent.EXPIRED);
        Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(36));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertListenerStatus();

        // REFETCH SUBSCRIPTION AND CHECK THAT IT IS EXPIRED
        subscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(subscription.getId(), false, internalCallContext);
        assertEquals(subscription.getState(), EntitlementState.EXPIRED);

        //MOVE CLOCK BY FEW MONTHS
        it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(12));
        clock.addDeltaFromReality(it.toDurationMillis());

        //CANCEL SUBSCRIPTION AND VERIFY THAT IT FAILS SINCE SUBSCRIPTION IS EXPIRED
        try {
            subscription.cancel(callContext);
            Assert.fail("Cancellation should fail as subscription is expired");
        } catch (final SubscriptionBaseApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.SUB_CANCEL_BAD_STATE.getCode());
        }
    }

    @Test(groups = "slow")
    public void testCancelSubscriptionWithAddOnBeforeBaseAndAddOnExpiry() throws SubscriptionBaseApiException {
        final String basePlanName = "pistol-monthly-fixedterm-no-trial";
        // CREATE BP AND VERIFY THAT EXPIRED EVENT IS PRESENT
        DefaultSubscriptionBase baseSubscription = testUtil.createSubscription(bundle, basePlanName);
        assertNotNull(baseSubscription);
        assertEquals(baseSubscription.getState(), EntitlementState.ACTIVE);

        PlanPhase currentPhase = baseSubscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.FIXEDTERM);

        List<SubscriptionBaseEvent> events = dao.getPendingEventsForSubscription(baseSubscription.getId(), internalCallContext);
        assertNotNull(events);
        testUtil.printEvents(events);
        assertTrue(events.size() == 1);
        assertTrue(events.get(0) instanceof ExpiredEvent);
        DateTime expiryDate = events.get(0).getEffectiveDate();
        DateTime expectedExpiryDate = TestSubscriptionHelper.addDuration(baseSubscription.getStartDate(), currentPhase.getDuration());
        assertEquals(expiryDate, expectedExpiryDate);

        //CREATE AO PLAN AND VERIFY THAT EXPIRED EVENT IS PRESENT
        final String aoPlanName = "refurbish-maintenance-6-months";
        DefaultSubscriptionBase aoSubscription = testUtil.createSubscription(bundle, aoPlanName);
        assertNotNull(aoSubscription);
        assertEquals(aoSubscription.getState(), EntitlementState.ACTIVE);

        currentPhase = aoSubscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.FIXEDTERM);

        events = dao.getPendingEventsForSubscription(aoSubscription.getId(), internalCallContext);
        assertNotNull(events);
        testUtil.printEvents(events);
        assertTrue(events.size() == 1);
        assertTrue(events.get(0) instanceof ExpiredEvent);
        expiryDate = events.get(0).getEffectiveDate();
        expectedExpiryDate = TestSubscriptionHelper.addDuration(aoSubscription.getStartDate(), currentPhase.getDuration());
        assertEquals(expiryDate, expectedExpiryDate);

        //MOVE CLOCK BY 2 MONTHS
        final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(2));
        clock.addDeltaFromReality(it.toDurationMillis());

        //CANCEL BASE SUBSCRIPTION 
        testListener.pushExpectedEvents(NextEvent.CANCEL, NextEvent.CANCEL);
        baseSubscription.cancel(callContext);
        assertListenerStatus();

        //VERIFY THAT BASE SUBSCRIPTION IS CANCELLED AND EXPIRED EVENT IS NOT PRESENT
        baseSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(baseSubscription.getId(), false, internalCallContext);
        assertEquals(baseSubscription.getState(), EntitlementState.CANCELLED);

        events = dao.getPendingEventsForSubscription(baseSubscription.getId(), internalCallContext);
        assertNotNull(events);
        testUtil.printEvents(events);
        assertTrue(events.size() == 0);

        //VERIFY THAT ADDON SUBSCRIPTION IS CANCELLED AND EXPIRED EVENT IS NOT PRESENT
        aoSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(aoSubscription.getId(), false, internalCallContext);
        assertEquals(aoSubscription.getState(), EntitlementState.CANCELLED);

        events = dao.getPendingEventsForSubscription(aoSubscription.getId(), internalCallContext);
        assertNotNull(events);
        testUtil.printEvents(events);
        assertTrue(events.size() == 0);
    }

    @Test(groups = "slow")
    public void testCancelSubscriptionWithAddOnAfterAddOnExpiry() throws SubscriptionBaseApiException {
        final String basePlanName = "pistol-monthly-fixedterm-no-trial";
        // CREATE BP
        DefaultSubscriptionBase baseSubscription = testUtil.createSubscription(bundle, basePlanName);
        assertNotNull(baseSubscription);
        assertEquals(baseSubscription.getState(), EntitlementState.ACTIVE);

        PlanPhase currentPhase = baseSubscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.FIXEDTERM);

        //CREATE AO PLAN
        final String aoPlanName = "refurbish-maintenance-6-months";
        DefaultSubscriptionBase aoSubscription = testUtil.createSubscription(bundle, aoPlanName);
        assertNotNull(aoSubscription);
        assertEquals(aoSubscription.getState(), EntitlementState.ACTIVE);

        currentPhase = aoSubscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.FIXEDTERM);

        //MOVE CLOCK BY 6 MONTHS AND VERIFY THAT ADDON IS EXPIRED
        testListener.pushExpectedEvents(NextEvent.EXPIRED);
        Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(6));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertListenerStatus();

        aoSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(aoSubscription.getId(), false, internalCallContext);
        assertEquals(aoSubscription.getState(), EntitlementState.EXPIRED);
        baseSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(baseSubscription.getId(), false, internalCallContext);
        assertEquals(baseSubscription.getState(), EntitlementState.ACTIVE);

        //MOVE CLOCK BY 2 MONTHS
        it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(2));
        clock.addDeltaFromReality(it.toDurationMillis());

        //CANCEL BASE SUBSCRIPTION 
        testListener.pushExpectedEvents(NextEvent.CANCEL);
        baseSubscription.cancel(callContext);
        assertListenerStatus();

        //VERFIY THAT BASE SUBSCIPTION IS CANCELLED AND EXPIRED EVENT IS NOT PRESENT
        baseSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(baseSubscription.getId(), false, internalCallContext);
        assertEquals(baseSubscription.getState(), EntitlementState.CANCELLED);
        final List<SubscriptionBaseEvent> events = dao.getPendingEventsForSubscription(baseSubscription.getId(), internalCallContext);
        assertNotNull(events);
        testUtil.printEvents(events);
        assertTrue(events.size() == 0);

        //VERIFY THAT ADDON REMAINS EXPIRED
        aoSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(aoSubscription.getId(), false, internalCallContext);
        assertEquals(aoSubscription.getState(), EntitlementState.EXPIRED);

    }


    @Test(groups = "slow")
    public void testChangePlanFromFixedTermToEverGreenBeforeExpiry() throws SubscriptionBaseApiException {

        //CREATE FIXEDTERM SUBCRIPTION AND VERIFY THAT EXPIRED EVENT IS PRESENT
        final String planName = "pistol-monthly-fixedterm-no-trial";

        final DefaultSubscriptionBase subscription = testUtil.createSubscription(bundle, planName);
        assertNotNull(subscription);
        assertEquals(subscription.getState(), EntitlementState.ACTIVE);

        PlanPhase currentPhase = subscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.FIXEDTERM);

        List<SubscriptionBaseEvent> events = dao.getPendingEventsForSubscription(subscription.getId(), internalCallContext);
        assertNotNull(events);
        testUtil.printEvents(events);
        assertTrue(events.size() == 1);
        assertTrue(events.get(0) instanceof ExpiredEvent);
        final DateTime expiryDate = events.get(0).getEffectiveDate();
        final DateTime expectedExpiryDate = TestSubscriptionHelper.addDuration(subscription.getStartDate(), currentPhase.getDuration());
        assertEquals(expiryDate, expectedExpiryDate);

        //MOVE CLOCK BY 2 MONTHS
        final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(2));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertEquals(subscription.getState(), EntitlementState.ACTIVE);

        //CHANGE TO PLAN WITH EVERGREEN PHASE AND VERIFY THAT EXPIRED EVENT IS NOT PRESENT
        testListener.pushExpectedEvent(NextEvent.CHANGE);
        final PlanPhaseSpecifier planPhaseSpecifier = new PlanPhaseSpecifier("pistol-monthly-notrial");
        subscription.changePlan(new DefaultEntitlementSpecifier(planPhaseSpecifier), callContext);
        assertListenerStatus();

        currentPhase = subscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.EVERGREEN);
        assertEquals(subscription.getState(), EntitlementState.ACTIVE);

        events = dao.getPendingEventsForSubscription(subscription.getId(), internalCallContext);
        assertNotNull(events);
        testUtil.printEvents(events);
        assertTrue(events.size() == 0);

    }

    @Test(groups = "slow")
    public void testChangePlanBeforeExpiryWithDatePastExpiryDate() throws SubscriptionBaseApiException {

        //CREATE FIXEDTERM SUBCRIPTION AND VERIFY THAT EXPIRED EVENT IS PRESENT
        final String planName = "pistol-monthly-fixedterm-no-trial";

        final DefaultSubscriptionBase subscription = testUtil.createSubscription(bundle, planName);
        assertNotNull(subscription);
        assertEquals(subscription.getState(), EntitlementState.ACTIVE);

        final PlanPhase currentPhase = subscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.FIXEDTERM);

        final List<SubscriptionBaseEvent> events = dao.getPendingEventsForSubscription(subscription.getId(), internalCallContext);
        assertNotNull(events);
        testUtil.printEvents(events);
        assertTrue(events.size() == 1);
        assertTrue(events.get(0) instanceof ExpiredEvent);
        final DateTime expiryDate = events.get(0).getEffectiveDate();
        final DateTime expectedExpiryDate = TestSubscriptionHelper.addDuration(subscription.getStartDate(), currentPhase.getDuration());
        assertEquals(expiryDate, expectedExpiryDate);

        //MOVE CLOCK BY FEW MONTHS
        final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(2));
        clock.addDeltaFromReality(it.toDurationMillis());

        //CHANGE SUBSCRIPTION PLAN WITH DATE PAST EXPIRY DATE AND VERIFY THAT IT FAILS SINCE SUBSCRIPTION IS EXPIRED
        try {
            final PlanPhaseSpecifier planPhaseSpecifier = new PlanPhaseSpecifier("pistol-monthly-fixedterm-no-trial-8-months");
            subscription.changePlanWithDate(new DefaultEntitlementSpecifier(planPhaseSpecifier), clock.getUTCNow().plusMonths(13), callContext);
            Assert.fail("Change plan should fail as date is past expiry date");
        } catch (final SubscriptionBaseApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.SUB_CHANGE_FUTURE_EXPIRED.getCode());
        }
    }

    @Test(groups = "slow")
    public void testChangePlanBeforeExpiryWithPolicy() throws SubscriptionBaseApiException {

        //CREATE FIXEDTERM SUBCRIPTION AND VERIFY THAT EXPIRED EVENT IS PRESENT
        final String planName = "pistol-monthly-fixedterm-no-trial";

        final DefaultSubscriptionBase subscription = testUtil.createSubscription(bundle, planName);
        assertNotNull(subscription);
        assertEquals(subscription.getState(), EntitlementState.ACTIVE);

        PlanPhase currentPhase = subscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.FIXEDTERM);

        List<SubscriptionBaseEvent> events = dao.getPendingEventsForSubscription(subscription.getId(), internalCallContext);
        assertNotNull(events);
        testUtil.printEvents(events);
        assertTrue(events.size() == 1);
        assertTrue(events.get(0) instanceof ExpiredEvent);
        final DateTime expiryDate = events.get(0).getEffectiveDate();
        final DateTime expectedExpiryDate = TestSubscriptionHelper.addDuration(subscription.getStartDate(), currentPhase.getDuration());
        assertEquals(expiryDate, expectedExpiryDate);

        //MOVE CLOCK BY 2 MONTHS
        final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(2));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertEquals(subscription.getState(), EntitlementState.ACTIVE);

        //CHANGE TO PLAN WITH EVERGREEN PHASE WITH POLICY AND VERIFY THAT EXPIRED EVENT IS NOT PRESENT
        testListener.pushExpectedEvent(NextEvent.CHANGE);
        final PlanPhaseSpecifier planPhaseSpecifier = new PlanPhaseSpecifier("pistol-monthly-notrial");
        subscription.changePlanWithPolicy(new DefaultEntitlementSpecifier(planPhaseSpecifier), BillingActionPolicy.IMMEDIATE, callContext);
        assertListenerStatus();

        currentPhase = subscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.EVERGREEN);
        assertEquals(subscription.getState(), EntitlementState.ACTIVE);

        events = dao.getPendingEventsForSubscription(subscription.getId(), internalCallContext);
        assertNotNull(events);
        testUtil.printEvents(events);
        assertTrue(events.size() == 0);

    }

    @Test(groups = "slow")
    public void testChangePlanAfterExpiry() throws SubscriptionBaseApiException {

        //CREATE FIXEDTERM SUBCRIPTION AND VERIFY THAT EXPIRED EVENT IS PRESENT
        final String planName = "pistol-monthly-fixedterm-no-trial";

        DefaultSubscriptionBase subscription = testUtil.createSubscription(bundle, planName);
        assertNotNull(subscription);
        assertEquals(subscription.getState(), EntitlementState.ACTIVE);

        final PlanPhase currentPhase = subscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.FIXEDTERM);

        final List<SubscriptionBaseEvent> events = dao.getPendingEventsForSubscription(subscription.getId(), internalCallContext);
        assertNotNull(events);
        testUtil.printEvents(events);
        assertTrue(events.size() == 1);
        assertTrue(events.get(0) instanceof ExpiredEvent);
        final DateTime expiryDate = events.get(0).getEffectiveDate();
        final DateTime expectedExpiryDate = TestSubscriptionHelper.addDuration(subscription.getStartDate(), currentPhase.getDuration());
        assertEquals(expiryDate, expectedExpiryDate);

        //MOVE PAST FIXEDTERM PHASE
        testListener.pushExpectedEvent(NextEvent.EXPIRED);
        Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(12));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertListenerStatus();

        // REFETCH SUBSCRIPTION AND CHECK THAT IT IS EXPIRED
        subscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(subscription.getId(), false, internalCallContext);
        assertEquals(subscription.getState(), EntitlementState.EXPIRED);

        //MOVE CLOCK BY FEW MONTHS
        it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(2));
        clock.addDeltaFromReality(it.toDurationMillis());

        //CHANGE SUBSCRIPTION PLAN AND VERIFY THAT IT FAILS SINCE SUBSCRIPTION IS EXPIRED
        try {
            final PlanPhaseSpecifier planPhaseSpecifier = new PlanPhaseSpecifier("pistol-monthly-fixedterm-no-trial-8-months");
            subscription.changePlan(new DefaultEntitlementSpecifier(planPhaseSpecifier), callContext);
            Assert.fail("Change plan should fail as subscription is expired");
        } catch (final SubscriptionBaseApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.SUB_CHANGE_NON_ACTIVE.getCode());
        }
    }

    @Test(groups = "slow")
    public void testChangePlanFromFromFixedTermToFixedTermWithDifferentExpiry() throws SubscriptionBaseApiException {

        //CREATE FIXEDTERM SUBCRIPTION AND VERIFY THAT EXPIRED EVENT IS PRESENT
        final String planName = "pistol-monthly-fixedterm-no-trial";

        DefaultSubscriptionBase subscription = testUtil.createSubscription(bundle, planName);
        assertNotNull(subscription);
        assertEquals(subscription.getState(), EntitlementState.ACTIVE);

        PlanPhase currentPhase = subscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.FIXEDTERM);

        List<SubscriptionBaseEvent> events = dao.getPendingEventsForSubscription(subscription.getId(), internalCallContext);
        assertNotNull(events);
        testUtil.printEvents(events);
        assertTrue(events.size() == 1);
        assertTrue(events.get(0) instanceof ExpiredEvent);
        DateTime expiryDate = events.get(0).getEffectiveDate();
        DateTime expectedExpiryDate = TestSubscriptionHelper.addDuration(subscription.getCurrentPhaseStart(), currentPhase.getDuration());
        assertEquals(expiryDate, expectedExpiryDate);

        //MOVE CLOCK BY 2 MONTHS
        Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(2));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertEquals(subscription.getState(), EntitlementState.ACTIVE);

        //CHANGE PLAN TO PLAN WITH FIXEDTERM PHASE BUT WITH A DIFFERENT EXPIRY AND VERIFY THAT EXPIRED EVENT IS PRESENT WITH NEW EFFECTIVE DATE
        testListener.pushExpectedEvent(NextEvent.CHANGE);
        final PlanPhaseSpecifier planPhaseSpecifier = new PlanPhaseSpecifier("pistol-monthly-fixedterm-no-trial-8-months");
        subscription.changePlan(new DefaultEntitlementSpecifier(planPhaseSpecifier), callContext);
        assertListenerStatus();

        currentPhase = subscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.FIXEDTERM);
        assertListenerStatus();

        events = dao.getPendingEventsForSubscription(subscription.getId(), internalCallContext);
        assertNotNull(events);
        testUtil.printEvents(events);
        assertTrue(events.size() == 1);
        assertTrue(events.get(0) instanceof ExpiredEvent);
        expiryDate = events.get(0).getEffectiveDate();
        // SINCE THE CATALOG IS CONFIGURED TO USE START_OF_SUBSCRIPTION ALIGNMENT, THE FIXEDTERM PHASE STARTS ON THE DAY THE SUBSCRIPTION IS CREATED AND EXPIRY DATE IS CALCULATED ACCORDINGLY
        expectedExpiryDate = TestSubscriptionHelper.addDuration(subscription.getStartDate(), currentPhase.getDuration());
        assertEquals(expiryDate, expectedExpiryDate);

        //MOVE PAST FIXEDTERM PHASE
        testListener.pushExpectedEvent(NextEvent.EXPIRED);
        it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(8));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertListenerStatus();

        // REFETCH SUBSCRIPTION AND CHECK THAT IT IS EXPIRED
        subscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(subscription.getId(), false, internalCallContext);
        assertEquals(subscription.getState(), EntitlementState.EXPIRED);

    }

    @Test(groups = "slow")
    public void testChangePlanFromEverGreenToFixedTermWithChangeOfPlanAlignment() throws SubscriptionBaseApiException {

        //CREATE SUBSCRIPTION TO PLAN WITH EVERGREEN PHASE
        final String planName = "pistol-monthly-notrial";

        DefaultSubscriptionBase subscription = testUtil.createSubscription(bundle, planName);
        assertNotNull(subscription);
        assertEquals(subscription.getState(), EntitlementState.ACTIVE);

        PlanPhase currentPhase = subscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.EVERGREEN);

        //MOVE CLOCK BY 2 MONTHS
        Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(2));
        clock.addDeltaFromReality(it.toDurationMillis());
        final DateTime changeDate = clock.getUTCNow();
        assertEquals(subscription.getState(), EntitlementState.ACTIVE);

        //CHANGE PLAN TO PLAN WITH FIXEDTERM PHASE SUCH THAT CHANGE_OF_PLAN ALIGNMENT IS USED AND VERFIY THAT EXPIRED EVENT IS PRESENT
        testListener.pushExpectedEvent(NextEvent.CHANGE);
        final PlanPhaseSpecifier planPhaseSpecifier = new PlanPhaseSpecifier("pistol-monthly-fixedterm-no-trial");
        subscription.changePlan(new DefaultEntitlementSpecifier(planPhaseSpecifier), callContext);
        assertListenerStatus();

        currentPhase = subscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.FIXEDTERM);
        assertListenerStatus();

        final List<SubscriptionBaseEvent> events = dao.getPendingEventsForSubscription(subscription.getId(), internalCallContext);
        assertNotNull(events);
        testUtil.printEvents(events);
        assertTrue(events.size() == 1);
        assertTrue(events.get(0) instanceof ExpiredEvent);
        final DateTime expiryDate = events.get(0).getEffectiveDate();
        // SINCE THE CATALOG IS CONFIGURED TO USE CHANGE_OF_PLAN ALIGNMENT, THE FIXEDTERM PHASE STARTS ON THE DAY THE PLAN IS CHANGED AND EXPIRY DATE IS CALCULATED ACCORDINGLY
        final DateTime expectedExpiryDate = TestSubscriptionHelper.addDuration(changeDate, currentPhase.getDuration());
        assertTrue(Math.abs(expiryDate.getMillis() - expectedExpiryDate.getMillis()) <= 1000,
                   "Expiry date is off by more than 1 second");


        //MOVE PAST FIXEDTERM PHASE
        testListener.pushExpectedEvent(NextEvent.EXPIRED);
        it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(12));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertListenerStatus();

        // REFETCH SUBSCRIPTION AND CHECK THAT IT IS EXPIRED
        subscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(subscription.getId(), false, internalCallContext);
        assertEquals(subscription.getState(), EntitlementState.EXPIRED);

    }

}
