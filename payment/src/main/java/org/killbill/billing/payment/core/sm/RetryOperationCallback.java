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

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.automaton.Operation.OperationCallback;
import org.killbill.automaton.OperationException;
import org.killbill.automaton.OperationResult;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.DefaultCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.DirectPayment;
import org.killbill.billing.payment.api.DirectPaymentTransaction;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.core.DirectPaymentProcessor;
import org.killbill.billing.payment.core.ProcessorBase.WithAccountLockCallback;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.billing.retry.plugin.api.FailureCallResult;
import org.killbill.billing.retry.plugin.api.PaymentControlApiException;
import org.killbill.billing.retry.plugin.api.PaymentControlContext;
import org.killbill.billing.retry.plugin.api.PaymentControlPluginApi;
import org.killbill.billing.retry.plugin.api.PriorPaymentControlResult;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.commons.locker.GlobalLocker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class RetryOperationCallback extends PluginOperation implements OperationCallback {

    protected final DirectPaymentProcessor directPaymentProcessor;
    private final OSGIServiceRegistration<PaymentControlPluginApi> paymentControlPluginRegistry;

    private final Logger logger = LoggerFactory.getLogger(RetryOperationCallback.class);

    protected RetryOperationCallback(final GlobalLocker locker, final PluginDispatcher<OperationResult> paymentPluginDispatcher, final RetryableDirectPaymentStateContext directPaymentStateContext, final DirectPaymentProcessor directPaymentProcessor, final OSGIServiceRegistration<PaymentControlPluginApi> retryPluginRegistry) {
        super(locker, paymentPluginDispatcher, directPaymentStateContext);
        this.directPaymentProcessor = directPaymentProcessor;
        this.paymentControlPluginRegistry = retryPluginRegistry;
    }

    private PriorPaymentControlResult getPluginResult(final String pluginName, final PaymentControlContext paymentControlContext) throws PaymentControlApiException {

        final PaymentControlPluginApi plugin = paymentControlPluginRegistry.getServiceForName(pluginName);
        final PriorPaymentControlResult result = plugin.priorCall(paymentControlContext);
        return result;
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

    private void onCompletion(final String pluginName, final PaymentControlContext paymentControlContext) {
        final PaymentControlPluginApi plugin = paymentControlPluginRegistry.getServiceForName(pluginName);
        try {
            plugin.onSuccessCall(paymentControlContext);
        } catch (PaymentControlApiException e) {
            logger.warn("Plugin " + pluginName + " failed to complete onCompletion call for " + paymentControlContext.getPaymentExternalKey(), e);
        }
    }

    @Override
    public OperationResult doOperationCallback() throws OperationException {

        return dispatchWithTimeout(new WithAccountLockCallback<OperationResult>() {

            @Override
            public OperationResult doOperation() throws OperationException {

                final RetryableDirectPaymentStateContext retryableDirectPaymentStateContext = (RetryableDirectPaymentStateContext) directPaymentStateContext;
                final PaymentControlContext paymentControlContext = new DefaultPaymentControlContext(directPaymentStateContext.account,
                                                                                                     directPaymentStateContext.paymentMethodId,
                                                                                                     directPaymentStateContext.directPaymentId,
                                                                                                     directPaymentStateContext.directPaymentExternalKey,
                                                                                                     directPaymentStateContext.directPaymentTransactionExternalKey,
                                                                                                     directPaymentStateContext.transactionType,
                                                                                                     directPaymentStateContext.amount,
                                                                                                     directPaymentStateContext.currency,
                                                                                                     directPaymentStateContext.properties,
                                                                                                     retryableDirectPaymentStateContext.isApiPayment(),
                                                                                                     directPaymentStateContext.callContext);

                // Note that we are using OperationResult.EXCEPTION result to transition to final ABORTED state -- see RetryStates.xml
                final PriorPaymentControlResult pluginResult;
                try {
                    pluginResult = getPluginResult(retryableDirectPaymentStateContext.getPluginName(), paymentControlContext);
                    if (pluginResult.isAborted()) {
                        return OperationResult.EXCEPTION;
                    }
                } catch (PaymentControlApiException e) {
                    throw new OperationException(e, OperationResult.EXCEPTION);
                }

                boolean success = false;
                try {
                    // Adjust amount with value returned by plugin if necessary
                    if (directPaymentStateContext.getAmount() == null ||
                        (pluginResult.getAdjustedAmount() != null && pluginResult.getAdjustedAmount().compareTo(directPaymentStateContext.getAmount()) != 0)) {
                        ((RetryableDirectPaymentStateContext) directPaymentStateContext).setAmount(pluginResult.getAdjustedAmount());
                    }

                    final DirectPayment result = doPluginOperation();
                    ((RetryableDirectPaymentStateContext) directPaymentStateContext).setResult(result);
                    final DirectPaymentTransaction transaction = ((RetryableDirectPaymentStateContext) directPaymentStateContext).getCurrentTransaction();

                    success = transaction.getTransactionStatus() == TransactionStatus.SUCCESS || transaction.getTransactionStatus() == TransactionStatus.PENDING;
                    if (success) {
                        final PaymentControlContext updatedPaymentControlContext = new DefaultPaymentControlContext(directPaymentStateContext.account,
                                                                                                                    directPaymentStateContext.paymentMethodId,
                                                                                                                    result.getId(),
                                                                                                                    result.getExternalKey(),
                                                                                                                    directPaymentStateContext.directPaymentTransactionExternalKey,
                                                                                                                    directPaymentStateContext.transactionType,
                                                                                                                    transaction.getAmount(),
                                                                                                                    transaction.getCurrency(),
                                                                                                                    transaction.getProcessedAmount(),
                                                                                                                    transaction.getProcessedCurrency(),
                                                                                                                    directPaymentStateContext.properties,
                                                                                                                    retryableDirectPaymentStateContext.isApiPayment(),
                                                                                                                    directPaymentStateContext.callContext);

                        onCompletion(retryableDirectPaymentStateContext.getPluginName(), updatedPaymentControlContext);
                    } else {
                        // Error code?
                        throwAndupdateRetryDateOnFailureOrException(retryableDirectPaymentStateContext, paymentControlContext, new PaymentApiException(ErrorCode.PAYMENT_INTERNAL_ERROR, "Plugin ERROR"));
                    }

                } catch (PaymentApiException e) {
                    throwAndupdateRetryDateOnFailureOrException(retryableDirectPaymentStateContext, paymentControlContext, e);
                } catch (OperationException e) {
                    // We need this catch clause to make sure this is not caught by the next more generic clause Exception
                    throw e;
                } catch (Exception e) {
                    // STEPH Any other exception we abort the retry logic, unclear if this is the *right* approach..
                    throw new OperationException(e, OperationResult.EXCEPTION);
                }
                return OperationResult.SUCCESS;
            }

            private void throwAndupdateRetryDateOnFailureOrException(final RetryableDirectPaymentStateContext retryableDirectPaymentStateContext, final PaymentControlContext paymentControlContext,
                                                                     @Nullable final PaymentApiException e) throws OperationException {
                final DateTime retryDate = getNextRetryDate(retryableDirectPaymentStateContext.getPluginName(), paymentControlContext);
                if (retryDate == null) {
                    // STEPH only throw if e is not null
                    throw new OperationException(e, OperationResult.EXCEPTION);
                } else {
                    // STEPH only throw if e is not null
                    ((RetryableDirectPaymentStateContext) directPaymentStateContext).setRetryDate(retryDate);
                    throw new OperationException(e, OperationResult.FAILURE);
                }
            }
        });
    }

    public class DefaultPaymentControlContext extends DefaultCallContext implements PaymentControlContext {

        private final Account account;
        private final UUID paymentId;
        private final UUID paymentMethodId;
        private final String paymentExternalKey;
        private final String transactionExternalKey;
        private final TransactionType transactionType;
        private final BigDecimal amount;
        private final Currency currency;
        private final BigDecimal processedAmount;
        private final Currency processedCurrency;
        private final boolean isApiPayment;
        private final Iterable<PluginProperty> properties;

        public DefaultPaymentControlContext(final Account account, final UUID paymentMethodId, @Nullable final UUID paymentId, final String paymentExternalKey, final String transactionExternalKey, final TransactionType transactionType, final BigDecimal amount, final Currency currency,
                                            final Iterable<PluginProperty> properties, final boolean isApiPayment, final CallContext callContext) {
            this(account, paymentMethodId, paymentId, paymentExternalKey, transactionExternalKey, transactionType, amount, currency, null, null, properties, isApiPayment, callContext);
        }

        public DefaultPaymentControlContext(final Account account, final UUID paymentMethodId, @Nullable final UUID paymentId, final String paymentExternalKey, final String transactionExternalKey, final TransactionType transactionType,
                                            final BigDecimal amount, final Currency currency, @Nullable final BigDecimal processedAmount, @Nullable final Currency processedCurrency, final Iterable<PluginProperty> properties, final boolean isApiPayment, final CallContext callContext) {
            super(callContext.getTenantId(), callContext.getUserName(), callContext.getCallOrigin(), callContext.getUserType(), callContext.getReasonCode(), callContext.getComments(), callContext.getUserToken(), callContext.getCreatedDate(), callContext.getUpdatedDate());
            this.account = account;
            this.paymentId = paymentId;
            this.paymentMethodId = paymentMethodId;
            this.paymentExternalKey = paymentExternalKey;
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

        @Override
        public Iterable<PluginProperty> getPluginProperties() {
            return properties;
        }
    }
}
