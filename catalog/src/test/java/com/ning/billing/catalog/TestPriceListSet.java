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

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import org.testng.Assert;
import org.testng.annotations.Test;

import static com.ning.billing.catalog.api.BillingPeriod.ANNUAL;
import static com.ning.billing.catalog.api.BillingPeriod.MONTHLY;
import static com.ning.billing.catalog.api.PhaseType.DISCOUNT;
import static com.ning.billing.catalog.api.PhaseType.EVERGREEN;

public class TestPriceListSet {
	@Test(enabled=true)
	public void testOverriding() throws CatalogApiException {
		DefaultProduct foo = new DefaultProduct("Foo", ProductCategory.BASE);
		DefaultProduct bar = new DefaultProduct("Bar", ProductCategory.BASE);
		DefaultPlan[] defaultPlans = new DefaultPlan[]{ 
				new MockPlan().setName("plan-foo-monthly").setProduct(foo).setFinalPhase(new MockPlanPhase().setBillCycleDuration(MONTHLY).setPhaseType(EVERGREEN)),
				new MockPlan().setName("plan-bar-monthly").setProduct(bar).setFinalPhase(new MockPlanPhase().setBillCycleDuration(MONTHLY).setPhaseType(EVERGREEN)),
				new MockPlan().setName("plan-foo-annual").setProduct(foo).setFinalPhase(new MockPlanPhase().setBillCycleDuration(ANNUAL).setPhaseType(EVERGREEN)),
				new MockPlan().setName("plan-bar-annual").setProduct(bar).setFinalPhase(new MockPlanPhase().setBillCycleDuration(ANNUAL).setPhaseType(EVERGREEN))
				};
		DefaultPlan[] childPlans = new DefaultPlan[]{ 
				new MockPlan().setName("plan-foo").setProduct(foo).setFinalPhase(new MockPlanPhase().setBillCycleDuration(ANNUAL).setPhaseType(DISCOUNT)),
				new MockPlan().setName("plan-bar").setProduct(bar).setFinalPhase(new MockPlanPhase().setBillCycleDuration(ANNUAL).setPhaseType(DISCOUNT))
				};
		PriceListDefault defaultPriceList = new PriceListDefault(defaultPlans);
		DefaultPriceList[] childPriceLists = new DefaultPriceList[] {
				new DefaultPriceList(childPlans, "child")
		};
		DefaultPriceListSet set = new DefaultPriceListSet(defaultPriceList, childPriceLists);
		
		Assert.assertEquals(set.getPlanListFrom(PriceListSet.DEFAULT_PRICELIST_NAME, foo, BillingPeriod.ANNUAL).getFinalPhase().getPhaseType(), PhaseType.EVERGREEN);
		Assert.assertEquals(set.getPlanListFrom(PriceListSet.DEFAULT_PRICELIST_NAME, foo, BillingPeriod.MONTHLY).getFinalPhase().getPhaseType(), PhaseType.EVERGREEN);
		Assert.assertEquals(set.getPlanListFrom("child", foo, BillingPeriod.ANNUAL).getFinalPhase().getPhaseType(), PhaseType.DISCOUNT);
		Assert.assertEquals(set.getPlanListFrom("child", foo, BillingPeriod.MONTHLY).getFinalPhase().getPhaseType(), PhaseType.EVERGREEN);
	}
	
	public void testForNullBillingPeriod() throws CatalogApiException {
		DefaultProduct foo = new DefaultProduct("Foo", ProductCategory.BASE);
		DefaultProduct bar = new DefaultProduct("Bar", ProductCategory.BASE);
		DefaultPlan[] defaultPlans = new DefaultPlan[]{ 
				new MockPlan().setName("plan-foo-monthly").setProduct(foo).setFinalPhase(new MockPlanPhase().setBillCycleDuration(MONTHLY).setPhaseType(EVERGREEN)),
				new MockPlan().setName("plan-bar-monthly").setProduct(bar).setFinalPhase(new MockPlanPhase().setBillCycleDuration(MONTHLY).setPhaseType(EVERGREEN)),
				new MockPlan().setName("plan-foo-annual").setProduct(foo).setFinalPhase(new MockPlanPhase().setBillCycleDuration(null).setPhaseType(EVERGREEN)),
				new MockPlan().setName("plan-bar-annual").setProduct(bar).setFinalPhase(new MockPlanPhase().setBillCycleDuration(null).setPhaseType(EVERGREEN))
				};
		DefaultPlan[] childPlans = new DefaultPlan[]{ 
				new MockPlan().setName("plan-foo").setProduct(foo).setFinalPhase(new MockPlanPhase().setBillCycleDuration(ANNUAL).setPhaseType(DISCOUNT)),
				new MockPlan().setName("plan-bar").setProduct(bar).setFinalPhase(new MockPlanPhase().setBillCycleDuration(ANNUAL).setPhaseType(DISCOUNT))
				};

		PriceListDefault defaultPriceList = new PriceListDefault(defaultPlans);
		DefaultPriceList[] childPriceLists = new DefaultPriceList[] {
				new DefaultPriceList(childPlans, "child")
		};
		DefaultPriceListSet set = new DefaultPriceListSet(defaultPriceList, childPriceLists);
		
		Assert.assertEquals(set.getPlanListFrom("child", foo, BillingPeriod.ANNUAL).getFinalPhase().getPhaseType(), PhaseType.DISCOUNT);
		Assert.assertEquals(set.getPlanListFrom("child", foo, BillingPeriod.MONTHLY).getFinalPhase().getPhaseType(), PhaseType.EVERGREEN);
		Assert.assertEquals(set.getPlanListFrom(PriceListSet.DEFAULT_PRICELIST_NAME, foo, BillingPeriod.ANNUAL).getFinalPhase().getPhaseType(), PhaseType.EVERGREEN);
		Assert.assertEquals(set.getPlanListFrom(PriceListSet.DEFAULT_PRICELIST_NAME, foo, BillingPeriod.MONTHLY).getFinalPhase().getPhaseType(), PhaseType.EVERGREEN);
	}

}
