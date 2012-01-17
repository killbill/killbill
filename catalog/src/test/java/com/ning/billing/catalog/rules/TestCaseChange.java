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

import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.DefaultPriceList;
import com.ning.billing.catalog.DefaultProduct;
import com.ning.billing.catalog.MockCatalog;
import com.ning.billing.catalog.StandaloneCatalog;
import com.ning.billing.catalog.api.*;
import com.ning.billing.catalog.rules.TestCase.CaseResult;

import org.testng.Assert;
import org.testng.annotations.Test;

import javax.xml.bind.annotation.XmlElement;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;

public class TestCaseChange {
	protected static class CaseChangeResult extends CaseChange<Result>  {

		@XmlElement(required=true)
		private Result result;

		public CaseChangeResult(DefaultProduct from, DefaultProduct to, 
				ProductCategory fromProductCategory, ProductCategory toProductCategory, 
				BillingPeriod fromBP, BillingPeriod toBP, 
				DefaultPriceList fromPriceList, DefaultPriceList toPriceList,
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
	public void testBasic() throws CatalogApiException{
		MockCatalog cat = new MockCatalog();

		DefaultProduct product1 = cat.getCurrentProducts()[0];
		DefaultPriceList priceList1 = cat.findCurrentPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

		DefaultProduct product2 = cat.getCurrentProducts()[2];
		DefaultPriceList priceList2 = cat.getPriceLists().getChildPriceLists()[1];


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
				 cat.getCurrentProducts()[1].getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				product1.getName(),  cat.getCurrentProducts()[1].getName(), 
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
		
		assertionException(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				 cat.getCurrentProducts()[1].getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionException(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(),  cat.getCurrentProducts()[1].getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.TRIAL, cat);	
	}
	
	@Test(enabled=true)
	public void testWildcardFromProduct()throws CatalogApiException{
		MockCatalog cat = new MockCatalog();

		DefaultProduct product1 = cat.getCurrentProducts()[0];
		DefaultPriceList priceList1 = cat.findCurrentPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

		DefaultProduct product2 = cat.getCurrentProducts()[2];
		DefaultPriceList priceList2 = cat.getPriceLists().getChildPriceLists()[1];


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
				 cat.getCurrentProducts()[1].getName(), product2.getName(), 
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
				product1.getName(),  cat.getCurrentProducts()[1].getName(), 
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
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				 cat.getCurrentProducts()[1].getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionException(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(),  cat.getCurrentProducts()[1].getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.TRIAL, cat);	
	}
	
	@Test(enabled=true)
	public void testWildcardToProduct() throws CatalogApiException{
		MockCatalog cat = new MockCatalog();

		DefaultProduct product1 = cat.getCurrentProducts()[0];
		DefaultPriceList priceList1 = cat.findCurrentPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

		DefaultProduct product2 = cat.getCurrentProducts()[2];
		DefaultPriceList priceList2 = cat.getPriceLists().getChildPriceLists()[1];


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
				 cat.getCurrentProducts()[1].getName(), product2.getName(), 
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
				product1.getName(),  cat.getCurrentProducts()[1].getName(), 
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
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				 cat.getCurrentProducts()[1].getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionException(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(),  cat.getCurrentProducts()[1].getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.TRIAL, cat);	
	}
	
	@Test(enabled=true)
	public void testWildcardFromProductCategory() throws CatalogApiException{
		MockCatalog cat = new MockCatalog();

		DefaultProduct product1 = cat.getCurrentProducts()[0];
		DefaultPriceList priceList1 = cat.findCurrentPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

		DefaultProduct product2 = cat.getCurrentProducts()[2];
		DefaultPriceList priceList2 = cat.getPriceLists().getChildPriceLists()[1];


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
				 cat.getCurrentProducts()[1].getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				product1.getName(),  cat.getCurrentProducts()[1].getName(), 
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
		
		assertionException(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				 cat.getCurrentProducts()[1].getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionException(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(),  cat.getCurrentProducts()[1].getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.TRIAL, cat);	
	}
	
	@Test(enabled=true)
	public void testWildcardToProductCategory() throws CatalogApiException{
		MockCatalog cat = new MockCatalog();

		DefaultProduct product1 = cat.getCurrentProducts()[0];
		DefaultPriceList priceList1 = cat.findCurrentPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

		DefaultProduct product2 = cat.getCurrentProducts()[2];
		DefaultPriceList priceList2 = cat.getPriceLists().getChildPriceLists()[1];


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
				 cat.getCurrentProducts()[1].getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				product1.getName(),  cat.getCurrentProducts()[1].getName(), 
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
		
		assertionException(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				 cat.getCurrentProducts()[1].getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionException(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(),  cat.getCurrentProducts()[1].getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.TRIAL, cat);	
	}
	
	@Test(enabled=true)
	public void testWildcardFromBillingPeriod() throws CatalogApiException{
		MockCatalog cat = new MockCatalog();

		DefaultProduct product1 = cat.getCurrentProducts()[0];
		DefaultPriceList priceList1 = cat.findCurrentPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

		DefaultProduct product2 = cat.getCurrentProducts()[2];
		DefaultPriceList priceList2 = cat.getPriceLists().getChildPriceLists()[1];


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
				 cat.getCurrentProducts()[1].getName(), product2.getName(), 
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
				product1.getName(),  cat.getCurrentProducts()[1].getName(), 
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
		
		assertionException(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				 cat.getCurrentProducts()[1].getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionException(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(),  cat.getCurrentProducts()[1].getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.TRIAL, cat);	
	}
	
	
	@Test(enabled=true)
	public void testWildCardToBillingPeriod() throws CatalogApiException{
		MockCatalog cat = new MockCatalog();

		DefaultProduct product1 = cat.getCurrentProducts()[0];
		DefaultPriceList priceList1 = cat.findCurrentPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

		DefaultProduct product2 = cat.getCurrentProducts()[2];
		DefaultPriceList priceList2 = cat.getPriceLists().getChildPriceLists()[1];


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
				 cat.getCurrentProducts()[1].getName(), product2.getName(), 
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
				product1.getName(),  cat.getCurrentProducts()[1].getName(), 
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
		
		assertionException(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				 cat.getCurrentProducts()[1].getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionException(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(),  cat.getCurrentProducts()[1].getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.TRIAL, cat);	
	}
	
	@Test(enabled=true)
	public void testWildCardFromPriceList() throws CatalogApiException{
		MockCatalog cat = new MockCatalog();

		DefaultProduct product1 = cat.getCurrentProducts()[0];
		DefaultPriceList priceList1 = cat.findCurrentPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

		DefaultProduct product2 = cat.getCurrentProducts()[2];
		DefaultPriceList priceList2 = cat.getPriceLists().getChildPriceLists()[1];


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
				 cat.getCurrentProducts()[1].getName(), product2.getName(), 
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
				product1.getName(),  cat.getCurrentProducts()[1].getName(), 
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
				 cat.getCurrentProducts()[1].getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionException(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(),  cat.getCurrentProducts()[1].getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.TRIAL, cat);	
	}
	
	@Test(enabled=true)
	public void testWildcardToPriceList() throws CatalogApiException{
		MockCatalog cat = new MockCatalog();

		DefaultProduct product1 = cat.getCurrentProducts()[0];
		DefaultPriceList priceList1 = cat.findCurrentPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

		DefaultProduct product2 = cat.getCurrentProducts()[2];
		DefaultPriceList priceList2 = cat.getPriceLists().getChildPriceLists()[1];


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
				 cat.getCurrentProducts()[1].getName(), product2.getName(), 
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
				product1.getName(),  cat.getCurrentProducts()[1].getName(), 
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
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				 cat.getCurrentProducts()[1].getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertion(Result.FOO,cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(),  cat.getCurrentProducts()[1].getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionNull(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.TRIAL, cat);	
	}
	
	@Test(enabled=true)
	public void testWildcardPlanPhase() throws CatalogApiException{
		MockCatalog cat = new MockCatalog();

		DefaultProduct product1 = cat.getCurrentProducts()[0];
		DefaultPriceList priceList1 = cat.findCurrentPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

		DefaultProduct product2 = cat.getCurrentProducts()[2];
		DefaultPriceList priceList2 = cat.getPriceLists().getChildPriceLists()[1];


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
				 cat.getCurrentProducts()[1].getName(), product2.getName(), 
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
				product1.getName(),  cat.getCurrentProducts()[1].getName(), 
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
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				 cat.getCurrentProducts()[1].getName(), priceList2.getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertionException(cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(),  cat.getCurrentProducts()[1].getName(), 
				PhaseType.EVERGREEN, cat);
		
		assertion(Result.FOO,cr, 
				product1.getName(), product2.getName(), 
				ProductCategory.BASE, ProductCategory.BASE,
				BillingPeriod.MONTHLY, BillingPeriod.MONTHLY, 
				priceList1.getName(), priceList2.getName(), 
				PhaseType.TRIAL, cat);	
	}
	
	
	@Test(enabled=true)
	public void testOrder() throws CatalogApiException{
		MockCatalog cat = new MockCatalog();

		DefaultProduct product1 = cat.getCurrentProducts()[0];
		DefaultPriceList priceList1 = cat.findCurrentPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

		DefaultProduct product2 = cat.getCurrentProducts()[2];
		DefaultPriceList priceList2 = cat.getPriceLists().getChildPriceLists()[1];


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
			PhaseType phaseType, StandaloneCatalog cat){
		try {
			assertNull(cr.getResult(new PlanPhaseSpecifier(fromProductName, fromProductCategory, fromBp, fromPriceListName, phaseType), 
									new PlanSpecifier(toProductName, toProductCategory, toBp, toPriceListName),cat));
		} catch (CatalogApiException e) {
			Assert.fail("", e);
		}
	}
	   protected void assertionException(CaseChangeResult cr, 
				String fromProductName, String toProductName,
				ProductCategory fromProductCategory, ProductCategory toProductCategory, 
				BillingPeriod fromBp, BillingPeriod toBp,
				String fromPriceListName, String toPriceListName,
				PhaseType phaseType, StandaloneCatalog cat){
	        try{
	        	cr.getResult(new PlanPhaseSpecifier(fromProductName, fromProductCategory, fromBp, fromPriceListName, phaseType), 
						new PlanSpecifier(toProductName, toProductCategory, toBp, toPriceListName),cat);	
	        	Assert.fail("Expecting an exception");
	        } catch (CatalogApiException e) {
	        	Assert.assertEquals(e.getCode(), ErrorCode.CAT_PRICE_LIST_NOT_FOUND.getCode());
	        }
	    }
	protected void assertion(Result result, CaseChangeResult cr, 
			String fromProductName, String toProductName,
			ProductCategory fromProductCategory, ProductCategory toProductCategory, 
			BillingPeriod fromBp, BillingPeriod toBp,
			String fromPriceListName, String toPriceListName,
			PhaseType phaseType, StandaloneCatalog cat){
		try {
			assertEquals(result, cr.getResult(new PlanPhaseSpecifier(fromProductName, fromProductCategory,fromBp, fromPriceListName, phaseType), 
					new PlanSpecifier(toProductName, toProductCategory, toBp, toPriceListName),cat));
		} catch (CatalogApiException e) {
			Assert.fail("", e);
		}
	}

}
