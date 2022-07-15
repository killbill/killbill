/*
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
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

package org.killbill.billing.beatrix.integration.usage;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.integration.TestIntegrationBase;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.junction.DefaultBlockingState;
import org.killbill.billing.usage.api.SubscriptionUsageRecord;
import org.killbill.billing.usage.api.UnitUsageRecord;
import org.killbill.billing.usage.api.UsageRecord;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

public class TestUsageInArrear extends TestIntegrationBase {



    @Test(groups = "slow")
    public void testWithUsageAtCancellationDay() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.ANNUAL, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        // Check bundle after BP got created otherwise we get an error from auditApi.
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));

        //
        // ADD ADD_ON ON THE SAME DAY
        //
        final DefaultEntitlement aoSubscription = addAOEntitlementAndCheckForCompletion(bpSubscription.getBundleId(), "Bullets", ProductCategory.ADD_ON, BillingPeriod.NO_BILLING_PERIOD, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);

        recordUsageData(aoSubscription.getId(), "tracking-1", "bullets", clock.getUTCNow(), BigDecimal.valueOf(99L), callContext);
        recordUsageData(aoSubscription.getId(), "tracking-2", "bullets", new LocalDate(2012, 4, 15), BigDecimal.valueOf(100L), callContext);

        // 2012-05-01
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2013, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")),
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.USAGE, new BigDecimal("5.90")));
        invoiceChecker.checkTrackingIds(curInvoice, Set.of("tracking-1", "tracking-2"), internalCallContext);

        recordUsageData(aoSubscription.getId(), "tracking-3", "bullets", new LocalDate(2012, 5, 1), BigDecimal.valueOf(50L), callContext);
        recordUsageData(aoSubscription.getId(), "tracking-4", "bullets", new LocalDate(2012, 5, 16), BigDecimal.valueOf(300L), callContext);

        // 2012-05-16
        clock.addDays(15);
        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        aoSubscription.cancelEntitlementWithDateOverrideBillingPolicy(new LocalDate(2012, 5, 16), BillingActionPolicy.IMMEDIATE, Collections.emptyList(), callContext);
        assertListenerStatus();

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 5, 16), InvoiceItemType.USAGE, new BigDecimal("11.8")));
        invoiceChecker.checkTrackingIds(curInvoice, Set.of("tracking-3", "tracking-4"), internalCallContext);

        clock.addDays(15);
        assertListenerStatus();
    }


    // We cancel on the startDt and expect to see the usage point for that day
    @Test(groups = "slow")
    public void testWithUsageAtCancellationDay2() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.ANNUAL, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        // Check bundle after BP got created otherwise we get an error from auditApi.
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));

        //
        // ADD ADD_ON ON THE SAME DAY
        //
        final DefaultEntitlement aoSubscription = addAOEntitlementAndCheckForCompletion(bpSubscription.getBundleId(), "Bullets", ProductCategory.ADD_ON, BillingPeriod.NO_BILLING_PERIOD, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);

        // This point should be taken into account
        recordUsageData(aoSubscription.getId(), "tracking-1", "bullets", clock.getUTCNow(), BigDecimal.valueOf(99L), callContext);
        // This point should not be taken into account as it is past cancellation date
        recordUsageData(aoSubscription.getId(), "tracking-2", "bullets", new LocalDate(2012, 4, 15), BigDecimal.valueOf(100L), callContext);

        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        aoSubscription.cancelEntitlementWithDate(clock.getUTCNow(), clock.getUTCNow(), Collections.emptyList(), callContext);
        assertListenerStatus();


        final Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                               new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 4, 1), InvoiceItemType.USAGE, new BigDecimal("2.95")));
        invoiceChecker.checkTrackingIds(curInvoice, Set.of("tracking-1"), internalCallContext);

        assertListenerStatus();
    }


    @Test(groups = "slow")
    public void testWithUsageAtCancellationDayAndEOTCancellation() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.ANNUAL, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        // Check bundle after BP got created otherwise we get an error from auditApi.
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));

        //
        // ADD ADD_ON ON THE SAME DAY
        //
        final DefaultEntitlement aoSubscription = addAOEntitlementAndCheckForCompletion(bpSubscription.getBundleId(), "Bullets", ProductCategory.ADD_ON, BillingPeriod.NO_BILLING_PERIOD, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);

        recordUsageData(aoSubscription.getId(), "tracking-1", "bullets", clock.getUTCNow(), BigDecimal.valueOf(99L), callContext);
        recordUsageData(aoSubscription.getId(), "tracking-2", "bullets", new LocalDate(2012, 4, 15), BigDecimal.valueOf(100L), callContext);

        // 2012-05-01
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2013, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")),
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.USAGE, new BigDecimal("5.90")));
        invoiceChecker.checkTrackingIds(curInvoice, Set.of("tracking-1", "tracking-2"), internalCallContext);

        recordUsageData(aoSubscription.getId(), "tracking-3", "bullets", new LocalDate(2012, 5, 1), BigDecimal.valueOf(50L), callContext);
        recordUsageData(aoSubscription.getId(), "tracking-4", "bullets", new LocalDate(2012, 6, 1), BigDecimal.valueOf(300L), callContext);

        // 2012-05-16
        clock.addDays(15);
        // Cancel "EOT" (at the BCD, cannot use EOT policy as usage is billed in arrear)
        aoSubscription.cancelEntitlementWithDate(new LocalDate(2012, 6, 1), true, Collections.emptyList(), callContext);
        assertListenerStatus();

        // 2012-06-01
        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT, NextEvent.NULL_INVOICE);
        clock.addDays(16);
        assertListenerStatus();

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.USAGE, new BigDecimal("11.8")));
        invoiceChecker.checkTrackingIds(curInvoice, Set.of("tracking-3", "tracking-4"), internalCallContext);
    }

    @Test(groups = "slow")
    public void testWithUsageAtCancellationDayWithUncancel() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.ANNUAL, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        // Check bundle after BP got created otherwise we get an error from auditApi.
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));

        //
        // ADD ADD_ON ON THE SAME DAY
        //
        final DefaultEntitlement aoSubscription = addAOEntitlementAndCheckForCompletion(bpSubscription.getBundleId(), "Bullets", ProductCategory.ADD_ON, BillingPeriod.NO_BILLING_PERIOD, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);

        recordUsageData(aoSubscription.getId(), "tracking-1", "bullets", new LocalDate(2012, 4, 1), BigDecimal.valueOf(99L), callContext);
        recordUsageData(aoSubscription.getId(), "tracking-2", "bullets", new LocalDate(2012, 4, 15), BigDecimal.valueOf(100L), callContext);

        // 2012-05-01
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2013, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")),
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.USAGE, new BigDecimal("5.90")));
        invoiceChecker.checkTrackingIds(curInvoice, Set.of("tracking-1", "tracking-2"), internalCallContext);

        // Billed on 2012-06-01
        recordUsageData(aoSubscription.getId(), "tracking-3", "bullets", new LocalDate(2012, 5, 1), BigDecimal.valueOf(50L), callContext);
        // Billed on 2012-07-01
        recordUsageData(aoSubscription.getId(), "tracking-4", "bullets", new LocalDate(2012, 6, 1), BigDecimal.valueOf(300L), callContext);

        // 2012-05-16
        clock.addDays(15);
        // Cancel "EOT" (at the BCD, cannot use EOT policy as usage is billed in arrear)
        aoSubscription.cancelEntitlementWithDate(new LocalDate(2012, 6, 1), true, Collections.emptyList(), callContext);
        assertListenerStatus();

        // 2012-05-17
        busHandler.pushExpectedEvents(NextEvent.UNCANCEL);
        clock.addDays(1);
        aoSubscription.uncancelEntitlement(Collections.emptyList(), callContext);
        assertListenerStatus();

        // 2012-06-01
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(15);
        assertListenerStatus();

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.USAGE, new BigDecimal("2.95")));
        invoiceChecker.checkTrackingIds(curInvoice, Set.of("tracking-3"), internalCallContext);

        // 2012-07-01
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), new LocalDate(2012, 7, 1), InvoiceItemType.USAGE, new BigDecimal("8.85")));
        invoiceChecker.checkTrackingIds(curInvoice, Set.of("tracking-4"), internalCallContext);
    }

    @Test(groups = "slow")
    public void testWithNoUsageInPeriodAndOldUsage() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.ANNUAL, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        // Check bundle after BP got created otherwise we get an error from auditApi.
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));

        //
        // ADD ADD_ON ON THE SAME DAY
        //
        final DefaultEntitlement aoSubscription = addAOEntitlementAndCheckForCompletion(bpSubscription.getBundleId(), "Bullets", ProductCategory.ADD_ON, BillingPeriod.NO_BILLING_PERIOD, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);

        recordUsageData(aoSubscription.getId(), "tracking-1", "bullets", clock.getUTCNow(), BigDecimal.valueOf(99L), callContext);
        recordUsageData(aoSubscription.getId(), "tracking-2", "bullets", new LocalDate(2012, 4, 15), BigDecimal.valueOf(100L), callContext);

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2013, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")),
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.USAGE, new BigDecimal("5.90")));
        invoiceChecker.checkTrackingIds(curInvoice, Set.of("tracking-1", "tracking-2"), internalCallContext);

        // $0 invoice
        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        clock.addMonths(1);
        assertListenerStatus();

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.USAGE, BigDecimal.ZERO));
        invoiceChecker.checkTrackingIds(curInvoice, Collections.emptySet(), internalCallContext);

        recordUsageData(aoSubscription.getId(), "tracking-3", "bullets", new LocalDate(2012, 6, 1), BigDecimal.valueOf(50L), callContext);
        recordUsageData(aoSubscription.getId(), "tracking-4", "bullets", new LocalDate(2012, 6, 16), BigDecimal.valueOf(300L), callContext);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), new LocalDate(2012, 7, 1), InvoiceItemType.USAGE, new BigDecimal("11.80")));
        invoiceChecker.checkTrackingIds(curInvoice, Set.of("tracking-3", "tracking-4"), internalCallContext);

        // Should be ignored because this is outside of optimization range (org.killbill.invoice.readMaxRawUsagePreviousPeriod = 2) => we will only look for items > 2012-7-1 - 2 months = 2012-5-1
        recordUsageData(aoSubscription.getId(), "tracking-5", "bullets", new LocalDate(2012, 4, 30), BigDecimal.valueOf(100L), callContext);

        // Should be invoiced from past period
        recordUsageData(aoSubscription.getId(), "tracking-6", "bullets", new LocalDate(2012, 5, 1), BigDecimal.valueOf(199L), callContext);

        // New usage for this past period
        recordUsageData(aoSubscription.getId(), "tracking-7", "bullets", new LocalDate(2012, 7, 1), BigDecimal.valueOf(50L), callContext);
        recordUsageData(aoSubscription.getId(), "tracking-8", "bullets", new LocalDate(2012, 7, 16), BigDecimal.valueOf(300L), callContext);

        // Remove old data, should be ignored by the system because readMaxRawUsagePreviousPeriod = 2, so:
        // * Last endDate invoiced is 2012-7-1 => Anything 2 period prior that will be ignored => Anything prior 2012-5-1 should be ignored
        removeUsageData(aoSubscription.getId(), "bullets", new LocalDate(2012, 4, 15));

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 5, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.USAGE, new BigDecimal("5.90")),
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 1), new LocalDate(2012, 8, 1), InvoiceItemType.USAGE, new BigDecimal("11.80")));
        invoiceChecker.checkTrackingIds(curInvoice, Set.of("tracking-6", "tracking-7", "tracking-8"), internalCallContext);

        // Add a few more month of usage data and check correctness of invoice: iterate 8 times until 2013-4-1 (prior ANNUAL renewal)
        LocalDate startDate = new LocalDate(2012, 8, 1);
        int currentInvoice = 6;
        for (int i = 0; i < 8; i++) {

            final String trackingId = "tracking-" + (9 + i);
            recordUsageData(aoSubscription.getId(), trackingId, "bullets", startDate.plusDays(15), BigDecimal.valueOf(350L), callContext);

            busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
            clock.addMonths(1);
            assertListenerStatus();

            curInvoice = invoiceChecker.checkInvoice(account.getId(), currentInvoice, callContext,
                                                     new ExpectedInvoiceItemCheck(startDate, startDate.plusMonths(1), InvoiceItemType.USAGE, new BigDecimal("11.80")));
            invoiceChecker.checkTrackingIds(curInvoice, Set.of(trackingId), internalCallContext);

            startDate = startDate.plusMonths(1);
            currentInvoice++;
        }
    }

    @Test(groups = "slow")
    public void testWithChange() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.ANNUAL, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        // Check bundle after BP got created otherwise we get an error from auditApi.
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));

        //
        // ADD ADD_ON ON THE SAME DAY
        //
        final DefaultEntitlement aoSubscription = addAOEntitlementAndCheckForCompletion(bpSubscription.getBundleId(),
                                                                                        "Bullets",
                                                                                        ProductCategory.ADD_ON,
                                                                                        BillingPeriod.NO_BILLING_PERIOD,
                                                                                        new LocalDate(2012, 4, 1),
                                                                                        NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);

        recordUsageData(aoSubscription.getId(), "t1", "bullets", new LocalDate(2012, 4, 1), BigDecimal.valueOf(99L), callContext);
        recordUsageData(aoSubscription.getId(), "t2", "bullets", new LocalDate(2012, 4, 15), BigDecimal.valueOf(100L), callContext);

        // Trigger future invoice
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceUserApi.triggerInvoiceGeneration(account.getId(),
                                                new LocalDate(2012, 5, 1),
                                                Collections.emptyList(),
                                                callContext);
        assertListenerStatus();
        final Invoice secondInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                                  new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2013, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")),
                                                                  new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.USAGE, new BigDecimal("5.90")));
        invoiceChecker.checkTrackingIds(secondInvoice, Set.of("t1", "t2"), internalCallContext);

        // Change to the Slugs plan
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.INVOICE);
        aoSubscription.changePlanWithDate(new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("slugs-usage-in-arrear")),
                                          new LocalDate(2012, 4, 1),
                                          Collections.emptyList(),
                                          callContext);
        assertListenerStatus();

        // Verify invoices (second invoice is unchanged)
        final Invoice updatedSecondInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                                         new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2013, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")),
                                                                         new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.USAGE, new BigDecimal("5.90")));
        invoiceChecker.checkTrackingIds(updatedSecondInvoice, Set.of("t1", "t2"), internalCallContext);
        final Invoice thirdInvoice = invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.USAGE, BigDecimal.ZERO));
        invoiceChecker.checkTrackingIds(thirdInvoice, Collections.emptySet(), internalCallContext);

        // Add usage data
        recordUsageData(aoSubscription.getId(), "u1", "slugs", new LocalDate(2012, 4, 1), BigDecimal.valueOf(99L), callContext);
        recordUsageData(aoSubscription.getId(), "u2", "slugs", new LocalDate(2012, 4, 15), BigDecimal.valueOf(100L), callContext);

        // Trigger future invoice
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceUserApi.triggerInvoiceGeneration(account.getId(),
                                                new LocalDate(2012, 5, 1),
                                                Collections.emptyList(),
                                                callContext);
        assertListenerStatus();

        final Invoice fourthInvoice = invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                                                  new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.USAGE, new BigDecimal("4")));
        invoiceChecker.checkTrackingIds(fourthInvoice, Set.of("u1", "u2"), internalCallContext);
    }

    @Test(groups = "slow")
    public void testWithCancellation() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.ANNUAL, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        // Check bundle after BP got created otherwise we get an error from auditApi.
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));

        //
        // ADD ADD_ON ON THE SAME DAY
        //
        final DefaultEntitlement aoSubscription = addAOEntitlementAndCheckForCompletion(bpSubscription.getBundleId(), "Bullets", ProductCategory.ADD_ON, BillingPeriod.NO_BILLING_PERIOD, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);

        recordUsageData(aoSubscription.getId(), "t1", "bullets", clock.getUTCNow(), BigDecimal.valueOf(99L), callContext);
        recordUsageData(aoSubscription.getId(), "t2", "bullets", new LocalDate(2012, 4, 15), BigDecimal.valueOf(100L), callContext);

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();
        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2013, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")),
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.USAGE, new BigDecimal("5.90")));
        invoiceChecker.checkTrackingIds(curInvoice, Set.of("t1", "t2"), internalCallContext);

        recordUsageData(aoSubscription.getId(), "t3", "bullets", new LocalDate(2012, 5, 3), BigDecimal.valueOf(99L), callContext);
        recordUsageData(aoSubscription.getId(), "t4", "bullets", new LocalDate(2012, 5, 5), BigDecimal.valueOf(100L), callContext);

        // This one should be ignored
        recordUsageData(aoSubscription.getId(), "t5", "bullets", new LocalDate(2012, 5, 29), BigDecimal.valueOf(100L), callContext);

        clock.addDays(27);
        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        aoSubscription.cancelEntitlementWithDateOverrideBillingPolicy(new LocalDate(2012, 5, 28), BillingActionPolicy.IMMEDIATE, Collections.emptyList(), callContext);
        assertListenerStatus();

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 5, 28), InvoiceItemType.USAGE, new BigDecimal("5.90")));
        invoiceChecker.checkTrackingIds(curInvoice, Set.of("t3", "t4"), internalCallContext);

        busHandler.pushExpectedEvent(NextEvent.NULL_INVOICE);
        clock.addDays(4);
        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testWithNoRecurringPlan() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        // Create subscription
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Trebuchet", ProductCategory.BASE, BillingPeriod.NO_BILLING_PERIOD, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        // Check bundle after BP got created otherwise we get an error from auditApi.
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);

        Assert.assertNull(bpSubscription.getSubscriptionBase().getChargedThroughDate());

        // Record usage for first month
        recordUsageData(bpSubscription.getId(), "xxx-1", "stones", new LocalDate(2012, 4, 5), BigDecimal.valueOf(85L), callContext);
        recordUsageData(bpSubscription.getId(), "xxx-2", "stones", new LocalDate(2012, 4, 15), BigDecimal.valueOf(150L), callContext);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.USAGE, new BigDecimal("1000")));
        invoiceChecker.checkTrackingIds(curInvoice, Set.of("xxx-1", "xxx-2"), internalCallContext);

        final DateTime firstExpectedCTD = account.getReferenceTime().withMonthOfYear(5).withDayOfMonth(1);
        assertEquals(subscriptionBaseInternalApiApi.getSubscriptionFromId(bpSubscription.getId(), internalCallContext).getChargedThroughDate().compareTo(firstExpectedCTD), 0);

        // $0 invoice
        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        clock.addMonths(1);
        assertListenerStatus();

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.USAGE, BigDecimal.ZERO));
        invoiceChecker.checkTrackingIds(curInvoice, Collections.emptySet(), internalCallContext);

        final DateTime secondExpectedCTD = account.getReferenceTime().withMonthOfYear(6).withDayOfMonth(1);

        assertEquals(subscriptionBaseInternalApiApi.getSubscriptionFromId(bpSubscription.getId(), internalCallContext).getChargedThroughDate().compareTo(secondExpectedCTD), 0);

        // Record usage for third month (verify invoicing resumes)
        recordUsageData(bpSubscription.getId(), "xxx-3", "stones", new LocalDate(2012, 6, 5), BigDecimal.valueOf(25L), callContext);
        recordUsageData(bpSubscription.getId(), "xxx-4", "stones", new LocalDate(2012, 6, 15), BigDecimal.valueOf(50L), callContext);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), new LocalDate(2012, 7, 1), InvoiceItemType.USAGE, new BigDecimal("100")));
        invoiceChecker.checkTrackingIds(curInvoice, Set.of("xxx-3", "xxx-4"), internalCallContext);

        final DateTime thirdExpectedCTD = account.getReferenceTime().withMonthOfYear(7).withDayOfMonth(1);
        assertEquals(subscriptionBaseInternalApiApi.getSubscriptionFromId(bpSubscription.getId(), internalCallContext).getChargedThroughDate().compareTo(thirdExpectedCTD), 0);
    }

    @Test(groups = "slow")
    public void testWithMultipleUsageSubscriptions() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        // Create subscription
        final DefaultEntitlement bp1 = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey1", "Trebuchet", ProductCategory.BASE, BillingPeriod.NO_BILLING_PERIOD, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        subscriptionChecker.checkSubscriptionCreated(bp1.getId(), internalCallContext);

        final DefaultEntitlement bp2 = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey2", "Trebuchet", ProductCategory.BASE, BillingPeriod.NO_BILLING_PERIOD, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        subscriptionChecker.checkSubscriptionCreated(bp2.getId(), internalCallContext);

        final List<UsageRecord> bp1StoneRecords1 = new ArrayList<>();
        bp1StoneRecords1.add(new UsageRecord(new LocalDate(2012, 4, 5).toDateTimeAtStartOfDay(), BigDecimal.valueOf(5L)));
        bp1StoneRecords1.add(new UsageRecord(new LocalDate(2012, 4, 15).toDateTimeAtStartOfDay(), BigDecimal.valueOf(10L)));
        bp1StoneRecords1.add(new UsageRecord(new LocalDate(2012, 4, 16).toDateTimeAtStartOfDay(), BigDecimal.valueOf(15L)));
        final SubscriptionUsageRecord bp1UsageRecord1 = new SubscriptionUsageRecord(bp1.getId(), "bp1-tracking-1", List.of(new UnitUsageRecord("stones", bp1StoneRecords1)));
        recordUsageData(bp1UsageRecord1, callContext);

        final List<UsageRecord> bp1StoneRecords2 = new ArrayList<>();
        bp1StoneRecords2.add(new UsageRecord(new LocalDate(2012, 4, 23).toDateTimeAtStartOfDay(), BigDecimal.valueOf(10L)));
        // Outside of range for this period -> Its tracking ID spreads across 2 invoices
        bp1StoneRecords2.add(new UsageRecord(new LocalDate(2012, 5, 1).toDateTimeAtStartOfDay(), BigDecimal.valueOf(101L)));
        final SubscriptionUsageRecord bp1UsageRecord2 = new SubscriptionUsageRecord(bp1.getId(), "bp1-tracking-2", List.of(new UnitUsageRecord("stones", bp1StoneRecords2)));
        recordUsageData(bp1UsageRecord2, callContext);

        final List<UsageRecord> bp2StoneRecords = new ArrayList<>();
        bp2StoneRecords.add(new UsageRecord(new LocalDate(2012, 4, 5).toDateTimeAtStartOfDay(), BigDecimal.valueOf(85L)));
        bp2StoneRecords.add(new UsageRecord(new LocalDate(2012, 4, 15).toDateTimeAtStartOfDay(), BigDecimal.valueOf(150L)));
        bp2StoneRecords.add(new UsageRecord(new LocalDate(2012, 4, 16).toDateTimeAtStartOfDay(), BigDecimal.valueOf(39L)));
        final SubscriptionUsageRecord bp2UsageRecord = new SubscriptionUsageRecord(bp2.getId(), "bp2-tracking-1", List.of(new UnitUsageRecord("stones", bp2StoneRecords)));
        recordUsageData(bp2UsageRecord, callContext);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.USAGE, new BigDecimal("100")),
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.USAGE, new BigDecimal("1000")));

        invoiceChecker.checkTrackingIds(curInvoice, Set.of("bp1-tracking-1", "bp1-tracking-2", "bp2-tracking-1"), internalCallContext);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.USAGE, new BigDecimal("0")),
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.USAGE, new BigDecimal("1000")));

        invoiceChecker.checkTrackingIds(curInvoice, Set.of("bp1-tracking-2"), internalCallContext);

    }

    @Test(groups = "slow")
    public void testWithVoidedInvoice() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccount(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        // Create subscription
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Trebuchet", ProductCategory.BASE, BillingPeriod.NO_BILLING_PERIOD, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        // Check bundle after BP got created otherwise we get an error from auditApi.
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);

        Assert.assertNull(bpSubscription.getSubscriptionBase().getChargedThroughDate());

        // Record usage for first month
        recordUsageData(bpSubscription.getId(), "xxx-1", "stones", new LocalDate(2012, 4, 5), BigDecimal.valueOf(85L), callContext);
        recordUsageData(bpSubscription.getId(), "xxx-2", "stones", new LocalDate(2012, 4, 15), BigDecimal.valueOf(150L), callContext);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT_ERROR);
        invoiceUserApi.triggerInvoiceGeneration(account.getId(),
                                                new LocalDate(2012, 5, 1),
                                                Collections.emptyList(),
                                                callContext);
        assertListenerStatus();

        final Invoice firstInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.USAGE, new BigDecimal("1000")));
        invoiceChecker.checkTrackingIds(firstInvoice, Set.of("xxx-1", "xxx-2"), internalCallContext);

        // Void the first invoice
        busHandler.pushExpectedEvents(NextEvent.INVOICE_ADJUSTMENT);
        invoiceUserApi.voidInvoice(firstInvoice.getId(), callContext);
        assertListenerStatus();
        invoiceChecker.checkTrackingIds(firstInvoice, Collections.emptySet(), internalCallContext);

        // Regenerate the invoice
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT_ERROR);
        invoiceUserApi.triggerInvoiceGeneration(account.getId(),
                                                new LocalDate(2012, 5, 1),
                                                Collections.emptyList(),
                                                callContext);
        assertListenerStatus();

        // Re-run checks
        final Invoice secondInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                                  new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.USAGE, new BigDecimal("1000")));
        assertNotEquals(firstInvoice.getId(), secondInvoice.getId());
        invoiceChecker.checkTrackingIds(secondInvoice, Set.of("xxx-1", "xxx-2"), internalCallContext);
    }

    @Test(groups = "slow")
    public void testWithPartialBlockBilling() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.ANNUAL, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        // Check bundle after BP got created otherwise we get an error from auditApi.
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));

        //
        // ADD ADD_ON ON THE SAME DAY
        //
        final DefaultEntitlement aoSubscription = addAOEntitlementAndCheckForCompletion(bpSubscription.getBundleId(), "Bullets", ProductCategory.ADD_ON, BillingPeriod.NO_BILLING_PERIOD, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);

        recordUsageData(aoSubscription.getId(), "tracking-1", "bullets", clock.getUTCNow(), BigDecimal.valueOf(99L), callContext);
        recordUsageData(aoSubscription.getId(), "tracking-2", "bullets", new LocalDate(2012, 4, 18), BigDecimal.valueOf(100L), callContext);

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        final Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                               new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2013, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")),
                                                               new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.USAGE, new BigDecimal("5.90")));
        invoiceChecker.checkTrackingIds(curInvoice, Set.of("tracking-1", "tracking-2"), internalCallContext);

        // 2012-04-21 -- block billing effective date is 2012-04-16
        clock.addDays(20);
        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.INVOICE);
        final BlockingState blockingState1 = new DefaultBlockingState(bpSubscription.getBundleId(), BlockingStateType.SUBSCRIPTION_BUNDLE, "state1", "Service", true, true, true, null);
        subscriptionApi.addBlockingState(blockingState1, new LocalDate(2012, 4, 16), Collections.emptyList(), callContext);
        assertListenerStatus();

        // Behavior notes:
        //  - We verify here that we don't double bill the usage for the period 2012-04-01 - 2012-04-16 (https://github.com/killbill/killbill/commit/b87b6ff567190c94b5ae9ad7da7e736cff4e37ef)
        //  - However, only the recurring portion is repaired, the usage recorded pre blocking is untouched (the data point post blocking from 2012-04-18 is still billed)
        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2013, 5, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-2399.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 21), new LocalDate(2012, 5, 21), InvoiceItemType.CBA_ADJ, new BigDecimal("2399.95")));
    }

    @Test(groups = "slow")
    public void testWithFullBlockBilling() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.ANNUAL, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        // Check bundle after BP got created otherwise we get an error from auditApi.
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));

        //
        // ADD ADD_ON ON THE SAME DAY
        //
        final DefaultEntitlement aoSubscription = addAOEntitlementAndCheckForCompletion(bpSubscription.getBundleId(), "Bullets", ProductCategory.ADD_ON, BillingPeriod.NO_BILLING_PERIOD, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);

        recordUsageData(aoSubscription.getId(), "tracking-1", "bullets", clock.getUTCNow(), BigDecimal.valueOf(99L), callContext);
        recordUsageData(aoSubscription.getId(), "tracking-2", "bullets", new LocalDate(2012, 4, 15), BigDecimal.valueOf(100L), callContext);

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        final Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                               new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2013, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")),
                                                               new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.USAGE, new BigDecimal("5.90")));
        invoiceChecker.checkTrackingIds(curInvoice, Set.of("tracking-1", "tracking-2"), internalCallContext);

        // 2012-05-21 -- block billing effective date is 2012-04-01
        clock.addDays(20);
        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.INVOICE);
        final BlockingState blockingState1 = new DefaultBlockingState(bpSubscription.getBundleId(), BlockingStateType.SUBSCRIPTION_BUNDLE, "state1", "Service", true, true, true, null);
        subscriptionApi.addBlockingState(blockingState1, new LocalDate(2012, 4, 1), Collections.emptyList(), callContext);
        assertListenerStatus();

        // Note that the usage is left untouched despite the blocking event
        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2013, 5, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-2399.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 21), new LocalDate(2012, 5, 21), InvoiceItemType.CBA_ADJ, new BigDecimal("2399.95")));

    }
}
