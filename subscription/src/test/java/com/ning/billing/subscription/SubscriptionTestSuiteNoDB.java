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

package com.ning.billing.subscription;

import java.net.URL;

import javax.inject.Inject;

import org.mockito.Mockito;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import com.ning.billing.GuicyKillbillTestSuiteNoDB;
import com.ning.billing.account.api.AccountData;
import com.ning.billing.api.TestApiListener;
import com.ning.billing.api.TestListenerStatus;
import com.ning.billing.catalog.api.Catalog;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.clock.ClockMock;
import com.ning.billing.subscription.api.SubscriptionService;
import com.ning.billing.subscription.api.migration.SubscriptionMigrationApi;
import com.ning.billing.subscription.api.user.TestSubscriptionHelper;
import com.ning.billing.subscription.engine.dao.MockSubscriptionDaoMemory;
import com.ning.billing.subscription.engine.dao.SubscriptionDao;
import com.ning.billing.subscription.glue.TestDefaultSubscriptionModuleNoDB;
import com.ning.billing.subscription.api.timeline.SubscriptionTimelineApi;
import com.ning.billing.subscription.api.transfer.SubscriptionTransferApi;
import com.ning.billing.subscription.api.user.SubscriptionBundle;
import com.ning.billing.subscription.api.user.SubscriptionUserApi;
import com.ning.billing.util.config.SubscriptionConfig;
import com.ning.billing.util.svcapi.subscription.SubscriptionInternalApi;
import com.ning.billing.util.svcsapi.bus.BusService;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;

public class SubscriptionTestSuiteNoDB extends GuicyKillbillTestSuiteNoDB {

    protected static final Logger log = LoggerFactory.getLogger(SubscriptionTestSuiteNoDB.class);

    @Inject
    protected SubscriptionService subscriptionService;
    @Inject
    protected SubscriptionInternalApi subscriptionInternalApi;
    @Inject
    protected SubscriptionTransferApi transferApi;

    @Inject
    protected SubscriptionMigrationApi migrationApi;
    @Inject
    protected SubscriptionTimelineApi repairApi;

    @Inject
    protected CatalogService catalogService;
    @Inject
    protected SubscriptionConfig config;
    @Inject
    protected SubscriptionDao dao;
    @Inject
    protected ClockMock clock;
    @Inject
    protected BusService busService;

    @Inject
    protected IDBI idbi;

    @Inject
    protected TestSubscriptionHelper testUtil;
    @Inject
    protected TestApiListener testListener;
    @Inject
    protected TestListenerStatus testListenerStatus;

    @Inject
    protected SubscriptionTestInitializer subscriptionTestInitializer;

    protected Catalog catalog;
    protected AccountData accountData;
    protected SubscriptionBundle bundle;

    private void loadSystemPropertiesFromClasspath(final String resource) {
        final URL url = DefaultSubscriptionTestInitializer.class.getResource(resource);
        Assert.assertNotNull(url);

        configSource.merge(url);
    }

    @BeforeClass(groups = "fast")
    public void beforeClass() throws Exception {
        loadSystemPropertiesFromClasspath("/subscription.properties");

        final Injector g = Guice.createInjector(Stage.PRODUCTION, new TestDefaultSubscriptionModuleNoDB(configSource));
        g.injectMembers(this);

        // For TestApiListener#isCompleted
        Mockito.doReturn(0L).when(idbi).withHandle(Mockito.<HandleCallback<Long>>any());
    }

    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {

        // CLEANUP ALL DB TABLES OR IN MEMORY STRUCTURES
        ((MockSubscriptionDaoMemory) dao).reset();

        subscriptionTestInitializer.startTestFamework(testListener, testListenerStatus, clock, busService, subscriptionService);

        this.catalog = subscriptionTestInitializer.initCatalog(catalogService);
        this.accountData = subscriptionTestInitializer.initAccountData();
        this.bundle = subscriptionTestInitializer.initBundle(subscriptionInternalApi, internalCallContext);
    }

    @AfterMethod(groups = "fast")
    public void afterMethod() throws Exception {
        subscriptionTestInitializer.stopTestFramework(testListener, busService, subscriptionService);
    }

    protected void assertListenerStatus() {
        ((SubscriptionTestListenerStatus) testListenerStatus).assertListenerStatus();
    }
}
