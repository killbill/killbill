/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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
import org.joda.time.Interval;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.beatrix.util.PaymentChecker.ExpectedPaymentCheck;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementActionPolicy;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.entitlement.api.SubscriptionBundle;
import org.killbill.billing.entitlement.api.SubscriptionEventType;
import org.killbill.billing.invoice.api.DryRunType;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBase;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class TestIntegration extends TestIntegrationBase {

    @Test(groups = "slow")
    public void testCancelBPWithAOTheSameDay() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //

        TestDryRunArguments dryRun = new TestDryRunArguments(DryRunType.SUBSCRIPTION_ACTION, "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, null, null,
                                                             SubscriptionEventType.START_BILLING, null, null, null, null);
        Invoice dryRunInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), clock.getUTCToday(), dryRun, callContext);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, callContext, expectedInvoices);

        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        // Check bundle after BP got created otherwise we get an error from auditApi.
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, expectedInvoices);
        expectedInvoices.clear();

        //
        // ADD ADD_ON ON THE SAME DAY
        //
        dryRun = new TestDryRunArguments(DryRunType.SUBSCRIPTION_ACTION, "Telescopic-Scope", ProductCategory.ADD_ON, BillingPeriod.MONTHLY, null, null,
                                         SubscriptionEventType.START_BILLING, null, bpSubscription.getBundleId(), null, null);
        dryRunInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), clock.getUTCToday(), dryRun, callContext);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("399.95")));
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, callContext, expectedInvoices);

        addAOEntitlementAndCheckForCompletion(bpSubscription.getBundleId(), "Telescopic-Scope", ProductCategory.ADD_ON, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        final Invoice invoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext, expectedInvoices);
        paymentChecker.checkPayment(account.getId(), 1, callContext, new ExpectedPaymentCheck(new LocalDate(2012, 4, 1), new BigDecimal("399.95"), TransactionStatus.SUCCESS, invoice.getId(), Currency.USD));
        expectedInvoices.clear();

        //
        // CANCEL BP ON THE SAME DAY (we should have two cancellations, BP and AO)
        // There is no invoice created as we only adjust the previous invoice.
        //
        dryRun = new TestDryRunArguments(DryRunType.SUBSCRIPTION_ACTION, null, null, null, null, null, SubscriptionEventType.STOP_BILLING, bpSubscription.getId(),
                                         bpSubscription.getBundleId(), null, null);
        dryRunInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), clock.getUTCToday(), dryRun, callContext);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-399.95")));
        // The second invoice should be adjusted for the AO (we paid for the full period) and since we paid we should also see a CBA
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 4, 1), InvoiceItemType.CBA_ADJ, new BigDecimal("399.95"),
                                                          false /* Avoid checking dates for CBA because code is using context and context createdDate is wrong  in the test as we reset the clock too late, bummer... */ ));
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, callContext, expectedInvoices);


        cancelEntitlementAndCheckForCompletion(bpSubscription, NextEvent.BLOCK, NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.CANCEL, NextEvent.INVOICE);

        invoiceChecker.checkInvoice(account.getId(), 3,
                                    callContext,
                                    expectedInvoices);

        checkNoMoreInvoiceToGenerate(account);
    }

    @Test(groups = "slow")
    public void testBasePlanCompleteWithBillingDayInPast() throws Exception {
        final int billingDay = 31;
        final DateTime initialCreationDate = new DateTime(2012, 2, 1, 0, 3, 42, 0, testTimeZone);
        // set clock to the initial start date
        clock.setTime(initialCreationDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(billingDay));

        int invoiceItemCount = 1;

        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //
        DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        DefaultSubscriptionBase subscription = subscriptionDataFromSubscription(baseEntitlement.getSubscriptionBase());
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, callContext, new ExpectedInvoiceItemCheck(initialCreationDate.toLocalDate(), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        // No end date for the trial item (fixed price of zero), and CTD should be today (i.e. when the trial started)
        invoiceChecker.checkChargedThroughDate(subscription.getId(), clock.getUTCToday(), callContext);

        //
        // CHANGE PLAN IMMEDIATELY AND EXPECT BOTH EVENTS: NextEvent.CHANGE NextEvent.INVOICE
        //
        TestDryRunArguments dryRun = new TestDryRunArguments(DryRunType.SUBSCRIPTION_ACTION, "Assault-Rifle", ProductCategory.BASE, BillingPeriod.MONTHLY, null, null, SubscriptionEventType.CHANGE,
                                                             subscription.getId(), subscription.getBundleId(), null, null);
        Invoice dryRunInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), clock.getUTCToday(), dryRun, callContext);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(initialCreationDate.toLocalDate(), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, callContext, expectedInvoices);


        clock.addDeltaFromReality(1000); // Make sure CHANGE does not collide with CREATE
        changeEntitlementAndCheckForCompletion(baseEntitlement, "Assault-Rifle", BillingPeriod.MONTHLY, null, NextEvent.CHANGE, NextEvent.INVOICE);
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, callContext, expectedInvoices);
        invoiceChecker.checkChargedThroughDate(subscription.getId(), clock.getUTCToday(), callContext);
        expectedInvoices.clear();

        //
        // MOVE 4 * TIME THE CLOCK
        //
        setDateAndCheckForCompletion(new DateTime(2012, 2, 28, 23, 59, 59, 0, testTimeZone));
        setDateAndCheckForCompletion(new DateTime(2012, 2, 29, 23, 59, 59, 0, testTimeZone));
        setDateAndCheckForCompletion(new DateTime(2012, 3, 1, 23, 59, 59, 0, testTimeZone));

        DateTime nextDate = clock.getUTCNow().plusDays(1);
        dryRun = new TestDryRunArguments(DryRunType.TARGET_DATE);


        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2012, 3, 2), new LocalDate(2012, 3, 31), InvoiceItemType.RECURRING, new BigDecimal("561.24")));

        // Verify first next targetDate
        dryRunInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), new LocalDate(nextDate, testTimeZone), dryRun, callContext);
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, callContext, expectedInvoices);


        setDateAndCheckForCompletion(nextDate, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, callContext, expectedInvoices);
        invoiceChecker.checkChargedThroughDate(subscription.getId(), new LocalDate(2012, 3, 31), callContext);
        expectedInvoices.clear();

        //
        // CHANGE PLAN EOT AND EXPECT NOTHING
        //
        baseEntitlement = changeEntitlementAndCheckForCompletion(baseEntitlement, "Pistol", BillingPeriod.MONTHLY, null);
        subscription = subscriptionDataFromSubscription(baseEntitlement.getSubscriptionBase());

        //
        // MOVE TIME AFTER CTD AND EXPECT BOTH EVENTS : NextEvent.CHANGE NextEvent.INVOICE
        //
        final LocalDate firstRecurringPistolDate = subscription.getChargedThroughDate().toLocalDate();
        final LocalDate secondRecurringPistolDate = firstRecurringPistolDate.plusMonths(1);


        nextDate = clock.getUTCNow().plusDays(31);
        dryRun = new TestDryRunArguments(DryRunType.TARGET_DATE);
        dryRunInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), new LocalDate(nextDate, testTimeZone), dryRun, callContext);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2012, 3, 31), new LocalDate(2012, 4, 30), InvoiceItemType.RECURRING, new BigDecimal("29.95")));

        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, callContext, expectedInvoices);

        addDaysAndCheckForCompletion(31, NextEvent.CHANGE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, callContext, expectedInvoices);
        invoiceChecker.checkChargedThroughDate(subscription.getId(), secondRecurringPistolDate, callContext);
        expectedInvoices.clear();

        //
        // MOVE 3 * TIME AFTER NEXT BILL CYCLE DAY AND EXPECT EVENT : NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT
        //
        addDaysAndCheckForCompletion(31, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 30), new LocalDate(2012, 5, 31), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
        invoiceChecker.checkChargedThroughDate(subscription.getId(), new LocalDate(2012, 5, 31), callContext);

        addDaysAndCheckForCompletion(31, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
        invoiceChecker.checkChargedThroughDate(subscription.getId(), new LocalDate(2012, 6, 30), callContext);

        addDaysAndCheckForCompletion(31, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 30), new LocalDate(2012, 7, 31), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
        invoiceChecker.checkChargedThroughDate(subscription.getId(), new LocalDate(2012, 7, 31), callContext);

        //
        // FINALLY CANCEL SUBSCRIPTION EOT
        //
        baseEntitlement = cancelEntitlementAndCheckForCompletion(baseEntitlement, NextEvent.BLOCK);

        // MOVE AFTER CANCEL DATE AND EXPECT EVENT : NextEvent.CANCEL
        addDaysAndCheckForCompletion(31, NextEvent.CANCEL, NextEvent.NULL_INVOICE, NextEvent.NULL_INVOICE);
        invoiceChecker.checkChargedThroughDate(subscription.getId(), new LocalDate(2012, 7, 31), callContext);

        checkNoMoreInvoiceToGenerate(account);
    }

    @Test(groups = "slow")
    public void testBasePlanCompleteWithBillingDayAlignedWithTrial() throws Exception {
        final int billingDay = 2;
        final DateTime initialCreationDate = new DateTime(2012, 2, 1, 0, 3, 42, 0, testTimeZone);
        // set clock to the initial start date
        clock.setTime(initialCreationDate);

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

        //
        // CHANGE PLAN IMMEDIATELY AND EXPECT BOTH EVENTS: NextEvent.CHANGE NextEvent.INVOICE
        //
        clock.addDeltaFromReality(1000); // Make sure CHANGE does not exactly align with CREATE
        changeEntitlementAndCheckForCompletion(baseEntitlement, "Assault-Rifle", BillingPeriod.MONTHLY, null, NextEvent.CHANGE, NextEvent.INVOICE);
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, callContext, new ExpectedInvoiceItemCheck(initialCreationDate.toLocalDate(), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(subscription.getId(), clock.getUTCToday(), callContext);

        //
        // MOVE 4 * TIME THE CLOCK
        //
        setDateAndCheckForCompletion(new DateTime(2012, 2, 28, 23, 59, 59, 0, testTimeZone));
        setDateAndCheckForCompletion(new DateTime(2012, 2, 29, 23, 59, 59, 0, testTimeZone));
        setDateAndCheckForCompletion(new DateTime(2012, 3, 1, 23, 59, 59, 0, testTimeZone));
        setDateAndCheckForCompletion(new DateTime(2012, 3, 2, 23, 59, 59, 0, testTimeZone), NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 3, 2),
                                                                                                                   new LocalDate(2012, 4, 2), InvoiceItemType.RECURRING, new BigDecimal("599.95")));
        invoiceChecker.checkChargedThroughDate(subscription.getId(), new LocalDate(2012, 4, 2), callContext);

        //
        // CHANGE PLAN EOT AND EXPECT NOTHING
        //


        final TestDryRunArguments dryRun = new TestDryRunArguments(DryRunType.SUBSCRIPTION_ACTION, "Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY, null, null, SubscriptionEventType.CHANGE,
                                                                   subscription.getId(), subscription.getBundleId(), null, null);
        try {
           invoiceUserApi.triggerInvoiceGeneration(account.getId(), clock.getUTCToday(), dryRun, callContext);
            Assert.fail("Call should return no invoices");
        } catch (final InvoiceApiException e) {
            assertEquals(e.getCode(), ErrorCode.INVOICE_NOTHING_TO_DO.getCode());
        }

        baseEntitlement = changeEntitlementAndCheckForCompletion(baseEntitlement, "Pistol", BillingPeriod.MONTHLY, null);
        subscription = subscriptionDataFromSubscription(baseEntitlement.getSubscriptionBase());

        //
        // MOVE TIME AFTER CTD AND EXPECT BOTH EVENTS : NextEvent.CHANGE NextEvent.INVOICE
        //
        final LocalDate firstRecurringPistolDate = subscription.getChargedThroughDate().toLocalDate();
        final LocalDate secondRecurringPistolDate = firstRecurringPistolDate.plusMonths(1);
        addDaysAndCheckForCompletion(31, NextEvent.CHANGE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 2), new LocalDate(2012, 5, 2), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
        invoiceChecker.checkChargedThroughDate(subscription.getId(), secondRecurringPistolDate, callContext);

        //
        // MOVE 3 * TIME AFTER NEXT BILL CYCLE DAY AND EXPECT EVENT : NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT
        //
        addDaysAndCheckForCompletion(31, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 2), new LocalDate(2012, 6, 2), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
        invoiceChecker.checkChargedThroughDate(subscription.getId(), new LocalDate(2012, 6, 2), callContext);

        addDaysAndCheckForCompletion(31, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 2), new LocalDate(2012, 7, 2), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
        invoiceChecker.checkChargedThroughDate(subscription.getId(), new LocalDate(2012, 7, 2), callContext);

        addDaysAndCheckForCompletion(31, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 2), new LocalDate(2012, 8, 2), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
        invoiceChecker.checkChargedThroughDate(subscription.getId(), new LocalDate(2012, 8, 2), callContext);

        //
        // FINALLY CANCEL SUBSCRIPTION EOT
        //
        cancelEntitlementAndCheckForCompletion(baseEntitlement, NextEvent.BLOCK);

        // MOVE AFTER CANCEL DATE
        addDaysAndCheckForCompletion(31, NextEvent.CANCEL, NextEvent.NULL_INVOICE, NextEvent.NULL_INVOICE);
        invoiceChecker.checkChargedThroughDate(subscription.getId(), new LocalDate(2012, 8, 2), callContext);

        checkNoMoreInvoiceToGenerate(account);
    }

    @Test(groups = "slow")
    public void testBasePlanCompleteWithBillingDayInFuture() throws Exception {
        final int billingDay = 3;
        final DateTime initialCreationDate = new DateTime(2012, 2, 1, 0, 3, 42, 0, testTimeZone);
        // set clock to the initial start date
        clock.setTime(initialCreationDate);

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

        //
        // CHANGE PLAN IMMEDIATELY AND EXPECT BOTH EVENTS: NextEvent.CHANGE NextEvent.INVOICE
        //
        clock.addDeltaFromReality(1000); // Ensure CHANGE does not collide with CREATE
        baseEntitlement = changeEntitlementAndCheckForCompletion(baseEntitlement, "Assault-Rifle", BillingPeriod.MONTHLY, null, NextEvent.CHANGE, NextEvent.INVOICE);
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, callContext, new ExpectedInvoiceItemCheck(initialCreationDate.toLocalDate(), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(subscription.getId(), clock.getUTCToday(), callContext);

        //
        // MOVE 4 * TIME THE CLOCK
        //
        setDateAndCheckForCompletion(new DateTime(2012, 2, 28, 23, 59, 59, 0, testTimeZone));
        setDateAndCheckForCompletion(new DateTime(2012, 2, 29, 23, 59, 59, 0, testTimeZone));
        setDateAndCheckForCompletion(new DateTime(2012, 3, 1, 23, 59, 59, 0, testTimeZone));
        setDateAndCheckForCompletion(new DateTime(2012, 3, 2, 23, 59, 59, 0, testTimeZone), NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        // PRO_RATION
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 3, 2),
                                                                                                                   new LocalDate(2012, 3, 3), InvoiceItemType.RECURRING, new BigDecimal("20.69")));
        invoiceChecker.checkChargedThroughDate(subscription.getId(), new LocalDate(2012, 3, 3), callContext);

        setDateAndCheckForCompletion(new DateTime(2012, 3, 3, 23, 59, 59, 0, testTimeZone), NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 3, 3),
                                                                                                                   new LocalDate(2012, 4, 3), InvoiceItemType.RECURRING, new BigDecimal("599.95")));
        invoiceChecker.checkChargedThroughDate(subscription.getId(), new LocalDate(2012, 4, 3), callContext);

        //
        // CHANGE PLAN EOT AND EXPECT NOTHING
        //
        baseEntitlement = changeEntitlementAndCheckForCompletion(baseEntitlement, "Pistol", BillingPeriod.MONTHLY, null);
        subscription = subscriptionDataFromSubscription(baseEntitlement.getSubscriptionBase());

        //
        // MOVE TIME AFTER CTD AND EXPECT BOTH EVENTS : NextEvent.CHANGE NextEvent.INVOICE
        //
        final LocalDate firstRecurringPistolDate = subscription.getChargedThroughDate().toLocalDate();
        final LocalDate secondRecurringPistolDate = firstRecurringPistolDate.plusMonths(1);
        addDaysAndCheckForCompletion(31, NextEvent.CHANGE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 3), new LocalDate(2012, 5, 3), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
        invoiceChecker.checkChargedThroughDate(subscription.getId(), secondRecurringPistolDate, callContext);

        //
        // MOVE 3 * TIME AFTER NEXT BILL CYCLE DAY AND EXPECT EVENT : NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT
        //
        addDaysAndCheckForCompletion(31, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 3), new LocalDate(2012, 6, 3), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
        invoiceChecker.checkChargedThroughDate(subscription.getId(), new LocalDate(2012, 6, 3), callContext);

        addDaysAndCheckForCompletion(31, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 3), new LocalDate(2012, 7, 3), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
        invoiceChecker.checkChargedThroughDate(subscription.getId(), new LocalDate(2012, 7, 3), callContext);

        addDaysAndCheckForCompletion(31, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 3), new LocalDate(2012, 8, 3), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
        invoiceChecker.checkChargedThroughDate(subscription.getId(), new LocalDate(2012, 8, 3), callContext);

        //
        // FINALLY CANCEL SUBSCRIPTION EOT
        //
        baseEntitlement = cancelEntitlementAndCheckForCompletion(baseEntitlement, NextEvent.BLOCK);

        // MOVE AFTER CANCEL DATE AND EXPECT EVENT : NextEvent.CANCEL
        addDaysAndCheckForCompletion(31, NextEvent.CANCEL, NextEvent.NULL_INVOICE, NextEvent.NULL_INVOICE);
        invoiceChecker.checkChargedThroughDate(subscription.getId(), new LocalDate(2012, 8, 3), callContext);

        checkNoMoreInvoiceToGenerate(account);
    }

    @Test(groups = {"stress"}, enabled = false)
    public void stressTest() throws Exception {
        final int maxIterations = 100;
        for (int curIteration = 0; curIteration < maxIterations; curIteration++) {
            if (curIteration != 0) {
                beforeMethod();
            }

            log.info("################################  ITERATION " + curIteration + "  #########################");
            afterMethod();
            beforeMethod();
            testBasePlanCompleteWithBillingDayInPast();
            Thread.sleep(1000);
            afterMethod();
            beforeMethod();
            testBasePlanCompleteWithBillingDayAlignedWithTrial();
            Thread.sleep(1000);
            afterMethod();
            beforeMethod();
            testBasePlanCompleteWithBillingDayInFuture();
            if (curIteration < maxIterations - 1) {
                afterMethod();
                Thread.sleep(1000);
            }
        }
    }

    @Test(groups = {"stress"}, enabled = false)
    public void stressTestDebug() throws Exception {
        final int maxIterations = 100;
        for (int curIteration = 0; curIteration < maxIterations; curIteration++) {
            log.info("################################  ITERATION " + curIteration + "  #########################");
            if (curIteration != 0) {
                beforeMethod();
            }
            testAddonsWithMultipleAlignments();
            if (curIteration < maxIterations - 1) {
                afterMethod();
                Thread.sleep(1000);
            }
        }
    }

    @Test(groups = "slow")
    public void testAddonsWithMultipleAlignments() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 13, 42, 0, testTimeZone);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(25));
        assertNotNull(account);

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        // MOVE CLOCK A LITTLE BIT-- STILL IN TRIAL
        final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(3));
        clock.addDays(3);

        final DefaultEntitlement aoEntitlement1 = addAOEntitlementAndCheckForCompletion(baseEntitlement.getBundleId(), "Telescopic-Scope", ProductCategory.ADD_ON, BillingPeriod.MONTHLY,
                                                                                        NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        final DefaultEntitlement aoEntitlement2 = addAOEntitlementAndCheckForCompletion(baseEntitlement.getBundleId(), "Laser-Scope", ProductCategory.ADD_ON, BillingPeriod.MONTHLY,
                                                                                        NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        // MOVE CLOCK A LITTLE BIT MORE -- EITHER STAY IN TRIAL OR GET OUT
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.PHASE, NextEvent.NULL_INVOICE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(28);// 26 / 5
        assertListenerStatus();

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(3);// 29 / 5
        assertListenerStatus();

        clock.addDays(10);// 8 / 6
        assertListenerStatus();

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(18);// 26 / 6
        assertListenerStatus();

        clock.addDays(3);
        assertListenerStatus();

        checkNoMoreInvoiceToGenerate(account);
    }

    @Test(groups = "slow")
    public void testCreateMultipleBPWithSameExternalKey() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 13, 42, 0, testTimeZone);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(25));
        assertNotNull(account);

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;

        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final SubscriptionBundle initialBundle = subscriptionApi.getActiveSubscriptionBundleForExternalKey("bundleKey", callContext);

        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.NULL_INVOICE);
        baseEntitlement.cancelEntitlementWithPolicy(EntitlementActionPolicy.IMMEDIATE, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        final String newProductName = "Pistol";
        final DefaultEntitlement newBaseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", newProductName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        final SubscriptionBundle newBundle = subscriptionApi.getActiveSubscriptionBundleForExternalKey("bundleKey", callContext);

        assertNotEquals(initialBundle.getId(), newBundle.getId());
        assertEquals(initialBundle.getAccountId(), newBundle.getAccountId());
        assertEquals(initialBundle.getExternalKey(), newBundle.getExternalKey());

        final Entitlement refreshedBseEntitlement = entitlementApi.getEntitlementForId(baseEntitlement.getId(), callContext);

        assertEquals(refreshedBseEntitlement.getState(), EntitlementState.CANCELLED);
        assertEquals(newBaseEntitlement.getState(), EntitlementState.ACTIVE);

        checkNoMoreInvoiceToGenerate(account);
    }

    @Test(groups = "slow")
    public void testWithPauseResume() throws Exception {
        final DateTime initialDate = new DateTime(2012, 2, 1, 0, 3, 42, 0, testTimeZone);
        final int billingDay = 2;
        // set clock to the initial start date
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(billingDay));
        final UUID accountId = account.getId();
        assertNotNull(account);

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(baseEntitlement);

        //
        // VERIFY CTD HAS BEEN SET
        //
        final DefaultSubscriptionBase subscription = (DefaultSubscriptionBase) baseEntitlement.getSubscriptionBase();
        final DateTime startDate = subscription.getCurrentPhaseStart();
        final BigDecimal rate = subscription.getCurrentPhase().getFixed().getPrice().getPrice(Currency.USD);
        verifyTestResult(accountId, subscription.getId(), startDate, null, rate, clock.getUTCNow(), 1);

        //
        // MOVE TIME TO AFTER TRIAL AND EXPECT BOTH EVENTS :  NextEvent.PHASE NextEvent.INVOICE
        //
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDeltaFromReality(AT_LEAST_ONE_MONTH_MS);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 3, 2), new LocalDate(2012, 4, 2), InvoiceItemType.RECURRING, new BigDecimal("249.95")));

        // PAUSE THE ENTITLEMENT
        DefaultEntitlement entitlement = (DefaultEntitlement) entitlementApi.getEntitlementForId(baseEntitlement.getId(), callContext);
        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.INVOICE);
        entitlementApi.pause(entitlement.getBundleId(), clock.getUTCNow().toLocalDate(), ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 3, 2), new LocalDate(2012, 4, 2), InvoiceItemType.RECURRING, new BigDecimal("249.95")));

        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 3, 4), new LocalDate(2012, 4, 2), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-233.82")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 3, 4), new LocalDate(2012, 3, 4), InvoiceItemType.CBA_ADJ, new BigDecimal("233.82")));

        entitlement = (DefaultEntitlement) entitlementApi.getEntitlementForId(baseEntitlement.getId(), callContext);
        Assert.assertEquals(entitlement.getState(), EntitlementState.BLOCKED);

        // MOVE CLOCK FORWARD ADN CHECK THERE IS NO NEW INVOICE
        clock.addDeltaFromReality(AT_LEAST_ONE_MONTH_MS);

        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.NULL_INVOICE, NextEvent.INVOICE);
        entitlementApi.resume(entitlement.getBundleId(), clock.getUTCNow().toLocalDate(), ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 5), new LocalDate(2012, 5, 2), InvoiceItemType.RECURRING, new BigDecimal("224.96")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 5), new LocalDate(2012, 4, 5), InvoiceItemType.CBA_ADJ, new BigDecimal("-224.96")));

        checkNoMoreInvoiceToGenerate(account);
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/715")
    public void testWithPauseResumeAfterENT_CANCELLEDBlockingState() throws Exception {
        final DateTime initialDate = new DateTime(2012, 2, 1, 0, 3, 42, 0, testTimeZone);
        final int billingDay = 2;
        // set clock to the initial start date
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(billingDay));
        final UUID accountId = account.getId();
        assertNotNull(account);

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(baseEntitlement);

        //
        // VERIFY CTD HAS BEEN SET
        //
        final DefaultSubscriptionBase subscription = (DefaultSubscriptionBase) baseEntitlement.getSubscriptionBase();
        final DateTime startDate = subscription.getCurrentPhaseStart();
        final BigDecimal rate = subscription.getCurrentPhase().getFixed().getPrice().getPrice(Currency.USD);
        verifyTestResult(accountId, subscription.getId(), startDate, null, rate, clock.getUTCNow(), 1);

        //
        // MOVE TIME TO AFTER TRIAL (2012-03-04)
        //
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDeltaFromReality(AT_LEAST_ONE_MONTH_MS);
        assertListenerStatus();

        invoiceChecker.checkInvoice(accountId,
                                    1,
                                    callContext,
                                    ImmutableList.<ExpectedInvoiceItemCheck>of(new ExpectedInvoiceItemCheck(new LocalDate(2012, 2, 1), null, InvoiceItemType.FIXED, BigDecimal.ZERO)));

        invoiceChecker.checkInvoice(accountId,
                                    2,
                                    callContext,
                                    ImmutableList.<ExpectedInvoiceItemCheck>of(new ExpectedInvoiceItemCheck(new LocalDate(2012, 3, 2), new LocalDate(2012, 4, 2), InvoiceItemType.RECURRING, new BigDecimal("249.95"))));

        // Pause the entitlement between 2012-03-05 and 2012-03-15
        DefaultEntitlement entitlement = (DefaultEntitlement) entitlementApi.getEntitlementForId(baseEntitlement.getId(), callContext);
        entitlementApi.pause(entitlement.getBundleId(), new LocalDate(2012, 3, 5), ImmutableList.<PluginProperty>of(), callContext);
        entitlementApi.resume(entitlement.getBundleId(), new LocalDate(2012, 3, 15), ImmutableList.<PluginProperty>of(), callContext);

        // Advance clock to 2012-03-07
        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.INVOICE);
        clock.addDays(3);
        assertListenerStatus();

        invoiceChecker.checkInvoice(accountId,
                                    2,
                                    callContext,
                                    ImmutableList.<ExpectedInvoiceItemCheck>of(new ExpectedInvoiceItemCheck(new LocalDate(2012, 3, 2), new LocalDate(2012, 4, 2), InvoiceItemType.RECURRING, new BigDecimal("249.95"))));

        invoiceChecker.checkInvoice(accountId,
                                    3,
                                    callContext,
                                    ImmutableList.<ExpectedInvoiceItemCheck>of(new ExpectedInvoiceItemCheck(new LocalDate(2012, 3, 5), new LocalDate(2012, 4, 2), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-225.76")),
                                                                               new ExpectedInvoiceItemCheck(new LocalDate(2012, 3, 7), new LocalDate(2012, 3, 7), InvoiceItemType.CBA_ADJ, new BigDecimal("225.76"))));

        // Entitlement should be blocked
        entitlement = (DefaultEntitlement) entitlementApi.getEntitlementForId(baseEntitlement.getId(), callContext);
        Assert.assertEquals(entitlement.getState(), EntitlementState.BLOCKED);

        // Advance clock to 2012-03-12, nothing should happen
        clock.addDays(5);
        assertListenerStatus();

        // Entitlement is still blocked
        entitlement = (DefaultEntitlement) entitlementApi.getEntitlementForId(baseEntitlement.getId(), callContext);
        Assert.assertEquals(entitlement.getState(), EntitlementState.BLOCKED);

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(accountId, false, callContext);
        assertEquals(invoices.size(), 3);

        // Cancel entitlement start of term but with billing policy immediate (ENT_BLOCKED must be after ENT_CANCELLED to trigger the bug)
        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.NULL_INVOICE);
        baseEntitlement.cancelEntitlementWithDateOverrideBillingPolicy(new LocalDate(2012, 3, 2), BillingActionPolicy.IMMEDIATE, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // 2012-03-16
        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        clock.addDays(4);
        assertListenerStatus();

        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE);
        clock.addMonths(3);
        assertListenerStatus();

        // No new invoices
        invoices = invoiceUserApi.getInvoicesByAccount(accountId, false, callContext);
        assertEquals(invoices.size(), 3);
    }

    @Test(groups = "slow")
    public void testForMultipleRecurringPhases() throws Exception {
        final DateTime initialCreationDate = new DateTime(2012, 2, 1, 0, 3, 42, 0, testTimeZone);
        clock.setDeltaFromReality(initialCreationDate.getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(2));
        final UUID accountId = account.getId();

        final String productName = "Blowdart";
        final String planSetName = "DEFAULT";

        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", productName, ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(accountId, false, callContext);
        assertNotNull(invoices);
        assertTrue(invoices.size() == 1);

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDeltaFromReality(AT_LEAST_ONE_MONTH_MS);
        assertListenerStatus();
        invoices = invoiceUserApi.getInvoicesByAccount(accountId, false, callContext);
        assertNotNull(invoices);
        assertEquals(invoices.size(), 2);

        for (int i = 0; i < 5; i++) {
            log.info("============== loop number " + i + "=======================");
            busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
            clock.addDeltaFromReality(AT_LEAST_ONE_MONTH_MS);
            assertListenerStatus();
        }

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDeltaFromReality(AT_LEAST_ONE_MONTH_MS);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(accountId, false, callContext);
        assertNotNull(invoices);
        assertEquals(invoices.size(), 8);

        for (int i = 0; i <= 5; i++) {
            log.info("============== second loop number " + i + "=======================");
            busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
            clock.addDeltaFromReality(AT_LEAST_ONE_MONTH_MS);
            assertListenerStatus();
        }

        invoices = invoiceUserApi.getInvoicesByAccount(accountId, false, callContext);
        assertNotNull(invoices);
        assertEquals(invoices.size(), 14);

        assertListenerStatus();

        checkNoMoreInvoiceToGenerate(account);
    }

    @Test(groups = "slow")
    public void testFixedTermPlan() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2015, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);


        // First invoice
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);


        // Second invoice -> first recurring for Refurbish-Maintenance
        addAOEntitlementAndCheckForCompletion(bpSubscription.getBundleId(), "Refurbish-Maintenance", ProductCategory.ADD_ON, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();

        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("599.95")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 4, 1), new LocalDate(2015, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("199.95")));
        invoiceChecker.checkInvoice(account.getId(), 2, callContext, expectedInvoices);
        expectedInvoices.clear();


        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 5, 1), new LocalDate(2015, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("199.95")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 5, 1), new LocalDate(2015, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")));

        // 2015-5-1
        // Third invoice -> second recurring for Refurbish-Maintenance
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30); // Also = 1 month because or initial date 2015, 4, 1
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 3, callContext, expectedInvoices);
        expectedInvoices.clear();


        // Next 10 invoices -> third to twelve **and last** recurring for Refurbish-Maintenance
        LocalDate startDate = new LocalDate(2015, 6, 1);
        for (int i = 0; i < 10; i++) {

            final LocalDate endDate = startDate.plusMonths(1);
            expectedInvoices.add(new ExpectedInvoiceItemCheck(startDate, endDate, InvoiceItemType.RECURRING, new BigDecimal("199.95")));
            expectedInvoices.add(new ExpectedInvoiceItemCheck(startDate, endDate, InvoiceItemType.RECURRING, new BigDecimal("29.95")));

            busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
            clock.addMonths(1);
            assertListenerStatus();

            invoiceChecker.checkInvoice(account.getId(), 4 + i, callContext, expectedInvoices);
            expectedInvoices.clear();

            startDate = endDate;
        }

        // We check there is no more recurring for Refurbish-Maintenance
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 4, 1), new LocalDate(2016, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")));

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();


        invoiceChecker.checkInvoice(account.getId(), 14, callContext, expectedInvoices);
        expectedInvoices.clear();
    }

    @Test(groups = "slow")
    public void testThirtyDaysPlanWithFixedTermMonthlyAddOn() throws Exception {
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2015, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();

        // First invoice
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Pistol", ProductCategory.BASE, BillingPeriod.THIRTY_DAYS, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 4, 1), null, InvoiceItemType.FIXED, BigDecimal.ZERO));
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, expectedInvoices);
        expectedInvoices.clear();

        // Second invoice -> first recurring for Refurbish-Maintenance
        addAOEntitlementAndCheckForCompletion(bpSubscription.getBundleId(), "Refurbish-Maintenance", ProductCategory.ADD_ON, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("599.95")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 4, 1), new LocalDate(2015, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("199.95")));
        invoiceChecker.checkInvoice(account.getId(), 2, callContext, expectedInvoices);
        expectedInvoices.clear();

        // 2015-5-1
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 5, 1), new LocalDate(2015, 5, 31), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 5, 1), new LocalDate(2015, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("199.95")));
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30); // Also = 1 month because or initial date 2015, 4, 1
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 3, callContext, expectedInvoices);
        expectedInvoices.clear();

        // Next 20 invoices, including last recurring for Refurbish-Maintenance
        LocalDate startDateBase = new LocalDate(2015, 5, 31);
        LocalDate startDateAddOn = new LocalDate(2015, 6, 1);
        for (int i = 0; i < 10; i++) {
            final LocalDate endDateBase = startDateBase.plusDays(30);
            expectedInvoices.add(new ExpectedInvoiceItemCheck(startDateBase, endDateBase, InvoiceItemType.RECURRING, new BigDecimal("29.95")));
            busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
            clock.setDay(startDateBase);
            assertListenerStatus();
            invoiceChecker.checkInvoice(account.getId(), 4 + 2 * i, callContext, expectedInvoices);
            expectedInvoices.clear();

            final LocalDate endDateAddOn = startDateAddOn.plusMonths(1);
            expectedInvoices.add(new ExpectedInvoiceItemCheck(startDateAddOn, endDateAddOn, InvoiceItemType.RECURRING, new BigDecimal("199.95")));
            busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
            clock.setDay(startDateAddOn);
            assertListenerStatus();
            invoiceChecker.checkInvoice(account.getId(), 5 + 2 * i, callContext, expectedInvoices);
            expectedInvoices.clear();

            startDateBase = endDateBase;
            startDateAddOn = endDateAddOn;
        }
        // clock at 2016-03-01

        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 3, 26), new LocalDate(2016, 4, 25), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.setDay(new LocalDate(2016, 3, 26));
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 24, callContext, expectedInvoices);
        expectedInvoices.clear();

        // We check there is no more recurring for Refurbish-Maintenance
        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE);
        clock.setDay(new LocalDate(2016, 4, 1));
        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testWeeklyPlan() throws Exception {
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2015, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();

        // First invoice
        createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Pistol", ProductCategory.BASE, BillingPeriod.WEEKLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 4, 1), null, InvoiceItemType.FIXED, BigDecimal.ZERO));
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, expectedInvoices);
        expectedInvoices.clear();

        // 2015-5-1
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 5, 1), new LocalDate(2015, 5, 8), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30); // Also = 1 month because or initial date 2015, 4, 1
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 2, callContext, expectedInvoices);
        expectedInvoices.clear();

        LocalDate startDateBase = new LocalDate(2015, 5, 8);
        for (int i = 0; i < 10; i++) {
            final LocalDate endDateBase = startDateBase.plusDays(7);
            expectedInvoices.add(new ExpectedInvoiceItemCheck(startDateBase, endDateBase, InvoiceItemType.RECURRING, new BigDecimal("29.95")));
            busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
            clock.setDay(startDateBase);
            assertListenerStatus();
            invoiceChecker.checkInvoice(account.getId(), 3 + i, callContext, expectedInvoices);
            expectedInvoices.clear();

            startDateBase = endDateBase;
        }
    }
}
