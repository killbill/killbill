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

package org.killbill.billing.catalog;

import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;

import static org.killbill.billing.catalog.api.BillingPeriod.ANNUAL;
import static org.killbill.billing.catalog.api.BillingPeriod.MONTHLY;
import static org.killbill.billing.catalog.api.PhaseType.DISCOUNT;
import static org.killbill.billing.catalog.api.PhaseType.EVERGREEN;

public class TestPriceListSet extends CatalogTestSuiteNoDB {

    @Test(groups = "fast")
    public void testOverriding() throws CatalogApiException {
        final DefaultProduct foo = new DefaultProduct("Foo", ProductCategory.BASE);
        final DefaultProduct bar = new DefaultProduct("Bar", ProductCategory.BASE);
        final DefaultPlan[] defaultPlans = new DefaultPlan[]{
                new MockPlan().setName("plan-foo-monthly").setProduct(foo).setFinalPhase(new MockPlanPhase().setRecurring(new MockRecurring(MONTHLY, null)).setPhaseType(EVERGREEN)),
                new MockPlan().setName("plan-bar-monthly").setProduct(bar).setFinalPhase(new MockPlanPhase().setRecurring(new MockRecurring(MONTHLY, null)).setPhaseType(EVERGREEN)),
                new MockPlan().setName("plan-foo-annual").setProduct(foo).setFinalPhase(new MockPlanPhase().setRecurring(new MockRecurring(ANNUAL, null)).setPhaseType(EVERGREEN)),
                new MockPlan().setName("plan-bar-annual").setProduct(bar).setFinalPhase(new MockPlanPhase().setRecurring(new MockRecurring(ANNUAL, null)).setPhaseType(EVERGREEN))
        };
        final DefaultPlan[] childPlans = new DefaultPlan[]{
                new MockPlan().setName("plan-foo").setProduct(foo).setFinalPhase(new MockPlanPhase().setRecurring(new MockRecurring(ANNUAL, null)).setPhaseType(DISCOUNT)),
                new MockPlan().setName("plan-bar").setProduct(bar).setFinalPhase(new MockPlanPhase().setRecurring(new MockRecurring(ANNUAL, null)).setPhaseType(DISCOUNT))
        };
        final PriceListDefault defaultPriceList = new PriceListDefault(defaultPlans);
        final DefaultPriceList[] childPriceLists = new DefaultPriceList[]{
                new DefaultPriceList(childPlans, "child")
        };
        final DefaultPriceListSet set = new DefaultPriceListSet(defaultPriceList, childPriceLists);

        Assert.assertEquals(set.getPlanFrom(foo, BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME).getFinalPhase().getPhaseType(), PhaseType.EVERGREEN);
        Assert.assertEquals(set.getPlanFrom(foo, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME).getFinalPhase().getPhaseType(), PhaseType.EVERGREEN);
        Assert.assertEquals(set.getPlanFrom(foo, BillingPeriod.ANNUAL, "child").getFinalPhase().getPhaseType(), PhaseType.DISCOUNT);
        Assert.assertEquals(set.getPlanFrom(foo, BillingPeriod.MONTHLY, "child").getFinalPhase().getPhaseType(), PhaseType.EVERGREEN);
    }

    @Test(groups = "fast")
    public void testForNullBillingPeriod() throws CatalogApiException {
        final DefaultProduct foo = new DefaultProduct("Foo", ProductCategory.BASE);
        final DefaultProduct bar = new DefaultProduct("Bar", ProductCategory.BASE);
        final DefaultPlan[] defaultPlans = new DefaultPlan[]{
                new MockPlan().setName("plan-foo-monthly").setProduct(foo).setFinalPhase(new MockPlanPhase().setRecurring(new MockRecurring(MONTHLY, null)).setPhaseType(EVERGREEN)),
                new MockPlan().setName("plan-bar-monthly").setProduct(bar).setFinalPhase(new MockPlanPhase().setRecurring(new MockRecurring(MONTHLY, null)).setPhaseType(EVERGREEN)),
                new MockPlan().setName("plan-foo-annual").setProduct(foo).setFinalPhase(new MockPlanPhase().setRecurring(new MockRecurring(ANNUAL, null)).setPhaseType(EVERGREEN)),
                new MockPlan().setName("plan-bar-annual").setProduct(bar).setFinalPhase(new MockPlanPhase().setRecurring(new MockRecurring(ANNUAL, null)).setPhaseType(EVERGREEN))
        };
        final DefaultPlan[] childPlans = new DefaultPlan[]{
                new MockPlan().setName("plan-foo").setProduct(foo).setFinalPhase(new MockPlanPhase().setRecurring(new MockRecurring(ANNUAL, null)).setPhaseType(DISCOUNT)),
                new MockPlan().setName("plan-bar").setProduct(bar).setFinalPhase(new MockPlanPhase().setRecurring(new MockRecurring(ANNUAL, null)).setPhaseType(DISCOUNT))
        };

        final PriceListDefault defaultPriceList = new PriceListDefault(defaultPlans);
        final DefaultPriceList[] childPriceLists = new DefaultPriceList[]{
                new DefaultPriceList(childPlans, "child")
        };
        final DefaultPriceListSet set = new DefaultPriceListSet(defaultPriceList, childPriceLists);

        Assert.assertEquals(set.getPlanFrom(foo, BillingPeriod.ANNUAL, "child").getFinalPhase().getPhaseType(), PhaseType.DISCOUNT);
        Assert.assertEquals(set.getPlanFrom(foo, BillingPeriod.MONTHLY, "child").getFinalPhase().getPhaseType(), PhaseType.EVERGREEN);
        Assert.assertEquals(set.getPlanFrom(foo, BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME).getFinalPhase().getPhaseType(), PhaseType.EVERGREEN);
        Assert.assertEquals(set.getPlanFrom(foo, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME).getFinalPhase().getPhaseType(), PhaseType.EVERGREEN);
    }
}
