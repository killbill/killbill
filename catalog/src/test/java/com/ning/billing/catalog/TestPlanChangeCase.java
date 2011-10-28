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

import static com.ning.billing.catalog.api.ActionPolicy.END_OF_TERM;
import static com.ning.billing.catalog.api.ActionPolicy.IMMEDIATE;
import static com.ning.billing.catalog.api.BillingPeriod.ANNUAL;
import static com.ning.billing.catalog.api.BillingPeriod.MONTHLY;
import static com.ning.billing.catalog.api.PhaseType.EVERGREEN;
import static com.ning.billing.catalog.api.PhaseType.TRIAL;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;

import org.testng.annotations.Test;

import com.ning.billing.catalog.PlanChangeRule.Qualifier;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;

public class TestPlanChangeCase extends TestPlanRules {


	@Test(enabled=true)
	public void testSpecialCase() {
		Product P1 = createProduct("FP");
		Product P2 = createProduct("BP");

		Catalog c = createCatalog(
				new PlanChangeRule[]{
				}, 
				new PlanChangeCase[] {
						new PlanChangeCase(P1, P2, ANNUAL, MONTHLY, null, null, null, null, IMMEDIATE ), 
				},
				null,
				P1,
				P2
				);
		assertEquals(IMMEDIATE,c.getPlanChangePolicy(
				new PlanPhaseSpecifier("FP", ANNUAL, null, EVERGREEN),
				new PlanPhaseSpecifier("BP", MONTHLY, null, EVERGREEN)
				));

		assertNull(c.getPlanChangePolicy(
				new PlanPhaseSpecifier("BP", MONTHLY, null, EVERGREEN),
				new PlanPhaseSpecifier("FP", ANNUAL, null, EVERGREEN)
				));
	}
	
	@Test(enabled=true)
	public void testSpecialCaseWildCards() {
		Product P1 = createProduct("FP");
		Product P2 = createProduct("BP");

		Catalog c = createCatalog(
				new PlanChangeRule[]{
				}, 
				new PlanChangeCase[] {
						new PlanChangeCase(null, null, ANNUAL, MONTHLY, null, null, null, null, IMMEDIATE ), 
				},
				null,
				P1,
				P2
				);
		assertEquals(IMMEDIATE,c.getPlanChangePolicy(
				new PlanPhaseSpecifier("FP", ANNUAL, null, EVERGREEN),
				new PlanPhaseSpecifier("BP", MONTHLY, null, EVERGREEN)
				));

		assertEquals(IMMEDIATE,c.getPlanChangePolicy(
				new PlanPhaseSpecifier("BP", ANNUAL, null, EVERGREEN),
				new PlanPhaseSpecifier("FP", MONTHLY, null, EVERGREEN)
				));
		
		assertEquals(IMMEDIATE,c.getPlanChangePolicy(
				new PlanPhaseSpecifier("BP", ANNUAL, null, EVERGREEN),
				new PlanPhaseSpecifier("BP", MONTHLY, null, EVERGREEN)
				));

		assertNull(c.getPlanChangePolicy(
				new PlanPhaseSpecifier("FP", MONTHLY, null, EVERGREEN),
				new PlanPhaseSpecifier("FP", MONTHLY, null, EVERGREEN)
				));
		
		assertNull(c.getPlanChangePolicy(
				new PlanPhaseSpecifier("BP", ANNUAL, null, EVERGREEN),
				new PlanPhaseSpecifier("FP", ANNUAL, null, EVERGREEN)
				));
		
		 c = createCatalog(
					new PlanChangeRule[]{
					}, 
					new PlanChangeCase[] {
							new PlanChangeCase(null, null, null, MONTHLY, null, null, null, null, IMMEDIATE ), 
					},
					null,
					P1,
					P2
					);
			
			assertEquals(IMMEDIATE,c.getPlanChangePolicy(
					new PlanPhaseSpecifier("FP", ANNUAL, null, EVERGREEN),
					new PlanPhaseSpecifier("BP", MONTHLY, null, EVERGREEN)
					));
			assertEquals(IMMEDIATE,c.getPlanChangePolicy(
					new PlanPhaseSpecifier("BP", ANNUAL, null, EVERGREEN),
					new PlanPhaseSpecifier("FP", MONTHLY, null, EVERGREEN)
					));

			assertEquals(IMMEDIATE,c.getPlanChangePolicy(
					new PlanPhaseSpecifier("FP", MONTHLY, null, EVERGREEN),
					new PlanPhaseSpecifier("FP", MONTHLY, null, EVERGREEN)
					));
			
			assertNull(c.getPlanChangePolicy(
					new PlanPhaseSpecifier("FP", ANNUAL, null, EVERGREEN),
					new PlanPhaseSpecifier("FP", ANNUAL, null, EVERGREEN)
					));
			
			assertNull(c.getPlanChangePolicy(
					new PlanPhaseSpecifier("FP", MONTHLY, null, EVERGREEN),
					new PlanPhaseSpecifier("FP", ANNUAL, null, EVERGREEN)
					));
	
			 c = createCatalog(
						new PlanChangeRule[]{
						}, 
						new PlanChangeCase[] {
								new PlanChangeCase(P1, null, null, null, null, null,null, null,  IMMEDIATE ), 
						},
						null,
						P1,
						P2
						);
			 
				assertEquals(IMMEDIATE,c.getPlanChangePolicy(
						new PlanPhaseSpecifier("FP", ANNUAL, null, EVERGREEN),
						new PlanPhaseSpecifier("BP", MONTHLY, null, EVERGREEN)
						));
				
				assertEquals(IMMEDIATE,c.getPlanChangePolicy(
						new PlanPhaseSpecifier("FP", ANNUAL, null, EVERGREEN),
						new PlanPhaseSpecifier("FP", MONTHLY, null, EVERGREEN)
						));

				assertEquals(IMMEDIATE,c.getPlanChangePolicy(
						new PlanPhaseSpecifier("FP", MONTHLY, null, EVERGREEN),
						new PlanPhaseSpecifier("FP", MONTHLY, null, EVERGREEN)
						));
				
				assertNull(c.getPlanChangePolicy(
						new PlanPhaseSpecifier("BP", ANNUAL, null, EVERGREEN),
						new PlanPhaseSpecifier("FP", ANNUAL, null, EVERGREEN)
						));
				
				assertNull(c.getPlanChangePolicy(
						new PlanPhaseSpecifier("BP", MONTHLY, null, EVERGREEN),
						new PlanPhaseSpecifier("FP", ANNUAL, null, EVERGREEN)
						));

	}

	@Test(enabled=true)
	public void testSpecialCasePhaseTypeSpecific() {
		Product P1 = createProduct("FP");
		Product P2 = createProduct("BP");
		PriceList PL1 = createPriceList("PL1");
		PriceList PL2 = createPriceList("PL2");

		Catalog c = createCatalog(
				new PlanChangeRule[]{
				}, 
				new PlanChangeCase[] {
						new PlanChangeCase(P1, P2, ANNUAL, MONTHLY, EVERGREEN, EVERGREEN, PL1, PL2, IMMEDIATE), 
				},
				null,
				P1,
				P2
				);
		c.setPriceLists(new PriceList[]{PL1, PL2});
		
		assertEquals(IMMEDIATE,c.getPlanChangePolicy(
				new PlanPhaseSpecifier("FP", ANNUAL, "PL1", EVERGREEN),
				new PlanPhaseSpecifier("BP", MONTHLY, "PL2", EVERGREEN)
				));
		
		assertNull(c.getPlanChangePolicy(
				new PlanPhaseSpecifier("FP", ANNUAL, "PL1", EVERGREEN),
				new PlanPhaseSpecifier("BP", ANNUAL, "PL2", EVERGREEN)
				));

		
		c = createCatalog(
				new PlanChangeRule[]{
						new PlanChangeRule(Qualifier.DEFAULT, END_OF_TERM, PhaseType.EVERGREEN)
				}, 
				new PlanChangeCase[] {
						new PlanChangeCase(P1, P2, ANNUAL, MONTHLY, EVERGREEN, EVERGREEN, null, null,  IMMEDIATE ), 
				},
				null,
				P1,
				P2
				);
		
		assertEquals(IMMEDIATE,c.getPlanChangePolicy(
				new PlanPhaseSpecifier("FP", ANNUAL, null, EVERGREEN),
				new PlanPhaseSpecifier("BP", MONTHLY, null, EVERGREEN)
				));
		
		assertEquals(END_OF_TERM, c.getPlanChangePolicy(
				new PlanPhaseSpecifier("BP", MONTHLY, null, EVERGREEN),
				new PlanPhaseSpecifier("FP", ANNUAL, null, EVERGREEN)
				));
		assertNull(c.getPlanChangePolicy(
				new PlanPhaseSpecifier("BP", MONTHLY, null, TRIAL),
				new PlanPhaseSpecifier("FP", ANNUAL, null, TRIAL)
				));

		

	}
	
	@Test(enabled=true)
	public void testPrecedenceSpecialCaseTrumpsRule() {
		Product P1 = createProduct("FP");
		Product P2 = createProduct("BP");

		Catalog c = createCatalog(
				new PlanChangeRule[]{
						new PlanChangeRule(Qualifier.PRODUCT_FROM_LOW_TO_HIGH, END_OF_TERM, null)
				}, 
				new PlanChangeCase[] {
						new PlanChangeCase(P1, P2, ANNUAL, MONTHLY, null, null, null, null, IMMEDIATE ), 
				},
				null,
				P1,
				P2
				);
		
		assertEquals(IMMEDIATE,c.getPlanChangePolicy(
				new PlanPhaseSpecifier("FP", ANNUAL, null, EVERGREEN),
				new PlanPhaseSpecifier("BP", MONTHLY, null, EVERGREEN)
				));
		
		assertNull(c.getPlanChangePolicy(
				new PlanPhaseSpecifier("BP", MONTHLY, null, EVERGREEN),
				new PlanPhaseSpecifier("FP", ANNUAL, null, EVERGREEN)
				));

	}
	
}
