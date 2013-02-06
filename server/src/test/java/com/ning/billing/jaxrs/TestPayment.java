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

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.jaxrs.json.AccountJson;
import com.ning.billing.jaxrs.json.InvoiceItemJsonSimple;
import com.ning.billing.jaxrs.json.InvoiceJsonSimple;
import com.ning.billing.jaxrs.json.InvoiceJsonWithItems;
import com.ning.billing.jaxrs.json.PaymentJsonSimple;
import com.ning.billing.jaxrs.json.PaymentJsonWithBundleKeys;
import com.ning.billing.jaxrs.json.PaymentMethodJson;
import com.ning.billing.jaxrs.json.RefundJson;

public class TestPayment extends TestJaxrsBase {

    @Test(groups = "slow")
    public void testRetrievePayment() throws Exception {
        final PaymentJsonSimple paymentJsonSimple = setupScenarioWithPayment();

        final PaymentJsonSimple retrievedPaymentJsonSimple = getPayment(paymentJsonSimple.getPaymentId());
        Assert.assertEquals(retrievedPaymentJsonSimple, paymentJsonSimple);
    }

    @Test(groups = "slow")
    public void testFullRefundWithNoAdjustment() throws Exception {
        final PaymentJsonSimple paymentJsonSimple = setupScenarioWithPayment();

        // Issue a refund for the full amount
        final BigDecimal refundAmount = paymentJsonSimple.getAmount();
        final BigDecimal expectedInvoiceBalance = refundAmount;

        // Post and verify the refund
        final RefundJson refundJsonCheck = createRefund(paymentJsonSimple.getPaymentId(), refundAmount);
        verifyRefund(paymentJsonSimple, refundJsonCheck, refundAmount);

        // Verify the invoice balance
        verifyInvoice(paymentJsonSimple, expectedInvoiceBalance);
    }

    @Test(groups = "slow")
    public void testPartialRefundWithNoAdjustment() throws Exception {
        final PaymentJsonSimple paymentJsonSimple = setupScenarioWithPayment();

        // Issue a refund for a fraction of the amount
        final BigDecimal refundAmount = getFractionOfAmount(paymentJsonSimple.getAmount());
        final BigDecimal expectedInvoiceBalance = refundAmount;

        // Post and verify the refund
        final RefundJson refundJsonCheck = createRefund(paymentJsonSimple.getPaymentId(), refundAmount);
        verifyRefund(paymentJsonSimple, refundJsonCheck, refundAmount);

        // Verify the invoice balance
        verifyInvoice(paymentJsonSimple, expectedInvoiceBalance);
    }

    @Test(groups = "slow")
    public void testFullRefundWithInvoiceAdjustment() throws Exception {
        final PaymentJsonSimple paymentJsonSimple = setupScenarioWithPayment();

        // Issue a refund for the full amount
        final BigDecimal refundAmount = paymentJsonSimple.getAmount();
        final BigDecimal expectedInvoiceBalance = BigDecimal.ZERO;

        // Post and verify the refund
        final RefundJson refundJsonCheck = createRefundWithInvoiceAdjustment(paymentJsonSimple.getPaymentId(), refundAmount);
        verifyRefund(paymentJsonSimple, refundJsonCheck, refundAmount);

        // Verify the invoice balance
        verifyInvoice(paymentJsonSimple, expectedInvoiceBalance);
    }

    @Test(groups = "slow")
    public void testPartialRefundWithInvoiceAdjustment() throws Exception {
        final PaymentJsonSimple paymentJsonSimple = setupScenarioWithPayment();

        // Issue a refund for a fraction of the amount
        final BigDecimal refundAmount = getFractionOfAmount(paymentJsonSimple.getAmount());
        final BigDecimal expectedInvoiceBalance = BigDecimal.ZERO;

        // Post and verify the refund
        final RefundJson refundJsonCheck = createRefundWithInvoiceAdjustment(paymentJsonSimple.getPaymentId(), refundAmount);
        verifyRefund(paymentJsonSimple, refundJsonCheck, refundAmount);

        // Verify the invoice balance
        verifyInvoice(paymentJsonSimple, expectedInvoiceBalance);
    }

    @Test(groups = "slow")
    public void testRefundWithFullInvoiceItemAdjustment() throws Exception {
        final PaymentJsonSimple paymentJsonSimple = setupScenarioWithPayment();

        // Get the individual items for the invoice
        final InvoiceJsonWithItems invoice = getInvoiceWithItems(paymentJsonSimple.getInvoiceId());
        final InvoiceItemJsonSimple itemToAdjust = invoice.getItems().get(0);

        // Issue a refund for the full amount
        final BigDecimal refundAmount = itemToAdjust.getAmount();
        final BigDecimal expectedInvoiceBalance = BigDecimal.ZERO;

        // Post and verify the refund
        final RefundJson refundJsonCheck = createRefundWithInvoiceItemAdjustment(paymentJsonSimple.getPaymentId(),
                                                                                 itemToAdjust.getInvoiceItemId(),
                                                                                 null /* null means full adjustment for that item */);
        verifyRefund(paymentJsonSimple, refundJsonCheck, refundAmount);

        // Verify the invoice balance
        verifyInvoice(paymentJsonSimple, expectedInvoiceBalance);
    }

    @Test(groups = "slow")
    public void testPartialRefundWithInvoiceItemAdjustment() throws Exception {
        final PaymentJsonSimple paymentJsonSimple = setupScenarioWithPayment();

        // Get the individual items for the invoice
        final InvoiceJsonWithItems invoice = getInvoiceWithItems(paymentJsonSimple.getInvoiceId());
        final InvoiceItemJsonSimple itemToAdjust = invoice.getItems().get(0);

        // Issue a refund for a fraction of the amount
        final BigDecimal refundAmount = getFractionOfAmount(itemToAdjust.getAmount());
        final BigDecimal expectedInvoiceBalance = BigDecimal.ZERO;

        // Post and verify the refund
        final RefundJson refundJsonCheck = createRefundWithInvoiceItemAdjustment(paymentJsonSimple.getPaymentId(),
                                                                                 itemToAdjust.getInvoiceItemId(),
                                                                                 refundAmount);
        verifyRefund(paymentJsonSimple, refundJsonCheck, refundAmount);

        // Verify the invoice balance
        verifyInvoice(paymentJsonSimple, expectedInvoiceBalance);
    }

    private BigDecimal getFractionOfAmount(final BigDecimal amount) {
        return amount.divide(BigDecimal.TEN).setScale(2, BigDecimal.ROUND_HALF_UP);
    }

    private PaymentJsonSimple setupScenarioWithPayment() throws Exception {
        final AccountJson accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();

        final List<PaymentJsonSimple> firstPaymentForAccount = getPaymentsForAccount(accountJson.getAccountId());
        Assert.assertEquals(firstPaymentForAccount.size(), 1);

        final PaymentJsonSimple paymentJsonSimple = firstPaymentForAccount.get(0);

        // Check the PaymentMethod from paymentMethodId returned in the Payment object
        final String paymentMethodId = paymentJsonSimple.getPaymentMethodId();
        final PaymentMethodJson paymentMethodJson = getPaymentMethodWithPluginInfo(paymentMethodId);
        Assert.assertEquals(paymentMethodJson.getPaymentMethodId(), paymentMethodId);
        Assert.assertEquals(paymentMethodJson.getAccountId(), accountJson.getAccountId());

        // Verify the refunds
        final List<RefundJson> objRefundFromJson = getRefundsForPayment(paymentJsonSimple.getPaymentId());
        Assert.assertEquals(objRefundFromJson.size(), 0);
        return paymentJsonSimple;
    }

    private void verifyRefund(final PaymentJsonSimple paymentJsonSimple, final RefundJson refundJsonCheck, final BigDecimal refundAmount) throws IOException {
        Assert.assertEquals(refundJsonCheck.getPaymentId(), paymentJsonSimple.getPaymentId());
        Assert.assertEquals(refundJsonCheck.getAmount().setScale(2, RoundingMode.HALF_UP), refundAmount.setScale(2, RoundingMode.HALF_UP));
        Assert.assertEquals(refundJsonCheck.getCurrency(), DEFAULT_CURRENCY);
        Assert.assertEquals(refundJsonCheck.getEffectiveDate().getYear(), clock.getUTCNow().getYear());
        Assert.assertEquals(refundJsonCheck.getEffectiveDate().getMonthOfYear(), clock.getUTCNow().getMonthOfYear());
        Assert.assertEquals(refundJsonCheck.getEffectiveDate().getDayOfMonth(), clock.getUTCNow().getDayOfMonth());
        Assert.assertEquals(refundJsonCheck.getRequestedDate().getYear(), clock.getUTCNow().getYear());
        Assert.assertEquals(refundJsonCheck.getRequestedDate().getMonthOfYear(), clock.getUTCNow().getMonthOfYear());
        Assert.assertEquals(refundJsonCheck.getRequestedDate().getDayOfMonth(), clock.getUTCNow().getDayOfMonth());

        // Verify the refunds
        final List<RefundJson> retrievedRefunds = getRefundsForPayment(paymentJsonSimple.getPaymentId());
        Assert.assertEquals(retrievedRefunds.size(), 1);

        // Verify the refund via the payment API
        final PaymentJsonWithBundleKeys retrievedPaymentJsonWithBundleKeys = getPaymentWithRefundsAndChargebacks(paymentJsonSimple.getPaymentId());
        Assert.assertEquals(retrievedPaymentJsonWithBundleKeys.getPaymentId(), paymentJsonSimple.getPaymentId());
        Assert.assertEquals(retrievedPaymentJsonWithBundleKeys.getPaidAmount().setScale(2, RoundingMode.HALF_UP), paymentJsonSimple.getPaidAmount().add(refundAmount.negate()).setScale(2, RoundingMode.HALF_UP));
        Assert.assertEquals(retrievedPaymentJsonWithBundleKeys.getAmount().setScale(2, RoundingMode.HALF_UP), paymentJsonSimple.getAmount().setScale(2, RoundingMode.HALF_UP));
        Assert.assertEquals(retrievedPaymentJsonWithBundleKeys.getAccountId(), paymentJsonSimple.getAccountId());
        Assert.assertEquals(retrievedPaymentJsonWithBundleKeys.getInvoiceId(), paymentJsonSimple.getInvoiceId());
        Assert.assertEquals(retrievedPaymentJsonWithBundleKeys.getRequestedDate(), paymentJsonSimple.getRequestedDate());
        Assert.assertEquals(retrievedPaymentJsonWithBundleKeys.getEffectiveDate(), paymentJsonSimple.getEffectiveDate());
        Assert.assertEquals(retrievedPaymentJsonWithBundleKeys.getRetryCount(), paymentJsonSimple.getRetryCount());
        Assert.assertEquals(retrievedPaymentJsonWithBundleKeys.getCurrency(), paymentJsonSimple.getCurrency());
        Assert.assertEquals(retrievedPaymentJsonWithBundleKeys.getStatus(), paymentJsonSimple.getStatus());
        Assert.assertEquals(retrievedPaymentJsonWithBundleKeys.getGatewayErrorCode(), paymentJsonSimple.getGatewayErrorCode());
        Assert.assertEquals(retrievedPaymentJsonWithBundleKeys.getGatewayErrorMsg(), paymentJsonSimple.getGatewayErrorMsg());
        Assert.assertEquals(retrievedPaymentJsonWithBundleKeys.getPaymentMethodId(), paymentJsonSimple.getPaymentMethodId());
        Assert.assertEquals(retrievedPaymentJsonWithBundleKeys.getExtFirstPaymentIdRef(), paymentJsonSimple.getExtFirstPaymentIdRef());
        Assert.assertEquals(retrievedPaymentJsonWithBundleKeys.getExtSecondPaymentIdRef(), paymentJsonSimple.getExtSecondPaymentIdRef());
        Assert.assertEquals(retrievedPaymentJsonWithBundleKeys.getChargebacks().size(), 0);
        Assert.assertEquals(retrievedPaymentJsonWithBundleKeys.getRefunds().size(), 1);
        Assert.assertEquals(retrievedPaymentJsonWithBundleKeys.getRefunds().get(0), refundJsonCheck);
    }

    private void verifyInvoice(final PaymentJsonSimple paymentJsonSimple, final BigDecimal expectedInvoiceBalance) throws IOException {
        final InvoiceJsonSimple invoiceJsonSimple = getInvoice(paymentJsonSimple.getInvoiceId());
        Assert.assertEquals(invoiceJsonSimple.getBalance().setScale(2, BigDecimal.ROUND_HALF_UP),
                            expectedInvoiceBalance.setScale(2, BigDecimal.ROUND_HALF_UP));
    }
}
