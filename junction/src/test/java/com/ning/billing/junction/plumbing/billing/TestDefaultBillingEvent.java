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
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.DefaultPrice;
import com.ning.billing.catalog.MockInternationalPrice;
import com.ning.billing.catalog.MockPlan;
import com.ning.billing.catalog.MockPlanPhase;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;

import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.entitlement.api.user.Subscription;

import com.ning.billing.entitlement.api.billing.BillingEvent;
import com.ning.billing.entitlement.api.billing.BillingModeType;
import com.ning.billing.junction.plumbing.billing.DefaultBillingEvent;

import com.ning.billing.mock.BrainDeadProxyFactory;
import com.ning.billing.mock.BrainDeadProxyFactory.ZombieControl;

public class TestDefaultBillingEvent {
	public static final UUID ID_ZERO = new UUID(0L,0L);
	public static final UUID ID_ONE = new UUID(0L,1L);
	public static final UUID ID_TWO = new UUID(0L,2L);

	@Test(groups={"fast"})
	public void testEventOrderingSubscription() {

		BillingEvent event0 = createEvent(subscription(ID_ZERO), new DateTime("2012-01-31T00:02:04.000Z"), SubscriptionTransitionType.CREATE);
		BillingEvent event1 = createEvent(subscription(ID_ONE), new DateTime("2012-01-31T00:02:04.000Z"), SubscriptionTransitionType.CREATE);
		BillingEvent event2 = createEvent(subscription(ID_TWO), new DateTime("2012-01-31T00:02:04.000Z"), SubscriptionTransitionType.CREATE);

		SortedSet<BillingEvent> set = new TreeSet<BillingEvent>();
		set.add(event2);
		set.add(event1);
		set.add(event0);

		Iterator<BillingEvent> it = set.iterator();

		Assert.assertEquals(event0, it.next());
		Assert.assertEquals(event1, it.next());
		Assert.assertEquals(event2, it.next());
	}

	@Test(groups={"fast"})
	public void testEventOrderingDate() {

		BillingEvent event0 = createEvent(subscription(ID_ZERO), new DateTime("2012-01-01T00:02:04.000Z"), SubscriptionTransitionType.CREATE);
		BillingEvent event1 = createEvent(subscription(ID_ZERO), new DateTime("2012-02-01T00:02:04.000Z"), SubscriptionTransitionType.CREATE);
		BillingEvent event2 = createEvent(subscription(ID_ZERO), new DateTime("2012-03-01T00:02:04.000Z"), SubscriptionTransitionType.CREATE);

		SortedSet<BillingEvent> set = new TreeSet<BillingEvent>();
		set.add(event2);
		set.add(event1);
		set.add(event0);

		Iterator<BillingEvent> it = set.iterator();

		Assert.assertEquals(event0, it.next());
		Assert.assertEquals(event1, it.next());
		Assert.assertEquals(event2, it.next());
	}

	@Test(groups={"fast"})
	public void testEventTotalOrdering() {

		BillingEvent event0 = createEvent(subscription(ID_ZERO), new DateTime("2012-01-01T00:02:04.000Z"), SubscriptionTransitionType.CREATE, 1L);
		BillingEvent event1 = createEvent(subscription(ID_ZERO), new DateTime("2012-01-01T00:02:04.000Z"), SubscriptionTransitionType.CANCEL, 2L);
		BillingEvent event2 = createEvent(subscription(ID_ZERO), new DateTime("2012-01-01T00:02:04.000Z"), SubscriptionTransitionType.RE_CREATE, 3L);

		SortedSet<BillingEvent> set = new TreeSet<BillingEvent>();
		set.add(event2);
		set.add(event1);
		set.add(event0);

		Iterator<BillingEvent> it = set.iterator();

		Assert.assertEquals(event0, it.next());
		Assert.assertEquals(event1, it.next());
		Assert.assertEquals(event2, it.next());
	}

	@Test(groups={"fast"})
	public void testEventOrderingMix() {

		BillingEvent event0 = createEvent(subscription(ID_ZERO), new DateTime("2012-01-01T00:02:04.000Z"), SubscriptionTransitionType.CREATE);
		BillingEvent event1 = createEvent(subscription(ID_ZERO), new DateTime("2012-01-02T00:02:04.000Z"), SubscriptionTransitionType.CHANGE);
		BillingEvent event2 = createEvent(subscription(ID_ONE), new DateTime("2012-01-01T00:02:04.000Z"), SubscriptionTransitionType.CANCEL);

		SortedSet<BillingEvent> set = new TreeSet<BillingEvent>();
		set.add(event2);
		set.add(event1);
		set.add(event0);

		Iterator<BillingEvent> it = set.iterator();

		Assert.assertEquals(event0, it.next());
		Assert.assertEquals(event1, it.next());
		Assert.assertEquals(event2, it.next());
	}

    private BillingEvent createEvent(Subscription sub, DateTime effectiveDate, SubscriptionTransitionType type) {
        return createEvent(sub, effectiveDate, type, 1L);
    }

    private BillingEvent createEvent(Subscription sub, DateTime effectiveDate, SubscriptionTransitionType type, long totalOrdering) {
		int billCycleDay = 1;

		Plan shotgun = new MockPlan();
		PlanPhase shotgunMonthly = createMockMonthlyPlanPhase(null, BigDecimal.ZERO, PhaseType.TRIAL);

		return new DefaultBillingEvent(null, sub , effectiveDate,
				shotgun, shotgunMonthly,
				BigDecimal.ZERO, null, Currency.USD, BillingPeriod.NO_BILLING_PERIOD, billCycleDay,
				BillingModeType.IN_ADVANCE, "Test Event 1", totalOrdering, type, DateTimeZone.UTC);
	}

	private MockPlanPhase createMockMonthlyPlanPhase(@Nullable final BigDecimal recurringRate,
			final BigDecimal fixedRate, PhaseType phaseType) {
		return new MockPlanPhase(new MockInternationalPrice(new DefaultPrice(recurringRate, Currency.USD)),
				new MockInternationalPrice(new DefaultPrice(fixedRate, Currency.USD)),
				BillingPeriod.MONTHLY, phaseType);
	}

	private Subscription subscription(final UUID id) {
	    Subscription subscription = BrainDeadProxyFactory.createBrainDeadProxyFor(Subscription.class);
	    ((ZombieControl) subscription).addResult("getId", id);
	    return subscription;
	}

}
