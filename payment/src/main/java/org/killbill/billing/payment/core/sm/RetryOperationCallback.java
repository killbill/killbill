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

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.automaton.Operation.OperationCallback;
import org.killbill.automaton.OperationException;
import org.killbill.automaton.OperationResult;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.DefaultCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentTransaction;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.core.PaymentProcessor;
import org.killbill.billing.payment.core.ProcessorBase.WithAccountLockCallback;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.billing.retry.plugin.api.FailureCallResult;
import org.killbill.billing.retry.plugin.api.PaymentControlApiException;
import org.killbill.billing.retry.plugin.api.PaymentControlContext;
import org.killbill.billing.retry.plugin.api.PaymentControlPluginApi;
import org.killbill.billing.retry.plugin.api.PriorPaymentControlResult;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.locker.LockFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class RetryOperationCallback extends OperationCallbackBase implements OperationCallback {

    private final OSGIServiceRegistration<PaymentControlPluginApi> paymentControlPluginRegistry;

    protected final PaymentProcessor paymentProcessor;
    protected final RetryablePaymentStateContext retryablePaymentStateContext;


    private final Logger logger = LoggerFactory.getLogger(RetryOperationCallback.class);

    protected RetryOperationCallback(final GlobalLocker locker, final PluginDispatcher<OperationResult> paymentPluginDispatcher, final RetryablePaymentStateContext paymentStateContext, final PaymentProcessor paymentProcessor, final OSGIServiceRegistration<PaymentControlPluginApi> retryPluginRegistry) {
        super(locker, paymentPluginDispatcher, paymentStateContext);
        this.paymentProcessor = paymentProcessor;
        this.paymentControlPluginRegistry = retryPluginRegistry;
        this.retryablePaymentStateContext = paymentStateContext;
    }


    @Override
    protected abstract Payment doCallSpecificOperationCallback() throws PaymentApiException;

    @Override
    public OperationResult doOperationCallback() throws OperationException {

        return dispatchWithAccountLockAndTimeout(new WithAccountLockCallback<OperationResult, OperationException>() {

            @Override
            public OperationResult doOperation() throws OperationException {

                final PaymentControlContext paymentControlContext = new DefaultPaymentControlContext(paymentStateContext.getAccount(),
                                                                                                     paymentStateContext.getPaymentMethodId(),
                                                                                                     retryablePaymentStateContext.getAttemptId(),
                                                                                                     paymentStateContext.getPaymentId(),
                                                                                                     paymentStateContext.getPaymentExternalKey(),
                                                                                                     paymentStateContext.getPaymentTransactionExternalKey(),
                                                                                                     paymentStateContext.getTransactionType(),
                                                                                                     paymentStateContext.getAmount(),
                                                                                                     paymentStateContext.getCurrency(),
                                                                                                     paymentStateContext.getProperties(),
                                                                                                     retryablePaymentStateContext.isApiPayment(),
                                                                                                     paymentStateContext.callContext);

                final PriorPaymentControlResult pluginResult;
                try {
                    pluginResult = getPluginResult(retryablePaymentStateContext.getPluginName(), paymentControlContext);
                    if (pluginResult.isAborted()) {
                        // Transition to ABORTED
                        return OperationResult.EXCEPTION;
                    }
                } catch (PaymentControlApiException e) {
                    // Transition to ABORTED and throw PaymentControlApiException to caller.
                    throw new OperationException(e, OperationResult.EXCEPTION);
                }

                boolean success;
                try {
                    // Adjust amount with value returned by plugin if necessary
                    if (paymentStateContext.getAmount() == null ||
                        (pluginResult.getAdjustedAmount() != null && pluginResult.getAdjustedAmount().compareTo(paymentStateContext.getAmount()) != 0)) {
                        ((RetryablePaymentStateContext) paymentStateContext).setAmount(pluginResult.getAdjustedAmount());
                    }

                    final Payment result = doCallSpecificOperationCallback();
                    ((RetryablePaymentStateContext) paymentStateContext).setResult(result);
                    final PaymentTransaction transaction = ((RetryablePaymentStateContext) paymentStateContext).getCurrentTransaction();

                    success = transaction.getTransactionStatus() == TransactionStatus.SUCCESS || transaction.getTransactionStatus() == TransactionStatus.PENDING;
                    if (success) {
                        final PaymentControlContext updatedPaymentControlContext = new DefaultPaymentControlContext(paymentStateContext.account,
                                                                                                                    paymentStateContext.paymentMethodId,
                                                                                                                    retryablePaymentStateContext.getAttemptId(),
                                                                                                                    result.getId(),
                                                                                                                    result.getExternalKey(),
                                                                                                                    transaction.getId(),
                                                                                                                    paymentStateContext.getPaymentTransactionExternalKey(),
                                                                                                                    paymentStateContext.getTransactionType(),
                                                                                                                    transaction.getAmount(),
                                                                                                                    transaction.getCurrency(),
                                                                                                                    transaction.getProcessedAmount(),
                                                                                                                    transaction.getProcessedCurrency(),
                                                                                                                    paymentStateContext.properties,
                                                                                                                    retryablePaymentStateContext.isApiPayment(),
                                                                                                                    paymentStateContext.callContext);

                        onCompletion(retryablePaymentStateContext.getPluginName(), updatedPaymentControlContext);
                        return OperationResult.SUCCESS;
                    } else {
                        throw new OperationException(null, getOperationResultAndSetContext(retryablePaymentStateContext, paymentControlContext));
                    }
                } catch (PaymentApiException e) {
                    // Wrap PaymentApiException, and throw a new OperationException with an ABORTED/FAILURE state based on the retry result.
                    throw new OperationException(e, getOperationResultAndSetContext(retryablePaymentStateContext, paymentControlContext));
                } catch (RuntimeException e) {
                    // Attempts to set the retry date in context if needed.
                    getOperationResultAndSetContext(retryablePaymentStateContext, paymentControlContext);
                    throw e;
                }
            }
        });
    }

    @Override
    protected OperationException rewrapExecutionException(final PaymentStateContext paymentStateContext, final ExecutionException e) {
        if (e.getCause() instanceof OperationException) {
            return (OperationException) e.getCause();
        } else if (e.getCause() instanceof LockFailedException) {
            final String format = String.format("Failed to lock account %s", paymentStateContext.getAccount().getExternalKey());
            logger.error(String.format(format), e);
            return new OperationException(e, getOperationResultOnException(paymentStateContext));
        } else /* most probably RuntimeException */ {
            logger.warn("RetryOperationCallback failed for account {}", paymentStateContext.getAccount().getExternalKey(), e);
            return new OperationException(e, getOperationResultOnException(paymentStateContext));
        }
    }

    @Override
    protected OperationException wrapTimeoutException(final PaymentStateContext paymentStateContext, final TimeoutException e) {
        logger.error("RetryOperationCallback call TIMEOUT for account {}: {}", paymentStateContext.getAccount().getExternalKey(), e.getMessage());
        return new OperationException(e, getOperationResultOnException(paymentStateContext));
    }

    @Override
    protected OperationException wrapInterruptedException(final PaymentStateContext paymentStateContext, final InterruptedException e) {
        logger.error("RetryOperationCallback call was interrupted for account {}: {}", paymentStateContext.getAccount().getExternalKey(), e.getMessage());
        return new OperationException(e, getOperationResultOnException(paymentStateContext));
    }

    protected void onCompletion(final String pluginName, final PaymentControlContext paymentControlContext) {
        final PaymentControlPluginApi plugin = paymentControlPluginRegistry.getServiceForName(pluginName);
        try {
            plugin.onSuccessCall(paymentControlContext);
        } catch (PaymentControlApiException e) {
            logger.warn("Plugin " + pluginName + " failed to complete onCompletion call for " + paymentControlContext.getPaymentExternalKey(), e);
        }
    }

    private OperationResult getOperationResultOnException(final PaymentStateContext paymentStateContext) {
        final RetryablePaymentStateContext retryablePaymentStateContext = (RetryablePaymentStateContext) paymentStateContext;
        final OperationResult operationResult = retryablePaymentStateContext.getRetryDate() != null ? OperationResult.FAILURE : OperationResult.EXCEPTION;
        return operationResult;
    }

    private PriorPaymentControlResult getPluginResult(final String pluginName, final PaymentControlContext paymentControlContext) throws PaymentControlApiException {

        final PaymentControlPluginApi plugin = paymentControlPluginRegistry.getServiceForName(pluginName);
        final PriorPaymentControlResult result = plugin.priorCall(paymentControlContext);
        return result;
    }

    private OperationResult getOperationResultAndSetContext(final RetryablePaymentStateContext retryablePaymentStateContext, final PaymentControlContext paymentControlContext) {
        final DateTime retryDate = getNextRetryDate(retryablePaymentStateContext.getPluginName(), paymentControlContext);
        if (retryDate != null) {
            ((RetryablePaymentStateContext) paymentStateContext).setRetryDate(retryDate);
            return OperationResult.FAILURE;
        } else {
            return OperationResult.EXCEPTION;
        }
    }

    private DateTime getNextRetryDate(final String pluginName, final PaymentControlContext paymentControlContext) {
        try {
            final PaymentControlPluginApi plugin = paymentControlPluginRegistry.getServiceForName(pluginName);
            final FailureCallResult result = plugin.onFailureCall(paymentControlContext);
            return result.getNextRetryDate();
        } catch (PaymentControlApiException e) {
            logger.warn("Plugin " + pluginName + " failed to return next retryDate for payment " + paymentControlContext.getPaymentExternalKey(), e);
            return null;
        }
    }


    public static class DefaultPaymentControlContext extends DefaultCallContext implements PaymentControlContext {

        private final Account account;
        private final UUID paymentMethodId;
        private final UUID attemptId;
        private final UUID paymentId;
        private final String paymentExternalKey;
        private final UUID transactionId;
        private final String transactionExternalKey;
        private final TransactionType transactionType;
        private final BigDecimal amount;
        private final Currency currency;
        private final BigDecimal processedAmount;
        private final Currency processedCurrency;
        private final boolean isApiPayment;
        private final Iterable<PluginProperty> properties;

        public DefaultPaymentControlContext(final Account account, final UUID paymentMethodId, final UUID attemptId, @Nullable final UUID paymentId, final String paymentExternalKey, final String transactionExternalKey, final TransactionType transactionType, final BigDecimal amount, final Currency currency,
                                            final Iterable<PluginProperty> properties, final boolean isApiPayment, final CallContext callContext) {
            this(account, paymentMethodId, attemptId, paymentId, paymentExternalKey, null, transactionExternalKey, transactionType, amount, currency, null, null, properties, isApiPayment, callContext);
        }

        public DefaultPaymentControlContext(final Account account, final UUID paymentMethodId, final UUID attemptId, @Nullable final UUID paymentId, final String paymentExternalKey, @Nullable final UUID transactionId, final String transactionExternalKey, final TransactionType transactionType,
                                            final BigDecimal amount, final Currency currency, @Nullable final BigDecimal processedAmount, @Nullable final Currency processedCurrency, final Iterable<PluginProperty> properties, final boolean isApiPayment, final CallContext callContext) {
            super(callContext.getTenantId(), callContext.getUserName(), callContext.getCallOrigin(), callContext.getUserType(), callContext.getReasonCode(), callContext.getComments(), callContext.getUserToken(), callContext.getCreatedDate(), callContext.getUpdatedDate());
            this.account = account;
            this.paymentMethodId = paymentMethodId;
            this.attemptId = attemptId;
            this.paymentId = paymentId;
            this.paymentExternalKey = paymentExternalKey;
            this.transactionId = transactionId;
            this.transactionExternalKey = transactionExternalKey;
            this.transactionType = transactionType;
            this.amount = amount;
            this.currency = currency;
            this.processedAmount = processedAmount;
            this.processedCurrency = processedCurrency;
            this.properties = properties;
            this.isApiPayment = isApiPayment;
        }

        @Override
        public UUID getAccountId() {
            return account.getId();
        }

        @Override
        public String getPaymentExternalKey() {
            return paymentExternalKey;
        }

        @Override
        public String getTransactionExternalKey() {
            return transactionExternalKey;
        }

        @Override
        public TransactionType getTransactionType() {
            return transactionType;
        }

        @Override
        public BigDecimal getAmount() {
            return amount;
        }

        @Override
        public Currency getCurrency() {
            return currency;
        }

        @Override
        public UUID getPaymentMethodId() {
            return paymentMethodId;
        }

        @Override
        public UUID getPaymentId() {
            return paymentId;
        }

        @Override
        public UUID getAttemptPaymentId() {
            return attemptId;
        }

        @Override
        public BigDecimal getProcessedAmount() {
            return processedAmount;
        }

        @Override
        public Currency getProcessedCurrency() {
            return processedCurrency;
        }

        @Override
        public boolean isApiPayment() {
            return isApiPayment;
        }


        public UUID getTransactionId() {
            return transactionId;
        }


        @Override
        public Iterable<PluginProperty> getPluginProperties() {
            return properties;
        }
    }
}
