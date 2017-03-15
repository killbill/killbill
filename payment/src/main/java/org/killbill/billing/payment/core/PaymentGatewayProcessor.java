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

package org.killbill.billing.payment.core;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.billing.payment.dispatcher.PluginDispatcher.PluginDispatcherReturnType;
import org.killbill.billing.payment.plugin.api.GatewayNotification;
import org.killbill.billing.payment.plugin.api.HostedPaymentPageFormDescriptor;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiException;
import org.killbill.billing.payment.provider.DefaultNoOpGatewayNotification;
import org.killbill.billing.payment.provider.DefaultNoOpHostedPaymentPageFormDescriptor;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.config.definition.PaymentConfig;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLocker;

import com.google.common.base.MoreObjects;

import static org.killbill.billing.payment.dispatcher.PaymentPluginDispatcher.dispatchWithExceptionHandling;

public class PaymentGatewayProcessor extends ProcessorBase {

    private final PluginDispatcher<HostedPaymentPageFormDescriptor> paymentPluginFormDispatcher;
    private final PluginDispatcher<GatewayNotification> paymentPluginNotificationDispatcher;

    @Inject
    public PaymentGatewayProcessor(final PaymentPluginServiceRegistration paymentPluginServiceRegistration,
                                   final AccountInternalApi accountUserApi,
                                   final InvoiceInternalApi invoiceApi,
                                   final TagInternalApi tagUserApi,
                                   final PaymentDao paymentDao,
                                   final GlobalLocker locker,
                                   final PaymentConfig paymentConfig,
                                   final PaymentExecutors executors,
                                   final InternalCallContextFactory internalCallContextFactory,
                                   final Clock clock) {
        super(paymentPluginServiceRegistration, accountUserApi, paymentDao, tagUserApi, locker, internalCallContextFactory, invoiceApi, clock);
        final long paymentPluginTimeoutSec = TimeUnit.SECONDS.convert(paymentConfig.getPaymentPluginTimeout().getPeriod(), paymentConfig.getPaymentPluginTimeout().getUnit());
        this.paymentPluginFormDispatcher = new PluginDispatcher<HostedPaymentPageFormDescriptor>(paymentPluginTimeoutSec, executors);
        this.paymentPluginNotificationDispatcher = new PluginDispatcher<GatewayNotification>(paymentPluginTimeoutSec, executors);
    }

    public GatewayNotification processNotification(final boolean shouldDispatch, final String notification, final UUID paymentMethodId, final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentApiException {
        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(paymentMethodId, ObjectType.PAYMENT_METHOD, callContext);
        final String pluginName = getPaymentMethodById(paymentMethodId, true, internalCallContext).getPluginName();
        return processNotification(shouldDispatch, notification, pluginName, properties, callContext);
    }

    public GatewayNotification processNotification(final boolean shouldDispatch, final String notification, final String pluginName, final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentApiException {
        final PaymentPluginApi plugin = getPaymentPluginApi(pluginName);

        if (shouldDispatch) {
            return dispatchWithExceptionHandling(null,
                                                 pluginName,
                                                 new Callable<PluginDispatcherReturnType<GatewayNotification>>() {
                                                     @Override
                                                     public PluginDispatcherReturnType<GatewayNotification> call() throws PaymentApiException {
                                                         try {
                                                             final GatewayNotification result = plugin.processNotification(notification, properties, callContext);
                                                             return PluginDispatcher.createPluginDispatcherReturnType(result == null ? new DefaultNoOpGatewayNotification() : result);
                                                         } catch (final PaymentPluginApiException e) {
                                                             throw new PaymentApiException(e, ErrorCode.PAYMENT_PLUGIN_EXCEPTION, e.getErrorMessage());
                                                         }
                                                     }
                                                 }, paymentPluginNotificationDispatcher);
        } else {
            try {
                return plugin.processNotification(notification, properties, callContext);
            } catch (final PaymentPluginApiException e) {
                throw new PaymentApiException(e, ErrorCode.PAYMENT_PLUGIN_EXCEPTION, e.getErrorMessage());
            }
        }
    }

    public HostedPaymentPageFormDescriptor buildFormDescriptor(final boolean shouldDispatch, final Account account, final UUID paymentMethodId, final Iterable<PluginProperty> customFields, final Iterable<PluginProperty> properties, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        final String pluginName = getPaymentMethodById(paymentMethodId, true, internalCallContext).getPluginName();
        final PaymentPluginApi plugin = getPaymentPluginApi(pluginName);

        if (shouldDispatch) {
            return dispatchWithExceptionHandling(account,
                                                 pluginName,
                                                 new Callable<PluginDispatcherReturnType<HostedPaymentPageFormDescriptor>>() {
                                                     @Override
                                                     public PluginDispatcherReturnType<HostedPaymentPageFormDescriptor> call() throws PaymentApiException {
                                                         try {
                                                             final HostedPaymentPageFormDescriptor result = plugin.buildFormDescriptor(account.getId(), customFields, properties, callContext);
                                                             return PluginDispatcher.createPluginDispatcherReturnType(result == null ? new DefaultNoOpHostedPaymentPageFormDescriptor(account.getId()) : result);
                                                         } catch (final RuntimeException e) {
                                                             throw new PaymentApiException(e, ErrorCode.PAYMENT_INTERNAL_ERROR, MoreObjects.firstNonNull(e.getMessage(), ""));
                                                         } catch (final PaymentPluginApiException e) {
                                                             throw new PaymentApiException(e, ErrorCode.PAYMENT_PLUGIN_EXCEPTION, e.getErrorMessage());
                                                         }
                                                     }
                                                 }, paymentPluginFormDispatcher);
        } else {
            try {
                return plugin.buildFormDescriptor(account.getId(), customFields, properties, callContext);
            } catch (final PaymentPluginApiException e) {
                throw new PaymentApiException(e, ErrorCode.PAYMENT_PLUGIN_EXCEPTION, e.getErrorMessage());
            }
        }
    }
}
