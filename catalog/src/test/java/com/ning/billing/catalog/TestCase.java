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

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;

import javax.xml.bind.annotation.XmlElement;

import org.testng.annotations.Test;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PlanSpecifier;
import com.ning.billing.catalog.api.ProductCategory;

public class TestCase {
	protected enum Result {
		FOO, BAR, WIBBLE
	}
	protected class CaseResult extends Case<Result>  {

		@XmlElement(required=true)
		private Result policy;

		public CaseResult(Product product, ProductCategory productCategory, BillingPeriod billingPeriod, PriceList priceList,
				 Result policy) {
			super(product, productCategory, billingPeriod, priceList,  policy);
			this.policy = policy;
		}

		@Override
		protected Result getResult() {
			return policy;
		}
	}

	@Test(enabled=true)
	public void testBasic(){
		MockCatalog cat = new MockCatalog();

		Product product = cat.getProducts()[0];
		PriceList priceList = cat.getPriceLists()[0];


		CaseResult cr = new CaseResult(
				product, 
				ProductCategory.BASE,
				BillingPeriod.MONTHLY, 
				priceList,
				Result.FOO);

		assertion(Result.FOO, cr, product.getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), cat);
		assertionNull(cr, "lala", ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), cat);
		assertionNull(cr, product.getName(), ProductCategory.ADD_ON,BillingPeriod.MONTHLY, priceList.getName(), cat);
		assertionNull(cr, product.getName(), ProductCategory.BASE,BillingPeriod.ANNUAL, priceList.getName(), cat);
		assertionNull(cr, product.getName(), ProductCategory.BASE,BillingPeriod.MONTHLY, "dipsy", cat);
	}

	@Test(enabled=true)
	public void testWildCardProduct(){
		MockCatalog cat = new MockCatalog();

		Product product = cat.getProducts()[0];
		PriceList priceList = cat.getPriceLists()[0];


		CaseResult cr = new CaseResult(
				null, 
				ProductCategory.BASE,
				BillingPeriod.MONTHLY, 
				priceList,

				Result.FOO);

		assertion(Result.FOO, cr, product.getName(), ProductCategory.BASE,BillingPeriod.MONTHLY, priceList.getName(), cat);
		assertion(Result.FOO, cr,"lala", ProductCategory.BASE,BillingPeriod.MONTHLY, priceList.getName(), cat);
		assertionNull(cr, product.getName(), ProductCategory.ADD_ON,BillingPeriod.MONTHLY, priceList.getName(), cat);
		assertionNull(cr, product.getName(), ProductCategory.BASE,BillingPeriod.ANNUAL, priceList.getName(), cat);
		assertionNull(cr, product.getName(), ProductCategory.BASE,BillingPeriod.MONTHLY, "dipsy", cat);
	}
	
	@Test(enabled=true)
	public void testWildCardProductCategory(){
		MockCatalog cat = new MockCatalog();

		Product product = cat.getProducts()[0];
		PriceList priceList = cat.getPriceLists()[0];


		CaseResult cr = new CaseResult(
				product, 
				null,
				BillingPeriod.MONTHLY, 
				priceList,

				Result.FOO);

		assertion(Result.FOO, cr, product.getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), cat);
		assertionNull(cr, "lala", ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), cat);
		assertion(Result.FOO, cr, product.getName(), ProductCategory.ADD_ON,BillingPeriod.MONTHLY, priceList.getName(), cat);
		assertionNull(cr, product.getName(), ProductCategory.BASE,BillingPeriod.ANNUAL, priceList.getName(), cat);
		assertionNull(cr, product.getName(), ProductCategory.BASE,BillingPeriod.MONTHLY, "dipsy", cat);
	}
	
	@Test(enabled=true)
	public void testWildCardBillingPeriod(){
		MockCatalog cat = new MockCatalog();

		Product product = cat.getProducts()[0];
		PriceList priceList = cat.getPriceLists()[0];


		CaseResult cr = new CaseResult(
				product, 
				ProductCategory.BASE,
				null, 
				priceList,

				Result.FOO);

		assertion(Result.FOO, cr, product.getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), cat);
		assertionNull(cr, "lala", ProductCategory.BASE,BillingPeriod.MONTHLY, priceList.getName(), cat);
		assertionNull(cr, product.getName(), ProductCategory.ADD_ON,BillingPeriod.MONTHLY, priceList.getName(), cat);
		assertion(Result.FOO,cr, product.getName(), ProductCategory.BASE,BillingPeriod.ANNUAL, priceList.getName(), cat);
		assertionNull(cr, product.getName(), ProductCategory.BASE,BillingPeriod.MONTHLY, "dipsy", cat);
	}

	@Test(enabled=true)
	public void testWildCardPriceList(){
		MockCatalog cat = new MockCatalog();

		Product product = cat.getProducts()[0];
		PriceList priceList = cat.getPriceLists()[0];


		CaseResult cr = new CaseResult(
				product, 
				ProductCategory.BASE,
				BillingPeriod.MONTHLY, 
				null,

				Result.FOO);

		assertion(Result.FOO, cr, product.getName(), ProductCategory.BASE,BillingPeriod.MONTHLY, priceList.getName(), cat);
		assertionNull(cr, "lala", ProductCategory.BASE,BillingPeriod.MONTHLY, priceList.getName(), cat);
		assertionNull(cr, product.getName(), ProductCategory.ADD_ON,BillingPeriod.MONTHLY, priceList.getName(), cat);
		assertionNull(cr, product.getName(), ProductCategory.BASE,BillingPeriod.ANNUAL, priceList.getName(), cat);
		assertion(Result.FOO, cr, product.getName(), ProductCategory.BASE,BillingPeriod.MONTHLY, "dipsy", cat);
	}

	protected void assertionNull(CaseResult cr, String productName, ProductCategory productCategory, BillingPeriod bp, String priceListName, Catalog cat){
		assertNull(cr.getResult(new PlanSpecifier(productName, productCategory, bp, priceListName), cat));
	}

	protected void assertion(Result result, CaseResult cr, String productName, ProductCategory productCategory, BillingPeriod bp, String priceListName,Catalog cat){
		assertEquals(result, cr.getResult(new PlanSpecifier(productName, productCategory, bp, priceListName), cat));
	}


}
