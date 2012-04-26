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
package com.ning.billing.beatrix.integration;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.testng.Assert;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.beatrix.integration.TestBusHandler.NextEvent;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.entitlement.api.repair.BundleRepair;
import com.ning.billing.entitlement.api.repair.SubscriptionRepair;
import com.ning.billing.entitlement.api.repair.SubscriptionRepair.DeletedEvent;
import com.ning.billing.entitlement.api.repair.SubscriptionRepair.ExistingEvent;
import com.ning.billing.entitlement.api.repair.SubscriptionRepair.NewEvent;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.api.user.SubscriptionEvents;
import com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;

@Test(groups = "slow")
@Guice(modules = {MockModule.class})
public class TestRepairIntegration extends TestIntegrationBase {
    
    @Test(groups={"slow"}, enabled=true)
    public void testRepairChangeBPWithAddonIncluded() throws Exception {
        
        DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());
        
        Account account = accountUserApi.createAccount(getAccountData(25), null, null, context);
        assertNotNull(account);

        SubscriptionBundle bundle = entitlementUserApi.createBundleForAccount(account.getId(), "whatever", context);

        String productName = "Shotgun";
        BillingPeriod term = BillingPeriod.MONTHLY;
        String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

        busHandler.pushExpectedEvent(NextEvent.CREATE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        SubscriptionData baseSubscription = (SubscriptionData) entitlementUserApi.createSubscription(bundle.getId(),
                new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSetName, null), null, context);
        assertNotNull(baseSubscription);
        assertTrue(busHandler.isCompleted(DELAY));
   
        // MOVE CLOCK A LITTLE BIT-- STILL IN TRIAL
        Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(3));
        clock.addDeltaFromReality(it.toDurationMillis());


        SubscriptionData aoSubscription = (SubscriptionData) entitlementUserApi.createSubscription(bundle.getId(),
                new PlanPhaseSpecifier("Telescopic-Scope", ProductCategory.ADD_ON, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null), null, context);
        
        SubscriptionData aoSubscription2 = (SubscriptionData) entitlementUserApi.createSubscription(bundle.getId(),
                new PlanPhaseSpecifier("Laser-Scope", ProductCategory.ADD_ON, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null), null, context); 

        // MOVE CLOCK A LITTLE BIT MORE -- STILL IN TRIAL
        it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(3));
        clock.addDeltaFromReality(it.toDurationMillis());


        BundleRepair bundleRepair = repairApi.getBundleRepair(bundle.getId());
        sortEventsOnBundle(bundleRepair);
        
        // Quick check
        SubscriptionRepair bpRepair = getSubscriptionRepair(baseSubscription.getId(), bundleRepair);
        assertEquals(bpRepair.getExistingEvents().size(), 2);
        
        SubscriptionRepair aoRepair = getSubscriptionRepair(aoSubscription.getId(), bundleRepair);
        assertEquals(aoRepair.getExistingEvents().size(), 2);

        SubscriptionRepair aoRepair2 = getSubscriptionRepair(aoSubscription2.getId(), bundleRepair);
        assertEquals(aoRepair2.getExistingEvents().size(), 2);

        DateTime bpChangeDate = clock.getUTCNow().minusDays(1);
        
        List<DeletedEvent> des = new LinkedList<SubscriptionRepair.DeletedEvent>();
        des.add(createDeletedEvent(bpRepair.getExistingEvents().get(1).getEventId()));        
        
        PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.TRIAL);
        NewEvent ne = createNewEvent(SubscriptionTransitionType.CHANGE, bpChangeDate, spec);
        
        bpRepair = createSubscriptionReapir(baseSubscription.getId(), des, Collections.singletonList(ne));
        
        bundleRepair =  createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(bpRepair));
        
        // TIME TO  REPAIR
        busHandler.pushExpectedEvent(NextEvent.REPAIR_BUNDLE);
        BundleRepair realRunBundleRepair = repairApi.repairBundle(bundleRepair, false, context);
        assertTrue(busHandler.isCompleted(DELAY));
        
        aoRepair = getSubscriptionRepair(aoSubscription.getId(), realRunBundleRepair);
        assertEquals(aoRepair.getExistingEvents().size(), 2);

        bpRepair = getSubscriptionRepair(baseSubscription.getId(), realRunBundleRepair);
        assertEquals(bpRepair.getExistingEvents().size(), 3);        
        
        // Check expected for AO
        List<ExistingEvent> expectedAO = new LinkedList<SubscriptionRepair.ExistingEvent>();
        expectedAO.add(createExistingEventForAssertion(SubscriptionTransitionType.CREATE, "Telescopic-Scope", PhaseType.DISCOUNT,
                ProductCategory.ADD_ON, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, aoSubscription.getStartDate()));
        expectedAO.add(createExistingEventForAssertion(SubscriptionTransitionType.CANCEL, "Telescopic-Scope", PhaseType.DISCOUNT,
                ProductCategory.ADD_ON, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, bpChangeDate));
        int index = 0;
        for (ExistingEvent e : expectedAO) {
           validateExistingEventForAssertion(e, aoRepair.getExistingEvents().get(index++));           
        }

        List<ExistingEvent> expectedAO2 = new LinkedList<SubscriptionRepair.ExistingEvent>();
        expectedAO2.add(createExistingEventForAssertion(SubscriptionTransitionType.CREATE, "Laser-Scope", PhaseType.DISCOUNT,
                ProductCategory.ADD_ON, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, aoSubscription2.getStartDate()));
        expectedAO2.add(createExistingEventForAssertion(SubscriptionTransitionType.PHASE, "Laser-Scope", PhaseType.EVERGREEN,
                ProductCategory.ADD_ON, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, aoSubscription2.getStartDate().plusMonths(1)));
        index = 0;
        for (ExistingEvent e : expectedAO2) {
           validateExistingEventForAssertion(e, aoRepair2.getExistingEvents().get(index++));           
        }
        
        // Check expected for BP        
        List<ExistingEvent> expectedBP = new LinkedList<SubscriptionRepair.ExistingEvent>();
        expectedBP.add(createExistingEventForAssertion(SubscriptionTransitionType.CREATE, "Shotgun", PhaseType.TRIAL,
                ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.NO_BILLING_PERIOD, baseSubscription.getStartDate()));
        expectedBP.add(createExistingEventForAssertion(SubscriptionTransitionType.CHANGE, "Assault-Rifle", PhaseType.TRIAL,
                ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.NO_BILLING_PERIOD, bpChangeDate));
        expectedBP.add(createExistingEventForAssertion(SubscriptionTransitionType.PHASE, "Assault-Rifle", PhaseType.EVERGREEN,
                ProductCategory.BASE, PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, baseSubscription.getStartDate().plusDays(30)));
        index = 0;
        for (ExistingEvent e : expectedBP) {
           validateExistingEventForAssertion(e, bpRepair.getExistingEvents().get(index++));           
        }

        SubscriptionData newAoSubscription = (SubscriptionData)  entitlementUserApi.getSubscriptionFromId(aoSubscription.getId());
        assertEquals(newAoSubscription.getState(), SubscriptionState.CANCELLED);
        assertEquals(newAoSubscription.getAllTransitions().size(), 2);
        assertEquals(newAoSubscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION + 1);
            
        SubscriptionData newAoSubscription2 = (SubscriptionData)  entitlementUserApi.getSubscriptionFromId(aoSubscription2.getId());
        assertEquals(newAoSubscription2.getState(), SubscriptionState.ACTIVE);
        assertEquals(newAoSubscription2.getAllTransitions().size(), 2);
        assertEquals(newAoSubscription2.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION + 1);

        
        SubscriptionData newBaseSubscription = (SubscriptionData)  entitlementUserApi.getSubscriptionFromId(baseSubscription.getId());
        assertEquals(newBaseSubscription.getState(), SubscriptionState.ACTIVE);
        assertEquals(newBaseSubscription.getAllTransitions().size(), 3);
        assertEquals(newBaseSubscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION + 1);
        
    
 
    }
    
    protected SubscriptionRepair createSubscriptionReapir(final UUID id, final List<DeletedEvent> deletedEvents, final List<NewEvent> newEvents) {
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

    
    protected BundleRepair createBundleRepair(final UUID bundleId, final String viewId, final List<SubscriptionRepair> subscriptionRepair) {
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

    protected ExistingEvent createExistingEventForAssertion(final SubscriptionTransitionType type, 
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
    
    protected SubscriptionRepair getSubscriptionRepair(final UUID id, final BundleRepair bundleRepair) {
        for (SubscriptionRepair cur : bundleRepair.getSubscriptions()) {
            if (cur.getId().equals(id)) {
                return cur;
            }
        }
        Assert.fail("Failed to find SubscriptionReapir " + id);
        return null;
    }
    protected void validateExistingEventForAssertion(final ExistingEvent expected, final ExistingEvent input) {
        
        log.info(String.format("Got %s -> Expected %s", input.getPlanPhaseSpecifier().getProductName(), expected.getPlanPhaseSpecifier().getProductName()));
        assertEquals(input.getPlanPhaseSpecifier().getProductName(), expected.getPlanPhaseSpecifier().getProductName());
        log.info(String.format("Got %s -> Expected %s", input.getPlanPhaseSpecifier().getPhaseType(), expected.getPlanPhaseSpecifier().getPhaseType()));
        assertEquals(input.getPlanPhaseSpecifier().getPhaseType(), expected.getPlanPhaseSpecifier().getPhaseType());
        log.info(String.format("Got %s -> Expected %s", input.getPlanPhaseSpecifier().getProductCategory(), expected.getPlanPhaseSpecifier().getProductCategory()));
        assertEquals(input.getPlanPhaseSpecifier().getProductCategory(), expected.getPlanPhaseSpecifier().getProductCategory());                    
        log.info(String.format("Got %s -> Expected %s", input.getPlanPhaseSpecifier().getPriceListName(), expected.getPlanPhaseSpecifier().getPriceListName()));
        assertEquals(input.getPlanPhaseSpecifier().getPriceListName(), expected.getPlanPhaseSpecifier().getPriceListName());                    
        log.info(String.format("Got %s -> Expected %s", input.getPlanPhaseSpecifier().getBillingPeriod(), expected.getPlanPhaseSpecifier().getBillingPeriod()));
        assertEquals(input.getPlanPhaseSpecifier().getBillingPeriod(), expected.getPlanPhaseSpecifier().getBillingPeriod());
        log.info(String.format("Got %s -> Expected %s", input.getEffectiveDate(), expected.getEffectiveDate()));
        assertEquals(input.getEffectiveDate(), expected.getEffectiveDate());        
    }
    
    protected DeletedEvent createDeletedEvent(final UUID eventId) {
        return new DeletedEvent() {
            @Override
            public UUID getEventId() {
                return eventId;
            }
        };
    }

    protected NewEvent createNewEvent(final SubscriptionTransitionType type, final DateTime requestedDate, final PlanPhaseSpecifier spec) {

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

    protected void sortEventsOnBundle(final BundleRepair bundle) {
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

    protected void sortExistingEvent(final List<ExistingEvent> events) {
        Collections.sort(events, new Comparator<ExistingEvent>() {
            @Override
            public int compare(ExistingEvent arg0, ExistingEvent arg1) {
                return arg0.getEffectiveDate().compareTo(arg1.getEffectiveDate());
            }
        });
    }
    protected void sortNewEvent(final List<NewEvent> events) {
        Collections.sort(events, new Comparator<NewEvent>() {
            @Override
            public int compare(NewEvent arg0, NewEvent arg1) {
                return arg0.getRequestedDate().compareTo(arg1.getRequestedDate());
            }
        });
    }
    

}
