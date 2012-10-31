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
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import com.ning.billing.account.api.AccountData;
import com.ning.billing.account.api.BillCycleDay;
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
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.catalog.api.TimeUnit;
import com.ning.billing.util.config.EntitlementConfig;
import com.ning.billing.entitlement.EntitlementTestSuiteWithEmbeddedDB;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApi;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApi.EntitlementAccountMigration;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApi.EntitlementBundleMigration;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApi.EntitlementSubscriptionMigration;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApi.EntitlementSubscriptionMigrationCase;
import com.ning.billing.entitlement.api.timeline.EntitlementTimelineApi;
import com.ning.billing.entitlement.api.transfer.EntitlementTransferApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.engine.core.Engine;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.entitlement.engine.dao.MockEntitlementDaoMemory;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.events.phase.PhaseEvent;
import com.ning.billing.entitlement.events.user.ApiEvent;
import com.ning.billing.entitlement.events.user.ApiEventType;
import com.ning.billing.mock.MockAccountBuilder;
import com.ning.billing.util.svcsapi.bus.BusService;
import com.ning.billing.util.bus.DefaultBusService;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.events.EffectiveSubscriptionInternalEvent;
import com.ning.billing.util.glue.RealImplementation;
import com.ning.billing.util.svcapi.entitlement.EntitlementInternalApi;

import com.google.inject.Injector;
import com.google.inject.Key;

public abstract class TestApiBase extends EntitlementTestSuiteWithEmbeddedDB implements TestListenerStatus {

    protected static final Logger log = LoggerFactory.getLogger(TestApiBase.class);

    protected EntitlementService entitlementService;
    protected EntitlementUserApi entitlementApi;
    protected EntitlementInternalApi entitlementInternalApi;
    protected EntitlementTransferApi transferApi;

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
            System.getProperties().load(url.openStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected abstract Injector getInjector();

    @AfterClass(alwaysRun = true)
    public void tearDown() {
        try {
            ((DefaultBusService) busService).stopBus();
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
        entitlementApi = g.getInstance(Key.get(EntitlementUserApi.class, RealImplementation.class));
        entitlementInternalApi = g.getInstance(EntitlementInternalApi.class);
        migrationApi = g.getInstance(EntitlementMigrationApi.class);
        repairApi = g.getInstance(EntitlementTimelineApi.class);
        transferApi = g.getInstance(EntitlementTransferApi.class);
        catalogService = g.getInstance(CatalogService.class);
        busService = g.getInstance(BusService.class);
        config = g.getInstance(EntitlementConfig.class);
        dao = g.getInstance(EntitlementDao.class);
        clock = (ClockMock) g.getInstance(Clock.class);
        init();
    }

    private void init() throws Exception {
        ((DefaultCatalogService) catalogService).loadCatalog();

        final BillCycleDay billCycleDay = Mockito.mock(BillCycleDay.class);
        Mockito.when(billCycleDay.getDayOfMonthUTC()).thenReturn(1);
        accountData = new MockAccountBuilder().name(UUID.randomUUID().toString())
                                              .firstNameLength(6)
                                              .email(UUID.randomUUID().toString())
                                              .phone(UUID.randomUUID().toString())
                                              .migrated(false)
                                              .isNotifiedForInvoices(false)
                                              .externalKey(UUID.randomUUID().toString())
                                              .billingCycleDay(billCycleDay)
                                              .currency(Currency.USD)
                                              .paymentMethodId(UUID.randomUUID())
                                              .timeZone(DateTimeZone.forID("Europe/Paris"))
                                              .build();

        assertNotNull(accountData);
        catalog = catalogService.getFullCatalog();
        assertNotNull(catalog);
        testListener = new TestApiListener(this);
    }

    private static boolean isSqlTest(final EntitlementDao theDao) {
        return (!(theDao instanceof MockEntitlementDaoMemory));
    }

    @BeforeMethod(alwaysRun = true)
    public void setupTest() throws Exception {
        log.warn("RESET TEST FRAMEWORK");

        // CLEANUP ALL DB TABLES OR IN MEMORY STRUCTURES
        if (!isSqlTest(dao)) {
            // The MySQL testing helper will clean the tables between each test
            ((MockEntitlementDaoMemory) dao).reset();
        }

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
        ((Engine) entitlementService).start();

        // SETUP START DATE
        clock.setDeltaFromReality(testStartDate.getMillis() - clock.getUTCNow().getMillis());

        // CREATE NEW BUNDLE FOR TEST
        final UUID accountId = UUID.randomUUID();
        bundle = entitlementApi.createBundleForAccount(accountId, "myDefaultBundle", callContext);
        assertNotNull(bundle);
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupTest() throws Exception {
        // UNREGISTER TEST LISTENER AND STOP BUS
        busService.getBus().unregister(testListener);
        busService.getBus().stop();

        // STOP NOTIFICATION QUEUE
        ((Engine) entitlementService).stop();

        log.warn("DONE WITH TEST");
    }

    protected void assertListenerStatus() {
        if (isListenerFailed) {
            log.error(listenerFailedMsg);
            Assert.fail(listenerFailedMsg);
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
        final SubscriptionData subscription = (SubscriptionData) entitlementApi.createSubscription(bundleId,
                                                                                                   new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSet, null),
                                                                                                   requestedDate == null ? clock.getUTCNow() : requestedDate, callContext);
        assertNotNull(subscription);
        assertTrue(testListener.isCompleted(5000));
        return subscription;
    }

    protected void checkNextPhaseChange(final SubscriptionData subscription, final int expPendingEvents, final DateTime expPhaseChange) {
        final List<EntitlementEvent> events = dao.getPendingEventsForSubscription(subscription.getId(), internalCallContext);
        assertNotNull(events);
        printEvents(events);
        assertEquals(events.size(), expPendingEvents);
        if (events.size() > 0 && expPhaseChange != null) {
            boolean foundPhase = false;
            boolean foundChange = false;

            for (final EntitlementEvent cur : events) {
                if (cur instanceof PhaseEvent) {
                    assertEquals(foundPhase, false);
                    foundPhase = true;
                    assertEquals(cur.getEffectiveDate(), expPhaseChange);
                } else if (cur instanceof ApiEvent) {
                    final ApiEvent uEvent = (ApiEvent) cur;
                    assertEquals(ApiEventType.CHANGE, uEvent.getEventType());
                    assertEquals(foundChange, false);
                    foundChange = true;
                } else {
                    assertFalse(true);
                }
            }
        }
    }

    protected void assertDateWithin(final DateTime in, final DateTime lower, final DateTime upper) {
        assertTrue(in.isEqual(lower) || in.isAfter(lower));
        assertTrue(in.isEqual(upper) || in.isBefore(upper));
    }

    protected Duration getDurationDay(final int days) {
        final Duration result = new Duration() {
            @Override
            public TimeUnit getUnit() {
                return TimeUnit.DAYS;
            }

            @Override
            public int getNumber() {
                return days;
            }

            @Override
            public DateTime addToDateTime(final DateTime dateTime) {
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
        final Duration result = new Duration() {
            @Override
            public TimeUnit getUnit() {
                return TimeUnit.MONTHS;
            }

            @Override
            public int getNumber() {
                return months;
            }

            @Override
            public DateTime addToDateTime(final DateTime dateTime) {
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
        final Duration result = new Duration() {
            @Override
            public TimeUnit getUnit() {
                return TimeUnit.YEARS;
            }

            @Override
            public int getNumber() {
                return years;
            }

            @Override
            public DateTime addToDateTime(final DateTime dateTime) {
                return dateTime.plusYears(years);
            }

            @Override
            public Period toJodaPeriod() {
                throw new UnsupportedOperationException();
            }
        };
        return result;
    }

    protected PlanPhaseSpecifier getProductSpecifier(final String productName, final String priceList,
                                                     final BillingPeriod term,
                                                     @Nullable final PhaseType phaseType) {
        return new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, priceList, phaseType);
    }

    protected void printEvents(final List<EntitlementEvent> events) {
        for (final EntitlementEvent cur : events) {
            log.debug("Inspect event " + cur);
        }
    }

    protected void printSubscriptionTransitions(final List<EffectiveSubscriptionInternalEvent> transitions) {
        for (final EffectiveSubscriptionInternalEvent cur : transitions) {
            log.debug("Transition " + cur);
        }
    }

    /**
     * ***********************************************************
     * Utilities for migration tests
     * *************************************************************
     */

    protected EntitlementAccountMigration createAccountForMigrationTest(final List<List<EntitlementSubscriptionMigrationCaseWithCTD>> cases) {
        return new EntitlementAccountMigration() {
            private final UUID accountId = UUID.randomUUID();

            @Override
            public EntitlementBundleMigration[] getBundles() {
                final List<EntitlementBundleMigration> bundles = new ArrayList<EntitlementBundleMigration>();
                final EntitlementBundleMigration bundle0 = new EntitlementBundleMigration() {
                    @Override
                    public EntitlementSubscriptionMigration[] getSubscriptions() {
                        final EntitlementSubscriptionMigration[] result = new EntitlementSubscriptionMigration[cases.size()];

                        for (int i = 0; i < cases.size(); i++) {
                            final List<EntitlementSubscriptionMigrationCaseWithCTD> curCases = cases.get(i);
                            final EntitlementSubscriptionMigration subscription = new EntitlementSubscriptionMigration() {
                                @Override
                                public EntitlementSubscriptionMigrationCaseWithCTD[] getSubscriptionCases() {
                                    return curCases.toArray(new EntitlementSubscriptionMigrationCaseWithCTD[curCases.size()]);
                                }

                                @Override
                                public ProductCategory getCategory() {
                                    return curCases.get(0).getPlanPhaseSpecifier().getProductCategory();
                                }

                                @Override
                                public DateTime getChargedThroughDate() {
                                    for (final EntitlementSubscriptionMigrationCaseWithCTD cur : curCases) {
                                        if (cur.getChargedThroughDate() != null) {
                                            return cur.getChargedThroughDate();
                                        }
                                    }
                                    return null;
                                }
                            };
                            result[i] = subscription;
                        }
                        return result;
                    }

                    @Override
                    public String getBundleKey() {
                        return "12345";
                    }
                };
                bundles.add(bundle0);
                return bundles.toArray(new EntitlementBundleMigration[bundles.size()]);
            }

            @Override
            public UUID getAccountKey() {
                return accountId;
            }
        };
    }

    protected EntitlementAccountMigration createAccountForMigrationWithRegularBasePlanAndAddons(final DateTime initialBPstart, final DateTime initalAddonStart) {

        final List<EntitlementSubscriptionMigrationCaseWithCTD> cases = new LinkedList<EntitlementSubscriptionMigrationCaseWithCTD>();
        cases.add(new EntitlementSubscriptionMigrationCaseWithCTD(
                new PlanPhaseSpecifier("Shotgun", ProductCategory.BASE, BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN),
                initialBPstart,
                null,
                initialBPstart.plusYears(1)));

        final List<EntitlementSubscriptionMigrationCaseWithCTD> firstAddOnCases = new LinkedList<EntitlementSubscriptionMigrationCaseWithCTD>();
        firstAddOnCases.add(new EntitlementSubscriptionMigrationCaseWithCTD(
                new PlanPhaseSpecifier("Telescopic-Scope", ProductCategory.ADD_ON, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.DISCOUNT),
                initalAddonStart,
                initalAddonStart.plusMonths(1),
                initalAddonStart.plusMonths(1)));
        firstAddOnCases.add(new EntitlementSubscriptionMigrationCaseWithCTD(
                new PlanPhaseSpecifier("Telescopic-Scope", ProductCategory.ADD_ON, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN),
                initalAddonStart.plusMonths(1),
                null,
                null));

        final List<List<EntitlementSubscriptionMigrationCaseWithCTD>> input = new ArrayList<List<EntitlementSubscriptionMigrationCaseWithCTD>>();
        input.add(cases);
        input.add(firstAddOnCases);
        return createAccountForMigrationTest(input);
    }

    protected EntitlementAccountMigration createAccountForMigrationWithRegularBasePlan(final DateTime startDate) {
        final List<EntitlementSubscriptionMigrationCaseWithCTD> cases = new LinkedList<EntitlementSubscriptionMigrationCaseWithCTD>();
        cases.add(new EntitlementSubscriptionMigrationCaseWithCTD(
                new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN),
                startDate,
                null,
                startDate.plusYears(1)));
        final List<List<EntitlementSubscriptionMigrationCaseWithCTD>> input = new ArrayList<List<EntitlementSubscriptionMigrationCaseWithCTD>>();
        input.add(cases);
        return createAccountForMigrationTest(input);
    }

    protected EntitlementAccountMigration createAccountForMigrationWithRegularBasePlanFutreCancelled(final DateTime startDate) {
        final List<EntitlementSubscriptionMigrationCaseWithCTD> cases = new LinkedList<EntitlementSubscriptionMigrationCaseWithCTD>();
        cases.add(new EntitlementSubscriptionMigrationCaseWithCTD(
                new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN),
                startDate,
                startDate.plusYears(1),
                startDate.plusYears(1)));
        final List<List<EntitlementSubscriptionMigrationCaseWithCTD>> input = new ArrayList<List<EntitlementSubscriptionMigrationCaseWithCTD>>();
        input.add(cases);
        return createAccountForMigrationTest(input);
    }

    protected EntitlementAccountMigration createAccountForMigrationFuturePendingPhase(final DateTime trialDate) {
        final List<EntitlementSubscriptionMigrationCaseWithCTD> cases = new LinkedList<EntitlementSubscriptionMigrationCaseWithCTD>();
        cases.add(new EntitlementSubscriptionMigrationCaseWithCTD(
                new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.TRIAL),
                trialDate,
                trialDate.plusDays(30),
                trialDate.plusDays(30)));
        cases.add(new EntitlementSubscriptionMigrationCaseWithCTD(
                new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN),
                trialDate.plusDays(30),
                null,
                null));
        final List<List<EntitlementSubscriptionMigrationCaseWithCTD>> input = new ArrayList<List<EntitlementSubscriptionMigrationCaseWithCTD>>();
        input.add(cases);
        return createAccountForMigrationTest(input);
    }

    protected EntitlementAccountMigration createAccountForMigrationFuturePendingChange() {
        final List<EntitlementSubscriptionMigrationCaseWithCTD> cases = new LinkedList<EntitlementSubscriptionMigrationCaseWithCTD>();
        final DateTime effectiveDate = clock.getUTCNow().minusDays(10);
        cases.add(new EntitlementSubscriptionMigrationCaseWithCTD(
                new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN),
                effectiveDate,
                effectiveDate.plusMonths(1),
                effectiveDate.plusMonths(1)));
        cases.add(new EntitlementSubscriptionMigrationCaseWithCTD(
                new PlanPhaseSpecifier("Shotgun", ProductCategory.BASE, BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN),
                effectiveDate.plusMonths(1).plusDays(1),
                null,
                null));
        final List<List<EntitlementSubscriptionMigrationCaseWithCTD>> input = new ArrayList<List<EntitlementSubscriptionMigrationCaseWithCTD>>();
        input.add(cases);
        return createAccountForMigrationTest(input);
    }

    public static class EntitlementSubscriptionMigrationCaseWithCTD implements EntitlementSubscriptionMigrationCase {

        private final PlanPhaseSpecifier pps;
        private final DateTime effDt;
        private final DateTime cancelDt;
        private final DateTime ctd;

        public EntitlementSubscriptionMigrationCaseWithCTD(final PlanPhaseSpecifier pps, final DateTime effDt, final DateTime cancelDt, final DateTime ctd) {
            this.pps = pps;
            this.cancelDt = cancelDt;
            this.effDt = effDt;
            this.ctd = ctd;
        }

        @Override
        public PlanPhaseSpecifier getPlanPhaseSpecifier() {
            return pps;
        }

        @Override
        public DateTime getEffectiveDate() {
            return effDt;
        }

        @Override
        public DateTime getCancelledDate() {
            return cancelDt;
        }

        public DateTime getChargedThroughDate() {
            return ctd;
        }
    }
}
