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

package org.killbill.billing.payment.core.sm.control;

import java.util.List;
import java.util.UUID;

import org.killbill.automaton.Operation.OperationCallback;
import org.killbill.automaton.OperationResult;
import org.killbill.automaton.State;
import org.killbill.automaton.State.EnteringStateCallback;
import org.killbill.automaton.State.LeavingStateCallback;
import org.killbill.billing.ObjectType;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.core.sm.PluginControlPaymentAutomatonRunner;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.dao.PaymentAttemptModelDao;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.killbill.billing.payment.dao.PluginPropertySerializer;
import org.killbill.billing.payment.dao.PluginPropertySerializer.PluginPropertySerializerException;
import org.killbill.billing.payment.retry.BaseRetryService.RetryServiceScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class DefaultControlCompleted implements EnteringStateCallback {

    private static final Logger logger = LoggerFactory.getLogger(DefaultControlCompleted.class);

    private final PaymentStateControlContext paymentStateContext;
    private final RetryServiceScheduler retryServiceScheduler;
    private final State retriedState;
    private final PluginControlPaymentAutomatonRunner retryablePaymentAutomatonRunner;

    public DefaultControlCompleted(final PluginControlPaymentAutomatonRunner retryablePaymentAutomatonRunner, final PaymentStateControlContext paymentStateContext,
                                   final State retriedState, final RetryServiceScheduler retryServiceScheduler) {
        this.retryablePaymentAutomatonRunner = retryablePaymentAutomatonRunner;
        this.paymentStateContext = paymentStateContext;
        this.retriedState = retriedState;
        this.retryServiceScheduler = retryServiceScheduler;
    }

    @Override
    public void enteringState(final State state, final OperationCallback operationCallback, final OperationResult operationResult, final LeavingStateCallback leavingStateCallback) {
        final PaymentAttemptModelDao attempt = retryablePaymentAutomatonRunner.getPaymentDao().getPaymentAttempt(paymentStateContext.getAttemptId(), paymentStateContext.getInternalCallContext());
        final UUID transactionId = paymentStateContext.getCurrentTransaction() != null ?
                                   paymentStateContext.getCurrentTransaction().getId() :
                                   null;

        logger.debug("enteringState attemptId='{}', transactionId='{}', state='{}'", attempt.getId(), transactionId, state.getName());
        // At this stage we can update the paymentAttempt state AND serialize the plugin properties. Control plugins will have had the opportunity to erase sensitive data if required.
        retryablePaymentAutomatonRunner.getPaymentDao().updatePaymentAttemptWithProperties(attempt.getId(),
                                                                                           paymentStateContext.getPaymentMethodId(),
                                                                                           transactionId,
                                                                                           state.getName(),
                                                                                           getSerializedProperties(),
                                                                                           paymentStateContext.getInternalCallContext());

        if (retriedState.getName().equals(state.getName()) && !isUnknownTransaction()) {
            retryServiceScheduler.scheduleRetry(ObjectType.PAYMENT_ATTEMPT, attempt.getId(), attempt.getId(), attempt.getTenantRecordId(),
                                                paymentStateContext.getPaymentControlPluginNames(), paymentStateContext.getRetryDate());
        }
    }

    private byte[] getSerializedProperties() {
        try {
            return PluginPropertySerializer.serialize(paymentStateContext.getProperties());
        } catch (final PluginPropertySerializerException e) {
            throw new IllegalStateException(e);
        }
    }


    //
    // If we see an UNKNOWN transaction we prevent it to be rescheduled as the Janitor will *try* to fix it, and that could lead to infinite retries from a badly behaved plugin
    // (In other words, plugin should ONLY retry 'known' transaction)
    //
    private boolean isUnknownTransaction() {
        if (paymentStateContext.getCurrentTransaction() != null) {
            return paymentStateContext.getCurrentTransaction().getTransactionStatus() == TransactionStatus.UNKNOWN;
        } else {
            final List<PaymentTransactionModelDao> transactions = retryablePaymentAutomatonRunner.getPaymentDao().getPaymentTransactionsByExternalKey(paymentStateContext.getPaymentTransactionExternalKey(), paymentStateContext.getInternalCallContext());
            return Iterables.any(transactions, new Predicate<PaymentTransactionModelDao>() {
                @Override
                public boolean apply(final PaymentTransactionModelDao input) {
                    return input.getTransactionStatus() == TransactionStatus.UNKNOWN &&
                           // Not strictly required
                           // (Note, we don't match on AttemptId as it is risky, the row on disk would match the first attempt, not necessarily the current one)
                           input.getAccountRecordId().equals(paymentStateContext.getInternalCallContext().getAccountRecordId());
                }
            });
        }
    }
}
