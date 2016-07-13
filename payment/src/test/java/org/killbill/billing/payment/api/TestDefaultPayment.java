/*
 * Copyright 2016 Groupon, Inc
 * Copyright 2016 The Billing Project, LLC
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

package org.killbill.billing.payment.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.PaymentTestSuiteNoDB;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class TestDefaultPayment extends PaymentTestSuiteNoDB {

    @Test(groups = "fast")
    public void testAmountsCaptureVoided() throws Exception {
        final UUID paymentId = UUID.randomUUID();
        final List<PaymentTransaction> transactions = ImmutableList.<PaymentTransaction>of(buildPaymentTransaction(paymentId, UUID.randomUUID().toString(), TransactionType.AUTHORIZE, TransactionStatus.SUCCESS, BigDecimal.TEN),
                                                                                           buildPaymentTransaction(paymentId, UUID.randomUUID().toString(), TransactionType.CAPTURE, TransactionStatus.SUCCESS, BigDecimal.TEN),
                                                                                           buildPaymentTransaction(paymentId, UUID.randomUUID().toString(), TransactionType.VOID, TransactionStatus.SUCCESS, null));
        final Payment payment = buildPayment(paymentId, transactions);
        Assert.assertEquals(payment.getAuthAmount().compareTo(BigDecimal.TEN), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
    }

    @Test(groups = "fast")
    public void testAmountsCaptureVoidedAuthReversed() throws Exception {
        final UUID paymentId = UUID.randomUUID();
        final List<PaymentTransaction> transactions = ImmutableList.<PaymentTransaction>of(buildPaymentTransaction(paymentId, UUID.randomUUID().toString(), TransactionType.AUTHORIZE, TransactionStatus.SUCCESS, BigDecimal.TEN),
                                                                                           buildPaymentTransaction(paymentId, UUID.randomUUID().toString(), TransactionType.CAPTURE, TransactionStatus.SUCCESS, BigDecimal.TEN),
                                                                                           buildPaymentTransaction(paymentId, UUID.randomUUID().toString(), TransactionType.VOID, TransactionStatus.SUCCESS, null),
                                                                                           buildPaymentTransaction(paymentId, UUID.randomUUID().toString(), TransactionType.VOID, TransactionStatus.SUCCESS, null));
        final Payment payment = buildPayment(paymentId, transactions);
        Assert.assertEquals(payment.getAuthAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
    }

    @Test(groups = "fast")
    public void testAmountsCaptureChargeback() throws Exception {
        final UUID paymentId = UUID.randomUUID();
        final String chargebackExternalKey = UUID.randomUUID().toString();
        final List<PaymentTransaction> transactions = ImmutableList.<PaymentTransaction>of(buildPaymentTransaction(paymentId, UUID.randomUUID().toString(), TransactionType.AUTHORIZE, TransactionStatus.SUCCESS, BigDecimal.TEN),
                                                                                           buildPaymentTransaction(paymentId, UUID.randomUUID().toString(), TransactionType.CAPTURE, TransactionStatus.SUCCESS, BigDecimal.TEN),
                                                                                           buildPaymentTransaction(paymentId, chargebackExternalKey, TransactionType.CHARGEBACK, TransactionStatus.SUCCESS, BigDecimal.TEN));
        final Payment payment = buildPayment(paymentId, transactions);
        Assert.assertEquals(payment.getAuthAmount().compareTo(BigDecimal.TEN), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
    }

    @Test(groups = "fast")
    public void testAmountsCaptureChargebackReversed() throws Exception {
        final UUID paymentId = UUID.randomUUID();
        final String chargebackExternalKey = UUID.randomUUID().toString();
        final List<PaymentTransaction> transactions = ImmutableList.<PaymentTransaction>of(buildPaymentTransaction(paymentId, UUID.randomUUID().toString(), TransactionType.AUTHORIZE, TransactionStatus.SUCCESS, BigDecimal.TEN),
                                                                                           buildPaymentTransaction(paymentId, UUID.randomUUID().toString(), TransactionType.CAPTURE, TransactionStatus.SUCCESS, BigDecimal.TEN),
                                                                                           buildPaymentTransaction(paymentId, chargebackExternalKey, TransactionType.CHARGEBACK, TransactionStatus.SUCCESS, BigDecimal.TEN),
                                                                                           buildPaymentTransaction(paymentId, chargebackExternalKey, TransactionType.CHARGEBACK, TransactionStatus.PAYMENT_FAILURE, BigDecimal.TEN));
        final Payment payment = buildPayment(paymentId, transactions);
        Assert.assertEquals(payment.getAuthAmount().compareTo(BigDecimal.TEN), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.TEN), 0);
        Assert.assertEquals(payment.getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
    }

    @Test(groups = "fast")
    public void testAmountsCaptureChargebackReversedMultipleCurrencies() throws Exception {
        final UUID paymentId = UUID.randomUUID();
        final String chargebackExternalKey = UUID.randomUUID().toString();
        final List<PaymentTransaction> transactions = ImmutableList.<PaymentTransaction>of(buildPaymentTransaction(paymentId, UUID.randomUUID().toString(), TransactionType.AUTHORIZE, TransactionStatus.SUCCESS, BigDecimal.TEN, Currency.EUR),
                                                                                           buildPaymentTransaction(paymentId, UUID.randomUUID().toString(), TransactionType.CAPTURE, TransactionStatus.SUCCESS, BigDecimal.TEN, Currency.USD),
                                                                                           buildPaymentTransaction(paymentId, chargebackExternalKey, TransactionType.CHARGEBACK, TransactionStatus.SUCCESS, BigDecimal.ONE, Currency.EUR),
                                                                                           buildPaymentTransaction(paymentId, chargebackExternalKey, TransactionType.CHARGEBACK, TransactionStatus.PAYMENT_FAILURE, BigDecimal.ONE, Currency.EUR));
        final Payment payment = buildPayment(paymentId, transactions);
        Assert.assertEquals(payment.getCurrency(), Currency.EUR);
        Assert.assertEquals(payment.getAuthAmount().compareTo(BigDecimal.TEN), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
    }

    @Test(groups = "fast")
    public void testAmountsCaptureChargebackReversedAndRefund() throws Exception {
        final UUID paymentId = UUID.randomUUID();
        final String chargebackExternalKey = UUID.randomUUID().toString();
        final List<PaymentTransaction> transactions = ImmutableList.<PaymentTransaction>of(buildPaymentTransaction(paymentId, UUID.randomUUID().toString(), TransactionType.AUTHORIZE, TransactionStatus.SUCCESS, BigDecimal.TEN),
                                                                                           buildPaymentTransaction(paymentId, UUID.randomUUID().toString(), TransactionType.CAPTURE, TransactionStatus.SUCCESS, BigDecimal.TEN),
                                                                                           buildPaymentTransaction(paymentId, UUID.randomUUID().toString(), TransactionType.REFUND, TransactionStatus.SUCCESS, BigDecimal.ONE),
                                                                                           buildPaymentTransaction(paymentId, chargebackExternalKey, TransactionType.CHARGEBACK, TransactionStatus.SUCCESS, BigDecimal.TEN),
                                                                                           buildPaymentTransaction(paymentId, chargebackExternalKey, TransactionType.CHARGEBACK, TransactionStatus.PAYMENT_FAILURE, BigDecimal.TEN));
        final Payment payment = buildPayment(paymentId, transactions);
        Assert.assertEquals(payment.getAuthAmount().compareTo(BigDecimal.TEN), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.TEN), 0);
        Assert.assertEquals(payment.getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getRefundedAmount().compareTo(BigDecimal.ONE), 0);
    }

    @Test(groups = "fast")
    public void testAmountsPurchaseChargeback() throws Exception {
        final UUID paymentId = UUID.randomUUID();
        final String chargebackExternalKey = UUID.randomUUID().toString();
        final List<PaymentTransaction> transactions = ImmutableList.<PaymentTransaction>of(buildPaymentTransaction(paymentId, UUID.randomUUID().toString(), TransactionType.PURCHASE, TransactionStatus.SUCCESS, BigDecimal.TEN),
                                                                                           buildPaymentTransaction(paymentId, chargebackExternalKey, TransactionType.CHARGEBACK, TransactionStatus.SUCCESS, BigDecimal.TEN));
        final Payment payment = buildPayment(paymentId, transactions);
        Assert.assertEquals(payment.getAuthAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
    }

    @Test(groups = "fast")
    public void testAmountsPurchaseChargebackDifferentCurrency() throws Exception {
        final UUID paymentId = UUID.randomUUID();
        final String chargebackExternalKey = UUID.randomUUID().toString();
        final List<PaymentTransaction> transactions = ImmutableList.<PaymentTransaction>of(buildPaymentTransaction(paymentId, UUID.randomUUID().toString(), TransactionType.PURCHASE, TransactionStatus.SUCCESS, BigDecimal.TEN, Currency.USD),
                                                                                           buildPaymentTransaction(paymentId, chargebackExternalKey, TransactionType.CHARGEBACK, TransactionStatus.SUCCESS, BigDecimal.ONE, Currency.EUR));
        final Payment payment = buildPayment(paymentId, transactions);
        Assert.assertEquals(payment.getAuthAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
    }

    @Test(groups = "fast")
    public void testAmountsPurchaseChargebackReversed() throws Exception {
        final UUID paymentId = UUID.randomUUID();
        final String chargebackExternalKey = UUID.randomUUID().toString();
        final List<PaymentTransaction> transactions = ImmutableList.<PaymentTransaction>of(buildPaymentTransaction(paymentId, UUID.randomUUID().toString(), TransactionType.PURCHASE, TransactionStatus.SUCCESS, BigDecimal.TEN),
                                                                                           buildPaymentTransaction(paymentId, chargebackExternalKey, TransactionType.CHARGEBACK, TransactionStatus.SUCCESS, BigDecimal.TEN),
                                                                                           buildPaymentTransaction(paymentId, chargebackExternalKey, TransactionType.CHARGEBACK, TransactionStatus.PAYMENT_FAILURE, BigDecimal.TEN));
        final Payment payment = buildPayment(paymentId, transactions);
        Assert.assertEquals(payment.getAuthAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getPurchasedAmount().compareTo(BigDecimal.TEN), 0);
        Assert.assertEquals(payment.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
    }

    @Test(groups = "fast")
    public void testAmountsPurchaseChargebackReversedAndRefund() throws Exception {
        final UUID paymentId = UUID.randomUUID();
        final String chargebackExternalKey = UUID.randomUUID().toString();
        final List<PaymentTransaction> transactions = ImmutableList.<PaymentTransaction>of(buildPaymentTransaction(paymentId, UUID.randomUUID().toString(), TransactionType.PURCHASE, TransactionStatus.SUCCESS, BigDecimal.TEN),
                                                                                           buildPaymentTransaction(paymentId, UUID.randomUUID().toString(), TransactionType.REFUND, TransactionStatus.SUCCESS, BigDecimal.ONE),
                                                                                           buildPaymentTransaction(paymentId, chargebackExternalKey, TransactionType.CHARGEBACK, TransactionStatus.SUCCESS, BigDecimal.TEN),
                                                                                           buildPaymentTransaction(paymentId, chargebackExternalKey, TransactionType.CHARGEBACK, TransactionStatus.PAYMENT_FAILURE, BigDecimal.TEN));
        final Payment payment = buildPayment(paymentId, transactions);
        Assert.assertEquals(payment.getAuthAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getPurchasedAmount().compareTo(BigDecimal.TEN), 0);
        Assert.assertEquals(payment.getRefundedAmount().compareTo(BigDecimal.ONE), 0);
    }

    @Test(groups = "fast")
    public void testAmountsPurchaseMultipleChargebacks() throws Exception {
        final UUID paymentId = UUID.randomUUID();
        final String chargebackExternalKey = UUID.randomUUID().toString();
        final List<PaymentTransaction> transactions = ImmutableList.<PaymentTransaction>of(buildPaymentTransaction(paymentId, UUID.randomUUID().toString(), TransactionType.PURCHASE, TransactionStatus.SUCCESS, BigDecimal.TEN),
                                                                                           buildPaymentTransaction(paymentId, UUID.randomUUID().toString(), TransactionType.REFUND, TransactionStatus.SUCCESS, BigDecimal.ONE),
                                                                                           buildPaymentTransaction(paymentId, chargebackExternalKey, TransactionType.CHARGEBACK, TransactionStatus.SUCCESS, BigDecimal.ONE),
                                                                                           buildPaymentTransaction(paymentId, UUID.randomUUID().toString(), TransactionType.CHARGEBACK, TransactionStatus.SUCCESS, BigDecimal.ONE),
                                                                                           buildPaymentTransaction(paymentId, chargebackExternalKey, TransactionType.CHARGEBACK, TransactionStatus.PAYMENT_FAILURE, BigDecimal.ONE));
        final Payment payment = buildPayment(paymentId, transactions);
        Assert.assertEquals(payment.getAuthAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getPurchasedAmount().compareTo(new BigDecimal("9")), 0);
        Assert.assertEquals(payment.getRefundedAmount().compareTo(BigDecimal.ONE), 0);
    }

    private PaymentTransaction buildPaymentTransaction(final UUID paymentId, final String externalKey, final TransactionType transactionType, final TransactionStatus transactionStatus, final BigDecimal amount) {
        return buildPaymentTransaction(paymentId, externalKey, transactionType, transactionStatus, amount, Currency.USD);
    }

    private PaymentTransaction buildPaymentTransaction(final UUID paymentId, final String externalKey, final TransactionType transactionType, final TransactionStatus transactionStatus, final BigDecimal amount, final Currency currency) {
        return new DefaultPaymentTransaction(UUID.randomUUID(),
                                             UUID.randomUUID(),
                                             externalKey,
                                             clock.getUTCNow(),
                                             clock.getUTCNow(),
                                             paymentId,
                                             transactionType,
                                             clock.getUTCNow(),
                                             transactionStatus,
                                             amount,
                                             currency,
                                             amount,
                                             currency,
                                             null,
                                             null,
                                             null);
    }

    private Payment buildPayment(final UUID paymentId, final List<PaymentTransaction> transactions) {
        return new DefaultPayment(paymentId,
                                  clock.getUTCNow(),
                                  clock.getUTCNow(),
                                  UUID.randomUUID(),
                                  UUID.randomUUID(),
                                  1,
                                  UUID.randomUUID().toString(),
                                  transactions,
                                  null);
    }
}
