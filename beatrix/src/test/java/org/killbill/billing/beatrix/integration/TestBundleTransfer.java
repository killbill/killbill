/*
 * Copyright 2010-2013 Ning, Inc.
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
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.beatrix.util.PaymentChecker.ExpectedPaymentCheck;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.Subscription;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class TestBundleTransfer extends TestIntegrationBase {

    @Test(groups = "slow")
    public void testBundleTransferWithBPAnnualOnly() throws Exception {
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        final DateTime initialDate = new DateTime(2012, 4, 1, 0, 15, 42, 0, testTimeZone);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(3));

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
        clock.addDays(40);
        assertListenerStatus();

        // BUNDLE TRANSFER
        final Account newAccount = createAccountWithNonOsgiPaymentMethod(getAccountData(17));

        busHandler.pushExpectedEvents(NextEvent.TRANSFER, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        transferApi.transferBundle(account.getId(), newAccount.getId(), "externalKey", clock.getUTCNow(), false, false, callContext);
        assertListenerStatus();

        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(newAccount.getId(), false, false, callContext);
        assertEquals(invoices.size(), 1);

        final List<InvoiceItem> invoiceItems = invoices.get(0).getInvoiceItems();
        assertEquals(invoiceItems.size(), 1);
        final InvoiceItem theItem = invoiceItems.get(0);
        assertTrue(theItem.getStartDate().compareTo(new LocalDate(2012, 5, 11)) == 0);
        assertTrue(theItem.getEndDate().compareTo(new LocalDate(2013, 5, 11)) == 0);
        assertTrue(theItem.getAmount().compareTo(new BigDecimal("2399.9500")) == 0);

        checkNoMoreInvoiceToGenerate(account);
    }

    @Test(groups = "slow")
    public void testBundleTransferWithBPMonthlyOnly() throws Exception {
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        final DateTime initialDate = new DateTime(2012, 4, 1, 0, 15, 42, 0, testTimeZone);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(0));

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;
        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //
        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);
        assertListenerStatus();
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext).size(), 1);

        assertEquals(bpEntitlement.getSubscriptionBase().getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.MONTHLY);

        // Move out of trials for interesting invoices adjustments
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(32);
        assertListenerStatus();

        // BUNDLE TRANSFER
        final Account newAccount = createAccountWithNonOsgiPaymentMethod(getAccountData(0));

        busHandler.pushExpectedEvents(NextEvent.TRANSFER, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        transferApi.transferBundle(account.getId(), newAccount.getId(), "externalKey", clock.getUTCNow(), false, false, callContext);
        assertListenerStatus();

        // Verify the BCD of the new account
        final Integer oldBCD = accountUserApi.getAccountById(account.getId(), callContext).getBillCycleDayLocal();
        final Integer newBCD = accountUserApi.getAccountById(newAccount.getId(), callContext).getBillCycleDayLocal();
        assertEquals(oldBCD, (Integer) 1);
        // Day of the transfer
        assertEquals(newBCD, (Integer) 3);

        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(newAccount.getId(), false, false, callContext);
        assertEquals(invoices.size(), 1);

        final List<InvoiceItem> invoiceItems = invoices.get(0).getInvoiceItems();
        assertEquals(invoiceItems.size(), 1);
        final InvoiceItem theItem = invoiceItems.get(0);
        assertTrue(theItem.getStartDate().compareTo(new LocalDate(2012, 5, 3)) == 0);
        assertTrue(theItem.getEndDate().compareTo(new LocalDate(2012, 6, 3)) == 0);
        assertTrue(theItem.getAmount().compareTo(new BigDecimal("249.95")) == 0);

        checkNoMoreInvoiceToGenerate(account);

    }

    @Test(groups = "slow")
    public void testBundleTransferWithBPMonthlyOnlyWIthCancellationImm() throws Exception {
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        final DateTime initialDate = new DateTime(2012, 4, 1, 0, 15, 42, 0, testTimeZone);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(9));

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;
        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //
        final PlanPhaseSpecifier bpPlanPhaseSpecifier = new PlanPhaseSpecifier(productName, term, planSetName, null);

        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);
        assertListenerStatus();
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext).size(), 1);

        assertEquals(bpEntitlement.getSubscriptionBase().getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.MONTHLY);

        // Move out of trials for interesting invoices adjustments
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(32);
        assertListenerStatus();

        // BUNDLE TRANSFER
        final Account newAccount = createAccountWithNonOsgiPaymentMethod(getAccountData(15));

        busHandler.pushExpectedEvents(NextEvent.CANCEL, NextEvent.TRANSFER, NextEvent.INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        transferApi.transferBundle(account.getId(), newAccount.getId(), "externalKey", clock.getUTCNow(), false, true, callContext);
        assertListenerStatus();

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 3);

        // CHECK OLD AND NEW ACCOUNTS ITEMS
        ImmutableList<ExpectedInvoiceItemCheck> toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 5, 9), InvoiceItemType.RECURRING, new BigDecimal("66.65")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);


        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 3), new LocalDate(2012, 5, 9), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-49.99")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 3), new LocalDate(2012, 5, 3), InvoiceItemType.CBA_ADJ, new BigDecimal("49.99")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, toBeChecked);

        // CHECK NEW ACCOUNT ITEMS
        invoices = invoiceUserApi.getInvoicesByAccount(newAccount.getId(), false, false, callContext);
        assertEquals(invoices.size(), 1);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 3), new LocalDate(2012, 5, 15), InvoiceItemType.RECURRING, new BigDecimal("99.98")));
        invoiceChecker.checkInvoice(invoices.get(0).getId(), callContext, toBeChecked);

        checkNoMoreInvoiceToGenerate(account);

    }

    @Test(groups = "slow", description = "Test entitlement-level transfer with add-on")
    public void testBundleTransferWithAddOn() throws Exception {
        final LocalDate startDate = new LocalDate(2012, 4, 1);
        clock.setDay(startDate);

        // Share the BCD on both accounts for simplicity
        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));
        final Account newAccount = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final BillingPeriod term = BillingPeriod.MONTHLY;
        final String bpProductName = "Shotgun";
        final String aoProductName = "Telescopic-Scope";

        // Create the base plan
        final String bundleExternalKey = UUID.randomUUID().toString();
        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), bundleExternalKey, bpProductName, ProductCategory.BASE, term,
                                                                                            NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        subscriptionChecker.checkSubscriptionCreated(bpEntitlement.getId(), internalCallContext);
        final Invoice firstInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));

        // Create the add-on
        final DefaultEntitlement aoEntitlement = addAOEntitlementAndCheckForCompletion(bpEntitlement.getBundleId(), aoProductName, ProductCategory.ADD_ON, term,
                                                                                       NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        final Invoice secondInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("399.95")));
        paymentChecker.checkPayment(account.getId(), 1, callContext, new ExpectedPaymentCheck(new LocalDate(2012, 4, 1), new BigDecimal("399.95"), TransactionStatus.SUCCESS, secondInvoice.getId(), Currency.USD));

        // Move past the phase for simplicity
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.PHASE, NextEvent.NULL_INVOICE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();
        final Invoice thirdInvoice = invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("999.95")),
                                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        paymentChecker.checkPayment(account.getId(), 2, callContext, new ExpectedPaymentCheck(new LocalDate(2012, 5, 1), new BigDecimal("1249.90"), TransactionStatus.SUCCESS, thirdInvoice.getId(), Currency.USD));

        // Align the transfer on the BCD to make pro-rations easier
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        // Move a bit the time to make sure notifications kick in
        clock.setTime(new DateTime(2012, 6, 1, 1, 0, DateTimeZone.UTC));
        assertListenerStatus();
        final Invoice fourthInvoice = invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                                                  new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), new LocalDate(2012, 7, 1), InvoiceItemType.RECURRING, new BigDecimal("999.95")),
                                                                  new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), new LocalDate(2012, 7, 1), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        paymentChecker.checkPayment(account.getId(), 3, callContext, new ExpectedPaymentCheck(new LocalDate(2012, 6, 1), new BigDecimal("1249.90"), TransactionStatus.SUCCESS, fourthInvoice.getId(), Currency.USD));

        final DateTime now = clock.getUTCNow();
        final LocalDate transferDay = now.toLocalDate();

        busHandler.pushExpectedEvents(NextEvent.CANCEL, NextEvent.CANCEL, NextEvent.BLOCK, NextEvent.BLOCK, NextEvent.TRANSFER, NextEvent.TRANSFER, NextEvent.BLOCK, NextEvent.BLOCK,  NextEvent.NULL_INVOICE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        final UUID newBundleId = entitlementApi.transferEntitlements(account.getId(), newAccount.getId(), bundleExternalKey, transferDay, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // Check the last 2 invoices on the old account
        invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), new LocalDate(2012, 7, 1), InvoiceItemType.RECURRING, new BigDecimal("999.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), new LocalDate(2012, 7, 1), InvoiceItemType.RECURRING, new BigDecimal("249.95")));

        invoiceChecker.checkInvoice(account.getId(), 5, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), new LocalDate(2012, 7, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-999.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), new LocalDate(2012, 7, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-249.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), new LocalDate(2012, 6, 1), InvoiceItemType.CBA_ADJ, new BigDecimal("1249.90")));

        // Check the first invoice and payment on the new account
        final Invoice firstInvoiceNewAccount = invoiceChecker.checkInvoice(newAccount.getId(), 1, callContext,
                                                                           new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), new LocalDate(2012, 7, 1), InvoiceItemType.RECURRING, new BigDecimal("999.95")),
                                                                           new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), new LocalDate(2012, 7, 1), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        paymentChecker.checkPayment(newAccount.getId(), 1, callContext, new ExpectedPaymentCheck(new LocalDate(2012, 6, 1), new BigDecimal("1249.90"), TransactionStatus.SUCCESS, firstInvoiceNewAccount.getId(), Currency.USD));

        // Check entitlements and subscriptions on the old account
        final List<Entitlement> oldEntitlements = entitlementApi.getAllEntitlementsForBundle(bpEntitlement.getBundleId(), callContext);
        Assert.assertEquals(oldEntitlements.size(), 2);
        for (final Entitlement entitlement : oldEntitlements) {
            final Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlement.getId(), callContext);
            Assert.assertEquals(subscription.getEffectiveStartDate(), startDate);
            Assert.assertEquals(subscription.getEffectiveEndDate(), transferDay);
            Assert.assertEquals(subscription.getBillingStartDate(), startDate);
            Assert.assertEquals(subscription.getBillingEndDate(), transferDay);
        }

        // Check entitlements and subscriptions on the new account
        final List<Entitlement> newEntitlements = entitlementApi.getAllEntitlementsForBundle(newBundleId, callContext);
        Assert.assertEquals(newEntitlements.size(), 2);
        for (final Entitlement entitlement : newEntitlements) {
            final Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlement.getId(), callContext);
            Assert.assertEquals(subscription.getEffectiveStartDate(), transferDay);
            Assert.assertNull(subscription.getEffectiveEndDate());
            Assert.assertEquals(subscription.getBillingStartDate(), transferDay);
            Assert.assertNull(subscription.getBillingEndDate());
        }

        checkNoMoreInvoiceToGenerate(account);
    }
}
