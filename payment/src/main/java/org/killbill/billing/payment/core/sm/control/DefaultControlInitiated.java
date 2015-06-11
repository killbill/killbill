/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.billing.payment.core.sm.control;

import java.util.UUID;

import org.killbill.automaton.Operation.OperationCallback;
import org.killbill.automaton.OperationResult;
import org.killbill.automaton.State;
import org.killbill.automaton.State.EnteringStateCallback;
import org.killbill.automaton.State.LeavingStateCallback;
import org.killbill.billing.ObjectType;
import org.killbill.billing.payment.core.sm.PluginRoutingPaymentAutomatonRunner;
import org.killbill.billing.payment.dao.PaymentAttemptModelDao;
import org.killbill.billing.payment.retry.BaseRetryService.RetryServiceScheduler;

public class DefaultControlInitiated implements EnteringStateCallback {

    private PluginRoutingPaymentAutomatonRunner retryablePaymentAutomatonRunner;
    private final PaymentStateControlContext paymentStateContext;
    private final RetryServiceScheduler retryServiceScheduler;

    public DefaultControlInitiated(final PluginRoutingPaymentAutomatonRunner retryablePaymentAutomatonRunner, final PaymentStateControlContext paymentStateContext,
                                   final RetryServiceScheduler retryServiceScheduler) {
        this.retryablePaymentAutomatonRunner = retryablePaymentAutomatonRunner;
        this.paymentStateContext = paymentStateContext;
        this.retryServiceScheduler = retryServiceScheduler;
    }

    @Override
    public void enteringState(final State state, final OperationCallback operationCallback, final OperationResult operationResult, final LeavingStateCallback leavingStateCallback) {
        final PaymentAttemptModelDao attempt = retryablePaymentAutomatonRunner.getPaymentDao().getPaymentAttempt(paymentStateContext.getAttemptId(), paymentStateContext.getInternalCallContext());
        final UUID transactionId = paymentStateContext.getCurrentTransaction() != null ?
                                   paymentStateContext.getCurrentTransaction().getId() :
                                   null;
        retryablePaymentAutomatonRunner.getPaymentDao().updatePaymentAttempt(attempt.getId(), transactionId, state.getName(), paymentStateContext.getInternalCallContext());

        if ("RETRIED".equals(state.getName())) {
            retryServiceScheduler.scheduleRetry(ObjectType.PAYMENT_ATTEMPT, attempt.getId(), attempt.getId(), attempt.getTenantRecordId(),
                                                paymentStateContext.getPaymentControlPluginNames(), paymentStateContext.getRetryDate());
        }
    }
}
