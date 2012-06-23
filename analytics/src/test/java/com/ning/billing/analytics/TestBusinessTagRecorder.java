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

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.account.api.user.DefaultAccountUserApi;
import com.ning.billing.account.dao.AccountDao;
import com.ning.billing.account.dao.AccountEmailDao;
import com.ning.billing.account.dao.AuditedAccountDao;
import com.ning.billing.account.dao.AuditedAccountEmailDao;
import com.ning.billing.analytics.dao.BusinessAccountTagSqlDao;
import com.ning.billing.analytics.dao.BusinessInvoicePaymentTagSqlDao;
import com.ning.billing.analytics.dao.BusinessInvoiceTagSqlDao;
import com.ning.billing.analytics.dao.BusinessSubscriptionTransitionTagSqlDao;
import com.ning.billing.catalog.DefaultCatalogService;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.io.VersionedCatalogLoader;
import com.ning.billing.config.CatalogConfig;
import com.ning.billing.entitlement.alignment.PlanAligner;
import com.ning.billing.entitlement.api.user.DefaultEntitlementUserApi;
import com.ning.billing.entitlement.api.user.DefaultSubscriptionApiService;
import com.ning.billing.entitlement.api.user.DefaultSubscriptionFactory;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.engine.addon.AddonUtils;
import com.ning.billing.entitlement.engine.dao.AuditedEntitlementDao;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.util.bus.InMemoryBus;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.DefaultCallContextFactory;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.clock.DefaultClock;
import com.ning.billing.util.dao.ObjectType;
import com.ning.billing.util.notificationq.DefaultNotificationQueueService;

public class TestBusinessTagRecorder extends TestWithEmbeddedDB {
    private BusinessAccountTagSqlDao accountTagSqlDao;
    private BusinessSubscriptionTransitionTagSqlDao subscriptionTransitionTagSqlDao;
    private InMemoryBus eventBus;
    private DefaultCallContextFactory callContextFactory;
    private AccountUserApi accountUserApi;
    private EntitlementUserApi entitlementUserApi;
    private BusinessTagRecorder tagRecorder;

    @BeforeMethod(groups = "slow")
    public void setUp() throws Exception {
        final IDBI dbi = helper.getDBI();
        accountTagSqlDao = dbi.onDemand(BusinessAccountTagSqlDao.class);
        final BusinessInvoiceTagSqlDao invoiceTagSqlDao = dbi.onDemand(BusinessInvoiceTagSqlDao.class);
        final BusinessInvoicePaymentTagSqlDao invoicePaymentTagSqlDao = dbi.onDemand(BusinessInvoicePaymentTagSqlDao.class);
        subscriptionTransitionTagSqlDao = dbi.onDemand(BusinessSubscriptionTransitionTagSqlDao.class);
        eventBus = new InMemoryBus();
        final AccountDao accountDao = new AuditedAccountDao(dbi, eventBus);
        final AccountEmailDao accountEmailDao = new AuditedAccountEmailDao(dbi);
        final DefaultClock clock = new DefaultClock();
        callContextFactory = new DefaultCallContextFactory(clock);
        accountUserApi = new DefaultAccountUserApi(callContextFactory, accountDao, accountEmailDao);
        final CatalogService catalogService = new DefaultCatalogService(Mockito.mock(CatalogConfig.class), Mockito.mock(VersionedCatalogLoader.class));
        final AddonUtils addonUtils = new AddonUtils(catalogService);
        final DefaultNotificationQueueService notificationQueueService = new DefaultNotificationQueueService(dbi, clock);
        final EntitlementDao entitlementDao = new AuditedEntitlementDao(dbi, clock, addonUtils, notificationQueueService, eventBus);
        final PlanAligner planAligner = new PlanAligner(catalogService);
        final DefaultSubscriptionApiService apiService = new DefaultSubscriptionApiService(clock, entitlementDao, catalogService, planAligner);
        final DefaultSubscriptionFactory subscriptionFactory = new DefaultSubscriptionFactory(apiService, clock, catalogService);
        entitlementUserApi = new DefaultEntitlementUserApi(clock, entitlementDao, catalogService,
                                                           apiService, subscriptionFactory, addonUtils);
        tagRecorder = new BusinessTagRecorder(accountTagSqlDao, invoicePaymentTagSqlDao, invoiceTagSqlDao, subscriptionTransitionTagSqlDao,
                                              accountUserApi, entitlementUserApi);

        eventBus.start();
    }

    @AfterMethod(groups = "slow")
    public void tearDown() throws Exception {
        eventBus.stop();
    }

    @Test(groups = "slow")
    public void testAddAndRemoveTagsForAccount() throws Exception {
        final String name = UUID.randomUUID().toString().substring(0, 20);
        final CallContext callContext = callContextFactory.createCallContext(UUID.randomUUID().toString(), CallOrigin.TEST, UserType.TEST);
        final String accountKey = UUID.randomUUID().toString();

        final Account account = accountUserApi.createAccount(new MockAccount(UUID.randomUUID(), accountKey, Currency.MXN), callContext);
        final UUID accountId = account.getId();

        Assert.assertEquals(accountTagSqlDao.getTagsForAccount(accountKey).size(), 0);
        tagRecorder.tagAdded(ObjectType.ACCOUNT, accountId, name);
        Assert.assertEquals(accountTagSqlDao.getTagsForAccount(accountKey).size(), 1);
        tagRecorder.tagRemoved(ObjectType.ACCOUNT, accountId, name);
        Assert.assertEquals(accountTagSqlDao.getTagsForAccount(accountKey).size(), 0);
    }

    @Test(groups = "slow")
    public void testAddAndRemoveTagsForBundle() throws Exception {
        final String name = UUID.randomUUID().toString().substring(0, 20);
        final CallContext callContext = callContextFactory.createCallContext(UUID.randomUUID().toString(), CallOrigin.TEST, UserType.TEST);
        final String externalKey = UUID.randomUUID().toString();

        final Account account = accountUserApi.createAccount(new MockAccount(UUID.randomUUID(), UUID.randomUUID().toString(), Currency.MXN), callContext);
        final SubscriptionBundle bundle = entitlementUserApi.createBundleForAccount(account.getId(), externalKey, callContext);
        final UUID bundleId = bundle.getId();

        Assert.assertEquals(subscriptionTransitionTagSqlDao.getTagsForBusinessSubscriptionTransition(externalKey).size(), 0);
        tagRecorder.tagAdded(ObjectType.BUNDLE, bundleId, name);
        Assert.assertEquals(subscriptionTransitionTagSqlDao.getTagsForBusinessSubscriptionTransition(externalKey).size(), 1);
        tagRecorder.tagRemoved(ObjectType.BUNDLE, bundleId, name);
        Assert.assertEquals(subscriptionTransitionTagSqlDao.getTagsForBusinessSubscriptionTransition(externalKey).size(), 0);
    }
}
