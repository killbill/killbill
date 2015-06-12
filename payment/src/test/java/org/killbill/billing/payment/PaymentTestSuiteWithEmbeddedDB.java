/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.payment;

import org.killbill.billing.GuicyKillbillTestSuiteWithEmbeddedDB;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.payment.core.PaymentProcessor;
import org.killbill.billing.payment.core.PaymentMethodProcessor;
import org.killbill.billing.payment.core.sm.PaymentStateMachineHelper;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.glue.TestPaymentModuleWithEmbeddedDB;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.provider.MockPaymentProviderPlugin;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.config.PaymentConfig;
import org.killbill.bus.api.PersistentBus;
import org.killbill.commons.profiling.Profiling;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;

public abstract class PaymentTestSuiteWithEmbeddedDB extends GuicyKillbillTestSuiteWithEmbeddedDB {

    @Inject
    protected PaymentConfig paymentConfig;
    @Inject
    protected PaymentMethodProcessor paymentMethodProcessor;
    @Inject
    protected PaymentProcessor paymentProcessor;
    @Inject
    protected InvoiceInternalApi invoiceApi;
    @Inject
    protected OSGIServiceRegistration<PaymentPluginApi> registry;
    @Inject
    protected PersistentBus eventBus;
    @Inject
    protected PaymentApi paymentApi;
    @Inject
    protected AccountInternalApi accountApi;
    @Inject
    protected PaymentStateMachineHelper paymentSMHelper;
    @Inject
    protected PaymentDao paymentDao;
    @Inject
    protected TestPaymentHelper testHelper;

    @Override
    protected KillbillConfigSource getConfigSource() {
        return getConfigSource("/payment.properties",
                               ImmutableMap.<String, String>of("org.killbill.payment.provider.default", MockPaymentProviderPlugin.PLUGIN_NAME,
                                                               "killbill.payment.engine.events.off", "false"));
    }

    @BeforeClass(groups = "slow")
    protected void beforeClass() throws Exception {
        final Injector injector = Guice.createInjector(new TestPaymentModuleWithEmbeddedDB(configSource, getClock()));
        injector.injectMembers(this);
    }

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        eventBus.start();
        Profiling.resetPerThreadProfilingData();
        clock.resetDeltaFromReality();

    }

    @AfterMethod(groups = "slow")
    public void afterMethod() throws Exception {
        eventBus.stop();
    }
}
