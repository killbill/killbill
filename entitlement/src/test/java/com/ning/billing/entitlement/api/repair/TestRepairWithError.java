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
package com.ning.billing.entitlement.api.repair;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Duration;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.entitlement.api.ApiTestListener.NextEvent;
import com.ning.billing.entitlement.api.repair.SubscriptionRepair.DeletedEvent;
import com.ning.billing.entitlement.api.repair.SubscriptionRepair.NewEvent;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.glue.MockEngineModuleMemory;

public class TestRepairWithError extends TestApiBaseRepair {

    private static final String baseProduct = "Shotgun";
    private TestWithException test;
    private Subscription baseSubscription;
    private DateTime startDate;
    @Override
    public Injector getInjector() {
        return Guice.createInjector(Stage.DEVELOPMENT, new MockEngineModuleMemory());
    }


    @BeforeMethod(groups={"fast"})
    public void beforeMethod() throws Exception {
        test = new TestWithException();
        startDate = clock.getUTCNow();
        baseSubscription = createSubscription(baseProduct, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, startDate);
        testListener.reset();
        clock.resetDeltaFromReality();
    }
  
    @Test(groups={"fast"})
    public void testENT_REPAIR_NEW_EVENT_BEFORE_LAST_BP_REMAINING() throws Exception {
        test.withException(new TestWithExceptionCallback() {
            @Override
            public void doTest() throws EntitlementRepairException {

                // MOVE AFTER TRIAL
                testListener.pushExpectedEvent(NextEvent.PHASE);
                Duration durationShift = getDurationDay(40);
                clock.setDeltaFromReality(durationShift, 0);
                assertTrue(testListener.isCompleted(5000));
                
                BundleRepair bundleRepair = repairApi.getBundleRepair(bundle.getId());
                sortEventsOnBundle(bundleRepair);
                PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN);
                NewEvent ne = createNewEvent(SubscriptionTransitionType.CHANGE, baseSubscription.getStartDate().plusDays(10), spec);
                
                SubscriptionRepair sRepair = createSubscriptionReapir(baseSubscription.getId(), Collections.<DeletedEvent>emptyList(), Collections.singletonList(ne));
                
                BundleRepair bRepair =  createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(sRepair));

                repairApi.repairBundle(bRepair, true, context);
            }
        }, ErrorCode.ENT_REPAIR_NEW_EVENT_BEFORE_LAST_BP_REMAINING);
    }
    
    @Test(groups={"fast"})
    public void testENT_REPAIR_INVALID_DELETE_SET() throws Exception {
        test.withException(new TestWithExceptionCallback() {
            @Override
            public void doTest() throws EntitlementRepairException, EntitlementUserApiException {

                Duration durationShift = getDurationDay(3);
                clock.setDeltaFromReality(durationShift, 0);
                
                testListener.pushExpectedEvent(NextEvent.CHANGE);
                DateTime changeTime = clock.getUTCNow();
                baseSubscription.changePlan("Assault-Rifle", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, changeTime, context);
                assertTrue(testListener.isCompleted(5000));
                
                // MOVE AFTER TRIAL
                testListener.pushExpectedEvent(NextEvent.PHASE);
                durationShift = getDurationDay(40);
                clock.addDeltaFromReality(durationShift);
                assertTrue(testListener.isCompleted(5000));
                
                BundleRepair bundleRepair = repairApi.getBundleRepair(bundle.getId());
                sortEventsOnBundle(bundleRepair);
                PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN);
                NewEvent ne = createNewEvent(SubscriptionTransitionType.CHANGE, baseSubscription.getStartDate().plusDays(10), spec);
                DeletedEvent de = createDeletedEvent(bundleRepair.getSubscriptions().get(0).getExistingEvents().get(1).getEventId());                

                SubscriptionRepair sRepair = createSubscriptionReapir(baseSubscription.getId(), Collections.singletonList(de), Collections.singletonList(ne));
                BundleRepair bRepair =  createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(sRepair));

                repairApi.repairBundle(bRepair, true, context);
            }
        }, ErrorCode.ENT_REPAIR_INVALID_DELETE_SET);
    }

    @Test(groups={"fast"})
    public void testENT_REPAIR_NON_EXISTENT_DELETE_EVENT() throws Exception {
        test.withException(new TestWithExceptionCallback() {
            @Override
            public void doTest() throws EntitlementRepairException {
                
                BundleRepair bundleRepair = repairApi.getBundleRepair(bundle.getId());
                sortEventsOnBundle(bundleRepair);
                PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN);
                NewEvent ne = createNewEvent(SubscriptionTransitionType.CHANGE, baseSubscription.getStartDate().plusDays(10), spec);
                DeletedEvent de = createDeletedEvent(UUID.randomUUID());
                SubscriptionRepair sRepair = createSubscriptionReapir(baseSubscription.getId(), Collections.singletonList(de), Collections.singletonList(ne));
                
                BundleRepair bRepair =  createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(sRepair));

                repairApi.repairBundle(bRepair, true, context);
            }
        }, ErrorCode.ENT_REPAIR_NON_EXISTENT_DELETE_EVENT);
    }
    
    @Test(groups={"fast"})
    public void testENT_REPAIR_SUB_RECREATE_NOT_EMPTY() throws Exception {
        test.withException(new TestWithExceptionCallback() {
            @Override
            public void doTest() throws EntitlementRepairException {
                
                // MOVE AFTER TRIAL
                   testListener.pushExpectedEvent(NextEvent.PHASE);
                   Duration durationShift = getDurationDay(40);
                   clock.setDeltaFromReality(durationShift, 0);
                   assertTrue(testListener.isCompleted(5000));
                   
                   BundleRepair bundleRepair = repairApi.getBundleRepair(bundle.getId());
                   sortEventsOnBundle(bundleRepair);
                   PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN);
                   NewEvent ne = createNewEvent(SubscriptionTransitionType.CREATE, baseSubscription.getStartDate().plusDays(10), spec);
                   List<DeletedEvent> des = new LinkedList<SubscriptionRepair.DeletedEvent>();
                   des.add(createDeletedEvent(bundleRepair.getSubscriptions().get(0).getExistingEvents().get(1).getEventId()));                
                   SubscriptionRepair sRepair = createSubscriptionReapir(baseSubscription.getId(), des, Collections.singletonList(ne));
                   
                   BundleRepair bRepair =  createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(sRepair));

                   repairApi.repairBundle(bRepair, true, context);
                
            }
        }, ErrorCode.ENT_REPAIR_SUB_RECREATE_NOT_EMPTY);
    }

    @Test(groups={"fast"})
    public void testENT_REPAIR_SUB_EMPTY() throws Exception {
        test.withException(new TestWithExceptionCallback() {
            @Override
            public void doTest() throws EntitlementRepairException {
                
             // MOVE AFTER TRIAL
                testListener.pushExpectedEvent(NextEvent.PHASE);
                Duration durationShift = getDurationDay(40);
                clock.setDeltaFromReality(durationShift, 0);
                assertTrue(testListener.isCompleted(5000));
                
                BundleRepair bundleRepair = repairApi.getBundleRepair(bundle.getId());
                sortEventsOnBundle(bundleRepair);
                PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN);
                NewEvent ne = createNewEvent(SubscriptionTransitionType.CHANGE, baseSubscription.getStartDate().plusDays(10), spec);
                List<DeletedEvent> des = new LinkedList<SubscriptionRepair.DeletedEvent>();
                des.add(createDeletedEvent(bundleRepair.getSubscriptions().get(0).getExistingEvents().get(0).getEventId()));                
                des.add(createDeletedEvent(bundleRepair.getSubscriptions().get(0).getExistingEvents().get(1).getEventId()));                                
                SubscriptionRepair sRepair = createSubscriptionReapir(baseSubscription.getId(), des, Collections.singletonList(ne));
                
                BundleRepair bRepair =  createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(sRepair));

                repairApi.repairBundle(bRepair, true, context);
            }
        }, ErrorCode.ENT_REPAIR_SUB_EMPTY);
    }
    
    @Test(groups={"fast"}, enabled=false)
    public void testENT_REPAIR_NEW_EVENT_BEFORE_LAST_AO_REMAINING() throws Exception {
        test.withException(new TestWithExceptionCallback() {
            @Override
            public void doTest() throws EntitlementRepairException {
                
            }
        }, ErrorCode.ENT_REPAIR_NEW_EVENT_BEFORE_LAST_AO_REMAINING);
    }

    @Test(groups={"fast"}, enabled=false)
    public void testENT_REPAIR_MISSING_AO_DELETE_EVENT() throws Exception {
        test.withException(new TestWithExceptionCallback() {
            @Override
            public void doTest() throws EntitlementRepairException {
                
            }
        }, ErrorCode.ENT_REPAIR_MISSING_AO_DELETE_EVENT);
    }

  
    @Test(groups={"fast"}, enabled=false)
    public void testENT_REPAIR_BP_RECREATE_MISSING_AO() throws Exception {
        test.withException(new TestWithExceptionCallback() {
            @Override
            public void doTest() throws EntitlementRepairException {
                
            }
        }, ErrorCode.ENT_REPAIR_BP_RECREATE_MISSING_AO);
    }
    
    @Test(groups={"fast"}, enabled=false)
    public void testENT_REPAIR_BP_RECREATE_MISSING_AO_CREATE() throws Exception {
        test.withException(new TestWithExceptionCallback() {
            @Override
            public void doTest() throws EntitlementRepairException {
                
            }
        }, ErrorCode.ENT_REPAIR_BP_RECREATE_MISSING_AO_CREATE);
    }
}
