/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.payment.glue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.killbill.billing.payment.api.DirectPaymentApi;
import org.killbill.billing.payment.api.svcs.DefaultDirectPaymentApi;
import org.killbill.billing.payment.core.DirectPaymentProcessor;
import org.skife.config.ConfigSource;
import org.skife.config.ConfigurationObjectFactory;

import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.DefaultPaymentApi;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.payment.api.PaymentInternalApi;
import org.killbill.billing.payment.api.PaymentService;
import org.killbill.billing.payment.api.svcs.DefaultPaymentInternalApi;
import org.killbill.billing.payment.bus.InvoiceHandler;
import org.killbill.billing.payment.bus.PaymentTagHandler;
import org.killbill.billing.payment.core.PaymentMethodProcessor;
import org.killbill.billing.payment.core.PaymentProcessor;
import org.killbill.billing.payment.core.RefundProcessor;
import org.killbill.billing.payment.dao.DefaultPaymentDao;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.retry.AutoPayRetryService;
import org.killbill.billing.payment.retry.AutoPayRetryService.AutoPayRetryServiceScheduler;
import org.killbill.billing.payment.retry.FailedPaymentRetryService;
import org.killbill.billing.payment.retry.FailedPaymentRetryService.FailedPaymentRetryServiceScheduler;
import org.killbill.billing.payment.retry.PluginFailureRetryService;
import org.killbill.billing.payment.retry.PluginFailureRetryService.PluginFailureRetryServiceScheduler;
import org.killbill.billing.util.config.PaymentConfig;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;

public class PaymentModule extends AbstractModule {

    private static final String PLUGIN_THREAD_PREFIX = "Plugin-th-";

    public static final String PLUGIN_EXECUTOR_NAMED = "PluginExecutor";

    protected ConfigSource configSource;

    public PaymentModule(final ConfigSource configSource) {
        this.configSource = configSource;
    }

    protected void installPaymentDao() {
        bind(PaymentDao.class).to(DefaultPaymentDao.class).asEagerSingleton();
    }

    protected void installPaymentProviderPlugins(final PaymentConfig config) {
    }

    protected void installRetryEngines() {
        bind(FailedPaymentRetryService.class).asEagerSingleton();
        bind(PluginFailureRetryService.class).asEagerSingleton();
        bind(AutoPayRetryService.class).asEagerSingleton();
        bind(FailedPaymentRetryServiceScheduler.class).asEagerSingleton();
        bind(PluginFailureRetryServiceScheduler.class).asEagerSingleton();
        bind(AutoPayRetryServiceScheduler.class).asEagerSingleton();
    }

    protected void installProcessors(final PaymentConfig paymentConfig) {
        final ExecutorService pluginExecutorService = Executors.newFixedThreadPool(paymentConfig.getPaymentPluginThreadNb(), new ThreadFactory() {

            @Override
            public Thread newThread(final Runnable r) {
                final Thread th = new Thread(r);
                th.setName(PLUGIN_THREAD_PREFIX + th.getId());
                return th;
            }
        });
        bind(ExecutorService.class).annotatedWith(Names.named(PLUGIN_EXECUTOR_NAMED)).toInstance(pluginExecutorService);
        bind(PaymentProcessor.class).asEagerSingleton();
        bind(DirectPaymentProcessor.class).asEagerSingleton();
        bind(RefundProcessor.class).asEagerSingleton();
        bind(PaymentMethodProcessor.class).asEagerSingleton();
    }

    @Override
    protected void configure() {
        final ConfigurationObjectFactory factory = new ConfigurationObjectFactory(configSource);
        final PaymentConfig paymentConfig = factory.build(PaymentConfig.class);

        bind(PaymentConfig.class).toInstance(paymentConfig);
        bind(new TypeLiteral<OSGIServiceRegistration<PaymentPluginApi>>() {}).toProvider(DefaultPaymentProviderPluginRegistryProvider.class).asEagerSingleton();

        bind(PaymentInternalApi.class).to(DefaultPaymentInternalApi.class).asEagerSingleton();
        bind(PaymentApi.class).to(DefaultPaymentApi.class).asEagerSingleton();
        bind(DirectPaymentApi.class).to(DefaultDirectPaymentApi.class).asEagerSingleton();
        bind(InvoiceHandler.class).asEagerSingleton();
        bind(PaymentTagHandler.class).asEagerSingleton();
        bind(PaymentService.class).to(DefaultPaymentService.class).asEagerSingleton();
        installPaymentProviderPlugins(paymentConfig);
        installPaymentDao();
        installProcessors(paymentConfig);
        installRetryEngines();
    }
}
