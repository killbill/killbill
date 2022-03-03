/*
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.DefaultPriceListSet;
import org.killbill.billing.catalog.api.CatalogApiException;
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
import org.killbill.billing.subscription.engine.dao.model.SubscriptionBundleModelDao;
import org.killbill.billing.subscription.engine.dao.model.SubscriptionEventModelDao;
import org.killbill.billing.subscription.engine.dao.model.SubscriptionModelDao;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent.EventType;
import org.killbill.billing.subscription.events.user.ApiEventBuilder;
import org.killbill.billing.subscription.events.user.ApiEventCancel;
import org.killbill.billing.subscription.events.user.ApiEventCreate;
import org.killbill.billing.subscription.events.user.ApiEventType;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.audit.AuditLog;
import org.killbill.billing.util.audit.AuditLogWithHistory;
import org.killbill.billing.util.audit.ChangeType;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.entity.dao.DBRouterUntyped;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
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

    private EntitySqlDaoTransactionalJdbiWrapper transactionalSqlDao;
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

        transactionalSqlDao = new EntitySqlDaoTransactionalJdbiWrapper(dbi, roDbi, clock, new CacheControllerDispatcher(), nonEntityDao, internalCallContextFactory);
    }

    @Override // to ignore events
    @AfterMethod(groups = "slow")
    public void afterMethod() throws Exception {
        if (hasFailed()) {
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

            return;
        }
        subscriptionTestInitializer.stopTestFramework(testListener, busService, subscriptionBaseService);
    }

    @Test(groups = "slow")
    public void testBundleExternalKeyReused() throws Exception {

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

        final List<AuditLog> auditLogsBeforeRenaming = auditUserApi.getAuditLogs(bundle.getId(), ObjectType.BUNDLE, AuditLevel.FULL, callContext);
        assertEquals(auditLogsBeforeRenaming.size(), 1);
        assertEquals(auditLogsBeforeRenaming.get(0).getChangeType(), ChangeType.INSERT);

        // Update key to 'internal KB value 'kbtsf-12345:'
        dao.updateBundleExternalKey(bundle.getId(), "kbtsf-12345:" + bundle.getExternalKey(), internalCallContext);
        final List<SubscriptionBaseBundle> result2 = dao.getSubscriptionBundlesForKey(externalKey, internalCallContext);
        assertEquals(result2.size(), 1);

        final List<AuditLog> auditLogsAfterRenaming = auditUserApi.getAuditLogs(bundle.getId(), ObjectType.BUNDLE, AuditLevel.FULL, callContext);
        assertEquals(auditLogsAfterRenaming.size(), 2);
        assertEquals(auditLogsAfterRenaming.get(0).getChangeType(), ChangeType.INSERT);
        assertEquals(auditLogsAfterRenaming.get(1).getChangeType(), ChangeType.UPDATE);

        // Create new bundle with original key, verify all results show original key, stripping down internal prefix
        final DefaultSubscriptionBaseBundle bundleDef2 = new DefaultSubscriptionBaseBundle(externalKey, accountId, startDate, startDate, createdDate, createdDate);
        final SubscriptionBaseBundle bundle2 = dao.createSubscriptionBundle(bundleDef2, catalog, true, internalCallContext);
        final List<SubscriptionBaseBundle> result3 = dao.getSubscriptionBundlesForKey(externalKey, internalCallContext);
        assertEquals(result3.size(), 2);
        assertEquals(result3.get(0).getId(), bundle.getId());
        assertEquals(result3.get(0).getExternalKey(), bundle2.getExternalKey());
        assertEquals(result3.get(1).getId(), bundle2.getId());
        assertEquals(result3.get(1).getExternalKey(), bundle2.getExternalKey());

        final List<AuditLog> auditLogs2BeforeRenaming = auditUserApi.getAuditLogs(bundle2.getId(), ObjectType.BUNDLE, AuditLevel.FULL, callContext);
        assertEquals(auditLogs2BeforeRenaming.size(), 1);
        assertEquals(auditLogs2BeforeRenaming.get(0).getChangeType(), ChangeType.INSERT);

        // This time we call the lower SqlDao to rename the bundle automatically and verify we still get same # results,
        // with original key
        transactionalSqlDao.execute(false,
                                    new EntitySqlDaoTransactionWrapper<Void>() {
                                        @Override
                                        public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                                            entitySqlDaoWrapperFactory.become(BundleSqlDao.class).renameBundleExternalKey(ImmutableList.<String>of(bundle2.getId().toString()), "foo", internalCallContext);
                                            return null;
                                        }
                                    });
        final List<SubscriptionBaseBundle> result4 = dao.getSubscriptionBundlesForKey(externalKey, internalCallContext);
        assertEquals(result4.size(), 2);
        assertEquals(result4.get(0).getExternalKey(), bundle2.getExternalKey());
        assertEquals(result4.get(0).getId(), bundle.getId());
        assertEquals(result4.get(1).getExternalKey(), bundle2.getExternalKey());
        assertEquals(result4.get(1).getId(), bundle2.getId());

        final List<AuditLog> auditLogs2AfterRenaming = auditUserApi.getAuditLogs(bundle2.getId(), ObjectType.BUNDLE, AuditLevel.FULL, callContext);
        assertEquals(auditLogs2AfterRenaming.size(), 2);
        assertEquals(auditLogs2AfterRenaming.get(0).getChangeType(), ChangeType.INSERT);
        assertEquals(auditLogs2AfterRenaming.get(1).getChangeType(), ChangeType.UPDATE);

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
    public void testWithAuditAndHistory() throws SubscriptionBaseApiException {
        final String bundleExternalKey = "54341455sttfs1";
        final DateTime startDate = clock.getUTCNow();

        final DefaultSubscriptionBaseBundle bundleDef = new DefaultSubscriptionBaseBundle(bundleExternalKey, accountId, startDate, startDate, startDate, startDate);
        final SubscriptionBaseBundle bundle = dao.createSubscriptionBundle(bundleDef, catalog, true, internalCallContext);

        final List<AuditLogWithHistory> bundleHistory1 =  dao.getSubscriptionBundleAuditLogsWithHistoryForId(bundle.getId(), AuditLevel.FULL, internalCallContext);
        assertEquals(bundleHistory1.size(), 1);
        final AuditLogWithHistory bundleHistoryRow1 = bundleHistory1.get(0);
        assertEquals(bundleHistoryRow1.getChangeType(), ChangeType.INSERT);
        final SubscriptionBundleModelDao historyRow1 = (SubscriptionBundleModelDao) bundleHistoryRow1.getEntity();
        assertEquals(historyRow1.getExternalKey(), bundle.getExternalKey());
        assertEquals(historyRow1.getAccountId(), bundle.getAccountId());

        dao.updateBundleExternalKey(bundle.getId(), "you changed me!", internalCallContext);
        final List<AuditLogWithHistory> bundleHistory2 =  dao.getSubscriptionBundleAuditLogsWithHistoryForId(bundle.getId(), AuditLevel.FULL, internalCallContext);
        assertEquals(bundleHistory2.size(), 2);
        final AuditLogWithHistory bundleHistoryRow2 = bundleHistory2.get(1);
        assertEquals(bundleHistoryRow2.getChangeType(), ChangeType.UPDATE);
        final SubscriptionBundleModelDao historyRow2 = (SubscriptionBundleModelDao) bundleHistoryRow2.getEntity();
        assertEquals(historyRow2.getExternalKey(), "you changed me!");
        assertEquals(historyRow2.getAccountId(), bundle.getAccountId());

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

        final List<SubscriptionBaseEvent> resultSubscriptions = dao.createSubscriptionsWithAddOns(ImmutableList.<SubscriptionBaseWithAddOns>of(subscriptionBaseWithAddOns),
                                                                                                  ImmutableMap.<UUID, List<SubscriptionBaseEvent>>of(subscription.getId(), ImmutableList.<SubscriptionBaseEvent>of(creationEvent)),
                                                                                                  catalog,
                                                                                                  internalCallContext);
        assertListenerStatus();
        assertEquals(resultSubscriptions.size(), 1);
        final SubscriptionBaseEvent subscriptionBaseEvent = resultSubscriptions.get(0);


        final List<AuditLogWithHistory> subscriptionHistory =  dao.getSubscriptionAuditLogsWithHistoryForId(subscriptionBaseEvent.getSubscriptionId(), AuditLevel.FULL, internalCallContext);
        assertEquals(subscriptionHistory.size(), 1);

        final AuditLogWithHistory subscriptionHistoryRow1 = subscriptionHistory.get(0);
        assertEquals(subscriptionHistoryRow1.getChangeType(), ChangeType.INSERT);
        final SubscriptionModelDao subHistoryRow1 = (SubscriptionModelDao) subscriptionHistoryRow1.getEntity();
        assertEquals(subHistoryRow1.getBundleId(), bundle.getId());
        assertEquals(subHistoryRow1.getCategory(), ProductCategory.BASE);


        final List<AuditLogWithHistory> subscriptionEventHistory =  dao.getSubscriptionEventAuditLogsWithHistoryForId(subscriptionBaseEvent.getId(), AuditLevel.FULL, internalCallContext);

        final AuditLogWithHistory subscriptionEventHistoryRow1 = subscriptionEventHistory.get(0);
        assertEquals(subscriptionEventHistoryRow1.getChangeType(), ChangeType.INSERT);
        final SubscriptionEventModelDao subEventHistoryRow1 = (SubscriptionEventModelDao) subscriptionEventHistoryRow1.getEntity();
        assertEquals(subEventHistoryRow1.getSubscriptionId(), subscriptionBaseEvent.getSubscriptionId());
        assertEquals(subEventHistoryRow1.getEventType(), EventType.API_USER);
        assertEquals(subEventHistoryRow1.getUserType(), ApiEventType.CREATE);
        assertEquals(subEventHistoryRow1.getPlanName(), "shotgun-monthly");
        assertEquals(subEventHistoryRow1.getIsActive(), true);
    }


    @Test(groups = "slow")
    public void testSubscriptionExternalKey() throws SubscriptionBaseApiException, CatalogApiException {
        final String externalKey = "6577564455sgwers2";
        final DateTime startDate = clock.getUTCNow();

        final List<SubscriptionBaseEvent> resultSubscriptions = createSubscription(bundle, externalKey, startDate, null);
        assertEquals(resultSubscriptions.size(), 1);

        final SubscriptionBase s = dao.getSubscriptionFromId(resultSubscriptions.get(0).getSubscriptionId(), catalog, internalCallContext);
        assertEquals(s.getExternalKey(), externalKey);
    }



    @Test(groups = "slow")
    public void testGetActiveSubscriptionsForAccounts() throws SubscriptionBaseApiException, CatalogApiException {


        final String bundleExternalKey = "54341455sttfs1";
        final DateTime startDate = clock.getUTCNow();
        final DateTime cancelDate = startDate.plusDays(17);

        final DefaultSubscriptionBaseBundle bundleDef = new DefaultSubscriptionBaseBundle(bundleExternalKey, accountId, startDate, startDate, startDate, startDate);
        final SubscriptionBaseBundle bundle = dao.createSubscriptionBundle(bundleDef, catalog, true, internalCallContext);

        createSubscription(bundle, null, startDate, null);
        createSubscription(bundle, null, startDate, cancelDate);

        final InternalCallContext callContextWithAccountID = internalCallContextFactory.createInternalCallContext(accountId, callContext);
        final Map<UUID, List<DefaultSubscriptionBase>> res1 =  dao.getSubscriptionsFromAccountId(null, callContextWithAccountID);
        assertEquals(res1.size(), 1);
        assertEquals(res1.get(bundle.getId()).size(), 2);

        final List<SubscriptionBaseEvent> events1 = ((DefaultSubscriptionDao) dao).getEventsForAccountId(null, callContextWithAccountID);
        assertEquals(events1.size(), 3);


        final Map<UUID, List<DefaultSubscriptionBase>> res2 =  dao.getSubscriptionsFromAccountId(cancelDate.toLocalDate(), callContextWithAccountID);
        assertEquals(res2.size(), 1);
        assertEquals(res2.get(bundle.getId()).size(), 2);

        final List<SubscriptionBaseEvent> events2 = ((DefaultSubscriptionDao) dao).getEventsForAccountId(cancelDate.toLocalDate(), callContextWithAccountID);
        assertEquals(events2.size(), 3);

        final Map<UUID, List<DefaultSubscriptionBase>> res3 =  dao.getSubscriptionsFromAccountId(cancelDate.plusDays(1).toLocalDate(), callContextWithAccountID);
        assertEquals(res3.size(), 1);
        assertEquals(res3.get(bundle.getId()).size(), 1);

        final List<SubscriptionBaseEvent> events3 = ((DefaultSubscriptionDao) dao).getEventsForAccountId(cancelDate.plusDays(1).toLocalDate(), callContextWithAccountID);
        assertEquals(events3.size(), 1);

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
                                                                           auditDao,
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


    private List<SubscriptionBaseEvent> createSubscription(final SubscriptionBaseBundle bundle, final String externalKey, final DateTime startDate, final DateTime cancelDate) {

        final SubscriptionBuilder builder = new SubscriptionBuilder()
                .setId(UUIDs.randomUUID())
                .setBundleId(bundle.getId())
                .setBundleExternalKey(bundle.getExternalKey())
                .setCategory(ProductCategory.BASE)
                .setBundleStartDate(startDate)
                .setAlignStartDate(startDate)
                .setExternalKey(externalKey)
                .setMigrated(false);

        final ApiEventBuilder createBuilder = new ApiEventBuilder()
                .setSubscriptionId(builder.getId())
                .setEventPlan("shotgun-monthly")
                .setEventPlanPhase("shotgun-monthly-trial")
                .setEventPriceList(DefaultPriceListSet.DEFAULT_PRICELIST_NAME)
                .setEffectiveDate(startDate)
                .setFromDisk(true);
        final SubscriptionBaseEvent creationEvent = new ApiEventCreate(createBuilder);

        final ApiEventBuilder cancelBuilder = cancelDate != null ? new ApiEventBuilder()
                .setSubscriptionId(builder.getId())
                .setEffectiveDate(cancelDate)
                .setFromDisk(true) : null;

        final SubscriptionBaseEvent cancelEvent = cancelBuilder != null ?
                                                  new ApiEventCancel(cancelBuilder) : null;


        final DefaultSubscriptionBase subscription = new DefaultSubscriptionBase(builder);
        final SubscriptionBaseWithAddOns subscriptionBaseWithAddOns = new DefaultSubscriptionBaseWithAddOns(bundle,
                                                                                                            ImmutableList.<SubscriptionBase>of(subscription));
        testListener.pushExpectedEvents(NextEvent.CREATE);
        final ImmutableList<SubscriptionBaseEvent> events = cancelEvent !=  null ?
                                                            ImmutableList.<SubscriptionBaseEvent>of(creationEvent, cancelEvent) :
                                                            ImmutableList.<SubscriptionBaseEvent>of(creationEvent);

        final List<SubscriptionBaseEvent> result = dao.createSubscriptionsWithAddOns(ImmutableList.<SubscriptionBaseWithAddOns>of(subscriptionBaseWithAddOns),
                                                                                     ImmutableMap.<UUID, List<SubscriptionBaseEvent>>of(subscription.getId(), events),
                                                                                     catalog,
                                                                                     internalCallContext);
        assertListenerStatus();
        return result;
    }

}
