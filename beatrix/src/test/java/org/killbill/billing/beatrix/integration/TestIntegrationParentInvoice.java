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
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.PlanSpecifier;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.api.InvoiceStatus;
import org.killbill.billing.payment.api.Payment;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestIntegrationParentInvoice extends TestIntegrationBase {



    @Test(groups = "slow")
    public void testParentInvoiceGeneration() throws Exception {

        final int billingDay = 14;
        final DateTime initialCreationDate = new DateTime(2015, 5, 15, 0, 0, 0, 0, testTimeZone);
        // set clock to the initial start date
        clock.setTime(initialCreationDate);

        log.info("Beginning test with BCD of " + billingDay);
        final Account parentAccount = createAccountWithNonOsgiPaymentMethod(getAccountData(billingDay));
        final Account child1Account = createAccountWithNonOsgiPaymentMethod(getChildAccountData(billingDay, parentAccount.getId(), true));
        final Account child2Account = createAccountWithNonOsgiPaymentMethod(getChildAccountData(billingDay, parentAccount.getId(), true));

        // CREATE SUBSCRIPTIONS AND EXPECT BOTH EVENTS EACH: NextEvent.CREATE NextEvent.INVOICE
        createBaseEntitlementAndCheckForCompletion(child1Account.getId(), "bundleKey1", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        createBaseEntitlementAndCheckForCompletion(child2Account.getId(), "bundleKey2", "Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        // First Parent invoice over TRIAL period
        List<Invoice> parentInvoices = invoiceUserApi.getInvoicesByAccount(parentAccount.getId(), false, callContext);
        assertEquals(parentInvoices.size(), 1);

        Invoice parentInvoice = parentInvoices.get(0);
        assertEquals(parentInvoice.getNumberOfItems(), 2);
        assertEquals(parentInvoice.getStatus(), InvoiceStatus.DRAFT);
        assertTrue(parentInvoice.isParentInvoice());
        assertEquals(parentInvoice.getBalance().compareTo(BigDecimal.ZERO), 0);

        // Moving a day the NotificationQ calls the commitInvoice. No payment is expected
        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        clock.addDays(1);
        assertListenerStatus();

        // reload parent invoice
        parentInvoice = invoiceUserApi.getInvoice(parentInvoice.getId(), callContext);
        assertEquals(parentInvoice.getStatus(), InvoiceStatus.COMMITTED);

        // Move through time and verify new parent Invoice. No payments are expected yet.
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.PHASE,
                                      NextEvent.INVOICE, NextEvent.INVOICE);
        clock.addDays(29);
        assertListenerStatus();

        // Second Parent invoice over Recurring period
        parentInvoices = invoiceUserApi.getInvoicesByAccount(parentAccount.getId(), false, callContext);
        assertEquals(parentInvoices.size(), 2);

        parentInvoice = parentInvoices.get(1);
        assertEquals(parentInvoice.getNumberOfItems(), 2);
        assertEquals(parentInvoice.getStatus(), InvoiceStatus.DRAFT);
        assertTrue(parentInvoice.isParentInvoice());
        // balance is 0 because parent invoice status is DRAFT
        assertEquals(parentInvoice.getBalance().compareTo(BigDecimal.ZERO), 0);
        // total 279.95
        assertEquals(parentInvoice.getInvoiceItems().get(0).getInvoiceItemType(), InvoiceItemType.PARENT_SUMMARY);
        assertEquals(parentInvoice.getInvoiceItems().get(0).getAmount().compareTo(BigDecimal.valueOf(249.95)), 0);
        assertEquals(parentInvoice.getInvoiceItems().get(1).getInvoiceItemType(), InvoiceItemType.PARENT_SUMMARY);
        assertEquals(parentInvoice.getInvoiceItems().get(1).getAmount().compareTo(BigDecimal.valueOf(29.95)), 0);

        // Check Child Balance. It should be > 0 here because Parent invoice is unpaid yet.
        List<Invoice> child1Invoices = invoiceUserApi.getInvoicesByAccount(child1Account.getId(), false, callContext);
        assertEquals(child1Invoices.size(), 2);
        // child balance is 0 because parent invoice status is DRAFT at this point
        assertEquals(child1Invoices.get(1).getBalance().compareTo(BigDecimal.ZERO), 0);

        // Moving a day the NotificationQ calls the commitInvoice. Payment is expected.
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(1);
        assertListenerStatus();

        parentInvoice = invoiceUserApi.getInvoice(parentInvoice.getId(), callContext);
        assertEquals(parentInvoice.getStatus(), InvoiceStatus.COMMITTED);

        // Check Child Balance. It should be = 0 because parent invoice had already paid.
        child1Invoices = invoiceUserApi.getInvoicesByAccount(child1Account.getId(), false, callContext);
        assertEquals(child1Invoices.size(), 2);
        assertTrue(parentInvoice.getBalance().compareTo(BigDecimal.ZERO) == 0);
        assertTrue(child1Invoices.get(1).getBalance().compareTo(BigDecimal.ZERO) == 0);

        // load children invoice items
        final List<InvoiceItem> childrenInvoiceItems = invoiceUserApi.getInvoiceItemsByParentInvoice(parentInvoice.getId(), callContext);
        assertEquals(childrenInvoiceItems.size(), 2);
        assertEquals(childrenInvoiceItems.get(0).getAccountId(), child1Account.getId());
        assertEquals(childrenInvoiceItems.get(0).getAmount().compareTo(BigDecimal.valueOf(249.95)), 0);
        assertEquals(childrenInvoiceItems.get(1).getAccountId(), child2Account.getId());
        assertEquals(childrenInvoiceItems.get(1).getAmount().compareTo(BigDecimal.valueOf(29.95)), 0);

        // loading children items from non parent account should return empty list
        assertEquals(invoiceUserApi.getInvoiceItemsByParentInvoice(child1Invoices.get(1).getId(), callContext).size(), 0);
    }

    @Test(groups = "slow")
    public void testParentInvoiceGenerationMultipleActionsSameDay() throws Exception {

        final int billingDay = 14;
        final DateTime initialCreationDate = new DateTime(2015, 5, 15, 0, 0, 0, 0, testTimeZone);

        // set clock to the initial start date
        clock.setTime(initialCreationDate);

        final Account parentAccount = createAccountWithNonOsgiPaymentMethod(getAccountData(billingDay));
        final Account childAccount = createAccountWithNonOsgiPaymentMethod(getChildAccountData(billingDay, parentAccount.getId(), true));

        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE NextEvent.INVOICE
        DefaultEntitlement baseEntitlementChild = createBaseEntitlementAndCheckForCompletion(childAccount.getId(), "bundleKey1", "Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        // Moving a day the NotificationQ calls the commitInvoice. No payment is expected.
        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        clock.addDays(1);
        assertListenerStatus();

        // Move through time and verify new parent Invoice. No payments are expected yet.
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE);
        clock.addDays(29);
        assertListenerStatus();

        // check parent Invoice with child plan amount
        List<Invoice> parentInvoices = invoiceUserApi.getInvoicesByAccount(parentAccount.getId(), false, callContext);
        assertEquals(parentInvoices.size(), 2);

        Invoice parentInvoice = parentInvoices.get(1);
        assertEquals(parentInvoice.getNumberOfItems(), 1);
        assertEquals(parentInvoice.getStatus(), InvoiceStatus.DRAFT);
        assertTrue(parentInvoice.isParentInvoice());
        // balance is 0 because parent invoice status is DRAFT
        assertEquals(parentInvoice.getBalance().compareTo(BigDecimal.ZERO), 0);
        assertEquals(parentInvoice.getInvoiceItems().get(0).getAmount().compareTo(BigDecimal.valueOf(29.95)), 0);

        // upgrade plan
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE);
        baseEntitlementChild.changePlanOverrideBillingPolicy(new PlanSpecifier("Shotgun", BillingPeriod.MONTHLY, baseEntitlementChild.getLastActivePriceList().getName()), null, clock.getToday(childAccount.getTimeZone()), BillingActionPolicy.IMMEDIATE, null, callContext);
        assertListenerStatus();

        // check parent invoice. Expected to have the same invoice item with the amount updated
        final List<Invoice> childInvoices = invoiceUserApi.getInvoicesByAccount(childAccount.getId(), false, callContext);
        parentInvoices = invoiceUserApi.getInvoicesByAccount(parentAccount.getId(), false, callContext);
        assertEquals(parentInvoices.size(), 2);

        parentInvoice = parentInvoices.get(1);
        assertEquals(parentInvoice.getNumberOfItems(), 1);
        assertEquals(parentInvoice.getStatus(), InvoiceStatus.DRAFT);
        assertTrue(parentInvoice.isParentInvoice());
        // balance is 0 because parent invoice status is DRAFT
        assertEquals(parentInvoice.getBalance().compareTo(BigDecimal.ZERO), 0);


        // Moving a day the NotificationQ calls the commitInvoice. Now payment is expected
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(1);
        assertListenerStatus();

        parentInvoice = invoiceUserApi.getInvoice(parentInvoice.getId(), callContext);
        assertEquals(parentInvoice.getStatus(), InvoiceStatus.COMMITTED);
        assertEquals(parentInvoice.getBalance().compareTo(BigDecimal.ZERO), 0);

    }

    @Test(groups = "slow")
    public void testParentInvoiceGenerationChildCreditUnpaidInvoice() throws Exception {

        final int billingDay = 14;
        final DateTime initialCreationDate = new DateTime(2015, 5, 15, 0, 0, 0, 0, testTimeZone);

        // set clock to the initial start date
        clock.setTime(initialCreationDate);

        log.info("Beginning test with BCD of " + billingDay);
        final Account parentAccount = createAccountWithNonOsgiPaymentMethod(getAccountData(billingDay));
        final Account childAccount = createAccountWithNonOsgiPaymentMethod(getChildAccountData(billingDay, parentAccount.getId(), true));

        DefaultEntitlement baseEntitlementChild = createBaseEntitlementAndCheckForCompletion(childAccount.getId(), "bundleKey1", "Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        // Moving a day the NotificationQ calls the commitInvoice. No payment is expected.
        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        clock.addDays(1);
        assertListenerStatus();

        // Move through time and verify new parent Invoice. No payments are expected yet.
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE);
        clock.addDays(29);
        assertListenerStatus();

        // add credit to child account when invoice is still unpaid
        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        invoiceUserApi.insertCredit(childAccount.getId(), BigDecimal.TEN, clock.getUTCToday(), Currency.USD, true, "test", callContext);
        assertListenerStatus();

        final List<Invoice> childInvoices = invoiceUserApi.getInvoicesByAccount(childAccount.getId(), false, callContext);
        assertEquals(childInvoices.size(), 3);

        // invoice monthly with credit
        final Invoice childInvoice = childInvoices.get(1);
        assertEquals(childInvoice.getNumberOfItems(), 2);
        assertEquals(childInvoice.getInvoiceItems().get(0).getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(childInvoice.getInvoiceItems().get(0).getAmount().compareTo(BigDecimal.valueOf(29.95)), 0);
        assertEquals(childInvoice.getInvoiceItems().get(1).getInvoiceItemType(), InvoiceItemType.CBA_ADJ);
        assertEquals(childInvoice.getInvoiceItems().get(1).getAmount().compareTo(BigDecimal.valueOf(-10.00)), 0);
        // child balance is 0 because parent invoice status is DRAFT at this point
        assertEquals(childInvoice.getBalance().compareTo(BigDecimal.ZERO), 0);

        // check parent Invoice with child plan amount
        List<Invoice> parentInvoices = invoiceUserApi.getInvoicesByAccount(parentAccount.getId(), false, callContext);
        assertEquals(parentInvoices.size(), 2);

        Invoice parentInvoice = parentInvoices.get(1);
        assertEquals(parentInvoice.getNumberOfItems(), 1);
        assertEquals(parentInvoice.getStatus(), InvoiceStatus.DRAFT);
        assertTrue(parentInvoice.isParentInvoice());
        // balance is 0 because parent invoice status is DRAFT
        assertEquals(parentInvoice.getBalance().compareTo(BigDecimal.ZERO), 0);
        assertEquals(parentInvoice.getInvoiceItems().get(0).getAmount().compareTo(BigDecimal.valueOf(19.95)), 0);

        // Moving a day the NotificationQ calls the commitInvoice.
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(1);
        assertListenerStatus();

        parentInvoices = invoiceUserApi.getInvoicesByAccount(parentAccount.getId(), false, callContext);
        assertEquals(parentInvoices.size(), 2);

        parentInvoice = parentInvoices.get(1);
        assertEquals(parentInvoice.getStatus(), InvoiceStatus.COMMITTED);
        assertTrue(parentInvoice.isParentInvoice());
        assertEquals(parentInvoice.getChargedAmount().compareTo(BigDecimal.valueOf(19.95)), 0);
        assertEquals(parentInvoice.getCreditedAmount().compareTo(BigDecimal.ZERO), 0);

        final List<Payment> accountPayments = paymentApi.getAccountPayments(parentAccount.getId(), false, false, null, callContext);
        assertEquals(accountPayments.size(), 1);
        assertEquals(accountPayments.get(0).getPurchasedAmount().setScale(2).compareTo(BigDecimal.valueOf(19.95)), 0);
        assertEquals(accountPayments.get(0).getCreditedAmount().compareTo(BigDecimal.ZERO), 0);

    }

    @Test(groups = "slow")
    public void testParentInvoiceGenerationChildCreditPaidInvoice() throws Exception {

        final int billingDay = 14;
        final DateTime initialCreationDate = new DateTime(2015, 5, 15, 0, 0, 0, 0, testTimeZone);

        // set clock to the initial start date
        clock.setTime(initialCreationDate);

        log.info("Beginning test with BCD of " + billingDay);
        final Account parentAccount = createAccountWithNonOsgiPaymentMethod(getAccountData(billingDay));
        final Account childAccount = createAccountWithNonOsgiPaymentMethod(getChildAccountData(billingDay, parentAccount.getId(), true));

        createBaseEntitlementAndCheckForCompletion(childAccount.getId(), "bundleKey1", "Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        // Moving a day the NotificationQ calls the commitInvoice. No payment is expected.
        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        clock.addDays(1);
        assertListenerStatus();

        // Move through time and verify new parent Invoice. No payments are expected yet.
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE);
        clock.addDays(29);
        assertListenerStatus();

        // Moving a day the NotificationQ calls the commitInvoice.
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(1);
        assertListenerStatus();

        // add credit to child account after invoice has been paid
        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        invoiceUserApi.insertCredit(childAccount.getId(), BigDecimal.TEN, clock.getUTCToday(), Currency.USD, true, "test", callContext);
        assertListenerStatus();

        List<Invoice> childInvoices = invoiceUserApi.getInvoicesByAccount(childAccount.getId(), false, callContext);
        assertEquals(childInvoices.size(), 3);

        // invoice monthly with credit
        Invoice childInvoice = childInvoices.get(1);
        assertEquals(childInvoice.getNumberOfItems(), 1);
        assertEquals(childInvoice.getInvoiceItems().get(0).getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(childInvoice.getInvoiceItems().get(0).getAmount().compareTo(BigDecimal.valueOf(29.95)), 0);

        // check parent Invoice with child plan amount
        List<Invoice> parentInvoices = invoiceUserApi.getInvoicesByAccount(parentAccount.getId(), false, callContext);
        assertEquals(parentInvoices.size(), 2);

        Invoice parentInvoice = parentInvoices.get(1);
        assertEquals(parentInvoice.getNumberOfItems(), 1);
        assertEquals(parentInvoice.getStatus(), InvoiceStatus.COMMITTED);
        assertTrue(parentInvoice.isParentInvoice());
        assertEquals(parentInvoice.getChargedAmount().compareTo(BigDecimal.valueOf(29.95)), 0);

        final List<Payment> accountPayments = paymentApi.getAccountPayments(parentAccount.getId(), false, false, null, callContext);
        assertEquals(accountPayments.size(), 1);
        assertEquals(accountPayments.get(0).getPurchasedAmount().setScale(2).compareTo(BigDecimal.valueOf(29.95)), 0);
        assertEquals(accountPayments.get(0).getCreditedAmount().compareTo(BigDecimal.ZERO), 0);

    }

    // Scenario 1.a: Follow up Invoice Item Adjustment on unpaid DRAFT invoice
    @Test(groups = "slow")
    public void testParentInvoiceItemAdjustmentUnpaidDraftInvoice() throws Exception {

        final int billingDay = 14;
        final DateTime initialCreationDate = new DateTime(2014, 5, 15, 0, 0, 0, 0, testTimeZone);
        // set clock to the initial start date
        clock.setTime(initialCreationDate);

        final Account parentAccount = createAccountWithNonOsgiPaymentMethod(getAccountData(billingDay));
        final Account childAccount = createAccountWithNonOsgiPaymentMethod(getChildAccountData(billingDay, parentAccount.getId(), true));

        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE NextEvent.INVOICE
        createBaseEntitlementAndCheckForCompletion(childAccount.getId(), "bundleKey1", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        // ---- trial period ----
        // Moving a day the NotificationQ calls the commitInvoice. No payment is expected because balance is 0
        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        clock.addDays(1);
        assertListenerStatus();

        // ---- recurring period ----
        // Move through time and verify new parent Invoice. No payments are expected.
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE);
        clock.addDays(29);
        assertListenerStatus();

        List<Invoice> parentInvoices = invoiceUserApi.getInvoicesByAccount(parentAccount.getId(), false, callContext);
        List<Invoice> childInvoices = invoiceUserApi.getInvoicesByAccount(childAccount.getId(), false, callContext);
        // get last child invoice
        Invoice childInvoice = childInvoices.get(1);
        assertEquals(childInvoice.getNumberOfItems(), 1);

        // Second Parent invoice over Recurring period
        assertEquals(parentInvoices.size(), 2);

        Invoice parentInvoice = parentInvoices.get(1);
        assertEquals(parentInvoice.getNumberOfItems(), 1);
        assertEquals(parentInvoice.getStatus(), InvoiceStatus.DRAFT);
        assertTrue(parentInvoice.isParentInvoice());
        // balance is 0 because parent invoice status is DRAFT
        assertEquals(parentInvoice.getBalance().compareTo(BigDecimal.ZERO), 0);
        assertEquals(parentInvoice.getInvoiceItems().get(0).getAmount().compareTo(BigDecimal.valueOf(249.95)), 0);


        // issue a $10 adj when invoice is unpaid
        busHandler.pushExpectedEvents(NextEvent.INVOICE_ADJUSTMENT);
        invoiceUserApi.insertInvoiceItemAdjustment(childAccount.getId(), childInvoice.getId(),
                                                   childInvoice.getInvoiceItems().get(0).getId(),
                                                   clock.getToday(childAccount.getTimeZone()), BigDecimal.TEN,
                                                   childAccount.getCurrency(), "test adjustment", callContext);
        assertListenerStatus();

        // expected child invoice
        // RECURRING : $ 249.95
        // ITEM_ADJ : $ -10

        childInvoice = invoiceUserApi.getInvoice(childInvoice.getId(), callContext);
        assertEquals(childInvoice.getNumberOfItems(), 2);
        // child balance is 0 because parent invoice status is DRAFT at this point
        assertEquals(childInvoice.getBalance().compareTo(BigDecimal.ZERO), 0);
        assertEquals(childInvoice.getInvoiceItems().get(0).getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(childInvoice.getInvoiceItems().get(0).getAmount().compareTo(BigDecimal.valueOf(249.95)), 0);
        assertEquals(childInvoice.getInvoiceItems().get(1).getInvoiceItemType(), InvoiceItemType.ITEM_ADJ);
        assertEquals(childInvoice.getInvoiceItems().get(1).getAmount().compareTo(BigDecimal.valueOf(-10.00)), 0);

        // reload parent invoice
        parentInvoice = invoiceUserApi.getInvoice(parentInvoice.getId(), callContext);
        // check parent invoice is updated and still in DRAFT status
        assertEquals(parentInvoice.getNumberOfItems(), 1);
        assertEquals(parentInvoice.getStatus(), InvoiceStatus.DRAFT);
        assertTrue(parentInvoice.isParentInvoice());
        // balance is 0 because parent invoice status is DRAFT
        assertEquals(parentInvoice.getBalance().compareTo(BigDecimal.ZERO), 0);
        assertEquals(parentInvoice.getInvoiceItems().get(0).getAmount().compareTo(BigDecimal.valueOf(239.95)), 0);

    }

    // Scenario 1.b: Follow up Invoice Item Adjustment on unpaid COMMITTED invoice
    @Test(groups = "slow")
    public void testParentInvoiceItemAdjustmentUnpaidCommittedInvoice() throws Exception {

        final int billingDay = 14;
        final DateTime initialCreationDate = new DateTime(2014, 5, 15, 0, 0, 0, 0, testTimeZone);
        // set clock to the initial start date
        clock.setTime(initialCreationDate);

        final Account parentAccount = createAccountWithNonOsgiPaymentMethod(getAccountData(billingDay));
        final Account childAccount = createAccountWithNonOsgiPaymentMethod(getChildAccountData(billingDay, parentAccount.getId(), true));

        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE NextEvent.INVOICE
        createBaseEntitlementAndCheckForCompletion(childAccount.getId(), "bundleKey1", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        // ---- trial period ----
        // Moving a day the NotificationQ calls the commitInvoice. No payment is expected because balance is 0
        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        clock.addDays(1);
        assertListenerStatus();

        // ---- recurring period ----
        // Move through time and verify new parent Invoice. No payments are expected.
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE);
        clock.addDays(29);
        assertListenerStatus();

        paymentPlugin.makeNextPaymentFailWithError();

        // move one day to have parent invoice paid
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        clock.addDays(1);
        assertListenerStatus();

        List<Invoice> parentInvoices = invoiceUserApi.getInvoicesByAccount(parentAccount.getId(), false, callContext);
        List<Invoice> childInvoices = invoiceUserApi.getInvoicesByAccount(childAccount.getId(), false, callContext);
        // get last child invoice
        Invoice childInvoice = childInvoices.get(1);
        assertEquals(childInvoice.getNumberOfItems(), 1);

        // Second Parent invoice over Recurring period
        assertEquals(parentInvoices.size(), 2);

        Invoice parentInvoice = parentInvoices.get(1);
        assertEquals(parentInvoice.getNumberOfItems(), 1);
        assertEquals(parentInvoice.getStatus(), InvoiceStatus.COMMITTED);
        assertTrue(parentInvoice.isParentInvoice());
        assertEquals(parentInvoice.getBalance().compareTo(BigDecimal.valueOf(249.95)), 0);

        // issue a $10 adj when invoice is unpaid
        insertInvoiceItemAdjustmentToChildInvoice(childAccount, childInvoice, BigDecimal.TEN);
        // make sure there is time difference between item adjustments.
        // Otherwise they are created with same id and createdDate and it's used to sort them.
        clock.addDeltaFromReality(1000);

        // issue a $5 adj when invoice is unpaid
        insertInvoiceItemAdjustmentToChildInvoice(childAccount, childInvoice, BigDecimal.valueOf(5));
        clock.addDeltaFromReality(1000);

        // issue a $10 adj when invoice is unpaid
        insertInvoiceItemAdjustmentToChildInvoice(childAccount, childInvoice, BigDecimal.TEN);

        // move one day
        busHandler.pushExpectedEvents();
        clock.addDays(1);
        assertListenerStatus();

        // issue a $5 adj when invoice is unpaid
        insertInvoiceItemAdjustmentToChildInvoice(childAccount, childInvoice, BigDecimal.valueOf(5));
        clock.addDeltaFromReality(1000);

        // issue a $10 adj when invoice is unpaid
        insertInvoiceItemAdjustmentToChildInvoice(childAccount, childInvoice, BigDecimal.TEN);

        // expected child invoice
        // RECURRING : $ 249.95
        // ITEM_ADJ : $ -10
        // ITEM_ADJ : $ -5
        // ITEM_ADJ : $ -10
        // ITEM_ADJ : $ -5
        // ITEM_ADJ : $ -10

        // expected parent invoice
        // PARENT_SUMMARY : $ 249.95
        // ITEM_ADJ : $ -10
        // ITEM_ADJ : $ -5
        // ITEM_ADJ : $ -10
        // ITEM_ADJ : $ -5
        // ITEM_ADJ : $ -10

        childInvoice = invoiceUserApi.getInvoice(childInvoice.getId(), callContext);
        assertEquals(childInvoice.getNumberOfItems(), 6);
        assertEquals(childInvoice.getBalance().compareTo(BigDecimal.valueOf(209.95)), 0);
        assertEquals(childInvoice.getInvoiceItems().get(0).getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(childInvoice.getInvoiceItems().get(1).getInvoiceItemType(), InvoiceItemType.ITEM_ADJ);
        assertEquals(childInvoice.getInvoiceItems().get(1).getAmount().compareTo(BigDecimal.valueOf(-10)), 0);
        assertEquals(childInvoice.getInvoiceItems().get(2).getInvoiceItemType(), InvoiceItemType.ITEM_ADJ);
        assertEquals(childInvoice.getInvoiceItems().get(2).getAmount().compareTo(BigDecimal.valueOf(-5)), 0);
        assertEquals(childInvoice.getInvoiceItems().get(3).getInvoiceItemType(), InvoiceItemType.ITEM_ADJ);
        assertEquals(childInvoice.getInvoiceItems().get(3).getAmount().compareTo(BigDecimal.valueOf(-10)), 0);
        assertEquals(childInvoice.getInvoiceItems().get(4).getInvoiceItemType(), InvoiceItemType.ITEM_ADJ);
        assertEquals(childInvoice.getInvoiceItems().get(4).getAmount().compareTo(BigDecimal.valueOf(-5)), 0);
        assertEquals(childInvoice.getInvoiceItems().get(5).getInvoiceItemType(), InvoiceItemType.ITEM_ADJ);
        assertEquals(childInvoice.getInvoiceItems().get(5).getAmount().compareTo(BigDecimal.valueOf(-10)), 0);

        // reload parent invoice
        parentInvoice = invoiceUserApi.getInvoice(parentInvoice.getId(), callContext);
        assertEquals(parentInvoice.getNumberOfItems(), 6);
        assertEquals(parentInvoice.getStatus(), InvoiceStatus.COMMITTED);
        assertTrue(parentInvoice.isParentInvoice());
        assertEquals(parentInvoice.getBalance().compareTo(BigDecimal.valueOf(209.95)), 0);
        assertEquals(parentInvoice.getInvoiceItems().get(0).getInvoiceItemType(), InvoiceItemType.PARENT_SUMMARY);
        assertEquals(parentInvoice.getInvoiceItems().get(1).getInvoiceItemType(), InvoiceItemType.ITEM_ADJ);
        assertEquals(parentInvoice.getInvoiceItems().get(1).getAmount().compareTo(BigDecimal.valueOf(-10)), 0);
        assertEquals(parentInvoice.getInvoiceItems().get(2).getInvoiceItemType(), InvoiceItemType.ITEM_ADJ);
        assertEquals(parentInvoice.getInvoiceItems().get(2).getAmount().compareTo(BigDecimal.valueOf(-5)), 0);
        assertEquals(parentInvoice.getInvoiceItems().get(3).getInvoiceItemType(), InvoiceItemType.ITEM_ADJ);
        assertEquals(parentInvoice.getInvoiceItems().get(3).getAmount().compareTo(BigDecimal.valueOf(-10)), 0);
        assertEquals(parentInvoice.getInvoiceItems().get(4).getInvoiceItemType(), InvoiceItemType.ITEM_ADJ);
        assertEquals(parentInvoice.getInvoiceItems().get(4).getAmount().compareTo(BigDecimal.valueOf(-5)), 0);
        assertEquals(parentInvoice.getInvoiceItems().get(5).getInvoiceItemType(), InvoiceItemType.ITEM_ADJ);
        assertEquals(parentInvoice.getInvoiceItems().get(5).getAmount().compareTo(BigDecimal.valueOf(-10)), 0);

    }

    private void insertInvoiceItemAdjustmentToChildInvoice(final Account childAccount, final Invoice childInvoice, BigDecimal amount) throws InvoiceApiException {
        busHandler.pushExpectedEvents(NextEvent.INVOICE_ADJUSTMENT, NextEvent.INVOICE_ADJUSTMENT);
        invoiceUserApi.insertInvoiceItemAdjustment(childAccount.getId(), childInvoice.getId(),
                                                   childInvoice.getInvoiceItems().get(0).getId(),
                                                   clock.getToday(childAccount.getTimeZone()), amount,
                                                   childAccount.getCurrency(), "test adjustment", callContext);
        assertListenerStatus();
    }

    // Scenario 2: Follow up Invoice Item Adjustment on PAID invoice
    @Test(groups = "slow")
    public void testParentInvoiceItemAdjustmentPaidInvoice() throws Exception {

        final int billingDay = 14;
        final DateTime initialCreationDate = new DateTime(2014, 5, 15, 0, 0, 0, 0, testTimeZone);
        // set clock to the initial start date
        clock.setTime(initialCreationDate);

        final Account parentAccount = createAccountWithNonOsgiPaymentMethod(getAccountData(billingDay));
        final Account childAccount = createAccountWithNonOsgiPaymentMethod(getChildAccountData(billingDay, parentAccount.getId(), true));

        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE NextEvent.INVOICE
        createBaseEntitlementAndCheckForCompletion(childAccount.getId(), "bundleKey1", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        // ---- trial period ----
        // Moving a day the NotificationQ calls the commitInvoice. No payment is expected because balance is 0
        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        clock.addDays(1);
        assertListenerStatus();

        // ---- recurring period ----
        // Move through time and verify new parent Invoice. No payments are expected.
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE);
        clock.addDays(29);
        assertListenerStatus();

        // move one day to have parent invoice paid
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(1);
        assertListenerStatus();

        final List<Invoice> parentInvoices = invoiceUserApi.getInvoicesByAccount(parentAccount.getId(), false, callContext);
        final List<Invoice> childInvoices = invoiceUserApi.getInvoicesByAccount(childAccount.getId(), false, callContext);
        
        // get last child invoice
        Invoice childInvoice = childInvoices.get(1);
        assertEquals(childInvoice.getNumberOfItems(), 1);

        // Second Parent invoice over Recurring period
        assertEquals(parentInvoices.size(), 2);

        Invoice parentInvoice = parentInvoices.get(1);
        assertEquals(parentInvoice.getNumberOfItems(), 1);
        assertEquals(parentInvoice.getStatus(), InvoiceStatus.COMMITTED);
        assertTrue(parentInvoice.isParentInvoice());
        assertEquals(parentInvoice.getBalance().compareTo(BigDecimal.ZERO), 0);
        assertEquals(parentInvoice.getChargedAmount().compareTo(BigDecimal.valueOf(249.95)), 0);

        // issue a $10 adj in a paid invoice
        busHandler.pushExpectedEvents(NextEvent.INVOICE_ADJUSTMENT);
        invoiceUserApi.insertInvoiceItemAdjustment(childAccount.getId(),
                                                   childInvoice.getId(),
                                                   childInvoice.getInvoiceItems().get(0).getId(),
                                                   clock.getToday(childAccount.getTimeZone()),
                                                   BigDecimal.TEN,
                                                   childAccount.getCurrency(), "test adjustment", callContext);
        assertListenerStatus();

        // expected child invoice
        // RECURRING : $ 249.95
        // ITEM_ADJ : $ -10
        // CBA_ADJ : $ +10

        childInvoice = invoiceUserApi.getInvoice(childInvoice.getId(), callContext);
        assertEquals(childInvoice.getNumberOfItems(), 3);
        assertEquals(childInvoice.getInvoiceItems().get(0).getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(childInvoice.getInvoiceItems().get(0).getAmount().compareTo(BigDecimal.valueOf(249.95)), 0);
        assertEquals(childInvoice.getInvoiceItems().get(1).getInvoiceItemType(), InvoiceItemType.ITEM_ADJ);
        assertEquals(childInvoice.getInvoiceItems().get(1).getAmount().compareTo(BigDecimal.valueOf(-10)), 0);
        assertEquals(childInvoice.getInvoiceItems().get(2).getInvoiceItemType(), InvoiceItemType.CBA_ADJ);
        assertEquals(childInvoice.getInvoiceItems().get(2).getAmount().compareTo(BigDecimal.valueOf(10)), 0);

        // check parent invoices
        parentInvoice = invoiceUserApi.getInvoice(parentInvoice.getId(), callContext);
        assertEquals(parentInvoice.getNumberOfItems(), 1);
        assertEquals(parentInvoice.getPaidAmount().compareTo(BigDecimal.valueOf(249.95)), 0);
        assertEquals(parentInvoice.getInvoiceItems().get(0).getInvoiceItemType(), InvoiceItemType.PARENT_SUMMARY);

    }

    // Scenario 3: Repair (early cancellation, plan downgrade) on unpaid invoice
    @Test(groups = "slow")
    public void testParentInvoiceEarlyCancellationUnpaidInvoice() throws Exception {

        final int billingDay = 14;
        final DateTime initialCreationDate = new DateTime(2014, 5, 15, 0, 0, 0, 0, testTimeZone);
        // set clock to the initial start date
        clock.setTime(initialCreationDate);

        final Account parentAccount = createAccountWithNonOsgiPaymentMethod(getAccountData(billingDay));
        final Account childAccount = createAccountWithNonOsgiPaymentMethod(getChildAccountData(billingDay, parentAccount.getId(), true));

        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE NextEvent.INVOICE
        DefaultEntitlement baseEntitlementChild = createBaseEntitlementAndCheckForCompletion(childAccount.getId(), "bundleKey1", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        // ---- trial period ----
        // Moving a day the NotificationQ calls the commitInvoice. No payment is expected because balance is 0
        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        clock.addDays(1);
        assertListenerStatus();

        // ---- recurring period ----
        // moving one extra day to get some cancellation difference
        // Move through time and verify new parent Invoice. No payments are expected.
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE);
        clock.addMonths(1);
        assertListenerStatus();

        List<Invoice> parentInvoices = invoiceUserApi.getInvoicesByAccount(parentAccount.getId(), false, callContext);
        List<Invoice> childInvoices = invoiceUserApi.getInvoicesByAccount(childAccount.getId(), false, callContext);
        // get last child invoice
        Invoice childInvoice = childInvoices.get(1);
        assertEquals(childInvoice.getNumberOfItems(), 1);

        // Second Parent invoice over Recurring period
        assertEquals(parentInvoices.size(), 2);

        Invoice parentInvoice = parentInvoices.get(1);
        assertEquals(parentInvoice.getNumberOfItems(), 1);
        assertEquals(parentInvoice.getStatus(), InvoiceStatus.DRAFT);
        assertTrue(parentInvoice.isParentInvoice());
        // balance is 0 because parent invoice status is DRAFT
        assertEquals(parentInvoice.getBalance().compareTo(BigDecimal.ZERO), 0);
        assertEquals(parentInvoice.getInvoiceItems().get(0).getAmount().compareTo(BigDecimal.valueOf(249.95)), 0);

        // cancel subscription
        busHandler.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK, NextEvent.INVOICE);
        baseEntitlementChild.cancelEntitlementWithDateOverrideBillingPolicy(clock.getToday(childAccount.getTimeZone()), BillingActionPolicy.IMMEDIATE, null, callContext);
        assertListenerStatus();

        // expected invoices

        // invoice 2:
        // REPAIR_ADJ $ -233.29
        // CBA_ADJ $ 233.29

        // Invoice 1:
        // RECURRING : $ 249.95
        // CBA_ADJ $ -233.29

        childInvoices = invoiceUserApi.getInvoicesByAccount(childAccount.getId(), false, callContext);
        // invoice 1
        childInvoice = childInvoices.get(1);
        assertEquals(childInvoice.getNumberOfItems(), 2);
        // child balance is 0 because parent invoice status is DRAFT at this point
        assertEquals(childInvoice.getBalance().compareTo(BigDecimal.ZERO), 0);
        assertEquals(childInvoice.getInvoiceItems().get(0).getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(childInvoice.getInvoiceItems().get(0).getAmount().compareTo(BigDecimal.valueOf(249.95)), 0);
        assertEquals(childInvoice.getInvoiceItems().get(1).getInvoiceItemType(), InvoiceItemType.CBA_ADJ);
        assertEquals(childInvoice.getInvoiceItems().get(1).getAmount().compareTo(BigDecimal.valueOf(-233.29)), 0);

        // invoice 2
        childInvoice = childInvoices.get(2);
        assertEquals(childInvoice.getNumberOfItems(), 2);
        assertEquals(childInvoice.getBalance().compareTo(BigDecimal.ZERO), 0);
        assertEquals(childInvoice.getInvoiceItems().get(0).getInvoiceItemType(), InvoiceItemType.REPAIR_ADJ);
        assertEquals(childInvoice.getInvoiceItems().get(0).getAmount().compareTo(BigDecimal.valueOf(-233.29)), 0);
        assertEquals(childInvoice.getInvoiceItems().get(1).getInvoiceItemType(), InvoiceItemType.CBA_ADJ);
        assertEquals(childInvoice.getInvoiceItems().get(1).getAmount().compareTo(BigDecimal.valueOf(233.29)), 0);

        // check if parent invoice was updated
        parentInvoices = invoiceUserApi.getInvoicesByAccount(parentAccount.getId(), false, callContext);
        assertEquals(parentInvoices.size(), 2);

        parentInvoice = parentInvoices.get(1);
        assertEquals(parentInvoice.getStatus(), InvoiceStatus.DRAFT);
        assertTrue(parentInvoice.isParentInvoice());
        // balance is 0 because parent invoice status is DRAFT
        assertEquals(parentInvoice.getBalance().compareTo(BigDecimal.ZERO), 0);
        assertEquals(parentInvoice.getNumberOfItems(), 1);
        assertEquals(parentInvoice.getInvoiceItems().get(0).getInvoiceItemType(), InvoiceItemType.PARENT_SUMMARY);
        assertEquals(parentInvoice.getInvoiceItems().get(0).getAmount().compareTo(BigDecimal.valueOf(16.66)), 0);

    }

    // Scenario 4: Repair (early cancellation, plan downgrade) on PAID invoice
    @Test(groups = "slow")
    public void testParentInvoiceEarlyCancellationPaidInvoice() throws Exception {

        final int billingDay = 14;
        final DateTime initialCreationDate = new DateTime(2014, 5, 15, 0, 0, 0, 0, testTimeZone);
        // set clock to the initial start date
        clock.setTime(initialCreationDate);

        final Account parentAccount = createAccountWithNonOsgiPaymentMethod(getAccountData(billingDay));
        final Account childAccount = createAccountWithNonOsgiPaymentMethod(getChildAccountData(billingDay, parentAccount.getId(), true));

        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE NextEvent.INVOICE
        DefaultEntitlement baseEntitlementChild = createBaseEntitlementAndCheckForCompletion(childAccount.getId(), "bundleKey1", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        // ---- trial period ----
        // Moving a day the NotificationQ calls the commitInvoice. No payment is expected because balance is 0
        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        clock.addDays(1);
        assertListenerStatus();

        // ---- recurring period ----
        // Move through time and verify new parent Invoice. No payments are expected.
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE);
        clock.addDays(29);
        assertListenerStatus();

        // move one day to have parent invoice paid
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(1);
        assertListenerStatus();

        List<Invoice> parentInvoices = invoiceUserApi.getInvoicesByAccount(parentAccount.getId(), false, callContext);
        List<Invoice> childInvoices = invoiceUserApi.getInvoicesByAccount(childAccount.getId(), false, callContext);
        // get last child invoice
        Invoice childInvoice = childInvoices.get(1);
        assertEquals(childInvoice.getNumberOfItems(), 1);

        // Second Parent invoice over Recurring period
        assertEquals(parentInvoices.size(), 2);

        Invoice parentInvoice = parentInvoices.get(1);
        assertEquals(parentInvoice.getNumberOfItems(), 1);
        assertEquals(parentInvoice.getStatus(), InvoiceStatus.COMMITTED);
        assertTrue(parentInvoice.isParentInvoice());
        assertEquals(parentInvoice.getChargedAmount().compareTo(BigDecimal.valueOf(249.95)), 0);

        busHandler.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK, NextEvent.INVOICE);
        baseEntitlementChild.cancelEntitlementWithDateOverrideBillingPolicy(clock.getToday(childAccount.getTimeZone()), BillingActionPolicy.IMMEDIATE, null, callContext);
        assertListenerStatus();

        // expected child invoices

        // Invoice 2:
        // REPAIR_ADJ $ -241.62
        // CBA_ADJ $ 241.62

        // Invoice 1: # unchanged
        // RECURRING : $ 249.95

        childInvoices = invoiceUserApi.getInvoicesByAccount(childAccount.getId(), false, callContext);
        // invoice 1
        childInvoice = childInvoices.get(1);
        assertEquals(childInvoice.getNumberOfItems(), 1);
        assertEquals(childInvoice.getChargedAmount().compareTo(BigDecimal.valueOf(249.95)), 0);
        assertEquals(childInvoice.getInvoiceItems().get(0).getInvoiceItemType(), InvoiceItemType.RECURRING);

        // invoice 2
        childInvoice = childInvoices.get(2);
        assertEquals(childInvoice.getNumberOfItems(), 2);
        assertEquals(childInvoice.getInvoiceItems().get(0).getInvoiceItemType(), InvoiceItemType.REPAIR_ADJ);
        assertEquals(childInvoice.getInvoiceItems().get(0).getAmount().compareTo(BigDecimal.valueOf(-241.62)), 0);
        assertEquals(childInvoice.getInvoiceItems().get(1).getInvoiceItemType(), InvoiceItemType.CBA_ADJ);
        assertEquals(childInvoice.getInvoiceItems().get(1).getAmount().compareTo(BigDecimal.valueOf(241.62)), 0);

        // check equal parent invoice
        parentInvoices = invoiceUserApi.getInvoicesByAccount(parentAccount.getId(), false, callContext);
        assertEquals(parentInvoices.size(), 2);

        parentInvoice = parentInvoices.get(1);
        assertEquals(parentInvoice.getNumberOfItems(), 1);
        assertEquals(parentInvoice.getStatus(), InvoiceStatus.COMMITTED);
        assertTrue(parentInvoice.isParentInvoice());
        assertEquals(parentInvoice.getChargedAmount().compareTo(BigDecimal.valueOf(249.95)), 0);

    }

    // Scenario 5: Use of credit
    @Test(groups = "slow")
    public void testParentInvoiceEarlyCancellationUseCredit() throws Exception {

        final int billingDay = 14;
        final DateTime initialCreationDate = new DateTime(2014, 5, 15, 0, 0, 0, 0, testTimeZone);
        // set clock to the initial start date
        clock.setTime(initialCreationDate);

        final Account parentAccount = createAccountWithNonOsgiPaymentMethod(getAccountData(billingDay));
        final Account childAccount = createAccountWithNonOsgiPaymentMethod(getChildAccountData(billingDay, parentAccount.getId(), true));

        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE NextEvent.INVOICE
        DefaultEntitlement baseEntitlementChild = createBaseEntitlementAndCheckForCompletion(childAccount.getId(), "bundleKey1", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        // ---- trial period ----
        // Moving a day the NotificationQ calls the commitInvoice. No payment is expected because balance is 0
        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        clock.addDays(1);
        assertListenerStatus();

        // ---- recurring period ----
        // Move through time and verify new parent Invoice. No payments are expected.
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE);
        clock.addDays(29);
        assertListenerStatus();

        // move one day to have parent invoice paid
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(1);
        assertListenerStatus();

        List<Invoice> parentInvoices = invoiceUserApi.getInvoicesByAccount(parentAccount.getId(), false, callContext);
        List<Invoice> childInvoices = invoiceUserApi.getInvoicesByAccount(childAccount.getId(), false, callContext);
        // get last child invoice
        Invoice childInvoice = childInvoices.get(1);
        assertEquals(childInvoice.getNumberOfItems(), 1);

        // Second Parent invoice over Recurring period
        assertEquals(parentInvoices.size(), 2);

        Invoice parentInvoice = parentInvoices.get(1);
        assertEquals(parentInvoice.getNumberOfItems(), 1);
        assertEquals(parentInvoice.getStatus(), InvoiceStatus.COMMITTED);
        assertTrue(parentInvoice.isParentInvoice());
        assertEquals(parentInvoice.getChargedAmount().compareTo(BigDecimal.valueOf(249.95)), 0);

        busHandler.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK, NextEvent.INVOICE);
        baseEntitlementChild.cancelEntitlementWithDateOverrideBillingPolicy(clock.getToday(childAccount.getTimeZone()), BillingActionPolicy.IMMEDIATE, null, callContext);
        assertListenerStatus();

        // expected child invoices

        // Invoice 2:
        // REPAIR_ADJ $ -241.62
        // CBA_ADJ $ 241.62

        // Invoice 1: # unchanged
        // RECURRING : $ 249.95

        childInvoices = invoiceUserApi.getInvoicesByAccount(childAccount.getId(), false, callContext);
        // invoice 1
        childInvoice = childInvoices.get(1);
        assertEquals(childInvoice.getNumberOfItems(), 1);
        assertEquals(childInvoice.getChargedAmount().compareTo(BigDecimal.valueOf(249.95)), 0);
        assertEquals(childInvoice.getInvoiceItems().get(0).getInvoiceItemType(), InvoiceItemType.RECURRING);

        // invoice 2
        childInvoice = childInvoices.get(2);
        assertEquals(childInvoice.getNumberOfItems(), 2);
        assertEquals(childInvoice.getInvoiceItems().get(0).getInvoiceItemType(), InvoiceItemType.REPAIR_ADJ);
        assertEquals(childInvoice.getInvoiceItems().get(0).getAmount().compareTo(BigDecimal.valueOf(-241.62)), 0);
        assertEquals(childInvoice.getInvoiceItems().get(1).getInvoiceItemType(), InvoiceItemType.CBA_ADJ);
        assertEquals(childInvoice.getInvoiceItems().get(1).getAmount().compareTo(BigDecimal.valueOf(241.62)), 0);

        // check equal parent invoice
        parentInvoices = invoiceUserApi.getInvoicesByAccount(parentAccount.getId(), false, callContext);
        assertEquals(parentInvoices.size(), 2);

        parentInvoice = parentInvoices.get(1);
        assertEquals(parentInvoice.getNumberOfItems(), 1);
        assertEquals(parentInvoice.getStatus(), InvoiceStatus.COMMITTED);
        assertTrue(parentInvoice.isParentInvoice());
        assertEquals(parentInvoice.getChargedAmount().compareTo(BigDecimal.valueOf(249.95)), 0);

        // ------

        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE);
        final DateTime date = new DateTime(2014, 7, 15, 0, 0, 0, 0, testTimeZone);
        clock.setTime(date);
        assertListenerStatus();

        createBaseEntitlementAndCheckForCompletion(childAccount.getId(), "bundleKey1", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        // Move through time and verify new parent Invoice.
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE);
        clock.addDays(30);
        assertListenerStatus();

        childInvoices = invoiceUserApi.getInvoicesByAccount(childAccount.getId(), false, callContext);
        assertEquals(childInvoices.size(), 5);

        childInvoice = childInvoices.get(4);
        assertEquals(childInvoice.getNumberOfItems(), 2);
        assertEquals(childInvoice.getInvoiceItems().get(0).getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(childInvoice.getInvoiceItems().get(0).getAmount().compareTo(BigDecimal.valueOf(249.95)), 0);
        assertEquals(childInvoice.getInvoiceItems().get(1).getInvoiceItemType(), InvoiceItemType.CBA_ADJ);
        assertEquals(childInvoice.getInvoiceItems().get(1).getAmount().compareTo(BigDecimal.valueOf(-241.62)), 0);

        // check equal parent invoice
        parentInvoices = invoiceUserApi.getInvoicesByAccount(parentAccount.getId(), false, callContext);
        assertEquals(parentInvoices.size(), 4);

        parentInvoice = parentInvoices.get(3);
        assertEquals(parentInvoice.getNumberOfItems(), 1);
        assertEquals(parentInvoice.getStatus(), InvoiceStatus.DRAFT);
        assertTrue(parentInvoice.isParentInvoice());
        // balance is 0 because parent invoice status is DRAFT
        assertEquals(parentInvoice.getBalance().compareTo(BigDecimal.ZERO), 0);
        assertEquals(parentInvoice.getInvoiceItems().get(0).getAmount().compareTo(BigDecimal.valueOf(8.33)), 0);

    }

    // Scenario 6: Transfer credit
    @Test(groups = "slow")
    public void testParentInvoiceTransferCredit() throws Exception {

        final int billingDay = 14;
        final DateTime initialCreationDate = new DateTime(2014, 5, 15, 0, 0, 0, 0, testTimeZone);
        // set clock to the initial start date
        clock.setTime(initialCreationDate);

        final Account parentAccount = createAccountWithNonOsgiPaymentMethod(getAccountData(billingDay));
        final Account childAccount = createAccountWithNonOsgiPaymentMethod(getChildAccountData(billingDay, parentAccount.getId(), true));

        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        invoiceUserApi.insertCredit(childAccount.getId(), new BigDecimal("250"), new LocalDate(clock.getUTCNow(), childAccount.getTimeZone()), childAccount.getCurrency(), true, null, callContext);
        assertListenerStatus();

        BigDecimal childAccountCBA = invoiceUserApi.getAccountCBA(childAccount.getId(), callContext);
        assertEquals(childAccountCBA.compareTo(BigDecimal.valueOf(250)), 0);

        BigDecimal parentAccountCBA = invoiceUserApi.getAccountCBA(parentAccount.getId(), callContext);
        assertEquals(parentAccountCBA.compareTo(BigDecimal.ZERO), 0);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE);
        invoiceUserApi.transferChildCreditToParent(childAccount.getId(), callContext);
        assertListenerStatus();

        childAccountCBA = invoiceUserApi.getAccountCBA(childAccount.getId(), callContext);
        assertEquals(childAccountCBA.compareTo(BigDecimal.ZERO), 0);

        parentAccountCBA = invoiceUserApi.getAccountCBA(parentAccount.getId(), callContext);
        assertEquals(parentAccountCBA.compareTo(BigDecimal.valueOf(250)), 0);

        final List<Invoice> childInvoices = invoiceUserApi.getInvoicesByAccount(childAccount.getId(), false, callContext);
        assertEquals(childInvoices.size(), 2);

        final Invoice childInvoice = childInvoices.get(1);
        assertEquals(childInvoice.getNumberOfItems(), 2);
        assertEquals(childInvoice.getInvoiceItems().get(0).getInvoiceItemType(), InvoiceItemType.EXTERNAL_CHARGE);
        assertEquals(childInvoice.getInvoiceItems().get(0).getAmount().compareTo(BigDecimal.valueOf(250)), 0);
        assertEquals(childInvoice.getInvoiceItems().get(1).getInvoiceItemType(), InvoiceItemType.CBA_ADJ);
        assertEquals(childInvoice.getInvoiceItems().get(1).getAmount().compareTo(BigDecimal.valueOf(-250)), 0);

        // check equal parent invoice
        final List<Invoice> parentInvoices = invoiceUserApi.getInvoicesByAccount(parentAccount.getId(), false, callContext);
        assertEquals(parentInvoices.size(), 1);

        final Invoice parentInvoice = parentInvoices.get(0);
        assertEquals(parentInvoice.getNumberOfItems(), 2);
        assertEquals(parentInvoice.getInvoiceItems().get(0).getInvoiceItemType(), InvoiceItemType.CREDIT_ADJ);
        assertEquals(parentInvoice.getInvoiceItems().get(0).getAmount().compareTo(BigDecimal.valueOf(-250)), 0);
        assertEquals(parentInvoice.getInvoiceItems().get(1).getInvoiceItemType(), InvoiceItemType.CBA_ADJ);
        assertEquals(parentInvoice.getInvoiceItems().get(1).getAmount().compareTo(BigDecimal.valueOf(250)), 0);
    }

    // Scenario 6-b: Transfer credit
    @Test(groups = "slow", expectedExceptions = InvoiceApiException.class,
        expectedExceptionsMessageRegExp = ".* does not have credit")
    public void testParentInvoiceTransferCreditAccountWithoutCredit() throws Exception {

        final int billingDay = 14;
        final DateTime initialCreationDate = new DateTime(2014, 5, 15, 0, 0, 0, 0, testTimeZone);
        // set clock to the initial start date
        clock.setTime(initialCreationDate);

        final Account parentAccount = createAccountWithNonOsgiPaymentMethod(getAccountData(billingDay));
        final Account childAccount = createAccountWithNonOsgiPaymentMethod(getChildAccountData(billingDay, parentAccount.getId(), true));

        BigDecimal childAccountCBA = invoiceUserApi.getAccountCBA(childAccount.getId(), callContext);
        assertEquals(childAccountCBA.compareTo(BigDecimal.ZERO), 0);

        BigDecimal parentAccountCBA = invoiceUserApi.getAccountCBA(parentAccount.getId(), callContext);
        assertEquals(parentAccountCBA.compareTo(BigDecimal.ZERO), 0);

        invoiceUserApi.transferChildCreditToParent(childAccount.getId(), callContext);

    }

    // Scenario 6-c: Transfer credit
    @Test(groups = "slow", expectedExceptions = InvoiceApiException.class,
            expectedExceptionsMessageRegExp = ".* does not have a Parent Account associated")
    public void testParentInvoiceTransferCreditAccountNoParent() throws Exception {

        final int billingDay = 14;
        final DateTime initialCreationDate = new DateTime(2014, 5, 15, 0, 0, 0, 0, testTimeZone);
        // set clock to the initial start date
        clock.setTime(initialCreationDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getChildAccountData(billingDay, null, true));

        BigDecimal childAccountCBA = invoiceUserApi.getAccountCBA(account.getId(), callContext);
        assertEquals(childAccountCBA.compareTo(BigDecimal.ZERO), 0);

        invoiceUserApi.transferChildCreditToParent(account.getId(), callContext);

    }

}
