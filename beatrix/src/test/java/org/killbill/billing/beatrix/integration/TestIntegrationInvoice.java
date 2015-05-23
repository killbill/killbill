/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.invoice.api.DryRunArguments;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBase;
import org.testng.annotations.Test;

public class TestIntegrationInvoice extends TestIntegrationBase {

    //
    // Basic test with one subscription that verifies the behavior of using invoice dryRun api with no date
    //
    @Test(groups = "slow")
    public void testDryRunWithNoTargetDate() throws Exception {

        final int billingDay = 14;
        final DateTime initialCreationDate = new DateTime(2015, 5, 15, 0, 0, 0, 0, testTimeZone);

        log.info("Beginning test with BCD of " + billingDay);
        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(billingDay));

        // set clock to the initial start date
        clock.setTime(initialCreationDate);
        int invoiceItemCount = 1;

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE NextEvent.INVOICE
        //
        DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.INVOICE);
        DefaultSubscriptionBase subscription = subscriptionDataFromSubscription(baseEntitlement.getSubscriptionBase());
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, callContext, new ExpectedInvoiceItemCheck(initialCreationDate.toLocalDate(), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        // No end date for the trial item (fixed price of zero), and CTD should be today (i.e. when the trial started)
        invoiceChecker.checkChargedThroughDate(subscription.getId(), clock.getUTCToday(), callContext);

        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 6, 14), new LocalDate(2015, 7, 14), InvoiceItemType.RECURRING, new BigDecimal("249.95")));

        // This will verify that the upcoming Phase is found and the invoice is generated at the right date, with correct items
        DryRunArguments dryRun = new TestDryRunArguments();
        Invoice dryRunInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), null, dryRun, callContext);
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, callContext, expectedInvoices);

        // Move through time and verify we get the same invoice
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT);
        clock.addDays(30);
        assertListenerStatus();
        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), callContext);
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        // This will verify that the upcoming invoice notification is found and the invoice is generated at the right date, with correct items
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 7, 14), new LocalDate(2015, 8, 14), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        dryRunInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), null, dryRun, callContext);
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, callContext, expectedInvoices);


        // Move through time and verify we get the same invoice
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), callContext);
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        // One more time, this will verify that the upcoming invoice notification is found and the invoice is generated at the right date, with correct items
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 8, 14), new LocalDate(2015, 9, 14), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        dryRunInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), null, dryRun, callContext);
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, callContext, expectedInvoices);
    }


    //
    // More sophisticated test with two non aligned subscriptions that verifies the behavior of using invoice dryRun api with no date
    // - The first subscription is an annual (SUBSCRIPTION aligned) whose billingDate is the first (we start on Jan 2nd to take into account the 30 days trial)
    // - The second subscription is a monthly (ACCOUNT aligned) whose billingDate is the 14 (we start on Dec 15 to also take into account the 30 days trial)
    //
    // The test verifies that the dryRun invoice with supplied date will always take into account the 'closest' invoice that should be generated
    //
    @Test(groups = "slow")
    public void testDryRunWithNoTargetDateAndMultipleNonAlignedSubscriptions() throws Exception {

        // billing date for the monthly
        final int billingDay = 14;

        // Set in such a way that annual billing date will be the 1st
        final DateTime initialCreationDate = new DateTime(2014, 1, 2, 0, 0, 0, 0, testTimeZone);
        clock.setTime(initialCreationDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(billingDay));

        int invoiceItemCount = 1;

        // Create ANNUAL BP
        DefaultEntitlement baseEntitlementAnnual = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKeyAnnual", "Shotgun", ProductCategory.BASE, BillingPeriod.ANNUAL, NextEvent.CREATE, NextEvent.INVOICE);
        DefaultSubscriptionBase subscriptionAnnual = subscriptionDataFromSubscription(baseEntitlementAnnual.getSubscriptionBase());
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, callContext, new ExpectedInvoiceItemCheck(initialCreationDate.toLocalDate(), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        // No end date for the trial item (fixed price of zero), and CTD should be today (i.e. when the trial started)
        invoiceChecker.checkChargedThroughDate(subscriptionAnnual.getId(), clock.getUTCToday(), callContext);


        // Verify next dryRun invoice and then move the clock to that date to also verify real invoice is the same
        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2014, 2, 1), new LocalDate(2015, 2, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));

        DryRunArguments dryRun = new TestDryRunArguments();
        Invoice dryRunInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), null, dryRun, callContext);
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, callContext, expectedInvoices);

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT);
        // 2014-1-2
        clock.addDays(30);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, callContext, expectedInvoices);
        expectedInvoices.clear();

        // Since we only have one subscription next dryRun will show the annual
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 2, 1), new LocalDate(2016, 2, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));
        dryRunInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), null, dryRun, callContext);
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, callContext, expectedInvoices);
        expectedInvoices.clear();

        // 2014-12-15
        final DateTime secondSubscriptionCreationDate = new DateTime(2014, 12, 15, 0, 0, 0, 0, testTimeZone);
        clock.setTime(secondSubscriptionCreationDate);

        // Create the monthly
        DefaultEntitlement baseEntitlementMonthly = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.INVOICE);
        DefaultSubscriptionBase subscriptionMonthly = subscriptionDataFromSubscription(baseEntitlementMonthly.getSubscriptionBase());
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, callContext, new ExpectedInvoiceItemCheck(secondSubscriptionCreationDate.toLocalDate(), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        // No end date for the trial item (fixed price of zero), and CTD should be today (i.e. when the trial started)
        invoiceChecker.checkChargedThroughDate(subscriptionMonthly.getId(), clock.getUTCToday(), callContext);

        // Verify next dryRun invoice and then move the clock to that date to also verify real invoice is the same
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 1, 14), new LocalDate(2015, 2, 14), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        dryRunInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), null, dryRun, callContext);
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, callContext, expectedInvoices);

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT);
        // 2015-1-14
        clock.addDays(30);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, callContext, expectedInvoices);
        expectedInvoices.clear();

        //
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 2, 1), new LocalDate(2016, 2, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));
        dryRunInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), null, dryRun, callContext);
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, callContext, expectedInvoices);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT);
        // 2015-2-1
        clock.addDays(18);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, callContext, expectedInvoices);
        expectedInvoices.clear();

        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 2, 14), new LocalDate(2015, 3, 14), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        dryRunInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), null, dryRun, callContext);
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, callContext, expectedInvoices);


    }

}
