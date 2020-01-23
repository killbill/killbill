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
import org.killbill.billing.catalog.api.PlanSpecifier;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;

public class TestCaseChange extends CatalogTestSuiteNoDB {

    protected static class DefaultCaseChangeResult extends DefaultCaseChange<Result> {

        @XmlElement(required = true)
        private final Result result;

        public DefaultCaseChangeResult(final DefaultProduct from, final DefaultProduct to,
                                       final ProductCategory fromProductCategory, final ProductCategory toProductCategory,
                                       final BillingPeriod fromBP, final BillingPeriod toBP,
                                       final DefaultPriceList fromPriceList, final DefaultPriceList toPriceList,
                                       final PhaseType fromType,
                                       final Result result) {
            setFromProduct(from);
            setToProduct(to);
            setFromProductCategory(fromProductCategory);
            setToProductCategory(toProductCategory);
            setFromPriceList(fromPriceList);
            setToPriceList(toPriceList);
            setFromBillingPeriod(fromBP);
            setToBillingPeriod(toBP);
            setPhaseType(fromType);

            this.result = result;
        }

        @Override
        protected Result getResult() {
            return result;
        }
    }

    @Test(groups = "fast")
    public void testBasic() throws CatalogApiException {
        final MockCatalog cat = new MockCatalog();

        final DefaultProduct product1 = cat.getCurrentProduct(0);
        final DefaultPriceList priceList1 = (DefaultPriceList) cat.findPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final DefaultProduct product2 = cat.getCurrentProduct(2);
        final DefaultPriceList priceList2 = cat.getPriceLists().getChildPriceLists()[1];

        final DefaultCaseChangeResult cr = new DefaultCaseChangeResult(
                product1, product2,
                ProductCategory.BASE, ProductCategory.BASE,
                BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                priceList1, priceList2,
                PhaseType.EVERGREEN,
                Result.FOO);

        assertion(Result.FOO, cr,
                  product1.getName(), product2.getName(),
                  BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                  priceList1.getName(), priceList2.getName(),
                  PhaseType.EVERGREEN, cat);

        assertionNull(cr,
                      cat.getCurrentProduct(1).getName(), product2.getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.EVERGREEN, cat);

        assertionNull(cr,
                      product1.getName(), cat.getCurrentProduct(1).getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.EVERGREEN, cat);

        assertionNull(cr,
                      product1.getName(), product2.getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.ANNUAL, BillingPeriod.MONTHLY,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.EVERGREEN, cat);

        assertionNull(cr,
                      product1.getName(), product2.getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.MONTHLY, BillingPeriod.ANNUAL,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.EVERGREEN, cat);

        assertionException(cr,
                           product1.getName(), product2.getName(),
                           BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                           cat.getCurrentProduct(1).getName(), priceList2.getName(),
                           PhaseType.EVERGREEN, cat);

        assertionException(cr,
                           product1.getName(), product2.getName(),
                           BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                           priceList1.getName(), cat.getCurrentProduct(1).getName(),
                           PhaseType.EVERGREEN, cat);

        assertionNull(cr,
                      product1.getName(), product2.getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.TRIAL, cat);
    }

    @Test(groups = "fast")
    public void testWildcardFromProduct() throws CatalogApiException {
        final MockCatalog cat = new MockCatalog();

        final DefaultProduct product1 = cat.getCurrentProduct(0);
        final DefaultPriceList priceList1 = (DefaultPriceList) cat.findPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final DefaultProduct product2 = cat.getCurrentProduct(2);
        final DefaultPriceList priceList2 = cat.getPriceLists().getChildPriceLists()[1];

        final DefaultCaseChangeResult cr = new DefaultCaseChangeResult(
                null, product2,
                ProductCategory.BASE, ProductCategory.BASE,
                BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                priceList1, priceList2,
                PhaseType.EVERGREEN,
                Result.FOO);

        assertion(Result.FOO, cr,
                  product1.getName(), product2.getName(),
                  BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                  priceList1.getName(), priceList2.getName(),
                  PhaseType.EVERGREEN, cat);

        assertion(Result.FOO, cr,
                  cat.getCurrentProduct(1).getName(), product2.getName(),
                  BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                  priceList1.getName(), priceList2.getName(),
                  PhaseType.EVERGREEN, cat);


        assertionNull(cr,
                      product1.getName(), cat.getCurrentProduct(1).getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.ANNUAL, BillingPeriod.MONTHLY,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.EVERGREEN, cat);

        assertionNull(cr,
                      product1.getName(), product2.getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.MONTHLY, BillingPeriod.ANNUAL,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.EVERGREEN, cat);

        assertionException(cr,
                           product1.getName(), product2.getName(),
                           BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                           cat.getCurrentProduct(1).getName(), priceList2.getName(),
                           PhaseType.EVERGREEN, cat);

        assertionException(cr,
                           product1.getName(), product2.getName(),
                           BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                           priceList1.getName(), cat.getCurrentProduct(1).getName(),
                           PhaseType.EVERGREEN, cat);

        assertionNull(cr,
                      product1.getName(), product2.getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.TRIAL, cat);
    }

    @Test(groups = "fast")
    public void testWildcardToProduct() throws CatalogApiException {
        final MockCatalog cat = new MockCatalog();

        final DefaultProduct product1 = cat.getCurrentProduct(0);
        final DefaultPriceList priceList1 = (DefaultPriceList) cat.findPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final DefaultProduct product2 = cat.getCurrentProduct(2);
        final DefaultPriceList priceList2 = cat.getPriceLists().getChildPriceLists()[1];

        final DefaultCaseChangeResult cr = new DefaultCaseChangeResult(
                product1, null,
                ProductCategory.BASE, ProductCategory.BASE,
                BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                priceList1, priceList2,
                PhaseType.EVERGREEN,
                Result.FOO);

        assertion(Result.FOO, cr,
                  product1.getName(), product2.getName(),
                  BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                  priceList1.getName(), priceList2.getName(),
                  PhaseType.EVERGREEN, cat);

        assertionNull(cr,
                      cat.getCurrentProduct(1).getName(), product2.getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.EVERGREEN, cat);


        assertion(Result.FOO, cr,
                  product1.getName(), cat.getCurrentProduct(1).getName(),
                  BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                  priceList1.getName(), priceList2.getName(),
                  PhaseType.EVERGREEN, cat);

        assertionNull(cr,
                      product1.getName(), product2.getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.ANNUAL, BillingPeriod.MONTHLY,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.EVERGREEN, cat);

        assertionNull(cr,
                      product1.getName(), product2.getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.MONTHLY, BillingPeriod.ANNUAL,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.EVERGREEN, cat);

        assertionException(cr,
                           product1.getName(), product2.getName(),
                           BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                           cat.getCurrentProduct(1).getName(), priceList2.getName(),
                           PhaseType.EVERGREEN, cat);

        assertionException(cr,
                           product1.getName(), product2.getName(),
                           BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                           priceList1.getName(), cat.getCurrentProduct(1).getName(),
                           PhaseType.EVERGREEN, cat);

        assertionNull(cr,
                      product1.getName(), product2.getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.TRIAL, cat);
    }

    @Test(groups = "fast")
    public void testWildcardFromProductCategory() throws CatalogApiException {
        final MockCatalog cat = new MockCatalog();

        final DefaultProduct product1 = cat.getCurrentProduct(0);
        final DefaultPriceList priceList1 = (DefaultPriceList) cat.findPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final DefaultProduct product2 = cat.getCurrentProduct(2);
        final DefaultPriceList priceList2 = cat.getPriceLists().getChildPriceLists()[1];

        final DefaultCaseChangeResult cr = new DefaultCaseChangeResult(
                product1, product2,
                null, ProductCategory.BASE,
                BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                priceList1, priceList2,
                PhaseType.EVERGREEN,
                Result.FOO);

        assertion(Result.FOO, cr,
                  product1.getName(), product2.getName(),
                  BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                  priceList1.getName(), priceList2.getName(),
                  PhaseType.EVERGREEN, cat);

        assertionNull(cr,
                      cat.getCurrentProduct(1).getName(), product2.getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.EVERGREEN, cat);

        assertionNull(cr,
                      product1.getName(), cat.getCurrentProduct(1).getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.EVERGREEN, cat);

        assertion(Result.FOO, cr,
                  product1.getName(), product2.getName(),
                  BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                  priceList1.getName(), priceList2.getName(),
                  PhaseType.EVERGREEN, cat);

        assertionNull(cr,
                      product1.getName(), product2.getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.ANNUAL, BillingPeriod.MONTHLY,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.EVERGREEN, cat);

        assertionNull(cr,
                      product1.getName(), product2.getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.MONTHLY, BillingPeriod.ANNUAL,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.EVERGREEN, cat);

        assertionException(cr,
                           product1.getName(), product2.getName(),
                           BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                           cat.getCurrentProduct(1).getName(), priceList2.getName(),
                           PhaseType.EVERGREEN, cat);

        assertionException(cr,
                           product1.getName(), product2.getName(),
                           BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                           priceList1.getName(), cat.getCurrentProduct(1).getName(),
                           PhaseType.EVERGREEN, cat);

        assertionNull(cr,
                      product1.getName(), product2.getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.TRIAL, cat);
    }

    @Test(groups = "fast")
    public void testWildcardToProductCategory() throws CatalogApiException {
        final MockCatalog cat = new MockCatalog();

        final DefaultProduct product1 = cat.getCurrentProduct(0);
        final DefaultPriceList priceList1 = (DefaultPriceList) cat.findPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final DefaultProduct product2 = cat.getCurrentProduct(2);
        final DefaultPriceList priceList2 = cat.getPriceLists().getChildPriceLists()[1];

        final DefaultCaseChangeResult cr = new DefaultCaseChangeResult(
                product1, product2,
                ProductCategory.BASE, null,
                BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                priceList1, priceList2,
                PhaseType.EVERGREEN,
                Result.FOO);

        assertion(Result.FOO, cr,
                  product1.getName(), product2.getName(),
                  BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                  priceList1.getName(), priceList2.getName(),
                  PhaseType.EVERGREEN, cat);

        assertionNull(cr,
                      cat.getCurrentProduct(1).getName(), product2.getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.EVERGREEN, cat);

        assertionNull(cr,
                      product1.getName(), cat.getCurrentProduct(1).getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.EVERGREEN, cat);


        assertion(Result.FOO, cr,
                  product1.getName(), product2.getName(),
                  BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                  priceList1.getName(), priceList2.getName(),
                  PhaseType.EVERGREEN, cat);

        assertionNull(cr,
                      product1.getName(), product2.getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.ANNUAL, BillingPeriod.MONTHLY,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.EVERGREEN, cat);

        assertionNull(cr,
                      product1.getName(), product2.getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.MONTHLY, BillingPeriod.ANNUAL,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.EVERGREEN, cat);

        assertionException(cr,
                           product1.getName(), product2.getName(),
                           BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                           cat.getCurrentProduct(1).getName(), priceList2.getName(),
                           PhaseType.EVERGREEN, cat);

        assertionException(cr,
                           product1.getName(), product2.getName(),
                           BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                           priceList1.getName(), cat.getCurrentProduct(1).getName(),
                           PhaseType.EVERGREEN, cat);

        assertionNull(cr,
                      product1.getName(), product2.getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.TRIAL, cat);
    }

    @Test(groups = "fast")
    public void testWildcardFromBillingPeriod() throws CatalogApiException {
        final MockCatalog cat = new MockCatalog();

        final DefaultProduct product1 = cat.getCurrentProduct(0);
        final DefaultPriceList priceList1 = (DefaultPriceList) cat.findPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final DefaultProduct product2 = cat.getCurrentProduct(2);
        final DefaultPriceList priceList2 = cat.getPriceLists().getChildPriceLists()[1];

        final DefaultCaseChangeResult cr = new DefaultCaseChangeResult(
                product1, product2,
                ProductCategory.BASE, ProductCategory.BASE,
                null, BillingPeriod.MONTHLY,
                priceList1, priceList2,
                PhaseType.EVERGREEN,
                Result.FOO);

        assertion(Result.FOO, cr,
                  product1.getName(), product2.getName(),
                  BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                  priceList1.getName(), priceList2.getName(),
                  PhaseType.EVERGREEN, cat);

        assertionNull(cr,
                      cat.getCurrentProduct(1).getName(), product2.getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.EVERGREEN, cat);

        assertionNull(cr,
                      product1.getName(), cat.getCurrentProduct(1).getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.EVERGREEN, cat);

        assertion(Result.FOO, cr,
                  product1.getName(), product2.getName(),
                  BillingPeriod.ANNUAL, BillingPeriod.MONTHLY,
                  priceList1.getName(), priceList2.getName(),
                  PhaseType.EVERGREEN, cat);

        assertionNull(cr,
                      product1.getName(), product2.getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.MONTHLY, BillingPeriod.ANNUAL,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.EVERGREEN, cat);

        assertionException(cr,
                           product1.getName(), product2.getName(),
                           BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                           cat.getCurrentProduct(1).getName(), priceList2.getName(),
                           PhaseType.EVERGREEN, cat);

        assertionException(cr,
                           product1.getName(), product2.getName(),
                           BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                           priceList1.getName(), cat.getCurrentProduct(1).getName(),
                           PhaseType.EVERGREEN, cat);

        assertionNull(cr,
                      product1.getName(), product2.getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.TRIAL, cat);
    }

    @Test(groups = "fast")
    public void testWildCardToBillingPeriod() throws CatalogApiException {
        final MockCatalog cat = new MockCatalog();

        final DefaultProduct product1 = cat.getCurrentProduct(0);
        final DefaultPriceList priceList1 = (DefaultPriceList) cat.findPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final DefaultProduct product2 = cat.getCurrentProduct(2);
        final DefaultPriceList priceList2 = cat.getPriceLists().getChildPriceLists()[1];

        final DefaultCaseChangeResult cr = new DefaultCaseChangeResult(
                product1, product2,
                ProductCategory.BASE, ProductCategory.BASE,
                BillingPeriod.MONTHLY, null,
                priceList1, priceList2,
                PhaseType.EVERGREEN,
                Result.FOO);

        assertion(Result.FOO, cr,
                  product1.getName(), product2.getName(),
                  BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                  priceList1.getName(), priceList2.getName(),
                  PhaseType.EVERGREEN, cat);

        assertionNull(cr,
                      cat.getCurrentProduct(1).getName(), product2.getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.EVERGREEN, cat);


        assertionNull(cr,
                      product1.getName(), cat.getCurrentProduct(1).getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.EVERGREEN, cat);

        assertionNull(cr,
                      product1.getName(), product2.getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.ANNUAL, BillingPeriod.MONTHLY,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.EVERGREEN, cat);

        assertion(Result.FOO, cr,
                  product1.getName(), product2.getName(),
                  BillingPeriod.MONTHLY, BillingPeriod.ANNUAL,
                  priceList1.getName(), priceList2.getName(),
                  PhaseType.EVERGREEN, cat);

        assertionException(cr,
                           product1.getName(), product2.getName(),
                           BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                           cat.getCurrentProduct(1).getName(), priceList2.getName(),
                           PhaseType.EVERGREEN, cat);

        assertionException(cr,
                           product1.getName(), product2.getName(),
                           BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                           priceList1.getName(), cat.getCurrentProduct(1).getName(),
                           PhaseType.EVERGREEN, cat);

        assertionNull(cr,
                      product1.getName(), product2.getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.TRIAL, cat);
    }

    @Test(groups = "fast")
    public void testWildCardFromPriceList() throws CatalogApiException {
        final MockCatalog cat = new MockCatalog();

        final DefaultProduct product1 = cat.getCurrentProduct(0);
        final DefaultPriceList priceList1 = (DefaultPriceList) cat.findPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final DefaultProduct product2 = cat.getCurrentProduct(2);
        final DefaultPriceList priceList2 = cat.getPriceLists().getChildPriceLists()[1];

        final DefaultCaseChangeResult cr = new DefaultCaseChangeResult(
                product1, product2,
                ProductCategory.BASE, ProductCategory.BASE,
                BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                null, priceList2,
                PhaseType.EVERGREEN,
                Result.FOO);

        assertion(Result.FOO, cr,
                  product1.getName(), product2.getName(),
                  BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                  priceList1.getName(), priceList2.getName(),
                  PhaseType.EVERGREEN, cat);

        assertionNull(cr,
                      cat.getCurrentProduct(1).getName(), product2.getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.EVERGREEN, cat);


        assertionNull(cr,
                      product1.getName(), cat.getCurrentProduct(1).getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.EVERGREEN, cat);

        assertionNull(cr,
                      product1.getName(), product2.getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.ANNUAL, BillingPeriod.MONTHLY,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.EVERGREEN, cat);

        assertionNull(cr,
                      product1.getName(), product2.getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.MONTHLY, BillingPeriod.ANNUAL,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.EVERGREEN, cat);

        assertion(Result.FOO, cr,
                  product1.getName(), product2.getName(),
                  BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                  priceList1.getName(), priceList2.getName(),
                  PhaseType.EVERGREEN, cat);

        assertionException(cr,
                           product1.getName(), product2.getName(),
                           BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                           priceList1.getName(), cat.getCurrentProduct(1).getName(),
                           PhaseType.EVERGREEN, cat);

        assertionNull(cr,
                      product1.getName(), product2.getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.TRIAL, cat);
    }

    @Test(groups = "fast")
    public void testWildcardToPriceList() throws CatalogApiException {
        final MockCatalog cat = new MockCatalog();

        final DefaultProduct product1 = cat.getCurrentProduct(0);
        final DefaultPriceList priceList1 = (DefaultPriceList) cat.findPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final DefaultProduct product2 = cat.getCurrentProduct(2);
        final DefaultPriceList priceList2 = cat.getPriceLists().getChildPriceLists()[1];

        final DefaultCaseChangeResult cr = new DefaultCaseChangeResult(
                product1, product2,
                ProductCategory.BASE, ProductCategory.BASE,
                BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                priceList1, null,
                PhaseType.EVERGREEN,
                Result.FOO);

        assertion(Result.FOO, cr,
                  product1.getName(), product2.getName(),
                  BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                  priceList1.getName(), priceList2.getName(),
                  PhaseType.EVERGREEN, cat);

        assertionNull(cr,
                      cat.getCurrentProduct(1).getName(), product2.getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.EVERGREEN, cat);


        assertionNull(cr,
                      product1.getName(), cat.getCurrentProduct(1).getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.EVERGREEN, cat);

        assertionNull(cr,
                      product1.getName(), product2.getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.ANNUAL, BillingPeriod.MONTHLY,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.EVERGREEN, cat);

        assertionNull(cr,
                      product1.getName(), product2.getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.MONTHLY, BillingPeriod.ANNUAL,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.EVERGREEN, cat);

        assertionException(cr,
                           product1.getName(), product2.getName(),
                           BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                           cat.getCurrentProduct(1).getName(), priceList2.getName(),
                           PhaseType.EVERGREEN, cat);

        assertion(Result.FOO, cr,
                  product1.getName(), product2.getName(),
                  BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                  priceList1.getName(), priceList2.getName(),
                  PhaseType.EVERGREEN, cat);

        assertionNull(cr,
                      product1.getName(), product2.getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.TRIAL, cat);
    }

    @Test(groups = "fast")
    public void testWildcardPlanPhase() throws CatalogApiException {
        final MockCatalog cat = new MockCatalog();

        final DefaultProduct product1 = cat.getCurrentProduct(0);
        final DefaultPriceList priceList1 = (DefaultPriceList) cat.findPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final DefaultProduct product2 = cat.getCurrentProduct(2);
        final DefaultPriceList priceList2 = cat.getPriceLists().getChildPriceLists()[1];

        final DefaultCaseChangeResult cr = new DefaultCaseChangeResult(
                product1, product2,
                ProductCategory.BASE, ProductCategory.BASE,
                BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                priceList1, priceList2,
                null,
                Result.FOO);

        assertion(Result.FOO, cr,
                  product1.getName(), product2.getName(),
                  BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                  priceList1.getName(), priceList2.getName(),
                  PhaseType.EVERGREEN, cat);

        assertionNull(cr,
                      cat.getCurrentProduct(1).getName(), product2.getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.EVERGREEN, cat);


        assertionNull(cr,
                      product1.getName(), cat.getCurrentProduct(1).getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.EVERGREEN, cat);

        assertionNull(cr,
                      product1.getName(), product2.getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.ANNUAL, BillingPeriod.MONTHLY,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.EVERGREEN, cat);

        assertionNull(cr,
                      product1.getName(), product2.getName(),
                      ProductCategory.BASE, ProductCategory.BASE,
                      BillingPeriod.MONTHLY, BillingPeriod.ANNUAL,
                      priceList1.getName(), priceList2.getName(),
                      PhaseType.EVERGREEN, cat);

        assertionException(cr,
                           product1.getName(), product2.getName(),
                           BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                           cat.getCurrentProduct(1).getName(), priceList2.getName(),
                           PhaseType.EVERGREEN, cat);

        assertionException(cr,
                           product1.getName(), product2.getName(),
                           BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                           priceList1.getName(), cat.getCurrentProduct(1).getName(),
                           PhaseType.EVERGREEN, cat);

        assertion(Result.FOO, cr,
                  product1.getName(), product2.getName(),
                  BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                  priceList1.getName(), priceList2.getName(),
                  PhaseType.TRIAL, cat);
    }

    @Test(groups = "fast")
    public void testOrder() throws CatalogApiException {
        final MockCatalog cat = new MockCatalog();

        final DefaultProduct product1 = cat.getCurrentProduct(0);
        final DefaultPriceList priceList1 = (DefaultPriceList) cat.findPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final DefaultProduct product2 = cat.getCurrentProduct(2);
        final DefaultPriceList priceList2 = cat.getPriceLists().getChildPriceLists()[1];

        final DefaultCaseChangeResult cr0 = new DefaultCaseChangeResult(
                product1, product2,
                ProductCategory.BASE, ProductCategory.BASE,
                BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                priceList1, priceList2,
                PhaseType.EVERGREEN,
                Result.FOO);

        final DefaultCaseChangeResult cr1 = new DefaultCaseChangeResult(
                product1, product2,
                ProductCategory.BASE, ProductCategory.BASE,
                BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                priceList1, priceList2,
                PhaseType.EVERGREEN,
                Result.BAR);

        final DefaultCaseChangeResult cr2 = new DefaultCaseChangeResult(
                product1, product2,
                ProductCategory.BASE, ProductCategory.BASE,
                BillingPeriod.MONTHLY, BillingPeriod.MONTHLY,
                priceList1, priceList2,
                PhaseType.EVERGREEN,
                Result.TINKYWINKY);

        final DefaultCaseChangeResult cr3 = new DefaultCaseChangeResult(
                product1, product2,
                ProductCategory.BASE, ProductCategory.BASE,
                BillingPeriod.MONTHLY, BillingPeriod.ANNUAL,
                priceList1, priceList2,
                PhaseType.EVERGREEN,
                Result.DIPSY);

        final DefaultCaseChangeResult cr4 = new DefaultCaseChangeResult(
                product1, product2,
                ProductCategory.BASE, ProductCategory.BASE,
                BillingPeriod.MONTHLY, BillingPeriod.ANNUAL,
                priceList1, priceList2,
                PhaseType.EVERGREEN,
                Result.LALA);

        final Result r1 = DefaultCaseChange.getResult(new DefaultCaseChangeResult[]{cr0, cr1, cr2, cr3, cr4},
                                                      new PlanPhaseSpecifier(product1.getName(),  BillingPeriod.MONTHLY, priceList1.getName(), PhaseType.EVERGREEN),
                                                      new PlanSpecifier(product2.getName(),  BillingPeriod.MONTHLY, priceList2.getName()), cat);

        Assert.assertEquals(r1, Result.FOO);

        final Result r2 = DefaultCaseChange.getResult(new DefaultCaseChangeResult[]{cr0, cr1, cr2, cr3, cr4},
                                                      new PlanPhaseSpecifier(product1.getName(),  BillingPeriod.MONTHLY, priceList1.getName(), PhaseType.EVERGREEN),
                                                      new PlanSpecifier(product2.getName(),  BillingPeriod.ANNUAL, priceList2.getName()), cat);

        Assert.assertEquals(r2, Result.DIPSY);
    }

    protected void assertionNull(final DefaultCaseChangeResult cr,
                                 final String fromProductName, final String toProductName,
                                 final ProductCategory fromProductCategory, final ProductCategory toProductCategory,
                                 final BillingPeriod fromBp, final BillingPeriod toBp,
                                 final String fromPriceListName, final String toPriceListName,
                                 final PhaseType phaseType, final StandaloneCatalog cat) {
        try {
            Assert.assertNull(cr.getResult(new PlanPhaseSpecifier(fromProductName, fromBp, fromPriceListName, phaseType),
                                           new PlanSpecifier(toProductName, toBp, toPriceListName), cat));
        } catch (CatalogApiException e) {
            Assert.fail("", e);
        }
    }

    protected void assertionException(final DefaultCaseChangeResult cr,
                                      final String fromProductName, final String toProductName,
                                      final BillingPeriod fromBp, final BillingPeriod toBp,
                                      final String fromPriceListName, final String toPriceListName,
                                      final PhaseType phaseType, final StandaloneCatalog cat) {
        try {
            cr.getResult(new PlanPhaseSpecifier(fromProductName, fromBp, fromPriceListName, phaseType),
                         new PlanSpecifier(toProductName, toBp, toPriceListName), cat);
            Assert.fail("Expecting an exception");
        } catch (CatalogApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.CAT_PRICE_LIST_NOT_FOUND.getCode());
        }
    }

    protected void assertion(final Result result, final DefaultCaseChangeResult cr,
                             final String fromProductName, final String toProductName,
                             final BillingPeriod fromBp, final BillingPeriod toBp,
                             final String fromPriceListName, final String toPriceListName,
                             final PhaseType phaseType, final StandaloneCatalog cat) {
        try {
            Assert.assertEquals(result, cr.getResult(new PlanPhaseSpecifier(fromProductName, fromBp, fromPriceListName, phaseType),
                                                     new PlanSpecifier(toProductName, toBp, toPriceListName), cat));
        } catch (CatalogApiException e) {
            Assert.fail("", e);
        }
    }
}
