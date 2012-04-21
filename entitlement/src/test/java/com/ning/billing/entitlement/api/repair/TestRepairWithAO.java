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

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
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
import com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.api.user.SubscriptionEvents;
import com.ning.billing.entitlement.glue.MockEngineModuleSql;

public class TestRepairWithAO extends TestApiBaseRepair {

    @Override
    public Injector getInjector() {
        return Guice.createInjector(Stage.DEVELOPMENT, new MockEngineModuleSql());
    }

    @Test(groups={"slow"})
    public void testRepairChangeBPWithAddonIncluded() throws Exception {
        
    }

    @Test(groups={"slow"})
    public void testRepairChangeBPWithAddonNonAvailable() throws Exception {
        
    }

    @Test(groups={"slow"})
    public void testRepairCancelBPWithAddons() throws Exception {
        
    }

    
    
    @Test(groups={"slow"})
    public void testRepairCancelAO() throws Exception {
        String baseProduct = "Shotgun";
        BillingPeriod baseTerm = BillingPeriod.MONTHLY;
        String basePriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        // CREATE BP
        SubscriptionData baseSubscription = createSubscription(baseProduct, baseTerm, basePriceList);

        // MOVE CLOCK A LITTLE BIT-- STILL IN TRIAL
        Duration someTimeLater = getDurationDay(3);
        clock.setDeltaFromReality(someTimeLater, DAY_IN_MS);

        SubscriptionData aoSubscription = createSubscription("Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);

        // MOVE CLOCK A LITTLE BIT MORE -- STILL IN TRIAL
        clock.addDeltaFromReality(someTimeLater);

        BundleRepair bundleRepair = repairApi.getBundleRepair(bundle.getId());
        sortEventsOnBundle(bundleRepair);
        
        // Quick check
        SubscriptionRepair bpRepair = getSubscriptionRepair(baseSubscription.getId(), bundleRepair);
        assertEquals(bpRepair.getExistingEvents().size(), 2);
        
        SubscriptionRepair aoRepair = getSubscriptionRepair(aoSubscription.getId(), bundleRepair);
        assertEquals(aoRepair.getExistingEvents().size(), 2);
        

        List<DeletedEvent> des = new LinkedList<SubscriptionRepair.DeletedEvent>();
        des.add(createDeletedEvent(aoRepair.getExistingEvents().get(1).getEventId()));        
        DateTime aoCancelDate = aoSubscription.getStartDate().plusDays(1);
        
        NewEvent ne = createNewEvent(SubscriptionTransitionType.CANCEL, aoCancelDate, null);
        
        SubscriptionRepair saoRepair = createSubscriptionReapir(aoSubscription.getId(), des, Collections.singletonList(ne));
        
        BundleRepair bRepair =  createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(saoRepair));
        
        boolean dryRun = true;
        BundleRepair dryRunBundleRepair = repairApi.repairBundle(bRepair, dryRun, context);
        
        aoRepair = getSubscriptionRepair(aoSubscription.getId(), dryRunBundleRepair);
        assertEquals(aoRepair.getExistingEvents().size(), 2);
        
        bpRepair = getSubscriptionRepair(baseSubscription.getId(), bundleRepair);
        assertEquals(bpRepair.getExistingEvents().size(), 2);        
        
        List<ExistingEvent> expected = new LinkedList<SubscriptionRepair.ExistingEvent>();
        expected.add(createExistingEventForAssertion(SubscriptionTransitionType.CREATE, "Telescopic-Scope", PhaseType.DISCOUNT,
                ProductCategory.ADD_ON, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, aoSubscription.getStartDate()));
        expected.add(createExistingEventForAssertion(SubscriptionTransitionType.CANCEL, null, null,
                    ProductCategory.ADD_ON, null, null, aoCancelDate));
        int index = 0;
        for (ExistingEvent e : expected) {
           validateExistingEventForAssertion(e, aoRepair.getExistingEvents().get(index++));           
        }
        SubscriptionData newAoSubscription = (SubscriptionData)  entitlementApi.getSubscriptionFromId(aoSubscription.getId());
        assertEquals(newAoSubscription.getState(), SubscriptionState.ACTIVE);
        assertEquals(newAoSubscription.getAllTransitions().size(), 2);
        assertEquals(newAoSubscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION);
                
        SubscriptionData newBaseSubscription = (SubscriptionData)  entitlementApi.getSubscriptionFromId(baseSubscription.getId());
        assertEquals(newBaseSubscription.getState(), SubscriptionState.ACTIVE);
        assertEquals(newBaseSubscription.getAllTransitions().size(), 2);
        assertEquals(newBaseSubscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION);
        
        dryRun = false;
        BundleRepair realRunBundleRepair = repairApi.repairBundle(bRepair, dryRun, context);
        
        aoRepair = getSubscriptionRepair(aoSubscription.getId(), realRunBundleRepair);
        assertEquals(aoRepair.getExistingEvents().size(), 2);
        index = 0;
        for (ExistingEvent e : expected) {
           validateExistingEventForAssertion(e, aoRepair.getExistingEvents().get(index++));           
        }
        
        newAoSubscription = (SubscriptionData)  entitlementApi.getSubscriptionFromId(aoSubscription.getId());
        assertEquals(newAoSubscription.getState(), SubscriptionState.CANCELLED);
        assertEquals(newAoSubscription.getAllTransitions().size(), 2);
        assertEquals(newAoSubscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION + 1);        
                
        newBaseSubscription = (SubscriptionData)  entitlementApi.getSubscriptionFromId(baseSubscription.getId());
        assertEquals(newBaseSubscription.getState(), SubscriptionState.ACTIVE);
        assertEquals(newBaseSubscription.getAllTransitions().size(), 2);
        assertEquals(newBaseSubscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION);
    }
    
    
    @Test(groups={"slow"})
    public void testRepairRecreateAO() throws Exception {
        String baseProduct = "Shotgun";
        BillingPeriod baseTerm = BillingPeriod.MONTHLY;
        String basePriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        // CREATE BP
        SubscriptionData baseSubscription = createSubscription(baseProduct, baseTerm, basePriceList);

        // MOVE CLOCK A LITTLE BIT-- STILL IN TRIAL
        Duration someTimeLater = getDurationDay(3);
        clock.setDeltaFromReality(someTimeLater, DAY_IN_MS);

        SubscriptionData aoSubscription = createSubscription("Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);

        // MOVE CLOCK A LITTLE BIT MORE -- STILL IN TRIAL
        clock.addDeltaFromReality(someTimeLater);

        BundleRepair bundleRepair = repairApi.getBundleRepair(bundle.getId());
        sortEventsOnBundle(bundleRepair);
        
        // Quick check
        SubscriptionRepair bpRepair = getSubscriptionRepair(baseSubscription.getId(), bundleRepair);
        assertEquals(bpRepair.getExistingEvents().size(), 2);
        
        SubscriptionRepair aoRepair = getSubscriptionRepair(aoSubscription.getId(), bundleRepair);
        assertEquals(aoRepair.getExistingEvents().size(), 2);
        

        List<DeletedEvent> des = new LinkedList<SubscriptionRepair.DeletedEvent>();
        des.add(createDeletedEvent(aoRepair.getExistingEvents().get(0).getEventId()));        
        des.add(createDeletedEvent(aoRepair.getExistingEvents().get(1).getEventId()));

        DateTime aoRecreateDate = aoSubscription.getStartDate().plusDays(1);
        PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Telescopic-Scope", ProductCategory.ADD_ON, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.DISCOUNT);
        NewEvent ne = createNewEvent(SubscriptionTransitionType.CREATE, aoRecreateDate, spec);
        
        SubscriptionRepair saoRepair = createSubscriptionReapir(aoSubscription.getId(), des, Collections.singletonList(ne));
        
        BundleRepair bRepair =  createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(saoRepair));
        
        boolean dryRun = true;
        BundleRepair dryRunBundleRepair = repairApi.repairBundle(bRepair, dryRun, context);
        
        aoRepair = getSubscriptionRepair(aoSubscription.getId(), dryRunBundleRepair);
        assertEquals(aoRepair.getExistingEvents().size(), 2);
        
        
        List<ExistingEvent> expected = new LinkedList<SubscriptionRepair.ExistingEvent>();
        expected.add(createExistingEventForAssertion(SubscriptionTransitionType.CREATE, "Telescopic-Scope", PhaseType.DISCOUNT,
                ProductCategory.ADD_ON, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, aoRecreateDate));
        expected.add(createExistingEventForAssertion(SubscriptionTransitionType.PHASE, "Telescopic-Scope", PhaseType.EVERGREEN,
                    ProductCategory.ADD_ON, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, baseSubscription.getStartDate().plusMonths(1) /* Bundle align */));
        int index = 0;
        for (ExistingEvent e : expected) {
           validateExistingEventForAssertion(e, aoRepair.getExistingEvents().get(index++));           
        }
        SubscriptionData newAoSubscription = (SubscriptionData)  entitlementApi.getSubscriptionFromId(aoSubscription.getId());
        assertEquals(newAoSubscription.getState(), SubscriptionState.ACTIVE);
        assertEquals(newAoSubscription.getAllTransitions().size(), 2);
        assertEquals(newAoSubscription.getStartDate(), aoSubscription.getStartDate());
        assertEquals(newAoSubscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION);        
        
        // NOW COMMIT
        dryRun = false;
        BundleRepair realRunBundleRepair = repairApi.repairBundle(bRepair, dryRun, context);
        
        aoRepair = getSubscriptionRepair(aoSubscription.getId(), realRunBundleRepair);
        assertEquals(aoRepair.getExistingEvents().size(), 2);
        index = 0;
        for (ExistingEvent e : expected) {
           validateExistingEventForAssertion(e, aoRepair.getExistingEvents().get(index++));           
        }
        
        newAoSubscription = (SubscriptionData)  entitlementApi.getSubscriptionFromId(aoSubscription.getId());
        assertEquals(newAoSubscription.getState(), SubscriptionState.ACTIVE);
        assertEquals(newAoSubscription.getAllTransitions().size(), 2);
        assertEquals(newAoSubscription.getStartDate(), aoRecreateDate);
        assertEquals(newAoSubscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION + 1);        

    }
    
    // Fasten your seatbelt here:
    //
    // We are doing repair for multi-phase tiered-addon with different alignment:
    // Telescopic-Scope -> Laser-Scope
    // Tiered ADON logix
    // . Both multi phase
    // . Telescopic-Scope (bundle align) and Laser-Scope is Subscription align
    //
    @Test(groups={"slow"})
    public void testRepairChangeAOOK() throws Exception {
        
        String baseProduct = "Shotgun";
        BillingPeriod baseTerm = BillingPeriod.MONTHLY;
        String basePriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        // CREATE BP
        SubscriptionData baseSubscription = createSubscription(baseProduct, baseTerm, basePriceList);

        // MOVE CLOCK A LITTLE BIT-- STILL IN TRIAL
        Duration someTimeLater = getDurationDay(3);
        clock.setDeltaFromReality(someTimeLater, DAY_IN_MS);

        SubscriptionData aoSubscription = createSubscription("Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);

        // MOVE CLOCK A LITTLE BIT MORE -- STILL IN TRIAL
        clock.addDeltaFromReality(someTimeLater);
        
        BundleRepair bundleRepair = repairApi.getBundleRepair(bundle.getId());
        sortEventsOnBundle(bundleRepair);
        
        // Quick check
        SubscriptionRepair bpRepair = getSubscriptionRepair(baseSubscription.getId(), bundleRepair);
        assertEquals(bpRepair.getExistingEvents().size(), 2);
        
        SubscriptionRepair aoRepair = getSubscriptionRepair(aoSubscription.getId(), bundleRepair);
        assertEquals(aoRepair.getExistingEvents().size(), 2);

        List<DeletedEvent> des = new LinkedList<SubscriptionRepair.DeletedEvent>();
        des.add(createDeletedEvent(aoRepair.getExistingEvents().get(1).getEventId()));        
        DateTime aoChangeDate = aoSubscription.getStartDate().plusDays(1);
        PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Laser-Scope", ProductCategory.ADD_ON, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.TRIAL);
        NewEvent ne = createNewEvent(SubscriptionTransitionType.CHANGE, aoChangeDate, spec);
        
        SubscriptionRepair saoRepair = createSubscriptionReapir(aoSubscription.getId(), des, Collections.singletonList(ne));
        
        BundleRepair bRepair =  createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(saoRepair));
        
        boolean dryRun = true;
        BundleRepair dryRunBundleRepair = repairApi.repairBundle(bRepair, dryRun, context);
        
        aoRepair = getSubscriptionRepair(aoSubscription.getId(), dryRunBundleRepair);
        assertEquals(aoRepair.getExistingEvents().size(), 3);
        
        
        List<ExistingEvent> expected = new LinkedList<SubscriptionRepair.ExistingEvent>();
        expected.add(createExistingEventForAssertion(SubscriptionTransitionType.CREATE, "Telescopic-Scope", PhaseType.DISCOUNT,
                ProductCategory.ADD_ON, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, aoSubscription.getStartDate()));
        expected.add(createExistingEventForAssertion(SubscriptionTransitionType.CHANGE, "Laser-Scope", PhaseType.DISCOUNT,
                ProductCategory.ADD_ON, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, aoChangeDate));
        expected.add(createExistingEventForAssertion(SubscriptionTransitionType.PHASE, "Laser-Scope", PhaseType.EVERGREEN,
                ProductCategory.ADD_ON, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY,
                aoSubscription.getStartDate().plusMonths(1) /* Subscription alignment */));
                
        int index = 0;
        for (ExistingEvent e : expected) {
           validateExistingEventForAssertion(e, aoRepair.getExistingEvents().get(index++));           
        }
        SubscriptionData newAoSubscription = (SubscriptionData)  entitlementApi.getSubscriptionFromId(aoSubscription.getId());
        assertEquals(newAoSubscription.getState(), SubscriptionState.ACTIVE);
        assertEquals(newAoSubscription.getAllTransitions().size(), 2);
        
        // AND NOW COMMIT
        dryRun = false;
        BundleRepair realRunBundleRepair = repairApi.repairBundle(bRepair, dryRun, context);
        
        aoRepair = getSubscriptionRepair(aoSubscription.getId(), realRunBundleRepair);
        assertEquals(aoRepair.getExistingEvents().size(), 3);
        index = 0;
        for (ExistingEvent e : expected) {
           validateExistingEventForAssertion(e, aoRepair.getExistingEvents().get(index++));           
        }
        
        newAoSubscription = (SubscriptionData)  entitlementApi.getSubscriptionFromId(aoSubscription.getId());
        assertEquals(newAoSubscription.getState(), SubscriptionState.ACTIVE);
        assertEquals(newAoSubscription.getAllTransitions().size(), 3);
        
        
        assertEquals(newAoSubscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION + 1);
        assertEquals(newAoSubscription.getBundleId(), bundle.getId());
        assertEquals(newAoSubscription.getStartDate(), aoSubscription.getStartDate());

        Plan currentPlan = newAoSubscription.getCurrentPlan();
        assertNotNull(currentPlan);
        assertEquals(currentPlan.getProduct().getName(), "Laser-Scope");
        assertEquals(currentPlan.getProduct().getCategory(), ProductCategory.ADD_ON);
        assertEquals(currentPlan.getBillingPeriod(), BillingPeriod.MONTHLY);

        PlanPhase currentPhase = newAoSubscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.DISCOUNT);
        
        testListener.pushExpectedEvent(NextEvent.PHASE);
        someTimeLater = getDurationDay(60);
        clock.addDeltaFromReality(someTimeLater);
        assertTrue(testListener.isCompleted(5000));
        
        newAoSubscription = (SubscriptionData)  entitlementApi.getSubscriptionFromId(aoSubscription.getId());
        currentPhase = newAoSubscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.EVERGREEN);
    }
}
