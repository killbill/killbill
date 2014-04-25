/*
 * Copyright 2014 Groupon, Inc
 *
 * Groupon licenses this file to you under the Apache License, version 2.0
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
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestDirectPayment extends TestJaxrsBase {

    @Test(groups = "slow")
    public void testCreateRetrievePayment() throws Exception {
        final Account account = createAccountWithDefaultPaymentMethod();

        // Authorization
        final DirectTransaction authTransaction = new DirectTransaction();
        authTransaction.setAmount(BigDecimal.TEN);
        authTransaction.setCurrency(account.getCurrency());
        authTransaction.setExternalKey("foo");
        authTransaction.setTransactionType("AUTHORIZE");
        final DirectPayment authDirectPayment = killBillClient.createDirectPayment(account.getAccountId(), authTransaction, createdBy, reason, comment);
        verifyDirectPayment(account, authDirectPayment, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, 1);

        // Capture 1
        final DirectTransaction captureTransaction = new DirectTransaction();
        captureTransaction.setDirectPaymentId(authDirectPayment.getDirectPaymentId());
        captureTransaction.setAmount(BigDecimal.ONE);
        captureTransaction.setCurrency(account.getCurrency());
        final DirectPayment capturedDirectPayment1 = killBillClient.captureAuthorization(captureTransaction, createdBy, reason, comment);
        verifyDirectPayment(account, capturedDirectPayment1, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO, 2);
        verifyDirectPaymentTransaction(authDirectPayment.getDirectPaymentId(), capturedDirectPayment1.getTransactions().get(1),
                                       account, captureTransaction.getAmount(), "CAPTURE");

        // Capture 2
        final DirectPayment capturedDirectPayment2 = killBillClient.captureAuthorization(captureTransaction, createdBy, reason, comment);
        verifyDirectPayment(account, capturedDirectPayment2, BigDecimal.TEN, new BigDecimal("2"), BigDecimal.ZERO, 3);
        verifyDirectPaymentTransaction(authDirectPayment.getDirectPaymentId(), capturedDirectPayment2.getTransactions().get(2),
                                       account, captureTransaction.getAmount(), "CAPTURE");

        // Credit
        final DirectTransaction creditTransaction = new DirectTransaction();
        creditTransaction.setDirectPaymentId(authDirectPayment.getDirectPaymentId());
        creditTransaction.setAmount(new BigDecimal("223.12"));
        creditTransaction.setCurrency(account.getCurrency());
        final DirectPayment creditDirectPayment = killBillClient.creditPayment(creditTransaction, createdBy, reason, comment);
        verifyDirectPayment(account, creditDirectPayment, BigDecimal.TEN, new BigDecimal("2"), new BigDecimal("223.12"), 4);
        verifyDirectPaymentTransaction(authDirectPayment.getDirectPaymentId(), creditDirectPayment.getTransactions().get(3),
                                       account, creditTransaction.getAmount(), "CREDIT");

        // Void
        final DirectPayment voidDirectPayment = killBillClient.voidPayment(authDirectPayment.getDirectPaymentId(), createdBy, reason, comment);
        verifyDirectPayment(account, voidDirectPayment, BigDecimal.TEN, new BigDecimal("2"), new BigDecimal("223.12"), 5);
        verifyDirectPaymentTransaction(authDirectPayment.getDirectPaymentId(), voidDirectPayment.getTransactions().get(4),
                                       account, null, "VOID");
    }

    private void verifyDirectPayment(final Account account, final DirectPayment directPayment,
                                     final BigDecimal authAmount, final BigDecimal capturedAmount,
                                     final BigDecimal refundedAmount, final int nbTransactions) throws KillBillClientException {
        Assert.assertEquals(directPayment.getAccountId(), account.getAccountId());
        Assert.assertNotNull(directPayment.getDirectPaymentId());
        Assert.assertNotNull(directPayment.getPaymentNumber());
        Assert.assertEquals(directPayment.getAuthAmount().compareTo(authAmount), 0);
        Assert.assertEquals(directPayment.getCapturedAmount().compareTo(capturedAmount), 0);
        Assert.assertEquals(directPayment.getRefundedAmount().compareTo(refundedAmount), 0);
        Assert.assertEquals(directPayment.getCurrency(), account.getCurrency());
        Assert.assertEquals(directPayment.getPaymentMethodId(), account.getPaymentMethodId());
        Assert.assertEquals(directPayment.getTransactions().size(), nbTransactions);

        verifyDirectPaymentTransaction(directPayment.getDirectPaymentId(), directPayment.getTransactions().get(0),
                                       account, authAmount, "AUTHORIZE");

        final DirectPayments directPayments = killBillClient.getDirectPayments();
        Assert.assertEquals(directPayments.size(), 1);
        Assert.assertEquals(directPayments.get(0), directPayment);

        final DirectPayment retrievedDirectPayment = killBillClient.getDirectPayment(directPayment.getDirectPaymentId());
        Assert.assertEquals(retrievedDirectPayment, directPayment);

        final DirectPayments directPaymentsForAccount = killBillClient.getDirectPaymentsForAccount(account.getAccountId());
        Assert.assertEquals(directPaymentsForAccount.size(), 1);
        Assert.assertEquals(directPaymentsForAccount.get(0), directPayment);
    }

    private void verifyDirectPaymentTransaction(final UUID directPaymentId, final DirectTransaction directTransaction,
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
        Assert.assertEquals(directTransaction.getExternalKey(), "foo");
    }
}
