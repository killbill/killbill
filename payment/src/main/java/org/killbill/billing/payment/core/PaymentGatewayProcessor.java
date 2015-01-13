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

package org.killbill.billing.payment.core;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.billing.payment.dispatcher.PluginDispatcher.PluginDispatcherReturnType;
import org.killbill.billing.payment.plugin.api.GatewayNotification;
import org.killbill.billing.payment.plugin.api.HostedPaymentPageFormDescriptor;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiException;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.config.PaymentConfig;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLocker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects;
import com.google.inject.name.Named;

import static org.killbill.billing.payment.glue.PaymentModule.PLUGIN_EXECUTOR_NAMED;

// We don't take any lock here because the call needs to be re-entrant
// from the plugin: for example, the BitPay plugin will create the payment during the
// processNotification call, while the PayU plugin will create it during buildFormDescriptor.
// These calls are not necessarily idempotent though (the PayU plugin will create
// a voucher in the gateway during the buildFormDescriptor call).
public class PaymentGatewayProcessor extends ProcessorBase {

    private final PluginDispatcher<HostedPaymentPageFormDescriptor> paymentPluginFormDispatcher;
    private final PluginDispatcher<GatewayNotification> paymentPluginNotificationDispatcher;

    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayProcessor.class);

    @Inject
    public PaymentGatewayProcessor(final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry,
                                   final AccountInternalApi accountUserApi,
                                   final InvoiceInternalApi invoiceApi,
                                   final TagInternalApi tagUserApi,
                                   final PaymentDao paymentDao,
                                   final NonEntityDao nonEntityDao,
                                   final GlobalLocker locker,
                                   final PaymentConfig paymentConfig,
                                   @Named(PLUGIN_EXECUTOR_NAMED) final ExecutorService executor,
                                   final Clock clock,
                                   final CacheControllerDispatcher controllerDispatcher) {
        super(pluginRegistry, accountUserApi, paymentDao, nonEntityDao, tagUserApi, locker, executor, invoiceApi, clock, controllerDispatcher);
        final long paymentPluginTimeoutSec = TimeUnit.SECONDS.convert(paymentConfig.getPaymentPluginTimeout().getPeriod(), paymentConfig.getPaymentPluginTimeout().getUnit());
        this.paymentPluginFormDispatcher = new PluginDispatcher<HostedPaymentPageFormDescriptor>(paymentPluginTimeoutSec, executor);
        this.paymentPluginNotificationDispatcher = new PluginDispatcher<GatewayNotification>(paymentPluginTimeoutSec, executor);
    }

    public GatewayNotification processNotification(final String notification, final String pluginName, final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentApiException {
        return dispatchWithExceptionHandling(null,
                                             new Callable<PluginDispatcherReturnType<GatewayNotification>>() {
                                                 @Override
                                                 public PluginDispatcherReturnType<GatewayNotification> call() throws PaymentApiException {
                                                     final PaymentPluginApi plugin = getPaymentPluginApi(pluginName);
                                                     try {
                                                         final GatewayNotification result = plugin.processNotification(notification, properties, callContext);
                                                         return PluginDispatcher.createPluginDispatcherReturnType(result);
                                                     } catch (final PaymentPluginApiException e) {
                                                         throw new PaymentApiException(ErrorCode.PAYMENT_PLUGIN_EXCEPTION, e.getErrorMessage());
                                                     }
                                                 }
                                             }, paymentPluginNotificationDispatcher);
    }

    public HostedPaymentPageFormDescriptor buildFormDescriptor(final Account account, final Iterable<PluginProperty> customFields, final Iterable<PluginProperty> properties, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return dispatchWithExceptionHandling(account,
                                             new Callable<PluginDispatcherReturnType<HostedPaymentPageFormDescriptor>>() {
                                                 @Override
                                                 public PluginDispatcherReturnType<HostedPaymentPageFormDescriptor> call() throws PaymentApiException {
                                                     final PaymentPluginApi plugin = getPaymentProviderPlugin(account, internalCallContext);

                                                     try {
                                                         final HostedPaymentPageFormDescriptor result = plugin.buildFormDescriptor(account.getId(), customFields, properties, callContext);
                                                         return PluginDispatcher.createPluginDispatcherReturnType(result);
                                                     } catch (final RuntimeException e) {
                                                         throw new PaymentApiException(e, ErrorCode.PAYMENT_INTERNAL_ERROR, Objects.firstNonNull(e.getMessage(), ""));
                                                     } catch (final PaymentPluginApiException e) {
                                                         throw new PaymentApiException(ErrorCode.PAYMENT_PLUGIN_EXCEPTION, e.getErrorMessage());
                                                     }
                                                 }
                                             }, paymentPluginFormDispatcher);
    }
}
