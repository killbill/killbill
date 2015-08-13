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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.automaton.Operation.OperationCallback;
import org.killbill.automaton.OperationException;
import org.killbill.automaton.OperationResult;
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
import org.killbill.billing.payment.core.ProcessorBase.WithAccountLockCallback;
import org.killbill.billing.payment.core.sm.OperationCallbackBase;
import org.killbill.billing.payment.core.sm.PaymentStateContext;
import org.killbill.billing.payment.core.sm.control.ControlPluginRunner.DefaultPaymentControlContext;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.billing.payment.dispatcher.PluginDispatcher.PluginDispatcherReturnType;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.locker.LockFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;

public abstract class OperationControlCallback extends OperationCallbackBase<Payment, PaymentApiException> implements OperationCallback {

    private static final Logger logger = LoggerFactory.getLogger(OperationControlCallback.class);

    protected final PaymentProcessor paymentProcessor;
    protected final PaymentStateControlContext paymentStateControlContext;
    private final ControlPluginRunner controlPluginRunner;

    protected OperationControlCallback(final GlobalLocker locker,
                                       final PluginDispatcher<OperationResult> paymentPluginDispatcher,
                                       final PaymentStateControlContext paymentStateContext,
                                       final PaymentProcessor paymentProcessor,
                                       final ControlPluginRunner controlPluginRunner) {
        super(locker, paymentPluginDispatcher, paymentStateContext);
        this.paymentProcessor = paymentProcessor;
        this.controlPluginRunner = controlPluginRunner;
        this.paymentStateControlContext = paymentStateContext;
    }

    @Override
    protected abstract Payment doCallSpecificOperationCallback() throws PaymentApiException;

    @Override
    public OperationResult doOperationCallback() throws OperationException {

        return dispatchWithAccountLockAndTimeout(new WithAccountLockCallback<PluginDispatcherReturnType<OperationResult>, OperationException>() {

            @Override
            public PluginDispatcherReturnType<OperationResult> doOperation() throws OperationException {

                final PaymentControlContext paymentControlContext = new DefaultPaymentControlContext(paymentStateContext.getAccount(),
                                                                                                     paymentStateContext.getPaymentMethodId(),
                                                                                                     paymentStateControlContext.getAttemptId(),
                                                                                                     paymentStateContext.getPaymentId(),
                                                                                                     paymentStateContext.getPaymentExternalKey(),
                                                                                                     paymentStateContext.getPaymentTransactionExternalKey(),
                                                                                                     PaymentApiType.PAYMENT_TRANSACTION,
                                                                                                     paymentStateContext.getTransactionType(),
                                                                                                     null,
                                                                                                     paymentStateContext.getAmount(),
                                                                                                     paymentStateContext.getCurrency(),
                                                                                                     paymentStateControlContext.isApiPayment(),
                                                                                                     paymentStateContext.getCallContext());

                final PriorPaymentControlResult pluginResult;
                try {
                    pluginResult = executePluginPriorCalls(paymentStateControlContext.getPaymentControlPluginNames(), paymentControlContext);
                    if (pluginResult != null && pluginResult.isAborted()) {
                        // Transition to ABORTED
                        return PluginDispatcher.createPluginDispatcherReturnType(OperationResult.EXCEPTION);
                    }
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
                    if (success) {
                        final PaymentControlContext updatedPaymentControlContext = new DefaultPaymentControlContext(paymentStateContext.getAccount(),
                                                                                                                    paymentStateContext.getPaymentMethodId(),
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

                        executePluginOnSuccessCalls(paymentStateControlContext.getPaymentControlPluginNames(), updatedPaymentControlContext);
                        return PluginDispatcher.createPluginDispatcherReturnType(OperationResult.SUCCESS);
                    } else {
                        throw new OperationException(null, executePluginOnFailureCallsAndSetRetryDate(paymentStateControlContext, paymentControlContext));
                    }
                } catch (final PaymentApiException e) {
                    // Wrap PaymentApiException, and throw a new OperationException with an ABORTED/FAILURE state based on the retry result.
                    throw new OperationException(e, executePluginOnFailureCallsAndSetRetryDate(paymentStateControlContext, paymentControlContext));
                } catch (final RuntimeException e) {
                    // Attempts to set the retry date in context if needed.
                    executePluginOnFailureCallsAndSetRetryDate(paymentStateControlContext, paymentControlContext);
                    throw e;
                }
            }
        });
    }

    @Override
    protected OperationException unwrapExceptionFromDispatchedTask(final PaymentStateContext paymentStateContext, final Exception e) {

        // If this is an ExecutionException we attempt to extract the cause first
        final Throwable originalExceptionOrCause = e instanceof ExecutionException ? MoreObjects.firstNonNull(e.getCause(), e) : e;

        if (originalExceptionOrCause instanceof OperationException) {
            return (OperationException) originalExceptionOrCause;
        } else if (originalExceptionOrCause instanceof LockFailedException) {
            final String format = String.format("Failed to lock account %s", paymentStateContext.getAccount().getExternalKey());
            logger.error(String.format(format));
        } else if (originalExceptionOrCause instanceof TimeoutException) {
            logger.warn("RetryOperationCallback call TIMEOUT for account {}", paymentStateContext.getAccount().getExternalKey());
        } else if (originalExceptionOrCause instanceof InterruptedException) {
            logger.error("RetryOperationCallback call was interrupted for account {}", paymentStateContext.getAccount().getExternalKey());
        } else /* most probably RuntimeException */ {
            logger.warn("RetryOperationCallback failed for account {}", paymentStateContext.getAccount().getExternalKey(), e);
        }
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
                                                                                             paymentStateControlContext.getAttemptId(),
                                                                                             paymentStateContext.getPaymentId(),
                                                                                             paymentStateContext.getPaymentExternalKey(),
                                                                                             paymentStateContext.getPaymentTransactionExternalKey(),
                                                                                             PaymentApiType.PAYMENT_TRANSACTION,
                                                                                             paymentStateContext.getTransactionType(),
                                                                                             null,
                                                                                             paymentControlContextArg.getAmount(),
                                                                                             paymentControlContextArg.getCurrency(),
                                                                                             paymentStateControlContext.isApiPayment(),
                                                                                             paymentControlPluginNames,
                                                                                             paymentStateContext.getProperties(),
                                                                                             paymentStateContext.getCallContext());

        adjustStateContextForPriorCall(paymentStateContext, result);
        return result;
    }

    protected void executePluginOnSuccessCalls(final List<String> paymentControlPluginNames, final PaymentControlContext paymentControlContext) {
        // Values that were obtained/chnaged after the payment call was made (paymentId, processedAmount, processedCurrency,... needs to be extracted from the paymentControlContext)
        // paymentId, paymentExternalKey, transactionAmount, transaction currency are extracted from  paymentControlContext which was update from the operation result.
        final OnSuccessPaymentControlResult result = controlPluginRunner.executePluginOnSuccessCalls(paymentStateContext.getAccount(),
                                                                                                     paymentStateContext.getPaymentMethodId(),
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

    private OperationResult executePluginOnFailureCallsAndSetRetryDate(final PaymentStateControlContext paymentStateControlContext, final PaymentControlContext paymentControlContext) {
        final DateTime retryDate = executePluginOnFailureCalls(paymentStateControlContext.getPaymentControlPluginNames(), paymentControlContext);
        if (retryDate != null) {
            ((PaymentStateControlContext) paymentStateContext).setRetryDate(retryDate);
        }
        return getOperationResultOnException(paymentStateContext);
    }

    private DateTime executePluginOnFailureCalls(final List<String> paymentControlPluginNames, final PaymentControlContext paymentControlContext) {

        final OnFailurePaymentControlResult result = controlPluginRunner.executePluginOnFailureCalls(paymentStateContext.getAccount(),
                                                                                                     paymentControlContext.getPaymentMethodId(),
                                                                                                     paymentStateControlContext.getAttemptId(),
                                                                                                     paymentStateContext.getPaymentId(),
                                                                                                     paymentStateContext.getPaymentExternalKey(),
                                                                                                     paymentStateContext.getPaymentTransactionExternalKey(),
                                                                                                     PaymentApiType.PAYMENT_TRANSACTION,
                                                                                                     paymentStateContext.getTransactionType(),
                                                                                                     null,
                                                                                                     paymentControlContext.getAmount(),
                                                                                                     paymentControlContext.getCurrency(),
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
