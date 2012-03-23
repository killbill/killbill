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

import com.ning.billing.catalog.api.overdue.BillingState;
import com.ning.billing.catalog.api.overdue.PaymentResponse;
import com.ning.billing.util.config.XMLLoader;
import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.DefaultControlTag;
import com.ning.billing.util.tag.DescriptiveTag;
import com.ning.billing.util.tag.Tag;

public class TestCondition {
	
	@XmlRootElement(name="condition")
	private static class MockCondition extends Condition {}

	@Test(groups={"fast"}, enabled=true)
	public void testNumberOfUnpaidInvoicesEqualsOrExceeds() throws Exception {
		String xml = 
				"<condition>" +
				"	<numberOfUnpaidInvoicesEqualsOrExceeds>1</numberOfUnpaidInvoicesEqualsOrExceeds>" +
				"</condition>";
		InputStream is = new ByteArrayInputStream(xml.getBytes());
		MockCondition c = XMLLoader.getObjectFromStreamNoValidation(is,  MockCondition.class);
		
		BillingState state0 = new BillingState(new UUID(0L,1L), 0, BigDecimal.ZERO, new DateTime(), PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{});
		BillingState state1 = new BillingState(new UUID(0L,1L), 1, BigDecimal.ZERO, new DateTime(), PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{});
		BillingState state2 = new BillingState(new UUID(0L,1L), 2, BigDecimal.ZERO, new DateTime(), PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{});
		
		Assert.assertTrue(!c.evaluate(state0, new DateTime()));
		Assert.assertTrue(c.evaluate(state1, new DateTime()));
		Assert.assertTrue(c.evaluate(state2, new DateTime()));
	}
	
	@Test(groups={"fast"}, enabled=true)
	public void testTotalUnpaidInvoiceBalanceEqualsOrExceeds() throws Exception {
		String xml = 
				"<condition>" +
				"	<totalUnpaidInvoiceBalanceEqualsOrExceeds>100.00</totalUnpaidInvoiceBalanceEqualsOrExceeds>" +
				"</condition>";
		InputStream is = new ByteArrayInputStream(xml.getBytes());
		MockCondition c = XMLLoader.getObjectFromStreamNoValidation(is,  MockCondition.class);
		
		BillingState state0 = new BillingState(new UUID(0L,1L), 0, BigDecimal.ZERO, new DateTime(), PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{});
		BillingState state1 = new BillingState(new UUID(0L,1L), 1, new BigDecimal("100.00"), new DateTime(), PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{});
		BillingState state2 = new BillingState(new UUID(0L,1L), 1, new BigDecimal("200.00"), new DateTime(), PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{});
		
		Assert.assertTrue(!c.evaluate(state0, new DateTime()));
		Assert.assertTrue(c.evaluate(state1, new DateTime()));
		Assert.assertTrue(c.evaluate(state2, new DateTime()));
	}

	
	@Test(groups={"fast"}, enabled=true)
	public void testTimeSinceEarliestUnpaidInvoiceEqualsOrExceeds() throws Exception {
		String xml = 
				"<condition>" +
				"	<timeSinceEarliestUnpaidInvoiceEqualsOrExceeds><unit>DAYS</unit><number>10</number></timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
				"</condition>";
		InputStream is = new ByteArrayInputStream(xml.getBytes());
		MockCondition c = XMLLoader.getObjectFromStreamNoValidation(is,  MockCondition.class);
		
		DateTime now = new DateTime();
		
		BillingState state0 = new BillingState(new UUID(0L,1L), 0, BigDecimal.ZERO, now, PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{});
		BillingState state1 = new BillingState(new UUID(0L,1L), 1, new BigDecimal("100.00"), now.minusDays(10), PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{});
		BillingState state2 = new BillingState(new UUID(0L,1L), 1, new BigDecimal("200.00"), now.minusDays(20), PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{});
		
		Assert.assertTrue(!c.evaluate(state0, now));
		Assert.assertTrue(c.evaluate(state1, now));
		Assert.assertTrue(c.evaluate(state2, now));
	}

	@Test(groups={"fast"}, enabled=true)
	public void testResponseForLastFailedPaymentIn() throws Exception {
		String xml = 
				"<condition>" +
				"	<responseForLastFailedPaymentIn><response>INSUFFICIENT_FUNDS</response><response>TEMPORARY_ACCOUNT_ISSUE</response></responseForLastFailedPaymentIn>" +
				"</condition>";
		InputStream is = new ByteArrayInputStream(xml.getBytes());
		MockCondition c = XMLLoader.getObjectFromStreamNoValidation(is,  MockCondition.class);
		
		DateTime now = new DateTime();
		
		BillingState state0 = new BillingState(new UUID(0L,1L), 0, BigDecimal.ZERO, now, PaymentResponse.LOST_OR_STOLEN, new Tag[]{});
		BillingState state1 = new BillingState(new UUID(0L,1L), 1, new BigDecimal("100.00"), now.minusDays(10), PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{});
		BillingState state2 = new BillingState(new UUID(0L,1L), 1, new BigDecimal("200.00"), now.minusDays(20), PaymentResponse.TEMPORARY_ACCOUNT_ISSUE, new Tag[]{});
		
		Assert.assertTrue(!c.evaluate(state0, now));
		Assert.assertTrue(c.evaluate(state1, now));
		Assert.assertTrue(c.evaluate(state2, now));
	}

	@Test(groups={"fast"}, enabled=true)
	public void testHasControlTag() throws Exception {
		String xml = 
				"<condition>" +
				"	<controlTag>OVERDUE_ENFORCEMENT_OFF</controlTag>" +
				"</condition>";
		InputStream is = new ByteArrayInputStream(xml.getBytes());
		MockCondition c = XMLLoader.getObjectFromStreamNoValidation(is,  MockCondition.class);
		
		DateTime now = new DateTime();
		
		BillingState state0 = new BillingState(new UUID(0L,1L), 0, BigDecimal.ZERO, now, PaymentResponse.LOST_OR_STOLEN, new Tag[]{new DefaultControlTag("Martin", now, ControlTagType.AUTO_INVOICING_OFF),new DescriptiveTag(null, "Tag", "Martin", now)});
		BillingState state1 = new BillingState(new UUID(0L,1L), 1, new BigDecimal("100.00"), now.minusDays(10), PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{new DefaultControlTag("Martin", now, ControlTagType.AUTO_PAY_OFF)});
		BillingState state2 = new BillingState(new UUID(0L,1L), 1, new BigDecimal("200.00"), now.minusDays(20), 
				PaymentResponse.TEMPORARY_ACCOUNT_ISSUE, 
				new Tag[]{new DefaultControlTag("Martin", now, ControlTagType.AUTO_PAY_OFF), 
						  new DefaultControlTag("Martin", now, ControlTagType.AUTO_INVOICING_OFF),
						  new DescriptiveTag(null, "Tag", "Martin", now)});
		
		Assert.assertTrue(!c.evaluate(state0, now));
		Assert.assertTrue(c.evaluate(state1, now));
		Assert.assertTrue(c.evaluate(state2, now));
	}



}
