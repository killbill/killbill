/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.control.plugin.api.HPPType;
import org.killbill.billing.control.plugin.api.PaymentApiType;
import org.killbill.billing.control.plugin.api.PaymentControlApiException;
import org.killbill.billing.control.plugin.api.PriorPaymentControlResult;
import org.killbill.billing.payment.core.PaymentGatewayProcessor;
import org.killbill.billing.payment.core.sm.control.ControlPluginRunner;
import org.killbill.billing.payment.plugin.api.GatewayNotification;
import org.killbill.billing.payment.plugin.api.HostedPaymentPageFormDescriptor;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.config.PaymentConfig;

public class DefaultPaymentGatewayApi extends DefaultApiBase implements PaymentGatewayApi {

    private final PaymentGatewayProcessor paymentGatewayProcessor;
    private final ControlPluginRunner controlPluginRunner;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public DefaultPaymentGatewayApi(final PaymentConfig paymentConfig,
                                    final PaymentGatewayProcessor paymentGatewayProcessor,
                                    final ControlPluginRunner controlPluginRunner,
                                    final InternalCallContextFactory internalCallContextFactory) {
        super(paymentConfig);
        this.paymentGatewayProcessor = paymentGatewayProcessor;
        this.controlPluginRunner = controlPluginRunner;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public HostedPaymentPageFormDescriptor buildFormDescriptor(final Account account, final UUID paymentMethodId, final Iterable<PluginProperty> customFields, final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentApiException {
        final UUID paymentMethodIdToUse = paymentMethodId != null ? paymentMethodId : account.getPaymentMethodId();

        if (paymentMethodId == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_INVALID_PARAMETER, paymentMethodId, "should not be null");
        }

        return paymentGatewayProcessor.buildFormDescriptor(account, paymentMethodIdToUse, customFields, properties, callContext, internalCallContextFactory.createInternalCallContext(account.getId(), callContext));
    }

    @Override
    public HostedPaymentPageFormDescriptor buildFormDescriptorWithPaymentControl(final Account account, final UUID paymentMethodId, final Iterable<PluginProperty> customFields, final Iterable<PluginProperty> properties, final PaymentOptions paymentOptions, final CallContext callContext) throws PaymentApiException {

        return executeWithPaymentControl(account, paymentMethodId, properties, paymentOptions, callContext, new WithPaymentControlCallback<HostedPaymentPageFormDescriptor>() {
            @Override
            public HostedPaymentPageFormDescriptor doPaymentGatewayApiOperation(final Iterable<PluginProperty> adjustedPluginProperties) throws PaymentApiException {
                return buildFormDescriptor(account, paymentMethodId, customFields, adjustedPluginProperties, callContext);
            }
        });
    }

    @Override
    public GatewayNotification processNotification(final String notification, final String pluginName, final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentApiException {
        return paymentGatewayProcessor.processNotification(notification, pluginName, properties, callContext);
    }

    @Override
    public GatewayNotification processNotificationWithPaymentControl(final String notification, final String pluginName, final Iterable<PluginProperty> properties, final PaymentOptions paymentOptions, final CallContext callContext) throws PaymentApiException {
        return executeWithPaymentControl(null, null, properties, paymentOptions, callContext, new WithPaymentControlCallback<GatewayNotification>() {
            @Override
            public GatewayNotification doPaymentGatewayApiOperation(final Iterable<PluginProperty> adjustedPluginProperties) throws PaymentApiException {
                return processNotification(notification, pluginName, adjustedPluginProperties, callContext);
            }
        });
    }


    private interface WithPaymentControlCallback<T> {
        T doPaymentGatewayApiOperation(final Iterable<PluginProperty> adjustedPluginProperties) throws PaymentApiException;
    }

    private <T> T executeWithPaymentControl(@Nullable final Account account,
                                            @Nullable final UUID paymentMethodId,
                                            final Iterable<PluginProperty> properties,
                                            final PaymentOptions paymentOptions,
                                            final CallContext callContext,
                                            final WithPaymentControlCallback<T> callback) throws PaymentApiException {

        final List<String> paymentControlPluginNames = toPaymentControlPluginNames(paymentOptions);
        if (paymentControlPluginNames.isEmpty()) {
            return callback.doPaymentGatewayApiOperation(properties);
        }

        final PriorPaymentControlResult priorCallResult;
        try {
            priorCallResult = controlPluginRunner.executePluginPriorCalls(account,
                                                                          paymentMethodId,
                                                                          null, null, null, null,
                                                                          PaymentApiType.HPP, null, HPPType.BUILD_FORM_DESCRIPTOR,
                                                                          null, null, true, paymentControlPluginNames, properties, callContext);

        } catch (final PaymentControlApiException e) {
            throw new PaymentApiException(ErrorCode.PAYMENT_PLUGIN_EXCEPTION, e);
        }

        try {
            final T result = callback.doPaymentGatewayApiOperation(priorCallResult.getAdjustedPluginProperties());
            controlPluginRunner.executePluginOnSuccessCalls(account,
                                                            paymentMethodId,
                                                            null, null, null, null,
                                                            PaymentApiType.HPP, null, HPPType.BUILD_FORM_DESCRIPTOR,
                                                            null, null, true, paymentControlPluginNames, priorCallResult.getAdjustedPluginProperties(), callContext);
            return result;
        } catch (final PaymentApiException e) {
            controlPluginRunner.executePluginOnFailureCalls(account,
                                                            paymentMethodId,
                                                            null, null, null, null,
                                                            PaymentApiType.HPP, null, HPPType.BUILD_FORM_DESCRIPTOR,
                                                            null, null, true, paymentControlPluginNames, priorCallResult.getAdjustedPluginProperties(), callContext);

            throw e;

        }
    }
}
