/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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
import java.util.Map;
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
import org.killbill.billing.client.model.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
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
        accountJson.setAccountId(null);

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

        final ComboPaymentTransaction comboPaymentTransaction = new ComboPaymentTransaction(accountJson, paymentMethodJson, authTransactionJson, ImmutableList.<PluginProperty>of(), ImmutableList.<PluginProperty>of());

        final Payment payment = killBillClient.createPayment(comboPaymentTransaction, ImmutableMap.<String, String>of(), createdBy, reason, comment);
        verifyComboPayment(payment, paymentExternalKey, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, 1, 1);

        // Void payment using externalKey
        final String voidTransactionExternalKey = UUID.randomUUID().toString();
        final Payment voidPayment = killBillClient.voidPayment(null, paymentExternalKey, voidTransactionExternalKey, ImmutableMap.<String, String>of(), createdBy, reason, comment);
        verifyPaymentTransaction(accountJson, voidPayment.getPaymentId(), paymentExternalKey, voidPayment.getTransactions().get(1),
                                 voidTransactionExternalKey, null, "VOID");
    }

    private void testCreateRetrievePayment(final Account account, @Nullable final UUID paymentMethodId,
                                           final String paymentExternalKey, final int paymentNb) throws Exception {
        // Authorization
        final String authTransactionExternalKey = UUID.randomUUID().toString();
        final Payment authPayment = createVerifyTransaction(account, paymentMethodId, paymentExternalKey, authTransactionExternalKey, TransactionType.AUTHORIZE, ImmutableMap.<String, String>of(), paymentNb);

        // Capture 1
        final String capture1TransactionExternalKey = UUID.randomUUID().toString();
        final PaymentTransaction captureTransaction = new PaymentTransaction();
        captureTransaction.setPaymentId(authPayment.getPaymentId());
        captureTransaction.setAmount(BigDecimal.ONE);
        captureTransaction.setCurrency(account.getCurrency());
        captureTransaction.setPaymentExternalKey(paymentExternalKey);
        captureTransaction.setTransactionExternalKey(capture1TransactionExternalKey);
        // captureAuthorization is using paymentId
        final Payment capturedPayment1 = killBillClient.captureAuthorization(captureTransaction, createdBy, reason, comment);
        verifyPayment(account, paymentMethodId, capturedPayment1, paymentExternalKey, authTransactionExternalKey,
                      BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO, 2, paymentNb);
        verifyPaymentTransaction(account, authPayment.getPaymentId(), paymentExternalKey, capturedPayment1.getTransactions().get(1),
                                 capture1TransactionExternalKey, captureTransaction.getAmount(), "CAPTURE");

        // Capture 2
        final String capture2TransactionExternalKey = UUID.randomUUID().toString();
        captureTransaction.setTransactionExternalKey(capture2TransactionExternalKey);
        // captureAuthorization is using externalKey
        captureTransaction.setPaymentId(null);
        final Payment capturedPayment2 = killBillClient.captureAuthorization(captureTransaction, createdBy, reason, comment);
        verifyPayment(account, paymentMethodId, capturedPayment2, paymentExternalKey, authTransactionExternalKey,
                      BigDecimal.TEN, new BigDecimal("2"), BigDecimal.ZERO, 3, paymentNb);
        verifyPaymentTransaction(account, authPayment.getPaymentId(), paymentExternalKey, capturedPayment2.getTransactions().get(2),
                                 capture2TransactionExternalKey, captureTransaction.getAmount(), "CAPTURE");

        // Refund
        final String refundTransactionExternalKey = UUID.randomUUID().toString();
        final PaymentTransaction refundTransaction = new PaymentTransaction();
        refundTransaction.setPaymentId(authPayment.getPaymentId());
        refundTransaction.setAmount(new BigDecimal("2"));
        refundTransaction.setCurrency(account.getCurrency());
        refundTransaction.setPaymentExternalKey(paymentExternalKey);
        refundTransaction.setTransactionExternalKey(refundTransactionExternalKey);
        final Payment refundPayment = killBillClient.refundPayment(refundTransaction, createdBy, reason, comment);
        verifyPayment(account, paymentMethodId, refundPayment, paymentExternalKey, authTransactionExternalKey,
                      BigDecimal.TEN, new BigDecimal("2"), new BigDecimal("2"), 4, paymentNb);
        verifyPaymentTransaction(account, authPayment.getPaymentId(), paymentExternalKey, refundPayment.getTransactions().get(3),
                                 refundTransactionExternalKey, refundTransaction.getAmount(), "REFUND");
    }

    private Payment createVerifyTransaction(final Account account,
                                            @Nullable final UUID paymentMethodId,
                                            final String paymentExternalKey,
                                            final String transactionExternalKey,
                                            final TransactionType transactionType,
                                            final Map<String, String> pluginProperties,
                                            final int paymentNb) throws KillBillClientException {
        final PaymentTransaction authTransaction = new PaymentTransaction();
        authTransaction.setAmount(BigDecimal.TEN);
        authTransaction.setCurrency(account.getCurrency());
        authTransaction.setPaymentExternalKey(paymentExternalKey);
        authTransaction.setTransactionExternalKey(transactionExternalKey);
        authTransaction.setTransactionType(transactionType.toString());
        final Payment payment = killBillClient.createPayment(account.getAccountId(), paymentMethodId, authTransaction, pluginProperties, createdBy, reason, comment);

        verifyPaymentNoTransaction(account, paymentMethodId, payment, paymentExternalKey, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, 1, paymentNb);

        return payment;
    }

    private void verifyComboPayment(final Payment payment,
                                    final String paymentExternalKey,
                                    final BigDecimal authAmount,
                                    final BigDecimal capturedAmount,
                                    final BigDecimal refundedAmount,
                                    final int nbTransactions,
                                    final int paymentNb) throws KillBillClientException {
        Assert.assertNotNull(payment.getPaymentNumber());
        Assert.assertEquals(payment.getPaymentExternalKey(), paymentExternalKey);
        Assert.assertEquals(payment.getAuthAmount().compareTo(authAmount), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(capturedAmount), 0);
        Assert.assertEquals(payment.getRefundedAmount().compareTo(refundedAmount), 0);
        Assert.assertEquals(payment.getTransactions().size(), nbTransactions);

        final Payments Payments = killBillClient.getPayments();
        Assert.assertEquals(Payments.size(), paymentNb);
        Assert.assertEquals(Payments.get(paymentNb - 1), payment);
    }

    private void verifyPayment(final Account account,
                               @Nullable final UUID paymentMethodId,
                               final Payment payment,
                               final String paymentExternalKey,
                               final String authTransactionExternalKey,
                               final BigDecimal authAmount,
                               final BigDecimal capturedAmount,
                               final BigDecimal refundedAmount,
                               final int nbTransactions,
                               final int paymentNb) throws KillBillClientException {
        verifyPaymentNoTransaction(account, paymentMethodId, payment, paymentExternalKey, authAmount, capturedAmount, refundedAmount, nbTransactions, paymentNb);
        verifyPaymentTransaction(account, payment.getPaymentId(), paymentExternalKey, payment.getTransactions().get(0), authTransactionExternalKey, authAmount, "AUTHORIZE");
    }

    private void verifyPaymentNoTransaction(final Account account,
                                            @Nullable final UUID paymentMethodId,
                                            final Payment payment,
                                            final String paymentExternalKey,
                                            final BigDecimal authAmount,
                                            final BigDecimal capturedAmount,
                                            final BigDecimal refundedAmount,
                                            final int nbTransactions,
                                            final int paymentNb) throws KillBillClientException {
        Assert.assertEquals(payment.getAccountId(), account.getAccountId());
        Assert.assertEquals(payment.getPaymentMethodId(), MoreObjects.firstNonNull(paymentMethodId, account.getPaymentMethodId()));
        Assert.assertNotNull(payment.getPaymentId());
        Assert.assertNotNull(payment.getPaymentNumber());
        Assert.assertEquals(payment.getPaymentExternalKey(), paymentExternalKey);
        Assert.assertEquals(payment.getAuthAmount().compareTo(authAmount), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(capturedAmount), 0);
        Assert.assertEquals(payment.getRefundedAmount().compareTo(refundedAmount), 0);
        Assert.assertEquals(payment.getCurrency(), account.getCurrency());
        Assert.assertEquals(payment.getTransactions().size(), nbTransactions);

        final Payments Payments = killBillClient.getPayments();
        Assert.assertEquals(Payments.size(), paymentNb);
        Assert.assertEquals(Payments.get(paymentNb - 1), payment);

        final Payment retrievedPayment = killBillClient.getPayment(payment.getPaymentId());
        Assert.assertEquals(retrievedPayment, payment);

        final Payments paymentsForAccount = killBillClient.getPaymentsForAccount(account.getAccountId());
        Assert.assertEquals(paymentsForAccount.size(), paymentNb);
        Assert.assertEquals(paymentsForAccount.get(paymentNb - 1), payment);
    }

    private void verifyPaymentTransaction(final Account account,
                                          final UUID paymentId,
                                          final String paymentExternalKey,
                                          final PaymentTransaction paymentTransaction,
                                          final String transactionExternalKey,
                                          @Nullable final BigDecimal amount,
                                          final String transactionType) {
        Assert.assertEquals(paymentTransaction.getPaymentId(), paymentId);
        Assert.assertNotNull(paymentTransaction.getTransactionId());
        Assert.assertEquals(paymentTransaction.getTransactionType(), transactionType);
        Assert.assertEquals(paymentTransaction.getStatus(), "SUCCESS");
        if (amount == null) {
            Assert.assertNull(paymentTransaction.getAmount());
            Assert.assertNull(paymentTransaction.getCurrency());
        } else {
            Assert.assertEquals(paymentTransaction.getAmount().compareTo(amount), 0);
            Assert.assertEquals(paymentTransaction.getCurrency(), account.getCurrency());
        }
        Assert.assertEquals(paymentTransaction.getTransactionExternalKey(), transactionExternalKey);
        Assert.assertEquals(paymentTransaction.getPaymentExternalKey(), paymentExternalKey);
    }
}
