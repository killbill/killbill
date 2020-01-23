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

package org.killbill.billing.catalog.rules;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.killbill.billing.catalog.CatalogTestSuiteNoDB;
import org.killbill.billing.catalog.DefaultPriceList;
import org.killbill.billing.catalog.DefaultProduct;
import org.killbill.billing.catalog.MockCatalog;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.IllegalPlanChange;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.PlanAlignmentChange;
import org.killbill.billing.catalog.api.PlanChangeResult;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PlanSpecifier;
import org.killbill.billing.catalog.api.PriceListSet;

public class TestPlanRules extends CatalogTestSuiteNoDB {

    private MockCatalog cat = null;

    @BeforeMethod(groups = "fast")
    public void beforeMethod() {
        cat = new MockCatalog();

        final DefaultPriceList priceList2 = cat.getPriceLists().getChildPriceLists()[0];

        final DefaultCaseChangePlanPolicy casePolicy = new DefaultCaseChangePlanPolicy().setPolicy(BillingActionPolicy.END_OF_TERM);
        final DefaultCaseChangePlanAlignment caseAlignment = new DefaultCaseChangePlanAlignment().setAlignment(PlanAlignmentChange.START_OF_SUBSCRIPTION);
        final DefaultCasePriceList casePriceList = new DefaultCasePriceList().setToPriceList(priceList2);

        ((DefaultPlanRules) cat.getPlanRules()).
                setChangeCase(new DefaultCaseChangePlanPolicy[]{casePolicy}).
                   setChangeAlignmentCase(new DefaultCaseChangePlanAlignment[]{caseAlignment}).
                   setPriceListCase(new DefaultCasePriceList[]{casePriceList});
    }

    @Test(groups = "fast")
    public void testExistingPriceListIsKept() throws CatalogApiException {
        final DefaultProduct product1 = cat.getCurrentProduct(0);
        final DefaultPriceList priceList1 = (DefaultPriceList) cat.findPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final PlanPhaseSpecifier from = new PlanPhaseSpecifier(product1.getName(), BillingPeriod.MONTHLY, priceList1.getName(), PhaseType.EVERGREEN);
        final PlanSpecifier to = new PlanSpecifier(product1.getName(), BillingPeriod.ANNUAL, priceList1.getName());

        PlanChangeResult result = null;
        try {
            result = ((DefaultPlanRules) cat.getPlanRules()).getPlanChangeResult(from, to);
        } catch (IllegalPlanChange e) {
            Assert.fail("We should not have triggered this error");
        } catch (CatalogApiException e) {
            Assert.fail("", e);
        }

        Assert.assertEquals(result.getPolicy(), BillingActionPolicy.END_OF_TERM);
        Assert.assertEquals(result.getAlignment(), PlanAlignmentChange.START_OF_SUBSCRIPTION);
        Assert.assertEquals(result.getNewPriceList(), priceList1);
    }

    @Test(groups = "fast")
    public void testBaseCase() throws CatalogApiException {
        final DefaultProduct product1 = cat.getCurrentProduct(0);
        final DefaultProduct product2 = cat.getCurrentProduct(1);
        final DefaultPriceList priceList1 = (DefaultPriceList) cat.findPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);
        final DefaultPriceList priceList2 = cat.getPriceLists().getChildPriceLists()[0];

        final PlanPhaseSpecifier from = new PlanPhaseSpecifier(product1.getName(), BillingPeriod.MONTHLY, priceList1.getName(), PhaseType.EVERGREEN);
        final PlanSpecifier to = new PlanSpecifier(product2.getName(), BillingPeriod.MONTHLY, null);

        PlanChangeResult result = null;
        try {
            result = cat.getPlanRules().getPlanChangeResult(from, to);
        } catch (IllegalPlanChange e) {
            Assert.fail("We should not have triggered this error");
        } catch (CatalogApiException e) {
            Assert.fail("", e);
        }

        Assert.assertEquals(result.getPolicy(), BillingActionPolicy.END_OF_TERM);
        Assert.assertEquals(result.getAlignment(), PlanAlignmentChange.START_OF_SUBSCRIPTION);
        Assert.assertEquals(result.getNewPriceList(), priceList2);
    }
}
