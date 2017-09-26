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

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.automaton.Operation.OperationCallback;
import org.killbill.automaton.OperationException;
import org.killbill.automaton.OperationResult;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.control.plugin.api.OnFailurePaymentControlResult;
import org.killbill.billing.control.plugin.api.OnSuccessPaymentControlResult;
import org.killbill.billing.control.plugin.api.PaymentApiType;
import org.killbill.billing.control.plugin.api.PaymentControlApiException;
import org.killbill.billing.control.plugin.api.PaymentControlContext;
import org.killbill.billing.control.plugin.api.PriorPaymentControlResult;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentTransaction;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.core.PaymentProcessor;
import org.killbill.billing.payment.core.ProcessorBase.DispatcherCallback;
import org.killbill.billing.payment.core.sm.OperationCallbackBase;
import org.killbill.billing.payment.core.sm.PaymentStateContext;
import org.killbill.billing.payment.core.sm.control.ControlPluginRunner.DefaultPaymentControlContext;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.billing.payment.dispatcher.PluginDispatcher.PluginDispatcherReturnType;
import org.killbill.billing.util.config.definition.PaymentConfig;
import org.killbill.commons.locker.GlobalLocker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;

public abstract class OperationControlCallback extends OperationCallbackBase<Payment, PaymentApiException> implements OperationCallback {

    private static final Logger logger = LoggerFactory.getLogger(OperationControlCallback.class);

    private static final Joiner JOINER = Joiner.on(", ");

    protected final PaymentProcessor paymentProcessor;
    protected final PaymentStateControlContext paymentStateControlContext;
    private final ControlPluginRunner controlPluginRunner;

    protected OperationControlCallback(final GlobalLocker locker,
                                       final PluginDispatcher<OperationResult> paymentPluginDispatcher,
                                       final PaymentStateControlContext paymentStateContext,
                                       final PaymentProcessor paymentProcessor,
                                       final PaymentConfig paymentConfig,
                                       final ControlPluginRunner controlPluginRunner) {
        super(locker, paymentPluginDispatcher, paymentConfig, paymentStateContext);
        this.paymentProcessor = paymentProcessor;
        this.controlPluginRunner = controlPluginRunner;
        this.paymentStateControlContext = paymentStateContext;
    }

    @Override
    protected abstract Payment doCallSpecificOperationCallback() throws PaymentApiException;

    @Override
    public OperationResult doOperationCallback() throws OperationException {
        final List<String> pluginNameList = paymentStateControlContext.getPaymentControlPluginNames();
        final String pluginNames = JOINER.join(pluginNameList);

        return dispatchWithAccountLockAndTimeout(pluginNames, new DispatcherCallback<PluginDispatcherReturnType<OperationResult>, OperationException>() {

            @Override
            public PluginDispatcherReturnType<OperationResult> doOperation() throws OperationException {

                final PaymentControlContext paymentControlContext = new DefaultPaymentControlContext(paymentStateContext.getAccount(),
                                                                                                     paymentStateContext.getPaymentMethodId(),
                                                                                                     null,
                                                                                                     paymentStateControlContext.getAttemptId(),
                                                                                                     paymentStateContext.getPaymentId(),
                                                                                                     paymentStateContext.getPaymentExternalKey(),
                                                                                                     paymentStateContext.getTransactionId(),
                                                                                                     paymentStateContext.getPaymentTransactionExternalKey(),
                                                                                                     PaymentApiType.PAYMENT_TRANSACTION,
                                                                                                     paymentStateContext.getTransactionType(),
                                                                                                     null,
                                                                                                     paymentStateContext.getAmount(),
                                                                                                     paymentStateContext.getCurrency(),
                                                                                                     paymentStateControlContext.getProcessedAmount(),
                                                                                                     paymentStateControlContext.getProcessedCurrency(),
                                                                                                     paymentStateControlContext.isApiPayment(),
                                                                                                     paymentStateContext.getCallContext());

                try {
                    executePluginPriorCalls(paymentStateControlContext.getPaymentControlPluginNames(), paymentControlContext);
                } catch (final PaymentControlApiAbortException e) {
                    // Transition to ABORTED
                    final PaymentApiException paymentAbortedException = new PaymentApiException(ErrorCode.PAYMENT_PLUGIN_API_ABORTED, e.getPluginName());
                    throw new OperationException(paymentAbortedException, OperationResult.EXCEPTION);
                } catch (final PaymentControlApiException e) {
                    // Transition to ABORTED and throw PaymentControlApiException to caller.
                    throw new OperationException(e, OperationResult.EXCEPTION);
                }

                final boolean success;
                try {
                    final Payment result = doCallSpecificOperationCallback();
                    ((PaymentStateControlContext) paymentStateContext).setResult(result);
                    final PaymentTransaction transaction = ((PaymentStateControlContext) paymentStateContext).getCurrentTransaction();

                    success = transaction.getTransactionStatus() == TransactionStatus.SUCCESS || transaction.getTransactionStatus() == TransactionStatus.PENDING;
                    final PaymentControlContext updatedPaymentControlContext = new DefaultPaymentControlContext(paymentStateContext.getAccount(),
                                                                                                                paymentStateContext.getPaymentMethodId(),
                                                                                                                null,
                                                                                                                paymentStateControlContext.getAttemptId(),
                                                                                                                result.getId(),
                                                                                                                result.getExternalKey(),
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
                    if (success) {
                        executePluginOnSuccessCalls(paymentStateControlContext.getPaymentControlPluginNames(), updatedPaymentControlContext);
                        return PluginDispatcher.createPluginDispatcherReturnType(OperationResult.SUCCESS);
                    } else {
                        throw new OperationException(null, executePluginOnFailureCallsAndSetRetryDate(updatedPaymentControlContext));
                    }
                } catch (final PaymentApiException e) {
                    // Wrap PaymentApiException, and throw a new OperationException with an ABORTED/FAILURE state based on the retry result.
                    throw new OperationException(e, executePluginOnFailureCallsAndSetRetryDate(paymentControlContext));
                } catch (final RuntimeException e) {
                    // Attempts to set the retry date in context if needed.
                    throw new OperationException(e, executePluginOnFailureCallsAndSetRetryDate(paymentControlContext));
                }
            }
        });
    }

    @Override
    protected OperationException unwrapExceptionFromDispatchedTask(final PaymentApiException e) {
        if (e.getCause() instanceof OperationException) {
            return (OperationException) e.getCause();
        }
        logger.warn("Operation failed for accountId='{}' accountExternalKey='{}' error='{}'", paymentStateContext.getAccount().getId(), paymentStateContext.getAccount().getExternalKey(), e.getMessage());
        return new OperationException(e, getOperationResultOnException(paymentStateContext));
    }

    private OperationResult getOperationResultOnException(final PaymentStateContext paymentStateContext) {
        final PaymentStateControlContext paymentStateControlContext = (PaymentStateControlContext) paymentStateContext;
        final OperationResult operationResult = paymentStateControlContext.getRetryDate() != null ? OperationResult.FAILURE : OperationResult.EXCEPTION;
        return operationResult;
    }

    private PriorPaymentControlResult executePluginPriorCalls(final List<String> paymentControlPluginNames, final PaymentControlContext paymentControlContextArg) throws PaymentControlApiException {

        final PriorPaymentControlResult result = controlPluginRunner.executePluginPriorCalls(paymentStateContext.getAccount(),
                                                                                             paymentControlContextArg.getPaymentMethodId(),
                                                                                             null,
                                                                                             paymentStateControlContext.getAttemptId(),
                                                                                             paymentStateContext.getPaymentId(),
                                                                                             paymentStateContext.getPaymentExternalKey(),
                                                                                             paymentStateContext.getTransactionId(),
                                                                                             paymentStateContext.getPaymentTransactionExternalKey(),
                                                                                             PaymentApiType.PAYMENT_TRANSACTION,
                                                                                             paymentStateContext.getTransactionType(),
                                                                                             null,
                                                                                             paymentControlContextArg.getAmount(),
                                                                                             paymentControlContextArg.getCurrency(),
                                                                                             paymentControlContextArg.getProcessedAmount(),
                                                                                             paymentControlContextArg.getProcessedCurrency(),
                                                                                             paymentStateControlContext.isApiPayment(),
                                                                                             paymentControlPluginNames,
                                                                                             paymentStateContext.getProperties(),
                                                                                             paymentStateContext.getCallContext());

        adjustStateContextForPriorCall(paymentStateContext, result);
        return result;
    }

    protected void executePluginOnSuccessCalls(final List<String> paymentControlPluginNames, final PaymentControlContext paymentControlContext) {
        // Values that were obtained/changed after the payment call was made (paymentId, processedAmount, processedCurrency,... needs to be extracted from the paymentControlContext)
        // paymentId, paymentExternalKey, transactionAmount, transaction currency are extracted from  paymentControlContext which was update from the operation result.
        final OnSuccessPaymentControlResult result = controlPluginRunner.executePluginOnSuccessCalls(paymentStateContext.getAccount(),
                                                                                                     paymentStateContext.getPaymentMethodId(),
                                                                                                     null,
                                                                                                     paymentStateControlContext.getAttemptId(),
                                                                                                     paymentControlContext.getPaymentId(),
                                                                                                     paymentControlContext.getPaymentExternalKey(),
                                                                                                     paymentControlContext.getTransactionId(),
                                                                                                     paymentStateContext.getPaymentTransactionExternalKey(),
                                                                                                     PaymentApiType.PAYMENT_TRANSACTION,
                                                                                                     paymentStateContext.getTransactionType(),
                                                                                                     null,
                                                                                                     paymentControlContext.getAmount(),
                                                                                                     paymentControlContext.getCurrency(),
                                                                                                     paymentControlContext.getProcessedAmount(),
                                                                                                     paymentControlContext.getProcessedCurrency(),
                                                                                                     paymentStateControlContext.isApiPayment(),
                                                                                                     paymentControlPluginNames,
                                                                                                     paymentStateContext.getProperties(),
                                                                                                     paymentStateContext.getCallContext());
        adjustStateContextPluginProperties(paymentStateContext, result.getAdjustedPluginProperties());
    }

    protected OperationResult executePluginOnFailureCallsAndSetRetryDate(final PaymentControlContext paymentControlContext) {
        final DateTime retryDate = executePluginOnFailureCalls(paymentStateControlContext.getPaymentControlPluginNames(), paymentControlContext);
        if (retryDate != null) {
            ((PaymentStateControlContext) paymentStateContext).setRetryDate(retryDate);
        }
        return getOperationResultOnException(paymentStateContext);
    }

    private DateTime executePluginOnFailureCalls(final List<String> paymentControlPluginNames, final PaymentControlContext paymentControlContext) {

        final OnFailurePaymentControlResult result = controlPluginRunner.executePluginOnFailureCalls(paymentStateContext.getAccount(),
                                                                                                     paymentControlContext.getPaymentMethodId(),
                                                                                                     null,
                                                                                                     paymentStateControlContext.getAttemptId(),
                                                                                                     paymentControlContext.getPaymentId(),
                                                                                                     paymentControlContext.getPaymentExternalKey(),
                                                                                                     paymentControlContext.getTransactionId(),
                                                                                                     paymentControlContext.getTransactionExternalKey(),
                                                                                                     PaymentApiType.PAYMENT_TRANSACTION,
                                                                                                     paymentControlContext.getTransactionType(),
                                                                                                     null,
                                                                                                     paymentControlContext.getAmount(),
                                                                                                     paymentControlContext.getCurrency(),
                                                                                                     paymentControlContext.getProcessedAmount(),
                                                                                                     paymentControlContext.getProcessedCurrency(),
                                                                                                     paymentStateControlContext.isApiPayment(),
                                                                                                     paymentControlPluginNames,
                                                                                                     paymentStateContext.getProperties(),
                                                                                                     paymentStateContext.getCallContext());
        adjustStateContextPluginProperties(paymentStateContext, result.getAdjustedPluginProperties());
        return result.getNextRetryDate();
    }

    private void adjustStateContextForPriorCall(final PaymentStateContext inputContext, @Nullable final PriorPaymentControlResult pluginResult) {
        if (pluginResult == null) {
            return;
        }

        final PaymentStateControlContext input = (PaymentStateControlContext) inputContext;
        if (pluginResult.getAdjustedAmount() != null) {
            input.setAmount(pluginResult.getAdjustedAmount());
        }
        if (pluginResult.getAdjustedCurrency() != null) {
            input.setCurrency(pluginResult.getAdjustedCurrency());
        }
        if (pluginResult.getAdjustedPaymentMethodId() != null) {
            input.setPaymentMethodId(pluginResult.getAdjustedPaymentMethodId());
        }
        adjustStateContextPluginProperties(inputContext, pluginResult.getAdjustedPluginProperties());
    }

    private void adjustStateContextPluginProperties(final PaymentStateContext inputContext, @Nullable Iterable<PluginProperty> pluginProperties) {
        if (pluginProperties == null) {
            return;
        }
        final PaymentStateControlContext input = (PaymentStateControlContext) inputContext;
        input.setProperties(pluginProperties);
    }
}
