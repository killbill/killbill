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

import org.killbill.automaton.Operation.OperationCallback;
import org.killbill.automaton.OperationResult;
import org.killbill.automaton.StateMachineConfig;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.core.DirectPaymentProcessor;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.billing.payment.glue.PaymentModule;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.retry.BaseRetryService.RetryServiceScheduler;
import org.killbill.billing.retry.plugin.api.PaymentControlPluginApi;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.config.PaymentConfig;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLocker;

import static org.killbill.billing.payment.glue.PaymentModule.PLUGIN_EXECUTOR_NAMED;
import static org.killbill.billing.payment.glue.PaymentModule.RETRYABLE_NAMED;

public class MockRetryableDirectPaymentAutomatonRunner extends PluginControlledDirectPaymentAutomatonRunner {

    private OperationCallback operationCallback;
    private RetryableDirectPaymentStateContext context;

    @Inject
    public MockRetryableDirectPaymentAutomatonRunner(@Named(PaymentModule.STATE_MACHINE_PAYMENT) final StateMachineConfig stateMachineConfig, @Named(PaymentModule.STATE_MACHINE_RETRY) final StateMachineConfig retryStateMachine, final PaymentDao paymentDao, final GlobalLocker locker, final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry, final OSGIServiceRegistration<PaymentControlPluginApi> retryPluginRegistry, final Clock clock, final TagInternalApi tagApi, final DirectPaymentProcessor directPaymentProcessor,
                                                     @Named(RETRYABLE_NAMED) final RetryServiceScheduler retryServiceScheduler, final PaymentConfig paymentConfig, @com.google.inject.name.Named(PLUGIN_EXECUTOR_NAMED) final ExecutorService executor,
                                                     final PaymentStateMachineHelper paymentSMHelper, final RetryStateMachineHelper retrySMHelper) {
        super(stateMachineConfig, paymentDao, locker, pluginRegistry, retryPluginRegistry, clock, directPaymentProcessor, retryServiceScheduler, paymentConfig, executor, paymentSMHelper, retrySMHelper);
    }

    @Override
    OperationCallback createOperationCallback(final TransactionType transactionType, final RetryableDirectPaymentStateContext directPaymentStateContext) {
        if (operationCallback == null) {
            return super.createOperationCallback(transactionType, directPaymentStateContext);
        } else {
            return operationCallback;
        }
    }

    @Override
    RetryableDirectPaymentStateContext createContext(final boolean isApiPayment, final TransactionType transactionType, final Account account, @Nullable final UUID paymentMethodId,
                                                     @Nullable final UUID directPaymentId, @Nullable final String directPaymentExternalKey, final String directPaymentTransactionExternalKey,
                                                     @Nullable final BigDecimal amount, @Nullable final Currency currency,
                                                     final Iterable<PluginProperty> properties,
                                                     final String pluginName, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        if (context == null) {
            return super.createContext(isApiPayment, transactionType, account, paymentMethodId, directPaymentId, directPaymentExternalKey, directPaymentTransactionExternalKey,
                                       amount, currency, properties, pluginName, callContext, internalCallContext);
        } else {
            return context;
        }
    }

    public MockRetryableDirectPaymentAutomatonRunner setOperationCallback(final OperationCallback operationCallback) {
        this.operationCallback = operationCallback;
        return this;
    }

    public MockRetryableDirectPaymentAutomatonRunner setContext(final RetryableDirectPaymentStateContext context) {
        this.context = context;
        return this;
    }

    public PluginDispatcher<OperationResult> getPaymentPluginDispatcher() {
        return paymentPluginDispatcher;
    }

    public OSGIServiceRegistration<PaymentControlPluginApi> getRetryPluginRegistry() {
        return paymentControlPluginRegistry;
    }
}
