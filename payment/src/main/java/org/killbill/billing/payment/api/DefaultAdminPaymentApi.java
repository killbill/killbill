/*
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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
import org.killbill.billing.payment.core.sm.PaymentAutomatonDAOHelper;
import org.killbill.billing.payment.core.sm.PaymentStateMachineHelper;
import org.killbill.billing.payment.dao.PaymentAttemptModelDao;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.config.definition.PaymentConfig;

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

    // Very similar implementation as the Janitor (see IncompletePaymentTransactionTask / IncompletePaymentAttemptTask)
    // The code is different enough to make it difficult to share unfortunately
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

        final PaymentAutomatonDAOHelper paymentAutomatonDAOHelper = new PaymentAutomatonDAOHelper(paymentDao,
                                                                                                  internalCallContext,
                                                                                                  paymentSMHelper);
        paymentAutomatonDAOHelper.processPaymentInfoPlugin(transactionStatus,
                                                           paymentTransaction.getPaymentInfoPlugin(),
                                                           currentPaymentStateName,
                                                           lastSuccessPaymentState,
                                                           // In case of success, and if we don't have any plugin details,
                                                           // assume the full amount was paid to correctly reconcile invoice payments
                                                           // See https://github.com/killbill/killbill/issues/1061#issuecomment-521911301
                                                           paymentTransaction.getAmount(),
                                                           paymentTransaction.getCurrency(),
                                                           payment.getAccountId(),
                                                           null,
                                                           payment.getId(),
                                                           paymentTransaction.getId(),
                                                           paymentTransaction.getTransactionType(),
                                                           true,
                                                           true);

        // If there is a payment attempt associated with that transaction, we need to update it as well
        final List<PaymentAttemptModelDao> paymentAttemptsModelDao = paymentDao.getPaymentAttemptByTransactionExternalKey(paymentTransaction.getExternalKey(), internalCallContext);
        paymentAttemptsModelDao.stream()
                .filter(payAttemptModelDao -> paymentTransaction.getId().equals(payAttemptModelDao.getTransactionId()))
                .findFirst()
                .ifPresent(payAttemptModelDao -> {
                    // We can re-use the logic from IncompletePaymentAttemptTask as it is doing very similar work (i.e. run the completion part of
                    // the state machine to call the plugins and update the attempt in the right terminal state)
                    incompletePaymentAttemptTask.doIteration(payAttemptModelDao, true, properties);
                });
    }
}
