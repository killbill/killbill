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
import static org.testng.Assert.assertTrue;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.testng.annotations.Test;

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
import com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.api.user.SubscriptionEvents;
import com.ning.billing.entitlement.glue.MockEngineModuleSql;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;

public class TestRepairWithAO extends EntitlementTestSuiteWithEmbeddedDB {



    @Test(groups = "slow")
    public void testRepairChangeBPWithAddonIncluded() throws Exception {
        final String baseProduct = "Shotgun";
        final BillingPeriod baseTerm = BillingPeriod.MONTHLY;
        final String basePriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        // CREATE BP
        final SubscriptionData baseSubscription = testUtil.createSubscription(bundle, baseProduct, baseTerm, basePriceList);

        // MOVE CLOCK A LITTLE BIT-- STILL IN TRIAL
        Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(4));
        clock.addDeltaFromReality(it.toDurationMillis());

        final SubscriptionData aoSubscription = testUtil.createSubscription(bundle, "Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);

        final SubscriptionData aoSubscription2 = testUtil.createSubscription(bundle, "Laser-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);

        // MOVE CLOCK A LITTLE BIT MORE -- STILL IN TRIAL
        it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(3));
        clock.addDeltaFromReality(it.toDurationMillis());

        BundleTimeline bundleRepair = repairApi.getBundleTimeline(bundle.getId(), callContext);
        testUtil.sortEventsOnBundle(bundleRepair);

        // Quick check
        SubscriptionTimeline bpRepair = testUtil.getSubscriptionRepair(baseSubscription.getId(), bundleRepair);
        assertEquals(bpRepair.getExistingEvents().size(), 2);

        SubscriptionTimeline aoRepair = testUtil.getSubscriptionRepair(aoSubscription.getId(), bundleRepair);
        assertEquals(aoRepair.getExistingEvents().size(), 2);

        SubscriptionTimeline aoRepair2 = testUtil.getSubscriptionRepair(aoSubscription2.getId(), bundleRepair);
        assertEquals(aoRepair2.getExistingEvents().size(), 2);

        final DateTime bpChangeDate = clock.getUTCNow().minusDays(1);

        final List<DeletedEvent> des = new LinkedList<SubscriptionTimeline.DeletedEvent>();
        des.add(testUtil.createDeletedEvent(bpRepair.getExistingEvents().get(1).getEventId()));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.TRIAL);
        final NewEvent ne = testUtil.createNewEvent(SubscriptionTransitionType.CHANGE, bpChangeDate, spec);

        bpRepair = testUtil.createSubscriptionRepair(baseSubscription.getId(), des, Collections.singletonList(ne));

        bundleRepair = testUtil.createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(bpRepair));

        boolean dryRun = true;
        final BundleTimeline dryRunBundleRepair = repairApi.repairBundle(bundleRepair, dryRun, callContext);

        aoRepair = testUtil.getSubscriptionRepair(aoSubscription.getId(), dryRunBundleRepair);
        assertEquals(aoRepair.getExistingEvents().size(), 2);

        aoRepair2 = testUtil.getSubscriptionRepair(aoSubscription2.getId(), dryRunBundleRepair);
        assertEquals(aoRepair.getExistingEvents().size(), 2);

        bpRepair = testUtil.getSubscriptionRepair(baseSubscription.getId(), dryRunBundleRepair);
        assertEquals(bpRepair.getExistingEvents().size(), 3);

        // Check expected for AO
        final List<ExistingEvent> expectedAO = new LinkedList<SubscriptionTimeline.ExistingEvent>();
        expectedAO.add(testUtil.createExistingEventForAssertion(SubscriptionTransitionType.CREATE, "Telescopic-Scope", PhaseType.DISCOUNT,
                                                       ProductCategory.ADD_ON, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, aoSubscription.getStartDate()));
        expectedAO.add(testUtil.createExistingEventForAssertion(SubscriptionTransitionType.CANCEL, "Telescopic-Scope", PhaseType.DISCOUNT,
                                                       ProductCategory.ADD_ON, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, bpChangeDate));
        int index = 0;
        for (final ExistingEvent e : expectedAO) {
            testUtil.validateExistingEventForAssertion(e, aoRepair.getExistingEvents().get(index++));
        }

        final List<ExistingEvent> expectedAO2 = new LinkedList<SubscriptionTimeline.ExistingEvent>();
        expectedAO2.add(testUtil.createExistingEventForAssertion(SubscriptionTransitionType.CREATE, "Laser-Scope", PhaseType.DISCOUNT,
                                                        ProductCategory.ADD_ON, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, aoSubscription2.getStartDate()));
        expectedAO2.add(testUtil.createExistingEventForAssertion(SubscriptionTransitionType.PHASE, "Laser-Scope", PhaseType.EVERGREEN,
                                                        ProductCategory.ADD_ON, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, aoSubscription2.getStartDate().plusMonths(1)));
        index = 0;
        for (final ExistingEvent e : expectedAO2) {
            testUtil.validateExistingEventForAssertion(e, aoRepair2.getExistingEvents().get(index++));
        }

        // Check expected for BP
        final List<ExistingEvent> expectedBP = new LinkedList<SubscriptionTimeline.ExistingEvent>();
        expectedBP.add(testUtil.createExistingEventForAssertion(SubscriptionTransitionType.CREATE, "Shotgun", PhaseType.TRIAL,
                                                       ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.NO_BILLING_PERIOD, baseSubscription.getStartDate()));
        expectedBP.add(testUtil.createExistingEventForAssertion(SubscriptionTransitionType.CHANGE, "Assault-Rifle", PhaseType.TRIAL,
                                                       ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.NO_BILLING_PERIOD, bpChangeDate));
        expectedBP.add(testUtil.createExistingEventForAssertion(SubscriptionTransitionType.PHASE, "Assault-Rifle", PhaseType.EVERGREEN,
                                                       ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, baseSubscription.getStartDate().plusDays(30)));
        index = 0;
        for (final ExistingEvent e : expectedBP) {
            testUtil.validateExistingEventForAssertion(e, bpRepair.getExistingEvents().get(index++));
        }

        SubscriptionData newAoSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(aoSubscription.getId(), callContext);
        assertEquals(newAoSubscription.getState(), SubscriptionState.ACTIVE);
        assertEquals(newAoSubscription.getAllTransitions().size(), 2);
        assertEquals(newAoSubscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION);

        SubscriptionData newAoSubscription2 = (SubscriptionData) entitlementApi.getSubscriptionFromId(aoSubscription2.getId(), callContext);
        assertEquals(newAoSubscription2.getState(), SubscriptionState.ACTIVE);
        assertEquals(newAoSubscription2.getAllTransitions().size(), 2);
        assertEquals(newAoSubscription2.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION);

        SubscriptionData newBaseSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(baseSubscription.getId(), callContext);
        assertEquals(newBaseSubscription.getState(), SubscriptionState.ACTIVE);
        assertEquals(newBaseSubscription.getAllTransitions().size(), 2);
        assertEquals(newBaseSubscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION);

        dryRun = false;
        testListener.pushExpectedEvent(NextEvent.REPAIR_BUNDLE);
        final BundleTimeline realRunBundleRepair = repairApi.repairBundle(bundleRepair, dryRun, callContext);
        assertTrue(testListener.isCompleted(5000));

        aoRepair = testUtil.getSubscriptionRepair(aoSubscription.getId(), realRunBundleRepair);
        assertEquals(aoRepair.getExistingEvents().size(), 2);

        bpRepair = testUtil.getSubscriptionRepair(baseSubscription.getId(), realRunBundleRepair);
        assertEquals(bpRepair.getExistingEvents().size(), 3);

        index = 0;
        for (final ExistingEvent e : expectedAO) {
            testUtil.validateExistingEventForAssertion(e, aoRepair.getExistingEvents().get(index++));
        }

        index = 0;
        for (final ExistingEvent e : expectedAO2) {
            testUtil.validateExistingEventForAssertion(e, aoRepair2.getExistingEvents().get(index++));
        }

        index = 0;
        for (final ExistingEvent e : expectedBP) {
            testUtil.validateExistingEventForAssertion(e, bpRepair.getExistingEvents().get(index++));
        }

        newAoSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(aoSubscription.getId(), callContext);
        assertEquals(newAoSubscription.getState(), SubscriptionState.CANCELLED);
        assertEquals(newAoSubscription.getAllTransitions().size(), 2);
        assertEquals(newAoSubscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION + 1);

        newAoSubscription2 = (SubscriptionData) entitlementApi.getSubscriptionFromId(aoSubscription2.getId(), callContext);
        assertEquals(newAoSubscription2.getState(), SubscriptionState.ACTIVE);
        assertEquals(newAoSubscription2.getAllTransitions().size(), 2);
        assertEquals(newAoSubscription2.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION + 1);

        newBaseSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(baseSubscription.getId(), callContext);
        assertEquals(newBaseSubscription.getState(), SubscriptionState.ACTIVE);
        assertEquals(newBaseSubscription.getAllTransitions().size(), 3);
        assertEquals(newBaseSubscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION + 1);
    }

    @Test(groups = "slow")
    public void testRepairChangeBPWithAddonNonAvailable() throws Exception {
        final String baseProduct = "Shotgun";
        final BillingPeriod baseTerm = BillingPeriod.MONTHLY;
        final String basePriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        // CREATE BP
        final SubscriptionData baseSubscription = testUtil.createSubscription(bundle, baseProduct, baseTerm, basePriceList);

        // MOVE CLOCK A LITTLE BIT-- STILL IN TRIAL
        Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(3));
        clock.addDeltaFromReality(it.toDurationMillis());

        final SubscriptionData aoSubscription = testUtil.createSubscription(bundle, "Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);

        // MOVE CLOCK A LITTLE BIT MORE -- AFTER TRIAL
        testListener.pushExpectedEvent(NextEvent.PHASE);
        testListener.pushExpectedEvent(NextEvent.PHASE);

        it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(32));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertTrue(testListener.isCompleted(7000));

        BundleTimeline bundleRepair = repairApi.getBundleTimeline(bundle.getId(), callContext);
        testUtil.sortEventsOnBundle(bundleRepair);

        // Quick check
        SubscriptionTimeline bpRepair = testUtil.getSubscriptionRepair(baseSubscription.getId(), bundleRepair);
        assertEquals(bpRepair.getExistingEvents().size(), 2);

        SubscriptionTimeline aoRepair = testUtil.getSubscriptionRepair(aoSubscription.getId(), bundleRepair);
        assertEquals(aoRepair.getExistingEvents().size(), 2);

        final DateTime bpChangeDate = clock.getUTCNow().minusDays(1);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN);
        final NewEvent ne = testUtil.createNewEvent(SubscriptionTransitionType.CHANGE, bpChangeDate, spec);

        bpRepair = testUtil.createSubscriptionRepair(baseSubscription.getId(), Collections.<SubscriptionTimeline.DeletedEvent>emptyList(), Collections.singletonList(ne));

        bundleRepair = testUtil.createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(bpRepair));

        boolean dryRun = true;
        final BundleTimeline dryRunBundleRepair = repairApi.repairBundle(bundleRepair, dryRun, callContext);

        aoRepair = testUtil.getSubscriptionRepair(aoSubscription.getId(), dryRunBundleRepair);
        assertEquals(aoRepair.getExistingEvents().size(), 3);

        bpRepair = testUtil.getSubscriptionRepair(baseSubscription.getId(), dryRunBundleRepair);
        assertEquals(bpRepair.getExistingEvents().size(), 3);

        // Check expected for AO
        final List<ExistingEvent> expectedAO = new LinkedList<SubscriptionTimeline.ExistingEvent>();
        expectedAO.add(testUtil.createExistingEventForAssertion(SubscriptionTransitionType.CREATE, "Telescopic-Scope", PhaseType.DISCOUNT,
                                                       ProductCategory.ADD_ON, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, aoSubscription.getStartDate()));
        expectedAO.add(testUtil.createExistingEventForAssertion(SubscriptionTransitionType.CREATE, "Telescopic-Scope", PhaseType.EVERGREEN,
                                                       ProductCategory.ADD_ON, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, baseSubscription.getStartDate().plusMonths(1)));
        expectedAO.add(testUtil.createExistingEventForAssertion(SubscriptionTransitionType.CANCEL, "Telescopic-Scope", PhaseType.EVERGREEN,
                                                       ProductCategory.ADD_ON, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, bpChangeDate));
        int index = 0;
        for (final ExistingEvent e : expectedAO) {
            testUtil.validateExistingEventForAssertion(e, aoRepair.getExistingEvents().get(index++));
        }

        // Check expected for BP
        final List<ExistingEvent> expectedBP = new LinkedList<SubscriptionTimeline.ExistingEvent>();
        expectedBP.add(testUtil.createExistingEventForAssertion(SubscriptionTransitionType.CREATE, "Shotgun", PhaseType.TRIAL,
                                                       ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.NO_BILLING_PERIOD, baseSubscription.getStartDate()));
        expectedBP.add(testUtil.createExistingEventForAssertion(SubscriptionTransitionType.PHASE, "Shotgun", PhaseType.EVERGREEN,
                                                       ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, baseSubscription.getStartDate().plusDays(30)));
        expectedBP.add(testUtil.createExistingEventForAssertion(SubscriptionTransitionType.CHANGE, "Pistol", PhaseType.EVERGREEN,
                                                       ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, bpChangeDate));
        index = 0;
        for (final ExistingEvent e : expectedBP) {
            testUtil.validateExistingEventForAssertion(e, bpRepair.getExistingEvents().get(index++));
        }

        SubscriptionData newAoSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(aoSubscription.getId(), callContext);
        assertEquals(newAoSubscription.getState(), SubscriptionState.ACTIVE);
        assertEquals(newAoSubscription.getAllTransitions().size(), 2);
        assertEquals(newAoSubscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION);

        SubscriptionData newBaseSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(baseSubscription.getId(), callContext);
        assertEquals(newBaseSubscription.getState(), SubscriptionState.ACTIVE);
        assertEquals(newBaseSubscription.getAllTransitions().size(), 2);
        assertEquals(newBaseSubscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION);

        dryRun = false;
        testListener.pushExpectedEvent(NextEvent.REPAIR_BUNDLE);
        final BundleTimeline realRunBundleRepair = repairApi.repairBundle(bundleRepair, dryRun, callContext);
        assertTrue(testListener.isCompleted(5000));

        aoRepair = testUtil.getSubscriptionRepair(aoSubscription.getId(), realRunBundleRepair);
        assertEquals(aoRepair.getExistingEvents().size(), 3);

        bpRepair = testUtil.getSubscriptionRepair(baseSubscription.getId(), realRunBundleRepair);
        assertEquals(bpRepair.getExistingEvents().size(), 3);

        index = 0;
        for (final ExistingEvent e : expectedAO) {
            testUtil.validateExistingEventForAssertion(e, aoRepair.getExistingEvents().get(index++));
        }

        index = 0;
        for (final ExistingEvent e : expectedBP) {
            testUtil.validateExistingEventForAssertion(e, bpRepair.getExistingEvents().get(index++));
        }

        newAoSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(aoSubscription.getId(), callContext);
        assertEquals(newAoSubscription.getState(), SubscriptionState.CANCELLED);
        assertEquals(newAoSubscription.getAllTransitions().size(), 3);
        assertEquals(newAoSubscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION + 1);

        newBaseSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(baseSubscription.getId(), callContext);
        assertEquals(newBaseSubscription.getState(), SubscriptionState.ACTIVE);
        assertEquals(newBaseSubscription.getAllTransitions().size(), 3);
        assertEquals(newBaseSubscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION + 1);
    }

    @Test(groups = "slow")
    public void testRepairCancelBP_EOT_WithAddons() throws Exception {
        final String baseProduct = "Shotgun";
        final BillingPeriod baseTerm = BillingPeriod.MONTHLY;
        final String basePriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        // CREATE BP
        SubscriptionData baseSubscription = testUtil.createSubscription(bundle, baseProduct, baseTerm, basePriceList);

        // MOVE CLOCK A LITTLE BIT-- STILL IN TRIAL
        Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(4));
        clock.addDeltaFromReality(it.toDurationMillis());

        final SubscriptionData aoSubscription = testUtil.createSubscription(bundle, "Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);

        // MOVE CLOCK A LITTLE BIT MORE -- AFTER TRIAL
        testListener.pushExpectedEvent(NextEvent.PHASE);
        testListener.pushExpectedEvent(NextEvent.PHASE);

        it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(40));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertTrue(testListener.isCompleted(7000));

        // SET CTD to BASE SUBSCRIPTION SP CANCEL OCCURS EOT
        final DateTime newChargedThroughDate = baseSubscription.getStartDate().plusDays(30).plusMonths(1);
        entitlementInternalApi.setChargedThroughDate(baseSubscription.getId(), newChargedThroughDate, internalCallContext);
        baseSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(baseSubscription.getId(), callContext);

        BundleTimeline bundleRepair = repairApi.getBundleTimeline(bundle.getId(), callContext);
        testUtil.sortEventsOnBundle(bundleRepair);

        // Quick check
        SubscriptionTimeline bpRepair = testUtil.getSubscriptionRepair(baseSubscription.getId(), bundleRepair);
        assertEquals(bpRepair.getExistingEvents().size(), 2);

        SubscriptionTimeline aoRepair = testUtil.getSubscriptionRepair(aoSubscription.getId(), bundleRepair);
        assertEquals(aoRepair.getExistingEvents().size(), 2);

        final DateTime bpCancelDate = clock.getUTCNow().minusDays(1);
        final NewEvent ne = testUtil.createNewEvent(SubscriptionTransitionType.CANCEL, bpCancelDate, null);
        bpRepair = testUtil.createSubscriptionRepair(baseSubscription.getId(), Collections.<SubscriptionTimeline.DeletedEvent>emptyList(), Collections.singletonList(ne));
        bundleRepair = testUtil.createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(bpRepair));

        boolean dryRun = true;
        final BundleTimeline dryRunBundleRepair = repairApi.repairBundle(bundleRepair, dryRun, callContext);

        aoRepair = testUtil.getSubscriptionRepair(aoSubscription.getId(), dryRunBundleRepair);
        assertEquals(aoRepair.getExistingEvents().size(), 3);

        bpRepair = testUtil.getSubscriptionRepair(baseSubscription.getId(), dryRunBundleRepair);
        assertEquals(bpRepair.getExistingEvents().size(), 3);

        // Check expected for AO
        final List<ExistingEvent> expectedAO = new LinkedList<SubscriptionTimeline.ExistingEvent>();
        expectedAO.add(testUtil.createExistingEventForAssertion(SubscriptionTransitionType.CREATE, "Telescopic-Scope", PhaseType.DISCOUNT,
                                                       ProductCategory.ADD_ON, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, aoSubscription.getStartDate()));
        expectedAO.add(testUtil.createExistingEventForAssertion(SubscriptionTransitionType.PHASE, "Telescopic-Scope", PhaseType.EVERGREEN,
                                                       ProductCategory.ADD_ON, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, baseSubscription.getStartDate().plusMonths(1)));
        expectedAO.add(testUtil.createExistingEventForAssertion(SubscriptionTransitionType.CANCEL, "Telescopic-Scope", PhaseType.EVERGREEN,
                                                       ProductCategory.ADD_ON, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, newChargedThroughDate));

        int index = 0;
        for (final ExistingEvent e : expectedAO) {
            testUtil.validateExistingEventForAssertion(e, aoRepair.getExistingEvents().get(index++));
        }

        // Check expected for BP
        final List<ExistingEvent> expectedBP = new LinkedList<SubscriptionTimeline.ExistingEvent>();
        expectedBP.add(testUtil.createExistingEventForAssertion(SubscriptionTransitionType.CREATE, "Shotgun", PhaseType.TRIAL,
                                                       ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.NO_BILLING_PERIOD, baseSubscription.getStartDate()));
        expectedBP.add(testUtil.createExistingEventForAssertion(SubscriptionTransitionType.PHASE, "Shotgun", PhaseType.EVERGREEN,
                                                       ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, baseSubscription.getStartDate().plusDays(30)));
        expectedBP.add(testUtil.createExistingEventForAssertion(SubscriptionTransitionType.CANCEL, "Shotgun", PhaseType.EVERGREEN,
                                                       ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, newChargedThroughDate));
        index = 0;
        for (final ExistingEvent e : expectedBP) {
            testUtil.validateExistingEventForAssertion(e, bpRepair.getExistingEvents().get(index++));
        }

        SubscriptionData newAoSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(aoSubscription.getId(), callContext);
        assertEquals(newAoSubscription.getState(), SubscriptionState.ACTIVE);
        assertEquals(newAoSubscription.getAllTransitions().size(), 2);
        assertEquals(newAoSubscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION);

        SubscriptionData newBaseSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(baseSubscription.getId(), callContext);
        assertEquals(newBaseSubscription.getState(), SubscriptionState.ACTIVE);
        assertEquals(newBaseSubscription.getAllTransitions().size(), 2);
        assertEquals(newBaseSubscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION);

        dryRun = false;
        testListener.pushExpectedEvent(NextEvent.REPAIR_BUNDLE);
        final BundleTimeline realRunBundleRepair = repairApi.repairBundle(bundleRepair, dryRun, callContext);
        assertTrue(testListener.isCompleted(5000));

        aoRepair = testUtil.getSubscriptionRepair(aoSubscription.getId(), realRunBundleRepair);
        assertEquals(aoRepair.getExistingEvents().size(), 3);

        bpRepair = testUtil.getSubscriptionRepair(baseSubscription.getId(), realRunBundleRepair);
        assertEquals(bpRepair.getExistingEvents().size(), 3);

        index = 0;
        for (final ExistingEvent e : expectedAO) {
            testUtil.validateExistingEventForAssertion(e, aoRepair.getExistingEvents().get(index++));
        }

        index = 0;
        for (final ExistingEvent e : expectedBP) {
            testUtil.validateExistingEventForAssertion(e, bpRepair.getExistingEvents().get(index++));
        }

        newAoSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(aoSubscription.getId(), callContext);
        assertEquals(newAoSubscription.getState(), SubscriptionState.ACTIVE);
        assertEquals(newAoSubscription.getAllTransitions().size(), 3);
        assertEquals(newAoSubscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION + 1);

        newBaseSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(baseSubscription.getId(), callContext);
        assertEquals(newBaseSubscription.getState(), SubscriptionState.ACTIVE);
        assertEquals(newBaseSubscription.getAllTransitions().size(), 3);
        assertEquals(newBaseSubscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION + 1);

        // MOVE CLOCK AFTER CANCEL DATE
        testListener.pushExpectedEvent(NextEvent.CANCEL);
        testListener.pushExpectedEvent(NextEvent.CANCEL);

        it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(32));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertTrue(testListener.isCompleted(7000));

        newAoSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(aoSubscription.getId(), callContext);
        assertEquals(newAoSubscription.getState(), SubscriptionState.CANCELLED);
        assertEquals(newAoSubscription.getAllTransitions().size(), 3);
        assertEquals(newAoSubscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION + 1);

        newBaseSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(baseSubscription.getId(), callContext);
        assertEquals(newBaseSubscription.getState(), SubscriptionState.CANCELLED);
        assertEquals(newBaseSubscription.getAllTransitions().size(), 3);
        assertEquals(newBaseSubscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION + 1);
    }

    @Test(groups = "slow")
    public void testRepairCancelAO() throws Exception {
        final String baseProduct = "Shotgun";
        final BillingPeriod baseTerm = BillingPeriod.MONTHLY;
        final String basePriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        // CREATE BP
        final SubscriptionData baseSubscription = testUtil.createSubscription(bundle, baseProduct, baseTerm, basePriceList);

        // MOVE CLOCK A LITTLE BIT-- STILL IN TRIAL
        Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(4));
        clock.addDeltaFromReality(it.toDurationMillis());

        final SubscriptionData aoSubscription = testUtil.createSubscription(bundle, "Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);

        // MOVE CLOCK A LITTLE BIT MORE -- STILL IN TRIAL
        it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(3));
        clock.addDeltaFromReality(it.toDurationMillis());

        final BundleTimeline bundleRepair = repairApi.getBundleTimeline(bundle.getId(), callContext);
        testUtil.sortEventsOnBundle(bundleRepair);

        // Quick check
        SubscriptionTimeline bpRepair = testUtil.getSubscriptionRepair(baseSubscription.getId(), bundleRepair);
        assertEquals(bpRepair.getExistingEvents().size(), 2);

        SubscriptionTimeline aoRepair = testUtil.getSubscriptionRepair(aoSubscription.getId(), bundleRepair);
        assertEquals(aoRepair.getExistingEvents().size(), 2);

        final List<DeletedEvent> des = new LinkedList<SubscriptionTimeline.DeletedEvent>();
        des.add(testUtil.createDeletedEvent(aoRepair.getExistingEvents().get(1).getEventId()));
        final DateTime aoCancelDate = aoSubscription.getStartDate().plusDays(1);

        final NewEvent ne = testUtil.createNewEvent(SubscriptionTransitionType.CANCEL, aoCancelDate, null);

        final SubscriptionTimeline saoRepair = testUtil.createSubscriptionRepair(aoSubscription.getId(), des, Collections.singletonList(ne));

        final BundleTimeline bRepair = testUtil.createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(saoRepair));

        boolean dryRun = true;
        final BundleTimeline dryRunBundleRepair = repairApi.repairBundle(bRepair, dryRun, callContext);

        aoRepair = testUtil.getSubscriptionRepair(aoSubscription.getId(), dryRunBundleRepair);
        assertEquals(aoRepair.getExistingEvents().size(), 2);

        bpRepair = testUtil.getSubscriptionRepair(baseSubscription.getId(), bundleRepair);
        assertEquals(bpRepair.getExistingEvents().size(), 2);

        final List<ExistingEvent> expected = new LinkedList<SubscriptionTimeline.ExistingEvent>();
        expected.add(testUtil.createExistingEventForAssertion(SubscriptionTransitionType.CREATE, "Telescopic-Scope", PhaseType.DISCOUNT,
                                                     ProductCategory.ADD_ON, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, aoSubscription.getStartDate()));
        expected.add(testUtil.createExistingEventForAssertion(SubscriptionTransitionType.CANCEL, "Telescopic-Scope", PhaseType.DISCOUNT,
                                                     ProductCategory.ADD_ON, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, aoCancelDate));
        int index = 0;
        for (final ExistingEvent e : expected) {
            testUtil.validateExistingEventForAssertion(e, aoRepair.getExistingEvents().get(index++));
        }
        SubscriptionData newAoSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(aoSubscription.getId(), callContext);
        assertEquals(newAoSubscription.getState(), SubscriptionState.ACTIVE);
        assertEquals(newAoSubscription.getAllTransitions().size(), 2);
        assertEquals(newAoSubscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION);

        SubscriptionData newBaseSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(baseSubscription.getId(), callContext);
        assertEquals(newBaseSubscription.getState(), SubscriptionState.ACTIVE);
        assertEquals(newBaseSubscription.getAllTransitions().size(), 2);
        assertEquals(newBaseSubscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION);

        dryRun = false;
        testListener.pushExpectedEvent(NextEvent.REPAIR_BUNDLE);
        final BundleTimeline realRunBundleRepair = repairApi.repairBundle(bRepair, dryRun, callContext);
        assertTrue(testListener.isCompleted(5000));

        aoRepair = testUtil.getSubscriptionRepair(aoSubscription.getId(), realRunBundleRepair);
        assertEquals(aoRepair.getExistingEvents().size(), 2);
        index = 0;
        for (final ExistingEvent e : expected) {
            testUtil.validateExistingEventForAssertion(e, aoRepair.getExistingEvents().get(index++));
        }

        newAoSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(aoSubscription.getId(), callContext);
        assertEquals(newAoSubscription.getState(), SubscriptionState.CANCELLED);
        assertEquals(newAoSubscription.getAllTransitions().size(), 2);
        assertEquals(newAoSubscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION + 1);

        newBaseSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(baseSubscription.getId(), callContext);
        assertEquals(newBaseSubscription.getState(), SubscriptionState.ACTIVE);
        assertEquals(newBaseSubscription.getAllTransitions().size(), 2);
        assertEquals(newBaseSubscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION);
    }

    @Test(groups = "slow")
    public void testRepairRecreateAO() throws Exception {
        final String baseProduct = "Shotgun";
        final BillingPeriod baseTerm = BillingPeriod.MONTHLY;
        final String basePriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        // CREATE BP
        final SubscriptionData baseSubscription = testUtil.createSubscription(bundle, baseProduct, baseTerm, basePriceList);

        // MOVE CLOCK A LITTLE BIT-- STILL IN TRIAL
        Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(4));
        clock.addDeltaFromReality(it.toDurationMillis());

        final SubscriptionData aoSubscription = testUtil.createSubscription(bundle, "Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);

        // MOVE CLOCK A LITTLE BIT MORE -- STILL IN TRIAL
        it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(3));
        clock.addDeltaFromReality(it.toDurationMillis());

        final BundleTimeline bundleRepair = repairApi.getBundleTimeline(bundle.getId(), callContext);
        testUtil.sortEventsOnBundle(bundleRepair);

        // Quick check
        final SubscriptionTimeline bpRepair = testUtil.getSubscriptionRepair(baseSubscription.getId(), bundleRepair);
        assertEquals(bpRepair.getExistingEvents().size(), 2);

        SubscriptionTimeline aoRepair = testUtil.getSubscriptionRepair(aoSubscription.getId(), bundleRepair);
        assertEquals(aoRepair.getExistingEvents().size(), 2);

        final List<DeletedEvent> des = new LinkedList<SubscriptionTimeline.DeletedEvent>();
        des.add(testUtil.createDeletedEvent(aoRepair.getExistingEvents().get(0).getEventId()));
        des.add(testUtil.createDeletedEvent(aoRepair.getExistingEvents().get(1).getEventId()));

        final DateTime aoRecreateDate = aoSubscription.getStartDate().plusDays(1);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Telescopic-Scope", ProductCategory.ADD_ON, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.DISCOUNT);
        final NewEvent ne = testUtil.createNewEvent(SubscriptionTransitionType.CREATE, aoRecreateDate, spec);

        final SubscriptionTimeline saoRepair = testUtil.createSubscriptionRepair(aoSubscription.getId(), des, Collections.singletonList(ne));

        final BundleTimeline bRepair = testUtil.createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(saoRepair));

        boolean dryRun = true;
        final BundleTimeline dryRunBundleRepair = repairApi.repairBundle(bRepair, dryRun, callContext);

        aoRepair = testUtil.getSubscriptionRepair(aoSubscription.getId(), dryRunBundleRepair);
        assertEquals(aoRepair.getExistingEvents().size(), 2);

        final List<ExistingEvent> expected = new LinkedList<SubscriptionTimeline.ExistingEvent>();
        expected.add(testUtil.createExistingEventForAssertion(SubscriptionTransitionType.CREATE, "Telescopic-Scope", PhaseType.DISCOUNT,
                                                     ProductCategory.ADD_ON, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, aoRecreateDate));
        expected.add(testUtil.createExistingEventForAssertion(SubscriptionTransitionType.PHASE, "Telescopic-Scope", PhaseType.EVERGREEN,
                                                     ProductCategory.ADD_ON, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, baseSubscription.getStartDate().plusMonths(1) /* Bundle align */));
        int index = 0;
        for (final ExistingEvent e : expected) {
            testUtil.validateExistingEventForAssertion(e, aoRepair.getExistingEvents().get(index++));
        }
        SubscriptionData newAoSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(aoSubscription.getId(), callContext);
        assertEquals(newAoSubscription.getState(), SubscriptionState.ACTIVE);
        assertEquals(newAoSubscription.getAllTransitions().size(), 2);
        assertEquals(newAoSubscription.getStartDate(), aoSubscription.getStartDate());
        assertEquals(newAoSubscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION);

        // NOW COMMIT
        dryRun = false;
        testListener.pushExpectedEvent(NextEvent.REPAIR_BUNDLE);
        final BundleTimeline realRunBundleRepair = repairApi.repairBundle(bRepair, dryRun, callContext);
        assertTrue(testListener.isCompleted(5000));

        aoRepair = testUtil.getSubscriptionRepair(aoSubscription.getId(), realRunBundleRepair);
        assertEquals(aoRepair.getExistingEvents().size(), 2);
        index = 0;
        for (final ExistingEvent e : expected) {
            testUtil.validateExistingEventForAssertion(e, aoRepair.getExistingEvents().get(index++));
        }

        newAoSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(aoSubscription.getId(), callContext);
        assertEquals(newAoSubscription.getState(), SubscriptionState.ACTIVE);
        assertEquals(newAoSubscription.getAllTransitions().size(), 2);
        assertEquals(newAoSubscription.getStartDate(), aoRecreateDate);
        assertEquals(newAoSubscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION + 1);

    }

    // Fasten your seatbelt here:
    //
    // We are doing repair for multi-phase tiered-addon with different alignment:
    // Telescopic-Scope -> Laser-Scope
    // Tiered ADON logic
    // . Both multi phase
    // . Telescopic-Scope (bundle align) and Laser-Scope is Subscription align
    //
    @Test(groups = "slow")
    public void testRepairChangeAOOK() throws Exception {
        final String baseProduct = "Shotgun";
        final BillingPeriod baseTerm = BillingPeriod.MONTHLY;
        final String basePriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        // CREATE BP
        final SubscriptionData baseSubscription = testUtil.createSubscription(bundle, baseProduct, baseTerm, basePriceList);

        // MOVE CLOCK A LITTLE BIT-- STILL IN TRIAL
        Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(4));
        clock.addDeltaFromReality(it.toDurationMillis());

        final SubscriptionData aoSubscription = testUtil.createSubscription(bundle, "Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);

        // MOVE CLOCK A LITTLE BIT MORE -- STILL IN TRIAL
        it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(3));
        clock.addDeltaFromReality(it.toDurationMillis());

        final BundleTimeline bundleRepair = repairApi.getBundleTimeline(bundle.getId(), callContext);
        testUtil.sortEventsOnBundle(bundleRepair);

        // Quick check
        final SubscriptionTimeline bpRepair = testUtil.getSubscriptionRepair(baseSubscription.getId(), bundleRepair);
        assertEquals(bpRepair.getExistingEvents().size(), 2);

        SubscriptionTimeline aoRepair = testUtil.getSubscriptionRepair(aoSubscription.getId(), bundleRepair);
        assertEquals(aoRepair.getExistingEvents().size(), 2);

        final List<DeletedEvent> des = new LinkedList<SubscriptionTimeline.DeletedEvent>();
        des.add(testUtil.createDeletedEvent(aoRepair.getExistingEvents().get(1).getEventId()));
        final DateTime aoChangeDate = aoSubscription.getStartDate().plusDays(1);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Laser-Scope", ProductCategory.ADD_ON, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.TRIAL);
        final NewEvent ne = testUtil.createNewEvent(SubscriptionTransitionType.CHANGE, aoChangeDate, spec);

        final SubscriptionTimeline saoRepair = testUtil.createSubscriptionRepair(aoSubscription.getId(), des, Collections.singletonList(ne));

        final BundleTimeline bRepair = testUtil.createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(saoRepair));

        boolean dryRun = true;
        final BundleTimeline dryRunBundleRepair = repairApi.repairBundle(bRepair, dryRun, callContext);

        aoRepair = testUtil.getSubscriptionRepair(aoSubscription.getId(), dryRunBundleRepair);
        assertEquals(aoRepair.getExistingEvents().size(), 3);

        final List<ExistingEvent> expected = new LinkedList<SubscriptionTimeline.ExistingEvent>();
        expected.add(testUtil.createExistingEventForAssertion(SubscriptionTransitionType.CREATE, "Telescopic-Scope", PhaseType.DISCOUNT,
                                                     ProductCategory.ADD_ON, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, aoSubscription.getStartDate()));
        expected.add(testUtil.createExistingEventForAssertion(SubscriptionTransitionType.CHANGE, "Laser-Scope", PhaseType.DISCOUNT,
                                                     ProductCategory.ADD_ON, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, aoChangeDate));
        expected.add(testUtil.createExistingEventForAssertion(SubscriptionTransitionType.PHASE, "Laser-Scope", PhaseType.EVERGREEN,
                                                     ProductCategory.ADD_ON, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY,
                                                     aoSubscription.getStartDate().plusMonths(1) /* Subscription alignment */));

        int index = 0;
        for (final ExistingEvent e : expected) {
            testUtil.validateExistingEventForAssertion(e, aoRepair.getExistingEvents().get(index++));
        }
        SubscriptionData newAoSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(aoSubscription.getId(), callContext);
        assertEquals(newAoSubscription.getState(), SubscriptionState.ACTIVE);
        assertEquals(newAoSubscription.getAllTransitions().size(), 2);

        // AND NOW COMMIT
        dryRun = false;
        testListener.pushExpectedEvent(NextEvent.REPAIR_BUNDLE);
        final BundleTimeline realRunBundleRepair = repairApi.repairBundle(bRepair, dryRun, callContext);
        assertTrue(testListener.isCompleted(5000));

        aoRepair = testUtil.getSubscriptionRepair(aoSubscription.getId(), realRunBundleRepair);
        assertEquals(aoRepair.getExistingEvents().size(), 3);
        index = 0;
        for (final ExistingEvent e : expected) {
            testUtil.validateExistingEventForAssertion(e, aoRepair.getExistingEvents().get(index++));
        }

        newAoSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(aoSubscription.getId(), callContext);
        assertEquals(newAoSubscription.getState(), SubscriptionState.ACTIVE);
        assertEquals(newAoSubscription.getAllTransitions().size(), 3);

        assertEquals(newAoSubscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION + 1);
        assertEquals(newAoSubscription.getBundleId(), bundle.getId());
        assertEquals(newAoSubscription.getStartDate(), aoSubscription.getStartDate());

        final Plan currentPlan = newAoSubscription.getCurrentPlan();
        assertNotNull(currentPlan);
        assertEquals(currentPlan.getProduct().getName(), "Laser-Scope");
        assertEquals(currentPlan.getProduct().getCategory(), ProductCategory.ADD_ON);
        assertEquals(currentPlan.getBillingPeriod(), BillingPeriod.MONTHLY);

        PlanPhase currentPhase = newAoSubscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.DISCOUNT);

        testListener.pushExpectedEvent(NextEvent.PHASE);

        it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(60));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertTrue(testListener.isCompleted(5000));

        newAoSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(aoSubscription.getId(), callContext);
        currentPhase = newAoSubscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.EVERGREEN);
    }
}
