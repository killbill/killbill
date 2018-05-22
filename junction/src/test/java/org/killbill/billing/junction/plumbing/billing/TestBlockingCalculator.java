/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.junction.plumbing.billing;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.DefaultUsage;
import org.killbill.billing.catalog.MockPlan;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.InternationalPrice;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.Recurring;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.dao.MockBlockingStateDao;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.junction.DefaultBlockingState;
import org.killbill.billing.junction.JunctionTestSuiteNoDB;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

public class TestBlockingCalculator extends JunctionTestSuiteNoDB {

    private static final String DISABLED_BUNDLE = "disabled-bundle";
    private static final String CLEAR_BUNDLE = "clear-bundle";

    private final UUID bundleId1 = UUID.randomUUID();
    private final UUID bundleId2 = UUID.randomUUID();

    private Account account;
    private SubscriptionBase subscription1;
    private SubscriptionBase subscription2;
    private SubscriptionBase subscription3;
    private SubscriptionBase subscription4;

    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();
        account = Mockito.mock(Account.class);
        subscription1 = Mockito.mock(SubscriptionBase.class);
        subscription2 = Mockito.mock(SubscriptionBase.class);
        subscription3 = Mockito.mock(SubscriptionBase.class);
        subscription4 = Mockito.mock(SubscriptionBase.class);
        Mockito.when(account.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(subscription1.getBundleId()).thenReturn(bundleId1);
        Mockito.when(subscription2.getBundleId()).thenReturn(bundleId1);
        Mockito.when(subscription3.getBundleId()).thenReturn(bundleId1);
        Mockito.when(subscription4.getBundleId()).thenReturn(bundleId2);
        Mockito.when(subscription1.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(subscription2.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(subscription3.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(subscription4.getId()).thenReturn(UUID.randomUUID());

        ((MockBlockingStateDao) blockingStateDao).clear();
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
    @Test(groups = "fast")
    public void testInsertBlockingEventsForBundle() throws CatalogApiException {
        final DateTime now = clock.getUTCNow();

        final BillingEvent A = createRealEvent(now.minusDays(1).minusHours(1), subscription1);
        final BillingEvent B = createRealEvent(now.minusDays(1), subscription2);
        final BillingEvent C = createRealEvent(now.plusDays(1), subscription2);
        final BillingEvent D = createRealEvent(now.plusDays(3), subscription3);
        final SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();
        billingEvents.add(A);
        billingEvents.add(B);
        billingEvents.add(C);
        billingEvents.add(D);

        final BlockingState blockingState1 = new DefaultBlockingState(bundleId1, BlockingStateType.SUBSCRIPTION_BUNDLE, DISABLED_BUNDLE, "test", true, true, true, now);
        final BlockingState blockingState2 = new DefaultBlockingState(bundleId1, BlockingStateType.SUBSCRIPTION_BUNDLE, CLEAR_BUNDLE, "test", false, false, false, now.plusDays(2));

        blockingStateDao.setBlockingStatesAndPostBlockingTransitionEvent(ImmutableMap.<BlockingState, Optional<UUID>>of(blockingState1, Optional.<UUID>absent(),
                                                                                                                        blockingState2, Optional.<UUID>absent()),
                                                                         internalCallContext);

        blockingCalculator.insertBlockingEvents(billingEvents, new HashSet<UUID>(), catalogInternalApi.getFullCatalog(true, true, internalCallContext), internalCallContext);

        assertEquals(billingEvents.size(), 7);

        final SortedSet<BillingEvent> s1Events = blockingCalculator.filter(billingEvents, subscription1);
        final Iterator<BillingEvent> it1 = s1Events.iterator();
        assertEquals(it1.next(), A);
        assertEquals(it1.next().getTransitionType(), SubscriptionBaseTransitionType.START_BILLING_DISABLED);
        assertEquals(it1.next().getTransitionType(), SubscriptionBaseTransitionType.END_BILLING_DISABLED);

        final SortedSet<BillingEvent> s2Events = blockingCalculator.filter(billingEvents, subscription2);
        final Iterator<BillingEvent> it2 = s2Events.iterator();
        assertEquals(it2.next(), B);
        assertEquals(it2.next().getTransitionType(), SubscriptionBaseTransitionType.START_BILLING_DISABLED);
        assertEquals(it2.next().getTransitionType(), SubscriptionBaseTransitionType.END_BILLING_DISABLED);

        final SortedSet<BillingEvent> s3Events = blockingCalculator.filter(billingEvents, subscription3);
        final Iterator<BillingEvent> it3 = s3Events.iterator();
        assertEquals(it3.next(), D);
    }

    // Open ended duration with a previous event
    // --X--[----------------------------------
    @Test(groups = "fast")
    public void testEventsToRemoveOpenPrev() {
        final DateTime now = clock.getUTCNow();
        final List<DisabledDuration> disabledDuration = new ArrayList<DisabledDuration>();
        final SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();

        disabledDuration.add(new DisabledDuration(now, null));
        billingEvents.add(createRealEvent(now.minusDays(1), subscription1));

        final SortedSet<BillingEvent> results = blockingCalculator.eventsToRemove(disabledDuration, billingEvents);

        assertEquals(results.size(), 0);
    }

    // Open with previous and following events
    // --X--[----Y-----------------------------
    @Test(groups = "fast")
    public void testEventsToRemoveOpenPrevFollow() {
        final DateTime now = clock.getUTCNow();
        final List<DisabledDuration> disabledDuration = new ArrayList<DisabledDuration>();
        final SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();

        disabledDuration.add(new DisabledDuration(now, null));
        final BillingEvent e1 = createRealEvent(now.minusDays(1), subscription1);
        final BillingEvent e2 = createRealEvent(now.plusDays(1), subscription1);
        billingEvents.add(e1);
        billingEvents.add(e2);

        final SortedSet<BillingEvent> results = blockingCalculator.eventsToRemove(disabledDuration, billingEvents);

        assertEquals(results.size(), 1);
        assertEquals(results.first(), e2);
    }

    // Open with no previous event (only following)
    // -----[----X-----------------------------
    @Test(groups = "fast")
    public void testEventsToRemoveOpenFollow() {
        final DateTime now = clock.getUTCNow();
        final List<DisabledDuration> disabledDuration = new ArrayList<DisabledDuration>();
        final SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();

        disabledDuration.add(new DisabledDuration(now, null));
        final BillingEvent e1 = createRealEvent(now.plusDays(1), subscription1);
        billingEvents.add(e1);

        final SortedSet<BillingEvent> results = blockingCalculator.eventsToRemove(disabledDuration, billingEvents);

        assertEquals(results.size(), 1);
        assertEquals(results.first(), e1);
    }

    // Open with no previous event (only at the same time)
    // -----[X-----------------------------
    @Test(groups = "fast")
    public void testEventsToRemoveOpenSameTime() {
        final DateTime now = clock.getUTCNow();
        final List<DisabledDuration> disabledDuration = new ArrayList<DisabledDuration>();
        final SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();

        disabledDuration.add(new DisabledDuration(now, null));
        final BillingEvent e1 = createRealEvent(now, subscription1);
        billingEvents.add(e1);

        final SortedSet<BillingEvent> results = blockingCalculator.eventsToRemove(disabledDuration, billingEvents);

        assertEquals(results.size(), 1);
        assertEquals(results.first(), e1);
    }

    // Closed duration with a single previous event
    // --X--[------------]---------------------
    @Test(groups = "fast")
    public void testEventsToRemoveClosedPrev() {
        final DateTime now = clock.getUTCNow();
        final List<DisabledDuration> disabledDuration = new ArrayList<DisabledDuration>();
        final SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();

        disabledDuration.add(new DisabledDuration(now, now.plusDays(2)));
        final BillingEvent e1 = createRealEvent(now.minusDays(1), subscription1);
        billingEvents.add(e1);

        final SortedSet<BillingEvent> results = blockingCalculator.eventsToRemove(disabledDuration, billingEvents);

        assertEquals(results.size(), 0);
    }

    // Closed duration with a previous event and in-between event
    // --X--[------Y-----]---------------------
    @Test(groups = "fast")
    public void testEventsToRemoveClosedPrevBetw() {
        final DateTime now = clock.getUTCNow();
        final List<DisabledDuration> disabledDuration = new ArrayList<DisabledDuration>();
        final SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();

        disabledDuration.add(new DisabledDuration(now, now.plusDays(2)));
        final BillingEvent e1 = createRealEvent(now.minusDays(1), subscription1);
        final BillingEvent e2 = createRealEvent(now.plusDays(1), subscription1);
        billingEvents.add(e1);
        billingEvents.add(e2);

        final SortedSet<BillingEvent> results = blockingCalculator.eventsToRemove(disabledDuration, billingEvents);

        assertEquals(results.size(), 1);
        assertEquals(results.first(), e2);
    }

    // Closed duration with a previous event and in-between event and following
    // --X--[------Y-----]-------Z-------------
    @Test(groups = "fast")
    public void testEventsToRemoveClosedPrevBetwNext() {
        final DateTime now = clock.getUTCNow();
        final List<DisabledDuration> disabledDuration = new ArrayList<DisabledDuration>();
        final SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();

        disabledDuration.add(new DisabledDuration(now, now.plusDays(2)));
        final BillingEvent e1 = createRealEvent(now.minusDays(1), subscription1);
        final BillingEvent e2 = createRealEvent(now.plusDays(1), subscription1);
        final BillingEvent e3 = createRealEvent(now.plusDays(3), subscription1);
        billingEvents.add(e1);
        billingEvents.add(e2);
        billingEvents.add(e3);

        final SortedSet<BillingEvent> results = blockingCalculator.eventsToRemove(disabledDuration, billingEvents);

        assertEquals(results.size(), 1);
        assertEquals(results.first(), e2);
    }

    // Closed with no previous event but in-between events
    // -----[------Y-----]---------------------
    @Test(groups = "fast")
    public void testEventsToRemoveClosedBetwn() {
        final DateTime now = clock.getUTCNow();
        final List<DisabledDuration> disabledDuration = new ArrayList<DisabledDuration>();
        final SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();

        disabledDuration.add(new DisabledDuration(now, now.plusDays(2)));
        final BillingEvent e2 = createRealEvent(now.plusDays(1), subscription1);
        billingEvents.add(e2);

        final SortedSet<BillingEvent> results = blockingCalculator.eventsToRemove(disabledDuration, billingEvents);

        assertEquals(results.size(), 1);
        assertEquals(results.first(), e2);
    }

    // Closed with no previous event but in-between events and following
    // -----[------Y-----]-------Z-------------
    @Test(groups = "fast")
    public void testEventsToRemoveClosedBetweenFollow() {
        final DateTime now = clock.getUTCNow();
        final List<DisabledDuration> disabledDuration = new ArrayList<DisabledDuration>();
        final SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();

        disabledDuration.add(new DisabledDuration(now, now.plusDays(2)));

        final BillingEvent e2 = createRealEvent(now.plusDays(1), subscription1);
        final BillingEvent e3 = createRealEvent(now.plusDays(3), subscription1);
        billingEvents.add(e2);
        billingEvents.add(e3);

        final SortedSet<BillingEvent> results = blockingCalculator.eventsToRemove(disabledDuration, billingEvents);

        assertEquals(results.size(), 1);
        assertEquals(results.first(), e2);
    }

    // Closed duration with only following
    // -----[------------]-------Z-------------
    @Test(groups = "fast")
    public void testEventsToRemoveClosedFollow() {
        final DateTime now = clock.getUTCNow();
        final List<DisabledDuration> disabledDuration = new ArrayList<DisabledDuration>();
        final SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();

        disabledDuration.add(new DisabledDuration(now, now.plusDays(2)));

        final BillingEvent e3 = createRealEvent(now.plusDays(3), subscription1);

        billingEvents.add(e3);

        final SortedSet<BillingEvent> results = blockingCalculator.eventsToRemove(disabledDuration, billingEvents);

        assertEquals(results.size(), 0);
    }

    // Open ended duration with a previous event
    // --X--[----------------------------------
    @Test(groups = "fast")
    public void testCreateNewEventsOpenPrev() throws CatalogApiException {
        final DateTime now = clock.getUTCNow();
        final List<DisabledDuration> disabledDuration = new ArrayList<DisabledDuration>();
        final SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();

        disabledDuration.add(new DisabledDuration(now, null));
        billingEvents.add(createRealEvent(now.minusDays(1), subscription1));

        final SortedSet<BillingEvent> results = blockingCalculator.createNewEvents(disabledDuration, billingEvents, catalogInternalApi.getFullCatalog(true, true, internalCallContext),  internalCallContext);

        assertEquals(results.size(), 1);
        assertEquals(results.first().getEffectiveDate(), now);
        assertNull(results.first().getFixedPrice());
        assertNull(results.first().getRecurringPrice(null));
        assertEquals(results.first().getBillingPeriod(), BillingPeriod.NO_BILLING_PERIOD);
        assertEquals(results.first().getTransitionType(), SubscriptionBaseTransitionType.START_BILLING_DISABLED);
    }

    // Open with previous and following events
    // --X--[----Y-----------------------------
    @Test(groups = "fast")
    public void testCreateNewEventsOpenPrevFollow() throws CatalogApiException {
        final DateTime now = clock.getUTCNow();
        final List<DisabledDuration> disabledDuration = new ArrayList<DisabledDuration>();
        final SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();

        disabledDuration.add(new DisabledDuration(now, null));
        billingEvents.add(createRealEvent(now.minusDays(1), subscription1));
        billingEvents.add(createRealEvent(now.plusDays(1), subscription1));

        final SortedSet<BillingEvent> results = blockingCalculator.createNewEvents(disabledDuration, billingEvents, catalogInternalApi.getFullCatalog(true, true, internalCallContext), internalCallContext);

        assertEquals(results.size(), 1);
        assertEquals(results.first().getEffectiveDate(), now);
        assertNull(results.first().getFixedPrice());
        assertNull(results.first().getRecurringPrice(null));
        assertEquals(results.first().getBillingPeriod(), BillingPeriod.NO_BILLING_PERIOD);
        assertEquals(results.first().getTransitionType(), SubscriptionBaseTransitionType.START_BILLING_DISABLED);
    }

    // Open with no previous event (only following)
    // -----[----X-----------------------------
    @Test(groups = "fast")
    public void testCreateNewEventsOpenFollow() throws CatalogApiException {
        final DateTime now = clock.getUTCNow();
        final List<DisabledDuration> disabledDuration = new ArrayList<DisabledDuration>();
        final SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();

        disabledDuration.add(new DisabledDuration(now, null));
        billingEvents.add(createRealEvent(now.plusDays(1), subscription1));

        final SortedSet<BillingEvent> results = blockingCalculator.createNewEvents(disabledDuration, billingEvents, catalogInternalApi.getFullCatalog(true, true, internalCallContext), internalCallContext);

        assertEquals(results.size(), 0);
    }

    // Open with no previous event (only at the same time)
    // -----[X-----------------------------
    @Test(groups = "fast")
    public void testCreateNewEventsOpenSameTime() throws CatalogApiException {
        final DateTime now = clock.getUTCNow();
        final List<DisabledDuration> disabledDuration = new ArrayList<DisabledDuration>();
        final SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();

        disabledDuration.add(new DisabledDuration(now, null));
        billingEvents.add(createRealEvent(now, subscription1));

        final SortedSet<BillingEvent> results = blockingCalculator.createNewEvents(disabledDuration, billingEvents, catalogInternalApi.getFullCatalog(true, true, internalCallContext), internalCallContext);

        assertEquals(results.size(), 0);
    }

    // Closed duration with a single previous event
    // --X--[------------]---------------------
    @Test(groups = "fast")
    public void testCreateNewEventsClosedPrev() throws CatalogApiException {
        final DateTime now = clock.getUTCNow();
        final List<DisabledDuration> disabledDuration = new ArrayList<DisabledDuration>();
        final SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();

        disabledDuration.add(new DisabledDuration(now, now.plusDays(2)));
        billingEvents.add(createRealEvent(now.minusDays(1), subscription1));

        final SortedSet<BillingEvent> results = blockingCalculator.createNewEvents(disabledDuration, billingEvents, catalogInternalApi.getFullCatalog(true, true, internalCallContext), internalCallContext);


        assertEquals(results.size(), 2);
        assertEquals(results.first().getEffectiveDate(), now);
        assertNull(results.first().getFixedPrice());
        assertNull(results.first().getRecurringPrice(null));
        assertEquals(results.first().getBillingPeriod(), BillingPeriod.NO_BILLING_PERIOD);
        assertEquals(results.first().getTransitionType(), SubscriptionBaseTransitionType.START_BILLING_DISABLED);
        assertEquals(results.last().getEffectiveDate(), now.plusDays(2));
        assertEquals(results.last().getRecurringPrice(null), billingEvents.first().getRecurringPrice(null));
        assertEquals(results.last().getTransitionType(), SubscriptionBaseTransitionType.END_BILLING_DISABLED);
    }

    // Closed duration with a previous event and in-between event
    // --X--[------Y-----]---------------------
    @Test(groups = "fast")
    public void testCreateNewEventsClosedPrevBetw() throws CatalogApiException {
        final DateTime now = clock.getUTCNow();
        final List<DisabledDuration> disabledDuration = new ArrayList<DisabledDuration>();
        final SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();

        disabledDuration.add(new DisabledDuration(now, now.plusDays(2)));
        billingEvents.add(createRealEvent(now.minusDays(1), subscription1));
        billingEvents.add(createRealEvent(now.plusDays(1), subscription1));

        final SortedSet<BillingEvent> results = blockingCalculator.createNewEvents(disabledDuration, billingEvents, catalogInternalApi.getFullCatalog(true, true, internalCallContext), internalCallContext);

        assertEquals(results.size(), 2);
        assertEquals(results.first().getEffectiveDate(), now);
        assertNull(results.first().getFixedPrice());
        assertNull(results.first().getRecurringPrice(null));
        assertEquals(results.first().getBillingPeriod(), BillingPeriod.NO_BILLING_PERIOD);
        assertEquals(results.first().getTransitionType(), SubscriptionBaseTransitionType.START_BILLING_DISABLED);
        assertEquals(results.last().getEffectiveDate(), now.plusDays(2));
        assertEquals(results.last().getRecurringPrice(null), billingEvents.first().getRecurringPrice(null));
        assertEquals(results.last().getTransitionType(), SubscriptionBaseTransitionType.END_BILLING_DISABLED);
    }

    // Closed duration with a previous event and in-between event and following
    // --X--[------Y-----]-------Z-------------
    @Test(groups = "fast")
    public void testCreateNewEventsClosedPrevBetwNext() throws CatalogApiException {
        final DateTime now = clock.getUTCNow();
        final List<DisabledDuration> disabledDuration = new ArrayList<DisabledDuration>();
        final SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();

        disabledDuration.add(new DisabledDuration(now, now.plusDays(2)));
        billingEvents.add(createRealEvent(now.minusDays(1), subscription1));
        billingEvents.add(createRealEvent(now.plusDays(1), subscription1));
        billingEvents.add(createRealEvent(now.plusDays(3), subscription1));

        final SortedSet<BillingEvent> results = blockingCalculator.createNewEvents(disabledDuration, billingEvents, catalogInternalApi.getFullCatalog(true, true, internalCallContext), internalCallContext);

        assertEquals(results.size(), 2);
        assertEquals(results.first().getEffectiveDate(), now);
        assertNull(results.first().getFixedPrice());
        assertNull(results.first().getRecurringPrice(null));
        assertEquals(results.first().getBillingPeriod(), BillingPeriod.NO_BILLING_PERIOD);
        assertEquals(results.first().getTransitionType(), SubscriptionBaseTransitionType.START_BILLING_DISABLED);
        assertEquals(results.last().getEffectiveDate(), now.plusDays(2));
        assertEquals(results.last().getRecurringPrice(null), billingEvents.first().getRecurringPrice(null));
        assertEquals(results.last().getTransitionType(), SubscriptionBaseTransitionType.END_BILLING_DISABLED);
    }

    // Closed with no previous event but in-between events
    // -----[------Y-----]---------------------
    @Test(groups = "fast")
    public void testCreateNewEventsClosedBetwn() throws CatalogApiException {
        final DateTime now = clock.getUTCNow();
        final List<DisabledDuration> disabledDuration = new ArrayList<DisabledDuration>();
        final SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();

        disabledDuration.add(new DisabledDuration(now, now.plusDays(2)));
        billingEvents.add(createRealEvent(now.plusDays(1), subscription1));

        final SortedSet<BillingEvent> results = blockingCalculator.createNewEvents(disabledDuration, billingEvents, catalogInternalApi.getFullCatalog(true, true, internalCallContext), internalCallContext);

        assertEquals(results.size(), 1);
        assertEquals(results.last().getEffectiveDate(), now.plusDays(2));
        assertEquals(results.last().getRecurringPrice(null), billingEvents.first().getRecurringPrice(null));
        assertEquals(results.last().getTransitionType(), SubscriptionBaseTransitionType.END_BILLING_DISABLED);
    }

    // Closed with no previous event but in-between events and following
    // -----[------Y-----]-------Z-------------
    @Test(groups = "fast")
    public void testCreateNewEventsClosedBetweenFollow() throws CatalogApiException {
        final DateTime now = clock.getUTCNow();
        final List<DisabledDuration> disabledDuration = new ArrayList<DisabledDuration>();
        final SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();

        disabledDuration.add(new DisabledDuration(now, now.plusDays(2)));
        billingEvents.add(createRealEvent(now.plusDays(1), subscription1));

        final SortedSet<BillingEvent> results = blockingCalculator.createNewEvents(disabledDuration, billingEvents, catalogInternalApi.getFullCatalog(true, true, internalCallContext), internalCallContext);

        assertEquals(results.size(), 1);
        assertEquals(results.last().getEffectiveDate(), now.plusDays(2));
        assertEquals(results.last().getRecurringPrice(null), billingEvents.first().getRecurringPrice(null));
        assertEquals(results.last().getTransitionType(), SubscriptionBaseTransitionType.END_BILLING_DISABLED);
    }

    // Closed duration with only following
    // -----[------------]-------Z-------------
    @Test(groups = "fast")
    public void testCreateNewEventsClosedFollow() throws CatalogApiException {
        final DateTime now = clock.getUTCNow();
        final List<DisabledDuration> disabledDuration = new ArrayList<DisabledDuration>();
        final SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();

        disabledDuration.add(new DisabledDuration(now, now.plusDays(2)));
        billingEvents.add(createRealEvent(now.plusDays(3), subscription1));

        final SortedSet<BillingEvent> results = blockingCalculator.createNewEvents(disabledDuration, billingEvents, catalogInternalApi.getFullCatalog(true, true, internalCallContext), internalCallContext);

        assertEquals(results.size(), 0);
    }

    @Test(groups = "fast")
    public void testPrecedingBillingEventForSubscription() {
        final DateTime now = new DateTime();

        final SortedSet<BillingEvent> events = new TreeSet<BillingEvent>();

        events.add(createRealEvent(now.minusDays(10), subscription1));
        events.add(createRealEvent(now.minusDays(6), subscription1));
        events.add(createRealEvent(now.minusDays(5), subscription1));
        events.add(createRealEvent(now.minusDays(1), subscription1));

        final BillingEvent minus11 = blockingCalculator.precedingBillingEventForSubscription(now.minusDays(11), events);
        assertNull(minus11);

        final BillingEvent minus5andAHalf = blockingCalculator.precedingBillingEventForSubscription(now.minusDays(5).minusHours(12), events);
        assertNotNull(minus5andAHalf);
        assertEquals(minus5andAHalf.getEffectiveDate(), now.minusDays(6));

    }

    protected BillingEvent createRealEvent(final DateTime effectiveDate, final SubscriptionBase subscription) {
        return createRealEvent(effectiveDate, subscription, SubscriptionBaseTransitionType.CHANGE);
    }

    protected BillingEvent createRealEvent(final DateTime effectiveDate, final SubscriptionBase subscription, final SubscriptionBaseTransitionType type)  {
        try {

            final Integer billCycleDay = 1;
            final Plan plan = new MockPlan();
            final Currency currency = Currency.USD;
            final String description = "";
            final BillingPeriod billingPeriod = BillingPeriod.MONTHLY;
            final Long totalOrdering = 0L;
            final DateTimeZone tz = DateTimeZone.UTC;


            final PlanPhase planPhase = Mockito.mock(PlanPhase.class);

            final InternationalPrice recurringPrice = Mockito.mock(InternationalPrice.class);

            Mockito.when(recurringPrice.getPrice(Mockito.<Currency>any())).thenReturn(BigDecimal.TEN);
            final Recurring recurring = Mockito.mock(Recurring.class);
            Mockito.when(recurring.getRecurringPrice()).thenReturn(recurringPrice);
            Mockito.when(planPhase.getRecurring()).thenReturn(recurring);
            Mockito.when(planPhase.getUsages()).thenReturn(new DefaultUsage[0]);

            final BigDecimal fixedPrice = BigDecimal.TEN;

            return new DefaultBillingEvent(subscription, effectiveDate, true, plan, planPhase, fixedPrice,
                                           currency,
                                           billingPeriod, billCycleDay,
                                           description, totalOrdering, type, null, false);

        } catch (final CatalogApiException e) {
            Assert.fail("", e);
        }
        throw new IllegalStateException();
    }

    @Test(groups = "fast")
    public void testFilter() {
        final SortedSet<BillingEvent> events = new TreeSet<BillingEvent>();

        events.add(createBillingEvent(subscription1));
        events.add(createBillingEvent(subscription1));
        events.add(createBillingEvent(subscription1));
        events.add(createBillingEvent(subscription2));

        final SortedSet<BillingEvent> result1 = blockingCalculator.filter(events, subscription1);
        final SortedSet<BillingEvent> result2 = blockingCalculator.filter(events, subscription2);
        final SortedSet<BillingEvent> result3 = blockingCalculator.filter(events, subscription3);

        assertEquals(result1.size(), 3);
        assertEquals(result1.first().getSubscription(), subscription1);
        assertEquals(result1.last().getSubscription(), subscription1);
        assertEquals(result2.size(), 1);
        assertEquals(result2.first().getSubscription(), subscription2);
        assertEquals(result3.size(), 0);
    }

    @Test(groups = "fast")
    public void testCreateNewDisableEvent() throws CatalogApiException {
        final DateTime now = clock.getUTCNow();
        final BillingEvent event = new MockBillingEvent();

        final BillingEvent result = blockingCalculator.createNewDisableEvent(now, event, null);
        assertEquals(result.getBillCycleDayLocal(), event.getBillCycleDayLocal());
        assertEquals(result.getEffectiveDate(), now);
        assertEquals(result.getPlanPhase(), event.getPlanPhase());
        assertEquals(result.getPlan(), event.getPlan());
        assertNull(result.getFixedPrice());
        assertNull(result.getRecurringPrice(null));
        assertEquals(result.getCurrency(), event.getCurrency());
        assertEquals(result.getDescription(), "");
        assertEquals(result.getBillingPeriod(), BillingPeriod.NO_BILLING_PERIOD);
        assertEquals(result.getTransitionType(), SubscriptionBaseTransitionType.START_BILLING_DISABLED);
        // TODO - ugly, fragile
        assertEquals(result.getTotalOrdering(), (Long) (BlockingCalculator.getGlobalTotalOrder().get() - 1));
    }

    @Test(groups = "fast")
    public void testCreateNewReenableEvent() throws CatalogApiException {
        final DateTime now = clock.getUTCNow();
        final BillingEvent event = new MockBillingEvent();

        final BillingEvent result = blockingCalculator.createNewReenableEvent(now, event, null, internalCallContext);
        assertEquals(result.getBillCycleDayLocal(), event.getBillCycleDayLocal());
        assertEquals(result.getEffectiveDate(), now);
        assertEquals(result.getPlanPhase(), event.getPlanPhase());
        assertEquals(result.getPlan(), event.getPlan());
        assertEquals(result.getFixedPrice(), event.getFixedPrice());
        assertEquals(result.getRecurringPrice(null), event.getRecurringPrice(null));
        assertEquals(result.getCurrency(), event.getCurrency());
        assertEquals(result.getDescription(), "");
        assertEquals(result.getBillingPeriod(), event.getBillingPeriod());
        assertEquals(result.getTransitionType(), SubscriptionBaseTransitionType.END_BILLING_DISABLED);
        // TODO - ugly, fragile
        assertEquals(result.getTotalOrdering(), (Long) (BlockingCalculator.getGlobalTotalOrder().get() - 1));
    }

    private class MockBillingEvent extends DefaultBillingEvent {

        public MockBillingEvent() {
            super(subscription1, clock.getUTCNow(), true, null, null, BigDecimal.ZERO, Currency.USD, BillingPeriod.ANNUAL,
                  4, "", 3L, SubscriptionBaseTransitionType.CREATE, null, false);
        }
    }

    @Test(groups = "fast")
    public void testCreateBundleSubscriptionMap() {
        final SortedSet<BillingEvent> events = new TreeSet<BillingEvent>();
        events.add(createBillingEvent(subscription1));
        events.add(createBillingEvent(subscription2));
        events.add(createBillingEvent(subscription3));
        events.add(createBillingEvent(subscription4));

        final Hashtable<UUID, List<SubscriptionBase>> map = blockingCalculator.createBundleSubscriptionMap(events);

        assertNotNull(map);
        assertEquals(map.keySet().size(), 2);
        assertEquals(map.get(bundleId1).size(), 3);
        assertEquals(map.get(bundleId2).size(), 1);

    }

    private BillingEvent createBillingEvent(final SubscriptionBase subscription) {
        final BillingEvent result = Mockito.mock(BillingEvent.class);
        Mockito.when(result.getSubscription()).thenReturn(subscription);
        Mockito.when(result.compareTo(Mockito.<BillingEvent>any())).thenReturn(1);
        return result;
    }

    @Test(groups = "fast")
    public void testCreateDisablePairs() {
        List<BlockingState> blockingEvents;
        final UUID ovdId = UUID.randomUUID();
        final UUID ovdId2 = UUID.randomUUID();
        final DateTime now = clock.getUTCNow();

        // Simple events open clear -> disabled
        blockingEvents = new ArrayList<BlockingState>();
        blockingEvents.add(new DefaultBlockingState(ovdId, BlockingStateType.SUBSCRIPTION_BUNDLE, CLEAR_BUNDLE, "test", false, false, false, now));
        blockingEvents.add(new DefaultBlockingState(ovdId, BlockingStateType.SUBSCRIPTION_BUNDLE, DISABLED_BUNDLE, "test", true, true, true, now.plusDays(1)));

        List<DisabledDuration> pairs = blockingCalculator.createBlockingDurations(blockingEvents);
        assertEquals(pairs.size(), 1);
        assertNotNull(pairs.get(0).getStart());
        assertEquals(pairs.get(0).getStart(), now.plusDays(1));
        assertNull(pairs.get(0).getEnd());

        // Simple events closed clear -> disabled
        blockingEvents = new ArrayList<BlockingState>();
        blockingEvents.add(new DefaultBlockingState(ovdId, BlockingStateType.SUBSCRIPTION_BUNDLE, CLEAR_BUNDLE, "test", false, false, false, now));
        blockingEvents.add(new DefaultBlockingState(ovdId, BlockingStateType.SUBSCRIPTION_BUNDLE, DISABLED_BUNDLE, "test", true, true, true, now.plusDays(1)));
        blockingEvents.add(new DefaultBlockingState(ovdId, BlockingStateType.SUBSCRIPTION_BUNDLE, CLEAR_BUNDLE, "test", false, false, false, now.plusDays(2)));

        pairs = blockingCalculator.createBlockingDurations(blockingEvents);
        assertEquals(pairs.size(), 1);
        assertNotNull(pairs.get(0).getStart());
        assertEquals(pairs.get(0).getStart(), now.plusDays(1));
        assertNotNull(pairs.get(0).getEnd());
        assertEquals(pairs.get(0).getEnd(), now.plusDays(2));

        // Simple BUNDLE events closed clear -> disabled
        blockingEvents = new ArrayList<BlockingState>();
        blockingEvents.add(new DefaultBlockingState(ovdId, BlockingStateType.SUBSCRIPTION_BUNDLE, CLEAR_BUNDLE, "test", false, false, false, now));
        blockingEvents.add(new DefaultBlockingState(ovdId, BlockingStateType.SUBSCRIPTION_BUNDLE, DISABLED_BUNDLE, "test", true, true, true, now.plusDays(1)));
        blockingEvents.add(new DefaultBlockingState(ovdId, BlockingStateType.SUBSCRIPTION_BUNDLE, CLEAR_BUNDLE, "test", false, false, false, now.plusDays(2)));

        pairs = blockingCalculator.createBlockingDurations(blockingEvents);
        assertEquals(pairs.size(), 1);
        assertNotNull(pairs.get(0).getStart());
        assertEquals(pairs.get(0).getStart(), now.plusDays(1));
        assertNotNull(pairs.get(0).getEnd());
        assertEquals(pairs.get(0).getEnd(), now.plusDays(2));

        // Two or more disabled in a row
        blockingEvents = new ArrayList<BlockingState>();
        blockingEvents.add(new DefaultBlockingState(ovdId, BlockingStateType.SUBSCRIPTION_BUNDLE, CLEAR_BUNDLE, "test", false, false, false, now));
        blockingEvents.add(new DefaultBlockingState(ovdId, BlockingStateType.SUBSCRIPTION_BUNDLE, DISABLED_BUNDLE, "test", true, true, true, now.plusDays(1)));
        blockingEvents.add(new DefaultBlockingState(ovdId, BlockingStateType.SUBSCRIPTION_BUNDLE, DISABLED_BUNDLE, "test", true, true, true, now.plusDays(2)));
        blockingEvents.add(new DefaultBlockingState(ovdId, BlockingStateType.SUBSCRIPTION_BUNDLE, CLEAR_BUNDLE, "test", false, false, false, now.plusDays(3)));

        pairs = blockingCalculator.createBlockingDurations(blockingEvents);
        assertEquals(pairs.size(), 1);
        assertNotNull(pairs.get(0).getStart());
        assertEquals(pairs.get(0).getStart(), now.plusDays(1));
        assertNotNull(pairs.get(0).getEnd());
        assertEquals(pairs.get(0).getEnd(), now.plusDays(3));

        blockingEvents = new ArrayList<BlockingState>();
        blockingEvents.add(new DefaultBlockingState(ovdId, BlockingStateType.SUBSCRIPTION_BUNDLE, CLEAR_BUNDLE, "test", false, false, false, now));
        blockingEvents.add(new DefaultBlockingState(ovdId, BlockingStateType.SUBSCRIPTION_BUNDLE, DISABLED_BUNDLE, "test", true, true, true, now.plusDays(1)));
        blockingEvents.add(new DefaultBlockingState(ovdId, BlockingStateType.SUBSCRIPTION_BUNDLE, DISABLED_BUNDLE, "test", true, true, true, now.plusDays(2)));
        blockingEvents.add(new DefaultBlockingState(ovdId, BlockingStateType.SUBSCRIPTION_BUNDLE, DISABLED_BUNDLE, "test", true, true, true, now.plusDays(3)));
        blockingEvents.add(new DefaultBlockingState(ovdId, BlockingStateType.SUBSCRIPTION_BUNDLE, CLEAR_BUNDLE, "test", false, false, false, now.plusDays(4)));

        pairs = blockingCalculator.createBlockingDurations(blockingEvents);
        assertEquals(pairs.size(), 1);
        assertNotNull(pairs.get(0).getStart());
        assertEquals(pairs.get(0).getStart(), now.plusDays(1));
        assertNotNull(pairs.get(0).getEnd());
        assertEquals(pairs.get(0).getEnd(), now.plusDays(4));

        // Verify ordering at the same effective date doesn't matter. This is to work around nondeterministic ordering
        // behavior in ProxyBlockingStateDao#BLOCKING_STATE_ORDERING_WITH_TIES_UNHANDLED. See also TestDefaultInternalBillingApi.
        blockingEvents = new ArrayList<BlockingState>();
        blockingEvents.add(new DefaultBlockingState(ovdId, BlockingStateType.SUBSCRIPTION_BUNDLE, DISABLED_BUNDLE, "test", true, true, true, now.plusDays(1)));
        blockingEvents.add(new DefaultBlockingState(ovdId, BlockingStateType.SUBSCRIPTION_BUNDLE, CLEAR_BUNDLE, "test", false, false, false, now.plusDays(2)));
        blockingEvents.add(new DefaultBlockingState(ovdId2, BlockingStateType.SUBSCRIPTION_BUNDLE, DISABLED_BUNDLE, "test", true, true, true, now.plusDays(2)));
        blockingEvents.add(new DefaultBlockingState(ovdId2, BlockingStateType.SUBSCRIPTION_BUNDLE, CLEAR_BUNDLE, "test", false, false, false, now.plusDays(3)));

        pairs = blockingCalculator.createBlockingDurations(blockingEvents);
        assertEquals(pairs.size(), 1);
        assertEquals(pairs.get(0).getStart(), now.plusDays(1));
        assertEquals(pairs.get(0).getEnd(), now.plusDays(3));

        blockingEvents = new ArrayList<BlockingState>();
        blockingEvents.add(new DefaultBlockingState(ovdId, BlockingStateType.SUBSCRIPTION_BUNDLE, DISABLED_BUNDLE, "test", true, true, true, now.plusDays(1)));
        blockingEvents.add(new DefaultBlockingState(ovdId2, BlockingStateType.SUBSCRIPTION_BUNDLE, DISABLED_BUNDLE, "test", true, true, true, now.plusDays(2)));
        blockingEvents.add(new DefaultBlockingState(ovdId, BlockingStateType.SUBSCRIPTION_BUNDLE, CLEAR_BUNDLE, "test", false, false, false, now.plusDays(2)));
        blockingEvents.add(new DefaultBlockingState(ovdId2, BlockingStateType.SUBSCRIPTION_BUNDLE, CLEAR_BUNDLE, "test", false, false, false, now.plusDays(3)));

        pairs = blockingCalculator.createBlockingDurations(blockingEvents);
        assertEquals(pairs.size(), 1);
        assertEquals(pairs.get(0).getStart(), now.plusDays(1));
        assertEquals(pairs.get(0).getEnd(), now.plusDays(3));
    }

    @Test(groups = "fast")
    public void testCreateAndMergeDisablePairs() {
        final List<BlockingState> blockingEvents = new ArrayList<BlockingState>();
        final UUID ovdId = UUID.randomUUID();
        final DateTime entitlementStartDate = clock.getUTCNow();
        final DateTime blockEffectiveDate = entitlementStartDate.plusSeconds(1);
        final DateTime unblockEffectiveDate = blockEffectiveDate.plusDays(2);

        // Similar to an entitlement start event
        blockingEvents.add(new DefaultBlockingState(ovdId, BlockingStateType.SUBSCRIPTION_BUNDLE, CLEAR_BUNDLE, "test", false, false, false, entitlementStartDate));
        List<DisabledDuration> pairs = blockingCalculator.createBlockingDurations(blockingEvents);
        assertEquals(pairs.size(), 0);

        blockingEvents.add(new DefaultBlockingState(ovdId, BlockingStateType.SUBSCRIPTION_BUNDLE, DISABLED_BUNDLE, "test", true, true, true, blockEffectiveDate));

        pairs = blockingCalculator.createBlockingDurations(blockingEvents);
        assertEquals(pairs.size(), 1);
        assertEquals(pairs.get(0).getStart().compareTo(blockEffectiveDate), 0);
        assertNull(pairs.get(0).getEnd());

        blockingEvents.add(new DefaultBlockingState(ovdId, BlockingStateType.SUBSCRIPTION_BUNDLE, CLEAR_BUNDLE, "test", false, false, false, unblockEffectiveDate));

        pairs = blockingCalculator.createBlockingDurations(blockingEvents);
        assertEquals(pairs.size(), 1);
        assertEquals(pairs.get(0).getStart().compareTo(blockEffectiveDate), 0);
        assertEquals(pairs.get(0).getEnd().compareTo(unblockEffectiveDate), 0);

        blockingEvents.add(new DefaultBlockingState(ovdId, BlockingStateType.SUBSCRIPTION_BUNDLE, DISABLED_BUNDLE, "test", true, true, true, unblockEffectiveDate));

        pairs = blockingCalculator.createBlockingDurations(blockingEvents);
        assertEquals(pairs.size(), 1);
        assertEquals(pairs.get(0).getStart().compareTo(blockEffectiveDate), 0);
        assertNull(pairs.get(0).getEnd());
    }

    @Test(groups = "fast")
    public void testSimpleWithClearBlockingDuration() throws Exception {

        final BillingEvent trial = createRealEvent(new LocalDate(2012, 5, 1).toDateTimeAtStartOfDay(DateTimeZone.UTC), subscription1, SubscriptionBaseTransitionType.CREATE);
        final BillingEvent phase = createRealEvent(new LocalDate(2012, 5, 31).toDateTimeAtStartOfDay(DateTimeZone.UTC), subscription1, SubscriptionBaseTransitionType.PHASE);
        final BillingEvent upgrade = createRealEvent(new LocalDate(2012, 7, 25).toDateTimeAtStartOfDay(DateTimeZone.UTC), subscription1, SubscriptionBaseTransitionType.CHANGE);
        final SortedSet<BillingEvent> billingEvents = new TreeSet<BillingEvent>();
        billingEvents.add(trial);
        billingEvents.add(phase);
        billingEvents.add(upgrade);

        final BlockingState blockingState1 = new DefaultBlockingState(bundleId1, BlockingStateType.SUBSCRIPTION_BUNDLE, DISABLED_BUNDLE, "test", true, false, false, new LocalDate(2012, 7, 5).toDateTimeAtStartOfDay(DateTimeZone.UTC));
        final BlockingState blockingState2 = new DefaultBlockingState(bundleId1, BlockingStateType.SUBSCRIPTION_BUNDLE, DISABLED_BUNDLE, "test", true, true, true, new LocalDate(2012, 7, 15).toDateTimeAtStartOfDay(DateTimeZone.UTC));
        final BlockingState blockingState3 = new DefaultBlockingState(bundleId1, BlockingStateType.SUBSCRIPTION_BUNDLE, DISABLED_BUNDLE, "test", true, true, true, new LocalDate(2012, 7, 24).toDateTimeAtStartOfDay(DateTimeZone.UTC));
        final BlockingState blockingState4 = new DefaultBlockingState(bundleId1, BlockingStateType.SUBSCRIPTION_BUNDLE, CLEAR_BUNDLE, "test", false, false, false, new LocalDate(2012, 7, 25).toDateTimeAtStartOfDay(DateTimeZone.UTC));

        blockingStateDao.setBlockingStatesAndPostBlockingTransitionEvent(ImmutableMap.<BlockingState, Optional<UUID>>of(blockingState1, Optional.<UUID>absent(),
                                                                                                                        blockingState2, Optional.<UUID>absent(),
                                                                                                                        blockingState3, Optional.<UUID>absent(),
                                                                                                                        blockingState4, Optional.<UUID>absent()),
                                                                         internalCallContext);

        blockingCalculator.insertBlockingEvents(billingEvents, new HashSet<UUID>(), catalogInternalApi.getFullCatalog(true, true, internalCallContext), internalCallContext);

        assertEquals(billingEvents.size(), 5);
        final List<BillingEvent> events = new ArrayList<BillingEvent>(billingEvents);
        assertEquals(events.get(0).getEffectiveDate(), new LocalDate(2012, 5, 1).toDateTimeAtStartOfDay(DateTimeZone.UTC));
        assertEquals(events.get(0).getTransitionType(), SubscriptionBaseTransitionType.CREATE);
        assertEquals(events.get(1).getEffectiveDate(), new LocalDate(2012, 5, 31).toDateTimeAtStartOfDay(DateTimeZone.UTC));
        assertEquals(events.get(1).getTransitionType(), SubscriptionBaseTransitionType.PHASE);
        assertEquals(events.get(2).getEffectiveDate(), new LocalDate(2012, 7, 15).toDateTimeAtStartOfDay(DateTimeZone.UTC));
        assertEquals(events.get(2).getTransitionType(), SubscriptionBaseTransitionType.START_BILLING_DISABLED);
        assertEquals(events.get(3).getEffectiveDate(), new LocalDate(2012, 7, 25).toDateTimeAtStartOfDay(DateTimeZone.UTC));
        assertEquals(events.get(3).getTransitionType(), SubscriptionBaseTransitionType.END_BILLING_DISABLED);
        assertEquals(events.get(4).getEffectiveDate(), new LocalDate(2012, 7, 25).toDateTimeAtStartOfDay(DateTimeZone.UTC));
        assertEquals(events.get(4).getTransitionType(), SubscriptionBaseTransitionType.CHANGE);    }
}
