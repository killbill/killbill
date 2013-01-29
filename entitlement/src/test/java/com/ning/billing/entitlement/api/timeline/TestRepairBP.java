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

package com.ning.billing.entitlement.api.timeline;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.testng.annotations.Test;

import com.ning.billing.ErrorCode;
import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.EntitlementTestSuiteWithEmbeddedDB;
import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.entitlement.api.timeline.SubscriptionTimeline.DeletedEvent;
import com.ning.billing.entitlement.api.timeline.SubscriptionTimeline.ExistingEvent;
import com.ning.billing.entitlement.api.timeline.SubscriptionTimeline.NewEvent;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.api.user.SubscriptionEvents;
import com.ning.billing.entitlement.api.user.TestUtil.TestWithException;
import com.ning.billing.entitlement.api.user.TestUtil.TestWithExceptionCallback;
import com.ning.billing.entitlement.glue.MockEngineModuleSql;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;

public class TestRepairBP extends EntitlementTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testFetchBundleRepair() throws Exception {
        final String baseProduct = "Shotgun";
        final BillingPeriod baseTerm = BillingPeriod.MONTHLY;
        final String basePriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        // CREATE BP
        final Subscription baseSubscription = testUtil.createSubscription(bundle, baseProduct, baseTerm, basePriceList);

        final String aoProduct = "Telescopic-Scope";
        final BillingPeriod aoTerm = BillingPeriod.MONTHLY;
        final String aoPriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        final SubscriptionData aoSubscription = testUtil.createSubscription(bundle, aoProduct, aoTerm, aoPriceList);

        final BundleTimeline bundleRepair = repairApi.getBundleTimeline(bundle.getId(), callContext);
        final List<SubscriptionTimeline> subscriptionRepair = bundleRepair.getSubscriptions();
        assertEquals(subscriptionRepair.size(), 2);

        for (final SubscriptionTimeline cur : subscriptionRepair) {
            assertNull(cur.getDeletedEvents());
            assertNull(cur.getNewEvents());

            final List<ExistingEvent> events = cur.getExistingEvents();
            assertEquals(events.size(), 2);
            testUtil.sortExistingEvent(events);

            assertEquals(events.get(0).getSubscriptionTransitionType(), SubscriptionTransitionType.CREATE);
            assertEquals(events.get(1).getSubscriptionTransitionType(), SubscriptionTransitionType.PHASE);
            final boolean isBP = cur.getId().equals(baseSubscription.getId());
            if (isBP) {
                assertEquals(cur.getId(), baseSubscription.getId());

                assertEquals(events.get(0).getPlanPhaseSpecifier().getProductName(), baseProduct);
                assertEquals(events.get(0).getPlanPhaseSpecifier().getPhaseType(), PhaseType.TRIAL);
                assertEquals(events.get(0).getPlanPhaseSpecifier().getProductCategory(), ProductCategory.BASE);
                assertEquals(events.get(0).getPlanPhaseSpecifier().getPriceListName(), basePriceList);
                assertEquals(events.get(0).getPlanPhaseSpecifier().getBillingPeriod(), BillingPeriod.NO_BILLING_PERIOD);

                assertEquals(events.get(1).getPlanPhaseSpecifier().getProductName(), baseProduct);
                assertEquals(events.get(1).getPlanPhaseSpecifier().getPhaseType(), PhaseType.EVERGREEN);
                assertEquals(events.get(1).getPlanPhaseSpecifier().getProductCategory(), ProductCategory.BASE);
                assertEquals(events.get(1).getPlanPhaseSpecifier().getPriceListName(), basePriceList);
                assertEquals(events.get(1).getPlanPhaseSpecifier().getBillingPeriod(), baseTerm);
            } else {
                assertEquals(cur.getId(), aoSubscription.getId());

                assertEquals(events.get(0).getPlanPhaseSpecifier().getProductName(), aoProduct);
                assertEquals(events.get(0).getPlanPhaseSpecifier().getPhaseType(), PhaseType.DISCOUNT);
                assertEquals(events.get(0).getPlanPhaseSpecifier().getProductCategory(), ProductCategory.ADD_ON);
                assertEquals(events.get(0).getPlanPhaseSpecifier().getPriceListName(), aoPriceList);
                assertEquals(events.get(1).getPlanPhaseSpecifier().getBillingPeriod(), aoTerm);

                assertEquals(events.get(1).getPlanPhaseSpecifier().getProductName(), aoProduct);
                assertEquals(events.get(1).getPlanPhaseSpecifier().getPhaseType(), PhaseType.EVERGREEN);
                assertEquals(events.get(1).getPlanPhaseSpecifier().getProductCategory(), ProductCategory.ADD_ON);
                assertEquals(events.get(1).getPlanPhaseSpecifier().getPriceListName(), aoPriceList);
                assertEquals(events.get(1).getPlanPhaseSpecifier().getBillingPeriod(), aoTerm);
            }
        }
        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testBPRepairWithCancellationOnstart() throws Exception {
        final String baseProduct = "Shotgun";
        final DateTime startDate = clock.getUTCNow();

        // CREATE BP
        final Subscription baseSubscription = testUtil.createSubscription(bundle, baseProduct, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, startDate);

        // Stays in trial-- for instance
        final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(10));
        clock.addDeltaFromReality(it.toDurationMillis());

        final BundleTimeline bundleRepair = repairApi.getBundleTimeline(bundle.getId(), callContext);
        testUtil.sortEventsOnBundle(bundleRepair);

        final List<DeletedEvent> des = new LinkedList<SubscriptionTimeline.DeletedEvent>();
        des.add(testUtil.createDeletedEvent(bundleRepair.getSubscriptions().get(0).getExistingEvents().get(1).getEventId()));
        final NewEvent ne = testUtil.createNewEvent(SubscriptionTransitionType.CANCEL, baseSubscription.getStartDate(), null);

        final SubscriptionTimeline sRepair = testUtil.createSubscriptionRepair(baseSubscription.getId(), des, Collections.singletonList(ne));

        // FIRST ISSUE DRY RUN
        final BundleTimeline bRepair = testUtil.createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(sRepair));

        boolean dryRun = true;
        final BundleTimeline dryRunBundleRepair = repairApi.repairBundle(bRepair, dryRun, callContext);
        testUtil.sortEventsOnBundle(dryRunBundleRepair);
        List<SubscriptionTimeline> subscriptionRepair = dryRunBundleRepair.getSubscriptions();
        assertEquals(subscriptionRepair.size(), 1);
        SubscriptionTimeline cur = subscriptionRepair.get(0);
        int index = 0;
        final List<ExistingEvent> events = subscriptionRepair.get(0).getExistingEvents();
        assertEquals(events.size(), 2);
        final List<ExistingEvent> expected = new LinkedList<SubscriptionTimeline.ExistingEvent>();
        expected.add(testUtil.createExistingEventForAssertion(SubscriptionTransitionType.CREATE, baseProduct, PhaseType.TRIAL,
                                                              ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.NO_BILLING_PERIOD, baseSubscription.getStartDate()));
        expected.add(testUtil.createExistingEventForAssertion(SubscriptionTransitionType.CANCEL, baseProduct, PhaseType.TRIAL,
                                                              ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.NO_BILLING_PERIOD, baseSubscription.getStartDate()));

        for (final ExistingEvent e : expected) {
            testUtil.validateExistingEventForAssertion(e, events.get(index++));
        }

        final SubscriptionData dryRunBaseSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(baseSubscription.getId(), callContext);

        assertEquals(dryRunBaseSubscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION);
        assertEquals(dryRunBaseSubscription.getBundleId(), bundle.getId());
        assertEquals(dryRunBaseSubscription.getStartDate(), baseSubscription.getStartDate());

        final Plan currentPlan = dryRunBaseSubscription.getCurrentPlan();
        assertNotNull(currentPlan);
        assertEquals(currentPlan.getProduct().getName(), baseProduct);
        assertEquals(currentPlan.getProduct().getCategory(), ProductCategory.BASE);
        assertEquals(currentPlan.getBillingPeriod(), BillingPeriod.MONTHLY);

        final PlanPhase currentPhase = dryRunBaseSubscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.TRIAL);

        // SECOND RE-ISSUE CALL-- NON DRY RUN
        dryRun = false;
        testListener.pushExpectedEvent(NextEvent.REPAIR_BUNDLE);
        final BundleTimeline realRunBundleRepair = repairApi.repairBundle(bRepair, dryRun, callContext);
        assertTrue(testListener.isCompleted(5000));

        subscriptionRepair = realRunBundleRepair.getSubscriptions();
        assertEquals(subscriptionRepair.size(), 1);
        cur = subscriptionRepair.get(0);
        assertEquals(cur.getId(), baseSubscription.getId());
        index = 0;
        for (final ExistingEvent e : expected) {
            testUtil.validateExistingEventForAssertion(e, events.get(index++));
        }
        final SubscriptionData realRunBaseSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(baseSubscription.getId(), callContext);
        assertEquals(realRunBaseSubscription.getAllTransitions().size(), 2);

        assertEquals(realRunBaseSubscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION + 1);
        assertEquals(realRunBaseSubscription.getBundleId(), bundle.getId());
        assertEquals(realRunBaseSubscription.getStartDate(), startDate);

        assertEquals(realRunBaseSubscription.getState(), SubscriptionState.CANCELLED);

        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testBPRepairReplaceCreateBeforeTrial() throws Exception {
        final String baseProduct = "Shotgun";
        final String newBaseProduct = "Assault-Rifle";

        final DateTime startDate = clock.getUTCNow();
        final int clockShift = -1;
        final DateTime restartDate = startDate.plusDays(clockShift).minusDays(1);
        final LinkedList<ExistingEvent> expected = new LinkedList<SubscriptionTimeline.ExistingEvent>();

        expected.add(testUtil.createExistingEventForAssertion(SubscriptionTransitionType.CREATE, newBaseProduct, PhaseType.TRIAL,
                                                              ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.NO_BILLING_PERIOD, restartDate));
        expected.add(testUtil.createExistingEventForAssertion(SubscriptionTransitionType.PHASE, newBaseProduct, PhaseType.EVERGREEN,
                                                              ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, restartDate.plusDays(30)));

        testBPRepairCreate(true, startDate, clockShift, baseProduct, newBaseProduct, expected);
        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testBPRepairReplaceCreateInTrial() throws Exception {
        final String baseProduct = "Shotgun";
        final String newBaseProduct = "Assault-Rifle";

        final DateTime startDate = clock.getUTCNow();
        final int clockShift = 10;
        final DateTime restartDate = startDate.plusDays(clockShift).minusDays(1);
        final LinkedList<ExistingEvent> expected = new LinkedList<SubscriptionTimeline.ExistingEvent>();

        expected.add(testUtil.createExistingEventForAssertion(SubscriptionTransitionType.CREATE, newBaseProduct, PhaseType.TRIAL,
                                                              ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.NO_BILLING_PERIOD, restartDate));
        expected.add(testUtil.createExistingEventForAssertion(SubscriptionTransitionType.PHASE, newBaseProduct, PhaseType.EVERGREEN,
                                                              ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, restartDate.plusDays(30)));

        final UUID baseSubscriptionId = testBPRepairCreate(true, startDate, clockShift, baseProduct, newBaseProduct, expected);

        testListener.pushExpectedEvent(NextEvent.PHASE);
        final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(32));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertTrue(testListener.isCompleted(5000));

        // CHECK WHAT"S GOING ON AFTER WE MOVE CLOCK-- FUTURE MOTIFICATION SHOULD KICK IN
        final SubscriptionData subscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(baseSubscriptionId, callContext);

        assertEquals(subscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION + 1);
        assertEquals(subscription.getBundleId(), bundle.getId());
        assertEquals(subscription.getStartDate(), restartDate);
        assertEquals(subscription.getBundleStartDate(), restartDate);

        final Plan currentPlan = subscription.getCurrentPlan();
        assertNotNull(currentPlan);
        assertEquals(currentPlan.getProduct().getName(), newBaseProduct);
        assertEquals(currentPlan.getProduct().getCategory(), ProductCategory.BASE);
        assertEquals(currentPlan.getBillingPeriod(), BillingPeriod.MONTHLY);

        final PlanPhase currentPhase = subscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.EVERGREEN);

        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testBPRepairReplaceCreateAfterTrial() throws Exception {
        final String baseProduct = "Shotgun";
        final String newBaseProduct = "Assault-Rifle";

        final DateTime startDate = clock.getUTCNow();
        final int clockShift = 40;
        final DateTime restartDate = startDate.plusDays(clockShift).minusDays(1);
        final LinkedList<ExistingEvent> expected = new LinkedList<SubscriptionTimeline.ExistingEvent>();

        expected.add(testUtil.createExistingEventForAssertion(SubscriptionTransitionType.CREATE, newBaseProduct, PhaseType.TRIAL,
                                                              ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.NO_BILLING_PERIOD, restartDate));
        expected.add(testUtil.createExistingEventForAssertion(SubscriptionTransitionType.PHASE, newBaseProduct, PhaseType.EVERGREEN,
                                                              ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, restartDate.plusDays(30)));

        testBPRepairCreate(false, startDate, clockShift, baseProduct, newBaseProduct, expected);
        assertListenerStatus();
    }

    private UUID testBPRepairCreate(final boolean inTrial, final DateTime startDate, final int clockShift,
                                    final String baseProduct, final String newBaseProduct, final List<ExistingEvent> expectedEvents) throws Exception {
        // CREATE BP
        final Subscription baseSubscription = testUtil.createSubscription(bundle, baseProduct, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, startDate);

        // MOVE CLOCK
        if (clockShift > 0) {
            if (!inTrial) {
                testListener.pushExpectedEvent(NextEvent.PHASE);
            }

            final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(clockShift));
            clock.addDeltaFromReality(it.toDurationMillis());
            if (!inTrial) {
                assertTrue(testListener.isCompleted(5000));
            }
        }

        final BundleTimeline bundleRepair = repairApi.getBundleTimeline(bundle.getId(), callContext);
        testUtil.sortEventsOnBundle(bundleRepair);

        final DateTime newCreateTime = baseSubscription.getStartDate().plusDays(clockShift - 1);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier(newBaseProduct, ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.TRIAL);

        final NewEvent ne = testUtil.createNewEvent(SubscriptionTransitionType.CREATE, newCreateTime, spec);
        final List<DeletedEvent> des = new LinkedList<SubscriptionTimeline.DeletedEvent>();
        des.add(testUtil.createDeletedEvent(bundleRepair.getSubscriptions().get(0).getExistingEvents().get(0).getEventId()));
        des.add(testUtil.createDeletedEvent(bundleRepair.getSubscriptions().get(0).getExistingEvents().get(1).getEventId()));

        final SubscriptionTimeline sRepair = testUtil.createSubscriptionRepair(baseSubscription.getId(), des, Collections.singletonList(ne));

        // FIRST ISSUE DRY RUN
        final BundleTimeline bRepair = testUtil.createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(sRepair));

        boolean dryRun = true;
        final BundleTimeline dryRunBundleRepair = repairApi.repairBundle(bRepair, dryRun, callContext);
        List<SubscriptionTimeline> subscriptionRepair = dryRunBundleRepair.getSubscriptions();
        assertEquals(subscriptionRepair.size(), 1);
        SubscriptionTimeline cur = subscriptionRepair.get(0);
        assertEquals(cur.getId(), baseSubscription.getId());

        List<ExistingEvent> events = cur.getExistingEvents();
        assertEquals(expectedEvents.size(), events.size());
        int index = 0;
        for (final ExistingEvent e : expectedEvents) {
            testUtil.validateExistingEventForAssertion(e, events.get(index++));
        }
        final SubscriptionData dryRunBaseSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(baseSubscription.getId(), callContext);

        assertEquals(dryRunBaseSubscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION);
        assertEquals(dryRunBaseSubscription.getBundleId(), bundle.getId());
        assertTrue(dryRunBaseSubscription.getStartDate().compareTo(baseSubscription.getStartDate()) == 0);

        Plan currentPlan = dryRunBaseSubscription.getCurrentPlan();
        assertNotNull(currentPlan);
        assertEquals(currentPlan.getProduct().getName(), baseProduct);
        assertEquals(currentPlan.getProduct().getCategory(), ProductCategory.BASE);
        assertEquals(currentPlan.getBillingPeriod(), BillingPeriod.MONTHLY);

        PlanPhase currentPhase = dryRunBaseSubscription.getCurrentPhase();
        assertNotNull(currentPhase);
        if (inTrial) {
            assertEquals(currentPhase.getPhaseType(), PhaseType.TRIAL);
        } else {
            assertEquals(currentPhase.getPhaseType(), PhaseType.EVERGREEN);
        }

        // SECOND RE-ISSUE CALL-- NON DRY RUN
        dryRun = false;
        testListener.pushExpectedEvent(NextEvent.REPAIR_BUNDLE);
        final BundleTimeline realRunBundleRepair = repairApi.repairBundle(bRepair, dryRun, callContext);
        assertTrue(testListener.isCompleted(5000));
        subscriptionRepair = realRunBundleRepair.getSubscriptions();
        assertEquals(subscriptionRepair.size(), 1);
        cur = subscriptionRepair.get(0);
        assertEquals(cur.getId(), baseSubscription.getId());

        events = cur.getExistingEvents();
        for (final ExistingEvent e : events) {
            log.info(String.format("%s, %s, %s, %s", e.getSubscriptionTransitionType(), e.getEffectiveDate(), e.getPlanPhaseSpecifier().getProductName(), e.getPlanPhaseSpecifier().getPhaseType()));
        }
        assertEquals(events.size(), expectedEvents.size());
        index = 0;
        for (final ExistingEvent e : expectedEvents) {
            testUtil.validateExistingEventForAssertion(e, events.get(index++));
        }
        final SubscriptionData realRunBaseSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(baseSubscription.getId(), callContext);
        assertEquals(realRunBaseSubscription.getAllTransitions().size(), 2);

        assertEquals(realRunBaseSubscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION + 1);
        assertEquals(realRunBaseSubscription.getBundleId(), bundle.getId());
        assertEquals(realRunBaseSubscription.getStartDate(), newCreateTime);

        currentPlan = realRunBaseSubscription.getCurrentPlan();
        assertNotNull(currentPlan);
        assertEquals(currentPlan.getProduct().getName(), newBaseProduct);
        assertEquals(currentPlan.getProduct().getCategory(), ProductCategory.BASE);
        assertEquals(currentPlan.getBillingPeriod(), BillingPeriod.MONTHLY);

        currentPhase = realRunBaseSubscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.TRIAL);

        return baseSubscription.getId();
    }

    @Test(groups = "slow")
    public void testBPRepairAddChangeInTrial() throws Exception {
        final String baseProduct = "Shotgun";
        final String newBaseProduct = "Assault-Rifle";

        final DateTime startDate = clock.getUTCNow();
        final int clockShift = 10;
        final DateTime changeDate = startDate.plusDays(clockShift).minusDays(1);
        final LinkedList<ExistingEvent> expected = new LinkedList<SubscriptionTimeline.ExistingEvent>();

        expected.add(testUtil.createExistingEventForAssertion(SubscriptionTransitionType.CREATE, baseProduct, PhaseType.TRIAL,
                                                              ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.NO_BILLING_PERIOD, startDate));
        expected.add(testUtil.createExistingEventForAssertion(SubscriptionTransitionType.CHANGE, newBaseProduct, PhaseType.TRIAL,
                                                              ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.NO_BILLING_PERIOD, changeDate));
        expected.add(testUtil.createExistingEventForAssertion(SubscriptionTransitionType.PHASE, newBaseProduct, PhaseType.EVERGREEN,
                                                              ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, startDate.plusDays(30)));

        final UUID baseSubscriptionId = testBPRepairAddChange(true, startDate, clockShift, baseProduct, newBaseProduct, expected, 3);

        // CHECK WHAT"S GOING ON AFTER WE MOVE CLOCK-- FUTURE MOTIFICATION SHOULD KICK IN
        testListener.pushExpectedEvent(NextEvent.PHASE);
        final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(32));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertTrue(testListener.isCompleted(5000));
        final SubscriptionData subscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(baseSubscriptionId, callContext);

        assertEquals(subscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION + 1);
        assertEquals(subscription.getBundleId(), bundle.getId());
        assertEquals(subscription.getStartDate(), startDate);
        assertEquals(subscription.getBundleStartDate(), startDate);

        final Plan currentPlan = subscription.getCurrentPlan();
        assertNotNull(currentPlan);
        assertEquals(currentPlan.getProduct().getName(), newBaseProduct);
        assertEquals(currentPlan.getProduct().getCategory(), ProductCategory.BASE);
        assertEquals(currentPlan.getBillingPeriod(), BillingPeriod.MONTHLY);

        final PlanPhase currentPhase = subscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.EVERGREEN);

        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testBPRepairAddChangeAfterTrial() throws Exception {
        final String baseProduct = "Shotgun";
        final String newBaseProduct = "Assault-Rifle";

        final DateTime startDate = clock.getUTCNow();
        final int clockShift = 40;
        final DateTime changeDate = startDate.plusDays(clockShift).minusDays(1);

        final LinkedList<ExistingEvent> expected = new LinkedList<SubscriptionTimeline.ExistingEvent>();
        expected.add(testUtil.createExistingEventForAssertion(SubscriptionTransitionType.CREATE, baseProduct, PhaseType.TRIAL,
                                                              ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.NO_BILLING_PERIOD, startDate));
        expected.add(testUtil.createExistingEventForAssertion(SubscriptionTransitionType.PHASE, baseProduct, PhaseType.EVERGREEN,
                                                              ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, startDate.plusDays(30)));
        expected.add(testUtil.createExistingEventForAssertion(SubscriptionTransitionType.CHANGE, newBaseProduct, PhaseType.EVERGREEN,
                                                              ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, changeDate));
        testBPRepairAddChange(false, startDate, clockShift, baseProduct, newBaseProduct, expected, 3);

        assertListenerStatus();
    }

    private UUID testBPRepairAddChange(final boolean inTrial, final DateTime startDate, final int clockShift,
                                       final String baseProduct, final String newBaseProduct, final List<ExistingEvent> expectedEvents, final int expectedTransitions) throws Exception {
        // CREATE BP
        final Subscription baseSubscription = testUtil.createSubscription(bundle, baseProduct, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, startDate);

        // MOVE CLOCK
        if (!inTrial) {
            testListener.pushExpectedEvent(NextEvent.PHASE);
        }

        final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(clockShift));
        clock.addDeltaFromReality(it.toDurationMillis());
        if (!inTrial) {
            assertTrue(testListener.isCompleted(5000));
        }

        final BundleTimeline bundleRepair = repairApi.getBundleTimeline(bundle.getId(), callContext);
        testUtil.sortEventsOnBundle(bundleRepair);

        final DateTime changeTime = baseSubscription.getStartDate().plusDays(clockShift - 1);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier(newBaseProduct, ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.TRIAL);

        final NewEvent ne = testUtil.createNewEvent(SubscriptionTransitionType.CHANGE, changeTime, spec);
        final List<DeletedEvent> des = new LinkedList<SubscriptionTimeline.DeletedEvent>();
        if (inTrial) {
            des.add(testUtil.createDeletedEvent(bundleRepair.getSubscriptions().get(0).getExistingEvents().get(1).getEventId()));
        }
        final SubscriptionTimeline sRepair = testUtil.createSubscriptionRepair(baseSubscription.getId(), des, Collections.singletonList(ne));

        // FIRST ISSUE DRY RUN
        final BundleTimeline bRepair = testUtil.createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(sRepair));

        boolean dryRun = true;
        final BundleTimeline dryRunBundleRepair = repairApi.repairBundle(bRepair, dryRun, callContext);

        List<SubscriptionTimeline> subscriptionRepair = dryRunBundleRepair.getSubscriptions();
        assertEquals(subscriptionRepair.size(), 1);
        SubscriptionTimeline cur = subscriptionRepair.get(0);
        assertEquals(cur.getId(), baseSubscription.getId());

        List<ExistingEvent> events = cur.getExistingEvents();
        assertEquals(expectedEvents.size(), events.size());
        int index = 0;
        for (final ExistingEvent e : expectedEvents) {
            testUtil.validateExistingEventForAssertion(e, events.get(index++));
        }
        final SubscriptionData dryRunBaseSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(baseSubscription.getId(), callContext);

        assertEquals(dryRunBaseSubscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION);
        assertEquals(dryRunBaseSubscription.getBundleId(), bundle.getId());
        assertEquals(dryRunBaseSubscription.getStartDate(), baseSubscription.getStartDate());

        Plan currentPlan = dryRunBaseSubscription.getCurrentPlan();
        assertNotNull(currentPlan);
        assertEquals(currentPlan.getProduct().getName(), baseProduct);
        assertEquals(currentPlan.getProduct().getCategory(), ProductCategory.BASE);
        assertEquals(currentPlan.getBillingPeriod(), BillingPeriod.MONTHLY);

        PlanPhase currentPhase = dryRunBaseSubscription.getCurrentPhase();
        assertNotNull(currentPhase);
        if (inTrial) {
            assertEquals(currentPhase.getPhaseType(), PhaseType.TRIAL);
        } else {
            assertEquals(currentPhase.getPhaseType(), PhaseType.EVERGREEN);
        }

        // SECOND RE-ISSUE CALL-- NON DRY RUN
        dryRun = false;
        testListener.pushExpectedEvent(NextEvent.REPAIR_BUNDLE);
        final BundleTimeline realRunBundleRepair = repairApi.repairBundle(bRepair, dryRun, callContext);
        assertTrue(testListener.isCompleted(5000));

        subscriptionRepair = realRunBundleRepair.getSubscriptions();
        assertEquals(subscriptionRepair.size(), 1);
        cur = subscriptionRepair.get(0);
        assertEquals(cur.getId(), baseSubscription.getId());

        events = cur.getExistingEvents();
        assertEquals(expectedEvents.size(), events.size());
        index = 0;
        for (final ExistingEvent e : expectedEvents) {
            testUtil.validateExistingEventForAssertion(e, events.get(index++));
        }
        final SubscriptionData realRunBaseSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(baseSubscription.getId(), callContext);
        assertEquals(realRunBaseSubscription.getAllTransitions().size(), expectedTransitions);

        assertEquals(realRunBaseSubscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION + 1);
        assertEquals(realRunBaseSubscription.getBundleId(), bundle.getId());
        assertEquals(realRunBaseSubscription.getStartDate(), baseSubscription.getStartDate());

        currentPlan = realRunBaseSubscription.getCurrentPlan();
        assertNotNull(currentPlan);
        assertEquals(currentPlan.getProduct().getName(), newBaseProduct);
        assertEquals(currentPlan.getProduct().getCategory(), ProductCategory.BASE);
        assertEquals(currentPlan.getBillingPeriod(), BillingPeriod.MONTHLY);

        currentPhase = realRunBaseSubscription.getCurrentPhase();
        assertNotNull(currentPhase);
        if (inTrial) {
            assertEquals(currentPhase.getPhaseType(), PhaseType.TRIAL);
        } else {
            assertEquals(currentPhase.getPhaseType(), PhaseType.EVERGREEN);
        }
        return baseSubscription.getId();
    }

    @Test(groups = "slow")
    public void testRepairWithFutureCancelEvent() throws Exception {
        final DateTime startDate = clock.getUTCNow();

        // CREATE BP
        Subscription baseSubscription = testUtil.createSubscription(bundle, "Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, startDate);

        // MOVE CLOCK -- OUT OF TRIAL
        testListener.pushExpectedEvent(NextEvent.PHASE);

        final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(35));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertTrue(testListener.isCompleted(5000));

        // SET CTD to BASE SUBSCRIPTION SP CANCEL OCCURS EOT
        final DateTime newChargedThroughDate = baseSubscription.getStartDate().plusDays(30).plusMonths(1);
        entitlementInternalApi.setChargedThroughDate(baseSubscription.getId(), newChargedThroughDate.toLocalDate(), internalCallContext);
        baseSubscription = entitlementApi.getSubscriptionFromId(baseSubscription.getId(), callContext);

        final DateTime requestedChange = clock.getUTCNow();
        baseSubscription.changePlan("Pistol", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, requestedChange, callContext);

        // CHECK CHANGE DID NOT OCCUR YET
        Plan currentPlan = baseSubscription.getCurrentPlan();
        assertNotNull(currentPlan);
        assertEquals(currentPlan.getProduct().getName(), "Shotgun");
        assertEquals(currentPlan.getProduct().getCategory(), ProductCategory.BASE);
        assertEquals(currentPlan.getBillingPeriod(), BillingPeriod.MONTHLY);

        final DateTime repairTime = clock.getUTCNow().minusDays(1);
        final BundleTimeline bundleRepair = repairApi.getBundleTimeline(bundle.getId(), callContext);
        testUtil.sortEventsOnBundle(bundleRepair);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN);

        final NewEvent ne = testUtil.createNewEvent(SubscriptionTransitionType.CHANGE, repairTime, spec);
        final List<DeletedEvent> des = new LinkedList<SubscriptionTimeline.DeletedEvent>();
        des.add(testUtil.createDeletedEvent(bundleRepair.getSubscriptions().get(0).getExistingEvents().get(2).getEventId()));

        final SubscriptionTimeline sRepair = testUtil.createSubscriptionRepair(baseSubscription.getId(), des, Collections.singletonList(ne));

        // SKIP DRY RUN AND DO REPAIR...
        final BundleTimeline bRepair = testUtil.createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(sRepair));

        final boolean dryRun = false;
        testListener.pushExpectedEvent(NextEvent.REPAIR_BUNDLE);
        repairApi.repairBundle(bRepair, dryRun, callContext);
        assertTrue(testListener.isCompleted(5000));

        baseSubscription = entitlementApi.getSubscriptionFromId(baseSubscription.getId(), callContext);

        assertEquals(((SubscriptionData) baseSubscription).getActiveVersion(), SubscriptionEvents.INITIAL_VERSION + 1);
        assertEquals(baseSubscription.getBundleId(), bundle.getId());
        assertEquals(baseSubscription.getStartDate(), baseSubscription.getStartDate());

        currentPlan = baseSubscription.getCurrentPlan();
        assertNotNull(currentPlan);
        assertEquals(currentPlan.getProduct().getName(), "Assault-Rifle");
        assertEquals(currentPlan.getProduct().getCategory(), ProductCategory.BASE);
        assertEquals(currentPlan.getBillingPeriod(), BillingPeriod.MONTHLY);

        final PlanPhase currentPhase = baseSubscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.EVERGREEN);

        assertListenerStatus();
    }

    // Needs real SQL backend to be tested properly
    @Test(groups = "slow")
    public void testENT_REPAIR_VIEW_CHANGED_newEvent() throws Exception {
        final TestWithException test = new TestWithException();
        final DateTime startDate = clock.getUTCNow();

        final Subscription baseSubscription = testUtil.createSubscription(bundle, "Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, startDate);

        test.withException(new TestWithExceptionCallback() {
            @Override
            public void doTest() throws EntitlementRepairException, EntitlementUserApiException {

                final BundleTimeline bundleRepair = repairApi.getBundleTimeline(bundle.getId(), callContext);
                testUtil.sortEventsOnBundle(bundleRepair);
                final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN);
                final NewEvent ne = testUtil.createNewEvent(SubscriptionTransitionType.CHANGE, baseSubscription.getStartDate().plusDays(10), spec);
                final List<DeletedEvent> des = new LinkedList<SubscriptionTimeline.DeletedEvent>();
                des.add(testUtil.createDeletedEvent(bundleRepair.getSubscriptions().get(0).getExistingEvents().get(0).getEventId()));
                des.add(testUtil.createDeletedEvent(bundleRepair.getSubscriptions().get(0).getExistingEvents().get(1).getEventId()));
                final SubscriptionTimeline sRepair = testUtil.createSubscriptionRepair(baseSubscription.getId(), des, Collections.singletonList(ne));

                final BundleTimeline bRepair = testUtil.createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(sRepair));

                testListener.pushExpectedEvent(NextEvent.CHANGE);
                final DateTime changeTime = clock.getUTCNow();
                baseSubscription.changePlan("Assault-Rifle", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, changeTime, callContext);
                assertTrue(testListener.isCompleted(5000));

                repairApi.repairBundle(bRepair, true, callContext);
                assertListenerStatus();
            }
        }, ErrorCode.ENT_REPAIR_VIEW_CHANGED);
    }

    @Test(groups = "slow")
    public void testENT_REPAIR_VIEW_CHANGED_ctd() throws Exception {
        final TestWithException test = new TestWithException();
        final DateTime startDate = clock.getUTCNow();

        final Subscription baseSubscription = testUtil.createSubscription(bundle, "Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, startDate);

        test.withException(new TestWithExceptionCallback() {
            @Override
            public void doTest() throws EntitlementRepairException, EntitlementUserApiException {

                final BundleTimeline bundleRepair = repairApi.getBundleTimeline(bundle.getId(), callContext);
                testUtil.sortEventsOnBundle(bundleRepair);
                final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN);
                final NewEvent ne = testUtil.createNewEvent(SubscriptionTransitionType.CHANGE, baseSubscription.getStartDate().plusDays(10), spec);
                final List<DeletedEvent> des = new LinkedList<SubscriptionTimeline.DeletedEvent>();
                des.add(testUtil.createDeletedEvent(bundleRepair.getSubscriptions().get(0).getExistingEvents().get(0).getEventId()));
                des.add(testUtil.createDeletedEvent(bundleRepair.getSubscriptions().get(0).getExistingEvents().get(1).getEventId()));
                final SubscriptionTimeline sRepair = testUtil.createSubscriptionRepair(baseSubscription.getId(), des, Collections.singletonList(ne));

                final BundleTimeline bRepair = testUtil.createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(sRepair));

                final DateTime newChargedThroughDate = baseSubscription.getStartDate().plusDays(30).plusMonths(1);

                // Move clock at least a sec to make sure the last_sys_update from bundle is different-- and therefore generates a different viewId
                clock.setDeltaFromReality(1000);

                entitlementInternalApi.setChargedThroughDate(baseSubscription.getId(), newChargedThroughDate.toLocalDate(), internalCallContext);
                entitlementApi.getSubscriptionFromId(baseSubscription.getId(), callContext);

                repairApi.repairBundle(bRepair, true, callContext);

                assertListenerStatus();
            }
        }, ErrorCode.ENT_REPAIR_VIEW_CHANGED);
    }
}
