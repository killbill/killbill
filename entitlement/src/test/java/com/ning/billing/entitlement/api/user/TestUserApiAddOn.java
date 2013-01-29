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
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.Duration;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanAlignmentCreate;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PlanSpecifier;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.EntitlementTestSuiteWithEmbeddedDB;
import com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;
import com.ning.billing.entitlement.api.user.SubscriptionStatusDryRun.DryRunChangeReason;
import com.ning.billing.util.clock.DefaultClock;

public class TestUserApiAddOn extends EntitlementTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testCreateCancelAddon() {
        try {
            final String baseProduct = "Shotgun";
            final BillingPeriod baseTerm = BillingPeriod.MONTHLY;
            final String basePriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

            testUtil.createSubscription(bundle, baseProduct, baseTerm, basePriceList);

            final String aoProduct = "Telescopic-Scope";
            final BillingPeriod aoTerm = BillingPeriod.MONTHLY;
            final String aoPriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

            final SubscriptionData aoSubscription = testUtil.createSubscription(bundle, aoProduct, aoTerm, aoPriceList);
            assertEquals(aoSubscription.getState(), SubscriptionState.ACTIVE);

            final DateTime now = clock.getUTCNow();
            aoSubscription.cancel(now, callContext);

            testListener.reset();
            testListener.pushExpectedEvent(NextEvent.CANCEL);
            assertTrue(testListener.isCompleted(5000));

            assertEquals(aoSubscription.getState(), SubscriptionState.CANCELLED);

            assertListenerStatus();
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test(groups = "slow")
    public void testCancelBPWithAddon() {
        try {
            final String baseProduct = "Shotgun";
            final BillingPeriod baseTerm = BillingPeriod.MONTHLY;
            final String basePriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

            // CREATE BP
            SubscriptionData baseSubscription = testUtil.createSubscription(bundle, baseProduct, baseTerm, basePriceList);

            final String aoProduct = "Telescopic-Scope";
            final BillingPeriod aoTerm = BillingPeriod.MONTHLY;
            final String aoPriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

            SubscriptionData aoSubscription = testUtil.createSubscription(bundle, aoProduct, aoTerm, aoPriceList);

            testListener.reset();
            testListener.pushExpectedEvent(NextEvent.PHASE);
            testListener.pushExpectedEvent(NextEvent.PHASE);

            // MOVE CLOCK AFTER TRIAL + AO DISCOUNT
            Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(2));
            clock.addDeltaFromReality(it.toDurationMillis());
            assertTrue(testListener.isCompleted(5000));

            // SET CTD TO CANCEL IN FUTURE
            final DateTime now = clock.getUTCNow();
            final Duration ctd = testUtil.getDurationMonth(1);
            // Why not just use clock.getUTCNow().plusMonths(1) ?
            final DateTime newChargedThroughDate = DefaultClock.addDuration(now, ctd);
            entitlementInternalApi.setChargedThroughDate(baseSubscription.getId(), newChargedThroughDate.toLocalDate(), internalCallContext);
            baseSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(baseSubscription.getId(), callContext);

            // FUTURE CANCELLATION
            baseSubscription.cancel(now, callContext);

            // REFETCH AO SUBSCRIPTION AND CHECK THIS IS ACTIVE
            aoSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(aoSubscription.getId(), callContext);
            assertEquals(aoSubscription.getState(), SubscriptionState.ACTIVE);
            assertTrue(aoSubscription.isSubscriptionFutureCancelled());

            // MOVE AFTER CANCELLATION
            testListener.reset();
            testListener.pushExpectedEvent(NextEvent.CANCEL);
            testListener.pushExpectedEvent(NextEvent.CANCEL);

            it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(1));
            clock.addDeltaFromReality(it.toDurationMillis());
            assertTrue(testListener.isCompleted(5000));

            // REFETCH AO SUBSCRIPTION AND CHECK THIS IS CANCELLED
            aoSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(aoSubscription.getId(), callContext);
            assertEquals(aoSubscription.getState(), SubscriptionState.CANCELLED);

            assertListenerStatus();

        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test(groups = "slow")
    public void testChangeBPWithAddonIncluded() {
        try {
            final String baseProduct = "Shotgun";
            final BillingPeriod baseTerm = BillingPeriod.MONTHLY;
            final String basePriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

            // CREATE BP
            SubscriptionData baseSubscription = testUtil.createSubscription(bundle, baseProduct, baseTerm, basePriceList);

            final String aoProduct = "Telescopic-Scope";
            final BillingPeriod aoTerm = BillingPeriod.MONTHLY;
            final String aoPriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

            SubscriptionData aoSubscription = testUtil.createSubscription(bundle, aoProduct, aoTerm, aoPriceList);

            testListener.reset();
            testListener.pushExpectedEvent(NextEvent.PHASE);
            testListener.pushExpectedEvent(NextEvent.PHASE);

            // MOVE CLOCK AFTER TRIAL + AO DISCOUNT
            final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(2));
            clock.addDeltaFromReality(it.toDurationMillis());
            assertTrue(testListener.isCompleted(5000));

            // SET CTD TO CHANGE IN FUTURE
            final DateTime now = clock.getUTCNow();
            final Duration ctd = testUtil.getDurationMonth(1);
            final DateTime newChargedThroughDate = DefaultClock.addDuration(now, ctd);
            entitlementInternalApi.setChargedThroughDate(baseSubscription.getId(), newChargedThroughDate.toLocalDate(), internalCallContext);
            baseSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(baseSubscription.getId(), callContext);

            // CHANGE IMMEDIATELY WITH TO BP WITH NON INCLUDED ADDON
            final String newBaseProduct = "Assault-Rifle";
            final BillingPeriod newBaseTerm = BillingPeriod.MONTHLY;
            final String newBasePriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

            final List<SubscriptionStatusDryRun> aoStatus = entitlementApi.getDryRunChangePlanStatus(baseSubscription.getId(),
                                                                                                     newBaseProduct, now, callContext);
            assertEquals(aoStatus.size(), 1);
            assertEquals(aoStatus.get(0).getId(), aoSubscription.getId());
            assertEquals(aoStatus.get(0).getProductName(), aoProduct);
            assertEquals(aoStatus.get(0).getBillingPeriod(), aoTerm);
            assertEquals(aoStatus.get(0).getPhaseType(), aoSubscription.getCurrentPhase().getPhaseType());
            assertEquals(aoStatus.get(0).getPriceList(), aoSubscription.getCurrentPriceList().getName());
            assertEquals(aoStatus.get(0).getReason(), DryRunChangeReason.AO_INCLUDED_IN_NEW_PLAN);

            testListener.reset();
            testListener.pushExpectedEvent(NextEvent.CHANGE);
            testListener.pushExpectedEvent(NextEvent.CANCEL);
            baseSubscription.changePlan(newBaseProduct, newBaseTerm, newBasePriceList, now, callContext);
            assertTrue(testListener.isCompleted(5000));

            // REFETCH AO SUBSCRIPTION AND CHECK THIS CANCELLED
            aoSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(aoSubscription.getId(), callContext);
            assertEquals(aoSubscription.getState(), SubscriptionState.CANCELLED);

            assertListenerStatus();
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test(groups = "slow")
    public void testChangeBPWithAddonNonAvailable() {
        try {
            final String baseProduct = "Shotgun";
            final BillingPeriod baseTerm = BillingPeriod.MONTHLY;
            final String basePriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

            // CREATE BP
            SubscriptionData baseSubscription = testUtil.createSubscription(bundle, baseProduct, baseTerm, basePriceList);

            final String aoProduct = "Telescopic-Scope";
            final BillingPeriod aoTerm = BillingPeriod.MONTHLY;
            final String aoPriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

            SubscriptionData aoSubscription = testUtil.createSubscription(bundle, aoProduct, aoTerm, aoPriceList);

            testListener.reset();
            testListener.pushExpectedEvent(NextEvent.PHASE);
            testListener.pushExpectedEvent(NextEvent.PHASE);

            // MOVE CLOCK AFTER TRIAL + AO DISCOUNT
            Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(2));
            clock.addDeltaFromReality(it.toDurationMillis());
            assertTrue(testListener.isCompleted(5000));

            // SET CTD TO CANCEL IN FUTURE
            final DateTime now = clock.getUTCNow();
            final Duration ctd = testUtil.getDurationMonth(1);
            final DateTime newChargedThroughDate = DefaultClock.addDuration(now, ctd);
            entitlementInternalApi.setChargedThroughDate(baseSubscription.getId(), newChargedThroughDate.toLocalDate(), internalCallContext);
            baseSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(baseSubscription.getId(), callContext);

            // CHANGE IMMEDIATELY WITH TO BP WITH NON AVAILABLE ADDON
            final String newBaseProduct = "Pistol";
            final BillingPeriod newBaseTerm = BillingPeriod.MONTHLY;
            final String newBasePriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

            final List<SubscriptionStatusDryRun> aoStatus = entitlementApi.getDryRunChangePlanStatus(baseSubscription.getId(),
                                                                                                     newBaseProduct, now, callContext);
            assertEquals(aoStatus.size(), 1);
            assertEquals(aoStatus.get(0).getId(), aoSubscription.getId());
            assertEquals(aoStatus.get(0).getProductName(), aoProduct);
            assertEquals(aoStatus.get(0).getBillingPeriod(), aoTerm);
            assertEquals(aoStatus.get(0).getPhaseType(), aoSubscription.getCurrentPhase().getPhaseType());
            assertEquals(aoStatus.get(0).getPriceList(), aoSubscription.getCurrentPriceList().getName());
            assertEquals(aoStatus.get(0).getReason(), DryRunChangeReason.AO_NOT_AVAILABLE_IN_NEW_PLAN);

            baseSubscription.changePlan(newBaseProduct, newBaseTerm, newBasePriceList, now, callContext);

            // REFETCH AO SUBSCRIPTION AND CHECK THIS IS ACTIVE
            aoSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(aoSubscription.getId(), callContext);
            assertEquals(aoSubscription.getState(), SubscriptionState.ACTIVE);
            assertTrue(aoSubscription.isSubscriptionFutureCancelled());

            // MOVE AFTER CHANGE
            testListener.reset();
            testListener.pushExpectedEvent(NextEvent.CHANGE);
            testListener.pushExpectedEvent(NextEvent.CANCEL);
            it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(1));
            clock.addDeltaFromReality(it.toDurationMillis());
            assertTrue(testListener.isCompleted(5000));

            // REFETCH AO SUBSCRIPTION AND CHECK THIS CANCELLED
            aoSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(aoSubscription.getId(), callContext);
            assertEquals(aoSubscription.getState(), SubscriptionState.CANCELLED);

            assertListenerStatus();
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test(groups = "slow")
    public void testAddonCreateWithBundleAlign() {
        try {
            final String aoProduct = "Telescopic-Scope";
            final BillingPeriod aoTerm = BillingPeriod.MONTHLY;
            final String aoPriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

            // This is just to double check our test catalog gives us what we want before we start the test
            final PlanSpecifier planSpecifier = new PlanSpecifier(aoProduct,
                                                                  ProductCategory.ADD_ON,
                                                                  aoTerm,
                                                                  aoPriceList);
            final PlanAlignmentCreate alignement = catalog.planCreateAlignment(planSpecifier, clock.getUTCNow());
            assertEquals(alignement, PlanAlignmentCreate.START_OF_BUNDLE);

            testAddonCreateInternal(aoProduct, aoTerm, aoPriceList, alignement);

            assertListenerStatus();
        } catch (CatalogApiException e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test(groups = "slow")
    public void testAddonCreateWithSubscriptionAlign() {
        try {
            final String aoProduct = "Laser-Scope";
            final BillingPeriod aoTerm = BillingPeriod.MONTHLY;
            final String aoPriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

            // This is just to double check our test catalog gives us what we want before we start the test
            final PlanSpecifier planSpecifier = new PlanSpecifier(aoProduct,
                                                                  ProductCategory.ADD_ON,
                                                                  aoTerm,
                                                                  aoPriceList);
            final PlanAlignmentCreate alignement = catalog.planCreateAlignment(planSpecifier, clock.getUTCNow());
            assertEquals(alignement, PlanAlignmentCreate.START_OF_SUBSCRIPTION);

            testAddonCreateInternal(aoProduct, aoTerm, aoPriceList, alignement);

            assertListenerStatus();
        } catch (CatalogApiException e) {
            Assert.fail(e.getMessage());
        }
    }

    private void testAddonCreateInternal(final String aoProduct, final BillingPeriod aoTerm, final String aoPriceList, final PlanAlignmentCreate expAlignement) {
        try {
            final String baseProduct = "Shotgun";
            final BillingPeriod baseTerm = BillingPeriod.MONTHLY;
            final String basePriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

            // CREATE BP
            final SubscriptionData baseSubscription = testUtil.createSubscription(bundle, baseProduct, baseTerm, basePriceList);

            // MOVE CLOCK 14 DAYS LATER
            Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(14));
            clock.addDeltaFromReality(it.toDurationMillis());

            // CREATE ADDON
            final DateTime beforeAOCreation = clock.getUTCNow();
            SubscriptionData aoSubscription = testUtil.createSubscription(bundle, aoProduct, aoTerm, aoPriceList);
            final DateTime afterAOCreation = clock.getUTCNow();

            // CHECK EVERYTHING
            Plan aoCurrentPlan = aoSubscription.getCurrentPlan();
            assertNotNull(aoCurrentPlan);
            assertEquals(aoCurrentPlan.getProduct().getName(), aoProduct);
            assertEquals(aoCurrentPlan.getProduct().getCategory(), ProductCategory.ADD_ON);
            assertEquals(aoCurrentPlan.getBillingPeriod(), aoTerm);

            PlanPhase aoCurrentPhase = aoSubscription.getCurrentPhase();
            assertNotNull(aoCurrentPhase);
            assertEquals(aoCurrentPhase.getPhaseType(), PhaseType.DISCOUNT);

            testUtil.assertDateWithin(aoSubscription.getStartDate(), beforeAOCreation, afterAOCreation);
            assertEquals(aoSubscription.getBundleStartDate(), baseSubscription.getBundleStartDate());

            // CHECK next AO PHASE EVENT IS INDEED A MONTH AFTER BP STARTED => BUNDLE ALIGNMENT
            SubscriptionTransition aoPendingTranstion = aoSubscription.getPendingTransition();
            if (expAlignement == PlanAlignmentCreate.START_OF_BUNDLE) {
                assertEquals(aoPendingTranstion.getEffectiveTransitionTime(), baseSubscription.getStartDate().plusMonths(1));
            } else {
                assertEquals(aoPendingTranstion.getEffectiveTransitionTime(), aoSubscription.getStartDate().plusMonths(1));
            }

            // ADD TWO PHASE EVENTS (BP + AO)
            testListener.reset();
            testListener.pushExpectedEvent(NextEvent.PHASE);
            testListener.pushExpectedEvent(NextEvent.PHASE);

            // MOVE THROUGH TIME TO GO INTO EVERGREEN
            it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(33));
            clock.addDeltaFromReality(it.toDurationMillis());
            assertTrue(testListener.isCompleted(5000));

            // CHECK EVERYTHING AGAIN
            aoSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(aoSubscription.getId(), callContext);

            aoCurrentPlan = aoSubscription.getCurrentPlan();
            assertNotNull(aoCurrentPlan);
            assertEquals(aoCurrentPlan.getProduct().getName(), aoProduct);
            assertEquals(aoCurrentPlan.getProduct().getCategory(), ProductCategory.ADD_ON);
            assertEquals(aoCurrentPlan.getBillingPeriod(), aoTerm);

            aoCurrentPhase = aoSubscription.getCurrentPhase();
            assertNotNull(aoCurrentPhase);
            assertEquals(aoCurrentPhase.getPhaseType(), PhaseType.EVERGREEN);

            aoSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(aoSubscription.getId(), callContext);
            aoPendingTranstion = aoSubscription.getPendingTransition();
            assertNull(aoPendingTranstion);
        } catch (EntitlementUserApiException e) {
            Assert.fail(e.getMessage());
        }
    }
}
