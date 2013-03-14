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

import com.ning.billing.GuicyKillbillTestSuiteNoDB;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.ovedue.notification.OverdueCheckNotifier;
import com.ning.billing.ovedue.notification.OverdueCheckPoster;
import com.ning.billing.overdue.applicator.OverdueBusListenerTester;
import com.ning.billing.overdue.applicator.OverdueStateApplicator;
import com.ning.billing.overdue.calculator.BillingStateCalculatorBundle;
import com.ning.billing.overdue.glue.TestOverdueModuleNoDB;
import com.ning.billing.overdue.service.DefaultOverdueService;
import com.ning.billing.overdue.wrapper.OverdueWrapperFactory;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.notificationq.NotificationQueueService;
import com.ning.billing.util.svcapi.account.AccountInternalApi;
import com.ning.billing.util.svcapi.entitlement.EntitlementInternalApi;
import com.ning.billing.util.svcapi.invoice.InvoiceInternalApi;
import com.ning.billing.util.svcapi.junction.BlockingInternalApi;
import com.ning.billing.util.svcsapi.bus.BusService;
import com.ning.billing.util.svcsapi.bus.InternalBus;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;

public abstract class OverdueTestSuiteNoDB extends GuicyKillbillTestSuiteNoDB {

    @Inject
    protected AccountInternalApi accountApi;
    @Inject
    protected BillingStateCalculatorBundle calculatorBundle;
    @Inject
    protected BlockingInternalApi blockingApi;
    @Inject
    protected BusService busService;
    @Inject
    protected DefaultOverdueService service;
    @Inject
    protected EntitlementInternalApi entitlementApi;
    @Inject
    protected InternalBus bus;
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
    protected OverdueStateApplicator<SubscriptionBundle> applicator;
    @Inject
    protected OverdueUserApi overdueApi;
    @Inject
    protected OverdueProperties overdueProperties;
    @Inject
    protected OverdueWrapperFactory overdueWrapperFactory;
    @Inject
    protected TestOverdueHelper testOverdueHelper;

    @BeforeClass(groups = "fast")
    protected void beforeClass() throws Exception {
        final Injector injector = Guice.createInjector(new TestOverdueModuleNoDB(configSource));
        injector.injectMembers(this);
    }

    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        bus.start();

        service.registerForBus();
            service.initialize();
        service.start();
    }

    @AfterMethod(groups = "fast")
    public void afterMethod() {
        service.stop();
        bus.stop();
    }
}
