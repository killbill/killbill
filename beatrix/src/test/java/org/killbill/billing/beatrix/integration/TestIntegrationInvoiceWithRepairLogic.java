/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.beatrix.util.PaymentChecker.ExpectedPaymentCheck;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementActionPolicy;
import org.killbill.billing.invoice.InvoiceDispatcher.FutureAccountNotifications;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.api.InvoiceStatus;
import org.killbill.billing.invoice.dao.InvoiceDao;
import org.killbill.billing.invoice.dao.InvoiceItemModelDao;
import org.killbill.billing.invoice.dao.InvoiceModelDao;
import org.killbill.billing.invoice.dao.InvoiceTrackingModelDao;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestIntegrationInvoiceWithRepairLogic extends TestIntegrationBase {

    @Inject
    protected InvoiceDao invoiceDao;

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

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
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

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
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
                                                   BigDecimal.TEN, account.getCurrency(), null, null, null, callContext);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
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
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
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

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
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

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
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

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
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

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
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

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
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

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
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

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
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
    public void testPartialRepairThroughBillingPeriodChange() throws Exception {
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

        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext).size(), 1);

        assertEquals(bpEntitlement.getSubscriptionBase().getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.ANNUAL);

        // Move out of trials for interesting invoices adjustments
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 2);
        ImmutableList<ExpectedInvoiceItemCheck> toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2013, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        //
        // FORCE AN IMMEDIATE CHANGE OF THE BILLING PERIOD
        //
        bpEntitlement = changeEntitlementAndCheckForCompletion(bpEntitlement, productName, BillingPeriod.MONTHLY, BillingActionPolicy.IMMEDIATE, NextEvent.CHANGE, NextEvent.INVOICE);

        assertEquals(bpEntitlement.getSubscriptionBase().getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.MONTHLY);

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
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

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
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

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
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
    public void testRepairWithFullRemainingItemAdjustment() throws Exception {
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
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext).size(), 1);

        assertEquals(bpEntitlement.getSubscriptionBase().getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.ANNUAL);

        // Move out of trials for interesting invoices adjustments
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 2);
        ImmutableList<ExpectedInvoiceItemCheck> toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 8, 18), new LocalDate(2014, 8, 18), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        // Move clock to 2013-09-17
        clock.addDays(30);
        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.INVOICE);
        bpEntitlement.cancelEntitlementWithPolicyOverrideBillingPolicy(EntitlementActionPolicy.IMMEDIATE, BillingActionPolicy.IMMEDIATE, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 3);
        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 8, 18), new LocalDate(2014, 8, 18), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 9, 17), new LocalDate(2014, 8, 18), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-2202.69")),
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 9, 17), new LocalDate(2013, 9, 17), InvoiceItemType.CBA_ADJ, new BigDecimal("2202.69")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, toBeChecked);

        //
        // ITEM ADJUSTMENT AFTER TO DOING THE REPAIR
        //
        final Invoice invoice1 = invoices.get(1);
        final List<Payment> payments = paymentApi.getAccountPayments(account.getId(), false, false, ImmutableList.<PluginProperty>of(), callContext);
        final ExpectedPaymentCheck expectedPaymentCheck = new ExpectedPaymentCheck(clock.getUTCNow().toLocalDate(), new BigDecimal("2399.95"), TransactionStatus.SUCCESS, invoice1.getId(), Currency.USD);
        final Payment payment1 = payments.get(0);

        final Map<UUID, BigDecimal> iias = new HashMap<UUID, BigDecimal>();
        iias.put(invoice1.getInvoiceItems().get(0).getId(), new BigDecimal("197.26"));
        refundPaymentWithInvoiceItemAdjAndCheckForCompletion(account, payment1, iias, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT, NextEvent.INVOICE_ADJUSTMENT);
        checkNoMoreInvoiceToGenerate(account);

        final BigDecimal accountBalance = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertEquals(accountBalance.compareTo(BigDecimal.ZERO), 0);
    }

    @Test(groups = "slow")
    public void testPartialItemAdjFollowedWithRepair() throws Exception {
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
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext).size(), 1);

        assertEquals(bpEntitlement.getSubscriptionBase().getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.ANNUAL);

        // Move out of trials for interesting invoices adjustments
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 2);
        ImmutableList<ExpectedInvoiceItemCheck> toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 8, 18), new LocalDate(2014, 8, 18), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

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

        // Move clock to 2013-09-17
        clock.addDays(30);
        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.INVOICE, NextEvent.INVOICE_ADJUSTMENT);
        bpEntitlement.cancelEntitlementWithPolicyOverrideBillingPolicy(EntitlementActionPolicy.IMMEDIATE, BillingActionPolicy.IMMEDIATE, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 3);
        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 8, 18), new LocalDate(2014, 8, 18), InvoiceItemType.RECURRING, new BigDecimal("2399.95")),
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 8, 18), new LocalDate(2013, 8, 18), InvoiceItemType.ITEM_ADJ, new BigDecimal("-197.26")),
                // CBA rebalanced from invoice 3
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 9, 17), new LocalDate(2013, 9, 17), InvoiceItemType.CBA_ADJ, new BigDecimal("-2202.69"))
                                                                );
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 9, 17), new LocalDate(2014, 8, 18), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-2202.69")),
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 9, 17), new LocalDate(2013, 9, 17), InvoiceItemType.CBA_ADJ, new BigDecimal("2202.69")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, toBeChecked);

        final BigDecimal accountBalance = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertEquals(accountBalance.compareTo(BigDecimal.ZERO), 0);
    }

    //
    // This is the exact same test as testRepairWithFullRemainingItemAdjustment except we now only do a partial item adjustment.
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
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext).size(), 1);

        assertEquals(bpEntitlement.getSubscriptionBase().getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.ANNUAL);

        // Move out of trials for interesting invoices adjustments
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 2);
        ImmutableList<ExpectedInvoiceItemCheck> toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 8, 18), new LocalDate(2014, 8, 18), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        // Move clock to 2013-09-17
        clock.addDays(30);
        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.INVOICE);
        bpEntitlement.cancelEntitlementWithPolicyOverrideBillingPolicy(EntitlementActionPolicy.IMMEDIATE, BillingActionPolicy.IMMEDIATE, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 3);
        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 8, 18), new LocalDate(2014, 8, 18), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 9, 17), new LocalDate(2014, 8, 18), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-2202.69")),
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 9, 17), new LocalDate(2013, 9, 17), InvoiceItemType.CBA_ADJ, new BigDecimal("2202.69")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, toBeChecked);

        //
        // ITEM ADJUSTMENT AFTER TO DOING THE REPAIR
        //
        final Invoice invoice1 = invoices.get(1);
        final List<Payment> payments = paymentApi.getAccountPayments(account.getId(), false, false, ImmutableList.<PluginProperty>of(), callContext);
        final ExpectedPaymentCheck expectedPaymentCheck = new ExpectedPaymentCheck(clock.getUTCNow().toLocalDate(), new BigDecimal("2399.95"), TransactionStatus.SUCCESS, invoice1.getId(), Currency.USD);
        final Payment payment1 = payments.get(0);

        final Map<UUID, BigDecimal> iias = new HashMap<UUID, BigDecimal>();
        iias.put(invoice1.getInvoiceItems().get(0).getId(), new BigDecimal("100.00"));
        refundPaymentWithInvoiceItemAdjAndCheckForCompletion(account, payment1, iias, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT, NextEvent.INVOICE_ADJUSTMENT);
        checkNoMoreInvoiceToGenerate(account);

        final BigDecimal accountBalance = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertEquals(accountBalance.compareTo(new BigDecimal("97.26")), 0);

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

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
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

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
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
                                                                       bpEntitlement.getBundleId(), bpEntitlement.getBaseEntitlementId(), "", "Shotgun", "shotgun-monthly", "shotgun-monthly-evergreen", null,
                                                                       null, new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), new BigDecimal("249.95"), new BigDecimal("249.95"), account.getCurrency(), null);

        final InvoiceItemModelDao repair1 = new InvoiceItemModelDao(lastInvoice.getCreatedDate(), InvoiceItemType.REPAIR_ADJ, lastInvoice.getId(), lastInvoice.getAccountId(),
                                                                    bpEntitlement.getBundleId(), bpEntitlement.getBaseEntitlementId(), null, null, null, null, null,
                                                                    null, new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), new BigDecimal("-249.95"), new BigDecimal("-249.95"), account.getCurrency(), recurring1.getId());

        final InvoiceItemModelDao recurring2 = new InvoiceItemModelDao(lastInvoice.getCreatedDate(), InvoiceItemType.RECURRING, lastInvoice.getId(), lastInvoice.getAccountId(),
                                                                       bpEntitlement.getBundleId(), bpEntitlement.getBaseEntitlementId(), "", "Shotgun", "shotgun-monthly", "shotgun-monthly-evergreen", null,
                                                                       null, new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), new BigDecimal("249.95"), new BigDecimal("249.95"), account.getCurrency(), null);

        final InvoiceItemModelDao repair21 = new InvoiceItemModelDao(lastInvoice.getCreatedDate(), InvoiceItemType.REPAIR_ADJ, lastInvoice.getId(), lastInvoice.getAccountId(),
                                                                     bpEntitlement.getBundleId(), bpEntitlement.getBaseEntitlementId(), null, null, null, null, null,
                                                                     null, new LocalDate(2012, 5, 1), new LocalDate(2012, 5, 13), new BigDecimal("-100.95"), new BigDecimal("-100.95"), account.getCurrency(), recurring2.getId());

        final InvoiceItemModelDao repair22 = new InvoiceItemModelDao(lastInvoice.getCreatedDate(), InvoiceItemType.REPAIR_ADJ, lastInvoice.getId(), lastInvoice.getAccountId(),
                                                                     bpEntitlement.getBundleId(), bpEntitlement.getBaseEntitlementId(), null, null, null, null, null,
                                                                     null, new LocalDate(2012, 5, 13), new LocalDate(2012, 5, 22), new BigDecimal("-100"), new BigDecimal("-100"), account.getCurrency(), recurring2.getId());

        final InvoiceItemModelDao repair23 = new InvoiceItemModelDao(lastInvoice.getCreatedDate(), InvoiceItemType.REPAIR_ADJ, lastInvoice.getId(), lastInvoice.getAccountId(),
                                                                     bpEntitlement.getBundleId(), bpEntitlement.getBaseEntitlementId(), null, null, null, null, null,
                                                                     null, new LocalDate(2012, 5, 22), new LocalDate(2012, 6, 1), new BigDecimal("-49"), new BigDecimal("-49"), account.getCurrency(), recurring2.getId());

        final InvoiceItemModelDao recurring3 = new InvoiceItemModelDao(lastInvoice.getCreatedDate(), InvoiceItemType.RECURRING, lastInvoice.getId(), lastInvoice.getAccountId(),
                                                                       bpEntitlement.getBundleId(), bpEntitlement.getBaseEntitlementId(), "", "Shotgun", "shotgun-monthly", "shotgun-monthly-evergreen", null,
                                                                       null, new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), new BigDecimal("249.95"), new BigDecimal("249.95"), account.getCurrency(), null);

        final InvoiceItemModelDao repair3 = new InvoiceItemModelDao(lastInvoice.getCreatedDate(), InvoiceItemType.REPAIR_ADJ, lastInvoice.getId(), lastInvoice.getAccountId(),
                                                                    bpEntitlement.getBundleId(), bpEntitlement.getBaseEntitlementId(), null, null, null, null, null,
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
        invoiceDao.createInvoice(shellInvoice, null, ImmutableSet.of(), new FutureAccountNotifications(), null, internalCallContext);

        // Move ahead one month, verify nothing from previous data was generated
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 3);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), new LocalDate(2012, 7, 1), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, toBeChecked);

    }

    //
    //    Full item adjustments scenarios
    //
    //    Case V1: Cancellation SOT => Balance is $0 and nothing to regenerate
    //    Case V2: Cancellation IMM => Balance is $0 and regenerate the new piece (proposed item)
    //    Case V3: Cancellation EOT => Balance is $0 and nothing to regenerate  **** Our weird behavior ov V3 behaving like V1 ****

    @Test(groups = "slow")
    public void testRepairWithFullItemAdjustmentV1() throws Exception {

        DefaultEntitlement bpEntitlement = setupTestRepairWithFullItemAdjustment();

        // Move clock to 2013-09-17
        clock.addDays(30);
        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.INVOICE);
        bpEntitlement.cancelEntitlementWithPolicyOverrideBillingPolicy(EntitlementActionPolicy.IMMEDIATE, BillingActionPolicy.START_OF_TERM, ImmutableList.<PluginProperty>of(), callContext);

        assertListenerStatus();

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(bpEntitlement.getAccountId(), false, false, callContext);
        assertEquals(invoices.size(), 3);
        ImmutableList<ExpectedInvoiceItemCheck> toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 8, 18), new LocalDate(2014, 8, 18), InvoiceItemType.RECURRING, new BigDecimal("2399.95")),
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 8, 18), new LocalDate(2013, 8, 18), InvoiceItemType.ITEM_ADJ, new BigDecimal("-2399.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 8, 18), new LocalDate(2014, 8, 18), InvoiceItemType.REPAIR_ADJ, BigDecimal.ZERO));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, toBeChecked);

        final BigDecimal accountBalance = invoiceUserApi.getAccountBalance(bpEntitlement.getAccountId(), callContext);
        assertEquals(accountBalance.compareTo(BigDecimal.ZERO), 0);
    }

    @Test(groups = "slow")
    public void testRepairWithFullItemAdjustmentV2() throws Exception {

        DefaultEntitlement bpEntitlement = setupTestRepairWithFullItemAdjustment();

        ////
        // Move clock to 2013-09-17
        clock.addDays(30);
        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.INVOICE);
        bpEntitlement.cancelEntitlementWithPolicyOverrideBillingPolicy(EntitlementActionPolicy.IMMEDIATE, BillingActionPolicy.IMMEDIATE, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(bpEntitlement.getAccountId(), false, false, callContext);
        assertEquals(invoices.size(), 3);
        ImmutableList<ExpectedInvoiceItemCheck> toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 8, 18), new LocalDate(2014, 8, 18), InvoiceItemType.RECURRING, new BigDecimal("2399.95")),
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 8, 18), new LocalDate(2013, 8, 18), InvoiceItemType.ITEM_ADJ, new BigDecimal("-2399.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 9, 17), new LocalDate(2014, 8, 18), InvoiceItemType.REPAIR_ADJ, BigDecimal.ZERO));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, toBeChecked);

        final BigDecimal accountBalance = invoiceUserApi.getAccountBalance(bpEntitlement.getAccountId(), callContext);
        assertEquals(accountBalance.compareTo(BigDecimal.ZERO), 0);
    }

    //
    // Same scenario as previous one but we insert a RECURRING for 2013-8-18 -> 2013-9-17 to simulate old full item adjustment behavior
    //
    @Test(groups = "slow")
    public void testRepairWithFullItemAdjustmentV2WithOldData() throws Exception {

        DefaultEntitlement bpEntitlement = setupTestRepairWithFullItemAdjustment();

        final InvoiceModelDao invoiceForPreviousBehavior = new InvoiceModelDao(UUID.randomUUID(), clock.getUTCNow(), bpEntitlement.getAccountId(), null, new LocalDate(2013, 9, 17), new LocalDate(2013, 9, 17), Currency.USD, false, InvoiceStatus.COMMITTED, false);

        invoiceForPreviousBehavior.addInvoiceItem(new InvoiceItemModelDao(UUID.randomUUID(), clock.getUTCNow(), InvoiceItemType.RECURRING, invoiceForPreviousBehavior.getId(), bpEntitlement.getAccountId(), null, null, bpEntitlement.getId(), "",
                                                                          "Shotgun", "shotgun-annual", "shotgun-annual-evergreen", null, null,
                                                                          new LocalDate(2013, 8, 18), new LocalDate(2013, 9, 17),
                                                                          new BigDecimal("197.26"), new BigDecimal("2399.95"), Currency.USD, null, null, null));

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        insertInvoiceItems(invoiceForPreviousBehavior);
        assertListenerStatus();

        //
        // Move clock to 2013-09-17
        clock.addDays(30);
        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.NULL_INVOICE);
        bpEntitlement.cancelEntitlementWithPolicyOverrideBillingPolicy(EntitlementActionPolicy.IMMEDIATE, BillingActionPolicy.IMMEDIATE, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(bpEntitlement.getAccountId(), false, false, callContext);
        assertEquals(invoices.size(), 3);
        ImmutableList<ExpectedInvoiceItemCheck> toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 8, 18), new LocalDate(2014, 8, 18), InvoiceItemType.RECURRING, new BigDecimal("2399.95")),
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 8, 18), new LocalDate(2013, 8, 18), InvoiceItemType.ITEM_ADJ, new BigDecimal("-2399.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        // This is the invoice + item we inserted by hand to simulate the old data; we verify there is nothing more
        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 8, 18), new LocalDate(2013, 9, 17), InvoiceItemType.RECURRING, new BigDecimal("197.26")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, toBeChecked);

        final BigDecimal accountBalance = invoiceUserApi.getAccountBalance(bpEntitlement.getAccountId(), callContext);
        assertEquals(accountBalance.compareTo(BigDecimal.ZERO), 0);

        checkNoMoreInvoiceToGenerate(bpEntitlement.getAccountId());
    }

    @Test(groups = "slow")
    public void testRepairWithFullItemAdjustmentV3() throws Exception {

        DefaultEntitlement bpEntitlement = setupTestRepairWithFullItemAdjustment();

        // Move clock to 2013-09-17
        clock.addDays(30);
        busHandler.pushExpectedEvents(NextEvent.BLOCK);
        bpEntitlement.cancelEntitlementWithPolicyOverrideBillingPolicy(EntitlementActionPolicy.IMMEDIATE, BillingActionPolicy.END_OF_TERM, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(bpEntitlement.getAccountId(), false, false, callContext);
        assertEquals(invoices.size(), 2);
        ImmutableList<ExpectedInvoiceItemCheck> toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 8, 18), new LocalDate(2014, 8, 18), InvoiceItemType.RECURRING, new BigDecimal("2399.95")),
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 8, 18), new LocalDate(2013, 8, 18), InvoiceItemType.ITEM_ADJ, new BigDecimal("-2399.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        final BigDecimal accountBalance = invoiceUserApi.getAccountBalance(bpEntitlement.getAccountId(), callContext);
        assertEquals(accountBalance.compareTo(BigDecimal.ZERO), 0);

    }

    private DefaultEntitlement setupTestRepairWithFullItemAdjustment() throws Exception {
        final LocalDate today = new LocalDate(2013, 7, 19);
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDeltaFromReality(today.toDateTimeAtCurrentTime(DateTimeZone.UTC).getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.ANNUAL;

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //
        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext).size(), 1);

        assertEquals(bpEntitlement.getSubscriptionBase().getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.ANNUAL);

        // Move out of trials for interesting invoices adjustments
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 2);
        ImmutableList<ExpectedInvoiceItemCheck> toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 8, 18), new LocalDate(2014, 8, 18), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        //
        // FULL ITEM ADJUSTMENT PRIOR TO DOING THE REPAIR
        //
        final Invoice invoice1 = invoices.get(1);
        final List<Payment> payments = paymentApi.getAccountPayments(account.getId(), false, false, ImmutableList.<PluginProperty>of(), callContext);
        final Payment payment1 = payments.get(0);

        final Map<UUID, BigDecimal> iias = new HashMap<UUID, BigDecimal>();
        iias.put(invoice1.getInvoiceItems().get(0).getId(), null);
        refundPaymentWithInvoiceItemAdjAndCheckForCompletion(account, payment1, iias, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT, NextEvent.INVOICE_ADJUSTMENT);
        checkNoMoreInvoiceToGenerate(account);

        return bpEntitlement;
    }
    private void insertInvoiceItems(final InvoiceModelDao invoice) {
        final FutureAccountNotifications callbackDateTimePerSubscriptions = new FutureAccountNotifications();
        invoiceDao.createInvoice(invoice, null, ImmutableSet.<InvoiceTrackingModelDao>of(), callbackDateTimePerSubscriptions, null, internalCallContext);
    }


    @Test(groups = "slow")
    public void testRepairWithFullItemAdjustmentInParts() throws Exception {


        final LocalDate today = new LocalDate(2013, 7, 19);
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDeltaFromReality(today.toDateTimeAtCurrentTime(DateTimeZone.UTC).getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.ANNUAL;

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //
        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext).size(), 1);

        assertEquals(bpEntitlement.getSubscriptionBase().getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.ANNUAL);

        // Move out of trials for interesting invoices adjustments
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 2);
        ImmutableList<ExpectedInvoiceItemCheck> toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 8, 18), new LocalDate(2014, 8, 18), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        //
        // FULL ITEM ADJUSTMENT PRIOR TO DOING THE REPAIR
        //
        final Invoice invoice1 = invoices.get(1);
        final List<Payment> payments = paymentApi.getAccountPayments(account.getId(), false, false, ImmutableList.<PluginProperty>of(), callContext);
        final Payment payment1 = payments.get(0);

        final Map<UUID, BigDecimal> iias = new HashMap<UUID, BigDecimal>();
        final UUID originalRecurringId = invoice1.getInvoiceItems().get(0).getId();
        iias.put(originalRecurringId, new BigDecimal("1000"));
        refundPaymentWithInvoiceItemAdjAndCheckForCompletion(account, payment1, iias, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT, NextEvent.INVOICE_ADJUSTMENT);
        checkNoMoreInvoiceToGenerate(account);


        busHandler.pushExpectedEvents(NextEvent.INVOICE_ADJUSTMENT);
        invoiceUserApi.insertInvoiceItemAdjustment(account.getId(), invoice1.getId(), originalRecurringId, new LocalDate(2013, 8, 18), new BigDecimal("1000"), account.getCurrency(), "", "", ImmutableList.of(), callContext);
        assertListenerStatus();

        busHandler.pushExpectedEvents(NextEvent.INVOICE_ADJUSTMENT);
        invoiceUserApi.insertInvoiceItemAdjustment(account.getId(), invoice1.getId(), originalRecurringId, new LocalDate(2013, 8, 18), "", "", ImmutableList.of(), callContext);
        assertListenerStatus();

        // Move clock to 2013-09-17
        clock.addDays(30);
        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.INVOICE);
        bpEntitlement.cancelEntitlementWithPolicyOverrideBillingPolicy(EntitlementActionPolicy.IMMEDIATE, BillingActionPolicy.IMMEDIATE, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(bpEntitlement.getAccountId(), false, false, callContext);
        assertEquals(invoices.size(), 3);
        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 8, 18), new LocalDate(2014, 8, 18), InvoiceItemType.RECURRING, new BigDecimal("2399.95")),
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 8, 18), new LocalDate(2013, 8, 18), InvoiceItemType.ITEM_ADJ, new BigDecimal("-1000")),
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 8, 18), new LocalDate(2013, 8, 18), InvoiceItemType.ITEM_ADJ, new BigDecimal("-1000")),
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 8, 18), new LocalDate(2013, 8, 18), InvoiceItemType.ITEM_ADJ, new BigDecimal("-399.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 9, 17), new LocalDate(2014, 8, 18), InvoiceItemType.REPAIR_ADJ, BigDecimal.ZERO));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, toBeChecked);


        final BigDecimal accountBalance = invoiceUserApi.getAccountBalance(bpEntitlement.getAccountId(), callContext);
        assertEquals(accountBalance.compareTo(BigDecimal.ZERO), 0);

        checkNoMoreInvoiceToGenerate(account);

    }



    @Test(groups = "slow")
    public void testWithFullRepairAndExistingPartialAdjustment() throws Exception {

        final LocalDate today = new LocalDate(2013, 7, 19);
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDeltaFromReality(today.toDateTimeAtCurrentTime(DateTimeZone.UTC).getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.ANNUAL;

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //
        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext).size(), 1);

        assertEquals(bpEntitlement.getSubscriptionBase().getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.ANNUAL);

        // Move out of trials for interesting invoices adjustments
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();


        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 2);
        ImmutableList<ExpectedInvoiceItemCheck> toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 8, 18), new LocalDate(2014, 8, 18), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);


        // Cancelation SOT -> fully repaired
        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.INVOICE);
        bpEntitlement.cancelEntitlementWithPolicyOverrideBillingPolicy(EntitlementActionPolicy.IMMEDIATE, BillingActionPolicy.START_OF_TERM, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();



        final InvoiceItem targetItem = invoices.get(1).getInvoiceItems().get(0);
        final InvoiceItemModelDao adj = new InvoiceItemModelDao(clock.getUTCNow(), InvoiceItemType.ITEM_ADJ, targetItem.getInvoiceId(), targetItem.getAccountId(),
                                                                null, null, null, targetItem.getProductName(), targetItem.getPlanName(), targetItem.getPhaseName(), targetItem.getUsageName(),
                                                                targetItem.getCatalogEffectiveDate(), clock.getUTCToday(), clock.getUTCToday(), new BigDecimal("-1000.00"), null, targetItem.getCurrency(), targetItem.getId());

        final InvoiceModelDao invoiceForAdj = new InvoiceModelDao(UUID.randomUUID(), clock.getUTCNow(), bpEntitlement.getAccountId(), null, clock.getUTCToday(), clock.getUTCToday(), Currency.USD, false, InvoiceStatus.COMMITTED, false);
        invoiceForAdj.addInvoiceItem(adj);

        busHandler.pushExpectedEvents(NextEvent.INVOICE_ADJUSTMENT);
        insertInvoiceItems(invoiceForAdj);
        assertListenerStatus();

        // __PARK__ account
        busHandler.pushExpectedEvents(NextEvent.TAG);
        try {
            invoiceUserApi.triggerInvoiceGeneration(account.getId(), clock.getUTCToday(), callContext);
            fail("Should not have generated an extra invoice");
        } catch (final InvoiceApiException e) { assertListenerStatus();
            assertTrue(e.getCause().getMessage().startsWith("Too many repairs for invoiceItemId"));
        } finally {
            assertListenerStatus();
        }


    }


    @Test(groups = "slow")
    public void testWithPartialRepairAndExistingPartialTooLargeAdjustment() throws Exception {

        final LocalDate today = new LocalDate(2013, 7, 19);
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDeltaFromReality(today.toDateTimeAtCurrentTime(DateTimeZone.UTC).getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.ANNUAL;

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //
        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext).size(), 1);

        assertEquals(bpEntitlement.getSubscriptionBase().getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.ANNUAL);

        // Move out of trials for interesting invoices adjustments
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();


        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 2);
        ImmutableList<ExpectedInvoiceItemCheck> toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 8, 18), new LocalDate(2014, 8, 18), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);


        clock.addMonths(6);
        // Cancelation IMM -> partially repaired
        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.INVOICE);
        bpEntitlement.cancelEntitlementWithPolicyOverrideBillingPolicy(EntitlementActionPolicy.IMMEDIATE, BillingActionPolicy.IMMEDIATE, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();


        // 1209.84
        final InvoiceItem targetItem = invoices.get(1).getInvoiceItems().get(0);
        final InvoiceItemModelDao adj = new InvoiceItemModelDao(clock.getUTCNow(), InvoiceItemType.ITEM_ADJ, targetItem.getInvoiceId(), targetItem.getAccountId(),
                                                                null, null, null, targetItem.getProductName(), targetItem.getPlanName(), targetItem.getPhaseName(), targetItem.getUsageName(),
                                                                targetItem.getCatalogEffectiveDate(), clock.getUTCToday(), clock.getUTCToday(), new BigDecimal("-1300.00"), null, targetItem.getCurrency(), targetItem.getId());

        final InvoiceModelDao invoiceForAdj = new InvoiceModelDao(UUID.randomUUID(), clock.getUTCNow(), bpEntitlement.getAccountId(), null, clock.getUTCToday(), clock.getUTCToday(), Currency.USD, false, InvoiceStatus.COMMITTED, false);
        invoiceForAdj.addInvoiceItem(adj);

        busHandler.pushExpectedEvents(NextEvent.INVOICE_ADJUSTMENT);
        insertInvoiceItems(invoiceForAdj);
        assertListenerStatus();

        // __PARK__ account
        busHandler.pushExpectedEvents(NextEvent.TAG);
        try {
            invoiceUserApi.triggerInvoiceGeneration(account.getId(), clock.getUTCToday(), callContext);
            fail("Should not have generated an extra invoice");
        } catch (final InvoiceApiException e) { assertListenerStatus();
            assertTrue(e.getCause().getMessage().startsWith("Too many repairs for invoiceItemId"));
        } finally {
            assertListenerStatus();
        }

    }

    @Test(groups = "slow")
    public void testAdjustmentsToolarge() throws Exception {

        final LocalDate today = new LocalDate(2013, 7, 19);
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDeltaFromReality(today.toDateTimeAtCurrentTime(DateTimeZone.UTC).getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.ANNUAL;

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //
        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext).size(), 1);

        assertEquals(bpEntitlement.getSubscriptionBase().getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.ANNUAL);

        // Move out of trials for interesting invoices adjustments
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();


        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 2);
        ImmutableList<ExpectedInvoiceItemCheck> toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 8, 18), new LocalDate(2014, 8, 18), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);



        final InvoiceItem targetItem = invoices.get(1).getInvoiceItems().get(0);
        final InvoiceItemModelDao adj1 = new InvoiceItemModelDao(clock.getUTCNow(), InvoiceItemType.ITEM_ADJ, targetItem.getInvoiceId(), targetItem.getAccountId(),
                                                                 null, null, null, targetItem.getProductName(), targetItem.getPlanName(), targetItem.getPhaseName(), targetItem.getUsageName(),
                                                                 targetItem.getCatalogEffectiveDate(), clock.getUTCToday(), clock.getUTCToday(), new BigDecimal("-1000.00"), null, targetItem.getCurrency(), targetItem.getId());

        final InvoiceItemModelDao adj2 = new InvoiceItemModelDao(clock.getUTCNow(), InvoiceItemType.ITEM_ADJ, targetItem.getInvoiceId(), targetItem.getAccountId(),
                                                                 null, null, null, targetItem.getProductName(), targetItem.getPlanName(), targetItem.getPhaseName(), targetItem.getUsageName(),
                                                                 targetItem.getCatalogEffectiveDate(), clock.getUTCToday(), clock.getUTCToday(), new BigDecimal("-2000.00"), null, targetItem.getCurrency(), targetItem.getId());

        final InvoiceModelDao invoiceForAdj = new InvoiceModelDao(UUID.randomUUID(), clock.getUTCNow(), bpEntitlement.getAccountId(), null, clock.getUTCToday(), clock.getUTCToday(), Currency.USD, false, InvoiceStatus.COMMITTED, false);
        invoiceForAdj.addInvoiceItem(adj1);
        invoiceForAdj.addInvoiceItem(adj2);


        busHandler.pushExpectedEvents(NextEvent.INVOICE_ADJUSTMENT);
        insertInvoiceItems(invoiceForAdj);
        assertListenerStatus();

        checkNoMoreInvoiceToGenerate(account);
    }



    // This is a beatrix level test matching ur invoice TestSubscriptionItemTree#testWithWrongInitialItem
    @Test(groups = "slow")
    public void testWithWrongInitialItem() throws Exception {

        final DateTime initialDate = new DateTime(2016, 9, 8, 0, 0, 0, 0, testTimeZone);
        final LocalDate correctStartDate = initialDate.toLocalDate();
        final LocalDate wrongStartDate = new LocalDate(2016, 9, 9);
        final LocalDate endDate = new LocalDate(2016, 10, 8);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());


        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(8));
        assertNotNull(account);

        add_AUTO_INVOICING_OFF_Tag(account.getId(), ObjectType.ACCOUNT);
        add_AUTO_PAY_OFF_Tag(account.getId(), ObjectType.ACCOUNT);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("pistol-monthly-notrial");

        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.CREATE);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, null), null, correctStartDate, correctStartDate, false, false, ImmutableList.<PluginProperty>of(), callContext);
        final Entitlement bpEntitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);
        assertListenerStatus();


        final InvoiceModelDao existingBadInvoice = new InvoiceModelDao(UUID.randomUUID(), clock.getUTCNow(), account.getId(), null, correctStartDate, correctStartDate, Currency.USD, false, InvoiceStatus.COMMITTED, false);
        final InvoiceItemModelDao wrongRecurring = new InvoiceItemModelDao(initialDate, InvoiceItemType.RECURRING, existingBadInvoice.getId(), existingBadInvoice.getAccountId(),
                                                                           bpEntitlement.getBundleId(), entitlementId, "", "Pistol", "pistol-monthly-notrial", "pistol-monthly-notrial-evergreen", null,
                                                                           null, new LocalDate(2016, 9, 9), new LocalDate(2016, 10, 8), new BigDecimal("19.29"), new BigDecimal("19.95"), account.getCurrency(), null);
        existingBadInvoice.addInvoiceItem(wrongRecurring);
        insertInvoiceItems(existingBadInvoice);


        existingBadInvoice.getInvoiceItems().clear();
        final InvoiceItemModelDao adj = new InvoiceItemModelDao(clock.getUTCNow(), InvoiceItemType.ITEM_ADJ, existingBadInvoice.getId(), existingBadInvoice.getAccountId(),
                                                                 null, null, null, wrongRecurring.getProductName(), wrongRecurring.getPlanName(), wrongRecurring.getPhaseName(), wrongRecurring.getUsageName(),
                                                                 wrongRecurring.getCatalogEffectiveDate(), clock.getUTCToday(), clock.getUTCToday(), new BigDecimal("-19.29"), null, wrongRecurring.getCurrency(), wrongRecurring.getId());
        existingBadInvoice.addInvoiceItem(adj);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_ADJUSTMENT);
        insertInvoiceItems(existingBadInvoice);
        assertListenerStatus();


        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        remove_AUTO_INVOICING_OFF_Tag(account.getId(), ObjectType.ACCOUNT);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 9, 8), new LocalDate(2016, 9, 9), InvoiceItemType.RECURRING, new BigDecimal("0.66")));

        checkNoMoreInvoiceToGenerate(account);
    }

    // This is a beatrix level test matching our invoice TestFixedAndRecurringInvoiceItemGenerator#testOverlappingItems
    @Test(groups = "slow")
    public void testOverlappingItems() throws Exception {

        final DateTime initialDate = new DateTime(2016, 1, 1, 0, 0, 0, 0, testTimeZone);
        final LocalDate startDate = initialDate.toLocalDate();
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));
        assertNotNull(account);

        add_AUTO_INVOICING_OFF_Tag(account.getId(), ObjectType.ACCOUNT);
        add_AUTO_PAY_OFF_Tag(account.getId(), ObjectType.ACCOUNT);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("pistol-monthly-notrial");

        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.CREATE);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, null), null, startDate, startDate, false, false, ImmutableList.<PluginProperty>of(), callContext);
        final Entitlement bpEntitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);
        assertListenerStatus();


        final InvoiceModelDao existingBadInvoice = new InvoiceModelDao(UUID.randomUUID(), clock.getUTCNow(), account.getId(), null, startDate, startDate, Currency.USD, false, InvoiceStatus.COMMITTED, false);
        final InvoiceItemModelDao wrongRecurring = new InvoiceItemModelDao(initialDate, InvoiceItemType.RECURRING, existingBadInvoice.getId(), existingBadInvoice.getAccountId(),
                                                                           bpEntitlement.getBundleId(), entitlementId, "", "Pistol", "pistol-monthly-notrial", "pistol-monthly-notrial-evergreen", null,
                                                                           null, new LocalDate(2016, 1, 1), new LocalDate(2016, 1, 30), new BigDecimal("18.66"), new BigDecimal("19.95"), account.getCurrency(), null);
        existingBadInvoice.addInvoiceItem(wrongRecurring);
        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        insertInvoiceItems(existingBadInvoice);
        assertListenerStatus();


        // Prior the change introduced in 6338109f8cdc7, this is what the code would have generated
        final InvoiceModelDao prevBehaviorInvoice = new InvoiceModelDao(UUID.randomUUID(), clock.getUTCNow(), account.getId(), null, startDate, startDate, Currency.USD, false, InvoiceStatus.COMMITTED, false);
        final InvoiceItemModelDao  prevRecurring = new InvoiceItemModelDao(initialDate, InvoiceItemType.RECURRING, prevBehaviorInvoice.getId(), prevBehaviorInvoice.getAccountId(),
                                                                           bpEntitlement.getBundleId(), entitlementId, "", "Pistol", "pistol-monthly-notrial", "pistol-monthly-notrial-evergreen", null,
                                                                           null, new LocalDate(2016, 1, 1), new LocalDate(2016, 2, 1), new BigDecimal("19.85"), new BigDecimal("19.95"), account.getCurrency(), null);
        final InvoiceItemModelDao prevRepair = new InvoiceItemModelDao(prevBehaviorInvoice.getCreatedDate(), InvoiceItemType.REPAIR_ADJ, prevBehaviorInvoice.getId(), prevBehaviorInvoice.getAccountId(),
                                                                       bpEntitlement.getBundleId(), bpEntitlement.getBaseEntitlementId(), null, null, null, null, null,
                                                                       null, new LocalDate(2016, 1, 1), new LocalDate(2016, 1, 30),  new BigDecimal("-18.66"), new BigDecimal("19.95"), account.getCurrency(), wrongRecurring.getId());

        prevBehaviorInvoice.addInvoiceItem(prevRecurring);
        prevBehaviorInvoice.addInvoiceItem(prevRepair);
        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        insertInvoiceItems(prevBehaviorInvoice);
        assertListenerStatus();

        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE);
        remove_AUTO_INVOICING_OFF_Tag(account.getId(), ObjectType.ACCOUNT);
        assertListenerStatus();

        checkNoMoreInvoiceToGenerate(account);
    }


    // This is a beatrix level test matching our invoice TestDefaultInvoiceGenerator#testRegressionFor170
    @Test(groups = "slow")
    public void testRegressionFor170() throws Exception {

        final DateTime initialDate = new DateTime(2013, 6, 15, 0, 0, 0, 0, testTimeZone);
        final LocalDate startDate = initialDate.toLocalDate();
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(15));
        assertNotNull(account);

        add_AUTO_PAY_OFF_Tag(account.getId(), ObjectType.ACCOUNT);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("pistol-monthly-notrial");

        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.CREATE, NextEvent.INVOICE);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, null), null, startDate, startDate, false, false, ImmutableList.<PluginProperty>of(), callContext);
        final Entitlement bpEntitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);
        assertListenerStatus();

        final Invoice invoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                            new ExpectedInvoiceItemCheck(new LocalDate(2013, 6, 15), new LocalDate(2013, 7, 15), InvoiceItemType.RECURRING, new BigDecimal("19.95")));

        add_AUTO_INVOICING_OFF_Tag(account.getId(), ObjectType.ACCOUNT);

        final UUID linkedItemId = invoice.getInvoiceItems().get(0).getId();
        final InvoiceModelDao repairInvoice = new InvoiceModelDao(UUID.randomUUID(), clock.getUTCNow(), account.getId(), null, startDate, startDate, Currency.USD, false, InvoiceStatus.COMMITTED, false);
        final InvoiceItemModelDao wrongRepair = new InvoiceItemModelDao(repairInvoice.getCreatedDate(), InvoiceItemType.REPAIR_ADJ, repairInvoice.getId(), repairInvoice.getAccountId(),
                                                                       bpEntitlement.getBundleId(), bpEntitlement.getBaseEntitlementId(), null, null, null, null, null,
                                                                       null, new LocalDate(2013, 6, 21), new LocalDate(2013, 6, 26),  new BigDecimal("-3.33"), new BigDecimal("19.95"), account.getCurrency(), linkedItemId);
        repairInvoice.addInvoiceItem(wrongRepair);

        busHandler.pushExpectedEvents(NextEvent.INVOICE_ADJUSTMENT, NextEvent.INVOICE);
        insertInvoiceItems(repairInvoice);
        assertListenerStatus();


        // Prior the change introduced in 6338109f8cdc7, this is what the code would have generated
        final InvoiceModelDao prevBehaviorInvoice = new InvoiceModelDao(UUID.randomUUID(), clock.getUTCNow(), account.getId(), null, startDate, startDate, Currency.USD, false, InvoiceStatus.COMMITTED, false);
        final InvoiceItemModelDao  prevRecurring = new InvoiceItemModelDao(initialDate, InvoiceItemType.RECURRING, prevBehaviorInvoice.getId(), prevBehaviorInvoice.getAccountId(),
                                                                           bpEntitlement.getBundleId(), entitlementId, "", "Pistol", "pistol-monthly-notrial", "pistol-monthly-notrial-evergreen", null,
                                                                           null, new LocalDate(2013, 6, 15), new LocalDate(2013, 7, 15), new BigDecimal("19.85"), new BigDecimal("19.95"), account.getCurrency(), null);
        final InvoiceItemModelDao prevRepair1 = new InvoiceItemModelDao(prevBehaviorInvoice.getCreatedDate(), InvoiceItemType.REPAIR_ADJ, prevBehaviorInvoice.getId(), prevBehaviorInvoice.getAccountId(),
                                                                        bpEntitlement.getBundleId(), bpEntitlement.getBaseEntitlementId(), null, null, null, null, null,
                                                                        null, new LocalDate(2013, 6, 15), new LocalDate(2013, 6, 21),  new BigDecimal("-3.99"), new BigDecimal("19.95"), account.getCurrency(), linkedItemId);
        final InvoiceItemModelDao prevRepair2 = new InvoiceItemModelDao(prevBehaviorInvoice.getCreatedDate(), InvoiceItemType.REPAIR_ADJ, prevBehaviorInvoice.getId(), prevBehaviorInvoice.getAccountId(),
                                                                        bpEntitlement.getBundleId(), bpEntitlement.getBaseEntitlementId(), null, null, null, null, null,
                                                                        null, new LocalDate(2013, 6, 26), new LocalDate(2013, 7, 15),  new BigDecimal("-12.63"), new BigDecimal("19.95"), account.getCurrency(), linkedItemId);
        prevBehaviorInvoice.addInvoiceItem(prevRecurring);
        prevBehaviorInvoice.addInvoiceItem(prevRepair1);
        prevBehaviorInvoice.addInvoiceItem(prevRepair2);

        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        insertInvoiceItems(prevBehaviorInvoice);
        assertListenerStatus();

        // Nothing to generate - new behavior is compatible with older behavior
        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE);
        remove_AUTO_INVOICING_OFF_Tag(account.getId(), ObjectType.ACCOUNT);
        assertListenerStatus();


        checkNoMoreInvoiceToGenerate(account);

    }
}
