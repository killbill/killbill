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

package org.killbill.billing.payment.core.sm;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;

import org.joda.time.DateTime;
import org.killbill.automaton.MissingEntryException;
import org.killbill.automaton.Operation.OperationCallback;
import org.killbill.automaton.OperationException;
import org.killbill.automaton.State;
import org.killbill.automaton.State.EnteringStateCallback;
import org.killbill.automaton.State.LeavingStateCallback;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.control.plugin.api.PaymentControlApiException;
import org.killbill.billing.control.plugin.api.PaymentControlPluginApi;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.core.PaymentExecutors;
import org.killbill.billing.payment.core.PaymentPluginServiceRegistration;
import org.killbill.billing.payment.core.PaymentProcessor;
import org.killbill.billing.payment.core.PaymentRefresher;
import org.killbill.billing.payment.core.sm.control.AuthorizeControlOperation;
import org.killbill.billing.payment.core.sm.control.CaptureControlOperation;
import org.killbill.billing.payment.core.sm.control.ChargebackControlOperation;
import org.killbill.billing.payment.core.sm.control.ChargebackReversalControlOperation;
import org.killbill.billing.payment.core.sm.control.CompletionControlOperation;
import org.killbill.billing.payment.core.sm.control.ControlPluginRunner;
import org.killbill.billing.payment.core.sm.control.CreditControlOperation;
import org.killbill.billing.payment.core.sm.control.DefaultControlCompleted;
import org.killbill.billing.payment.core.sm.control.DefaultControlInitiated;
import org.killbill.billing.payment.core.sm.control.NoopControlInitiated;
import org.killbill.billing.payment.core.sm.control.NotificationOfStateChangeControlOperation;
import org.killbill.billing.payment.core.sm.control.PaymentStateControlContext;
import org.killbill.billing.payment.core.sm.control.PurchaseControlOperation;
import org.killbill.billing.payment.core.sm.control.RefundControlOperation;
import org.killbill.billing.payment.core.sm.control.VoidControlOperation;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.retry.BaseRetryService.RetryServiceScheduler;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.config.definition.PaymentConfig;
import org.killbill.bus.api.PersistentBus;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLocker;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;

import static org.killbill.billing.payment.glue.PaymentModule.RETRYABLE_NAMED;

public class PluginControlPaymentAutomatonRunner extends PaymentAutomatonRunner {

    public enum ControlOperation {
        AUTHORIZE,
        CAPTURE,
        CHARGEBACK,
        CHARGEBACK_REVERSAL,
        CREDIT,
        PURCHASE,
        REFUND,
        VOID,
        NOTIFICATION_OF_STATE_CHANGE
    }

    protected final OSGIServiceRegistration<PaymentControlPluginApi> paymentControlPluginRegistry;
    private final PaymentProcessor paymentProcessor;
    private final RetryServiceScheduler retryServiceScheduler;
    private final PaymentControlStateMachineHelper paymentControlStateMachineHelper;
    private final ControlPluginRunner controlPluginRunner;
    private final PaymentConfig paymentConfig;
    private final PaymentRefresher paymentRefresher;

    @Inject
    public PluginControlPaymentAutomatonRunner(final PaymentDao paymentDao, final GlobalLocker locker, final PaymentPluginServiceRegistration paymentPluginServiceRegistration,
                                               final OSGIServiceRegistration<PaymentControlPluginApi> paymentControlPluginRegistry, final Clock clock, final PaymentProcessor paymentProcessor, @Named(RETRYABLE_NAMED) final RetryServiceScheduler retryServiceScheduler,
                                               final PaymentConfig paymentConfig, final PaymentExecutors executors, final PaymentStateMachineHelper paymentSMHelper, final PaymentControlStateMachineHelper paymentControlStateMachineHelper,
                                               final ControlPluginRunner controlPluginRunner, final PersistentBus eventBus, final PaymentRefresher paymentRefresher) {
        super(paymentConfig, paymentDao, locker, paymentPluginServiceRegistration, clock, executors, eventBus, paymentSMHelper);
        this.paymentProcessor = paymentProcessor;
        this.paymentControlPluginRegistry = paymentControlPluginRegistry;
        this.retryServiceScheduler = retryServiceScheduler;
        this.paymentControlStateMachineHelper = paymentControlStateMachineHelper;
        this.controlPluginRunner = controlPluginRunner;
        this.paymentConfig = paymentConfig;
        this.paymentRefresher = paymentRefresher;
    }

    public Payment run(final boolean isApiPayment,
                       final TransactionType transactionType,
                       final ControlOperation controlOperation,
                       final Account account,
                       @Nullable final UUID paymentMethodId,
                       @Nullable final UUID paymentId,
                       @Nullable final String paymentExternalKey,
                       final String paymentTransactionExternalKey,
                       @Nullable final BigDecimal amount,
                       @Nullable final Currency currency,
                       @Nullable final DateTime effectiveDate,
                       final Iterable<PluginProperty> properties,
                       @Nullable final List<String> paymentControlPluginNames,
                       final CallContext callContext,
                       final InternalCallContext internalCallContext) throws PaymentApiException {
        return run(paymentControlStateMachineHelper.getInitialState(),
                   isApiPayment,
                   null,
                   transactionType,
                   controlOperation,
                   account,
                   paymentMethodId,
                   paymentId,
                   paymentExternalKey,
                   null,
                   paymentTransactionExternalKey,
                   amount,
                   currency,
                   effectiveDate,
                   properties,
                   paymentControlPluginNames,
                   callContext,
                   internalCallContext);
    }

    public Payment run(final boolean isApiPayment,
                       final Boolean isSuccess,
                       final TransactionType transactionType,
                       final ControlOperation controlOperation,
                       final Account account,
                       @Nullable final UUID paymentMethodId,
                       @Nullable final UUID paymentId,
                       @Nullable final String paymentExternalKey,
                       @Nullable final UUID transactionId,
                       final String paymentTransactionExternalKey,
                       @Nullable final BigDecimal amount,
                       @Nullable final Currency currency,
                       @Nullable final DateTime effectiveDate,
                       final Iterable<PluginProperty> properties,
                       @Nullable final List<String> paymentControlPluginNames,
                       final CallContext callContext,
                       final InternalCallContext internalCallContext) throws PaymentApiException {
        return run(paymentControlStateMachineHelper.getInitialState(),
                   isApiPayment,
                   isSuccess,
                   transactionType,
                   controlOperation,
                   account,
                   paymentMethodId,
                   paymentId,
                   paymentExternalKey,
                   transactionId,
                   paymentTransactionExternalKey,
                   amount,
                   currency,
                   effectiveDate,
                   properties,
                   paymentControlPluginNames,
                   callContext,
                   internalCallContext);
    }

    public Payment run(final State state,
                       final boolean isApiPayment,
                       final TransactionType transactionType,
                       final ControlOperation controlOperation,
                       final Account account,
                       @Nullable final UUID paymentMethodId,
                       @Nullable final UUID paymentId,
                       @Nullable final String paymentExternalKey,
                       final String paymentTransactionExternalKey,
                       @Nullable final BigDecimal amount,
                       @Nullable final Currency currency,
                       @Nullable final DateTime effectiveDate,
                       final Iterable<PluginProperty> properties,
                       @Nullable final List<String> paymentControlPluginNames,
                       final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return run(state,
                   isApiPayment,
                   null,
                   transactionType,
                   controlOperation,
                   account,
                   paymentMethodId,
                   paymentId,
                   paymentExternalKey,
                   null,
                   paymentTransactionExternalKey,
                   amount,
                   currency,
                   effectiveDate,
                   properties,
                   paymentControlPluginNames,
                   callContext,
                   internalCallContext);
    }

    public Payment run(final State state,
                       final boolean isApiPayment,
                       final Boolean isSuccess,
                       final TransactionType transactionType,
                       final ControlOperation controlOperation,
                       final Account account,
                       @Nullable final UUID paymentMethodId,
                       @Nullable final UUID paymentId,
                       @Nullable final String paymentExternalKey,
                       @Nullable final UUID transactionId,
                       final String paymentTransactionExternalKey,
                       @Nullable final BigDecimal amount,
                       @Nullable final Currency currency,
                       @Nullable final DateTime effectiveDate,
                       final Iterable<PluginProperty> properties,
                       @Nullable final List<String> paymentControlPluginNames,
                       final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        final PaymentStateControlContext paymentStateContext = createContext(isApiPayment,
                                                                             isSuccess,
                                                                             transactionType,
                                                                             account,
                                                                             paymentMethodId,
                                                                             paymentId,
                                                                             paymentExternalKey,
                                                                             transactionId,
                                                                             paymentTransactionExternalKey,
                                                                             amount,
                                                                             currency,
                                                                             effectiveDate,
                                                                             properties,
                                                                             paymentControlPluginNames,
                                                                             callContext,
                                                                             internalCallContext);
        try {
            final OperationCallback callback = createOperationCallback(controlOperation, paymentStateContext);
            final LeavingStateCallback leavingStateCallback = new DefaultControlInitiated(this, paymentStateContext, paymentDao, paymentControlStateMachineHelper.getInitialState(), paymentControlStateMachineHelper.getRetriedState(), transactionType);
            final EnteringStateCallback enteringStateCallback = new DefaultControlCompleted(this, paymentStateContext, paymentControlStateMachineHelper.getRetriedState(), retryServiceScheduler);

            state.runOperation(paymentControlStateMachineHelper.getOperation(), callback, enteringStateCallback, leavingStateCallback);
        } catch (final MissingEntryException e) {
            throw new PaymentApiException(e.getCause(), ErrorCode.PAYMENT_INTERNAL_ERROR, MoreObjects.firstNonNull(e.getMessage(), ""));
        } catch (final OperationException e) {
            if (e.getCause() instanceof PaymentApiException) {
                throw (PaymentApiException) e.getCause();
            // If the control plugin tries to pass us back a PaymentApiException we throw it
            } else if (e.getCause() instanceof PaymentControlApiException && e.getCause().getCause() instanceof PaymentApiException) {
                throw (PaymentApiException) e.getCause().getCause();
            } else if (e.getCause() != null || paymentStateContext.getResult() == null) {
                throw new PaymentApiException(e.getCause(), ErrorCode.PAYMENT_INTERNAL_ERROR, MoreObjects.firstNonNull(e.getMessage(), ""));
            }
        }
        // If the result is set (and cause is null), that means we created a Payment but the associated transaction status is 'XXX_FAILURE',
        // we don't throw, and return the failed Payment instead to be consistent with what happens when we don't go through control api.
        return paymentStateContext.getResult();
    }

    public Payment completeRun(final PaymentStateControlContext paymentStateContext) throws PaymentApiException {
        try {
            final OperationCallback callback = new CompletionControlOperation(locker, paymentPluginDispatcher, paymentConfig, paymentStateContext, paymentRefresher, paymentProcessor, controlPluginRunner);
            final LeavingStateCallback leavingStateCallback = new NoopControlInitiated();
            final EnteringStateCallback enteringStateCallback = new DefaultControlCompleted(this, paymentStateContext, paymentControlStateMachineHelper.getRetriedState(), retryServiceScheduler);

            paymentControlStateMachineHelper.getInitialState().runOperation(paymentControlStateMachineHelper.getOperation(), callback, enteringStateCallback, leavingStateCallback);
        } catch (final MissingEntryException e) {
            throw new PaymentApiException(e.getCause(), ErrorCode.PAYMENT_INTERNAL_ERROR, MoreObjects.firstNonNull(e.getMessage(), ""));
        } catch (final OperationException e) {
            if (e.getCause() instanceof PaymentApiException) {
                throw (PaymentApiException) e.getCause();
                // If the control plugin tries to pass us back a PaymentApiException we throw it
            } else if (e.getCause() instanceof PaymentControlApiException && e.getCause().getCause() instanceof PaymentApiException) {
                throw (PaymentApiException) e.getCause().getCause();
            } else if (e.getCause() != null || paymentStateContext.getResult() == null) {
                throw new PaymentApiException(e.getCause(), ErrorCode.PAYMENT_INTERNAL_ERROR, MoreObjects.firstNonNull(e.getMessage(), ""));
            }
        }
        // If the result is set (and cause is null), that means we created a Payment but the associated transaction status is 'XXX_FAILURE',
        // we don't throw, and return the failed Payment instead to be consistent with what happens when we don't go through control api.
        return paymentStateContext.getResult();
    }

    @VisibleForTesting
    PaymentStateControlContext createContext(final boolean isApiPayment, final Boolean isSuccess, final TransactionType transactionType, final Account account, @Nullable final UUID paymentMethodId,
                                             @Nullable final UUID paymentId, @Nullable final String paymentExternalKey, @Nullable final UUID transactionId, final String paymentTransactionExternalKey,
                                             @Nullable final BigDecimal amount, @Nullable final Currency currency, @Nullable final DateTime effectiveDate, final Iterable<PluginProperty> properties,
                                             final List<String> paymentControlPluginNames, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return new PaymentStateControlContext(paymentControlPluginNames, isApiPayment, isSuccess, paymentId, paymentExternalKey, transactionId, paymentTransactionExternalKey, transactionType, account,
                                              paymentMethodId, amount, currency, effectiveDate, properties, internalCallContext, callContext);
    }

    @VisibleForTesting
    OperationCallback createOperationCallback(final ControlOperation controlOperation, final PaymentStateControlContext paymentStateContext) {
        final OperationCallback callback;
        switch (controlOperation) {
            case AUTHORIZE:
                callback = new AuthorizeControlOperation(locker, paymentPluginDispatcher, paymentConfig, paymentStateContext, paymentProcessor, controlPluginRunner);
                break;
            case CAPTURE:
                callback = new CaptureControlOperation(locker, paymentPluginDispatcher, paymentConfig, paymentStateContext, paymentProcessor, controlPluginRunner);
                break;
            case PURCHASE:
                callback = new PurchaseControlOperation(locker, paymentPluginDispatcher, paymentConfig, paymentStateContext, paymentProcessor, controlPluginRunner);
                break;
            case VOID:
                callback = new VoidControlOperation(locker, paymentPluginDispatcher, paymentConfig, paymentStateContext, paymentProcessor, controlPluginRunner);
                break;
            case CREDIT:
                callback = new CreditControlOperation(locker, paymentPluginDispatcher, paymentConfig, paymentStateContext, paymentProcessor, controlPluginRunner);
                break;
            case REFUND:
                callback = new RefundControlOperation(locker, paymentPluginDispatcher, paymentConfig, paymentStateContext, paymentProcessor, controlPluginRunner);
                break;
            case CHARGEBACK:
                callback = new ChargebackControlOperation(locker, paymentPluginDispatcher, paymentConfig, paymentStateContext, paymentProcessor, controlPluginRunner);
                break;
            case CHARGEBACK_REVERSAL:
                callback = new ChargebackReversalControlOperation(locker, paymentPluginDispatcher, paymentConfig, paymentStateContext, paymentProcessor, controlPluginRunner);
                break;
            case NOTIFICATION_OF_STATE_CHANGE:
                callback = new NotificationOfStateChangeControlOperation(locker, paymentPluginDispatcher, paymentConfig, paymentStateContext, paymentProcessor, controlPluginRunner);
                break;
            default:
                throw new IllegalStateException("Unsupported control operation " + controlOperation);
        }
        return callback;
    }
}
