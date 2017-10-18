/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.billing.entitlement;

import java.util.UUID;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.config.Ini;
import org.apache.shiro.config.IniSecurityManagerFactory;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.util.Factory;
import org.apache.shiro.util.ThreadContext;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.killbill.billing.GuicyKillbillTestSuiteWithEmbeddedDB;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.api.TestApiListener;
import org.killbill.billing.catalog.DefaultCatalogService;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.CatalogService;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.entitlement.api.EntitlementApi;
import org.killbill.billing.entitlement.api.SubscriptionApi;
import org.killbill.billing.entitlement.dao.BlockingStateDao;
import org.killbill.billing.entitlement.engine.core.EntitlementUtils;
import org.killbill.billing.entitlement.engine.core.EventsStreamBuilder;
import org.killbill.billing.entitlement.glue.TestEntitlementModuleWithEmbeddedDB;
import org.killbill.billing.junction.BlockingInternalApi;
import org.killbill.billing.lifecycle.api.BusService;
import org.killbill.billing.mock.MockAccountBuilder;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.security.Permission;
import org.killbill.billing.security.api.SecurityApi;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.subscription.api.SubscriptionBaseService;
import org.killbill.billing.subscription.engine.core.DefaultSubscriptionBaseService;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.tag.dao.TagDao;
import org.killbill.bus.api.PersistentBus;
import org.killbill.clock.ClockMock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Stage;

import static org.testng.Assert.assertNotNull;

public class EntitlementTestSuiteWithEmbeddedDB extends GuicyKillbillTestSuiteWithEmbeddedDB {

    protected static final Logger log = LoggerFactory.getLogger(EntitlementTestSuiteWithEmbeddedDB.class);

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
    protected BusService busService;
    @Inject
    protected SubscriptionBaseService subscriptionBaseService;
    @Inject
    protected EntitlementService entitlementService;
    @Inject
    protected EntitlementUtils entitlementUtils;
    @Inject
    protected EventsStreamBuilder eventsStreamBuilder;
    @Inject
    protected AuditUserApi auditUserApi;
    @Inject
    protected InternalCallContextFactory internalCallContextFactory;
    @Inject
    protected SecurityApi securityApi;
    @Inject
    protected NonEntityDao nonEntityDao;

    protected Catalog catalog;

    @Override
    protected KillbillConfigSource getConfigSource() {
        return getConfigSource("/entitlement.properties");
    }

    @BeforeClass(groups = "slow")
    protected void beforeClass() throws Exception {
        final Injector injector = Guice.createInjector(Stage.PRODUCTION, new TestEntitlementModuleWithEmbeddedDB(configSource));
        injector.injectMembers(this);
    }

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        startTestFamework(testListener, clock, busService, subscriptionBaseService, entitlementService);
        this.catalog = initCatalog(catalogService);

        configureShiro();
        login("EntitlementUser");
    }

    private void login(final String username) {
        securityApi.login(username, "password");
    }


    protected void configureShiro() {
        final Ini config = new Ini();
        config.addSection("users");
        config.getSection("users").put("EntitlementUser", "password, entitlement");
        config.addSection("roles");
        config.getSection("roles").put("entitlement", Permission.ACCOUNT_CAN_CREATE.toString() +
                                                      "," + Permission.ENTITLEMENT_CAN_CREATE.toString() +
                                                      "," + Permission.ENTITLEMENT_CAN_CHANGE_PLAN.toString() +
                                                      "," + Permission.ENTITLEMENT_CAN_PAUSE_RESUME.toString() +
                                                      "," + Permission.ENTITLEMENT_CAN_TRANSFER.toString() +
                                                      "," + Permission.ENTITLEMENT_CAN_CANCEL.toString());

        // Reset the security manager
        ThreadContext.unbindSecurityManager();

        final Factory<SecurityManager> factory = new IniSecurityManagerFactory(config);
        final SecurityManager securityManager = factory.getInstance();
        SecurityUtils.setSecurityManager(securityManager);
    }

    @AfterMethod(groups = "slow")
    public void afterMethod() throws Exception {
        securityApi.logout();

        stopTestFramework(testListener, busService, subscriptionBaseService, entitlementService);
    }

    private Catalog initCatalog(final CatalogService catalogService) throws Exception {

        ((DefaultCatalogService) catalogService).loadCatalog();
        final Catalog catalog = catalogService.getFullCatalog(true, true, internalCallContext);
        assertNotNull(catalog);
        return catalog;
    }

    private void startTestFamework(final TestApiListener testListener,
                                   final ClockMock clock,
                                   final BusService busService,
                                   final SubscriptionBaseService subscriptionBaseService,
                                   final EntitlementService entitlementService) throws Exception {
        log.debug("STARTING TEST FRAMEWORK");

        resetTestListener(testListener);

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

    private void resetTestListener(final TestApiListener testListener) {
        // RESET LIST OF EXPECTED EVENTS
        if (testListener != null) {
            testListener.reset();
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

    protected Account createAccount(final AccountData accountData) throws AccountApiException {
        final Account account = accountApi.createAccount(accountData, callContext);

        refreshCallContext(account.getId());

        return account;
    }

    @Override
    protected void assertListenerStatus() {
        testListener.assertListenerStatus();
    }
}
