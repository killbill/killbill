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

package com.ning.billing.entitlement.api;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.UUID;

import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.TestCallContext;
import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.ITestResult;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

import com.google.inject.Injector;
import com.ning.billing.account.api.AccountData;
import com.ning.billing.catalog.DefaultCatalogService;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Catalog;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.Duration;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.catalog.api.TimeUnit;
import com.ning.billing.config.EntitlementConfig;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.entitlement.api.ApiTestListener.NextEvent;
import com.ning.billing.entitlement.api.billing.EntitlementBillingApi;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.api.user.SubscriptionEventTransition;
import com.ning.billing.entitlement.engine.core.Engine;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.entitlement.engine.dao.MockEntitlementDao;
import com.ning.billing.entitlement.engine.dao.MockEntitlementDaoMemory;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.events.phase.PhaseEvent;
import com.ning.billing.entitlement.events.user.ApiEvent;
import com.ning.billing.entitlement.events.user.ApiEventType;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.bus.DefaultBusService;
import com.ning.billing.util.bus.BusService;

import javax.annotation.Nullable;


public abstract class TestApiBase {
    protected static final Logger log = LoggerFactory.getLogger(TestApiBase.class);

    protected static final long DAY_IN_MS = (24 * 3600 * 1000);

    protected EntitlementService entitlementService;
    protected EntitlementUserApi entitlementApi;
    protected EntitlementBillingApi billingApi;

    protected EntitlementMigrationApi migrationApi;

    protected CatalogService catalogService;
    protected EntitlementConfig config;
    protected EntitlementDao dao;
    protected ClockMock clock;
    protected BusService busService;

    protected AccountData accountData;
    protected Catalog catalog;
    protected ApiTestListener testListener;
    protected SubscriptionBundle bundle;

    private MysqlTestingHelper helper;
    protected CallContext context = new TestCallContext("Api Test");

    public static void loadSystemPropertiesFromClasspath(final String resource) {
        final URL url = TestApiBase.class.getResource(resource);
        assertNotNull(url);

        try {
            System.getProperties().load( url.openStream() );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected abstract Injector getInjector();

    @AfterClass(alwaysRun = true)
    public void tearDown() {
        try {
            ((DefaultBusService) busService).stopBus();
            if (helper != null) {
                helper.stopMysql();
            }
        } catch (Exception e) {
            log.warn("Failed to tearDown test properly ", e);
        }
    }

    @BeforeClass(alwaysRun = true)
    public void setup() {

        loadSystemPropertiesFromClasspath("/entitlement.properties");
        final Injector g = getInjector();

        entitlementService = g.getInstance(EntitlementService.class);
        catalogService = g.getInstance(CatalogService.class);
        busService = g.getInstance(BusService.class);
        config = g.getInstance(EntitlementConfig.class);
        dao = g.getInstance(EntitlementDao.class);
        clock = (ClockMock) g.getInstance(Clock.class);
        helper = (isSqlTest(dao)) ? g.getInstance(MysqlTestingHelper.class) : null;

        try {
            ((DefaultCatalogService) catalogService).loadCatalog();
            ((DefaultBusService) busService).startBus();
            ((Engine) entitlementService).initialize();
            init();
        } catch (Exception e) {
        }
    }

    private static boolean isSqlTest(EntitlementDao theDao) {
        return (! (theDao instanceof MockEntitlementDaoMemory));
    }

    private void setupMySQL() throws IOException {
        if (helper != null) {
            final String entitlementDdl = IOUtils.toString(TestApiBase.class.getResourceAsStream("/com/ning/billing/entitlement/ddl.sql"));
            final String utilDdl = IOUtils.toString(TestApiBase.class.getResourceAsStream("/com/ning/billing/util/ddl.sql"));
            helper.startMysql();
            helper.initDb(entitlementDdl);
            helper.initDb(utilDdl);
        }
    }

    private void init() throws Exception {

        setupMySQL();

        accountData = getAccountData();
        assertNotNull(accountData);

        catalog = catalogService.getFullCatalog();
        assertNotNull(catalog);


        testListener = new ApiTestListener(busService.getBus());
        entitlementApi = entitlementService.getUserApi();
        billingApi = entitlementService.getBillingApi();
        migrationApi = entitlementService.getMigrationApi();
    }

    @BeforeMethod(alwaysRun = true)
    public void setupTest() {

        log.warn("RESET TEST FRAMEWORK\n\n");

        testListener.reset();

        clock.resetDeltaFromReality();
        ((MockEntitlementDao) dao).reset();
        try {
            busService.getBus().register(testListener);
            UUID accountId = UUID.randomUUID();
            bundle = entitlementApi.createBundleForAccount(accountId, "myDefaultBundle", context);
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
        assertNotNull(bundle);

        ((Engine)entitlementService).start();
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupTest() {
        try {
            busService.getBus().unregister(testListener);
            ((Engine)entitlementService).stop();
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
        log.warn("DONE WITH TEST\n");
    }

    protected SubscriptionData createSubscription(final String productName, final BillingPeriod term, final String planSet) throws EntitlementUserApiException {
        return createSubscriptionWithBundle(bundle.getId(), productName, term, planSet);
    }

    protected SubscriptionData createSubscriptionWithBundle(final UUID bundleId, final String productName, final BillingPeriod term, final String planSet) throws EntitlementUserApiException {
        testListener.pushExpectedEvent(NextEvent.CREATE);

        SubscriptionData subscription = (SubscriptionData) entitlementApi.createSubscription(bundleId,
                new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSet, null),
                clock.getUTCNow(), context);
        assertNotNull(subscription);
        assertTrue(testListener.isCompleted(5000));
        return subscription;
    }

    protected void checkNextPhaseChange(SubscriptionData subscription, int expPendingEvents, DateTime expPhaseChange) {

        List<EntitlementEvent> events = dao.getPendingEventsForSubscription(subscription.getId());
        assertNotNull(events);
        printEvents(events);
        assertEquals(events.size(), expPendingEvents);
        if (events.size() > 0 && expPhaseChange != null) {
            boolean foundPhase = false;
            boolean foundChange = false;

            for (EntitlementEvent cur : events) {
                if (cur instanceof PhaseEvent) {
                    assertEquals(foundPhase, false);
                    foundPhase = true;
                    assertEquals(cur.getEffectiveDate(), expPhaseChange);
                } else if (cur instanceof ApiEvent) {
                    ApiEvent uEvent = (ApiEvent) cur;
                    assertEquals(ApiEventType.CHANGE, uEvent.getEventType());
                    assertEquals(foundChange, false);
                    foundChange = true;
                } else {
                    assertFalse(true);
                }
            }
        }
    }


    protected void assertDateWithin(DateTime in, DateTime lower, DateTime upper) {
        assertTrue(in.isEqual(lower) || in.isAfter(lower));
        assertTrue(in.isEqual(upper) || in.isBefore(upper));
    }

    protected Duration getDurationDay(final int days) {
        Duration result = new Duration() {
            @Override
            public TimeUnit getUnit() {
                return TimeUnit.DAYS;
            }
            @Override
            public int getNumber() {
                return days;
            }

            @Override
            public DateTime addToDateTime(DateTime dateTime) {
                return null;
            }
        };
        return result;
    }

    protected Duration getDurationMonth(final int months) {
        Duration result = new Duration() {
            @Override
            public TimeUnit getUnit() {
                return TimeUnit.MONTHS;
            }
            @Override
            public int getNumber() {
                return months;
            }

            @Override
            public DateTime addToDateTime(DateTime dateTime) {
                return null;  //To change body of implemented methods use File | Settings | File Templates.
            }
        };
        return result;
    }


    protected Duration getDurationYear(final int years) {
        Duration result = new Duration() {
            @Override
            public TimeUnit getUnit() {
                return TimeUnit.YEARS;
            }
            @Override
            public int getNumber() {
                return years;
            }

            @Override
            public DateTime addToDateTime(DateTime dateTime) {
                return null;  //To change body of implemented methods use File | Settings | File Templates.
            }
        };
        return result;
    }

    protected AccountData getAccountData() {
        AccountData accountData = new AccountData() {
            @Override
            public String getName() {
                return "firstName lastName";
            }

            @Override
            public int getFirstNameLength() {
                return "firstName".length();
            }

            @Override
            public String getEmail() {
                return "accountName@yahoo.com";
            }

            @Override
            public String getPhone() {
                return "4152876341";
            }

            @Override
            public String getExternalKey() {
                return "k123456";
            }

            @Override
            public int getBillCycleDay() {
                return 1;
            }

            @Override
            public Currency getCurrency() {
                return Currency.USD;
            }

            @Override
            public String getPaymentProviderName() {
                return "Paypal";
            }
            @Override
            public DateTimeZone getTimeZone() {
                return DateTimeZone.forID("Europe/Paris");
            }

            @Override
            public String getLocale() {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getAddress1() {
                return null;
            }

            @Override
            public String getAddress2() {
                return null;
            }

            @Override
            public String getCompanyName() {
                return null;
            }

            @Override
            public String getCity() {
                return null;
            }

            @Override
            public String getStateOrProvince() {
                return null;
            }

            @Override
            public String getPostalCode() {
                return null;
            }

            @Override
            public String getCountry() {
                return null;
            }
        };
        return accountData;
    }

    protected PlanPhaseSpecifier getProductSpecifier(final String productName, final String priceList,
                                                     final BillingPeriod term,
                                                     @Nullable final PhaseType phaseType) {
        return new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, priceList, phaseType);
    }

    protected void printEvents(List<EntitlementEvent> events) {
        for (EntitlementEvent cur : events) {
            log.debug("Inspect event " + cur);
        }
    }

    protected void printSubscriptionTransitions(List<SubscriptionEventTransition> transitions) {
        for (SubscriptionEventTransition cur : transitions) {
            log.debug("Transition " + cur);
        }
    }

}
