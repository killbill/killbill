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

package com.ning.billing.beatrix.integration;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;

import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.util.clock.MockClockModule;
import com.sun.org.apache.bcel.internal.generic.NEW;
import org.apache.commons.lang.RandomStringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Days;
import org.joda.time.Interval;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;


import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountData;
import com.ning.billing.account.api.AccountService;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.beatrix.integration.TestBusHandler.NextEvent;
import com.ning.billing.beatrix.lifecycle.Lifecycle;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.EntitlementService;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.invoice.api.InvoiceService;
import com.ning.billing.invoice.api.InvoiceUserApi;

import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.bus.BusService;

@Guice(modules = {MockModule.class})
public class TestBasic {

    private static final Logger log = LoggerFactory.getLogger(TestBasic.class);
    private static long AT_LEAST_ONE_MONTH_MS =  31L * 24L * 3600L * 1000L;

    @Inject IDBI dbi;

    @Inject
    private ClockMock clock;

    @Inject
    private Lifecycle lifecycle;

    @Inject
    private BusService busService;

    @Inject
    private EntitlementService entitlementService;

    @Inject
    private InvoiceService invoiceService;

    @Inject
    private AccountService accountService;

    private EntitlementUserApi entitlementUserApi;

    private InvoiceUserApi invoiceUserApi;

    private AccountUserApi accountUserApi;

    private TestBusHandler busHandler;



    @BeforeSuite(alwaysRun = true)
    public void setup() throws Exception{

        /**
         * Initialize lifecyle for subset of services
         */
        busHandler = new TestBusHandler();
        lifecycle.fireStartupSequencePriorEventRegistration();
        busService.getBus().register(busHandler);
        lifecycle.fireStartupSequencePostEventRegistration();

        /**
         * Retrieve APIs
         */
        entitlementUserApi = entitlementService.getUserApi();
        invoiceUserApi = invoiceService.getUserApi();
        accountUserApi = accountService.getAccountUserApi();
    }

    @AfterSuite(alwaysRun = true)
    public void tearDown() throws Exception {
        lifecycle.fireShutdownSequencePriorEventUnRegistration();
        busService.getBus().unregister(busHandler);
        lifecycle.fireShutdownSequencePostEventUnRegistration();
    }


    @BeforeMethod(alwaysRun = true)
    public void setupTest() {

        log.warn("\n");
        log.warn("RESET TEST FRAMEWORK\n\n");
        busHandler.reset();
        clock.resetDeltaFromReality();
        cleanupData();
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupTest() {
        log.warn("DONE WITH TEST\n");
    }

    private void cleanupData() {
        dbi.inTransaction(new TransactionCallback<Void>() {
            @Override
            public Void inTransaction(Handle h, TransactionStatus status)
                    throws Exception {
                h.execute("truncate table accounts");
                h.execute("truncate table events");
                h.execute("truncate table subscriptions");
                h.execute("truncate table bundles");
                h.execute("truncate table notifications");
                h.execute("truncate table claimed_notifications");
                h.execute("truncate table invoices");
                h.execute("truncate table fixed_invoice_items");
                h.execute("truncate table recurring_invoice_items");
                h.execute("truncate table tag_definitions");
                h.execute("truncate table tags");
                h.execute("truncate table custom_fields");
                h.execute("truncate table invoice_payments");
                h.execute("truncate table payment_attempts");
                h.execute("truncate table payments");
                return null;
            }
        });
    }

    private void verifyTestResult(UUID accountId, UUID subscriptionId, InvoiceTestResult expectedResult) {
        SubscriptionData subscription = (SubscriptionData) entitlementUserApi.getSubscriptionFromId(subscriptionId);

        List<InvoiceItem> invoiceItems = invoiceUserApi.getInvoiceItemsByAccount(accountId);
        Iterator<InvoiceItem> invoiceItemIterator = invoiceItems.iterator();
        while (invoiceItemIterator.hasNext()) {
            InvoiceItem item = invoiceItemIterator.next();

            for (InvoiceItemData data : expectedResult.getItems()) {
                if (item.getStartDate().compareTo(data.getStartDate()) == 0) {
                    if (item.getEndDate().compareTo(data.getEndDate()) == 0) {
                        if (item.getAmount().compareTo(data.getAmount()) == 0) {
                            invoiceItemIterator.remove();
                        }
                    }
                }
            }
        }

        assertEquals(invoiceItems.size(), 0);

        DateTime ctd = subscription.getChargedThroughDate();
        assertNotNull(ctd);
        log.info("Checking CTD: " + ctd.toString() + "; clock is " + clock.getUTCNow().toString());
        assertTrue(clock.getUTCNow().isBefore(ctd));
        assertTrue(ctd.compareTo(expectedResult.getChargeThroughDate()) == 0);
    }

    @Test(groups = "fast", enabled = false)
    public void testBasePlanCompleteWithBillingDayInPast() throws Exception {
        List<InvoiceTestResult> expectedTestResults = new ArrayList<InvoiceTestResult>();
        DateTime startDate = new DateTime(2012, 2, 1, 0, 3, 42, 0);
        testBasePlanComplete(startDate, 31, expectedTestResults);
    }

    @Test(groups = "fast", enabled = false)
    public void testBasePlanCompleteWithBillingDayPresent() throws Exception {
        List<InvoiceTestResult> expectedTestResults = new ArrayList<InvoiceTestResult>();
        DateTime startDate = new DateTime(2012, 2, 1, 0, 3, 42, 0);
        testBasePlanComplete(startDate, 1, expectedTestResults);
    }

    @Test(groups = "fast", enabled = false)
    public void testBasePlanCompleteWithBillingDayAlignedWithTrial() throws Exception {
        List<InvoiceTestResult> expectedTestResults = new ArrayList<InvoiceTestResult>();
        DateTime startDate = new DateTime(2012, 2, 1, 0, 3, 42, 0);
        testBasePlanComplete(startDate, 2, expectedTestResults);
    }

    @Test(groups = "fast", enabled = true)
    public void testBasePlanCompleteWithBillingDayInFuture() throws Exception {
        List<InvoiceTestResult> expectedTestResults = new ArrayList<InvoiceTestResult>();
        DateTime startDate = new DateTime(2012, 2, 1, 0, 3, 42, 0);

        DateTime firstCTD = new DateTime(2012, 3, 2, 0, 3, 42, 0);
        List<InvoiceItemData> firstItemList = new ArrayList<InvoiceItemData>();
        firstItemList.add(new InvoiceItemData(BigDecimal.ZERO, startDate,firstCTD));
        expectedTestResults.add(new InvoiceTestResult(firstCTD, firstItemList));

        testBasePlanComplete(startDate, 3, expectedTestResults);
    }

    private void waitForDebug() throws Exception {
        Thread.sleep(600000);
    }

    @Test(groups = "stress", enabled = false)
    public void stressTest() throws Exception {
        final int maxIterations = 3;
        int curIteration = maxIterations;
        for (curIteration = 0; curIteration < maxIterations; curIteration++) {
            log.info("################################  ITERATION " + curIteration + "  #########################");
            setupTest();
            Thread.sleep(1000);
            testBasePlanCompleteWithBillingDayPresent();
        }
    }

    private void testBasePlanComplete(DateTime initialCreationDate, int billingDay, List<InvoiceTestResult> expectedResults) throws Exception {
        long DELAY = 5000;

        Account account = accountUserApi.createAccount(getAccountData(billingDay), null, null);
        UUID accountId = account.getId();
        assertNotNull(account);

        // set clock to the initial start date
        clock.setDeltaFromReality(initialCreationDate.getMillis() - DateTime.now().getMillis());
        SubscriptionBundle bundle = entitlementUserApi.createBundleForAccount(account.getId(), "whatever");

        String productName = "Shotgun";
        BillingPeriod term = BillingPeriod.MONTHLY;
        String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

        Iterator<InvoiceTestResult> testItemIterator = expectedResults.iterator();

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE NextEvent.INVOICE
        //
        busHandler.pushExpectedEvent(NextEvent.CREATE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        SubscriptionData subscription = (SubscriptionData) entitlementUserApi.createSubscription(bundle.getId(),
                new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSetName, null), null);
        assertNotNull(subscription);

        assertTrue(busHandler.isCompleted(DELAY));
        log.info("testSimple passed first busHandler checkpoint.");

        //
        // VERIFY CTD HAS BEEN SET
        //

        verifyTestResult(accountId, subscription.getId(), testItemIterator.next());

        //
        // CHANGE PLAN IMMEDIATELY AND EXPECT BOTH EVENTS: NextEvent.CHANGE NextEvent.INVOICE
        //
        busHandler.pushExpectedEvent(NextEvent.CHANGE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);

        BillingPeriod newTerm = BillingPeriod.MONTHLY;
        String newPlanSetName = PriceListSet.DEFAULT_PRICELIST_NAME;
        String newProductName = "Assault-Rifle";
        subscription.changePlan(newProductName, newTerm, newPlanSetName, clock.getUTCNow());

        assertTrue(busHandler.isCompleted(DELAY));
        log.info("testSimple passed second busHandler checkpoint.");

        //
        // VERIFY AGAIN CTD HAS BEEN SET
        //
        verifyTestResult(accountId, subscription.getId(), testItemIterator.next());

        //
        // MOVE TIME TO AFTER TRIAL AND EXPECT BOTH EVENTS :  NextEvent.PHASE NextEvent.INVOICE
        //
        busHandler.pushExpectedEvent(NextEvent.PHASE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        clock.setDeltaFromReality(AT_LEAST_ONE_MONTH_MS);

        assertTrue(busHandler.isCompleted(DELAY));

        //
        // CHANGE PLAN EOT AND EXPECT NOTHING
        //
        newTerm = BillingPeriod.MONTHLY;
        newPlanSetName = PriceListSet.DEFAULT_PRICELIST_NAME;
        newProductName = "Pistol";
        subscription = (SubscriptionData) entitlementUserApi.getSubscriptionFromId(subscription.getId());
        subscription.changePlan(newProductName, newTerm, newPlanSetName, clock.getUTCNow());
        log.info("testSimple has passed third busHandler checkpoint (no events)");

        //
        // MOVE TIME AFTER CTD AND EXPECT BOTH EVENTS : NextEvent.CHANGE NextEvent.INVOICE
        //
        busHandler.pushExpectedEvent(NextEvent.CHANGE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        //clock.addDeltaFromReality(ctd.getMillis() - clock.getUTCNow().getMillis());
        clock.addDeltaFromReality(AT_LEAST_ONE_MONTH_MS + 1000);

        //waitForDebug();

        assertTrue(busHandler.isCompleted(DELAY));
        log.info("testSimple passed fourth busHandler checkpoint.");

        //
        // MOVE TIME AFTER NEXT BILL CYCLE DAY AND EXPECT EVENT : NextEvent.INVOICE
        //
        int maxCycles = 3;
        do {
            busHandler.pushExpectedEvent(NextEvent.INVOICE);
            busHandler.pushExpectedEvent(NextEvent.PAYMENT);
            clock.addDeltaFromReality(AT_LEAST_ONE_MONTH_MS + 1000);
            assertTrue(busHandler.isCompleted(DELAY));
            verifyTestResult(accountId, subscription.getId(), testItemIterator.next());
        } while (maxCycles-- > 0);

        //
        // FINALLY CANCEL SUBSCRIPTION EOT
        //
        subscription = (SubscriptionData) entitlementUserApi.getSubscriptionFromId(subscription.getId());
        subscription.cancel(clock.getUTCNow(), false);

//        // MOVE AFTER CANCEL DATE AND EXPECT EVENT : NextEvent.CANCEL
//        busHandler.pushExpectedEvent(NextEvent.CANCEL);
//        Interval it = new Interval(clock.getUTCNow(), lastCtd);
//        clock.addDeltaFromReality(it.toDurationMillis());
//        assertTrue(busHandler.isCompleted(DELAY));
//
//        //
//        // CHECK AGAIN THERE IS NO MORE INVOICES GENERATED
//        //
//        busHandler.reset();
//        clock.addDeltaFromReality(AT_LEAST_ONE_MONTH_MS + 1000);
//        assertTrue(busHandler.isCompleted(DELAY));
//
//
//        subscription = (SubscriptionData) entitlementUserApi.getSubscriptionFromId(subscription.getId());
//        lastCtd = subscription.getChargedThroughDate();
//        assertNotNull(lastCtd);
//        log.info("Checking CTD: " + lastCtd.toString() + "; clock is " + clock.getUTCNow().toString());
//        assertTrue(lastCtd.isBefore(clock.getUTCNow()));
    }

    @Test(enabled=false)
    public void testHappyPath() throws AccountApiException, EntitlementUserApiException {
        long DELAY = 5000 * 10;

        Account account = accountUserApi.createAccount(getAccountData(3), null, null);
        assertNotNull(account);

        SubscriptionBundle bundle = entitlementUserApi.createBundleForAccount(account.getId(), "whatever");

        String productName = "Shotgun";
        BillingPeriod term = BillingPeriod.MONTHLY;
        String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

        busHandler.pushExpectedEvent(NextEvent.CREATE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        SubscriptionData subscription = (SubscriptionData) entitlementUserApi.createSubscription(bundle.getId(),
                new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSetName, null), null);
        assertNotNull(subscription);

        assertTrue(busHandler.isCompleted(DELAY));

        busHandler.pushExpectedEvent(NextEvent.CHANGE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        BillingPeriod newTerm = BillingPeriod.MONTHLY;
        String newPlanSetName = PriceListSet.DEFAULT_PRICELIST_NAME;
        String newProductName = "Assault-Rifle";
        subscription.changePlan(newProductName, newTerm, newPlanSetName, clock.getUTCNow());

        assertTrue(busHandler.isCompleted(DELAY));

        busHandler.pushExpectedEvent(NextEvent.PHASE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        clock.setDeltaFromReality(AT_LEAST_ONE_MONTH_MS);
        assertTrue(busHandler.isCompleted(DELAY));

    }

    protected AccountData getAccountData(final int billingDay) {

        final String someRandomKey = RandomStringUtils.randomAlphanumeric(10);
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
                return  someRandomKey + "@laposte.fr";
            }
            @Override
            public String getPhone() {
                return "4152876341";
            }
            @Override
            public String getExternalKey() {
                return someRandomKey;
            }
            @Override
            public int getBillCycleDay() {
                return billingDay;
            }
            @Override
            public Currency getCurrency() {
                return Currency.USD;
            }
            @Override
            public String getPaymentProviderName() {
                return MockModule.PLUGIN_NAME;
            }

            @Override
            public DateTimeZone getTimeZone() {
                return null;
            }

            @Override
            public String getLocale() {
                return null;
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

    private class InvoiceTestResult {
        private final DateTime chargeThroughDate;
        private final List<InvoiceItemData> items;

        private InvoiceTestResult(DateTime chargeThroughDate, List<InvoiceItemData> items) {
            this.chargeThroughDate = chargeThroughDate;
            this.items = items;
        }

        public DateTime getChargeThroughDate() {
            return chargeThroughDate;
        }

        public List<InvoiceItemData> getItems() {
            return items;
        }
    }

    private class InvoiceItemData {
        private final BigDecimal amount;
        private final DateTime startDate;
        private final DateTime endDate;

        private InvoiceItemData(BigDecimal amount, DateTime startDate, DateTime endDate) {
            this.amount = amount;
            this.startDate = startDate;
            this.endDate = endDate;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public DateTime getStartDate() {
            return startDate;
        }

        public DateTime getEndDate() {
            return endDate;
        }
    }
}
