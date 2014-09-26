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
import java.util.concurrent.TimeoutException;

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
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.control.InvoicePaymentControlPluginApi;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dao.PaymentModelDao;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.billing.payment.glue.PaymentModule;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.config.PaymentConfig;
import org.killbill.bus.api.PersistentBus;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLocker;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.inject.name.Named;

import static org.killbill.billing.payment.glue.PaymentModule.PLUGIN_EXECUTOR_NAMED;

public class PaymentAutomatonRunner {

    protected final PaymentStateMachineHelper paymentSMHelper;
    protected final PaymentDao paymentDao;
    protected final GlobalLocker locker;
    protected final PluginDispatcher<OperationResult> paymentPluginDispatcher;
    protected final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry;
    protected final Clock clock;
    private final PersistentBus eventBus;

    @Inject
    public PaymentAutomatonRunner(@javax.inject.Named(PaymentModule.STATE_MACHINE_PAYMENT) final StateMachineConfig stateMachineConfig,
                                  final PaymentConfig paymentConfig,
                                  final PaymentDao paymentDao,
                                  final GlobalLocker locker,
                                  final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry,
                                  final Clock clock,
                                  @Named(PLUGIN_EXECUTOR_NAMED) final ExecutorService executor,
                                  final PersistentBus eventBus,
                                  final PaymentStateMachineHelper paymentSMHelper) {
        this.paymentSMHelper = paymentSMHelper;
        this.paymentDao = paymentDao;
        this.locker = locker;
        this.pluginRegistry = pluginRegistry;
        this.clock = clock;
        this.eventBus = eventBus;

        final long paymentPluginTimeoutSec = TimeUnit.SECONDS.convert(paymentConfig.getPaymentPluginTimeout().getPeriod(), paymentConfig.getPaymentPluginTimeout().getUnit());
        this.paymentPluginDispatcher = new PluginDispatcher<OperationResult>(paymentPluginTimeoutSec, executor);

    }

    public UUID run(final boolean isApiPayment, final TransactionType transactionType, final Account account, @Nullable final UUID attemptId, @Nullable final UUID paymentMethodId,
                    @Nullable final UUID paymentId, @Nullable final UUID transactionId, @Nullable final String paymentExternalKey, final String paymentTransactionExternalKey,
                    @Nullable final BigDecimal amount, @Nullable final Currency currency,
                    final boolean shouldLockAccount, final OperationResult overridePluginOperationResult, final Iterable<PluginProperty> properties,
                    final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {

        final DateTime utcNow = clock.getUTCNow();

        final PaymentStateContext paymentStateContext = new PaymentStateContext(isApiPayment, paymentId, transactionId, attemptId, paymentExternalKey, paymentTransactionExternalKey, transactionType,
                                                                                account, paymentMethodId, amount, currency, shouldLockAccount, overridePluginOperationResult, properties, internalCallContext, callContext);

        final PaymentAutomatonDAOHelper daoHelper = new PaymentAutomatonDAOHelper(paymentStateContext, utcNow, paymentDao, pluginRegistry, internalCallContext, eventBus, paymentSMHelper);

        final UUID effectivePaymentMethodId;
        final String currentStateName;
        if (paymentId != null) {
            final PaymentModelDao paymentModelDao = daoHelper.getPayment();
            effectivePaymentMethodId = paymentModelDao.getPaymentMethodId();
            currentStateName = paymentModelDao.getLastSuccessStateName() != null ? paymentModelDao.getLastSuccessStateName() : paymentSMHelper.getInitStateNameForTransaction();

            // Check for illegal states (should never happen)
            Preconditions.checkState(currentStateName != null, "State name cannot be null for payment " + paymentId);
            Preconditions.checkState(paymentMethodId == null || effectivePaymentMethodId.equals(paymentMethodId), "Specified payment method id " + paymentMethodId + " doesn't match the one on the payment " + effectivePaymentMethodId);
        } else {
            // If the payment method is not specified, retrieve the default one on the account; it could still be null, in which case
            //
            effectivePaymentMethodId = paymentMethodId != null ? paymentMethodId : account.getPaymentMethodId();
            currentStateName = paymentSMHelper.getInitStateNameForTransaction();
        }

        paymentStateContext.setPaymentMethodId(effectivePaymentMethodId);

        final OperationCallback operationCallback;
        final LeavingStateCallback leavingStateCallback;
        final EnteringStateCallback enteringStateCallback;
        switch (transactionType) {
            case PURCHASE:
                operationCallback = new PurchaseOperation(daoHelper, locker, paymentPluginDispatcher, paymentStateContext);
                leavingStateCallback = new PurchaseInitiated(daoHelper, paymentStateContext);
                enteringStateCallback = new PurchaseCompleted(daoHelper, paymentStateContext);
                break;
            case AUTHORIZE:
                operationCallback = new AuthorizeOperation(daoHelper, locker, paymentPluginDispatcher, paymentStateContext);
                leavingStateCallback = new AuthorizeInitiated(daoHelper, paymentStateContext);
                enteringStateCallback = new AuthorizeCompleted(daoHelper, paymentStateContext);
                break;
            case CAPTURE:
                operationCallback = new CaptureOperation(daoHelper, locker, paymentPluginDispatcher, paymentStateContext);
                leavingStateCallback = new CaptureInitiated(daoHelper, paymentStateContext);
                enteringStateCallback = new CaptureCompleted(daoHelper, paymentStateContext);
                break;
            case VOID:
                operationCallback = new VoidOperation(daoHelper, locker, paymentPluginDispatcher, paymentStateContext);
                leavingStateCallback = new VoidInitiated(daoHelper, paymentStateContext);
                enteringStateCallback = new VoidCompleted(daoHelper, paymentStateContext);
                break;
            case REFUND:
                operationCallback = new RefundOperation(daoHelper, locker, paymentPluginDispatcher, paymentStateContext);
                leavingStateCallback = new RefundInitiated(daoHelper, paymentStateContext);
                enteringStateCallback = new RefundCompleted(daoHelper, paymentStateContext);
                break;
            case CREDIT:
                operationCallback = new CreditOperation(daoHelper, locker, paymentPluginDispatcher, paymentStateContext);
                leavingStateCallback = new CreditInitiated(daoHelper, paymentStateContext);
                enteringStateCallback = new CreditCompleted(daoHelper, paymentStateContext);
                break;
            case CHARGEBACK:
                operationCallback = new ChargebackOperation(daoHelper, locker, paymentPluginDispatcher, paymentStateContext);
                leavingStateCallback = new ChargebackInitiated(daoHelper, paymentStateContext);
                enteringStateCallback = new ChargebackCompleted(daoHelper, paymentStateContext);
                break;
            default:
                throw new IllegalStateException("Unsupported transaction type " + transactionType);
        }

        runStateMachineOperation(currentStateName, transactionType, leavingStateCallback, operationCallback, enteringStateCallback, account.getId(), getInvoiceId(properties));

        return paymentStateContext.getPaymentId();
    }

    protected void runStateMachineOperation(final String initialStateName, final TransactionType transactionType,
                                            final LeavingStateCallback leavingStateCallback, final OperationCallback operationCallback, final EnteringStateCallback enteringStateCallback,
                                            final UUID accountId, final String invoiceId) throws PaymentApiException {
        try {
            final StateMachine initialStateMachine = paymentSMHelper.getStateMachineForStateName(initialStateName);
            final State initialState = initialStateMachine.getState(initialStateName);
            final Operation operation = paymentSMHelper.getOperationForTransaction(transactionType);

            initialState.runOperation(operation, operationCallback, enteringStateCallback, leavingStateCallback);
        } catch (final MissingEntryException e) {
            throw new PaymentApiException(e.getCause(), ErrorCode.PAYMENT_INVALID_OPERATION, transactionType, initialStateName);
        } catch (final OperationException e) {
            if (e.getCause() == null) {
                throw new PaymentApiException(e, ErrorCode.PAYMENT_INTERNAL_ERROR, Objects.firstNonNull(e.getMessage(), ""));
            } else if (e.getCause() instanceof PaymentApiException) {
                throw (PaymentApiException) e.getCause();
            } else if (e.getCause() instanceof TimeoutException) {
                throw new PaymentApiException(e.getCause(), ErrorCode.PAYMENT_PLUGIN_TIMEOUT, accountId, invoiceId);
            } else {
                throw new PaymentApiException(e.getCause(), ErrorCode.PAYMENT_INTERNAL_ERROR, Objects.firstNonNull(e.getMessage(), ""));
            }
        }
    }

    private String getInvoiceId(final Iterable<PluginProperty> properties) {
        final PluginProperty invoiceProperty = Iterables.tryFind(properties, new Predicate<PluginProperty>() {
            @Override
            public boolean apply(final PluginProperty input) {
                return InvoicePaymentControlPluginApi.PROP_IPCD_INVOICE_ID.equals(input.getKey());
            }
        }).orNull();

        return invoiceProperty == null || invoiceProperty.getValue() == null ? null : invoiceProperty.getValue().toString();
    }
}
