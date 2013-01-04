/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.payment.glue;

import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.skife.config.ConfigSource;
import org.skife.config.ConfigurationObjectFactory;
import org.skife.config.SimplePropertyConfigSource;

import com.ning.billing.payment.bus.PaymentTagHandler;
import com.ning.billing.payment.dao.DefaultPaymentDao;
import com.ning.billing.util.config.PaymentConfig;
import com.ning.billing.payment.api.DefaultPaymentApi;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.payment.api.PaymentService;
import com.ning.billing.payment.api.svcs.DefaultPaymentInternalApi;
import com.ning.billing.payment.bus.InvoiceHandler;
import com.ning.billing.payment.core.PaymentMethodProcessor;
import com.ning.billing.payment.core.PaymentProcessor;
import com.ning.billing.payment.core.RefundProcessor;
import com.ning.billing.payment.dao.PaymentDao;
import com.ning.billing.payment.provider.PaymentProviderPluginRegistry;
import com.ning.billing.payment.retry.AutoPayRetryService;
import com.ning.billing.payment.retry.AutoPayRetryService.AutoPayRetryServiceScheduler;
import com.ning.billing.payment.retry.FailedPaymentRetryService;
import com.ning.billing.payment.retry.FailedPaymentRetryService.FailedPaymentRetryServiceScheduler;
import com.ning.billing.payment.retry.PluginFailureRetryService;
import com.ning.billing.payment.retry.PluginFailureRetryService.PluginFailureRetryServiceScheduler;
import com.ning.billing.util.svcapi.payment.PaymentInternalApi;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

public class PaymentModule extends AbstractModule {
    private static final int PLUGIN_NB_THREADS = 3;
    private static final String PLUGIN_THREAD_PREFIX = "Plugin-th-";

    public static final String PLUGIN_EXECUTOR_NAMED = "PluginExecutor";

    @VisibleForTesting
    protected ConfigSource configSource;

    public PaymentModule() {
        this(System.getProperties());
    }

    public PaymentModule(final ConfigSource configSource) {
        this.configSource = configSource;
    }

    public PaymentModule(final Properties properties) {
        this(new SimplePropertyConfigSource(properties));
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

    protected void installProcessors() {
        final ExecutorService pluginExecutorService = Executors.newFixedThreadPool(PLUGIN_NB_THREADS, new ThreadFactory() {

            @Override
            public Thread newThread(final Runnable r) {
                final Thread th = new Thread(r);
                th.setName(PLUGIN_THREAD_PREFIX + th.getId());
                return th;
            }
        });
        bind(ExecutorService.class).annotatedWith(Names.named(PLUGIN_EXECUTOR_NAMED)).toInstance(pluginExecutorService);
        bind(PaymentProcessor.class).asEagerSingleton();
        bind(RefundProcessor.class).asEagerSingleton();
        bind(PaymentMethodProcessor.class).asEagerSingleton();
    }

    @Override
    protected void configure() {
        final ConfigurationObjectFactory factory = new ConfigurationObjectFactory(configSource);
        final PaymentConfig paymentConfig = factory.build(PaymentConfig.class);

        bind(PaymentConfig.class).toInstance(paymentConfig);
        bind(PaymentProviderPluginRegistry.class).toProvider(DefaultPaymentProviderPluginRegistryProvider.class).asEagerSingleton();

        bind(PaymentInternalApi.class).to(DefaultPaymentInternalApi.class).asEagerSingleton();
        bind(PaymentApi.class).to(DefaultPaymentApi.class).asEagerSingleton();
        bind(InvoiceHandler.class).asEagerSingleton();
        bind(PaymentTagHandler.class).asEagerSingleton();
        bind(PaymentService.class).to(DefaultPaymentService.class).asEagerSingleton();
        installPaymentProviderPlugins(paymentConfig);
        installPaymentDao();
        installProcessors();
        installRetryEngines();
    }
}
