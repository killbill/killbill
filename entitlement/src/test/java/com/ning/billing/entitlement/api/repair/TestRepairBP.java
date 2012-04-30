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
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Duration;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.entitlement.api.ApiTestListener.NextEvent;
import com.ning.billing.entitlement.api.repair.SubscriptionRepair.DeletedEvent;
import com.ning.billing.entitlement.api.repair.SubscriptionRepair.ExistingEvent;
import com.ning.billing.entitlement.api.repair.SubscriptionRepair.NewEvent;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.api.user.SubscriptionEvents;
import com.ning.billing.entitlement.glue.MockEngineModuleSql;

public class TestRepairBP extends TestApiBaseRepair {

    @Override
    public Injector getInjector() {
        return Guice.createInjector(Stage.DEVELOPMENT, new MockEngineModuleSql());
    }

    @Test(groups={"slow"})
    public void testFetchBundleRepair() throws Exception  {

        String baseProduct = "Shotgun";
        BillingPeriod baseTerm = BillingPeriod.MONTHLY;
        String basePriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        // CREATE BP
        Subscription baseSubscription = createSubscription(baseProduct, baseTerm, basePriceList);

        String aoProduct = "Telescopic-Scope";
        BillingPeriod aoTerm = BillingPeriod.MONTHLY;
        String aoPriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        SubscriptionData aoSubscription = createSubscription(aoProduct, aoTerm, aoPriceList);

        BundleRepair bundleRepair = repairApi.getBundleRepair(bundle.getId());
        List<SubscriptionRepair> subscriptionRepair = bundleRepair.getSubscriptions();
        assertEquals(subscriptionRepair.size(), 2);

        for (SubscriptionRepair cur : subscriptionRepair) {
            assertNull(cur.getDeletedEvents());
            assertNull(cur.getNewEvents());                

            List<ExistingEvent> events = cur.getExistingEvents();
            assertEquals(events.size(), 2);
            sortExistingEvent(events);

            assertEquals(events.get(0).getSubscriptionTransitionType(), SubscriptionTransitionType.CREATE);
            assertEquals(events.get(1).getSubscriptionTransitionType(), SubscriptionTransitionType.PHASE);                    
            final boolean isBP = cur.getId().equals(baseSubscription.getId());
            if (isBP) {
                assertEquals(cur.getId(), baseSubscription.getId());

                assertEquals(events.get(0).getPlanPhaseSpecifier().getProductName(), baseProduct);
                assertEquals(events.get(0).getPlanPhaseSpecifier().getPhaseType(), PhaseType.TRIAL);
                assertEquals(events.get(0).getPlanPhaseSpecifier().getProductCategory(),ProductCategory.BASE);                    
                assertEquals(events.get(0).getPlanPhaseSpecifier().getPriceListName(), basePriceList);                    
                assertEquals(events.get(0).getPlanPhaseSpecifier().getBillingPeriod(), BillingPeriod.NO_BILLING_PERIOD);

                assertEquals(events.get(1).getPlanPhaseSpecifier().getProductName(), baseProduct);
                assertEquals(events.get(1).getPlanPhaseSpecifier().getPhaseType(), PhaseType.EVERGREEN);
                assertEquals(events.get(1).getPlanPhaseSpecifier().getProductCategory(),ProductCategory.BASE);                    
                assertEquals(events.get(1).getPlanPhaseSpecifier().getPriceListName(), basePriceList);                    
                assertEquals(events.get(1).getPlanPhaseSpecifier().getBillingPeriod(), baseTerm);
            } else {
                assertEquals(cur.getId(), aoSubscription.getId());

                assertEquals(events.get(0).getPlanPhaseSpecifier().getProductName(), aoProduct);
                assertEquals(events.get(0).getPlanPhaseSpecifier().getPhaseType(), PhaseType.DISCOUNT);                    
                assertEquals(events.get(0).getPlanPhaseSpecifier().getProductCategory(),ProductCategory.ADD_ON); 
                assertEquals(events.get(0).getPlanPhaseSpecifier().getPriceListName(), aoPriceList); 
                assertEquals(events.get(1).getPlanPhaseSpecifier().getBillingPeriod(), aoTerm);                    

                assertEquals(events.get(1).getPlanPhaseSpecifier().getProductName(), aoProduct);
                assertEquals(events.get(1).getPlanPhaseSpecifier().getPhaseType(), PhaseType.EVERGREEN);                    
                assertEquals(events.get(1).getPlanPhaseSpecifier().getProductCategory(),ProductCategory.ADD_ON); 
                assertEquals(events.get(1).getPlanPhaseSpecifier().getPriceListName(), aoPriceList);  
                assertEquals(events.get(1).getPlanPhaseSpecifier().getBillingPeriod(), aoTerm);                    
            }
        }
    }
    
    @Test(groups={"slow"})
    public void testBPRepairWithCancellationOnstart() throws Exception {

        String baseProduct = "Shotgun";
        DateTime startDate = clock.getUTCNow();
        
        // CREATE BP
        Subscription baseSubscription = createSubscription(baseProduct, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, startDate);

        // Stays in trial-- for instance
        Duration durationShift = getDurationDay(10);
        clock.setDeltaFromReality(durationShift, 0);

        BundleRepair bundleRepair = repairApi.getBundleRepair(bundle.getId());
        sortEventsOnBundle(bundleRepair);

        List<DeletedEvent> des = new LinkedList<SubscriptionRepair.DeletedEvent>();
        des.add(createDeletedEvent(bundleRepair.getSubscriptions().get(0).getExistingEvents().get(1).getEventId()));
        NewEvent ne = createNewEvent(SubscriptionTransitionType.CANCEL, baseSubscription.getStartDate(), null);
        
        
        SubscriptionRepair sRepair = createSubscriptionReapir(baseSubscription.getId(), des, Collections.singletonList(ne));
        
        // FIRST ISSUE DRY RUN
        BundleRepair bRepair =  createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(sRepair));
        
        boolean dryRun = true;
        BundleRepair dryRunBundleRepair = repairApi.repairBundle(bRepair, dryRun, context);
        sortEventsOnBundle(dryRunBundleRepair);
        List<SubscriptionRepair> subscriptionRepair = dryRunBundleRepair.getSubscriptions();
        assertEquals(subscriptionRepair.size(), 1);
        SubscriptionRepair cur = subscriptionRepair.get(0);
        int index = 0;
        List<ExistingEvent> events = subscriptionRepair.get(0).getExistingEvents();
        assertEquals(events.size(), 2);
        List<ExistingEvent> expected = new LinkedList<SubscriptionRepair.ExistingEvent>();
        expected.add(createExistingEventForAssertion(SubscriptionTransitionType.CREATE, baseProduct, PhaseType.TRIAL,
                ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.NO_BILLING_PERIOD, baseSubscription.getStartDate()));
        expected.add(createExistingEventForAssertion(SubscriptionTransitionType.CANCEL, baseProduct, PhaseType.TRIAL,
                ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.NO_BILLING_PERIOD,baseSubscription.getStartDate()));

        for (ExistingEvent e : expected) {
           validateExistingEventForAssertion(e, events.get(index++));           
        }
        
        SubscriptionData dryRunBaseSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(baseSubscription.getId());
        
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
        assertEquals(currentPhase.getPhaseType(), PhaseType.TRIAL);
        
       // SECOND RE-ISSUE CALL-- NON DRY RUN
        dryRun = false;
        testListener.expectRepairCompletion();
        BundleRepair realRunBundleRepair = repairApi.repairBundle(bRepair, dryRun, context);
        assertTrue(testListener.isRepairCompleted(5000));
        
        subscriptionRepair = realRunBundleRepair.getSubscriptions();
        assertEquals(subscriptionRepair.size(), 1);
        cur = subscriptionRepair.get(0);
        assertEquals(cur.getId(), baseSubscription.getId());
        index = 0;
        for (ExistingEvent e : expected) {
           validateExistingEventForAssertion(e, events.get(index++));           
        }
        SubscriptionData realRunBaseSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(baseSubscription.getId());
        assertEquals(realRunBaseSubscription.getAllTransitions().size(), 2);
        
        
        assertEquals(realRunBaseSubscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION + 1);
        assertEquals(realRunBaseSubscription.getBundleId(), bundle.getId());
        assertEquals(realRunBaseSubscription.getStartDate(), startDate);

        assertEquals(realRunBaseSubscription.getState(), SubscriptionState.CANCELLED);
    }
    
    @Test(groups={"slow"})
    public void testBPRepairReplaceCreateBeforeTrial() throws Exception {
        String baseProduct = "Shotgun";
        String newBaseProduct = "Assault-Rifle";
        
        DateTime startDate = clock.getUTCNow();
        int clockShift = -1;
        DateTime restartDate =  startDate.plusDays(clockShift).minusDays(1);
        LinkedList<ExistingEvent> expected = new LinkedList<SubscriptionRepair.ExistingEvent>();
        
        expected.add(createExistingEventForAssertion(SubscriptionTransitionType.CREATE, newBaseProduct, PhaseType.TRIAL,
                ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.NO_BILLING_PERIOD, restartDate));
        expected.add(createExistingEventForAssertion(SubscriptionTransitionType.PHASE, newBaseProduct, PhaseType.EVERGREEN,
                    ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, restartDate.plusDays(30)));

        testBPRepairCreate(true, startDate, clockShift, baseProduct, newBaseProduct, expected);
    }

    @Test(groups={"slow"}, enabled=true)
    public void testBPRepairReplaceCreateInTrial() throws Exception {
        String baseProduct = "Shotgun";
        String newBaseProduct = "Assault-Rifle";
        
        DateTime startDate = clock.getUTCNow();
        int clockShift = 10;
        DateTime restartDate =  startDate.plusDays(clockShift).minusDays(1);
        LinkedList<ExistingEvent> expected = new LinkedList<SubscriptionRepair.ExistingEvent>();
        
        expected.add(createExistingEventForAssertion(SubscriptionTransitionType.CREATE, newBaseProduct, PhaseType.TRIAL,
                ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.NO_BILLING_PERIOD, restartDate));
        expected.add(createExistingEventForAssertion(SubscriptionTransitionType.PHASE, newBaseProduct, PhaseType.EVERGREEN,
                    ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, restartDate.plusDays(30)));

        UUID baseSubscriptionId = testBPRepairCreate(true, startDate, clockShift, baseProduct, newBaseProduct, expected);
        
        testListener.pushNextApiExpectedEvent(NextEvent.PHASE);
        clock.addDeltaFromReality(getDurationDay(32));
        assertTrue(testListener.isApiCompleted(5000));
        
        // CHECK WHAT"S GOING ON AFTER WE MOVE CLOCK-- FUTURE MOTIFICATION SHOULD KICK IN
        SubscriptionData subscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(baseSubscriptionId);
        
        assertEquals(subscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION + 1);
        assertEquals(subscription.getBundleId(), bundle.getId());
        assertEquals(subscription.getStartDate(), restartDate);
        assertEquals(subscription.getBundleStartDate(), restartDate);        

        Plan currentPlan = subscription.getCurrentPlan();
        assertNotNull(currentPlan);
        assertEquals(currentPlan.getProduct().getName(), newBaseProduct);
        assertEquals(currentPlan.getProduct().getCategory(), ProductCategory.BASE);
        assertEquals(currentPlan.getBillingPeriod(), BillingPeriod.MONTHLY);

        PlanPhase currentPhase = subscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.EVERGREEN);
    }

    
    @Test(groups={"slow"})
    public void testBPRepairReplaceCreateAfterTrial() throws Exception {
        String baseProduct = "Shotgun";
        String newBaseProduct = "Assault-Rifle";
        
        DateTime startDate = clock.getUTCNow();
        int clockShift = 40;
        DateTime restartDate =  startDate.plusDays(clockShift).minusDays(1);
        LinkedList<ExistingEvent> expected = new LinkedList<SubscriptionRepair.ExistingEvent>();
        
        expected.add(createExistingEventForAssertion(SubscriptionTransitionType.CREATE, newBaseProduct, PhaseType.TRIAL,
                ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.NO_BILLING_PERIOD, restartDate));
        expected.add(createExistingEventForAssertion(SubscriptionTransitionType.PHASE, newBaseProduct, PhaseType.EVERGREEN,
                    ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, restartDate.plusDays(30)));

        testBPRepairCreate(false, startDate, clockShift, baseProduct, newBaseProduct, expected);
        
    }
    
    
    private UUID testBPRepairCreate(boolean inTrial, DateTime startDate, int clockShift, 
            String baseProduct, String newBaseProduct, List<ExistingEvent> expectedEvents) throws Exception {

        // CREATE BP
        Subscription baseSubscription = createSubscription(baseProduct, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, startDate);

        // MOVE CLOCK
        if (clockShift > 0) {
            if (!inTrial) {
                testListener.pushNextApiExpectedEvent(NextEvent.PHASE);
            }               
            Duration durationShift = getDurationDay(clockShift);
            clock.setDeltaFromReality(durationShift, 0);
            if (!inTrial) {
                assertTrue(testListener.isApiCompleted(5000));
            }
        }

        BundleRepair bundleRepair = repairApi.getBundleRepair(bundle.getId());
        sortEventsOnBundle(bundleRepair);
        
        DateTime newCreateTime = baseSubscription.getStartDate().plusDays(clockShift - 1);

        PlanPhaseSpecifier spec = new PlanPhaseSpecifier(newBaseProduct, ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.TRIAL);

        NewEvent ne = createNewEvent(SubscriptionTransitionType.CREATE, newCreateTime, spec);
        List<DeletedEvent> des = new LinkedList<SubscriptionRepair.DeletedEvent>();
        des.add(createDeletedEvent(bundleRepair.getSubscriptions().get(0).getExistingEvents().get(0).getEventId()));
        des.add(createDeletedEvent(bundleRepair.getSubscriptions().get(0).getExistingEvents().get(1).getEventId()));

        SubscriptionRepair sRepair = createSubscriptionReapir(baseSubscription.getId(), des, Collections.singletonList(ne));
        
        // FIRST ISSUE DRY RUN
        BundleRepair bRepair =  createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(sRepair));
        
        boolean dryRun = true;
        BundleRepair dryRunBundleRepair = repairApi.repairBundle(bRepair, dryRun, context);
        List<SubscriptionRepair> subscriptionRepair = dryRunBundleRepair.getSubscriptions();
        assertEquals(subscriptionRepair.size(), 1);
        SubscriptionRepair cur = subscriptionRepair.get(0);
        assertEquals(cur.getId(), baseSubscription.getId());

        List<ExistingEvent> events = cur.getExistingEvents();
        assertEquals(expectedEvents.size(), events.size());
        int index = 0;
        for (ExistingEvent e : expectedEvents) {
           validateExistingEventForAssertion(e, events.get(index++));           
        }
        SubscriptionData dryRunBaseSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(baseSubscription.getId());
        
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
        BundleRepair realRunBundleRepair = repairApi.repairBundle(bRepair, dryRun, context);

        subscriptionRepair = realRunBundleRepair.getSubscriptions();
        assertEquals(subscriptionRepair.size(), 1);
        cur = subscriptionRepair.get(0);
        assertEquals(cur.getId(), baseSubscription.getId());

        events = cur.getExistingEvents();
        for (ExistingEvent e : events) {
            log.info(String.format("%s, %s, %s, %s", e.getSubscriptionTransitionType(), e.getEffectiveDate(), e.getPlanPhaseSpecifier().getProductName(),  e.getPlanPhaseSpecifier().getPhaseType()));
        }
        assertEquals(events.size(), expectedEvents.size());
        index = 0;
        for (ExistingEvent e : expectedEvents) {
           validateExistingEventForAssertion(e, events.get(index++));           
        }
        SubscriptionData realRunBaseSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(baseSubscription.getId());
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

    @Test(groups={"slow"})
    public void testBPRepairAddChangeInTrial() throws Exception {
        
        String baseProduct = "Shotgun";
        String newBaseProduct = "Assault-Rifle";
        
        DateTime startDate = clock.getUTCNow();
        int clockShift = 10;
        DateTime changeDate =  startDate.plusDays(clockShift).minusDays(1);
        LinkedList<ExistingEvent> expected = new LinkedList<SubscriptionRepair.ExistingEvent>();
        
        expected.add(createExistingEventForAssertion(SubscriptionTransitionType.CREATE, baseProduct, PhaseType.TRIAL,
                ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.NO_BILLING_PERIOD, startDate));
        expected.add(createExistingEventForAssertion(SubscriptionTransitionType.CHANGE, newBaseProduct, PhaseType.TRIAL,
                    ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.NO_BILLING_PERIOD, changeDate));
        expected.add(createExistingEventForAssertion(SubscriptionTransitionType.PHASE, newBaseProduct, PhaseType.EVERGREEN,
                    ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, startDate.plusDays(30)));

        UUID baseSubscriptionId = testBPRepairAddChange(true, startDate, clockShift, baseProduct, newBaseProduct, expected, 3);
        
        // CHECK WHAT"S GOING ON AFTER WE MOVE CLOCK-- FUTURE MOTIFICATION SHOULD KICK IN
        testListener.pushNextApiExpectedEvent(NextEvent.PHASE);
        clock.addDeltaFromReality(getDurationDay(32));
        assertTrue(testListener.isApiCompleted(5000));
        SubscriptionData subscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(baseSubscriptionId);
        
        assertEquals(subscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION + 1);
        assertEquals(subscription.getBundleId(), bundle.getId());
        assertEquals(subscription.getStartDate(), startDate);
        assertEquals(subscription.getBundleStartDate(), startDate);        

        Plan currentPlan = subscription.getCurrentPlan();
        assertNotNull(currentPlan);
        assertEquals(currentPlan.getProduct().getName(), newBaseProduct);
        assertEquals(currentPlan.getProduct().getCategory(), ProductCategory.BASE);
        assertEquals(currentPlan.getBillingPeriod(), BillingPeriod.MONTHLY);

        PlanPhase currentPhase = subscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.EVERGREEN);
    }

    @Test(groups={"slow"})
    public void testBPRepairAddChangeAfterTrial() throws Exception {
        
        String baseProduct = "Shotgun";
        String newBaseProduct = "Assault-Rifle";
        
        DateTime startDate = clock.getUTCNow();
        int clockShift = 40;
        DateTime changeDate =  startDate.plusDays(clockShift).minusDays(1);
        
        LinkedList<ExistingEvent> expected = new LinkedList<SubscriptionRepair.ExistingEvent>();
        expected.add(createExistingEventForAssertion(SubscriptionTransitionType.CREATE, baseProduct, PhaseType.TRIAL,
                ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.NO_BILLING_PERIOD, startDate));
        expected.add(createExistingEventForAssertion(SubscriptionTransitionType.PHASE, baseProduct, PhaseType.EVERGREEN,
                ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, startDate.plusDays(30)));
        expected.add(createExistingEventForAssertion(SubscriptionTransitionType.CHANGE, newBaseProduct, PhaseType.EVERGREEN,
                ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, changeDate));
        testBPRepairAddChange(false, startDate, clockShift, baseProduct, newBaseProduct, expected, 3);
    }
    

    private UUID testBPRepairAddChange(boolean inTrial, DateTime startDate, int clockShift, 
            String baseProduct, String newBaseProduct, List<ExistingEvent> expectedEvents, int expectedTransitions) throws Exception {

        // CREATE BP
        Subscription baseSubscription = createSubscription(baseProduct, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, startDate);

        // MOVE CLOCK
        if (!inTrial) {
            testListener.pushNextApiExpectedEvent(NextEvent.PHASE);
        }               

        Duration durationShift = getDurationDay(clockShift);
        clock.setDeltaFromReality(durationShift, 0);
        if (!inTrial) {
            assertTrue(testListener.isApiCompleted(5000));
        }

        BundleRepair bundleRepair = repairApi.getBundleRepair(bundle.getId());
        sortEventsOnBundle(bundleRepair);
        
        DateTime changeTime = baseSubscription.getStartDate().plusDays(clockShift - 1);

        PlanPhaseSpecifier spec = new PlanPhaseSpecifier(newBaseProduct, ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.TRIAL);

        NewEvent ne = createNewEvent(SubscriptionTransitionType.CHANGE, changeTime, spec);
        List<DeletedEvent> des = new LinkedList<SubscriptionRepair.DeletedEvent>();
        if (inTrial) {
            des.add(createDeletedEvent(bundleRepair.getSubscriptions().get(0).getExistingEvents().get(1).getEventId()));
        }
        SubscriptionRepair sRepair = createSubscriptionReapir(baseSubscription.getId(), des, Collections.singletonList(ne));
        
        // FIRST ISSUE DRY RUN
        BundleRepair bRepair =  createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(sRepair));
        
        boolean dryRun = true;
        BundleRepair dryRunBundleRepair = repairApi.repairBundle(bRepair, dryRun, context);
        List<SubscriptionRepair> subscriptionRepair = dryRunBundleRepair.getSubscriptions();
        assertEquals(subscriptionRepair.size(), 1);
        SubscriptionRepair cur = subscriptionRepair.get(0);
        assertEquals(cur.getId(), baseSubscription.getId());

        List<ExistingEvent> events = cur.getExistingEvents();
       assertEquals(expectedEvents.size(), events.size());
       int index = 0;
       for (ExistingEvent e : expectedEvents) {
           validateExistingEventForAssertion(e, events.get(index++));           
       }
        SubscriptionData dryRunBaseSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(baseSubscription.getId());
        
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
        BundleRepair realRunBundleRepair = repairApi.repairBundle(bRepair, dryRun, context);

        subscriptionRepair = realRunBundleRepair.getSubscriptions();
        assertEquals(subscriptionRepair.size(), 1);
        cur = subscriptionRepair.get(0);
        assertEquals(cur.getId(), baseSubscription.getId());

        events = cur.getExistingEvents();
        assertEquals(expectedEvents.size(), events.size());
        index = 0;
        for (ExistingEvent e : expectedEvents) {
           validateExistingEventForAssertion(e, events.get(index++));           
        }
        SubscriptionData realRunBaseSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(baseSubscription.getId());
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
    
    @Test(groups={"slow"})
    public void testRepairWithFurureCancelEvent() throws Exception {
      
        DateTime startDate = clock.getUTCNow();
        
        // CREATE BP
        Subscription baseSubscription = createSubscription("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, startDate);

        // MOVE CLOCK -- OUT OF TRIAL
        testListener.pushNextApiExpectedEvent(NextEvent.PHASE);                
        clock.setDeltaFromReality(getDurationDay(35), 0);
        assertTrue(testListener.isApiCompleted(5000));
        
        // SET CTD to BASE SUBSCRIPTION SP CANCEL OCCURS EOT
        DateTime newChargedThroughDate = baseSubscription.getStartDate().plusDays(30).plusMonths(1);
        billingApi.setChargedThroughDate(baseSubscription.getId(), newChargedThroughDate, context);
        baseSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(baseSubscription.getId());

        
        DateTime requestedChange = clock.getUTCNow();
        baseSubscription.changePlan("Pistol", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, requestedChange, context);
        
        
        // CHECK CHANGE DID NOT OCCUR YET
        Plan currentPlan = baseSubscription.getCurrentPlan();
        assertNotNull(currentPlan);
        assertEquals(currentPlan.getProduct().getName(), "Shotgun");
        assertEquals(currentPlan.getProduct().getCategory(), ProductCategory.BASE);
        assertEquals(currentPlan.getBillingPeriod(), BillingPeriod.MONTHLY);
        
        
        DateTime repairTime = clock.getUTCNow().minusDays(1);
        BundleRepair bundleRepair = repairApi.getBundleRepair(bundle.getId());
        sortEventsOnBundle(bundleRepair);
        
        PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN);

        NewEvent ne = createNewEvent(SubscriptionTransitionType.CHANGE, repairTime, spec);
        List<DeletedEvent> des = new LinkedList<SubscriptionRepair.DeletedEvent>();
        des.add(createDeletedEvent(bundleRepair.getSubscriptions().get(0).getExistingEvents().get(2).getEventId()));

        SubscriptionRepair sRepair = createSubscriptionReapir(baseSubscription.getId(), des, Collections.singletonList(ne));
        
        // SKIP DRY RUN AND DO REPAIR...
        BundleRepair bRepair =  createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(sRepair));
        
        boolean dryRun = false;
        repairApi.repairBundle(bRepair, dryRun, context);
     
        baseSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(baseSubscription.getId());
        
        assertEquals(((SubscriptionData) baseSubscription).getActiveVersion(), SubscriptionEvents.INITIAL_VERSION + 1);
        assertEquals(baseSubscription.getBundleId(), bundle.getId());
        assertEquals(baseSubscription.getStartDate(), baseSubscription.getStartDate());

        currentPlan = baseSubscription.getCurrentPlan();
        assertNotNull(currentPlan);
        assertEquals(currentPlan.getProduct().getName(), "Assault-Rifle");
        assertEquals(currentPlan.getProduct().getCategory(), ProductCategory.BASE);
        assertEquals(currentPlan.getBillingPeriod(), BillingPeriod.MONTHLY);

        PlanPhase currentPhase = baseSubscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.EVERGREEN);
    }
    
    
    // Needs real SQL backend to be tested properly
    @Test(groups={"slow"})
    public void testENT_REPAIR_VIEW_CHANGED_newEvent() throws Exception {
       
        TestWithException test = new TestWithException();
        DateTime startDate = clock.getUTCNow();
        
        testListener.reset();
        clock.resetDeltaFromReality();

        final Subscription baseSubscription = createSubscription("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, startDate);
        
        test.withException(new TestWithExceptionCallback() {
            @Override
            public void doTest() throws EntitlementRepairException, EntitlementUserApiException {

                BundleRepair bundleRepair = repairApi.getBundleRepair(bundle.getId());
                sortEventsOnBundle(bundleRepair);
                PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN);
                NewEvent ne = createNewEvent(SubscriptionTransitionType.CHANGE, baseSubscription.getStartDate().plusDays(10), spec);
                List<DeletedEvent> des = new LinkedList<SubscriptionRepair.DeletedEvent>();
                des.add(createDeletedEvent(bundleRepair.getSubscriptions().get(0).getExistingEvents().get(0).getEventId()));                
                des.add(createDeletedEvent(bundleRepair.getSubscriptions().get(0).getExistingEvents().get(1).getEventId()));                                
                SubscriptionRepair sRepair = createSubscriptionReapir(baseSubscription.getId(), des, Collections.singletonList(ne));

                BundleRepair bRepair =  createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(sRepair));

                testListener.pushNextApiExpectedEvent(NextEvent.CHANGE);
                DateTime changeTime = clock.getUTCNow();
                baseSubscription.changePlan("Assault-Rifle", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, changeTime, context);
                assertTrue(testListener.isApiCompleted(5000));
                                
                repairApi.repairBundle(bRepair, true, context);
            }
        }, ErrorCode.ENT_REPAIR_VIEW_CHANGED);
    }

    @Test(groups={"slow"}, enabled=false)
    public void testENT_REPAIR_VIEW_CHANGED_ctd() throws Exception {
       
        TestWithException test = new TestWithException();
        DateTime startDate = clock.getUTCNow();
        
        testListener.reset();
        clock.resetDeltaFromReality();

        final Subscription baseSubscription = createSubscription("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, startDate);
        
        test.withException(new TestWithExceptionCallback() {
            @Override
            public void doTest() throws EntitlementRepairException, EntitlementUserApiException {

                BundleRepair bundleRepair = repairApi.getBundleRepair(bundle.getId());
                sortEventsOnBundle(bundleRepair);
                PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN);
                NewEvent ne = createNewEvent(SubscriptionTransitionType.CHANGE, baseSubscription.getStartDate().plusDays(10), spec);
                List<DeletedEvent> des = new LinkedList<SubscriptionRepair.DeletedEvent>();
                des.add(createDeletedEvent(bundleRepair.getSubscriptions().get(0).getExistingEvents().get(0).getEventId()));                
                des.add(createDeletedEvent(bundleRepair.getSubscriptions().get(0).getExistingEvents().get(1).getEventId()));                                
                SubscriptionRepair sRepair = createSubscriptionReapir(baseSubscription.getId(), des, Collections.singletonList(ne));

                BundleRepair bRepair =  createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(sRepair));

                DateTime newChargedThroughDate = baseSubscription.getStartDate().plusDays(30).plusMonths(1);
                billingApi.setChargedThroughDate(baseSubscription.getId(), newChargedThroughDate, context);
                entitlementApi.getSubscriptionFromId(baseSubscription.getId());

                repairApi.repairBundle(bRepair, true, context);
            }
        }, ErrorCode.ENT_REPAIR_VIEW_CHANGED);
    }

}
