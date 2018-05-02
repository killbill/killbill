/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.payment;

import org.killbill.billing.GuicyKillbillTestSuiteWithEmbeddedDB;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.control.plugin.api.PaymentControlPluginApi;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.AdminPaymentApi;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.payment.api.PaymentGatewayApi;
import org.killbill.billing.payment.caching.StateMachineConfigCache;
import org.killbill.billing.payment.core.PaymentExecutors;
import org.killbill.billing.payment.core.PaymentMethodProcessor;
import org.killbill.billing.payment.core.PaymentPluginServiceRegistration;
import org.killbill.billing.payment.core.PaymentProcessor;
import org.killbill.billing.payment.core.janitor.IncompletePaymentTransactionTask;
import org.killbill.billing.payment.core.janitor.Janitor;
import org.killbill.billing.payment.core.sm.PaymentControlStateMachineHelper;
import org.killbill.billing.payment.core.sm.PaymentStateMachineHelper;
import org.killbill.billing.payment.core.sm.PluginControlPaymentAutomatonRunner;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.glue.PaymentModule;
import org.killbill.billing.payment.glue.TestPaymentModuleWithEmbeddedDB;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.provider.MockPaymentProviderPlugin;
import org.killbill.billing.payment.retry.DefaultRetryService;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.config.definition.PaymentConfig;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.bus.api.PersistentBus;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.profiling.Profiling;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;

import static org.killbill.billing.payment.provider.MockPaymentControlProviderPlugin.PLUGIN_NAME;

public abstract class PaymentTestSuiteWithEmbeddedDB extends GuicyKillbillTestSuiteWithEmbeddedDB {

    @Inject
    protected PaymentConfig paymentConfig;
    @Inject
    protected PaymentMethodProcessor paymentMethodProcessor;
    @Inject
    protected PaymentProcessor paymentProcessor;
    @Inject
    protected DefaultRetryService retryService;
    @Inject
    protected InvoiceInternalApi invoiceApi;
    @Inject
    protected PaymentPluginServiceRegistration paymentPluginServiceRegistration;
    @Inject
    protected OSGIServiceRegistration<PaymentPluginApi> registry;
    @Inject
    protected OSGIServiceRegistration<PaymentControlPluginApi> controlPluginRegistry;
    @Inject
    protected PersistentBus eventBus;
    @Inject
    protected PaymentApi paymentApi;
    @Inject
    protected AdminPaymentApi adminPaymentApi;
    @Inject
    protected PaymentGatewayApi paymentGatewayApi;
    @Inject
    protected AccountInternalApi accountApi;
    @Inject
    protected PaymentStateMachineHelper paymentSMHelper;
    @Inject
    protected PaymentDao paymentDao;
    @Inject
    protected TestPaymentHelper testHelper;
    @Inject
    protected PaymentExecutors paymentExecutors;
    @Inject
    protected NonEntityDao nonEntityDao;
    @Inject
    protected StateMachineConfigCache stateMachineConfigCache;
    @Inject
    protected Janitor janitor;
    @Inject
    protected IncompletePaymentTransactionTask incompletePaymentTransactionTask;
    @Inject
    protected GlobalLocker locker;
    @Inject
    protected PluginControlPaymentAutomatonRunner pluginControlPaymentAutomatonRunner;
    @Inject
    protected PaymentControlStateMachineHelper paymentControlStateMachineHelper;

    @Override
    protected KillbillConfigSource getConfigSource() {
        return getConfigSource("/payment.properties",
                               ImmutableMap.<String, String>of("org.killbill.payment.provider.default", MockPaymentProviderPlugin.PLUGIN_NAME,
                                                               "killbill.payment.engine.events.off", "false"));
    }

    @BeforeClass(groups = "slow")
    protected void beforeClass() throws Exception {
        if (hasFailed()) {
            return;
        }

        final Injector injector = Guice.createInjector(new TestPaymentModuleWithEmbeddedDB(configSource, getClock()));
        injector.injectMembers(this);
    }

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();

        stateMachineConfigCache.clearPaymentStateMachineConfig(PLUGIN_NAME, internalCallContext);
        stateMachineConfigCache.loadDefaultPaymentStateMachineConfig(PaymentModule.DEFAULT_STATE_MACHINE_PAYMENT_XML);

        paymentExecutors.initialize();
        eventBus.start();
        Profiling.resetPerThreadProfilingData();
        clock.resetDeltaFromReality();

        janitor.initialize();
        janitor.start();
    }

    @AfterMethod(groups = "slow")
    public void afterMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        janitor.stop();
        eventBus.stop();
        paymentExecutors.stop();
    }
}
