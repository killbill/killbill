/*
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.SubscriptionEventType;
import org.killbill.billing.invoice.api.DryRunArguments;
import org.killbill.billing.invoice.api.DryRunType;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBase;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import static com.tc.util.Assert.fail;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestIntegrationDryRunInvoice extends TestIntegrationBase {

    private static final DryRunArguments DRY_RUN_UPCOMING_INVOICE_ARG = new TestDryRunArguments(DryRunType.UPCOMING_INVOICE);
    private static final DryRunArguments DRY_RUN_TARGET_DATE_ARG = new TestDryRunArguments(DryRunType.TARGET_DATE);

    //
    // Basic test with one subscription that verifies the behavior of using invoice dryRun api with no date
    //
    @Test(groups = "slow")
    public void testDryRunWithNoTargetDate() throws Exception {
        final int billingDay = 14;
        final DateTime initialCreationDate = new DateTime(2015, 5, 15, 0, 0, 0, 0, testTimeZone);
        // set clock to the initial start date
        clock.setTime(initialCreationDate);

        log.info("Beginning test with BCD of " + billingDay);
        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(billingDay));

        int invoiceNumber = 1;

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //
        DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        DefaultSubscriptionBase subscription = subscriptionDataFromSubscription(baseEntitlement.getSubscriptionBase());
        invoiceChecker.checkInvoice(account.getId(), invoiceNumber++, callContext, new ExpectedInvoiceItemCheck(initialCreationDate.toLocalDate(), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        // No end date for the trial item (fixed price of zero), and CTD should be today (i.e. when the trial started)
        invoiceChecker.checkChargedThroughDate(subscription.getId(), clock.getUTCToday(), callContext);

        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 6, 14), new LocalDate(2015, 7, 14), InvoiceItemType.RECURRING, new BigDecimal("249.95")));

        // This will verify that the upcoming Phase is found and the invoice is generated at the right date, with correct items
        Invoice dryRunInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), null, DRY_RUN_UPCOMING_INVOICE_ARG, callContext);
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, expectedInvoices);

        // Move through time and verify we get the same invoice
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();
        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        // This will verify that the upcoming invoice notification is found and the invoice is generated at the right date, with correct items
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 7, 14), new LocalDate(2015, 8, 14), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        dryRunInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), null, DRY_RUN_UPCOMING_INVOICE_ARG, callContext);
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, expectedInvoices);

        // Move through time and verify we get the same invoice
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        // One more time, this will verify that the upcoming invoice notification is found and the invoice is generated at the right date, with correct items
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 8, 14), new LocalDate(2015, 9, 14), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        dryRunInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), null, DRY_RUN_UPCOMING_INVOICE_ARG, callContext);
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, expectedInvoices);
    }

    //
    // More sophisticated test with two non aligned subscriptions that verifies the behavior of using invoice dryRun api with no date
    // - The first subscription is an annual (SUBSCRIPTION aligned) whose billingDate is the first (we start on Jan 2nd to take into account the 30 days trial)
    // - The second and third subscriptions are monthly (ACCOUNT aligned) whose billingDate are the 14 (we start on Dec 15 to also take into account the 30 days trial)
    //
    // The test verifies that the dryRun invoice with supplied date will always take into account the 'closest' invoice that should be generated
    //
    @Test(groups = "slow")
    public void testDryRunWithNoTargetDateAndMultipleNonAlignedSubscriptions() throws Exception {
        // Set in such a way that annual billing date will be the 1st
        final DateTime initialCreationDate = new DateTime(2014, 1, 2, 0, 0, 0, 0, testTimeZone);
        clock.setTime(initialCreationDate);

        // billing date for the monthly
        final int billingDay = 14;

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(billingDay));

        int invoiceNumber = 1;

        // Create ANNUAL BP
        DefaultEntitlement baseEntitlementAnnual = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKeyAnnual", "Shotgun", ProductCategory.BASE, BillingPeriod.ANNUAL, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        DefaultSubscriptionBase subscriptionAnnual = subscriptionDataFromSubscription(baseEntitlementAnnual.getSubscriptionBase());
        invoiceChecker.checkInvoice(account.getId(), invoiceNumber++, callContext, new ExpectedInvoiceItemCheck(initialCreationDate.toLocalDate(), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        // No end date for the trial item (fixed price of zero), and CTD should be today (i.e. when the trial started)
        invoiceChecker.checkChargedThroughDate(subscriptionAnnual.getId(), clock.getUTCToday(), callContext);

        // Verify next dryRun invoice and then move the clock to that date to also verify real invoice is the same
        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2014, 2, 1), new LocalDate(2015, 2, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));

        Invoice dryRunInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), null, DRY_RUN_UPCOMING_INVOICE_ARG, callContext);
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, expectedInvoices);

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        // 2014-2-1
        clock.addDays(30);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), invoiceNumber++, callContext, expectedInvoices);
        expectedInvoices.clear();

        // Since we only have one subscription next dryRun will show the annual
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 2, 1), new LocalDate(2016, 2, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));
        dryRunInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), null, DRY_RUN_UPCOMING_INVOICE_ARG, callContext);
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, expectedInvoices);
        expectedInvoices.clear();

        // 2014-12-15
        final DateTime secondSubscriptionCreationDate = new DateTime(2014, 12, 15, 0, 0, 0, 0, testTimeZone);
        clock.setTime(secondSubscriptionCreationDate);

        // Create the first monthly
        DefaultEntitlement baseEntitlementMonthly1 = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey1", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        DefaultSubscriptionBase subscriptionMonthly1 = subscriptionDataFromSubscription(baseEntitlementMonthly1.getSubscriptionBase());
        invoiceChecker.checkInvoice(account.getId(), invoiceNumber++, callContext, new ExpectedInvoiceItemCheck(secondSubscriptionCreationDate.toLocalDate(), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        // No end date for the trial item (fixed price of zero), and CTD should be today (i.e. when the trial started)
        invoiceChecker.checkChargedThroughDate(subscriptionMonthly1.getId(), clock.getUTCToday(), callContext);

        // Create the second monthly
        DefaultEntitlement baseEntitlementMonthly2 = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey2", "Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        DefaultSubscriptionBase subscriptionMonthly2 = subscriptionDataFromSubscription(baseEntitlementMonthly2.getSubscriptionBase());
        invoiceChecker.checkInvoice(account.getId(), invoiceNumber++, callContext, new ExpectedInvoiceItemCheck(secondSubscriptionCreationDate.toLocalDate(), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        // No end date for the trial item (fixed price of zero), and CTD should be today (i.e. when the trial started)
        invoiceChecker.checkChargedThroughDate(subscriptionMonthly2.getId(), clock.getUTCToday(), callContext);

        // Verify next dryRun invoice and then move the clock to that date to also verify real invoice is the same
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 1, 14), new LocalDate(2015, 2, 14), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 1, 14), new LocalDate(2015, 2, 14), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
        dryRunInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), null, DRY_RUN_UPCOMING_INVOICE_ARG, callContext);
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, expectedInvoices);

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.NULL_INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        // 2015-1-14
        clock.addDays(30);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), invoiceNumber++, callContext, expectedInvoices);
        expectedInvoices.clear();

        // We test first the next expected invoice for a specific subscription: We can see the targetDate is 2015-2-14 and not 2015-2-1
        final DryRunArguments dryRunUpcomingInvoiceWithFilterArg1 = new TestDryRunArguments(DryRunType.UPCOMING_INVOICE, null, null, null, null, null, null, subscriptionMonthly1.getId(), null, null, null);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 2, 14), new LocalDate(2015, 3, 14), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 2, 14), new LocalDate(2015, 3, 14), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
        dryRunInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), null, dryRunUpcomingInvoiceWithFilterArg1, callContext);
        assertEquals(dryRunInvoice.getTargetDate(), new LocalDate(2015, 2, 14));
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, expectedInvoices);
        expectedInvoices.clear();

        final DryRunArguments dryRunUpcomingInvoiceWithFilterArg2 = new TestDryRunArguments(DryRunType.UPCOMING_INVOICE, null, null, null, null, null, null, subscriptionMonthly2.getId(), null, null, null);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 2, 14), new LocalDate(2015, 3, 14), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 2, 14), new LocalDate(2015, 3, 14), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
        dryRunInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), null, dryRunUpcomingInvoiceWithFilterArg2, callContext);
        assertEquals(dryRunInvoice.getTargetDate(), new LocalDate(2015, 2, 14));
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, expectedInvoices);
        expectedInvoices.clear();

        // Then we test first the next expected invoice at the account level
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 2, 1), new LocalDate(2016, 2, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));
        dryRunInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), null, DRY_RUN_UPCOMING_INVOICE_ARG, callContext);
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, expectedInvoices);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        // 2015-2-1
        clock.addDays(18);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), invoiceNumber++, callContext, expectedInvoices);
        expectedInvoices.clear();

        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 2, 14), new LocalDate(2015, 3, 14), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 2, 14), new LocalDate(2015, 3, 14), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
        dryRunInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), null, DRY_RUN_UPCOMING_INVOICE_ARG, callContext);
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, expectedInvoices);
    }

    @Test(groups = "slow")
    public void testDryRunWithPendingSubscription() throws Exception {

        final LocalDate initialDate = new LocalDate(2017, 4, 1);
        clock.setDay(initialDate);

        // Create account with non BCD to force junction BCD logic to activate
        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(null));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        final LocalDate futureDate = new LocalDate(2017, 5, 1);

        // No CREATE event as this is set in the future
        final UUID createdEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), spec, account.getExternalKey(), null, futureDate, futureDate, false, true, ImmutableList.<PluginProperty>of(), callContext);
        final Entitlement createdEntitlement = entitlementApi.getEntitlementForId(createdEntitlementId, callContext);
        assertEquals(createdEntitlement.getState(), Entitlement.EntitlementState.PENDING);
        assertEquals(createdEntitlement.getEffectiveStartDate().compareTo(futureDate), 0);
        assertEquals(createdEntitlement.getEffectiveEndDate(), null);
        assertListenerStatus();

        // Generate a dryRun invoice on the billing startDate
        final Invoice dryRunInvoice1 = invoiceUserApi.triggerInvoiceGeneration(createdEntitlement.getAccountId(), futureDate, DRY_RUN_TARGET_DATE_ARG, callContext);
        assertEquals(dryRunInvoice1.getInvoiceItems().size(), 1);
        assertEquals(dryRunInvoice1.getInvoiceItems().get(0).getInvoiceItemType(), InvoiceItemType.FIXED);
        assertEquals(dryRunInvoice1.getInvoiceItems().get(0).getAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(dryRunInvoice1.getInvoiceItems().get(0).getStartDate(), futureDate);
        assertEquals(dryRunInvoice1.getInvoiceItems().get(0).getPlanName(), "shotgun-annual");

        // Generate a dryRun invoice with a plan change
        final DryRunArguments dryRunSubscriptionActionArg = new TestDryRunArguments(DryRunType.SUBSCRIPTION_ACTION, "Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null,
                                                                                    SubscriptionEventType.CHANGE, createdEntitlement.getId(), createdEntitlement.getBundleId(), futureDate, BillingActionPolicy.IMMEDIATE);

        // First one day prior subscription starts
        try {
            invoiceUserApi.triggerInvoiceGeneration(createdEntitlement.getAccountId(), futureDate.minusDays(1), dryRunSubscriptionActionArg, callContext);
            fail("Should fail to trigger dryRun invoice prior subscription starts");
        } catch (final InvoiceApiException e) {
            assertEquals(e.getCode(), ErrorCode.INVOICE_NOTHING_TO_DO.getCode());
        }

        // Second, on the startDate
        final Invoice dryRunInvoice2 = invoiceUserApi.triggerInvoiceGeneration(createdEntitlement.getAccountId(), futureDate, dryRunSubscriptionActionArg, callContext);
        assertEquals(dryRunInvoice2.getInvoiceItems().size(), 1);
        assertEquals(dryRunInvoice2.getInvoiceItems().get(0).getInvoiceItemType(), InvoiceItemType.FIXED);
        assertEquals(dryRunInvoice2.getInvoiceItems().get(0).getAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(dryRunInvoice2.getInvoiceItems().get(0).getStartDate(), futureDate);
        assertEquals(dryRunInvoice2.getInvoiceItems().get(0).getPlanName(), "pistol-monthly");

        // Check BCD is not yet set
        final Account refreshedAccount1 = accountUserApi.getAccountById(account.getId(), callContext);
        assertEquals(refreshedAccount1.getBillCycleDayLocal(), new Integer(0));

        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        final Invoice realInvoice = invoiceUserApi.triggerInvoiceGeneration(createdEntitlement.getAccountId(), futureDate, null, callContext);
        assertListenerStatus();

        assertEquals(realInvoice.getInvoiceItems().size(), 1);
        assertEquals(realInvoice.getInvoiceItems().get(0).getInvoiceItemType(), InvoiceItemType.FIXED);
        assertEquals(realInvoice.getInvoiceItems().get(0).getAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(realInvoice.getInvoiceItems().get(0).getStartDate(), futureDate);
        assertEquals(realInvoice.getInvoiceItems().get(0).getPlanName(), "shotgun-annual");

        // Check BCD is now set
        final Account refreshedAccount2 = accountUserApi.getAccountById(account.getId(), callContext);
        assertEquals(refreshedAccount2.getBillCycleDayLocal(), new Integer(31));

        // Move clock past startDate to check nothing happens
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        clock.addDays(31);
        assertListenerStatus();

        // Move clock after PHASE event
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(12);
        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testDryRunWithPendingCancelledSubscription() throws Exception {

        final LocalDate initialDate = new LocalDate(2017, 4, 1);
        clock.setDay(initialDate);

        // Create account with non BCD to force junction BCD logic to activate
        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(null));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("pistol-monthly-notrial", null);

        final LocalDate futureStartDate = new LocalDate(2017, 5, 1);

        // No CREATE event as this is set in the future
        final UUID createdEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), spec, account.getExternalKey(), null, futureStartDate, futureStartDate, false, true, ImmutableList.<PluginProperty>of(), callContext);
        final Entitlement createdEntitlement = entitlementApi.getEntitlementForId(createdEntitlementId, callContext);
        assertEquals(createdEntitlement.getState(), Entitlement.EntitlementState.PENDING);
        assertEquals(createdEntitlement.getEffectiveStartDate().compareTo(futureStartDate), 0);
        assertEquals(createdEntitlement.getEffectiveEndDate(), null);
        assertListenerStatus();

        // Generate an invoice using a future targetDate
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final Invoice firstInvoice = invoiceUserApi.triggerInvoiceGeneration(createdEntitlement.getAccountId(), futureStartDate, null, callContext);
        assertListenerStatus();

        assertEquals(firstInvoice.getInvoiceItems().size(), 1);
        assertEquals(firstInvoice.getInvoiceItems().get(0).getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(firstInvoice.getInvoiceItems().get(0).getAmount().compareTo(new BigDecimal("19.95")), 0);
        assertEquals(firstInvoice.getInvoiceItems().get(0).getStartDate(), futureStartDate);
        assertEquals(firstInvoice.getInvoiceItems().get(0).getPlanName(), "pistol-monthly-notrial");

        // Cancel subscription on its pending startDate
        createdEntitlement.cancelEntitlementWithDate(futureStartDate, true, ImmutableList.<PluginProperty>of(), callContext);

        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2017, 5, 1), new LocalDate(2017, 6, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-19.95")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2017, 4, 1), new LocalDate(2017, 4, 1), InvoiceItemType.CBA_ADJ, new BigDecimal("19.95")));

        final Invoice dryRunInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), null, DRY_RUN_UPCOMING_INVOICE_ARG, callContext);
        assertEquals(dryRunInvoice.getTargetDate(), new LocalDate(2017, 5, 1));
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, expectedInvoices);
        expectedInvoices.clear();

        // Move to startDate/cancel Date
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.BLOCK, NextEvent.NULL_INVOICE, NextEvent.INVOICE);
        clock.addMonths(1);
        assertListenerStatus();

        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 2);

        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2017, 5, 1), new LocalDate(2017, 6, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-19.95")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2017, 5, 1), new LocalDate(2017, 5, 1), InvoiceItemType.CBA_ADJ, new BigDecimal("19.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, expectedInvoices);
    }


    @Test(groups = "slow", description = "See https://github.com/killbill/killbill/issues/774")
    public void testDryRunTargetDateWithIntermediateInvoice() throws Exception {
        final DateTime initialCreationDate = new DateTime(2014, 1, 2, 0, 0, 0, 0, testTimeZone);
        clock.setTime(initialCreationDate);

        // billing date for the monthly
        final int billingDay = 14;

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(billingDay));

        // Create first ANNUAL BP -> BCD = 1
        createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKeyAnnual1", "Shotgun", ProductCategory.BASE, BillingPeriod.ANNUAL, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        // 2014-1-4
        clock.addDays(2);
        // Create second ANNUAL BP -> BCD = 3
        createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKeyAnnual2", "Pistol", ProductCategory.BASE, BillingPeriod.ANNUAL, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        // 2014-2-1
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(28);
        assertListenerStatus();

        // 2014-2-3
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(2);
        assertListenerStatus();

        // 2014-12-15
        final DateTime monthlySubscriptionCreationDate = new DateTime(2014, 12, 15, 0, 0, 0, 0, testTimeZone);
        clock.setTime(monthlySubscriptionCreationDate);

        // Create the monthly
        createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        // 2015-1-14
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();

        // At this point (2015-1-14), we have 3 pending invoice notifications:
        //
        // - The 1st ANNUAL on 2015-2-1
        // - The 2nd ANNUAL on 2015-2-3
        // - The MONTHLY on 2015-2-14
        //
        // 1. We verify that a DryRunType.TARGET_DATE for 2015-2-14 leads to an invoice that **only** contains the MONTHLY item (fix for #774)
        //
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 2, 14), new LocalDate(2015, 3, 14), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        Invoice dryRunInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), new LocalDate(2015, 2, 14), DRY_RUN_TARGET_DATE_ARG, callContext);
        assertEquals(dryRunInvoice.getTargetDate(), new LocalDate(2015, 2, 14));
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, expectedInvoices);
        expectedInvoices.clear();

        // 2. We verify that a DryRunType.TARGET_DATE for 2015-2-3 leads to an invoice that **only** contains the 2nd ANNUAL item (fix for #774)
        //
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 2, 3), new LocalDate(2016, 2, 3), InvoiceItemType.RECURRING, new BigDecimal("199.95")));
        dryRunInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), new LocalDate(2015, 2, 3), DRY_RUN_TARGET_DATE_ARG, callContext);
        assertEquals(dryRunInvoice.getTargetDate(), new LocalDate(2015, 2, 3));
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, expectedInvoices);
        expectedInvoices.clear();

        // 3. We verify that UPCOMING_INVOICE leads to next invoice fo the ANNUAL
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 2, 1), new LocalDate(2016, 2, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));
        dryRunInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), null, DRY_RUN_UPCOMING_INVOICE_ARG, callContext);
        assertEquals(dryRunInvoice.getTargetDate(), new LocalDate(2015, 2, 1));
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, expectedInvoices);
        expectedInvoices.clear();

    }

    @Test(groups = "slow")
    public void testDryRunWithAOs() throws Exception {
        final LocalDate initialDate = new LocalDate(2017, 12, 1);
        clock.setDay(initialDate);

        // Create account with non BCD to force junction BCD logic to activate
        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(null));

        // No CREATE event as this is set in the future
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("pistol-monthly-notrial", null);
        final UUID baseEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), spec, account.getExternalKey(), null, null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement baseEntitlement = entitlementApi.getEntitlementForId(baseEntitlementId, callContext);

        final DefaultEntitlement aoEntitlement = addAOEntitlementAndCheckForCompletion(baseEntitlement.getBundleId(), "Refurbish-Maintenance", ProductCategory.ADD_ON, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2018, 2, 1), new LocalDate(2018, 3, 1), InvoiceItemType.RECURRING, new BigDecimal("19.95")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2018, 2, 1), new LocalDate(2018, 3, 1), InvoiceItemType.RECURRING, new BigDecimal("199.95")));

        // Specify AO subscriptionId filter
        final DryRunArguments dryRunUpcomingInvoiceWithFilterArg1 = new TestDryRunArguments(DryRunType.UPCOMING_INVOICE, null, null, null, null, null, null, aoEntitlement.getId(), null, null, null);
        Invoice dryRunInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), null, dryRunUpcomingInvoiceWithFilterArg1, callContext);
        assertEquals(dryRunInvoice.getTargetDate(), new LocalDate(2018, 2, 1));
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, expectedInvoices);

        // Specify BP subscriptionId filter
        final DryRunArguments dryRunUpcomingInvoiceWithFilterArg2 = new TestDryRunArguments(DryRunType.UPCOMING_INVOICE, null, null, null, null, null, null, baseEntitlement.getId(), null, null, null);
        dryRunInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), null, dryRunUpcomingInvoiceWithFilterArg2, callContext);
        assertEquals(dryRunInvoice.getTargetDate(), new LocalDate(2018, 2, 1));
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, expectedInvoices);

        // Specify bundleId filter
        final DryRunArguments dryRunUpcomingInvoiceWithFilterArg3 = new TestDryRunArguments(DryRunType.UPCOMING_INVOICE, null, null, null, null, null, null, null, baseEntitlement.getBundleId(), null, null);
        dryRunInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), null, dryRunUpcomingInvoiceWithFilterArg3, callContext);
        assertEquals(dryRunInvoice.getTargetDate(), new LocalDate(2018, 2, 1));
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, expectedInvoices);

    }

    @Test(groups = "slow")
    public void testDryRunWithUpcomingSubscriptionEvents() throws Exception {

        final DateTime initialDate = new DateTime(2017, 11, 1, 0, 3, 42, 0, testTimeZone);

        // set clock to the initial start date
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;

        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(baseEntitlement);

        // Verify the next invoice based on the PHASE event is correctly seen in the dryRun scenario
        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2017, 12, 1), new LocalDate(2018, 1, 1), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        Invoice dryRunInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), null, DRY_RUN_UPCOMING_INVOICE_ARG, callContext);
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, expectedInvoices);
        expectedInvoices.clear();

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2017, 12, 1), new LocalDate(2018, 1, 1), InvoiceItemType.RECURRING, new BigDecimal("249.95")));

        // Future pause the subscription
        LocalDate effectivePauseDate = new LocalDate(2017, 12, 15);
        entitlementApi.pause(baseEntitlement.getBundleId(), effectivePauseDate, ImmutableList.<PluginProperty>of(), callContext);

        // Verify the next invoice based on the PAUSE event is correctly seen in the dryRun scenario
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2017, 12, 15), new LocalDate(2018, 1, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-137.07")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2017, 12, 1), new LocalDate(2017, 12, 1), InvoiceItemType.CBA_ADJ, new BigDecimal("137.07")));
        dryRunInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), null, DRY_RUN_UPCOMING_INVOICE_ARG, callContext);
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, expectedInvoices);
        expectedInvoices.clear();

        // Hit the pause effective date 2017-12-15)
        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.INVOICE);
        clock.addDays(14);
        assertListenerStatus();

        // Unfortunately we can't reuse *exactly the items from the dryRun invoice because the effective date for the CBA is set with current date.
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2017, 12, 15), new LocalDate(2018, 1, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-137.07")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2017, 12, 15), new LocalDate(2017, 12, 15), InvoiceItemType.CBA_ADJ, new BigDecimal("137.07")));
        invoiceChecker.checkInvoice(account.getId(), 3, callContext, expectedInvoices);
        expectedInvoices.clear();

        // Future resume the subscription
        LocalDate effectiveResumeDate = new LocalDate(2017, 12, 25);
        entitlementApi.resume(baseEntitlement.getBundleId(), effectiveResumeDate, ImmutableList.<PluginProperty>of(), callContext);

        // Verify the next invoice based on the RESUME event is correctly seen in the dryRun scenario
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2017, 12, 25), new LocalDate(2018, 1, 1), InvoiceItemType.RECURRING, new BigDecimal("56.44")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2017, 12, 15), new LocalDate(2017, 12, 15), InvoiceItemType.CBA_ADJ, new BigDecimal("-56.44")));
        dryRunInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), null, DRY_RUN_UPCOMING_INVOICE_ARG, callContext);
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, expectedInvoices);
        expectedInvoices.clear();

        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.INVOICE);
        clock.addDays(10);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2017, 12, 25), new LocalDate(2018, 1, 1), InvoiceItemType.RECURRING, new BigDecimal("56.44")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2017, 12, 25), new LocalDate(2017, 12, 25), InvoiceItemType.CBA_ADJ, new BigDecimal("-56.44")));

    }

}
