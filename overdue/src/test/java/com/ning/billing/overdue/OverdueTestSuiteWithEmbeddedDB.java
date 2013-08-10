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

package com.ning.billing.overdue;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import com.ning.billing.GuicyKillbillTestSuiteWithEmbeddedDB;
import com.ning.billing.bus.api.PersistentBus;
import com.ning.billing.overdue.calculator.BillingStateCalculator;
import com.ning.billing.subscription.api.user.SubscriptionBaseBundle;
import com.ning.billing.notificationq.api.NotificationQueueService;
import com.ning.billing.ovedue.notification.OverdueCheckNotifier;
import com.ning.billing.ovedue.notification.OverdueCheckPoster;
import com.ning.billing.overdue.applicator.OverdueBusListenerTester;
import com.ning.billing.overdue.applicator.OverdueStateApplicator;
import com.ning.billing.overdue.glue.TestOverdueModuleWithEmbeddedDB;
import com.ning.billing.overdue.service.DefaultOverdueService;
import com.ning.billing.overdue.wrapper.OverdueWrapperFactory;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.dao.NonEntityDao;
import com.ning.billing.util.svcapi.account.AccountInternalApi;
import com.ning.billing.util.svcapi.subscription.SubscriptionBaseInternalApi;
import com.ning.billing.util.svcapi.invoice.InvoiceInternalApi;
import com.ning.billing.util.svcapi.junction.BlockingInternalApi;
import com.ning.billing.util.svcsapi.bus.BusService;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;

public abstract class OverdueTestSuiteWithEmbeddedDB extends GuicyKillbillTestSuiteWithEmbeddedDB {

    @Inject
    protected AccountInternalApi accountApi;
    @Inject
    protected BillingStateCalculator calculatorBundle;
    @Inject
    protected BlockingInternalApi blockingApi;
    @Inject
    protected BusService busService;
    @Inject
    protected DefaultOverdueService service;
    @Inject
    protected SubscriptionBaseInternalApi subscriptionApi;
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
    @Inject
    protected OverdueCheckNotifier notifier;
    @Inject
    protected OverdueCheckPoster poster;
    @Inject
    protected OverdueStateApplicator<SubscriptionBaseBundle> applicator;
    @Inject
    protected OverdueUserApi overdueApi;
    @Inject
    protected OverdueProperties overdueProperties;
    @Inject
    protected OverdueWrapperFactory overdueWrapperFactory;
    @Inject
    protected NonEntityDao nonEntityDao;
    @Inject
    protected TestOverdueHelper testOverdueHelper;

    @BeforeClass(groups = "slow")
    protected void beforeClass() throws Exception {
        final Injector injector = Guice.createInjector(new TestOverdueModuleWithEmbeddedDB(configSource));
        injector.injectMembers(this);
    }

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        cacheControllerDispatcher.clearAll();
        bus.start();
        bus.register(listener);
        service.initialize();
        service.start();
    }

    @AfterMethod(groups = "slow")
    public void afterMethod() throws Exception {
        service.stop();
        bus.unregister(listener);
        bus.stop();
    }
}
