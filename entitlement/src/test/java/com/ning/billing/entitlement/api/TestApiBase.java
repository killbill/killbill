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

import javax.annotation.Nullable;

import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.ning.billing.account.api.AccountData;
import com.ning.billing.api.TestApiListener;
import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.api.TestListenerStatus;
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
import com.ning.billing.entitlement.api.billing.ChargeThruApi;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApi;
import com.ning.billing.entitlement.api.timeline.EntitlementTimelineApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.api.user.SubscriptionEvent;
import com.ning.billing.entitlement.engine.core.Engine;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.entitlement.engine.dao.MockEntitlementDao;
import com.ning.billing.entitlement.engine.dao.MockEntitlementDaoMemory;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.events.phase.PhaseEvent;
import com.ning.billing.entitlement.events.user.ApiEvent;
import com.ning.billing.entitlement.events.user.ApiEventType;
import com.ning.billing.util.bus.BusService;
import com.ning.billing.util.bus.DefaultBusService;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.TestCallContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.glue.RealImplementation;


public abstract class TestApiBase implements TestListenerStatus {
    
    protected static final Logger log = LoggerFactory.getLogger(TestApiBase.class);

    protected static final long DAY_IN_MS = (24 * 3600 * 1000);

    protected EntitlementService entitlementService;
    protected EntitlementUserApi entitlementApi;
    protected ChargeThruApi billingApi;

    protected EntitlementMigrationApi migrationApi;
    protected EntitlementTimelineApi repairApi;

    protected CatalogService catalogService;
    protected EntitlementConfig config;
    protected EntitlementDao dao;
    protected ClockMock clock;
    protected BusService busService;

    protected AccountData accountData;
    protected Catalog catalog;
    protected TestApiListener testListener;
    protected SubscriptionBundle bundle;

    private MysqlTestingHelper helper;
    protected CallContext context = new TestCallContext("Api Test");

    private boolean isListenerFailed;
    private String listenerFailedMsg;    

    //
    // The date on which we make our test start; just to ensure that running tests at different dates does not
    // produce different results. nothing specific about that date; we could change it to anything.
    //
    protected DateTime testStartDate = new DateTime(2012, 5, 7, 0, 3, 42, 0);


    
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

    
    @Override
    public void failed(final String msg) {
        this.isListenerFailed = true;
        this.listenerFailedMsg = msg;
    }

    @Override
    public void resetTestListenerStatus() {
        this.isListenerFailed = false;
        this.listenerFailedMsg = null;
    }
    
    @BeforeClass(alwaysRun = true)
    public void setup() throws Exception {

        loadSystemPropertiesFromClasspath("/entitlement.properties");
        final Injector g = getInjector();

        entitlementService = g.getInstance(EntitlementService.class);
        EntitlementUserApi entApi = (EntitlementUserApi)g.getInstance(Key.get(EntitlementUserApi.class, RealImplementation.class));
        entitlementApi = entApi;
        billingApi = g.getInstance(ChargeThruApi.class);
        migrationApi = g.getInstance(EntitlementMigrationApi.class);
        repairApi = g.getInstance(EntitlementTimelineApi.class);
        catalogService = g.getInstance(CatalogService.class);
        busService = g.getInstance(BusService.class);
        config = g.getInstance(EntitlementConfig.class);
        dao = g.getInstance(EntitlementDao.class);
        clock = (ClockMock) g.getInstance(Clock.class);
        helper = (isSqlTest(dao)) ? g.getInstance(MysqlTestingHelper.class) : null;
        init();
    }

    private void init() throws Exception {

        setupDao();

        ((DefaultCatalogService) catalogService).loadCatalog();        

        accountData = getAccountData();
        assertNotNull(accountData);
        catalog = catalogService.getFullCatalog();
        assertNotNull(catalog);
        testListener = new TestApiListener(this);
    }

    private void setupDao() throws IOException {
        if (helper != null) {
            final String entitlementDdl = IOUtils.toString(TestApiBase.class.getResourceAsStream("/com/ning/billing/entitlement/ddl.sql"));
            final String utilDdl = IOUtils.toString(TestApiBase.class.getResourceAsStream("/com/ning/billing/util/ddl.sql"));
            helper.startMysql();
            helper.initDb(entitlementDdl);
            helper.initDb(utilDdl);
        }
    }

    private static boolean isSqlTest(EntitlementDao theDao) {
        return (! (theDao instanceof MockEntitlementDaoMemory));
    }
   
    @BeforeMethod(alwaysRun = true)
    public void setupTest() throws Exception {

        log.warn("RESET TEST FRAMEWORK\n\n");

        // CLEANUP ALL DB TABLES OR IN MEMORY STRUCTURES
        cleanupDao();
        
        // RESET LIST OF EXPECTED EVENTS
        if (testListener != null) {
            testListener.reset();
            resetTestListenerStatus();
        }
        
        // RESET CLOCK
        clock.resetDeltaFromReality();

        // START BUS AND REGISTER LISTENER
        busService.getBus().start();
        busService.getBus().register(testListener);
        
        // START NOTIFICATION QUEUE FOR ENTITLEMENT
        ((Engine) entitlementService).initialize();
        ((Engine)entitlementService).start();
        
        // SETUP START DATE
        clock.setDeltaFromReality(testStartDate.getMillis() - clock.getUTCNow().getMillis());
        
        // CREATE NEW BUNDLE FOR TEST
        UUID accountId = UUID.randomUUID();
        bundle = entitlementApi.createBundleForAccount(accountId, "myDefaultBundle", context);
        assertNotNull(bundle);
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupTest() throws Exception {
        
        // UNREGISTER TEST LISTENER AND STOP BUS
        busService.getBus().unregister(testListener);
        busService.getBus().stop();
        
        // STOP NOTIFICATION QUEUE
        ((Engine)entitlementService).stop();

        log.warn("DONE WITH TEST\n");
    }
    
    protected void assertListenerStatus() {
        if (isListenerFailed) {
            log.error(listenerFailedMsg);
            Assert.fail(listenerFailedMsg);
        }
    }
    
    private void cleanupDao() {
        if (helper != null) {
            helper.cleanupAllTables();
        } else {
            ((MockEntitlementDao) dao).reset();
        }
    }

    protected SubscriptionData createSubscription(final String productName, final BillingPeriod term, final String planSet, final DateTime requestedDate)
        throws EntitlementUserApiException {
        return createSubscriptionWithBundle(bundle.getId(), productName, term, planSet, requestedDate);
    }
    protected SubscriptionData createSubscription(final String productName, final BillingPeriod term, final String planSet)
    throws EntitlementUserApiException {
        return createSubscriptionWithBundle(bundle.getId(), productName, term, planSet, null);
    }

    protected SubscriptionData createSubscriptionWithBundle(final UUID bundleId, final String productName, final BillingPeriod term, final String planSet, final DateTime requestedDate)
        throws EntitlementUserApiException {
        
        testListener.pushExpectedEvent(NextEvent.CREATE);
        SubscriptionData subscription = (SubscriptionData) entitlementApi.createSubscription(bundleId,
                new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSet, null),
                requestedDate == null ? clock.getUTCNow() : requestedDate, context);
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
            @Override
            public Period toJodaPeriod() {
                throw new UnsupportedOperationException();
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
            @Override
            public Period toJodaPeriod() {
                throw new UnsupportedOperationException();
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
                return dateTime.plusYears(years);  
            }
            @Override
            public Period toJodaPeriod() {
                throw new UnsupportedOperationException();
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
            public boolean isMigrated() {
                return false;
            }

            @Override
            public boolean isNotifiedForInvoices() {
                return false;
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
            public UUID getPaymentMethodId() {
                return UUID.randomUUID();
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

    protected void printSubscriptionTransitions(List<SubscriptionEvent> transitions) {
        for (SubscriptionEvent cur : transitions) {
            log.debug("Transition " + cur);
        }
    }

}
