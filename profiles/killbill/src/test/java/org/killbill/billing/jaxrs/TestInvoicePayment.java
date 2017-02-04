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

import org.joda.time.DateTime;
import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.model.Account;
import org.killbill.billing.client.model.Invoice;
import org.killbill.billing.client.model.InvoiceItem;
import org.killbill.billing.client.model.InvoicePayment;
import org.killbill.billing.client.model.InvoicePaymentTransaction;
import org.killbill.billing.client.model.InvoicePayments;
import org.killbill.billing.client.model.Invoices;
import org.killbill.billing.client.model.Payment;
import org.killbill.billing.client.model.PaymentMethod;
import org.killbill.billing.client.model.PaymentTransaction;
import org.killbill.billing.client.model.Payments;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.provider.MockPaymentProviderPlugin;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestInvoicePayment extends TestJaxrsBase {

    @Inject
    protected OSGIServiceRegistration<PaymentPluginApi> registry;

    private MockPaymentProviderPlugin mockPaymentProviderPlugin;

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        mockPaymentProviderPlugin = (MockPaymentProviderPlugin) registry.getServiceForName(PLUGIN_NAME);
    }

    @Test(groups = "slow")
    public void testRetrievePayment() throws Exception {
        final InvoicePayment paymentJson = setupScenarioWithPayment();
        final Payment retrievedPaymentJson = killBillClient.getPayment(paymentJson.getPaymentId(), false);
        Assert.assertTrue(retrievedPaymentJson.equals((Payment) paymentJson));
    }

    @Test(groups = "slow", description = "Can create a full refund with no adjustment")
    public void testFullRefundWithNoAdjustment() throws Exception {
        final InvoicePayment invoicePaymentJson = setupScenarioWithPayment();

        // Issue a refund for the full amount
        final BigDecimal refundAmount = invoicePaymentJson.getPurchasedAmount();
        final BigDecimal expectedInvoiceBalance = refundAmount;

        // Post and verify the refund
        final InvoicePaymentTransaction refund = new InvoicePaymentTransaction();
        refund.setPaymentId(invoicePaymentJson.getPaymentId());
        refund.setAmount(refundAmount);
        final Payment paymentAfterRefundJson = killBillClient.createInvoicePaymentRefund(refund, createdBy, reason, comment);
        verifyRefund(invoicePaymentJson, paymentAfterRefundJson, refundAmount);

        // Verify the invoice balance
        verifyInvoice(invoicePaymentJson, expectedInvoiceBalance);
    }

    @Test(groups = "slow", description = "Can create a partial refund with no adjustment")
    public void testPartialRefundWithNoAdjustment() throws Exception {
        final InvoicePayment paymentJson = setupScenarioWithPayment();

        // Issue a refund for a fraction of the amount
        final BigDecimal refundAmount = getFractionOfAmount(paymentJson.getPurchasedAmount());
        final BigDecimal expectedInvoiceBalance = refundAmount;

        // Post and verify the refund
        final InvoicePaymentTransaction refund = new InvoicePaymentTransaction();
        refund.setPaymentId(paymentJson.getPaymentId());
        refund.setAmount(refundAmount);
        final Payment paymentAfterRefundJson = killBillClient.createInvoicePaymentRefund(refund, createdBy, reason, comment);
        verifyRefund(paymentJson, paymentAfterRefundJson, refundAmount);

        // Verify the invoice balance
        verifyInvoice(paymentJson, expectedInvoiceBalance);
    }

    @Test(groups = "slow", description = "Can create a full refund with invoice item adjustment")
    public void testRefundWithFullInvoiceItemAdjustment() throws Exception {
        final InvoicePayment paymentJson = setupScenarioWithPayment();

        // Get the individual items for the invoice
        final Invoice invoice = killBillClient.getInvoice(paymentJson.getTargetInvoiceId(), true);
        final InvoiceItem itemToAdjust = invoice.getItems().get(0);

        // Issue a refund for the full amount
        final BigDecimal refundAmount = itemToAdjust.getAmount();
        final BigDecimal expectedInvoiceBalance = BigDecimal.ZERO;

        // Post and verify the refund
        final InvoicePaymentTransaction refund = new InvoicePaymentTransaction();
        refund.setPaymentId(paymentJson.getPaymentId());
        refund.setAmount(refundAmount);
        refund.setIsAdjusted(true);
        final InvoiceItem adjustment = new InvoiceItem();
        adjustment.setInvoiceItemId(itemToAdjust.getInvoiceItemId());
        /* null amount means full adjustment for that item */
        refund.setAdjustments(ImmutableList.<InvoiceItem>of(adjustment));
        final Payment paymentAfterRefundJson = killBillClient.createInvoicePaymentRefund(refund, createdBy, reason, comment);
        verifyRefund(paymentJson, paymentAfterRefundJson, refundAmount);

        // Verify the invoice balance
        verifyInvoice(paymentJson, expectedInvoiceBalance);
    }

    @Test(groups = "slow", description = "Can create a partial refund with invoice item adjustment")
    public void testPartialRefundWithInvoiceItemAdjustment() throws Exception {
        final InvoicePayment paymentJson = setupScenarioWithPayment();

        // Get the individual items for the invoice
        final Invoice invoice = killBillClient.getInvoice(paymentJson.getTargetInvoiceId(), true);
        final InvoiceItem itemToAdjust = invoice.getItems().get(0);

        // Issue a refund for a fraction of the amount
        final BigDecimal refundAmount = getFractionOfAmount(itemToAdjust.getAmount());
        final BigDecimal expectedInvoiceBalance = BigDecimal.ZERO;

        // Post and verify the refund
        final InvoicePaymentTransaction refund = new InvoicePaymentTransaction();
        refund.setPaymentId(paymentJson.getPaymentId());
        refund.setIsAdjusted(true);
        final InvoiceItem adjustment = new InvoiceItem();
        adjustment.setInvoiceItemId(itemToAdjust.getInvoiceItemId());
        adjustment.setAmount(refundAmount);
        refund.setAdjustments(ImmutableList.<InvoiceItem>of(adjustment));
        final Payment paymentAfterRefundJson = killBillClient.createInvoicePaymentRefund(refund, createdBy, reason, comment);
        verifyRefund(paymentJson, paymentAfterRefundJson, refundAmount);

        // Verify the invoice balance
        verifyInvoice(paymentJson, expectedInvoiceBalance);
    }

    @Test(groups = "slow", description = "Cannot create invoice item adjustments for more than the refund amount")
    public void testPartialRefundWithFullInvoiceItemAdjustment() throws Exception {
        final InvoicePayment paymentJson = setupScenarioWithPayment();

        // Get the individual items for the invoice
        final Invoice invoice = killBillClient.getInvoice(paymentJson.getTargetInvoiceId(), true, false, requestOptions);
        final InvoiceItem itemToAdjust = invoice.getItems().get(0);

        // Issue a refund for a fraction of the amount
        final BigDecimal refundAmount = getFractionOfAmount(itemToAdjust.getAmount());
        final BigDecimal expectedInvoiceBalance = invoice.getBalance();

        // Post and verify the refund
        final InvoicePaymentTransaction refund = new InvoicePaymentTransaction();
        refund.setPaymentId(paymentJson.getPaymentId());
        refund.setAmount(refundAmount);
        refund.setIsAdjusted(true);
        final InvoiceItem adjustment = new InvoiceItem();
        adjustment.setInvoiceItemId(itemToAdjust.getInvoiceItemId());
        // Ask for an adjustment for the full amount (bigger than the refund amount)
        adjustment.setAmount(itemToAdjust.getAmount());
        refund.setAdjustments(ImmutableList.<InvoiceItem>of(adjustment));
        final Payment paymentAfterRefundJson = killBillClient.createInvoicePaymentRefund(refund, requestOptions);

        // The refund did go through
        verifyRefund(paymentJson, paymentAfterRefundJson, refundAmount);

        // But not the adjustments
        verifyInvoice(paymentJson, expectedInvoiceBalance);
    }

    @Test(groups = "slow", description = "Can paginate through all payments and refunds")
    public void testPaymentsAndRefundsPagination() throws Exception {
        InvoicePayment lastPayment = setupScenarioWithPayment();

        for (int i = 0; i < 5; i++) {
            final InvoicePaymentTransaction refund = new InvoicePaymentTransaction();
            refund.setPaymentId(lastPayment.getPaymentId());
            refund.setAmount(lastPayment.getPurchasedAmount());
            killBillClient.createInvoicePaymentRefund(refund, createdBy, reason, comment);

            final InvoicePayment invoicePayment = new InvoicePayment();
            invoicePayment.setPurchasedAmount(lastPayment.getPurchasedAmount());
            invoicePayment.setAccountId(lastPayment.getAccountId());
            invoicePayment.setTargetInvoiceId(lastPayment.getTargetInvoiceId());
            final InvoicePayment payment = killBillClient.createInvoicePayment(invoicePayment, false, createdBy, reason, comment);
            lastPayment = payment;
        }

        final InvoicePayments allPayments = killBillClient.getInvoicePaymentsForAccount(lastPayment.getAccountId());
        Assert.assertEquals(allPayments.size(), 6);

        final List<PaymentTransaction> objRefundFromJson = getPaymentTransactions(allPayments, TransactionType.REFUND.toString());
        Assert.assertEquals(objRefundFromJson.size(), 5);

        Payments paymentsPage = killBillClient.getPayments(0L, 1L);
        for (int i = 0; i < 6; i++) {
            Assert.assertNotNull(paymentsPage);
            Assert.assertEquals(paymentsPage.size(), 1);
            Assert.assertTrue(paymentsPage.get(0).equals((Payment) allPayments.get(i)));
            paymentsPage = paymentsPage.getNext();
        }
        Assert.assertNull(paymentsPage);
    }

    @Test(groups = "slow")
    public void testWithFailedInvoicePayment() throws Exception {

        mockPaymentProviderPlugin.makeNextPaymentFailWithError();

        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();

        InvoicePayments invoicePayments = killBillClient.getInvoicePaymentsForAccount(accountJson.getAccountId(), requestOptions);
        assertEquals(invoicePayments.size(), 1);

        final InvoicePayment invoicePayment = invoicePayments.get(0);
        // Verify targetInvoiceId is not Null. See #593
        assertNotNull(invoicePayment.getTargetInvoiceId());

        final Invoices invoices = killBillClient.getInvoicesForAccount(accountJson.getAccountId(), requestOptions);
        assertEquals(invoices.size(), 2);
        final Invoice invoice = invoices.get(1);
        // Verify this is the correct value
        assertEquals(invoicePayment.getTargetInvoiceId(), invoice.getInvoiceId());

        // Make a payment and verify both invoice payment point to the same targetInvoiceId
        killBillClient.payAllInvoices(accountJson.getAccountId(), false, null, requestOptions);
        invoicePayments = killBillClient.getInvoicePaymentsForAccount(accountJson.getAccountId(), requestOptions);
        assertEquals(invoicePayments.size(), 2);
        for (final InvoicePayment cur : invoicePayments) {
            assertEquals(cur.getTargetInvoiceId(), invoice.getInvoiceId());
        }
    }

    private BigDecimal getFractionOfAmount(final BigDecimal amount) {
        return amount.divide(BigDecimal.TEN).setScale(2, BigDecimal.ROUND_HALF_UP);
    }

    private InvoicePayment setupScenarioWithPayment() throws Exception {
        final Account accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();

        final List<InvoicePayment> paymentsForAccount = killBillClient.getInvoicePaymentsForAccount(accountJson.getAccountId());
        Assert.assertEquals(paymentsForAccount.size(), 1);

        final InvoicePayment paymentJson = paymentsForAccount.get(0);

        // Check the PaymentMethod from paymentMethodId returned in the Payment object
        final UUID paymentMethodId = paymentJson.getPaymentMethodId();
        final PaymentMethod paymentMethodJson = killBillClient.getPaymentMethod(paymentMethodId, true);
        Assert.assertEquals(paymentMethodJson.getPaymentMethodId(), paymentMethodId);
        Assert.assertEquals(paymentMethodJson.getAccountId(), accountJson.getAccountId());

        // Verify the refunds
        final List<PaymentTransaction> objRefundFromJson = getPaymentTransactions(paymentsForAccount, TransactionType.REFUND.toString());
        Assert.assertEquals(objRefundFromJson.size(), 0);
        return paymentJson;
    }

    private void verifyRefund(final InvoicePayment paymentJson, final Payment paymentAfterRefund, final BigDecimal refundAmount) throws KillBillClientException {

        final List<PaymentTransaction> transactions = getPaymentTransactions(ImmutableList.of(paymentAfterRefund), TransactionType.REFUND.toString());
        Assert.assertEquals(transactions.size(), 1);

        final PaymentTransaction refund = transactions.get(0);
        Assert.assertEquals(refund.getPaymentId(), paymentJson.getPaymentId());
        Assert.assertEquals(refund.getAmount().setScale(2, RoundingMode.HALF_UP), refundAmount.setScale(2, RoundingMode.HALF_UP));
        Assert.assertEquals(refund.getCurrency(), DEFAULT_CURRENCY);
        Assert.assertEquals(refund.getStatus(), "SUCCESS");
        Assert.assertEquals(refund.getEffectiveDate().getYear(), clock.getUTCNow().getYear());
        Assert.assertEquals(refund.getEffectiveDate().getMonthOfYear(), clock.getUTCNow().getMonthOfYear());
        Assert.assertEquals(refund.getEffectiveDate().getDayOfMonth(), clock.getUTCNow().getDayOfMonth());

        // Verify the refund via the payment API
        final Payment retrievedPaymentJson = killBillClient.getPayment(paymentJson.getPaymentId(), true);
        Assert.assertEquals(retrievedPaymentJson.getPaymentId(), paymentJson.getPaymentId());
        Assert.assertEquals(retrievedPaymentJson.getPurchasedAmount().setScale(2, RoundingMode.HALF_UP), paymentJson.getPurchasedAmount().setScale(2, RoundingMode.HALF_UP));
        Assert.assertEquals(retrievedPaymentJson.getAccountId(), paymentJson.getAccountId());
        Assert.assertEquals(retrievedPaymentJson.getCurrency(), paymentJson.getCurrency());
        Assert.assertEquals(retrievedPaymentJson.getPaymentMethodId(), paymentJson.getPaymentMethodId());
    }

    private void verifyInvoice(final InvoicePayment paymentJson, final BigDecimal expectedInvoiceBalance) throws KillBillClientException {
        final Invoice invoiceJson = killBillClient.getInvoice(paymentJson.getTargetInvoiceId());
        Assert.assertEquals(invoiceJson.getBalance().setScale(2, BigDecimal.ROUND_HALF_UP),
                            expectedInvoiceBalance.setScale(2, BigDecimal.ROUND_HALF_UP));
    }
}
