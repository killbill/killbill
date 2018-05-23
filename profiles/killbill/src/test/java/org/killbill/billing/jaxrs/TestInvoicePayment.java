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
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.model.InvoicePayments;
import org.killbill.billing.client.model.Invoices;
import org.killbill.billing.client.model.Payments;
import org.killbill.billing.client.model.gen.Account;
import org.killbill.billing.client.model.gen.Invoice;
import org.killbill.billing.client.model.gen.InvoiceItem;
import org.killbill.billing.client.model.gen.InvoicePayment;
import org.killbill.billing.client.model.gen.InvoicePaymentTransaction;
import org.killbill.billing.client.model.gen.Payment;
import org.killbill.billing.client.model.gen.PaymentMethod;
import org.killbill.billing.client.model.gen.PaymentTransaction;
import org.killbill.billing.client.model.gen.Subscription;
import org.killbill.billing.notification.plugin.api.ExtBusEventType;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.provider.MockPaymentProviderPlugin;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.tag.ControlTagType;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestInvoicePayment extends TestJaxrsBase {

    @Inject
    protected OSGIServiceRegistration<PaymentPluginApi> registry;

    private MockPaymentProviderPlugin mockPaymentProviderPlugin;

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();
        mockPaymentProviderPlugin = (MockPaymentProviderPlugin) registry.getServiceForName(PLUGIN_NAME);
    }

    @Test(groups = "slow")
    public void testRetrievePayment() throws Exception {
        final InvoicePayment paymentJson = setupScenarioWithPayment(true);
        final Payment retrievedPaymentJson = paymentApi.getPayment(paymentJson.getPaymentId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        verifyInvoicePaymentAgainstPayment(paymentJson, retrievedPaymentJson);
    }

    @Test(groups = "slow")
    public void testInvoicePaymentCompletion() throws Exception {
        mockPaymentProviderPlugin.makeNextPaymentPending();

        final InvoicePayment paymentJson = setupScenarioWithPayment(false);

        final Payment retrievedPaymentJson = paymentApi.getPayment(paymentJson.getPaymentId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        verifyInvoicePaymentAgainstPayment(paymentJson, retrievedPaymentJson);
        Assert.assertEquals(retrievedPaymentJson.getTransactions().size(), 1);
        Assert.assertEquals(retrievedPaymentJson.getTransactions().get(0).getStatus(), TransactionStatus.PENDING);

        final PaymentTransaction completeTransactionByPaymentId = new PaymentTransaction();
        completeTransactionByPaymentId.setPaymentId(retrievedPaymentJson.getPaymentId());

        final Account accountWithBalance = accountApi.getAccount(paymentJson.getAccountId(), true, false, AuditLevel.NONE, requestOptions);
        Assert.assertTrue(accountWithBalance.getAccountBalance().compareTo(BigDecimal.ZERO) > 0);

        invoicePaymentApi.completeInvoicePaymentTransaction(retrievedPaymentJson.getPaymentId(), new PaymentTransaction(), NULL_PLUGIN_NAMES, NULL_PLUGIN_PROPERTIES, requestOptions);
        final Payment completedPayment = paymentApi.getPayment(paymentJson.getPaymentId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        Assert.assertEquals(completedPayment.getTransactions().get(0).getStatus(), TransactionStatus.SUCCESS);

        final Account accountWithBalance2 = accountApi.getAccount(paymentJson.getAccountId(), true, false, AuditLevel.NONE, requestOptions);
        Assert.assertEquals(accountWithBalance2.getAccountBalance().compareTo(BigDecimal.ZERO), 0);

    }

    @Test(groups = "slow", description = "Can create a full refund with no adjustment")
    public void testFullRefundWithNoAdjustment() throws Exception {
        final InvoicePayment invoicePaymentJson = setupScenarioWithPayment(true);

        // Issue a refund for the full amount
        final BigDecimal refundAmount = invoicePaymentJson.getPurchasedAmount();
        final BigDecimal expectedInvoiceBalance = refundAmount;

        // Post and verify the refund
        final InvoicePaymentTransaction refund = new InvoicePaymentTransaction();
        refund.setPaymentId(invoicePaymentJson.getPaymentId());
        refund.setAmount(refundAmount);
        invoicePaymentApi.createRefundWithAdjustments(invoicePaymentJson.getPaymentId(), refund, invoicePaymentJson.getPaymentMethodId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        final Payment paymentAfterRefundJson = paymentApi.getPayment(invoicePaymentJson.getPaymentId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        verifyRefund(invoicePaymentJson, paymentAfterRefundJson, refundAmount);

        // Verify the invoice balance
        verifyInvoice(invoicePaymentJson, expectedInvoiceBalance);
    }

    @Test(groups = "slow", description = "Can create a partial refund with no adjustment")
    public void testPartialRefundWithNoAdjustment() throws Exception {
        final InvoicePayment paymentJson = setupScenarioWithPayment(true);

        // Issue a refund for a fraction of the amount
        final BigDecimal refundAmount = getFractionOfAmount(paymentJson.getPurchasedAmount());
        final BigDecimal expectedInvoiceBalance = refundAmount;

        // Post and verify the refund
        final InvoicePaymentTransaction refund = new InvoicePaymentTransaction();
        refund.setPaymentId(paymentJson.getPaymentId());
        refund.setAmount(refundAmount);

        invoicePaymentApi.createRefundWithAdjustments(paymentJson.getPaymentId(), refund, paymentJson.getPaymentMethodId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        final Payment paymentAfterRefundJson = paymentApi.getPayment(paymentJson.getPaymentId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        verifyRefund(paymentJson, paymentAfterRefundJson, refundAmount);

        // Verify the invoice balance
        verifyInvoice(paymentJson, expectedInvoiceBalance);
    }

    @Test(groups = "slow", description = "Can create a full refund with invoice item adjustment")
    public void testRefundWithFullInvoiceItemAdjustment() throws Exception {
        final InvoicePayment paymentJson = setupScenarioWithPayment(true);

        // Get the individual items for the invoice
        final Invoice invoice = invoiceApi.getInvoice(paymentJson.getTargetInvoiceId(), true, false, AuditLevel.NONE, requestOptions);
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
        //null amount means full adjustment for that item
        refund.setAdjustments(ImmutableList.<InvoiceItem>of(adjustment));

        invoicePaymentApi.createRefundWithAdjustments(paymentJson.getPaymentId(), refund, paymentJson.getPaymentMethodId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        final Payment paymentAfterRefundJson = paymentApi.getPayment(paymentJson.getPaymentId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        verifyRefund(paymentJson, paymentAfterRefundJson, refundAmount);

        // Verify the invoice balance
        verifyInvoice(paymentJson, expectedInvoiceBalance);
    }

    @Test(groups = "slow", description = "Can create a partial refund with invoice item adjustment")
    public void testPartialRefundWithInvoiceItemAdjustment() throws Exception {
        final InvoicePayment paymentJson = setupScenarioWithPayment(true);

        // Get the individual items for the invoice
        final Invoice invoice = invoiceApi.getInvoice(paymentJson.getTargetInvoiceId(), true, false, AuditLevel.NONE, requestOptions);
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

        invoicePaymentApi.createRefundWithAdjustments(paymentJson.getPaymentId(), refund, paymentJson.getPaymentMethodId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        final Payment paymentAfterRefundJson = paymentApi.getPayment(paymentJson.getPaymentId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        verifyRefund(paymentJson, paymentAfterRefundJson, refundAmount);

        // Verify the invoice balance
        verifyInvoice(paymentJson, expectedInvoiceBalance);
    }

    @Test(groups = "slow", description = "Cannot create invoice item adjustments for more than the refund amount")
    public void testPartialRefundWithFullInvoiceItemAdjustment() throws Exception {
        final InvoicePayment paymentJson = setupScenarioWithPayment(true);

        // Get the individual items for the invoice
        final Invoice invoice = invoiceApi.getInvoice(paymentJson.getTargetInvoiceId(), true, false, AuditLevel.NONE, requestOptions);
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

        invoicePaymentApi.createRefundWithAdjustments(paymentJson.getPaymentId(), refund, paymentJson.getPaymentMethodId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        final Payment paymentAfterRefundJson = paymentApi.getPayment(paymentJson.getPaymentId(), NULL_PLUGIN_PROPERTIES, requestOptions);

        // The refund did go through
        verifyRefund(paymentJson, paymentAfterRefundJson, refundAmount);

        // But not the adjustments
        verifyInvoice(paymentJson, expectedInvoiceBalance);
    }

    @Test(groups = "slow", description = "Can paginate through all payments and refunds")
    public void testPaymentsAndRefundsPagination() throws Exception {
        InvoicePayment lastPayment = setupScenarioWithPayment(true);

        for (int i = 0; i < 5; i++) {
            final InvoicePaymentTransaction refund = new InvoicePaymentTransaction();
            refund.setPaymentId(lastPayment.getPaymentId());
            refund.setAmount(lastPayment.getPurchasedAmount());

            invoicePaymentApi.createRefundWithAdjustments(lastPayment.getPaymentId(), refund, lastPayment.getPaymentMethodId(), NULL_PLUGIN_PROPERTIES, requestOptions);

            final InvoicePayment invoicePayment = new InvoicePayment();
            invoicePayment.setPurchasedAmount(lastPayment.getPurchasedAmount());
            invoicePayment.setAccountId(lastPayment.getAccountId());
            invoicePayment.setTargetInvoiceId(lastPayment.getTargetInvoiceId());
            final InvoicePayment payment = invoiceApi.createInstantPayment(lastPayment.getTargetInvoiceId(), invoicePayment, NULL_PLUGIN_PROPERTIES, requestOptions);
            lastPayment = payment;
        }

        final InvoicePayments allPayments = accountApi.getInvoicePayments(lastPayment.getAccountId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        Assert.assertEquals(allPayments.size(), 6);

        final List<PaymentTransaction> objRefundFromJson = getInvoicePaymentTransactions(allPayments, TransactionType.REFUND);
        Assert.assertEquals(objRefundFromJson.size(), 5);

        Payments paymentsPage = paymentApi.getPayments(0L, 1L, null, false, false, NULL_PLUGIN_PROPERTIES, AuditLevel.NONE, requestOptions);
        for (int i = 0; i < 6; i++) {
            Assert.assertNotNull(paymentsPage);
            Assert.assertEquals(paymentsPage.size(), 1);
            verifyInvoicePaymentAgainstPayment(allPayments.get(i), paymentsPage.get(0));
            paymentsPage = paymentsPage.getNext();
        }
        assertNull(paymentsPage);
    }

    @Test(groups = "slow")
    public void testWithFailedInvoicePayment() throws Exception {

        mockPaymentProviderPlugin.makeNextPaymentFailWithError();

        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice(false);

        InvoicePayments invoicePayments = accountApi.getInvoicePayments(accountJson.getAccountId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        assertEquals(invoicePayments.size(), 1);

        final InvoicePayment invoicePayment = invoicePayments.get(0);
        // Verify targetInvoiceId is not Null. See #593
        assertNotNull(invoicePayment.getTargetInvoiceId());

        final Invoices invoices = accountApi.getInvoicesForAccount(accountJson.getAccountId(), requestOptions);
        assertEquals(invoices.size(), 2);
        final Invoice invoice = invoices.get(1);
        // Verify this is the correct value
        assertEquals(invoicePayment.getTargetInvoiceId(), invoice.getInvoiceId());

        // Make a payment and verify both invoice payment point to the same targetInvoiceId
        accountApi.payAllInvoices(accountJson.getAccountId(), false, null, NULL_PLUGIN_PROPERTIES, requestOptions);
        invoicePayments = accountApi.getInvoicePayments(accountJson.getAccountId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        assertEquals(invoicePayments.size(), 2);
        for (final InvoicePayment cur : invoicePayments) {
            assertEquals(cur.getTargetInvoiceId(), invoice.getInvoiceId());
        }
    }

    @Test(groups = "slow")
    public void testManualInvoicePayment() throws Exception {
        final Account accountJson = createAccountWithDefaultPaymentMethod();
        assertNotNull(accountJson);

        // Disable automatic payments
        callbackServlet.pushExpectedEvent(ExtBusEventType.TAG_CREATION);
        accountApi.createAccountTags(accountJson.getAccountId(), ImmutableList.<UUID>of(ControlTagType.AUTO_PAY_OFF.getId()), requestOptions);
        callbackServlet.assertListenerStatus();

        // Add a bundle, subscription and move the clock to get the first invoice
        final Subscription subscriptionJson = createSubscription(accountJson.getAccountId(), UUID.randomUUID().toString(), "Shotgun",
                                                                 ProductCategory.BASE, BillingPeriod.MONTHLY, true);
        assertNotNull(subscriptionJson);

        callbackServlet.pushExpectedEvents(ExtBusEventType.SUBSCRIPTION_PHASE,
                                           ExtBusEventType.INVOICE_CREATION);
        clock.addDays(32);
        callbackServlet.assertListenerStatus();

        final List<Invoice> invoices = accountApi.getInvoicesForAccount(accountJson.getAccountId(), requestOptions);
        assertEquals(invoices.size(), 2);

        final InvoicePayment invoicePayment1 = new InvoicePayment();
        invoicePayment1.setPurchasedAmount(invoices.get(1).getBalance().add(BigDecimal.TEN));
        invoicePayment1.setAccountId(accountJson.getAccountId());
        invoicePayment1.setTargetInvoiceId(invoices.get(1).getInvoiceId());

        // Pay too too much => 400
        try {
            invoiceApi.createInstantPayment(invoicePayment1.getTargetInvoiceId(), invoicePayment1, NULL_PLUGIN_PROPERTIES, requestOptions);
            Assert.fail("InvoicePayment call should fail with 400");
        } catch (final KillBillClientException e) {
            assertTrue(true);
        }

        final InvoicePayment invoicePayment2 = new InvoicePayment();
        invoicePayment2.setPurchasedAmount(invoices.get(1).getBalance());
        invoicePayment2.setAccountId(accountJson.getAccountId());
        invoicePayment2.setTargetInvoiceId(invoices.get(1).getInvoiceId());

        // Just right, Yah! => 201
        final InvoicePayment result2 = invoiceApi.createInstantPayment(invoicePayment2.getTargetInvoiceId(), invoicePayment2, NULL_PLUGIN_PROPERTIES, requestOptions);
        assertEquals(result2.getTransactions().size(), 1);
        assertTrue(result2.getTransactions().get(0).getAmount().compareTo(invoices.get(1).getBalance()) == 0);

        // Already paid -> 204
        final InvoicePayment result3 = invoiceApi.createInstantPayment(invoicePayment2.getTargetInvoiceId(), invoicePayment2, NULL_PLUGIN_PROPERTIES, requestOptions);
        assertNull(result3);
    }

    private BigDecimal getFractionOfAmount(final BigDecimal amount) {
        return amount.divide(BigDecimal.TEN).setScale(2, BigDecimal.ROUND_HALF_UP);
    }

    private InvoicePayment setupScenarioWithPayment(final boolean invoicePaymentSuccess) throws Exception {
        final Account accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice("Shotgun", invoicePaymentSuccess, true);

        final List<InvoicePayment> paymentsForAccount = accountApi.getInvoicePayments(accountJson.getAccountId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        Assert.assertEquals(paymentsForAccount.size(), 1);

        final InvoicePayment paymentJson = paymentsForAccount.get(0);

        // Check the PaymentMethod from paymentMethodId returned in the Payment object
        final UUID paymentMethodId = paymentJson.getPaymentMethodId();
        final PaymentMethod paymentMethodJson = paymentMethodApi.getPaymentMethod(paymentMethodId, NULL_PLUGIN_PROPERTIES, requestOptions);
        Assert.assertEquals(paymentMethodJson.getPaymentMethodId(), paymentMethodId);
        Assert.assertEquals(paymentMethodJson.getAccountId(), accountJson.getAccountId());

        // Verify the refunds
        final List<PaymentTransaction> objRefundFromJson = getInvoicePaymentTransactions(paymentsForAccount, TransactionType.REFUND);
        Assert.assertEquals(objRefundFromJson.size(), 0);
        return paymentJson;
    }

    private void verifyRefund(final InvoicePayment paymentJson, final Payment paymentAfterRefund, final BigDecimal refundAmount) throws KillBillClientException {

        final List<PaymentTransaction> transactions = getPaymentTransactions(ImmutableList.of(paymentAfterRefund), TransactionType.REFUND);
        Assert.assertEquals(transactions.size(), 1);

        final PaymentTransaction refund = transactions.get(0);
        Assert.assertEquals(refund.getPaymentId(), paymentJson.getPaymentId());
        Assert.assertEquals(refund.getAmount().setScale(2, RoundingMode.HALF_UP), refundAmount.setScale(2, RoundingMode.HALF_UP));
        Assert.assertEquals(refund.getCurrency(), DEFAULT_CURRENCY);
        Assert.assertEquals(refund.getStatus(), TransactionStatus.SUCCESS);
        Assert.assertEquals(refund.getEffectiveDate().getYear(), clock.getUTCNow().getYear());
        Assert.assertEquals(refund.getEffectiveDate().getMonthOfYear(), clock.getUTCNow().getMonthOfYear());
        Assert.assertEquals(refund.getEffectiveDate().getDayOfMonth(), clock.getUTCNow().getDayOfMonth());

        // Verify the refund via the payment API
        final Payment retrievedPaymentJson = paymentApi.getPayment(paymentJson.getPaymentId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        Assert.assertEquals(retrievedPaymentJson.getPaymentId(), paymentJson.getPaymentId());
        Assert.assertEquals(retrievedPaymentJson.getPurchasedAmount().setScale(2, RoundingMode.HALF_UP), paymentJson.getPurchasedAmount().setScale(2, RoundingMode.HALF_UP));
        Assert.assertEquals(retrievedPaymentJson.getAccountId(), paymentJson.getAccountId());
        Assert.assertEquals(retrievedPaymentJson.getCurrency(), paymentJson.getCurrency());
        Assert.assertEquals(retrievedPaymentJson.getPaymentMethodId(), paymentJson.getPaymentMethodId());
    }

    private void verifyInvoice(final InvoicePayment paymentJson, final BigDecimal expectedInvoiceBalance) throws KillBillClientException {
        final Invoice invoiceJson = invoiceApi.getInvoice(paymentJson.getTargetInvoiceId(), requestOptions);
        Assert.assertEquals(invoiceJson.getBalance().setScale(2, BigDecimal.ROUND_HALF_UP),
                            expectedInvoiceBalance.setScale(2, BigDecimal.ROUND_HALF_UP));
    }

    private void verifyInvoicePaymentAgainstPayment(final InvoicePayment ip, final Payment p) {
        Assert.assertEquals(ip.getAccountId(), p.getAccountId());
        Assert.assertEquals(ip.getPaymentId(), p.getPaymentId());
        Assert.assertEquals(ip.getPaymentNumber(), p.getPaymentNumber());
        Assert.assertEquals(ip.getAccountId(), p.getAccountId());
        Assert.assertEquals(ip.getAuthAmount(), p.getAuthAmount());
        Assert.assertEquals(ip.getCapturedAmount(), p.getCapturedAmount());
        Assert.assertEquals(ip.getPurchasedAmount(), p.getPurchasedAmount());
        Assert.assertEquals(ip.getRefundedAmount(), p.getRefundedAmount());
        Assert.assertEquals(ip.getCreditedAmount(), p.getCreditedAmount());
        Assert.assertEquals(ip.getCurrency(), p.getCurrency());
        Assert.assertEquals(ip.getPaymentMethodId(), p.getPaymentMethodId());
    }

}
