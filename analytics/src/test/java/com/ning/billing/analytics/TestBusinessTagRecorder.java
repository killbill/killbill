/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.analytics;

import java.util.UUID;

import org.mockito.Mockito;
import org.skife.jdbi.v2.IDBI;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.ObjectType;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.account.api.svcs.DefaultAccountInternalApi;
import com.ning.billing.account.api.user.DefaultAccountUserApi;
import com.ning.billing.account.dao.AccountDao;
import com.ning.billing.account.dao.DefaultAccountDao;
import com.ning.billing.analytics.dao.BusinessAccountTagSqlDao;
import com.ning.billing.analytics.dao.BusinessInvoicePaymentTagSqlDao;
import com.ning.billing.analytics.dao.BusinessInvoiceTagSqlDao;
import com.ning.billing.analytics.dao.BusinessSubscriptionTransitionTagSqlDao;
import com.ning.billing.catalog.DefaultCatalogService;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.io.VersionedCatalogLoader;
import com.ning.billing.entitlement.alignment.PlanAligner;
import com.ning.billing.entitlement.api.svcs.DefaultEntitlementInternalApi;
import com.ning.billing.entitlement.api.user.DefaultEntitlementUserApi;
import com.ning.billing.entitlement.api.user.DefaultSubscriptionApiService;
import com.ning.billing.entitlement.api.user.DefaultSubscriptionFactory;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.engine.addon.AddonUtils;
import com.ning.billing.entitlement.engine.dao.AuditedEntitlementDao;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.mock.MockAccountBuilder;
import com.ning.billing.util.bus.InMemoryInternalBus;
import com.ning.billing.util.callcontext.DefaultCallContextFactory;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.config.CatalogConfig;
import com.ning.billing.util.notificationq.DefaultNotificationQueueService;
import com.ning.billing.util.svcapi.account.AccountInternalApi;
import com.ning.billing.util.svcapi.entitlement.EntitlementInternalApi;

public class TestBusinessTagRecorder extends AnalyticsTestSuiteWithEmbeddedDB {

    private BusinessAccountTagSqlDao accountTagSqlDao;
    private BusinessSubscriptionTransitionTagSqlDao subscriptionTransitionTagSqlDao;
    private InMemoryInternalBus eventBus;
    private DefaultCallContextFactory callContextFactory;
    private AccountInternalApi accountApi;
    private AccountUserApi accountUserApi;
    private EntitlementInternalApi entitlementApi;
    private EntitlementUserApi entitlementUserApi;
    private BusinessTagDao tagDao;

    @BeforeMethod(groups = "slow")
    public void setUp() throws Exception {
        final Clock clock = new ClockMock();
        final IDBI dbi = helper.getDBI();
        accountTagSqlDao = dbi.onDemand(BusinessAccountTagSqlDao.class);
        final BusinessInvoiceTagSqlDao invoiceTagSqlDao = dbi.onDemand(BusinessInvoiceTagSqlDao.class);
        final BusinessInvoicePaymentTagSqlDao invoicePaymentTagSqlDao = dbi.onDemand(BusinessInvoicePaymentTagSqlDao.class);
        subscriptionTransitionTagSqlDao = dbi.onDemand(BusinessSubscriptionTransitionTagSqlDao.class);
        eventBus = new InMemoryInternalBus();
        final AccountDao accountDao = new DefaultAccountDao(dbi, eventBus, new InternalCallContextFactory(dbi, new ClockMock()));
        callContextFactory = new DefaultCallContextFactory(clock);
        final InternalCallContextFactory internalCallContextFactory = new InternalCallContextFactory(dbi, clock);
        accountApi = new DefaultAccountInternalApi(accountDao);
        accountUserApi = new DefaultAccountUserApi(callContextFactory, internalCallContextFactory, accountDao);
        final CatalogService catalogService = new DefaultCatalogService(Mockito.mock(CatalogConfig.class), Mockito.mock(VersionedCatalogLoader.class));
        final AddonUtils addonUtils = new AddonUtils(catalogService);
        final DefaultNotificationQueueService notificationQueueService = new DefaultNotificationQueueService(dbi, clock, internalCallContextFactory);
        final EntitlementDao entitlementDao = new AuditedEntitlementDao(dbi, clock, addonUtils, notificationQueueService, eventBus, catalogService);
        final PlanAligner planAligner = new PlanAligner(catalogService);
        final DefaultSubscriptionApiService apiService = new DefaultSubscriptionApiService(clock, entitlementDao, catalogService, planAligner, internalCallContextFactory);
        final DefaultSubscriptionFactory subscriptionFactory = new DefaultSubscriptionFactory(apiService, clock, catalogService);
        entitlementApi = new DefaultEntitlementInternalApi(entitlementDao, subscriptionFactory);
        entitlementUserApi = new DefaultEntitlementUserApi(clock, entitlementDao, catalogService, apiService, subscriptionFactory, addonUtils, internalCallContextFactory);
        tagDao = new BusinessTagDao(accountTagSqlDao, invoicePaymentTagSqlDao, invoiceTagSqlDao, subscriptionTransitionTagSqlDao,
                                    accountApi, entitlementApi);

        eventBus.start();
    }

    @AfterMethod(groups = "slow")
    public void tearDown() throws Exception {
        eventBus.stop();
    }

    @Test(groups = "slow")
    public void testAddAndRemoveTagsForAccount() throws Exception {
        final String name = UUID.randomUUID().toString().substring(0, 20);
        final String accountKey = UUID.randomUUID().toString();

        final Account accountData = new MockAccountBuilder()
                .externalKey(accountKey)
                .currency(Currency.MXN)
                .build();
        final Account account = accountUserApi.createAccount(accountData, callContext);
        final UUID accountId = account.getId();

        Assert.assertEquals(accountTagSqlDao.getTagsForAccountByKey(accountKey, internalCallContext).size(), 0);
        tagDao.tagAdded(ObjectType.ACCOUNT, accountId, name, internalCallContext);
        Assert.assertEquals(accountTagSqlDao.getTagsForAccountByKey(accountKey, internalCallContext).size(), 1);
        tagDao.tagRemoved(ObjectType.ACCOUNT, accountId, name, internalCallContext);
        Assert.assertEquals(accountTagSqlDao.getTagsForAccountByKey(accountKey, internalCallContext).size(), 0);
    }

    @Test(groups = "slow")
    public void testAddAndRemoveTagsForBundle() throws Exception {
        final String name = UUID.randomUUID().toString().substring(0, 20);
        final String externalKey = UUID.randomUUID().toString();

        final Account accountData = new MockAccountBuilder()
                .currency(Currency.MXN)
                .build();
        final Account account = accountUserApi.createAccount(accountData, callContext);
        final SubscriptionBundle bundle = entitlementUserApi.createBundleForAccount(account.getId(), externalKey, callContext);
        final UUID bundleId = bundle.getId();

        Assert.assertEquals(subscriptionTransitionTagSqlDao.getTagsForBusinessSubscriptionTransitionByKey(externalKey, internalCallContext).size(), 0);
        tagDao.tagAdded(ObjectType.BUNDLE, bundleId, name, internalCallContext);
        Assert.assertEquals(subscriptionTransitionTagSqlDao.getTagsForBusinessSubscriptionTransitionByKey(externalKey, internalCallContext).size(), 1);
        tagDao.tagRemoved(ObjectType.BUNDLE, bundleId, name, internalCallContext);
        Assert.assertEquals(subscriptionTransitionTagSqlDao.getTagsForBusinessSubscriptionTransitionByKey(externalKey, internalCallContext).size(), 0);
    }
}
