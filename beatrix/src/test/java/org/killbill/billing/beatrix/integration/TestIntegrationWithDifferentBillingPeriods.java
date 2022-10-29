/*
 * Copyright 2010-2014 Ning, Inc.
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
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.invoice.InvoiceDispatcher.FutureAccountNotifications;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.api.InvoiceStatus;
import org.killbill.billing.invoice.dao.InvoiceDao;
import org.killbill.billing.invoice.dao.InvoiceItemModelDao;
import org.killbill.billing.invoice.dao.InvoiceModelDao;
import org.killbill.billing.util.tag.ControlTagType;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestIntegrationWithDifferentBillingPeriods extends TestIntegrationBase {

    @Inject
    protected InvoiceDao invoiceDao;

    private void insertInvoiceItems(final InvoiceModelDao invoice) {
        final FutureAccountNotifications callbackDateTimePerSubscriptions = new FutureAccountNotifications();
        invoiceDao.createInvoices(Collections.singletonList(invoice), null, Collections.emptySet(), callbackDateTimePerSubscriptions, null, false, internalCallContext);
    }

    @Test(groups = "slow")
    public void testChangeMonthlyToAnnual() throws Exception {

        // We take april as it has 30 days (easier to play with BCD)
        final LocalDate today = new LocalDate(2012, 4, 1);
        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDeltaFromReality(today.toDateTimeAtCurrentTime(DateTimeZone.UTC).getMillis() - clock.getUTCNow().getMillis());

        final String productName = "Shotgun";

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //

        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext).size(), 1);

        assertEquals(bpEntitlement.getSubscriptionBase().getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.MONTHLY);

        // Move out of trials for interesting invoices adjustments
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(31);
        assertListenerStatus();

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 2);
        List<ExpectedInvoiceItemCheck> toBeChecked = List.of(new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        //
        // MOVE MONTHLY TO ANNUAL
        //
        clock.addDays(10);

        changeEntitlementAndCheckForCompletion(bpEntitlement, productName, BillingPeriod.ANNUAL, BillingActionPolicy.IMMEDIATE, NextEvent.CHANGE, NextEvent.INVOICE,  NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 3);

        toBeChecked = List.of(new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        toBeChecked = List.of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 12), new LocalDate(2013, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("2327.62")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 12), new LocalDate(2012, 6, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-161.26")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, toBeChecked);

        checkNoMoreInvoiceToGenerate(account);
    }


    @Test(groups = "slow")
    public void testChangeMonthlyToAnnualWithDifferentBCD() throws Exception {

        // We take april as it has 30 days
        final LocalDate today = new LocalDate(2016, 6, 1);
        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(10));

        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDeltaFromReality(today.toDateTimeAtCurrentTime(DateTimeZone.UTC).getMillis() - clock.getUTCNow().getMillis());

        final String productName = "Shotgun";

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //

        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext).size(), 1);

        assertEquals(bpEntitlement.getSubscriptionBase().getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.MONTHLY);

        // Move out of trials for interesting invoices adjustments
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 2);
        List<ExpectedInvoiceItemCheck> toBeChecked = List.of(new ExpectedInvoiceItemCheck(new LocalDate(2016, 7, 1), new LocalDate(2016, 7, 10), InvoiceItemType.RECURRING, new BigDecimal("74.99")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);


        // Invoice for a full month
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();


        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 3);
        toBeChecked = List.of(new ExpectedInvoiceItemCheck(new LocalDate(2016, 7, 10), new LocalDate(2016, 8, 10), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, toBeChecked);

        //
        // MOVE MONTHLY TO ANNUAL: Because of different Billing Alignment the BCD now becomes the 1 (first non null recurring phase)
        //
        changeEntitlementAndCheckForCompletion(bpEntitlement, productName, BillingPeriod.ANNUAL, BillingActionPolicy.IMMEDIATE, NextEvent.CHANGE, NextEvent.INVOICE,  NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 4);

        toBeChecked = List.of(
                new ExpectedInvoiceItemCheck(new LocalDate(2016, 8, 1), new LocalDate(2017, 8, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")),
                new ExpectedInvoiceItemCheck(new LocalDate(2016, 8, 1), new LocalDate(2016, 8, 10), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-72.57")));
        invoiceChecker.checkInvoice(invoices.get(3).getId(), callContext, toBeChecked);

        checkNoMoreInvoiceToGenerate(account);
    }


    @Test(groups = "slow")
    public void testChangeMonthlyToQuarterly() throws Exception {

        // We take april as it has 30 days (easier to play with BCD)
        final LocalDate today = new LocalDate(2012, 4, 1);
        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDeltaFromReality(today.toDateTimeAtCurrentTime(DateTimeZone.UTC).getMillis() - clock.getUTCNow().getMillis());

        final String productName = "Pistol";

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //

        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext).size(), 1);

        assertEquals(bpEntitlement.getSubscriptionBase().getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.MONTHLY);

        // Move out of trials for interesting invoices adjustments
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(31);
        assertListenerStatus();

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 2);
        List<ExpectedInvoiceItemCheck> toBeChecked = List.of(new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        //
        // MOVE MONTHLY TO QUARTERLY
        //
        clock.addDays(10);

        changeEntitlementAndCheckForCompletion(bpEntitlement, productName, BillingPeriod.QUARTERLY, BillingActionPolicy.IMMEDIATE, NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 3);

        toBeChecked = List.of(new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        toBeChecked = List.of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 12), new LocalDate(2012, 8, 1), InvoiceItemType.RECURRING, new BigDecimal("61.59")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 12), new LocalDate(2012, 6, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-19.32")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, toBeChecked);

        // Move to 1020-08-01
        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(20);
        clock.addMonths(2);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 4);

        toBeChecked = List.<ExpectedInvoiceItemCheck>of(new ExpectedInvoiceItemCheck(new LocalDate(2012, 8, 1), new LocalDate(2012, 11, 1), InvoiceItemType.RECURRING, new BigDecimal("69.95")));
        invoiceChecker.checkInvoice(invoices.get(3).getId(), callContext, toBeChecked);

        checkNoMoreInvoiceToGenerate(account);

    }

    @Test(groups = "slow")
    public void testPauseResumeAnnual() throws Exception {

        // We take april as it has 30 days (easier to play with BCD)
        final LocalDate today = new LocalDate(2012, 4, 1);
        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDeltaFromReality(today.toDateTimeAtCurrentTime(DateTimeZone.UTC).getMillis() - clock.getUTCNow().getMillis());

        final String productName = "Shotgun";

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //

        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, BillingPeriod.ANNUAL, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext).size(), 1);

        assertEquals(bpEntitlement.getSubscriptionBase().getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.ANNUAL);

        // Move out of trials for interesting invoices adjustments
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(31);
        assertListenerStatus();

        // 2012-5-12
        clock.addDays(10);

        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.INVOICE);
        entitlementApi.pause(bpEntitlement.getBundleId(), clock.getUTCNow().toLocalDate(), Collections.emptyList(), callContext);
        assertListenerStatus();

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 3);

        List<ExpectedInvoiceItemCheck> toBeChecked = List.of(new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2013, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        toBeChecked = List.of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 12), new LocalDate(2012, 5, 12), InvoiceItemType.CBA_ADJ, new BigDecimal("2327.62")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 12), new LocalDate(2013, 5, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-2327.62")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, toBeChecked);

        // 2012-6-4
        clock.addDays(23);

        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        entitlementApi.resume(bpEntitlement.getBundleId(), clock.getUTCNow().toLocalDate(), Collections.emptyList(), callContext);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 4);

        toBeChecked = List.of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 4), new LocalDate(2013, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("2380.22")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 4), new LocalDate(2012, 6, 4), InvoiceItemType.CBA_ADJ, new BigDecimal("-2327.62")));
        invoiceChecker.checkInvoice(invoices.get(3).getId(), callContext, toBeChecked);

        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addYears(1);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 5);

        toBeChecked = List.of(new ExpectedInvoiceItemCheck(new LocalDate(2013, 6, 1), new LocalDate(2014, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));
        invoiceChecker.checkInvoice(invoices.get(4).getId(), callContext, toBeChecked);

        checkNoMoreInvoiceToGenerate(account);
    }

    @Test(groups = "slow")
    public void testPauseResumeAnnualWithInvoicingOff() throws Exception {

        // We take april as it has 30 days (easier to play with BCD)
        final LocalDate today = new LocalDate(2012, 4, 1);
        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDeltaFromReality(today.toDateTimeAtCurrentTime(DateTimeZone.UTC).getMillis() - clock.getUTCNow().getMillis());

        final String productName = "Shotgun";

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //

        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, BillingPeriod.ANNUAL, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext).size(), 1);

        assertEquals(bpEntitlement.getSubscriptionBase().getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.ANNUAL);

        // Move out of trials for interesting invoices adjustments
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(31);
        assertListenerStatus();

        // Auto invoice off
        busHandler.pushExpectedEvents(NextEvent.TAG);
        tagUserApi.addTag(account.getId(), ObjectType.ACCOUNT, ControlTagType.AUTO_INVOICING_OFF.getId(), callContext);
        assertListenerStatus();

        // 2012-5-12
        clock.addDays(10);

        busHandler.pushExpectedEvents(NextEvent.BLOCK);
        entitlementApi.pause(bpEntitlement.getBundleId(), clock.getUTCNow().toLocalDate(), Collections.emptyList(), callContext);
        assertListenerStatus();

        // 2012-6-4
        clock.addDays(23);
        busHandler.pushExpectedEvents(NextEvent.BLOCK);
        entitlementApi.resume(bpEntitlement.getBundleId(), clock.getUTCNow().toLocalDate(), Collections.emptyList(), callContext);
        assertListenerStatus();


        busHandler.pushExpectedEvents(NextEvent.TAG, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        tagUserApi.removeTag(account.getId(), ObjectType.ACCOUNT, ControlTagType.AUTO_INVOICING_OFF.getId(), callContext);
        assertListenerStatus();

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 3);


        List<ExpectedInvoiceItemCheck> toBeChecked = List.of(new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2013, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        toBeChecked = List.of(
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 5, 1), new LocalDate(2013, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("203.83")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 12), new LocalDate(2012, 6, 4), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-151.23")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, toBeChecked);

        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addYears(1);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 4);

        toBeChecked = List.of(new ExpectedInvoiceItemCheck(new LocalDate(2013, 6, 1), new LocalDate(2014, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));
        invoiceChecker.checkInvoice(invoices.get(3).getId(), callContext, toBeChecked);

        checkNoMoreInvoiceToGenerate(account);
    }


    @Test(groups = "slow")
    public void testChangeAnnualToAnnual() throws Exception {

        //
        // Initialize test 'startDate' with 2014-12-2 with an account BCD  set to the 1st (in such a way that at the end of the 30 days TRIAL,
        // recurring phase starts on 2015-01-01 and we invoice for a full year from 2015-01-01 to 2016-01-01
        //
        final LocalDate startDate = new LocalDate(2014, 12, 2);
        final int accountBCD = 1;
        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(accountBCD));

        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDeltaFromReality(startDate.toDateTimeAtCurrentTime(DateTimeZone.UTC).getMillis() - clock.getUTCNow().getMillis());

        // Create subscription and check we get the initial invoice for the 30 days TRIAL
        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", "Shotgun", ProductCategory.BASE, BillingPeriod.ANNUAL, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext).size(), 1);
        assertEquals(bpEntitlement.getSubscriptionBase().getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.ANNUAL);

        // Move out of TRIAL and verify we invioice for a full year
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 2);
        List<ExpectedInvoiceItemCheck> toBeChecked = List.of(new ExpectedInvoiceItemCheck(new LocalDate(2015, 1, 1), new LocalDate(2016, 1, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);


        //
        // Move to 2015-3-15 (somehow arbitrary date) and upgrade to another ANNUAL plan
        // We verify that we will invoice for the longest possible period in such a way that:
        // - We keep the same billing cycle day (1st)
        // - We invoice for *up to* a full year (but no more)
        //
        clock.addDays(73);

        changeEntitlementAndCheckForCompletion(bpEntitlement, "Assault-Rifle", BillingPeriod.ANNUAL, BillingActionPolicy.IMMEDIATE, NextEvent.CHANGE, NextEvent.INVOICE,  NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 3);
        toBeChecked = List.of(
                new ExpectedInvoiceItemCheck(new LocalDate(2015, 3, 15), new LocalDate(2016, 3, 1), InvoiceItemType.RECURRING, new BigDecimal("5770.44")),
                new ExpectedInvoiceItemCheck(new LocalDate(2015, 3, 15), new LocalDate(2016, 1, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-1919.96")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, toBeChecked);


        checkNoMoreInvoiceToGenerate(account);
    }


    @Test(groups = "slow")
    public void testPauseResumeAnnualWithInvoicingOffMigrationPath_0_20_to_0_22() throws Exception {

        // We take april as it has 30 days (easier to play with BCD)
        final LocalDate today = new LocalDate(2012, 4, 1);
        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDeltaFromReality(today.toDateTimeAtCurrentTime(DateTimeZone.UTC).getMillis() - clock.getUTCNow().getMillis());

        final String productName = "Shotgun";

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //

        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, BillingPeriod.ANNUAL, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext).size(), 1);

        assertEquals(bpEntitlement.getSubscriptionBase().getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.ANNUAL);

        // Move out of trials for interesting invoices adjustments
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(31);
        assertListenerStatus();

        // Auto invoice off
        busHandler.pushExpectedEvents(NextEvent.TAG);
        tagUserApi.addTag(account.getId(), ObjectType.ACCOUNT, ControlTagType.AUTO_INVOICING_OFF.getId(), callContext);
        assertListenerStatus();

        // 2012-5-12
        clock.addDays(10);

        busHandler.pushExpectedEvents(NextEvent.BLOCK);
        entitlementApi.pause(bpEntitlement.getBundleId(), clock.getUTCNow().toLocalDate(), Collections.emptyList(), callContext);
        assertListenerStatus();

        // 2012-6-4
        clock.addDays(23);
        busHandler.pushExpectedEvents(NextEvent.BLOCK);
        entitlementApi.resume(bpEntitlement.getBundleId(), clock.getUTCNow().toLocalDate(), Collections.emptyList(), callContext);
        assertListenerStatus();

        ///
        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 2);
        final Invoice secondInvoice = invoices.get(1);


        final UUID originalRecuringItemId = secondInvoice.getInvoiceItems().get(0).getId();
        final InvoiceModelDao invoiceForRepair_0_20 = new InvoiceModelDao(UUID.randomUUID(), clock.getUTCNow(), account.getId(), null, clock.getUTCToday(), clock.getUTCToday(), Currency.USD, false, InvoiceStatus.COMMITTED, false);

        final InvoiceItemModelDao repair = new InvoiceItemModelDao(invoiceForRepair_0_20.getCreatedDate(), InvoiceItemType.REPAIR_ADJ, invoiceForRepair_0_20.getId(), account.getId(),
                                                                   UUID.randomUUID(), bpEntitlement.getId(), null, null, null, null, null,
                                                                   null, new LocalDate(2012, 5, 12), new LocalDate(2013, 5, 1), new BigDecimal("-2327.62"), new BigDecimal("-2327.62"), account.getCurrency(), originalRecuringItemId);

        final InvoiceItemModelDao recurring = new InvoiceItemModelDao(invoiceForRepair_0_20.getCreatedDate(), InvoiceItemType.RECURRING, invoiceForRepair_0_20.getId(), account.getId(),
                                                                      UUID.randomUUID(), bpEntitlement.getId(), "", "Shotgun", "shotgun-monthly", "shotgun-monthly-evergreen", null,
                                                                      null, new LocalDate(2012, 6, 4), new LocalDate(2013, 6, 1), new BigDecimal("2380.22"), new BigDecimal("2380.22"), account.getCurrency(), null);

        invoiceForRepair_0_20.addInvoiceItem(repair);
        invoiceForRepair_0_20.addInvoiceItem(recurring);
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        insertInvoiceItems(invoiceForRepair_0_20);
        assertListenerStatus();

        //
        // We should not generate any invoice, instead, we generate a new invoice with 2 canceling items
        //
        //busHandler.pushExpectedEvents(NextEvent.TAG, NextEvent.NULL_INVOICE);
        busHandler.pushExpectedEvents(NextEvent.TAG, NextEvent.INVOICE);
        tagUserApi.removeTag(account.getId(), ObjectType.ACCOUNT, ControlTagType.AUTO_INVOICING_OFF.getId(), callContext);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 4);
        final List<ExpectedInvoiceItemCheck> toBeChecked = List.of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 4), new LocalDate(2013, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("2380.22")),
                // Cancels similar item previously added from 0_22
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 4), new LocalDate(2013, 6, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-2380.22")));
        invoiceChecker.checkInvoice(invoices.get(3).getId(), callContext, toBeChecked);

        checkNoMoreInvoiceToGenerate(account);
    }



}
