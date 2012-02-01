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

package com.ning.billing.entitlement.api.billing;

import java.math.BigDecimal;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.testng.annotations.Test;

import com.ning.billing.catalog.DefaultPrice;
import com.ning.billing.catalog.MockInternationalPrice;
import com.ning.billing.catalog.MockPlan;
import com.ning.billing.catalog.MockPlanPhase;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.InternationalPrice;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionTransition.SubscriptionTransitionType;

public class TestDefaultBillingEvent {

	@Test(groups={"fast"})
	public void testEventOrdering() {
		Subscription subscription = new BrainDeadSubscription();
		;
		BillingEvent event1 = createEvent(subscription, new DateTime("2012-01-31T00:02:04.000Z"), SubscriptionTransitionType.CREATE);
		
		SortedSet<BillingEvent> set = new TreeSet<BillingEvent>();
		
		set.add(event1);
		
	}
	
	private BillingEvent createEvent(Subscription sub, DateTime effectiveDate, SubscriptionTransitionType type) {
		InternationalPrice zeroPrice = new MockInternationalPrice(new DefaultPrice(BigDecimal.ZERO, Currency.USD));
		int billCycleDay = 1;

		Plan shotgun = new MockPlan();
		PlanPhase shotgunMonthly = createMockMonthlyPlanPhase(null, BigDecimal.ZERO, PhaseType.TRIAL);
		
		return new DefaultBillingEvent(sub , effectiveDate,
				shotgun, shotgunMonthly,
				zeroPrice, null, BillingPeriod.NO_BILLING_PERIOD, billCycleDay,
				BillingModeType.IN_ADVANCE, "Test Event 1", type);
	}

	private MockPlanPhase createMockMonthlyPlanPhase(@Nullable final BigDecimal recurringRate,
			final BigDecimal fixedRate, PhaseType phaseType) {
		return new MockPlanPhase(new MockInternationalPrice(new DefaultPrice(recurringRate, Currency.USD)),
				new MockInternationalPrice(new DefaultPrice(fixedRate, Currency.USD)),
				BillingPeriod.MONTHLY, phaseType);
	}

}
