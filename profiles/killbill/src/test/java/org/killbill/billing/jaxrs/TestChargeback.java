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
import java.util.List;
import java.util.UUID;

import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.model.InvoicePayments;
import org.killbill.billing.client.model.gen.Account;
import org.killbill.billing.client.model.gen.Invoice;
import org.killbill.billing.client.model.gen.InvoicePayment;
import org.killbill.billing.client.model.gen.InvoicePaymentTransaction;
import org.killbill.billing.client.model.gen.PaymentTransaction;
import org.killbill.billing.client.model.gen.Subscription;
import org.killbill.billing.notification.plugin.api.ExtBusEventType;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestChargeback extends TestJaxrsBase {

    @Test(groups = "slow", description = "Can create a chargeback")
    public void testAddChargeback() throws Exception {
        final InvoicePayment payment = createAccountWithInvoiceAndPayment();
        createAndVerifyChargeback(payment);
    }

    @Test(groups = "slow", description = "Can create multiple chargebacks")
    public void testMultipleChargeback() throws Exception {
        final InvoicePayment payment = createAccountWithInvoiceAndPayment();

        // We get a 249.95 payment so we do 4 chargeback and then the fifth should fail
        final InvoicePaymentTransaction input = new InvoicePaymentTransaction();
        input.setPaymentId(payment.getPaymentId());
        input.setAmount(new BigDecimal("50.00"));
        int count = 4;
        while (count-- > 0) {
            assertNotNull(invoicePaymentApi.createChargeback(payment.getPaymentId(), input, requestOptions));
        }

        // Last attempt should fail because this is more than the Payment
        final InvoicePayment foo = invoicePaymentApi.createChargeback(payment.getPaymentId(), input, requestOptions);
        final InvoicePayments payments = accountApi.getInvoicePayments(payment.getAccountId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        final List<PaymentTransaction> transactions = getInvoicePaymentTransactions(payments, TransactionType.CHARGEBACK);
        Assert.assertEquals(transactions.size(), 5);
        int found = 0;
        for (final PaymentTransaction transaction : transactions) {
            if (transaction.getStatus().equals(TransactionStatus.SUCCESS)) {
                assertTrue(transaction.getAmount().compareTo(input.getAmount()) == 0);
                assertEquals(transaction.getPaymentId(), input.getPaymentId());
                found++;
            } else {
                assertEquals(transaction.getStatus(), TransactionStatus.PAYMENT_FAILURE);
                found++;
            }
        }
        assertEquals(found, 5);
    }

    @Test(groups = "slow", description = "Can add a chargeback for deleted payment methods")
    public void testAddChargebackForDeletedPaymentMethod() throws Exception {
        final InvoicePayment payment = createAccountWithInvoiceAndPayment();

        // Check the payment method exists
        assertEquals(accountApi.getAccount(payment.getAccountId(), requestOptions).getPaymentMethodId(), payment.getPaymentMethodId());
        assertEquals(paymentMethodApi.getPaymentMethod(payment.getPaymentMethodId(), NULL_PLUGIN_PROPERTIES, requestOptions).getAccountId(), payment.getAccountId());

        // Delete the payment method
        paymentMethodApi.deletePaymentMethod(payment.getPaymentMethodId(), true, false, NULL_PLUGIN_PROPERTIES, requestOptions);

        // Check the payment method was deleted
        assertNull(accountApi.getAccount(payment.getAccountId(), requestOptions).getPaymentMethodId());

        createAndVerifyChargeback(payment);
    }

    @Test(groups = "slow", description = "Cannot add a chargeback for non existent payment")
    public void testInvoicePaymentDoesNotExist() {

        final InvoicePaymentTransaction input = new InvoicePaymentTransaction();
        input.setPaymentId(input.getPaymentId());
        input.setAmount(BigDecimal.TEN);
        try {
            invoicePaymentApi.createChargeback(input.getPaymentId(), input, requestOptions);
            fail();
        } catch (NullPointerException e) {
        } catch (KillBillClientException e) {
            fail();
        }
    }

    @Test(groups = "slow", description = "Cannot add a badly formatted chargeback")
    public void testBadRequest() throws Exception {
        final InvoicePayment payment = createAccountWithInvoiceAndPayment();

        final InvoicePaymentTransaction input = new InvoicePaymentTransaction();
        input.setPaymentId(payment.getPaymentId());

        try {
            invoicePaymentApi.createChargeback(payment.getPaymentId(), input, requestOptions);
            fail();
        } catch (final KillBillClientException e) {
        }
    }

    @Test(groups = "slow", description = "Accounts can have zero chargeback")
    public void testNoChargebackForAccount() throws Exception {
        final List<InvoicePayment> payments = accountApi.getInvoicePayments(UUID.randomUUID(), NULL_PLUGIN_PROPERTIES, requestOptions);
        final List<PaymentTransaction> transactions = getInvoicePaymentTransactions(payments, TransactionType.CHARGEBACK);
        Assert.assertEquals(transactions.size(), 0);
    }

    private void createAndVerifyChargeback(final InvoicePayment payment) throws KillBillClientException {
        // Create the chargeback
        final InvoicePaymentTransaction chargeback = new InvoicePaymentTransaction();
        chargeback.setPaymentId(payment.getPaymentId());
        chargeback.setAmount(BigDecimal.TEN);

        final InvoicePayment chargebackJson = invoicePaymentApi.createChargeback(payment.getPaymentId(), chargeback, requestOptions);
        final List<PaymentTransaction> chargebackTransactions = getInvoicePaymentTransactions(ImmutableList.of(chargebackJson), TransactionType.CHARGEBACK);
        assertEquals(chargebackTransactions.size(), 1);

        assertEquals(chargebackTransactions.get(0).getAmount().compareTo(chargeback.getAmount()), 0);
        assertEquals(chargebackTransactions.get(0).getPaymentId(), chargeback.getPaymentId());

        // Find the chargeback by account
        final List<InvoicePayment> payments = accountApi.getInvoicePayments(payment.getAccountId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        final List<PaymentTransaction> transactions = getInvoicePaymentTransactions(payments, TransactionType.CHARGEBACK);
        Assert.assertEquals(transactions.size(), 1);
        assertEquals(transactions.get(0).getAmount().compareTo(chargeback.getAmount()), 0);
        assertEquals(transactions.get(0).getPaymentId(), chargeback.getPaymentId());
    }

    private InvoicePayment createAccountWithInvoiceAndPayment() throws Exception {
        final Invoice invoice = createAccountWithInvoice();
        return getPayment(invoice);
    }

    private Invoice createAccountWithInvoice() throws Exception {
        // Create account
        final Account accountJson = createAccountWithDefaultPaymentMethod();

        // Create subscription
        final Subscription subscriptionJson = createSubscription(accountJson.getAccountId(), "6253283", "Shotgun",
                                                                 ProductCategory.BASE, BillingPeriod.MONTHLY, true);
        assertNotNull(subscriptionJson);

        // Move after the trial period to trigger an invoice with a non-zero invoice item
        callbackServlet.pushExpectedEvents(ExtBusEventType.SUBSCRIPTION_PHASE,
                                           ExtBusEventType.INVOICE_CREATION,
                                           ExtBusEventType.INVOICE_PAYMENT_SUCCESS,
                                           ExtBusEventType.PAYMENT_SUCCESS);
        clock.addDays(32);
        callbackServlet.assertListenerStatus();

        // Retrieve the invoice
        final List<Invoice> invoices = accountApi.getInvoicesForAccount(accountJson.getAccountId(), requestOptions);
        // We should have two invoices, one for the trial (zero dollar amount) and one for the first month
        assertEquals(invoices.size(), 2);
        assertTrue(invoices.get(1).getAmount().doubleValue() > 0);

        return invoices.get(1);
    }

    private InvoicePayment getPayment(final Invoice invoice) throws KillBillClientException {
        final InvoicePayments payments = invoiceApi.getPaymentsForInvoice(invoice.getInvoiceId(), requestOptions);
        assertNotNull(payments);
        assertEquals(payments.size(), 1);
        return payments.get(0);
    }
}
