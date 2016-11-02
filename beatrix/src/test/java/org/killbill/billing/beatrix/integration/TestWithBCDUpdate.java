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
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.junction.DefaultBlockingState;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

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
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        // 2016-4-4 : (BP still in TRIAL)
        clock.addDays(3);

        // Set next BCD to be the 15
        subscriptionBaseInternalApi.updateBCD(baseEntitlement.getId(), 15, null, internalCallContext);
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
        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 1), new LocalDate(2016, 5, 15), InvoiceItemType.RECURRING, new BigDecimal("116.64")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        // 2016-5-15 : NEW BCD
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(14);
        assertListenerStatus();


        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 15), new LocalDate(2016, 6, 15), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
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
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        // 2016-5-1 : BP out of TRIAL
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        // Set next BCD to be the 15
        subscriptionBaseInternalApi.updateBCD(baseEntitlement.getId(), 15,  null, internalCallContext);
        Thread.sleep(1000);
        assertListenerStatus();

        // 2016-5-15 : Catch BCD_CHANGE event and repair invoice accordingly
        busHandler.pushExpectedEvents(NextEvent.BCD_CHANGE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(14);
        assertListenerStatus();

        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();
        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
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

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 6, 15), new LocalDate(2016, 7, 15), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkInvoice(invoices.get(3).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        // Set next BCD to be the 10
        subscriptionBaseInternalApi.updateBCD(baseEntitlement.getId(), 10,  null, internalCallContext);
        Thread.sleep(1000);
        assertListenerStatus();

        // 2016-7-10
        busHandler.pushExpectedEvents(NextEvent.BCD_CHANGE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(25);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
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
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        // 2016-5-1 : BP out of TRIAL
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        subscriptionBaseInternalApi.updateBCD(baseEntitlement.getId(), 10,  null, internalCallContext);

        // 2016-5-5
        clock.addDays(4);
        changeEntitlementAndCheckForCompletion(baseEntitlement, "Assault-Rifle", BillingPeriod.MONTHLY, null, NextEvent.CHANGE, NextEvent.INVOICE);

        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();
        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 5), new LocalDate(2016, 5, 10), InvoiceItemType.RECURRING, new BigDecimal("99.99")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 5), new LocalDate(2016, 6, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-217.70")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 5), new LocalDate(2016, 5, 5), InvoiceItemType.CBA_ADJ, new BigDecimal("117.71")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        // 2016-5-10
        busHandler.pushExpectedEvents(NextEvent.BCD_CHANGE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(5);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
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
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        // 2016-5-1 : BP out of TRIAL
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        // 2016-5-5
        clock.addDays(4);
        changeEntitlementAndCheckForCompletion(baseEntitlement, "Assault-Rifle", BillingPeriod.MONTHLY, null, NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();
        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 5), new LocalDate(2016, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("522.54")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 5), new LocalDate(2016, 6, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-217.70")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        subscriptionBaseInternalApi.updateBCD(baseEntitlement.getId(), 10,  null, internalCallContext);

        // 2016-5-10
        busHandler.pushExpectedEvents(NextEvent.BCD_CHANGE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(5);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
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
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        // 2016-5-1 : BP out of TRIAL
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        subscriptionBaseInternalApi.updateBCD(baseEntitlement.getId(), 10,  null, internalCallContext);

        busHandler.pushExpectedEvents(NextEvent.BCD_CHANGE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(9);
        assertListenerStatus();

        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();
        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
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

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
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
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        // 2016-4-4 : (BP still in TRIAL)
        // Laser-Scope has 1 month DISCOUNT
        clock.addDays(3);
        final DefaultEntitlement aoEntitlement = addAOEntitlementAndCheckForCompletion(baseEntitlement.getBundleId(), "Laser-Scope", ProductCategory.ADD_ON, BillingPeriod.MONTHLY,
                                                                                       NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

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
        subscriptionBaseInternalApi.updateBCD(aoEntitlement.getId(), 4,  null, internalCallContext);
        assertListenerStatus();

        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();
        List<Invoice> invoices = null;

        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 6, 4), new LocalDate(2016, 7, 4), InvoiceItemType.RECURRING, new BigDecimal("1999.95")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 6, 4), new LocalDate(2016, 7, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-1799.96")));
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
        invoiceChecker.checkInvoice(invoices.get(5).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        // 2016-7-1 : BP only
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(27);
        assertListenerStatus();

        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 7, 1), new LocalDate(2016, 8, 1), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
        invoiceChecker.checkInvoice(invoices.get(6).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        // 2016-7-4 : AO only
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(3);
        assertListenerStatus();

        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 7, 4), new LocalDate(2016, 8, 4), InvoiceItemType.RECURRING, new BigDecimal("1999.95")));
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
        invoiceChecker.checkInvoice(invoices.get(7).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        checkNoMoreInvoiceToGenerate(account);
    }

    @Test(groups = "slow")
    public void testBlockPastUnpaidPeriodAndRealignBCD() throws Exception {

        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();
        List<Invoice> invoices = null;

        final DateTime initialDate = new DateTime(2016, 4, 1, 0, 13, 42, 0, testTimeZone);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(0));
        assertNotNull(account);

        // BP creation : Will set Account BCD to the first (2016-4-1 + 30 days = 2016-5-1)
        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);


        paymentPlugin.makeNextPaymentFailWithError();

        // 2016-5-1 : BP out of TRIAL
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        clock.addDays(30);
        assertListenerStatus();


        //
        // Let's assume 15 days later, the customer comes back and wants to continue using the service (after he updated his payment method)
        //
        // The company 'a.b.c' decides to block both the billing and entitlement for the past 15 days and also move his BCD to
        // the 16 so he gets to pay right away and for a full period (MONTHLY)
        //
        // 2016-5-16
        busHandler.pushExpectedEvents(NextEvent.INVOICE_PAYMENT_ERROR, NextEvent.PAYMENT_ERROR);
        paymentPlugin.makeNextPaymentFailWithError();
        clock.addDays(15);
        assertListenerStatus();


        // First BLOCK subscription starting from the 2016-5-1
        // This will generate the credit for the full period, bringing by account balance to 0
        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.INVOICE);
        final BlockingState blockingState = new DefaultBlockingState(baseEntitlement.getId(), BlockingStateType.SUBSCRIPTION, "COURTESY_BLOCK", "company.a.b.c", true, true, true, null);
        subscriptionApi.addBlockingState(blockingState,  new LocalDate(2016, 5, 1),  ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();


        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 1), new LocalDate(2016, 6, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-249.95")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 16), new LocalDate(2016, 5, 16), InvoiceItemType.CBA_ADJ, new BigDecimal("249.95")));
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        // Second, move the BCD to the 16
        // Because we did not unblock yet, we don't have a new invoice but we see the NULL_INVOICE event
        busHandler.pushExpectedEvents(NextEvent.BCD_CHANGE, NextEvent.NULL_INVOICE);
        subscriptionBaseInternalApi.updateBCD(baseEntitlement.getId(), 16,  null, internalCallContext);
        assertListenerStatus();

        // Third, unblock starting at the 16, will generate a full period invoice
        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.INVOICE,  NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        final BlockingState unblockingState = new DefaultBlockingState(baseEntitlement.getId(), BlockingStateType.SUBSCRIPTION, "END_OF_COURTESY_BLOCK", "company.a.b.c", false, false, false, null);
        subscriptionApi.addBlockingState(unblockingState,  new LocalDate(2016, 5, 16),  ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 16), new LocalDate(2016, 6, 16), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
        invoiceChecker.checkInvoice(invoices.get(3).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();
    }


    @Test(groups = "slow")
    public void testBCDChangeWithEffectiveDateFromInTheFuture() throws Exception {

        final DateTime initialDate = new DateTime(2016, 4, 1, 0, 13, 42, 0, testTimeZone);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(0));
        assertNotNull(account);

        // BP creation : Will set Account BCD to the first (2016-4-1 + 30 days = 2016-5-1)
        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        // 2016-5-1 : BP out of TRIAL
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        // Set next BCD to be the 15 but only starting from 2016-5-31
        subscriptionBaseInternalApi.updateBCD(baseEntitlement.getId(), 15,  new LocalDate(2016, 5, 31), internalCallContext);
        Thread.sleep(1000);
        assertListenerStatus();

        // 2016-5-15 : We don't expect anything yet because of effectiveDateFrom = 2016-6-1
        clock.addDays(14);
        Thread.sleep(1000);
        assertListenerStatus();

        // 2016-6-1 : We expect a pro-ration from 2016-6-1 -> 2016-6-15
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(17);
        assertListenerStatus();

        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();
        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 6, 1), new LocalDate(2016, 6, 15), InvoiceItemType.RECURRING, new BigDecimal("116.64")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        //  2016-6-15 : Finally we get the BCD_CHANGE event and start building for full monthly period
        busHandler.pushExpectedEvents(NextEvent.BCD_CHANGE, NextEvent.NULL_INVOICE,  NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(14);
        assertListenerStatus();
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 6, 15), new LocalDate(2016, 7, 15), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkInvoice(invoices.get(3).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();
    }
}
