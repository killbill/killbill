/*
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.DefaultPlanPhasePriceOverride;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
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
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.model.ExternalChargeInvoiceItem;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBase;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import static com.tc.util.Assert.fail;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestIntegrationInvoice extends TestIntegrationBase {

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

        int invoiceItemCount = 1;

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //
        DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        DefaultSubscriptionBase subscription = subscriptionDataFromSubscription(baseEntitlement.getSubscriptionBase());
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, callContext, new ExpectedInvoiceItemCheck(initialCreationDate.toLocalDate(), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        // No end date for the trial item (fixed price of zero), and CTD should be today (i.e. when the trial started)
        invoiceChecker.checkChargedThroughDate(subscription.getId(), clock.getUTCToday(), callContext);

        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 6, 14), new LocalDate(2015, 7, 14), InvoiceItemType.RECURRING, new BigDecimal("249.95")));

        // This will verify that the upcoming Phase is found and the invoice is generated at the right date, with correct items
        DryRunArguments dryRun = new TestDryRunArguments(DryRunType.UPCOMING_INVOICE);
        Invoice dryRunInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), null, dryRun, callContext);
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, callContext, expectedInvoices);

        // Move through time and verify we get the same invoice
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();
        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        // This will verify that the upcoming invoice notification is found and the invoice is generated at the right date, with correct items
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 7, 14), new LocalDate(2015, 8, 14), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        dryRunInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), null, dryRun, callContext);
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, callContext, expectedInvoices);


        // Move through time and verify we get the same invoice
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
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
        // Set in such a way that annual billing date will be the 1st
        final DateTime initialCreationDate = new DateTime(2014, 1, 2, 0, 0, 0, 0, testTimeZone);
        clock.setTime(initialCreationDate);

        // billing date for the monthly
        final int billingDay = 14;

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(billingDay));

        int invoiceItemCount = 1;

        // Create ANNUAL BP
        DefaultEntitlement baseEntitlementAnnual = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKeyAnnual", "Shotgun", ProductCategory.BASE, BillingPeriod.ANNUAL, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        DefaultSubscriptionBase subscriptionAnnual = subscriptionDataFromSubscription(baseEntitlementAnnual.getSubscriptionBase());
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, callContext, new ExpectedInvoiceItemCheck(initialCreationDate.toLocalDate(), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        // No end date for the trial item (fixed price of zero), and CTD should be today (i.e. when the trial started)
        invoiceChecker.checkChargedThroughDate(subscriptionAnnual.getId(), clock.getUTCToday(), callContext);


        // Verify next dryRun invoice and then move the clock to that date to also verify real invoice is the same
        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2014, 2, 1), new LocalDate(2015, 2, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));

        DryRunArguments dryRun = new TestDryRunArguments(DryRunType.UPCOMING_INVOICE);
        Invoice dryRunInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), null, dryRun, callContext);
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, callContext, expectedInvoices);

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        // 2014-2-1
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
        DefaultEntitlement baseEntitlementMonthly = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        DefaultSubscriptionBase subscriptionMonthly = subscriptionDataFromSubscription(baseEntitlementMonthly.getSubscriptionBase());
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, callContext, new ExpectedInvoiceItemCheck(secondSubscriptionCreationDate.toLocalDate(), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        // No end date for the trial item (fixed price of zero), and CTD should be today (i.e. when the trial started)
        invoiceChecker.checkChargedThroughDate(subscriptionMonthly.getId(), clock.getUTCToday(), callContext);

        // Verify next dryRun invoice and then move the clock to that date to also verify real invoice is the same
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 1, 14), new LocalDate(2015, 2, 14), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        dryRunInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), null, dryRun, callContext);
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, callContext, expectedInvoices);

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        // 2015-1-14
        clock.addDays(30);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, callContext, expectedInvoices);
        expectedInvoices.clear();


        // We test first the next expected invoice for a specific subscription: We can see the targetDate is 2015-2-14 and not 2015-2-1
        final DryRunArguments dryRunWIthSubscription = new TestDryRunArguments(DryRunType.UPCOMING_INVOICE, null, null, null, null, null, null, subscriptionMonthly.getId(), null, null, null);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 2, 14), new LocalDate(2015, 3, 14), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        dryRunInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), null, dryRunWIthSubscription, callContext);
        assertEquals(dryRunInvoice.getTargetDate(), new LocalDate(2015, 2, 14));
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, callContext, expectedInvoices);
        expectedInvoices.clear();

        // Then we test first the next expected invoice at the account level
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 2, 1), new LocalDate(2016, 2, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));
        dryRunInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), null, dryRun, callContext);
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, callContext, expectedInvoices);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        // 2015-2-1
        clock.addDays(18);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, callContext, expectedInvoices);
        expectedInvoices.clear();

        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 2, 14), new LocalDate(2015, 3, 14), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        dryRunInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), null, dryRun, callContext);
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, callContext, expectedInvoices);
    }

    @Test(groups = "slow")
    public void testApplyCreditOnExistingBalance() throws Exception {
        final DateTime initialCreationDate = new DateTime(2015, 5, 15, 0, 0, 0, 0, testTimeZone);
        // set clock to the initial start date
        clock.setTime(initialCreationDate);

        final int billingDay = 14;

        log.info("Beginning test with BCD of " + billingDay);
        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(billingDay));

        add_AUTO_PAY_OFF_Tag(account.getId(), ObjectType.ACCOUNT);

        createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        // Move through time and verify we get the same invoice
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE);
        clock.addDays(30);
        assertListenerStatus();

        final Collection<Invoice> invoices = invoiceUserApi.getUnpaidInvoicesByAccountId(account.getId(), new LocalDate(clock.getUTCNow(), account.getTimeZone()), callContext);
        assertEquals(invoices.size(), 1);

        final UUID unpaidInvoiceId = invoices.iterator().next().getId();
        final Invoice unpaidInvoice = invoiceUserApi.getInvoice(unpaidInvoiceId, callContext);
        assertTrue(unpaidInvoice.getBalance().compareTo(new BigDecimal("249.95")) == 0);

        final BigDecimal accountBalance1 = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance1.compareTo(new BigDecimal("249.95")) == 0);

        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        invoiceUserApi.insertCredit(account.getId(), new BigDecimal("300"), new LocalDate(clock.getUTCNow(), account.getTimeZone()), account.getCurrency(), true, null, callContext);
        assertListenerStatus();

        final BigDecimal accountBalance2 = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance2.compareTo(new BigDecimal("-50.05")) == 0);

        final Invoice unpaidInvoice2 = invoiceUserApi.getInvoice(unpaidInvoiceId, callContext);
        assertTrue(unpaidInvoice2.getBalance().compareTo(BigDecimal.ZERO) == 0);

        remove_AUTO_PAY_OFF_Tag(account.getId(), ObjectType.ACCOUNT);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(31);
        assertListenerStatus();

        final BigDecimal accountBalance3 = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance3.compareTo(BigDecimal.ZERO) == 0);


        final List<Payment> payments = paymentApi.getAccountPayments(account.getId(), false, false, ImmutableList.<PluginProperty>of(), callContext);
        assertEquals(payments.size(), 1);

        final Payment payment = payments.get(0);
        assertTrue(payment.getPurchasedAmount().compareTo(new BigDecimal("199.90")) == 0);
    }

    @Test(groups = "slow")
    public void testDraftInvoice() throws Exception {

        final int billingDay = 14;
        final DateTime initialCreationDate = new DateTime(2015, 5, 15, 0, 0, 0, 0, testTimeZone);
        // set clock to the initial start date
        clock.setTime(initialCreationDate);

        log.info("Beginning test with BCD of " + billingDay);
        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(billingDay));

        int invoiceItemCount = 1;

        DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY,  NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        DefaultSubscriptionBase subscription = subscriptionDataFromSubscription(baseEntitlement.getSubscriptionBase());

        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 6, 14), new LocalDate(2015, 7, 14), InvoiceItemType.RECURRING, new BigDecimal("249.95")));

        // Move through time and verify we get the same invoice
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        // This will verify that the upcoming invoice notification is found and the invoice is generated at the right date, with correct items
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 7, 14), new LocalDate(2015, 8, 14), InvoiceItemType.RECURRING, new BigDecimal("249.95")));

        // add create external charge
        final LocalDate date = clock.getToday(account.getTimeZone());
        final List<InvoiceItem> invoiceItemList = new ArrayList<InvoiceItem>();
        ExternalChargeInvoiceItem item = new ExternalChargeInvoiceItem(null, account.getId(), subscription.getBundleId(), "", date, BigDecimal.TEN, account.getCurrency());
        invoiceItemList.add(item);
        final List<InvoiceItem> draftInvoiceItems = invoiceUserApi.insertExternalCharges(account.getId(), date, invoiceItemList, false, callContext);

        // add expected invoice
        final List<ExpectedInvoiceItemCheck> expectedDraftInvoices = new ArrayList<ExpectedInvoiceItemCheck>();
        expectedDraftInvoices.add(new ExpectedInvoiceItemCheck(InvoiceItemType.EXTERNAL_CHARGE, BigDecimal.TEN));

        // Move through time and verify invoices
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);

        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, expectedDraftInvoices);
        invoiceChecker.checkInvoice(invoices.get(3).getId(), callContext, expectedInvoices);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceUserApi.commitInvoice(draftInvoiceItems.get(0).getInvoiceId(), callContext);
        assertListenerStatus();

        final List<Payment> accountPayments = paymentApi.getAccountPayments(account.getId(), false, false, null, callContext);
        assertEquals(accountPayments.size(), 3);
        assertEquals(accountPayments.get(2).getPurchasedAmount(), new BigDecimal("10.00"));
    }


    @Test(groups = "slow", description= "See https://github.com/killbill/killbill/issues/127#issuecomment-292445089")
    public void testIntegrationWithBCDLargerThanEndMonth() throws Exception {

        final int billingDay = 31;
        final DateTime initialCreationDate = new DateTime(2017, 01, 31, 0, 0, 0, 0, testTimeZone);
        // set clock to the initial start date
        clock.setTime(initialCreationDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(billingDay));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Blowdart", BillingPeriod.MONTHLY, "notrial", null);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        entitlementApi.createBaseEntitlement(account.getId(), spec, "bundleExternalKey", ImmutableList.<PlanPhasePriceOverride>of(), null, null, false, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // 2017-02-28
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        // 2017-03-31
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        clock.addDays(3);
        assertListenerStatus();

        // 2017-04-30
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();
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
        final Entitlement createdEntitlement = entitlementApi.createBaseEntitlement(account.getId(), spec, account.getExternalKey(), null, futureDate, futureDate, false, ImmutableList.<PluginProperty>of(), callContext);
        assertEquals(createdEntitlement.getState(), Entitlement.EntitlementState.PENDING);
        assertEquals(createdEntitlement.getEffectiveStartDate().compareTo(futureDate), 0);
        assertEquals(createdEntitlement.getEffectiveEndDate(), null);
        assertListenerStatus();

        // Generate a dryRun invoice on the billing startDate
        final DryRunArguments dryRunArguments1 = new TestDryRunArguments(DryRunType.TARGET_DATE);
        final Invoice dryRunInvoice1 = invoiceUserApi.triggerInvoiceGeneration(createdEntitlement.getAccountId(), futureDate, dryRunArguments1, callContext);
        assertEquals(dryRunInvoice1.getInvoiceItems().size(), 1);
        assertEquals(dryRunInvoice1.getInvoiceItems().get(0).getInvoiceItemType(),  InvoiceItemType.FIXED);
        assertEquals(dryRunInvoice1.getInvoiceItems().get(0).getAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(dryRunInvoice1.getInvoiceItems().get(0).getStartDate(),  futureDate);
        assertEquals(dryRunInvoice1.getInvoiceItems().get(0).getPlanName(),  "shotgun-annual");


        // Generate a dryRun invoice with a plan change
        final DryRunArguments dryRunArguments = new TestDryRunArguments(DryRunType.SUBSCRIPTION_ACTION, "Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null,
                SubscriptionEventType.CHANGE,  createdEntitlement.getId(), createdEntitlement.getBundleId(), futureDate, BillingActionPolicy.IMMEDIATE);

        // First one day prior subscription starts
        try {
            invoiceUserApi.triggerInvoiceGeneration(createdEntitlement.getAccountId(), futureDate.minusDays(1), dryRunArguments, callContext);
            fail("Should fail to trigger dryRun invoice prior subscription starts");
        } catch (final InvoiceApiException e) {
            assertEquals(e.getCode(),ErrorCode.INVOICE_NOTHING_TO_DO.getCode());
        }

        // Second, on the startDate
        final Invoice dryRunInvoice2 = invoiceUserApi.triggerInvoiceGeneration(createdEntitlement.getAccountId(), futureDate, dryRunArguments, callContext);
        assertEquals(dryRunInvoice2.getInvoiceItems().size(), 1);
        assertEquals(dryRunInvoice2.getInvoiceItems().get(0).getInvoiceItemType(),  InvoiceItemType.FIXED);
        assertEquals(dryRunInvoice2.getInvoiceItems().get(0).getAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(dryRunInvoice2.getInvoiceItems().get(0).getStartDate(),  futureDate);
        assertEquals(dryRunInvoice2.getInvoiceItems().get(0).getPlanName(),  "pistol-monthly");


        // Check BCD is not yet set
        final Account refreshedAccount1 = accountUserApi.getAccountById(account.getId(), callContext);
        assertEquals(refreshedAccount1.getBillCycleDayLocal(), new Integer(0));


        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        final Invoice realInvoice = invoiceUserApi.triggerInvoiceGeneration(createdEntitlement.getAccountId(), futureDate, null, callContext);
        assertListenerStatus();

        assertEquals(realInvoice.getInvoiceItems().size(), 1);
        assertEquals(realInvoice.getInvoiceItems().get(0).getInvoiceItemType(),  InvoiceItemType.FIXED);
        assertEquals(realInvoice.getInvoiceItems().get(0).getAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(realInvoice.getInvoiceItems().get(0).getStartDate(),  futureDate);
        assertEquals(realInvoice.getInvoiceItems().get(0).getPlanName(),  "shotgun-annual");

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

        final LocalDate futureDate = new LocalDate(2017, 5, 1);

        // No CREATE event as this is set in the future
        final Entitlement createdEntitlement = entitlementApi.createBaseEntitlement(account.getId(), spec, account.getExternalKey(), null, futureDate, futureDate, false, ImmutableList.<PluginProperty>of(), callContext);
        assertEquals(createdEntitlement.getState(), Entitlement.EntitlementState.PENDING);
        assertEquals(createdEntitlement.getEffectiveStartDate().compareTo(futureDate), 0);
        assertEquals(createdEntitlement.getEffectiveEndDate(), null);
        assertListenerStatus();

        // Generate an invoice using a future targetDate
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final Invoice firstInvoice = invoiceUserApi.triggerInvoiceGeneration(createdEntitlement.getAccountId(), futureDate, null, callContext);
        assertListenerStatus();

        assertEquals(firstInvoice.getInvoiceItems().size(), 1);
        assertEquals(firstInvoice.getInvoiceItems().get(0).getInvoiceItemType(),  InvoiceItemType.RECURRING);
        assertEquals(firstInvoice.getInvoiceItems().get(0).getAmount().compareTo(new BigDecimal("19.95")), 0);
        assertEquals(firstInvoice.getInvoiceItems().get(0).getStartDate(),  futureDate);
        assertEquals(firstInvoice.getInvoiceItems().get(0).getPlanName(),  "pistol-monthly-notrial");


        // Cancel subscription on its pending startDate
        createdEntitlement.cancelEntitlementWithDate(futureDate, true, ImmutableList.<PluginProperty>of(), callContext);

        // Move to startDate/cancel Date
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.BLOCK, NextEvent.NULL_INVOICE, NextEvent.INVOICE);
        clock.addMonths(1);
        assertListenerStatus();

        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
        assertEquals(invoices.size(), 2);

        final List<ExpectedInvoiceItemCheck> toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2017, 5, 1), new LocalDate(2017, 6, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-19.95")),
                new ExpectedInvoiceItemCheck(new LocalDate(2017, 5, 1), new LocalDate(2017, 5, 1), InvoiceItemType.CBA_ADJ, new BigDecimal("19.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);




    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/783")
    public void testIntegrationWithRecurringFreePlan() throws Exception {
        final DateTime initialCreationDate = new DateTime(2017, 1, 1, 0, 0, 0, 0, testTimeZone);
        // set clock to the initial start date
        clock.setTime(initialCreationDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Blowdart", BillingPeriod.MONTHLY, "notrial", null);

        // Price override of $0
        final List<PlanPhasePriceOverride> overrides = new ArrayList<PlanPhasePriceOverride>();
        overrides.add(new DefaultPlanPhasePriceOverride("blowdart-monthly-notrial-evergreen", account.getCurrency(), null, BigDecimal.ZERO));
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final Entitlement entitlement = entitlementApi.createBaseEntitlement(account.getId(), spec, "bundleExternalKey", overrides, null, null, false, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2017, 1, 1), new LocalDate(2017, 2, 1), InvoiceItemType.RECURRING, BigDecimal.ZERO));

        // 2017-02-01
        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        clock.addMonths(1);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2017, 2, 1), new LocalDate(2017, 3, 1), InvoiceItemType.RECURRING, BigDecimal.ZERO));

        // Do the change mid-month so the repair triggers the bug in https://github.com/killbill/killbill/issues/783
        entitlement.changePlanWithDate(spec, ImmutableList.<PlanPhasePriceOverride>of(), new LocalDate("2017-02-15"), ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // 2017-02-15
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDays(15);
        assertListenerStatus();

        // Note: no repair
        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2017, 2, 1), new LocalDate(2017, 3, 1), InvoiceItemType.RECURRING, BigDecimal.ZERO));

        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2017, 2, 1), new LocalDate(2017, 2, 15), InvoiceItemType.RECURRING, BigDecimal.ZERO),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2017, 2, 15), new LocalDate(2017, 3, 1), InvoiceItemType.RECURRING, new BigDecimal("14.98")));

        // 2017-03-01
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDays(15);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2017, 3, 1), new LocalDate(2017, 4, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")));

        // 2017-04-01
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 5, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2017, 4, 1), new LocalDate(2017, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
    }
}
