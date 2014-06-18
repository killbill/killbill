/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.payment.dao;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.PaymentTestSuiteWithEmbeddedDB;
import org.killbill.billing.payment.api.PaymentStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestDefaultPaymentDao extends PaymentTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testDirectPaymentCRUD() throws Exception {
        for (int i = 0; i < 3; i++) {
            testDirectPaymentCRUDForAccount(UUID.randomUUID(), i + 1);
        }
    }

    public void testDirectPaymentCRUDForAccount(final UUID accountId, final int accountNb) {
        // We need to create specific call contexts to make the account_record_id magic work
        final InternalCallContext accountCallContext = new InternalCallContext(internalCallContext, (long) accountNb);

        final DirectPaymentModelDao specifiedFirstDirectPaymentModelDao = generateDirectPaymentModelDao(accountId);
        final DirectPaymentTransactionModelDao specifiedFirstDirectPaymentTransactionModelDao = generateDirectPaymentTransactionModelDao(specifiedFirstDirectPaymentModelDao.getId());

        // Create and verify the payment and transaction
        final DirectPaymentModelDao firstDirectPaymentModelDao = paymentDao.insertDirectPaymentWithFirstTransaction(specifiedFirstDirectPaymentModelDao, specifiedFirstDirectPaymentTransactionModelDao, accountCallContext);
        verifyDirectPayment(firstDirectPaymentModelDao, specifiedFirstDirectPaymentModelDao);
        verifyDirectPaymentAndTransactions(accountCallContext, specifiedFirstDirectPaymentModelDao, specifiedFirstDirectPaymentTransactionModelDao);

        // Create a second transaction for the same payment
        final DirectPaymentTransactionModelDao specifiedSecondDirectPaymentTransactionModelDao = generateDirectPaymentTransactionModelDao(specifiedFirstDirectPaymentModelDao.getId());
        final DirectPaymentTransactionModelDao secondDirectTransactionModelDao = paymentDao.updateDirectPaymentWithNewTransaction(specifiedFirstDirectPaymentTransactionModelDao.getDirectPaymentId(), specifiedSecondDirectPaymentTransactionModelDao, accountCallContext);
        verifyDirectPaymentTransaction(secondDirectTransactionModelDao, specifiedSecondDirectPaymentTransactionModelDao);
        verifyDirectPaymentAndTransactions(accountCallContext, specifiedFirstDirectPaymentModelDao, specifiedFirstDirectPaymentTransactionModelDao, specifiedSecondDirectPaymentTransactionModelDao);

        // Update the latest transaction
        final BigDecimal processedAmount = new BigDecimal("902341.23232");
        final Currency processedCurrency = Currency.USD;
        final String gatewayErrorCode = UUID.randomUUID().toString().substring(0, 5);
        final String gatewayErrorMsg = UUID.randomUUID().toString();
        paymentDao.updateDirectPaymentAndTransactionOnCompletion(specifiedSecondDirectPaymentTransactionModelDao.getDirectPaymentId(),
                                                                 "SOME_ERRORED_STATE",
                                                                 specifiedSecondDirectPaymentTransactionModelDao.getId(),
                                                                 PaymentStatus.PAYMENT_FAILURE_ABORTED,
                                                                 processedAmount,
                                                                 processedCurrency,
                                                                 gatewayErrorCode,
                                                                 gatewayErrorMsg,
                                                                 accountCallContext);

        final DirectPaymentTransactionModelDao updatedSecondDirectPaymentTransactionModelDao = paymentDao.getDirectPaymentTransaction(specifiedSecondDirectPaymentTransactionModelDao.getId(), accountCallContext);
        Assert.assertEquals(updatedSecondDirectPaymentTransactionModelDao.getPaymentStatus(), PaymentStatus.PAYMENT_FAILURE_ABORTED);
        Assert.assertEquals(updatedSecondDirectPaymentTransactionModelDao.getGatewayErrorMsg(), gatewayErrorMsg);
        Assert.assertEquals(updatedSecondDirectPaymentTransactionModelDao.getGatewayErrorMsg(), gatewayErrorMsg);

        // Create multiple payments for that account
        for (int i = 0; i < 3; i++) {
            final DirectPaymentModelDao directPaymentModelDao = generateDirectPaymentModelDao(accountId);
            final DirectPaymentTransactionModelDao directPaymentTransactionModelDao = generateDirectPaymentTransactionModelDao(directPaymentModelDao.getId());

            final DirectPaymentModelDao insertedDirectPaymentModelDao = paymentDao.insertDirectPaymentWithFirstTransaction(directPaymentModelDao, directPaymentTransactionModelDao, accountCallContext);
            verifyDirectPayment(insertedDirectPaymentModelDao, directPaymentModelDao);
        }
        Assert.assertEquals(paymentDao.getDirectPaymentsForAccount(specifiedFirstDirectPaymentModelDao.getAccountId(), accountCallContext).size(), 4);
    }

    private void verifyDirectPaymentAndTransactions(final InternalCallContext accountCallContext, final DirectPaymentModelDao specifiedFirstDirectPaymentModelDao, final DirectPaymentTransactionModelDao... specifiedFirstDirectPaymentTransactionModelDaos) {
        for (final DirectPaymentTransactionModelDao specifiedFirstDirectPaymentTransactionModelDao : specifiedFirstDirectPaymentTransactionModelDaos) {
            final DirectPaymentTransactionModelDao firstDirectTransactionModelDao = paymentDao.getDirectPaymentTransaction(specifiedFirstDirectPaymentTransactionModelDao.getId(), accountCallContext);
            verifyDirectPaymentTransaction(firstDirectTransactionModelDao, specifiedFirstDirectPaymentTransactionModelDao);
        }

        // Retrieve the payment directly
        final DirectPaymentModelDao secondDirectPaymentModelDao = paymentDao.getDirectPayment(specifiedFirstDirectPaymentModelDao.getId(), accountCallContext);
        verifyDirectPayment(secondDirectPaymentModelDao, specifiedFirstDirectPaymentModelDao);

        // Retrieve the payments for the account
        final List<DirectPaymentModelDao> paymentsForAccount = paymentDao.getDirectPaymentsForAccount(specifiedFirstDirectPaymentModelDao.getAccountId(), accountCallContext);
        Assert.assertEquals(paymentsForAccount.size(), 1);
        verifyDirectPayment(paymentsForAccount.get(0), specifiedFirstDirectPaymentModelDao);

        // Retrieve the transactions for the account
        final List<DirectPaymentTransactionModelDao> transactionsForAccount = paymentDao.getDirectTransactionsForAccount(specifiedFirstDirectPaymentModelDao.getAccountId(), accountCallContext);
        Assert.assertEquals(transactionsForAccount.size(), specifiedFirstDirectPaymentTransactionModelDaos.length);
        for (int i = 0; i < specifiedFirstDirectPaymentTransactionModelDaos.length; i++) {
            verifyDirectPaymentTransaction(transactionsForAccount.get(i), specifiedFirstDirectPaymentTransactionModelDaos[i]);
        }

        // Retrieve the transactions for the payment
        final List<DirectPaymentTransactionModelDao> transactionsForPayment = paymentDao.getDirectTransactionsForDirectPayment(specifiedFirstDirectPaymentModelDao.getId(), accountCallContext);
        Assert.assertEquals(transactionsForPayment.size(), specifiedFirstDirectPaymentTransactionModelDaos.length);
        for (int i = 0; i < specifiedFirstDirectPaymentTransactionModelDaos.length; i++) {
            verifyDirectPaymentTransaction(transactionsForPayment.get(i), specifiedFirstDirectPaymentTransactionModelDaos[i]);
        }
    }

    private DirectPaymentTransactionModelDao generateDirectPaymentTransactionModelDao(final UUID directPaymentId) {
        return new DirectPaymentTransactionModelDao(UUID.randomUUID(),
                                                    UUID.randomUUID().toString(),
                                                    clock.getUTCNow(),
                                                    clock.getUTCNow(),
                                                    directPaymentId,
                                                    TransactionType.CAPTURE,
                                                    clock.getUTCNow(),
                                                    PaymentStatus.SUCCESS,
                                                    new BigDecimal("192.32910002"),
                                                    Currency.EUR,
                                                    UUID.randomUUID().toString().substring(0, 5),
                                                    UUID.randomUUID().toString(),
                                                    null, null);
    }

    private DirectPaymentModelDao generateDirectPaymentModelDao(final UUID accountId) {
        return new DirectPaymentModelDao(UUID.randomUUID(),
                                         clock.getUTCNow(),
                                         clock.getUTCNow(),
                                         accountId,
                                         UUID.randomUUID(),
                                         -1,
                                         UUID.randomUUID().toString(),
                                         null, null);
    }

    private void verifyDirectPayment(final DirectPaymentModelDao loadedDirectPaymentModelDao, final DirectPaymentModelDao specifiedDirectPaymentModelDao) {
        Assert.assertEquals(loadedDirectPaymentModelDao.getAccountId(), specifiedDirectPaymentModelDao.getAccountId());
        Assert.assertTrue(loadedDirectPaymentModelDao.getPaymentNumber() > 0);
        Assert.assertEquals(loadedDirectPaymentModelDao.getPaymentMethodId(), specifiedDirectPaymentModelDao.getPaymentMethodId());
        Assert.assertEquals(loadedDirectPaymentModelDao.getExternalKey(), specifiedDirectPaymentModelDao.getExternalKey());
    }

    private void verifyDirectPaymentTransaction(final DirectPaymentTransactionModelDao loadedDirectPaymentTransactionModelDao, final DirectPaymentTransactionModelDao specifiedDirectPaymentTransactionModelDao) {
        Assert.assertEquals(loadedDirectPaymentTransactionModelDao.getDirectPaymentId(), specifiedDirectPaymentTransactionModelDao.getDirectPaymentId());
        Assert.assertEquals(loadedDirectPaymentTransactionModelDao.getTransactionExternalKey(), specifiedDirectPaymentTransactionModelDao.getTransactionExternalKey());
        Assert.assertEquals(loadedDirectPaymentTransactionModelDao.getTransactionType(), specifiedDirectPaymentTransactionModelDao.getTransactionType());
        Assert.assertEquals(loadedDirectPaymentTransactionModelDao.getEffectiveDate().compareTo(specifiedDirectPaymentTransactionModelDao.getEffectiveDate()), 0);
        Assert.assertEquals(loadedDirectPaymentTransactionModelDao.getPaymentStatus(), specifiedDirectPaymentTransactionModelDao.getPaymentStatus());
        Assert.assertEquals(loadedDirectPaymentTransactionModelDao.getAmount().compareTo(specifiedDirectPaymentTransactionModelDao.getAmount()), 0);
        Assert.assertEquals(loadedDirectPaymentTransactionModelDao.getCurrency(), specifiedDirectPaymentTransactionModelDao.getCurrency());
        Assert.assertEquals(loadedDirectPaymentTransactionModelDao.getGatewayErrorCode(), specifiedDirectPaymentTransactionModelDao.getGatewayErrorCode());
        Assert.assertEquals(loadedDirectPaymentTransactionModelDao.getGatewayErrorMsg(), specifiedDirectPaymentTransactionModelDao.getGatewayErrorMsg());
    }
}
