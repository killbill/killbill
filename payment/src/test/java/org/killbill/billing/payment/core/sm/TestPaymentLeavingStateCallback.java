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

package org.killbill.billing.payment.core.sm;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.killbill.automaton.State;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.PaymentTestSuiteWithEmbeddedDB;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.core.sm.payments.PaymentLeavingStateCallback;
import org.killbill.billing.payment.dao.PaymentModelDao;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class TestPaymentLeavingStateCallback extends PaymentTestSuiteWithEmbeddedDB {

    private final State state = Mockito.mock(State.class);

    private PaymentStateContext paymentStateContext;
    private PaymentLeavingStateTestCallback callback;
    private Account account;

    @Test(groups = "slow")
    public void testLeaveStateForNewPayment() throws Exception {
        setUp(null);

        callback.leavingState(state);

        // Verify a new transaction was created
        verifyPaymentTransaction();

        // Verify a new payment was created
        final PaymentModelDao payment = paymentDao.getPayment(paymentStateContext.getPaymentTransactionModelDao().getPaymentId(), internalCallContext);
        Assert.assertEquals(payment.getExternalKey(), paymentStateContext.getPaymentExternalKey());
        Assert.assertNull(payment.getStateName());

        // Verify the payment has only one transaction
        Assert.assertEquals(paymentDao.getTransactionsForPayment(payment.getId(), internalCallContext).size(), 1);
    }

    @Test(groups = "slow")
    public void testLeaveStateForExistingPayment() throws Exception {
        final UUID paymentId = UUID.randomUUID();
        setUp(paymentId);

        // Verify the payment has only one transaction
        final List<PaymentTransactionModelDao> transactions = paymentDao.getTransactionsForPayment(paymentId, internalCallContext);
        Assert.assertEquals(transactions.size(), 1);

        // Reset paymentExternalKey to something else
        paymentStateContext.setPaymentTransactionExternalKey(UUID.randomUUID().toString());
        callback.leavingState(state);

        // Verify a new transaction was created
        verifyPaymentTransaction();

        // Verify the payment has now two transactions
        Assert.assertEquals(paymentDao.getTransactionsForPayment(paymentId, internalCallContext).size(), 2);
    }

    private void verifyPaymentTransaction() {
        Assert.assertNotNull(paymentStateContext.getPaymentTransactionModelDao().getPaymentId());
        Assert.assertEquals(paymentStateContext.getPaymentTransactionModelDao().getTransactionExternalKey(), paymentStateContext.getPaymentTransactionExternalKey());
        Assert.assertEquals(paymentStateContext.getPaymentTransactionModelDao().getTransactionStatus(), TransactionStatus.UNKNOWN);
        Assert.assertEquals(paymentStateContext.getPaymentTransactionModelDao().getAmount().compareTo(paymentStateContext.getAmount()), 0);
        Assert.assertEquals(paymentStateContext.getPaymentTransactionModelDao().getCurrency(), paymentStateContext.getCurrency());
        Assert.assertNull(paymentStateContext.getPaymentTransactionModelDao().getProcessedAmount());
        Assert.assertNull(paymentStateContext.getPaymentTransactionModelDao().getProcessedCurrency());
        Assert.assertNull(paymentStateContext.getPaymentTransactionModelDao().getGatewayErrorCode());
        Assert.assertNull(paymentStateContext.getPaymentTransactionModelDao().getGatewayErrorMsg());
    }

    private void setUp(@Nullable final UUID paymentId) throws Exception {
        account = Mockito.mock(Account.class);
        Mockito.when(account.getId()).thenReturn(UUID.randomUUID());
        paymentStateContext = new PaymentStateContext(true,
                                                      paymentId,
                                                      null,
                                                      null,
                                                      UUID.randomUUID().toString(),
                                                      UUID.randomUUID().toString(),
                                                      TransactionType.CAPTURE,
                                                      account,
                                                      UUID.randomUUID(),
                                                      new BigDecimal("192.3920111"),
                                                      Currency.BRL,
                                                      null,
                                                      null,
                                                      null,
                                                      false,
                                                      null, ImmutableList.<PluginProperty>of(),
                                                      internalCallContext,
                                                      callContext);

        if (paymentId != null) {
            // Create the first payment manually
            final PaymentModelDao newPaymentModelDao = new PaymentModelDao(paymentId,
                                                                           clock.getUTCNow(),
                                                                           clock.getUTCNow(),
                                                                           paymentStateContext.getAccount().getId(),
                                                                           paymentStateContext.getPaymentMethodId(),
                                                                           1,
                                                                           paymentStateContext.getPaymentExternalKey()
            );
            final PaymentTransactionModelDao newPaymentTransactionModelDao = new PaymentTransactionModelDao(clock.getUTCNow(),
                                                                                                            clock.getUTCNow(),
                                                                                                            null,
                                                                                                            paymentStateContext.getPaymentTransactionExternalKey(),
                                                                                                            paymentId,
                                                                                                            paymentStateContext.getTransactionType(),
                                                                                                            clock.getUTCNow(),
                                                                                                            TransactionStatus.UNKNOWN,
                                                                                                            paymentStateContext.getAmount(),
                                                                                                            paymentStateContext.getCurrency(),
                                                                                                            null,
                                                                                                            null);
            paymentDao.insertPaymentWithFirstTransaction(newPaymentModelDao, newPaymentTransactionModelDao, internalCallContext);
        }

        final PaymentAutomatonDAOHelper daoHelper = new PaymentAutomatonDAOHelper(paymentStateContext, clock.getUTCNow(), paymentDao, paymentPluginServiceRegistration, internalCallContext, eventBus, paymentSMHelper);
        callback = new PaymentLeavingStateTestCallback(daoHelper, paymentStateContext);

        Mockito.when(state.getName()).thenReturn("NEW_STATE");
    }

    private static final class PaymentLeavingStateTestCallback extends PaymentLeavingStateCallback {

        private PaymentLeavingStateTestCallback(final PaymentAutomatonDAOHelper daoHelper, final PaymentStateContext paymentStateContext) throws PaymentApiException {
            super(daoHelper, paymentStateContext);
        }
    }
}
