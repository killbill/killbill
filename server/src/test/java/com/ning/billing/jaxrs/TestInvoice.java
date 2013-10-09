/*
 * Copyright 2010-2013 Ning, Inc.
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

package com.ning.billing.jaxrs;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.jaxrs.json.AccountJson;
import com.ning.billing.jaxrs.json.AuditLogJson;
import com.ning.billing.jaxrs.json.InvoiceItemJson;
import com.ning.billing.jaxrs.json.InvoiceJson;
import com.ning.billing.jaxrs.json.PaymentJson;
import com.ning.billing.jaxrs.json.PaymentMethodJson;
import com.ning.billing.payment.provider.ExternalPaymentProviderPlugin;
import com.ning.billing.util.api.AuditLevel;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

public class TestInvoice extends TestJaxrsBase {

    @Test(groups = "slow")
    public void testInvoiceOk() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final AccountJson accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();

        final List<InvoiceJson> invoices = getInvoicesForAccountWithAudits(accountJson.getAccountId(), AuditLevel.FULL);
        assertEquals(invoices.size(), 2);
        for (final InvoiceJson invoiceJson : invoices) {
            Assert.assertEquals(invoiceJson.getAuditLogs().size(), 1);
            final AuditLogJson auditLogJson = invoiceJson.getAuditLogs().get(0);
            Assert.assertEquals(auditLogJson.getChangeType(), "INSERT");
            Assert.assertEquals(auditLogJson.getChangedBy(), "SubscriptionBaseTransition");
            Assert.assertFalse(auditLogJson.getChangeDate().isBefore(initialDate));
            Assert.assertNotNull(auditLogJson.getUserToken());
            Assert.assertNull(auditLogJson.getReasonCode());
            Assert.assertNull(auditLogJson.getComments());
        }

        // Check we can retrieve an individual invoice
        final InvoiceJson invoiceJson = invoices.get(0);
        final InvoiceJson firstInvoiceJson = getInvoice(invoiceJson.getInvoiceId());
        assertEquals(firstInvoiceJson, invoiceJson);

        // Check we can retrieve the invoice by number
        final InvoiceJson firstInvoiceByNumberJson = getInvoice(invoiceJson.getInvoiceNumber());
        assertEquals(firstInvoiceByNumberJson, invoiceJson);

        // Then create a dryRun Invoice
        final DateTime futureDate = clock.getUTCNow().plusMonths(1).plusDays(3);
        createDryRunInvoice(accountJson.getAccountId(), futureDate);

        // The one more time with no DryRun
        createInvoice(accountJson.getAccountId(), futureDate);

        // Check again # invoices, should be 3 this time
        final List<InvoiceJson> newInvoiceList = getInvoicesForAccount(accountJson.getAccountId());
        assertEquals(newInvoiceList.size(), 3);
    }

    @Test(groups = "slow")
    public void testInvoicePayments() throws Exception {
        clock.setTime(new DateTime(2012, 4, 25, 0, 3, 42, 0));

        final AccountJson accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();

        final List<InvoiceJson> invoices = getInvoicesForAccount(accountJson.getAccountId());
        assertEquals(invoices.size(), 2);

        for (final InvoiceJson cur : invoices) {
            final List<PaymentJson> objFromJson = getPaymentsForInvoice(cur.getInvoiceId());

            if (cur.getAmount().compareTo(BigDecimal.ZERO) == 0) {
                assertEquals(objFromJson.size(), 0);
            } else {
                assertEquals(objFromJson.size(), 1);
                assertEquals(cur.getAmount().compareTo(objFromJson.get(0).getAmount()), 0);
            }
        }
    }

    @Test(groups = "slow")
    public void testPayAllInvoices() throws Exception {
        clock.setTime(new DateTime(2012, 4, 25, 0, 3, 42, 0));

        // No payment method
        final AccountJson accountJson = createAccountNoPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // Check there was no payment made
        assertEquals(getPaymentsForAccount(accountJson.getAccountId()).size(), 1);

        // Get the invoices
        final List<InvoiceJson> invoices = getInvoicesForAccount(accountJson.getAccountId());
        assertEquals(invoices.size(), 2);
        final InvoiceJson invoiceToPay = invoices.get(1);
        assertEquals(invoiceToPay.getBalance().compareTo(BigDecimal.ZERO), 1);

        // Pay all invoices
        payAllInvoices(accountJson, true);
        for (final InvoiceJson invoice : getInvoicesForAccount(accountJson.getAccountId())) {
            assertEquals(invoice.getBalance().compareTo(BigDecimal.ZERO), 0);
        }
        assertEquals(getPaymentsForAccount(accountJson.getAccountId()).size(), 2);
    }

    @Test(groups = "slow")
    public void testInvoiceCreatePayment() throws Exception {
        clock.setTime(new DateTime(2012, 4, 25, 0, 3, 42, 0));

        // STEPH MISSING SET ACCOUNT AUTO_PAY_OFF
        final AccountJson accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // Get the invoices
        final List<InvoiceJson> invoices = getInvoicesForAccount(accountJson.getAccountId());
        assertEquals(invoices.size(), 2);

        for (final InvoiceJson cur : invoices) {
            if (cur.getAmount().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            // CREATE INSTA PAYMENT
            final List<PaymentJson> objFromJson = createInstaPayment(accountJson, cur);
            assertEquals(objFromJson.size(), 1);
            assertEquals(cur.getAmount().compareTo(objFromJson.get(0).getAmount()), 0);
        }
    }

    @Test(groups = "slow")
    public void testExternalPayment() throws Exception {
        final AccountJson accountJson = createAccountNoPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // Verify we didn't get any payment
        final List<PaymentJson> noPaymentsFromJson = getPaymentsForAccount(accountJson.getAccountId());
        assertEquals(noPaymentsFromJson.size(), 1);
        final String initialPaymentId = noPaymentsFromJson.get(0).getPaymentId();

        // Get the invoices
        final List<InvoiceJson> invoices = getInvoicesForAccount(accountJson.getAccountId());
        // 2 invoices but look for the non zero dollar one
        assertEquals(invoices.size(), 2);
        final String invoiceId = invoices.get(1).getInvoiceId();

        // Post an external payment
        final BigDecimal paidAmount = BigDecimal.TEN;
        createExternalPayment(accountJson, invoiceId, paidAmount);

        // Verify we indeed got the payment
        final List<PaymentJson> paymentsFromJson = getPaymentsForAccount(accountJson.getAccountId());
        assertEquals(paymentsFromJson.size(), 2);
        PaymentJson secondPayment = null;
        for (PaymentJson cur : paymentsFromJson) {
            if (! cur.getPaymentId().equals(initialPaymentId)) {
                secondPayment = cur;
                break;
            }
        }
        assertNotNull(secondPayment);

        assertEquals(secondPayment.getPaidAmount().compareTo(paidAmount), 0);

        // Check the PaymentMethod from paymentMethodId returned in the Payment object
        final String paymentMethodId = secondPayment.getPaymentMethodId();
        final PaymentMethodJson paymentMethodJson = getPaymentMethod(paymentMethodId);
        assertEquals(paymentMethodJson.getPaymentMethodId(), paymentMethodId);
        assertEquals(paymentMethodJson.getAccountId(), accountJson.getAccountId());
        assertEquals(paymentMethodJson.getPluginName(), ExternalPaymentProviderPlugin.PLUGIN_NAME);
        assertNull(paymentMethodJson.getPluginInfo());
    }

    @Test(groups = "slow")
    public void testFullInvoiceItemAdjustment() throws Exception {
        final AccountJson accountJson = createAccountNoPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // Get the invoices
        final List<InvoiceJson> invoices = getInvoicesWithItemsForAccount(accountJson.getAccountId());
        // 2 invoices but look for the non zero dollar one
        assertEquals(invoices.size(), 2);
        final InvoiceJson invoice = invoices.get(1);
        // Verify the invoice we picked is non zero
        assertEquals(invoice.getAmount().compareTo(BigDecimal.ZERO), 1);
        final InvoiceItemJson invoiceItem = invoice.getItems().get(0);
        // Verify the item we picked is non zero
        assertEquals(invoiceItem.getAmount().compareTo(BigDecimal.ZERO), 1);

        // Adjust the full amount
        adjustInvoiceItem(accountJson.getAccountId(), invoice.getInvoiceId(), invoiceItem.getInvoiceItemId(), null, null, null);

        // Verify the new invoice balance is zero
        final InvoiceJson adjustedInvoice = getInvoiceWithItemsWithAudits(invoice.getInvoiceId(), AuditLevel.FULL);
        assertEquals(adjustedInvoice.getAmount().compareTo(BigDecimal.ZERO), 0);

        // Verify invoice audit logs
        Assert.assertEquals(adjustedInvoice.getAuditLogs().size(), 1);
        final AuditLogJson invoiceAuditLogJson = adjustedInvoice.getAuditLogs().get(0);
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
        final AuditLogJson itemAuditLogJson = adjustedInvoice.getItems().get(0).getAuditLogs().get(0);
        Assert.assertEquals(itemAuditLogJson.getChangeType(), "INSERT");
        Assert.assertEquals(itemAuditLogJson.getChangedBy(), "SubscriptionBaseTransition");
        Assert.assertNotNull(itemAuditLogJson.getChangeDate());
        Assert.assertNotNull(itemAuditLogJson.getUserToken());
        Assert.assertNull(itemAuditLogJson.getReasonCode());
        Assert.assertNull(itemAuditLogJson.getComments());

        // The second one is the adjustment
        Assert.assertEquals(adjustedInvoice.getItems().get(1).getAuditLogs().size(), 1);
        final AuditLogJson adjustedItemAuditLogJson = adjustedInvoice.getItems().get(1).getAuditLogs().get(0);
        Assert.assertEquals(adjustedItemAuditLogJson.getChangeType(), "INSERT");
        Assert.assertEquals(adjustedItemAuditLogJson.getChangedBy(), createdBy);
        Assert.assertEquals(adjustedItemAuditLogJson.getReasonCode(), reason);
        Assert.assertEquals(adjustedItemAuditLogJson.getComments(), comment);
        Assert.assertNotNull(adjustedItemAuditLogJson.getChangeDate());
        Assert.assertNotNull(adjustedItemAuditLogJson.getUserToken());
    }

    @Test(groups = "slow")
    public void testPartialInvoiceItemAdjustment() throws Exception {
        final AccountJson accountJson = createAccountNoPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // Get the invoices
        final List<InvoiceJson> invoices = getInvoicesWithItemsForAccount(accountJson.getAccountId());
        // 2 invoices but look for the non zero dollar one
        assertEquals(invoices.size(), 2);
        final InvoiceJson invoice = invoices.get(1);
        // Verify the invoice we picked is non zero
        assertEquals(invoice.getAmount().compareTo(BigDecimal.ZERO), 1);
        final InvoiceItemJson invoiceItem = invoice.getItems().get(0);
        // Verify the item we picked is non zero
        assertEquals(invoiceItem.getAmount().compareTo(BigDecimal.ZERO), 1);

        // Adjust partially the item
        final BigDecimal adjustedAmount = invoiceItem.getAmount().divide(BigDecimal.TEN);
        adjustInvoiceItem(accountJson.getAccountId(), invoice.getInvoiceId(), invoiceItem.getInvoiceItemId(), null, adjustedAmount, null);

        // Verify the new invoice balance
        final InvoiceJson adjustedInvoice = getInvoice(invoice.getInvoiceId());
        final BigDecimal adjustedInvoiceBalance = invoice.getBalance().add(adjustedAmount.negate()).setScale(2, BigDecimal.ROUND_HALF_UP);
        assertEquals(adjustedInvoice.getBalance().compareTo(adjustedInvoiceBalance), 0);
    }

    @Test(groups = "slow")
    public void testExternalChargeOnNewInvoice() throws Exception {
        final AccountJson accountJson = createAccountNoPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // Get the invoices
        assertEquals(getInvoicesForAccount(accountJson.getAccountId()).size(), 2);

        // Post an external charge
        final BigDecimal chargeAmount = BigDecimal.TEN;
        final InvoiceJson invoiceWithItems = createExternalCharge(accountJson.getAccountId(), chargeAmount, null, null, null, false);
        assertEquals(invoiceWithItems.getBalance().compareTo(chargeAmount), 0);
        assertEquals(invoiceWithItems.getItems().size(), 1);
        assertNull(invoiceWithItems.getItems().get(0).getBundleId());

        // Verify the total number of invoices
        assertEquals(getInvoicesForAccount(accountJson.getAccountId()).size(), 3);
    }


    @Test(groups = "slow")
    public void testExternalChargeOnNewInvoiceWithAutomaticPayment() throws Exception {
        final AccountJson accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // Get the invoices
        assertEquals(getInvoicesForAccount(accountJson.getAccountId()).size(), 2);

        // Post an external charge
        final BigDecimal chargeAmount = BigDecimal.TEN;
        final InvoiceJson invoiceWithItems = createExternalCharge(accountJson.getAccountId(), chargeAmount, null, null, null, true);
        assertEquals(invoiceWithItems.getBalance().compareTo(BigDecimal.ZERO), 0);
        assertEquals(invoiceWithItems.getItems().size(), 1);
        assertNull(invoiceWithItems.getItems().get(0).getBundleId());

        // Verify the total number of invoices
        assertEquals(getInvoicesForAccount(accountJson.getAccountId()).size(), 3);

    }

    @Test(groups = "slow")
    public void testExternalChargeForBundleOnNewInvoice() throws Exception {
        final AccountJson accountJson = createAccountNoPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // Get the invoices
        assertEquals(getInvoicesForAccount(accountJson.getAccountId()).size(), 2);

        // Post an external charge
        final BigDecimal chargeAmount = BigDecimal.TEN;
        final String bundleId = UUID.randomUUID().toString();
        final InvoiceJson invoiceWithItems = createExternalCharge(accountJson.getAccountId(), chargeAmount, bundleId, null, null, false);
        assertEquals(invoiceWithItems.getBalance().compareTo(chargeAmount), 0);
        assertEquals(invoiceWithItems.getItems().size(), 1);
        assertEquals(invoiceWithItems.getItems().get(0).getBundleId(), bundleId);

        // Verify the total number of invoices
        assertEquals(getInvoicesForAccount(accountJson.getAccountId()).size(), 3);
    }

    @Test(groups = "slow")
    public void testExternalChargeOnExistingInvoice() throws Exception {
        final AccountJson accountJson = createAccountNoPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // Get the invoices
        final List<InvoiceJson> invoices = getInvoicesWithItemsForAccount(accountJson.getAccountId());
        // 2 invoices but look for the non zero dollar one
        assertEquals(invoices.size(), 2);
        final String invoiceId = invoices.get(1).getInvoiceId();
        final BigDecimal originalInvoiceAmount = invoices.get(1).getAmount();
        final int originalNumberOfItemsForInvoice = invoices.get(1).getItems().size();

        // Post an external charge
        final BigDecimal chargeAmount = BigDecimal.TEN;
        final InvoiceJson invoiceWithItems = createExternalChargeForInvoice(accountJson.getAccountId(), invoiceId,
                                                                                     null, chargeAmount, null, null, false);
        assertEquals(invoiceWithItems.getItems().size(), originalNumberOfItemsForInvoice + 1);
        assertNull(invoiceWithItems.getItems().get(originalNumberOfItemsForInvoice).getBundleId());

        // Verify the new invoice balance
        final InvoiceJson adjustedInvoice = getInvoice(invoiceId);
        final BigDecimal adjustedInvoiceBalance = originalInvoiceAmount.add(chargeAmount.setScale(2, RoundingMode.HALF_UP));
        assertEquals(adjustedInvoice.getBalance().compareTo(adjustedInvoiceBalance), 0);
    }

    @Test(groups = "slow")
    public void testExternalChargeOnExistingInvoiceWithAutomaticPayment() throws Exception {
        final AccountJson accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // Get the invoices
        final List<InvoiceJson> invoices = getInvoicesWithItemsForAccount(accountJson.getAccountId());
        // 2 invoices but look for the non zero dollar one
        assertEquals(invoices.size(), 2);
        final String invoiceId = invoices.get(1).getInvoiceId();
        final BigDecimal originalInvoiceAmount = invoices.get(1).getAmount();
        final int originalNumberOfItemsForInvoice = invoices.get(1).getItems().size();

        // Post an external charge
        final BigDecimal chargeAmount = BigDecimal.TEN;
        final InvoiceJson invoiceWithItems = createExternalChargeForInvoice(accountJson.getAccountId(), invoiceId,
                                                                            null, chargeAmount, null, null, true);
        assertEquals(invoiceWithItems.getItems().size(), originalNumberOfItemsForInvoice + 1);
        assertNull(invoiceWithItems.getItems().get(originalNumberOfItemsForInvoice).getBundleId());

        // Verify the new invoice balance
        final InvoiceJson adjustedInvoice = getInvoice(invoiceId);
        assertEquals(adjustedInvoice.getBalance().compareTo(BigDecimal.ZERO), 0);
    }


    @Test(groups = "slow")
    public void testExternalChargeForBundleOnExistingInvoice() throws Exception {
        final AccountJson accountJson = createAccountNoPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // Get the invoices
        final List<InvoiceJson> invoices = getInvoicesWithItemsForAccount(accountJson.getAccountId());
        // 2 invoices but look for the non zero dollar one
        assertEquals(invoices.size(), 2);
        final String invoiceId = invoices.get(1).getInvoiceId();
        final BigDecimal originalInvoiceAmount = invoices.get(1).getAmount();
        final int originalNumberOfItemsForInvoice = invoices.get(1).getItems().size();

        // Post an external charge
        final BigDecimal chargeAmount = BigDecimal.TEN;
        final String bundleId = UUID.randomUUID().toString();
        final InvoiceJson invoiceWithItems = createExternalChargeForInvoice(accountJson.getAccountId(), invoiceId,
                                                                                     bundleId, chargeAmount, null, null, false);
        assertEquals(invoiceWithItems.getItems().size(), originalNumberOfItemsForInvoice + 1);
        assertEquals(invoiceWithItems.getItems().get(originalNumberOfItemsForInvoice).getBundleId(), bundleId);

        // Verify the new invoice balance
        final InvoiceJson adjustedInvoice = getInvoice(invoiceId);
        final BigDecimal adjustedInvoiceBalance = originalInvoiceAmount.add(chargeAmount.setScale(2, RoundingMode.HALF_UP));
        assertEquals(adjustedInvoice.getBalance().compareTo(adjustedInvoiceBalance), 0);
    }
}
