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

package com.ning.billing.junction.plumbing.billing;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

import org.joda.time.DateTime;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.ning.billing.account.api.Account;
import com.ning.billing.catalog.MockPlan;
import com.ning.billing.catalog.MockPlanPhase;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.entitlement.api.billing.BillingEvent;
import com.ning.billing.entitlement.api.billing.BillingModeType;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.junction.api.Blockable;
import com.ning.billing.junction.api.Blockable.Type;
import com.ning.billing.junction.api.BlockingApi;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.junction.api.DefaultBlockingState;
import com.ning.billing.junction.dao.BlockingStateDao;
import com.ning.billing.junction.plumbing.billing.BlockingCalculator.DisabledDuration;
import com.ning.billing.mock.BrainDeadProxyFactory;
import com.ning.billing.mock.BrainDeadProxyFactory.ZombieControl;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;


public class TestBlockingCalculator {

    private static final String DISABLED_BUNDLE = "disabled-bundle";
    private static final String CLEAR_BUNDLE = "clear-bundle";

    private BlockingApi blockingApi;
    private Account account;
    private Subscription subscription1;
    private Subscription subscription2;
    private Subscription subscription3;
    private Subscription subscription4;
    private UUID bundleId1 = UUID.randomUUID();
    private UUID bundleId2 = UUID.randomUUID();
    private Clock clock;
    private BlockingCalculator odc;

    @BeforeClass
    public void setUpBeforeClass() throws Exception {

        clock = new ClockMock();
        
        Injector i = Guice.createInjector(new AbstractModule() {

            @Override
            protected void configure() {
                blockingApi = BrainDeadProxyFactory.createBrainDeadProxyFor(BlockingApi.class);
                account = BrainDeadProxyFactory.createBrainDeadProxyFor(Account.class);
                subscription1 = BrainDeadProxyFactory.createBrainDeadProxyFor(Subscription.class, Comparable.class);
                subscription2 = BrainDeadProxyFactory.createBrainDeadProxyFor(Subscription.class, Comparable.class);
                subscription3 = BrainDeadProxyFactory.createBrainDeadProxyFor(Subscription.class, Comparable.class);
                subscription4 = BrainDeadProxyFactory.createBrainDeadProxyFor(Subscription.class, Comparable.class);
                ((ZombieControl) account).addResult("getId", UUID.randomUUID());
                ((ZombieControl) subscription1).addResult("getBundleId", bundleId1);
                ((ZombieControl) subscription2).addResult("getBundleId", bundleId1);
                ((ZombieControl) subscription3).addResult("getBundleId", bundleId1);
                ((ZombieControl) subscription4).addResult("getBundleId", bundleId2);
                ((ZombieControl) subscription1).addResult("compareTo", 1);
                ((ZombieControl) subscription2).addResult("compareTo", 1);
                ((ZombieControl) subscription3).addResult("compareTo", 1);
                ((ZombieControl) subscription4).addResult("compareTo", 1);
                ((ZombieControl) subscription1).addResult("getId", UUID.randomUUID());
                ((ZombieControl) subscription2).addResult("getId", UUID.randomUUID());
                ((ZombieControl) subscription3).addResult("getId", UUID.randomUUID());
                ((ZombieControl) subscription4).addResult("getId", UUID.randomUUID());
         
         
                bind(BlockingStateDao.class).toInstance(BrainDeadProxyFactory.createBrainDeadProxyFor(BlockingStateDao.class));
                bind(BlockingApi.class).toInstance(blockingApi);              
                              
            }
            
        });
        odc = i.getInstance(BlockingCalculator.class);

    }

    @Test
    // S1-S2-S3 subscriptions in B1
    // B1 -----[--------]
    // S1 --A-------------------------------------
    // S2 --B------C------------------------------
    // S3 ------------------D---------------------
    

    //Result
    // S1 --A--[-------]--------------------------
    // S2 --B--[-------]--------------------------
    // S3 ------------------D---------------------
    
    public void testInsertBlockingEvents() {
        DateTime now = clock.getUTCNow();
        List<DisabledDuration> disabledDuration = new ArrayList<BlockingCalculator.DisabledDuration>();
        SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();
        
        disabledDuration.add(new DisabledDuration(now, null));
        BillingEvent A = createRealEvent(now.minusDays(1).minusHours(1), subscription1);
        BillingEvent B = createRealEvent(now.minusDays(1), subscription2);
        BillingEvent C = createRealEvent(now.plusDays(1), subscription2);
        BillingEvent D = createRealEvent(now.plusDays(3), subscription3);
        billingEvents.add(A);
        billingEvents.add(B);
        billingEvents.add(C);
        billingEvents.add(D);

        SortedSet<BlockingState> blockingStates = new TreeSet<BlockingState>();
        blockingStates.add(new DefaultBlockingState(bundleId1,DISABLED_BUNDLE, Blockable.Type.SUBSCRIPTION_BUNDLE, "test", true, true, true, now));
        blockingStates.add(new DefaultBlockingState(bundleId1,CLEAR_BUNDLE, Blockable.Type.SUBSCRIPTION_BUNDLE, "test", false, false, false, now.plusDays(2)));
        
        ((ZombieControl)blockingApi).addResult("getBlockingHistory", blockingStates);
        
        
        odc.insertBlockingEvents(billingEvents);
        
        assertEquals(billingEvents.size(), 7);
        
        SortedSet<BillingEvent> s1Events = odc.filter(billingEvents, subscription1);
        Iterator<BillingEvent> it1 = s1Events.iterator();
        assertEquals(it1.next(), A);
        assertEquals(it1.next().getTransitionType(), SubscriptionTransitionType.CANCEL);
        assertEquals(it1.next().getTransitionType(), SubscriptionTransitionType.RE_CREATE);
        
        SortedSet<BillingEvent> s2Events = odc.filter(billingEvents, subscription2);
        Iterator<BillingEvent> it2 = s2Events.iterator();       
        assertEquals(it2.next(), B);
        assertEquals(it2.next().getTransitionType(), SubscriptionTransitionType.CANCEL);
        assertEquals(it2.next().getTransitionType(), SubscriptionTransitionType.RE_CREATE);
                
        SortedSet<BillingEvent> s3Events = odc.filter(billingEvents, subscription3);
        Iterator<BillingEvent> it3 = s3Events.iterator();       
        assertEquals(it3.next(),D);
    }

    // Open ended duration with a previous event
    // --X--[----------------------------------
   @Test
    public void testEventsToRemoveOpenPrev() {
       DateTime now = clock.getUTCNow();
       List<DisabledDuration> disabledDuration = new ArrayList<BlockingCalculator.DisabledDuration>();
       SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();
       
       disabledDuration.add(new DisabledDuration(now, null));
       billingEvents.add(createRealEvent(now.minusDays(1), subscription1));
       
       SortedSet<BillingEvent> results = odc.eventsToRemove( disabledDuration, billingEvents, subscription1);
       
       assertEquals(results.size(), 0);
   }

   // Open with previous and following events
   // --X--[----Y-----------------------------
    @Test
    public void testEventsToRemoveOpenPrevFollow() {
        DateTime now = clock.getUTCNow();
        List<DisabledDuration> disabledDuration = new ArrayList<BlockingCalculator.DisabledDuration>();
        SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();
        
        disabledDuration.add(new DisabledDuration(now, null));
        BillingEvent e1 = createRealEvent(now.minusDays(1), subscription1);
        BillingEvent e2  = createRealEvent(now.plusDays(1), subscription1);
        billingEvents.add(e1);
        billingEvents.add(e2);
        
        SortedSet<BillingEvent> results = odc.eventsToRemove( disabledDuration, billingEvents,  subscription1);
        
        assertEquals(results.size(), 1);
        assertEquals(results.first(), e2);
    }

    // Open with no previous event (only following)
    // -----[----X-----------------------------
    @Test
    public void testEventsToRemoveOpenFollow() {
        DateTime now = clock.getUTCNow();
        List<DisabledDuration> disabledDuration = new ArrayList<BlockingCalculator.DisabledDuration>();
        SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();
        
        disabledDuration.add(new DisabledDuration(now, null));
        BillingEvent e1  = createRealEvent(now.plusDays(1), subscription1);
        billingEvents.add(e1);
        
        SortedSet<BillingEvent> results = odc.eventsToRemove( disabledDuration, billingEvents,  subscription1);
        
        assertEquals(results.size(), 1);
        assertEquals(results.first(), e1);
   }

    // Closed duration with a single previous event
    // --X--[------------]---------------------
        @Test
    public void testEventsToRemoveClosedPrev() {
            DateTime now = clock.getUTCNow();
            List<DisabledDuration> disabledDuration = new ArrayList<BlockingCalculator.DisabledDuration>();
            SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();
            
            disabledDuration.add(new DisabledDuration(now, now.plusDays(2)));
            BillingEvent e1  = createRealEvent(now.minusDays(1), subscription1);
            billingEvents.add(e1);
            
            SortedSet<BillingEvent> results = odc.eventsToRemove( disabledDuration, billingEvents,  subscription1);
            
            assertEquals(results.size(), 0);
    }

    // Closed duration with a previous event and in-between event
    // --X--[------Y-----]---------------------
    @Test
    public void testEventsToRemoveClosedPrevBetw() {
        DateTime now = clock.getUTCNow();
        List<DisabledDuration> disabledDuration = new ArrayList<BlockingCalculator.DisabledDuration>();
        SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();
        
        disabledDuration.add(new DisabledDuration(now, now.plusDays(2)));
        BillingEvent e1 = createRealEvent(now.minusDays(1), subscription1);
        BillingEvent e2  = createRealEvent(now.plusDays(1), subscription1);
        billingEvents.add(e1);
        billingEvents.add(e2);        
       
        
        SortedSet<BillingEvent> results = odc.eventsToRemove( disabledDuration, billingEvents, subscription1);
        
        assertEquals(results.size(), 1);
        assertEquals(results.first(), e2);
    }

    // Closed duration with a previous event and in-between event and following
    // --X--[------Y-----]-------Z-------------
    @Test
    public void testEventsToRemoveClosedPrevBetwNext() {
        DateTime now = clock.getUTCNow();
        List<DisabledDuration> disabledDuration = new ArrayList<BlockingCalculator.DisabledDuration>();
        SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();
        
        disabledDuration.add(new DisabledDuration(now, now.plusDays(2)));
        BillingEvent e1 = createRealEvent(now.minusDays(1), subscription1);
        BillingEvent e2  = createRealEvent(now.plusDays(1), subscription1);
        BillingEvent e3  = createRealEvent(now.plusDays(3), subscription1);
        billingEvents.add(e1);
        billingEvents.add(e2);        
        billingEvents.add(e3);        
        
        SortedSet<BillingEvent> results = odc.eventsToRemove( disabledDuration, billingEvents, subscription1);
        
        assertEquals(results.size(), 1);
        assertEquals(results.first(), e2);
   }

    // Closed with no previous event but in-between events
    // -----[------Y-----]---------------------
    @Test
    public void testEventsToRemoveClosedBetwn() {
        DateTime now = clock.getUTCNow();
        List<DisabledDuration> disabledDuration = new ArrayList<BlockingCalculator.DisabledDuration>();
        SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();
        
        disabledDuration.add(new DisabledDuration(now, now.plusDays(2)));
        BillingEvent e2  = createRealEvent(now.plusDays(1), subscription1);
        billingEvents.add(e2);        
      
        
        SortedSet<BillingEvent> results = odc.eventsToRemove( disabledDuration, billingEvents, subscription1);
        
        assertEquals(results.size(), 1);
        assertEquals(results.first(), e2);
    }

    // Closed with no previous event but in-between events and following
    // -----[------Y-----]-------Z-------------
    @Test
    public void testEventsToRemoveClosedBetweenFollow() {
        DateTime now = clock.getUTCNow();
        List<DisabledDuration> disabledDuration = new ArrayList<BlockingCalculator.DisabledDuration>();
        SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();
        
        disabledDuration.add(new DisabledDuration(now, now.plusDays(2)));

        BillingEvent e2  = createRealEvent(now.plusDays(1), subscription1);
        BillingEvent e3  = createRealEvent(now.plusDays(3), subscription1);
        billingEvents.add(e2);        
        billingEvents.add(e3);        
        
        SortedSet<BillingEvent> results = odc.eventsToRemove( disabledDuration, billingEvents, subscription1);
        
        assertEquals(results.size(), 1);
        assertEquals(results.first(), e2);
    }

    // Closed duration with only following
    // -----[------------]-------Z-------------
    @Test
    public void testEventsToRemoveClosedFollow() {
        DateTime now = clock.getUTCNow();
        List<DisabledDuration> disabledDuration = new ArrayList<BlockingCalculator.DisabledDuration>();
        SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();
        
        disabledDuration.add(new DisabledDuration(now, now.plusDays(2)));

        BillingEvent e3  = createRealEvent(now.plusDays(3), subscription1);
       
        billingEvents.add(e3);        
        
        SortedSet<BillingEvent> results = odc.eventsToRemove( disabledDuration, billingEvents, subscription1);
        
        assertEquals(results.size(), 0);
     }
    
    // Open ended duration with a previous event
    // --X--[----------------------------------
   @Test
    public void testCreateNewEventsOpenPrev() {
       DateTime now = clock.getUTCNow();
       List<DisabledDuration> disabledDuration = new ArrayList<BlockingCalculator.DisabledDuration>();
       SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();
       
       disabledDuration.add(new DisabledDuration(now, null));
       billingEvents.add(createRealEvent(now.minusDays(1), subscription1));
       
       SortedSet<BillingEvent> results = odc.createNewEvents( disabledDuration, billingEvents,  account, subscription1);
       
       assertEquals(results.size(), 1);
       assertEquals(results.first().getEffectiveDate(), now);
       assertEquals(results.first().getRecurringPrice(), BigDecimal.ZERO);
       assertEquals(results.first().getTransitionType(), SubscriptionTransitionType.CANCEL);
   }

   // Open with previous and following events
   // --X--[----Y-----------------------------
    @Test
    public void testCreateNewEventsOpenPrevFollow() {
        DateTime now = clock.getUTCNow();
        List<DisabledDuration> disabledDuration = new ArrayList<BlockingCalculator.DisabledDuration>();
        SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();
        
        disabledDuration.add(new DisabledDuration(now, null));
        billingEvents.add(createRealEvent(now.minusDays(1), subscription1));
        billingEvents.add(createRealEvent(now.plusDays(1), subscription1));
        
        SortedSet<BillingEvent> results = odc.createNewEvents( disabledDuration, billingEvents,  account, subscription1);
        
        assertEquals(results.size(), 1);
        assertEquals(results.first().getEffectiveDate(), now);
        assertEquals(results.first().getRecurringPrice(), BigDecimal.ZERO);
        assertEquals(results.first().getTransitionType(), SubscriptionTransitionType.CANCEL);
    }

    // Open with no previous event (only following)
    // -----[----X-----------------------------
    @Test
    public void testCreateNewEventsOpenFollow() {
        DateTime now = clock.getUTCNow();
        List<DisabledDuration> disabledDuration = new ArrayList<BlockingCalculator.DisabledDuration>();
        SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();
        
        disabledDuration.add(new DisabledDuration(now, null));
        billingEvents.add(createRealEvent(now.plusDays(1), subscription1));
        
        SortedSet<BillingEvent> results = odc.createNewEvents( disabledDuration, billingEvents,  account, subscription1);
        
        assertEquals(results.size(), 0);
   }

    // Closed duration with a single previous event
    // --X--[------------]---------------------
        @Test
    public void testCreateNewEventsClosedPrev() {
            DateTime now = clock.getUTCNow();
            List<DisabledDuration> disabledDuration = new ArrayList<BlockingCalculator.DisabledDuration>();
            SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();
            
            disabledDuration.add(new DisabledDuration(now, now.plusDays(2)));
            billingEvents.add(createRealEvent(now.minusDays(1), subscription1));
            
            SortedSet<BillingEvent> results = odc.createNewEvents( disabledDuration, billingEvents,  account, subscription1);
            
            assertEquals(results.size(), 2);
            assertEquals(results.first().getEffectiveDate(), now);
            assertEquals(results.first().getRecurringPrice(), BigDecimal.ZERO);
            assertEquals(results.first().getTransitionType(), SubscriptionTransitionType.CANCEL);
            assertEquals(results.last().getEffectiveDate(), now.plusDays(2));
            assertEquals(results.last().getRecurringPrice(), billingEvents.first().getRecurringPrice());
            assertEquals(results.last().getTransitionType(), SubscriptionTransitionType.RE_CREATE);
    }

    // Closed duration with a previous event and in-between event
    // --X--[------Y-----]---------------------
    @Test
    public void testCreateNewEventsClosedPrevBetw() {
        DateTime now = clock.getUTCNow();
        List<DisabledDuration> disabledDuration = new ArrayList<BlockingCalculator.DisabledDuration>();
        SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();
        
        disabledDuration.add(new DisabledDuration(now, now.plusDays(2)));
        billingEvents.add(createRealEvent(now.minusDays(1), subscription1));
        billingEvents.add(createRealEvent(now.plusDays(1), subscription1));
        
        SortedSet<BillingEvent> results = odc.createNewEvents( disabledDuration, billingEvents,  account, subscription1);
        
        assertEquals(results.size(), 2);
        assertEquals(results.first().getEffectiveDate(), now);
        assertEquals(results.first().getRecurringPrice(), BigDecimal.ZERO);
        assertEquals(results.first().getTransitionType(), SubscriptionTransitionType.CANCEL);
        assertEquals(results.last().getEffectiveDate(), now.plusDays(2));
        assertEquals(results.last().getRecurringPrice(), billingEvents.first().getRecurringPrice());
        assertEquals(results.last().getTransitionType(), SubscriptionTransitionType.RE_CREATE);
    }

    // Closed duration with a previous event and in-between event and following
    // --X--[------Y-----]-------Z-------------
    @Test
    public void testCreateNewEventsClosedPrevBetwNext() {
        DateTime now = clock.getUTCNow();
        List<DisabledDuration> disabledDuration = new ArrayList<BlockingCalculator.DisabledDuration>();
        SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();
        
        disabledDuration.add(new DisabledDuration(now, now.plusDays(2)));
        billingEvents.add(createRealEvent(now.minusDays(1), subscription1));
        billingEvents.add(createRealEvent(now.plusDays(1), subscription1));
        billingEvents.add(createRealEvent(now.plusDays(3), subscription1));
        
        SortedSet<BillingEvent> results = odc.createNewEvents( disabledDuration, billingEvents,  account, subscription1);
        
        assertEquals(results.size(), 2);
        assertEquals(results.first().getEffectiveDate(), now);
        assertEquals(results.first().getRecurringPrice(), BigDecimal.ZERO);
        assertEquals(results.first().getTransitionType(), SubscriptionTransitionType.CANCEL);
        assertEquals(results.last().getEffectiveDate(), now.plusDays(2));
        assertEquals(results.last().getRecurringPrice(), billingEvents.first().getRecurringPrice());
        assertEquals(results.last().getTransitionType(), SubscriptionTransitionType.RE_CREATE);
   }

    // Closed with no previous event but in-between events
    // -----[------Y-----]---------------------
    @Test
    public void testCreateNewEventsClosedBetwn() {
        DateTime now = clock.getUTCNow();
        List<DisabledDuration> disabledDuration = new ArrayList<BlockingCalculator.DisabledDuration>();
        SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();
        
        disabledDuration.add(new DisabledDuration(now, now.plusDays(2)));
        billingEvents.add(createRealEvent(now.plusDays(1), subscription1));
        
        SortedSet<BillingEvent> results = odc.createNewEvents( disabledDuration, billingEvents,  account, subscription1);
        
        assertEquals(results.size(), 1);
        assertEquals(results.last().getEffectiveDate(), now.plusDays(2));
        assertEquals(results.last().getRecurringPrice(), billingEvents.first().getRecurringPrice());
        assertEquals(results.last().getTransitionType(), SubscriptionTransitionType.RE_CREATE);
    }

    // Closed with no previous event but in-between events and following
    // -----[------Y-----]-------Z-------------
    @Test
    public void testCreateNewEventsClosedBetweenFollow() {
        DateTime now = clock.getUTCNow();
        List<DisabledDuration> disabledDuration = new ArrayList<BlockingCalculator.DisabledDuration>();
        SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();
        
        disabledDuration.add(new DisabledDuration(now, now.plusDays(2)));
        billingEvents.add(createRealEvent(now.plusDays(1), subscription1));
        
        SortedSet<BillingEvent> results = odc.createNewEvents( disabledDuration, billingEvents,  account, subscription1);
        
        assertEquals(results.size(), 1);
        assertEquals(results.last().getEffectiveDate(), now.plusDays(2));
        assertEquals(results.last().getRecurringPrice(), billingEvents.first().getRecurringPrice());
        assertEquals(results.last().getTransitionType(), SubscriptionTransitionType.RE_CREATE);
    }

    // Closed duration with only following
    // -----[------------]-------Z-------------
    @Test
    public void testCreateNewEventsClosedFollow() {
        DateTime now = clock.getUTCNow();
        List<DisabledDuration> disabledDuration = new ArrayList<BlockingCalculator.DisabledDuration>();
        SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();
        
        disabledDuration.add(new DisabledDuration(now, now.plusDays(2)));
        billingEvents.add(createRealEvent(now.plusDays(3), subscription1));
        
        SortedSet<BillingEvent> results = odc.createNewEvents( disabledDuration, billingEvents,  account, subscription1);
        
        assertEquals(results.size(), 0);
     }

    @Test
    public void testPrecedingBillingEventForSubscription() {
        DateTime now = new DateTime();
        
        SortedSet<BillingEvent> events = new TreeSet<BillingEvent>();

        events.add(createRealEvent(now.minusDays(10), subscription1));
        events.add(createRealEvent(now.minusDays(6), subscription1));
        events.add(createRealEvent(now.minusDays(5), subscription1));
        events.add(createRealEvent(now.minusDays(1), subscription1));
        
        BillingEvent minus11 = odc.precedingBillingEventForSubscription(now.minusDays(11), events, subscription1);
        assertNull(minus11);
        
        BillingEvent minus5andAHalf= odc.precedingBillingEventForSubscription(now.minusDays(5).minusHours(12), events, subscription1);
        assertNotNull(minus5andAHalf);
        assertEquals(minus5andAHalf.getEffectiveDate(), now.minusDays(6));
      
        
    }
    
    protected BillingEvent createRealEvent(DateTime effectiveDate, Subscription subscription) {
        final Account account = this.account;
        final int billCycleDay = 1;
        final PlanPhase planPhase = new MockPlanPhase();
        final Plan plan = new MockPlan();
        final BigDecimal fixedPrice = BigDecimal.TEN;
        final BigDecimal recurringPrice = BigDecimal.TEN;
        final Currency currency = Currency.USD;
        final String description = "";
        final BillingModeType billingModeType = BillingModeType.IN_ADVANCE;
        final BillingPeriod billingPeriod = BillingPeriod.MONTHLY;
        final SubscriptionTransitionType type = SubscriptionTransitionType.CHANGE;
        final Long totalOrdering = 0L; 

        return new DefaultBillingEvent(account, subscription, effectiveDate, plan, planPhase,
                fixedPrice, recurringPrice, currency,
                billingPeriod, billCycleDay, billingModeType,
                description, totalOrdering, type);
    }


    @Test
    public void testFilter() {
        SortedSet<BillingEvent> events = new TreeSet<BillingEvent>();
        
        events.add(createBillingEvent(subscription1));
        events.add(createBillingEvent(subscription1));
        events.add(createBillingEvent(subscription1));
        events.add(createBillingEvent(subscription2));
        
        SortedSet<BillingEvent> result1 = odc.filter(events, subscription1);
        SortedSet<BillingEvent> result2 = odc.filter(events, subscription2);
        SortedSet<BillingEvent> result3 = odc.filter(events, subscription3);
        
        assertEquals(result1.size(), 3);
        assertEquals(result1.first().getSubscription(), subscription1);
        assertEquals(result1.last().getSubscription(), subscription1);
        assertEquals(result2.size(), 1);
        assertEquals(result2.first().getSubscription(), subscription2);
        assertEquals(result3.size(), 0);
    }

    @Test
    public void testCreateNewDisableEvent() {
        DateTime now = clock.getUTCNow();
        BillingEvent event = new MockBillingEvent();
        
        BillingEvent result = odc.createNewDisableEvent(now, event);
        assertEquals(result.getBillCycleDay(),event.getBillCycleDay());
        assertEquals(result.getEffectiveDate(), now);
        assertEquals(result.getPlanPhase(), event.getPlanPhase());
        assertEquals(result.getPlan(), event.getPlan());
        assertEquals(result.getFixedPrice(),BigDecimal.ZERO);
        assertEquals(result.getRecurringPrice(), BigDecimal.ZERO);
        assertEquals(result.getCurrency(), event.getCurrency());
        assertEquals(result.getDescription(), "");
        assertEquals(result.getBillingMode(), event.getBillingMode());
        assertEquals(result.getBillingPeriod(), event.getBillingPeriod());
        assertEquals(result.getTransitionType(),  SubscriptionTransitionType.CANCEL);
        assertEquals(result.getTotalOrdering(), new Long(0));
    }

    @Test
    public void testCreateNewReenableEvent() {
        DateTime now = clock.getUTCNow();
        BillingEvent event = new MockBillingEvent();
        
        BillingEvent result = odc.createNewReenableEvent(now, event);
        assertEquals(result.getBillCycleDay(),event.getBillCycleDay());
        assertEquals(result.getEffectiveDate(), now);
        assertEquals(result.getPlanPhase(), event.getPlanPhase());
        assertEquals(result.getPlan(), event.getPlan());
        assertEquals(result.getFixedPrice(),event.getFixedPrice());
        assertEquals(result.getRecurringPrice(), event.getRecurringPrice());
        assertEquals(result.getCurrency(), event.getCurrency());
        assertEquals(result.getDescription(), "");
        assertEquals(result.getBillingMode(), event.getBillingMode());
        assertEquals(result.getBillingPeriod(), event.getBillingPeriod());
        assertEquals(result.getTransitionType(),  SubscriptionTransitionType.RE_CREATE);
        assertEquals(result.getTotalOrdering(), new Long(0));
    }
    
    private class MockBillingEvent extends DefaultBillingEvent {
        public MockBillingEvent() {
            super(account, subscription1, clock.getUTCNow(), null, null, BigDecimal.ZERO, BigDecimal.TEN, Currency.USD, BillingPeriod.ANNUAL,
                    4, BillingModeType.IN_ADVANCE, "", 3L, SubscriptionTransitionType.CREATE);
        }        
    }

    @Test
    public void testCreateBundleSubscriptionMap() {
        SortedSet<BillingEvent> events = new TreeSet<BillingEvent>();
        events.add(createBillingEvent(subscription1));
        events.add(createBillingEvent(subscription2));
        events.add(createBillingEvent(subscription3));
        events.add(createBillingEvent(subscription4));
        
        Hashtable<UUID,List<Subscription>> map = odc.createBundleSubscriptionMap(events);
        
        assertNotNull(map);
        assertEquals(map.keySet().size(),2);
        assertEquals(map.get(bundleId1).size(), 3);
        assertEquals(map.get(bundleId2).size(), 1);
        
    }

    private BillingEvent createBillingEvent(Subscription subscription) {
        BillingEvent result =  BrainDeadProxyFactory.createBrainDeadProxyFor(BillingEvent.class, Comparable.class);
        ((ZombieControl)result).addResult("getSubscription", subscription);
        ((ZombieControl)result).addResult("compareTo", 1);
        return result;
    }

    @Test
    public void testCreateDisablePairs() {
        SortedSet<BlockingState> blockingEvents;
        UUID ovdId = UUID.randomUUID();
        DateTime now = clock.getUTCNow();
        
        //simple events open clear -> disabled
        blockingEvents = new TreeSet<BlockingState>();
        blockingEvents.add(new DefaultBlockingState(ovdId,CLEAR_BUNDLE, Type.SUBSCRIPTION_BUNDLE,"test", false, false, false, now));
        blockingEvents.add(new DefaultBlockingState(ovdId,DISABLED_BUNDLE, Type.SUBSCRIPTION_BUNDLE, "test", true, true, true,now.plusDays(1)));
        
        List<DisabledDuration> pairs = odc.createBlockingDurations(blockingEvents);
        assertEquals(pairs.size(), 1);
        assertNotNull(pairs.get(0).getStart());
        assertEquals(pairs.get(0).getStart(),now.plusDays(1));
        assertNull(pairs.get(0).getEnd());
        
        //simple events closed clear -> disabled
        blockingEvents = new TreeSet<BlockingState>();
        blockingEvents.add(new DefaultBlockingState(ovdId,CLEAR_BUNDLE, Type.SUBSCRIPTION_BUNDLE,"test", false, false, false,now));
        blockingEvents.add(new DefaultBlockingState(ovdId,DISABLED_BUNDLE, Type.SUBSCRIPTION_BUNDLE, "test", true, true, true,now.plusDays(1)));
        blockingEvents.add(new DefaultBlockingState(ovdId,CLEAR_BUNDLE, Type.SUBSCRIPTION_BUNDLE,"test", false, false, false,now.plusDays(2)));
        
        pairs = odc.createBlockingDurations(blockingEvents);
        assertEquals(pairs.size(), 1);
        assertNotNull(pairs.get(0).getStart());
        assertEquals(pairs.get(0).getStart(),now.plusDays(1));
        assertNotNull(pairs.get(0).getEnd());
        assertEquals(pairs.get(0).getEnd(),now.plusDays(2));

        //simple BUNDLE events closed clear -> disabled
        blockingEvents = new TreeSet<BlockingState>();
        blockingEvents.add(new DefaultBlockingState(ovdId,CLEAR_BUNDLE, Type.SUBSCRIPTION_BUNDLE,"test", false, false, false,now));
        blockingEvents.add(new DefaultBlockingState(ovdId,DISABLED_BUNDLE, Type.SUBSCRIPTION_BUNDLE, "test", true, true, true,now.plusDays(1)));
        blockingEvents.add(new DefaultBlockingState(ovdId,CLEAR_BUNDLE, Type.SUBSCRIPTION_BUNDLE,"test", false, false, false,now.plusDays(2)));
        
        pairs = odc.createBlockingDurations(blockingEvents);
        assertEquals(pairs.size(), 1);
        assertNotNull(pairs.get(0).getStart());
        assertEquals(pairs.get(0).getStart(),now.plusDays(1));
        assertNotNull(pairs.get(0).getEnd());
        assertEquals(pairs.get(0).getEnd(),now.plusDays(2));
        
        
        //two or more disableds in a row
        blockingEvents = new TreeSet<BlockingState>();
        blockingEvents.add(new DefaultBlockingState(ovdId,CLEAR_BUNDLE, Type.SUBSCRIPTION_BUNDLE,"test", false, false, false,now));
        blockingEvents.add(new DefaultBlockingState(ovdId,DISABLED_BUNDLE, Type.SUBSCRIPTION_BUNDLE, "test", true, true, true,now.plusDays(1)));
        blockingEvents.add(new DefaultBlockingState(ovdId,DISABLED_BUNDLE, Type.SUBSCRIPTION_BUNDLE, "test", true, true, true,now.plusDays(2)));
        blockingEvents.add(new DefaultBlockingState(ovdId,CLEAR_BUNDLE, Type.SUBSCRIPTION_BUNDLE,"test", false, false, false,now.plusDays(3)));
        
        pairs = odc.createBlockingDurations(blockingEvents);
        assertEquals(pairs.size(), 1);
        assertNotNull(pairs.get(0).getStart());
        assertEquals(pairs.get(0).getStart(),now.plusDays(1));
        assertNotNull(pairs.get(0).getEnd());
        assertEquals(pairs.get(0).getEnd(),now.plusDays(3));

       
        blockingEvents = new TreeSet<BlockingState>();
        blockingEvents.add(new DefaultBlockingState(ovdId,CLEAR_BUNDLE, Type.SUBSCRIPTION_BUNDLE,"test", false, false, false,now));
        blockingEvents.add(new DefaultBlockingState(ovdId,DISABLED_BUNDLE, Type.SUBSCRIPTION_BUNDLE, "test", true, true, true,now.plusDays(1)));
        blockingEvents.add(new DefaultBlockingState(ovdId,DISABLED_BUNDLE, Type.SUBSCRIPTION_BUNDLE, "test", true, true, true,now.plusDays(2)));
        blockingEvents.add(new DefaultBlockingState(ovdId,DISABLED_BUNDLE, Type.SUBSCRIPTION_BUNDLE, "test", true, true, true,now.plusDays(3)));
        blockingEvents.add(new DefaultBlockingState(ovdId,CLEAR_BUNDLE, Type.SUBSCRIPTION_BUNDLE,"test", false, false, false,now.plusDays(4)));
        
        pairs = odc.createBlockingDurations(blockingEvents);
        assertEquals(pairs.size(), 1);
        assertNotNull(pairs.get(0).getStart());
        assertEquals(pairs.get(0).getStart(),now.plusDays(1));
        assertNotNull(pairs.get(0).getEnd());
        assertEquals(pairs.get(0).getEnd(),now.plusDays(4));
  
    }
}
