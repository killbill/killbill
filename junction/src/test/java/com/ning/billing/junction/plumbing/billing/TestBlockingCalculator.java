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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.mockito.Mockito;
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
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

public class TestBlockingCalculator {
    private static final String DISABLED_BUNDLE = "disabled-bundle";
    private static final String CLEAR_BUNDLE = "clear-bundle";

    private BlockingApi blockingApi;
    private Account account;
    private Subscription subscription1;
    private Subscription subscription2;
    private Subscription subscription3;
    private Subscription subscription4;
    private final UUID bundleId1 = UUID.randomUUID();
    private final UUID bundleId2 = UUID.randomUUID();
    private Clock clock;
    private BlockingCalculator odc;

    @BeforeClass
    public void setUpBeforeClass() throws Exception {
        clock = new ClockMock();

        final Injector i = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                blockingApi = Mockito.mock(BlockingApi.class);
                account = Mockito.mock(Account.class);
                subscription1 = Mockito.mock(Subscription.class);
                subscription2 = Mockito.mock(Subscription.class);
                subscription3 = Mockito.mock(Subscription.class);
                subscription4 = Mockito.mock(Subscription.class);
                Mockito.when(account.getId()).thenReturn(UUID.randomUUID());
                Mockito.when(subscription1.getBundleId()).thenReturn(bundleId1);
                Mockito.when(subscription2.getBundleId()).thenReturn(bundleId1);
                Mockito.when(subscription3.getBundleId()).thenReturn(bundleId1);
                Mockito.when(subscription4.getBundleId()).thenReturn(bundleId2);
                Mockito.when(subscription1.getId()).thenReturn(UUID.randomUUID());
                Mockito.when(subscription2.getId()).thenReturn(UUID.randomUUID());
                Mockito.when(subscription3.getId()).thenReturn(UUID.randomUUID());
                Mockito.when(subscription4.getId()).thenReturn(UUID.randomUUID());

                bind(BlockingStateDao.class).toInstance(Mockito.mock(BlockingStateDao.class));
                bind(BlockingApi.class).toInstance(blockingApi);
            }

        });
        odc = i.getInstance(BlockingCalculator.class);

    }

    // S1-S2-S3 subscriptions in B1
    // B1 -----[--------]
    // S1 --A-------------------------------------
    // S2 --B------C------------------------------
    // S3 ------------------D---------------------

    //Result
    // S1 --A--[-------]--------------------------
    // S2 --B--[-------]--------------------------
    // S3 ------------------D---------------------
    @Test
    public void testInsertBlockingEvents() {
        final DateTime now = clock.getUTCNow();
        final List<DisabledDuration> disabledDuration = new ArrayList<BlockingCalculator.DisabledDuration>();
        final SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();

        disabledDuration.add(new DisabledDuration(now, null));
        final BillingEvent A = createRealEvent(now.minusDays(1).minusHours(1), subscription1);
        final BillingEvent B = createRealEvent(now.minusDays(1), subscription2);
        final BillingEvent C = createRealEvent(now.plusDays(1), subscription2);
        final BillingEvent D = createRealEvent(now.plusDays(3), subscription3);
        billingEvents.add(A);
        billingEvents.add(B);
        billingEvents.add(C);
        billingEvents.add(D);

        final SortedSet<BlockingState> blockingStates = new TreeSet<BlockingState>();
        blockingStates.add(new DefaultBlockingState(bundleId1, DISABLED_BUNDLE, Blockable.Type.SUBSCRIPTION_BUNDLE, "test", true, true, true, now));
        blockingStates.add(new DefaultBlockingState(bundleId1, CLEAR_BUNDLE, Blockable.Type.SUBSCRIPTION_BUNDLE, "test", false, false, false, now.plusDays(2)));

        Mockito.when(blockingApi.getBlockingHistory(Mockito.<Blockable>anyObject())).thenReturn(blockingStates);

        odc.insertBlockingEvents(billingEvents);

        assertEquals(billingEvents.size(), 7);

        final SortedSet<BillingEvent> s1Events = odc.filter(billingEvents, subscription1);
        final Iterator<BillingEvent> it1 = s1Events.iterator();
        assertEquals(it1.next(), A);
        assertEquals(it1.next().getTransitionType(), SubscriptionTransitionType.CANCEL);
        assertEquals(it1.next().getTransitionType(), SubscriptionTransitionType.RE_CREATE);

        final SortedSet<BillingEvent> s2Events = odc.filter(billingEvents, subscription2);
        final Iterator<BillingEvent> it2 = s2Events.iterator();
        assertEquals(it2.next(), B);
        assertEquals(it2.next().getTransitionType(), SubscriptionTransitionType.CANCEL);
        assertEquals(it2.next().getTransitionType(), SubscriptionTransitionType.RE_CREATE);

        final SortedSet<BillingEvent> s3Events = odc.filter(billingEvents, subscription3);
        final Iterator<BillingEvent> it3 = s3Events.iterator();
        assertEquals(it3.next(), D);
    }

    // Open ended duration with a previous event
    // --X--[----------------------------------
    @Test
    public void testEventsToRemoveOpenPrev() {
        final DateTime now = clock.getUTCNow();
        final List<DisabledDuration> disabledDuration = new ArrayList<BlockingCalculator.DisabledDuration>();
        final SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();

        disabledDuration.add(new DisabledDuration(now, null));
        billingEvents.add(createRealEvent(now.minusDays(1), subscription1));

        final SortedSet<BillingEvent> results = odc.eventsToRemove(disabledDuration, billingEvents, subscription1);

        assertEquals(results.size(), 0);
    }

    // Open with previous and following events
    // --X--[----Y-----------------------------
    @Test
    public void testEventsToRemoveOpenPrevFollow() {
        final DateTime now = clock.getUTCNow();
        final List<DisabledDuration> disabledDuration = new ArrayList<BlockingCalculator.DisabledDuration>();
        final SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();

        disabledDuration.add(new DisabledDuration(now, null));
        final BillingEvent e1 = createRealEvent(now.minusDays(1), subscription1);
        final BillingEvent e2 = createRealEvent(now.plusDays(1), subscription1);
        billingEvents.add(e1);
        billingEvents.add(e2);

        final SortedSet<BillingEvent> results = odc.eventsToRemove(disabledDuration, billingEvents, subscription1);

        assertEquals(results.size(), 1);
        assertEquals(results.first(), e2);
    }

    // Open with no previous event (only following)
    // -----[----X-----------------------------
    @Test
    public void testEventsToRemoveOpenFollow() {
        final DateTime now = clock.getUTCNow();
        final List<DisabledDuration> disabledDuration = new ArrayList<BlockingCalculator.DisabledDuration>();
        final SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();

        disabledDuration.add(new DisabledDuration(now, null));
        final BillingEvent e1 = createRealEvent(now.plusDays(1), subscription1);
        billingEvents.add(e1);

        final SortedSet<BillingEvent> results = odc.eventsToRemove(disabledDuration, billingEvents, subscription1);

        assertEquals(results.size(), 1);
        assertEquals(results.first(), e1);
    }

    // Closed duration with a single previous event
    // --X--[------------]---------------------
    @Test
    public void testEventsToRemoveClosedPrev() {
        final DateTime now = clock.getUTCNow();
        final List<DisabledDuration> disabledDuration = new ArrayList<BlockingCalculator.DisabledDuration>();
        final SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();

        disabledDuration.add(new DisabledDuration(now, now.plusDays(2)));
        final BillingEvent e1 = createRealEvent(now.minusDays(1), subscription1);
        billingEvents.add(e1);

        final SortedSet<BillingEvent> results = odc.eventsToRemove(disabledDuration, billingEvents, subscription1);

        assertEquals(results.size(), 0);
    }

    // Closed duration with a previous event and in-between event
    // --X--[------Y-----]---------------------
    @Test
    public void testEventsToRemoveClosedPrevBetw() {
        final DateTime now = clock.getUTCNow();
        final List<DisabledDuration> disabledDuration = new ArrayList<BlockingCalculator.DisabledDuration>();
        final SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();

        disabledDuration.add(new DisabledDuration(now, now.plusDays(2)));
        final BillingEvent e1 = createRealEvent(now.minusDays(1), subscription1);
        final BillingEvent e2 = createRealEvent(now.plusDays(1), subscription1);
        billingEvents.add(e1);
        billingEvents.add(e2);

        final SortedSet<BillingEvent> results = odc.eventsToRemove(disabledDuration, billingEvents, subscription1);

        assertEquals(results.size(), 1);
        assertEquals(results.first(), e2);
    }

    // Closed duration with a previous event and in-between event and following
    // --X--[------Y-----]-------Z-------------
    @Test
    public void testEventsToRemoveClosedPrevBetwNext() {
        final DateTime now = clock.getUTCNow();
        final List<DisabledDuration> disabledDuration = new ArrayList<BlockingCalculator.DisabledDuration>();
        final SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();

        disabledDuration.add(new DisabledDuration(now, now.plusDays(2)));
        final BillingEvent e1 = createRealEvent(now.minusDays(1), subscription1);
        final BillingEvent e2 = createRealEvent(now.plusDays(1), subscription1);
        final BillingEvent e3 = createRealEvent(now.plusDays(3), subscription1);
        billingEvents.add(e1);
        billingEvents.add(e2);
        billingEvents.add(e3);

        final SortedSet<BillingEvent> results = odc.eventsToRemove(disabledDuration, billingEvents, subscription1);

        assertEquals(results.size(), 1);
        assertEquals(results.first(), e2);
    }

    // Closed with no previous event but in-between events
    // -----[------Y-----]---------------------
    @Test
    public void testEventsToRemoveClosedBetwn() {
        final DateTime now = clock.getUTCNow();
        final List<DisabledDuration> disabledDuration = new ArrayList<BlockingCalculator.DisabledDuration>();
        final SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();

        disabledDuration.add(new DisabledDuration(now, now.plusDays(2)));
        final BillingEvent e2 = createRealEvent(now.plusDays(1), subscription1);
        billingEvents.add(e2);

        final SortedSet<BillingEvent> results = odc.eventsToRemove(disabledDuration, billingEvents, subscription1);

        assertEquals(results.size(), 1);
        assertEquals(results.first(), e2);
    }

    // Closed with no previous event but in-between events and following
    // -----[------Y-----]-------Z-------------
    @Test
    public void testEventsToRemoveClosedBetweenFollow() {
        final DateTime now = clock.getUTCNow();
        final List<DisabledDuration> disabledDuration = new ArrayList<BlockingCalculator.DisabledDuration>();
        final SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();

        disabledDuration.add(new DisabledDuration(now, now.plusDays(2)));

        final BillingEvent e2 = createRealEvent(now.plusDays(1), subscription1);
        final BillingEvent e3 = createRealEvent(now.plusDays(3), subscription1);
        billingEvents.add(e2);
        billingEvents.add(e3);

        final SortedSet<BillingEvent> results = odc.eventsToRemove(disabledDuration, billingEvents, subscription1);

        assertEquals(results.size(), 1);
        assertEquals(results.first(), e2);
    }

    // Closed duration with only following
    // -----[------------]-------Z-------------
    @Test
    public void testEventsToRemoveClosedFollow() {
        final DateTime now = clock.getUTCNow();
        final List<DisabledDuration> disabledDuration = new ArrayList<BlockingCalculator.DisabledDuration>();
        final SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();

        disabledDuration.add(new DisabledDuration(now, now.plusDays(2)));

        final BillingEvent e3 = createRealEvent(now.plusDays(3), subscription1);

        billingEvents.add(e3);

        final SortedSet<BillingEvent> results = odc.eventsToRemove(disabledDuration, billingEvents, subscription1);

        assertEquals(results.size(), 0);
    }

    // Open ended duration with a previous event
    // --X--[----------------------------------
    @Test
    public void testCreateNewEventsOpenPrev() {
        final DateTime now = clock.getUTCNow();
        final List<DisabledDuration> disabledDuration = new ArrayList<BlockingCalculator.DisabledDuration>();
        final SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();

        disabledDuration.add(new DisabledDuration(now, null));
        billingEvents.add(createRealEvent(now.minusDays(1), subscription1));

        final SortedSet<BillingEvent> results = odc.createNewEvents(disabledDuration, billingEvents, account, subscription1);

        assertEquals(results.size(), 1);
        assertEquals(results.first().getEffectiveDate(), now);
        assertEquals(results.first().getRecurringPrice(), BigDecimal.ZERO);
        assertEquals(results.first().getTransitionType(), SubscriptionTransitionType.CANCEL);
    }

    // Open with previous and following events
    // --X--[----Y-----------------------------
    @Test
    public void testCreateNewEventsOpenPrevFollow() {
        final DateTime now = clock.getUTCNow();
        final List<DisabledDuration> disabledDuration = new ArrayList<BlockingCalculator.DisabledDuration>();
        final SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();

        disabledDuration.add(new DisabledDuration(now, null));
        billingEvents.add(createRealEvent(now.minusDays(1), subscription1));
        billingEvents.add(createRealEvent(now.plusDays(1), subscription1));

        final SortedSet<BillingEvent> results = odc.createNewEvents(disabledDuration, billingEvents, account, subscription1);

        assertEquals(results.size(), 1);
        assertEquals(results.first().getEffectiveDate(), now);
        assertEquals(results.first().getRecurringPrice(), BigDecimal.ZERO);
        assertEquals(results.first().getTransitionType(), SubscriptionTransitionType.CANCEL);
    }

    // Open with no previous event (only following)
    // -----[----X-----------------------------
    @Test
    public void testCreateNewEventsOpenFollow() {
        final DateTime now = clock.getUTCNow();
        final List<DisabledDuration> disabledDuration = new ArrayList<BlockingCalculator.DisabledDuration>();
        final SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();

        disabledDuration.add(new DisabledDuration(now, null));
        billingEvents.add(createRealEvent(now.plusDays(1), subscription1));

        final SortedSet<BillingEvent> results = odc.createNewEvents(disabledDuration, billingEvents, account, subscription1);

        assertEquals(results.size(), 0);
    }

    // Closed duration with a single previous event
    // --X--[------------]---------------------
    @Test
    public void testCreateNewEventsClosedPrev() {
        final DateTime now = clock.getUTCNow();
        final List<DisabledDuration> disabledDuration = new ArrayList<BlockingCalculator.DisabledDuration>();
        final SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();

        disabledDuration.add(new DisabledDuration(now, now.plusDays(2)));
        billingEvents.add(createRealEvent(now.minusDays(1), subscription1));

        final SortedSet<BillingEvent> results = odc.createNewEvents(disabledDuration, billingEvents, account, subscription1);

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
        final DateTime now = clock.getUTCNow();
        final List<DisabledDuration> disabledDuration = new ArrayList<BlockingCalculator.DisabledDuration>();
        final SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();

        disabledDuration.add(new DisabledDuration(now, now.plusDays(2)));
        billingEvents.add(createRealEvent(now.minusDays(1), subscription1));
        billingEvents.add(createRealEvent(now.plusDays(1), subscription1));

        final SortedSet<BillingEvent> results = odc.createNewEvents(disabledDuration, billingEvents, account, subscription1);

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
        final DateTime now = clock.getUTCNow();
        final List<DisabledDuration> disabledDuration = new ArrayList<BlockingCalculator.DisabledDuration>();
        final SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();

        disabledDuration.add(new DisabledDuration(now, now.plusDays(2)));
        billingEvents.add(createRealEvent(now.minusDays(1), subscription1));
        billingEvents.add(createRealEvent(now.plusDays(1), subscription1));
        billingEvents.add(createRealEvent(now.plusDays(3), subscription1));

        final SortedSet<BillingEvent> results = odc.createNewEvents(disabledDuration, billingEvents, account, subscription1);

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
        final DateTime now = clock.getUTCNow();
        final List<DisabledDuration> disabledDuration = new ArrayList<BlockingCalculator.DisabledDuration>();
        final SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();

        disabledDuration.add(new DisabledDuration(now, now.plusDays(2)));
        billingEvents.add(createRealEvent(now.plusDays(1), subscription1));

        final SortedSet<BillingEvent> results = odc.createNewEvents(disabledDuration, billingEvents, account, subscription1);

        assertEquals(results.size(), 1);
        assertEquals(results.last().getEffectiveDate(), now.plusDays(2));
        assertEquals(results.last().getRecurringPrice(), billingEvents.first().getRecurringPrice());
        assertEquals(results.last().getTransitionType(), SubscriptionTransitionType.RE_CREATE);
    }

    // Closed with no previous event but in-between events and following
    // -----[------Y-----]-------Z-------------
    @Test
    public void testCreateNewEventsClosedBetweenFollow() {
        final DateTime now = clock.getUTCNow();
        final List<DisabledDuration> disabledDuration = new ArrayList<BlockingCalculator.DisabledDuration>();
        final SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();

        disabledDuration.add(new DisabledDuration(now, now.plusDays(2)));
        billingEvents.add(createRealEvent(now.plusDays(1), subscription1));

        final SortedSet<BillingEvent> results = odc.createNewEvents(disabledDuration, billingEvents, account, subscription1);

        assertEquals(results.size(), 1);
        assertEquals(results.last().getEffectiveDate(), now.plusDays(2));
        assertEquals(results.last().getRecurringPrice(), billingEvents.first().getRecurringPrice());
        assertEquals(results.last().getTransitionType(), SubscriptionTransitionType.RE_CREATE);
    }

    // Closed duration with only following
    // -----[------------]-------Z-------------
    @Test
    public void testCreateNewEventsClosedFollow() {
        final DateTime now = clock.getUTCNow();
        final List<DisabledDuration> disabledDuration = new ArrayList<BlockingCalculator.DisabledDuration>();
        final SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();

        disabledDuration.add(new DisabledDuration(now, now.plusDays(2)));
        billingEvents.add(createRealEvent(now.plusDays(3), subscription1));

        final SortedSet<BillingEvent> results = odc.createNewEvents(disabledDuration, billingEvents, account, subscription1);

        assertEquals(results.size(), 0);
    }

    @Test
    public void testPrecedingBillingEventForSubscription() {
        final DateTime now = new DateTime();

        final SortedSet<BillingEvent> events = new TreeSet<BillingEvent>();

        events.add(createRealEvent(now.minusDays(10), subscription1));
        events.add(createRealEvent(now.minusDays(6), subscription1));
        events.add(createRealEvent(now.minusDays(5), subscription1));
        events.add(createRealEvent(now.minusDays(1), subscription1));

        final BillingEvent minus11 = odc.precedingBillingEventForSubscription(now.minusDays(11), events, subscription1);
        assertNull(minus11);

        final BillingEvent minus5andAHalf = odc.precedingBillingEventForSubscription(now.minusDays(5).minusHours(12), events, subscription1);
        assertNotNull(minus5andAHalf);
        assertEquals(minus5andAHalf.getEffectiveDate(), now.minusDays(6));


    }

    protected BillingEvent createRealEvent(final DateTime effectiveDate, final Subscription subscription) {
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
        final DateTimeZone tz = DateTimeZone.UTC;

        return new DefaultBillingEvent(account, subscription, effectiveDate, plan, planPhase,
                                       fixedPrice, recurringPrice, currency,
                                       billingPeriod, billCycleDay, billingModeType,
                                       description, totalOrdering, type, tz);
    }

    @Test
    public void testFilter() {
        final SortedSet<BillingEvent> events = new TreeSet<BillingEvent>();

        events.add(createBillingEvent(subscription1));
        events.add(createBillingEvent(subscription1));
        events.add(createBillingEvent(subscription1));
        events.add(createBillingEvent(subscription2));

        final SortedSet<BillingEvent> result1 = odc.filter(events, subscription1);
        final SortedSet<BillingEvent> result2 = odc.filter(events, subscription2);
        final SortedSet<BillingEvent> result3 = odc.filter(events, subscription3);

        assertEquals(result1.size(), 3);
        assertEquals(result1.first().getSubscription(), subscription1);
        assertEquals(result1.last().getSubscription(), subscription1);
        assertEquals(result2.size(), 1);
        assertEquals(result2.first().getSubscription(), subscription2);
        assertEquals(result3.size(), 0);
    }

    @Test
    public void testCreateNewDisableEvent() {
        final DateTime now = clock.getUTCNow();
        final BillingEvent event = new MockBillingEvent();

        final BillingEvent result = odc.createNewDisableEvent(now, event);
        assertEquals(result.getBillCycleDay(), event.getBillCycleDay());
        assertEquals(result.getEffectiveDate(), now);
        assertEquals(result.getPlanPhase(), event.getPlanPhase());
        assertEquals(result.getPlan(), event.getPlan());
        assertEquals(result.getFixedPrice(), BigDecimal.ZERO);
        assertEquals(result.getRecurringPrice(), BigDecimal.ZERO);
        assertEquals(result.getCurrency(), event.getCurrency());
        assertEquals(result.getDescription(), "");
        assertEquals(result.getBillingMode(), event.getBillingMode());
        assertEquals(result.getBillingPeriod(), event.getBillingPeriod());
        assertEquals(result.getTransitionType(), SubscriptionTransitionType.CANCEL);
        assertEquals(result.getTotalOrdering(), new Long(0));
    }

    @Test
    public void testCreateNewReenableEvent() {
        final DateTime now = clock.getUTCNow();
        final BillingEvent event = new MockBillingEvent();

        final BillingEvent result = odc.createNewReenableEvent(now, event);
        assertEquals(result.getBillCycleDay(), event.getBillCycleDay());
        assertEquals(result.getEffectiveDate(), now);
        assertEquals(result.getPlanPhase(), event.getPlanPhase());
        assertEquals(result.getPlan(), event.getPlan());
        assertEquals(result.getFixedPrice(), event.getFixedPrice());
        assertEquals(result.getRecurringPrice(), event.getRecurringPrice());
        assertEquals(result.getCurrency(), event.getCurrency());
        assertEquals(result.getDescription(), "");
        assertEquals(result.getBillingMode(), event.getBillingMode());
        assertEquals(result.getBillingPeriod(), event.getBillingPeriod());
        assertEquals(result.getTransitionType(), SubscriptionTransitionType.RE_CREATE);
        assertEquals(result.getTotalOrdering(), new Long(0));
    }

    private class MockBillingEvent extends DefaultBillingEvent {
        public MockBillingEvent() {
            super(account, subscription1, clock.getUTCNow(), null, null, BigDecimal.ZERO, BigDecimal.TEN, Currency.USD, BillingPeriod.ANNUAL,
                  4, BillingModeType.IN_ADVANCE, "", 3L, SubscriptionTransitionType.CREATE, DateTimeZone.UTC);
        }
    }

    @Test
    public void testCreateBundleSubscriptionMap() {
        final SortedSet<BillingEvent> events = new TreeSet<BillingEvent>();
        events.add(createBillingEvent(subscription1));
        events.add(createBillingEvent(subscription2));
        events.add(createBillingEvent(subscription3));
        events.add(createBillingEvent(subscription4));

        final Hashtable<UUID, List<Subscription>> map = odc.createBundleSubscriptionMap(events);

        assertNotNull(map);
        assertEquals(map.keySet().size(), 2);
        assertEquals(map.get(bundleId1).size(), 3);
        assertEquals(map.get(bundleId2).size(), 1);

    }

    private BillingEvent createBillingEvent(final Subscription subscription) {
        final BillingEvent result = Mockito.mock(BillingEvent.class);
        Mockito.when(result.getSubscription()).thenReturn(subscription);
        Mockito.when(result.compareTo(Mockito.<BillingEvent>any())).thenReturn(1);
        return result;
    }

    @Test
    public void testCreateDisablePairs() {
        SortedSet<BlockingState> blockingEvents;
        final UUID ovdId = UUID.randomUUID();
        final DateTime now = clock.getUTCNow();

        //simple events open clear -> disabled
        blockingEvents = new TreeSet<BlockingState>();
        blockingEvents.add(new DefaultBlockingState(ovdId, CLEAR_BUNDLE, Type.SUBSCRIPTION_BUNDLE, "test", false, false, false, now));
        blockingEvents.add(new DefaultBlockingState(ovdId, DISABLED_BUNDLE, Type.SUBSCRIPTION_BUNDLE, "test", true, true, true, now.plusDays(1)));

        List<DisabledDuration> pairs = odc.createBlockingDurations(blockingEvents);
        assertEquals(pairs.size(), 1);
        assertNotNull(pairs.get(0).getStart());
        assertEquals(pairs.get(0).getStart(), now.plusDays(1));
        assertNull(pairs.get(0).getEnd());

        //simple events closed clear -> disabled
        blockingEvents = new TreeSet<BlockingState>();
        blockingEvents.add(new DefaultBlockingState(ovdId, CLEAR_BUNDLE, Type.SUBSCRIPTION_BUNDLE, "test", false, false, false, now));
        blockingEvents.add(new DefaultBlockingState(ovdId, DISABLED_BUNDLE, Type.SUBSCRIPTION_BUNDLE, "test", true, true, true, now.plusDays(1)));
        blockingEvents.add(new DefaultBlockingState(ovdId, CLEAR_BUNDLE, Type.SUBSCRIPTION_BUNDLE, "test", false, false, false, now.plusDays(2)));

        pairs = odc.createBlockingDurations(blockingEvents);
        assertEquals(pairs.size(), 1);
        assertNotNull(pairs.get(0).getStart());
        assertEquals(pairs.get(0).getStart(), now.plusDays(1));
        assertNotNull(pairs.get(0).getEnd());
        assertEquals(pairs.get(0).getEnd(), now.plusDays(2));

        //simple BUNDLE events closed clear -> disabled
        blockingEvents = new TreeSet<BlockingState>();
        blockingEvents.add(new DefaultBlockingState(ovdId, CLEAR_BUNDLE, Type.SUBSCRIPTION_BUNDLE, "test", false, false, false, now));
        blockingEvents.add(new DefaultBlockingState(ovdId, DISABLED_BUNDLE, Type.SUBSCRIPTION_BUNDLE, "test", true, true, true, now.plusDays(1)));
        blockingEvents.add(new DefaultBlockingState(ovdId, CLEAR_BUNDLE, Type.SUBSCRIPTION_BUNDLE, "test", false, false, false, now.plusDays(2)));

        pairs = odc.createBlockingDurations(blockingEvents);
        assertEquals(pairs.size(), 1);
        assertNotNull(pairs.get(0).getStart());
        assertEquals(pairs.get(0).getStart(), now.plusDays(1));
        assertNotNull(pairs.get(0).getEnd());
        assertEquals(pairs.get(0).getEnd(), now.plusDays(2));

        //two or more disableds in a row
        blockingEvents = new TreeSet<BlockingState>();
        blockingEvents.add(new DefaultBlockingState(ovdId, CLEAR_BUNDLE, Type.SUBSCRIPTION_BUNDLE, "test", false, false, false, now));
        blockingEvents.add(new DefaultBlockingState(ovdId, DISABLED_BUNDLE, Type.SUBSCRIPTION_BUNDLE, "test", true, true, true, now.plusDays(1)));
        blockingEvents.add(new DefaultBlockingState(ovdId, DISABLED_BUNDLE, Type.SUBSCRIPTION_BUNDLE, "test", true, true, true, now.plusDays(2)));
        blockingEvents.add(new DefaultBlockingState(ovdId, CLEAR_BUNDLE, Type.SUBSCRIPTION_BUNDLE, "test", false, false, false, now.plusDays(3)));

        pairs = odc.createBlockingDurations(blockingEvents);
        assertEquals(pairs.size(), 1);
        assertNotNull(pairs.get(0).getStart());
        assertEquals(pairs.get(0).getStart(), now.plusDays(1));
        assertNotNull(pairs.get(0).getEnd());
        assertEquals(pairs.get(0).getEnd(), now.plusDays(3));

        blockingEvents = new TreeSet<BlockingState>();
        blockingEvents.add(new DefaultBlockingState(ovdId, CLEAR_BUNDLE, Type.SUBSCRIPTION_BUNDLE, "test", false, false, false, now));
        blockingEvents.add(new DefaultBlockingState(ovdId, DISABLED_BUNDLE, Type.SUBSCRIPTION_BUNDLE, "test", true, true, true, now.plusDays(1)));
        blockingEvents.add(new DefaultBlockingState(ovdId, DISABLED_BUNDLE, Type.SUBSCRIPTION_BUNDLE, "test", true, true, true, now.plusDays(2)));
        blockingEvents.add(new DefaultBlockingState(ovdId, DISABLED_BUNDLE, Type.SUBSCRIPTION_BUNDLE, "test", true, true, true, now.plusDays(3)));
        blockingEvents.add(new DefaultBlockingState(ovdId, CLEAR_BUNDLE, Type.SUBSCRIPTION_BUNDLE, "test", false, false, false, now.plusDays(4)));

        pairs = odc.createBlockingDurations(blockingEvents);
        assertEquals(pairs.size(), 1);
        assertNotNull(pairs.get(0).getStart());
        assertEquals(pairs.get(0).getStart(), now.plusDays(1));
        assertNotNull(pairs.get(0).getEnd());
        assertEquals(pairs.get(0).getEnd(), now.plusDays(4));
    }
}
