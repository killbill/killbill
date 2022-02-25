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
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.subscription.SubscriptionTestSuiteWithEmbeddedDB;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent;
import org.killbill.billing.subscription.events.expired.ExpiredEvent;
import org.killbill.billing.subscription.events.phase.PhaseEvent;
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
        assertListenerStatus();

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
        subscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(subscription.getId(), internalCallContext);
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
        assertListenerStatus();

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

        subscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(subscription.getId(), internalCallContext);
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
        subscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(subscription.getId(), internalCallContext);
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
        assertListenerStatus();

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

        subscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(subscription.getId(), internalCallContext);
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

        subscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(subscription.getId(), internalCallContext); //TODO_1533: Why is it necessary to reread subscription here? Without this, the phase is still DISCOUNT, so test fails
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

        subscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(subscription.getId(), internalCallContext);
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
        assertListenerStatus();

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

        subscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(subscription.getId(), internalCallContext);
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

        subscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(subscription.getId(), internalCallContext);
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
        assertListenerStatus();

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
        subscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(subscription.getId(), internalCallContext);
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

        currentPhase = baseSubscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.FIXEDTERM);

        //MOVE PAST ADDON FIXEDTERM PHASE AND CHECK THAT ADDON IS EXPIRED BUT BASE SUBSCRIPTION IS ACTIVE
        testListener.pushExpectedEvents(NextEvent.EXPIRED);
        Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(6));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertListenerStatus();

        aoSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(aoSubscription.getId(), internalCallContext);
        assertEquals(aoSubscription.getState(), EntitlementState.EXPIRED);
        baseSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(baseSubscription.getId(), internalCallContext);
        assertEquals(baseSubscription.getState(), EntitlementState.ACTIVE);

        //MOVE PAST BASE SUBSCRIPTION FIXEDTERM PHASE AND CHECK THAT IT IS EXPIRED
        testListener.pushExpectedEvents(NextEvent.EXPIRED);
        it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(6));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertListenerStatus();

        baseSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(baseSubscription.getId(), internalCallContext);
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

        currentPhase = baseSubscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.FIXEDTERM);

        //MOVE PAST BASE FIXEDTERM PHASE AND CHECK THAT BOTH SUBSCRIPTIONS ARE EXPIRED
        testListener.pushExpectedEvents(NextEvent.EXPIRED, NextEvent.EXPIRED);
        Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(12));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertListenerStatus();

        baseSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(baseSubscription.getId(), internalCallContext);
        assertEquals(baseSubscription.getState(), EntitlementState.EXPIRED);
        aoSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(aoSubscription.getId(), internalCallContext);
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

        currentPhase = baseSubscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.FIXEDTERM);

        //MOVE PAST FIXEDTERM PHASE OF BOTH PLANS AND CHECK THAT THEY ARE EXPIRED
        testListener.pushExpectedEvents(NextEvent.EXPIRED, NextEvent.EXPIRED);
        final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(12));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertListenerStatus();

        baseSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(baseSubscription.getId(), internalCallContext);
        assertEquals(baseSubscription.getState(), EntitlementState.EXPIRED);
        aoSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(aoSubscription.getId(), internalCallContext);
        assertEquals(aoSubscription.getState(), EntitlementState.EXPIRED);
    }

}
