/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.payment.api;

import java.util.List;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.payment.core.PaymentTransactionInfoPluginConverter;
import org.killbill.billing.payment.core.janitor.IncompletePaymentAttemptTask;
import org.killbill.billing.payment.core.sm.PaymentStateMachineHelper;
import org.killbill.billing.payment.dao.PaymentAttemptModelDao;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.config.definition.PaymentConfig;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

public class DefaultAdminPaymentApi extends DefaultApiBase implements AdminPaymentApi {

    private final PaymentStateMachineHelper paymentSMHelper;
    private final IncompletePaymentAttemptTask incompletePaymentAttemptTask;
    private final PaymentDao paymentDao;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public DefaultAdminPaymentApi(final PaymentConfig paymentConfig,
                                  final PaymentStateMachineHelper paymentSMHelper,
                                  final IncompletePaymentAttemptTask incompletePaymentAttemptTask,
                                  final PaymentDao paymentDao,
                                  final InternalCallContextFactory internalCallContextFactory) {
        super(paymentConfig, internalCallContextFactory);
        this.paymentSMHelper = paymentSMHelper;
        this.incompletePaymentAttemptTask = incompletePaymentAttemptTask;
        this.paymentDao = paymentDao;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public void fixPaymentTransactionState(final Payment payment,
                                           final PaymentTransaction paymentTransaction,
                                           @Nullable final TransactionStatus transactionStatusOrNull,
                                           @Nullable final String lastSuccessPaymentStateOrNull,
                                           @Nullable final String currentPaymentStateNameOrNull,
                                           final Iterable<PluginProperty> properties,
                                           final CallContext callContext) throws PaymentApiException {
        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(payment.getAccountId(), callContext);

        TransactionStatus transactionStatus = transactionStatusOrNull;
        if (transactionStatusOrNull == null) {
            checkNotNullParameter(paymentTransaction.getPaymentInfoPlugin(), "PaymentTransactionInfoPlugin");
            transactionStatus = PaymentTransactionInfoPluginConverter.toTransactionStatus(paymentTransaction.getPaymentInfoPlugin());
        }

        String currentPaymentStateName = currentPaymentStateNameOrNull;
        if (currentPaymentStateName == null) {
            switch (transactionStatus) {
                case PENDING:
                    currentPaymentStateName = paymentSMHelper.getPendingStateForTransaction(paymentTransaction.getTransactionType());
                    break;
                case SUCCESS:
                    currentPaymentStateName = paymentSMHelper.getSuccessfulStateForTransaction(paymentTransaction.getTransactionType());
                    break;
                case PAYMENT_FAILURE:
                    currentPaymentStateName = paymentSMHelper.getFailureStateForTransaction(paymentTransaction.getTransactionType());
                    break;
                case PLUGIN_FAILURE:
                case UNKNOWN:
                default:
                    currentPaymentStateName = paymentSMHelper.getErroredStateForTransaction(paymentTransaction.getTransactionType());
                    break;
            }
        }

        String lastSuccessPaymentState = lastSuccessPaymentStateOrNull;
        if (lastSuccessPaymentState == null &&
            // Verify we are not updating an older transaction (only the last one has an impact on lastSuccessPaymentState)
            paymentTransaction.getId().equals(payment.getTransactions().get(payment.getTransactions().size() - 1).getId())) {
            if (paymentSMHelper.isSuccessState(currentPaymentStateName)) {
                lastSuccessPaymentState = currentPaymentStateName;
            } else {
                for (int i = payment.getTransactions().size() - 2; i >= 0; i--) {
                    final PaymentTransaction transaction = payment.getTransactions().get(i);
                    if (TransactionStatus.SUCCESS.equals(transaction.getTransactionStatus())) {
                        lastSuccessPaymentState = paymentSMHelper.getSuccessfulStateForTransaction(transaction.getTransactionType());
                        break;
                    }
                }
            }
        }

        paymentDao.updatePaymentAndTransactionOnCompletion(payment.getAccountId(),
                                                           null,
                                                           payment.getId(),
                                                           paymentTransaction.getTransactionType(),
                                                           currentPaymentStateName,
                                                           lastSuccessPaymentState,
                                                           paymentTransaction.getId(),
                                                           transactionStatus,
                                                           paymentTransaction.getProcessedAmount(),
                                                           paymentTransaction.getProcessedCurrency(),
                                                           paymentTransaction.getGatewayErrorCode(),
                                                           paymentTransaction.getGatewayErrorMsg(),
                                                           internalCallContext);

        // If there is a payment attempt associated with that transaction, we need to update it as well
        final List<PaymentAttemptModelDao> paymentAttemptsModelDao = paymentDao.getPaymentAttemptByTransactionExternalKey(paymentTransaction.getExternalKey(), internalCallContext);
        final PaymentAttemptModelDao paymentAttemptModelDao = Iterables.<PaymentAttemptModelDao>tryFind(paymentAttemptsModelDao,
                                                                                                        new Predicate<PaymentAttemptModelDao>() {
                                                                                                            @Override
                                                                                                            public boolean apply(final PaymentAttemptModelDao input) {
                                                                                                                return paymentTransaction.getId().equals(input.getTransactionId());
                                                                                                            }
                                                                                                        }).orNull();
        if (paymentAttemptModelDao != null) {
            // We can re-use the logic from IncompletePaymentAttemptTask as it is doing very similar work (i.e. run the completion part of
            // the state machine to call the plugins and update the attempt in the right terminal state)
            incompletePaymentAttemptTask.doIteration(paymentAttemptModelDao);
        }
    }
}
