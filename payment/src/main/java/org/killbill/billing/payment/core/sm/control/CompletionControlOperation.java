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

import java.util.List;

import org.killbill.automaton.OperationException;
import org.killbill.automaton.OperationResult;
import org.killbill.billing.control.plugin.api.PaymentApiType;
import org.killbill.billing.control.plugin.api.PaymentControlContext;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.core.PaymentProcessor;
import org.killbill.billing.payment.core.ProcessorBase.DispatcherCallback;
import org.killbill.billing.payment.core.sm.control.ControlPluginRunner.DefaultPaymentControlContext;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.billing.payment.dispatcher.PluginDispatcher.PluginDispatcherReturnType;
import org.killbill.billing.util.config.definition.PaymentConfig;
import org.killbill.commons.locker.GlobalLocker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

//
// Used from AttemptCompletionTask to resume an incomplete payment that went through control API.
//
public class CompletionControlOperation extends OperationControlCallback {

    private static final Logger logger = LoggerFactory.getLogger(CompletionControlOperation.class);

    private static final Joiner JOINER = Joiner.on(", ");

    public CompletionControlOperation(final GlobalLocker locker,
                                      final PluginDispatcher<OperationResult> paymentPluginDispatcher,
                                      final PaymentConfig paymentConfig,
                                      final PaymentStateControlContext paymentStateContext,
                                      final PaymentProcessor paymentProcessor,
                                      final ControlPluginRunner controlPluginRunner) {
        super(locker, paymentPluginDispatcher, paymentStateContext, paymentProcessor, paymentConfig, controlPluginRunner);
    }

    @Override
    public OperationResult doOperationCallback() throws OperationException {
        final List<String> controlPluginNameList = paymentStateControlContext.getPaymentControlPluginNames();
        final String controlPluginNames = JOINER.join(controlPluginNameList);

        return dispatchWithAccountLockAndTimeout(controlPluginNames, new DispatcherCallback<PluginDispatcherReturnType<OperationResult>, OperationException>() {

            @Override
            public PluginDispatcherReturnType<OperationResult> doOperation() throws OperationException {
                final PaymentTransactionModelDao transaction = paymentStateContext.getPaymentTransactionModelDao();
                final PaymentControlContext updatedPaymentControlContext = new DefaultPaymentControlContext(paymentStateContext.getAccount(),
                                                                                                            paymentStateContext.getPaymentMethodId(),
                                                                                                            null,
                                                                                                            paymentStateControlContext.getAttemptId(),
                                                                                                            transaction.getPaymentId(),
                                                                                                            paymentStateContext.getPaymentExternalKey(),
                                                                                                            transaction.getId(),
                                                                                                            paymentStateContext.getPaymentTransactionExternalKey(),
                                                                                                            PaymentApiType.PAYMENT_TRANSACTION,
                                                                                                            paymentStateContext.getTransactionType(),
                                                                                                            null,
                                                                                                            transaction.getAmount(),
                                                                                                            transaction.getCurrency(),
                                                                                                            transaction.getProcessedAmount(),
                                                                                                            transaction.getProcessedCurrency(),
                                                                                                            paymentStateControlContext.isApiPayment(),
                                                                                                            paymentStateContext.getCallContext());
                try {
                    final Payment result = doCallSpecificOperationCallback();
                    logger.debug("doOperationCallback payment='{}', transaction='{}'", result, transaction);
                    ((PaymentStateControlContext) paymentStateContext).setResult(result);

                    final boolean success = transaction.getTransactionStatus() == TransactionStatus.SUCCESS || transaction.getTransactionStatus() == TransactionStatus.PENDING;
                    if (success) {
                        executePluginOnSuccessCalls(paymentStateControlContext.getPaymentControlPluginNames(), updatedPaymentControlContext);

                        // Remove scheduled retry, if any
                        paymentProcessor.cancelScheduledPaymentTransaction(paymentStateControlContext.getAttemptId(), paymentStateControlContext.getInternalCallContext());

                        return PluginDispatcher.createPluginDispatcherReturnType(OperationResult.SUCCESS);
                    } else {
                        throw new OperationException(null, executePluginOnFailureCallsAndSetRetryDate(updatedPaymentControlContext));
                    }
                } catch (final PaymentApiException e) {
                    // Wrap PaymentApiException, and throw a new OperationException with an ABORTED/FAILURE state based on the retry result.
                    throw new OperationException(e, executePluginOnFailureCallsAndSetRetryDate(updatedPaymentControlContext));
                } catch (final RuntimeException e) {
                    // Attempts to set the retry date in context if needed.
                    throw new OperationException(e, executePluginOnFailureCallsAndSetRetryDate(updatedPaymentControlContext));
                }
            }
        });
    }

    @Override
    protected Payment doCallSpecificOperationCallback() throws PaymentApiException {
        return paymentProcessor.getPayment(paymentStateContext.getPaymentId(), false, false, ImmutableList.<PluginProperty>of(), paymentStateContext.getCallContext(), paymentStateContext.getInternalCallContext());
    }
}
