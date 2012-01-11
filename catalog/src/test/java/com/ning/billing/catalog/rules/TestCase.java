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

package com.ning.billing.catalog.rules;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlIDREF;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.DefaultPriceList;
import com.ning.billing.catalog.DefaultProduct;
import com.ning.billing.catalog.MockCatalog;
import com.ning.billing.catalog.StandaloneCatalog;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.PlanSpecifier;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;

public class TestCase {

    protected class CaseResult extends Case<Result>  {

        @XmlElement(required=true)
        private Result policy;

        public CaseResult(DefaultProduct product, ProductCategory productCategory, BillingPeriod billingPeriod, DefaultPriceList priceList,
                Result policy) {
            setProduct(product);
            setProductCategory(productCategory);
            setBillingPeriod(billingPeriod);
            setPriceList(priceList);
            this.policy = policy;
        }

        @Override
        protected Result getResult() {
            return policy;
        }

        @XmlElement(required=false, name="product")
        @XmlIDREF
        protected DefaultProduct product;
        @XmlElement(required=false, name="productCategory")
        protected ProductCategory productCategory;

        @XmlElement(required=false, name="billingPeriod")
        protected BillingPeriod billingPeriod;

        @XmlElement(required=false, name="priceList")
        @XmlIDREF
        protected DefaultPriceList priceList;

        public DefaultProduct getProduct(){
            return product;
        }

        public ProductCategory getProductCategory() {
            return productCategory;
        }

        public BillingPeriod getBillingPeriod() {
            return billingPeriod;
        }

        public DefaultPriceList getPriceList() {
            return priceList;
        }

        protected CaseResult setProduct(DefaultProduct product) {
            this.product = product;
            return this;
        }

        protected CaseResult setProductCategory(ProductCategory productCategory) {
            this.productCategory = productCategory;
            return this;
        }

        protected CaseResult setBillingPeriod(BillingPeriod billingPeriod) {
            this.billingPeriod = billingPeriod;
            return this;
        }

        protected CaseResult setPriceList(DefaultPriceList priceList) {
            this.priceList = priceList;
            return this;
        }
    }

    @Test(enabled=true)
    public void testBasic() throws CatalogApiException{
        MockCatalog cat = new MockCatalog();

        DefaultProduct product = cat.getCurrentProducts()[0];
        DefaultPriceList priceList = cat.findCurrentPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);


        CaseResult cr = new CaseResult(
                product, 
                ProductCategory.BASE,
                BillingPeriod.MONTHLY, 
                priceList,
                Result.FOO);

        assertion(Result.FOO, cr, product.getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), cat);
        assertionNull(cr, cat.getCurrentProducts()[1].getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), cat);
        assertionNull(cr, product.getName(), ProductCategory.ADD_ON,BillingPeriod.MONTHLY, priceList.getName(), cat);
        assertionNull(cr, product.getName(), ProductCategory.BASE,BillingPeriod.ANNUAL, priceList.getName(), cat);
        assertionException(cr, product.getName(), ProductCategory.BASE,BillingPeriod.MONTHLY, "dipsy", cat);
    }

    @Test(enabled=true)
    public void testWildCardProduct() throws CatalogApiException{
        MockCatalog cat = new MockCatalog();

        DefaultProduct product = cat.getCurrentProducts()[0];
        DefaultPriceList priceList = cat.findCurrentPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);


        CaseResult cr = new CaseResult(
                null, 
                ProductCategory.BASE,
                BillingPeriod.MONTHLY, 
                priceList,

                Result.FOO);

        assertion(Result.FOO, cr, product.getName(), ProductCategory.BASE,BillingPeriod.MONTHLY, priceList.getName(), cat);
        assertion(Result.FOO, cr, cat.getCurrentProducts()[1].getName(), ProductCategory.BASE,BillingPeriod.MONTHLY, priceList.getName(), cat);
        assertionNull(cr, product.getName(), ProductCategory.ADD_ON,BillingPeriod.MONTHLY, priceList.getName(), cat);
        assertionNull(cr, product.getName(), ProductCategory.BASE,BillingPeriod.ANNUAL, priceList.getName(), cat);
        assertionException(cr, product.getName(), ProductCategory.BASE,BillingPeriod.MONTHLY, "dipsy", cat);
    }

    @Test(enabled=true)
    public void testWildCardProductCategory() throws CatalogApiException{
        MockCatalog cat = new MockCatalog();

        DefaultProduct product = cat.getCurrentProducts()[0];
        DefaultPriceList priceList = cat.findCurrentPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);


        CaseResult cr = new CaseResult(
                product, 
                null,
                BillingPeriod.MONTHLY, 
                priceList,

                Result.FOO);

        assertion(Result.FOO, cr, product.getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), cat);
        assertionNull(cr,  cat.getCurrentProducts()[1].getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), cat);
        assertion(Result.FOO, cr, product.getName(), ProductCategory.ADD_ON,BillingPeriod.MONTHLY, priceList.getName(), cat);
        assertionNull(cr, product.getName(), ProductCategory.BASE,BillingPeriod.ANNUAL, priceList.getName(), cat);
        assertionException(cr, product.getName(), ProductCategory.BASE,BillingPeriod.MONTHLY, "dipsy", cat);
    }

    @Test(enabled=true)
    public void testWildCardBillingPeriod() throws CatalogApiException{
        MockCatalog cat = new MockCatalog();

        DefaultProduct product = cat.getCurrentProducts()[0];
        DefaultPriceList priceList = cat.findCurrentPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);


        CaseResult cr = new CaseResult(
                product, 
                ProductCategory.BASE,
                null, 
                priceList,

                Result.FOO);

        assertion(Result.FOO, cr, product.getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), cat);
        assertionNull(cr,  cat.getCurrentProducts()[1].getName(), ProductCategory.BASE,BillingPeriod.MONTHLY, priceList.getName(), cat);
        assertionNull(cr, product.getName(), ProductCategory.ADD_ON,BillingPeriod.MONTHLY, priceList.getName(), cat);
        assertion(Result.FOO,cr, product.getName(), ProductCategory.BASE,BillingPeriod.ANNUAL, priceList.getName(), cat);
        assertionException(cr, product.getName(), ProductCategory.BASE,BillingPeriod.MONTHLY, "dipsy", cat);
    }

    @Test(enabled=true)
    public void testWildCardPriceList() throws CatalogApiException{
        MockCatalog cat = new MockCatalog();

        DefaultProduct product = cat.getCurrentProducts()[0];
        DefaultPriceList priceList = cat.findCurrentPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);


        CaseResult cr = new CaseResult(
                product, 
                ProductCategory.BASE,
                BillingPeriod.MONTHLY, 
                null,

                Result.FOO);

        assertion(Result.FOO, cr, product.getName(), ProductCategory.BASE,BillingPeriod.MONTHLY, priceList.getName(), cat);
        assertionNull(cr,  cat.getCurrentProducts()[1].getName(), ProductCategory.BASE,BillingPeriod.MONTHLY, priceList.getName(), cat);
        assertionNull(cr, product.getName(), ProductCategory.ADD_ON,BillingPeriod.MONTHLY, priceList.getName(), cat);
        assertionNull(cr, product.getName(), ProductCategory.BASE,BillingPeriod.ANNUAL, priceList.getName(), cat);
        assertion(Result.FOO, cr, product.getName(), ProductCategory.BASE,BillingPeriod.MONTHLY, "dipsy", cat);
    }

    @Test
    public void testCaseOrder() throws CatalogApiException {
        MockCatalog cat = new MockCatalog();

        DefaultProduct product = cat.getCurrentProducts()[0];
        DefaultPriceList priceList = cat.findCurrentPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);


        CaseResult cr0 = new CaseResult(
                product, 
                ProductCategory.BASE,
                BillingPeriod.MONTHLY, 
                priceList,
                Result.FOO);

        CaseResult cr1 = new CaseResult(
                product, 
                ProductCategory.BASE,
                BillingPeriod.MONTHLY, 
                priceList,
                Result.BAR);

        CaseResult cr2 = new CaseResult(
                product, 
                ProductCategory.BASE,
                BillingPeriod.ANNUAL, 
                priceList,
                Result.DIPSY);

        CaseResult cr3 = new CaseResult(
                product, 
                ProductCategory.BASE,
                BillingPeriod.ANNUAL, 
                priceList,
                Result.LALA);

        Result r1 = Case.getResult(new CaseResult[]{cr0, cr1, cr2,cr3}, 
                new PlanSpecifier(product.getName(), product.getCategory(), BillingPeriod.MONTHLY, priceList.getName()), cat);
        assertEquals(Result.FOO, r1);

        Result r2 = Case.getResult(new CaseResult[]{cr0, cr1, cr2}, 
                new PlanSpecifier(product.getName(), product.getCategory(), BillingPeriod.ANNUAL, priceList.getName()), cat);
        assertEquals(Result.DIPSY, r2);
    }




    protected void assertionNull(CaseResult cr, String productName, ProductCategory productCategory, BillingPeriod bp, String priceListName, StandaloneCatalog cat) throws CatalogApiException{
        assertNull(cr.getResult(new PlanSpecifier(productName, productCategory, bp, priceListName), cat));
    }

    protected void assertionException(CaseResult cr, String productName, ProductCategory productCategory, BillingPeriod bp, String priceListName, StandaloneCatalog cat) {
        try{
        	cr.getResult(new PlanSpecifier(productName, productCategory, bp, priceListName), cat);  	
        	Assert.fail("Expecting an exception");
        } catch (CatalogApiException e) {
        	Assert.assertEquals(e.getCode(), ErrorCode.CAT_PRICE_LIST_NOT_FOUND.getCode());
        }
    }

    protected void assertion(Result result, CaseResult cr, String productName, ProductCategory productCategory, BillingPeriod bp, String priceListName,StandaloneCatalog cat) throws CatalogApiException{
        assertEquals(result, cr.getResult(new PlanSpecifier(productName, productCategory, bp, priceListName), cat));
    }


}
