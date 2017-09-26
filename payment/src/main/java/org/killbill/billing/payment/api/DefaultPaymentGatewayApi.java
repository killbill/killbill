/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.billing.payment.api;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.control.plugin.api.HPPType;
import org.killbill.billing.control.plugin.api.PaymentApiType;
import org.killbill.billing.control.plugin.api.PaymentControlApiException;
import org.killbill.billing.control.plugin.api.PriorPaymentControlResult;
import org.killbill.billing.payment.core.PaymentExecutors;
import org.killbill.billing.payment.core.PaymentGatewayProcessor;
import org.killbill.billing.payment.core.sm.control.ControlPluginRunner;
import org.killbill.billing.payment.core.sm.control.PaymentControlApiAbortException;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.billing.payment.dispatcher.PluginDispatcher.PluginDispatcherReturnType;
import org.killbill.billing.payment.plugin.api.GatewayNotification;
import org.killbill.billing.payment.plugin.api.HostedPaymentPageFormDescriptor;
import org.killbill.billing.util.PluginProperties;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.config.definition.PaymentConfig;

import com.google.common.base.Joiner;

import static org.killbill.billing.payment.dispatcher.PaymentPluginDispatcher.dispatchWithExceptionHandling;

public class DefaultPaymentGatewayApi extends DefaultApiBase implements PaymentGatewayApi {

    private static final Joiner JOINER = Joiner.on(", ");

    private final PaymentGatewayProcessor paymentGatewayProcessor;
    private final ControlPluginRunner controlPluginRunner;
    private final PluginDispatcher<HostedPaymentPageFormDescriptor> paymentPluginFormDispatcher;
    private final PluginDispatcher<GatewayNotification> paymentPluginNotificationDispatcher;

    @Inject
    public DefaultPaymentGatewayApi(final PaymentConfig paymentConfig,
                                    final PaymentGatewayProcessor paymentGatewayProcessor,
                                    final ControlPluginRunner controlPluginRunner,
                                    final PaymentExecutors executors,
                                    final InternalCallContextFactory internalCallContextFactory) {
        super(paymentConfig, internalCallContextFactory);
        this.paymentGatewayProcessor = paymentGatewayProcessor;
        this.controlPluginRunner = controlPluginRunner;
        final long paymentPluginTimeoutSec = TimeUnit.SECONDS.convert(paymentConfig.getPaymentPluginTimeout().getPeriod(), paymentConfig.getPaymentPluginTimeout().getUnit());
        this.paymentPluginFormDispatcher = new PluginDispatcher<HostedPaymentPageFormDescriptor>(paymentPluginTimeoutSec, executors);
        this.paymentPluginNotificationDispatcher = new PluginDispatcher<GatewayNotification>(paymentPluginTimeoutSec, executors);
    }

    @Override
    public HostedPaymentPageFormDescriptor buildFormDescriptor(final Account account, final UUID paymentMethodId, final Iterable<PluginProperty> customFields, final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentApiException {
        return buildFormDescriptor(true, account, paymentMethodId, customFields, properties, callContext);
    }

    @Override
    public HostedPaymentPageFormDescriptor buildFormDescriptorWithPaymentControl(final Account account, final UUID paymentMethodId, final Iterable<PluginProperty> customFields, final Iterable<PluginProperty> properties, final PaymentOptions paymentOptions, final CallContext callContext) throws PaymentApiException {
        final Iterable<PluginProperty> mergedProperties = PluginProperties.merge(customFields, properties);
        return executeWithPaymentControl(account, paymentMethodId, mergedProperties, paymentOptions, callContext, paymentPluginFormDispatcher, new WithPaymentControlCallback<HostedPaymentPageFormDescriptor>() {
            @Override
            public HostedPaymentPageFormDescriptor doPaymentGatewayApiOperation(final UUID adjustedPaymentMethodId, final Iterable<PluginProperty> adjustedPluginProperties) throws PaymentApiException {
                return buildFormDescriptor(false, account, adjustedPaymentMethodId, customFields, adjustedPluginProperties, callContext);
            }
        });
    }

    @Override
    public GatewayNotification processNotification(final String notification, final String pluginName, final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentApiException {
        return paymentGatewayProcessor.processNotification(true, notification, pluginName, properties, callContext);
    }

    @Override
    public GatewayNotification processNotificationWithPaymentControl(final String notification, final String pluginName, final Iterable<PluginProperty> properties, final PaymentOptions paymentOptions, final CallContext callContext) throws PaymentApiException {
        return executeWithPaymentControl(null, null, properties, paymentOptions, callContext, paymentPluginNotificationDispatcher, new WithPaymentControlCallback<GatewayNotification>() {
            @Override
            public GatewayNotification doPaymentGatewayApiOperation(final UUID adjustedPaymentMethodId, final Iterable<PluginProperty> adjustedPluginProperties) throws PaymentApiException {
                if (adjustedPaymentMethodId == null) {
                    return paymentGatewayProcessor.processNotification(false, notification, pluginName, adjustedPluginProperties, callContext);
                } else {
                    return paymentGatewayProcessor.processNotification(false, notification, adjustedPaymentMethodId, adjustedPluginProperties, callContext);
                }
            }
        });
    }

    private HostedPaymentPageFormDescriptor buildFormDescriptor(final boolean shouldDispatch, final Account account, @Nullable final UUID paymentMethodId, final Iterable<PluginProperty> customFields, final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentApiException {
        final UUID paymentMethodIdToUse = paymentMethodId != null ? paymentMethodId : account.getPaymentMethodId();
        if (paymentMethodIdToUse == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_INVALID_PARAMETER, "paymentMethodId", "should not be null");
        }

        return paymentGatewayProcessor.buildFormDescriptor(shouldDispatch, account, paymentMethodIdToUse, customFields, properties, callContext, internalCallContextFactory.createInternalCallContext(account.getId(), callContext));
    }

    private interface WithPaymentControlCallback<T> {

        T doPaymentGatewayApiOperation(final UUID adjustedPaymentMethodId, final Iterable<PluginProperty> adjustedPluginProperties) throws PaymentApiException;
    }

    private <T> T executeWithPaymentControl(@Nullable final Account account,
                                            @Nullable final UUID paymentMethodId,
                                            final Iterable<PluginProperty> properties,
                                            final PaymentOptions paymentOptions,
                                            final CallContext callContext,
                                            final PluginDispatcher<T> pluginDispatcher,
                                            final WithPaymentControlCallback<T> callback) throws PaymentApiException {
        final List<String> paymentControlPluginNames = toPaymentControlPluginNames(paymentOptions, callContext);
        if (paymentControlPluginNames.isEmpty()) {
            return callback.doPaymentGatewayApiOperation(paymentMethodId, properties);
        }

        final List<String> controlPluginNames = paymentOptions.getPaymentControlPluginNames();
        return dispatchWithExceptionHandling(account,
                                             JOINER.join(controlPluginNames),
                                             new Callable<PluginDispatcherReturnType<T>>() {
                                                 @Override
                                                 public PluginDispatcherReturnType<T> call() throws Exception {
                                                     final PriorPaymentControlResult priorCallResult;
                                                     try {
                                                         priorCallResult = controlPluginRunner.executePluginPriorCalls(account,
                                                                                                                       paymentMethodId,
                                                                                                                       null,
                                                                                                                       null,
                                                                                                                       null,
                                                                                                                       null,
                                                                                                                       null,
                                                                                                                       null,
                                                                                                                       PaymentApiType.HPP,
                                                                                                                       null,
                                                                                                                       HPPType.BUILD_FORM_DESCRIPTOR,
                                                                                                                       null,
                                                                                                                       null,
                                                                                                                       null,
                                                                                                                       null,
                                                                                                                       true,
                                                                                                                       paymentControlPluginNames,
                                                                                                                       properties,
                                                                                                                       callContext);

                                                     } catch (final PaymentControlApiAbortException e) {
                                                         throw new PaymentApiException(ErrorCode.PAYMENT_PLUGIN_API_ABORTED, e.getPluginName());
                                                     } catch (final PaymentControlApiException e) {
                                                         throw new PaymentApiException(e, ErrorCode.PAYMENT_PLUGIN_EXCEPTION, e);
                                                     }

                                                     try {
                                                         final T result = callback.doPaymentGatewayApiOperation(priorCallResult.getAdjustedPaymentMethodId(), priorCallResult.getAdjustedPluginProperties());
                                                         controlPluginRunner.executePluginOnSuccessCalls(account,
                                                                                                         paymentMethodId,
                                                                                                         null,
                                                                                                         null,
                                                                                                         null,
                                                                                                         null,
                                                                                                         null,
                                                                                                         null,
                                                                                                         PaymentApiType.HPP,
                                                                                                         null,
                                                                                                         HPPType.BUILD_FORM_DESCRIPTOR,
                                                                                                         null,
                                                                                                         null,
                                                                                                         null,
                                                                                                         null,
                                                                                                         true,
                                                                                                         paymentControlPluginNames,
                                                                                                         priorCallResult.getAdjustedPluginProperties(),
                                                                                                         callContext);
                                                         return PluginDispatcher.createPluginDispatcherReturnType(result);
                                                     } catch (final PaymentApiException e) {
                                                         controlPluginRunner.executePluginOnFailureCalls(account,
                                                                                                         paymentMethodId,
                                                                                                         null,
                                                                                                         null,
                                                                                                         null,
                                                                                                         null,
                                                                                                         null,
                                                                                                         null,
                                                                                                         PaymentApiType.HPP,
                                                                                                         null,
                                                                                                         HPPType.BUILD_FORM_DESCRIPTOR,
                                                                                                         null,
                                                                                                         null,
                                                                                                         null,
                                                                                                         null,
                                                                                                         true,
                                                                                                         paymentControlPluginNames,
                                                                                                         priorCallResult.getAdjustedPluginProperties(),
                                                                                                         callContext);
                                                         throw e;
                                                     }
                                                 }
                                             },
                                             pluginDispatcher);
    }
}
