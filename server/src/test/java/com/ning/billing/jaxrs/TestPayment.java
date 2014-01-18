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

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.client.KillBillClientException;
import com.ning.billing.client.model.Account;
import com.ning.billing.client.model.Invoice;
import com.ning.billing.client.model.InvoiceItem;
import com.ning.billing.client.model.Payment;
import com.ning.billing.client.model.PaymentMethod;
import com.ning.billing.client.model.Refund;
import com.ning.billing.payment.api.RefundStatus;

import com.google.common.collect.ImmutableList;

public class TestPayment extends TestJaxrsBase {

    @Test(groups = "slow")
    public void testRetrievePayment() throws Exception {
        final Payment paymentJson = setupScenarioWithPayment();

        final Payment retrievedPaymentJson = killBillClient.getPayment(paymentJson.getPaymentId(), false);
        Assert.assertEquals(retrievedPaymentJson, paymentJson);
    }

    @Test(groups = "slow", description = "Can create a full refund with no adjustment")
    public void testFullRefundWithNoAdjustment() throws Exception {
        final Payment paymentJson = setupScenarioWithPayment();

        // Issue a refund for the full amount
        final BigDecimal refundAmount = paymentJson.getAmount();
        final BigDecimal expectedInvoiceBalance = refundAmount;

        // Post and verify the refund
        final Refund refund = new Refund();
        refund.setPaymentId(paymentJson.getPaymentId());
        refund.setAmount(refundAmount);
        final Refund refundJsonCheck = killBillClient.createRefund(refund, createdBy, reason, comment);
        verifyRefund(paymentJson, refundJsonCheck, refundAmount);

        // Verify the invoice balance
        verifyInvoice(paymentJson, expectedInvoiceBalance);
    }

    @Test(groups = "slow", description = "Can create a partial refund with no adjustment")
    public void testPartialRefundWithNoAdjustment() throws Exception {
        final Payment paymentJson = setupScenarioWithPayment();

        // Issue a refund for a fraction of the amount
        final BigDecimal refundAmount = getFractionOfAmount(paymentJson.getAmount());
        final BigDecimal expectedInvoiceBalance = refundAmount;

        // Post and verify the refund
        final Refund refund = new Refund();
        refund.setPaymentId(paymentJson.getPaymentId());
        refund.setAmount(refundAmount);
        final Refund refundJsonCheck = killBillClient.createRefund(refund, createdBy, reason, comment);
        verifyRefund(paymentJson, refundJsonCheck, refundAmount);

        // Verify the invoice balance
        verifyInvoice(paymentJson, expectedInvoiceBalance);
    }

    @Test(groups = "slow", description = "Can create a full refund with invoice adjustment")
    public void testFullRefundWithInvoiceAdjustment() throws Exception {
        final Payment paymentJson = setupScenarioWithPayment();

        // Issue a refund for the full amount
        final BigDecimal refundAmount = paymentJson.getAmount();
        final BigDecimal expectedInvoiceBalance = BigDecimal.ZERO;

        // Post and verify the refund
        final Refund refund = new Refund();
        refund.setPaymentId(paymentJson.getPaymentId());
        refund.setAmount(refundAmount);
        refund.setAdjusted(true);
        final Refund refundJsonCheck = killBillClient.createRefund(refund, createdBy, reason, comment);
        verifyRefund(paymentJson, refundJsonCheck, refundAmount);

        // Verify the invoice balance
        verifyInvoice(paymentJson, expectedInvoiceBalance);
    }

    @Test(groups = "slow", description = "Can create a partial refund with invoice adjustment")
    public void testPartialRefundWithInvoiceAdjustment() throws Exception {
        final Payment paymentJson = setupScenarioWithPayment();

        // Issue a refund for a fraction of the amount
        final BigDecimal refundAmount = getFractionOfAmount(paymentJson.getAmount());
        final BigDecimal expectedInvoiceBalance = BigDecimal.ZERO;

        // Post and verify the refund
        final Refund refund = new Refund();
        refund.setPaymentId(paymentJson.getPaymentId());
        refund.setAmount(refundAmount);
        refund.setAdjusted(true);
        final Refund refundJsonCheck = killBillClient.createRefund(refund, createdBy, reason, comment);
        verifyRefund(paymentJson, refundJsonCheck, refundAmount);

        // Verify the invoice balance
        verifyInvoice(paymentJson, expectedInvoiceBalance);
    }

    @Test(groups = "slow", description = "Can create a full refund with invoice item adjustment")
    public void testRefundWithFullInvoiceItemAdjustment() throws Exception {
        final Payment paymentJson = setupScenarioWithPayment();

        // Get the individual items for the invoice
        final Invoice invoice = killBillClient.getInvoice(paymentJson.getInvoiceId(), true);
        final InvoiceItem itemToAdjust = invoice.getItems().get(0);

        // Issue a refund for the full amount
        final BigDecimal refundAmount = itemToAdjust.getAmount();
        final BigDecimal expectedInvoiceBalance = BigDecimal.ZERO;

        // Post and verify the refund
        final Refund refund = new Refund();
        refund.setPaymentId(paymentJson.getPaymentId());
        refund.setAmount(refundAmount);
        refund.setAdjusted(true);
        final InvoiceItem adjustment = new InvoiceItem();
        adjustment.setInvoiceItemId(itemToAdjust.getInvoiceItemId());
        /* null amount means full adjustment for that item */
        refund.setAdjustments(ImmutableList.<InvoiceItem>of(adjustment));
        final Refund refundJsonCheck = killBillClient.createRefund(refund, createdBy, reason, comment);
        verifyRefund(paymentJson, refundJsonCheck, refundAmount);

        // Verify the invoice balance
        verifyInvoice(paymentJson, expectedInvoiceBalance);
    }

    @Test(groups = "slow", description = "Can create a partial refund with invoice item adjustment")
    public void testPartialRefundWithInvoiceItemAdjustment() throws Exception {
        final Payment paymentJson = setupScenarioWithPayment();

        // Get the individual items for the invoice
        final Invoice invoice = killBillClient.getInvoice(paymentJson.getInvoiceId(), true);
        final InvoiceItem itemToAdjust = invoice.getItems().get(0);

        // Issue a refund for a fraction of the amount
        final BigDecimal refundAmount = getFractionOfAmount(itemToAdjust.getAmount());
        final BigDecimal expectedInvoiceBalance = BigDecimal.ZERO;

        // Post and verify the refund
        final Refund refund = new Refund();
        refund.setPaymentId(paymentJson.getPaymentId());
        refund.setAdjusted(true);
        final InvoiceItem adjustment = new InvoiceItem();
        adjustment.setInvoiceItemId(itemToAdjust.getInvoiceItemId());
        adjustment.setAmount(refundAmount);
        refund.setAdjustments(ImmutableList.<InvoiceItem>of(adjustment));
        final Refund refundJsonCheck = killBillClient.createRefund(refund, createdBy, reason, comment);
        verifyRefund(paymentJson, refundJsonCheck, refundAmount);

        // Verify the invoice balance
        verifyInvoice(paymentJson, expectedInvoiceBalance);
    }

    private BigDecimal getFractionOfAmount(final BigDecimal amount) {
        return amount.divide(BigDecimal.TEN).setScale(2, BigDecimal.ROUND_HALF_UP);
    }

    private Payment setupScenarioWithPayment() throws Exception {
        final Account accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();

        final List<Payment> firstPaymentForAccount = killBillClient.getPaymentsForAccount(accountJson.getAccountId());
        Assert.assertEquals(firstPaymentForAccount.size(), 1);

        final Payment paymentJson = firstPaymentForAccount.get(0);

        // Check the PaymentMethod from paymentMethodId returned in the Payment object
        final UUID paymentMethodId = paymentJson.getPaymentMethodId();
        final PaymentMethod paymentMethodJson = killBillClient.getPaymentMethod(paymentMethodId, true);
        Assert.assertEquals(paymentMethodJson.getPaymentMethodId(), paymentMethodId);
        Assert.assertEquals(paymentMethodJson.getAccountId(), accountJson.getAccountId());

        // Verify the refunds
        final List<Refund> objRefundFromJson = killBillClient.getRefundsForPayment(paymentJson.getPaymentId());
        Assert.assertEquals(objRefundFromJson.size(), 0);
        return paymentJson;
    }

    private void verifyRefund(final Payment paymentJson, final Refund refundJsonCheck, final BigDecimal refundAmount) throws KillBillClientException {
        Assert.assertEquals(refundJsonCheck.getPaymentId(), paymentJson.getPaymentId());
        Assert.assertEquals(refundJsonCheck.getAmount().setScale(2, RoundingMode.HALF_UP), refundAmount.setScale(2, RoundingMode.HALF_UP));
        Assert.assertEquals(refundJsonCheck.getCurrency(), DEFAULT_CURRENCY);
        Assert.assertEquals(refundJsonCheck.getStatus(), RefundStatus.COMPLETED.toString());
        Assert.assertEquals(refundJsonCheck.getEffectiveDate().getYear(), clock.getUTCNow().getYear());
        Assert.assertEquals(refundJsonCheck.getEffectiveDate().getMonthOfYear(), clock.getUTCNow().getMonthOfYear());
        Assert.assertEquals(refundJsonCheck.getEffectiveDate().getDayOfMonth(), clock.getUTCNow().getDayOfMonth());
        Assert.assertEquals(refundJsonCheck.getRequestedDate().getYear(), clock.getUTCNow().getYear());
        Assert.assertEquals(refundJsonCheck.getRequestedDate().getMonthOfYear(), clock.getUTCNow().getMonthOfYear());
        Assert.assertEquals(refundJsonCheck.getRequestedDate().getDayOfMonth(), clock.getUTCNow().getDayOfMonth());

        // Verify the refunds
        final List<Refund> retrievedRefunds = killBillClient.getRefundsForPayment(paymentJson.getPaymentId());
        Assert.assertEquals(retrievedRefunds.size(), 1);

        // Verify the refund via the payment API
        final Payment retrievedPaymentJson = killBillClient.getPayment(paymentJson.getPaymentId(), true);
        Assert.assertEquals(retrievedPaymentJson.getPaymentId(), paymentJson.getPaymentId());
        Assert.assertEquals(retrievedPaymentJson.getPaidAmount().setScale(2, RoundingMode.HALF_UP), paymentJson.getPaidAmount().add(refundAmount.negate()).setScale(2, RoundingMode.HALF_UP));
        Assert.assertEquals(retrievedPaymentJson.getAmount().setScale(2, RoundingMode.HALF_UP), paymentJson.getAmount().setScale(2, RoundingMode.HALF_UP));
        Assert.assertEquals(retrievedPaymentJson.getAccountId(), paymentJson.getAccountId());
        Assert.assertEquals(retrievedPaymentJson.getInvoiceId(), paymentJson.getInvoiceId());
        Assert.assertEquals(retrievedPaymentJson.getRequestedDate(), paymentJson.getRequestedDate());
        Assert.assertEquals(retrievedPaymentJson.getEffectiveDate(), paymentJson.getEffectiveDate());
        Assert.assertEquals(retrievedPaymentJson.getRetryCount(), paymentJson.getRetryCount());
        Assert.assertEquals(retrievedPaymentJson.getCurrency(), paymentJson.getCurrency());
        Assert.assertEquals(retrievedPaymentJson.getStatus(), paymentJson.getStatus());
        Assert.assertEquals(retrievedPaymentJson.getGatewayErrorCode(), paymentJson.getGatewayErrorCode());
        Assert.assertEquals(retrievedPaymentJson.getGatewayErrorMsg(), paymentJson.getGatewayErrorMsg());
        Assert.assertEquals(retrievedPaymentJson.getPaymentMethodId(), paymentJson.getPaymentMethodId());
        Assert.assertEquals(retrievedPaymentJson.getChargebacks().size(), 0);
        Assert.assertEquals(retrievedPaymentJson.getRefunds().size(), 1);
        Assert.assertEquals(retrievedPaymentJson.getRefunds().get(0), refundJsonCheck);
    }

    private void verifyInvoice(final Payment paymentJson, final BigDecimal expectedInvoiceBalance) throws KillBillClientException {
        final Invoice invoiceJson = killBillClient.getInvoice(paymentJson.getInvoiceId());
        Assert.assertEquals(invoiceJson.getBalance().setScale(2, BigDecimal.ROUND_HALF_UP),
                            expectedInvoiceBalance.setScale(2, BigDecimal.ROUND_HALF_UP));
    }
}
