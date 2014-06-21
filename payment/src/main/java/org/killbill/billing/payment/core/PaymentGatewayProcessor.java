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

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;
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
import org.killbill.billing.payment.plugin.api.GatewayNotification;
import org.killbill.billing.payment.plugin.api.HostedPaymentPageFormDescriptor;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiException;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.config.PaymentConfig;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.bus.api.PersistentBus;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.locker.LockFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects;
import com.google.inject.name.Named;

import static org.killbill.billing.payment.glue.PaymentModule.PLUGIN_EXECUTOR_NAMED;

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
                                   final PersistentBus eventBus,
                                   final GlobalLocker locker,
                                   final PaymentConfig paymentConfig,
                                   @Named(PLUGIN_EXECUTOR_NAMED) final ExecutorService executor,
                                  final Clock clock) {
        super(pluginRegistry, accountUserApi, eventBus, paymentDao, nonEntityDao, tagUserApi, locker, executor, invoiceApi, clock);
        final long paymentPluginTimeoutSec = TimeUnit.SECONDS.convert(paymentConfig.getPaymentPluginTimeout().getPeriod(), paymentConfig.getPaymentPluginTimeout().getUnit());
        this.paymentPluginFormDispatcher = new PluginDispatcher<HostedPaymentPageFormDescriptor>(paymentPluginTimeoutSec, executor);
        this.paymentPluginNotificationDispatcher = new PluginDispatcher<GatewayNotification>(paymentPluginTimeoutSec, executor);
    }

    public HostedPaymentPageFormDescriptor buildFormDescriptor(final Account account, final Iterable<PluginProperty> customFields, final Iterable<PluginProperty> properties, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return dispatchWithExceptionHandling(account,
                                             new CallableWithAccountLock<HostedPaymentPageFormDescriptor, PaymentApiException>(locker,
                                                                                                                               account.getExternalKey(),
                                                                                                                               new WithAccountLockCallback<HostedPaymentPageFormDescriptor, PaymentApiException>() {

                                                                                                                                   @Override
                                                                                                                                   public HostedPaymentPageFormDescriptor doOperation() throws PaymentApiException {
                                                                                                                                       final PaymentPluginApi plugin = getPaymentProviderPlugin(account, internalCallContext);

                                                                                                                                       try {
                                                                                                                                           return plugin.buildFormDescriptor(account.getId(), customFields, properties, callContext);
                                                                                                                                       } catch (final RuntimeException e) {
                                                                                                                                           throw new PaymentApiException(e, ErrorCode.PAYMENT_INTERNAL_ERROR, Objects.firstNonNull(e.getMessage(), ""));
                                                                                                                                       } catch (final PaymentPluginApiException e) {
                                                                                                                                           throw new PaymentApiException(ErrorCode.PAYMENT_PLUGIN_EXCEPTION, e.getErrorMessage());
                                                                                                                                       }
                                                                                                                                   }
                                                                                                                               }),
                                             paymentPluginFormDispatcher);
    }

    public GatewayNotification processNotification(final String notification, final String pluginName, final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentApiException {
        return dispatchWithExceptionHandling(null,
                                      new Callable<GatewayNotification>() {
                                          @Override
                                          public GatewayNotification call() throws PaymentApiException {
                                              final PaymentPluginApi plugin = getPaymentPluginApi(pluginName);
                                              try {
                                                  return plugin.processNotification(notification, properties, callContext);
                                              } catch (PaymentPluginApiException e) {
                                                  throw new PaymentApiException(ErrorCode.PAYMENT_PLUGIN_EXCEPTION, e.getErrorMessage());
                                              }
                                          }
                                      }, paymentPluginNotificationDispatcher);
    }

    private static <ReturnType> ReturnType dispatchWithExceptionHandling(@Nullable final Account account, final Callable<ReturnType> callable, PluginDispatcher<ReturnType> pluginFormDispatcher) throws PaymentApiException {
        final UUID accountId = account != null ? account.getId() : null;
        final String accountExternalKey = account != null ? account.getExternalKey() : "";
        try {
            return pluginFormDispatcher.dispatchWithTimeout(callable);
        } catch (final TimeoutException e) {
            throw new PaymentApiException(ErrorCode.PAYMENT_PLUGIN_TIMEOUT, accountId, null);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentApiException(ErrorCode.PAYMENT_INTERNAL_ERROR, Objects.firstNonNull(e.getMessage(), ""));
        } catch (final ExecutionException e) {
            if (e.getCause() instanceof PaymentApiException) {
                throw (PaymentApiException) e.getCause();
            } else if (e.getCause() instanceof LockFailedException) {
                final String format = String.format("Failed to lock account %s", accountExternalKey);
                log.error(String.format(format), e);
                throw new PaymentApiException(ErrorCode.PAYMENT_INTERNAL_ERROR, format);
            } else {
                throw new PaymentApiException(e, ErrorCode.PAYMENT_INTERNAL_ERROR, Objects.firstNonNull(e.getMessage(), ""));
            }
        }
    }
}
