/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.payment;

import java.util.List;

import javax.inject.Named;

import org.killbill.billing.GuicyKillbillTestSuiteNoDB;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.InvoicePaymentApi;
import org.killbill.billing.payment.api.InvoicePaymentInternalApi;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.payment.api.PaymentGatewayApi;
import org.killbill.billing.payment.api.PaymentOptions;
import org.killbill.billing.payment.caching.StateMachineConfigCache;
import org.killbill.billing.payment.core.PaymentExecutors;
import org.killbill.billing.payment.core.PaymentMethodProcessor;
import org.killbill.billing.payment.core.PaymentPluginServiceRegistration;
import org.killbill.billing.payment.core.PaymentProcessor;
import org.killbill.billing.payment.core.PaymentRefresher;
import org.killbill.billing.payment.core.PluginControlPaymentProcessor;
import org.killbill.billing.payment.core.sm.PaymentStateMachineHelper;
import org.killbill.billing.payment.core.sm.PluginControlPaymentAutomatonRunner;
import org.killbill.billing.payment.dao.MockPaymentDao;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.glue.PaymentModule;
import org.killbill.billing.payment.glue.TestPaymentModuleNoDB;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.provider.MockPaymentProviderPlugin;
import org.killbill.billing.payment.retry.DefaultRetryService;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.tenant.api.TenantInternalApi;
import org.killbill.billing.tenant.api.TenantInternalApi.CacheInvalidationCallback;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.config.definition.PaymentConfig;
import org.killbill.bus.api.PersistentBus;
import org.killbill.commons.profiling.Profiling;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;

import static org.killbill.billing.payment.provider.MockPaymentControlProviderPlugin.PLUGIN_NAME;

public abstract class PaymentTestSuiteNoDB extends GuicyKillbillTestSuiteNoDB {

    @Inject
    protected PaymentConfig paymentConfig;
    @Inject
    protected PaymentMethodProcessor paymentMethodProcessor;
    @Inject
    protected InvoiceInternalApi invoiceApi;
    @Inject
    protected PaymentPluginServiceRegistration paymentPluginServiceRegistration;
    @Inject
    protected OSGIServiceRegistration<PaymentPluginApi> registry;
    @Inject
    protected PersistentBus eventBus;
    @Inject
    protected PaymentApi paymentApi;
    @Inject
    protected InvoicePaymentApi invoicePaymentApi;
    @Inject
    protected InvoicePaymentInternalApi invoicePaymentInternalApi;
    @Inject
    protected PaymentGatewayApi paymentGatewayApi;
    @Inject
    protected AccountInternalApi accountInternalApi;
    @Inject
    protected TestPaymentHelper testHelper;
    @Inject
    protected PaymentDao paymentDao;
    @Inject
    protected PaymentStateMachineHelper paymentSMHelper;
    @Inject
    protected PaymentRefresher paymentRefresher;
    @Inject
    protected PaymentProcessor paymentProcessor;
    @Inject
    protected PluginControlPaymentProcessor pluginControlPaymentProcessor;
    @Inject
    protected PluginControlPaymentAutomatonRunner retryablePaymentAutomatonRunner;
    @Inject
    protected DefaultRetryService retryService;
    @Inject
    protected CacheControllerDispatcher cacheControllerDispatcher;
    @Inject
    protected PaymentExecutors paymentExecutors;
    @Inject
    protected StateMachineConfigCache stateMachineConfigCache;
    @Inject
    @Named(PaymentModule.STATE_MACHINE_CONFIG_INVALIDATION_CALLBACK)
    protected CacheInvalidationCallback cacheInvalidationCallback;
    @Inject
    protected TenantInternalApi tenantInternalApi;

    protected static final PaymentOptions PAYMENT_OPTIONS = new PaymentOptions() {
        @Override
        public boolean isExternalPayment() {
            return false;
        }

        @Override
        public List<String> getPaymentControlPluginNames() {
            return ImmutableList.<String>of();
        }
    };

    @Override
    protected KillbillConfigSource getConfigSource() {
        return getConfigSource("/payment.properties",
                               ImmutableMap.<String, String>of("org.killbill.payment.provider.default", MockPaymentProviderPlugin.PLUGIN_NAME,
                                                               "killbill.payment.engine.events.off", "false"));

    }

    @BeforeClass(groups = "fast")
    protected void beforeClass() throws Exception {
        if (hasFailed()) {
            return;
        }

        final Injector injector = Guice.createInjector(new TestPaymentModuleNoDB(configSource, getClock()));
        injector.injectMembers(this);
    }

    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        stateMachineConfigCache.clearPaymentStateMachineConfig(PLUGIN_NAME, internalCallContext);
        stateMachineConfigCache.loadDefaultPaymentStateMachineConfig(PaymentModule.DEFAULT_STATE_MACHINE_PAYMENT_XML);

        eventBus.start();
        paymentExecutors.initialize();
        ((MockPaymentDao) paymentDao).reset();
        Profiling.resetPerThreadProfilingData();
    }

    @AfterMethod(groups = "fast")
    public void afterMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        paymentExecutors.stop();
        eventBus.stop();
    }
}
