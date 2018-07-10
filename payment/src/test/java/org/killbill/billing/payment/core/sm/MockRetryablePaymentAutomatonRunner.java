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
import org.killbill.automaton.Operation.OperationCallback;
import org.killbill.automaton.OperationResult;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.control.plugin.api.PaymentControlPluginApi;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.core.PaymentExecutors;
import org.killbill.billing.payment.core.PaymentPluginServiceRegistration;
import org.killbill.billing.payment.core.PaymentProcessor;
import org.killbill.billing.payment.core.PaymentRefresher;
import org.killbill.billing.payment.core.sm.control.ControlPluginRunner;
import org.killbill.billing.payment.core.sm.control.PaymentStateControlContext;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.billing.payment.retry.BaseRetryService.RetryServiceScheduler;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.config.definition.PaymentConfig;
import org.killbill.bus.api.PersistentBus;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLocker;

import static org.killbill.billing.payment.glue.PaymentModule.RETRYABLE_NAMED;

public class MockRetryablePaymentAutomatonRunner extends PluginControlPaymentAutomatonRunner {

    private OperationCallback operationCallback;
    private PaymentStateControlContext context;

    @Inject
    public MockRetryablePaymentAutomatonRunner(final PaymentDao paymentDao, final GlobalLocker locker, final PaymentPluginServiceRegistration paymentPluginServiceRegistration, final OSGIServiceRegistration<PaymentControlPluginApi> retryPluginRegistry, final Clock clock, final TagInternalApi tagApi, final PaymentProcessor paymentProcessor,
                                               @Named(RETRYABLE_NAMED) final RetryServiceScheduler retryServiceScheduler, final PaymentConfig paymentConfig, final PaymentExecutors executors,
                                               final PaymentStateMachineHelper paymentSMHelper, final PaymentControlStateMachineHelper retrySMHelper, final ControlPluginRunner controlPluginRunner, final PersistentBus eventBus, final PaymentRefresher paymentRefresher) {
        super(paymentDao, locker, paymentPluginServiceRegistration, retryPluginRegistry, clock, paymentProcessor, retryServiceScheduler, paymentConfig, executors, paymentSMHelper, retrySMHelper, controlPluginRunner, eventBus, paymentRefresher);
    }

    @Override
    OperationCallback createOperationCallback(final ControlOperation controlOperation, final PaymentStateControlContext paymentStateContext) {
        if (operationCallback == null) {
            return super.createOperationCallback(controlOperation, paymentStateContext);
        } else {
            return operationCallback;
        }
    }

    @Override
    PaymentStateControlContext createContext(final boolean isApiPayment, final Boolean isSuccess, final TransactionType transactionType, final Account account, @Nullable final UUID paymentMethodId,
                                             @Nullable final UUID paymentId, @Nullable final String paymentExternalKey, @Nullable final UUID transactionId, final String paymentTransactionExternalKey,
                                             @Nullable final BigDecimal amount, @Nullable final Currency currency, @Nullable DateTime effectiveDate,
                                             final Iterable<PluginProperty> properties,
                                             final List<String> pluginNames, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        if (context == null) {
            return super.createContext(isApiPayment, isSuccess, transactionType, account, paymentMethodId, paymentId, paymentExternalKey, transactionId, paymentTransactionExternalKey,
                                       amount, currency, effectiveDate, properties, pluginNames, callContext, internalCallContext);
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

    public OSGIServiceRegistration<PaymentControlPluginApi> getRetryPluginRegistry() {
        return paymentControlPluginRegistry;
    }
}
