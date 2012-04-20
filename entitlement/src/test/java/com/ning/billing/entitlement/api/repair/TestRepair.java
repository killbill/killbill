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
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Duration;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.entitlement.api.TestApiBase;
import com.ning.billing.entitlement.api.ApiTestListener.NextEvent;
import com.ning.billing.entitlement.api.repair.SubscriptionRepair.DeletedEvent;
import com.ning.billing.entitlement.api.repair.SubscriptionRepair.ExistingEvent;
import com.ning.billing.entitlement.api.repair.SubscriptionRepair.NewEvent;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.api.user.SubscriptionEvents;
import com.ning.billing.entitlement.glue.MockEngineModuleSql;

public class TestRepair extends TestApiBase {

    @Override
    public Injector getInjector() {
        return Guice.createInjector(Stage.DEVELOPMENT, new MockEngineModuleSql());
    }

    @Test(groups={"slow"})
    public void testFetchBundleRepair() {
        try {

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
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }
    
    @Test(groups={"slow"})
    public void testSimpleBPRepairReplaceCreateBeforeTrial() throws Exception {
        String baseProduct = "Shotgun";
        String newBaseProduct = "Assault-Rifle";
        
        DateTime startDate = clock.getUTCNow();
        int clockShift = -10;
        DateTime restartDate =  startDate.plusDays(clockShift).minusDays(1);
        LinkedList<ExistingEvent> expected = new LinkedList<SubscriptionRepair.ExistingEvent>();
        
        expected.add(createExistingEventForAssertion(SubscriptionTransitionType.CREATE, newBaseProduct, PhaseType.TRIAL,
                ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.NO_BILLING_PERIOD, restartDate));
        expected.add(createExistingEventForAssertion(SubscriptionTransitionType.PHASE, newBaseProduct, PhaseType.EVERGREEN,
                    ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, restartDate.plusDays(30)));

        testSimpleBPRepairCreate(true, startDate, clockShift, baseProduct, newBaseProduct, expected);
    }

    @Test(groups={"slow"}, enabled=true)
    public void testSimpleBPRepairReplaceCreateInTrial() throws Exception {
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

        testSimpleBPRepairCreate(true, startDate, clockShift, baseProduct, newBaseProduct, expected);
    }

    
    @Test(groups={"slow"})
    public void testSimpleBPRepairReplaceCreateAfterTrial() throws Exception {
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

        testSimpleBPRepairCreate(false, startDate, clockShift, baseProduct, newBaseProduct, expected);
        
    }
    
    
    private void testSimpleBPRepairCreate(boolean inTrial, DateTime startDate, int clockShift, 
            String baseProduct, String newBaseProduct, List<ExistingEvent> expectedEvents) throws Exception {

        // CREATE BP
        Subscription baseSubscription = createSubscription(baseProduct, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, startDate);

        // MOVE CLOCK
        if (clockShift > 0) {
            if (!inTrial) {
                testListener.pushExpectedEvent(NextEvent.PHASE);
            }               
            Duration durationShift = getDurationDay(clockShift);
            clock.setDeltaFromReality(durationShift, 0);
            if (!inTrial) {
                assertTrue(testListener.isCompleted(5000));
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

        
    }

    @Test(groups={"slow"})
    public void testSimpleBPRepairAddChangeInTrial() throws Exception {
        
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

        testSimpleBPRepairAddChange(true, startDate, clockShift, baseProduct, newBaseProduct, expected);
    }

    @Test(groups={"slow"})
    public void testSimpleBPRepairAddChangeAfterTrial() throws Exception {
        
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
        testSimpleBPRepairAddChange(false, startDate, clockShift, baseProduct, newBaseProduct, expected);
    }

    private void testSimpleBPRepairAddChange(boolean inTrial, DateTime startDate, int clockShift, 
            String baseProduct, String newBaseProduct, List<ExistingEvent> expectedEvents) throws Exception {

        // CREATE BP
        Subscription baseSubscription = createSubscription(baseProduct, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, startDate);

        // MOVE CLOCK
        if (!inTrial) {
            testListener.pushExpectedEvent(NextEvent.PHASE);
        }               

        Duration durationShift = getDurationDay(clockShift);
        clock.setDeltaFromReality(durationShift, 0);
        if (!inTrial) {
            assertTrue(testListener.isCompleted(5000));
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
        
    }


    private SubscriptionRepair createSubscriptionReapir(final UUID id, final List<DeletedEvent> deletedEvents, final List<NewEvent> newEvents) {
        return new SubscriptionRepair() {
            @Override
            public UUID getId() {
                return id;
            }
            @Override
            public List<NewEvent> getNewEvents() {
                return newEvents;
            }
            @Override
            public List<ExistingEvent> getExistingEvents() {
                return null;
            }
            @Override
            public List<DeletedEvent> getDeletedEvents() {
                return deletedEvents;
            }
        };
    }

    private BundleRepair createBundleRepair(final UUID bundleId, final String viewId, final List<SubscriptionRepair> subscriptionRepair) {
        return new BundleRepair() {
            @Override
            public String getViewId() {
                return viewId;
            }
            @Override
            public List<SubscriptionRepair> getSubscriptions() {
                return subscriptionRepair;
            }
            @Override
            public UUID getBundleId() {
                return bundleId;
            }
        };
    }

    private ExistingEvent createExistingEventForAssertion(final SubscriptionTransitionType type, 
            final String productName, final PhaseType phaseType, final ProductCategory category, final String priceListName, final BillingPeriod billingPeriod,
            final DateTime effectiveDateTime) {

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier(productName, category, billingPeriod, priceListName, phaseType);
        ExistingEvent ev = new ExistingEvent() {
            @Override
            public SubscriptionTransitionType getSubscriptionTransitionType() {
                return type;
            }
             @Override
            public DateTime getRequestedDate() {
                 return null;
            }
            @Override
            public PlanPhaseSpecifier getPlanPhaseSpecifier() {
                return spec;
            }
            @Override
            public UUID getEventId() {
                return null;
            }
            @Override
            public DateTime getEffectiveDate() {
                return effectiveDateTime;
            }
        };
        return ev;
    }
    
    private void validateExistingEventForAssertion(final ExistingEvent expected, final ExistingEvent input) {
        assertEquals(expected.getPlanPhaseSpecifier().getProductName(), input.getPlanPhaseSpecifier().getProductName());
        assertEquals(expected.getPlanPhaseSpecifier().getPhaseType(), input.getPlanPhaseSpecifier().getPhaseType());
        assertEquals(expected.getPlanPhaseSpecifier().getProductCategory(), input.getPlanPhaseSpecifier().getProductCategory());                    
        assertEquals(expected.getPlanPhaseSpecifier().getPriceListName(), input.getPlanPhaseSpecifier().getPriceListName());                    
        assertEquals(expected.getPlanPhaseSpecifier().getBillingPeriod(), input.getPlanPhaseSpecifier().getBillingPeriod());
        assertEquals(expected.getEffectiveDate(), input.getEffectiveDate());        
    }
    
    private DeletedEvent createDeletedEvent(final UUID eventId) {
        return new DeletedEvent() {
            @Override
            public UUID getEventId() {
                return eventId;
            }
        };
    }

    private NewEvent createNewEvent(final SubscriptionTransitionType type, final DateTime requestedDate, final PlanPhaseSpecifier spec) {

        return new NewEvent() {
            @Override
            public SubscriptionTransitionType getSubscriptionTransitionType() {
                return type;
            }
            @Override
            public DateTime getRequestedDate() {
                return requestedDate;
            }
            @Override
            public PlanPhaseSpecifier getPlanPhaseSpecifier() {
                return spec;
            }
        };
    }

    private void sortEventsOnBundle(final BundleRepair bundle) {
        if (bundle.getSubscriptions() == null) {
            return;
        }
        for (SubscriptionRepair cur : bundle.getSubscriptions()) {
            if (cur.getExistingEvents() != null) {
                sortExistingEvent(cur.getExistingEvents());
            }
            if (cur.getNewEvents() != null) {
                sortNewEvent(cur.getNewEvents());
            }
        }
    }

    private void sortExistingEvent(final List<ExistingEvent> events) {
        Collections.sort(events, new Comparator<ExistingEvent>() {
            @Override
            public int compare(ExistingEvent arg0, ExistingEvent arg1) {
                return arg0.getEffectiveDate().compareTo(arg1.getEffectiveDate());
            }
        });
    }
    private void sortNewEvent(final List<NewEvent> events) {
        Collections.sort(events, new Comparator<NewEvent>() {
            @Override
            public int compare(NewEvent arg0, NewEvent arg1) {
                return arg0.getRequestedDate().compareTo(arg1.getRequestedDate());
            }
        });
    }

}
