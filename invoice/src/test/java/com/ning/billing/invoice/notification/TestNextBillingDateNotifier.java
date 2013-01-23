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

package com.ning.billing.invoice.notification;

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
import com.ning.billing.catalog.MockCatalogModule;
import com.ning.billing.dbi.DBIProvider;
import com.ning.billing.dbi.DBTestingHelper;
import com.ning.billing.dbi.DbiConfig;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.invoice.InvoiceDispatcher;
import com.ning.billing.invoice.InvoiceListener;
import com.ning.billing.invoice.InvoiceTestSuiteWithEmbeddedDB;
import com.ning.billing.invoice.api.formatters.InvoiceFormatterFactory;
import com.ning.billing.invoice.glue.InvoiceModuleWithMocks;
import com.ning.billing.invoice.template.formatters.DefaultInvoiceFormatterFactory;
import com.ning.billing.lifecycle.KillbillService;
import com.ning.billing.mock.glue.MockJunctionModule;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.config.InvoiceConfig;
import com.ning.billing.util.dao.NonEntityDao;
import com.ning.billing.util.email.templates.TemplateModule;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoTransactionWrapper;
import com.ning.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import com.ning.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import com.ning.billing.util.glue.BusModule;
import com.ning.billing.util.glue.BusModule.BusType;
import com.ning.billing.util.glue.CacheModule;
import com.ning.billing.util.glue.NonEntityDaoModule;
import com.ning.billing.util.glue.NotificationQueueModule;
import com.ning.billing.util.glue.TagStoreModule;
import com.ning.billing.util.notificationq.NotificationQueueService;
import com.ning.billing.util.svcapi.account.AccountInternalApi;
import com.ning.billing.util.svcapi.entitlement.EntitlementInternalApi;
import com.ning.billing.util.svcsapi.bus.InternalBus;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;

import static com.jayway.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.MINUTES;

public class TestNextBillingDateNotifier extends InvoiceTestSuiteWithEmbeddedDB {

    private Clock clock;
    private DefaultNextBillingDateNotifier notifier;
    private InternalBus eventBus;
    private InvoiceListenerMock listener;
    private NotificationQueueService notificationQueueService;
    private InternalCallContextFactory internalCallContextFactory;
    private EntitySqlDaoTransactionalJdbiWrapper entitySqlDaoTransactionalJdbiWrapper;
    private CacheControllerDispatcher controllerDispatcher;
    private NonEntityDao nonEntityDao;

    private static final class InvoiceListenerMock extends InvoiceListener {

        int eventCount = 0;
        UUID latestSubscriptionId = null;

        public InvoiceListenerMock(final InternalCallContextFactory internalCallContextFactory, final InvoiceDispatcher dispatcher) {
            super(internalCallContextFactory, dispatcher);
        }

        @Override
        public void handleNextBillingDateEvent(final UUID subscriptionId, final DateTime eventDateTime, final UUID userToken, final Long accountRecordId, final Long tenantRecordId) {
            eventCount++;
            latestSubscriptionId = subscriptionId;
        }

        public int getEventCount() {
            return eventCount;
        }

        public UUID getLatestSubscriptionId() {
            return latestSubscriptionId;
        }

    }

    @BeforeClass(groups = {"slow"})
    public void setup() throws KillbillService.ServiceException, IOException, ClassNotFoundException, SQLException, EntitlementUserApiException {
        //TestApiBase.loadSystemPropertiesFromClasspath("/entitlement.properties");
        final Injector g = Guice.createInjector(Stage.PRODUCTION, new AbstractModule() {
            @Override
            protected void configure() {

                final ClockMock clock = new ClockMock();
                bind(Clock.class).toInstance(clock);
                bind(ClockMock.class).toInstance(clock);

                install(new BusModule(BusType.MEMORY));
                install(new InvoiceModuleWithMocks());
                install(new MockJunctionModule());
                install(new MockCatalogModule());
                install(new NotificationQueueModule());
                install(new TemplateModule());
                install(new TagStoreModule());
                install(new CacheModule());
                install(new NonEntityDaoModule());

                final DBTestingHelper helper = KillbillTestSuiteWithEmbeddedDB.getDBTestingHelper();
                if (helper.isUsingLocalInstance()) {
                    bind(IDBI.class).toProvider(DBIProvider.class).asEagerSingleton();
                    final DbiConfig config = new ConfigurationObjectFactory(System.getProperties()).build(DbiConfig.class);
                    bind(DbiConfig.class).toInstance(config);
                } else {
                    final IDBI dbi = helper.getDBI();
                    bind(IDBI.class).toInstance(dbi);
                }

                /*
                final InternalCallContextFactory internalCallContextFactory = new InternalCallContextFactory(helper.getDBI(), clock, nonEntityDao);
                bind(InternalCallContextFactory.class).toInstance(internalCallContextFactory);
*/
                bind(InvoiceFormatterFactory.class).to(DefaultInvoiceFormatterFactory.class).asEagerSingleton();

                bind(AccountInternalApi.class).toInstance(Mockito.mock(AccountInternalApi.class));
                bind(EntitlementInternalApi.class).toInstance(Mockito.mock(EntitlementInternalApi.class));
            }
        });

        clock = g.getInstance(Clock.class);
        final IDBI dbi = g.getInstance(IDBI.class);

        nonEntityDao = g.getInstance(NonEntityDao.class);
        controllerDispatcher = g.getInstance(CacheControllerDispatcher.class);

        entitySqlDaoTransactionalJdbiWrapper = new EntitySqlDaoTransactionalJdbiWrapper(dbi, clock, controllerDispatcher, nonEntityDao);

        eventBus = g.getInstance(InternalBus.class);
        notificationQueueService = g.getInstance(NotificationQueueService.class);
        final InvoiceDispatcher dispatcher = g.getInstance(InvoiceDispatcher.class);

        final Subscription subscription = Mockito.mock(Subscription.class);
        final EntitlementInternalApi entitlementUserApi = Mockito.mock(EntitlementInternalApi.class);
        Mockito.when(entitlementUserApi.getSubscriptionFromId(Mockito.<UUID>any(), Mockito.<InternalTenantContext>any())).thenReturn(subscription);

        internalCallContextFactory = g.getInstance(InternalCallContextFactory.class);
        listener = new InvoiceListenerMock(internalCallContextFactory, dispatcher);
        notifier = new DefaultNextBillingDateNotifier(notificationQueueService, g.getInstance(InvoiceConfig.class), entitlementUserApi,
                                                      listener, internalCallContextFactory);
    }

    @Test(groups = "slow")
    public void testInvoiceNotifier() throws Exception {

        final UUID accountId = UUID.randomUUID();
        final UUID subscriptionId = new UUID(0L, 1L);
        final DateTime now = new DateTime();
        final DateTime readyTime = now.plusMillis(2000);
        final NextBillingDatePoster poster = new DefaultNextBillingDatePoster(notificationQueueService, internalCallContextFactory);

        eventBus.start();
        notifier.initialize();
        notifier.start();

        entitySqlDaoTransactionalJdbiWrapper.execute(new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                poster.insertNextBillingNotification(entitySqlDaoWrapperFactory, accountId, subscriptionId, readyTime, UUID.randomUUID());
                return null;
            }
        });

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
    public void tearDown() throws Exception {
        notifier.stop();
    }
}
