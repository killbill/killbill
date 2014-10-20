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

import java.util.UUID;

import org.killbill.automaton.Operation.OperationCallback;
import org.killbill.automaton.OperationResult;
import org.killbill.automaton.State;
import org.killbill.automaton.State.EnteringStateCallback;
import org.killbill.automaton.State.LeavingStateCallback;
import org.killbill.billing.ObjectType;
import org.killbill.billing.payment.dao.PaymentAttemptModelDao;
import org.killbill.billing.payment.retry.BaseRetryService.RetryServiceScheduler;

public class RetryEnteringStateCallback implements EnteringStateCallback {

    private PluginControlledPaymentAutomatonRunner retryablePaymentAutomatonRunner;
    private final RetryablePaymentStateContext paymentStateContext;
    private final RetryServiceScheduler retryServiceScheduler;

    public RetryEnteringStateCallback(final PluginControlledPaymentAutomatonRunner retryablePaymentAutomatonRunner, final RetryablePaymentStateContext paymentStateContext,
                                      final RetryServiceScheduler retryServiceScheduler) {
        this.retryablePaymentAutomatonRunner = retryablePaymentAutomatonRunner;
        this.paymentStateContext = paymentStateContext;
        this.retryServiceScheduler = retryServiceScheduler;
    }

    @Override
    public void enteringState(final State state, final OperationCallback operationCallback, final OperationResult operationResult, final LeavingStateCallback leavingStateCallback) {

        final PaymentAttemptModelDao attempt = retryablePaymentAutomatonRunner.paymentDao.getPaymentAttempt(paymentStateContext.getAttemptId(), paymentStateContext.internalCallContext);
        final UUID transactionId = paymentStateContext.getCurrentTransaction() != null ?
                                   paymentStateContext.getCurrentTransaction().getId() :
                                   null;
        retryablePaymentAutomatonRunner.paymentDao.updatePaymentAttempt(attempt.getId(), transactionId, state.getName(), paymentStateContext.internalCallContext);

        if ("RETRIED".equals(state.getName())) {
            retryServiceScheduler.scheduleRetry(ObjectType.PAYMENT_ATTEMPT, attempt.getId(), attempt.getId(),
                                                paymentStateContext.getPaymentControlPluginNames(), paymentStateContext.getRetryDate());
        }
    }
}
