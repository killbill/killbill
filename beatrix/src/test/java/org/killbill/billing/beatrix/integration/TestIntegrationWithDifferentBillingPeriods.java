/*
 * Copyright 2010-2014 Ning, Inc.
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

package org.killbill.billing.beatrix.integration;

import java.math.BigDecimal;
import java.util.List;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.testng.annotations.Test;

import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.util.tag.ControlTagType;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestIntegrationWithDifferentBillingPeriods extends TestIntegrationBase {

    @Test(groups = "slow")
    public void testChangeMonthlyToAnnual() throws Exception {

        // We take april as it has 30 days (easier to play with BCD)
        final LocalDate today = new LocalDate(2012, 4, 1);
        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDeltaFromReality(today.toDateTimeAtCurrentTime(DateTimeZone.UTC).getMillis() - clock.getUTCNow().getMillis());

        final String productName = "Shotgun";

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE NextEvent.INVOICE
        //

        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), callContext).size(), 1);

        assertEquals(bpEntitlement.getSubscriptionBase().getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.MONTHLY);

        // Move out of trials for interesting invoices adjustments
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT);
        clock.addDays(31);
        assertListenerStatus();

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), callContext);
        assertEquals(invoices.size(), 2);
        ImmutableList<ExpectedInvoiceItemCheck> toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        //
        // MOVE MONTHLY TO ANNUAL
        //
        clock.addDays(10);

        changeEntitlementAndCheckForCompletion(bpEntitlement, productName, BillingPeriod.ANNUAL, BillingActionPolicy.IMMEDIATE, NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.INVOICE_ADJUSTMENT, NextEvent.PAYMENT);

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), callContext);
        assertEquals(invoices.size(), 3);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("249.95")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 12), new LocalDate(2012, 5, 12), InvoiceItemType.CBA_ADJ, new BigDecimal("161.26")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 12), new LocalDate(2012, 6, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-161.26")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 12), new LocalDate(2013, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("2327.62")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 12), new LocalDate(2012, 5, 12), InvoiceItemType.CBA_ADJ, new BigDecimal("-161.26")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, toBeChecked);

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
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE NextEvent.INVOICE
        //

        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), callContext).size(), 1);

        assertEquals(bpEntitlement.getSubscriptionBase().getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.MONTHLY);

        // Move out of trials for interesting invoices adjustments
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT);
        clock.addDays(31);
        assertListenerStatus();

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), callContext);
        assertEquals(invoices.size(), 2);
        ImmutableList<ExpectedInvoiceItemCheck> toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        //
        // MOVE MONTHLY TO QUARTERLY
        //
        clock.addDays(10);

        changeEntitlementAndCheckForCompletion(bpEntitlement, productName, BillingPeriod.QUARTERLY, BillingActionPolicy.IMMEDIATE, NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.INVOICE_ADJUSTMENT, NextEvent.PAYMENT);

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), callContext);
        assertEquals(invoices.size(), 3);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 12), new LocalDate(2012, 5, 12), InvoiceItemType.CBA_ADJ, new BigDecimal("19.32")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 12), new LocalDate(2012, 6, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-19.32")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 12), new LocalDate(2012, 8, 1), InvoiceItemType.RECURRING, new BigDecimal("61.59")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 12), new LocalDate(2012, 5, 12), InvoiceItemType.CBA_ADJ, new BigDecimal("-19.32")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, toBeChecked);

        // Move to 1020-08-01
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT);
        clock.addDays(20);
        clock.addMonths(2);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), callContext);
        assertEquals(invoices.size(), 4);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 8, 1), new LocalDate(2012, 11, 1), InvoiceItemType.RECURRING, new BigDecimal("69.95")));
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
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE NextEvent.INVOICE
        //

        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, BillingPeriod.ANNUAL, NextEvent.CREATE, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), callContext).size(), 1);

        assertEquals(bpEntitlement.getSubscriptionBase().getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.ANNUAL);

        // Move out of trials for interesting invoices adjustments
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT);
        clock.addDays(31);
        assertListenerStatus();

        // 2012-5-12
        clock.addDays(10);

        busHandler.pushExpectedEvents(NextEvent.PAUSE, NextEvent.BLOCK, NextEvent.INVOICE_ADJUSTMENT);
        entitlementApi.pause(bpEntitlement.getBundleId(), clock.getUTCNow().toLocalDate(), callContext);
        assertListenerStatus();

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), callContext);
        assertEquals(invoices.size(), 2);

        ImmutableList<ExpectedInvoiceItemCheck> toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2013, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 12), new LocalDate(2012, 5, 12), InvoiceItemType.CBA_ADJ, new BigDecimal("2327.62")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 12), new LocalDate(2013, 5, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-2327.62")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        // 2012-6-4
        clock.addDays(23);

        busHandler.pushExpectedEvents(NextEvent.RESUME, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT);
        entitlementApi.resume(bpEntitlement.getBundleId(), clock.getUTCNow().toLocalDate(), callContext);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), callContext);
        assertEquals(invoices.size(), 3);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 4), new LocalDate(2013, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("2380.22")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 4), new LocalDate(2012, 6, 4), InvoiceItemType.CBA_ADJ, new BigDecimal("-2327.62")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, toBeChecked);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT);
        clock.addYears(1);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), callContext);
        assertEquals(invoices.size(), 4);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 6, 1), new LocalDate(2014, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));
        invoiceChecker.checkInvoice(invoices.get(3).getId(), callContext, toBeChecked);

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
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE NextEvent.INVOICE
        //

        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, BillingPeriod.ANNUAL, NextEvent.CREATE, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), callContext).size(), 1);

        assertEquals(bpEntitlement.getSubscriptionBase().getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.ANNUAL);

        // Move out of trials for interesting invoices adjustments
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT);
        clock.addDays(31);
        assertListenerStatus();

        // Auto invoice off
        busHandler.pushExpectedEvents(NextEvent.TAG);
        tagUserApi.addTag(account.getId(), ObjectType.ACCOUNT, ControlTagType.AUTO_INVOICING_OFF.getId(), callContext);
        assertListenerStatus();

        // 2012-5-12
        clock.addDays(10);

        busHandler.pushExpectedEvents(NextEvent.PAUSE, NextEvent.BLOCK);
        entitlementApi.pause(bpEntitlement.getBundleId(), clock.getUTCNow().toLocalDate(), callContext);
        assertListenerStatus();

        // 2012-6-4
        clock.addDays(23);
        busHandler.pushExpectedEvents(NextEvent.RESUME, NextEvent.BLOCK);
        entitlementApi.resume(bpEntitlement.getBundleId(), clock.getUTCNow().toLocalDate(), callContext);
        assertListenerStatus();


        busHandler.pushExpectedEvents(NextEvent.TAG, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_ADJUSTMENT);
        tagUserApi.removeTag(account.getId(), ObjectType.ACCOUNT, ControlTagType.AUTO_INVOICING_OFF.getId(), callContext);
        assertListenerStatus();

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), callContext);
        assertEquals(invoices.size(), 3);


        ImmutableList<ExpectedInvoiceItemCheck> toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2013, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 4), new LocalDate(2012, 6, 4), InvoiceItemType.CBA_ADJ, new BigDecimal("2327.62")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 12), new LocalDate(2013, 5, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-2327.62")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 4), new LocalDate(2013, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("2380.22")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 4), new LocalDate(2012, 6, 4), InvoiceItemType.CBA_ADJ, new BigDecimal("-2327.62")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, toBeChecked);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT);
        clock.addYears(1);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), callContext);
        assertEquals(invoices.size(), 4);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2013, 6, 1), new LocalDate(2014, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));
        invoiceChecker.checkInvoice(invoices.get(3).getId(), callContext, toBeChecked);

        checkNoMoreInvoiceToGenerate(account);
    }
}
