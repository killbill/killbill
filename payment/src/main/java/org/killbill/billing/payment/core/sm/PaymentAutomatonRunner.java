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
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.joda.time.DateTime;
import org.killbill.automaton.MissingEntryException;
import org.killbill.automaton.Operation;
import org.killbill.automaton.Operation.OperationCallback;
import org.killbill.automaton.OperationException;
import org.killbill.automaton.OperationResult;
import org.killbill.automaton.State;
import org.killbill.automaton.State.EnteringStateCallback;
import org.killbill.automaton.State.LeavingStateCallback;
import org.killbill.automaton.StateMachine;
import org.killbill.automaton.StateMachineConfig;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.core.PaymentExecutors;
import org.killbill.billing.payment.core.PaymentPluginServiceRegistration;
import org.killbill.billing.payment.core.sm.payments.AuthorizeCompleted;
import org.killbill.billing.payment.core.sm.payments.AuthorizeInitiated;
import org.killbill.billing.payment.core.sm.payments.AuthorizeOperation;
import org.killbill.billing.payment.core.sm.payments.CaptureCompleted;
import org.killbill.billing.payment.core.sm.payments.CaptureInitiated;
import org.killbill.billing.payment.core.sm.payments.CaptureOperation;
import org.killbill.billing.payment.core.sm.payments.ChargebackCompleted;
import org.killbill.billing.payment.core.sm.payments.ChargebackInitiated;
import org.killbill.billing.payment.core.sm.payments.ChargebackOperation;
import org.killbill.billing.payment.core.sm.payments.CreditCompleted;
import org.killbill.billing.payment.core.sm.payments.CreditInitiated;
import org.killbill.billing.payment.core.sm.payments.CreditOperation;
import org.killbill.billing.payment.core.sm.payments.PurchaseCompleted;
import org.killbill.billing.payment.core.sm.payments.PurchaseInitiated;
import org.killbill.billing.payment.core.sm.payments.PurchaseOperation;
import org.killbill.billing.payment.core.sm.payments.RefundCompleted;
import org.killbill.billing.payment.core.sm.payments.RefundInitiated;
import org.killbill.billing.payment.core.sm.payments.RefundOperation;
import org.killbill.billing.payment.core.sm.payments.VoidCompleted;
import org.killbill.billing.payment.core.sm.payments.VoidInitiated;
import org.killbill.billing.payment.core.sm.payments.VoidOperation;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dao.PaymentModelDao;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.config.definition.PaymentConfig;
import org.killbill.bus.api.PersistentBus;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLocker;

import com.google.common.base.MoreObjects;

public class PaymentAutomatonRunner {

    protected final PaymentStateMachineHelper paymentSMHelper;
    protected final PaymentDao paymentDao;
    protected final GlobalLocker locker;
    protected final PluginDispatcher<OperationResult> paymentPluginDispatcher;
    protected final PaymentPluginServiceRegistration paymentPluginServiceRegistration;
    protected final Clock clock;

    private final PersistentBus eventBus;
    private final PaymentConfig paymentConfig;

    @Inject
    public PaymentAutomatonRunner(final PaymentConfig paymentConfig,
                                  final PaymentDao paymentDao,
                                  final GlobalLocker locker,
                                  final PaymentPluginServiceRegistration paymentPluginServiceRegistration,
                                  final Clock clock,
                                  final PaymentExecutors executors,
                                  final PersistentBus eventBus,
                                  final PaymentStateMachineHelper paymentSMHelper) {
        this.paymentSMHelper = paymentSMHelper;
        this.paymentDao = paymentDao;
        this.locker = locker;
        this.paymentPluginServiceRegistration = paymentPluginServiceRegistration;
        this.clock = clock;
        this.eventBus = eventBus;
        this.paymentConfig = paymentConfig;
        final long paymentPluginTimeoutSec = TimeUnit.SECONDS.convert(paymentConfig.getPaymentPluginTimeout().getPeriod(), paymentConfig.getPaymentPluginTimeout().getUnit());
        this.paymentPluginDispatcher = new PluginDispatcher<OperationResult>(paymentPluginTimeoutSec, executors);
    }

    public PaymentStateContext buildPaymentStateContext(final boolean isApiPayment,
                                                        final TransactionType transactionType,
                                                        final Account account,
                                                        @Nullable final UUID attemptId,
                                                        @Nullable final UUID paymentMethodId,
                                                        @Nullable final UUID paymentId,
                                                        @Nullable final UUID transactionId,
                                                        @Nullable final String paymentExternalKey,
                                                        final String paymentTransactionExternalKey,
                                                        @Nullable final BigDecimal amount,
                                                        @Nullable final Currency currency,
                                                        final DateTime effectiveDate,
                                                        @Nullable final UUID paymentIdForNewPayment,
                                                        @Nullable final UUID paymentTransactionIdForNewPaymentTransaction,
                                                        final boolean shouldLockAccount,
                                                        final OperationResult overridePluginOperationResult,
                                                        final Iterable<PluginProperty> properties,
                                                        final CallContext callContext,
                                                        final InternalCallContext internalCallContext) throws PaymentApiException {

        // Retrieve the payment id from the payment external key if needed
        final UUID effectivePaymentId = paymentId != null ? paymentId : retrievePaymentId(paymentExternalKey, paymentTransactionExternalKey, internalCallContext);

        return new PaymentStateContext(isApiPayment,
                                       effectivePaymentId,
                                       transactionId,
                                       attemptId,
                                       paymentExternalKey,
                                       paymentTransactionExternalKey,
                                       transactionType,
                                       account,
                                       paymentMethodId,
                                       amount,
                                       currency,
                                       effectiveDate,
                                       paymentIdForNewPayment,
                                       paymentTransactionIdForNewPaymentTransaction,
                                       shouldLockAccount,
                                       overridePluginOperationResult,
                                       properties,
                                       internalCallContext,
                                       callContext);
    }

    public PaymentAutomatonDAOHelper buildDaoHelper(final PaymentStateContext paymentStateContext,
                                                    final InternalCallContext internalCallContext) throws PaymentApiException {
        final DateTime utcNow = internalCallContext.getCreatedDate();

        return new PaymentAutomatonDAOHelper(paymentStateContext, utcNow, paymentDao, paymentPluginServiceRegistration, internalCallContext, eventBus, paymentSMHelper);
    }

    public void run(final PaymentStateContext paymentStateContext,
                    final PaymentAutomatonDAOHelper daoHelper,
                    @Nullable final String currentStateNameOrNull,
                    final TransactionType transactionType) throws PaymentApiException {
        final String currentStateName = MoreObjects.firstNonNull(currentStateNameOrNull, paymentSMHelper.getInitStateNameForTransaction());

        final OperationCallback operationCallback;
        final LeavingStateCallback leavingStateCallback;
        final EnteringStateCallback enteringStateCallback;
        Boolean includeDeletedPaymentMethod = Boolean.FALSE;
        switch (transactionType) {
            case PURCHASE:
                operationCallback = new PurchaseOperation(daoHelper, locker, paymentPluginDispatcher, paymentConfig, paymentStateContext);
                leavingStateCallback = new PurchaseInitiated(daoHelper, paymentStateContext);
                enteringStateCallback = new PurchaseCompleted(daoHelper, paymentStateContext);
                break;
            case AUTHORIZE:
                operationCallback = new AuthorizeOperation(daoHelper, locker, paymentPluginDispatcher, paymentConfig, paymentStateContext);
                leavingStateCallback = new AuthorizeInitiated(daoHelper, paymentStateContext);
                enteringStateCallback = new AuthorizeCompleted(daoHelper, paymentStateContext);
                break;
            case CAPTURE:
                operationCallback = new CaptureOperation(daoHelper, locker, paymentPluginDispatcher, paymentConfig, paymentStateContext);
                leavingStateCallback = new CaptureInitiated(daoHelper, paymentStateContext);
                enteringStateCallback = new CaptureCompleted(daoHelper, paymentStateContext);
                break;
            case VOID:
                operationCallback = new VoidOperation(daoHelper, locker, paymentPluginDispatcher, paymentConfig, paymentStateContext);
                leavingStateCallback = new VoidInitiated(daoHelper, paymentStateContext);
                enteringStateCallback = new VoidCompleted(daoHelper, paymentStateContext);
                includeDeletedPaymentMethod = Boolean.TRUE;
                break;
            case REFUND:
                operationCallback = new RefundOperation(daoHelper, locker, paymentPluginDispatcher, paymentConfig, paymentStateContext);
                leavingStateCallback = new RefundInitiated(daoHelper, paymentStateContext);
                enteringStateCallback = new RefundCompleted(daoHelper, paymentStateContext);
                includeDeletedPaymentMethod = Boolean.TRUE;
                break;
            case CREDIT:
                operationCallback = new CreditOperation(daoHelper, locker, paymentPluginDispatcher, paymentConfig, paymentStateContext);
                leavingStateCallback = new CreditInitiated(daoHelper, paymentStateContext);
                enteringStateCallback = new CreditCompleted(daoHelper, paymentStateContext);
                break;
            case CHARGEBACK:
                operationCallback = new ChargebackOperation(daoHelper, locker, paymentPluginDispatcher, paymentConfig, paymentStateContext);
                leavingStateCallback = new ChargebackInitiated(daoHelper, paymentStateContext);
                enteringStateCallback = new ChargebackCompleted(daoHelper, paymentStateContext);
                includeDeletedPaymentMethod = Boolean.TRUE;
                break;
            default:
                throw new IllegalStateException("Unsupported transaction type " + transactionType);
        }

        runStateMachineOperation(currentStateName, transactionType, leavingStateCallback, operationCallback, enteringStateCallback, includeDeletedPaymentMethod, paymentStateContext, daoHelper);
    }

    //
    // TODO Fix fields accessed by some callbacks (which are not injected)
    //
    public PaymentDao getPaymentDao() {
        return paymentDao;
    }

    public Clock getClock() {
        return clock;
    }

    private void runStateMachineOperation(final String initialStateName,
                                          final TransactionType transactionType,
                                          final LeavingStateCallback leavingStateCallback,
                                          final OperationCallback operationCallback,
                                          final EnteringStateCallback enteringStateCallback,
                                          final Boolean includeDeletedPaymentMethod,
                                          final PaymentStateContext paymentStateContext,
                                          final PaymentAutomatonDAOHelper daoHelper) throws PaymentApiException {
        try {
            final StateMachineConfig stateMachineConfig = paymentSMHelper.getStateMachineConfig(daoHelper.getPaymentProviderPluginName(includeDeletedPaymentMethod), paymentStateContext.getInternalCallContext());
            final StateMachine initialStateMachine = stateMachineConfig.getStateMachineForState(initialStateName);
            final State initialState = initialStateMachine.getState(initialStateName);
            final Operation operation = paymentSMHelper.getOperationForTransaction(stateMachineConfig, transactionType);

            initialState.runOperation(operation, operationCallback, enteringStateCallback, leavingStateCallback);
        } catch (final MissingEntryException e) {
            throw new PaymentApiException(e.getCause(), ErrorCode.PAYMENT_INVALID_OPERATION, transactionType, initialStateName);
        } catch (final OperationException e) {
            if (e.getCause() == null) {
                throw new PaymentApiException(e, ErrorCode.PAYMENT_INTERNAL_ERROR, MoreObjects.firstNonNull(e.getMessage(), ""));
            } else if (e.getCause() instanceof PaymentApiException) {
                throw (PaymentApiException) e.getCause();
            } else {
                throw new PaymentApiException(e.getCause(), ErrorCode.PAYMENT_INTERNAL_ERROR, MoreObjects.firstNonNull(e.getMessage(), ""));
            }
        }
    }

    // TODO Could we cache these to avoid extra queries in PaymentAutomatonDAOHelper?
    private UUID retrievePaymentId(@Nullable final String paymentExternalKey, @Nullable final String paymentTransactionExternalKey, final InternalCallContext internalCallContext) throws PaymentApiException {
        if (paymentExternalKey != null) {
            final PaymentModelDao payment = paymentDao.getPaymentByExternalKey(paymentExternalKey, internalCallContext);
            if (payment != null) {
                return payment.getId();
            }
        }

        if (paymentTransactionExternalKey == null) {
            return null;
        }

        final List<PaymentTransactionModelDao> paymentTransactionModelDaos = paymentDao.getPaymentTransactionsByExternalKey(paymentTransactionExternalKey, internalCallContext);
        for (final PaymentTransactionModelDao paymentTransactionModelDao : paymentTransactionModelDaos) {
            if (paymentTransactionModelDao.getTransactionStatus() == TransactionStatus.SUCCESS ||
                paymentTransactionModelDao.getTransactionStatus() == TransactionStatus.PENDING ||
                paymentTransactionModelDao.getTransactionStatus() == TransactionStatus.UNKNOWN) {
                return paymentTransactionModelDao.getPaymentId();
            }
        }

        UUID paymentIdCandidate = null;
        for (final PaymentTransactionModelDao paymentTransactionModelDao : paymentTransactionModelDaos) {
            if (paymentTransactionModelDao.getTransactionStatus() == TransactionStatus.PAYMENT_FAILURE ||
                paymentTransactionModelDao.getTransactionStatus() == TransactionStatus.PLUGIN_FAILURE) {
                if (paymentIdCandidate == null) {
                    paymentIdCandidate = paymentTransactionModelDao.getPaymentId();
                } else if (!paymentIdCandidate.equals(paymentTransactionModelDao.getPaymentId())) {
                    throw new PaymentApiException(ErrorCode.PAYMENT_INTERNAL_ERROR, "Multiple failed payments sharing the same transaction external key - this should never happen");
                }
            }
        }

        return paymentIdCandidate;
    }

}
