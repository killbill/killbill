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

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;

import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
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
public class TestIntegration {
    private static final int NUMBER_OF_DECIMALS = 4;
    private static final int ROUNDING_METHOD = BigDecimal.ROUND_HALF_EVEN;

    private static final BigDecimal ONE = new BigDecimal("1.0000").setScale(NUMBER_OF_DECIMALS);
    private static final BigDecimal TWENTY_NINE = new BigDecimal("29.0000").setScale(NUMBER_OF_DECIMALS);
    private static final BigDecimal THIRTY = new BigDecimal("30.0000").setScale(NUMBER_OF_DECIMALS);
    private static final BigDecimal THIRTY_ONE = new BigDecimal("31.0000").setScale(NUMBER_OF_DECIMALS);

    private static final Logger log = LoggerFactory.getLogger(TestIntegration.class);
    private static long AT_LEAST_ONE_MONTH_MS =  31L * 24L * 3600L * 1000L;

    private static final long DELAY = 5000;

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

    @Inject
    private MysqlTestingHelper helper;

    private EntitlementUserApi entitlementUserApi;

    private InvoiceUserApi invoiceUserApi;

    private AccountUserApi accountUserApi;

    private TestBusHandler busHandler;

    private void setupMySQL() throws IOException
    {


        final String accountDdl = IOUtils.toString(TestIntegration.class.getResourceAsStream("/com/ning/billing/account/ddl.sql"));
        final String entitlementDdl = IOUtils.toString(TestIntegration.class.getResourceAsStream("/com/ning/billing/entitlement/ddl.sql"));
        final String invoiceDdl = IOUtils.toString(TestIntegration.class.getResourceAsStream("/com/ning/billing/invoice/ddl.sql"));
        final String paymentDdl = IOUtils.toString(TestIntegration.class.getResourceAsStream("/com/ning/billing/payment/ddl.sql"));
        final String utilDdl = IOUtils.toString(TestIntegration.class.getResourceAsStream("/com/ning/billing/util/ddl.sql"));

        helper.startMysql();

        helper.initDb(accountDdl);
        helper.initDb(entitlementDdl);
        helper.initDb(invoiceDdl);
        helper.initDb(paymentDdl);
        helper.initDb(utilDdl);
    }

    @BeforeSuite(alwaysRun = true)
    public void setup() throws Exception{

        setupMySQL();

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
                h.execute("truncate table entitlement_events");
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

    private void verifyTestResult(UUID accountId, UUID subscriptionId,
                                  DateTime startDate, DateTime endDate,
                                  BigDecimal amount, DateTime chargeThroughDate,
                                  int totalInvoiceItemCount) {
        SubscriptionData subscription = (SubscriptionData) entitlementUserApi.getSubscriptionFromId(subscriptionId);

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(accountId);
        List<InvoiceItem> invoiceItems = new ArrayList<InvoiceItem>();
        for (Invoice invoice : invoices) {
            invoiceItems.addAll(invoice.getInvoiceItems());
        }
        assertEquals(invoiceItems.size(), totalInvoiceItemCount);

        boolean wasFound = false;

        for (InvoiceItem item : invoiceItems) {
            if (item.getStartDate().compareTo(startDate) == 0) {
                if (item.getEndDate().compareTo(endDate) == 0) {
                    if (item.getAmount().compareTo(amount) == 0) {
                        wasFound = true;
                        break;
                    }
                }
            }
        }

        assertTrue(wasFound);

        DateTime ctd = subscription.getChargedThroughDate();
        assertNotNull(ctd);
        log.info("Checking CTD: " + ctd.toString() + "; clock is " + clock.getUTCNow().toString());
        assertTrue(clock.getUTCNow().isBefore(ctd));
        assertTrue(ctd.compareTo(chargeThroughDate) == 0);
    }

    @Test(groups = "slow", enabled = true)
    public void testBasePlanCompleteWithBillingDayInPast() throws Exception {
        DateTime startDate = new DateTime(2012, 2, 1, 0, 3, 42, 0);
        testBasePlanComplete(startDate, 31, false);
    }

    @Test(groups = "slow", enabled = true)
    public void testBasePlanCompleteWithBillingDayPresent() throws Exception {
        DateTime startDate = new DateTime(2012, 2, 1, 0, 3, 42, 0);
        testBasePlanComplete(startDate, 1, false);
    }

    @Test(groups = "slow", enabled = true)
    public void testBasePlanCompleteWithBillingDayAlignedWithTrial() throws Exception {
        DateTime startDate = new DateTime(2012, 2, 1, 0, 3, 42, 0);
        testBasePlanComplete(startDate, 2, false);
    }

    @Test(groups = "slow", enabled = true)
    public void testBasePlanCompleteWithBillingDayInFuture() throws Exception {
        DateTime startDate = new DateTime(2012, 2, 1, 0, 3, 42, 0);
        testBasePlanComplete(startDate, 3, true);
    }

    private void waitForDebug() throws Exception {
        Thread.sleep(600000);
    }

    @Test(groups = "stress", enabled = false)
    public void stressTest() throws Exception {
        final int maxIterations = 7;
        for (int curIteration = 0; curIteration < maxIterations; curIteration++) {
            log.info("################################  ITERATION " + curIteration + "  #########################");
            Thread.sleep(1000);
            setupTest();
            testBasePlanCompleteWithBillingDayPresent();
            Thread.sleep(1000);
            setupTest();
            testBasePlanCompleteWithBillingDayInPast();
            Thread.sleep(1000);
            setupTest();
            testBasePlanCompleteWithBillingDayAlignedWithTrial();
            Thread.sleep(1000);
            setupTest();
            testBasePlanCompleteWithBillingDayInFuture();
        }
    }

    @Test(groups = "slow", enabled = true)
    public void testWithRecreatePlan() throws Exception {

        DateTime initialDate = new DateTime(2012, 2, 1, 0, 3, 42, 0);
        int billingDay = 2;

        log.info("Beginning test with BCD of " + billingDay);
        Account account = accountUserApi.createAccount(getAccountData(billingDay), null, null);
        UUID accountId = account.getId();
        assertNotNull(account);

        // set clock to the initial start date
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());
        SubscriptionBundle bundle = entitlementUserApi.createBundleForAccount(account.getId(), "whatever2");

        String productName = "Shotgun";
        BillingPeriod term = BillingPeriod.MONTHLY;
        String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE NextEvent.INVOICE
        //
        busHandler.pushExpectedEvent(NextEvent.CREATE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        SubscriptionData subscription = (SubscriptionData) entitlementUserApi.createSubscription(bundle.getId(),
                new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSetName, null), null);
        assertNotNull(subscription);
        assertTrue(busHandler.isCompleted(DELAY));

        //
        // VERIFY CTD HAS BEEN SET
        //
        DateTime startDate = subscription.getCurrentPhaseStart();
        DateTime endDate = startDate.plusDays(30);
        BigDecimal rate = subscription.getCurrentPhase().getFixedPrice().getPrice(Currency.USD);
        int invoiceItemCount = 1;
        verifyTestResult(accountId, subscription.getId(), startDate, endDate, rate, endDate, invoiceItemCount);

        //
        // MOVE TIME TO AFTER TRIAL AND EXPECT BOTH EVENTS :  NextEvent.PHASE NextEvent.INVOICE
        //
        busHandler.pushExpectedEvent(NextEvent.PHASE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        clock.addDeltaFromReality(AT_LEAST_ONE_MONTH_MS);
        assertTrue(busHandler.isCompleted(DELAY));

        subscription = (SubscriptionData) entitlementUserApi.getSubscriptionFromId(subscription.getId());
        subscription.cancel(clock.getUTCNow(), false);

        // MOVE AFTER CANCEL DATE AND EXPECT EVENT : NextEvent.CANCEL
        busHandler.pushExpectedEvent(NextEvent.CANCEL);
        endDate = subscription.getChargedThroughDate();
        Interval it = new Interval(clock.getUTCNow(), endDate);
        clock.addDeltaFromReality(it.toDurationMillis());
        assertTrue(busHandler.isCompleted(DELAY));

        productName = "Assault-Rifle";
        term = BillingPeriod.MONTHLY;
        planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

        busHandler.pushExpectedEvent(NextEvent.CREATE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        subscription.recreate(new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSetName, null), endDate);
        assertTrue(busHandler.isCompleted(DELAY));


    }
    private void testBasePlanComplete(DateTime initialCreationDate, int billingDay,
                                      boolean proRationExpected) throws Exception {

        log.info("Beginning test with BCD of " + billingDay);
        Account account = accountUserApi.createAccount(getAccountData(billingDay), null, null);
        UUID accountId = account.getId();
        assertNotNull(account);

        // set clock to the initial start date
        clock.setDeltaFromReality(initialCreationDate.getMillis() - clock.getUTCNow().getMillis());
        SubscriptionBundle bundle = entitlementUserApi.createBundleForAccount(account.getId(), "whatever");

        String productName = "Shotgun";
        BillingPeriod term = BillingPeriod.MONTHLY;
        String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE NextEvent.INVOICE
        //
        busHandler.pushExpectedEvent(NextEvent.CREATE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        SubscriptionData subscription = (SubscriptionData) entitlementUserApi.createSubscription(bundle.getId(),
                new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSetName, null), null);
        assertNotNull(subscription);

        assertTrue(busHandler.isCompleted(DELAY));

        //
        // VERIFY CTD HAS BEEN SET
        //
        DateTime startDate = subscription.getCurrentPhaseStart();
        DateTime endDate = startDate.plusDays(30);
        BigDecimal rate = subscription.getCurrentPhase().getFixedPrice().getPrice(Currency.USD);
        int invoiceItemCount = 1;
        verifyTestResult(accountId, subscription.getId(), startDate, endDate, rate, endDate, invoiceItemCount);

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

        //
        // VERIFY AGAIN CTD HAS BEEN SET
        //
        startDate = subscription.getCurrentPhaseStart();
        endDate = startDate.plusDays(30);
        invoiceItemCount = 2;
        verifyTestResult(accountId, subscription.getId(), startDate, endDate, rate, endDate, invoiceItemCount);

        //
        // MOVE TIME TO AFTER TRIAL AND EXPECT BOTH EVENTS :  NextEvent.PHASE NextEvent.INVOICE
        //
        busHandler.pushExpectedEvent(NextEvent.PHASE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);

        if (proRationExpected) {
            busHandler.pushExpectedEvent(NextEvent.INVOICE);
            busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        }

        clock.addDeltaFromReality(AT_LEAST_ONE_MONTH_MS);

        assertTrue(busHandler.isCompleted(DELAY));

        startDate = subscription.getCurrentPhaseStart();
        rate = subscription.getCurrentPhase().getRecurringPrice().getPrice(Currency.USD);
        BigDecimal price;
        DateTime chargeThroughDate;

        switch (billingDay) {
            case 1:
                // this will result in a 30-day pro-ration
                price = THIRTY.divide(THIRTY_ONE, 2 * NUMBER_OF_DECIMALS, ROUNDING_METHOD).multiply(rate).setScale(NUMBER_OF_DECIMALS, ROUNDING_METHOD);
                chargeThroughDate = startDate.plusMonths(1).toMutableDateTime().dayOfMonth().set(billingDay).toDateTime();
                invoiceItemCount += 1;
                verifyTestResult(accountId, subscription.getId(), startDate, chargeThroughDate, price, chargeThroughDate, invoiceItemCount);
                break;
            case 2:
                // this will result in one full-period invoice item
                price = rate;
                chargeThroughDate = startDate.plusMonths(1);
                invoiceItemCount += 1;
                verifyTestResult(accountId, subscription.getId(), startDate, chargeThroughDate, price, chargeThroughDate, invoiceItemCount);
                break;
            case 3:
                // this will result in a 1-day leading pro-ration and a full-period invoice item
                price = ONE.divide(TWENTY_NINE, 2 * NUMBER_OF_DECIMALS, ROUNDING_METHOD).multiply(rate).setScale(NUMBER_OF_DECIMALS, ROUNDING_METHOD);
                DateTime firstEndDate = startDate.plusDays(1);
                chargeThroughDate = firstEndDate.plusMonths(1);
                invoiceItemCount += 2;
                verifyTestResult(accountId, subscription.getId(), startDate, firstEndDate, price, chargeThroughDate, invoiceItemCount);
                verifyTestResult(accountId, subscription.getId(), firstEndDate, chargeThroughDate, rate, chargeThroughDate, invoiceItemCount);
                break;
            case 31:
                // this will result in a 29-day pro-ration
                chargeThroughDate = startDate.toMutableDateTime().dayOfMonth().set(31).toDateTime();
                price = TWENTY_NINE.divide(THIRTY_ONE, 2 * NUMBER_OF_DECIMALS, ROUNDING_METHOD).multiply(rate).setScale(NUMBER_OF_DECIMALS, ROUNDING_METHOD);
                invoiceItemCount += 1;
                verifyTestResult(accountId, subscription.getId(), startDate, chargeThroughDate, price, chargeThroughDate, invoiceItemCount);
                break;
            default:
                throw new UnsupportedOperationException();
        }

        //
        // CHANGE PLAN EOT AND EXPECT NOTHING
        //
        newTerm = BillingPeriod.MONTHLY;
        newPlanSetName = PriceListSet.DEFAULT_PRICELIST_NAME;
        newProductName = "Pistol";
        subscription = (SubscriptionData) entitlementUserApi.getSubscriptionFromId(subscription.getId());
        subscription.changePlan(newProductName, newTerm, newPlanSetName, clock.getUTCNow());

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

        startDate = chargeThroughDate;
        endDate = chargeThroughDate.plusMonths(1);
        price = subscription.getCurrentPhase().getRecurringPrice().getPrice(Currency.USD);
        invoiceItemCount += 1;
        verifyTestResult(accountId, subscription.getId(), startDate, endDate, price, endDate, invoiceItemCount);

        //
        // MOVE TIME AFTER NEXT BILL CYCLE DAY AND EXPECT EVENT : NextEvent.INVOICE
        //
        int maxCycles = 3;
        do {
            busHandler.pushExpectedEvent(NextEvent.INVOICE);
            busHandler.pushExpectedEvent(NextEvent.PAYMENT);
            clock.addDeltaFromReality(AT_LEAST_ONE_MONTH_MS + 1000);
            assertTrue(busHandler.isCompleted(DELAY));

            startDate = endDate;
            endDate = startDate.plusMonths(1);
            if (endDate.dayOfMonth().get() != billingDay) {
                // adjust for end of month issues
                int maximumDay = endDate.dayOfMonth().getMaximumValue();
                int newDay = (maximumDay < billingDay) ? maximumDay : billingDay;
                endDate = endDate.toMutableDateTime().dayOfMonth().set(newDay).toDateTime();
            }

            invoiceItemCount += 1;
            verifyTestResult(accountId, subscription.getId(), startDate, endDate, price, endDate, invoiceItemCount);
        } while (maxCycles-- > 0);

        //
        // FINALLY CANCEL SUBSCRIPTION EOT
        //
        subscription = (SubscriptionData) entitlementUserApi.getSubscriptionFromId(subscription.getId());
        subscription.cancel(clock.getUTCNow(), false);

        // MOVE AFTER CANCEL DATE AND EXPECT EVENT : NextEvent.CANCEL
        busHandler.pushExpectedEvent(NextEvent.CANCEL);
        Interval it = new Interval(clock.getUTCNow(), endDate);
        clock.addDeltaFromReality(it.toDurationMillis());
        assertTrue(busHandler.isCompleted(DELAY));

        //
        // CHECK AGAIN THERE IS NO MORE INVOICES GENERATED
        //
        busHandler.reset();
        clock.addDeltaFromReality(AT_LEAST_ONE_MONTH_MS + 1000);
        assertTrue(busHandler.isCompleted(DELAY));

        subscription = (SubscriptionData) entitlementUserApi.getSubscriptionFromId(subscription.getId());
        DateTime lastCtd = subscription.getChargedThroughDate();
        assertNotNull(lastCtd);
        log.info("Checking CTD: " + lastCtd.toString() + "; clock is " + clock.getUTCNow().toString());
        assertTrue(lastCtd.isBefore(clock.getUTCNow()));

        // The invoice system is still working to verify there is nothing to do
        Thread.sleep(DELAY);
        log.info("TEST PASSED !");
    }

    @Test(groups = "slow")
    public void testHappyPath() throws AccountApiException, EntitlementUserApiException {
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

    @Test(groups = "slow")
    public void testForMultipleRecurringPhases() throws AccountApiException, EntitlementUserApiException, InterruptedException {
        clock.setDeltaFromReality(new DateTime().getMillis() - clock.getUTCNow().getMillis());

        Account account = accountUserApi.createAccount(getAccountData(15), null, null);
        UUID accountId = account.getId();

        String productName = "Blowdart";
        String planSetName = "DEFAULT";

        busHandler.pushExpectedEvent(NextEvent.CREATE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        SubscriptionBundle bundle = entitlementUserApi.createBundleForAccount(accountId, "testKey");
        SubscriptionData subscription = (SubscriptionData) entitlementUserApi.createSubscription(bundle.getId(),
                                        new PlanPhaseSpecifier(productName, ProductCategory.BASE,
                                        BillingPeriod.MONTHLY, planSetName, PhaseType.TRIAL), null);
        assertTrue(busHandler.isCompleted(DELAY));
        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(accountId);
        assertNotNull(invoices);
        assertTrue(invoices.size() == 1);

        busHandler.pushExpectedEvent(NextEvent.PHASE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        clock.addDeltaFromReality(6 * AT_LEAST_ONE_MONTH_MS);
        assertTrue(busHandler.isCompleted(DELAY));
        invoices = invoiceUserApi.getInvoicesByAccount(accountId);
        assertNotNull(invoices);
        assertTrue(invoices.size() == 2);

        busHandler.pushExpectedEvent(NextEvent.PHASE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        clock.addDeltaFromReality(6 * AT_LEAST_ONE_MONTH_MS);
        assertTrue(busHandler.isCompleted(DELAY));
        invoices = invoiceUserApi.getInvoicesByAccount(accountId);
        assertNotNull(invoices);
        assertTrue(invoices.size() == 3);
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
}
