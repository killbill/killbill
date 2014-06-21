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

package org.killbill.billing.payment.core.sm;

import java.math.BigDecimal;
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
import org.killbill.billing.payment.dao.PaymentModelDao;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class TestDirectPaymentLeavingStateCallback extends PaymentTestSuiteWithEmbeddedDB {

    private final State state = Mockito.mock(State.class);

    private DirectPaymentStateContext directPaymentStateContext;
    private DirectPaymentLeavingStateTestCallback callback;

    @Test(groups = "slow")
    public void testLeaveStateForNewPayment() throws Exception {
        setUp(null);

        callback.leavingState(state);

        // Verify a new transaction was created
        verifyDirectPaymentTransaction();

        // Verify a new payment was created
        final PaymentModelDao directPayment = paymentDao.getDirectPayment(directPaymentStateContext.getDirectPaymentTransactionModelDao().getPaymentId(), internalCallContext);
        Assert.assertEquals(directPayment.getExternalKey(), directPaymentStateContext.getDirectPaymentExternalKey());
        Assert.assertNull(directPayment.getStateName());

        // Verify the direct payment has only one transaction
        Assert.assertEquals(paymentDao.getDirectTransactionsForDirectPayment(directPayment.getId(), internalCallContext).size(), 1);
    }

    @Test(groups = "slow")
    public void testLeaveStateForExistingPayment() throws Exception {
        final UUID directPaymentId = UUID.randomUUID();
        setUp(directPaymentId);

        // Verify the direct payment has only one transaction
        Assert.assertEquals(paymentDao.getDirectTransactionsForDirectPayment(directPaymentId, internalCallContext).size(), 1);

        callback.leavingState(state);

        // Verify a new transaction was created
        verifyDirectPaymentTransaction();

        // Verify the direct payment has now two transactions
        Assert.assertEquals(paymentDao.getDirectTransactionsForDirectPayment(directPaymentId, internalCallContext).size(), 2);
    }

    private void verifyDirectPaymentTransaction() {
        Assert.assertNotNull(directPaymentStateContext.getDirectPaymentTransactionModelDao().getPaymentId());
        Assert.assertEquals(directPaymentStateContext.getDirectPaymentTransactionModelDao().getTransactionExternalKey(), directPaymentStateContext.getDirectPaymentTransactionExternalKey());
        Assert.assertEquals(directPaymentStateContext.getDirectPaymentTransactionModelDao().getTransactionStatus(), TransactionStatus.UNKNOWN);
        Assert.assertEquals(directPaymentStateContext.getDirectPaymentTransactionModelDao().getAmount().compareTo(directPaymentStateContext.getAmount()), 0);
        Assert.assertEquals(directPaymentStateContext.getDirectPaymentTransactionModelDao().getCurrency(), directPaymentStateContext.getCurrency());
        Assert.assertNull(directPaymentStateContext.getDirectPaymentTransactionModelDao().getProcessedAmount());
        Assert.assertNull(directPaymentStateContext.getDirectPaymentTransactionModelDao().getProcessedCurrency());
        Assert.assertNull(directPaymentStateContext.getDirectPaymentTransactionModelDao().getGatewayErrorCode());
        Assert.assertNull(directPaymentStateContext.getDirectPaymentTransactionModelDao().getGatewayErrorMsg());
    }

    private void setUp(@Nullable final UUID directPaymentId) throws Exception {
        final Account account = Mockito.mock(Account.class);
        Mockito.when(account.getId()).thenReturn(UUID.randomUUID());
        directPaymentStateContext = new DirectPaymentStateContext(directPaymentId,
                                                                  UUID.randomUUID().toString(),
                                                                  UUID.randomUUID().toString(),
                                                                  TransactionType.CAPTURE,
                                                                  account,
                                                                  UUID.randomUUID(),
                                                                  new BigDecimal("192.3920111"),
                                                                  Currency.BRL,
                                                                  false,
                                                                  ImmutableList.<PluginProperty>of(),
                                                                  internalCallContext,
                                                                  callContext);

        if (directPaymentId != null) {
            // Create the first payment manually
            final PaymentModelDao newPaymentModelDao = new PaymentModelDao(directPaymentId,
                                                                                       clock.getUTCNow(),
                                                                                       clock.getUTCNow(),
                                                                                       directPaymentStateContext.getAccount().getId(),
                                                                                       directPaymentStateContext.getPaymentMethodId(),
                                                                                       1,
                                                                                       directPaymentStateContext.getDirectPaymentExternalKey(),
                                                                                       null, null);
            final PaymentTransactionModelDao newPaymentTransactionModelDao = new PaymentTransactionModelDao(clock.getUTCNow(),
                                                                                                                        clock.getUTCNow(),
                                                                                                                        directPaymentStateContext.getDirectPaymentTransactionExternalKey(),
                                                                                                                        directPaymentId,
                                                                                                                        directPaymentStateContext.getTransactionType(),
                                                                                                                        clock.getUTCNow(),
                                                                                                                        PaymentStatus.UNKNOWN,
                                                                                                                        directPaymentStateContext.getAmount(),
                                                                                                                        directPaymentStateContext.getCurrency(),
                                                                                                                        null,
                                                                                                                        null);
            paymentDao.insertDirectPaymentWithFirstTransaction(newPaymentModelDao, newPaymentTransactionModelDao, internalCallContext);
        }

        final DirectPaymentAutomatonDAOHelper daoHelper = new DirectPaymentAutomatonDAOHelper(directPaymentStateContext, clock.getUTCNow(), paymentDao, registry, internalCallContext);
        callback = new DirectPaymentLeavingStateTestCallback(daoHelper);

        Mockito.when(state.getName()).thenReturn("NEW_STATE");
    }

    private static final class DirectPaymentLeavingStateTestCallback extends DirectPaymentLeavingStateCallback {

        private DirectPaymentLeavingStateTestCallback(final DirectPaymentAutomatonDAOHelper daoHelper) throws PaymentApiException {
            super(daoHelper);
        }
    }
}
