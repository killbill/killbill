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

import com.ning.billing.catalog.api.PlanPhaseSpecifier;

public class TestCancelCase extends TestPlanRules {
	@Test(enabled=true)
	public void testCancelCase() {
		Product P1 = createProduct("FP");
		Product P2 = createProduct("BP");

		Catalog c = createCatalog(
				new PlanChangeRule[]{
				}, 
				new PlanChangeCase[] {
				},
				new PlanCancelCase[] {
						new PlanCancelCase(P1,MONTHLY,EVERGREEN,IMMEDIATE)
				},
				P1,
				P2
				);
		assertEquals(IMMEDIATE,c.getPlanCancelPolicy(
				new PlanPhaseSpecifier("FP", MONTHLY, null, EVERGREEN)
				));

		assertNull(c.getPlanCancelPolicy(
				new PlanPhaseSpecifier("BP", MONTHLY, null, EVERGREEN)
				));
		assertNull(c.getPlanCancelPolicy(
				new PlanPhaseSpecifier("FP", ANNUAL, null, EVERGREEN)
				));
		assertNull(c.getPlanCancelPolicy(
				new PlanPhaseSpecifier("FP", MONTHLY, null, TRIAL)
				));
	}

	@Test(enabled=true)
	public void testCancelCaseWildcard() {
		Product P1 = createProduct("FP");
		Product P2 = createProduct("BP");

		Catalog c = createCatalog(
				new PlanChangeRule[]{
				}, 
				new PlanChangeCase[] {
				},
				new PlanCancelCase[] {
						new PlanCancelCase(P1,null,null,IMMEDIATE)
				},
				P1,
				P2
				);
		assertEquals(IMMEDIATE,c.getPlanCancelPolicy(
				new PlanPhaseSpecifier("FP", MONTHLY, null, EVERGREEN)
				));
		assertEquals(IMMEDIATE,c.getPlanCancelPolicy(
				new PlanPhaseSpecifier("FP", ANNUAL, null, EVERGREEN)
				));
		assertEquals(IMMEDIATE,c.getPlanCancelPolicy(
				new PlanPhaseSpecifier("FP", ANNUAL, null, TRIAL)
				));

		assertNull(c.getPlanCancelPolicy(
				new PlanPhaseSpecifier("BP", MONTHLY, null, EVERGREEN)
				));
	}

	@Test(enabled=true)
	public void testCancelCasePrecedence() {
		Product P1 = createProduct("FP");
		Product P2 = createProduct("BP");

		Catalog c = createCatalog(
				new PlanChangeRule[]{
				}, 
				new PlanChangeCase[] {
				},
				new PlanCancelCase[] {
						new PlanCancelCase(P1,null,null,END_OF_TERM),
						new PlanCancelCase(P1,null,TRIAL,IMMEDIATE)
				},
				P1,
				P2
				);
		assertEquals(END_OF_TERM,c.getPlanCancelPolicy(
				new PlanPhaseSpecifier("FP", MONTHLY, null, EVERGREEN)
				));
		assertEquals(END_OF_TERM,c.getPlanCancelPolicy(
				new PlanPhaseSpecifier("FP", ANNUAL, null, EVERGREEN)
				));
		assertEquals(IMMEDIATE,c.getPlanCancelPolicy(
				new PlanPhaseSpecifier("FP", ANNUAL, null, TRIAL)
				));
	}

}
