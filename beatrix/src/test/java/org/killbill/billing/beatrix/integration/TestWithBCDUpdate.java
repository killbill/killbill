/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.DefaultPlanPhasePriceOverride;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.catalog.api.UsagePriceOverride;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementActionPolicy;
import org.killbill.billing.entitlement.api.Subscription;
import org.killbill.billing.invoice.InvoiceDispatcher.FutureAccountNotifications;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.api.InvoiceStatus;
import org.killbill.billing.invoice.dao.InvoiceDao;
import org.killbill.billing.invoice.dao.InvoiceItemModelDao;
import org.killbill.billing.invoice.dao.InvoiceModelDao;
import org.killbill.billing.invoice.dao.InvoiceTrackingModelDao;
import org.killbill.billing.junction.DefaultBlockingState;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

public class TestWithBCDUpdate extends TestIntegrationBase {

    @Inject
    protected SubscriptionBaseInternalApi subscriptionBaseInternalApi;

    @Inject
    protected InvoiceDao invoiceDao;

    private void insertInvoiceItems(final InvoiceModelDao invoice) {
        final FutureAccountNotifications callbackDateTimePerSubscriptions = new FutureAccountNotifications();
        invoiceDao.createInvoice(invoice, null, ImmutableSet.<InvoiceTrackingModelDao>of(), callbackDateTimePerSubscriptions, null, internalCallContext);
    }

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
        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 1), new LocalDate(2016, 5, 15), InvoiceItemType.RECURRING, new BigDecimal("116.64")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        // 2016-5-15 : NEW BCD
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(14);
        assertListenerStatus();


        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 15), new LocalDate(2016, 6, 15), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        // Add cancellation with START_OF_TERM to verify BCD update is correctly interpreted
        clock.addDays(3);

        busHandler.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK, NextEvent.INVOICE);
        final Entitlement cancelledEntitlement = baseEntitlement.cancelEntitlementWithPolicyOverrideBillingPolicy(EntitlementActionPolicy.IMMEDIATE, BillingActionPolicy.START_OF_TERM, null, callContext);
        assertListenerStatus();

        final Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(cancelledEntitlement.getId(), callContext);
        assertEquals(subscription.getEffectiveEndDate().compareTo(new LocalDate(2016, 5, 18)), 0);
        assertEquals(subscription.getBillingEndDate().compareTo(new LocalDate(2016, 5, 15)), 0);
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
        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 6, 1), new LocalDate(2016, 6, 15), InvoiceItemType.RECURRING, new BigDecimal("112.88")));
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

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
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

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 7, 15), new LocalDate(2016, 8, 10), InvoiceItemType.RECURRING, new BigDecimal("209.64")));
        invoiceChecker.checkInvoice(invoices.get(4).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        clock.addDays(3);
        busHandler.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK, NextEvent.INVOICE);
        final Entitlement cancelledEntitlement = baseEntitlement.cancelEntitlementWithPolicyOverrideBillingPolicy(EntitlementActionPolicy.IMMEDIATE, BillingActionPolicy.START_OF_TERM, null, callContext);
        assertListenerStatus();

        final Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(cancelledEntitlement.getId(), callContext);
        assertEquals(subscription.getEffectiveEndDate().compareTo(new LocalDate(2016, 7, 13)), 0);
        assertEquals(subscription.getBillingEndDate().compareTo(new LocalDate(2016, 7, 10)), 0);

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
        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 5), new LocalDate(2016, 5, 10), InvoiceItemType.RECURRING, new BigDecimal("99.99")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 5), new LocalDate(2016, 6, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-217.70")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 5), new LocalDate(2016, 5, 5), InvoiceItemType.CBA_ADJ, new BigDecimal("117.71")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        // 2016-5-10
        busHandler.pushExpectedEvents(NextEvent.BCD_CHANGE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(5);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
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
        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 5), new LocalDate(2016, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("522.54")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 5), new LocalDate(2016, 6, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-217.70")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        subscriptionBaseInternalApi.updateBCD(baseEntitlement.getId(), 10,  null, internalCallContext);

        // 2016-5-10
        busHandler.pushExpectedEvents(NextEvent.BCD_CHANGE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(5);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 6, 1), new LocalDate(2016, 6, 10), InvoiceItemType.RECURRING, new BigDecimal("174.18")));
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
        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2017, 5, 1), new LocalDate(2017, 5, 10), InvoiceItemType.RECURRING, new BigDecimal("59.18")));
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

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
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

        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 7, 1), new LocalDate(2016, 7, 4), InvoiceItemType.RECURRING, new BigDecimal("200.00")));
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        invoiceChecker.checkInvoice(invoices.get(5).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        // 2016-7-1 : BP only
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(27);
        assertListenerStatus();

        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 7, 1), new LocalDate(2016, 8, 1), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        invoiceChecker.checkInvoice(invoices.get(6).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        // 2016-7-4 : AO only
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(3);
        assertListenerStatus();

        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 7, 4), new LocalDate(2016, 8, 4), InvoiceItemType.RECURRING, new BigDecimal("1999.95")));
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
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
        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_ADJUSTMENT);
        final BlockingState blockingState = new DefaultBlockingState(baseEntitlement.getId(), BlockingStateType.SUBSCRIPTION, "COURTESY_BLOCK", "company.a.b.c", true, true, true, null);
        subscriptionApi.addBlockingState(blockingState,  new LocalDate(2016, 5, 1),  ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();


        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 1), new LocalDate(2016, 6, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-249.95")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 16), new LocalDate(2016, 5, 16), InvoiceItemType.CBA_ADJ, new BigDecimal("249.95")));
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
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
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
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
        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 6, 1), new LocalDate(2016, 6, 15), InvoiceItemType.RECURRING, new BigDecimal("116.64")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        //  2016-6-15 : Finally we get the BCD_CHANGE event and start building for full monthly period
        busHandler.pushExpectedEvents(NextEvent.BCD_CHANGE, NextEvent.NULL_INVOICE,  NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(14);
        assertListenerStatus();
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 6, 15), new LocalDate(2016, 7, 15), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkInvoice(invoices.get(3).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();
    }

    @Test(groups = "slow")
    public void testBCDChangeFromFreePlanToPayingPlanNoTrial() throws Exception {
        final PlanPhaseSpecifier specNoTrial = new PlanPhaseSpecifier("Blowdart", BillingPeriod.MONTHLY, "notrial", null);
        testBCDChangeFromFreePlanToPayingPlan(specNoTrial);
    }

    @Test(groups = "slow")
    public void testBCDChangeFromFreePlanToPayingPlanWithTrial() throws Exception {
        // Change to the paying plan (alignment is START_OF_SUBSCRIPTION, but because we are already in an EVERGREEN phase, we will re-align with the EVERGREEN phase of the new 3-phases paying plan)
        final PlanPhaseSpecifier specWithTrial = new PlanPhaseSpecifier("Blowdart", BillingPeriod.MONTHLY, "DEFAULT", null);
        testBCDChangeFromFreePlanToPayingPlan(specWithTrial);
    }

    @Test(groups = "slow")
    public void testBCDChangeFromFreePlanToPayingPlanWithTrialAndCHANGE_OF_PLANPolicy30DaysMonth() throws Exception {
        final DateTime initialDate = new DateTime(2016, 4, 1, 0, 13, 42, 0, testTimeZone);
        clock.setTime(initialDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(0));
        assertNotNull(account);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Blowdart", BillingPeriod.MONTHLY, "notrial", null);

        // Price override of $0
        final List<PlanPhasePriceOverride> overrides = new ArrayList<PlanPhasePriceOverride>();
        overrides.add(new DefaultPlanPhasePriceOverride("blowdart-monthly-notrial-evergreen", account.getCurrency(), null, BigDecimal.ZERO, ImmutableList.<UsagePriceOverride>of()));
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        // BP creation : Will set Account BCD to the first (DateOfFirstRecurringNonZeroCharge is the subscription start date in this case)
        final UUID baseEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, overrides), "bundleExternalKey", null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement baseEntitlement = entitlementApi.getEntitlementForId(baseEntitlementId, callContext);

        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 4, 1), new LocalDate(2016, 5, 1), InvoiceItemType.RECURRING, BigDecimal.ZERO));

        // 2016-4-15
        clock.addDays(14);

        // Set next BCD to be the 15
        busHandler.pushExpectedEvents(NextEvent.BCD_CHANGE, NextEvent.INVOICE);
        subscriptionBaseInternalApi.updateBCD(baseEntitlement.getId(), 15, null, internalCallContext);
        assertListenerStatus();

        // Re-alignment invoice
        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 4, 1), new LocalDate(2016, 4, 15), InvoiceItemType.RECURRING, BigDecimal.ZERO),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 4, 15), new LocalDate(2016, 5, 15), InvoiceItemType.RECURRING, BigDecimal.ZERO));

        // Change to the paying plan (alignment is CHANGE_OF_PLAN: we end up in TRIAL)
        final PlanPhaseSpecifier specWithTrial = new PlanPhaseSpecifier("Blowdart", BillingPeriod.MONTHLY, "trial", null);
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE);
        baseEntitlement.changePlanOverrideBillingPolicy(new DefaultEntitlementSpecifier(specWithTrial), clock.getUTCToday(), BillingActionPolicy.IMMEDIATE, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // Trial invoice
        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 4, 15), null, InvoiceItemType.FIXED, BigDecimal.ZERO));

        // Verify next month (extra null invoice because of the original notification set on the 1st)
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.NULL_INVOICE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        // First paying invoice
        invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 15), new LocalDate(2016, 6, 15), InvoiceItemType.RECURRING, new BigDecimal("29.95")));

        // Verify next month
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 5, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 6, 15), new LocalDate(2016, 7, 15), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
    }

    @Test(groups = "slow")
    public void testBCDChangeFromFreePlanToPayingPlanWithTrialAndCHANGE_OF_PLANPolicy31DaysMonth() throws Exception {
        final DateTime initialDate = new DateTime(2016, 5, 1, 0, 13, 42, 0, testTimeZone);
        clock.setTime(initialDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(0));
        assertNotNull(account);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Blowdart", BillingPeriod.MONTHLY, "notrial", null);

        // Price override of $0
        final List<PlanPhasePriceOverride> overrides = new ArrayList<PlanPhasePriceOverride>();
        overrides.add(new DefaultPlanPhasePriceOverride("blowdart-monthly-notrial-evergreen", account.getCurrency(), null, BigDecimal.ZERO, ImmutableList.<UsagePriceOverride>of()));
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        // BP creation : Will set Account BCD to the first (DateOfFirstRecurringNonZeroCharge is the subscription start date in this case)
        final UUID baseEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, overrides), "bundleExternalKey", null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Account accountBCD = accountUserApi.getAccountById(account.getId(), callContext);
        assertEquals(accountBCD.getBillCycleDayLocal(), (Integer) 1);
        final Entitlement baseEntitlement = entitlementApi.getEntitlementForId(baseEntitlementId, callContext);

        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 1), new LocalDate(2016, 6, 1), InvoiceItemType.RECURRING, BigDecimal.ZERO));

        // 2016-5-15
        clock.addDays(14);

        // Set next BCD to be the 14
        subscriptionBaseInternalApi.updateBCD(baseEntitlement.getId(), 14, null, internalCallContext);
        // No bus event, no invoice expected
        assertListenerStatus();

        // Change to the paying plan (alignment is CHANGE_OF_PLAN: we end up in TRIAL)
        final PlanPhaseSpecifier specWithTrial = new PlanPhaseSpecifier("Blowdart", BillingPeriod.MONTHLY, "trial", null);
        busHandler.pushExpectedEvents(NextEvent.CHANGE,  NextEvent.INVOICE);
        baseEntitlement.changePlanOverrideBillingPolicy(new DefaultEntitlementSpecifier(specWithTrial), clock.getUTCToday(), BillingActionPolicy.IMMEDIATE, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // Trial invoice (with re-alignment invoice item from free plan)
        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 1), new LocalDate(2016, 5, 15), InvoiceItemType.RECURRING, BigDecimal.ZERO),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 15), null, InvoiceItemType.FIXED, BigDecimal.ZERO));

        // Verify next month (extra null invoice because of the original notification set on the 1st)
        busHandler.pushExpectedEvents(NextEvent.BCD_CHANGE, NextEvent.PHASE, NextEvent.NULL_INVOICE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        // First paying invoice
        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 6, 14), new LocalDate(2016, 7, 14), InvoiceItemType.RECURRING, new BigDecimal("29.95")));

        // Verify next month
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 7, 14), new LocalDate(2016, 8, 14), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
    }

    private void testBCDChangeFromFreePlanToPayingPlan(final PlanPhaseSpecifier toSpec) throws Exception {
        final DateTime initialDate = new DateTime(2016, 4, 1, 0, 13, 42, 0, testTimeZone);
        clock.setTime(initialDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(0));
        assertNotNull(account);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Blowdart", BillingPeriod.MONTHLY, "notrial", null);

        // Price override of $0
        final List<PlanPhasePriceOverride> overrides = new ArrayList<PlanPhasePriceOverride>();
        overrides.add(new DefaultPlanPhasePriceOverride("blowdart-monthly-notrial-evergreen", account.getCurrency(), null, BigDecimal.ZERO, ImmutableList.<UsagePriceOverride>of()));
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        // BP creation : Will set Account BCD to the first (DateOfFirstRecurringNonZeroCharge is the subscription start date in this case)
        final UUID baseEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, overrides), "bundleExternalKey", null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement baseEntitlement = entitlementApi.getEntitlementForId(baseEntitlementId, callContext);

        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 4, 1), new LocalDate(2016, 5, 1), InvoiceItemType.RECURRING, BigDecimal.ZERO));

        // 2016-4-15
        clock.addDays(14);

        // Set next BCD to be the 15
        busHandler.pushExpectedEvents(NextEvent.BCD_CHANGE, NextEvent.INVOICE);
        subscriptionBaseInternalApi.updateBCD(baseEntitlement.getId(), 15, null, internalCallContext);
        assertListenerStatus();

        // Re-alignment invoice
        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 4, 1), new LocalDate(2016, 4, 15), InvoiceItemType.RECURRING, BigDecimal.ZERO),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 4, 15), new LocalDate(2016, 5, 15), InvoiceItemType.RECURRING, BigDecimal.ZERO));

        // Change to the paying plan
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        baseEntitlement.changePlanOverrideBillingPolicy(new DefaultEntitlementSpecifier(toSpec), clock.getUTCToday(), BillingActionPolicy.IMMEDIATE, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // First paying invoice
        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 4, 15), new LocalDate(2016, 5, 15), InvoiceItemType.RECURRING, new BigDecimal("29.95")));

        // Verify next month (null invoice because of the original notification set on the 1st)
        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 15), new LocalDate(2016, 6, 15), InvoiceItemType.RECURRING, new BigDecimal("29.95")));

        // Verify next month
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 5, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 6, 15), new LocalDate(2016, 7, 15), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
    }

    @Test(groups = "slow")
    public void testWithBCDOnOperations() throws Exception {

        final DateTime initialDate = new DateTime(2018, 6, 21, 0, 13, 42, 0, testTimeZone);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(21));
        assertNotNull(account);


        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("pistol-monthly-notrial");


        busHandler.pushExpectedEvents( NextEvent.CREATE, NextEvent.BLOCK, NextEvent.BCD_CHANGE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);

        // We will realign the BCD on the 15 as we create the subscription - ignoring the account setting on 21.
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, 15, null, null), null, null, null, false, false, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2018, 6, 21), new LocalDate(2018, 7, 15), InvoiceItemType.RECURRING, new BigDecimal("15.96")));


        // Verify next month
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDays(24);  // 2018-7-15
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2018, 7, 15), new LocalDate(2018, 8, 15), InvoiceItemType.RECURRING, new BigDecimal("19.95")));


        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);

        final PlanPhaseSpecifier spec2 = new PlanPhaseSpecifier("blowdart-monthly-notrial");

        // Change plan EOT
        // We will now realign the BCD on the 21 as we change the plan for the subscription.
        entitlement.changePlan(new DefaultEntitlementSpecifier(spec2, 21, null, null), ImmutableList.<PluginProperty>of(), callContext);

        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.BCD_CHANGE, NextEvent.NULL_INVOICE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();


        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2018, 8, 15), new LocalDate(2018, 8, 21), InvoiceItemType.RECURRING, new BigDecimal("5.80")));


    }

    @Test(groups = "slow")
    public void testBCDChangeForConsumableInArrearPlan() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        // Create BASE subscription
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.ANNUAL, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        // Check bundle after BP got created otherwise we get an error from auditApi.
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        assertListenerStatus();

        // Add ADD_ON on the same day
        final DefaultEntitlement aoSubscription = addAOEntitlementAndCheckForCompletion(bpSubscription.getBundleId(), "Bullets", ProductCategory.ADD_ON, BillingPeriod.NO_BILLING_PERIOD, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        assertListenerStatus();
        assertNull(bpSubscription.getSubscriptionBase().getChargedThroughDate());

        // Record usage for first month
        recordUsageData(aoSubscription.getId(), "tracking-1", "bullets", new LocalDate(2012, 4, 5), BigDecimal.valueOf(100L), callContext);
        recordUsageData(aoSubscription.getId(), "tracking-2", "bullets", new LocalDate(2012, 4, 15), BigDecimal.valueOf(100L), callContext);

        // 2012-05-01
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2013, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.USAGE, new BigDecimal("5.90")));

        final DateTime bpExpectedCTD = new DateTime("2013-05-01T00:00:00.000Z");
        assertEquals(subscriptionBaseInternalApiApi.getSubscriptionFromId(bpSubscription.getId(), internalCallContext).getChargedThroughDate().compareTo(bpExpectedCTD), 0);
        DateTime aoExpectedCTD = new DateTime("2012-05-01T00:00:00.000Z");
        assertEquals(subscriptionBaseInternalApiApi.getSubscriptionFromId(aoSubscription.getId(), internalCallContext).getChargedThroughDate().compareTo(aoExpectedCTD), 0);

        // 2012-05-05
        clock.addDays(4);
        assertListenerStatus();

        // Set BCD to be the 5
        busHandler.pushExpectedEvents(NextEvent.BCD_CHANGE, NextEvent.INVOICE);
        subscriptionBaseInternalApi.updateBCD(aoSubscription.getId(), 5, null, internalCallContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 5, 5), InvoiceItemType.USAGE, BigDecimal.ZERO));

        // Record usage for second month
        recordUsageData(aoSubscription.getId(), "tracking-3", "bullets", new LocalDate(2012, 5, 5), BigDecimal.valueOf(100L), callContext);
        recordUsageData(aoSubscription.getId(), "tracking-4", "bullets", new LocalDate(2012, 6, 4), BigDecimal.valueOf(100L), callContext);

        // 2012-06-01
        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE);
        clock.addDays(27);
        assertListenerStatus();

        // 2012-06-05
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(4);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 5), new LocalDate(2012, 6, 5), InvoiceItemType.USAGE, new BigDecimal("5.90")));

        aoExpectedCTD = new DateTime("2012-06-05T00:00:00.000Z");
        assertEquals(subscriptionBaseInternalApiApi.getSubscriptionFromId(aoSubscription.getId(), internalCallContext).getChargedThroughDate().compareTo(aoExpectedCTD), 0);
    }


    @Test(groups = "slow")
    public void testWithOverrideFixedPriceAndNewBCD() throws Exception {

        final DateTime initialDate = new DateTime(2016, 4, 1, 0, 13, 42, 0, testTimeZone);
        clock.setTime(initialDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(0));
        assertNotNull(account);

        // Price override of $10 for fixed and $0 for recurring
        // Override BCD as well
        final Integer billCycleDay = 1;
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Blowdart", BillingPeriod.QUARTERLY, "notrial", null);
        final List<PlanPhasePriceOverride> overrides = new ArrayList<PlanPhasePriceOverride>();
        overrides.add(new DefaultPlanPhasePriceOverride("blowdart-quarterly-notrial-evergreen", account.getCurrency(), BigDecimal.TEN, BigDecimal.ZERO, ImmutableList.<UsagePriceOverride>of()));
        final DefaultEntitlementSpecifier entitlementSpecifier = new DefaultEntitlementSpecifier(spec, billCycleDay, UUID.randomUUID().toString(), overrides);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.BCD_CHANGE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        entitlementApi.createBaseEntitlement(account.getId(), entitlementSpecifier, "134582864", null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 4, 1), null, InvoiceItemType.FIXED, BigDecimal.TEN),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 4, 1), new LocalDate(2016, 7, 1), InvoiceItemType.RECURRING, BigDecimal.ZERO));

    }


    @Test(groups = "slow")
    public void testBCDUpgradeMigrationPath_0_20_to_0_22() throws Exception {

        // Change to false to verify new behavior
        final boolean checkMigrationFrom_0_20_to_0_22 = true;

        final DateTime initialDate = new DateTime(2018, 6, 21, 0, 13, 42, 0, testTimeZone);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(21));
        assertNotNull(account);


        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("pistol-monthly-notrial");


        busHandler.pushExpectedEvents( NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);

        // We will realign the BCD on the 15 as we create the subscription - ignoring the account setting on 21.
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, null), null, null, null, false, false, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        final Invoice firstInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2018, 6, 21), new LocalDate(2018, 7, 21), InvoiceItemType.RECURRING, new BigDecimal("19.95")));



        // Set BCD to be the 1
        subscriptionBaseInternalApi.updateBCD(entitlementId, 1, new LocalDate(2018, 7, 1), internalCallContext);

        if (checkMigrationFrom_0_20_to_0_22) {
            final InvoiceModelDao invoiceForRepair_0_20 = new InvoiceModelDao(UUID.randomUUID(), clock.getUTCNow(), account.getId(), null, clock.getUTCToday(), clock.getUTCToday(), Currency.USD, false, InvoiceStatus.COMMITTED, false);

            final UUID originalRecuringItemId = firstInvoice.getInvoiceItems().get(0).getId();
            final InvoiceItemModelDao repair = new InvoiceItemModelDao(invoiceForRepair_0_20.getCreatedDate(), InvoiceItemType.REPAIR_ADJ, invoiceForRepair_0_20.getId(), account.getId(),
                                                                       UUID.randomUUID(), entitlementId, null, null, null, null, null,
                                                                       null, new LocalDate(2018, 7, 1), new LocalDate(2018, 7, 21), new BigDecimal("-13.30"), new BigDecimal("-13.30"), account.getCurrency(), originalRecuringItemId);

            final InvoiceItemModelDao recurring = new InvoiceItemModelDao(invoiceForRepair_0_20.getCreatedDate(), InvoiceItemType.RECURRING, invoiceForRepair_0_20.getId(), account.getId(),
                                                                          UUID.randomUUID(), entitlementId, "", "Pistol", "pistol-monthly-notrial", "pistol-monthly-notrial-evergreen", null,
                                                                          null, new LocalDate(2018, 7, 1), new LocalDate(2018, 8, 1), new BigDecimal("19.95"), new BigDecimal("19.95"), account.getCurrency(), null);


            invoiceForRepair_0_20.addInvoiceItem(repair);
            invoiceForRepair_0_20.addInvoiceItem(recurring);


            busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
            insertInvoiceItems(invoiceForRepair_0_20);
            assertListenerStatus();
        }

        // With existing 0_20 data, nothing will be re-generated -> shows new behavior is compatible with old data
        if (checkMigrationFrom_0_20_to_0_22) {
            busHandler.pushExpectedEvents(NextEvent.BCD_CHANGE, NextEvent.NULL_INVOICE);
        } else {
            busHandler.pushExpectedEvents(NextEvent.BCD_CHANGE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        }

        // Verify next month
        clock.addDays(10);  // 2018-7-1
        assertListenerStatus();

        if (!checkMigrationFrom_0_20_to_0_22) {
            invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                        new ExpectedInvoiceItemCheck(new LocalDate(2018, 7, 21), new LocalDate(2018, 8, 1), InvoiceItemType.RECURRING, new BigDecimal("7.08")));
        }

    }
}
