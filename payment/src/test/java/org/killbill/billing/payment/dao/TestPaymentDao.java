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
        final UUID transactionId = UUID.randomUUID();
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
                                                                          paymentExternalKey, transactionId, transactionExternalKey, transactionType, stateName,
                                                                          BigDecimal.ZERO, Currency.ALL, ImmutableList.<String>of(pluginName), serialized);

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
        final PaymentTransactionModelDao transactionModelDao = new PaymentTransactionModelDao(utcNow, utcNow, null, transactionExternalKey,
                                                                                              paymentModelDao.getId(), TransactionType.AUTHORIZE, utcNow,
                                                                                              TransactionStatus.SUCCESS, BigDecimal.TEN, Currency.AED,
                                                                                              "success", "");

        final PaymentModelDao savedPayment = paymentDao.insertPaymentWithFirstTransaction(paymentModelDao, transactionModelDao, internalCallContext);
        assertEquals(savedPayment.getId(), paymentModelDao.getId());
        assertEquals(savedPayment.getAccountId(), paymentModelDao.getAccountId());
        assertEquals(savedPayment.getExternalKey(), paymentModelDao.getExternalKey());
        assertEquals(savedPayment.getPaymentMethodId(), paymentModelDao.getPaymentMethodId());
        assertNull(savedPayment.getStateName());

        final PaymentModelDao savedPayment2 = paymentDao.getPayment(savedPayment.getId(), internalCallContext);
        assertEquals(savedPayment2.getId(), paymentModelDao.getId());
        assertEquals(savedPayment2.getAccountId(), paymentModelDao.getAccountId());
        assertEquals(savedPayment2.getExternalKey(), paymentModelDao.getExternalKey());
        assertEquals(savedPayment2.getPaymentMethodId(), paymentModelDao.getPaymentMethodId());
        assertNull(savedPayment2.getStateName());

        final PaymentModelDao savedPayment3 = paymentDao.getPaymentByExternalKey(externalKey, internalCallContext);
        assertEquals(savedPayment3.getId(), paymentModelDao.getId());
        assertEquals(savedPayment3.getAccountId(), paymentModelDao.getAccountId());
        assertEquals(savedPayment3.getExternalKey(), paymentModelDao.getExternalKey());
        assertEquals(savedPayment3.getPaymentMethodId(), paymentModelDao.getPaymentMethodId());
        assertNull(savedPayment3.getStateName());

        final PaymentTransactionModelDao savedTransaction = paymentDao.getPaymentTransaction(transactionModelDao.getId(), internalCallContext);
        assertEquals(savedTransaction.getTransactionExternalKey(), transactionExternalKey);
        assertEquals(savedTransaction.getPaymentId(), paymentModelDao.getId());
        assertEquals(savedTransaction.getTransactionType(), TransactionType.AUTHORIZE);
        assertEquals(savedTransaction.getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(savedTransaction.getAmount().compareTo(BigDecimal.TEN), 0);
        assertEquals(savedTransaction.getCurrency(), Currency.AED);

        final List<PaymentTransactionModelDao> savedTransactions = paymentDao.getPaymentTransactionsByExternalKey(transactionExternalKey, internalCallContext);
        assertEquals(savedTransactions.size(), 1);
        final PaymentTransactionModelDao savedTransaction2 = savedTransactions.get(0);
        assertEquals(savedTransaction2.getTransactionExternalKey(), transactionExternalKey);
        assertEquals(savedTransaction2.getPaymentId(), paymentModelDao.getId());
        assertEquals(savedTransaction2.getTransactionType(), TransactionType.AUTHORIZE);
        assertEquals(savedTransaction2.getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(savedTransaction2.getAmount().compareTo(BigDecimal.TEN), 0);
        assertEquals(savedTransaction2.getCurrency(), Currency.AED);

        final PaymentTransactionModelDao transactionModelDao2 = new PaymentTransactionModelDao(utcNow, utcNow, null, transactionExternalKey2,
                                                                                               paymentModelDao.getId(), TransactionType.AUTHORIZE, utcNow,
                                                                                               TransactionStatus.UNKNOWN, BigDecimal.TEN, Currency.AED,
                                                                                               "success", "");

        final PaymentTransactionModelDao savedTransactionModelDao2 = paymentDao.updatePaymentWithNewTransaction(savedPayment.getId(), transactionModelDao2, internalCallContext);
        assertEquals(savedTransactionModelDao2.getTransactionExternalKey(), transactionExternalKey2);
        assertEquals(savedTransactionModelDao2.getPaymentId(), paymentModelDao.getId());
        assertEquals(savedTransactionModelDao2.getTransactionType(), TransactionType.AUTHORIZE);
        assertEquals(savedTransactionModelDao2.getTransactionStatus(), TransactionStatus.UNKNOWN);
        assertEquals(savedTransactionModelDao2.getAmount().compareTo(BigDecimal.TEN), 0);
        assertEquals(savedTransactionModelDao2.getCurrency(), Currency.AED);

        final List<PaymentTransactionModelDao> transactions = paymentDao.getTransactionsForPayment(savedPayment.getId(), internalCallContext);
        assertEquals(transactions.size(), 2);

        paymentDao.updatePaymentAndTransactionOnCompletion(accountId, savedPayment.getId(), savedTransactionModelDao2.getTransactionType(), "AUTH_ABORTED", "AUTH_SUCCESS", transactionModelDao2.getId(), TransactionStatus.SUCCESS,
                                                           BigDecimal.ONE, Currency.USD, null, "nothing", internalCallContext);

        final PaymentModelDao savedPayment4 = paymentDao.getPayment(savedPayment.getId(), internalCallContext);
        assertEquals(savedPayment4.getId(), paymentModelDao.getId());
        assertEquals(savedPayment4.getAccountId(), paymentModelDao.getAccountId());
        assertEquals(savedPayment4.getExternalKey(), paymentModelDao.getExternalKey());
        assertEquals(savedPayment4.getPaymentMethodId(), paymentModelDao.getPaymentMethodId());
        assertEquals(savedPayment4.getStateName(), "AUTH_ABORTED");
        assertEquals(savedPayment4.getLastSuccessStateName(), "AUTH_SUCCESS");

        final PaymentTransactionModelDao savedTransactionModelDao4 = paymentDao.getPaymentTransaction(savedTransactionModelDao2.getId(), internalCallContext);
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

        paymentDao.updatePaymentAndTransactionOnCompletion(accountId, savedPayment.getId(), savedTransactionModelDao2.getTransactionType(), "AUTH_ABORTED", null, transactionModelDao2.getId(), TransactionStatus.SUCCESS,
                                                           BigDecimal.ONE, Currency.USD, null, "nothing", internalCallContext);

        final PaymentModelDao savedPayment4Again = paymentDao.getPayment(savedPayment.getId(), internalCallContext);
        assertEquals(savedPayment4Again.getId(), paymentModelDao.getId());
        assertEquals(savedPayment4Again.getStateName(), "AUTH_ABORTED");
        assertEquals(savedPayment4Again.getLastSuccessStateName(), "AUTH_SUCCESS");

        paymentDao.updatePaymentAndTransactionOnCompletion(accountId, savedPayment.getId(), savedTransactionModelDao2.getTransactionType(), "AUTH_ABORTED", "AUTH_SUCCESS", transactionModelDao2.getId(), TransactionStatus.SUCCESS,
                                                           BigDecimal.ONE, Currency.USD, null, "nothing", internalCallContext);

        final PaymentModelDao savedPayment4Final = paymentDao.getPayment(savedPayment.getId(), internalCallContext);
        assertEquals(savedPayment4Final.getId(), paymentModelDao.getId());
        assertEquals(savedPayment4Final.getStateName(), "AUTH_ABORTED");
        assertEquals(savedPayment4Final.getLastSuccessStateName(), "AUTH_SUCCESS");

        final List<PaymentModelDao> payments = paymentDao.getPaymentsForAccount(accountId, internalCallContext);
        assertEquals(payments.size(), 1);

        final List<PaymentTransactionModelDao> transactions2 = paymentDao.getTransactionsForAccount(accountId, internalCallContext);
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

        final DateTime initialTime = clock.getUTCNow().minusMinutes(1);

        final PaymentModelDao paymentModelDao = new PaymentModelDao(initialTime, initialTime, accountId, paymentMethodId, externalKey);
        final PaymentTransactionModelDao transaction1 = new PaymentTransactionModelDao(initialTime, initialTime, null, transactionExternalKey1,
                                                                                       paymentModelDao.getId(), TransactionType.AUTHORIZE, initialTime,
                                                                                       TransactionStatus.PENDING, BigDecimal.TEN, Currency.AED,
                                                                                       "pending", "");

        final PaymentModelDao payment  = paymentDao.insertPaymentWithFirstTransaction(paymentModelDao, transaction1, internalCallContext);

        final PaymentTransactionModelDao transaction2 = new PaymentTransactionModelDao(initialTime, initialTime, null, transactionExternalKey2,
                                                                                       paymentModelDao.getId(), TransactionType.AUTHORIZE, initialTime,
                                                                                       TransactionStatus.PENDING, BigDecimal.TEN, Currency.AED,
                                                                                       "pending", "");
        paymentDao.updatePaymentWithNewTransaction(paymentModelDao.getId(), transaction2, internalCallContext);

        final PaymentTransactionModelDao transaction3 = new PaymentTransactionModelDao(initialTime, initialTime, null, transactionExternalKey3,
                                                                                       paymentModelDao.getId(), TransactionType.AUTHORIZE, initialTime,
                                                                                       TransactionStatus.SUCCESS, BigDecimal.TEN, Currency.AED,
                                                                                       "success", "");

        paymentDao.updatePaymentWithNewTransaction(paymentModelDao.getId(), transaction3, internalCallContext);

        clock.addDays(1);
        final DateTime newTime = clock.getUTCNow();
        final InternalCallContext internalCallContextWithNewTime = new InternalCallContext(InternalCallContextFactory.INTERNAL_TENANT_RECORD_ID, 1687L, UUID.randomUUID(),
                                                                                           UUID.randomUUID().toString(), CallOrigin.TEST,
                                                                                           UserType.TEST, "Testing", "This is a test",
                                                                                           newTime, newTime);

        final PaymentTransactionModelDao transaction4 = new PaymentTransactionModelDao(initialTime, initialTime, null, transactionExternalKey4,
                                                                                       paymentModelDao.getId(), TransactionType.AUTHORIZE, newTime,
                                                                                       TransactionStatus.PENDING, BigDecimal.TEN, Currency.AED,
                                                                                       "pending", "");
        paymentDao.updatePaymentWithNewTransaction(paymentModelDao.getId(), transaction4, internalCallContextWithNewTime);

        final List<PaymentTransactionModelDao> result = getPendingTransactions(paymentModelDao.getId());
        Assert.assertEquals(result.size(), 3);

        final List<PaymentTransactionModelDao> transactions1 = paymentDao.getByTransactionStatusAcrossTenants(ImmutableList.of(TransactionStatus.PENDING), newTime, initialTime, 3);
        for (PaymentTransactionModelDao paymentTransaction : transactions1) {
            final String newPaymentState = "XXX_FAILED";
            paymentDao.updatePaymentAndTransactionOnCompletion(payment.getAccountId(), payment.getId(), paymentTransaction.getTransactionType(), newPaymentState, payment.getLastSuccessStateName(),
                                                               paymentTransaction.getId(), TransactionStatus.PAYMENT_FAILURE, paymentTransaction.getProcessedAmount(), paymentTransaction.getProcessedCurrency(),
                                                               paymentTransaction.getGatewayErrorCode(), paymentTransaction.getGatewayErrorMsg(), internalCallContext);
        }

        final List<PaymentTransactionModelDao> result2 = getPendingTransactions(paymentModelDao.getId());
        Assert.assertEquals(result2.size(), 1);

        // Just to guarantee that next clock.getUTCNow() > newTime
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        ;

        final List<PaymentTransactionModelDao> transactions2 = paymentDao.getByTransactionStatusAcrossTenants(ImmutableList.of(TransactionStatus.PENDING), clock.getUTCNow(), initialTime, 1);
        for (PaymentTransactionModelDao paymentTransaction : transactions2) {
            final String newPaymentState = "XXX_FAILED";
            paymentDao.updatePaymentAndTransactionOnCompletion(payment.getAccountId(), payment.getId(), paymentTransaction.getTransactionType(), newPaymentState, payment.getLastSuccessStateName(),
                                                               paymentTransaction.getId(), TransactionStatus.PAYMENT_FAILURE, paymentTransaction.getProcessedAmount(), paymentTransaction.getProcessedCurrency(),
                                                               paymentTransaction.getGatewayErrorCode(), paymentTransaction.getGatewayErrorMsg(), internalCallContext);
        }

        final List<PaymentTransactionModelDao> result3 = getPendingTransactions(paymentModelDao.getId());
        Assert.assertEquals(result3.size(), 0);

    }

    @Test(groups = "slow")
    public void testPaymentByStatesAcrossTenants() {

        final UUID paymentMethodId = UUID.randomUUID();
        final UUID accountId = UUID.randomUUID();
        final String externalKey1 = "XXhhhhooo1";
        final String transactionExternalKey1 = "transactionXX1";

        final String externalKey2 = "XXhhhhooo2";
        final String transactionExternalKey2 = "transactionXX2";

        final String externalKey3 = "XXhhhhooo3";
        final String transactionExternalKey3 = "transactionXX3";

        final String externalKey4 = "XXhhhhooo4";
        final String transactionExternalKey4 = "transactionXX4";

        final String externalKey5 = "XXhhhhooo5";
        final String transactionExternalKey5 = "transactionXX5";

        final DateTime createdAfterDate = clock.getUTCNow().minusDays(10);
        final DateTime createdBeforeDate = clock.getUTCNow().minusDays(1);

        // Right before createdAfterDate, so should not be returned
        final DateTime createdDate1 = createdAfterDate.minusHours(1);
        final PaymentModelDao paymentModelDao1 = new PaymentModelDao(createdDate1, createdDate1, accountId, paymentMethodId, externalKey1);
        paymentModelDao1.setStateName("AUTH_ERRORED");
        final PaymentTransactionModelDao transaction1 = new PaymentTransactionModelDao(createdDate1, createdDate1, null, transactionExternalKey1,
                                                                                       paymentModelDao1.getId(), TransactionType.AUTHORIZE, createdDate1,
                                                                                       TransactionStatus.UNKNOWN, BigDecimal.TEN, Currency.AED,
                                                                                       "unknown", "");

        final InternalCallContext context1 = new InternalCallContext(1L,
                                                                     1L,
                                                                     internalCallContext.getUserToken(),
                                                                     internalCallContext.getCreatedBy(),
                                                                     internalCallContext.getCallOrigin(),
                                                                     internalCallContext.getContextUserType(),
                                                                     internalCallContext.getReasonCode(),
                                                                     internalCallContext.getComments(),
                                                                     createdDate1,
                                                                     createdDate1);
        paymentDao.insertPaymentWithFirstTransaction(paymentModelDao1, transaction1, context1);


        // Right after createdAfterDate, so it should  be returned
        final DateTime createdDate2 = createdAfterDate.plusHours(1);
        final PaymentModelDao paymentModelDao2 = new PaymentModelDao(createdDate2, createdDate2, accountId, paymentMethodId, externalKey2);
        paymentModelDao2.setStateName("CAPTURE_ERRORED");
        final PaymentTransactionModelDao transaction2 = new PaymentTransactionModelDao(createdDate2, createdDate2, null, transactionExternalKey2,
                                                                                       paymentModelDao2.getId(), TransactionType.AUTHORIZE, createdDate2,
                                                                                       TransactionStatus.UNKNOWN, BigDecimal.TEN, Currency.AED,
                                                                                       "unknown", "");

        final InternalCallContext context2 = new InternalCallContext(2L,
                                                                     2L,
                                                                     internalCallContext.getUserToken(),
                                                                     internalCallContext.getCreatedBy(),
                                                                     internalCallContext.getCallOrigin(),
                                                                     internalCallContext.getContextUserType(),
                                                                     internalCallContext.getReasonCode(),
                                                                     internalCallContext.getComments(),
                                                                     createdDate2,
                                                                     createdDate2);
        paymentDao.insertPaymentWithFirstTransaction(paymentModelDao2, transaction2, context2);

        // Right before createdBeforeDate, so it should be returned
        final DateTime createdDate3 = createdBeforeDate.minusDays(1);
        final PaymentModelDao paymentModelDao3 = new PaymentModelDao(createdDate3, createdDate3, accountId, paymentMethodId, externalKey3);
        paymentModelDao3.setStateName("CAPTURE_ERRORED");
        final PaymentTransactionModelDao transaction3 = new PaymentTransactionModelDao(createdDate3, createdDate3, null, transactionExternalKey3,
                                                                                       paymentModelDao3.getId(), TransactionType.AUTHORIZE, createdDate3,
                                                                                       TransactionStatus.UNKNOWN, BigDecimal.TEN, Currency.AED,
                                                                                       "unknown", "");

        final InternalCallContext context3 = new InternalCallContext(3L,
                                                                     3L,
                                                                     internalCallContext.getUserToken(),
                                                                     internalCallContext.getCreatedBy(),
                                                                     internalCallContext.getCallOrigin(),
                                                                     internalCallContext.getContextUserType(),
                                                                     internalCallContext.getReasonCode(),
                                                                     internalCallContext.getComments(),
                                                                     createdDate3,
                                                                     createdDate3);

        paymentDao.insertPaymentWithFirstTransaction(paymentModelDao3, transaction3, context3);


        // Right before createdBeforeDate but with a SUCCESS state so it should NOT be returned
        final DateTime createdDate4 = createdBeforeDate.minusDays(1);
        final PaymentModelDao paymentModelDao4 = new PaymentModelDao(createdDate4, createdDate4, accountId, paymentMethodId, externalKey4);
        paymentModelDao4.setStateName("CAPTURE_SUCCESS");
        final PaymentTransactionModelDao transaction4 = new PaymentTransactionModelDao(createdDate4, createdDate4, null, transactionExternalKey4,
                                                                                       paymentModelDao4.getId(), TransactionType.AUTHORIZE, createdDate4,
                                                                                       TransactionStatus.UNKNOWN, BigDecimal.TEN, Currency.AED,
                                                                                       "unknown", "");

        final InternalCallContext context4 = new InternalCallContext(4L,
                                                                     4L,
                                                                     internalCallContext.getUserToken(),
                                                                     internalCallContext.getCreatedBy(),
                                                                     internalCallContext.getCallOrigin(),
                                                                     internalCallContext.getContextUserType(),
                                                                     internalCallContext.getReasonCode(),
                                                                     internalCallContext.getComments(),
                                                                     createdDate4,
                                                                     createdDate4);

        paymentDao.insertPaymentWithFirstTransaction(paymentModelDao4, transaction4, context4);

        // Right after createdBeforeDate, so it should NOT be returned
        final DateTime createdDate5 = createdBeforeDate.plusDays(1);
        final PaymentModelDao paymentModelDao5 = new PaymentModelDao(createdDate5, createdDate5, accountId, paymentMethodId, externalKey5);
        paymentModelDao5.setStateName("CAPTURE_ERRORED");
        final PaymentTransactionModelDao transaction5 = new PaymentTransactionModelDao(createdDate5, createdDate5, null, transactionExternalKey5,
                                                                                       paymentModelDao5.getId(), TransactionType.AUTHORIZE, createdDate5,
                                                                                       TransactionStatus.UNKNOWN, BigDecimal.TEN, Currency.AED,
                                                                                       "unknown", "");

        final InternalCallContext context5 = new InternalCallContext(5L,
                                                                     5L,
                                                                     internalCallContext.getUserToken(),
                                                                     internalCallContext.getCreatedBy(),
                                                                     internalCallContext.getCallOrigin(),
                                                                     internalCallContext.getContextUserType(),
                                                                     internalCallContext.getReasonCode(),
                                                                     internalCallContext.getComments(),
                                                                     createdDate5,
                                                                     createdDate5);

        paymentDao.insertPaymentWithFirstTransaction(paymentModelDao5, transaction5, context5);

        final String[] errorStates = {"AUTH_ERRORED", "CAPTURE_ERRORED", "REFUND_ERRORED", "CREDIT_ERRORED"};
        final List<PaymentModelDao> result = paymentDao.getPaymentsByStatesAcrossTenants(errorStates, createdBeforeDate, createdAfterDate, 10);
        assertEquals(result.size(), 2);
    }

    @Test(groups = "slow")
    public void testPaymentAttemptsByStateAcrossTenants() {

        final UUID paymentMethodId = UUID.randomUUID();
        final UUID accountId = UUID.randomUUID();
        final String externalKey1 = "gfhfg";
        final String transactionExternalKey1 = "sadas";

        final String externalKey2 = "asdwqeqw";
        final String transactionExternalKey2 = "fghfg";

        final DateTime createdAfterDate = clock.getUTCNow().minusDays(10);
        final DateTime createdBeforeDate = clock.getUTCNow().minusDays(1);

        final String stateName = "FOO";
        final String pluginName = "miraculous";

        final PaymentAttemptModelDao attempt1 = new PaymentAttemptModelDao(accountId, paymentMethodId, createdAfterDate, createdAfterDate, externalKey1,
                                                                     UUID.randomUUID(), transactionExternalKey1, TransactionType.AUTHORIZE, stateName, BigDecimal.ONE, Currency.USD,
                                                                     ImmutableList.<String>of(pluginName), null);


        final PaymentAttemptModelDao attempt2 = new PaymentAttemptModelDao(accountId, paymentMethodId, createdAfterDate, createdAfterDate, externalKey2,
                                                                     UUID.randomUUID(), transactionExternalKey2, TransactionType.AUTHORIZE, stateName, BigDecimal.ONE, Currency.USD,
                                                                     ImmutableList.<String>of(pluginName), null);

        final InternalCallContext context1 = new InternalCallContext(1L,
                                                                     1L,
                                                                     internalCallContext.getUserToken(),
                                                                     internalCallContext.getCreatedBy(),
                                                                     internalCallContext.getCallOrigin(),
                                                                     internalCallContext.getContextUserType(),
                                                                     internalCallContext.getReasonCode(),
                                                                     internalCallContext.getComments(),
                                                                     createdAfterDate,
                                                                     createdAfterDate);
        paymentDao.insertPaymentAttemptWithProperties(attempt1, context1);


        final InternalCallContext context2 = new InternalCallContext(2L,
                                                                     2L,
                                                                     internalCallContext.getUserToken(),
                                                                     internalCallContext.getCreatedBy(),
                                                                     internalCallContext.getCallOrigin(),
                                                                     internalCallContext.getContextUserType(),
                                                                     internalCallContext.getReasonCode(),
                                                                     internalCallContext.getComments(),
                                                                     createdAfterDate,
                                                                     createdAfterDate);
        paymentDao.insertPaymentAttemptWithProperties(attempt2, context2);


        final List<PaymentAttemptModelDao> result = paymentDao.getPaymentAttemptsByStateAcrossTenants(stateName, createdBeforeDate);
        Assert.assertEquals(result.size(), 2);
    }


    private List<PaymentTransactionModelDao> getPendingTransactions(final UUID paymentId) {
        final List<PaymentTransactionModelDao> total = paymentDao.getTransactionsForPayment(paymentId, internalCallContext);
        return ImmutableList.copyOf(Iterables.filter(total, new Predicate<PaymentTransactionModelDao>() {
            @Override
            public boolean apply(final PaymentTransactionModelDao input) {
                return input.getTransactionStatus() == TransactionStatus.PENDING;
            }
        }));
    }
}

