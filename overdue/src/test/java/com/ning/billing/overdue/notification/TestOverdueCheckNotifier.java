/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.overdue.notification;

import java.io.IOException;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.joda.time.DateTime;
import org.mockito.Mockito;
import org.skife.config.ConfigurationObjectFactory;
import org.skife.jdbi.v2.IDBI;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.catalog.DefaultCatalogService;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.junction.api.Blockable;
import com.ning.billing.lifecycle.KillbillService.ServiceException;
import com.ning.billing.mock.glue.MockInvoiceModule;
import com.ning.billing.mock.glue.MockJunctionModule;
import com.ning.billing.mock.glue.MockPaymentModule;
import com.ning.billing.ovedue.notification.DefaultOverdueCheckNotifier;
import com.ning.billing.ovedue.notification.DefaultOverdueCheckPoster;
import com.ning.billing.ovedue.notification.OverdueCheckNotificationKey;
import com.ning.billing.ovedue.notification.OverdueCheckPoster;
import com.ning.billing.overdue.OverdueProperties;
import com.ning.billing.overdue.OverdueTestSuiteWithEmbeddedDB;
import com.ning.billing.overdue.glue.DefaultOverdueModule;
import com.ning.billing.overdue.listener.OverdueListener;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.callcontext.CallContextFactory;
import com.ning.billing.util.callcontext.DefaultCallContextFactory;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.config.CatalogConfig;
import com.ning.billing.util.config.InvoiceConfig;
import com.ning.billing.util.customfield.dao.CustomFieldDao;
import com.ning.billing.util.customfield.dao.DefaultCustomFieldDao;
import com.ning.billing.util.dao.DefaultNonEntityDao;
import com.ning.billing.util.dao.NonEntityDao;
import com.ning.billing.util.email.EmailModule;
import com.ning.billing.util.email.templates.TemplateModule;
import com.ning.billing.util.globallocker.GlobalLocker;
import com.ning.billing.util.globallocker.MySqlGlobalLocker;
import com.ning.billing.util.glue.BusModule;
import com.ning.billing.util.glue.CacheModule;
import com.ning.billing.util.glue.NonEntityDaoModule;
import com.ning.billing.util.glue.NotificationQueueModule;
import com.ning.billing.util.glue.TagStoreModule;
import com.ning.billing.util.notificationq.NotificationQueueService;
import com.ning.billing.util.svcapi.account.AccountInternalApi;
import com.ning.billing.util.svcapi.entitlement.EntitlementInternalApi;
import com.ning.billing.util.svcsapi.bus.InternalBus;
import com.ning.billing.util.tag.dao.DefaultTagDao;
import com.ning.billing.util.tag.dao.TagDao;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;

import static com.jayway.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.MINUTES;

public class TestOverdueCheckNotifier extends OverdueTestSuiteWithEmbeddedDB {

    private Clock clock;
    private DefaultOverdueCheckNotifier notifier;

    private InternalBus eventBus;
    private OverdueListenerMock listener;
    private NotificationQueueService notificationQueueService;

    private static final class OverdueListenerMock extends OverdueListener {

        int eventCount = 0;
        UUID latestSubscriptionId = null;

        public OverdueListenerMock() {
            super(null, new InternalCallContextFactory(new ClockMock(), new DefaultNonEntityDao(getDBI()), new CacheControllerDispatcher()));
        }

        @Override
        public void handleNextOverdueCheck(final OverdueCheckNotificationKey key, final UUID userToken, final Long accountRecordId, final Long tenantRecordId) {
            eventCount++;
            latestSubscriptionId = key.getUuidKey();
        }

        public int getEventCount() {
            return eventCount;
        }

        public UUID getLatestSubscriptionId() {
            return latestSubscriptionId;
        }
    }

    @BeforeClass(groups = "slow")
    public void setup() throws ServiceException, IOException, ClassNotFoundException, SQLException, EntitlementUserApiException, AccountApiException {
        final Injector g = Guice.createInjector(Stage.PRODUCTION, new MockInvoiceModule(), new MockPaymentModule(), new BusModule(), new TagStoreModule(),
                                                new DefaultOverdueModule() {
            @Override
            protected void configure() {
                super.configure();
                bind(Clock.class).to(ClockMock.class).asEagerSingleton();
                bind(CallContextFactory.class).to(DefaultCallContextFactory.class).asEagerSingleton();
                final InvoiceConfig invoiceConfig = new ConfigurationObjectFactory(System.getProperties()).build(InvoiceConfig.class);
                bind(InvoiceConfig.class).toInstance(invoiceConfig);
                final CatalogConfig catalogConfig = new ConfigurationObjectFactory(System.getProperties()).build(CatalogConfig.class);
                bind(CatalogConfig.class).toInstance(catalogConfig);
                bind(CatalogService.class).to(DefaultCatalogService.class).asEagerSingleton();
                final IDBI dbi = getDBI();
                bind(IDBI.class).toInstance(dbi);
                bind(TagDao.class).to(DefaultTagDao.class).asEagerSingleton();
                bind(CustomFieldDao.class).to(DefaultCustomFieldDao.class).asEagerSingleton();
                bind(GlobalLocker.class).to(MySqlGlobalLocker.class).asEagerSingleton();
                install(new MockJunctionModule());
                install(new EmailModule());
                install(new TemplateModule());
                install(new NotificationQueueModule());
                install(new CacheModule());
                install(new NonEntityDaoModule());
                final AccountInternalApi accountApi = Mockito.mock(AccountInternalApi.class);
                bind(AccountInternalApi.class).toInstance(accountApi);

                final EntitlementInternalApi entitlementApi = Mockito.mock(EntitlementInternalApi.class);
                bind(EntitlementInternalApi.class).toInstance(entitlementApi);
            }
        });

        clock = g.getInstance(Clock.class);

        eventBus = g.getInstance(InternalBus.class);
        notificationQueueService = g.getInstance(NotificationQueueService.class);

        final OverdueProperties properties = g.getInstance(OverdueProperties.class);

        final Account account = Mockito.mock(Account.class);
        final AccountInternalApi accountApi = g.getInstance(AccountInternalApi.class);
        Mockito.when(accountApi.getAccountById(Mockito.<UUID>any(), Mockito.<InternalTenantContext>any())).thenReturn(account);

        final Subscription subscription = Mockito.mock(Subscription.class);
        final EntitlementInternalApi entitlementApi = g.getInstance(EntitlementInternalApi.class);
        Mockito.when(entitlementApi.getSubscriptionFromId(Mockito.<UUID>any(), Mockito.<InternalTenantContext>any())).thenReturn(subscription);

        listener = new OverdueListenerMock();
        notifier = new DefaultOverdueCheckNotifier(notificationQueueService,
                                                   properties, listener);

        eventBus.start();
        notifier.initialize();
        notifier.start();
    }

    @Test(groups = "slow")
    public void test() throws Exception {
        final UUID subscriptionId = new UUID(0L, 1L);
        final Blockable blockable = Mockito.mock(Subscription.class);
        Mockito.when(blockable.getId()).thenReturn(subscriptionId);
        final DateTime now = new DateTime();
        final DateTime readyTime = now.plusMillis(2000);
        final OverdueCheckPoster poster = new DefaultOverdueCheckPoster(notificationQueueService);

        poster.insertOverdueCheckNotification(blockable, readyTime, internalCallContext);

        // Move time in the future after the notification effectiveDate
        ((ClockMock) clock).setDeltaFromReality(3000);

        await().atMost(1, MINUTES).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return listener.getEventCount() == 1;
            }
        });

        Assert.assertEquals(listener.getEventCount(), 1);
        Assert.assertEquals(listener.getLatestSubscriptionId(), subscriptionId);
    }

    @AfterClass(groups = "slow")
    public void tearDown() {
        eventBus.stop();
        notifier.stop();
    }
}
