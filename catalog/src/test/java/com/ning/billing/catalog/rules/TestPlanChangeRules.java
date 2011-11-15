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

import static com.ning.billing.catalog.api.ActionPolicy.END_OF_TERM;
import static com.ning.billing.catalog.api.ActionPolicy.IMMEDIATE;
import static com.ning.billing.catalog.api.BillingPeriod.ANNUAL;
import static com.ning.billing.catalog.api.BillingPeriod.MONTHLY;
import static com.ning.billing.catalog.api.PhaseType.EVERGREEN;
import static com.ning.billing.catalog.api.PhaseType.TRIAL;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;

import org.testng.annotations.Test;

import com.ning.billing.catalog.Catalog;
import com.ning.billing.catalog.Product;
import com.ning.billing.catalog.api.ActionPolicy;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PlanSpecifier;
import com.ning.billing.catalog.rules.CaseChangePlanPolicy;
import com.ning.billing.catalog.rules.PlanPolicyChangeRule;
import com.ning.billing.catalog.rules.PlanPolicyChangeRule.Qualifier;

public class TestPlanChangeRules extends TestPlanRules {

	@Test(enabled=true)
	public void testDefault() {
		Product P1 = createProduct("FP");
		Product P2 = createProduct("BP");

		Catalog c = createCatalog(
				new PlanPolicyChangeRule[]{
						new PlanPolicyChangeRule(Qualifier.DEFAULT, ActionPolicy.END_OF_TERM, null)
				}, 
				null,
				null,
				P1,
				P2
				);
		assertEquals(ActionPolicy.END_OF_TERM,c.getPlanChangePolicy(
				new PlanPhaseSpecifier("FP", null, ANNUAL, null, EVERGREEN),
				new PlanSpecifier("BP", null, ANNUAL, null)
				));
	}

	@Test(enabled=true)
	public void testBillingPeriodRule() {
		Product P1 = createProduct("FP");
		Product P2 = createProduct("BP");

		Catalog c = createCatalog(
				new PlanPolicyChangeRule[]{
						new PlanPolicyChangeRule(Qualifier.TERM_FROM_LONG_TO_SHORT, END_OF_TERM, null)
				}, 
				null,
				null,
				P1,
				P2
				);
		
		assertEquals(END_OF_TERM,c.getPlanChangePolicy(
				new PlanPhaseSpecifier("FP", null, ANNUAL, null, EVERGREEN),
				new PlanSpecifier("BP", null, MONTHLY, null)
				));
		
		assertNull(c.getPlanChangePolicy(
				new PlanPhaseSpecifier("FP", null, MONTHLY, null, EVERGREEN),
				new PlanSpecifier("BP", null, ANNUAL, null)
				));


	
	}

	@Test(enabled=true)
	public void testBillingPeriodRule2() {
		Product P1 = createProduct("FP");
		Product P2 = createProduct("BP");

		Catalog c = createCatalog(
				new PlanPolicyChangeRule[]{
						new PlanPolicyChangeRule(Qualifier.TERM_FROM_SHORT_TO_LONG, END_OF_TERM, null)
				}, 
				null,
				null,
				P1,
				P2
				);

		assertEquals(END_OF_TERM,c.getPlanChangePolicy(
				new PlanPhaseSpecifier("FP", null, MONTHLY, null, EVERGREEN),
				new PlanSpecifier("BP", null, ANNUAL, null)
				));
		
		assertNull(c.getPlanChangePolicy(
				new PlanPhaseSpecifier("FP", null, ANNUAL, null, EVERGREEN),
				new PlanSpecifier("BP", null, MONTHLY, null)
				));

}

	@Test(enabled=true)
	public void testBillingPeriodRulePhaseSpecific() {
		Product P1 = createProduct("FP"); 
		Product P2 = createProduct("BP");

		Catalog c = createCatalog(
				new PlanPolicyChangeRule[]{
						new PlanPolicyChangeRule(Qualifier.TERM_FROM_SHORT_TO_LONG, END_OF_TERM, PhaseType.EVERGREEN)
				}, 
				null,
				null,
				P1,
				P2
				);

		assertEquals(END_OF_TERM,c.getPlanChangePolicy(
				new PlanPhaseSpecifier("FP", null, MONTHLY, null, EVERGREEN),
				new PlanSpecifier("BP", null, ANNUAL, null)
				));
		
		assertNull(c.getPlanChangePolicy(
				new PlanPhaseSpecifier("FP", null, MONTHLY, null, TRIAL),
				new PlanSpecifier("BP", null, ANNUAL, null)
				));
		
	}

	@Test(enabled=true)
	public void testTierChangeRule() {
		Product P1 = createProduct("FP");
		Product P2 = createProduct("BP");

		Catalog c = createCatalog(
				new PlanPolicyChangeRule[]{
						new PlanPolicyChangeRule(Qualifier.PRODUCT_FROM_LOW_TO_HIGH, IMMEDIATE, null)
				}, 
				null,
				null,
				P1,
				P2
				);
		
		assertEquals(IMMEDIATE,c.getPlanChangePolicy(
				new PlanPhaseSpecifier("FP", null, MONTHLY, null, EVERGREEN),
				new PlanSpecifier("BP", null, ANNUAL, null)
				));
		
		assertNull(c.getPlanChangePolicy(
				new PlanPhaseSpecifier("BP", null, MONTHLY, null, EVERGREEN),
				new PlanSpecifier("FP", null, ANNUAL, null)
				));
		

	}
	
	@Test(enabled=true)
	public void testTierChangeRule2() {
		Product P1 = createProduct("FP");
		Product P2 = createProduct("BP");

		Catalog c = createCatalog(
				new PlanPolicyChangeRule[]{
						new PlanPolicyChangeRule(Qualifier.PRODUCT_FROM_HIGH_TO_LOW, IMMEDIATE, null)
				}, 
				null,
				null,
				P1,
				P2
				);

		assertEquals(IMMEDIATE,c.getPlanChangePolicy(
				new PlanPhaseSpecifier("BP", null, ANNUAL, null, EVERGREEN),
				new PlanSpecifier("FP", null, MONTHLY, null)
				));
		
		assertNull(c.getPlanChangePolicy(
				new PlanPhaseSpecifier("FP", null, MONTHLY, null, TRIAL),
				new PlanSpecifier("BP", null, ANNUAL, null)
				));
		
	}

	@Test(enabled=true)
	public void testPrecedenceRuleOrderAndSpecialCaseIgnored() {
		Product P1 = createProduct("FP");
		Product P2 = createProduct("BP");

		Catalog c = createCatalog(
				new PlanPolicyChangeRule[]{
						new PlanPolicyChangeRule(Qualifier.TERM_FROM_LONG_TO_SHORT, IMMEDIATE, null),
						new PlanPolicyChangeRule(Qualifier.PRODUCT_FROM_LOW_TO_HIGH, END_OF_TERM, null)
				}, 
				new CaseChangePlanPolicy[] {
						new CaseChangePlanPolicy(P1, P2, null, null, MONTHLY, MONTHLY, null, null, null, IMMEDIATE  ), 
				},
				null,
				P1,
				P2
				);
		
		assertEquals(END_OF_TERM,c.getPlanChangePolicy(
				new PlanPhaseSpecifier("FP", null, ANNUAL, null, EVERGREEN),
				new PlanSpecifier("BP", null, MONTHLY, null)
				));
		
		assertNull(c.getPlanChangePolicy(
				new PlanPhaseSpecifier("BP", null, MONTHLY, null, EVERGREEN),
				new PlanSpecifier("FP", null, ANNUAL, null)
				));

	}
	@Test(enabled=true)
	public void testPrecedenceSpecialCaseTrumpsRule() {
		Product P1 = createProduct("FP");
		Product P2 = createProduct("BP");

		Catalog c = createCatalog(
				new PlanPolicyChangeRule[]{
						new PlanPolicyChangeRule(Qualifier.PRODUCT_FROM_LOW_TO_HIGH, END_OF_TERM, null)
				}, 
				new CaseChangePlanPolicy[] {
						new CaseChangePlanPolicy(P1, P2, null, null, ANNUAL, MONTHLY, null, null, null, IMMEDIATE ), 
				},
				null,
				P1,
				P2
				);
		
		assertEquals(IMMEDIATE,c.getPlanChangePolicy(
				new PlanPhaseSpecifier("FP", null, ANNUAL, null, EVERGREEN),
				new PlanSpecifier("BP", null, MONTHLY, null)
				));
		
		assertNull(c.getPlanChangePolicy(
				new PlanPhaseSpecifier("BP", null, MONTHLY, null, EVERGREEN),
				new PlanSpecifier("FP", null, ANNUAL, null)
				));

	}
}
