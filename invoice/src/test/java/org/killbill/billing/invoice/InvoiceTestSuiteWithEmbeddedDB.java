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

import org.killbill.billing.GuicyKillbillTestSuiteWithEmbeddedDB;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.DefaultInvoiceService;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.invoice.api.InvoiceService;
import org.killbill.billing.invoice.api.InvoiceUserApi;
import org.killbill.billing.invoice.dao.InvoiceDao;
import org.killbill.billing.invoice.generator.InvoiceGenerator;
import org.killbill.billing.invoice.glue.TestInvoiceModuleWithEmbeddedDb;
import org.killbill.billing.invoice.notification.NextBillingDateNotifier;
import org.killbill.billing.junction.BillingInternalApi;
import org.killbill.billing.lifecycle.api.BusService;
import org.killbill.billing.payment.api.InvoicePaymentApi;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.config.definition.InvoiceConfig;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.bus.api.PersistentBus;
import org.killbill.clock.Clock;
import org.killbill.clock.ClockMock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.notificationq.api.NotificationQueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;

public abstract class InvoiceTestSuiteWithEmbeddedDB extends GuicyKillbillTestSuiteWithEmbeddedDB {

    private static final Logger log = LoggerFactory.getLogger(InvoiceTestSuiteWithEmbeddedDB.class);

    protected static final Currency accountCurrency = Currency.USD;

    @Inject
    protected InvoiceService invoiceService;
    @Inject
    protected PersistentBus bus;
    @Inject
    protected CacheControllerDispatcher controllerDispatcher;
    @Inject
    protected InvoiceUserApi invoiceUserApi;
    @Inject
    protected InvoiceGenerator generator;
    @Inject
    protected BillingInternalApi billingApi;
    @Inject
    protected AccountUserApi accountUserApi;
    @Inject
    protected AccountInternalApi accountApi;
    @Inject
    protected SubscriptionBaseInternalApi subscriptionApi;
    @Inject
    protected BusService busService;
    @Inject
    protected InvoiceDao invoiceDao;
    @Inject
    protected NonEntityDao nonEntityDao;
    @Inject
    protected TagUserApi tagUserApi;
    @Inject
    protected GlobalLocker locker;
    @Inject
    protected InternalCallContextFactory internalCallContextFactory;
    @Inject
    protected InvoiceInternalApi invoiceInternalApi;
    @Inject
    protected NextBillingDateNotifier nextBillingDateNotifier;
    @Inject
    protected NotificationQueueService notificationQueueService;
    @Inject
    protected TestInvoiceHelper invoiceUtil;
    @Inject
    protected TestInvoiceNotificationQListener testInvoiceNotificationQListener;
    @Inject
    protected InvoicePluginDispatcher invoicePluginDispatcher;
    @Inject
    protected InvoiceConfig invoiceConfig;
    @Inject
    protected ParkedAccountsManager parkedAccountsManager;

    @Override
    protected KillbillConfigSource getConfigSource() {
        return getConfigSource("/resource.properties");
    }

    @BeforeClass(groups = "slow")
    protected void beforeClass() throws Exception {
        if (hasFailed()) {
            return;
        }

        final Injector injector = Guice.createInjector(new TestInvoiceModuleWithEmbeddedDb(configSource));
        injector.injectMembers(this);
    }

    @Override
    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();
        controllerDispatcher.clearAll();
        bus.start();
        restartInvoiceService(invoiceService);
        clock.resetDeltaFromReality();
    }

    private void restartInvoiceService(final InvoiceService invoiceService) throws Exception {
        ((DefaultInvoiceService) invoiceService).initialize();
        ((DefaultInvoiceService) invoiceService).start();
    }

    private void stopInvoiceService(final InvoiceService invoiceService) throws Exception {
        ((DefaultInvoiceService) invoiceService).stop();
    }

    @AfterMethod(groups = "slow")
    public void afterMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        bus.stop();
        stopInvoiceService(invoiceService);
    }
}
