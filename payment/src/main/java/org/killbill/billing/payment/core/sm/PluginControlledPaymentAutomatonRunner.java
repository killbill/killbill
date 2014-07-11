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
import java.util.concurrent.ExecutorService;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;

import org.killbill.automaton.MissingEntryException;
import org.killbill.automaton.Operation.OperationCallback;
import org.killbill.automaton.OperationException;
import org.killbill.automaton.State;
import org.killbill.automaton.State.EnteringStateCallback;
import org.killbill.automaton.State.LeavingStateCallback;
import org.killbill.automaton.StateMachineConfig;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.core.PaymentProcessor;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.glue.PaymentModule;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.retry.BaseRetryService.RetryServiceScheduler;
import org.killbill.billing.retry.plugin.api.PaymentControlPluginApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.config.PaymentConfig;
import org.killbill.bus.api.PersistentBus;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLocker;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;

import static org.killbill.billing.payment.glue.PaymentModule.PLUGIN_EXECUTOR_NAMED;
import static org.killbill.billing.payment.glue.PaymentModule.RETRYABLE_NAMED;

public class PluginControlledPaymentAutomatonRunner extends PaymentAutomatonRunner {

    private final PaymentProcessor paymentProcessor;
    private final RetryServiceScheduler retryServiceScheduler;

    private final RetryStateMachineHelper retrySMHelper;

    protected final OSGIServiceRegistration<PaymentControlPluginApi> paymentControlPluginRegistry;

    @Inject
    public PluginControlledPaymentAutomatonRunner(@Named(PaymentModule.STATE_MACHINE_PAYMENT) final StateMachineConfig stateMachineConfig, final PaymentDao paymentDao, final GlobalLocker locker, final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry,
                                                  final OSGIServiceRegistration<PaymentControlPluginApi> retryPluginRegistry, final Clock clock, final PaymentProcessor paymentProcessor, @Named(RETRYABLE_NAMED) final RetryServiceScheduler retryServiceScheduler,
                                                  final PaymentConfig paymentConfig, @com.google.inject.name.Named(PLUGIN_EXECUTOR_NAMED) final ExecutorService executor, PaymentStateMachineHelper paymentSMHelper, RetryStateMachineHelper retrySMHelper, final PersistentBus eventBus) {
        super(stateMachineConfig, paymentConfig, paymentDao, locker, pluginRegistry, clock, executor, eventBus, paymentSMHelper);
        this.paymentProcessor = paymentProcessor;
        this.paymentControlPluginRegistry = retryPluginRegistry;
        this.retryServiceScheduler = retryServiceScheduler;
        this.retrySMHelper = retrySMHelper;
    }

    public Payment run(final boolean isApiPayment, final TransactionType transactionType, final Account account, @Nullable final UUID paymentMethodId,
                       @Nullable final UUID paymentId, @Nullable final String paymentExternalKey, final String paymentTransactionExternalKey,
                       @Nullable final BigDecimal amount, @Nullable final Currency currency,
                       final Iterable<PluginProperty> properties, @Nullable final String pluginName,
                       final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return run(retrySMHelper.getInitialState(), isApiPayment, transactionType, account, paymentMethodId, paymentId, paymentExternalKey, paymentTransactionExternalKey,
                   amount, currency, properties, pluginName, callContext, internalCallContext);
    }

    public Payment run(final State state, final boolean isApiPayment, final TransactionType transactionType, final Account account, @Nullable final UUID paymentMethodId,
                       @Nullable final UUID paymentId, @Nullable final String paymentExternalKey, final String paymentTransactionExternalKey,
                       @Nullable final BigDecimal amount, @Nullable final Currency currency,
                       final Iterable<PluginProperty> properties, @Nullable final String pluginName,
                       final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {

        final RetryablePaymentStateContext paymentStateContext = createContext(isApiPayment, transactionType, account, paymentMethodId,
                                                                                     paymentId, paymentExternalKey,
                                                                                     paymentTransactionExternalKey,
                                                                                     amount, currency,
                                                                                     properties, pluginName, callContext, internalCallContext);
        try {

            final OperationCallback callback = createOperationCallback(transactionType, paymentStateContext);
            final LeavingStateCallback leavingStateCallback = new RetryLeavingStateCallback(this, paymentStateContext, paymentDao, retrySMHelper.getInitialState(), retrySMHelper.getRetriedState(), transactionType);
            final EnteringStateCallback enteringStateCallback = new RetryEnteringStateCallback(this, paymentStateContext, retryServiceScheduler);

            state.runOperation(retrySMHelper.getRetryOperation(), callback, enteringStateCallback, leavingStateCallback);

        } catch (MissingEntryException e) {
            throw new PaymentApiException(e.getCause(), ErrorCode.PAYMENT_INTERNAL_ERROR, Objects.firstNonNull(e.getMessage(), ""));
        } catch (OperationException e) {
            if (e.getCause() == null) {
                throw new PaymentApiException(e, ErrorCode.PAYMENT_INTERNAL_ERROR, Objects.firstNonNull(e.getMessage(), ""));
            } else if (e.getCause() instanceof PaymentApiException) {
                throw (PaymentApiException) e.getCause();
            } else {
                throw new PaymentApiException(e.getCause(), ErrorCode.PAYMENT_INTERNAL_ERROR, Objects.firstNonNull(e.getMessage(), ""));
            }
        }
        return paymentStateContext.getResult();
    }

    public Payment completeRun(final RetryablePaymentStateContext paymentStateContext) throws PaymentApiException {

        try {

            final OperationCallback callback = new RetryCompletionOperationCallback(locker, paymentPluginDispatcher, paymentStateContext, paymentProcessor, paymentControlPluginRegistry);
            final LeavingStateCallback leavingStateCallback = new RetryNoopLeavingStateCallback();
            final EnteringStateCallback enteringStateCallback = new RetryEnteringStateCallback(this, paymentStateContext, retryServiceScheduler);

            retrySMHelper.getInitialState().runOperation(retrySMHelper.getRetryOperation(), callback, enteringStateCallback, leavingStateCallback);

        } catch (MissingEntryException e) {
            throw new PaymentApiException(e.getCause(), ErrorCode.PAYMENT_INTERNAL_ERROR, Objects.firstNonNull(e.getMessage(), ""));
        } catch (OperationException e) {
            if (e.getCause() == null) {
                throw new PaymentApiException(e, ErrorCode.PAYMENT_INTERNAL_ERROR, Objects.firstNonNull(e.getMessage(), ""));
            } else if (e.getCause() instanceof PaymentApiException) {
                throw (PaymentApiException) e.getCause();
            } else {
                throw new PaymentApiException(e.getCause(), ErrorCode.PAYMENT_INTERNAL_ERROR, Objects.firstNonNull(e.getMessage(), ""));
            }
        }
        return paymentStateContext.getResult();
    }

    @VisibleForTesting
    RetryablePaymentStateContext createContext(final boolean isApiPayment, final TransactionType transactionType, final Account account, @Nullable final UUID paymentMethodId,
                                               @Nullable final UUID paymentId, @Nullable final String paymentExternalKey, final String paymentTransactionExternalKey,
                                               @Nullable final BigDecimal amount, @Nullable final Currency currency, final Iterable<PluginProperty> properties,
                                               final String pluginName, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return new RetryablePaymentStateContext(pluginName, isApiPayment, paymentId, paymentExternalKey, paymentTransactionExternalKey, transactionType, account,
                                                paymentMethodId, amount, currency, properties, internalCallContext, callContext);
    }

    @VisibleForTesting
    OperationCallback createOperationCallback(final TransactionType transactionType, final RetryablePaymentStateContext paymentStateContext) {
        final OperationCallback callback;
        switch (transactionType) {
            case AUTHORIZE:
                callback = new RetryAuthorizeOperationCallback(locker, paymentPluginDispatcher, paymentStateContext, paymentProcessor, paymentControlPluginRegistry);
                break;
            case CAPTURE:
                callback = new RetryCaptureOperationCallback(locker, paymentPluginDispatcher, paymentStateContext, paymentProcessor, paymentControlPluginRegistry);
                break;
            case PURCHASE:
                callback = new RetryPurchaseOperationCallback(locker, paymentPluginDispatcher, paymentStateContext, paymentProcessor, paymentControlPluginRegistry);
                break;
            case VOID:
                callback = new RetryVoidOperationCallback(locker, paymentPluginDispatcher, paymentStateContext, paymentProcessor, paymentControlPluginRegistry);
                break;
            case CREDIT:
                callback = new RetryCreditOperationCallback(locker, paymentPluginDispatcher, paymentStateContext, paymentProcessor, paymentControlPluginRegistry);
                break;
            case REFUND:
                callback = new RetryRefundOperationCallback(locker, paymentPluginDispatcher, paymentStateContext, paymentProcessor, paymentControlPluginRegistry);
                break;
            case CHARGEBACK:
                callback = new RetryChargebackOperationCallback(locker, paymentPluginDispatcher, paymentStateContext, paymentProcessor, paymentControlPluginRegistry);
                break;
            default:
                throw new IllegalStateException("Unsupported transaction type " + transactionType);
        }
        return callback;
    }
}
