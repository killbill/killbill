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

package com.ning.billing.beatrix.integration.inv_ent;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.UUID;

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
import com.ning.billing.beatrix.integration.inv_ent.TestBusHandler.NextEvent;
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
import com.ning.billing.util.eventbus.BusService;

@Guice(modules = {MockModule.class})
public class TestBasic {

    private static final Logger log = LoggerFactory.getLogger(TestBasic.class);
    private static long AT_LEAST_ONE_MONTH_MS = (31 * 24 * 3600 * 1000);

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
                h.execute("truncate table invoice_items");
                h.execute("truncate table tag_definitions");
                h.execute("truncate table tags");
                return null;
            }
        });
    }


    private DateTime checkAndGetCTD(UUID subscriptionId) {
        SubscriptionData subscription = (SubscriptionData) entitlementUserApi.getSubscriptionFromId(subscriptionId);
        DateTime ctd = subscription.getChargedThroughDate();
        assertNotNull(ctd);
        assertTrue(clock.getUTCNow().isBefore(ctd));
        return ctd;
    }

    @Test(groups = "fast", enabled = false)
    public void testSimple() throws Exception {

        Account account = accountUserApi.createAccount(getAccountData(), null, null);
        assertNotNull(account);

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
        assertTrue(busHandler.isCompleted(5000));

        //
        // VERIFY CTD HAS BEEN SET
        //
        checkAndGetCTD(subscription.getId());

        //
        // CHANGE PLAN IMMEDIATELY AND EXPECT BOTH EVENTS: NextEvent.CHANGE NextEvent.INVOICE
        //
        busHandler.pushExpectedEvent(NextEvent.CHANGE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);

        BillingPeriod newTerm = BillingPeriod.MONTHLY;
        String newPlanSetName = PriceListSet.DEFAULT_PRICELIST_NAME;
        String newProductName = "Assault-Rifle";
        subscription.changePlan(newProductName, newTerm, newPlanSetName, clock.getUTCNow());
        assertTrue(busHandler.isCompleted(5000));

        //
        // VERIFY AGAIN CTD HAS BEEN SET
        //
        DateTime ctd = checkAndGetCTD(subscription.getId());

        //
        // CHANGE PLAN EOT AND EXPECT NOTHING
        //
        newTerm = BillingPeriod.MONTHLY;
        newPlanSetName = PriceListSet.DEFAULT_PRICELIST_NAME;
        newProductName = "Pistol";
        subscription.changePlan(newProductName, newTerm, newPlanSetName, clock.getUTCNow());

        //
        // MOVE TIME AFTER CTD AND EXPECT BOTH EVENTS : NextEvent.CHANGE NextEvent.INVOICE
        //
        busHandler.pushExpectedEvent(NextEvent.CHANGE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        clock.setDeltaFromReality(ctd.getMillis() - clock.getUTCNow().getMillis());
        //clock.setDeltaFromReality(AT_LEAST_ONE_MONTH_MS + 1000);
        assertTrue(busHandler.isCompleted(5000));

        //
        // MOVE TIME AFTER NEXT BILL CYCLE DAY AND EXPECT EVENT : NextEvent.INVOICE
        //
        int maxCycles = 3;
        DateTime lastCtd = null;
        do {
            clock.addDeltaFromReality(AT_LEAST_ONE_MONTH_MS + 1000);
            busHandler.pushExpectedEvent(NextEvent.INVOICE);
            assertTrue(busHandler.isCompleted(5000));
            lastCtd = checkAndGetCTD(subscription.getId());
        } while (maxCycles-- > 0);

        //
        // FINALLY CANCEL SUBSCRIPTION EOT
        //
        subscription.cancel(clock.getUTCNow(), false);

        // MOVE AFTER CANCEL DATE AND EXPECT EVENT : NextEvent.CANCEL, NextEvent.INVOICE
        busHandler.pushExpectedEvent(NextEvent.CANCEL);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        Interval it = new Interval(lastCtd, clock.getUTCNow());
        clock.addDeltaFromReality(it.toDurationMillis());
        assertTrue(busHandler.isCompleted(5000));
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
