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

package com.ning.billing.beatrix.integration;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.LocalDate;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.beatrix.util.InvoiceChecker.ExpectedItemCheck;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItemType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

@Guice(modules = {BeatrixModule.class})
public class TestIntegration extends TestIntegrationBase {

    @Test(groups = "slow")
    public void testCancelBPWithAOTheSameDay() throws Exception {

        final Account account = createAccountWithPaymentMethod(getAccountData(1));

        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final SubscriptionBundle bundle = entitlementUserApi.createBundleForAccount(account.getId(), "whatever", context);

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE NextEvent.INVOICE
        //
        final Subscription bpSubscription = createSubscriptionAndCheckForCompletion(bundle.getId(), "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.INVOICE);
        invoiceChecker.checkInvoice(account.getId(), 1, new ExpectedItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));

        //
        // ADD ADD_ON ON THE SAME DAY
        //
        createSubscriptionAndCheckForCompletion(bundle.getId(), "Telescopic-Scope", ProductCategory.ADD_ON, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.INVOICE, NextEvent.PAYMENT);
        invoiceChecker.checkInvoice(account.getId(), 2, new ExpectedItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("399.95")));

        //
        // CANCEL BP ON THE SAME DAY (we should have two cancellations, BP and AO)
        // There is no invoice created as we only adjust the previous invoice.
        //
        cancelSubscriptionAndCheckForCompletion(bpSubscription, clock.getUTCNow(), NextEvent.CANCEL, NextEvent.CANCEL);
        invoiceChecker.checkInvoice(account.getId(), 2,
                                    new ExpectedItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("399.95")),
                                    // The second invoice should be adjusted for the AO (we paid for the full period) and since we paid we should also see a CBA
                                    new ExpectedItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-399.95")),
                                    new ExpectedItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 4, 1), InvoiceItemType.CBA_ADJ, new BigDecimal("399.95")));
    }

    @Test(groups = "slow")
    public void testBasePlanCompleteWithBillingDayInPast() throws Exception {

        final int billingDay = 31;
        final DateTime initialCreationDate = new DateTime(2012, 2, 1, 0, 3, 42, 0, testTimeZone);

        log.info("Beginning test with BCD of " + billingDay);
        final Account account = createAccountWithPaymentMethod(getAccountData(billingDay));

        // set clock to the initial start date
        clock.setTime(initialCreationDate);
        final SubscriptionBundle bundle = entitlementUserApi.createBundleForAccount(account.getId(), "whatever", context);

        int invoiceItemCount = 1;

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE NextEvent.INVOICE
        //
        SubscriptionData subscription = subscriptionDataFromSubscription(createSubscriptionAndCheckForCompletion(bundle.getId(), "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.INVOICE));
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, new ExpectedItemCheck(initialCreationDate.toLocalDate(), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        // No end date for the trial item (fixed price of zero), and CTD should be today (i.e. when the trial started)
        invoiceChecker.checkChargedThroughDate(subscription.getId(), clock.getUTCToday());

        //
        // CHANGE PLAN IMMEDIATELY AND EXPECT BOTH EVENTS: NextEvent.CHANGE NextEvent.INVOICE
        //
        subscription = subscriptionDataFromSubscription(changeSubscriptionAndCheckForCompletion(subscription, "Assault-Rifle", BillingPeriod.MONTHLY, NextEvent.CHANGE, NextEvent.INVOICE));
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, new ExpectedItemCheck(initialCreationDate.toLocalDate(), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(subscription.getId(), clock.getUTCToday());

        //
        // MOVE 4 * TIME THE CLOCK
        //
        setDateAndCheckForCompletion(new DateTime(2012, 2, 28, 0, 3, 45, 0, testTimeZone));
        setDateAndCheckForCompletion(new DateTime(2012, 2, 29, 0, 3, 45, 0, testTimeZone));
        setDateAndCheckForCompletion(new DateTime(2012, 3, 1, 0, 3, 45, 0, testTimeZone));
        setDateAndCheckForCompletion(new DateTime(2012, 3, 2, 0, 3, 45, 0, testTimeZone), NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT);
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, new ExpectedItemCheck(new LocalDate(2012, 3, 2),
                                                                                               new LocalDate(2012, 3, 31), InvoiceItemType.RECURRING, new BigDecimal("561.25")));
        invoiceChecker.checkChargedThroughDate(subscription.getId(), new LocalDate(2012, 3, 31));

        //
        // CHANGE PLAN EOT AND EXPECT NOTHING
        //
        subscription = subscriptionDataFromSubscription(changeSubscriptionAndCheckForCompletion(subscription, "Pistol", BillingPeriod.MONTHLY));

        //
        // MOVE TIME AFTER CTD AND EXPECT BOTH EVENTS : NextEvent.CHANGE NextEvent.INVOICE
        //
        final LocalDate firstRecurringPistolDate = subscription.getChargedThroughDate().toLocalDate();
        final LocalDate secondRecurringPistolDate = firstRecurringPistolDate.plusMonths(1);
        addDaysAndCheckForCompletion(31, NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.PAYMENT);
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, new ExpectedItemCheck(new LocalDate(2012, 3, 31), new LocalDate(2012, 4, 30), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
        invoiceChecker.checkChargedThroughDate(subscription.getId(), secondRecurringPistolDate);

        //
        // MOVE 3 * TIME AFTER NEXT BILL CYCLE DAY AND EXPECT EVENT : NextEvent.INVOICE, NextEvent.PAYMENT
        //
        addDaysAndCheckForCompletion(31, NextEvent.INVOICE, NextEvent.PAYMENT);
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, new ExpectedItemCheck(new LocalDate(2012, 4, 30), new LocalDate(2012, 5, 31), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
        invoiceChecker.checkChargedThroughDate(subscription.getId(), new LocalDate(2012, 5, 31));

        addDaysAndCheckForCompletion(31, NextEvent.INVOICE, NextEvent.PAYMENT);
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, new ExpectedItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
        invoiceChecker.checkChargedThroughDate(subscription.getId(), new LocalDate(2012, 6, 30));

        addDaysAndCheckForCompletion(31, NextEvent.INVOICE, NextEvent.PAYMENT);
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, new ExpectedItemCheck(new LocalDate(2012, 6, 30), new LocalDate(2012, 7, 31), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
        invoiceChecker.checkChargedThroughDate(subscription.getId(), new LocalDate(2012, 7, 31));

        //
        // FINALLY CANCEL SUBSCRIPTION EOT
        //
        subscription = subscriptionDataFromSubscription(cancelSubscriptionAndCheckForCompletion(subscription, clock.getUTCNow()));

        // MOVE AFTER CANCEL DATE AND EXPECT EVENT : NextEvent.CANCEL
        addDaysAndCheckForCompletion(31, NextEvent.CANCEL);
        invoiceChecker.checkChargedThroughDate(subscription.getId(), new LocalDate(2012, 7, 31));

        log.info("TEST PASSED !");
    }

    @Test(groups = "slow")
    public void testBasePlanCompleteWithBillingDayAlignedWithTrial() throws Exception {

        final int billingDay = 2;
        final DateTime initialCreationDate = new DateTime(2012, 2, 1, 0, 3, 42, 0, testTimeZone);

        log.info("Beginning test with BCD of " + billingDay);
        final Account account = createAccountWithPaymentMethod(getAccountData(billingDay));

        // set clock to the initial start date
        clock.setTime(initialCreationDate);
        final SubscriptionBundle bundle = entitlementUserApi.createBundleForAccount(account.getId(), "whatever", context);

        int invoiceItemCount = 1;

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE NextEvent.INVOICE
        //
        SubscriptionData subscription = subscriptionDataFromSubscription(createSubscriptionAndCheckForCompletion(bundle.getId(), "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.INVOICE));
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, new ExpectedItemCheck(initialCreationDate.toLocalDate(), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        // No end date for the trial item (fixed price of zero), and CTD should be today (i.e. when the trial started)
        invoiceChecker.checkChargedThroughDate(subscription.getId(), clock.getUTCToday());

        //
        // CHANGE PLAN IMMEDIATELY AND EXPECT BOTH EVENTS: NextEvent.CHANGE NextEvent.INVOICE
        //
        subscription = subscriptionDataFromSubscription(changeSubscriptionAndCheckForCompletion(subscription, "Assault-Rifle", BillingPeriod.MONTHLY, NextEvent.CHANGE, NextEvent.INVOICE));
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, new ExpectedItemCheck(initialCreationDate.toLocalDate(), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(subscription.getId(), clock.getUTCToday());

        //
        // MOVE 4 * TIME THE CLOCK
        //
        setDateAndCheckForCompletion(new DateTime(2012, 2, 28, 0, 3, 45, 0, testTimeZone));
        setDateAndCheckForCompletion(new DateTime(2012, 2, 29, 0, 3, 45, 0, testTimeZone));
        setDateAndCheckForCompletion(new DateTime(2012, 3, 1, 0, 3, 45, 0, testTimeZone));
        setDateAndCheckForCompletion(new DateTime(2012, 3, 2, 0, 3, 45, 0, testTimeZone), NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT);
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, new ExpectedItemCheck(new LocalDate(2012, 3, 2),
                                                                                               new LocalDate(2012, 4, 2), InvoiceItemType.RECURRING, new BigDecimal("599.95")));
        invoiceChecker.checkChargedThroughDate(subscription.getId(), new LocalDate(2012, 4, 2));

        //
        // CHANGE PLAN EOT AND EXPECT NOTHING
        //
        subscription = subscriptionDataFromSubscription(changeSubscriptionAndCheckForCompletion(subscription, "Pistol", BillingPeriod.MONTHLY));

        //
        // MOVE TIME AFTER CTD AND EXPECT BOTH EVENTS : NextEvent.CHANGE NextEvent.INVOICE
        //
        final LocalDate firstRecurringPistolDate = subscription.getChargedThroughDate().toLocalDate();
        final LocalDate secondRecurringPistolDate = firstRecurringPistolDate.plusMonths(1);
        addDaysAndCheckForCompletion(31, NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.PAYMENT);
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, new ExpectedItemCheck(new LocalDate(2012, 4, 2), new LocalDate(2012, 5, 2), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
        invoiceChecker.checkChargedThroughDate(subscription.getId(), secondRecurringPistolDate);

        //
        // MOVE 3 * TIME AFTER NEXT BILL CYCLE DAY AND EXPECT EVENT : NextEvent.INVOICE, NextEvent.PAYMENT
        //
        addDaysAndCheckForCompletion(31, NextEvent.INVOICE, NextEvent.PAYMENT);
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, new ExpectedItemCheck(new LocalDate(2012, 5, 2), new LocalDate(2012, 6, 2), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
        invoiceChecker.checkChargedThroughDate(subscription.getId(), new LocalDate(2012, 6, 2));

        addDaysAndCheckForCompletion(31, NextEvent.INVOICE, NextEvent.PAYMENT);
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, new ExpectedItemCheck(new LocalDate(2012, 6, 2), new LocalDate(2012, 7, 2), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
        invoiceChecker.checkChargedThroughDate(subscription.getId(), new LocalDate(2012, 7, 2));

        addDaysAndCheckForCompletion(31, NextEvent.INVOICE, NextEvent.PAYMENT);
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, new ExpectedItemCheck(new LocalDate(2012, 7, 2), new LocalDate(2012, 8, 2), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
        invoiceChecker.checkChargedThroughDate(subscription.getId(), new LocalDate(2012, 8, 2));

        //
        // FINALLY CANCEL SUBSCRIPTION EOT
        //
        subscription = subscriptionDataFromSubscription(cancelSubscriptionAndCheckForCompletion(subscription, clock.getUTCNow()));

        // MOVE AFTER CANCEL DATE AND EXPECT EVENT : NextEvent.CANCEL
        addDaysAndCheckForCompletion(31, NextEvent.CANCEL);
        invoiceChecker.checkChargedThroughDate(subscription.getId(), new LocalDate(2012, 8, 2));

        log.info("TEST PASSED !");
    }

    @Test(groups = "slow")
    public void testBasePlanCompleteWithBillingDayInFuture() throws Exception {

        final int billingDay = 3;
        final DateTime initialCreationDate = new DateTime(2012, 2, 1, 0, 3, 42, 0, testTimeZone);

        log.info("Beginning test with BCD of " + billingDay);
        final Account account = createAccountWithPaymentMethod(getAccountData(billingDay));

        // set clock to the initial start date
        clock.setTime(initialCreationDate);
        final SubscriptionBundle bundle = entitlementUserApi.createBundleForAccount(account.getId(), "whatever", context);

        int invoiceItemCount = 1;

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE NextEvent.INVOICE
        //
        SubscriptionData subscription = subscriptionDataFromSubscription(createSubscriptionAndCheckForCompletion(bundle.getId(), "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.INVOICE));
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, new ExpectedItemCheck(initialCreationDate.toLocalDate(), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        // No end date for the trial item (fixed price of zero), and CTD should be today (i.e. when the trial started)
        invoiceChecker.checkChargedThroughDate(subscription.getId(), clock.getUTCToday());

        //
        // CHANGE PLAN IMMEDIATELY AND EXPECT BOTH EVENTS: NextEvent.CHANGE NextEvent.INVOICE
        //
        subscription = subscriptionDataFromSubscription(changeSubscriptionAndCheckForCompletion(subscription, "Assault-Rifle", BillingPeriod.MONTHLY, NextEvent.CHANGE, NextEvent.INVOICE));
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, new ExpectedItemCheck(initialCreationDate.toLocalDate(), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(subscription.getId(), clock.getUTCToday());

        //
        // MOVE 4 * TIME THE CLOCK
        //
        setDateAndCheckForCompletion(new DateTime(2012, 2, 28, 0, 3, 45, 0, testTimeZone));
        setDateAndCheckForCompletion(new DateTime(2012, 2, 29, 0, 3, 45, 0, testTimeZone));
        setDateAndCheckForCompletion(new DateTime(2012, 3, 1, 0, 3, 45, 0, testTimeZone));
        setDateAndCheckForCompletion(new DateTime(2012, 3, 2, 0, 3, 45, 0, testTimeZone), NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT);
        // PRO_RATION
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, new ExpectedItemCheck(new LocalDate(2012, 3, 2),
                                                                                               new LocalDate(2012, 3, 3), InvoiceItemType.RECURRING, new BigDecimal("20.70")));
        invoiceChecker.checkChargedThroughDate(subscription.getId(), new LocalDate(2012, 3, 3));

        setDateAndCheckForCompletion(new DateTime(2012, 3, 3, 0, 3, 45, 0, testTimeZone), NextEvent.INVOICE, NextEvent.PAYMENT);
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, new ExpectedItemCheck(new LocalDate(2012, 3, 3),
                                                                                               new LocalDate(2012, 4, 3), InvoiceItemType.RECURRING, new BigDecimal("599.95")));
        invoiceChecker.checkChargedThroughDate(subscription.getId(), new LocalDate(2012, 4, 3));

        //
        // CHANGE PLAN EOT AND EXPECT NOTHING
        //
        subscription = subscriptionDataFromSubscription(changeSubscriptionAndCheckForCompletion(subscription, "Pistol", BillingPeriod.MONTHLY));

        //
        // MOVE TIME AFTER CTD AND EXPECT BOTH EVENTS : NextEvent.CHANGE NextEvent.INVOICE
        //
        final LocalDate firstRecurringPistolDate = subscription.getChargedThroughDate().toLocalDate();
        final LocalDate secondRecurringPistolDate = firstRecurringPistolDate.plusMonths(1);
        addDaysAndCheckForCompletion(31, NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.PAYMENT);
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, new ExpectedItemCheck(new LocalDate(2012, 4, 3), new LocalDate(2012, 5, 3), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
        invoiceChecker.checkChargedThroughDate(subscription.getId(), secondRecurringPistolDate);

        //
        // MOVE 3 * TIME AFTER NEXT BILL CYCLE DAY AND EXPECT EVENT : NextEvent.INVOICE, NextEvent.PAYMENT
        //
        addDaysAndCheckForCompletion(31, NextEvent.INVOICE, NextEvent.PAYMENT);
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, new ExpectedItemCheck(new LocalDate(2012, 5, 3), new LocalDate(2012, 6, 3), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
        invoiceChecker.checkChargedThroughDate(subscription.getId(), new LocalDate(2012, 6, 3));

        addDaysAndCheckForCompletion(31, NextEvent.INVOICE, NextEvent.PAYMENT);
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, new ExpectedItemCheck(new LocalDate(2012, 6, 3), new LocalDate(2012, 7, 3), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
        invoiceChecker.checkChargedThroughDate(subscription.getId(), new LocalDate(2012, 7, 3));

        addDaysAndCheckForCompletion(31, NextEvent.INVOICE, NextEvent.PAYMENT);
        invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, new ExpectedItemCheck(new LocalDate(2012, 7, 3), new LocalDate(2012, 8, 3), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
        invoiceChecker.checkChargedThroughDate(subscription.getId(), new LocalDate(2012, 8, 3));

        //
        // FINALLY CANCEL SUBSCRIPTION EOT
        //
        subscription = subscriptionDataFromSubscription(cancelSubscriptionAndCheckForCompletion(subscription, clock.getUTCNow()));

        // MOVE AFTER CANCEL DATE AND EXPECT EVENT : NextEvent.CANCEL
        addDaysAndCheckForCompletion(31, NextEvent.CANCEL);
        invoiceChecker.checkChargedThroughDate(subscription.getId(), new LocalDate(2012, 8, 3));

        log.info("TEST PASSED !");


    }

    @Test(groups = {"stress"}, enabled = false)
    public void stressTest() throws Exception {
        final int maxIterations = 100;
        for (int curIteration = 0; curIteration < maxIterations; curIteration++) {
            if (curIteration != 0) {
                setupTest();
            }

            log.info("################################  ITERATION " + curIteration + "  #########################");
            cleanupTest();
            setupTest();
            testBasePlanCompleteWithBillingDayInPast();
            Thread.sleep(1000);
            cleanupTest();
            setupTest();
            testBasePlanCompleteWithBillingDayAlignedWithTrial();
            Thread.sleep(1000);
            cleanupTest();
            setupTest();
            testBasePlanCompleteWithBillingDayInFuture();
            if (curIteration < maxIterations - 1) {
                cleanupTest();
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
                setupTest();
            }
            testAddonsWithMultipleAlignments();
            if (curIteration < maxIterations - 1) {
                cleanupTest();
                Thread.sleep(1000);
            }
        }
    }

    @Test(groups = "slow")
    public void testAddonsWithMultipleAlignments() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 13, 42, 0, testTimeZone);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithPaymentMethod(getAccountData(25));
        assertNotNull(account);

        final SubscriptionBundle bundle = entitlementUserApi.createBundleForAccount(account.getId(), "whatever", context);

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

        busHandler.pushExpectedEvent(NextEvent.CREATE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        final SubscriptionData baseSubscription = subscriptionDataFromSubscription(entitlementUserApi.createSubscription(bundle.getId(),
                                                                                                                         new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSetName, null), null, context));
        assertNotNull(baseSubscription);
        assertTrue(busHandler.isCompleted(DELAY));
        assertListenerStatus();

        // MOVE CLOCK A LITTLE BIT-- STILL IN TRIAL
        final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(3));
        log.info("Moving clock from" + clock.getUTCNow() + " to " + clock.getUTCNow().plusDays(3));
        clock.addDays(3);

        busHandler.pushExpectedEvent(NextEvent.CREATE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        subscriptionDataFromSubscription(entitlementUserApi.createSubscription(bundle.getId(),
                                                                               new PlanPhaseSpecifier("Telescopic-Scope", ProductCategory.ADD_ON, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null), null, context));
        assertTrue(busHandler.isCompleted(DELAY));
        assertListenerStatus();

        busHandler.pushExpectedEvent(NextEvent.CREATE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        final SubscriptionData aoSubscription2 = subscriptionDataFromSubscription(entitlementUserApi.createSubscription(bundle.getId(),
                                                                                                                        new PlanPhaseSpecifier("Laser-Scope", ProductCategory.ADD_ON, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null), null, context));
        assertTrue(busHandler.isCompleted(DELAY));
        assertListenerStatus();

        // MOVE CLOCK A LITTLE BIT MORE -- EITHER STAY IN TRIAL OR GET OUT
        busHandler.pushExpectedEvent(NextEvent.PHASE);
        busHandler.pushExpectedEvent(NextEvent.PHASE);
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        log.info("Moving clock from" + clock.getUTCNow() + " to " + clock.getUTCNow().plusDays(28));
        clock.addDays(28);// 26 / 5
        assertTrue(busHandler.isCompleted(DELAY));
        assertListenerStatus();

        busHandler.pushExpectedEvent(NextEvent.PHASE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        log.info("Moving clock from" + clock.getUTCNow() + " to " + clock.getUTCNow().plusDays(3));
        clock.addDays(3);// 29 / 5
        assertTrue(busHandler.isCompleted(DELAY));
        assertListenerStatus();

        log.info("Moving clock from" + clock.getUTCNow() + " to " + clock.getUTCNow().plusDays(10));
        clock.addDays(10);// 8 / 6
        assertTrue(busHandler.isCompleted(DELAY));
        assertListenerStatus();

        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        log.info("Moving clock from" + clock.getUTCNow() + " to " + clock.getUTCNow().plusDays(18));
        clock.addDays(18);// 26 / 6
        assertTrue(busHandler.isCompleted(DELAY));
        assertListenerStatus();

        log.info("Moving clock from" + clock.getUTCNow() + " to " + clock.getUTCNow().plusDays(3));
        clock.addDays(3);
        assertTrue(busHandler.isCompleted(DELAY));
        assertListenerStatus();
    }

    @Test(groups = {"slow"})
    public void testRepairForInvoicing() throws Exception {

        log.info("Starting testRepairForInvoicing");

        final Account account = createAccountWithPaymentMethod(getAccountData(1));
        final UUID accountId = account.getId();
        assertNotNull(account);

        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0, testTimeZone);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final SubscriptionBundle bundle = entitlementUserApi.createBundleForAccount(account.getId(), "someBundle", context);
        assertNotNull(bundle);

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;
        entitlementUserApi.createSubscription(bundle.getId(),
                                              new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSetName, null), null, context);

        busHandler.reset();
        busHandler.pushExpectedEvent(NextEvent.CREATE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        assertTrue(busHandler.isCompleted(DELAY));

        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(accountId);
        assertEquals(invoices.size(), 1);

        // TODO: Jeff implement repair
    }

    @Test(groups = "slow")
    public void testWithRecreatePlan() throws Exception {
        final DateTime initialDate = new DateTime(2012, 2, 1, 0, 3, 42, 0, testTimeZone);
        final int billingDay = 2;

        log.info("Beginning test with BCD of " + billingDay);
        final Account account = createAccountWithPaymentMethod(getAccountData(billingDay));
        final UUID accountId = account.getId();
        assertNotNull(account);

        // set clock to the initial start date
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());
        final SubscriptionBundle bundle = entitlementUserApi.createBundleForAccount(account.getId(), "whatever2", context);

        String productName = "Shotgun";
        BillingPeriod term = BillingPeriod.MONTHLY;
        String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE NextEvent.INVOICE
        //
        busHandler.pushExpectedEvent(NextEvent.CREATE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);

        SubscriptionData subscription = subscriptionDataFromSubscription(entitlementUserApi.createSubscription(bundle.getId(),
                                                                                                               new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSetName, null), null, context));

        assertNotNull(subscription);
        assertTrue(busHandler.isCompleted(DELAY));

        //
        // VERIFY CTD HAS BEEN SET
        //
        final DateTime startDate = subscription.getCurrentPhaseStart();
        final BigDecimal rate = subscription.getCurrentPhase().getFixedPrice().getPrice(Currency.USD);
        final int invoiceItemCount = 1;
        verifyTestResult(accountId, subscription.getId(), startDate, null, rate, clock.getUTCNow(), invoiceItemCount);

        //
        // MOVE TIME TO AFTER TRIAL AND EXPECT BOTH EVENTS :  NextEvent.PHASE NextEvent.INVOICE
        //
        busHandler.pushExpectedEvent(NextEvent.PHASE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        clock.addDeltaFromReality(AT_LEAST_ONE_MONTH_MS);
        assertTrue(busHandler.isCompleted(DELAY));

        subscription = subscriptionDataFromSubscription(entitlementUserApi.getSubscriptionFromId(subscription.getId()));
        subscription.cancel(clock.getUTCNow(), false, context);

        // MOVE AFTER CANCEL DATE AND EXPECT EVENT : NextEvent.CANCEL
        busHandler.pushExpectedEvent(NextEvent.CANCEL);
        DateTime endDate = subscription.getChargedThroughDate();
        final Interval it = new Interval(clock.getUTCNow(), endDate);
        clock.addDeltaFromReality(it.toDurationMillis());
        assertTrue(busHandler.isCompleted(DELAY));

        productName = "Assault-Rifle";
        term = BillingPeriod.MONTHLY;
        planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

        busHandler.pushExpectedEvent(NextEvent.RE_CREATE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        subscription.recreate(new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSetName, null), endDate, context);
        assertTrue(busHandler.isCompleted(DELAY));

        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testForMultipleRecurringPhases() throws Exception {

        log.info("Starting testForMultipleRecurringPhases");

        final DateTime initialCreationDate = new DateTime(2012, 2, 1, 0, 3, 42, 0, testTimeZone);
        clock.setDeltaFromReality(initialCreationDate.getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithPaymentMethod(getAccountData(2));
        final UUID accountId = account.getId();

        final String productName = "Blowdart";
        final String planSetName = "DEFAULT";

        busHandler.pushExpectedEvent(NextEvent.CREATE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        final SubscriptionBundle bundle = entitlementUserApi.createBundleForAccount(accountId, "testKey", context);
        subscriptionDataFromSubscription(entitlementUserApi.createSubscription(bundle.getId(),
                                                                               new PlanPhaseSpecifier(productName, ProductCategory.BASE,
                                                                                                      BillingPeriod.MONTHLY, planSetName, PhaseType.TRIAL), null, context));

        assertTrue(busHandler.isCompleted(DELAY));
        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(accountId);
        assertNotNull(invoices);
        assertTrue(invoices.size() == 1);

        busHandler.pushExpectedEvent(NextEvent.PHASE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        clock.addDeltaFromReality(AT_LEAST_ONE_MONTH_MS);
        assertTrue(busHandler.isCompleted(DELAY));
        invoices = invoiceUserApi.getInvoicesByAccount(accountId);
        assertNotNull(invoices);
        assertEquals(invoices.size(), 2);

        for (int i = 0; i < 5; i++) {
            log.info("============== loop number " + i + "=======================");
            busHandler.pushExpectedEvent(NextEvent.INVOICE);
            busHandler.pushExpectedEvent(NextEvent.PAYMENT);
            clock.addDeltaFromReality(AT_LEAST_ONE_MONTH_MS);
            assertTrue(busHandler.isCompleted(DELAY));
        }

        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        busHandler.pushExpectedEvent(NextEvent.PHASE);
        clock.addDeltaFromReality(AT_LEAST_ONE_MONTH_MS);
        assertTrue(busHandler.isCompleted(DELAY));

        invoices = invoiceUserApi.getInvoicesByAccount(accountId);
        assertNotNull(invoices);
        assertEquals(invoices.size(), 8);

        for (int i = 0; i <= 5; i++) {
            log.info("============== second loop number " + i + "=======================");
            busHandler.pushExpectedEvent(NextEvent.INVOICE);
            busHandler.pushExpectedEvent(NextEvent.PAYMENT);
            clock.addDeltaFromReality(AT_LEAST_ONE_MONTH_MS);
            assertTrue(busHandler.isCompleted(DELAY));
        }

        invoices = invoiceUserApi.getInvoicesByAccount(accountId);
        assertNotNull(invoices);
        assertEquals(invoices.size(), 14);

        assertListenerStatus();
    }
}
