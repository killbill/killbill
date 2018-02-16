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

package org.killbill.billing.jaxrs;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.model.Account;
import org.killbill.billing.client.model.Invoice;
import org.killbill.billing.client.model.InvoiceItem;
import org.killbill.billing.client.model.InvoicePayment;
import org.killbill.billing.client.model.InvoicePaymentTransaction;
import org.killbill.billing.client.model.InvoicePayments;
import org.killbill.billing.client.model.PaymentTransaction;
import org.killbill.billing.invoice.api.InvoiceStatus;
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
        final List<InvoicePayment> noPaymentsFromJson = killBillClient.getInvoicePaymentsForAccount(accountJson.getAccountId(), requestOptions);
        assertEquals(noPaymentsFromJson.size(), 0);

        // Get the invoices
        List<Invoice> invoices = killBillClient.getInvoicesForAccount(accountJson.getAccountId(), requestOptions);
        // 2 invoices but look for the non zero dollar one
        assertEquals(invoices.size(), 2);
        // verify account balance
        Account account = killBillClient.getAccount(accountJson.getAccountId(), true, true, requestOptions);
        assertEquals(account.getAccountBalance().compareTo(invoices.get(1).getBalance()), 0);

        // void the invoice
        killBillClient.voidInvoice(invoices.get(1).getInvoiceId(), requestOptions);

        // Get the invoices excluding voided
        invoices = killBillClient.getInvoicesForAccount(accountJson.getAccountId(), requestOptions);
        // the voided invoice should not be returned
        assertEquals(invoices.size(), 1);

        // Get the invoices including voided
        invoices = killBillClient.getInvoicesForAccount(accountJson.getAccountId(), true, false, true, true, AuditLevel.NONE, requestOptions);
        assertEquals(invoices.size(), 2);
        assertEquals(invoices.get(1).getStatus(), InvoiceStatus.VOID.toString());
        assertEquals(invoices.get(1).getBalance().compareTo(BigDecimal.ZERO), 0);

        // check that account balance is zero
        account = killBillClient.getAccount(accountJson.getAccountId(), true, true, requestOptions);
        assertEquals(account.getAccountBalance().compareTo(BigDecimal.ZERO), 0);

        // After invoice was voided verify the subscription is re-invoiced on a new invoice
        // trigger an invoice generation
        killBillClient.createInvoice(accountJson.getAccountId(), clock.getToday(DateTimeZone.forID(accountJson.getTimeZone())), requestOptions);

        // Get the invoices excluding voided
        invoices = killBillClient.getInvoicesForAccount(accountJson.getAccountId(), requestOptions);
        // the voided invoice should not be returned
        assertEquals(invoices.size(), 2);

        // process payment
        InvoicePayment invoicePayment = processPayment(accountJson, invoices.get(1), false);

        // try to void invoice
        try {
            killBillClient.voidInvoice(invoices.get(1).getInvoiceId(), requestOptions);
            Assert.fail("VoidInvoice call should fail with 400");
        } catch (final KillBillClientException e) {
            assertTrue(true);
        }

        //refund payment
        refundPayment(invoicePayment);

        // try to void invoice
        try {
            killBillClient.voidInvoice(invoices.get(1).getInvoiceId(), requestOptions);
        } catch (final KillBillClientException e) {
            assertTrue(false);
        }

        // check that account balance is zero
        account = killBillClient.getAccount(accountJson.getAccountId(), true, true, requestOptions);
        assertEquals(account.getAccountBalance().compareTo(BigDecimal.ZERO), 0);

    }

    @Test(groups = "slow", description = "Can not void an invoice with partial payment")
    public void testInvoiceVoidWithPartialPay() throws Exception {
        final Account accountJson = createAccountWithExternalPMBundleAndSubscriptionAndManualPayTagAndWaitForFirstInvoice();
        assertNotNull(accountJson);

        // Verify we didn't get any invoicePayment
        final List<InvoicePayment> noPaymentsFromJson = killBillClient.getInvoicePaymentsForAccount(accountJson.getAccountId(), requestOptions);
        assertEquals(noPaymentsFromJson.size(), 0);

        // Get the invoices
        List<Invoice> invoices = killBillClient.getInvoicesForAccount(accountJson.getAccountId(), requestOptions);
        // 2 invoices but look for the non zero dollar one
        assertEquals(invoices.size(), 2);
        // verify account balance
        Account account = killBillClient.getAccount(accountJson.getAccountId(), true, true, requestOptions);
        assertEquals(account.getAccountBalance().compareTo(invoices.get(1).getBalance()), 0);

        // process payment
        InvoicePayment invoicePayment = processPayment(accountJson, invoices.get(1), true);

        // try to void invoice
        try {
            killBillClient.voidInvoice(invoices.get(1).getInvoiceId(), requestOptions);
            Assert.fail("VoidInvoice call should fail with 400");
        } catch (final KillBillClientException e) {
            assertTrue(true);
        }

    }

    private InvoicePayment processPayment(Account accountJson, Invoice invoice, boolean partialPay) throws Exception {

        final BigDecimal payAmount = partialPay ? invoice.getBalance().subtract(BigDecimal.TEN) : invoice.getBalance();
        final InvoicePayment invoicePayment = new InvoicePayment();
        invoicePayment.setPurchasedAmount(payAmount);
        invoicePayment.setAccountId(accountJson.getAccountId());
        invoicePayment.setTargetInvoiceId(invoice.getInvoiceId());

        final InvoicePayment result = killBillClient.createInvoicePayment(invoicePayment, false, requestOptions);
        assertEquals(result.getTransactions().size(), 1);
        assertTrue(result.getTransactions().get(0).getAmount().compareTo(payAmount) == 0);

        return result;
    }

    private void refundPayment(InvoicePayment payment) throws Exception {

        final InvoicePaymentTransaction refund = new InvoicePaymentTransaction();
        refund.setPaymentId(payment.getPaymentId());
        refund.setAmount(payment.getPurchasedAmount());
        killBillClient.createInvoicePaymentRefund(refund, requestOptions);

        final InvoicePayments allPayments = killBillClient.getInvoicePaymentsForAccount(payment.getAccountId(), requestOptions);
        assertEquals(allPayments.size(), 1);

        final List<PaymentTransaction> objRefundFromJson = getPaymentTransactions(allPayments, TransactionType.REFUND.toString());
        assertEquals(objRefundFromJson.size(), 1);
    }
}
