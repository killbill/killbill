/*
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

import org.killbill.automaton.Operation.OperationCallback;
import org.killbill.automaton.OperationResult;
import org.killbill.automaton.State;
import org.killbill.automaton.State.LeavingStateCallback;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.PaymentTestSuiteWithEmbeddedDB;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.core.sm.payments.PaymentEnteringStateCallback;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.killbill.billing.payment.plugin.api.PaymentPluginStatus;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class TestPaymentEnteringStateCallback extends PaymentTestSuiteWithEmbeddedDB {

    private final State state = Mockito.mock(State.class);
    private final OperationCallback operationCallback = Mockito.mock(OperationCallback.class);
    private final LeavingStateCallback leavingStateCallback = Mockito.mock(LeavingStateCallback.class);

    private PaymentStateContext paymentStateContext;
    private PaymentAutomatonDAOHelper daoHelper;
    private PaymentEnteringStateTestCallback callback;
    private OperationResult operationResult;

    @BeforeMethod(groups = "slow")
    public void setUp() throws Exception {
        final Account account = Mockito.mock(Account.class);
        Mockito.when(account.getId()).thenReturn(UUID.randomUUID());
        paymentStateContext = new PaymentStateContext(true,
                                                      null,
                                                      UUID.randomUUID().toString(),
                                                      TransactionType.CAPTURE,
                                                      account,
                                                      UUID.randomUUID(),
                                                      new BigDecimal("192.3920111"),
                                                      Currency.BRL,
                                                      null,
                                                      false,
                                                      ImmutableList.<PluginProperty>of(),
                                                      internalCallContext,
                                                      callContext);
        daoHelper = new PaymentAutomatonDAOHelper(paymentStateContext, clock.getUTCNow(), paymentDao, paymentPluginServiceRegistration, internalCallContext, eventBus, paymentSMHelper);
        callback = new PaymentEnteringStateTestCallback(daoHelper, paymentStateContext);

        Mockito.when(state.getName()).thenReturn("NEW_STATE");
        operationResult = OperationResult.SUCCESS;
    }

    @Test(groups = "slow")
    public void testEnterStateAndProcessPaymentTransactionInfoPlugin() throws Exception {
        // Create the payment and first transaction (would be done by PaymentLeavingStateCallback)
        daoHelper.createNewPaymentTransaction();
        Assert.assertEquals(paymentDao.getPaymentTransaction(paymentStateContext.getPaymentTransactionModelDao().getId(), internalCallContext).getTransactionStatus(), TransactionStatus.UNKNOWN);

        // Mock the plugin result
        final PaymentTransactionInfoPlugin paymentInfoPlugin = Mockito.mock(PaymentTransactionInfoPlugin.class);
        Mockito.when(paymentInfoPlugin.getAmount()).thenReturn(new BigDecimal("82010.222"));
        Mockito.when(paymentInfoPlugin.getCurrency()).thenReturn(Currency.CAD);
        Mockito.when(paymentInfoPlugin.getStatus()).thenReturn(PaymentPluginStatus.PENDING);
        Mockito.when(paymentInfoPlugin.getGatewayErrorCode()).thenReturn(UUID.randomUUID().toString().substring(0, 5));
        Mockito.when(paymentInfoPlugin.getGatewayError()).thenReturn(UUID.randomUUID().toString());
        paymentStateContext.setPaymentTransactionInfoPlugin(paymentInfoPlugin);

        // Process the plugin result
        callback.enteringState(state, operationCallback, operationResult, leavingStateCallback);

        // Verify the updated transaction
        final PaymentTransactionModelDao paymentTransaction = paymentDao.getPaymentTransaction(paymentStateContext.getPaymentTransactionModelDao().getId(), internalCallContext);
        Assert.assertEquals(paymentTransaction.getAmount().compareTo(paymentStateContext.getAmount()), 0);
        Assert.assertEquals(paymentTransaction.getCurrency(), paymentStateContext.getCurrency());
        Assert.assertEquals(paymentTransaction.getProcessedAmount().compareTo(paymentInfoPlugin.getAmount()), 0);
        Assert.assertEquals(paymentTransaction.getProcessedCurrency(), paymentInfoPlugin.getCurrency());
        Assert.assertEquals(paymentTransaction.getTransactionStatus(), TransactionStatus.PENDING);
        Assert.assertEquals(paymentTransaction.getGatewayErrorCode(), paymentInfoPlugin.getGatewayErrorCode());
        Assert.assertEquals(paymentTransaction.getGatewayErrorMsg(), paymentInfoPlugin.getGatewayError());
    }

    private static final class PaymentEnteringStateTestCallback extends PaymentEnteringStateCallback {

        private PaymentEnteringStateTestCallback(final PaymentAutomatonDAOHelper daoHelper, final PaymentStateContext paymentStateContext) throws PaymentApiException {
            super(daoHelper, paymentStateContext);
        }
    }
}
