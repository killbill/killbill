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
import static org.testng.Assert.fail;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import com.google.inject.Inject;
import com.ning.billing.account.api.AccountData;
import com.ning.billing.account.api.AccountService;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.api.TestApiListener;
import com.ning.billing.api.TestListenerStatus;
import com.ning.billing.beatrix.lifecycle.Lifecycle;
import com.ning.billing.catalog.DefaultCatalogService;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.entitlement.api.EntitlementService;
import com.ning.billing.entitlement.api.timeline.EntitlementTimelineApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.engine.core.Engine;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceService;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.invoice.model.InvoicingConfiguration;
import com.ning.billing.junction.plumbing.api.BlockingSubscription;
import com.ning.billing.util.bus.BusService;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.DefaultCallContextFactory;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.clock.ClockMock;

public class TestIntegrationBase implements TestListenerStatus {

    protected static final int NUMBER_OF_DECIMALS = InvoicingConfiguration.getNumberOfDecimals();
    protected static final int ROUNDING_METHOD = InvoicingConfiguration.getRoundingMode();

    protected static final BigDecimal ONE = new BigDecimal("1.0000").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal TWENTY_NINE = new BigDecimal("29.0000").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal THIRTY = new BigDecimal("30.0000").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal THIRTY_ONE = new BigDecimal("31.0000").setScale(NUMBER_OF_DECIMALS);

    protected static final Logger log = LoggerFactory.getLogger(TestIntegration.class);
    protected static long AT_LEAST_ONE_MONTH_MS =  31L * 24L * 3600L * 1000L;

    protected static final long DELAY = 5000;

    @Inject
    protected IDBI dbi;

    @Inject
    protected ClockMock clock;
    
    protected CallContext context;

    @Inject
    protected Lifecycle lifecycle;

    @Inject
    protected BusService busService;

    @Inject
    protected EntitlementService entitlementService;

    @Inject
    protected InvoiceService invoiceService;

    @Inject
    protected AccountService accountService;

    @Inject
    protected MysqlTestingHelper helper;
    @Inject
    protected EntitlementUserApi entitlementUserApi;

    @Inject
    protected EntitlementTimelineApi repairApi;
    
    @Inject
    protected InvoiceUserApi invoiceUserApi;

    @Inject
    protected AccountUserApi accountUserApi;

    protected TestApiListener busHandler;

    
    private boolean isListenerFailed;
    private String listenerFailedMsg;
    
    @Override
    public void failed(String msg) {
        isListenerFailed = true;
        listenerFailedMsg = msg;
    }

    @Override
    public void resetTestListenerStatus() {
        isListenerFailed = false;
        listenerFailedMsg = null;
    }

    
    protected void assertListenerStatus() {
        if (isListenerFailed) {
            log.error(listenerFailedMsg);
            Assert.fail(listenerFailedMsg);
        }
    }

    protected void setupMySQL() throws IOException
    {
        final String accountDdl = IOUtils.toString(TestIntegration.class.getResourceAsStream("/com/ning/billing/account/ddl.sql"));
        final String entitlementDdl = IOUtils.toString(TestIntegration.class.getResourceAsStream("/com/ning/billing/entitlement/ddl.sql"));
        final String invoiceDdl = IOUtils.toString(TestIntegration.class.getResourceAsStream("/com/ning/billing/invoice/ddl.sql"));
        final String paymentDdl = IOUtils.toString(TestIntegration.class.getResourceAsStream("/com/ning/billing/payment/ddl.sql"));
        final String utilDdl = IOUtils.toString(TestIntegration.class.getResourceAsStream("/com/ning/billing/util/ddl.sql"));
        final String junctionDb = IOUtils.toString(TestIntegration.class.getResourceAsStream("/com/ning/billing/junction/ddl.sql"));

        helper.startMysql();

        helper.initDb(accountDdl);
        helper.initDb(entitlementDdl);
        helper.initDb(invoiceDdl);
        helper.initDb(paymentDdl);
        helper.initDb(utilDdl);
        helper.initDb(junctionDb);
    }

  
    @BeforeClass(groups = "slow")
    public void setup() throws Exception{

        setupMySQL();
        
        context = new DefaultCallContextFactory(clock).createCallContext("Integration Test", CallOrigin.TEST, UserType.TEST);
        busHandler = new TestApiListener(this);
        
    }

    @AfterClass(groups = "slow")
    public void tearDown() throws Exception {
        helper.stopMysql();
    }


    @BeforeMethod(groups = "slow")
    public void setupTest() throws Exception {

        log.warn("\n");
        log.warn("RESET TEST FRAMEWORK\n\n");
        
        // Pre test cleanup
        helper.cleanupAllTables();

        clock.resetDeltaFromReality();
        resetTestListenerStatus();
        
        // Start services
        lifecycle.fireStartupSequencePriorEventRegistration();
        busService.getBus().register(busHandler);
        lifecycle.fireStartupSequencePostEventRegistration();
    }

    @AfterMethod(groups = "slow")
    public void cleanupTest() throws Exception {
        lifecycle.fireShutdownSequencePriorEventUnRegistration();
        busService.getBus().unregister(busHandler);
        lifecycle.fireShutdownSequencePostEventUnRegistration();

        log.warn("DONE WITH TEST\n");
    }
    

    protected void verifyTestResult(UUID accountId, UUID subscriptionId,
                                  DateTime startDate, DateTime endDate,
                                  BigDecimal amount, DateTime chargeThroughDate,
                                  int totalInvoiceItemCount) throws EntitlementUserApiException {
        SubscriptionData subscription = subscriptionDataFromSubscription(entitlementUserApi.getSubscriptionFromId(subscriptionId));

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

        if (!wasFound) {
            fail();
        }

        DateTime ctd = subscription.getChargedThroughDate();
        assertNotNull(ctd);
        log.info("Checking CTD: " + ctd.toString() + "; clock is " + clock.getUTCNow().toString());
        assertTrue(clock.getUTCNow().isBefore(ctd));
        assertTrue(ctd.compareTo(chargeThroughDate) == 0);
    }
       
    protected SubscriptionData subscriptionDataFromSubscription(Subscription sub) {
        return (SubscriptionData)((BlockingSubscription)sub).getDelegateSubscription();
    }
    
    protected AccountData getAccountData(final int billingDay) {

        final String someRandomKey = RandomStringUtils.randomAlphanumeric(10);
        return new AccountData() {
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
            public boolean isMigrated() {
                return false;
            }

            @Override
            public boolean isNotifiedForInvoices() {
                return false;
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
    }
}
