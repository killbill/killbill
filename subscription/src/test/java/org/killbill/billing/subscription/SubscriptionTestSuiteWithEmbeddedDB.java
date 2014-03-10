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

package org.killbill.billing.subscription;

import java.net.URL;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import org.killbill.billing.GuicyKillbillTestSuiteWithEmbeddedDB;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.CatalogService;
import org.killbill.clock.ClockMock;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.subscription.api.SubscriptionBaseService;
import org.killbill.billing.subscription.api.migration.SubscriptionBaseMigrationApi;
import org.killbill.billing.subscription.api.timeline.SubscriptionBaseTimelineApi;
import org.killbill.billing.subscription.api.transfer.SubscriptionBaseTransferApi;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;
import org.killbill.billing.subscription.api.user.TestSubscriptionHelper;
import org.killbill.billing.subscription.engine.dao.SubscriptionDao;
import org.killbill.billing.subscription.glue.TestDefaultSubscriptionModuleWithEmbeddedDB;
import org.killbill.billing.util.config.SubscriptionConfig;
import org.killbill.billing.util.svcsapi.bus.BusService;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;

public class SubscriptionTestSuiteWithEmbeddedDB extends GuicyKillbillTestSuiteWithEmbeddedDB {

    protected static final Logger log = LoggerFactory.getLogger(SubscriptionTestSuiteWithEmbeddedDB.class);

    public static final Long DELAY = 10000L;

    @Inject
    protected SubscriptionBaseService subscriptionBaseService;
    @Inject
    protected SubscriptionBaseInternalApi subscriptionInternalApi;
    @Inject
    protected SubscriptionBaseTransferApi transferApi;

    @Inject
    protected SubscriptionBaseMigrationApi migrationApi;
    @Inject
    protected SubscriptionBaseTimelineApi repairApi;

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
    protected TestSubscriptionHelper testUtil;
    @Inject
    protected TestApiListener testListener;
    @Inject
    protected SubscriptionTestInitializer subscriptionTestInitializer;

    protected Catalog catalog;
    protected AccountData accountData;
    protected SubscriptionBaseBundle bundle;

    private void loadSystemPropertiesFromClasspath(final String resource) {
        final URL url = DefaultSubscriptionTestInitializer.class.getResource(resource);
        Assert.assertNotNull(url);

        configSource.merge(url);
    }

    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {
        loadSystemPropertiesFromClasspath("/subscription.properties");

        final Injector g = Guice.createInjector(Stage.PRODUCTION, new TestDefaultSubscriptionModuleWithEmbeddedDB(configSource));
        g.injectMembers(this);
    }

    @Override
    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        subscriptionTestInitializer.startTestFamework(testListener, clock, busService, subscriptionBaseService);

        this.catalog = subscriptionTestInitializer.initCatalog(catalogService);
        this.accountData = subscriptionTestInitializer.initAccountData();
        this.bundle = subscriptionTestInitializer.initBundle(subscriptionInternalApi, internalCallContext);

        // Make sure we start with a clean state
        assertListenerStatus();
    }

    @AfterMethod(groups = "slow")
    public void afterMethod() throws Exception {
        // Make sure we finish in a clean state
        assertListenerStatus();

        subscriptionTestInitializer.stopTestFramework(testListener, busService, subscriptionBaseService);
    }

    protected void assertListenerStatus() {
        testListener.assertListenerStatus();
    }
}
