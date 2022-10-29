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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.DefaultPlanPhasePriceOverride;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.model.CreditAdjInvoiceItem;
import org.killbill.billing.invoice.model.ExternalChargeInvoiceItem;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBase;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestIntegrationInvoice extends TestIntegrationBase {

    @Test(groups = "slow")
    public void testWithUserCreditDeletion() throws Exception {
        final DateTime initialCreationDate = new DateTime(2015, 5, 15, 0, 0, 0, 0, testTimeZone);
        // set clock to the initial start date
        clock.setTime(initialCreationDate);

        final int billingDay = 14;

        log.info("Beginning test with BCD of " + billingDay);
        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(billingDay));

        add_AUTO_PAY_OFF_Tag(account.getId(), ObjectType.ACCOUNT);

        createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        // Move through time and verify we get the same invoice
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE);
        clock.addDays(30);
        assertListenerStatus();

        Collection<Invoice> invoices = invoiceUserApi.getUnpaidInvoicesByAccountId(account.getId(), null, new LocalDate(clock.getUTCNow(), account.getTimeZone()), callContext);
        assertEquals(invoices.size(), 1);

        final UUID unpaidInvoiceId = invoices.iterator().next().getId();
        final Invoice firstInvoice = invoiceUserApi.getInvoice(unpaidInvoiceId, callContext);
        assertTrue(firstInvoice.getBalance().compareTo(new BigDecimal("249.95")) == 0);

        final BigDecimal accountBalance1 = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance1.compareTo(new BigDecimal("249.95")) == 0);

        // Add the 1000.00 credit
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_ADJUSTMENT);
        final InvoiceItem inputCredit = new CreditAdjInvoiceItem(null, account.getId(), new LocalDate(clock.getUTCNow(), account.getTimeZone()), "some description", new BigDecimal("1000.00"), account.getCurrency(), null);
        final List<InvoiceItem> creditItems = invoiceUserApi.insertCredits(account.getId(), new LocalDate(clock.getUTCNow(), account.getTimeZone()), List.of(inputCredit), true, null, callContext);
        assertListenerStatus();

        Assert.assertEquals(creditItems.size(), 1);
        final InvoiceItem creditItem = creditItems.get(0);
        final Invoice creditInvoice = invoiceUserApi.getInvoice(creditItem.getInvoiceId(), callContext);
        final InvoiceItem cbaGenItem = creditInvoice.getInvoiceItems().stream()
                .filter(invoiceItem -> invoiceItem.getInvoiceItemType() == InvoiceItemType.CBA_ADJ)
                .findFirst().orElse(null);
        Assert.assertNotNull(cbaGenItem);


        // Next month invoice
        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        clock.addMonths(1);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 4);

        // Verify credit has been used on 2 previous invoices and we still have some left
        final BigDecimal accountBalance2 = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance2.compareTo(new BigDecimal("-500.10")) == 0);

        // We expect 3 events as we modified 3 invoices
        busHandler.pushExpectedEvents(NextEvent.INVOICE_ADJUSTMENT, NextEvent.INVOICE_ADJUSTMENT, NextEvent.INVOICE_ADJUSTMENT);
        invoiceUserApi.deleteCBA(account.getId(), creditItem.getInvoiceId(), cbaGenItem.getId(), callContext);
        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testApplyCreditOnExistingBalance() throws Exception {
        final DateTime initialCreationDate = new DateTime(2015, 5, 15, 0, 0, 0, 0, testTimeZone);
        // set clock to the initial start date
        clock.setTime(initialCreationDate);

        final int billingDay = 14;

        log.info("Beginning test with BCD of " + billingDay);
        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(billingDay));

        add_AUTO_PAY_OFF_Tag(account.getId(), ObjectType.ACCOUNT);

        createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        // Move through time and verify we get the same invoice
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE);
        clock.addDays(30);
        assertListenerStatus();

        final Collection<Invoice> invoices = invoiceUserApi.getUnpaidInvoicesByAccountId(account.getId(), null, new LocalDate(clock.getUTCNow(), account.getTimeZone()), callContext);
        assertEquals(invoices.size(), 1);

        final UUID unpaidInvoiceId = invoices.iterator().next().getId();
        final Invoice unpaidInvoice = invoiceUserApi.getInvoice(unpaidInvoiceId, callContext);
        assertTrue(unpaidInvoice.getBalance().compareTo(new BigDecimal("249.95")) == 0);

        final BigDecimal accountBalance1 = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance1.compareTo(new BigDecimal("249.95")) == 0);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_ADJUSTMENT);
        final InvoiceItem inputCredit = new CreditAdjInvoiceItem(null, account.getId(), new LocalDate(clock.getUTCNow(), account.getTimeZone()), "some description", new BigDecimal("300"), account.getCurrency(), null);
        invoiceUserApi.insertCredits(account.getId(), new LocalDate(clock.getUTCNow(), account.getTimeZone()), List.of(inputCredit), true, null, callContext);
        assertListenerStatus();

        final BigDecimal accountBalance2 = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance2.compareTo(new BigDecimal("-50.05")) == 0);

        final Invoice unpaidInvoice2 = invoiceUserApi.getInvoice(unpaidInvoiceId, callContext);
        assertTrue(unpaidInvoice2.getBalance().compareTo(BigDecimal.ZERO) == 0);

        remove_AUTO_PAY_OFF_Tag(account.getId(), ObjectType.ACCOUNT);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(31);
        assertListenerStatus();

        final BigDecimal accountBalance3 = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance3.compareTo(BigDecimal.ZERO) == 0);

        final List<Payment> payments = paymentApi.getAccountPayments(account.getId(), false, false, Collections.emptyList(), callContext);
        assertEquals(payments.size(), 1);

        final Payment payment = payments.get(0);
        assertTrue(payment.getPurchasedAmount().compareTo(new BigDecimal("199.90")) == 0);
    }

    @Test(groups = "slow")
    public void testDraftInvoice() throws Exception {

        final int billingDay = 14;
        final DateTime initialCreationDate = new DateTime(2015, 5, 15, 0, 0, 0, 0, testTimeZone);
        // set clock to the initial start date
        clock.setTime(initialCreationDate);

        log.info("Beginning test with BCD of " + billingDay);
        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(billingDay));

        int invoiceItemCount = 1;

        DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        DefaultSubscriptionBase subscription = subscriptionDataFromSubscription(baseEntitlement.getSubscriptionBase());

        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 6, 14), new LocalDate(2015, 7, 14), InvoiceItemType.RECURRING, new BigDecimal("249.95")));

        // Move through time and verify we get the same invoice
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        // This will verify that the upcoming invoice notification is found and the invoice is generated at the right date, with correct items
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 7, 14), new LocalDate(2015, 8, 14), InvoiceItemType.RECURRING, new BigDecimal("249.95")));

        // add create external charge
        final LocalDate date = clock.getToday(account.getTimeZone());
        final List<InvoiceItem> invoiceItemList = new ArrayList<InvoiceItem>();
        ExternalChargeInvoiceItem item = new ExternalChargeInvoiceItem(null, account.getId(), subscription.getBundleId(), "", date, date, BigDecimal.TEN, account.getCurrency(), null);
        invoiceItemList.add(item);
        final List<InvoiceItem> draftInvoiceItems = invoiceUserApi.insertExternalCharges(account.getId(), date, invoiceItemList, false, null, callContext);

        // add expected invoice
        final List<ExpectedInvoiceItemCheck> expectedDraftInvoices = new ArrayList<ExpectedInvoiceItemCheck>();
        expectedDraftInvoices.add(new ExpectedInvoiceItemCheck(InvoiceItemType.EXTERNAL_CHARGE, BigDecimal.TEN));

        // Move through time and verify invoices
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);

        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, expectedDraftInvoices);
        invoiceChecker.checkInvoice(invoices.get(3).getId(), callContext, expectedInvoices);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceUserApi.commitInvoice(draftInvoiceItems.get(0).getInvoiceId(), callContext);
        assertListenerStatus();

        final List<Payment> accountPayments = paymentApi.getAccountPayments(account.getId(), false, false, null, callContext);
        assertEquals(accountPayments.size(), 3);
        assertEquals(accountPayments.get(2).getPurchasedAmount(), new BigDecimal("10.00"));
    }

    @Test(groups = "slow", description = "See https://github.com/killbill/killbill/issues/127#issuecomment-292445089")
    public void testIntegrationWithBCDLargerThanEndMonth() throws Exception {

        final int billingDay = 31;
        final DateTime initialCreationDate = new DateTime(2017, 01, 31, 0, 0, 0, 0, testTimeZone);
        // set clock to the initial start date
        clock.setTime(initialCreationDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(billingDay));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Blowdart", BillingPeriod.MONTHLY, "notrial", null);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), "bundleExternalKey", null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        // 2017-02-28
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        // 2017-03-31
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        clock.addDays(3);
        assertListenerStatus();

        // 2017-04-30
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();
    }


    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/783")
    public void testIntegrationWithRecurringFreePlan() throws Exception {
        final DateTime initialCreationDate = new DateTime(2017, 1, 1, 0, 0, 0, 0, testTimeZone);
        // set clock to the initial start date
        clock.setTime(initialCreationDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Blowdart", BillingPeriod.MONTHLY, "notrial", null);

        // Price override of $0
        final List<PlanPhasePriceOverride> overrides = new ArrayList<PlanPhasePriceOverride>();
        overrides.add(new DefaultPlanPhasePriceOverride("blowdart-monthly-notrial-evergreen", account.getCurrency(), null, BigDecimal.ZERO, Collections.emptyList()));
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(),  new DefaultEntitlementSpecifier(spec, null, null, overrides), "bundleExternalKey", null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, false, callContext);

        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2017, 1, 1), new LocalDate(2017, 2, 1), InvoiceItemType.RECURRING, BigDecimal.ZERO));

        // 2017-02-01
        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        clock.addMonths(1);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2017, 2, 1), new LocalDate(2017, 3, 1), InvoiceItemType.RECURRING, BigDecimal.ZERO));

        // Do the change mid-month so the repair triggers the bug in https://github.com/killbill/killbill/issues/783
        entitlement.changePlanWithDate( new DefaultEntitlementSpecifier(spec), new LocalDate("2017-02-15"), Collections.emptyList(), callContext);
        assertListenerStatus();

        // 2017-02-15
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDays(15);
        assertListenerStatus();

        // Note: no repair
        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2017, 2, 1), new LocalDate(2017, 3, 1), InvoiceItemType.RECURRING, BigDecimal.ZERO));

        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2017, 2, 1), new LocalDate(2017, 2, 15), InvoiceItemType.RECURRING, BigDecimal.ZERO),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2017, 2, 15), new LocalDate(2017, 3, 1), InvoiceItemType.RECURRING, new BigDecimal("14.98")));

        // 2017-03-01
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDays(15);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2017, 3, 1), new LocalDate(2017, 4, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")));

        // 2017-04-01
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 5, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2017, 4, 1), new LocalDate(2017, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
    }





    @Test(groups = "slow")
    public void testExternalChargeInTheFuture() throws Exception {

        final int billingDay = 1;
        final DateTime initialCreationDate = new DateTime(2019, 1, 1, 0, 0, 0, 0, testTimeZone);
        // set clock to the initial start date
        clock.setTime(initialCreationDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(billingDay));


        // Create external charge with an effective date a year from now
        final LocalDate chargeEffectiveDate = clock.getToday(account.getTimeZone()).plusYears(1);
        final List<InvoiceItem> invoiceItemList = new ArrayList<InvoiceItem>();
        ExternalChargeInvoiceItem item = new ExternalChargeInvoiceItem(null, account.getId(), null, "", chargeEffectiveDate, chargeEffectiveDate, BigDecimal.TEN, account.getCurrency(), null);
        invoiceItemList.add(item);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        final List<InvoiceItem> chargeItems = invoiceUserApi.insertExternalCharges(account.getId(), chargeEffectiveDate, invoiceItemList, true, null, callContext);
        assertListenerStatus();

        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();
        expectedInvoices.add(new ExpectedInvoiceItemCheck(chargeEffectiveDate, chargeEffectiveDate, InvoiceItemType.EXTERNAL_CHARGE, BigDecimal.TEN));

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        invoiceChecker.checkInvoice(invoices.get(0).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();



        DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        DefaultSubscriptionBase subscription = subscriptionDataFromSubscription(baseEntitlement.getSubscriptionBase());

        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2019, 1, 1), new LocalDate(2019, 2, 1), InvoiceItemType.RECURRING, new BigDecimal("249.95")));

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();
    }


}
