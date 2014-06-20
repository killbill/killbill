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

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.PaymentTestSuiteWithEmbeddedDB;
import org.killbill.billing.payment.api.PaymentStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

public class TestPaymentDao extends PaymentTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testPaymentAttempt() {
        final UUID paymentId = UUID.randomUUID();
        final UUID directTransactionId = UUID.randomUUID();
        final String paymentExternalKey = "vraiment?";
        final String transactionExternalKey = "tduteuqweq";
        final String stateName = "INIT";
        final String operationName = "AUTHORIZE";
        final String pluginName = "superPlugin";

        final UUID accountId = UUID.randomUUID();
        final PluginPropertyModelDao prop1 = new PluginPropertyModelDao("foo", transactionExternalKey, accountId, "PLUGIN", "key1", "value1", "yo", clock.getUTCNow());
        final PluginPropertyModelDao prop2 = new PluginPropertyModelDao("foo2", transactionExternalKey, accountId, "PLUGIN", "key2", "value2", "yo", clock.getUTCNow());
        final PluginPropertyModelDao prop3 = new PluginPropertyModelDao("foo3", "other", UUID.randomUUID(), "PLUGIN", "key2", "value2", "yo", clock.getUTCNow());
        final List<PluginPropertyModelDao> props = new ArrayList<PluginPropertyModelDao>();
        props.add(prop1);
        props.add(prop2);
        props.add(prop3);

        final PaymentAttemptModelDao attempt = new PaymentAttemptModelDao(clock.getUTCNow(), clock.getUTCNow(), paymentExternalKey, directTransactionId, transactionExternalKey, stateName, operationName, pluginName);
        PaymentAttemptModelDao savedAttempt = paymentDao.insertPaymentAttemptWithProperties(attempt, props, internalCallContext);
        assertEquals(savedAttempt.getTransactionExternalKey(), transactionExternalKey);
        assertEquals(savedAttempt.getOperationName(), operationName);
        assertEquals(savedAttempt.getStateName(), stateName);
        assertEquals(savedAttempt.getPluginName(), pluginName);

        final List<PluginPropertyModelDao> retrievedProperties = paymentDao.getProperties(transactionExternalKey, internalCallContext);
        assertEquals(retrievedProperties.size(), 2);
        assertEquals(retrievedProperties.get(0).getAccountId(), accountId);
        assertEquals(retrievedProperties.get(0).getTransactionExternalKey(), transactionExternalKey);
        assertEquals(retrievedProperties.get(0).getPluginName(), "PLUGIN");
        assertEquals(retrievedProperties.get(0).getPaymentExternalKey(), "foo");
        assertEquals(retrievedProperties.get(0).getPropKey(), "key1");
        assertEquals(retrievedProperties.get(0).getPropValue(), "value1");
        assertEquals(retrievedProperties.get(0).getCreatedBy(), "yo");

        assertEquals(retrievedProperties.get(1).getAccountId(), accountId);
        assertEquals(retrievedProperties.get(1).getTransactionExternalKey(), transactionExternalKey);
        assertEquals(retrievedProperties.get(1).getPluginName(), "PLUGIN");
        assertEquals(retrievedProperties.get(1).getPaymentExternalKey(), "foo2");
        assertEquals(retrievedProperties.get(1).getPropKey(), "key2");
        assertEquals(retrievedProperties.get(1).getPropValue(), "value2");
        assertEquals(retrievedProperties.get(1).getCreatedBy(), "yo");

        final PaymentAttemptModelDao retrievedAttempt1 = paymentDao.getPaymentAttempt(attempt.getId(), internalCallContext);
        assertEquals(retrievedAttempt1.getTransactionExternalKey(), transactionExternalKey);
        assertEquals(retrievedAttempt1.getOperationName(), operationName);
        assertEquals(retrievedAttempt1.getStateName(), stateName);
        assertEquals(retrievedAttempt1.getPluginName(), pluginName);

        final PaymentAttemptModelDao retrievedAttempt2 = paymentDao.getPaymentAttemptByExternalKey(transactionExternalKey, internalCallContext);
        assertEquals(retrievedAttempt2.getTransactionExternalKey(), transactionExternalKey);
        assertEquals(retrievedAttempt2.getOperationName(), operationName);
        assertEquals(retrievedAttempt2.getStateName(), stateName);
        assertEquals(retrievedAttempt2.getPluginName(), pluginName);
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
                                                                                                          PaymentStatus.SUCCESS, BigDecimal.TEN, Currency.AED,
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
        assertEquals(savedTransaction.getPaymentStatus(), PaymentStatus.SUCCESS);
        assertEquals(savedTransaction.getAmount().compareTo(BigDecimal.TEN), 0);
        assertEquals(savedTransaction.getCurrency(), Currency.AED);

        final PaymentTransactionModelDao savedTransaction2 = paymentDao.getDirectPaymentTransactionByExternalKey(transactionExternalKey, internalCallContext);
        assertEquals(savedTransaction2.getTransactionExternalKey(), transactionExternalKey);
        assertEquals(savedTransaction2.getPaymentId(), paymentModelDao.getId());
        assertEquals(savedTransaction2.getTransactionType(), TransactionType.AUTHORIZE);
        assertEquals(savedTransaction2.getPaymentStatus(), PaymentStatus.SUCCESS);
        assertEquals(savedTransaction2.getAmount().compareTo(BigDecimal.TEN), 0);
        assertEquals(savedTransaction2.getCurrency(), Currency.AED);

        final PaymentTransactionModelDao transactionModelDao2 = new PaymentTransactionModelDao(utcNow, utcNow, transactionExternalKey2,
                                                                                                           paymentModelDao.getId(), TransactionType.AUTHORIZE, utcNow,
                                                                                                           PaymentStatus.UNKNOWN, BigDecimal.TEN, Currency.AED,
                                                                                                           "success", "");

        final PaymentTransactionModelDao savedTransactionModelDao2 = paymentDao.updateDirectPaymentWithNewTransaction(savedPayment.getId(), transactionModelDao2, internalCallContext);
        assertEquals(savedTransactionModelDao2.getTransactionExternalKey(), transactionExternalKey2);
        assertEquals(savedTransactionModelDao2.getPaymentId(), paymentModelDao.getId());
        assertEquals(savedTransactionModelDao2.getTransactionType(), TransactionType.AUTHORIZE);
        assertEquals(savedTransactionModelDao2.getPaymentStatus(), PaymentStatus.UNKNOWN);
        assertEquals(savedTransactionModelDao2.getAmount().compareTo(BigDecimal.TEN), 0);
        assertEquals(savedTransactionModelDao2.getCurrency(), Currency.AED);

        final List<PaymentTransactionModelDao> transactions = paymentDao.getDirectTransactionsForDirectPayment(savedPayment.getId(), internalCallContext);
        assertEquals(transactions.size(), 2);

        paymentDao.updateDirectPaymentAndTransactionOnCompletion(savedPayment.getId(), "AUTH_SUCCESS", transactionModelDao2.getId(), PaymentStatus.SUCCESS,
                                                                 BigDecimal.ONE, Currency.USD, null, "nothing", internalCallContext);

        final PaymentModelDao savedPayment4 = paymentDao.getDirectPayment(savedPayment.getId(), internalCallContext);
        assertEquals(savedPayment4.getId(), paymentModelDao.getId());
        assertEquals(savedPayment4.getAccountId(), paymentModelDao.getAccountId());
        assertEquals(savedPayment4.getExternalKey(), paymentModelDao.getExternalKey());
        assertEquals(savedPayment4.getPaymentMethodId(), paymentModelDao.getPaymentMethodId());
        assertEquals(savedPayment4.getStateName(), "AUTH_SUCCESS");

        final PaymentTransactionModelDao savedTransactionModelDao4 = paymentDao.getDirectPaymentTransaction(savedTransactionModelDao2.getId(), internalCallContext);
        assertEquals(savedTransactionModelDao4.getTransactionExternalKey(), transactionExternalKey2);
        assertEquals(savedTransactionModelDao4.getPaymentId(), paymentModelDao.getId());
        assertEquals(savedTransactionModelDao4.getTransactionType(), TransactionType.AUTHORIZE);
        assertEquals(savedTransactionModelDao4.getPaymentStatus(), PaymentStatus.SUCCESS);
        assertEquals(savedTransactionModelDao4.getAmount().compareTo(BigDecimal.TEN), 0);
        assertEquals(savedTransactionModelDao4.getCurrency(), Currency.AED);
        assertEquals(savedTransactionModelDao4.getProcessedAmount().compareTo(BigDecimal.ONE), 0);
        assertEquals(savedTransactionModelDao4.getProcessedCurrency(), Currency.USD);
        assertNull(savedTransactionModelDao4.getGatewayErrorCode());
        assertEquals(savedTransactionModelDao4.getGatewayErrorMsg(), "nothing");
        assertNull(savedTransactionModelDao4.getExtFirstPaymentRefId());
        assertNull(savedTransactionModelDao4.getExtSecondPaymentRefId());

        final List<PaymentModelDao> payments = paymentDao.getDirectPaymentsForAccount(accountId, internalCallContext);
        assertEquals(payments.size(), 1);

        final List<PaymentTransactionModelDao> transactions2 =paymentDao.getDirectTransactionsForAccount(accountId, internalCallContext);
        assertEquals(transactions2.size(), 2);
    }

    @Test(groups = "slow")
    public void testPaymentMethod() {

        final UUID paymentMethodId = UUID.randomUUID();
        final UUID accountId = UUID.randomUUID();
        final String pluginName = "nobody";
        final Boolean isActive = Boolean.TRUE;

        final PaymentMethodModelDao method = new PaymentMethodModelDao(paymentMethodId, null, null,
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
}
