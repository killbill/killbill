/*
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
import java.util.UUID;

import javax.annotation.Nullable;

import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.model.Account;
import org.killbill.billing.client.model.ComboPaymentTransaction;
import org.killbill.billing.client.model.Payment;
import org.killbill.billing.client.model.PaymentMethod;
import org.killbill.billing.client.model.PaymentMethodPluginDetail;
import org.killbill.billing.client.model.PaymentTransaction;
import org.killbill.billing.client.model.Payments;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;

public class TestPayment extends TestJaxrsBase {

    @Test(groups = "slow")
    public void testCreateRetrievePayment() throws Exception {
        final Account account = createAccountWithDefaultPaymentMethod();
        testCreateRetrievePayment(account, null, UUID.randomUUID().toString(), 1);

        final PaymentMethod paymentMethodJson = new PaymentMethod(null, UUID.randomUUID().toString(), account.getAccountId(), false, PLUGIN_NAME, new PaymentMethodPluginDetail());
        final PaymentMethod nonDefaultPaymentMethod = killBillClient.createPaymentMethod(paymentMethodJson, createdBy, reason, comment);
        testCreateRetrievePayment(account, nonDefaultPaymentMethod.getPaymentMethodId(), UUID.randomUUID().toString(), 2);
    }

    @Test(groups = "slow")
    public void testComboAuthorization() throws Exception {
        final Account accountJson = getAccount();
        final PaymentMethodPluginDetail info = new PaymentMethodPluginDetail();
        info.setProperties(null);

        final String paymentMethodExternalKey = UUID.randomUUID().toString();
        final PaymentMethod paymentMethodJson = new PaymentMethod(null, paymentMethodExternalKey, null, true, PLUGIN_NAME, info);

        final String paymentExternalKey = UUID.randomUUID().toString();
        final String authTransactionExternalKey = UUID.randomUUID().toString();
        final PaymentTransaction authTransactionJson = new PaymentTransaction();
        authTransactionJson.setAmount(BigDecimal.TEN);
        authTransactionJson.setCurrency(accountJson.getCurrency());
        authTransactionJson.setPaymentExternalKey(paymentExternalKey);
        authTransactionJson.setTransactionExternalKey(authTransactionExternalKey);
        authTransactionJson.setTransactionType("AUTHORIZE");

        final ComboPaymentTransaction comboPaymentTransaction = new ComboPaymentTransaction(accountJson, paymentMethodJson, authTransactionJson);

        final Payment payment = killBillClient.createPayment(comboPaymentTransaction, ImmutableMap.<String, String>of(), createdBy, reason, comment);
        verifyComboPayment(payment, paymentExternalKey,
                      BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, 1, 1);

    }

    public void testCreateRetrievePayment(final Account account, @Nullable final UUID paymentMethodId,
                                          final String PaymentExternalKey, final int PaymentNb) throws Exception {
        // Authorization
        final String authTransactionExternalKey = UUID.randomUUID().toString();
        final PaymentTransaction authTransaction = new PaymentTransaction();
        authTransaction.setAmount(BigDecimal.TEN);
        authTransaction.setCurrency(account.getCurrency());
        authTransaction.setPaymentExternalKey(PaymentExternalKey);
        authTransaction.setTransactionExternalKey(authTransactionExternalKey);
        authTransaction.setTransactionType("AUTHORIZE");
        final Payment authPayment = killBillClient.createPayment(account.getAccountId(), paymentMethodId, authTransaction, createdBy, reason, comment);
        verifyPayment(account, paymentMethodId, authPayment, PaymentExternalKey, authTransactionExternalKey,
                      BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, 1, PaymentNb);

        // Capture 1
        final String capture1TransactionExternalKey = UUID.randomUUID().toString();
        final PaymentTransaction captureTransaction = new PaymentTransaction();
        captureTransaction.setPaymentId(authPayment.getPaymentId());
        captureTransaction.setAmount(BigDecimal.ONE);
        captureTransaction.setCurrency(account.getCurrency());
        captureTransaction.setPaymentExternalKey(PaymentExternalKey);
        captureTransaction.setTransactionExternalKey(capture1TransactionExternalKey);
        final Payment capturedPayment1 = killBillClient.captureAuthorization(captureTransaction, createdBy, reason, comment);
        verifyPayment(account, paymentMethodId, capturedPayment1, PaymentExternalKey, authTransactionExternalKey,
                      BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO, 2, PaymentNb);
        verifyPaymentTransaction(authPayment.getPaymentId(), capturedPayment1.getTransactions().get(1),
                                 PaymentExternalKey, capture1TransactionExternalKey,
                                 account, captureTransaction.getAmount(), "CAPTURE");

        // Capture 2
        final String capture2TransactionExternalKey = UUID.randomUUID().toString();
        captureTransaction.setTransactionExternalKey(capture2TransactionExternalKey);
        final Payment capturedPayment2 = killBillClient.captureAuthorization(captureTransaction, createdBy, reason, comment);
        verifyPayment(account, paymentMethodId, capturedPayment2, PaymentExternalKey, authTransactionExternalKey,
                      BigDecimal.TEN, new BigDecimal("2"), BigDecimal.ZERO, 3, PaymentNb);
        verifyPaymentTransaction(authPayment.getPaymentId(), capturedPayment2.getTransactions().get(2),
                                 PaymentExternalKey, capture2TransactionExternalKey,
                                 account, captureTransaction.getAmount(), "CAPTURE");

        // Refund
        final String refundTransactionExternalKey = UUID.randomUUID().toString();
        final PaymentTransaction refundTransaction = new PaymentTransaction();
        refundTransaction.setPaymentId(authPayment.getPaymentId());
        refundTransaction.setAmount(new BigDecimal("2"));
        refundTransaction.setCurrency(account.getCurrency());
        refundTransaction.setPaymentExternalKey(PaymentExternalKey);
        refundTransaction.setTransactionExternalKey(refundTransactionExternalKey);
        final Payment refundPayment = killBillClient.refundPayment(refundTransaction, createdBy, reason, comment);
        verifyPayment(account, paymentMethodId, refundPayment, PaymentExternalKey, authTransactionExternalKey,
                      BigDecimal.TEN, new BigDecimal("2"), new BigDecimal("2"), 4, PaymentNb);
        verifyPaymentTransaction(authPayment.getPaymentId(), refundPayment.getTransactions().get(3),
                                 PaymentExternalKey, refundTransactionExternalKey,
                                 account, refundTransaction.getAmount(), "REFUND");
    }

    private void verifyPayment(final Account account, @Nullable final UUID paymentMethodId, final Payment Payment,
                               final String PaymentExternalKey, final String authTransactionExternalKey,
                               final BigDecimal authAmount, final BigDecimal capturedAmount,
                               final BigDecimal refundedAmount, final int nbTransactions, final int PaymentNb) throws KillBillClientException {
        Assert.assertEquals(Payment.getAccountId(), account.getAccountId());
        Assert.assertEquals(Payment.getPaymentMethodId(), Objects.firstNonNull(paymentMethodId, account.getPaymentMethodId()));
        Assert.assertNotNull(Payment.getPaymentId());
        Assert.assertNotNull(Payment.getPaymentNumber());
        Assert.assertEquals(Payment.getPaymentExternalKey(), PaymentExternalKey);
        Assert.assertEquals(Payment.getAuthAmount().compareTo(authAmount), 0);
        Assert.assertEquals(Payment.getCapturedAmount().compareTo(capturedAmount), 0);
        Assert.assertEquals(Payment.getRefundedAmount().compareTo(refundedAmount), 0);
        Assert.assertEquals(Payment.getCurrency(), account.getCurrency());
        Assert.assertEquals(Payment.getTransactions().size(), nbTransactions);

        verifyPaymentTransaction(Payment.getPaymentId(), Payment.getTransactions().get(0),
                                 PaymentExternalKey, authTransactionExternalKey, account, authAmount, "AUTHORIZE");

        final Payments Payments = killBillClient.getPayments();
        Assert.assertEquals(Payments.size(), PaymentNb);
        Assert.assertEquals(Payments.get(PaymentNb - 1), Payment);

        final Payment retrievedPayment = killBillClient.getPayment(Payment.getPaymentId());
        Assert.assertEquals(retrievedPayment, Payment);

        final Payments paymentsForAccount = killBillClient.getPaymentsForAccount(account.getAccountId());
        Assert.assertEquals(paymentsForAccount.size(), PaymentNb);
        Assert.assertEquals(paymentsForAccount.get(PaymentNb - 1), Payment);
    }

    private void verifyComboPayment(final Payment Payment,
                                    final String paymentExternalKey,
                                    final BigDecimal authAmount,
                                    final BigDecimal capturedAmount,
                                    final BigDecimal refundedAmount,
                                    final int nbTransactions,
                                    final int PaymentNb) throws KillBillClientException {

        Assert.assertNotNull(Payment.getPaymentId());
        Assert.assertNotNull(Payment.getPaymentNumber());
        Assert.assertEquals(Payment.getPaymentExternalKey(), paymentExternalKey);
        Assert.assertEquals(Payment.getAuthAmount().compareTo(authAmount), 0);
        Assert.assertEquals(Payment.getCapturedAmount().compareTo(capturedAmount), 0);
        Assert.assertEquals(Payment.getRefundedAmount().compareTo(refundedAmount), 0);
        Assert.assertEquals(Payment.getTransactions().size(), nbTransactions);

        final Payments Payments = killBillClient.getPayments();
        Assert.assertEquals(Payments.size(), PaymentNb);
        Assert.assertEquals(Payments.get(PaymentNb - 1), Payment);

    }

    private void verifyPaymentTransaction(final UUID PaymentId, final PaymentTransaction PaymentTransaction,
                                          final String PaymentExternalKey, final String TransactionExternalKey,
                                          final Account account, @Nullable final BigDecimal amount, final String transactionType) {
        Assert.assertEquals(PaymentTransaction.getPaymentId(), PaymentId);
        Assert.assertNotNull(PaymentTransaction.getTransactionId());
        Assert.assertEquals(PaymentTransaction.getTransactionType(), transactionType);
        Assert.assertEquals(PaymentTransaction.getStatus(), "SUCCESS");
        if (amount == null) {
            Assert.assertNull(PaymentTransaction.getAmount());
            Assert.assertNull(PaymentTransaction.getCurrency());
        } else {
            Assert.assertEquals(PaymentTransaction.getAmount().compareTo(amount), 0);
            Assert.assertEquals(PaymentTransaction.getCurrency(), account.getCurrency());
        }
        Assert.assertEquals(PaymentTransaction.getTransactionExternalKey(), TransactionExternalKey);
        Assert.assertEquals(PaymentTransaction.getPaymentExternalKey(), PaymentExternalKey);
    }
}
