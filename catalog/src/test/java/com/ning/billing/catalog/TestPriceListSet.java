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

package com.ning.billing.catalog;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.IPriceListSet;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.ProductCategory;

public class TestPriceListSet {
	@Test(enabled=true)
	public void testOverriding() {
		Product foo = new Product("Foo", ProductCategory.BASE);
		Product bar = new Product("Bar", ProductCategory.BASE);
		Plan[] defaultPlans = new Plan[]{ 
				new Plan("plan-foo-monthly", foo, new PlanPhase(BillingPeriod.MONTHLY, PhaseType.EVERGREEN)),
				new Plan("plan-bar-monthly", bar, new PlanPhase(BillingPeriod.MONTHLY, PhaseType.EVERGREEN)),
				new Plan("plan-foo-annual", foo, new PlanPhase(BillingPeriod.ANNUAL, PhaseType.EVERGREEN)),
				new Plan("plan-bar-annual", bar, new PlanPhase(BillingPeriod.ANNUAL, PhaseType.EVERGREEN))
				};
		Plan[] childPlans = new Plan[]{ 
				new Plan("plan-foo", foo, new PlanPhase(BillingPeriod.ANNUAL, PhaseType.DISCOUNT)),
				new Plan("plan-bar", bar, new PlanPhase(BillingPeriod.ANNUAL, PhaseType.DISCOUNT))
				};
		PriceListDefault defaultPriceList = new PriceListDefault(defaultPlans);
		PriceList[] childPriceLists = new PriceList[] {
				new PriceList(childPlans, "child")
		};
		PriceListSet set = new PriceListSet(defaultPriceList, childPriceLists);
		
		Assert.assertEquals(set.getPlanListFrom(IPriceListSet.DEFAULT_PRICELIST_NAME, foo, BillingPeriod.ANNUAL).getFinalPhase().getPhaseType(), PhaseType.EVERGREEN);
		Assert.assertEquals(set.getPlanListFrom(IPriceListSet.DEFAULT_PRICELIST_NAME, foo, BillingPeriod.MONTHLY).getFinalPhase().getPhaseType(), PhaseType.EVERGREEN);
		Assert.assertEquals(set.getPlanListFrom("child", foo, BillingPeriod.ANNUAL).getFinalPhase().getPhaseType(), PhaseType.DISCOUNT);
		Assert.assertEquals(set.getPlanListFrom("child", foo, BillingPeriod.MONTHLY).getFinalPhase().getPhaseType(), PhaseType.EVERGREEN);
	}
}
