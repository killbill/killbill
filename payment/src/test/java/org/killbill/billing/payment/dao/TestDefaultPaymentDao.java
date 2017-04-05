/*
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

package org.killbill.billing.payment.dao;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.PaymentTestSuiteWithEmbeddedDB;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.core.sm.PaymentStateMachineHelper;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class TestDefaultPaymentDao extends PaymentTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testPaymentCRUD() throws Exception {
        for (int i = 0; i < 3; i++) {
            testPaymentCRUDForAccount(i + 1);
        }
    }

    private void testPaymentCRUDForAccount(final int runNb) throws Exception {
        final Account account = testHelper.createTestAccount(UUID.randomUUID().toString(), true);
        final UUID accountId = account.getId();

        final PaymentModelDao specifiedFirstPaymentModelDao = generatePaymentModelDao(accountId);
        final PaymentTransactionModelDao specifiedFirstPaymentTransactionModelDao = generatePaymentTransactionModelDao(specifiedFirstPaymentModelDao.getId());

        // Create and verify the payment and transaction
        final PaymentModelDao firstPaymentModelDao = paymentDao.insertPaymentWithFirstTransaction(specifiedFirstPaymentModelDao, specifiedFirstPaymentTransactionModelDao, internalCallContext).getPaymentModelDao();
        verifyPayment(firstPaymentModelDao, specifiedFirstPaymentModelDao);
        verifyPaymentAndTransactions(internalCallContext, specifiedFirstPaymentModelDao, specifiedFirstPaymentTransactionModelDao);

        // Create a second transaction for the same payment
        final PaymentTransactionModelDao specifiedSecondPaymentTransactionModelDao = generatePaymentTransactionModelDao(specifiedFirstPaymentModelDao.getId());
        final PaymentTransactionModelDao secondTransactionModelDao = paymentDao.updatePaymentWithNewTransaction(specifiedFirstPaymentTransactionModelDao.getPaymentId(), specifiedSecondPaymentTransactionModelDao, internalCallContext);
        verifyPaymentTransaction(secondTransactionModelDao, specifiedSecondPaymentTransactionModelDao);
        verifyPaymentAndTransactions(internalCallContext, specifiedFirstPaymentModelDao, specifiedFirstPaymentTransactionModelDao, specifiedSecondPaymentTransactionModelDao);

        // Update the latest transaction
        final BigDecimal processedAmount = new BigDecimal("902341.23232");
        final Currency processedCurrency = Currency.USD;
        final String gatewayErrorCode = UUID.randomUUID().toString().substring(0, 5);
        final String gatewayErrorMsg = UUID.randomUUID().toString();
        paymentDao.updatePaymentAndTransactionOnCompletion(accountId,
                                                           specifiedSecondPaymentTransactionModelDao.getAttemptId(),
                                                           specifiedSecondPaymentTransactionModelDao.getPaymentId(),
                                                           specifiedFirstPaymentTransactionModelDao.getTransactionType(),
                                                           PaymentStateMachineHelper.STATE_NAMES[0],
                                                           PaymentStateMachineHelper.STATE_NAMES[0],
                                                           specifiedSecondPaymentTransactionModelDao.getId(),
                                                           TransactionStatus.PAYMENT_FAILURE,
                                                           processedAmount,
                                                           processedCurrency,
                                                           gatewayErrorCode,
                                                           gatewayErrorMsg,
                                                           internalCallContext);

        final PaymentTransactionModelDao updatedSecondPaymentTransactionModelDao = paymentDao.getPaymentTransaction(specifiedSecondPaymentTransactionModelDao.getId(), internalCallContext);
        Assert.assertEquals(updatedSecondPaymentTransactionModelDao.getTransactionStatus(), TransactionStatus.PAYMENT_FAILURE);
        Assert.assertEquals(updatedSecondPaymentTransactionModelDao.getGatewayErrorMsg(), gatewayErrorMsg);
        Assert.assertEquals(updatedSecondPaymentTransactionModelDao.getGatewayErrorMsg(), gatewayErrorMsg);

        // Create multiple payments for that account
        for (int i = 0; i < 3; i++) {
            final PaymentModelDao paymentModelDao = generatePaymentModelDao(accountId);
            final PaymentTransactionModelDao paymentTransactionModelDao = generatePaymentTransactionModelDao(paymentModelDao.getId());

            final PaymentModelDao insertedPaymentModelDao = paymentDao.insertPaymentWithFirstTransaction(paymentModelDao, paymentTransactionModelDao, internalCallContext).getPaymentModelDao();
            verifyPayment(insertedPaymentModelDao, paymentModelDao);

            // Verify search APIs
            Assert.assertEquals(ImmutableList.<PaymentModelDao>copyOf(paymentDao.searchPayments(paymentModelDao.getPaymentMethodId().toString(), 0L, 100L, internalCallContext).iterator()).size(), 1);
            Assert.assertEquals(ImmutableList.<PaymentModelDao>copyOf(paymentDao.searchPayments(paymentModelDao.getExternalKey(), 0L, 100L, internalCallContext).iterator()).size(), 1);
        }
        Assert.assertEquals(paymentDao.getPaymentsForAccount(specifiedFirstPaymentModelDao.getAccountId(), internalCallContext).size(), 4);

        // Verify search APIs
        Assert.assertEquals(ImmutableList.<PaymentModelDao>copyOf(paymentDao.searchPayments(accountId.toString(), 0L, 100L, internalCallContext).iterator()).size(), 4);
        Assert.assertEquals(ImmutableList.<PaymentModelDao>copyOf(paymentDao.searchPayments("_ERRORED", 0L, 100L, internalCallContext).iterator()).size(), runNb);
    }

    private void verifyPaymentAndTransactions(final InternalCallContext accountCallContext, final PaymentModelDao specifiedFirstPaymentModelDao, final PaymentTransactionModelDao... specifiedFirstPaymentTransactionModelDaos) {
        for (final PaymentTransactionModelDao specifiedFirstPaymentTransactionModelDao : specifiedFirstPaymentTransactionModelDaos) {
            final PaymentTransactionModelDao firstTransactionModelDao = paymentDao.getPaymentTransaction(specifiedFirstPaymentTransactionModelDao.getId(), accountCallContext);
            verifyPaymentTransaction(firstTransactionModelDao, specifiedFirstPaymentTransactionModelDao);
        }

        // Retrieve the payment directly
        final PaymentModelDao secondPaymentModelDao = paymentDao.getPayment(specifiedFirstPaymentModelDao.getId(), accountCallContext);
        verifyPayment(secondPaymentModelDao, specifiedFirstPaymentModelDao);

        // Retrieve the payments for the account
        final List<PaymentModelDao> paymentsForAccount = paymentDao.getPaymentsForAccount(specifiedFirstPaymentModelDao.getAccountId(), accountCallContext);
        Assert.assertEquals(paymentsForAccount.size(), 1);
        verifyPayment(paymentsForAccount.get(0), specifiedFirstPaymentModelDao);

        // Retrieve the transactions for the account
        final List<PaymentTransactionModelDao> transactionsForAccount = paymentDao.getTransactionsForAccount(specifiedFirstPaymentModelDao.getAccountId(), accountCallContext);
        Assert.assertEquals(transactionsForAccount.size(), specifiedFirstPaymentTransactionModelDaos.length);
        for (int i = 0; i < specifiedFirstPaymentTransactionModelDaos.length; i++) {
            verifyPaymentTransaction(transactionsForAccount.get(i), specifiedFirstPaymentTransactionModelDaos[i]);
        }

        // Retrieve the transactions for the payment
        final List<PaymentTransactionModelDao> transactionsForPayment = paymentDao.getTransactionsForPayment(specifiedFirstPaymentModelDao.getId(), accountCallContext);
        Assert.assertEquals(transactionsForPayment.size(), specifiedFirstPaymentTransactionModelDaos.length);
        for (int i = 0; i < specifiedFirstPaymentTransactionModelDaos.length; i++) {
            verifyPaymentTransaction(transactionsForPayment.get(i), specifiedFirstPaymentTransactionModelDaos[i]);
        }
    }

    private PaymentTransactionModelDao generatePaymentTransactionModelDao(final UUID paymentId) {
        return new PaymentTransactionModelDao(UUID.randomUUID(),
                                              null,
                                              UUID.randomUUID().toString(),
                                              clock.getUTCNow(),
                                              clock.getUTCNow(),
                                              paymentId,
                                              TransactionType.CAPTURE,
                                              clock.getUTCNow(),
                                              TransactionStatus.SUCCESS,
                                              new BigDecimal("192.32910002"),
                                              Currency.EUR,
                                              UUID.randomUUID().toString().substring(0, 5),
                                              UUID.randomUUID().toString()
        );
    }

    private PaymentModelDao generatePaymentModelDao(final UUID accountId) {
        return new PaymentModelDao(UUID.randomUUID(),
                                   clock.getUTCNow(),
                                   clock.getUTCNow(),
                                   accountId,
                                   UUID.randomUUID(),
                                   -1,
                                   UUID.randomUUID().toString()
        );
    }

    private void verifyPayment(final PaymentModelDao loadedPaymentModelDao, final PaymentModelDao specifiedPaymentModelDao) {
        Assert.assertEquals(loadedPaymentModelDao.getAccountId(), specifiedPaymentModelDao.getAccountId());
        Assert.assertTrue(loadedPaymentModelDao.getPaymentNumber() > 0);
        Assert.assertEquals(loadedPaymentModelDao.getPaymentMethodId(), specifiedPaymentModelDao.getPaymentMethodId());
        Assert.assertEquals(loadedPaymentModelDao.getExternalKey(), specifiedPaymentModelDao.getExternalKey());

        // Verify search APIs
        Assert.assertEquals(ImmutableList.<PaymentModelDao>copyOf(paymentDao.searchPayments(specifiedPaymentModelDao.getPaymentMethodId().toString(), 0L, 100L, internalCallContext).iterator()).size(), 1);
        Assert.assertEquals(ImmutableList.<PaymentModelDao>copyOf(paymentDao.searchPayments(specifiedPaymentModelDao.getExternalKey(), 0L, 100L, internalCallContext).iterator()).size(), 1);
    }

    private void verifyPaymentTransaction(final PaymentTransactionModelDao loadedPaymentTransactionModelDao, final PaymentTransactionModelDao specifiedPaymentTransactionModelDao) {
        Assert.assertEquals(loadedPaymentTransactionModelDao.getPaymentId(), specifiedPaymentTransactionModelDao.getPaymentId());
        Assert.assertEquals(loadedPaymentTransactionModelDao.getTransactionExternalKey(), specifiedPaymentTransactionModelDao.getTransactionExternalKey());
        Assert.assertEquals(loadedPaymentTransactionModelDao.getTransactionType(), specifiedPaymentTransactionModelDao.getTransactionType());
        Assert.assertEquals(loadedPaymentTransactionModelDao.getEffectiveDate().compareTo(specifiedPaymentTransactionModelDao.getEffectiveDate()), 0);
        Assert.assertEquals(loadedPaymentTransactionModelDao.getTransactionStatus(), specifiedPaymentTransactionModelDao.getTransactionStatus());
        Assert.assertEquals(loadedPaymentTransactionModelDao.getAmount().compareTo(specifiedPaymentTransactionModelDao.getAmount()), 0);
        Assert.assertEquals(loadedPaymentTransactionModelDao.getCurrency(), specifiedPaymentTransactionModelDao.getCurrency());
        Assert.assertEquals(loadedPaymentTransactionModelDao.getGatewayErrorCode(), specifiedPaymentTransactionModelDao.getGatewayErrorCode());
        Assert.assertEquals(loadedPaymentTransactionModelDao.getGatewayErrorMsg(), specifiedPaymentTransactionModelDao.getGatewayErrorMsg());
    }
}
