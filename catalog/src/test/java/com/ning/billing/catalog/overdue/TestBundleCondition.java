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

package com.ning.billing.catalog.overdue;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.DefaultPriceList;
import com.ning.billing.catalog.DefaultProduct;
import com.ning.billing.catalog.MockPriceList;
import com.ning.billing.catalog.MockProduct;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.overdue.BillingStateBundle;
import com.ning.billing.catalog.api.overdue.PaymentResponse;
import com.ning.billing.util.tag.Tag;

public class TestBundleCondition {

	@Test(groups={"fast"}, enabled=true)
	public void testProduct() throws Exception {
		DefaultProduct prod = MockProduct.createBicycle();
		BundleCondition c = new BundleCondition().setBasePlanProduct(prod);
		
    	BillingStateBundle state0 = new BillingStateBundle(new UUID(0L,1L), 0, BigDecimal.ZERO, new DateTime(), PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{}, MockProduct.createJet(), BillingPeriod.MONTHLY, new MockPriceList(), PhaseType.EVERGREEN);
		BillingStateBundle state1 = new BillingStateBundle(new UUID(0L,1L), 0, BigDecimal.ZERO, new DateTime(), PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{}, prod, BillingPeriod.MONTHLY, new MockPriceList(), PhaseType.EVERGREEN);

		Assert.assertTrue(!c.evaluate(state0, new DateTime()));
		Assert.assertTrue(c.evaluate(state1, new DateTime()));
	}
	
	
	@Test(groups={"fast"}, enabled=true)
	public void testBillingPeriod() throws Exception {
		BundleCondition c = new BundleCondition().setBasePlanBillingPeriod(BillingPeriod.ANNUAL);

		BillingStateBundle state0 = new BillingStateBundle(new UUID(0L,1L), 0, BigDecimal.ZERO, new DateTime(), PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{}, MockProduct.createJet(), BillingPeriod.MONTHLY, new MockPriceList(), PhaseType.EVERGREEN);
		BillingStateBundle state1 = new BillingStateBundle(new UUID(0L,1L), 0, BigDecimal.ZERO, new DateTime(), PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{}, MockProduct.createJet(), BillingPeriod.ANNUAL, new MockPriceList(), PhaseType.EVERGREEN);

		Assert.assertTrue(!c.evaluate(state0, new DateTime()));
		Assert.assertTrue(c.evaluate(state1, new DateTime()));
	}

	@Test(groups={"fast"}, enabled=true)
	public void testPriceList() throws Exception {
		DefaultPriceList pl = new MockPriceList().setName("test");
		BundleCondition c = new BundleCondition().setBasePlanPriceList(pl);

		BillingStateBundle state0 = new BillingStateBundle(new UUID(0L,1L), 0, BigDecimal.ZERO, new DateTime(), PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{}, MockProduct.createJet(), BillingPeriod.MONTHLY, new MockPriceList(), PhaseType.EVERGREEN);
		BillingStateBundle state1 = new BillingStateBundle(new UUID(0L,1L), 0, BigDecimal.ZERO, new DateTime(), PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{},  MockProduct.createJet(), BillingPeriod.MONTHLY, pl, PhaseType.EVERGREEN);

		Assert.assertTrue(!c.evaluate(state0, new DateTime()));
		Assert.assertTrue(c.evaluate(state1, new DateTime()));
	}
	
	

}
