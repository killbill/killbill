/*
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

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNotNull;

public class TestWithBCDUpdate extends TestIntegrationBase {

    @Inject
    protected SubscriptionBaseInternalApi subscriptionBaseInternalApi;


    @Test(groups = "slow")
    public void testBCDChangeInTrial() throws Exception {

        final DateTime initialDate = new DateTime(2016, 4, 1, 0, 13, 42, 0, testTimeZone);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(0));
        assertNotNull(account);

        // BP creation : Will set Account BCD to the first (2016-4-1 + 30 days = 2016-5-1)
        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.INVOICE);

        // 2016-4-4 : (BP still in TRIAL)
        clock.addDays(3);

        // Set next BCD to be the 15
        subscriptionBaseInternalApi.updateBCD(baseEntitlement.getId(), 15, internalCallContext);
        Thread.sleep(1000);
        assertListenerStatus();

        // 2016-5-15 : Catch BCD_CHANGE event
        busHandler.pushExpectedEvents(NextEvent.BCD_CHANGE, NextEvent.NULL_INVOICE);
        clock.addDays(11);
        assertListenerStatus();

        // 2016-5-1 : BP out of TRIAL
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(16);
        assertListenerStatus();


        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();
        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), callContext);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 1), new LocalDate(2016, 5, 15), InvoiceItemType.RECURRING, new BigDecimal("116.64")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        // 2016-5-15 : NEW BCD
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(14);
        assertListenerStatus();


        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 15), new LocalDate(2016, 6, 15), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), callContext);
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();
    }


    @Test(groups = "slow")
    public void testBCDChangeAfterTrialFollowOtherBCDChange() throws Exception {

        final DateTime initialDate = new DateTime(2016, 4, 1, 0, 13, 42, 0, testTimeZone);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(0));
        assertNotNull(account);

        // BP creation : Will set Account BCD to the first (2016-4-1 + 30 days = 2016-5-1)
        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.INVOICE);

        // 2016-5-1 : BP out of TRIAL
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        // Set next BCD to be the 15
        subscriptionBaseInternalApi.updateBCD(baseEntitlement.getId(), 15, internalCallContext);
        Thread.sleep(1000);
        assertListenerStatus();

        // 2016-5-15 : Catch BCD_CHANGE event and repair invoice accordingly
        busHandler.pushExpectedEvents(NextEvent.BCD_CHANGE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(14);
        assertListenerStatus();

        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();
        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), callContext);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 15), new LocalDate(2016, 6, 15), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 15), new LocalDate(2016, 6, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-137.07")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        // 2016-6-01 : Original notification for 2016-6-01 (prior BCD change)
        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE);
        clock.addDays(17);
        assertListenerStatus();


        // 2016-6-15
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(14);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), callContext);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 6, 15), new LocalDate(2016, 7, 15), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkInvoice(invoices.get(3).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        // Set next BCD to be the 10
        subscriptionBaseInternalApi.updateBCD(baseEntitlement.getId(), 10, internalCallContext);
        Thread.sleep(1000);
        assertListenerStatus();

        // 2016-7-10
        busHandler.pushExpectedEvents(NextEvent.BCD_CHANGE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(25);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), callContext);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 7, 10), new LocalDate(2016, 8, 10), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 7, 10), new LocalDate(2016, 7, 15), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-41.66")));
        invoiceChecker.checkInvoice(invoices.get(4).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();
    }


    @Test(groups = "slow")
    public void testBCDChangeBeforeChangePlan() throws Exception {

        final DateTime initialDate = new DateTime(2016, 4, 1, 0, 13, 42, 0, testTimeZone);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(0));
        assertNotNull(account);

        // BP creation : Will set Account BCD to the first (2016-4-1 + 30 days = 2016-5-1)
        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.INVOICE);

        // 2016-5-1 : BP out of TRIAL
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        subscriptionBaseInternalApi.updateBCD(baseEntitlement.getId(), 10, internalCallContext);

        // 2016-5-5
        clock.addDays(4);
        changeEntitlementAndCheckForCompletion(baseEntitlement, "Assault-Rifle", BillingPeriod.MONTHLY, null, NextEvent.CHANGE, NextEvent.INVOICE);

        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();
        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), callContext);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 5), new LocalDate(2016, 5, 10), InvoiceItemType.RECURRING, new BigDecimal("99.99")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 5), new LocalDate(2016, 6, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-217.70")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 5), new LocalDate(2016, 5, 5), InvoiceItemType.CBA_ADJ, new BigDecimal("117.71")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        // 2016-5-10
        busHandler.pushExpectedEvents(NextEvent.BCD_CHANGE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(5);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), callContext);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 10), new LocalDate(2016, 6, 10), InvoiceItemType.RECURRING, new BigDecimal("599.95")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 10), new LocalDate(2016, 5, 10), InvoiceItemType.CBA_ADJ, new BigDecimal("-117.71")));
        invoiceChecker.checkInvoice(invoices.get(3).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

    }


    @Test(groups = "slow")
    public void testBCDChangeAfterChangePlan() throws Exception {


        final DateTime initialDate = new DateTime(2016, 4, 1, 0, 13, 42, 0, testTimeZone);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(0));
        assertNotNull(account);

        // BP creation : Will set Account BCD to the first (2016-4-1 + 30 days = 2016-5-1)
        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.INVOICE);

        // 2016-5-1 : BP out of TRIAL
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        // 2016-5-5
        clock.addDays(4);
        changeEntitlementAndCheckForCompletion(baseEntitlement, "Assault-Rifle", BillingPeriod.MONTHLY, null, NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();
        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), callContext);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 5), new LocalDate(2016, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("522.54")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 5), new LocalDate(2016, 6, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-217.70")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        subscriptionBaseInternalApi.updateBCD(baseEntitlement.getId(), 10, internalCallContext);

        // 2016-5-10
        busHandler.pushExpectedEvents(NextEvent.BCD_CHANGE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(5);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), callContext);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 10), new LocalDate(2016, 6, 10), InvoiceItemType.RECURRING, new BigDecimal("599.95")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 10), new LocalDate(2016, 6, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-425.77")));
        invoiceChecker.checkInvoice(invoices.get(3).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

    }

    @Test(groups = "slow")
    public void testBCDChangeForAnnualSubscriptionAndCancellation() throws Exception {

        final DateTime initialDate = new DateTime(2016, 4, 1, 0, 13, 42, 0, testTimeZone);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(0));
        assertNotNull(account);

        // BP creation : Will set Account BCD to the first (2016-4-1 + 30 days = 2016-5-1)
        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.ANNUAL;
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.INVOICE);

        // 2016-5-1 : BP out of TRIAL
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        subscriptionBaseInternalApi.updateBCD(baseEntitlement.getId(), 10, internalCallContext);

        busHandler.pushExpectedEvents(NextEvent.BCD_CHANGE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(9);
        assertListenerStatus();

        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();
        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), callContext);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 10), new LocalDate(2017, 5, 10), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 10), new LocalDate(2017, 5, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-2340.77")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        // 2017, 5, 1 (at 13, 42, 0)
        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE);
        clock.setTime(new DateTime(2017, 5, 1, 0, 13, 42, 0, testTimeZone));
        assertListenerStatus();


        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        //clock.setDay(new LocalDate(2017, 5, 10));
        clock.addDays(9);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), callContext);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2017, 5, 10), new LocalDate(2018, 5, 10), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));
        invoiceChecker.checkInvoice(invoices.get(3).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

    }


    @Test(groups = "slow")
    public void testBCDChangeForAO() throws Exception {

        final DateTime initialDate = new DateTime(2016, 4, 1, 0, 13, 42, 0, testTimeZone);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(0));
        assertNotNull(account);

        // BP creation : Will set Account BCD to the first (2016-4-1 + 30 days = 2016-5-1)
        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.INVOICE);

        // 2016-4-4 : (BP still in TRIAL)
        // Laser-Scope has 1 month DISCOUNT
        clock.addDays(3);
        final DefaultEntitlement aoEntitlement = addAOEntitlementAndCheckForCompletion(baseEntitlement.getBundleId(), "Laser-Scope", ProductCategory.ADD_ON, BillingPeriod.MONTHLY,
                                                                                       NextEvent.CREATE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        // 2016-5-1 : BP out of TRIAL + AO
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(27);
        assertListenerStatus();

        // 2016-5-4: Laser-Scope out of DISCOUNT
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(3);
        assertListenerStatus();

        // 2016-6-1 : BP + AO invoice
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(28);
        assertListenerStatus();

        // 2016-6-4 : Change BCD for AO and
        clock.addDays(3);

        busHandler.pushExpectedEvents(NextEvent.BCD_CHANGE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        subscriptionBaseInternalApi.updateBCD(aoEntitlement.getId(), 4, internalCallContext);
        assertListenerStatus();

        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();
        List<Invoice> invoices = null;

        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 6, 4), new LocalDate(2016, 7, 4), InvoiceItemType.RECURRING, new BigDecimal("1999.95")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 6, 4), new LocalDate(2016, 7, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-1799.96")));
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), callContext);
        invoiceChecker.checkInvoice(invoices.get(5).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        // 2016-7-1 : BP only
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(27);
        assertListenerStatus();

        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 7, 1), new LocalDate(2016, 8, 1), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), callContext);
        invoiceChecker.checkInvoice(invoices.get(6).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        // 2016-7-4 : AO only
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(3);
        assertListenerStatus();

        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 7, 4), new LocalDate(2016, 8, 4), InvoiceItemType.RECURRING, new BigDecimal("1999.95")));
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), callContext);
        invoiceChecker.checkInvoice(invoices.get(7).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        checkNoMoreInvoiceToGenerate(account);
    }

}
