/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Duration;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanAlignmentCreate;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PlanSpecifier;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.entitlement.api.EntitlementAOStatusDryRun;
import org.killbill.billing.entitlement.api.EntitlementAOStatusDryRun.DryRunChangeReason;
import org.killbill.billing.subscription.SubscriptionTestSuiteWithEmbeddedDB;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestUserApiAddOn extends SubscriptionTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testCreateCancelAddon() throws SubscriptionBaseApiException {
        final String baseProduct = "Shotgun";
        final BillingPeriod baseTerm = BillingPeriod.ANNUAL;
        final String basePriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        DefaultSubscriptionBase baseSubscription = testUtil.createSubscription(bundle, baseProduct, baseTerm, basePriceList);

        final String aoProduct = "Telescopic-Scope";
        final BillingPeriod aoTerm = BillingPeriod.MONTHLY;
        final String aoPriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        DefaultSubscriptionBase aoSubscription = testUtil.createSubscription(bundle, aoProduct, aoTerm, aoPriceList);
        assertEquals(aoSubscription.getState(), EntitlementState.ACTIVE);

        testListener.pushExpectedEvent(NextEvent.CANCEL);
        final DateTime now = clock.getUTCNow();
        aoSubscription.cancel(callContext);

        assertListenerStatus();
        aoSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(aoSubscription.getId(), internalCallContext);
        assertEquals(aoSubscription.getState(), EntitlementState.CANCELLED);

        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testCreateCancelAddonAndThenBP() throws SubscriptionBaseApiException {
        final String baseProduct = "Shotgun";
        final BillingPeriod baseTerm = BillingPeriod.ANNUAL;
        final String basePriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        DefaultSubscriptionBase baseSubscription = testUtil.createSubscription(bundle, baseProduct, baseTerm, basePriceList);

        final String aoProduct = "Telescopic-Scope";
        final BillingPeriod aoTerm = BillingPeriod.MONTHLY;
        final String aoPriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        DefaultSubscriptionBase aoSubscription = testUtil.createSubscription(bundle, aoProduct, aoTerm, aoPriceList);
        assertEquals(aoSubscription.getState(), EntitlementState.ACTIVE);

        // Move clock after a month
        Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(1));
        testListener.pushExpectedEvent(NextEvent.PHASE);
        testListener.pushExpectedEvent(NextEvent.PHASE);
        clock.addDeltaFromReality(it.toDurationMillis());
        assertListenerStatus();

        // SET CTD TO CANCEL IN FUTURE
        final DateTime now = clock.getUTCNow();
        final Duration aoCtd = testUtil.getDurationMonth(1);
        final DateTime newAOChargedThroughDate = TestSubscriptionHelper.addDuration(now, aoCtd);
        subscriptionInternalApi.setChargedThroughDate(aoSubscription.getId(), newAOChargedThroughDate, internalCallContext);

        final Duration bpCtd = testUtil.getDurationMonth(11);
        final DateTime newBPChargedThroughDate = TestSubscriptionHelper.addDuration(now, bpCtd);
        subscriptionInternalApi.setChargedThroughDate(baseSubscription.getId(), newBPChargedThroughDate, internalCallContext);

        baseSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(baseSubscription.getId(), internalCallContext);
        aoSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(aoSubscription.getId(), internalCallContext);

        it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(1));
        clock.addDeltaFromReality(it.toDurationMillis());

        // CANCEL AO
        aoSubscription.cancel(callContext);
        aoSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(aoSubscription.getId(), internalCallContext);
        assertEquals(aoSubscription.getState(), EntitlementState.ACTIVE);
        assertTrue(aoSubscription.isFutureCancelled());

        // CANCEL BASE NOW
        baseSubscription.cancel(callContext);
        baseSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(baseSubscription.getId(), internalCallContext);
        assertEquals(baseSubscription.getState(), EntitlementState.ACTIVE);
        assertTrue(baseSubscription.isFutureCancelled());

        aoSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(aoSubscription.getId(), internalCallContext);
        List<SubscriptionBaseTransition> aoTransitions = aoSubscription.getAllTransitions();
        assertEquals(aoTransitions.size(), 3);
        assertEquals(aoTransitions.get(0).getTransitionType(), SubscriptionBaseTransitionType.CREATE);
        assertEquals(aoTransitions.get(1).getTransitionType(), SubscriptionBaseTransitionType.PHASE);
        assertEquals(aoTransitions.get(2).getTransitionType(), SubscriptionBaseTransitionType.CANCEL);
        assertTrue(aoSubscription.getFutureEndDate().compareTo(newAOChargedThroughDate) == 0);

        testListener.pushExpectedEvent(NextEvent.UNCANCEL);
        aoSubscription.uncancel(callContext);
        assertListenerStatus();

        aoSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(aoSubscription.getId(), internalCallContext);
        aoTransitions = aoSubscription.getAllTransitions();
        assertEquals(aoTransitions.size(), 3);
        assertEquals(aoTransitions.get(0).getTransitionType(), SubscriptionBaseTransitionType.CREATE);
        assertEquals(aoTransitions.get(1).getTransitionType(), SubscriptionBaseTransitionType.PHASE);
        assertEquals(aoTransitions.get(2).getTransitionType(), SubscriptionBaseTransitionType.CANCEL);
        assertTrue(aoSubscription.getFutureEndDate().compareTo(newBPChargedThroughDate) == 0);

        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testCancelBPWithAddon() throws SubscriptionBaseApiException {
        final String baseProduct = "Shotgun";
        final BillingPeriod baseTerm = BillingPeriod.MONTHLY;
        final String basePriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        // CREATE BP
        DefaultSubscriptionBase baseSubscription = testUtil.createSubscription(bundle, baseProduct, baseTerm, basePriceList);

        final String aoProduct = "Telescopic-Scope";
        final BillingPeriod aoTerm = BillingPeriod.MONTHLY;
        final String aoPriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        DefaultSubscriptionBase aoSubscription = testUtil.createSubscription(bundle, aoProduct, aoTerm, aoPriceList);

        testListener.pushExpectedEvent(NextEvent.PHASE);
        testListener.pushExpectedEvent(NextEvent.PHASE);

        // MOVE CLOCK AFTER TRIAL + AO DISCOUNT
        Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(2));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertListenerStatus();

        // SET CTD TO CANCEL IN FUTURE
        final DateTime now = clock.getUTCNow();
        final Duration ctd = testUtil.getDurationMonth(1);
        // Why not just use clock.getUTCNow().plusMonths(1) ?
        final DateTime newChargedThroughDate = TestSubscriptionHelper.addDuration(now, ctd);
        subscriptionInternalApi.setChargedThroughDate(baseSubscription.getId(), newChargedThroughDate, internalCallContext);
        baseSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(baseSubscription.getId(), internalCallContext);

        // FUTURE CANCELLATION
        baseSubscription.cancel(callContext);

        // REFETCH AO SUBSCRIPTION AND CHECK THIS IS ACTIVE
        aoSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(aoSubscription.getId(), internalCallContext);
        assertEquals(aoSubscription.getState(), EntitlementState.ACTIVE);
        assertTrue(aoSubscription.isFutureCancelled());

        // MOVE AFTER CANCELLATION
        testListener.pushExpectedEvent(NextEvent.CANCEL);
        testListener.pushExpectedEvent(NextEvent.CANCEL);

        it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(1));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertListenerStatus();

        // REFETCH AO SUBSCRIPTION AND CHECK THIS IS CANCELLED
        aoSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(aoSubscription.getId(), internalCallContext);
        assertEquals(aoSubscription.getState(), EntitlementState.CANCELLED);

        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testCancelUncancelBPWithAddon() throws SubscriptionBaseApiException {
        final String baseProduct = "Shotgun";
        final BillingPeriod baseTerm = BillingPeriod.MONTHLY;
        final String basePriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        // CREATE BP
        DefaultSubscriptionBase baseSubscription = testUtil.createSubscription(bundle, baseProduct, baseTerm, basePriceList);

        final String aoProduct = "Telescopic-Scope";
        final BillingPeriod aoTerm = BillingPeriod.MONTHLY;
        final String aoPriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        DefaultSubscriptionBase aoSubscription = testUtil.createSubscription(bundle, aoProduct, aoTerm, aoPriceList);

        testListener.pushExpectedEvent(NextEvent.PHASE);
        testListener.pushExpectedEvent(NextEvent.PHASE);

        // MOVE CLOCK AFTER TRIAL + AO DISCOUNT
        Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(2));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertListenerStatus();

        // SET CTD TO CANCEL IN FUTURE
        final DateTime now = clock.getUTCNow();
        final Duration ctd = testUtil.getDurationMonth(1);
        // Why not just use clock.getUTCNow().plusMonths(1) ?
        final DateTime newChargedThroughDate = TestSubscriptionHelper.addDuration(now, ctd);
        subscriptionInternalApi.setChargedThroughDate(baseSubscription.getId(), newChargedThroughDate, internalCallContext);
        baseSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(baseSubscription.getId(), internalCallContext);

        // FUTURE CANCELLATION
        baseSubscription.cancel(callContext);

        // REFETCH AO SUBSCRIPTION AND CHECK THIS IS ACTIVE
        aoSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(aoSubscription.getId(), internalCallContext);
        assertEquals(aoSubscription.getState(), EntitlementState.ACTIVE);
        assertTrue(aoSubscription.isFutureCancelled());

        testListener.pushExpectedEvent(NextEvent.UNCANCEL);
        baseSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(baseSubscription.getId(), internalCallContext);
        baseSubscription.uncancel(callContext);
        assertListenerStatus();

        aoSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(aoSubscription.getId(), internalCallContext);
        assertEquals(aoSubscription.getState(), EntitlementState.ACTIVE);
        assertFalse(aoSubscription.isFutureCancelled());

        // CANCEL AGAIN
        it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(1));
        clock.addDeltaFromReality(it.toDurationMillis());

        baseSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(baseSubscription.getId(), internalCallContext);
        baseSubscription.cancel(callContext);
        baseSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(baseSubscription.getId(), internalCallContext);
        assertEquals(baseSubscription.getState(), EntitlementState.ACTIVE);
        assertTrue(baseSubscription.isFutureCancelled());

        aoSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(aoSubscription.getId(), internalCallContext);
        assertEquals(aoSubscription.getState(), EntitlementState.ACTIVE);
        assertTrue(aoSubscription.isFutureCancelled());
        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testChangeBPWithAddonIncluded() throws SubscriptionBaseApiException {
        final String baseProduct = "Shotgun";
        final BillingPeriod baseTerm = BillingPeriod.MONTHLY;
        final String basePriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        // CREATE BP
        DefaultSubscriptionBase baseSubscription = testUtil.createSubscription(bundle, baseProduct, baseTerm, basePriceList);

        final String aoProduct = "Telescopic-Scope";
        final BillingPeriod aoTerm = BillingPeriod.MONTHLY;
        final String aoPriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        DefaultSubscriptionBase aoSubscription = testUtil.createSubscription(bundle, aoProduct, aoTerm, aoPriceList);

        testListener.pushExpectedEvent(NextEvent.PHASE);
        testListener.pushExpectedEvent(NextEvent.PHASE);

        // MOVE CLOCK AFTER TRIAL + AO DISCOUNT
        final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(2));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertListenerStatus();

        // SET CTD TO CHANGE IN FUTURE
        final DateTime now = clock.getUTCNow();
        final Duration ctd = testUtil.getDurationMonth(1);
        final DateTime newChargedThroughDate = TestSubscriptionHelper.addDuration(now, ctd);
        subscriptionInternalApi.setChargedThroughDate(baseSubscription.getId(), newChargedThroughDate, internalCallContext);
        baseSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(baseSubscription.getId(), internalCallContext);

        // CHANGE IMMEDIATELY WITH TO BP WITH NON INCLUDED ADDON
        final String newBaseProduct = "Assault-Rifle";
        final BillingPeriod newBaseTerm = BillingPeriod.MONTHLY;
        final String newBasePriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        final List<EntitlementAOStatusDryRun> aoStatus = subscriptionInternalApi.getDryRunChangePlanStatus(baseSubscription.getId(),
                                                                                                           newBaseProduct, now, internalCallContext);
        assertEquals(aoStatus.size(), 1);
        assertEquals(aoStatus.get(0).getId(), aoSubscription.getId());
        assertEquals(aoStatus.get(0).getProductName(), aoProduct);
        assertEquals(aoStatus.get(0).getBillingPeriod(), aoTerm);
        assertEquals(aoStatus.get(0).getPhaseType(), aoSubscription.getCurrentPhase().getPhaseType());
        assertEquals(aoStatus.get(0).getPriceList(), aoSubscription.getCurrentPriceList().getName());
        assertEquals(aoStatus.get(0).getReason(), DryRunChangeReason.AO_INCLUDED_IN_NEW_PLAN);

        testListener.pushExpectedEvent(NextEvent.CHANGE);
        testListener.pushExpectedEvent(NextEvent.CANCEL);
        baseSubscription.changePlan(new PlanPhaseSpecifier(newBaseProduct, newBaseTerm, newBasePriceList, null), null, callContext);
        assertListenerStatus();

        // REFETCH AO SUBSCRIPTION AND CHECK THIS CANCELLED
        aoSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(aoSubscription.getId(), internalCallContext);
        assertEquals(aoSubscription.getState(), EntitlementState.CANCELLED);

        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testChangeBPWithAddonNonAvailable() throws SubscriptionBaseApiException {
        final String baseProduct = "Shotgun";
        final BillingPeriod baseTerm = BillingPeriod.MONTHLY;
        final String basePriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        // CREATE BP
        DefaultSubscriptionBase baseSubscription = testUtil.createSubscription(bundle, baseProduct, baseTerm, basePriceList);

        final String aoProduct = "Telescopic-Scope";
        final BillingPeriod aoTerm = BillingPeriod.MONTHLY;
        final String aoPriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        // CREATE AO
        DefaultSubscriptionBase aoSubscription = testUtil.createSubscription(bundle, aoProduct, aoTerm, aoPriceList);

        testListener.pushExpectedEvent(NextEvent.PHASE);
        testListener.pushExpectedEvent(NextEvent.PHASE);

        // MOVE CLOCK AFTER TRIAL + AO DISCOUNT
        Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(2));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertListenerStatus();

        // SET CTD TO CANCEL IN FUTURE
        final DateTime now = clock.getUTCNow();
        final Duration ctd = testUtil.getDurationMonth(1);
        final DateTime newChargedThroughDate = TestSubscriptionHelper.addDuration(now, ctd);
        subscriptionInternalApi.setChargedThroughDate(baseSubscription.getId(), newChargedThroughDate, internalCallContext);
        baseSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(baseSubscription.getId(), internalCallContext);

        // CHANGE IMMEDIATELY WITH TO BP WITH NON AVAILABLE ADDON
        final String newBaseProduct = "Pistol";
        final BillingPeriod newBaseTerm = BillingPeriod.MONTHLY;
        final String newBasePriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        final List<EntitlementAOStatusDryRun> aoStatus = subscriptionInternalApi.getDryRunChangePlanStatus(baseSubscription.getId(),
                                                                                                           newBaseProduct, now, internalCallContext);
        assertEquals(aoStatus.size(), 1);
        assertEquals(aoStatus.get(0).getId(), aoSubscription.getId());
        assertEquals(aoStatus.get(0).getProductName(), aoProduct);
        assertEquals(aoStatus.get(0).getBillingPeriod(), aoTerm);
        assertEquals(aoStatus.get(0).getPhaseType(), aoSubscription.getCurrentPhase().getPhaseType());
        assertEquals(aoStatus.get(0).getPriceList(), aoSubscription.getCurrentPriceList().getName());
        assertEquals(aoStatus.get(0).getReason(), DryRunChangeReason.AO_NOT_AVAILABLE_IN_NEW_PLAN);

        baseSubscription.changePlan(new PlanPhaseSpecifier(newBaseProduct, newBaseTerm, newBasePriceList), null, callContext);

        // REFETCH AO SUBSCRIPTION AND CHECK THIS IS ACTIVE
        aoSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(aoSubscription.getId(), internalCallContext);
        assertEquals(aoSubscription.getState(), EntitlementState.ACTIVE);
        assertTrue(aoSubscription.isFutureCancelled());

        // MOVE AFTER CHANGE
        testListener.pushExpectedEvent(NextEvent.CHANGE);
        testListener.pushExpectedEvent(NextEvent.CANCEL);
        it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(1));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertListenerStatus();

        // REFETCH AO SUBSCRIPTION AND CHECK THIS CANCELLED
        aoSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(aoSubscription.getId(), internalCallContext);
        assertEquals(aoSubscription.getState(), EntitlementState.CANCELLED);

        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testAddonCreateWithBundleAlign() throws CatalogApiException, SubscriptionBaseApiException {
        final String aoProduct = "Telescopic-Scope";
        final BillingPeriod aoTerm = BillingPeriod.MONTHLY;
        final String aoPriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        // This is just to double check our test catalog gives us what we want before we start the test
        final PlanSpecifier planSpecifier = new PlanSpecifier(aoProduct,
                                                              aoTerm,
                                                              aoPriceList);
        final DateTime utcNow = clock.getUTCNow();
        final PlanAlignmentCreate alignment = catalog.planCreateAlignment(planSpecifier, utcNow, utcNow);
        assertEquals(alignment, PlanAlignmentCreate.START_OF_BUNDLE);

        testAddonCreateInternal(aoProduct, aoTerm, aoPriceList, alignment);
    }

    @Test(groups = "slow")
    public void testAddonCreateWithSubscriptionAlign() throws SubscriptionBaseApiException, CatalogApiException {
        final String aoProduct = "Laser-Scope";
        final BillingPeriod aoTerm = BillingPeriod.MONTHLY;
        final String aoPriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        // This is just to double check our test catalog gives us what we want before we start the test
        final PlanSpecifier planSpecifier = new PlanSpecifier(aoProduct,
                                                              aoTerm,
                                                              aoPriceList);
        final DateTime utcNow = clock.getUTCNow();
        final PlanAlignmentCreate alignment = catalog.planCreateAlignment(planSpecifier, utcNow, utcNow);
        assertEquals(alignment, PlanAlignmentCreate.START_OF_SUBSCRIPTION);

        testAddonCreateInternal(aoProduct, aoTerm, aoPriceList, alignment);
    }

    private void testAddonCreateInternal(final String aoProduct, final BillingPeriod aoTerm, final String aoPriceList, final PlanAlignmentCreate expAlignement) throws SubscriptionBaseApiException {
        final String baseProduct = "Shotgun";
        final BillingPeriod baseTerm = BillingPeriod.MONTHLY;
        final String basePriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        // CREATE BP
        final DefaultSubscriptionBase baseSubscription = testUtil.createSubscription(bundle, baseProduct, baseTerm, basePriceList);

        // MOVE CLOCK 14 DAYS LATER
        Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(14));
        clock.addDeltaFromReality(it.toDurationMillis());

        // CREATE ADDON
        final DateTime beforeAOCreation = clock.getUTCNow();
        DefaultSubscriptionBase aoSubscription = testUtil.createSubscription(bundle, aoProduct, aoTerm, aoPriceList);
        final DateTime afterAOCreation = clock.getUTCNow();

        // CHECK EVERYTHING
        Plan aoCurrentPlan = aoSubscription.getCurrentPlan();
        assertNotNull(aoCurrentPlan);
        assertEquals(aoCurrentPlan.getProduct().getName(), aoProduct);
        assertEquals(aoCurrentPlan.getProduct().getCategory(), ProductCategory.ADD_ON);
        assertEquals(aoCurrentPlan.getRecurringBillingPeriod(), aoTerm);

        PlanPhase aoCurrentPhase = aoSubscription.getCurrentPhase();
        assertNotNull(aoCurrentPhase);
        assertEquals(aoCurrentPhase.getPhaseType(), PhaseType.DISCOUNT);

        testUtil.assertDateWithin(aoSubscription.getStartDate(), beforeAOCreation, afterAOCreation);
        assertEquals(aoSubscription.getBundleStartDate(), baseSubscription.getBundleStartDate());

        // CHECK next AO PHASE EVENT IS INDEED A MONTH AFTER BP STARTED => BUNDLE ALIGNMENT
        SubscriptionBaseTransition aoPendingTranstion = aoSubscription.getPendingTransition();
        if (expAlignement == PlanAlignmentCreate.START_OF_BUNDLE) {
            assertEquals(aoPendingTranstion.getEffectiveTransitionTime(), baseSubscription.getStartDate().plusMonths(1));
        } else {
            assertEquals(aoPendingTranstion.getEffectiveTransitionTime(), aoSubscription.getStartDate().plusMonths(1));
        }

        // ADD TWO PHASE EVENTS (BP + AO)
        testListener.pushExpectedEvent(NextEvent.PHASE);
        testListener.pushExpectedEvent(NextEvent.PHASE);

        // MOVE THROUGH TIME TO GO INTO EVERGREEN
        it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(33));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertListenerStatus();

        // CHECK EVERYTHING AGAIN
        aoSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(aoSubscription.getId(), internalCallContext);

        aoCurrentPlan = aoSubscription.getCurrentPlan();
        assertNotNull(aoCurrentPlan);
        assertEquals(aoCurrentPlan.getProduct().getName(), aoProduct);
        assertEquals(aoCurrentPlan.getProduct().getCategory(), ProductCategory.ADD_ON);
        assertEquals(aoCurrentPlan.getRecurringBillingPeriod(), aoTerm);

        aoCurrentPhase = aoSubscription.getCurrentPhase();
        assertNotNull(aoCurrentPhase);
        assertEquals(aoCurrentPhase.getPhaseType(), PhaseType.EVERGREEN);

        aoSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(aoSubscription.getId(), internalCallContext);
        aoPendingTranstion = aoSubscription.getPendingTransition();
        assertNull(aoPendingTranstion);

        assertListenerStatus();
    }
}
