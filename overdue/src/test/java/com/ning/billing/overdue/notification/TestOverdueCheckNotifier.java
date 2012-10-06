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

import static com.jayway.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.MINUTES;

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

import com.ning.billing.KillbillTestSuiteWithEmbeddedDB;
import com.ning.billing.catalog.DefaultCatalogService;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.config.CatalogConfig;
import com.ning.billing.config.InvoiceConfig;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
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
import com.ning.billing.util.svcsapi.bus.Bus;
import com.ning.billing.util.callcontext.CallContextFactory;
import com.ning.billing.util.callcontext.DefaultCallContextFactory;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.customfield.dao.AuditedCustomFieldDao;
import com.ning.billing.util.customfield.dao.CustomFieldDao;
import com.ning.billing.util.email.EmailModule;
import com.ning.billing.util.email.templates.TemplateModule;
import com.ning.billing.util.globallocker.GlobalLocker;
import com.ning.billing.util.globallocker.MySqlGlobalLocker;
import com.ning.billing.util.glue.BusModule;
import com.ning.billing.util.notificationq.DefaultNotificationQueueService;
import com.ning.billing.util.notificationq.NotificationQueueService;
import com.ning.billing.util.tag.dao.AuditedTagDao;
import com.ning.billing.util.tag.dao.TagDao;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;

public class TestOverdueCheckNotifier extends OverdueTestSuiteWithEmbeddedDB {
    private Clock clock;
    private DefaultOverdueCheckNotifier notifier;

    private Bus eventBus;
    private OverdueListenerMock listener;
    private NotificationQueueService notificationQueueService;

    private static final class OverdueListenerMock extends OverdueListener {
        int eventCount = 0;
        UUID latestSubscriptionId = null;

        public OverdueListenerMock() {
            super(null, new InternalCallContextFactory(getMysqlTestingHelper().getDBI(), new ClockMock()));
        }

        @Override
        public void handleNextOverdueCheck(final OverdueCheckNotificationKey key) {
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
    public void setup() throws ServiceException, IOException, ClassNotFoundException, SQLException, EntitlementUserApiException {
        final Injector g = Guice.createInjector(Stage.PRODUCTION, new MockInvoiceModule(), new MockPaymentModule(), new BusModule(), new DefaultOverdueModule() {
            @Override
            protected void configure() {
                super.configure();
                bind(Clock.class).to(ClockMock.class).asEagerSingleton();
                bind(CallContextFactory.class).to(DefaultCallContextFactory.class).asEagerSingleton();
                bind(NotificationQueueService.class).to(DefaultNotificationQueueService.class).asEagerSingleton();
                final InvoiceConfig invoiceConfig = new ConfigurationObjectFactory(System.getProperties()).build(InvoiceConfig.class);
                bind(InvoiceConfig.class).toInstance(invoiceConfig);
                final CatalogConfig catalogConfig = new ConfigurationObjectFactory(System.getProperties()).build(CatalogConfig.class);
                bind(CatalogConfig.class).toInstance(catalogConfig);
                bind(CatalogService.class).to(DefaultCatalogService.class).asEagerSingleton();
                final MysqlTestingHelper helper = KillbillTestSuiteWithEmbeddedDB.getMysqlTestingHelper();
                bind(MysqlTestingHelper.class).toInstance(helper);
                final IDBI dbi = helper.getDBI();
                bind(IDBI.class).toInstance(dbi);
                bind(TagDao.class).to(AuditedTagDao.class).asEagerSingleton();
                bind(CustomFieldDao.class).to(AuditedCustomFieldDao.class).asEagerSingleton();
                bind(GlobalLocker.class).to(MySqlGlobalLocker.class).asEagerSingleton();
                install(new MockJunctionModule());
                install(new EmailModule());
                install(new TemplateModule());
            }
        });

        clock = g.getInstance(Clock.class);

        eventBus = g.getInstance(Bus.class);
        notificationQueueService = g.getInstance(NotificationQueueService.class);

        final OverdueProperties properties = g.getInstance(OverdueProperties.class);

        final Subscription subscription = Mockito.mock(Subscription.class);
        final EntitlementUserApi entitlementUserApi = Mockito.mock(EntitlementUserApi.class);
        Mockito.when(entitlementUserApi.getSubscriptionFromId(Mockito.<UUID>any(), Mockito.<TenantContext>any())).thenReturn(subscription);

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
