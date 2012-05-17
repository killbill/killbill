/*
00 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.overdue.config;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.UUID;

import javax.xml.bind.annotation.XmlRootElement;

import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.junction.api.Blockable;
import com.ning.billing.overdue.config.api.BillingState;
import com.ning.billing.overdue.config.api.PaymentResponse;
import com.ning.billing.util.config.XMLLoader;
import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.DefaultControlTag;
import com.ning.billing.util.tag.DescriptiveTag;
import com.ning.billing.util.tag.Tag;

public class TestCondition {
	
	@XmlRootElement(name="condition")
	private static class MockCondition extends DefaultCondition<Blockable> {}

	@Test(groups={"fast"}, enabled=true)
	public void testNumberOfUnpaidInvoicesEqualsOrExceeds() throws Exception {
		String xml = 
				"<condition>" +
				"	<numberOfUnpaidInvoicesEqualsOrExceeds>1</numberOfUnpaidInvoicesEqualsOrExceeds>" +
				"</condition>";
		InputStream is = new ByteArrayInputStream(xml.getBytes());
		MockCondition c = XMLLoader.getObjectFromStreamNoValidation(is,  MockCondition.class);
		UUID unpaidInvoiceId = UUID.randomUUID();		        
		
		BillingState<Blockable> state0 = new BillingState<Blockable>(new UUID(0L,1L), 0, BigDecimal.ZERO, new DateTime(), unpaidInvoiceId, PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{});
		BillingState<Blockable> state1 = new BillingState<Blockable>(new UUID(0L,1L), 1, BigDecimal.ZERO, new DateTime(), unpaidInvoiceId, PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{});
		BillingState<Blockable> state2 = new BillingState<Blockable>(new UUID(0L,1L), 2, BigDecimal.ZERO, new DateTime(), unpaidInvoiceId, PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{});
		
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
        UUID unpaidInvoiceId = UUID.randomUUID();               
		
		BillingState<Blockable> state0 = new BillingState<Blockable>(new UUID(0L,1L), 0, BigDecimal.ZERO, new DateTime(), unpaidInvoiceId, PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{});
		BillingState<Blockable> state1 = new BillingState<Blockable>(new UUID(0L,1L), 1, new BigDecimal("100.00"), new DateTime(), unpaidInvoiceId, PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{});
		BillingState<Blockable> state2 = new BillingState<Blockable>(new UUID(0L,1L), 1, new BigDecimal("200.00"), new DateTime(), unpaidInvoiceId, PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{});
		
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
        UUID unpaidInvoiceId = UUID.randomUUID();               
		
		DateTime now = new DateTime();
		
		BillingState<Blockable> state0 = new BillingState<Blockable>(new UUID(0L,1L), 0, BigDecimal.ZERO, null, unpaidInvoiceId, PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{});
		BillingState<Blockable> state1 = new BillingState<Blockable>(new UUID(0L,1L), 1, new BigDecimal("100.00"), now.minusDays(10), unpaidInvoiceId, PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{});
		BillingState<Blockable> state2 = new BillingState<Blockable>(new UUID(0L,1L), 1, new BigDecimal("200.00"), now.minusDays(20), unpaidInvoiceId, PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{});
		
		Assert.assertTrue(!c.evaluate(state0, now));
		Assert.assertTrue(c.evaluate(state1, now));
		Assert.assertTrue(c.evaluate(state2, now));
	}

	@Test(groups={"fast"}, enabled=true)
	public void testResponseForLastFailedPaymentIn() throws Exception {
		String xml = 
				"<condition>" +
				"	<responseForLastFailedPaymentIn><response>INSUFFICIENT_FUNDS</response><response>DO_NOT_HONOR</response></responseForLastFailedPaymentIn>" +
				"</condition>";
		InputStream is = new ByteArrayInputStream(xml.getBytes());
		MockCondition c = XMLLoader.getObjectFromStreamNoValidation(is,  MockCondition.class);
        UUID unpaidInvoiceId = UUID.randomUUID();               
		
		DateTime now = new DateTime();
		
		BillingState<Blockable> state0 = new BillingState<Blockable>(new UUID(0L,1L), 0, BigDecimal.ZERO, null, unpaidInvoiceId, PaymentResponse.LOST_OR_STOLEN_CARD, new Tag[]{});
		BillingState<Blockable> state1 = new BillingState<Blockable>(new UUID(0L,1L), 1, new BigDecimal("100.00"), now.minusDays(10), unpaidInvoiceId, PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{});
		BillingState<Blockable> state2 = new BillingState<Blockable>(new UUID(0L,1L), 1, new BigDecimal("200.00"), now.minusDays(20), unpaidInvoiceId, PaymentResponse.DO_NOT_HONOR , new Tag[]{});
		
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
        UUID unpaidInvoiceId = UUID.randomUUID();               
		
		DateTime now = new DateTime();
		
		BillingState<Blockable> state0 = new BillingState<Blockable>(new UUID(0L,1L), 0, BigDecimal.ZERO, null, unpaidInvoiceId, PaymentResponse.LOST_OR_STOLEN_CARD, new Tag[]{new DefaultControlTag(ControlTagType.AUTO_INVOICING_OFF),new DescriptiveTag("Tag")});
		BillingState<Blockable> state1 = new BillingState<Blockable>(new UUID(0L,1L), 1, new BigDecimal("100.00"), now.minusDays(10), unpaidInvoiceId, PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{new DefaultControlTag(ControlTagType.OVERDUE_ENFORCEMENT_OFF)});
		BillingState<Blockable> state2 = new BillingState<Blockable>(new UUID(0L,1L), 1, new BigDecimal("200.00"), now.minusDays(20), unpaidInvoiceId, 
				PaymentResponse.DO_NOT_HONOR, 
				new Tag[]{new DefaultControlTag(ControlTagType.OVERDUE_ENFORCEMENT_OFF), 
						  new DefaultControlTag(ControlTagType.AUTO_INVOICING_OFF),
						  new DescriptiveTag("Tag")});
		
		Assert.assertTrue(!c.evaluate(state0, now));
		Assert.assertTrue(c.evaluate(state1, now));
		Assert.assertTrue(c.evaluate(state2, now));
	}



}
