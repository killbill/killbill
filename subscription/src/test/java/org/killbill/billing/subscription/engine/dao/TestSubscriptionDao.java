/*
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

package org.killbill.billing.subscription.engine.dao;

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.DefaultPriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.subscription.SubscriptionTestSuiteWithEmbeddedDB;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseWithAddOns;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBase;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBaseBundle;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBaseWithAddOns;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;
import org.killbill.billing.subscription.api.user.SubscriptionBuilder;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent;
import org.killbill.billing.subscription.events.user.ApiEventBuilder;
import org.killbill.billing.subscription.events.user.ApiEventCreate;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.entity.dao.DBRouterUntyped;
import org.killbill.commons.profiling.Profiling.WithProfilingCallback;
import org.mockito.Mockito;
import org.skife.jdbi.v2.IDBI;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import static org.testng.Assert.assertEquals;

public class TestSubscriptionDao extends SubscriptionTestSuiteWithEmbeddedDB {

    protected UUID accountId;

    @Override
    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        // Note: this will cleanup all tables
        super.beforeMethod();

        // internal context will be configured for accountId
        final AccountData accountData = subscriptionTestInitializer.initAccountData(clock);
        final Account account = createAccount(accountData);
        accountId = account.getId();
    }

    @Override // to ignore events
    @AfterMethod(groups = "slow")
    public void afterMethod() throws Exception {
        if (hasFailed()) {
            return;
        }
        subscriptionTestInitializer.stopTestFramework(testListener, busService, subscriptionBaseService);
    }

    @Test(groups = "slow")
    public void testBundleExternalKeyReused() throws Exception {

        final String externalKey = "12345";
        final DateTime startDate = clock.getUTCNow();
        final DateTime createdDate = startDate.plusSeconds(10);

        final DefaultSubscriptionBaseBundle bundleDef = new DefaultSubscriptionBaseBundle(externalKey, accountId, startDate, startDate, createdDate, createdDate);
        final SubscriptionBaseBundle bundle = dao.createSubscriptionBundle(bundleDef, catalog, true, internalCallContext);

        final List<SubscriptionBaseBundle> result = dao.getSubscriptionBundlesForKey(externalKey, internalCallContext);
        assertEquals(result.size(), 1);
        assertEquals(result.get(0).getExternalKey(), bundle.getExternalKey());

        // Operation succeeds but nothing new got created because bundle is empty
        dao.createSubscriptionBundle(bundleDef, catalog, true, internalCallContext);
        final List<SubscriptionBaseBundle> result2 = dao.getSubscriptionBundlesForKey(externalKey, internalCallContext);
        assertEquals(result2.size(), 1);

        // Create a subscription and this time operation should fail
        final SubscriptionBuilder builder = new SubscriptionBuilder()
                .setId(UUIDs.randomUUID())
                .setBundleId(bundle.getId())
                .setBundleExternalKey(bundle.getExternalKey())
                .setCategory(ProductCategory.BASE)
                .setBundleStartDate(startDate)
                .setAlignStartDate(startDate)
                .setMigrated(false);

        final ApiEventBuilder createBuilder = new ApiEventBuilder()
                .setSubscriptionId(builder.getId())
                .setEventPlan("shotgun-monthly")
                .setEventPlanPhase("shotgun-monthly-trial")
                .setEventPriceList(DefaultPriceListSet.DEFAULT_PRICELIST_NAME)
                .setEffectiveDate(startDate)
                .setFromDisk(true);
        final SubscriptionBaseEvent creationEvent = new ApiEventCreate(createBuilder);

        final DefaultSubscriptionBase subscription = new DefaultSubscriptionBase(builder);
        testListener.pushExpectedEvents(NextEvent.CREATE);
        final SubscriptionBaseWithAddOns subscriptionBaseWithAddOns = new DefaultSubscriptionBaseWithAddOns(bundle,
                                                                                                            ImmutableList.<SubscriptionBase>of(subscription));
        dao.createSubscriptionsWithAddOns(ImmutableList.<SubscriptionBaseWithAddOns>of(subscriptionBaseWithAddOns),
                                          ImmutableMap.<UUID, List<SubscriptionBaseEvent>>of(subscription.getId(), ImmutableList.<SubscriptionBaseEvent>of(creationEvent)),
                                          catalog,
                                          internalCallContext);
        assertListenerStatus();

        // Operation Should now fail
        try {
            dao.createSubscriptionBundle(bundleDef, catalog, true, internalCallContext);
            Assert.fail("Should fail to create new subscription bundle with existing key");
        } catch (SubscriptionBaseApiException e) {
            assertEquals(ErrorCode.SUB_CREATE_ACTIVE_BUNDLE_KEY_EXISTS.getCode(), e.getCode());
        }
    }

    @Test(groups = "slow")
    public void testBundleExternalKeyTransferred() throws Exception {

        final String externalKey = "2534125sdfsd";
        final DateTime startDate = clock.getUTCNow();
        final DateTime createdDate = startDate.plusSeconds(10);

        final DefaultSubscriptionBaseBundle bundleDef = new DefaultSubscriptionBaseBundle(externalKey, accountId, startDate, startDate, createdDate, createdDate);
        final SubscriptionBaseBundle bundle = dao.createSubscriptionBundle(bundleDef, catalog, true, internalCallContext);

        final List<SubscriptionBaseBundle> result = dao.getSubscriptionBundlesForKey(externalKey, internalCallContext);
        assertEquals(result.size(), 1);
        assertEquals(result.get(0).getExternalKey(), bundle.getExternalKey());

        // Update key to 'internal KB value 'kbtsf-12345:'
        dao.updateBundleExternalKey(bundle.getId(), "kbtsf-12345:" + bundle.getExternalKey(), internalCallContext);
        final List<SubscriptionBaseBundle> result2 = dao.getSubscriptionBundlesForKey(externalKey, internalCallContext);
        assertEquals(result2.size(), 1);

        // Create new bundle with original key, verify all results show original key, stripping down internal prefix
        final DefaultSubscriptionBaseBundle bundleDef2 = new DefaultSubscriptionBaseBundle(externalKey, accountId, startDate, startDate, createdDate, createdDate);
        final SubscriptionBaseBundle bundle2 = dao.createSubscriptionBundle(bundleDef2, catalog, true, internalCallContext);
        final List<SubscriptionBaseBundle> result3 = dao.getSubscriptionBundlesForKey(externalKey, internalCallContext);
        assertEquals(result3.size(), 2);
        assertEquals(result3.get(0).getExternalKey(), bundle2.getExternalKey());
        assertEquals(result3.get(1).getExternalKey(), bundle2.getExternalKey());

        // This time we call the lower SqlDao to rename the bundle automatically and verify we still get same # results,
        // with original key
        dbi.onDemand(BundleSqlDao.class).renameBundleExternalKey(externalKey, "foo", internalCallContext);
        final List<SubscriptionBaseBundle> result4 = dao.getSubscriptionBundlesForKey(externalKey, internalCallContext);
        assertEquals(result4.size(), 2);
        assertEquals(result4.get(0).getExternalKey(), bundle2.getExternalKey());
        assertEquals(result4.get(1).getExternalKey(), bundle2.getExternalKey());

        // Create bundle one more time
        final DefaultSubscriptionBaseBundle bundleDef3 = new DefaultSubscriptionBaseBundle(externalKey, accountId, startDate, startDate, createdDate, createdDate);
        final SubscriptionBaseBundle bundle3 = dao.createSubscriptionBundle(bundleDef3, catalog, true, internalCallContext);
        final List<SubscriptionBaseBundle> result5 = dao.getSubscriptionBundlesForKey(externalKey, internalCallContext);
        assertEquals(result5.size(), 3);
        assertEquals(result5.get(0).getExternalKey(), bundle2.getExternalKey());
        assertEquals(result5.get(1).getExternalKey(), bundle2.getExternalKey());
        assertEquals(result5.get(2).getExternalKey(), bundle2.getExternalKey());
    }

    @Test(groups = "slow")
    public void testDirtyFlag() throws Throwable {
        final IDBI dbiSpy = Mockito.spy(dbi);
        final IDBI roDbiSpy = Mockito.spy(roDbi);
        final SubscriptionDao subscriptionDao = new DefaultSubscriptionDao(dbiSpy,
                                                                           roDbiSpy,
                                                                           clock,
                                                                           addonUtils,
                                                                           notificationQueueService,
                                                                           bus,
                                                                           controlCacheDispatcher,
                                                                           nonEntityDao,
                                                                           internalCallContextFactory);
        Mockito.verify(dbiSpy, Mockito.times(0)).open();
        Mockito.verify(roDbiSpy, Mockito.times(0)).open();

        // @BeforeMethod created the account
        DBRouterUntyped.withRODBIAllowed(true,
                                         new WithProfilingCallback<Object, Throwable>() {
                                      @Override
                                      public Object execute() throws Throwable {
                                          Assert.assertEquals(subscriptionDao.getSubscriptionBundleForAccount(accountId, internalCallContext).size(), 0);
                                          Mockito.verify(dbiSpy, Mockito.times(0)).open();
                                          Mockito.verify(roDbiSpy, Mockito.times(1)).open();

                                          Assert.assertEquals(subscriptionDao.getSubscriptionBundleForAccount(accountId, internalCallContext).size(), 0);
                                          Mockito.verify(dbiSpy, Mockito.times(0)).open();
                                          Mockito.verify(roDbiSpy, Mockito.times(2)).open();

                                          final String externalKey = UUID.randomUUID().toString();
                                          final DateTime startDate = clock.getUTCNow();
                                          final DateTime createdDate = startDate.plusSeconds(10);
                                          final DefaultSubscriptionBaseBundle bundleDef = new DefaultSubscriptionBaseBundle(externalKey, accountId, startDate, startDate, createdDate, createdDate);
                                          final SubscriptionBaseBundle bundle = subscriptionDao.createSubscriptionBundle(bundleDef, catalog, false, internalCallContext);
                                          Mockito.verify(dbiSpy, Mockito.times(1)).open();
                                          Mockito.verify(roDbiSpy, Mockito.times(2)).open();

                                          Assert.assertEquals(subscriptionDao.getSubscriptionBundleForAccount(accountId, internalCallContext).size(), 1);
                                          Mockito.verify(dbiSpy, Mockito.times(2)).open();
                                          Mockito.verify(roDbiSpy, Mockito.times(2)).open();

                                          Assert.assertEquals(subscriptionDao.getSubscriptionBundleForAccount(accountId, internalCallContext).size(), 1);
                                          Mockito.verify(dbiSpy, Mockito.times(3)).open();
                                          Mockito.verify(roDbiSpy, Mockito.times(2)).open();

                                          return null;
                                      }
                                  });

        DBRouterUntyped.withRODBIAllowed(true,
                                         new WithProfilingCallback<Object, Throwable>() {
                                      @Override
                                      public Object execute() {
                                          Assert.assertEquals(subscriptionDao.getSubscriptionBundleForAccount(accountId, internalCallContext).size(), 1);
                                          Mockito.verify(dbiSpy, Mockito.times(3)).open();
                                          Mockito.verify(roDbiSpy, Mockito.times(3)).open();

                                          return null;
                                      }
                                  });
    }
}
