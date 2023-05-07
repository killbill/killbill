/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.beatrix.integration;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.invoice.api.DryRunType;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.testng.annotations.Test;

public class TestPhaseTransitionAccountPark extends TestIntegrationBase {

	@Override
	protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
		final Map<String, String> allExtraProperties = new HashMap<>(extraProperties);
		allExtraProperties.put("org.killbill.catalog.uri", "catalogs/testPhaseTransitionAccountPark");
		return super.getConfigSource(null, allExtraProperties);
	}

	@Test(groups = "slow")
	public void testAccountSubscriptionInvoiceCreation() throws Exception {

		final LocalDate today = new LocalDate(2023, 1, 30);
		// Set clock to the initial start date - we implicitly assume here that the
		// account timezone is UTC
		clock.setDeltaFromReality(
				today.toDateTimeAtCurrentTime(DateTimeZone.UTC).getMillis() - clock.getUTCNow().getMillis());

		System.out.println("Creating account now");

		final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(null));

		System.out.println("Account successfully created");

		final String productName = "Standard";

		//
		// CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK
		// NextEvent.INVOICE
		//

		final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(),
				"externalKey", productName, ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE,
				NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

		assertNotNull(bpEntitlement);

		List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
		assertEquals(invoices.size(), 1);

		Invoice checkInvoice = invoices.get(0);

		assertEquals(checkInvoice.getInvoiceItems().get(0).getInvoiceItemType(), InvoiceItemType.RECURRING);
		assertEquals(checkInvoice.getInvoiceItems().get(0).getAmount().compareTo(BigDecimal.valueOf(3.99)), 0);

		assertEquals(bpEntitlement.getSubscriptionBase().getCurrentPlan().getRecurringBillingPeriod(),
				BillingPeriod.MONTHLY);

		// Move to 28th Feb
		busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
		clock.addDays(29);
		assertListenerStatus();

		invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
		assertEquals(invoices.size(), 2);

		checkInvoice = invoices.get(1);

		assertEquals(checkInvoice.getInvoiceItems().get(0).getInvoiceItemType(), InvoiceItemType.RECURRING);
		assertEquals(checkInvoice.getInvoiceItems().get(0).getAmount().compareTo(BigDecimal.valueOf(3.99)), 0);

		// Move to 30th March
		busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
		clock.addDays(30);
		assertListenerStatus();

		invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);

		assertEquals(invoices.size(), 3);
		checkInvoice = invoices.get(2);

		assertEquals(checkInvoice.getInvoiceItems().get(0).getInvoiceItemType(), InvoiceItemType.RECURRING);
		assertEquals(checkInvoice.getInvoiceItems().get(0).getAmount().compareTo(BigDecimal.valueOf(3.99)), 0);

		// Move to 30th April, move to Evergreen phase and 8.99 price
		busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.NULL_INVOICE, NextEvent.PAYMENT,
				NextEvent.INVOICE_PAYMENT);
		clock.addDays(31);
		assertListenerStatus();

		invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);

		assertEquals(invoices.size(), 4);
		checkInvoice = invoices.get(3);

		assertEquals(checkInvoice.getInvoiceItems().get(0).getInvoiceItemType(), InvoiceItemType.RECURRING);
		assertEquals(checkInvoice.getInvoiceItems().get(0).getAmount().compareTo(BigDecimal.valueOf(8.99)), 0);

		final TestDryRunArguments dryRun = new TestDryRunArguments(DryRunType.UPCOMING_INVOICE, productName,
				ProductCategory.BASE, BillingPeriod.MONTHLY, null, null, null, bpEntitlement.getId(),
				bpEntitlement.getBundleId(), null, null);
		final Invoice dryRunInvoice = invoiceUserApi.triggerDryRunInvoiceGeneration(account.getId(),
				clock.getUTCToday(), dryRun, Collections.emptyList(), callContext);

		assertNotNull(dryRunInvoice);

	}

}
