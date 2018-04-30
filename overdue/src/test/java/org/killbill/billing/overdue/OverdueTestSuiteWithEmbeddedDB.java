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

package org.killbill.billing.overdue;

import javax.inject.Named;

import org.killbill.billing.GuicyKillbillTestSuiteWithEmbeddedDB;
import org.killbill.billing.account.api.ImmutableAccountInternalApi;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.junction.BlockingInternalApi;
import org.killbill.billing.lifecycle.api.BusService;
import org.killbill.billing.overdue.api.OverdueApi;
import org.killbill.billing.overdue.applicator.OverdueBusListenerTester;
import org.killbill.billing.overdue.applicator.OverdueStateApplicator;
import org.killbill.billing.overdue.caching.OverdueConfigCache;
import org.killbill.billing.overdue.calculator.BillingStateCalculator;
import org.killbill.billing.overdue.glue.DefaultOverdueModule;
import org.killbill.billing.overdue.glue.TestOverdueModuleWithEmbeddedDB;
import org.killbill.billing.overdue.notification.OverdueNotifier;
import org.killbill.billing.overdue.notification.OverduePoster;
import org.killbill.billing.overdue.service.DefaultOverdueService;
import org.killbill.billing.overdue.wrapper.OverdueWrapperFactory;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.bus.api.PersistentBus;
import org.killbill.notificationq.api.NotificationQueueService;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;

public abstract class OverdueTestSuiteWithEmbeddedDB extends GuicyKillbillTestSuiteWithEmbeddedDB {

    @Inject
    protected ImmutableAccountInternalApi accountApi;
    @Inject
    protected BillingStateCalculator calculatorBundle;
    @Inject
    protected BlockingInternalApi blockingApi;
    @Inject
    protected BusService busService;
    @Inject
    protected DefaultOverdueService service;
    @Inject
    protected CacheControllerDispatcher cacheControllerDispatcher;
    @Inject
    protected PersistentBus bus;
    @Inject
    protected InternalCallContextFactory internalCallContextFactory;
    @Inject
    protected InvoiceInternalApi invoiceApi;
    @Inject
    protected NotificationQueueService notificationQueueService;
    @Inject
    protected OverdueBusListenerTester listener;
    @Named(DefaultOverdueModule.OVERDUE_NOTIFIER_CHECK_NAMED)
    @Inject
    protected OverdueNotifier checkNotifier;
    @Named(DefaultOverdueModule.OVERDUE_NOTIFIER_ASYNC_BUS_NAMED)
    @Inject
    protected OverdueNotifier asyncNotifier;
    @Inject
    @Named(DefaultOverdueModule.OVERDUE_NOTIFIER_CHECK_NAMED)
    protected OverduePoster checkPoster;
    @Inject
    @Named(DefaultOverdueModule.OVERDUE_NOTIFIER_ASYNC_BUS_NAMED)
    protected OverduePoster asyncPoster;
    @Inject
    protected OverdueStateApplicator applicator;
    @Inject
    protected OverdueApi overdueApi;
    @Inject
    protected OverdueProperties overdueProperties;
    @Inject
    protected OverdueWrapperFactory overdueWrapperFactory;
    @Inject
    protected NonEntityDao nonEntityDao;
    @Inject
    protected TestOverdueHelper testOverdueHelper;
    @Inject
    protected OverdueConfigCache overdueConfigCache;

    @BeforeClass(groups = "slow")
    protected void beforeClass() throws Exception {
        if (hasFailed()) {
            return;
        }

        final Injector injector = Guice.createInjector(new TestOverdueModuleWithEmbeddedDB(configSource));
        injector.injectMembers(this);
    }

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();
        cacheControllerDispatcher.clearAll();
        bus.start();
        bus.register(listener);
        service.loadConfig();
        service.initialize();
        service.start();
    }

    @AfterMethod(groups = "slow")
    public void afterMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        service.stop();
        bus.unregister(listener);
        bus.stop();
    }
}
