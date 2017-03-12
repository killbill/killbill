/*
 * Copyright 2010-2013 Ning, Inc.
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.beatrix.util.PaymentChecker.ExpectedPaymentCheck;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementActionPolicy;
import org.killbill.billing.invoice.InvoiceDispatcher.FutureAccountNotifications;
import org.killbill.billing.invoice.InvoiceDispatcher.FutureAccountNotifications.SubscriptionNotification;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.api.InvoiceStatus;
import org.killbill.billing.invoice.dao.InvoiceDao;
import org.killbill.billing.invoice.dao.InvoiceItemModelDao;
import org.killbill.billing.invoice.dao.InvoiceModelDao;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestIntegrationInvoiceWithRepairLogic extends TestIntegrationBase {

    @Inject
    protected InvoiceDao invoiceDao;


    @AfterMethod(groups = "slow")
    public void afterMethod() throws Exception {
        super.afterMethod();
    }

    @Test(groups = "slow")
    public void testSimplePartialRepairWithItemAdjustment() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        final LocalDate today = new LocalDate(2012, 4, 1);
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final String pricelistName = PriceListSet.DEFAULT_PRICELIST_NAME;

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //

        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
        assertEquals(invoices.size(), 1);
        ImmutableList<ExpectedInvoiceItemCheck> toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, BigDecimal.ZERO));
        invoiceChecker.checkInvoice(invoices.get(0).getId(), callContext, toBeChecked);

        //
        // Check we get the first invoice at the phase event
        //
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        // Move the clock to 2012-05-02
        clock.addDays(31);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
        assertEquals(invoices.size(), 2);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, BigDecimal.ZERO));
        invoiceChecker.checkInvoice(invoices.get(0).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        //
        // Adjust the recurring item
        //
        busHandler.pushExpectedEvents(NextEvent.INVOICE_ADJUSTMENT);
        invoiceUserApi.insertInvoiceItemAdjustment(account.getId(), invoices.get(1).getId(), invoices.get(1).getInvoiceItems().get(0).getId(), clock.getUTCToday(),
                                                   BigDecimal.TEN, account.getCurrency(), null, callContext);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
        assertEquals(invoices.size(), 2);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, BigDecimal.ZERO));
        invoiceChecker.checkInvoice(invoices.get(0).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("249.95")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 2), new LocalDate(2012, 5, 2), InvoiceItemType.ITEM_ADJ, new BigDecimal("-10")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 2), new LocalDate(2012, 5, 2), InvoiceItemType.CBA_ADJ, new BigDecimal("10")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        //
        // Force a plan change
        //
        changeEntitlementAndCheckForCompletion(bpEntitlement, "Blowdart", term, BillingActionPolicy.IMMEDIATE, NextEvent.CHANGE, NextEvent.INVOICE);
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
        assertEquals(invoices.size(), 3);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, BigDecimal.ZERO));
        invoiceChecker.checkInvoice(invoices.get(0).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("249.95")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 2), new LocalDate(2012, 5, 2), InvoiceItemType.ITEM_ADJ, new BigDecimal("-10")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 2), new LocalDate(2012, 5, 2), InvoiceItemType.CBA_ADJ, new BigDecimal("10")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 2), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("9.63")),
                // The pro-rated piece is ~ 249.95 - (249.95 / 31) ~ 241.88. However we adjusted the item so max available amount is  249.95 - 10 = 239.95.
                // So we consume all of it since max amount is less than 241.88.
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 2), new LocalDate(2012, 6, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-239.95")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 2), new LocalDate(2012, 5, 2), InvoiceItemType.CBA_ADJ, new BigDecimal("230.32")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, toBeChecked);

        checkNoMoreInvoiceToGenerate(account);
    }

    @Test(groups = "slow")
    public void testMultiplePartialRepairs() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        final LocalDate today = new LocalDate(2012, 4, 1);
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final String pricelistName = PriceListSet.DEFAULT_PRICELIST_NAME;

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //
        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
        assertEquals(invoices.size(), 1);
        ImmutableList<ExpectedInvoiceItemCheck> toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, BigDecimal.ZERO));
        invoiceChecker.checkInvoice(invoices.get(0).getId(), callContext, toBeChecked);

        // Move the clock to 2012-04-04
        clock.addDays(3);
        assertListenerStatus();

        //
        // Change plan in trial - there is no repair
        //
        changeEntitlementAndCheckForCompletion(bpEntitlement, "Assault-Rifle", term, BillingActionPolicy.IMMEDIATE, NextEvent.CHANGE, NextEvent.INVOICE);

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
        assertEquals(invoices.size(), 2);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, BigDecimal.ZERO));
        invoiceChecker.checkInvoice(invoices.get(0).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 4), null, InvoiceItemType.FIXED, BigDecimal.ZERO));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        //
        // Check we get the first invoice at the phase event
        //
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        // Move the clock to 2012-05-02
        clock.addDays(28);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
        assertEquals(invoices.size(), 3);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, BigDecimal.ZERO));
        invoiceChecker.checkInvoice(invoices.get(0).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 4), null, InvoiceItemType.FIXED, BigDecimal.ZERO));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("599.95")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, toBeChecked);

        //
        // Force another plan change
        //
        // Move the clock to 2012-05-07
        clock.addDays(5);
        changeEntitlementAndCheckForCompletion(bpEntitlement, "Blowdart", term, BillingActionPolicy.IMMEDIATE, NextEvent.CHANGE, NextEvent.INVOICE);

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
        assertEquals(invoices.size(), 4);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, BigDecimal.ZERO));
        invoiceChecker.checkInvoice(invoices.get(0).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 4), null, InvoiceItemType.FIXED, BigDecimal.ZERO));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("599.95")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 7), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("8.02")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 7), new LocalDate(2012, 6, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-483.83")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 7), new LocalDate(2012, 5, 7), InvoiceItemType.CBA_ADJ, new BigDecimal("475.81")));
        invoiceChecker.checkInvoice(invoices.get(3).getId(), callContext, toBeChecked);

        //
        // Force another plan change
        //
        // Move the clock to 2012-05-08
        clock.addDays(1);
        changeEntitlementAndCheckForCompletion(bpEntitlement, "Pistol", term, BillingActionPolicy.IMMEDIATE, NextEvent.CHANGE, NextEvent.INVOICE);

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
        assertEquals(invoices.size(), 5);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, BigDecimal.ZERO));
        invoiceChecker.checkInvoice(invoices.get(0).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 4), null, InvoiceItemType.FIXED, BigDecimal.ZERO));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("599.95")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 7), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("8.02")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 7), new LocalDate(2012, 6, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-483.83")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 7), new LocalDate(2012, 5, 7), InvoiceItemType.CBA_ADJ, new BigDecimal("475.81")));
        invoiceChecker.checkInvoice(invoices.get(3).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 8), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("23.19")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 8), new LocalDate(2012, 6, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-7.70")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 8), new LocalDate(2012, 5, 8), InvoiceItemType.CBA_ADJ, new BigDecimal("-15.49")));
        invoiceChecker.checkInvoice(invoices.get(4).getId(), callContext, toBeChecked);

        //
        // Verify the next bcd will happen as expected
        //
        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        // Move the clock to 2012-06-08
        clock.addMonths(1);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
        assertEquals(invoices.size(), 6);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, BigDecimal.ZERO));
        invoiceChecker.checkInvoice(invoices.get(0).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 4), null, InvoiceItemType.FIXED, BigDecimal.ZERO));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("599.95")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 7), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("8.02")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 7), new LocalDate(2012, 6, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-483.83")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 7), new LocalDate(2012, 5, 7), InvoiceItemType.CBA_ADJ, new BigDecimal("475.81")));
        invoiceChecker.checkInvoice(invoices.get(3).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 8), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("23.19")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 8), new LocalDate(2012, 6, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-7.70")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 8), new LocalDate(2012, 5, 8), InvoiceItemType.CBA_ADJ, new BigDecimal("-15.49")));
        invoiceChecker.checkInvoice(invoices.get(4).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), new LocalDate(2012, 7, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 8), new LocalDate(2012, 6, 8), InvoiceItemType.CBA_ADJ, new BigDecimal("-29.95")));
        invoiceChecker.checkInvoice(invoices.get(5).getId(), callContext, toBeChecked);

        //
        // Verify the next month as well
        //
        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        // Move the clock to 2012-07-08
        clock.addMonths(1);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
        assertEquals(invoices.size(), 7);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, BigDecimal.ZERO));
        invoiceChecker.checkInvoice(invoices.get(0).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 4), null, InvoiceItemType.FIXED, BigDecimal.ZERO));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("599.95")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 7), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("8.02")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 7), new LocalDate(2012, 6, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-483.83")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 7), new LocalDate(2012, 5, 7), InvoiceItemType.CBA_ADJ, new BigDecimal("475.81")));
        invoiceChecker.checkInvoice(invoices.get(3).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 8), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("23.19")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 8), new LocalDate(2012, 6, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-7.70")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 8), new LocalDate(2012, 5, 8), InvoiceItemType.CBA_ADJ, new BigDecimal("-15.49")));
        invoiceChecker.checkInvoice(invoices.get(4).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), new LocalDate(2012, 7, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 8), new LocalDate(2012, 6, 8), InvoiceItemType.CBA_ADJ, new BigDecimal("-29.95")));
        invoiceChecker.checkInvoice(invoices.get(5).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 1), new LocalDate(2012, 8, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 8), new LocalDate(2012, 7, 8), InvoiceItemType.CBA_ADJ, new BigDecimal("-29.95")));
        invoiceChecker.checkInvoice(invoices.get(6).getId(), callContext, toBeChecked);

        checkNoMoreInvoiceToGenerate(account);
    }

    @Test(groups = "slow")
    public void testPartialRepairWithCompleteRefund() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        final LocalDate today = new LocalDate(2012, 4, 1);
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDeltaFromReality(today.toDateTimeAtCurrentTime(DateTimeZone.UTC).getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.ANNUAL;
        final String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //
        DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);

        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext).size(), 1);

        assertEquals(bpEntitlement.getSubscriptionBase().getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.ANNUAL);

        // Move out of trials for interesting invoices adjustments
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
        assertEquals(invoices.size(), 2);
        ImmutableList<ExpectedInvoiceItemCheck> toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2013, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        //
        // FORCE AN IMMEDIATE CHANGE OF THE BILLING PERIOD
        //
        bpEntitlement = changeEntitlementAndCheckForCompletion(bpEntitlement, productName, BillingPeriod.MONTHLY, BillingActionPolicy.IMMEDIATE, NextEvent.CHANGE, NextEvent.INVOICE);

        assertEquals(bpEntitlement.getSubscriptionBase().getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.MONTHLY);

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
        assertEquals(invoices.size(), 3);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2013, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("249.95")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2013, 5, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-2399.95")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 5, 1), InvoiceItemType.CBA_ADJ, new BigDecimal("2150.")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, toBeChecked);

        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        clock.addMonths(1);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
        assertEquals(invoices.size(), 4);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2013, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("249.95")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2013, 5, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-2399.95")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 5, 1), InvoiceItemType.CBA_ADJ, new BigDecimal("2150.")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), new LocalDate(2012, 7, 1), InvoiceItemType.RECURRING, new BigDecimal("249.95")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), new LocalDate(2012, 6, 1), InvoiceItemType.CBA_ADJ, new BigDecimal("-249.95")));
        invoiceChecker.checkInvoice(invoices.get(3).getId(), callContext, toBeChecked);

        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        clock.addMonths(1);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
        assertEquals(invoices.size(), 5);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2013, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("249.95")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2013, 5, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-2399.95")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 5, 1), InvoiceItemType.CBA_ADJ, new BigDecimal("2150.")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), new LocalDate(2012, 7, 1), InvoiceItemType.RECURRING, new BigDecimal("249.95")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), new LocalDate(2012, 6, 1), InvoiceItemType.CBA_ADJ, new BigDecimal("-249.95")));
        invoiceChecker.checkInvoice(invoices.get(3).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 1), new LocalDate(2012, 8, 1), InvoiceItemType.RECURRING, new BigDecimal("249.95")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 1), new LocalDate(2012, 7, 1), InvoiceItemType.CBA_ADJ, new BigDecimal("-249.95")));
        invoiceChecker.checkInvoice(invoices.get(4).getId(), callContext, toBeChecked);

        checkNoMoreInvoiceToGenerate(account);
    }

    @Test(groups = "slow")
    public void testRepairWithFullItemAdjustment() throws Exception {
        final LocalDate today = new LocalDate(2013, 7, 19);
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDeltaFromReality(today.toDateTimeAtCurrentTime(DateTimeZone.UTC).getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.ANNUAL;
        final String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //
        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext).size(), 1);

        assertEquals(bpEntitlement.getSubscriptionBase().getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.ANNUAL);

        // Move out of trials for interesting invoices adjustments
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
        assertEquals(invoices.size(), 2);
        ImmutableList<ExpectedInvoiceItemCheck> toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 8, 18), new LocalDate(2014, 8, 18), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        // Move clock to 2013-09-17
        clock.addDays(30);
        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.INVOICE);
        bpEntitlement.cancelEntitlementWithPolicyOverrideBillingPolicy(EntitlementActionPolicy.IMMEDIATE, BillingActionPolicy.IMMEDIATE, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
        assertEquals(invoices.size(), 3);
        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 8, 18), new LocalDate(2014, 8, 18), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 9, 17), new LocalDate(2014, 8, 18), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-2202.69")),
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 9, 17), new LocalDate(2013, 9, 17), InvoiceItemType.CBA_ADJ, new BigDecimal("2202.69")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, toBeChecked);

        //
        // ITEM ADJUSTMENT PRIOR TO DOING THE REPAIR
        //
        final Invoice invoice1 = invoices.get(1);
        final List<Payment> payments = paymentApi.getAccountPayments(account.getId(), false, false, ImmutableList.<PluginProperty>of(), callContext);
        final ExpectedPaymentCheck expectedPaymentCheck = new ExpectedPaymentCheck(clock.getUTCNow().toLocalDate(), new BigDecimal("2399.95"), TransactionStatus.SUCCESS, invoice1.getId(), Currency.USD);
        final Payment payment1 = payments.get(0);

        final Map<UUID, BigDecimal> iias = new HashMap<UUID, BigDecimal>();
        iias.put(invoice1.getInvoiceItems().get(0).getId(), new BigDecimal("197.26"));
        refundPaymentWithInvoiceItemAdjAndCheckForCompletion(account, payment1, iias, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT, NextEvent.INVOICE_ADJUSTMENT);
        checkNoMoreInvoiceToGenerate(account);
    }

    //
    // This is the exact same test as testRepairWithFullItemAdjustment except we now only do a partial item adjustment.
    // The invoice code will NOT reinvoice for the remaining part as the item was both repaired and adjusted and so
    // it does not have enough info to understand what should be re-invoiced.
    //
    // Note that there is no real use case for those scenarii in real life
    //
    @Test(groups = "slow")
    public void testRepairWithPartialItemAdjustment() throws Exception {
        final LocalDate today = new LocalDate(2013, 7, 19);
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDeltaFromReality(today.toDateTimeAtCurrentTime(DateTimeZone.UTC).getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.ANNUAL;
        final String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //
        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext).size(), 1);

        assertEquals(bpEntitlement.getSubscriptionBase().getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.ANNUAL);

        // Move out of trials for interesting invoices adjustments
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
        assertEquals(invoices.size(), 2);
        ImmutableList<ExpectedInvoiceItemCheck> toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 8, 18), new LocalDate(2014, 8, 18), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        // Move clock to 2013-09-17
        clock.addDays(30);
        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.INVOICE);
        bpEntitlement.cancelEntitlementWithPolicyOverrideBillingPolicy(EntitlementActionPolicy.IMMEDIATE, BillingActionPolicy.IMMEDIATE, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
        assertEquals(invoices.size(), 3);
        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 8, 18), new LocalDate(2014, 8, 18), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 9, 17), new LocalDate(2014, 8, 18), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-2202.69")),
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 9, 17), new LocalDate(2013, 9, 17), InvoiceItemType.CBA_ADJ, new BigDecimal("2202.69")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, toBeChecked);

        //
        // ITEM ADJUSTMENT PRIOR TO DOING THE REPAIR
        //
        final Invoice invoice1 = invoices.get(1);
        final List<Payment> payments = paymentApi.getAccountPayments(account.getId(), false, false, ImmutableList.<PluginProperty>of(), callContext);
        final ExpectedPaymentCheck expectedPaymentCheck = new ExpectedPaymentCheck(clock.getUTCNow().toLocalDate(), new BigDecimal("2399.95"), TransactionStatus.SUCCESS, invoice1.getId(), Currency.USD);
        final Payment payment1 = payments.get(0);

        final Map<UUID, BigDecimal> iias = new HashMap<UUID, BigDecimal>();
        iias.put(invoice1.getInvoiceItems().get(0).getId(), new BigDecimal("100.00"));
        refundPaymentWithInvoiceItemAdjAndCheckForCompletion(account, payment1, iias, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT, NextEvent.INVOICE_ADJUSTMENT);
        checkNoMoreInvoiceToGenerate(account);
    }

    @Test(groups = "slow")
    public void testWithSuperflousRepairedItems() throws Exception {

        // We take april as it has 30 days (easier to play with BCD)
        final LocalDate today = new LocalDate(2012, 4, 1);
        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(today);

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final String pricelistName = PriceListSet.DEFAULT_PRICELIST_NAME;

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE NextEvent.INVOICE
        //

        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.INVOICE, NextEvent.BLOCK);
        assertNotNull(bpEntitlement);

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
        assertEquals(invoices.size(), 1);
        ImmutableList<ExpectedInvoiceItemCheck> toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, BigDecimal.ZERO));
        invoiceChecker.checkInvoice(invoices.get(0).getId(), callContext, toBeChecked);

        //
        // Check we get the first invoice at the phase event
        //
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        // Move the clock to 2012-05-02
        clock.addDays(31);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
        assertEquals(invoices.size(), 2);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, BigDecimal.ZERO));
        invoiceChecker.checkInvoice(invoices.get(0).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        final Invoice lastInvoice = invoices.get(1);
        invoiceChecker.checkInvoice(lastInvoice.getId(), callContext, toBeChecked);


        //
        // Let's add a bunch of items by hand to pretend we have lots of cancelling items that should be cleaned (data issue after a potential invoice bug)
        //
        // We test both full and partial repair.

        /*
        final UUID id, @Nullable final DateTime createdDate, final UUID accountId,
                           @Nullable final Integer invoiceNumber, final LocalDate invoiceDate, final LocalDate targetDate,
                           final Currency currency, final boolean migrated, final InvoiceStatus status, final boolean isParentInvoice
         */
        final InvoiceModelDao shellInvoice = new InvoiceModelDao(UUID.randomUUID(), lastInvoice.getCreatedDate(), lastInvoice.getAccountId(), null,
                                                                 lastInvoice.getInvoiceDate(), lastInvoice.getTargetDate(), lastInvoice.getCurrency(), false, InvoiceStatus.COMMITTED, false);

        final InvoiceItemModelDao recurring1 = new InvoiceItemModelDao(lastInvoice.getCreatedDate(), InvoiceItemType.RECURRING, lastInvoice.getId(), lastInvoice.getAccountId(),
                                                                       bpEntitlement.getBundleId(), bpEntitlement.getBaseEntitlementId(), "", "shotgun-monthly", "shotgun-monthly-evergreen",
                                                                       null, new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), new BigDecimal("249.95"), new BigDecimal("249.95"), account.getCurrency(), null);

        final InvoiceItemModelDao repair1 = new InvoiceItemModelDao(lastInvoice.getCreatedDate(), InvoiceItemType.REPAIR_ADJ, lastInvoice.getId(), lastInvoice.getAccountId(),
                                                                    bpEntitlement.getBundleId(), bpEntitlement.getBaseEntitlementId(), null, null, null,
                                                                    null, new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), new BigDecimal("-249.95"), new BigDecimal("-249.95"), account.getCurrency(), recurring1.getId());

        final InvoiceItemModelDao recurring2 = new InvoiceItemModelDao(lastInvoice.getCreatedDate(), InvoiceItemType.RECURRING, lastInvoice.getId(), lastInvoice.getAccountId(),
                                                                       bpEntitlement.getBundleId(), bpEntitlement.getBaseEntitlementId(), "", "shotgun-monthly", "shotgun-monthly-evergreen",
                                                                       null, new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), new BigDecimal("249.95"), new BigDecimal("249.95"), account.getCurrency(), null);


        final InvoiceItemModelDao repair21 = new InvoiceItemModelDao(lastInvoice.getCreatedDate(), InvoiceItemType.REPAIR_ADJ, lastInvoice.getId(), lastInvoice.getAccountId(),
                                                                     bpEntitlement.getBundleId(), bpEntitlement.getBaseEntitlementId(), null, null, null,
                                                                     null, new LocalDate(2012, 5, 1), new LocalDate(2012, 5, 13), new BigDecimal("-100.95"), new BigDecimal("-100.95"), account.getCurrency(), recurring2.getId());

        final InvoiceItemModelDao repair22 = new InvoiceItemModelDao(lastInvoice.getCreatedDate(), InvoiceItemType.REPAIR_ADJ, lastInvoice.getId(), lastInvoice.getAccountId(),
                                                                     bpEntitlement.getBundleId(), bpEntitlement.getBaseEntitlementId(), null, null, null,
                                                                     null, new LocalDate(2012, 5, 13), new LocalDate(2012, 5, 22), new BigDecimal("-100"), new BigDecimal("-100"), account.getCurrency(), recurring2.getId());

        final InvoiceItemModelDao repair23 = new InvoiceItemModelDao(lastInvoice.getCreatedDate(), InvoiceItemType.REPAIR_ADJ, lastInvoice.getId(), lastInvoice.getAccountId(),
                                                                     bpEntitlement.getBundleId(), bpEntitlement.getBaseEntitlementId(), null, null, null,
                                                                     null, new LocalDate(2012, 5, 22), new LocalDate(2012, 6, 1), new BigDecimal("-49"), new BigDecimal("-49"), account.getCurrency(), recurring2.getId());



        final InvoiceItemModelDao recurring3 = new InvoiceItemModelDao(lastInvoice.getCreatedDate(), InvoiceItemType.RECURRING, lastInvoice.getId(), lastInvoice.getAccountId(),
                                                                       bpEntitlement.getBundleId(), bpEntitlement.getBaseEntitlementId(), "", "shotgun-monthly", "shotgun-monthly-evergreen",
                                                                       null, new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), new BigDecimal("249.95"), new BigDecimal("249.95"), account.getCurrency(), null);


        final InvoiceItemModelDao repair3 = new InvoiceItemModelDao(lastInvoice.getCreatedDate(), InvoiceItemType.REPAIR_ADJ, lastInvoice.getId(), lastInvoice.getAccountId(),
                                                                    bpEntitlement.getBundleId(), bpEntitlement.getBaseEntitlementId(), null, null, null,
                                                                    null, new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), new BigDecimal("-249.95"), new BigDecimal("-249.95"), account.getCurrency(), recurring3.getId());

        List<InvoiceItemModelDao> newItems = new ArrayList<InvoiceItemModelDao>();
        newItems.add(recurring1);
        newItems.add(repair1);
        newItems.add(recurring2);
        newItems.add(repair21);
        newItems.add(repair22);
        newItems.add(repair23);
        newItems.add(recurring3);
        newItems.add(repair3);
        shellInvoice.addInvoiceItems(newItems);
        invoiceDao.createInvoice(shellInvoice, new FutureAccountNotifications(new HashMap<UUID, List<SubscriptionNotification>>()), internalCallContext);


        // Move ahead one month, verify nothing from previous data was generated
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
        assertEquals(invoices.size(), 3);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), new LocalDate(2012, 7, 1), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, toBeChecked);



    }

}
