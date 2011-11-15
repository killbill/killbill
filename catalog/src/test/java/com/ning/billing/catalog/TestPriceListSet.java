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

import static com.ning.billing.catalog.api.BillingPeriod.*;
import static com.ning.billing.catalog.api.PhaseType.*;

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
				new MockPlan().setName("plan-foo-monthly").setProduct(foo).setFinalPhase(new MockPlanPhase().setBillCycleDuration(MONTHLY).setPhaseType(EVERGREEN)),
				new MockPlan().setName("plan-bar-monthly").setProduct(bar).setFinalPhase(new MockPlanPhase().setBillCycleDuration(MONTHLY).setPhaseType(EVERGREEN)),
				new MockPlan().setName("plan-foo-annual").setProduct(foo).setFinalPhase(new MockPlanPhase().setBillCycleDuration(ANNUAL).setPhaseType(EVERGREEN)),
				new MockPlan().setName("plan-bar-annual").setProduct(bar).setFinalPhase(new MockPlanPhase().setBillCycleDuration(ANNUAL).setPhaseType(EVERGREEN))
				};
		Plan[] childPlans = new Plan[]{ 
				new MockPlan().setName("plan-foo").setProduct(foo).setFinalPhase(new MockPlanPhase().setBillCycleDuration(ANNUAL).setPhaseType(DISCOUNT)),
				new MockPlan().setName("plan-bar").setProduct(bar).setFinalPhase(new MockPlanPhase().setBillCycleDuration(ANNUAL).setPhaseType(DISCOUNT))
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
	
	public void testForNullBillingPeriod() {
		Product foo = new Product("Foo", ProductCategory.BASE);
		Product bar = new Product("Bar", ProductCategory.BASE);
		Plan[] defaultPlans = new Plan[]{ 
				new MockPlan().setName("plan-foo-monthly").setProduct(foo).setFinalPhase(new MockPlanPhase().setBillCycleDuration(MONTHLY).setPhaseType(EVERGREEN)),
				new MockPlan().setName("plan-bar-monthly").setProduct(bar).setFinalPhase(new MockPlanPhase().setBillCycleDuration(MONTHLY).setPhaseType(EVERGREEN)),
				new MockPlan().setName("plan-foo-annual").setProduct(foo).setFinalPhase(new MockPlanPhase().setBillCycleDuration(null).setPhaseType(EVERGREEN)),
				new MockPlan().setName("plan-bar-annual").setProduct(bar).setFinalPhase(new MockPlanPhase().setBillCycleDuration(null).setPhaseType(EVERGREEN))
				};
		Plan[] childPlans = new Plan[]{ 
				new MockPlan().setName("plan-foo").setProduct(foo).setFinalPhase(new MockPlanPhase().setBillCycleDuration(ANNUAL).setPhaseType(DISCOUNT)),
				new MockPlan().setName("plan-bar").setProduct(bar).setFinalPhase(new MockPlanPhase().setBillCycleDuration(ANNUAL).setPhaseType(DISCOUNT))
				};

		PriceListDefault defaultPriceList = new PriceListDefault(defaultPlans);
		PriceList[] childPriceLists = new PriceList[] {
				new PriceList(childPlans, "child")
		};
		PriceListSet set = new PriceListSet(defaultPriceList, childPriceLists);
		
		Assert.assertEquals(set.getPlanListFrom("child", foo, BillingPeriod.ANNUAL).getFinalPhase().getPhaseType(), PhaseType.DISCOUNT);
		Assert.assertEquals(set.getPlanListFrom("child", foo, BillingPeriod.MONTHLY).getFinalPhase().getPhaseType(), PhaseType.EVERGREEN);
		Assert.assertEquals(set.getPlanListFrom(IPriceListSet.DEFAULT_PRICELIST_NAME, foo, BillingPeriod.ANNUAL).getFinalPhase().getPhaseType(), PhaseType.EVERGREEN);
		Assert.assertEquals(set.getPlanListFrom(IPriceListSet.DEFAULT_PRICELIST_NAME, foo, BillingPeriod.MONTHLY).getFinalPhase().getPhaseType(), PhaseType.EVERGREEN);
	}

}
