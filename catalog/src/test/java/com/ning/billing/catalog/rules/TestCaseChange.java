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

import org.testng.annotations.Test;

import com.ning.billing.catalog.Catalog;
import com.ning.billing.catalog.MockCatalog;
import com.ning.billing.catalog.PriceList;
import com.ning.billing.catalog.Product;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.IPriceListSet;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PlanSpecifier;
import com.ning.billing.catalog.api.ProductCategory;

public class TestCaseChange {
	protected static class CaseChangeResult extends CaseChange<Result>  {

		@XmlElement(required=true)
		private Result result;

		public CaseChangeResult(Product from, Product to, 
				ProductCategory fromProductCategory, ProductCategory toProductCategory, 
				BillingPeriod fromBP, BillingPeriod toBP, 
				PriceList fromPriceList, PriceList toPriceList,
				PhaseType fromType, 
				Result result) {
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
	@Test(enabled=true)
	public void testBasic(){
		MockCatalog cat = new MockCatalog();

		Product product1 = cat.getProducts()[0];
		PriceList priceList1 = cat.getPriceListFromName(IPriceListSet.DEFAULT_PRICELIST_NAME);

		Product product2 = cat.getProducts()[2];
		PriceList priceList2 = cat.getPriceLists().getChildPriceLists()[1];


		CaseChangeResult cr = new CaseChangeResult(
				product1, product2,
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1,priceList2,
				PhaseType.EVERGREEN,
				Result.FOO);

		assertion(Result.FOO, cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				"wrong", product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				product1.getName(), "wrong", 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull( cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.ADD_ON, ProductCategory.BASE,				
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull( cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.ADD_ON,				
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
		
		assertionNull(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				"wrong", priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), "wrong", 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.TRIAL, cat);	
	}
	
	@Test(enabled=true)
	public void testWildcardFromProduct(){
		MockCatalog cat = new MockCatalog();

		Product product1 = cat.getProducts()[0];
		PriceList priceList1 = cat.getPriceListFromName(IPriceListSet.DEFAULT_PRICELIST_NAME);

		Product product2 = cat.getProducts()[2];
		PriceList priceList2 = cat.getPriceLists().getChildPriceLists()[1];


		CaseChangeResult cr = new CaseChangeResult(
				null, product2,
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1,priceList2,
				PhaseType.EVERGREEN,
				Result.FOO);

		assertion(Result.FOO, cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertion(Result.FOO,cr, 
				"wrong", product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
			
		assertionNull( cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.ADD_ON, ProductCategory.BASE,				
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull( cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.ADD_ON,				
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);

		assertionNull(cr, 
				product1.getName(), "wrong", 
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
		
		assertionNull(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				"wrong", priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), "wrong", 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.TRIAL, cat);	
	}
	
	@Test(enabled=true)
	public void testWildcardToProduct(){
		MockCatalog cat = new MockCatalog();

		Product product1 = cat.getProducts()[0];
		PriceList priceList1 = cat.getPriceListFromName(IPriceListSet.DEFAULT_PRICELIST_NAME);

		Product product2 = cat.getProducts()[2];
		PriceList priceList2 = cat.getPriceLists().getChildPriceLists()[1];


		CaseChangeResult cr = new CaseChangeResult(
				product1, null,
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1,priceList2,
				PhaseType.EVERGREEN,
				Result.FOO);

		assertion(Result.FOO, cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				"wrong", product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull( cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.ADD_ON, ProductCategory.BASE,				
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull( cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.ADD_ON,				
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertion(Result.FOO, cr, 
				product1.getName(), "wrong", 
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
		
		assertionNull(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				"wrong", priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), "wrong", 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.TRIAL, cat);	
	}
	
	@Test(enabled=true)
	public void testWildcardFromProductCategory(){
		MockCatalog cat = new MockCatalog();

		Product product1 = cat.getProducts()[0];
		PriceList priceList1 = cat.getPriceListFromName(IPriceListSet.DEFAULT_PRICELIST_NAME);

		Product product2 = cat.getProducts()[2];
		PriceList priceList2 = cat.getPriceLists().getChildPriceLists()[1];


		CaseChangeResult cr = new CaseChangeResult(
				product1, product2,
				null, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1,priceList2,
				PhaseType.EVERGREEN,
				Result.FOO);

		assertion(Result.FOO, cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				"wrong", product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				product1.getName(), "wrong", 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertion(Result.FOO, cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.ADD_ON, ProductCategory.BASE,				
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull( cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.ADD_ON,				
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
		
		assertionNull(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				"wrong", priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), "wrong", 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.TRIAL, cat);	
	}
	
	@Test(enabled=true)
	public void testWildcardToProductCategory(){
		MockCatalog cat = new MockCatalog();

		Product product1 = cat.getProducts()[0];
		PriceList priceList1 = cat.getPriceListFromName(IPriceListSet.DEFAULT_PRICELIST_NAME);

		Product product2 = cat.getProducts()[2];
		PriceList priceList2 = cat.getPriceLists().getChildPriceLists()[1];


		CaseChangeResult cr = new CaseChangeResult(
				product1, product2,
				ProductCategory.BASE, null,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1,priceList2,
				PhaseType.EVERGREEN,
				Result.FOO);

		assertion(Result.FOO, cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				"wrong", product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				product1.getName(), "wrong", 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull( cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.ADD_ON, ProductCategory.BASE,				
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertion(Result.FOO, cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.ADD_ON,				
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
		
		assertionNull(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				"wrong", priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), "wrong", 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.TRIAL, cat);	
	}
	
	@Test(enabled=true)
	public void testWildcardFromBillingPeriod(){
		MockCatalog cat = new MockCatalog();

		Product product1 = cat.getProducts()[0];
		PriceList priceList1 = cat.getPriceListFromName(IPriceListSet.DEFAULT_PRICELIST_NAME);

		Product product2 = cat.getProducts()[2];
		PriceList priceList2 = cat.getPriceLists().getChildPriceLists()[1];


		CaseChangeResult cr = new CaseChangeResult(
				product1, product2,
				ProductCategory.BASE, ProductCategory.BASE,
				null, BillingPeriod.MONTHLY, 
				priceList1, priceList2,
				PhaseType.EVERGREEN,
				Result.FOO);

		assertion(Result.FOO, cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				"wrong", product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull( cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.ADD_ON, ProductCategory.BASE,				
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull( cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.ADD_ON,				
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				product1.getName(), "wrong", 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertion(Result.FOO,cr, 
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
		
		assertionNull(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				"wrong", priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), "wrong", 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.TRIAL, cat);	
	}
	
	
	@Test(enabled=true)
	public void testWildCardToBillingPeriod(){
		MockCatalog cat = new MockCatalog();

		Product product1 = cat.getProducts()[0];
		PriceList priceList1 = cat.getPriceListFromName(IPriceListSet.DEFAULT_PRICELIST_NAME);

		Product product2 = cat.getProducts()[2];
		PriceList priceList2 = cat.getPriceLists().getChildPriceLists()[1];


		CaseChangeResult cr = new CaseChangeResult(
				product1, product2,
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, null, 
				priceList1,priceList2,
				PhaseType.EVERGREEN,
				Result.FOO);

		assertion(Result.FOO, cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				"wrong", product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull( cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.ADD_ON, ProductCategory.BASE,				
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull( cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.ADD_ON,				
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				product1.getName(), "wrong", 
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
		
		assertion(Result.FOO,cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.ANNUAL, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				"wrong", priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), "wrong", 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.TRIAL, cat);	
	}
	
	@Test(enabled=true)
	public void testWildCardFromPriceList(){
		MockCatalog cat = new MockCatalog();

		Product product1 = cat.getProducts()[0];
		PriceList priceList1 = cat.getPriceListFromName(IPriceListSet.DEFAULT_PRICELIST_NAME);

		Product product2 = cat.getProducts()[2];
		PriceList priceList2 = cat.getPriceLists().getChildPriceLists()[1];


		CaseChangeResult cr = new CaseChangeResult(
				product1, product2,
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				null,priceList2,
				PhaseType.EVERGREEN,
				Result.FOO);

		assertion(Result.FOO, cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				"wrong", product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull( cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.ADD_ON, ProductCategory.BASE,				
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull( cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.ADD_ON,				
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				product1.getName(), "wrong", 
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
		
		assertion(Result.FOO,cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				"wrong", priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), "wrong", 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.TRIAL, cat);	
	}
	
	@Test(enabled=true)
	public void testWildcardToPriceList(){
		MockCatalog cat = new MockCatalog();

		Product product1 = cat.getProducts()[0];
		PriceList priceList1 = cat.getPriceListFromName(IPriceListSet.DEFAULT_PRICELIST_NAME);

		Product product2 = cat.getProducts()[2];
		PriceList priceList2 = cat.getPriceLists().getChildPriceLists()[1];


		CaseChangeResult cr = new CaseChangeResult(
				product1, product2,
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1,null,
				PhaseType.EVERGREEN,
				Result.FOO);

		assertion(Result.FOO, cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				"wrong", product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull( cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.ADD_ON, ProductCategory.BASE,				
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull( cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.ADD_ON,				
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				product1.getName(), "wrong", 
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
		
		assertionNull(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				"wrong", priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertion(Result.FOO,cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), "wrong", 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.TRIAL, cat);	
	}
	
	@Test(enabled=true)
	public void testWildcardPlanPhase(){
		MockCatalog cat = new MockCatalog();

		Product product1 = cat.getProducts()[0];
		PriceList priceList1 = cat.getPriceListFromName(IPriceListSet.DEFAULT_PRICELIST_NAME);

		Product product2 = cat.getProducts()[2];
		PriceList priceList2 = cat.getPriceLists().getChildPriceLists()[1];


		CaseChangeResult cr = new CaseChangeResult(
				product1, product2,
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1,priceList2,
				null,
				Result.FOO);

		assertion(Result.FOO, cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				"wrong", product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull( cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.ADD_ON, ProductCategory.BASE,				
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull( cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.ADD_ON,				
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				product1.getName(), "wrong", 
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
		
		assertionNull(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				"wrong", priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), "wrong", 
				PhaseType.EVERGREEN, cat);
		
		assertion(Result.FOO,cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.TRIAL, cat);	
	}
	
	
	@Test(enabled=true)
	public void testOrder(){
		MockCatalog cat = new MockCatalog();

		Product product1 = cat.getProducts()[0];
		PriceList priceList1 = cat.getPriceListFromName(IPriceListSet.DEFAULT_PRICELIST_NAME);

		Product product2 = cat.getProducts()[2];
		PriceList priceList2 = cat.getPriceLists().getChildPriceLists()[1];


		CaseChangeResult cr0 = new CaseChangeResult(
				product1, product2,
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1,priceList2,
				PhaseType.EVERGREEN,
				Result.FOO);

		CaseChangeResult cr1 = new CaseChangeResult(
				product1, product2,
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1,priceList2,
				PhaseType.EVERGREEN,
				Result.BAR);

		CaseChangeResult cr2 = new CaseChangeResult(
				product1, product2,
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1,priceList2,
				PhaseType.EVERGREEN,
				Result.TINKYWINKY);

		CaseChangeResult cr3 = new CaseChangeResult(
				product1, product2,
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.ANNUAL, 
				priceList1,priceList2,
				PhaseType.EVERGREEN,
				Result.DIPSY);

		CaseChangeResult cr4 = new CaseChangeResult(
				product1, product2,
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.ANNUAL, 
				priceList1,priceList2,
				PhaseType.EVERGREEN,
				Result.LALA);
		
		Result r1 = CaseChange.getResult(new CaseChangeResult[]{ cr0, cr1, cr2, cr3, cr4 }, 
				new PlanPhaseSpecifier(product1.getName(), product1.getCategory(), BillingPeriod.MONTHLY, priceList1.getName(), PhaseType.EVERGREEN), 
				new PlanSpecifier(product2.getName(), product2.getCategory(), BillingPeriod.MONTHLY, priceList2.getName()), cat);

		assertEquals(Result.FOO,r1);
		
		Result r2 = CaseChange.getResult(new CaseChangeResult[]{ cr0, cr1, cr2, cr3, cr4 }, 
				new PlanPhaseSpecifier(product1.getName(), product1.getCategory(), BillingPeriod.MONTHLY, priceList1.getName(), PhaseType.EVERGREEN), 
				new PlanSpecifier(product2.getName(), product2.getCategory(), BillingPeriod.ANNUAL, priceList2.getName()), cat);

		assertEquals(Result.DIPSY,r2);
		
	}
	
	
	protected void assertionNull(CaseChangeResult cr, 
			String fromProductName, String toProductName,
			ProductCategory fromProductCategory, ProductCategory toProductCategory, 
			BillingPeriod fromBp, BillingPeriod toBp,
			String fromPriceListName, String toPriceListName,
			PhaseType phaseType, Catalog cat){
		assertNull(cr.getResult(new PlanPhaseSpecifier(fromProductName, fromProductCategory, fromBp, fromPriceListName, phaseType), 
								new PlanSpecifier(toProductName, toProductCategory, toBp, toPriceListName),cat));
	}

	protected void assertion(Result result, CaseChangeResult cr, 
			String fromProductName, String toProductName,
			ProductCategory fromProductCategory, ProductCategory toProductCategory, 
			BillingPeriod fromBp, BillingPeriod toBp,
			String fromPriceListName, String toPriceListName,
			PhaseType phaseType, Catalog cat){
		assertEquals(result, cr.getResult(new PlanPhaseSpecifier(fromProductName, fromProductCategory,fromBp, fromPriceListName, phaseType), 
								new PlanSpecifier(toProductName, toProductCategory, toBp, toPriceListName),cat));
	}

}
