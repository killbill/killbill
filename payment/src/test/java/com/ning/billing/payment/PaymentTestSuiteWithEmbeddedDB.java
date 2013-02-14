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

package com.ning.billing.payment;

import java.io.IOException;
import java.net.URL;
import java.util.Properties;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import com.ning.billing.GuicyKillbillTestSuiteWithEmbeddedDB;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.payment.core.PaymentMethodProcessor;
import com.ning.billing.payment.core.PaymentProcessor;
import com.ning.billing.payment.dao.PaymentDao;
import com.ning.billing.payment.glue.TestPaymentModuleWithEmbeddedDB;
import com.ning.billing.payment.provider.PaymentProviderPluginRegistry;
import com.ning.billing.payment.retry.FailedPaymentRetryService;
import com.ning.billing.payment.retry.PluginFailureRetryService;
import com.ning.billing.util.config.PaymentConfig;
import com.ning.billing.util.svcapi.account.AccountInternalApi;
import com.ning.billing.util.svcapi.invoice.InvoiceInternalApi;
import com.ning.billing.util.svcsapi.bus.InternalBus;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;

import static org.testng.Assert.assertNotNull;

public abstract class PaymentTestSuiteWithEmbeddedDB extends GuicyKillbillTestSuiteWithEmbeddedDB {

    @Inject
    protected PaymentConfig paymentConfig;
    @Inject
    protected PaymentProcessor paymentProcessor;
    @Inject
    protected PaymentMethodProcessor paymentMethodProcessor;
    @Inject
    protected InvoiceInternalApi invoiceApi;
    @Inject
    protected PaymentProviderPluginRegistry registry;
    @Inject
    protected FailedPaymentRetryService retryService;
    @Inject
    protected PluginFailureRetryService pluginRetryService;
    @Inject
    protected InternalBus eventBus;
    @Inject
    protected PaymentApi paymentApi;
    @Inject
    protected AccountInternalApi accountApi;
    @Inject
    protected PaymentDao paymentDao;
    @Inject
    protected TestPaymentHelper testHelper;

    @BeforeClass(groups = "slow")
    protected void setup() throws Exception {

        loadSystemPropertiesFromClasspath("/resource.properties");

        final Injector injector = Guice.createInjector(new TestPaymentModuleWithEmbeddedDB(getClock()));
        injector.injectMembers(this);
    }

    @BeforeMethod(groups = "slow")
    public void setupTest() throws Exception {
        eventBus.start();
    }

    @AfterMethod(groups = "slow")
    public void cleanupTest()throws Exception  {
        eventBus.stop();
    }



    private void loadSystemPropertiesFromClasspath(final String resource) {
        final URL url = PaymentTestSuiteWithEmbeddedDB.class.getResource(resource);
        assertNotNull(url);

        try {
            final Properties properties = System.getProperties();
            properties.load(url.openStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
