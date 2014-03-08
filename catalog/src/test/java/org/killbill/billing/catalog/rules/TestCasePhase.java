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

import javax.xml.bind.annotation.XmlElement;

import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.catalog.CatalogTestSuiteNoDB;
import org.killbill.billing.catalog.DefaultPriceList;
import org.killbill.billing.catalog.DefaultProduct;
import org.killbill.billing.catalog.MockCatalog;
import org.killbill.billing.catalog.StandaloneCatalog;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.ProductCategory;

public class TestCasePhase extends CatalogTestSuiteNoDB {

    protected class CaseResult extends CasePhase<Result> {

        @XmlElement(required = true)
        private final Result policy;

        public CaseResult(final DefaultProduct product, final ProductCategory productCategory, final BillingPeriod billingPeriod, final DefaultPriceList priceList,
                          final PhaseType phaseType, final Result policy) {
            setProduct(product);
            setProductCategory(productCategory);
            setBillingPeriod(billingPeriod);
            setPriceList(priceList);
            setPhaseType(phaseType);

            this.policy = policy;
        }

        @Override
        protected Result getResult() {
            return policy;
        }
    }

    @Test(groups = "fast")
    public void testBasic() {
        final MockCatalog cat = new MockCatalog();

        final DefaultProduct product = cat.getCurrentProducts()[0];
        final DefaultPriceList priceList = cat.getPriceLists().getDefaultPricelist();

        final CaseResult cr = new CaseResult(
                product,
                ProductCategory.BASE,
                BillingPeriod.MONTHLY,
                priceList,
                PhaseType.EVERGREEN,
                Result.FOO);

        assertion(Result.FOO, cr, product.getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), PhaseType.EVERGREEN, cat);
        assertionNull(cr, cat.getCurrentProducts()[1].getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), PhaseType.EVERGREEN, cat);
        assertionNull(cr, product.getName(), ProductCategory.ADD_ON, BillingPeriod.MONTHLY, priceList.getName(), PhaseType.EVERGREEN, cat);
        assertionNull(cr, product.getName(), ProductCategory.BASE, BillingPeriod.ANNUAL, priceList.getName(), PhaseType.EVERGREEN, cat);
        assertionException(cr, product.getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, "dipsy", PhaseType.EVERGREEN, cat);
        assertionNull(cr, product.getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), PhaseType.TRIAL, cat);
    }

    @Test(groups = "fast")
    public void testWildCardProduct() {
        final MockCatalog cat = new MockCatalog();

        final DefaultProduct product = cat.getCurrentProducts()[0];
        final DefaultPriceList priceList = cat.getPriceLists().getDefaultPricelist();

        final CaseResult cr = new CaseResult(
                null,
                ProductCategory.BASE,
                BillingPeriod.MONTHLY,
                priceList,
                PhaseType.EVERGREEN,
                Result.FOO);

        assertion(Result.FOO, cr, product.getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), PhaseType.EVERGREEN, cat);
        assertion(Result.FOO, cr, cat.getCurrentProducts()[1].getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), PhaseType.EVERGREEN, cat);
        assertionNull(cr, product.getName(), ProductCategory.ADD_ON, BillingPeriod.MONTHLY, priceList.getName(), PhaseType.EVERGREEN, cat);
        assertionNull(cr, product.getName(), ProductCategory.BASE, BillingPeriod.ANNUAL, priceList.getName(), PhaseType.EVERGREEN, cat);
        assertionException(cr, product.getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, "dipsy", PhaseType.EVERGREEN, cat);
        assertionNull(cr, product.getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), PhaseType.TRIAL, cat);
    }

    @Test(groups = "fast")
    public void testWildCardProductCategory() {
        final MockCatalog cat = new MockCatalog();

        final DefaultProduct product = cat.getCurrentProducts()[0];
        final DefaultPriceList priceList = cat.getPriceLists().getDefaultPricelist();

        final CaseResult cr = new CaseResult(
                product,
                null,
                BillingPeriod.MONTHLY,
                priceList,
                PhaseType.EVERGREEN,
                Result.FOO);

        assertion(Result.FOO, cr, product.getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), PhaseType.EVERGREEN, cat);
        assertionNull(cr, cat.getCurrentProducts()[1].getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), PhaseType.EVERGREEN, cat);
        assertion(Result.FOO, cr, product.getName(), ProductCategory.ADD_ON, BillingPeriod.MONTHLY, priceList.getName(), PhaseType.EVERGREEN, cat);
        assertionNull(cr, product.getName(), ProductCategory.BASE, BillingPeriod.ANNUAL, priceList.getName(), PhaseType.EVERGREEN, cat);
        assertionException(cr, product.getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, "dipsy", PhaseType.EVERGREEN, cat);
        assertionNull(cr, product.getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), PhaseType.TRIAL, cat);
    }

    @Test(groups = "fast")
    public void testWildCardBillingPeriod() {
        final MockCatalog cat = new MockCatalog();

        final DefaultProduct product = cat.getCurrentProducts()[0];
        final DefaultPriceList priceList = cat.getPriceLists().getDefaultPricelist();

        final CaseResult cr = new CaseResult(
                product,
                ProductCategory.BASE,
                null,
                priceList,
                PhaseType.EVERGREEN,
                Result.FOO);

        assertion(Result.FOO, cr, product.getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), PhaseType.EVERGREEN, cat);
        assertionNull(cr, cat.getCurrentProducts()[1].getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), PhaseType.EVERGREEN, cat);
        assertionNull(cr, product.getName(), ProductCategory.ADD_ON, BillingPeriod.MONTHLY, priceList.getName(), PhaseType.EVERGREEN, cat);
        assertion(Result.FOO, cr, product.getName(), ProductCategory.BASE, BillingPeriod.ANNUAL, priceList.getName(), PhaseType.EVERGREEN, cat);
        assertionException(cr, product.getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, "dipsy", PhaseType.EVERGREEN, cat);
        assertionNull(cr, product.getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), PhaseType.TRIAL, cat);
    }

    @Test(groups = "fast")
    public void testWildCardPriceList() {
        final MockCatalog cat = new MockCatalog();

        final DefaultProduct product = cat.getCurrentProducts()[0];
        final DefaultPriceList priceList = cat.getPriceLists().getDefaultPricelist();

        final CaseResult cr = new CaseResult(
                product,
                ProductCategory.BASE,
                BillingPeriod.MONTHLY,
                null,
                PhaseType.EVERGREEN,
                Result.FOO);

        assertion(Result.FOO, cr, product.getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), PhaseType.EVERGREEN, cat);
        assertionNull(cr, cat.getCurrentProducts()[1].getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), PhaseType.EVERGREEN, cat);
        assertionNull(cr, product.getName(), ProductCategory.ADD_ON, BillingPeriod.MONTHLY, priceList.getName(), PhaseType.EVERGREEN, cat);
        assertionNull(cr, product.getName(), ProductCategory.BASE, BillingPeriod.ANNUAL, priceList.getName(), PhaseType.EVERGREEN, cat);
        assertion(Result.FOO, cr, product.getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, "dipsy", PhaseType.EVERGREEN, cat);
        assertionNull(cr, product.getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), PhaseType.TRIAL, cat);
    }

    @Test(groups = "fast")
    public void testWildCardPhaseType() {
        final MockCatalog cat = new MockCatalog();

        final DefaultProduct product = cat.getCurrentProducts()[0];
        final DefaultPriceList priceList = cat.getPriceLists().getDefaultPricelist();

        final CaseResult cr = new CaseResult(
                product,
                ProductCategory.BASE,
                BillingPeriod.MONTHLY,
                priceList,
                null,
                Result.FOO);

        assertion(Result.FOO, cr, product.getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), PhaseType.EVERGREEN, cat);
        assertionNull(cr, cat.getCurrentProducts()[1].getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), PhaseType.EVERGREEN, cat);
        assertionNull(cr, product.getName(), ProductCategory.ADD_ON, BillingPeriod.MONTHLY, priceList.getName(), PhaseType.EVERGREEN, cat);
        assertionNull(cr, product.getName(), ProductCategory.BASE, BillingPeriod.ANNUAL, priceList.getName(), PhaseType.EVERGREEN, cat);
        assertionException(cr, product.getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, "dipsy", PhaseType.EVERGREEN, cat);
        assertion(Result.FOO, cr, product.getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), PhaseType.TRIAL, cat);
    }

    @Test(groups = "fast")
    public void testOrder() throws CatalogApiException {
        final MockCatalog cat = new MockCatalog();

        final DefaultProduct product = cat.getCurrentProducts()[0];
        final DefaultPriceList priceList = cat.getPriceLists().getDefaultPricelist();

        final CaseResult cr0 = new CaseResult(
                product,
                ProductCategory.BASE,
                BillingPeriod.MONTHLY,
                priceList,
                PhaseType.EVERGREEN,
                Result.FOO);

        final CaseResult cr1 = new CaseResult(
                product,
                ProductCategory.BASE,
                BillingPeriod.MONTHLY,
                priceList,
                PhaseType.EVERGREEN,
                Result.BAR);

        final CaseResult cr2 = new CaseResult(
                product,
                ProductCategory.BASE,
                BillingPeriod.MONTHLY,
                priceList,
                PhaseType.EVERGREEN,
                Result.TINKYWINKY);

        final CaseResult cr3 = new CaseResult(
                product,
                ProductCategory.BASE,
                BillingPeriod.ANNUAL,
                priceList,
                PhaseType.EVERGREEN,
                Result.DIPSY);

        final CaseResult cr4 = new CaseResult(
                product,
                ProductCategory.BASE,
                BillingPeriod.ANNUAL,
                priceList,
                PhaseType.EVERGREEN,
                Result.LALA);

        final Result r1 = CasePhase.getResult(new CaseResult[]{cr0, cr1, cr2, cr3, cr4},
                                              new PlanPhaseSpecifier(product.getName(), product.getCategory(), BillingPeriod.MONTHLY, priceList.getName(), PhaseType.EVERGREEN), cat);

        Assert.assertEquals(Result.FOO, r1);

        final Result r2 = CasePhase.getResult(new CaseResult[]{cr0, cr1, cr2, cr3, cr4},
                                              new PlanPhaseSpecifier(product.getName(), product.getCategory(), BillingPeriod.ANNUAL, priceList.getName(), PhaseType.EVERGREEN), cat);

        Assert.assertEquals(Result.DIPSY, r2);

    }

    protected void assertionNull(final CaseResult cr, final String productName, final ProductCategory productCategory, final BillingPeriod bp, final String priceListName, final PhaseType phaseType, final StandaloneCatalog cat) {
        try {
            Assert.assertNull(cr.getResult(new PlanPhaseSpecifier(productName, productCategory, bp, priceListName, phaseType), cat));
        } catch (CatalogApiException e) {
            Assert.fail("", e);
        }
    }

    protected void assertionException(final CaseResult cr, final String productName, final ProductCategory productCategory, final BillingPeriod bp, final String priceListName, final PhaseType phaseType, final StandaloneCatalog cat) {
        try {
            Assert.assertNull(cr.getResult(new PlanPhaseSpecifier(productName, productCategory, bp, priceListName, phaseType), cat));
            Assert.fail("Exception expected");
        } catch (CatalogApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.CAT_PRICE_LIST_NOT_FOUND.getCode());
        }
    }

    protected void assertion(final Result result, final CaseResult cr, final String productName, final ProductCategory productCategory, final BillingPeriod bp, final String priceListName, final PhaseType phaseType, final StandaloneCatalog cat) {
        try {
            Assert.assertEquals(result, cr.getResult(new PlanPhaseSpecifier(productName, productCategory, bp, priceListName, phaseType), cat));
        } catch (CatalogApiException e) {
            Assert.fail("", e);
        }
    }
}
