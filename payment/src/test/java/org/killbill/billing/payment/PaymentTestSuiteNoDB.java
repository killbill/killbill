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

import org.killbill.billing.GuicyKillbillTestSuiteNoDB;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.DirectPaymentApi;
import org.killbill.billing.payment.core.DirectPaymentProcessor;
import org.killbill.billing.payment.core.PaymentMethodProcessor;
import org.killbill.billing.payment.core.PluginControlledPaymentProcessor;
import org.killbill.billing.payment.core.sm.PluginControlledDirectPaymentAutomatonRunner;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.glue.TestPaymentModuleNoDB;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.provider.MockPaymentProviderPlugin;
import org.killbill.billing.payment.retry.DefaultRetryService;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.config.PaymentConfig;
import org.killbill.bus.api.PersistentBus;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;

public abstract class PaymentTestSuiteNoDB extends GuicyKillbillTestSuiteNoDB {

    @Inject
    protected PaymentConfig paymentConfig;
    @Inject
    protected PaymentMethodProcessor paymentMethodProcessor;
    @Inject
    protected InvoiceInternalApi invoiceApi;
    @Inject
    protected OSGIServiceRegistration<PaymentPluginApi> registry;
    @Inject
    protected PersistentBus eventBus;
    @Inject
    protected DirectPaymentApi paymentApi;
    @Inject
    protected AccountInternalApi accountInternalApi;
    @Inject
    protected TestPaymentHelper testHelper;
    @Inject
    protected PaymentDao paymentDao;
    @Inject
    protected DirectPaymentProcessor paymentProcessor;
    @Inject
    protected PluginControlledPaymentProcessor pluginControlledPaymentProcessor;
    @Inject
    protected PluginControlledDirectPaymentAutomatonRunner retryableDirectPaymentAutomatonRunner;
    @Inject
    protected DefaultRetryService retryService;

    @Override
    protected KillbillConfigSource getConfigSource() {
        return getConfigSource("/payment.properties",
                               ImmutableMap.<String, String>of("org.killbill.payment.provider.default", MockPaymentProviderPlugin.PLUGIN_NAME,
                                                               "killbill.payment.engine.events.off", "false"));

    }

    @BeforeClass(groups = "fast")
    protected void beforeClass() throws Exception {
        final Injector injector = Guice.createInjector(new TestPaymentModuleNoDB(configSource, getClock()));
        injector.injectMembers(this);
    }

    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        eventBus.start();
    }

    @AfterMethod(groups = "fast")
    public void afterMethod() throws Exception {
        eventBus.stop();
    }
}
