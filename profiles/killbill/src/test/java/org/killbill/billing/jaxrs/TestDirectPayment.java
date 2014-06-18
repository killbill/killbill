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
import org.killbill.billing.client.model.DirectPayment;
import org.killbill.billing.client.model.DirectPayments;
import org.killbill.billing.client.model.DirectTransaction;
import org.killbill.billing.client.model.PaymentMethod;
import org.killbill.billing.client.model.PaymentMethodPluginDetail;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.base.Objects;

public class TestDirectPayment extends TestJaxrsBase {

    @Test(groups = "slow")
    public void testCreateRetrievePayment() throws Exception {
        final Account account = createAccountWithDefaultPaymentMethod();
        testCreateRetrievePayment(account, null, UUID.randomUUID().toString(), 1);

        final PaymentMethod paymentMethodJson = new PaymentMethod(null, account.getAccountId(), false, PLUGIN_NAME, new PaymentMethodPluginDetail());
        final PaymentMethod nonDefaultPaymentMethod = killBillClient.createPaymentMethod(paymentMethodJson, createdBy, reason, comment);
        testCreateRetrievePayment(account, nonDefaultPaymentMethod.getPaymentMethodId(), UUID.randomUUID().toString(), 2);
    }

    public void testCreateRetrievePayment(final Account account, @Nullable final UUID paymentMethodId,
                                          final String directPaymentExternalKey, final int directPaymentNb) throws Exception {
        // Authorization
        final String authDirectTransactionExternalKey = UUID.randomUUID().toString();
        final DirectTransaction authTransaction = new DirectTransaction();
        authTransaction.setAmount(BigDecimal.TEN);
        authTransaction.setCurrency(account.getCurrency());
        authTransaction.setDirectPaymentExternalKey(directPaymentExternalKey);
        authTransaction.setDirectTransactionExternalKey(authDirectTransactionExternalKey);
        authTransaction.setTransactionType("AUTHORIZE");
        final DirectPayment authDirectPayment = killBillClient.createDirectPayment(account.getAccountId(), paymentMethodId, authTransaction, createdBy, reason, comment);
        verifyDirectPayment(account, paymentMethodId, authDirectPayment, directPaymentExternalKey, authDirectTransactionExternalKey,
                            BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, 1, directPaymentNb);

        // Capture 1
        final String capture1DirectTransactionExternalKey = UUID.randomUUID().toString();
        final DirectTransaction captureTransaction = new DirectTransaction();
        captureTransaction.setDirectPaymentId(authDirectPayment.getDirectPaymentId());
        captureTransaction.setAmount(BigDecimal.ONE);
        captureTransaction.setCurrency(account.getCurrency());
        captureTransaction.setDirectPaymentExternalKey(directPaymentExternalKey);
        captureTransaction.setDirectTransactionExternalKey(capture1DirectTransactionExternalKey);
        final DirectPayment capturedDirectPayment1 = killBillClient.captureAuthorization(captureTransaction, createdBy, reason, comment);
        verifyDirectPayment(account, paymentMethodId, capturedDirectPayment1, directPaymentExternalKey, authDirectTransactionExternalKey,
                            BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO, 2, directPaymentNb);
        verifyDirectPaymentTransaction(authDirectPayment.getDirectPaymentId(), capturedDirectPayment1.getTransactions().get(1),
                                       directPaymentExternalKey, capture1DirectTransactionExternalKey,
                                       account, captureTransaction.getAmount(), "CAPTURE");

        // Capture 2
        final String capture2DirectTransactionExternalKey = UUID.randomUUID().toString();
        captureTransaction.setDirectTransactionExternalKey(capture2DirectTransactionExternalKey);
        final DirectPayment capturedDirectPayment2 = killBillClient.captureAuthorization(captureTransaction, createdBy, reason, comment);
        verifyDirectPayment(account, paymentMethodId, capturedDirectPayment2, directPaymentExternalKey, authDirectTransactionExternalKey,
                            BigDecimal.TEN, new BigDecimal("2"), BigDecimal.ZERO, 3, directPaymentNb);
        verifyDirectPaymentTransaction(authDirectPayment.getDirectPaymentId(), capturedDirectPayment2.getTransactions().get(2),
                                       directPaymentExternalKey, capture2DirectTransactionExternalKey,
                                       account, captureTransaction.getAmount(), "CAPTURE");

        // Refund
        final String refundDirectTransactionExternalKey = UUID.randomUUID().toString();
        final DirectTransaction refundTransaction = new DirectTransaction();
        refundTransaction.setDirectPaymentId(authDirectPayment.getDirectPaymentId());
        refundTransaction.setAmount(new BigDecimal("2"));
        refundTransaction.setCurrency(account.getCurrency());
        refundTransaction.setDirectPaymentExternalKey(directPaymentExternalKey);
        refundTransaction.setDirectTransactionExternalKey(refundDirectTransactionExternalKey);
        final DirectPayment refundDirectPayment = killBillClient.refundPayment(refundTransaction, createdBy, reason, comment);
        verifyDirectPayment(account, paymentMethodId, refundDirectPayment, directPaymentExternalKey, authDirectTransactionExternalKey,
                            BigDecimal.TEN, new BigDecimal("2"), new BigDecimal("2"), 4, directPaymentNb);
        verifyDirectPaymentTransaction(authDirectPayment.getDirectPaymentId(), refundDirectPayment.getTransactions().get(3),
                                       directPaymentExternalKey, refundDirectTransactionExternalKey,
                                       account, refundTransaction.getAmount(), "REFUND");
    }

    private void verifyDirectPayment(final Account account, @Nullable final UUID paymentMethodId, final DirectPayment directPayment,
                                     final String directPaymentExternalKey, final String authDirectTransactionExternalKey,
                                     final BigDecimal authAmount, final BigDecimal capturedAmount,
                                     final BigDecimal refundedAmount, final int nbTransactions, final int directPaymentNb) throws KillBillClientException {
        Assert.assertEquals(directPayment.getAccountId(), account.getAccountId());
        Assert.assertEquals(directPayment.getPaymentMethodId(), Objects.firstNonNull(paymentMethodId, account.getPaymentMethodId()));
        Assert.assertNotNull(directPayment.getDirectPaymentId());
        Assert.assertNotNull(directPayment.getPaymentNumber());
        Assert.assertEquals(directPayment.getDirectPaymentExternalKey(), directPaymentExternalKey);
        Assert.assertEquals(directPayment.getAuthAmount().compareTo(authAmount), 0);
        Assert.assertEquals(directPayment.getCapturedAmount().compareTo(capturedAmount), 0);
        Assert.assertEquals(directPayment.getRefundedAmount().compareTo(refundedAmount), 0);
        Assert.assertEquals(directPayment.getCurrency(), account.getCurrency());
        Assert.assertEquals(directPayment.getTransactions().size(), nbTransactions);

        verifyDirectPaymentTransaction(directPayment.getDirectPaymentId(), directPayment.getTransactions().get(0),
                                       directPaymentExternalKey, authDirectTransactionExternalKey, account, authAmount, "AUTHORIZE");

        final DirectPayments directPayments = killBillClient.getDirectPayments();
        Assert.assertEquals(directPayments.size(), directPaymentNb);
        Assert.assertEquals(directPayments.get(directPaymentNb - 1), directPayment);

        final DirectPayment retrievedDirectPayment = killBillClient.getDirectPayment(directPayment.getDirectPaymentId());
        Assert.assertEquals(retrievedDirectPayment, directPayment);

        final DirectPayments directPaymentsForAccount = killBillClient.getDirectPaymentsForAccount(account.getAccountId());
        Assert.assertEquals(directPaymentsForAccount.size(), directPaymentNb);
        Assert.assertEquals(directPaymentsForAccount.get(directPaymentNb - 1), directPayment);
    }

    private void verifyDirectPaymentTransaction(final UUID directPaymentId, final DirectTransaction directTransaction,
                                                final String directPaymentExternalKey, final String directTransactionExternalKey,
                                                final Account account, @Nullable final BigDecimal amount, final String transactionType) {
        Assert.assertEquals(directTransaction.getDirectPaymentId(), directPaymentId);
        Assert.assertNotNull(directTransaction.getDirectTransactionId());
        Assert.assertEquals(directTransaction.getTransactionType(), transactionType);
        Assert.assertEquals(directTransaction.getStatus(), "SUCCESS");
        if (amount == null) {
            Assert.assertNull(directTransaction.getAmount());
            Assert.assertNull(directTransaction.getCurrency());
        } else {
            Assert.assertEquals(directTransaction.getAmount().compareTo(amount), 0);
            Assert.assertEquals(directTransaction.getCurrency(), account.getCurrency());
        }
        Assert.assertEquals(directTransaction.getDirectTransactionExternalKey(), directTransactionExternalKey);
        Assert.assertEquals(directTransaction.getDirectPaymentExternalKey(), directPaymentExternalKey);
    }
}
