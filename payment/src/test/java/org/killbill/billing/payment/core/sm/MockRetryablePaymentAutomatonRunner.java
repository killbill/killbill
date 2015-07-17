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

package org.killbill.billing.payment.core.sm;

import java.math.BigDecimal;
import java.util.List;
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
import org.killbill.billing.payment.core.PaymentProcessor;
import org.killbill.billing.payment.core.sm.control.PaymentStateControlContext;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.billing.payment.glue.PaymentModule;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.retry.BaseRetryService.RetryServiceScheduler;
import org.killbill.billing.routing.plugin.api.PaymentRoutingPluginApi;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.config.PaymentConfig;
import org.killbill.bus.api.PersistentBus;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLocker;

import static org.killbill.billing.payment.glue.PaymentModule.PLUGIN_EXECUTOR_NAMED;
import static org.killbill.billing.payment.glue.PaymentModule.RETRYABLE_NAMED;

public class MockRetryablePaymentAutomatonRunner extends PluginControlPaymentAutomatonRunner {

    private OperationCallback operationCallback;
    private PaymentStateControlContext context;

    @Inject
    public MockRetryablePaymentAutomatonRunner(@Named(PaymentModule.STATE_MACHINE_PAYMENT) final StateMachineConfig stateMachineConfig, @Named(PaymentModule.STATE_MACHINE_RETRY) final StateMachineConfig retryStateMachine, final PaymentDao paymentDao, final GlobalLocker locker, final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry, final OSGIServiceRegistration<PaymentRoutingPluginApi> retryPluginRegistry, final Clock clock, final TagInternalApi tagApi, final PaymentProcessor paymentProcessor,
                                               @Named(RETRYABLE_NAMED) final RetryServiceScheduler retryServiceScheduler, final PaymentConfig paymentConfig, @com.google.inject.name.Named(PLUGIN_EXECUTOR_NAMED) final ExecutorService executor,
                                               final PaymentStateMachineHelper paymentSMHelper, final PaymentControlStateMachineHelper retrySMHelper, final PersistentBus eventBus) {
        super(stateMachineConfig, paymentDao, locker, pluginRegistry, retryPluginRegistry, clock, paymentProcessor, retryServiceScheduler, paymentConfig, executor, paymentSMHelper, retrySMHelper, eventBus);
    }

    @Override
    OperationCallback createOperationCallback(final TransactionType transactionType, final PaymentStateControlContext paymentStateContext) {
        if (operationCallback == null) {
            return super.createOperationCallback(transactionType, paymentStateContext);
        } else {
            return operationCallback;
        }
    }

    @Override
    PaymentStateControlContext createContext(final boolean isApiPayment, final TransactionType transactionType, final Account account, @Nullable final UUID paymentMethodId,
                                             @Nullable final UUID paymentId, @Nullable final String paymentExternalKey, final String paymentTransactionExternalKey,
                                             @Nullable final BigDecimal amount, @Nullable final Currency currency,
                                             final Iterable<PluginProperty> properties,
                                             final List<String> pluginNames, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        if (context == null) {
            return super.createContext(isApiPayment, transactionType, account, paymentMethodId, paymentId, paymentExternalKey, paymentTransactionExternalKey,
                                       amount, currency, properties, pluginNames, callContext, internalCallContext);
        } else {
            return context;
        }
    }

    public MockRetryablePaymentAutomatonRunner setOperationCallback(final OperationCallback operationCallback) {
        this.operationCallback = operationCallback;
        return this;
    }

    public MockRetryablePaymentAutomatonRunner setContext(final PaymentStateControlContext context) {
        this.context = context;
        return this;
    }

    public PluginDispatcher<OperationResult> getPaymentPluginDispatcher() {
        return paymentPluginDispatcher;
    }

    public OSGIServiceRegistration<PaymentRoutingPluginApi> getRetryPluginRegistry() {
        return paymentControlPluginRegistry;
    }
}
