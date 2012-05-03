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

package com.ning.billing.analytics.api;

import static org.testng.Assert.fail;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.ning.billing.util.tag.TagDefinition;
import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountCreationEvent;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.account.api.user.DefaultAccountCreationEvent;
import com.ning.billing.analytics.AnalyticsTestModule;
import com.ning.billing.analytics.BusinessSubscription;
import com.ning.billing.analytics.BusinessSubscriptionEvent;
import com.ning.billing.analytics.BusinessSubscriptionTransition;
import com.ning.billing.analytics.MockAccount;
import com.ning.billing.analytics.MockDuration;
import com.ning.billing.analytics.MockPhase;
import com.ning.billing.analytics.MockPlan;
import com.ning.billing.analytics.MockProduct;
import com.ning.billing.analytics.dao.BusinessAccountDao;
import com.ning.billing.analytics.dao.BusinessSubscriptionTransitionDao;
import com.ning.billing.catalog.MockCatalogModule;
import com.ning.billing.catalog.MockPriceList;
import com.ning.billing.catalog.api.Catalog;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PriceList;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.entitlement.api.user.DefaultSubscriptionEvent;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionEvent;
import com.ning.billing.entitlement.api.user.SubscriptionTransitionData;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.events.user.ApiEventType;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceCreationEvent;
import com.ning.billing.invoice.api.user.DefaultInvoiceCreationEvent;
import com.ning.billing.invoice.dao.InvoiceDao;
import com.ning.billing.invoice.model.DefaultInvoice;
import com.ning.billing.invoice.model.FixedPriceInvoiceItem;
import com.ning.billing.mock.BrainDeadProxyFactory;
import com.ning.billing.mock.BrainDeadProxyFactory.ZombieControl;
import com.ning.billing.payment.api.DefaultPaymentInfoEvent;
import com.ning.billing.payment.api.PaymentAttempt;
import com.ning.billing.payment.api.PaymentInfoEvent;
import com.ning.billing.payment.dao.PaymentDao;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.DefaultCallContextFactory;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.DefaultClock;
import com.ning.billing.util.tag.DefaultTagDefinition;
import com.ning.billing.util.tag.dao.TagDefinitionSqlDao;

@Guice(modules = {AnalyticsTestModule.class, MockCatalogModule.class})
public class TestAnalyticsService {
    
    final Product product = new MockProduct("platinum", "subscription", ProductCategory.BASE);
    final Plan plan = new MockPlan("platinum-monthly", product);
    final PlanPhase phase = new MockPhase(PhaseType.EVERGREEN, plan, MockDuration.UNLIMITED(), 25.95);


    private static final UUID ID = UUID.randomUUID();
    private static final String KEY = "12345";
    private static final String ACCOUNT_KEY = "pierre-12345";
    private static final Currency ACCOUNT_CURRENCY = Currency.EUR;
    private static final DefaultTagDefinition TAG_ONE = new DefaultTagDefinition("batch20", "something");
    private static final DefaultTagDefinition TAG_TWO = new DefaultTagDefinition("awesome", "something");
    private static final BigDecimal INVOICE_AMOUNT = BigDecimal.valueOf(1243.11);
    private static final String PAYMENT_METHOD = "Paypal";
    private static final String CARD_COUNTRY = "France";

    private final Clock clock = new DefaultClock();
    private final CallContext context = new DefaultCallContextFactory(clock).createCallContext("Analytics Test", CallOrigin.TEST, UserType.TEST);

    @Inject
    private AccountUserApi accountApi;

    @Inject
    private EntitlementUserApi entitlementApi;

    @Inject
    private TagDefinitionSqlDao tagDao;

    @Inject
    private InvoiceDao invoiceDao;

    @Inject
    private PaymentDao paymentDao;

    @Inject
    private DefaultAnalyticsService service;

    @Inject
    private Bus bus;

    @Inject
    private BusinessSubscriptionTransitionDao subscriptionDao;

    @Inject
    private BusinessAccountDao accountDao;

    @Inject
    private MysqlTestingHelper helper;

    private SubscriptionEvent transition;
    private BusinessSubscriptionTransition expectedTransition;

    private AccountCreationEvent accountCreationNotification;
    private InvoiceCreationEvent invoiceCreationNotification;
    private PaymentInfoEvent paymentInfoNotification;

    @Inject
    private  CatalogService catalogService;
    
    private Catalog catalog;
 
    @BeforeClass(groups = "slow")
    public void startMysql() throws IOException, ClassNotFoundException, SQLException, EntitlementUserApiException {

        catalog = catalogService.getFullCatalog();
        ((ZombieControl) catalog).addResult("findPlan", plan);
        ((ZombieControl) catalog).addResult("findPhase", phase);        

        // Killbill generic setup
        setupBusAndMySQL();

        helper.cleanupAllTables();
        
        tagDao.create(TAG_ONE, context);
        tagDao.create(TAG_TWO, context);

        final MockAccount account = new MockAccount(UUID.randomUUID(), ACCOUNT_KEY, ACCOUNT_CURRENCY);
        try {
            final List<TagDefinition> tagDefinitions = new ArrayList<TagDefinition>();
            tagDefinitions.add(TAG_ONE);
            tagDefinitions.add(TAG_TWO);

            final Account storedAccount = accountApi.createAccount(account, null, tagDefinitions, context);

            // Create events for the bus and expected results
            createSubscriptionTransitionEvent(storedAccount);
            createAccountCreationEvent(storedAccount);
            createInvoiceAndPaymentCreationEvents(storedAccount);
        } catch (Throwable t) {
            fail("Initializing accounts failed.", t);
        }
    }

    private void setupBusAndMySQL() throws IOException {

        final String analyticsDdl = IOUtils.toString(BusinessSubscriptionTransitionDao.class.getResourceAsStream("/com/ning/billing/analytics/ddl.sql"));
        final String accountDdl = IOUtils.toString(BusinessSubscriptionTransitionDao.class.getResourceAsStream("/com/ning/billing/account/ddl.sql"));
        final String entitlementDdl = IOUtils.toString(BusinessSubscriptionTransitionDao.class.getResourceAsStream("/com/ning/billing/entitlement/ddl.sql"));
        final String invoiceDdl = IOUtils.toString(BusinessSubscriptionTransitionDao.class.getResourceAsStream("/com/ning/billing/invoice/ddl.sql"));
        final String paymentDdl = IOUtils.toString(BusinessSubscriptionTransitionDao.class.getResourceAsStream("/com/ning/billing/payment/ddl.sql"));
        final String utilDdl = IOUtils.toString(BusinessSubscriptionTransitionDao.class.getResourceAsStream("/com/ning/billing/util/ddl.sql"));
        final String junctionDdl = IOUtils.toString(BusinessSubscriptionTransitionDao.class.getResourceAsStream("/com/ning/billing/junction/ddl.sql"));

        helper.startMysql();
        helper.initDb(analyticsDdl);
        helper.initDb(accountDdl);
        helper.initDb(entitlementDdl);
        helper.initDb(invoiceDdl);
        helper.initDb(paymentDdl);
        helper.initDb(utilDdl);
        helper.initDb(junctionDdl);

        helper.cleanupAllTables();
    	
        bus.start();
    }

    private void createSubscriptionTransitionEvent(final Account account) throws EntitlementUserApiException {
        final SubscriptionBundle bundle = entitlementApi.createBundleForAccount(account.getId(), KEY, context);

        // Verify we correctly initialized the account subsystem
        Assert.assertNotNull(bundle);
        Assert.assertEquals(bundle.getKey(), KEY);

        // Create a subscription transition event
        final UUID subscriptionId = UUID.randomUUID();
        final DateTime effectiveTransitionTime = clock.getUTCNow();
        final DateTime requestedTransitionTime = clock.getUTCNow();
        final PriceList priceList = new MockPriceList().setName("something");

        
        transition = new DefaultSubscriptionEvent(new SubscriptionTransitionData(
                ID,
                subscriptionId,
                bundle.getId(),
                EntitlementEvent.EventType.API_USER,
                ApiEventType.CREATE,
                requestedTransitionTime,
                effectiveTransitionTime,
                null,
                null,
                null,
                null,
                Subscription.SubscriptionState.ACTIVE,
                plan,
                phase,
                priceList,
                1L,
                null,
                true), null);
        expectedTransition = new BusinessSubscriptionTransition(
                ID,
                KEY,
                ACCOUNT_KEY,
                requestedTransitionTime,
                BusinessSubscriptionEvent.subscriptionCreated(plan.getName(), catalog, new DateTime(), new DateTime()),
                null,
                new BusinessSubscription(priceList.getName(), plan.getName(), phase.getName(), ACCOUNT_CURRENCY, effectiveTransitionTime, Subscription.SubscriptionState.ACTIVE, subscriptionId, bundle.getId(), catalog)
        );
    }

    private void createAccountCreationEvent(final Account account) {
        accountCreationNotification = new DefaultAccountCreationEvent(account, null);
    }

    private void createInvoiceAndPaymentCreationEvents(final Account account) {
        final DefaultInvoice invoice = new DefaultInvoice(account.getId(), clock.getUTCNow(), clock.getUTCNow(), ACCOUNT_CURRENCY);
        final FixedPriceInvoiceItem invoiceItem = new FixedPriceInvoiceItem(
                UUID.randomUUID(), invoice.getId(), account.getId(), UUID.randomUUID(), UUID.randomUUID(), "somePlan", "somePhase", clock.getUTCNow(), clock.getUTCNow().plusDays(1),
                INVOICE_AMOUNT, ACCOUNT_CURRENCY, context.getUserName(), clock.getUTCNow());
        invoice.addInvoiceItem(invoiceItem);

        invoiceDao.create(invoice, context);
        List<Invoice> invoices = invoiceDao.getInvoicesByAccount(account.getId());
        Assert.assertEquals(invoices.size(), 1);
        Assert.assertEquals(invoices.get(0).getInvoiceItems().size(), 1);

        // It doesn't really matter what the events contain - the listener will go back to the db
        invoiceCreationNotification = new DefaultInvoiceCreationEvent(invoice.getId(), account.getId(),
                INVOICE_AMOUNT, ACCOUNT_CURRENCY, clock.getUTCNow(), null);

        paymentInfoNotification = new DefaultPaymentInfoEvent.Builder().setPaymentId(UUID.randomUUID().toString()).setPaymentMethod(PAYMENT_METHOD).setCardCountry(CARD_COUNTRY).build();
        final PaymentAttempt paymentAttempt = new PaymentAttempt(UUID.randomUUID(), invoice.getId(), account.getId(), BigDecimal.TEN,
                ACCOUNT_CURRENCY, clock.getUTCNow(), clock.getUTCNow(), paymentInfoNotification.getPaymentId(), 1);
        paymentDao.createPaymentAttempt(paymentAttempt, context);
        paymentDao.savePaymentInfo(paymentInfoNotification, context);
        Assert.assertEquals(paymentDao.getPaymentInfo(Arrays.asList(invoice.getId().toString())).size(), 1);
    }

    @AfterClass(groups = "slow")
    public void stopMysql() {
        helper.stopMysql();
    }

    @Test(groups = "slow", enabled=true)
    public void testRegisterForNotifications() throws Exception {
        // Make sure the service has been instantiated
        Assert.assertEquals(service.getName(), "analytics-service");

        // Test the bus and make sure we can register our service
        try {
            service.registerForNotifications();
        } catch (Throwable t) {
            Assert.fail("Unable to start the bus or service! " + t);
        }

        Assert.assertNull(accountDao.getAccount(ACCOUNT_KEY));

        // Send events and wait for the async part...
        bus.post(transition);
        bus.post(accountCreationNotification);
        Thread.sleep(5000);

        Assert.assertEquals(subscriptionDao.getTransitions(KEY).size(), 1);
        Assert.assertEquals(subscriptionDao.getTransitions(KEY).get(0), expectedTransition);

        Assert.assertEquals(accountDao.getAccount(ACCOUNT_KEY).getKey(), ACCOUNT_KEY);
        Assert.assertEquals(accountDao.getAccount(ACCOUNT_KEY).getTags().size(), 2);
        Assert.assertTrue(accountDao.getAccount(ACCOUNT_KEY).getTags().indexOf(TAG_ONE.getName()) != -1);
        Assert.assertTrue(accountDao.getAccount(ACCOUNT_KEY).getTags().indexOf(TAG_TWO.getName()) != -1);

        // Test invoice integration - the account creation notification has triggered a BAC update
        Assert.assertTrue(accountDao.getAccount(ACCOUNT_KEY).getTotalInvoiceBalance().compareTo(INVOICE_AMOUNT) == 0);

        // Post the same invoice event again - the invoice balance shouldn't change
        bus.post(invoiceCreationNotification);
        Thread.sleep(5000);
        Assert.assertTrue(accountDao.getAccount(ACCOUNT_KEY).getTotalInvoiceBalance().compareTo(INVOICE_AMOUNT) == 0);

        // Test payment integration - the fields have already been populated, just make sure the code is exercised
        bus.post(paymentInfoNotification);
        Thread.sleep(5000);
        Assert.assertEquals(accountDao.getAccount(ACCOUNT_KEY).getPaymentMethod(), PAYMENT_METHOD);
        Assert.assertEquals(accountDao.getAccount(ACCOUNT_KEY).getBillingAddressCountry(), CARD_COUNTRY);

        // Test the shutdown sequence
        try {
            bus.stop();
        } catch (Throwable t) {
            Assert.fail("Unable to stop the bus!");
        }
    }
}
