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

import com.ning.billing.catalog.Catalog;
import com.ning.billing.catalog.Duration;
import com.ning.billing.catalog.Plan;
import com.ning.billing.catalog.PlanPhase;
import com.ning.billing.catalog.PriceList;
import com.ning.billing.catalog.PriceListDefault;
import com.ning.billing.catalog.PriceListSet;
import com.ning.billing.catalog.Product;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.catalog.api.TimeUnit;
import com.ning.billing.catalog.rules.CaseCancelPolicy;
import com.ning.billing.catalog.rules.CaseChangePlanAlignment;
import com.ning.billing.catalog.rules.CaseChangePlanPolicy;
import com.ning.billing.catalog.rules.CaseCreateAlignment;
import com.ning.billing.catalog.rules.PlanPolicyChangeRule;
import com.ning.billing.catalog.rules.PlanRules;
import com.ning.billing.catalog.rules.ProductTier;

public class MockCatalog extends Catalog {
	private static final String[] PRODUCT_NAMES = new String[]{ "TestProduct1", "TestProduct2", "TestProduct3"};
	
	public MockCatalog() {
		populateProducts();
		populateRules();
		populateProductTiers();
		populatePlans();
		populatePriceLists();
	}
	
	public void populateRules(){
		setPlanRules(new PlanRules());
	}

	public void setRules(PlanPolicyChangeRule[] rules,
			CaseChangePlanPolicy[] caseChangePlanPolicy,
			CaseChangePlanAlignment[] caseChangePlanAlignment,
			CaseCancelPolicy[] caseCancelPolicy,
			CaseCreateAlignment[] caseCreateAlignment
			){
		
	}

	public void populateProducts() {
		String[] names = getProductNames();
		Product[] products = new Product[names.length];
		for(int i = 0; i < names.length; i++) {
			products[i] = new Product(names[i], ProductCategory.BASE);
		}
		setProducts(products);
	}

	public void populateProductTiers() {
		//default to having a single tier with all products in it
		ProductTier tier = new ProductTier();
		tier.setProducts(getProducts());
		getPlanRules().setProductTiers(new ProductTier[]{tier});
	}
	
	public void populatePlans() {
		Product[] products = getProducts();
		Plan[] plans = new Plan[products.length];
		for(int i = 0; i < products.length; i++) {
			plans[i] = new MockPlan().setName(products[i].getName().toLowerCase() + "-plan").setProduct(products[i]);
		}
		setPlans(plans);
	}

	public void populatePriceLists() {
		Plan[] plans = getPlans();
		
		PriceList[] priceList = new PriceList[plans.length - 1];
		for(int i = 1; i < plans.length; i++) {
			priceList[i-1] = new PriceList(new Plan[]{plans[i]},plans[i].getName()+ "-pl");
		}
		
		PriceListSet set = new PriceListSet(new PriceListDefault(new Plan[]{plans[0]}),priceList);
		setPriceLists(set);
	}
	
	public String[] getProductNames() {
		return PRODUCT_NAMES;
	}


	
}
