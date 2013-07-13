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

package com.ning.billing.invoice;

import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import com.ning.billing.GuicyKillbillTestSuiteNoDB;
import com.ning.billing.bus.api.PersistentBus;
import com.ning.billing.invoice.api.InvoiceMigrationApi;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.invoice.dao.InvoiceDao;
import com.ning.billing.invoice.generator.InvoiceGenerator;
import com.ning.billing.invoice.glue.TestInvoiceModuleNoDB;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.clock.Clock;
import com.ning.billing.util.globallocker.GlobalLocker;
import com.ning.billing.util.svcapi.account.AccountInternalApi;
import com.ning.billing.util.svcapi.subscription.SubscriptionInternalApi;
import com.ning.billing.util.svcapi.invoice.InvoiceInternalApi;
import com.ning.billing.util.svcapi.junction.BillingInternalApi;
import com.ning.billing.util.svcsapi.bus.BusService;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;

public abstract class InvoiceTestSuiteNoDB extends GuicyKillbillTestSuiteNoDB {

    private static final Logger log = LoggerFactory.getLogger(InvoiceTestSuiteNoDB.class);

    @Inject
    protected PersistentBus bus;
    @Inject
    protected CacheControllerDispatcher controllerDispatcher;
    @Inject
    protected InvoiceUserApi invoiceUserApi;
    @Inject
    protected InvoicePaymentApi invoicePaymentApi;
    @Inject
    protected InvoiceMigrationApi migrationApi;
    @Inject
    protected InvoiceGenerator generator;
    @Inject
    protected BillingInternalApi billingApi;
    @Inject
    protected AccountInternalApi accountApi;
    @Inject
    protected SubscriptionInternalApi subscriptionApi;
    @Inject
    protected BusService busService;
    @Inject
    protected TagUserApi tagUserApi;
    @Inject
    protected GlobalLocker locker;
    @Inject
    protected Clock clock;
    @Inject
    protected InternalCallContextFactory internalCallContextFactory;
    @Inject
    protected InvoiceInternalApi invoiceInternalApi;
    @Inject
    protected InvoiceDao invoiceDao;
    @Inject
    protected TestInvoiceHelper invoiceUtil;

    private void loadSystemPropertiesFromClasspath(final String resource) {
        final URL url = InvoiceTestSuiteNoDB.class.getResource(resource);
        Assert.assertNotNull(url);

        configSource.merge(url);
    }

    @BeforeClass(groups = "fast")
    protected void beforeClass() throws Exception {
        loadSystemPropertiesFromClasspath("/resource.properties");

        final Injector injector = Guice.createInjector(new TestInvoiceModuleNoDB(configSource));
        injector.injectMembers(this);
    }

    @BeforeMethod(groups = "fast")
    public void beforeMethod() {
        bus.start();
    }

    @AfterMethod(groups = "fast")
    public void afterMethod() {
        bus.stop();
    }
}
