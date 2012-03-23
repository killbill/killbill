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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.UUID;

import javax.xml.bind.annotation.XmlRootElement;

import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.DefaultPriceList;
import com.ning.billing.catalog.DefaultProduct;
import com.ning.billing.catalog.MockPriceList;
import com.ning.billing.catalog.MockProduct;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.overdue.BillingState;
import com.ning.billing.catalog.api.overdue.BillingStateBundle;
import com.ning.billing.catalog.api.overdue.PaymentResponse;
import com.ning.billing.util.config.XMLLoader;
import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.DefaultControlTag;
import com.ning.billing.util.tag.DescriptiveTag;
import com.ning.billing.util.tag.Tag;

public class TestBundleCondition {

	@XmlRootElement(name="conditions")
	private static class MockBundleCondition extends BundleCondition {}

	@Test(groups={"fast"}, enabled=true)
	public void testAccountState() throws Exception {
		String xml = 
				"<conditions>" +
				"	<accountConditions>" +
				"		<controlTag>OVERDUE_ENFORCEMENT_OFF</controlTag>" +
				"	</accountConditions>" +
				"</conditions>";
		InputStream is = new ByteArrayInputStream(xml.getBytes());
		BundleCondition c = XMLLoader.getObjectFromStreamNoValidation(is,  MockBundleCondition.class);

		DateTime now = new DateTime();
		
		BillingState accountState0 = new BillingState(new UUID(0L,1L), 0, BigDecimal.ZERO, now, PaymentResponse.LOST_OR_STOLEN, new Tag[]{new DefaultControlTag("Martin", now, ControlTagType.AUTO_INVOICING_OFF),new DescriptiveTag(null, "Tag", "Martin", now)});
		BillingState accountState1 = new BillingState(new UUID(0L,1L), 1, new BigDecimal("100.00"), now.minusDays(10), PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{new DefaultControlTag("Martin", now, ControlTagType.AUTO_PAY_OFF)});
		BillingState accountState2 = new BillingState(new UUID(0L,1L), 1, new BigDecimal("200.00"), now.minusDays(20), 
				PaymentResponse.TEMPORARY_ACCOUNT_ISSUE, 
				new Tag[]{new DefaultControlTag("Martin", now, ControlTagType.AUTO_PAY_OFF), 
						  new DefaultControlTag("Martin", now, ControlTagType.AUTO_INVOICING_OFF),
						  new DescriptiveTag(null, "Tag", "Martin", now)});
		
		BillingStateBundle state0 = new BillingStateBundle(new UUID(0L,1L), accountState0, 0, BigDecimal.ZERO, new DateTime(), PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{}, MockProduct.createBicycle(), BillingPeriod.MONTHLY, new MockPriceList() );
		BillingStateBundle state1 = new BillingStateBundle(new UUID(0L,1L), accountState1, 0, BigDecimal.ZERO, new DateTime(), PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{}, MockProduct.createBicycle(), BillingPeriod.MONTHLY, new MockPriceList() );
		BillingStateBundle state2 = new BillingStateBundle(new UUID(0L,1L), accountState2, 0, BigDecimal.ZERO, new DateTime(), PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{}, MockProduct.createBicycle(), BillingPeriod.MONTHLY, new MockPriceList() );
		
		Assert.assertTrue(!c.evaluate(state0, new DateTime()));
		Assert.assertTrue(c.evaluate(state1, new DateTime()));
		Assert.assertTrue(c.evaluate(state2, new DateTime()));
	}
	
	@Test(groups={"fast"}, enabled=true)
	public void testProduct() throws Exception {
		DefaultProduct prod = MockProduct.createBicycle();
		BundleCondition c = new BundleCondition().setBasePlanProduct(prod);

		DateTime now = new DateTime();
		
		BillingState accountState0 = new BillingState(new UUID(0L,1L), 0, BigDecimal.ZERO, now, PaymentResponse.LOST_OR_STOLEN, new Tag[]{new DefaultControlTag("Martin", now, ControlTagType.AUTO_INVOICING_OFF),new DescriptiveTag(null, "Tag", "Martin", now)});
		BillingState accountState1 = new BillingState(new UUID(0L,1L), 1, new BigDecimal("100.00"), now.minusDays(10), PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{new DefaultControlTag("Martin", now, ControlTagType.AUTO_PAY_OFF)});
		
		BillingStateBundle state0 = new BillingStateBundle(new UUID(0L,1L), accountState0, 0, BigDecimal.ZERO, new DateTime(), PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{}, MockProduct.createJet(), BillingPeriod.MONTHLY, new MockPriceList() );
		BillingStateBundle state1 = new BillingStateBundle(new UUID(0L,1L), accountState1, 0, BigDecimal.ZERO, new DateTime(), PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{}, prod, BillingPeriod.MONTHLY, new MockPriceList() );

		Assert.assertTrue(!c.evaluate(state0, new DateTime()));
		Assert.assertTrue(c.evaluate(state1, new DateTime()));
	}
	
	
	@Test(groups={"fast"}, enabled=true)
	public void testBillingPeriod() throws Exception {
		BundleCondition c = new BundleCondition().setBasePlanBillingPeriod(BillingPeriod.ANNUAL);

		DateTime now = new DateTime();
		
		BillingState accountState0 = new BillingState(new UUID(0L,1L), 0, BigDecimal.ZERO, now, PaymentResponse.LOST_OR_STOLEN, new Tag[]{new DefaultControlTag("Martin", now, ControlTagType.AUTO_INVOICING_OFF),new DescriptiveTag(null, "Tag", "Martin", now)});
		BillingState accountState1 = new BillingState(new UUID(0L,1L), 1, new BigDecimal("100.00"), now.minusDays(10), PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{new DefaultControlTag("Martin", now, ControlTagType.AUTO_PAY_OFF)});
		
		BillingStateBundle state0 = new BillingStateBundle(new UUID(0L,1L), accountState0, 0, BigDecimal.ZERO, new DateTime(), PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{}, MockProduct.createJet(), BillingPeriod.MONTHLY, new MockPriceList() );
		BillingStateBundle state1 = new BillingStateBundle(new UUID(0L,1L), accountState1, 0, BigDecimal.ZERO, new DateTime(), PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{}, MockProduct.createJet(), BillingPeriod.ANNUAL, new MockPriceList() );

		Assert.assertTrue(!c.evaluate(state0, new DateTime()));
		Assert.assertTrue(c.evaluate(state1, new DateTime()));
	}

	@Test(groups={"fast"}, enabled=true)
	public void testPriceList() throws Exception {
		DefaultPriceList pl = new MockPriceList().setName("test");
		BundleCondition c = new BundleCondition().setBasePlanPriceList(pl);

		DateTime now = new DateTime();
		
		BillingState accountState0 = new BillingState(new UUID(0L,1L), 0, BigDecimal.ZERO, now, PaymentResponse.LOST_OR_STOLEN, new Tag[]{new DefaultControlTag("Martin", now, ControlTagType.AUTO_INVOICING_OFF),new DescriptiveTag(null, "Tag", "Martin", now)});
		BillingState accountState1 = new BillingState(new UUID(0L,1L), 1, new BigDecimal("100.00"), now.minusDays(10), PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{new DefaultControlTag("Martin", now, ControlTagType.AUTO_PAY_OFF)});
		
		BillingStateBundle state0 = new BillingStateBundle(new UUID(0L,1L), accountState0, 0, BigDecimal.ZERO, new DateTime(), PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{}, MockProduct.createJet(), BillingPeriod.MONTHLY, new MockPriceList() );
		BillingStateBundle state1 = new BillingStateBundle(new UUID(0L,1L), accountState1, 0, BigDecimal.ZERO, new DateTime(), PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{},  MockProduct.createJet(), BillingPeriod.MONTHLY, pl );

		Assert.assertTrue(!c.evaluate(state0, new DateTime()));
		Assert.assertTrue(c.evaluate(state1, new DateTime()));
	}

	//MDW TODO: test Pricelist and billing period
	
	

}
