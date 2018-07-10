/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.payment.glue;

import javax.inject.Provider;

import org.killbill.automaton.DefaultStateMachineConfig;
import org.killbill.automaton.StateMachineConfig;
import org.killbill.billing.control.plugin.api.PaymentControlPluginApi;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.AdminPaymentApi;
import org.killbill.billing.payment.api.DefaultAdminPaymentApi;
import org.killbill.billing.payment.api.DefaultInvoicePaymentApi;
import org.killbill.billing.payment.api.DefaultPaymentApi;
import org.killbill.billing.payment.api.DefaultPaymentGatewayApi;
import org.killbill.billing.payment.api.InvoicePaymentApi;
import org.killbill.billing.payment.api.InvoicePaymentInternalApi;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.payment.api.PaymentGatewayApi;
import org.killbill.billing.payment.api.PaymentService;
import org.killbill.billing.payment.api.svcs.DefaultInvoicePaymentInternalApi;
import org.killbill.billing.payment.bus.PaymentBusEventHandler;
import org.killbill.billing.payment.config.MultiTenantPaymentConfig;
import org.killbill.billing.payment.caching.EhCacheStateMachineConfigCache;
import org.killbill.billing.payment.caching.StateMachineConfigCache;
import org.killbill.billing.payment.caching.StateMachineConfigCacheInvalidationCallback;
import org.killbill.billing.payment.core.PaymentExecutors;
import org.killbill.billing.payment.core.PaymentGatewayProcessor;
import org.killbill.billing.payment.core.PaymentMethodProcessor;
import org.killbill.billing.payment.core.PaymentPluginServiceRegistration;
import org.killbill.billing.payment.core.PaymentProcessor;
import org.killbill.billing.payment.core.PluginControlPaymentProcessor;
import org.killbill.billing.payment.core.janitor.IncompletePaymentAttemptTask;
import org.killbill.billing.payment.core.janitor.IncompletePaymentTransactionTask;
import org.killbill.billing.payment.core.janitor.Janitor;
import org.killbill.billing.payment.core.sm.PaymentControlStateMachineHelper;
import org.killbill.billing.payment.core.sm.PaymentStateMachineHelper;
import org.killbill.billing.payment.core.sm.PluginControlPaymentAutomatonRunner;
import org.killbill.billing.payment.core.sm.control.ControlPluginRunner;
import org.killbill.billing.payment.dao.DefaultPaymentDao;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.invoice.PaymentTagHandler;
import org.killbill.billing.payment.invoice.dao.InvoicePaymentControlDao;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.retry.BaseRetryService.RetryServiceScheduler;
import org.killbill.billing.payment.retry.DefaultRetryService;
import org.killbill.billing.payment.retry.DefaultRetryService.DefaultRetryServiceScheduler;
import org.killbill.billing.payment.retry.RetryService;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.config.definition.PaymentConfig;
import org.killbill.billing.tenant.api.TenantInternalApi.CacheInvalidationCallback;
import org.killbill.billing.util.glue.KillBillModule;
import org.killbill.xmlloader.XMLLoader;
import org.skife.config.ConfigurationObjectFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.Resources;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;

public class PaymentModule extends KillBillModule {

    public static final String STATIC_CONFIG = "StaticConfig";

    public static final String RETRYABLE_NAMED = "Retryable";

    public static final String STATE_MACHINE_RETRY = "RetryStateMachine";

    @VisibleForTesting
    public static final String DEFAULT_STATE_MACHINE_RETRY_XML = "org/killbill/billing/payment/retry/RetryStates.xml";
    @VisibleForTesting
    public static final String DEFAULT_STATE_MACHINE_PAYMENT_XML = "org/killbill/billing/payment/PaymentStates.xml";

    public static final String STATE_MACHINE_CONFIG_INVALIDATION_CALLBACK = "StateMachineConfigInvalidationCallback";

    public PaymentModule(final KillbillConfigSource configSource) {
        super(configSource);
    }

    protected void installPaymentDao() {
        bind(PaymentDao.class).to(DefaultPaymentDao.class).asEagerSingleton();
        // Payment Control Plugin Dao
        bind(InvoicePaymentControlDao.class).asEagerSingleton();
    }

    protected void installPaymentProviderPlugins(final PaymentConfig config) {
    }

    protected void installJanitor() {
        bind(Janitor.class).asEagerSingleton();
    }

    protected void installRetryEngines() {
        bind(DefaultRetryService.class).asEagerSingleton();
        bind(RetryService.class).annotatedWith(Names.named(RETRYABLE_NAMED)).to(DefaultRetryService.class);

        bind(DefaultRetryServiceScheduler.class).asEagerSingleton();
        bind(RetryServiceScheduler.class).annotatedWith(Names.named(RETRYABLE_NAMED)).to(DefaultRetryServiceScheduler.class);
    }

    protected void installStateMachines() {
        bind(StateMachineProvider.class).annotatedWith(Names.named(STATE_MACHINE_RETRY)).toInstance(new StateMachineProvider(DEFAULT_STATE_MACHINE_RETRY_XML));
        bind(StateMachineConfig.class).annotatedWith(Names.named(STATE_MACHINE_RETRY)).toProvider(Key.get(StateMachineProvider.class, Names.named(STATE_MACHINE_RETRY)));

        bind(PaymentControlStateMachineHelper.class).asEagerSingleton();

        bind(StateMachineConfigCache.class).to(EhCacheStateMachineConfigCache.class).asEagerSingleton();
        bind(CacheInvalidationCallback.class).annotatedWith(Names.named(STATE_MACHINE_CONFIG_INVALIDATION_CALLBACK)).to(StateMachineConfigCacheInvalidationCallback.class).asEagerSingleton();

        bind(PaymentStateMachineHelper.class).asEagerSingleton();

        bind(ControlPluginRunner.class).asEagerSingleton();
    }

    protected void installAutomatonRunner() {
        bind(PluginControlPaymentAutomatonRunner.class).asEagerSingleton();
    }

    protected void installProcessors(final PaymentConfig paymentConfig) {
        bind(IncompletePaymentAttemptTask.class).asEagerSingleton();
        bind(IncompletePaymentTransactionTask.class).asEagerSingleton();
        bind(PaymentProcessor.class).asEagerSingleton();
        bind(PluginControlPaymentProcessor.class).asEagerSingleton();
        bind(PaymentGatewayProcessor.class).asEagerSingleton();
        bind(PaymentMethodProcessor.class).asEagerSingleton();
    }

    @Override
    protected void configure() {
        final ConfigurationObjectFactory factory = new ConfigurationObjectFactory(skifeConfigSource);
        final PaymentConfig paymentConfig = factory.build(PaymentConfig.class);
        bind(PaymentConfig.class).annotatedWith(Names.named(STATIC_CONFIG)).toInstance(paymentConfig);
        bind(PaymentConfig.class).to(MultiTenantPaymentConfig.class).asEagerSingleton();

        bind(new TypeLiteral<OSGIServiceRegistration<PaymentPluginApi>>() {}).toProvider(DefaultPaymentProviderPluginRegistryProvider.class).asEagerSingleton();
        bind(new TypeLiteral<OSGIServiceRegistration<PaymentControlPluginApi>>() {}).toProvider(DefaultPaymentControlProviderPluginRegistryProvider.class).asEagerSingleton();

        bind(PaymentPluginServiceRegistration.class).asEagerSingleton();

        bind(PaymentApi.class).to(DefaultPaymentApi.class).asEagerSingleton();
        bind(InvoicePaymentApi.class).to(DefaultInvoicePaymentApi.class).asEagerSingleton();
        bind(InvoicePaymentInternalApi.class).to(DefaultInvoicePaymentInternalApi.class).asEagerSingleton();
        bind(PaymentGatewayApi.class).to(DefaultPaymentGatewayApi.class).asEagerSingleton();
        bind(AdminPaymentApi.class).to(DefaultAdminPaymentApi.class).asEagerSingleton();
        bind(PaymentBusEventHandler.class).asEagerSingleton();
        bind(PaymentTagHandler.class).asEagerSingleton();
        bind(PaymentService.class).to(DefaultPaymentService.class).asEagerSingleton();
        bind(PaymentExecutors.class).asEagerSingleton();
        installPaymentProviderPlugins(paymentConfig);
        installPaymentDao();
        installProcessors(paymentConfig);
        installStateMachines();
        installAutomatonRunner();
        installRetryEngines();
        installJanitor();
    }

    public static final class StateMachineProvider implements Provider<StateMachineConfig> {

        private final String stateMachineConfig;

        public StateMachineProvider(final String stateMachineConfig) {
            this.stateMachineConfig = stateMachineConfig;
        }

        @Override
        public StateMachineConfig get() {
            try {
                return XMLLoader.getObjectFromString(Resources.getResource(stateMachineConfig).toExternalForm(), DefaultStateMachineConfig.class);
            } catch (final Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
