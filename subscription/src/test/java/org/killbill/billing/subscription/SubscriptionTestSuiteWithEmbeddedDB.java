/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.subscription;

import javax.inject.Inject;

import org.killbill.billing.GuicyKillbillTestSuiteWithEmbeddedDB;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.api.TestApiListener;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.CatalogService;
import org.killbill.billing.lifecycle.api.BusService;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.subscription.api.SubscriptionBaseService;
import org.killbill.billing.subscription.api.timeline.SubscriptionBaseTimelineApi;
import org.killbill.billing.subscription.api.transfer.SubscriptionBaseTransferApi;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;
import org.killbill.billing.subscription.api.user.TestSubscriptionHelper;
import org.killbill.billing.subscription.engine.addon.AddonUtils;
import org.killbill.billing.subscription.engine.dao.SubscriptionDao;
import org.killbill.billing.subscription.glue.TestDefaultSubscriptionModuleWithEmbeddedDB;
import org.killbill.billing.util.config.definition.SubscriptionConfig;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.bus.api.PersistentBus;
import org.killbill.clock.ClockMock;
import org.killbill.notificationq.api.NotificationQueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;

public class SubscriptionTestSuiteWithEmbeddedDB extends GuicyKillbillTestSuiteWithEmbeddedDB {

    protected static final Logger log = LoggerFactory.getLogger(SubscriptionTestSuiteWithEmbeddedDB.class);

    public static final Long DELAY = 10000L;

    @Inject
    protected AccountUserApi accountUserApi;
    @Inject
    protected SubscriptionBaseService subscriptionBaseService;
    @Inject
    protected SubscriptionBaseInternalApi subscriptionInternalApi;
    @Inject
    protected SubscriptionBaseTransferApi transferApi;
    @Inject
    protected PersistentBus bus;
    @Inject
    protected SubscriptionBaseTimelineApi repairApi;
    @Inject
    protected NotificationQueueService notificationQueueService;
    @Inject
    protected CatalogService catalogService;
    @Inject
    protected SubscriptionConfig config;
    @Inject
    protected SubscriptionDao dao;
    @Inject
    protected BusService busService;
    @Inject
    protected AddonUtils addonUtils;
    @Inject
    protected TestSubscriptionHelper testUtil;
    @Inject
    protected TestApiListener testListener;
    @Inject
    protected SubscriptionTestInitializer subscriptionTestInitializer;

    @Inject
    protected NonEntityDao nonEntityDao;

    protected Catalog catalog;
    protected AccountData accountData;
    protected SubscriptionBaseBundle bundle;

    @Override
    protected KillbillConfigSource getConfigSource() {
        return getConfigSource("/subscription.properties");
    }

    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {
        if (hasFailed()) {
            return;
        }

        final Injector g = Guice.createInjector(Stage.PRODUCTION, new TestDefaultSubscriptionModuleWithEmbeddedDB(configSource));
        g.injectMembers(this);
    }

    @Override
    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();
        subscriptionTestInitializer.startTestFramework(testListener, clock, busService, subscriptionBaseService);

        this.catalog = subscriptionTestInitializer.initCatalog(catalogService, internalCallContext);
        this.accountData = subscriptionTestInitializer.initAccountData(clock);
        final Account account = createAccount(accountData);
        final SubscriptionBaseBundle model = subscriptionTestInitializer.initBundle(account.getId(), subscriptionInternalApi, clock, internalCallContext);
        this.bundle = subscriptionInternalApi.createBundleForAccount(model.getAccountId(), model.getExternalKey(), false, internalCallContext);
    }

    @AfterMethod(groups = "slow")
    public void afterMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        subscriptionTestInitializer.stopTestFramework(testListener, busService, subscriptionBaseService);
    }

    protected Account createAccount(final AccountData accountData) throws AccountApiException {
        final Account account = accountUserApi.createAccount(accountData, callContext);

        refreshCallContext(account.getId());

        return account;
    }

    @Override
    protected void assertListenerStatus() {
        testListener.assertListenerStatus();
    }
}
