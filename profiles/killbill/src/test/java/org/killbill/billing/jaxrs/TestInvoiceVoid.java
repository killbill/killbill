/*
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
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
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.model.InvoicePayments;
import org.killbill.billing.client.model.gen.Account;
import org.killbill.billing.client.model.gen.Invoice;
import org.killbill.billing.client.model.gen.InvoicePayment;
import org.killbill.billing.client.model.gen.InvoicePaymentTransaction;
import org.killbill.billing.client.model.gen.PaymentTransaction;
import org.killbill.billing.invoice.api.InvoiceStatus;
import org.killbill.billing.notification.plugin.api.ExtBusEventType;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.util.api.AuditLevel;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class TestInvoiceVoid extends TestJaxrsBase {

    @Test(groups = "slow", description = "Can void an invoice")
    public void testInvoiceVoid() throws Exception {
        final Account accountJson = createAccountWithExternalPMBundleAndSubscriptionAndManualPayTagAndWaitForFirstInvoice();
        assertNotNull(accountJson);

        // Verify we didn't get any invoicePayment
        final List<InvoicePayment> noPaymentsFromJson = accountApi.getInvoicePayments(accountJson.getAccountId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        assertEquals(noPaymentsFromJson.size(), 0);

        // Get the invoices
        List<Invoice> invoices = accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, null, false, false, false, true, null, AuditLevel.NONE, requestOptions);
        // 2 invoices but look for the non zero dollar one
        assertEquals(invoices.size(), 2);
        // verify account balance
        Account account = accountApi.getAccount(accountJson.getAccountId(), true, true, AuditLevel.NONE, requestOptions);
        assertEquals(account.getAccountBalance().compareTo(invoices.get(1).getBalance()), 0);

        // void the invoice
        callbackServlet.pushExpectedEvent(ExtBusEventType.INVOICE_ADJUSTMENT);
        invoiceApi.voidInvoice(invoices.get(1).getInvoiceId(), requestOptions);
        callbackServlet.assertListenerStatus();

        // Get the invoices excluding voided
        invoices = accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, null, null, requestOptions);
        // the voided invoice should not be returned
        assertEquals(invoices.size(), 1);

        // Get the invoices including voided
        invoices = accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, null, false, false, true, true, null, AuditLevel.NONE, requestOptions);
        assertEquals(invoices.size(), 2);
        assertEquals(invoices.get(1).getStatus(), InvoiceStatus.VOID);
        assertEquals(invoices.get(1).getBalance().compareTo(BigDecimal.ZERO), 0);

        // check that account balance is zero
        account = accountApi.getAccount(accountJson.getAccountId(), true, true, AuditLevel.NONE, requestOptions);
        assertEquals(account.getAccountBalance().compareTo(BigDecimal.ZERO), 0);

        // After invoice was voided verify the subscription is re-invoiced on a new invoice
        // trigger an invoice generation
        callbackServlet.pushExpectedEvent(ExtBusEventType.INVOICE_CREATION);
        invoiceApi.createFutureInvoice(accountJson.getAccountId(), clock.getToday(DateTimeZone.forID(accountJson.getTimeZone())), NULL_PLUGIN_PROPERTIES, requestOptions);
        callbackServlet.assertListenerStatus();

        // Get the invoices excluding voided
        invoices = accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, null, false, false, false, true, null, AuditLevel.NONE, requestOptions);
        // the voided invoice should not be returned
        assertEquals(invoices.size(), 2);

        // process payment
        final InvoicePayment invoicePayment = processPayment(accountJson, invoices.get(1), false);

        // try to void invoice
        try {
            invoiceApi.voidInvoice(invoices.get(1).getInvoiceId(), requestOptions);
            Assert.fail("VoidInvoice call should fail with 400");
        } catch (final KillBillClientException e) {
            assertTrue(true);
        }

        //refund payment
        refundPayment(invoicePayment);

        // Void invoice
        callbackServlet.pushExpectedEvent(ExtBusEventType.INVOICE_ADJUSTMENT);
        invoiceApi.voidInvoice(invoices.get(1).getInvoiceId(), requestOptions);
        callbackServlet.assertListenerStatus();

        // check that account balance is zero
        account = accountApi.getAccount(accountJson.getAccountId(), true, true, AuditLevel.NONE, requestOptions);
        assertEquals(account.getAccountBalance().compareTo(BigDecimal.ZERO), 0);
    }

    @Test(groups = "slow", description = "Can not void an invoice with partial payment")
    public void testInvoiceVoidWithPartialPay() throws Exception {
        final Account accountJson = createAccountWithExternalPMBundleAndSubscriptionAndManualPayTagAndWaitForFirstInvoice();
        assertNotNull(accountJson);

        // Verify we didn't get any invoicePayment
        final List<InvoicePayment> noPaymentsFromJson = accountApi.getInvoicePayments(accountJson.getAccountId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        assertEquals(noPaymentsFromJson.size(), 0);

        // Get the invoices
        final List<Invoice> invoices = accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, null, false, false, false, true, null, AuditLevel.NONE, requestOptions);
        // 2 invoices but look for the non zero dollar one
        assertEquals(invoices.size(), 2);
        // verify account balance
        final Account account = accountApi.getAccount(accountJson.getAccountId(), true, true, AuditLevel.NONE, requestOptions);
        assertEquals(account.getAccountBalance().compareTo(invoices.get(1).getBalance()), 0);

        // process payment
        final InvoicePayment invoicePayment = processPayment(accountJson, invoices.get(1), true);

        // try to void invoice
        try {
            invoiceApi.voidInvoice(invoices.get(1).getInvoiceId(), requestOptions);
            Assert.fail("VoidInvoice call should fail with 400");
        } catch (final KillBillClientException e) {
            assertTrue(true);
        }
    }

    @Test(groups = "slow", description = "Void a child invoice")
    public void testChildVoidInvoice() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        final LocalDate triggeredDate = new LocalDate(2012, 5, 26);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account parentAccount = createAccount();
        final Account childAccount1 = createAccount(parentAccount.getAccountId());

        // Add a bundle and subscription
        createSubscription(childAccount1.getAccountId(), UUID.randomUUID().toString(), "Shotgun",
                           ProductCategory.BASE, BillingPeriod.MONTHLY);

        // trigger an invoice generation
        callbackServlet.pushExpectedEvent(ExtBusEventType.INVOICE_CREATION);
        invoiceApi.createFutureInvoice(childAccount1.getAccountId(), triggeredDate, NULL_PLUGIN_PROPERTIES, requestOptions);
        callbackServlet.assertListenerStatus();
        List<Invoice> child1Invoices = accountApi.getInvoicesForAccount(childAccount1.getAccountId(), null, null, false, false, true, true, null, AuditLevel.NONE, requestOptions);
        assertEquals(child1Invoices.size(), 2);

        // move one day so that the parent invoice is committed
        callbackServlet.pushExpectedEvents(ExtBusEventType.INVOICE_CREATION, ExtBusEventType.INVOICE_PAYMENT_FAILED);
        clock.addDays(1);
        callbackServlet.assertListenerStatus();
        List<Invoice> parentInvoices = accountApi.getInvoicesForAccount(parentAccount.getAccountId(), null, null, false, false, false, true, null, AuditLevel.NONE, requestOptions);
        assertEquals(parentInvoices.size(), 1);

        // try to void child invoice
        callbackServlet.pushExpectedEvent(ExtBusEventType.INVOICE_ADJUSTMENT);
        invoiceApi.voidInvoice(child1Invoices.get(1).getInvoiceId(), requestOptions);
        callbackServlet.assertListenerStatus();

        //  move the clock 1 month to check if invoices change
        callbackServlet.pushExpectedEvents(ExtBusEventType.SUBSCRIPTION_PHASE,
                                           ExtBusEventType.INVOICE_CREATION,
                                           // Overdue state is computed from the parent state
                                           ExtBusEventType.OVERDUE_CHANGE,
                                           ExtBusEventType.BLOCKING_STATE,
                                           ExtBusEventType.OVERDUE_CHANGE,
                                           ExtBusEventType.BLOCKING_STATE);
        clock.addDays(31);
        callbackServlet.assertListenerStatus();

        // The parent added another invoice, now it has two (duplicate)
        parentInvoices = accountApi.getInvoicesForAccount(parentAccount.getAccountId(), null, null, false, false, false, true, null, AuditLevel.NONE, requestOptions);
        assertEquals(parentInvoices.size(), 2);

        // the child added one invoice as expected
        child1Invoices = accountApi.getInvoicesForAccount(childAccount1.getAccountId(), null, null, false, false, false, true, null, AuditLevel.NONE, requestOptions);
        assertEquals(child1Invoices.size(), 2);
    }

    @Test(groups = "slow", description = "Void a parent invoice")
    public void testParentVoidInvoice() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        final LocalDate triggeredDate = new LocalDate(2012, 5, 26);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account parentAccount = createAccount();
        final Account childAccount1 = createAccount(parentAccount.getAccountId());

        // Add a bundle and subscription
        createSubscription(childAccount1.getAccountId(), UUID.randomUUID().toString(), "Shotgun",
                           ProductCategory.BASE, BillingPeriod.MONTHLY);

        // trigger an invoice generation
        callbackServlet.pushExpectedEvents(ExtBusEventType.INVOICE_CREATION);
        invoiceApi.createFutureInvoice(childAccount1.getAccountId(), triggeredDate, NULL_PLUGIN_PROPERTIES, requestOptions);
        callbackServlet.assertListenerStatus();
        List<Invoice> child1Invoices = accountApi.getInvoicesForAccount(childAccount1.getAccountId(), null, null, false, false, true, true, null, AuditLevel.NONE, requestOptions);
        assertEquals(child1Invoices.size(), 2);

        // move one day so that the parent invoice is committed
        callbackServlet.pushExpectedEvents(ExtBusEventType.INVOICE_CREATION, ExtBusEventType.INVOICE_PAYMENT_FAILED);
        clock.addDays(1);
        callbackServlet.assertListenerStatus();
        List<Invoice> parentInvoices = accountApi.getInvoicesForAccount(parentAccount.getAccountId(), null, null, false, false, false, true, null, AuditLevel.NONE, requestOptions);
        assertEquals(parentInvoices.size(), 1);

        // try to void parent invoice
        callbackServlet.pushExpectedEvent(ExtBusEventType.INVOICE_ADJUSTMENT);
        invoiceApi.voidInvoice(parentInvoices.get(0).getInvoiceId(), requestOptions);
        callbackServlet.assertListenerStatus();

        //  move the clock 1 month to check if invoices change
        callbackServlet.pushExpectedEvents(ExtBusEventType.SUBSCRIPTION_PHASE);
        clock.addDays(31);
        callbackServlet.assertListenerStatus();

        // since the child did not have any change, the parent does not have an invoice
        // after the void.
        parentInvoices = accountApi.getInvoicesForAccount(parentAccount.getAccountId(), null, null, false, false, false, true, null, AuditLevel.NONE, requestOptions);
        assertEquals(parentInvoices.size(), 0);

        // the child does not have any change
        child1Invoices = accountApi.getInvoicesForAccount(childAccount1.getAccountId(), null, null, false, false, true, true, null, AuditLevel.NONE, requestOptions);
        assertEquals(child1Invoices.size(), 2);
    }

    private InvoicePayment processPayment(final Account accountJson, final Invoice invoice, final boolean partialPay) throws Exception {
        final BigDecimal payAmount = partialPay ? invoice.getBalance().subtract(BigDecimal.TEN) : invoice.getBalance();
        final InvoicePayment invoicePayment = new InvoicePayment();
        invoicePayment.setPurchasedAmount(payAmount);
        invoicePayment.setAccountId(accountJson.getAccountId());
        invoicePayment.setTargetInvoiceId(invoice.getInvoiceId());

        callbackServlet.pushExpectedEvents(ExtBusEventType.PAYMENT_SUCCESS, ExtBusEventType.INVOICE_PAYMENT_SUCCESS);
        final InvoicePayment result = invoiceApi.createInstantPayment(invoice.getInvoiceId(), invoicePayment, true, Collections.emptyList(), NULL_PLUGIN_PROPERTIES, requestOptions);
        callbackServlet.assertListenerStatus();
        assertEquals(result.getTransactions().size(), 1);
        assertTrue(result.getTransactions().get(0).getAmount().compareTo(payAmount) == 0);

        return result;
    }

    private void refundPayment(final InvoicePayment payment) throws Exception {
        final InvoicePaymentTransaction refund = new InvoicePaymentTransaction();
        refund.setPaymentId(payment.getPaymentId());
        refund.setAmount(payment.getPurchasedAmount());
        callbackServlet.pushExpectedEvents(ExtBusEventType.PAYMENT_SUCCESS, ExtBusEventType.INVOICE_PAYMENT_SUCCESS);
        invoicePaymentApi.createRefundWithAdjustments(payment.getPaymentId(), refund, payment.getPaymentMethodId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        callbackServlet.assertListenerStatus();

        final InvoicePayments allPayments = accountApi.getInvoicePayments(payment.getAccountId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        assertEquals(allPayments.size(), 1);

        final List<PaymentTransaction> objRefundFromJson = getInvoicePaymentTransactions(allPayments, TransactionType.REFUND);
        assertEquals(objRefundFromJson.size(), 1);
    }
}
