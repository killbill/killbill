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

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.ErrorCode;
import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.EntitlementTestSuiteNoDB;
import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.entitlement.api.timeline.SubscriptionTimeline.DeletedEvent;
import com.ning.billing.entitlement.api.timeline.SubscriptionTimeline.NewEvent;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.api.user.TestUtil.TestWithException;
import com.ning.billing.entitlement.api.user.TestUtil.TestWithExceptionCallback;
import com.ning.billing.entitlement.glue.MockEngineModuleMemory;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestRepairWithError extends EntitlementTestSuiteNoDB {

    private static final String baseProduct = "Shotgun";
    private TestWithException test;
    private Subscription baseSubscription;


    @BeforeMethod(alwaysRun = true)
    public void setupTest() throws Exception {
        super.setupTest();
        test = new TestWithException();
        final DateTime startDate = clock.getUTCNow();
        baseSubscription = testUtil.createSubscription(bundle, baseProduct, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, startDate);
    }

    @Test(groups = "fast")
    public void testENT_REPAIR_NEW_EVENT_BEFORE_LAST_BP_REMAINING() throws Exception {
        test.withException(new TestWithExceptionCallback() {
            @Override
            public void doTest() throws EntitlementRepairException {
                // MOVE AFTER TRIAL
                testListener.pushExpectedEvent(NextEvent.PHASE);

                final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(40));
                clock.addDeltaFromReality(it.toDurationMillis());

                assertTrue(testListener.isCompleted(5000));

                final BundleTimeline bundleRepair = repairApi.getBundleTimeline(bundle.getId(), callContext);
                testUtil.sortEventsOnBundle(bundleRepair);
                final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN);
                final NewEvent ne = testUtil.createNewEvent(SubscriptionTransitionType.CHANGE, baseSubscription.getStartDate().plusDays(10), spec);

                final SubscriptionTimeline sRepair = testUtil.createSubscriptionRepair(baseSubscription.getId(), Collections.<DeletedEvent>emptyList(), Collections.singletonList(ne));

                final BundleTimeline bRepair = testUtil.createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(sRepair));

                repairApi.repairBundle(bRepair, true, callContext);
            }
        }, ErrorCode.ENT_REPAIR_NEW_EVENT_BEFORE_LAST_BP_REMAINING);
    }

    @Test(groups = "fast")
    public void testENT_REPAIR_INVALID_DELETE_SET() throws Exception {
        test.withException(new TestWithExceptionCallback() {
            @Override
            public void doTest() throws EntitlementRepairException, EntitlementUserApiException {

                Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(3));
                clock.addDeltaFromReality(it.toDurationMillis());

                testListener.pushExpectedEvent(NextEvent.CHANGE);
                final DateTime changeTime = clock.getUTCNow();
                baseSubscription.changePlan("Assault-Rifle", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, changeTime, callContext);
                assertTrue(testListener.isCompleted(5000));

                // MOVE AFTER TRIAL
                testListener.pushExpectedEvent(NextEvent.PHASE);
                it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(40));
                clock.addDeltaFromReality(it.toDurationMillis());
                assertTrue(testListener.isCompleted(5000));

                final BundleTimeline bundleRepair = repairApi.getBundleTimeline(bundle.getId(), callContext);
                testUtil.sortEventsOnBundle(bundleRepair);
                final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN);
                final NewEvent ne = testUtil.createNewEvent(SubscriptionTransitionType.CHANGE, baseSubscription.getStartDate().plusDays(10), spec);
                final DeletedEvent de = testUtil.createDeletedEvent(bundleRepair.getSubscriptions().get(0).getExistingEvents().get(1).getEventId());

                final SubscriptionTimeline sRepair = testUtil.createSubscriptionRepair(baseSubscription.getId(), Collections.singletonList(de), Collections.singletonList(ne));
                final BundleTimeline bRepair = testUtil.createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(sRepair));

                repairApi.repairBundle(bRepair, true, callContext);
            }
        }, ErrorCode.ENT_REPAIR_INVALID_DELETE_SET);
    }

    @Test(groups = "fast")
    public void testENT_REPAIR_NON_EXISTENT_DELETE_EVENT() throws Exception {
        test.withException(new TestWithExceptionCallback() {
            @Override
            public void doTest() throws EntitlementRepairException {

                final BundleTimeline bundleRepair = repairApi.getBundleTimeline(bundle.getId(), callContext);
                testUtil.sortEventsOnBundle(bundleRepair);
                final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN);
                final NewEvent ne = testUtil.createNewEvent(SubscriptionTransitionType.CHANGE, baseSubscription.getStartDate().plusDays(10), spec);
                final DeletedEvent de = testUtil.createDeletedEvent(UUID.randomUUID());
                final SubscriptionTimeline sRepair = testUtil.createSubscriptionRepair(baseSubscription.getId(), Collections.singletonList(de), Collections.singletonList(ne));

                final BundleTimeline bRepair = testUtil.createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(sRepair));

                repairApi.repairBundle(bRepair, true, callContext);
            }
        }, ErrorCode.ENT_REPAIR_NON_EXISTENT_DELETE_EVENT);
    }

    @Test(groups = "fast")
    public void testENT_REPAIR_SUB_RECREATE_NOT_EMPTY() throws Exception {
        test.withException(new TestWithExceptionCallback() {
            @Override
            public void doTest() throws EntitlementRepairException {

                // MOVE AFTER TRIAL
                testListener.pushExpectedEvent(NextEvent.PHASE);
                final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(40));
                clock.addDeltaFromReality(it.toDurationMillis());
                assertTrue(testListener.isCompleted(5000));

                final BundleTimeline bundleRepair = repairApi.getBundleTimeline(bundle.getId(), callContext);
                testUtil.sortEventsOnBundle(bundleRepair);
                final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN);
                final NewEvent ne = testUtil.createNewEvent(SubscriptionTransitionType.CREATE, baseSubscription.getStartDate().plusDays(10), spec);
                final List<DeletedEvent> des = new LinkedList<SubscriptionTimeline.DeletedEvent>();
                des.add(testUtil.createDeletedEvent(bundleRepair.getSubscriptions().get(0).getExistingEvents().get(1).getEventId()));
                final SubscriptionTimeline sRepair = testUtil.createSubscriptionRepair(baseSubscription.getId(), des, Collections.singletonList(ne));

                final BundleTimeline bRepair = testUtil.createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(sRepair));

                repairApi.repairBundle(bRepair, true, callContext);

            }
        }, ErrorCode.ENT_REPAIR_SUB_RECREATE_NOT_EMPTY);
    }

    @Test(groups = "fast")
    public void testENT_REPAIR_SUB_EMPTY() throws Exception {
        test.withException(new TestWithExceptionCallback() {

            @Override
            public void doTest() throws EntitlementRepairException {

                // MOVE AFTER TRIAL
                testListener.pushExpectedEvent(NextEvent.PHASE);
                final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(40));
                clock.addDeltaFromReality(it.toDurationMillis());
                assertTrue(testListener.isCompleted(5000));

                final BundleTimeline bundleRepair = repairApi.getBundleTimeline(bundle.getId(), callContext);
                testUtil.sortEventsOnBundle(bundleRepair);
                final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN);
                final NewEvent ne = testUtil.createNewEvent(SubscriptionTransitionType.CHANGE, baseSubscription.getStartDate().plusDays(10), spec);
                final List<DeletedEvent> des = new LinkedList<SubscriptionTimeline.DeletedEvent>();
                des.add(testUtil.createDeletedEvent(bundleRepair.getSubscriptions().get(0).getExistingEvents().get(0).getEventId()));
                des.add(testUtil.createDeletedEvent(bundleRepair.getSubscriptions().get(0).getExistingEvents().get(1).getEventId()));
                final SubscriptionTimeline sRepair = testUtil.createSubscriptionRepair(baseSubscription.getId(), des, Collections.singletonList(ne));

                final BundleTimeline bRepair = testUtil.createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(sRepair));

                repairApi.repairBundle(bRepair, true, callContext);
            }
        }, ErrorCode.ENT_REPAIR_SUB_EMPTY);
    }

    @Test(groups = "fast")
    public void testENT_REPAIR_AO_CREATE_BEFORE_BP_START() throws Exception {
        test.withException(new TestWithExceptionCallback() {
            @Override
            public void doTest() throws EntitlementRepairException, EntitlementUserApiException {
                // MOVE CLOCK A LITTLE BIT-- STILL IN TRIAL
                Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(4));
                clock.addDeltaFromReality(it.toDurationMillis());
                final SubscriptionData aoSubscription = testUtil.createSubscription(bundle, "Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);

                // MOVE CLOCK A LITTLE BIT MORE -- STILL IN TRIAL
                it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(4));
                clock.addDeltaFromReality(it.toDurationMillis());

                final BundleTimeline bundleRepair = repairApi.getBundleTimeline(bundle.getId(), callContext);
                testUtil.sortEventsOnBundle(bundleRepair);

                // Quick check
                final SubscriptionTimeline bpRepair = testUtil.getSubscriptionRepair(baseSubscription.getId(), bundleRepair);
                assertEquals(bpRepair.getExistingEvents().size(), 2);

                final SubscriptionTimeline aoRepair = testUtil.getSubscriptionRepair(aoSubscription.getId(), bundleRepair);
                assertEquals(aoRepair.getExistingEvents().size(), 2);

                final List<DeletedEvent> des = new LinkedList<SubscriptionTimeline.DeletedEvent>();
                des.add(testUtil.createDeletedEvent(aoRepair.getExistingEvents().get(0).getEventId()));
                des.add(testUtil.createDeletedEvent(aoRepair.getExistingEvents().get(1).getEventId()));

                final DateTime aoRecreateDate = aoSubscription.getStartDate().minusDays(5);
                final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Telescopic-Scope", ProductCategory.ADD_ON, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.DISCOUNT);
                final NewEvent ne = testUtil.createNewEvent(SubscriptionTransitionType.CREATE, aoRecreateDate, spec);

                final SubscriptionTimeline saoRepair = testUtil.createSubscriptionRepair(aoSubscription.getId(), des, Collections.singletonList(ne));

                final BundleTimeline bRepair = testUtil.createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(saoRepair));

                final boolean dryRun = true;
                repairApi.repairBundle(bRepair, dryRun, callContext);
            }
        }, ErrorCode.ENT_REPAIR_AO_CREATE_BEFORE_BP_START);
    }

    @Test(groups = "fast")
    public void testENT_REPAIR_NEW_EVENT_BEFORE_LAST_AO_REMAINING() throws Exception {
        test.withException(new TestWithExceptionCallback() {
            @Override
            public void doTest() throws EntitlementRepairException, EntitlementUserApiException {

                // MOVE CLOCK A LITTLE BIT-- STILL IN TRIAL
                Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(4));
                clock.addDeltaFromReality(it.toDurationMillis());
                final SubscriptionData aoSubscription = testUtil.createSubscription(bundle, "Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);

                // MOVE CLOCK A LITTLE BIT MORE -- STILL IN TRIAL
                it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(4));
                clock.addDeltaFromReality(it.toDurationMillis());

                BundleTimeline bundleRepair = repairApi.getBundleTimeline(bundle.getId(), callContext);
                testUtil.sortEventsOnBundle(bundleRepair);

                // Quick check
                final SubscriptionTimeline bpRepair = testUtil.getSubscriptionRepair(baseSubscription.getId(), bundleRepair);
                assertEquals(bpRepair.getExistingEvents().size(), 2);

                final SubscriptionTimeline aoRepair = testUtil.getSubscriptionRepair(aoSubscription.getId(), bundleRepair);
                assertEquals(aoRepair.getExistingEvents().size(), 2);

                final List<DeletedEvent> des = new LinkedList<SubscriptionTimeline.DeletedEvent>();
                //des.add(createDeletedEvent(aoRepair.getExistingEvents().get(1).getEventId()));        
                final DateTime aoCancelDate = aoSubscription.getStartDate().plusDays(10);

                final NewEvent ne = testUtil.createNewEvent(SubscriptionTransitionType.CANCEL, aoCancelDate, null);

                final SubscriptionTimeline saoRepair = testUtil.createSubscriptionRepair(aoSubscription.getId(), des, Collections.singletonList(ne));

                bundleRepair = testUtil.createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(saoRepair));

                final boolean dryRun = true;
                repairApi.repairBundle(bundleRepair, dryRun, callContext);
            }
        }, ErrorCode.ENT_REPAIR_NEW_EVENT_BEFORE_LAST_AO_REMAINING);
    }

    @Test(groups = "fast", enabled = false) // TODO - fails on jdk7 on Travis
    public void testENT_REPAIR_BP_RECREATE_MISSING_AO() throws Exception {
        test.withException(new TestWithExceptionCallback() {
            @Override
            public void doTest() throws EntitlementRepairException, EntitlementUserApiException {

                //testListener.pushExpectedEvent(NextEvent.PHASE);

                final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(4));
                clock.addDeltaFromReality(it.toDurationMillis());
                //assertTrue(testListener.isCompleted(5000));

                final SubscriptionData aoSubscription = testUtil.createSubscription(bundle, "Laser-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);

                final BundleTimeline bundleRepair = repairApi.getBundleTimeline(bundle.getId(), callContext);
                testUtil.sortEventsOnBundle(bundleRepair);

                final DateTime newCreateTime = baseSubscription.getStartDate().plusDays(3);

                final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.TRIAL);

                final NewEvent ne = testUtil.createNewEvent(SubscriptionTransitionType.CREATE, newCreateTime, spec);
                final List<DeletedEvent> des = new LinkedList<SubscriptionTimeline.DeletedEvent>();
                des.add(testUtil.createDeletedEvent(bundleRepair.getSubscriptions().get(0).getExistingEvents().get(0).getEventId()));
                des.add(testUtil.createDeletedEvent(bundleRepair.getSubscriptions().get(0).getExistingEvents().get(1).getEventId()));

                final SubscriptionTimeline sRepair = testUtil.createSubscriptionRepair(baseSubscription.getId(), des, Collections.singletonList(ne));

                // FIRST ISSUE DRY RUN
                final BundleTimeline bRepair = testUtil.createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(sRepair));

                final boolean dryRun = true;
                repairApi.repairBundle(bRepair, dryRun, callContext);
            }
        }, ErrorCode.ENT_REPAIR_BP_RECREATE_MISSING_AO);
    }

    //
    // CAN'T seem to trigger such case easily, other errors trigger before...
    //
    @Test(groups = "fast", enabled = false)
    public void testENT_REPAIR_BP_RECREATE_MISSING_AO_CREATE() throws Exception {
        test.withException(new TestWithExceptionCallback() {
            @Override
            public void doTest() throws EntitlementRepairException, EntitlementUserApiException {
                /*
              //testListener.pushExpectedEvent(NextEvent.PHASE);

                Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(4));
                clock.addDeltaFromReality(it.toDurationMillis());


                SubscriptionData aoSubscription = createSubscription("Laser-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);
                
                BundleRepair bundleRepair = repairApi.getBundleRepair(bundle.getId());
                sortEventsOnBundle(bundleRepair);
                
                DateTime newCreateTime = baseSubscription.getStartDate().plusDays(3);

                PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.TRIAL);

                NewEvent ne = createNewEvent(SubscriptionTransitionType.CREATE, newCreateTime, spec);
                List<DeletedEvent> des = new LinkedList<SubscriptionRepair.DeletedEvent>();
                des.add(createDeletedEvent(bundleRepair.getSubscriptions().get(0).getExistingEvents().get(0).getEventId()));
                des.add(createDeletedEvent(bundleRepair.getSubscriptions().get(0).getExistingEvents().get(1).getEventId()));

                SubscriptionRepair bpRepair = createSubscriptionReapir(baseSubscription.getId(), des, Collections.singletonList(ne));
                
                ne = createNewEvent(SubscriptionTransitionType.CANCEL, clock.getUTCNow().minusDays(1),  null);
                SubscriptionRepair aoRepair = createSubscriptionReapir(aoSubscription.getId(), Collections.<SubscriptionRepair.DeletedEvent>emptyList(), Collections.singletonList(ne));
                
                
                List<SubscriptionRepair> allRepairs = new LinkedList<SubscriptionRepair>();
                allRepairs.add(bpRepair);
                allRepairs.add(aoRepair);
                bundleRepair =  createBundleRepair(bundle.getId(), bundleRepair.getViewId(), allRepairs);
                // FIRST ISSUE DRY RUN
                BundleRepair bRepair =  createBundleRepair(bundle.getId(), bundleRepair.getViewId(), allRepairs);
                
                boolean dryRun = true;
                repairApi.repairBundle(bRepair, dryRun, callContext);
                */
            }
        }, ErrorCode.ENT_REPAIR_BP_RECREATE_MISSING_AO_CREATE);
    }

    @Test(groups = "fast", enabled = false)
    public void testENT_REPAIR_MISSING_AO_DELETE_EVENT() throws Exception {
        test.withException(new TestWithExceptionCallback() {
            @Override
            public void doTest() throws EntitlementRepairException, EntitlementUserApiException {

                /*
                // MOVE CLOCK -- JUST BEFORE END OF TRIAL
                 *                 
                Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(29));
                clock.addDeltaFromReality(it.toDurationMillis());

                clock.setDeltaFromReality(getDurationDay(29), 0);
                
                SubscriptionData aoSubscription = createSubscription("Laser-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);
                
                // MOVE CLOCK -- RIGHT OUT OF TRIAL
                testListener.pushExpectedEvent(NextEvent.PHASE);                
                clock.addDeltaFromReality(getDurationDay(5));
                assertTrue(testListener.isCompleted(5000));

                DateTime requestedChange = clock.getUTCNow();
                baseSubscription.changePlan("Assault-Rifle", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, requestedChange, callContext);

                DateTime reapairTime = clock.getUTCNow().minusDays(1);

                BundleRepair bundleRepair = repairApi.getBundleRepair(bundle.getId());
                sortEventsOnBundle(bundleRepair);
                
                SubscriptionRepair bpRepair = getSubscriptionRepair(baseSubscription.getId(), bundleRepair);
                SubscriptionRepair aoRepair = getSubscriptionRepair(aoSubscription.getId(), bundleRepair);

                List<DeletedEvent> bpdes = new LinkedList<SubscriptionRepair.DeletedEvent>();
                bpdes.add(createDeletedEvent(bpRepair.getExistingEvents().get(2).getEventId()));    
                bpRepair = createSubscriptionReapir(baseSubscription.getId(), bpdes, Collections.<NewEvent>emptyList());
                
                NewEvent ne = createNewEvent(SubscriptionTransitionType.CANCEL, reapairTime, null);
                aoRepair = createSubscriptionReapir(aoSubscription.getId(), Collections.<SubscriptionRepair.DeletedEvent>emptyList(), Collections.singletonList(ne));
                
                List<SubscriptionRepair> allRepairs = new LinkedList<SubscriptionRepair>();
                allRepairs.add(bpRepair);
                allRepairs.add(aoRepair);
                bundleRepair =  createBundleRepair(bundle.getId(), bundleRepair.getViewId(), allRepairs);
                
                boolean dryRun = false;
                repairApi.repairBundle(bundleRepair, dryRun, callContext);
                */
            }
        }, ErrorCode.ENT_REPAIR_MISSING_AO_DELETE_EVENT);
    }

}
