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


public class TestPlanRules {

	public Catalog createCatalog(PlanChangeRule[] generalRules, 
			PlanChangeCase[] specialCaseRules,
			PlanCancelCase[] cancelCaseRules,
					Product fromP,
					Product toP) {
		Catalog c = new Catalog();
		PlanRules pcr = new PlanRules();
		pcr.setGeneralRules(generalRules);
		pcr.setSpecialCaseRules(specialCaseRules);
		pcr.setCancelCaseRules(cancelCaseRules);
		c.setPlanChangeRules(pcr);
		pcr.setProductTiers(new ProductTier[] {new ProductTier(new Product[]{ fromP, toP })});
		c.setProducts(new Product[] { fromP, toP });
		return c;
	}
	
	public Catalog createCatalog(PlanChangeRule[] generalRules, 
			PlanChangeCase[] specialCaseRules,
			PlanCancelCase[] cancelCaseRules,
			PlanAlignmentCase[] alignmentCases,
					Product fromP,
					Product toP) {
		Catalog c = new Catalog();
		PlanRules pcr = new PlanRules();
		pcr.setGeneralRules(generalRules);
		pcr.setSpecialCaseRules(specialCaseRules);
		pcr.setCancelCaseRules(cancelCaseRules);
		pcr.setAlignmentCase(alignmentCases);
		c.setPlanChangeRules(pcr);
		pcr.setProductTiers(new ProductTier[] {new ProductTier(new Product[]{ fromP, toP })});
		c.setProducts(new Product[] { fromP, toP });
		return c;
	}

	
	public Product createProduct(String name) {
		ProductType type = new ProductType("TestType");
		return new Product(type, name);
	}
	

	protected PriceList createPriceList(String name) {
		PriceList result = new PriceList();
		result.setName(name);
		return result;
	}


}
