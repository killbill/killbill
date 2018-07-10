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

package org.killbill.billing.invoice;

import org.killbill.billing.GuicyKillbillTestSuiteNoDB;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.currency.api.CurrencyConversionApi;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.invoice.api.InvoiceUserApi;
import org.killbill.billing.invoice.api.formatters.ResourceBundleFactory;
import org.killbill.billing.invoice.dao.InvoiceDao;
import org.killbill.billing.invoice.dao.InvoiceDaoHelper;
import org.killbill.billing.invoice.generator.FixedAndRecurringInvoiceItemGenerator;
import org.killbill.billing.invoice.generator.InvoiceGenerator;
import org.killbill.billing.invoice.glue.TestInvoiceModuleNoDB;
import org.killbill.billing.invoice.usage.RawUsageOptimizer;
import org.killbill.billing.junction.BillingInternalApi;
import org.killbill.billing.lifecycle.api.BusService;
import org.killbill.billing.payment.api.InvoicePaymentApi;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.usage.api.UsageUserApi;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.config.definition.InvoiceConfig;
import org.killbill.billing.util.config.definition.InvoiceConfig.UsageDetailMode;
import org.killbill.bus.api.PersistentBus;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLocker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

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
    protected InvoiceGenerator generator;
    @Inject
    protected InvoiceConfig invoiceConfig;
    @Inject
    protected BillingInternalApi billingApi;
    @Inject
    protected AccountInternalApi accountApi;
    @Inject
    protected SubscriptionBaseInternalApi subscriptionApi;
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
    @Inject
    protected CurrencyConversionApi currencyConversionApi;
    @Inject
    protected UsageUserApi usageUserApi;
    @Inject
    protected ResourceBundleFactory resourceBundleFactory;
    @Inject
    protected RawUsageOptimizer rawUsageOptimizer;
    @Inject
    protected InvoiceDaoHelper invoiceDaoHelper;
    @Inject
    protected FixedAndRecurringInvoiceItemGenerator fixedAndRecurringInvoiceItemGenerator;
    @Override
    protected KillbillConfigSource getConfigSource() {
        return getConfigSource("/resource.properties");
    }
    protected UsageDetailMode usageDetailMode;

    @BeforeClass(groups = "fast")
    protected void beforeClass() throws Exception {
        if (hasFailed()) {
            return;
        }

        final Injector injector = Guice.createInjector(new TestInvoiceModuleNoDB(configSource));
        injector.injectMembers(this);
    }

    @BeforeMethod(groups = "fast")
    public void beforeMethod() {
        if (hasFailed()) {
            return;
        }

        bus.start();
    }

    @AfterMethod(groups = "fast")
    public void afterMethod() {
        if (hasFailed()) {
            return;
        }

        bus.stop();
    }
}
