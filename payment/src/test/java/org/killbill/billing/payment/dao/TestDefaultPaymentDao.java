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
import org.killbill.billing.payment.api.TransactionStatus;
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

        final PaymentModelDao specifiedFirstPaymentModelDao = generateDirectPaymentModelDao(accountId);
        final PaymentTransactionModelDao specifiedFirstDirectPaymentTransactionModelDao = generateDirectPaymentTransactionModelDao(specifiedFirstPaymentModelDao.getId());

        // Create and verify the payment and transaction
        final PaymentModelDao firstPaymentModelDao = paymentDao.insertDirectPaymentWithFirstTransaction(specifiedFirstPaymentModelDao, specifiedFirstDirectPaymentTransactionModelDao, accountCallContext);
        verifyDirectPayment(firstPaymentModelDao, specifiedFirstPaymentModelDao);
        verifyDirectPaymentAndTransactions(accountCallContext, specifiedFirstPaymentModelDao, specifiedFirstDirectPaymentTransactionModelDao);

        // Create a second transaction for the same payment
        final PaymentTransactionModelDao specifiedSecondDirectPaymentTransactionModelDao = generateDirectPaymentTransactionModelDao(specifiedFirstPaymentModelDao.getId());
        final PaymentTransactionModelDao secondDirectTransactionModelDao = paymentDao.updateDirectPaymentWithNewTransaction(specifiedFirstDirectPaymentTransactionModelDao.getPaymentId(), specifiedSecondDirectPaymentTransactionModelDao, accountCallContext);
        verifyDirectPaymentTransaction(secondDirectTransactionModelDao, specifiedSecondDirectPaymentTransactionModelDao);
        verifyDirectPaymentAndTransactions(accountCallContext, specifiedFirstPaymentModelDao, specifiedFirstDirectPaymentTransactionModelDao, specifiedSecondDirectPaymentTransactionModelDao);

        // Update the latest transaction
        final BigDecimal processedAmount = new BigDecimal("902341.23232");
        final Currency processedCurrency = Currency.USD;
        final String gatewayErrorCode = UUID.randomUUID().toString().substring(0, 5);
        final String gatewayErrorMsg = UUID.randomUUID().toString();
        paymentDao.updateDirectPaymentAndTransactionOnCompletion(specifiedSecondDirectPaymentTransactionModelDao.getPaymentId(),
                                                                 "SOME_ERRORED_STATE",
                                                                 "SOME_ERRORED_STATE",
                                                                 specifiedSecondDirectPaymentTransactionModelDao.getId(),
                                                                 TransactionStatus.PAYMENT_FAILURE,
                                                                 processedAmount,
                                                                 processedCurrency,
                                                                 gatewayErrorCode,
                                                                 gatewayErrorMsg,
                                                                 accountCallContext);

        final PaymentTransactionModelDao updatedSecondDirectPaymentTransactionModelDao = paymentDao.getDirectPaymentTransaction(specifiedSecondDirectPaymentTransactionModelDao.getId(), accountCallContext);
        Assert.assertEquals(updatedSecondDirectPaymentTransactionModelDao.getTransactionStatus(), TransactionStatus.PAYMENT_FAILURE);
        Assert.assertEquals(updatedSecondDirectPaymentTransactionModelDao.getGatewayErrorMsg(), gatewayErrorMsg);
        Assert.assertEquals(updatedSecondDirectPaymentTransactionModelDao.getGatewayErrorMsg(), gatewayErrorMsg);

        // Create multiple payments for that account
        for (int i = 0; i < 3; i++) {
            final PaymentModelDao paymentModelDao = generateDirectPaymentModelDao(accountId);
            final PaymentTransactionModelDao directPaymentTransactionModelDao = generateDirectPaymentTransactionModelDao(paymentModelDao.getId());

            final PaymentModelDao insertedPaymentModelDao = paymentDao.insertDirectPaymentWithFirstTransaction(paymentModelDao, directPaymentTransactionModelDao, accountCallContext);
            verifyDirectPayment(insertedPaymentModelDao, paymentModelDao);
        }
        Assert.assertEquals(paymentDao.getDirectPaymentsForAccount(specifiedFirstPaymentModelDao.getAccountId(), accountCallContext).size(), 4);
    }

    private void verifyDirectPaymentAndTransactions(final InternalCallContext accountCallContext, final PaymentModelDao specifiedFirstPaymentModelDao, final PaymentTransactionModelDao... specifiedFirstDirectPaymentTransactionModelDaos) {
        for (final PaymentTransactionModelDao specifiedFirstDirectPaymentTransactionModelDao : specifiedFirstDirectPaymentTransactionModelDaos) {
            final PaymentTransactionModelDao firstDirectTransactionModelDao = paymentDao.getDirectPaymentTransaction(specifiedFirstDirectPaymentTransactionModelDao.getId(), accountCallContext);
            verifyDirectPaymentTransaction(firstDirectTransactionModelDao, specifiedFirstDirectPaymentTransactionModelDao);
        }

        // Retrieve the payment directly
        final PaymentModelDao secondPaymentModelDao = paymentDao.getDirectPayment(specifiedFirstPaymentModelDao.getId(), accountCallContext);
        verifyDirectPayment(secondPaymentModelDao, specifiedFirstPaymentModelDao);

        // Retrieve the payments for the account
        final List<PaymentModelDao> paymentsForAccount = paymentDao.getDirectPaymentsForAccount(specifiedFirstPaymentModelDao.getAccountId(), accountCallContext);
        Assert.assertEquals(paymentsForAccount.size(), 1);
        verifyDirectPayment(paymentsForAccount.get(0), specifiedFirstPaymentModelDao);

        // Retrieve the transactions for the account
        final List<PaymentTransactionModelDao> transactionsForAccount = paymentDao.getDirectTransactionsForAccount(specifiedFirstPaymentModelDao.getAccountId(), accountCallContext);
        Assert.assertEquals(transactionsForAccount.size(), specifiedFirstDirectPaymentTransactionModelDaos.length);
        for (int i = 0; i < specifiedFirstDirectPaymentTransactionModelDaos.length; i++) {
            verifyDirectPaymentTransaction(transactionsForAccount.get(i), specifiedFirstDirectPaymentTransactionModelDaos[i]);
        }

        // Retrieve the transactions for the payment
        final List<PaymentTransactionModelDao> transactionsForPayment = paymentDao.getDirectTransactionsForDirectPayment(specifiedFirstPaymentModelDao.getId(), accountCallContext);
        Assert.assertEquals(transactionsForPayment.size(), specifiedFirstDirectPaymentTransactionModelDaos.length);
        for (int i = 0; i < specifiedFirstDirectPaymentTransactionModelDaos.length; i++) {
            verifyDirectPaymentTransaction(transactionsForPayment.get(i), specifiedFirstDirectPaymentTransactionModelDaos[i]);
        }
    }

    private PaymentTransactionModelDao generateDirectPaymentTransactionModelDao(final UUID directPaymentId) {
        return new PaymentTransactionModelDao(UUID.randomUUID(),
                                              null,
                                              UUID.randomUUID().toString(),
                                              clock.getUTCNow(),
                                              clock.getUTCNow(),
                                              directPaymentId,
                                              TransactionType.CAPTURE,
                                              clock.getUTCNow(),
                                              TransactionStatus.SUCCESS,
                                              new BigDecimal("192.32910002"),
                                              Currency.EUR,
                                              UUID.randomUUID().toString().substring(0, 5),
                                              UUID.randomUUID().toString()
        );
    }

    private PaymentModelDao generateDirectPaymentModelDao(final UUID accountId) {
        return new PaymentModelDao(UUID.randomUUID(),
                                   clock.getUTCNow(),
                                   clock.getUTCNow(),
                                   accountId,
                                   UUID.randomUUID(),
                                   -1,
                                   UUID.randomUUID().toString()
        );
    }

    private void verifyDirectPayment(final PaymentModelDao loadedPaymentModelDao, final PaymentModelDao specifiedPaymentModelDao) {
        Assert.assertEquals(loadedPaymentModelDao.getAccountId(), specifiedPaymentModelDao.getAccountId());
        Assert.assertTrue(loadedPaymentModelDao.getPaymentNumber() > 0);
        Assert.assertEquals(loadedPaymentModelDao.getPaymentMethodId(), specifiedPaymentModelDao.getPaymentMethodId());
        Assert.assertEquals(loadedPaymentModelDao.getExternalKey(), specifiedPaymentModelDao.getExternalKey());
    }

    private void verifyDirectPaymentTransaction(final PaymentTransactionModelDao loadedDirectPaymentTransactionModelDao, final PaymentTransactionModelDao specifiedDirectPaymentTransactionModelDao) {
        Assert.assertEquals(loadedDirectPaymentTransactionModelDao.getPaymentId(), specifiedDirectPaymentTransactionModelDao.getPaymentId());
        Assert.assertEquals(loadedDirectPaymentTransactionModelDao.getTransactionExternalKey(), specifiedDirectPaymentTransactionModelDao.getTransactionExternalKey());
        Assert.assertEquals(loadedDirectPaymentTransactionModelDao.getTransactionType(), specifiedDirectPaymentTransactionModelDao.getTransactionType());
        Assert.assertEquals(loadedDirectPaymentTransactionModelDao.getEffectiveDate().compareTo(specifiedDirectPaymentTransactionModelDao.getEffectiveDate()), 0);
        Assert.assertEquals(loadedDirectPaymentTransactionModelDao.getTransactionStatus(), specifiedDirectPaymentTransactionModelDao.getTransactionStatus());
        Assert.assertEquals(loadedDirectPaymentTransactionModelDao.getAmount().compareTo(specifiedDirectPaymentTransactionModelDao.getAmount()), 0);
        Assert.assertEquals(loadedDirectPaymentTransactionModelDao.getCurrency(), specifiedDirectPaymentTransactionModelDao.getCurrency());
        Assert.assertEquals(loadedDirectPaymentTransactionModelDao.getGatewayErrorCode(), specifiedDirectPaymentTransactionModelDao.getGatewayErrorCode());
        Assert.assertEquals(loadedDirectPaymentTransactionModelDao.getGatewayErrorMsg(), specifiedDirectPaymentTransactionModelDao.getGatewayErrorMsg());
    }
}
