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

package com.ning.billing.entitlement;

import java.net.URL;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import com.ning.billing.GuicyKillbillTestSuiteWithEmbeddedDB;
import com.ning.billing.account.api.AccountData;
import com.ning.billing.account.api.AccountInternalApi;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.api.TestApiListener;
import com.ning.billing.api.TestListenerStatus;
import com.ning.billing.bus.api.PersistentBus;
import com.ning.billing.catalog.DefaultCatalogService;
import com.ning.billing.catalog.api.Catalog;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.clock.ClockMock;
import com.ning.billing.entitlement.api.EntitlementApi;
import com.ning.billing.entitlement.api.SubscriptionApi;
import com.ning.billing.entitlement.dao.BlockingStateDao;
import com.ning.billing.entitlement.engine.core.EntitlementUtils;
import com.ning.billing.entitlement.engine.core.EventsStreamBuilder;
import com.ning.billing.entitlement.glue.TestEntitlementModuleWithEmbeddedDB;
import com.ning.billing.junction.BlockingInternalApi;
import com.ning.billing.mock.MockAccountBuilder;
import com.ning.billing.subscription.api.SubscriptionBaseInternalApi;
import com.ning.billing.subscription.api.SubscriptionBaseService;
import com.ning.billing.subscription.engine.core.DefaultSubscriptionBaseService;
import com.ning.billing.tag.TagInternalApi;
import com.ning.billing.util.svcsapi.bus.BusService;
import com.ning.billing.util.tag.dao.TagDao;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Stage;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class EntitlementTestSuiteWithEmbeddedDB extends GuicyKillbillTestSuiteWithEmbeddedDB {

    protected static final Logger log = LoggerFactory.getLogger(EntitlementTestSuiteWithEmbeddedDB.class);

    // Be generous...
    protected static final Long DELAY = 20000L;

    @Inject
    protected AccountUserApi accountApi;
    @Inject
    protected AccountInternalApi accountInternalApi;
    @Inject
    protected BlockingInternalApi blockingInternalApi;
    @Inject
    protected EntitlementApi entitlementApi;
    @Inject
    protected SubscriptionApi subscriptionApi;
    @Inject
    protected BlockingStateDao blockingStateDao;
    @Inject
    protected CatalogService catalogService;
    @Inject
    protected SubscriptionBaseInternalApi subscriptionInternalApi;
    @Inject
    protected PersistentBus bus;
    @Inject
    protected TagDao tagDao;
    @Inject
    protected TagInternalApi tagInternalApi;
    @Inject
    protected TestApiListener testListener;
    @Inject
    protected TestListenerStatus testListenerStatus;
    @Inject
    protected BusService busService;
    @Inject
    protected SubscriptionBaseService subscriptionBaseService;
    @Inject
    protected EntitlementService entitlementService;
    @Inject
    protected EntitlementUtils entitlementUtils;
    @Inject
    protected EventsStreamBuilder eventsStreamBuilder;

    protected Catalog catalog;

    private void loadSystemPropertiesFromClasspath(final String resource) {
        final URL url = EntitlementTestSuiteWithEmbeddedDB.class.getResource(resource);
        Assert.assertNotNull(url);

        configSource.merge(url);
    }

    @BeforeClass(groups = "slow")
    protected void beforeClass() throws Exception {
        loadSystemPropertiesFromClasspath("/entitlement.properties");
        final Injector injector = Guice.createInjector(Stage.PRODUCTION, new TestEntitlementModuleWithEmbeddedDB(configSource));
        injector.injectMembers(this);
    }

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        startTestFamework(testListener, testListenerStatus, clock, busService, subscriptionBaseService, entitlementService);
        this.catalog = initCatalog(catalogService);

        // Make sure we start with a clean state
        assertListenerStatus();
    }

    @AfterMethod(groups = "slow")
    public void afterMethod() throws Exception {
        // Make sure we finish in a clean state
        assertListenerStatus();

        stopTestFramework(testListener, busService, subscriptionBaseService, entitlementService);
    }

    private Catalog initCatalog(final CatalogService catalogService) throws Exception {

        ((DefaultCatalogService) catalogService).loadCatalog();
        final Catalog catalog = catalogService.getFullCatalog();
        assertNotNull(catalog);
        return catalog;
    }


    private void startTestFamework(final TestApiListener testListener,
                                   final TestListenerStatus testListenerStatus,
                                   final ClockMock clock,
                                   final BusService busService,
                                   final SubscriptionBaseService subscriptionBaseService,
                                   final EntitlementService entitlementService) throws Exception {
        log.debug("STARTING TEST FRAMEWORK");

        resetTestListener(testListener, testListenerStatus);

        resetClockToStartOfTest(clock);

        startBusAndRegisterListener(busService, testListener);

        restartSubscriptionService(subscriptionBaseService);
        restartEntitlementService(entitlementService);

        log.debug("STARTED TEST FRAMEWORK");
    }


    private void stopTestFramework(final TestApiListener testListener,
                                   final BusService busService,
                                   final SubscriptionBaseService subscriptionBaseService,
                                   final EntitlementService entitlementService) throws Exception {
        log.debug("STOPPING TEST FRAMEWORK");
        stopBusAndUnregisterListener(busService, testListener);
        stopSubscriptionService(subscriptionBaseService);
        stopEntitlementService(entitlementService);
        log.debug("STOPPED TEST FRAMEWORK");
    }

    private void resetTestListener(final TestApiListener testListener, final TestListenerStatus testListenerStatus) {
        // RESET LIST OF EXPECTED EVENTS
        if (testListener != null) {
            testListener.reset();
            testListenerStatus.resetTestListenerStatus();
        }
    }

    private void resetClockToStartOfTest(final ClockMock clock) {
        clock.resetDeltaFromReality();

        // Date at which all tests start-- we create the date object here after the system properties which set the JVM in UTC have been set.
        final DateTime testStartDate = new DateTime(2012, 5, 7, 0, 3, 42, 0);
        clock.setDeltaFromReality(testStartDate.getMillis() - clock.getUTCNow().getMillis());
    }

    private void startBusAndRegisterListener(final BusService busService, final TestApiListener testListener) throws Exception {
        busService.getBus().start();
        busService.getBus().register(testListener);
    }

    private void restartSubscriptionService(final SubscriptionBaseService subscriptionBaseService) {
        // START NOTIFICATION QUEUE FOR SUBSCRIPTION
        ((DefaultSubscriptionBaseService) subscriptionBaseService).initialize();
        ((DefaultSubscriptionBaseService) subscriptionBaseService).start();
    }

    private void restartEntitlementService(final EntitlementService entitlementService) {
        // START NOTIFICATION QUEUE FOR ENTITLEMENT
        ((DefaultEntitlementService) entitlementService).initialize();
        ((DefaultEntitlementService) entitlementService).start();
    }

    private void stopBusAndUnregisterListener(final BusService busService, final TestApiListener testListener) throws Exception {
        busService.getBus().unregister(testListener);
        busService.getBus().stop();
    }

    private void stopSubscriptionService(final SubscriptionBaseService subscriptionBaseService) throws Exception {
        ((DefaultSubscriptionBaseService) subscriptionBaseService).stop();
    }

    private void stopEntitlementService(final EntitlementService entitlementService) throws Exception {
        ((DefaultEntitlementService) entitlementService).stop();
    }

    protected AccountData getAccountData(final int billingDay) {
        return new MockAccountBuilder().name(UUID.randomUUID().toString().substring(1, 8))
                                       .firstNameLength(6)
                                       .email(UUID.randomUUID().toString().substring(1, 8))
                                       .phone(UUID.randomUUID().toString().substring(1, 8))
                                       .migrated(false)
                                       .isNotifiedForInvoices(false)
                                       .externalKey(UUID.randomUUID().toString().substring(1, 8))
                                       .billingCycleDayLocal(billingDay)
                                       .currency(Currency.USD)
                                       .paymentMethodId(UUID.randomUUID())
                                       .timeZone(DateTimeZone.UTC)
                                       .build();
    }

    protected void assertListenerStatus() {
        assertTrue(testListener.isCompleted(DELAY));
        ((EntitlementTestListenerStatus) testListenerStatus).assertListenerStatus();
    }
}
