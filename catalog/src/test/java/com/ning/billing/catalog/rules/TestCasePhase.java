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

import javax.xml.bind.annotation.XmlElement;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.Catalog;
import com.ning.billing.catalog.MockCatalog;
import com.ning.billing.catalog.PriceList;
import com.ning.billing.catalog.Product;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.catalog.rules.CasePhase;

public class TestCasePhase {
	protected class CaseResult extends CasePhase<Result>  {

		@XmlElement(required=true)
		private Result policy;

		public CaseResult(Product product, ProductCategory productCategory, BillingPeriod billingPeriod, PriceList priceList,
				PhaseType phaseType, Result policy) {
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

	@Test(enabled=true)
	public void testBasic(){
		MockCatalog cat = new MockCatalog();

		Product product = cat.getProducts()[0];
		PriceList priceList = cat.getPriceLists().getDefaultPricelist();


		CaseResult cr = new CaseResult(
				product, 
				ProductCategory.BASE,
				BillingPeriod.MONTHLY, 
				priceList,
				PhaseType.EVERGREEN, 
				Result.FOO);

		assertion(Result.FOO, cr, product.getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), PhaseType.EVERGREEN, cat);
		assertionNull(cr,  cat.getProducts()[1].getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), PhaseType.EVERGREEN, cat);
		assertionNull(cr, product.getName(), ProductCategory.ADD_ON,BillingPeriod.MONTHLY, priceList.getName(), PhaseType.EVERGREEN, cat);
		assertionNull(cr, product.getName(), ProductCategory.BASE,BillingPeriod.ANNUAL, priceList.getName(), PhaseType.EVERGREEN, cat);
		assertionNull(cr, product.getName(), ProductCategory.BASE,BillingPeriod.MONTHLY, "dipsy", PhaseType.EVERGREEN, cat);
		assertionNull(cr, product.getName(), ProductCategory.BASE,BillingPeriod.MONTHLY, priceList.getName(), PhaseType.TRIAL, cat);
	}

	@Test(enabled=true)
	public void testWildCardProduct(){
		MockCatalog cat = new MockCatalog();

		Product product = cat.getProducts()[0];
		PriceList priceList = cat.getPriceLists().getDefaultPricelist();


		CaseResult cr = new CaseResult(
				null, 
				ProductCategory.BASE,
				BillingPeriod.MONTHLY, 
				priceList,
				PhaseType.EVERGREEN, 
				Result.FOO);

		assertion(Result.FOO, cr, product.getName(), ProductCategory.BASE,BillingPeriod.MONTHLY, priceList.getName(), PhaseType.EVERGREEN, cat);
		assertion(Result.FOO, cr, cat.getProducts()[1].getName(), ProductCategory.BASE,BillingPeriod.MONTHLY, priceList.getName(), PhaseType.EVERGREEN, cat);
		assertionNull(cr, product.getName(), ProductCategory.ADD_ON,BillingPeriod.MONTHLY, priceList.getName(), PhaseType.EVERGREEN, cat);
		assertionNull(cr, product.getName(), ProductCategory.BASE,BillingPeriod.ANNUAL, priceList.getName(), PhaseType.EVERGREEN, cat);
		assertionNull(cr, product.getName(), ProductCategory.BASE,BillingPeriod.MONTHLY, "dipsy", PhaseType.EVERGREEN, cat);
		assertionNull(cr, product.getName(), ProductCategory.BASE,BillingPeriod.MONTHLY, priceList.getName(), PhaseType.TRIAL, cat);
	}
	
	@Test(enabled=true)
	public void testWildCardProductCategory(){
		MockCatalog cat = new MockCatalog();

		Product product = cat.getProducts()[0];
		PriceList priceList = cat.getPriceLists().getDefaultPricelist();


		CaseResult cr = new CaseResult(
				product, 
				null,
				BillingPeriod.MONTHLY, 
				priceList,
				PhaseType.EVERGREEN, 
				Result.FOO);

		assertion(Result.FOO, cr, product.getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), PhaseType.EVERGREEN, cat);
		assertionNull(cr,  cat.getProducts()[1].getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), PhaseType.EVERGREEN, cat);
		assertion(Result.FOO, cr, product.getName(), ProductCategory.ADD_ON,BillingPeriod.MONTHLY, priceList.getName(), PhaseType.EVERGREEN, cat);
		assertionNull(cr, product.getName(), ProductCategory.BASE,BillingPeriod.ANNUAL, priceList.getName(), PhaseType.EVERGREEN, cat);
		assertionNull(cr, product.getName(), ProductCategory.BASE,BillingPeriod.MONTHLY, "dipsy", PhaseType.EVERGREEN, cat);
		assertionNull(cr, product.getName(), ProductCategory.BASE,BillingPeriod.MONTHLY, priceList.getName(), PhaseType.TRIAL, cat);
	}
	
	@Test(enabled=true)
	public void testWildCardBillingPeriod(){
		MockCatalog cat = new MockCatalog();

		Product product = cat.getProducts()[0];
		PriceList priceList = cat.getPriceLists().getDefaultPricelist();


		CaseResult cr = new CaseResult(
				product, 
				ProductCategory.BASE,
				null, 
				priceList,
				PhaseType.EVERGREEN, 
				Result.FOO);

		assertion(Result.FOO, cr, product.getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), PhaseType.EVERGREEN, cat);
		assertionNull(cr,  cat.getProducts()[1].getName(), ProductCategory.BASE,BillingPeriod.MONTHLY, priceList.getName(), PhaseType.EVERGREEN, cat);
		assertionNull(cr, product.getName(), ProductCategory.ADD_ON,BillingPeriod.MONTHLY, priceList.getName(), PhaseType.EVERGREEN, cat);
		assertion(Result.FOO,cr, product.getName(), ProductCategory.BASE,BillingPeriod.ANNUAL, priceList.getName(), PhaseType.EVERGREEN, cat);
		assertionNull(cr, product.getName(), ProductCategory.BASE,BillingPeriod.MONTHLY, "dipsy", PhaseType.EVERGREEN, cat);
		assertionNull(cr, product.getName(), ProductCategory.BASE,BillingPeriod.MONTHLY, priceList.getName(), PhaseType.TRIAL, cat);
	}

	@Test(enabled=true)
	public void testWildCardPriceList(){
		MockCatalog cat = new MockCatalog();

		Product product = cat.getProducts()[0];
		PriceList priceList = cat.getPriceLists().getDefaultPricelist();


		CaseResult cr = new CaseResult(
				product, 
				ProductCategory.BASE,
				BillingPeriod.MONTHLY, 
				null,
				PhaseType.EVERGREEN, 
				Result.FOO);

		assertion(Result.FOO, cr, product.getName(), ProductCategory.BASE,BillingPeriod.MONTHLY, priceList.getName(), PhaseType.EVERGREEN, cat);
		assertionNull(cr,  cat.getProducts()[1].getName(), ProductCategory.BASE,BillingPeriod.MONTHLY, priceList.getName(), PhaseType.EVERGREEN, cat);
		assertionNull(cr, product.getName(), ProductCategory.ADD_ON,BillingPeriod.MONTHLY, priceList.getName(), PhaseType.EVERGREEN, cat);
		assertionNull(cr, product.getName(), ProductCategory.BASE,BillingPeriod.ANNUAL, priceList.getName(), PhaseType.EVERGREEN, cat);
		assertion(Result.FOO, cr, product.getName(), ProductCategory.BASE,BillingPeriod.MONTHLY, "dipsy", PhaseType.EVERGREEN, cat);
		assertionNull(cr, product.getName(), ProductCategory.BASE,BillingPeriod.MONTHLY, priceList.getName(), PhaseType.TRIAL, cat);
	}

	@Test(enabled=true)
	public void testWildCardPhaseType(){
		MockCatalog cat = new MockCatalog();

		Product product = cat.getProducts()[0];
		PriceList priceList = cat.getPriceLists().getDefaultPricelist();


		CaseResult cr = new CaseResult(
				product, 
				ProductCategory.BASE,
				BillingPeriod.MONTHLY, 
				priceList,
				null, 
				Result.FOO);

		assertion(Result.FOO, cr, product.getName(), ProductCategory.BASE,BillingPeriod.MONTHLY, priceList.getName(), PhaseType.EVERGREEN, cat);
		assertionNull(cr,  cat.getProducts()[1].getName(), ProductCategory.BASE,BillingPeriod.MONTHLY, priceList.getName(), PhaseType.EVERGREEN, cat);
		assertionNull(cr, product.getName(), ProductCategory.ADD_ON,BillingPeriod.MONTHLY, priceList.getName(), PhaseType.EVERGREEN, cat);
		assertionNull(cr, product.getName(), ProductCategory.BASE,BillingPeriod.ANNUAL, priceList.getName(), PhaseType.EVERGREEN, cat);
		assertionNull(cr, product.getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, "dipsy", PhaseType.EVERGREEN, cat);
		assertion(Result.FOO,cr, product.getName(), ProductCategory.BASE,BillingPeriod.MONTHLY, priceList.getName(), PhaseType.TRIAL, cat);
	}
	
	@Test(enabled=true)
	public void testOrder() throws CatalogApiException{
		MockCatalog cat = new MockCatalog();

		Product product = cat.getProducts()[0];
		PriceList priceList = cat.getPriceLists().getDefaultPricelist();


		CaseResult cr0 = new CaseResult(
				product, 
				ProductCategory.BASE,
				BillingPeriod.MONTHLY, 
				priceList,
				PhaseType.EVERGREEN, 
				Result.FOO);

		CaseResult cr1 = new CaseResult(
				product, 
				ProductCategory.BASE,
				BillingPeriod.MONTHLY, 
				priceList,
				PhaseType.EVERGREEN, 
				Result.BAR);

		CaseResult cr2 = new CaseResult(
				product, 
				ProductCategory.BASE,
				BillingPeriod.MONTHLY, 
				priceList,
				PhaseType.EVERGREEN, 
				Result.TINKYWINKY);

		CaseResult cr3 = new CaseResult(
				product, 
				ProductCategory.BASE,
				BillingPeriod.ANNUAL, 
				priceList,
				PhaseType.EVERGREEN, 
				Result.DIPSY);

		CaseResult cr4 = new CaseResult(
				product, 
				ProductCategory.BASE,
				BillingPeriod.ANNUAL, 
				priceList,
				PhaseType.EVERGREEN, 
				Result.LALA);
		
		Result r1 = CasePhase.getResult(new CaseResult[]{cr0, cr1, cr2,cr3,cr4}, 
				new PlanPhaseSpecifier(product.getName(), product.getCategory(), BillingPeriod.MONTHLY, priceList.getName(), PhaseType.EVERGREEN), cat);
		
		Assert.assertEquals(Result.FOO, r1);

		Result r2 = CasePhase.getResult(new CaseResult[]{cr0, cr1, cr2,cr3,cr4}, 
				new PlanPhaseSpecifier(product.getName(), product.getCategory(), BillingPeriod.ANNUAL, priceList.getName(), PhaseType.EVERGREEN), cat);
		
		Assert.assertEquals(Result.DIPSY, r2);

	}


	protected void assertionNull(CaseResult cr, String productName, ProductCategory productCategory, BillingPeriod bp, String priceListName, PhaseType phaseType, Catalog cat){
		try {
			Assert.assertNull(cr.getResult(new PlanPhaseSpecifier(productName, productCategory, bp, priceListName, phaseType), cat));
		} catch (CatalogApiException e) {
			Assert.fail("", e);
		}
	}

	protected void assertion(Result result, CaseResult cr, String productName, ProductCategory productCategory, BillingPeriod bp, String priceListName, PhaseType phaseType, Catalog cat){
		try {
			Assert.assertEquals(result, cr.getResult(new PlanPhaseSpecifier(productName, productCategory, bp, priceListName, phaseType), cat));
		} catch (CatalogApiException e) {
			Assert.fail("", e);
		}
	}


}
