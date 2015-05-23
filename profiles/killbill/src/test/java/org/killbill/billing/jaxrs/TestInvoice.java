/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.jaxrs;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.client.model.Account;
import org.killbill.billing.client.model.AuditLog;
import org.killbill.billing.client.model.Invoice;
import org.killbill.billing.client.model.InvoiceDryRun;
import org.killbill.billing.client.model.InvoiceItem;
import org.killbill.billing.client.model.InvoicePayment;
import org.killbill.billing.client.model.InvoicePayments;
import org.killbill.billing.client.model.Invoices;
import org.killbill.billing.client.model.Payment;
import org.killbill.billing.client.model.PaymentMethod;
import org.killbill.billing.entitlement.api.SubscriptionEventType;
import org.killbill.billing.payment.provider.ExternalPaymentProviderPlugin;
import org.killbill.billing.util.api.AuditLevel;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestInvoice extends TestJaxrsBase {

    @Test(groups = "slow", description = "Can search and retrieve invoices with and without items")
    public void testInvoiceOk() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();

        final List<Invoice> invoices = killBillClient.getInvoicesForAccount(accountJson.getAccountId(), true, false, AuditLevel.FULL);
        assertEquals(invoices.size(), 2);
        for (final Invoice invoiceJson : invoices) {
            Assert.assertEquals(invoiceJson.getAuditLogs().size(), 1);
            final AuditLog auditLogJson = invoiceJson.getAuditLogs().get(0);
            Assert.assertEquals(auditLogJson.getChangeType(), "INSERT");
            Assert.assertEquals(auditLogJson.getChangedBy(), "SubscriptionBaseTransition");
            Assert.assertFalse(auditLogJson.getChangeDate().isBefore(initialDate));
            Assert.assertNotNull(auditLogJson.getUserToken());
            Assert.assertNull(auditLogJson.getReasonCode());
            Assert.assertNull(auditLogJson.getComments());
        }

        final Invoice invoiceJson = invoices.get(0);

        // Check get with & without items
        assertTrue(killBillClient.getInvoice(invoiceJson.getInvoiceId(), Boolean.FALSE).getItems().isEmpty());
        assertTrue(killBillClient.getInvoice(invoiceJson.getInvoiceNumber(), Boolean.FALSE).getItems().isEmpty());
        assertEquals(killBillClient.getInvoice(invoiceJson.getInvoiceId(), Boolean.TRUE).getItems().size(), invoiceJson.getItems().size());
        assertEquals(killBillClient.getInvoice(invoiceJson.getInvoiceNumber(), Boolean.TRUE).getItems().size(), invoiceJson.getItems().size());

        // Check we can retrieve an individual invoice
        final Invoice firstInvoice = killBillClient.getInvoice(invoiceJson.getInvoiceId());
        assertEquals(firstInvoice, invoiceJson);

        // Check we can retrieve the invoice by number
        final Invoice firstInvoiceByNumberJson = killBillClient.getInvoice(invoiceJson.getInvoiceNumber());
        assertEquals(firstInvoiceByNumberJson, invoiceJson);

        // Then create a dryRun for next upcoming invoice
        final Invoice dryRunInvoice = killBillClient.createDryRunInvoice(accountJson.getAccountId(), null, true, null, createdBy, reason, comment);
        assertEquals(dryRunInvoice.getBalance(), new BigDecimal("249.95"));
        assertEquals(dryRunInvoice.getTargetDate(), new LocalDate(2012, 6, 25));
        assertEquals(dryRunInvoice.getItems().size(), 1);
        assertEquals(dryRunInvoice.getItems().get(0).getStartDate(), new LocalDate(2012, 6, 25));
        assertEquals(dryRunInvoice.getItems().get(0).getEndDate(), new LocalDate(2012, 7, 25));
        assertEquals(dryRunInvoice.getItems().get(0).getAmount(), new BigDecimal("249.95"));

        final LocalDate futureDate = dryRunInvoice.getTargetDate();
        // The one more time with no DryRun
        killBillClient.createInvoice(accountJson.getAccountId(), futureDate, createdBy, reason, comment);

        // Check again # invoices, should be 3 this time
        final List<Invoice> newInvoiceList = killBillClient.getInvoicesForAccount(accountJson.getAccountId());
        assertEquals(newInvoiceList.size(), 3);
    }


    @Test(groups = "slow", description = "Can create a subscription in dryRun mode and get an invoice back")
    public void testDryRunSubscriptionCreate() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        // "Assault-Rifle", BillingPeriod.ANNUAL, "rescue", BillingActionPolicy.IMMEDIATE,
        final Account accountJson = createAccountWithDefaultPaymentMethod();
        final InvoiceDryRun dryRunArg = new InvoiceDryRun(SubscriptionEventType.START_BILLING,
                                                          null, "Assault-Rifle", ProductCategory.BASE, BillingPeriod.ANNUAL, null, null, null, null, null, null);
        final Invoice dryRunInvoice = killBillClient.createDryRunInvoice(accountJson.getAccountId(), new LocalDate(initialDate, DateTimeZone.forID(accountJson.getTimeZone())), false, dryRunArg, createdBy, reason, comment);
        assertEquals(dryRunInvoice.getItems().size(), 1);

    }

    @Test(groups = "slow", description = "Can retrieve invoice payments")
    public void testInvoicePayments() throws Exception {
        clock.setTime(new DateTime(2012, 4, 25, 0, 3, 42, 0));

        final Account accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();

        final List<Invoice> invoices = killBillClient.getInvoicesForAccount(accountJson.getAccountId());
        assertEquals(invoices.size(), 2);

        final Invoice invoiceWithPositiveAmount = Iterables.tryFind(invoices, new Predicate<Invoice>() {
            @Override
            public boolean apply(final Invoice input) {
                return input.getAmount().compareTo(BigDecimal.ZERO) > 0;
            }
        }).orNull();
        Assert.assertNotNull(invoiceWithPositiveAmount);

        final InvoicePayments objFromJson = killBillClient.getInvoicePayment(invoiceWithPositiveAmount.getInvoiceId());
        assertEquals(objFromJson.size(), 1);
        assertEquals(invoiceWithPositiveAmount.getAmount().compareTo(objFromJson.get(0).getPurchasedAmount()), 0);
    }

    @Test(groups = "slow", description = "Can pay invoices")
    public void testPayAllInvoices() throws Exception {
        clock.setTime(new DateTime(2012, 4, 25, 0, 3, 42, 0));

        // No payment method
        final Account accountJson = createAccountNoPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // Check there was no payment made
        assertEquals(killBillClient.getPaymentsForAccount(accountJson.getAccountId()).size(), 0);

        // Get the invoices
        final List<Invoice> invoices = killBillClient.getInvoicesForAccount(accountJson.getAccountId());
        assertEquals(invoices.size(), 2);
        final Invoice invoiceToPay = invoices.get(1);
        assertEquals(invoiceToPay.getBalance().compareTo(BigDecimal.ZERO), 1);

        // Pay all invoices
        killBillClient.payAllInvoices(accountJson.getAccountId(), true, null, createdBy, reason, comment);
        for (final Invoice invoice : killBillClient.getInvoicesForAccount(accountJson.getAccountId())) {
            assertEquals(invoice.getBalance().compareTo(BigDecimal.ZERO), 0);
        }
        assertEquals(killBillClient.getPaymentsForAccount(accountJson.getAccountId()).size(), 1);
    }

    @Test(groups = "slow", description = "Can create an insta-payment")
    public void testInvoiceCreatePayment() throws Exception {
        clock.setTime(new DateTime(2012, 4, 25, 0, 3, 42, 0));

        // STEPH MISSING SET ACCOUNT AUTO_PAY_OFF
        final Account accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // Get the invoices
        final List<Invoice> invoices = killBillClient.getInvoicesForAccount(accountJson.getAccountId());
        assertEquals(invoices.size(), 2);

        for (final Invoice cur : invoices) {
            if (cur.getBalance().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            // CREATE PAYMENT
            final InvoicePayment invoicePayment = new InvoicePayment();
            invoicePayment.setPurchasedAmount(cur.getBalance());
            invoicePayment.setAccountId(accountJson.getAccountId());
            invoicePayment.setTargetInvoiceId(cur.getInvoiceId());
            final InvoicePayment objFromJson = killBillClient.createInvoicePayment(invoicePayment, true, createdBy, reason, comment);
            assertEquals(cur.getBalance().compareTo(objFromJson.getPurchasedAmount()), 0);
        }
    }

    @Test(groups = "slow", description = "Can create an external payment")
    public void testExternalPayment() throws Exception {
        final Account accountJson = createAccountNoPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // Verify we didn't get any invoicePayment
        final List<InvoicePayment> noPaymentsFromJson = killBillClient.getInvoicePaymentsForAccount(accountJson.getAccountId());
        assertEquals(noPaymentsFromJson.size(), 0);

        // Get the invoices
        final List<Invoice> invoices = killBillClient.getInvoicesForAccount(accountJson.getAccountId());
        // 2 invoices but look for the non zero dollar one
        assertEquals(invoices.size(), 2);
        final UUID invoiceId = invoices.get(1).getInvoiceId();

        // Post an external invoicePayment
        final InvoicePayment invoicePayment = new InvoicePayment();
        invoicePayment.setPurchasedAmount(BigDecimal.TEN);
        invoicePayment.setAccountId(accountJson.getAccountId());
        invoicePayment.setTargetInvoiceId(invoiceId);
        killBillClient.createInvoicePayment(invoicePayment, true, createdBy, reason, comment);

        // Verify we indeed got the invoicePayment
        final List<InvoicePayment> paymentsFromJson = killBillClient.getInvoicePaymentsForAccount(accountJson.getAccountId());
        assertEquals(paymentsFromJson.size(), 1);
        assertEquals(paymentsFromJson.get(0).getPurchasedAmount().compareTo(BigDecimal.TEN), 0);
        assertEquals(paymentsFromJson.get(0).getTargetInvoiceId(), invoiceId);

        // Check the PaymentMethod from paymentMethodId returned in the Payment object
        final UUID paymentMethodId = paymentsFromJson.get(0).getPaymentMethodId();
        final PaymentMethod paymentMethodJson = killBillClient.getPaymentMethod(paymentMethodId);
        assertEquals(paymentMethodJson.getPaymentMethodId(), paymentMethodId);
        assertEquals(paymentMethodJson.getAccountId(), accountJson.getAccountId());
        assertEquals(paymentMethodJson.getPluginName(), ExternalPaymentProviderPlugin.PLUGIN_NAME);
        assertNull(paymentMethodJson.getPluginInfo());
    }

    @Test(groups = "slow", description = "Can fully adjust an invoice item")
    public void testFullInvoiceItemAdjustment() throws Exception {
        final Account accountJson = createAccountNoPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // Get the invoices
        final List<Invoice> invoices = killBillClient.getInvoicesForAccount(accountJson.getAccountId(), true);
        // 2 invoices but look for the non zero dollar one
        assertEquals(invoices.size(), 2);
        final Invoice invoice = invoices.get(1);
        // Verify the invoice we picked is non zero
        assertEquals(invoice.getAmount().compareTo(BigDecimal.ZERO), 1);
        final InvoiceItem invoiceItem = invoice.getItems().get(0);
        // Verify the item we picked is non zero
        assertEquals(invoiceItem.getAmount().compareTo(BigDecimal.ZERO), 1);

        // Adjust the full amount
        final InvoiceItem adjustmentInvoiceItem = new InvoiceItem();
        adjustmentInvoiceItem.setAccountId(accountJson.getAccountId());
        adjustmentInvoiceItem.setInvoiceId(invoice.getInvoiceId());
        adjustmentInvoiceItem.setInvoiceItemId(invoiceItem.getInvoiceItemId());
        killBillClient.adjustInvoiceItem(invoiceItem, createdBy, reason, comment);

        // Verify the new invoice balance is zero
        final Invoice adjustedInvoice = killBillClient.getInvoice(invoice.getInvoiceId(), true, AuditLevel.FULL);
        assertEquals(adjustedInvoice.getAmount().compareTo(BigDecimal.ZERO), 0);

        // Verify invoice audit logs
        Assert.assertEquals(adjustedInvoice.getAuditLogs().size(), 1);
        final AuditLog invoiceAuditLogJson = adjustedInvoice.getAuditLogs().get(0);
        Assert.assertEquals(invoiceAuditLogJson.getChangeType(), "INSERT");
        Assert.assertEquals(invoiceAuditLogJson.getChangedBy(), "SubscriptionBaseTransition");
        Assert.assertNotNull(invoiceAuditLogJson.getChangeDate());
        Assert.assertNotNull(invoiceAuditLogJson.getUserToken());
        Assert.assertNull(invoiceAuditLogJson.getReasonCode());
        Assert.assertNull(invoiceAuditLogJson.getComments());

        Assert.assertEquals(adjustedInvoice.getItems().size(), 2);

        // Verify invoice items audit logs

        // The first item is the original item
        Assert.assertEquals(adjustedInvoice.getItems().get(0).getAuditLogs().size(), 1);
        final AuditLog itemAuditLogJson = adjustedInvoice.getItems().get(0).getAuditLogs().get(0);
        Assert.assertEquals(itemAuditLogJson.getChangeType(), "INSERT");
        Assert.assertEquals(itemAuditLogJson.getChangedBy(), "SubscriptionBaseTransition");
        Assert.assertNotNull(itemAuditLogJson.getChangeDate());
        Assert.assertNotNull(itemAuditLogJson.getUserToken());
        Assert.assertNull(itemAuditLogJson.getReasonCode());
        Assert.assertNull(itemAuditLogJson.getComments());

        // The second one is the adjustment
        Assert.assertEquals(adjustedInvoice.getItems().get(1).getAuditLogs().size(), 1);
        final AuditLog adjustedItemAuditLogJson = adjustedInvoice.getItems().get(1).getAuditLogs().get(0);
        Assert.assertEquals(adjustedItemAuditLogJson.getChangeType(), "INSERT");
        Assert.assertEquals(adjustedItemAuditLogJson.getChangedBy(), createdBy);
        Assert.assertEquals(adjustedItemAuditLogJson.getReasonCode(), reason);
        Assert.assertEquals(adjustedItemAuditLogJson.getComments(), comment);
        Assert.assertNotNull(adjustedItemAuditLogJson.getChangeDate());
        Assert.assertNotNull(adjustedItemAuditLogJson.getUserToken());
    }

    @Test(groups = "slow", description = "Can partially adjust an invoice item")
    public void testPartialInvoiceItemAdjustment() throws Exception {
        final Account accountJson = createAccountNoPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // Get the invoices
        final List<Invoice> invoices = killBillClient.getInvoicesForAccount(accountJson.getAccountId(), true);
        // 2 invoices but look for the non zero dollar one
        assertEquals(invoices.size(), 2);
        final Invoice invoice = invoices.get(1);
        // Verify the invoice we picked is non zero
        assertEquals(invoice.getAmount().compareTo(BigDecimal.ZERO), 1);
        final InvoiceItem invoiceItem = invoice.getItems().get(0);
        // Verify the item we picked is non zero
        assertEquals(invoiceItem.getAmount().compareTo(BigDecimal.ZERO), 1);

        // Adjust partially the item
        final BigDecimal adjustedAmount = invoiceItem.getAmount().divide(BigDecimal.TEN);
        final InvoiceItem adjustmentInvoiceItem = new InvoiceItem();
        adjustmentInvoiceItem.setAccountId(accountJson.getAccountId());
        adjustmentInvoiceItem.setInvoiceId(invoice.getInvoiceId());
        adjustmentInvoiceItem.setInvoiceItemId(invoiceItem.getInvoiceItemId());
        adjustmentInvoiceItem.setAmount(adjustedAmount);
        adjustmentInvoiceItem.setCurrency(invoice.getCurrency());
        killBillClient.adjustInvoiceItem(adjustmentInvoiceItem, createdBy, reason, comment);

        // Verify the new invoice balance
        final Invoice adjustedInvoice = killBillClient.getInvoice(invoice.getInvoiceId());
        final BigDecimal adjustedInvoiceBalance = invoice.getBalance().add(adjustedAmount.negate()).setScale(2, BigDecimal.ROUND_HALF_UP);
        assertEquals(adjustedInvoice.getBalance().compareTo(adjustedInvoiceBalance), 0, String.format("Adjusted invoice balance is %s, should be %s", adjustedInvoice.getBalance(), adjustedInvoiceBalance));
    }

    @Test(groups = "slow", description = "Can create an external charge")
    public void testExternalChargeOnNewInvoice() throws Exception {
        final Account accountJson = createAccountNoPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // Get the invoices
        assertEquals(killBillClient.getInvoicesForAccount(accountJson.getAccountId()).size(), 2);

        // Post an external charge
        final BigDecimal chargeAmount = BigDecimal.TEN;
        final InvoiceItem externalCharge = new InvoiceItem();
        externalCharge.setAccountId(accountJson.getAccountId());
        externalCharge.setAmount(chargeAmount);
        externalCharge.setCurrency(Currency.valueOf(accountJson.getCurrency()));
        externalCharge.setDescription(UUID.randomUUID().toString());
        final InvoiceItem createdExternalCharge = killBillClient.createExternalCharge(externalCharge, clock.getUTCNow(), false, createdBy, reason, comment);
        final Invoice invoiceWithItems = killBillClient.getInvoice(createdExternalCharge.getInvoiceId(), true);
        assertEquals(invoiceWithItems.getBalance().compareTo(chargeAmount), 0);
        assertEquals(invoiceWithItems.getItems().size(), 1);
        assertEquals(invoiceWithItems.getItems().get(0).getDescription(), externalCharge.getDescription());
        assertNull(invoiceWithItems.getItems().get(0).getBundleId());

        // Verify the total number of invoices
        assertEquals(killBillClient.getInvoicesForAccount(accountJson.getAccountId()).size(), 3);
    }

    @Test(groups = "slow", description = "Can create an external charge and trigger a payment")
    public void testExternalChargeOnNewInvoiceWithAutomaticPayment() throws Exception {
        final Account accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // Get the invoices
        assertEquals(killBillClient.getInvoicesForAccount(accountJson.getAccountId()).size(), 2);

        // Post an external charge
        final BigDecimal chargeAmount = BigDecimal.TEN;
        final InvoiceItem externalCharge = new InvoiceItem();
        externalCharge.setAccountId(accountJson.getAccountId());
        externalCharge.setAmount(chargeAmount);
        externalCharge.setCurrency(Currency.valueOf(accountJson.getCurrency()));
        final InvoiceItem createdExternalCharge = killBillClient.createExternalCharge(externalCharge, clock.getUTCNow(), true, createdBy, reason, comment);
        final Invoice invoiceWithItems = killBillClient.getInvoice(createdExternalCharge.getInvoiceId(), true);
        assertEquals(invoiceWithItems.getBalance().compareTo(BigDecimal.ZERO), 0);
        assertEquals(invoiceWithItems.getItems().size(), 1);
        assertNull(invoiceWithItems.getItems().get(0).getBundleId());

        // Verify the total number of invoices
        assertEquals(killBillClient.getInvoicesForAccount(accountJson.getAccountId()).size(), 3);
    }

    @Test(groups = "slow", description = "Can create an external charge for a bundle")
    public void testExternalChargeForBundleOnNewInvoice() throws Exception {
        final Account accountJson = createAccountNoPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // Get the invoices
        assertEquals(killBillClient.getInvoicesForAccount(accountJson.getAccountId()).size(), 2);

        // Post an external charge
        final BigDecimal chargeAmount = BigDecimal.TEN;
        final UUID bundleId = UUID.randomUUID();
        final InvoiceItem externalCharge = new InvoiceItem();
        externalCharge.setAccountId(accountJson.getAccountId());
        externalCharge.setAmount(chargeAmount);
        externalCharge.setCurrency(Currency.valueOf(accountJson.getCurrency()));
        externalCharge.setBundleId(bundleId);
        final InvoiceItem createdExternalCharge = killBillClient.createExternalCharge(externalCharge, clock.getUTCNow(), false, createdBy, reason, comment);
        final Invoice invoiceWithItems = killBillClient.getInvoice(createdExternalCharge.getInvoiceId(), true);
        assertEquals(invoiceWithItems.getBalance().compareTo(chargeAmount), 0);
        assertEquals(invoiceWithItems.getItems().size(), 1);
        assertEquals(invoiceWithItems.getItems().get(0).getBundleId(), bundleId);

        // Verify the total number of invoices
        assertEquals(killBillClient.getInvoicesForAccount(accountJson.getAccountId()).size(), 3);
    }

    @Test(groups = "slow", description = "Can create an external charge on an existing invoice")
    public void testExternalChargeOnExistingInvoice() throws Exception {
        final Account accountJson = createAccountNoPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // Get the invoices
        final List<Invoice> invoices = killBillClient.getInvoicesForAccount(accountJson.getAccountId(), true);
        // 2 invoices but look for the non zero dollar one
        assertEquals(invoices.size(), 2);
        final UUID invoiceId = invoices.get(1).getInvoiceId();
        final BigDecimal originalInvoiceAmount = invoices.get(1).getAmount();
        final int originalNumberOfItemsForInvoice = invoices.get(1).getItems().size();

        // Post an external charge
        final BigDecimal chargeAmount = BigDecimal.TEN;
        final InvoiceItem externalCharge = new InvoiceItem();
        externalCharge.setAccountId(accountJson.getAccountId());
        externalCharge.setAmount(chargeAmount);
        externalCharge.setCurrency(Currency.valueOf(accountJson.getCurrency()));
        externalCharge.setInvoiceId(invoiceId);
        final InvoiceItem createdExternalCharge = killBillClient.createExternalCharge(externalCharge, clock.getUTCNow(), false, createdBy, reason, comment);
        final Invoice invoiceWithItems = killBillClient.getInvoice(createdExternalCharge.getInvoiceId(), true);
        assertEquals(invoiceWithItems.getItems().size(), originalNumberOfItemsForInvoice + 1);
        assertNull(invoiceWithItems.getItems().get(originalNumberOfItemsForInvoice).getBundleId());

        // Verify the new invoice balance
        final Invoice adjustedInvoice = killBillClient.getInvoice(invoiceId);
        final BigDecimal adjustedInvoiceBalance = originalInvoiceAmount.add(chargeAmount.setScale(2, RoundingMode.HALF_UP));
        assertEquals(adjustedInvoice.getBalance().compareTo(adjustedInvoiceBalance), 0);
    }

    @Test(groups = "slow", description = "Can create an external charge on an existing invoice and trigger a payment")
    public void testExternalChargeOnExistingInvoiceWithAutomaticPayment() throws Exception {
        final Account accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // Get the invoices
        final List<Invoice> invoices = killBillClient.getInvoicesForAccount(accountJson.getAccountId(), true);
        // 2 invoices but look for the non zero dollar one
        assertEquals(invoices.size(), 2);
        final UUID invoiceId = invoices.get(1).getInvoiceId();
        final BigDecimal originalInvoiceAmount = invoices.get(1).getAmount();
        final int originalNumberOfItemsForInvoice = invoices.get(1).getItems().size();

        // Post an external charge
        final BigDecimal chargeAmount = BigDecimal.TEN;
        final InvoiceItem externalCharge = new InvoiceItem();
        externalCharge.setAccountId(accountJson.getAccountId());
        externalCharge.setAmount(chargeAmount);
        externalCharge.setCurrency(Currency.valueOf(accountJson.getCurrency()));
        externalCharge.setInvoiceId(invoiceId);
        final InvoiceItem createdExternalCharge = killBillClient.createExternalCharge(externalCharge, clock.getUTCNow(), true, createdBy, reason, comment);
        final Invoice invoiceWithItems = killBillClient.getInvoice(createdExternalCharge.getInvoiceId(), true);
        assertEquals(invoiceWithItems.getItems().size(), originalNumberOfItemsForInvoice + 1);
        assertNull(invoiceWithItems.getItems().get(originalNumberOfItemsForInvoice).getBundleId());

        // Verify the new invoice balance
        final Invoice adjustedInvoice = killBillClient.getInvoice(invoiceId);
        assertEquals(adjustedInvoice.getBalance().compareTo(BigDecimal.ZERO), 0);
    }

    @Test(groups = "slow", description = "Can create an external charge for a bundle on an existing invoice")
    public void testExternalChargeForBundleOnExistingInvoice() throws Exception {
        final Account accountJson = createAccountNoPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // Get the invoices
        final List<Invoice> invoices = killBillClient.getInvoicesForAccount(accountJson.getAccountId(), true);
        // 2 invoices but look for the non zero dollar one
        assertEquals(invoices.size(), 2);
        final UUID invoiceId = invoices.get(1).getInvoiceId();
        final BigDecimal originalInvoiceAmount = invoices.get(1).getAmount();
        final int originalNumberOfItemsForInvoice = invoices.get(1).getItems().size();

        // Post an external charge
        final BigDecimal chargeAmount = BigDecimal.TEN;
        final UUID bundleId = UUID.randomUUID();
        final InvoiceItem externalCharge = new InvoiceItem();
        externalCharge.setAccountId(accountJson.getAccountId());
        externalCharge.setAmount(chargeAmount);
        externalCharge.setCurrency(Currency.valueOf(accountJson.getCurrency()));
        externalCharge.setInvoiceId(invoiceId);
        externalCharge.setBundleId(bundleId);
        final InvoiceItem createdExternalCharge = killBillClient.createExternalCharge(externalCharge, clock.getUTCNow(), false, createdBy, reason, comment);
        final Invoice invoiceWithItems = killBillClient.getInvoice(createdExternalCharge.getInvoiceId(), true);
        assertEquals(invoiceWithItems.getItems().size(), originalNumberOfItemsForInvoice + 1);
        assertEquals(invoiceWithItems.getItems().get(originalNumberOfItemsForInvoice).getBundleId(), bundleId);

        // Verify the new invoice balance
        final Invoice adjustedInvoice = killBillClient.getInvoice(invoiceId);
        final BigDecimal adjustedInvoiceBalance = originalInvoiceAmount.add(chargeAmount.setScale(2, RoundingMode.HALF_UP));
        assertEquals(adjustedInvoice.getBalance().compareTo(adjustedInvoiceBalance), 0);
    }

    @Test(groups = "slow", description = "Can paginate and search through all invoices")
    public void testInvoicesPagination() throws Exception {
        createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();

        for (int i = 0; i < 3; i++) {
            clock.addMonths(1);
            crappyWaitForLackOfProperSynchonization();
        }

        final Invoices allInvoices = killBillClient.getInvoices();
        Assert.assertEquals(allInvoices.size(), 5);

        for (final Invoice invoice : allInvoices) {
            Assert.assertEquals(killBillClient.searchInvoices(invoice.getInvoiceId().toString()).size(), 1);
            Assert.assertEquals(killBillClient.searchInvoices(invoice.getAccountId().toString()).size(), 5);
            Assert.assertEquals(killBillClient.searchInvoices(invoice.getInvoiceNumber().toString()).size(), 1);
            Assert.assertEquals(killBillClient.searchInvoices(invoice.getCurrency().toString()).size(), 5);
        }

        Invoices page = killBillClient.getInvoices(0L, 1L);
        for (int i = 0; i < 5; i++) {
            Assert.assertNotNull(page);
            Assert.assertEquals(page.size(), 1);
            Assert.assertEquals(page.get(0), allInvoices.get(i));
            page = page.getNext();
        }
        Assert.assertNull(page);
    }
}
