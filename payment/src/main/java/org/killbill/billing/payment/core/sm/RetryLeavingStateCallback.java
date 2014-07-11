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

package org.killbill.billing.payment.core.sm;

import org.joda.time.DateTime;
import org.killbill.automaton.OperationException;
import org.killbill.automaton.State;
import org.killbill.automaton.State.LeavingStateCallback;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.dao.PaymentModelDao;
import org.killbill.billing.payment.dao.PaymentAttemptModelDao;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dao.PluginPropertySerializer;
import org.killbill.billing.payment.dao.PluginPropertySerializer.PluginPropertySerializerException;

import com.google.common.base.Preconditions;

public class RetryLeavingStateCallback implements LeavingStateCallback {

    private PluginControlledPaymentAutomatonRunner retryablePaymentAutomatonRunner;
    private final RetryablePaymentStateContext stateContext;
    private final State initialState;
    private final State retriedState;
    private final TransactionType transactionType;
    private final PaymentDao paymentDao;

    public RetryLeavingStateCallback(final PluginControlledPaymentAutomatonRunner retryablePaymentAutomatonRunner, final PaymentStateContext stateContext, final PaymentDao paymentDao,
                                     final State initialState, final State retriedState, final TransactionType transactionType) {
        this.retryablePaymentAutomatonRunner = retryablePaymentAutomatonRunner;
        this.paymentDao = paymentDao;
        this.initialState = initialState;
        this.retriedState = retriedState;
        this.stateContext = (RetryablePaymentStateContext) stateContext;
        this.transactionType = transactionType;
    }

    @Override
    public void leavingState(final State state) throws OperationException {

        final DateTime utcNow = retryablePaymentAutomatonRunner.clock.getUTCNow();

        Preconditions.checkState(stateContext.getPaymentExternalKey() != null || /* CAPTURE, PURCHASE, CREDIT calls will provide the paymentId */
                                 stateContext.getPaymentId() != null);
        if (stateContext.getPaymentExternalKey() == null) {
            final PaymentModelDao payment = paymentDao.getPayment(stateContext.getPaymentId(), stateContext.internalCallContext);
            Preconditions.checkState(payment != null);
            stateContext.setPaymentExternalKey(payment.getExternalKey());
        }


        if (state.getName().equals(initialState.getName()) ||
            state.getName().equals(retriedState.getName())) {

            try {
                final byte [] serializedProperties = PluginPropertySerializer.serialize(stateContext.getProperties());


                final PaymentAttemptModelDao attempt = new PaymentAttemptModelDao(stateContext.getAccount().getId(), stateContext.getPaymentMethodId(),
                                                                                  utcNow, utcNow, stateContext.getPaymentExternalKey(), null,
                                                                                  stateContext.paymentTransactionExternalKey, transactionType, initialState.getName(),
                                                                                  stateContext.getAmount(), stateContext.getCurrency(),
                                                                                  stateContext.getPluginName(), serializedProperties);

                retryablePaymentAutomatonRunner.paymentDao.insertPaymentAttemptWithProperties(attempt, stateContext.internalCallContext);
                stateContext.setAttemptId(attempt.getId());
            } catch (PluginPropertySerializerException e) {
                throw new OperationException(e);
            }

        }
    }
}
