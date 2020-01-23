/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

import org.joda.time.DateTime;
import org.killbill.automaton.OperationException;
import org.killbill.automaton.State;
import org.killbill.automaton.State.LeavingStateCallback;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.core.sm.PaymentStateContext;
import org.killbill.billing.payment.core.sm.PluginControlPaymentAutomatonRunner;
import org.killbill.billing.payment.dao.PaymentAttemptModelDao;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dao.PaymentMethodModelDao;
import org.killbill.billing.payment.dao.PaymentModelDao;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.killbill.billing.payment.dao.PluginPropertySerializer;
import org.killbill.billing.payment.dao.PluginPropertySerializer.PluginPropertySerializerException;
import org.killbill.billing.util.UUIDs;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class DefaultControlInitiated implements LeavingStateCallback {

    private static final ImmutableList<TransactionStatus> TRANSIENT_TRANSACTION_STATUSES = ImmutableList.<TransactionStatus>builder().add(TransactionStatus.PENDING)
                                                                                                                                     .add(TransactionStatus.UNKNOWN)
                                                                                                                                     .build();

    private final PluginControlPaymentAutomatonRunner pluginControlPaymentAutomatonRunner;
    private final PaymentStateControlContext stateContext;
    private final State initialState;
    private final State retriedState;
    private final TransactionType transactionType;
    private final PaymentDao paymentDao;

    public DefaultControlInitiated(final PluginControlPaymentAutomatonRunner pluginControlPaymentAutomatonRunner, final PaymentStateContext stateContext, final PaymentDao paymentDao,
                                   final State initialState, final State retriedState, final TransactionType transactionType) {
        this.pluginControlPaymentAutomatonRunner = pluginControlPaymentAutomatonRunner;
        this.paymentDao = paymentDao;
        this.initialState = initialState;
        this.retriedState = retriedState;
        this.stateContext = (PaymentStateControlContext) stateContext;
        this.transactionType = transactionType;
    }

    @Override
    public void leavingState(final State state) throws OperationException {
        final DateTime utcNow = pluginControlPaymentAutomatonRunner.getClock().getUTCNow();

        // Retrieve the associated payment transaction, if any
        PaymentTransactionModelDao paymentTransactionModelDaoCandidate = null;
        if (stateContext.getTransactionId() != null) {
            paymentTransactionModelDaoCandidate = paymentDao.getPaymentTransaction(stateContext.getTransactionId(), stateContext.getInternalCallContext());
            Preconditions.checkNotNull(paymentTransactionModelDaoCandidate, "paymentTransaction cannot be null for id " + stateContext.getTransactionId());
        } else if (stateContext.getPaymentTransactionExternalKey() != null) {
            final List<PaymentTransactionModelDao> paymentTransactionModelDaos = paymentDao.getPaymentTransactionsByExternalKey(stateContext.getPaymentTransactionExternalKey(), stateContext.getInternalCallContext());
            if (!paymentTransactionModelDaos.isEmpty()) {
                paymentTransactionModelDaoCandidate = paymentTransactionModelDaos.get(paymentTransactionModelDaos.size() - 1);
            }
        }
        final PaymentTransactionModelDao paymentTransactionModelDao = paymentTransactionModelDaoCandidate != null && TRANSIENT_TRANSACTION_STATUSES.contains(paymentTransactionModelDaoCandidate.getTransactionStatus()) ? paymentTransactionModelDaoCandidate : null;

        if (stateContext.getPaymentId() != null && stateContext.getPaymentExternalKey() == null) {
            final PaymentModelDao payment = paymentDao.getPayment(stateContext.getPaymentId(), stateContext.getInternalCallContext());
            Preconditions.checkNotNull(payment, "payment cannot be null for id " + stateContext.getPaymentId());
            stateContext.setPaymentExternalKey(payment.getExternalKey());
            stateContext.setPaymentMethodId(payment.getPaymentMethodId());
        } else if (stateContext.getPaymentExternalKey() == null) {
            final UUID paymentIdForNewPayment = UUIDs.randomUUID();
            stateContext.setPaymentIdForNewPayment(paymentIdForNewPayment);
            stateContext.setPaymentExternalKey(paymentIdForNewPayment.toString());
        }

        if (paymentTransactionModelDao != null) {
            stateContext.setPaymentTransactionModelDao(paymentTransactionModelDao);
            stateContext.setProcessedAmount(paymentTransactionModelDao.getProcessedAmount());
            stateContext.setProcessedCurrency(paymentTransactionModelDao.getProcessedCurrency());
        } else if (stateContext.getPaymentTransactionExternalKey() == null) {
            final UUID paymentTransactionIdForNewPaymentTransaction = UUIDs.randomUUID();
            stateContext.setPaymentTransactionIdForNewPaymentTransaction(paymentTransactionIdForNewPaymentTransaction);
            stateContext.setPaymentTransactionExternalKey(paymentTransactionIdForNewPaymentTransaction.toString());
        }

        // The user specified the payment Method to use for a new payment or we computed which one to use for a subsequent payment
        // In this case, we also want to provide the associated plugin name
        if (stateContext.getPaymentMethodId() != null) {
            final PaymentMethodModelDao pm = paymentDao.getPaymentMethod(stateContext.getPaymentMethodId(), stateContext.getInternalCallContext());
            // Payment method was deleted
            if (pm != null) {
                stateContext.setOriginalPaymentPluginName(pm.getPluginName());
            }
        }


        if (state.getName().equals(initialState.getName()) || state.getName().equals(retriedState.getName())) {
            try {
                final PaymentAttemptModelDao attempt;
                if (paymentTransactionModelDao != null && paymentTransactionModelDao.getAttemptId() != null) {
                    attempt = pluginControlPaymentAutomatonRunner.getPaymentDao().getPaymentAttempt(paymentTransactionModelDao.getAttemptId(), stateContext.getInternalCallContext());
                    Preconditions.checkNotNull(attempt, "attempt cannot be null for id " + paymentTransactionModelDao.getAttemptId());
                } else {
                    //
                    // We don't serialize any properties at this stage to avoid serializing sensitive information.
                    // However, if after going through the control plugins, the attempt end up in RETRIED state,
                    // the properties will be serialized in the enteringState callback (any plugin that sets a
                    // retried date is responsible to correctly remove sensitive information such as CVV, ...)
                    //
                    final byte[] serializedProperties = PluginPropertySerializer.serialize(ImmutableList.<PluginProperty>of());

                    attempt = new PaymentAttemptModelDao(stateContext.getAccount().getId(), stateContext.getPaymentMethodId(),
                                                         utcNow, utcNow, stateContext.getPaymentExternalKey(), stateContext.getTransactionId(),
                                                         stateContext.getPaymentTransactionExternalKey(), transactionType, initialState.getName(),
                                                         stateContext.getAmount(), stateContext.getCurrency(),
                                                         stateContext.getPaymentControlPluginNames(), serializedProperties);
                    pluginControlPaymentAutomatonRunner.getPaymentDao().insertPaymentAttemptWithProperties(attempt, stateContext.getInternalCallContext());
                }

                stateContext.setAttemptId(attempt.getId());
            } catch (final PluginPropertySerializerException e) {
                throw new OperationException(e);
            }
        }
    }
}
