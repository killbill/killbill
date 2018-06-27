/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.junction.plumbing.billing;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.DefaultPrice;
import org.killbill.billing.catalog.MockInternationalPrice;
import org.killbill.billing.catalog.MockPlan;
import org.killbill.billing.catalog.MockPlanPhase;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.junction.JunctionTestSuiteNoDB;
import org.killbill.billing.mock.MockAccountBuilder;
import org.killbill.billing.junction.BillingEvent;

public class TestDefaultBillingEvent extends JunctionTestSuiteNoDB {

    private static final UUID ID_ZERO = new UUID(0L, 0L);
    private static final UUID ID_ONE = new UUID(0L, 1L);
    private static final UUID ID_TWO = new UUID(0L, 2L);

    @Test(groups = "fast")
    public void testEntitlementEventsHappeningAtTheSameTimeAsOverdueEvents() throws Exception {
        final BillingEvent event0 = createEvent(subscription(ID_ZERO), new DateTime("2012-01-31T00:02:04.000Z"), SubscriptionBaseTransitionType.START_BILLING_DISABLED);
        final BillingEvent event1 = createEvent(subscription(ID_ZERO), new DateTime("2012-01-31T00:02:04.000Z"), SubscriptionBaseTransitionType.CREATE);
        final BillingEvent event2 = createEvent(subscription(ID_ZERO), new DateTime("2012-01-31T00:02:05.000Z"), SubscriptionBaseTransitionType.CHANGE);
        final BillingEvent event3 = createEvent(subscription(ID_ZERO), new DateTime("2012-01-31T00:02:05.000Z"), SubscriptionBaseTransitionType.END_BILLING_DISABLED);

        final SortedSet<BillingEvent> set = new TreeSet<BillingEvent>();
        set.add(event0);
        set.add(event1);
        set.add(event2);
        set.add(event3);

        final Iterator<BillingEvent> it = set.iterator();

        Assert.assertEquals(event1, it.next());
        Assert.assertEquals(event0, it.next());
        Assert.assertEquals(event3, it.next());
        Assert.assertEquals(event2, it.next());
    }

    @Test(groups = "fast")
    public void testEdgeCaseAllEventsHappenAtTheSameTime() throws Exception {
        final BillingEvent event0 = createEvent(subscription(ID_ZERO), new DateTime("2012-01-31T00:02:04.000Z"), SubscriptionBaseTransitionType.START_BILLING_DISABLED);
        final BillingEvent event1 = createEvent(subscription(ID_ZERO), new DateTime("2012-01-31T00:02:04.000Z"), SubscriptionBaseTransitionType.CREATE, 1);
        final BillingEvent event2 = createEvent(subscription(ID_ZERO), new DateTime("2012-01-31T00:02:04.000Z"), SubscriptionBaseTransitionType.CHANGE, 2);
        // Note the time delta here. Having a blocking duration of zero and events at the same time won't work as the backing tree set does local
        // comparisons (and not global), making the END_BILLING_DISABLED start the first one in the set
        final BillingEvent event3 = createEvent(subscription(ID_ZERO), new DateTime("2012-01-31T00:02:05.000Z"), SubscriptionBaseTransitionType.END_BILLING_DISABLED);

        final SortedSet<BillingEvent> set = new TreeSet<BillingEvent>();
        set.add(event0);
        set.add(event1);
        set.add(event2);
        set.add(event3);

        final Iterator<BillingEvent> it = set.iterator();

        Assert.assertEquals(event1, it.next());
        Assert.assertEquals(event2, it.next());
        Assert.assertEquals(event0, it.next());
        Assert.assertEquals(event3, it.next());
    }

    @Test(groups = "fast")
    public void testEventOrderingSubscription() throws CatalogApiException {
        final BillingEvent event0 = createEvent(subscription(ID_ZERO), new DateTime("2012-01-31T00:02:04.000Z"), SubscriptionBaseTransitionType.CREATE);
        final BillingEvent event1 = createEvent(subscription(ID_ONE), new DateTime("2012-01-31T00:02:04.000Z"), SubscriptionBaseTransitionType.CREATE);
        final BillingEvent event2 = createEvent(subscription(ID_TWO), new DateTime("2012-01-31T00:02:04.000Z"), SubscriptionBaseTransitionType.CREATE);

        final SortedSet<BillingEvent> set = new TreeSet<BillingEvent>();
        set.add(event2);
        set.add(event1);
        set.add(event0);

        final Iterator<BillingEvent> it = set.iterator();

        Assert.assertEquals(event0, it.next());
        Assert.assertEquals(event1, it.next());
        Assert.assertEquals(event2, it.next());
    }

    @Test(groups = "fast")
    public void testEventOrderingDate() throws CatalogApiException {
        final BillingEvent event0 = createEvent(subscription(ID_ZERO), new DateTime("2012-01-01T00:02:04.000Z"), SubscriptionBaseTransitionType.CREATE);
        final BillingEvent event1 = createEvent(subscription(ID_ZERO), new DateTime("2012-02-01T00:02:04.000Z"), SubscriptionBaseTransitionType.CREATE);
        final BillingEvent event2 = createEvent(subscription(ID_ZERO), new DateTime("2012-03-01T00:02:04.000Z"), SubscriptionBaseTransitionType.CREATE);

        final SortedSet<BillingEvent> set = new TreeSet<BillingEvent>();
        set.add(event2);
        set.add(event1);
        set.add(event0);

        final Iterator<BillingEvent> it = set.iterator();

        Assert.assertEquals(event0, it.next());
        Assert.assertEquals(event1, it.next());
        Assert.assertEquals(event2, it.next());
    }

    @Test(groups = "fast")
    public void testEventTotalOrdering() throws CatalogApiException {
        final BillingEvent event0 = createEvent(subscription(ID_ZERO), new DateTime("2012-01-01T00:02:04.000Z"), SubscriptionBaseTransitionType.CREATE, 1L);
        final BillingEvent event1 = createEvent(subscription(ID_ZERO), new DateTime("2012-01-01T00:02:04.000Z"), SubscriptionBaseTransitionType.CANCEL, 2L);
        final BillingEvent event2 = createEvent(subscription(ID_ZERO), new DateTime("2012-01-01T00:02:04.000Z"), SubscriptionBaseTransitionType.CANCEL, 3L);

        final SortedSet<BillingEvent> set = new TreeSet<BillingEvent>();
        set.add(event2);
        set.add(event1);
        set.add(event0);

        final Iterator<BillingEvent> it = set.iterator();

        Assert.assertEquals(event0, it.next());
        Assert.assertEquals(event1, it.next());
        Assert.assertEquals(event2, it.next());
    }

    @Test(groups = "fast")
    public void testEventOrderingMix() throws CatalogApiException {
        final BillingEvent event0 = createEvent(subscription(ID_ZERO), new DateTime("2012-01-01T00:02:04.000Z"), SubscriptionBaseTransitionType.CREATE);
        final BillingEvent event1 = createEvent(subscription(ID_ZERO), new DateTime("2012-01-02T00:02:04.000Z"), SubscriptionBaseTransitionType.CHANGE);
        final BillingEvent event2 = createEvent(subscription(ID_ONE), new DateTime("2012-01-01T00:02:04.000Z"), SubscriptionBaseTransitionType.CANCEL);

        final SortedSet<BillingEvent> set = new TreeSet<BillingEvent>();
        set.add(event2);
        set.add(event1);
        set.add(event0);

        final Iterator<BillingEvent> it = set.iterator();

        Assert.assertEquals(event0, it.next());
        Assert.assertEquals(event1, it.next());
        Assert.assertEquals(event2, it.next());
    }

    @Test(groups = "fast")
    public void testToString() throws Exception {
        // Simple test to ensure we have an easy to read toString representation
        final BillingEvent event = createEvent(subscription(ID_ZERO), new DateTime("2012-01-01T00:02:04.000Z", DateTimeZone.UTC), SubscriptionBaseTransitionType.CREATE);
        Assert.assertEquals(event.toString(), "DefaultBillingEvent{type=CREATE, effectiveDate=2012-01-01T00:02:04.000Z, planPhaseName=Test-trial, subscriptionId=00000000-0000-0000-0000-000000000000, totalOrdering=1}");
    }

    private BillingEvent createEvent(final SubscriptionBase sub, final DateTime effectiveDate, final SubscriptionBaseTransitionType type) throws CatalogApiException {
        return createEvent(sub, effectiveDate, type, 1L);
    }

    private BillingEvent createEvent(final SubscriptionBase sub, final DateTime effectiveDate, final SubscriptionBaseTransitionType type, final long totalOrdering) throws CatalogApiException {
        final int billCycleDay = 1;

        final Plan shotgun = new MockPlan();
        final PlanPhase shotgunMonthly = createMockMonthlyPlanPhase(null, BigDecimal.ZERO, PhaseType.TRIAL);

        return new DefaultBillingEvent(sub, effectiveDate, true,
                                       shotgun, shotgunMonthly, BigDecimal.ZERO,
                                       Currency.USD, BillingPeriod.NO_BILLING_PERIOD, billCycleDay,
                                       "Test Event 1", totalOrdering, type, null, false);
    }

    private MockPlanPhase createMockMonthlyPlanPhase(@Nullable final BigDecimal recurringRate,
                                                     final BigDecimal fixedRate, final PhaseType phaseType) {
        return new MockPlanPhase(new MockInternationalPrice(new DefaultPrice(recurringRate, Currency.USD)),
                                 new MockInternationalPrice(new DefaultPrice(fixedRate, Currency.USD)),
                                 BillingPeriod.MONTHLY, phaseType);
    }

    private SubscriptionBase subscription(final UUID id) {
        final SubscriptionBase subscription = Mockito.mock(SubscriptionBase.class);
        Mockito.when(subscription.getId()).thenReturn(id);
        return subscription;
    }
}
