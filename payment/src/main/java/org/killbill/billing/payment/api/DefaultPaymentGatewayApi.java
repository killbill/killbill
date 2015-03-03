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

import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentGatewayApi;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.core.PaymentGatewayProcessor;
import org.killbill.billing.payment.plugin.api.GatewayNotification;
import org.killbill.billing.payment.plugin.api.HostedPaymentPageFormDescriptor;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;

public class DefaultPaymentGatewayApi implements PaymentGatewayApi {

    private final PaymentGatewayProcessor paymentGatewayProcessor;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public DefaultPaymentGatewayApi(final PaymentGatewayProcessor paymentGatewayProcessor, final InternalCallContextFactory internalCallContextFactory) {
        this.paymentGatewayProcessor = paymentGatewayProcessor;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public HostedPaymentPageFormDescriptor buildFormDescriptor(final Account account, @Nullable final UUID paymentMethodId, final Iterable<PluginProperty> customFields, final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentApiException {
        final UUID paymentMethodIdToUse = paymentMethodId != null ? paymentMethodId : account.getPaymentMethodId();
        return paymentGatewayProcessor.buildFormDescriptor(account, paymentMethodIdToUse, customFields, properties, callContext, internalCallContextFactory.createInternalCallContext(account.getId(), callContext));
    }

    @Override
    public GatewayNotification processNotification(final String notification, final String pluginName, final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentApiException {
        return paymentGatewayProcessor.processNotification(notification, pluginName, properties, callContext);
    }
}
