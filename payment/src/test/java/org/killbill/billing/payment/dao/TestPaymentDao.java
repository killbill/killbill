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

package org.killbill.billing.payment.dao;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.billing.GuicyKillbillTestSuite;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.PaymentTestSuiteWithEmbeddedDB;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.dao.PluginPropertySerializer.PluginPropertySerializerException;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.UserType;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

public class TestPaymentDao extends PaymentTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testPaymentAttempt() throws PluginPropertySerializerException {
        final UUID directTransactionId = UUID.randomUUID();
        final String paymentExternalKey = "vraiment?";
        final String transactionExternalKey = "tduteuqweq";
        final String stateName = "INIT";
        final TransactionType transactionType = TransactionType.AUTHORIZE;
        final String pluginName = "superPlugin";

        final UUID accountId = UUID.randomUUID();

        final List<PluginProperty> properties = new ArrayList<PluginProperty>();
        properties.add(new PluginProperty("key1", "value1", false));
        properties.add(new PluginProperty("key2", "value2", false));

        final byte[] serialized = PluginPropertySerializer.serialize(properties);
        final PaymentAttemptModelDao attempt = new PaymentAttemptModelDao(UUID.randomUUID(), UUID.randomUUID(), clock.getUTCNow(), clock.getUTCNow(),
                                                                          paymentExternalKey, directTransactionId, transactionExternalKey, transactionType, stateName,
                                                                          BigDecimal.ZERO, Currency.ALL, pluginName, serialized);

        PaymentAttemptModelDao savedAttempt = paymentDao.insertPaymentAttemptWithProperties(attempt, internalCallContext);
        assertEquals(savedAttempt.getTransactionExternalKey(), transactionExternalKey);
        assertEquals(savedAttempt.getTransactionType(), transactionType);
        assertEquals(savedAttempt.getStateName(), stateName);
        assertEquals(savedAttempt.getPluginName(), pluginName);

        final Iterable<PluginProperty> deserialized = PluginPropertySerializer.deserialize(savedAttempt.getPluginProperties());
        int i = 0;
        for (PluginProperty cur : deserialized) {
            Assert.assertEquals(cur, properties.get(i++));
        }

        final PaymentAttemptModelDao retrievedAttempt1 = paymentDao.getPaymentAttempt(attempt.getId(), internalCallContext);
        assertEquals(retrievedAttempt1.getTransactionExternalKey(), transactionExternalKey);
        assertEquals(retrievedAttempt1.getTransactionType(), transactionType);
        assertEquals(retrievedAttempt1.getStateName(), stateName);
        assertEquals(retrievedAttempt1.getPluginName(), pluginName);

        final List<PaymentAttemptModelDao> retrievedAttempts = paymentDao.getPaymentAttemptByTransactionExternalKey(transactionExternalKey, internalCallContext);
        assertEquals(retrievedAttempts.size(), 1);
        assertEquals(retrievedAttempts.get(0).getTransactionExternalKey(), transactionExternalKey);
        assertEquals(retrievedAttempts.get(0).getTransactionType(), transactionType);
        assertEquals(retrievedAttempts.get(0).getStateName(), stateName);
        assertEquals(retrievedAttempts.get(0).getPluginName(), pluginName);
    }

    @Test(groups = "slow")
    public void testPaymentAndTransactions() {

        final UUID paymentMethodId = UUID.randomUUID();
        final UUID accountId = UUID.randomUUID();
        final String externalKey = "hhhhooo";
        final String transactionExternalKey = "grrrrrr";
        final String transactionExternalKey2 = "hahahaha";

        final DateTime utcNow = clock.getUTCNow();

        final PaymentModelDao paymentModelDao = new PaymentModelDao(utcNow, utcNow, accountId, paymentMethodId, externalKey);
        final PaymentTransactionModelDao transactionModelDao = new PaymentTransactionModelDao(utcNow, utcNow, transactionExternalKey,
                                                                                              paymentModelDao.getId(), TransactionType.AUTHORIZE, utcNow,
                                                                                              TransactionStatus.SUCCESS, BigDecimal.TEN, Currency.AED,
                                                                                              "success", "");

        final PaymentModelDao savedPayment = paymentDao.insertDirectPaymentWithFirstTransaction(paymentModelDao, transactionModelDao, internalCallContext);
        assertEquals(savedPayment.getId(), paymentModelDao.getId());
        assertEquals(savedPayment.getAccountId(), paymentModelDao.getAccountId());
        assertEquals(savedPayment.getExternalKey(), paymentModelDao.getExternalKey());
        assertEquals(savedPayment.getPaymentMethodId(), paymentModelDao.getPaymentMethodId());
        assertNull(savedPayment.getStateName());

        final PaymentModelDao savedPayment2 = paymentDao.getDirectPayment(savedPayment.getId(), internalCallContext);
        assertEquals(savedPayment2.getId(), paymentModelDao.getId());
        assertEquals(savedPayment2.getAccountId(), paymentModelDao.getAccountId());
        assertEquals(savedPayment2.getExternalKey(), paymentModelDao.getExternalKey());
        assertEquals(savedPayment2.getPaymentMethodId(), paymentModelDao.getPaymentMethodId());
        assertNull(savedPayment2.getStateName());

        final PaymentModelDao savedPayment3 = paymentDao.getDirectPaymentByExternalKey(externalKey, internalCallContext);
        assertEquals(savedPayment3.getId(), paymentModelDao.getId());
        assertEquals(savedPayment3.getAccountId(), paymentModelDao.getAccountId());
        assertEquals(savedPayment3.getExternalKey(), paymentModelDao.getExternalKey());
        assertEquals(savedPayment3.getPaymentMethodId(), paymentModelDao.getPaymentMethodId());
        assertNull(savedPayment3.getStateName());

        final PaymentTransactionModelDao savedTransaction = paymentDao.getDirectPaymentTransaction(transactionModelDao.getId(), internalCallContext);
        assertEquals(savedTransaction.getTransactionExternalKey(), transactionExternalKey);
        assertEquals(savedTransaction.getPaymentId(), paymentModelDao.getId());
        assertEquals(savedTransaction.getTransactionType(), TransactionType.AUTHORIZE);
        assertEquals(savedTransaction.getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(savedTransaction.getAmount().compareTo(BigDecimal.TEN), 0);
        assertEquals(savedTransaction.getCurrency(), Currency.AED);

        final List<PaymentTransactionModelDao> savedTransactions = paymentDao.getDirectPaymentTransactionsByExternalKey(transactionExternalKey, internalCallContext);
        assertEquals(savedTransactions.size(), 1);
        final PaymentTransactionModelDao savedTransaction2 = savedTransactions.get(0);
        assertEquals(savedTransaction2.getTransactionExternalKey(), transactionExternalKey);
        assertEquals(savedTransaction2.getPaymentId(), paymentModelDao.getId());
        assertEquals(savedTransaction2.getTransactionType(), TransactionType.AUTHORIZE);
        assertEquals(savedTransaction2.getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(savedTransaction2.getAmount().compareTo(BigDecimal.TEN), 0);
        assertEquals(savedTransaction2.getCurrency(), Currency.AED);

        final PaymentTransactionModelDao transactionModelDao2 = new PaymentTransactionModelDao(utcNow, utcNow, transactionExternalKey2,
                                                                                               paymentModelDao.getId(), TransactionType.AUTHORIZE, utcNow,
                                                                                               TransactionStatus.UNKNOWN, BigDecimal.TEN, Currency.AED,
                                                                                               "success", "");

        final PaymentTransactionModelDao savedTransactionModelDao2 = paymentDao.updateDirectPaymentWithNewTransaction(savedPayment.getId(), transactionModelDao2, internalCallContext);
        assertEquals(savedTransactionModelDao2.getTransactionExternalKey(), transactionExternalKey2);
        assertEquals(savedTransactionModelDao2.getPaymentId(), paymentModelDao.getId());
        assertEquals(savedTransactionModelDao2.getTransactionType(), TransactionType.AUTHORIZE);
        assertEquals(savedTransactionModelDao2.getTransactionStatus(), TransactionStatus.UNKNOWN);
        assertEquals(savedTransactionModelDao2.getAmount().compareTo(BigDecimal.TEN), 0);
        assertEquals(savedTransactionModelDao2.getCurrency(), Currency.AED);

        final List<PaymentTransactionModelDao> transactions = paymentDao.getDirectTransactionsForDirectPayment(savedPayment.getId(), internalCallContext);
        assertEquals(transactions.size(), 2);

        paymentDao.updateDirectPaymentAndTransactionOnCompletion(savedPayment.getId(), "AUTH_ABORTED", "AUTH_SUCCESS", transactionModelDao2.getId(), TransactionStatus.SUCCESS,
                                                                 BigDecimal.ONE, Currency.USD, null, "nothing", internalCallContext);

        final PaymentModelDao savedPayment4 = paymentDao.getDirectPayment(savedPayment.getId(), internalCallContext);
        assertEquals(savedPayment4.getId(), paymentModelDao.getId());
        assertEquals(savedPayment4.getAccountId(), paymentModelDao.getAccountId());
        assertEquals(savedPayment4.getExternalKey(), paymentModelDao.getExternalKey());
        assertEquals(savedPayment4.getPaymentMethodId(), paymentModelDao.getPaymentMethodId());
        assertEquals(savedPayment4.getStateName(), "AUTH_ABORTED");
        assertEquals(savedPayment4.getLastSuccessStateName(), "AUTH_SUCCESS");

        final PaymentTransactionModelDao savedTransactionModelDao4 = paymentDao.getDirectPaymentTransaction(savedTransactionModelDao2.getId(), internalCallContext);
        assertEquals(savedTransactionModelDao4.getTransactionExternalKey(), transactionExternalKey2);
        assertEquals(savedTransactionModelDao4.getPaymentId(), paymentModelDao.getId());
        assertEquals(savedTransactionModelDao4.getTransactionType(), TransactionType.AUTHORIZE);
        assertEquals(savedTransactionModelDao4.getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(savedTransactionModelDao4.getAmount().compareTo(BigDecimal.TEN), 0);
        assertEquals(savedTransactionModelDao4.getCurrency(), Currency.AED);
        assertEquals(savedTransactionModelDao4.getProcessedAmount().compareTo(BigDecimal.ONE), 0);
        assertEquals(savedTransactionModelDao4.getProcessedCurrency(), Currency.USD);
        assertNull(savedTransactionModelDao4.getGatewayErrorCode());
        assertEquals(savedTransactionModelDao4.getGatewayErrorMsg(), "nothing");

        paymentDao.updateDirectPaymentAndTransactionOnCompletion(savedPayment.getId(), "AUTH_ABORTED", null, transactionModelDao2.getId(), TransactionStatus.SUCCESS,
                                                                 BigDecimal.ONE, Currency.USD, null, "nothing", internalCallContext);

        final PaymentModelDao savedPayment4Again = paymentDao.getDirectPayment(savedPayment.getId(), internalCallContext);
        assertEquals(savedPayment4Again.getId(), paymentModelDao.getId());
        assertEquals(savedPayment4Again.getStateName(), "AUTH_ABORTED");
        assertEquals(savedPayment4Again.getLastSuccessStateName(), "AUTH_SUCCESS");

        paymentDao.updateDirectPaymentAndTransactionOnCompletion(savedPayment.getId(), "AUTH_ABORTED", "AUTH_SUCCESS", transactionModelDao2.getId(), TransactionStatus.SUCCESS,
                                                                 BigDecimal.ONE, Currency.USD, null, "nothing", internalCallContext);

        final PaymentModelDao savedPayment4Final = paymentDao.getDirectPayment(savedPayment.getId(), internalCallContext);
        assertEquals(savedPayment4Final.getId(), paymentModelDao.getId());
        assertEquals(savedPayment4Final.getStateName(), "AUTH_ABORTED");
        assertEquals(savedPayment4Final.getLastSuccessStateName(), "AUTH_SUCCESS");

        final List<PaymentModelDao> payments = paymentDao.getDirectPaymentsForAccount(accountId, internalCallContext);
        assertEquals(payments.size(), 1);

        final List<PaymentTransactionModelDao> transactions2 = paymentDao.getDirectTransactionsForAccount(accountId, internalCallContext);
        assertEquals(transactions2.size(), 2);
    }

    @Test(groups = "slow")
    public void testPaymentMethod() {

        final UUID paymentMethodId = UUID.randomUUID();
        final UUID accountId = UUID.randomUUID();
        final String pluginName = "nobody";
        final Boolean isActive = Boolean.TRUE;

        final PaymentMethodModelDao method = new PaymentMethodModelDao(paymentMethodId, UUID.randomUUID().toString(), null, null,
                                                                       accountId, pluginName, isActive);

        PaymentMethodModelDao savedMethod = paymentDao.insertPaymentMethod(method, internalCallContext);
        assertEquals(savedMethod.getId(), paymentMethodId);
        assertEquals(savedMethod.getAccountId(), accountId);
        assertEquals(savedMethod.getPluginName(), pluginName);
        assertEquals(savedMethod.isActive(), isActive);

        final List<PaymentMethodModelDao> result = paymentDao.getPaymentMethods(accountId, internalCallContext);
        assertEquals(result.size(), 1);
        savedMethod = result.get(0);
        assertEquals(savedMethod.getId(), paymentMethodId);
        assertEquals(savedMethod.getAccountId(), accountId);
        assertEquals(savedMethod.getPluginName(), pluginName);
        assertEquals(savedMethod.isActive(), isActive);

        paymentDao.deletedPaymentMethod(paymentMethodId, internalCallContext);

        PaymentMethodModelDao deletedPaymentMethod = paymentDao.getPaymentMethod(paymentMethodId, internalCallContext);
        assertNull(deletedPaymentMethod);

        deletedPaymentMethod = paymentDao.getPaymentMethodIncludedDeleted(paymentMethodId, internalCallContext);
        assertNotNull(deletedPaymentMethod);
        assertFalse(deletedPaymentMethod.isActive());
        assertEquals(deletedPaymentMethod.getAccountId(), accountId);
        assertEquals(deletedPaymentMethod.getId(), paymentMethodId);
        assertEquals(deletedPaymentMethod.getPluginName(), pluginName);
    }

    @Test(groups = "slow")
    public void testPendingTransactions() {

        final UUID paymentMethodId = UUID.randomUUID();
        final UUID accountId = UUID.randomUUID();
        final String externalKey = "hhhhooo";
        final String transactionExternalKey1 = "transaction1";
        final String transactionExternalKey2 = "transaction2";
        final String transactionExternalKey3 = "transaction3";
        final String transactionExternalKey4 = "transaction4";

        final DateTime initialTime = clock.getUTCNow();

        final PaymentModelDao paymentModelDao = new PaymentModelDao(initialTime, initialTime, accountId, paymentMethodId, externalKey);
        final PaymentTransactionModelDao transaction1 = new PaymentTransactionModelDao(initialTime, initialTime, transactionExternalKey1,
                                                                                       paymentModelDao.getId(), TransactionType.AUTHORIZE, initialTime,
                                                                                       TransactionStatus.PENDING, BigDecimal.TEN, Currency.AED,
                                                                                       "pending", "");

        paymentDao.insertDirectPaymentWithFirstTransaction(paymentModelDao, transaction1, internalCallContext);

        final PaymentTransactionModelDao transaction2 = new PaymentTransactionModelDao(initialTime, initialTime, transactionExternalKey2,
                                                                                       paymentModelDao.getId(), TransactionType.AUTHORIZE, initialTime,
                                                                                       TransactionStatus.PENDING, BigDecimal.TEN, Currency.AED,
                                                                                       "pending", "");
        paymentDao.updateDirectPaymentWithNewTransaction(paymentModelDao.getId(), transaction2, internalCallContext);

        final PaymentTransactionModelDao transaction3 = new PaymentTransactionModelDao(initialTime, initialTime, transactionExternalKey3,
                                                                                       paymentModelDao.getId(), TransactionType.AUTHORIZE, initialTime,
                                                                                       TransactionStatus.SUCCESS, BigDecimal.TEN, Currency.AED,
                                                                                       "success", "");

        paymentDao.updateDirectPaymentWithNewTransaction(paymentModelDao.getId(), transaction3, internalCallContext);

        clock.addDays(1);
        final DateTime newTime = clock.getUTCNow();


        final InternalCallContext internalCallContextWithNewTime = new InternalCallContext(InternalCallContextFactory.INTERNAL_TENANT_RECORD_ID, 1687L, UUID.randomUUID(),
                                                                                        UUID.randomUUID().toString(), CallOrigin.TEST,
                                                                                        UserType.TEST, "Testing", "This is a test",
                                                                                        newTime, newTime);

        final PaymentTransactionModelDao transaction4 = new PaymentTransactionModelDao(initialTime, initialTime, transactionExternalKey4,
                                                                                       paymentModelDao.getId(), TransactionType.AUTHORIZE, newTime,
                                                                                       TransactionStatus.PENDING, BigDecimal.TEN, Currency.AED,
                                                                                       "pending", "");
        paymentDao.updateDirectPaymentWithNewTransaction(paymentModelDao.getId(), transaction4, internalCallContextWithNewTime);


        final List<PaymentTransactionModelDao> result = getPendingTransactions(paymentModelDao.getId());
        Assert.assertEquals(result.size(), 3);


        paymentDao.failOldPendingTransactions(TransactionStatus.PAYMENT_FAILURE, newTime, internalCallContext);

        final List<PaymentTransactionModelDao> result2 = getPendingTransactions(paymentModelDao.getId());
        Assert.assertEquals(result2.size(), 1);

        // Just to guarantee that next clock.getUTCNow() > newTime
        try { Thread.sleep(1000); } catch (InterruptedException e) {};

        paymentDao.failOldPendingTransactions(TransactionStatus.PAYMENT_FAILURE, clock.getUTCNow(), internalCallContextWithNewTime);

        final List<PaymentTransactionModelDao> result3 = getPendingTransactions(paymentModelDao.getId());
        Assert.assertEquals(result3.size(), 0);

    }

    private List<PaymentTransactionModelDao> getPendingTransactions(final UUID paymentId) {
        final List<PaymentTransactionModelDao> total =  paymentDao.getDirectTransactionsForDirectPayment(paymentId, internalCallContext);
        return ImmutableList.copyOf(Iterables.filter(total, new Predicate<PaymentTransactionModelDao>() {
            @Override
            public boolean apply(final PaymentTransactionModelDao input) {
                return input.getTransactionStatus() == TransactionStatus.PENDING;
            }
        }));
    }
}

