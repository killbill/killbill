/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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
import org.killbill.automaton.Transition;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.dao.PaymentModelDao;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.billing.payment.glue.PaymentModule;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.config.PaymentConfig;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLocker;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.inject.name.Named;

import static org.killbill.billing.payment.glue.PaymentModule.PLUGIN_EXECUTOR_NAMED;

public class DirectPaymentAutomatonRunner {

    protected final StateMachineConfig stateMachineConfig;
    protected final PaymentDao paymentDao;
    protected final GlobalLocker locker;
    protected final PluginDispatcher<OperationResult> paymentPluginDispatcher;
    protected final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry;
    protected final Clock clock;

    @Inject
    public DirectPaymentAutomatonRunner(@javax.inject.Named(PaymentModule.STATE_MACHINE_PAYMENT) final StateMachineConfig stateMachineConfig,
                                        final PaymentConfig paymentConfig,
                                        final PaymentDao paymentDao,
                                        final GlobalLocker locker,
                                        final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry,
                                        final Clock clock,
                                        @Named(PLUGIN_EXECUTOR_NAMED) final ExecutorService executor) {
        this.stateMachineConfig = stateMachineConfig;
        this.paymentDao = paymentDao;
        this.locker = locker;
        this.pluginRegistry = pluginRegistry;
        this.clock = clock;

        final long paymentPluginTimeoutSec = TimeUnit.SECONDS.convert(paymentConfig.getPaymentPluginTimeout().getPeriod(), paymentConfig.getPaymentPluginTimeout().getUnit());
        this.paymentPluginDispatcher = new PluginDispatcher<OperationResult>(paymentPluginTimeoutSec, executor);

    }

    public UUID run(final TransactionType transactionType, final Account account, @Nullable final UUID paymentMethodId,
                    @Nullable final UUID directPaymentId, @Nullable final String directPaymentExternalKey, final String directPaymentTransactionExternalKey,
                    @Nullable final BigDecimal amount, @Nullable final Currency currency,
                    final boolean shouldLockAccount, final Iterable<PluginProperty> properties,
                    final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {

        final DateTime utcNow = clock.getUTCNow();

        final DirectPaymentStateContext directPaymentStateContext = new DirectPaymentStateContext(directPaymentId, directPaymentExternalKey, directPaymentTransactionExternalKey, transactionType,
                                                                                                  account, paymentMethodId, amount, currency, shouldLockAccount, properties, internalCallContext, callContext);
        final DirectPaymentAutomatonDAOHelper daoHelper = new DirectPaymentAutomatonDAOHelper(directPaymentStateContext, utcNow, paymentDao, pluginRegistry, internalCallContext);

        final UUID effectivePaymentMethodId;
        final String currentStateMachineName;
        final String currentStateName;
        if (directPaymentId != null) {
            final PaymentModelDao paymentModelDao = daoHelper.getDirectPayment();
            effectivePaymentMethodId = paymentModelDao.getPaymentMethodId();
            currentStateName = paymentModelDao.getStateName();
            currentStateMachineName = getStateMachineName(currentStateName);

            // Check for illegal states (should never happen)
            Preconditions.checkState(currentStateMachineName != null, "State machine name cannot be null for direct payment " + directPaymentId);
            Preconditions.checkState(currentStateName != null, "State name cannot be null for direct payment " + directPaymentId);
            Preconditions.checkState(paymentMethodId == null || effectivePaymentMethodId.equals(paymentMethodId), "Specified payment method id " + paymentMethodId + " doesn't match the one on the payment " + effectivePaymentMethodId);
        } else {
            // If the payment method is not specified, retrieve the default one on the account
            effectivePaymentMethodId = paymentMethodId != null ? paymentMethodId : daoHelper.getDefaultPaymentMethodId();

            switch (transactionType) {
                case AUTHORIZE:
                    currentStateMachineName = "AUTHORIZE";
                    currentStateName = "AUTH_INIT";
                    break;
                case CREDIT:
                    currentStateMachineName = "CREDIT";
                    currentStateName = "CREDIT_INIT";
                    break;
                case PURCHASE:
                    currentStateMachineName = "PURCHASE";
                    currentStateName = "PURCHASE_INIT";
                    break;
                default:
                    throw new IllegalStateException("Unsupported transaction type " + transactionType + " for null direct payment id");
            }
        }

        directPaymentStateContext.setPaymentMethodId(effectivePaymentMethodId);

        final String operationStateMachineName;
        final String operationName;
        final OperationCallback operationCallback;
        final LeavingStateCallback leavingStateCallback;
        final EnteringStateCallback enteringStateCallback;
        switch (transactionType) {
            case PURCHASE:
                operationStateMachineName = "PURCHASE";
                operationName = "OP_PURCHASE";
                operationCallback = new PurchaseOperation(daoHelper, locker, paymentPluginDispatcher, directPaymentStateContext);
                leavingStateCallback = new PurchaseInitiated(daoHelper, directPaymentStateContext);
                enteringStateCallback = new PurchaseCompleted(daoHelper, directPaymentStateContext);
                break;
            case AUTHORIZE:
                operationStateMachineName = "AUTHORIZE";
                operationName = "OP_AUTHORIZE";
                operationCallback = new AuthorizeOperation(daoHelper, locker, paymentPluginDispatcher, directPaymentStateContext);
                leavingStateCallback = new AuthorizeInitiated(daoHelper, directPaymentStateContext);
                enteringStateCallback = new AuthorizeCompleted(daoHelper, directPaymentStateContext);
                break;
            case CAPTURE:
                operationStateMachineName = "CAPTURE";
                operationName = "OP_CAPTURE";
                operationCallback = new CaptureOperation(daoHelper, locker, paymentPluginDispatcher, directPaymentStateContext);
                leavingStateCallback = new CaptureInitiated(daoHelper, directPaymentStateContext);
                enteringStateCallback = new CaptureCompleted(daoHelper, directPaymentStateContext);
                break;
            case VOID:
                operationStateMachineName = "VOID";
                operationName = "OP_VOID";
                operationCallback = new VoidOperation(daoHelper, locker, paymentPluginDispatcher, directPaymentStateContext);
                leavingStateCallback = new VoidInitiated(daoHelper, directPaymentStateContext);
                enteringStateCallback = new VoidCompleted(daoHelper, directPaymentStateContext);
                break;
            case REFUND:
                operationStateMachineName = "REFUND";
                operationName = "OP_REFUND";
                operationCallback = new RefundOperation(daoHelper, locker, paymentPluginDispatcher, directPaymentStateContext);
                leavingStateCallback = new RefundInitiated(daoHelper, directPaymentStateContext);
                enteringStateCallback = new RefundCompleted(daoHelper, directPaymentStateContext);
                break;
            case CREDIT:
                operationStateMachineName = "CREDIT";
                operationName = "OP_CREDIT";
                operationCallback = new CreditOperation(daoHelper, locker, paymentPluginDispatcher, directPaymentStateContext);
                leavingStateCallback = new CreditInitiated(daoHelper, directPaymentStateContext);
                enteringStateCallback = new CreditCompleted(daoHelper, directPaymentStateContext);
                break;
            case CHARGEBACK:
                operationStateMachineName = "CHARGEBACK";
                operationName = "OP_CHARGEBACK";
                operationCallback = new ChargebackOperation(daoHelper, locker, paymentPluginDispatcher, directPaymentStateContext);
                leavingStateCallback = new ChargebackInitiated(daoHelper, directPaymentStateContext);
                enteringStateCallback = new ChargebackCompleted(daoHelper, directPaymentStateContext);
                break;
            default:
                throw new IllegalStateException("Unsupported transaction type " + transactionType);
        }

        runStateMachineOperation(currentStateMachineName, currentStateName, operationStateMachineName, operationName, leavingStateCallback, operationCallback, enteringStateCallback);

        return directPaymentStateContext.getDirectPaymentId();
    }

    public final State fetchNextState(final String prevStateName, final boolean isSuccess) {
        final StateMachine stateMachine = getStateMachine(prevStateName);
        final Transition transition = Iterables.tryFind(ImmutableList.copyOf(stateMachine.getTransitions()), new Predicate<Transition>() {
            @Override
            public boolean apply(final Transition input) {
                // STEPH this only works if there is only one operation defined for a given state machine, which is our model for PaymentStates.xml
                return input.getInitialState().getName().equals(prevStateName) &&
                       input.getOperationResult().equals(isSuccess ? OperationResult.SUCCESS : OperationResult.FAILURE);
            }
        }).orNull();
        return transition != null ? transition.getFinalState() : null;
    }

    // Hack for now
    protected String getStateMachineName(final String currentStateName) {
        final StateMachine stateMachine = getStateMachine(currentStateName);
        if (stateMachine == null) {
            return null;
        }
        return stateMachine.getName();
    }

    private StateMachine getStateMachine(final String currentStateName) {
        for (final StateMachine stateMachine : stateMachineConfig.getStateMachines()) {
            for (final State state : stateMachine.getStates()) {
                if (state.getName().equals(currentStateName)) {
                    return stateMachine;
                }
            }
        }
        return null;
    }

    protected void runStateMachineOperation(final String initialStateMachineName, final String initialStateName,
                                            final String operationStateMachineName, final String operationName,
                                            final LeavingStateCallback leavingStateCallback, final OperationCallback operationCallback, final EnteringStateCallback enteringStateCallback) throws PaymentApiException {
        try {
            final StateMachine initialStateMachine = stateMachineConfig.getStateMachine(initialStateMachineName);
            final State initialState = initialStateMachine.getState(initialStateName);

            final StateMachine operationStateMachine = stateMachineConfig.getStateMachine(operationStateMachineName);
            final Operation operation = operationStateMachine.getOperation(operationName);

            initialState.runOperation(operation, operationCallback, enteringStateCallback, leavingStateCallback);
        } catch (final MissingEntryException e) {
            throw new PaymentApiException(e.getCause(), ErrorCode.PAYMENT_INTERNAL_ERROR, Objects.firstNonNull(e.getMessage(), ""));
        } catch (final OperationException e) {
            if (e.getCause() == null) {
                throw new PaymentApiException(e, ErrorCode.PAYMENT_INTERNAL_ERROR, Objects.firstNonNull(e.getMessage(), ""));
            } else if (e.getCause() instanceof PaymentApiException) {
                throw (PaymentApiException) e.getCause();
            } else {
                throw new PaymentApiException(e.getCause(), ErrorCode.PAYMENT_INTERNAL_ERROR, Objects.firstNonNull(e.getMessage(), ""));
            }
        }
    }
}
