/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

import javax.annotation.Nullable;

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
import org.killbill.billing.catalog.api.VersionedCatalog;
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
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

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
    private Map<UUID, List<SubscriptionBase>> subscriptionsForAccount;

    private VersionedCatalog catalog;

    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();
        account = Mockito.mock(Account.class);

        subscriptionsForAccount = new HashMap<>();

        subscription1 = Mockito.mock(SubscriptionBase.class);
        subscription2 = Mockito.mock(SubscriptionBase.class);
        subscription3 = Mockito.mock(SubscriptionBase.class);
        subscription4 = Mockito.mock(SubscriptionBase.class);
        Mockito.when(account.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(subscription1.getBundleId()).thenReturn(bundleId1);
        Mockito.when(subscription2.getBundleId()).thenReturn(bundleId1);
        Mockito.when(subscription3.getBundleId()).thenReturn(bundleId1);
        final List<SubscriptionBase> bundleSubscriptions1 = new ArrayList<>();
        bundleSubscriptions1.add(subscription1);
        bundleSubscriptions1.add(subscription2);
        bundleSubscriptions1.add(subscription3);
        subscriptionsForAccount.put(bundleId1, bundleSubscriptions1);

        Mockito.when(subscription4.getBundleId()).thenReturn(bundleId2);
        final List<SubscriptionBase> bundleSubscriptions2 = new ArrayList<>();
        bundleSubscriptions1.add(subscription4);
        subscriptionsForAccount.put(bundleId2, bundleSubscriptions2);

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

        final BillingEvent A = createRealEvent(subscription1, now.minusDays(1).minusHours(1));
        final BillingEvent B = createRealEvent(subscription2, now.minusDays(1));
        final BillingEvent C = createRealEvent(subscription2, now.plusDays(1));
        final BillingEvent D = createRealEvent(subscription3, now.plusDays(3));
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

        blockingCalculator.insertBlockingEvents(billingEvents, new HashSet<UUID>(), subscriptionsForAccount, catalog, null, internalCallContext);

        assertEquals(billingEvents.size(), 7);

        final Iterable<BillingEvent> s1Events = Iterables.filter(billingEvents, new Predicate<BillingEvent>() {
            @Override
            public boolean apply(@Nullable final BillingEvent input) {
                return input != null && input.getSubscriptionId().equals(subscription1.getId());
            }
        });
        final Iterator<BillingEvent> it1 = s1Events.iterator();
        assertEquals(it1.next(), A);
        assertEquals(it1.next().getTransitionType(), SubscriptionBaseTransitionType.START_BILLING_DISABLED);
        assertEquals(it1.next().getTransitionType(), SubscriptionBaseTransitionType.END_BILLING_DISABLED);

        final Iterable<BillingEvent> s2Events = Iterables.filter(billingEvents, new Predicate<BillingEvent>() {
            @Override
            public boolean apply(@Nullable final BillingEvent input) {
                return input != null && input.getSubscriptionId().equals(subscription2.getId());
            }
        });
        final Iterator<BillingEvent> it2 = s2Events.iterator();
        assertEquals(it2.next(), B);
        assertEquals(it2.next().getTransitionType(), SubscriptionBaseTransitionType.START_BILLING_DISABLED);
        assertEquals(it2.next().getTransitionType(), SubscriptionBaseTransitionType.END_BILLING_DISABLED);

        final Iterable<BillingEvent> s3Events = Iterables.filter(billingEvents, new Predicate<BillingEvent>() {
            @Override
            public boolean apply(@Nullable final BillingEvent input) {
                return input != null && input.getSubscriptionId().equals(subscription3.getId());
            }
        });
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
        billingEvents.add(createRealEvent(subscription1, now.minusDays(1)));

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
        final BillingEvent e1 = createRealEvent(subscription1, now.minusDays(1));
        final BillingEvent e2 = createRealEvent(subscription1, now.plusDays(1));
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
        final BillingEvent e1 = createRealEvent(subscription1, now.plusDays(1));
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
        final BillingEvent e1 = createRealEvent(subscription1, now);
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
        final BillingEvent e1 = createRealEvent(subscription1, now.minusDays(1));
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
        final BillingEvent e1 = createRealEvent(subscription1, now.minusDays(1));
        final BillingEvent e2 = createRealEvent(subscription1, now.plusDays(1));
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
        final BillingEvent e1 = createRealEvent(subscription1, now.minusDays(1));
        final BillingEvent e2 = createRealEvent(subscription1, now.plusDays(1));
        final BillingEvent e3 = createRealEvent(subscription1, now.plusDays(3));
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
        final BillingEvent e2 = createRealEvent(subscription1, now.plusDays(1));
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

        final BillingEvent e2 = createRealEvent(subscription1, now.plusDays(1));
        final BillingEvent e3 = createRealEvent(subscription1, now.plusDays(3));
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

        final BillingEvent e3 = createRealEvent(subscription1, now.plusDays(3));

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
        billingEvents.add(createRealEvent(subscription1, now.minusDays(1)));

        final SortedSet<BillingEvent> results = blockingCalculator.createNewEvents(disabledDuration, billingEvents, internalCallContext);

        assertEquals(results.size(), 1);
        assertEquals(results.first().getEffectiveDate(), now);
        assertNull(results.first().getFixedPrice());
        assertNull(results.first().getRecurringPrice());
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
        billingEvents.add(createRealEvent(subscription1, now.minusDays(1)));
        billingEvents.add(createRealEvent(subscription1, now.plusDays(1)));

        final SortedSet<BillingEvent> results = blockingCalculator.createNewEvents(disabledDuration, billingEvents, internalCallContext);

        assertEquals(results.size(), 1);
        assertEquals(results.first().getEffectiveDate(), now);
        assertNull(results.first().getFixedPrice());
        assertNull(results.first().getRecurringPrice());
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
        billingEvents.add(createRealEvent(subscription1, now.plusDays(1)));

        final SortedSet<BillingEvent> results = blockingCalculator.createNewEvents(disabledDuration, billingEvents, internalCallContext);

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
        billingEvents.add(createRealEvent(subscription1, now));

        final SortedSet<BillingEvent> results = blockingCalculator.createNewEvents(disabledDuration, billingEvents, internalCallContext);

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
        billingEvents.add(createRealEvent(subscription1, now.minusDays(1)));

        final SortedSet<BillingEvent> results = blockingCalculator.createNewEvents(disabledDuration, billingEvents, internalCallContext);

        assertEquals(results.size(), 2);
        assertEquals(results.first().getEffectiveDate(), now);
        assertNull(results.first().getFixedPrice());
        assertNull(results.first().getRecurringPrice());
        assertEquals(results.first().getBillingPeriod(), BillingPeriod.NO_BILLING_PERIOD);
        assertEquals(results.first().getTransitionType(), SubscriptionBaseTransitionType.START_BILLING_DISABLED);
        assertEquals(results.last().getEffectiveDate(), now.plusDays(2));
        assertEquals(results.last().getRecurringPrice(), billingEvents.first().getRecurringPrice());
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
        billingEvents.add(createRealEvent(subscription1, now.minusDays(1)));
        billingEvents.add(createRealEvent(subscription1, now.plusDays(1)));

        final SortedSet<BillingEvent> results = blockingCalculator.createNewEvents(disabledDuration, billingEvents, internalCallContext);

        assertEquals(results.size(), 2);
        assertEquals(results.first().getEffectiveDate(), now);
        assertNull(results.first().getFixedPrice());
        assertNull(results.first().getRecurringPrice());
        assertEquals(results.first().getBillingPeriod(), BillingPeriod.NO_BILLING_PERIOD);
        assertEquals(results.first().getTransitionType(), SubscriptionBaseTransitionType.START_BILLING_DISABLED);
        assertEquals(results.last().getEffectiveDate(), now.plusDays(2));
        assertEquals(results.last().getRecurringPrice(), billingEvents.first().getRecurringPrice());
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
        billingEvents.add(createRealEvent(subscription1, now.minusDays(1)));
        billingEvents.add(createRealEvent(subscription1, now.plusDays(1)));
        billingEvents.add(createRealEvent(subscription1, now.plusDays(3)));

        final SortedSet<BillingEvent> results = blockingCalculator.createNewEvents(disabledDuration, billingEvents, internalCallContext);

        assertEquals(results.size(), 2);
        assertEquals(results.first().getEffectiveDate(), now);
        assertNull(results.first().getFixedPrice());
        assertNull(results.first().getRecurringPrice());
        assertEquals(results.first().getBillingPeriod(), BillingPeriod.NO_BILLING_PERIOD);
        assertEquals(results.first().getTransitionType(), SubscriptionBaseTransitionType.START_BILLING_DISABLED);
        assertEquals(results.last().getEffectiveDate(), now.plusDays(2));
        assertEquals(results.last().getRecurringPrice(), billingEvents.first().getRecurringPrice());
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
        billingEvents.add(createRealEvent(subscription1, now.plusDays(1)));

        final SortedSet<BillingEvent> results = blockingCalculator.createNewEvents(disabledDuration, billingEvents, internalCallContext);

        assertEquals(results.size(), 1);
        assertEquals(results.last().getEffectiveDate(), now.plusDays(2));
        assertEquals(results.last().getRecurringPrice(), billingEvents.first().getRecurringPrice());
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
        billingEvents.add(createRealEvent(subscription1, now.plusDays(1)));

        final SortedSet<BillingEvent> results = blockingCalculator.createNewEvents(disabledDuration, billingEvents, internalCallContext);

        assertEquals(results.size(), 1);
        assertEquals(results.last().getEffectiveDate(), now.plusDays(2));
        assertEquals(results.last().getRecurringPrice(), billingEvents.first().getRecurringPrice());
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
        billingEvents.add(createRealEvent(subscription1, now.plusDays(3)));

        final SortedSet<BillingEvent> results = blockingCalculator.createNewEvents(disabledDuration, billingEvents, internalCallContext);

        assertEquals(results.size(), 0);
    }

    @Test(groups = "fast")
    public void testPrecedingBillingEventForSubscription() {
        final DateTime now = new DateTime();

        final SortedSet<BillingEvent> events = new TreeSet<BillingEvent>();

        events.add(createRealEvent(subscription1, now.minusDays(10)));
        events.add(createRealEvent(subscription1, now.minusDays(6)));
        events.add(createRealEvent(subscription1, now.minusDays(5)));
        events.add(createRealEvent(subscription1, now.minusDays(1)));

        final BillingEvent minus11 = blockingCalculator.precedingBillingEventForSubscription(now.minusDays(11), events);
        assertNull(minus11);

        final BillingEvent minus5andAHalf = blockingCalculator.precedingBillingEventForSubscription(now.minusDays(5).minusHours(12), events);
        assertNotNull(minus5andAHalf);
        assertEquals(minus5andAHalf.getEffectiveDate(), now.minusDays(6));

    }

    @Test(groups = "fast")
    public void testCreateNewDisableEvent() throws CatalogApiException {
        final DateTime now = clock.getUTCNow();

        final BillingEvent event = createRealEvent(subscription1, now);

        final BillingEvent result = blockingCalculator.createNewDisableEvent(now, event);
        assertEquals(result.getBillCycleDayLocal(), event.getBillCycleDayLocal());
        assertEquals(result.getEffectiveDate(), now);
        assertEquals(result.getPlanPhase(), event.getPlanPhase());
        assertEquals(result.getPlan(), event.getPlan());
        assertNull(result.getFixedPrice());
        assertNull(result.getRecurringPrice());
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
        final BillingEvent event = createRealEvent(subscription1, now);

        final BillingEvent result = blockingCalculator.createNewReenableEvent(now, event);
        assertEquals(result.getBillCycleDayLocal(), event.getBillCycleDayLocal());
        assertEquals(result.getEffectiveDate(), now);
        assertEquals(result.getPlanPhase(), event.getPlanPhase());
        assertEquals(result.getPlan(), event.getPlan());
        assertEquals(result.getFixedPrice(), event.getFixedPrice());
        assertEquals(result.getRecurringPrice(), event.getRecurringPrice());
        assertEquals(result.getCurrency(), event.getCurrency());
        assertEquals(result.getDescription(), "");
        assertEquals(result.getBillingPeriod(), event.getBillingPeriod());
        assertEquals(result.getTransitionType(), SubscriptionBaseTransitionType.END_BILLING_DISABLED);
        // TODO - ugly, fragile
        assertEquals(result.getTotalOrdering(), (Long) (BlockingCalculator.getGlobalTotalOrder().get() - 1));
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

        final BillingEvent trial = createRealEvent(subscription1, new LocalDate(2012, 5, 1).toDateTimeAtStartOfDay(DateTimeZone.UTC), SubscriptionBaseTransitionType.CREATE);
        final BillingEvent phase = createRealEvent(subscription1, new LocalDate(2012, 5, 31).toDateTimeAtStartOfDay(DateTimeZone.UTC), SubscriptionBaseTransitionType.PHASE);
        final BillingEvent upgrade = createRealEvent(subscription1, new LocalDate(2012, 7, 25).toDateTimeAtStartOfDay(DateTimeZone.UTC), SubscriptionBaseTransitionType.CHANGE);
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

        blockingCalculator.insertBlockingEvents(billingEvents, new HashSet<UUID>(), subscriptionsForAccount, catalog, null, internalCallContext);

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
        assertEquals(events.get(4).getTransitionType(), SubscriptionBaseTransitionType.CHANGE);
    }

    private BillingEvent createBillingEvent(final SubscriptionBase subscription, final Long totalOrdering) {
        return createRealEvent(subscription, new DateTime(), SubscriptionBaseTransitionType.CREATE, totalOrdering);
    }

    protected BillingEvent createRealEvent(final SubscriptionBase subscription, final DateTime effectiveDate) {
        return createRealEvent(subscription, effectiveDate, SubscriptionBaseTransitionType.CHANGE);
    }

    protected BillingEvent createRealEvent(final SubscriptionBase subscription, final DateTime effectiveDate, final SubscriptionBaseTransitionType type) {
        return createRealEvent(subscription, effectiveDate, type, 0L);
    }

    private BillingEvent createRealEvent(final SubscriptionBase subscription, final DateTime effectiveDate, final SubscriptionBaseTransitionType type, final Long totalOrdering) {
        try {

            final Integer billCycleDay = 1;
            final Plan plan = new MockPlan();
            final Currency currency = Currency.USD;
            final String description = "";
            final BillingPeriod billingPeriod = BillingPeriod.MONTHLY;

            final PlanPhase planPhase = Mockito.mock(PlanPhase.class);

            final InternationalPrice internationalPrice = Mockito.mock(InternationalPrice.class);

            Mockito.when(internationalPrice.getPrice(Mockito.<Currency>any())).thenReturn(BigDecimal.TEN);
            final Recurring recurring = Mockito.mock(Recurring.class);
            Mockito.when(recurring.getRecurringPrice()).thenReturn(internationalPrice);
            Mockito.when(planPhase.getRecurring()).thenReturn(recurring);
            Mockito.when(planPhase.getUsages()).thenReturn(new DefaultUsage[0]);

            final BigDecimal fixedPrice = BigDecimal.TEN;
            final BigDecimal recurringPrice = BigDecimal.TEN;

            return new DefaultBillingEvent(subscription.getId(),
                                           subscription.getBundleId(),
                                           effectiveDate,
                                           plan,
                                           planPhase,
                                           fixedPrice,
                                           recurringPrice,
                                           ImmutableList.of(),
                                           currency,
                                           billingPeriod,
                                           billCycleDay,
                                           description,
                                           totalOrdering,
                                           type,
                                           false
            );

        } catch (final CatalogApiException e) {
            Assert.fail("", e);
        }
        throw new IllegalStateException();
    }

}
