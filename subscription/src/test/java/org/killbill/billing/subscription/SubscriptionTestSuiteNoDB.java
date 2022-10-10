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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;

import org.killbill.billing.GuicyKillbillTestSuiteNoDB;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.account.api.ImmutableAccountInternalApi;
import org.killbill.billing.api.TestApiListener;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.CatalogService;
import org.killbill.billing.dao.MockNonEntityDao;
import org.killbill.billing.lifecycle.api.BusService;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.subscription.api.SubscriptionBaseApiService;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.subscription.api.SubscriptionBaseService;
import org.killbill.billing.subscription.api.timeline.SubscriptionBaseTimelineApi;
import org.killbill.billing.subscription.api.transfer.SubscriptionBaseTransferApi;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;
import org.killbill.billing.subscription.api.user.TestSubscriptionHelper;
import org.killbill.billing.subscription.catalog.DefaultSubscriptionCatalogApi;
import org.killbill.billing.subscription.catalog.SubscriptionCatalog;
import org.killbill.billing.subscription.catalog.SubscriptionCatalogApi;
import org.killbill.billing.subscription.engine.dao.MockSubscriptionDaoMemory;
import org.killbill.billing.subscription.engine.dao.SubscriptionDao;
import org.killbill.billing.subscription.glue.TestDefaultSubscriptionModuleNoDB;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.config.definition.SubscriptionConfig;
import org.mockito.Mockito;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;

public class SubscriptionTestSuiteNoDB extends GuicyKillbillTestSuiteNoDB {

    protected static final Logger log = LoggerFactory.getLogger(SubscriptionTestSuiteNoDB.class);

    @Inject
    protected AccountUserApi accountUserApi;
    @Inject
    protected AccountInternalApi accountInternalApi;
    @Inject
    protected ImmutableAccountInternalApi immutableAccountInternalApi;
    @Inject
    protected SubscriptionBaseService subscriptionBaseService;
    @Inject
    protected SubscriptionBaseApiService subscriptionBaseApiService;
    @Inject
    protected SubscriptionBaseInternalApi subscriptionInternalApi;
    @Inject
    protected SubscriptionBaseTransferApi transferApi;

    @Inject
    protected SubscriptionBaseTimelineApi repairApi;

    @Inject
    protected CatalogService catalogService;

    @Inject
    protected SubscriptionCatalogApi subscriptionCatalogApi;
    @Inject
    protected SubscriptionConfig config;
    @Inject
    protected SubscriptionDao dao;

    @Inject
    protected BusService busService;

    @Inject
    protected IDBI idbi;

    @Inject
    protected TestSubscriptionHelper testUtil;
    @Inject
    protected TestApiListener testListener;

    @Inject
    protected SubscriptionTestInitializer subscriptionTestInitializer;

    @Inject
    protected CacheControllerDispatcher cacheControllerDispatcher;

    @Inject
    protected MockNonEntityDao mockNonEntityDao;

    @Inject
    protected InternalCallContextFactory internalCallContextFactory;

    protected SubscriptionCatalog catalog;
    protected AccountData accountData;
    protected SubscriptionBaseBundle bundle;

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.put("org.killbill.catalog.uri", "org/killbill/billing/catalog/catalogTest.xml");
        return getConfigSource(null, allExtraProperties);    }

    @BeforeClass(groups = "fast")
    public void beforeClass() throws Exception {
        if (hasFailed()) {
            return;
        }

        final Injector g = Guice.createInjector(Stage.PRODUCTION, new TestDefaultSubscriptionModuleNoDB(configSource, clock));
        g.injectMembers(this);

        // For TestApiListener#isCompleted
        Mockito.doReturn(0L).when(idbi).withHandle(Mockito.<HandleCallback<Long>>any());
    }

    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        // CLEANUP ALL DB TABLES OR IN MEMORY STRUCTURES
        ((MockSubscriptionDaoMemory) dao).reset();

        subscriptionTestInitializer.startTestFramework(testListener, clock, busService, subscriptionBaseService);

        this.catalog = DefaultSubscriptionCatalogApi.wrapCatalog(subscriptionTestInitializer.initCatalog(catalogService, internalCallContext), clock);
        this.accountData = subscriptionTestInitializer.initAccountData(clock);

        Mockito.when(accountInternalApi.getBCD(Mockito.any())). thenReturn(1);
        final Account account = GuicyKillbillTestSuiteNoDB.createMockAccount(accountData,
                                                                             accountUserApi,
                                                                             accountInternalApi,
                                                                             immutableAccountInternalApi,
                                                                             mockNonEntityDao,
                                                                             clock,
                                                                             internalCallContextFactory,
                                                                             callContext,
                                                                             internalCallContext);
        final UUID accountId = account.getId();
        mockNonEntityDao.addAccountRecordIdMapping(accountId, internalCallContext);
        mockNonEntityDao.addTenantRecordIdMapping(accountId, internalCallContext);
        mockNonEntityDao.addAccountIdMapping(internalCallContext.getAccountRecordId(), accountId);

        final ImmutableAccountData immutableAccountData = Mockito.mock(ImmutableAccountData.class);
        Mockito.when(immutableAccountInternalApi.getImmutableAccountDataByRecordId(Mockito.<Long>eq(internalCallContext.getAccountRecordId()), Mockito.<InternalTenantContext>any())).thenReturn(immutableAccountData);

        this.bundle = subscriptionTestInitializer.initBundle(accountId, subscriptionInternalApi, clock, internalCallContext);
        mockNonEntityDao.addTenantRecordIdMapping(bundle.getId(), internalCallContext);
        mockNonEntityDao.addAccountRecordIdMapping(bundle.getId(), internalCallContext);
    }

    @AfterMethod(groups = "fast")
    public void afterMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        subscriptionTestInitializer.stopTestFramework(testListener, busService, subscriptionBaseService);
    }

    @Override
    protected void assertListenerStatus() {
        testListener.assertListenerStatus();
    }
}
