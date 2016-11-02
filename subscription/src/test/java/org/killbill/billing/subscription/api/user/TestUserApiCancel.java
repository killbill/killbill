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

package org.killbill.billing.subscription.api.user;

import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.entity.EntityPersistenceException;
import org.killbill.billing.subscription.engine.dao.SubscriptionEventSqlDao;
import org.killbill.billing.subscription.engine.dao.model.SubscriptionEventModelDao;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent;
import org.skife.jdbi.v2.Handle;
import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Duration;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.subscription.SubscriptionTestSuiteWithEmbeddedDB;
import org.killbill.billing.subscription.api.SubscriptionBillingApiException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestUserApiCancel extends SubscriptionTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testCancelSubscriptionIMM() throws SubscriptionBaseApiException {
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
        assertListenerStatus();

        assertEquals(subscription.getLastActiveProduct().getName(), prod);
        assertEquals(subscription.getLastActivePriceList().getName(), planSet);
        assertEquals(subscription.getLastActiveBillingPeriod(), term);
        assertEquals(subscription.getLastActiveCategory(), ProductCategory.BASE);

        assertNull(currentPhase);
        testUtil.checkNextPhaseChange(subscription, 0, null);

        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testCancelSubscriptionEOTWithChargeThroughDate() throws SubscriptionBillingApiException, SubscriptionBaseApiException {
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
        subscription.cancel(callContext);
        assertListenerStatus();

        // CANCEL a second time (first pending CANCEL should be made inactive)
        subscription.cancel(callContext);
        assertListenerStatus();

        assertEquals(subscription.getLastActiveProduct().getName(), prod);
        assertEquals(subscription.getLastActivePriceList().getName(), planSet);
        assertEquals(subscription.getLastActiveBillingPeriod(), term);
        assertEquals(subscription.getLastActiveCategory(), ProductCategory.BASE);

        final DateTime futureEndDate = subscription.getFutureEndDate();
        Assert.assertNotNull(futureEndDate);

        // MOVE TO EOT + RECHECK
        testListener.pushExpectedEvents(NextEvent.CANCEL);
        it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(1));
        clock.addDeltaFromReality(it.toDurationMillis());
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
    }

    @Test(groups = "slow")
    public void testCancelSubscriptionEOTWithNoChargeThroughDate() throws SubscriptionBaseApiException {
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
    }

    // Similar test to testCancelSubscriptionEOTWithChargeThroughDate except we uncancel and check things
    // are as they used to be and we can move forward without hitting cancellation
    @Test(groups = "slow")
    public void testUncancel() throws SubscriptionBillingApiException, SubscriptionBaseApiException {
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
    }

    @Test(groups = "slow", expectedExceptions = SubscriptionBaseApiException.class)
    public void testCancelSubscriptionWithInvalidRequestedDate() throws SubscriptionBaseApiException {
        final String prod = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final String planSet = PriceListSet.DEFAULT_PRICELIST_NAME;

        // CREATE
        final DefaultSubscriptionBase subscription = testUtil.createSubscription(bundle, prod, term, planSet);
        PlanPhase currentPhase = subscription.getCurrentPhase();
        assertEquals(currentPhase.getPhaseType(), PhaseType.TRIAL);

        // MOVE TO NEXT PHASE
        testListener.pushExpectedEvent(NextEvent.PHASE);

        final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(31));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertListenerStatus();
        currentPhase = subscription.getCurrentPhase();
        assertEquals(currentPhase.getPhaseType(), PhaseType.EVERGREEN);

        final DateTime invalidDate = subscription.getBundleStartDate().minusDays(3);
        // CANCEL in EVERGREEN period with an invalid Date (prior to the Creation Date)
        subscription.cancelWithDate(invalidDate, callContext);
    }



    @Test(groups = "slow")
    public void testWithMultipleCancellationEvent() throws SubscriptionBillingApiException, SubscriptionBaseApiException {
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
        subscription.cancel(callContext);
        assertListenerStatus();

        subscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(subscription.getId(), internalCallContext);
        Assert.assertEquals(subscription.getAllTransitions().size(), 3);


        // Manually add a CANCEL event on the same EOT date as the previous one to verify the code is resilient enough to ignore it
        final SubscriptionBaseEvent cancelEvent = subscription.getEvents().get(subscription.getEvents().size() - 1);
        final SubscriptionEventModelDao newCancelEvent = new SubscriptionEventModelDao(cancelEvent);
        newCancelEvent.setId(UUID.randomUUID());

        final Handle handle = dbi.open();
        final SubscriptionEventSqlDao sqlDao = handle.attach(SubscriptionEventSqlDao.class);
        try {
            sqlDao.create(newCancelEvent, internalCallContext);
        } catch (EntityPersistenceException e) {
            Assert.fail(e.getMessage());
        }

        subscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(subscription.getId(), internalCallContext);
        // The extra cancel event is being ignored
        Assert.assertEquals(subscription.getEvents().size(), 3);
        Assert.assertEquals(subscription.getAllTransitions().size(), 3);


        // We expect only one CANCEL event, this other one is skipped
        testListener.pushExpectedEvents(NextEvent.CANCEL);
        it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(1));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertListenerStatus();

        // Our previous transition should be a CANCEL with a valid previous plan
        final SubscriptionBaseTransition previousTransition = subscription.getPreviousTransition();
        Assert.assertEquals(previousTransition.getPreviousState(), EntitlementState.ACTIVE);
        Assert.assertNotNull(previousTransition.getPreviousPlan());

    }
}
