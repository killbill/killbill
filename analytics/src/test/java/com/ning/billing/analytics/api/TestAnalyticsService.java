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

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.mockito.Mockito;
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
import com.ning.billing.analytics.TestWithEmbeddedDB;
import com.ning.billing.analytics.dao.BusinessAccountDao;
import com.ning.billing.analytics.dao.BusinessSubscriptionTransitionDao;
import com.ning.billing.catalog.MockPriceList;
import com.ning.billing.catalog.api.Catalog;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PriceList;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.catalog.api.ProductCategory;
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
import com.ning.billing.payment.api.DefaultPaymentInfoEvent;
import com.ning.billing.payment.api.PaymentInfoEvent;
import com.ning.billing.payment.api.PaymentStatus;
import com.ning.billing.payment.dao.PaymentDao;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.DefaultCallContextFactory;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.DefaultClock;

import static org.testng.Assert.fail;

@Guice(modules = {AnalyticsTestModule.class})
public class TestAnalyticsService extends TestWithEmbeddedDB {
    final Product product = new MockProduct("platinum", "subscription", ProductCategory.BASE);
    final Plan plan = new MockPlan("platinum-monthly", product);
    final PlanPhase phase = new MockPhase(PhaseType.EVERGREEN, plan, MockDuration.UNLIMITED(), 25.95);

    private static final UUID ID = UUID.randomUUID();
    private static final String KEY = "12345";
    private static final String ACCOUNT_KEY = "pierre-12345";
    private static final Currency ACCOUNT_CURRENCY = Currency.EUR;
    private static final BigDecimal INVOICE_AMOUNT = BigDecimal.valueOf(1243.11);

    private final Clock clock = new DefaultClock();
    private final CallContext context = new DefaultCallContextFactory(clock).createCallContext("Analytics Test", CallOrigin.TEST, UserType.TEST);

    @Inject
    private AccountUserApi accountApi;

    @Inject
    private EntitlementUserApi entitlementApi;

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

    private SubscriptionEvent transition;
    private BusinessSubscriptionTransition expectedTransition;

    private AccountCreationEvent accountCreationNotification;
    private InvoiceCreationEvent invoiceCreationNotification;
    private PaymentInfoEvent paymentInfoNotification;

    @Inject
    private CatalogService catalogService;

    private Catalog catalog;

    @BeforeClass(groups = "slow")
    public void setUp() throws IOException, ClassNotFoundException, SQLException, EntitlementUserApiException, CatalogApiException {
        catalog = catalogService.getFullCatalog();
        Mockito.when(catalog.findPlan(Mockito.anyString(), Mockito.<DateTime>any())).thenReturn(plan);
        Mockito.when(catalog.findPhase(Mockito.anyString(), Mockito.<DateTime>any(), Mockito.<DateTime>any())).thenReturn(phase);

        // Killbill generic setup
        bus.start();
    }

    @BeforeMethod(groups = "slow")
    public void createMocks() {
        final MockAccount account = new MockAccount(UUID.randomUUID(), ACCOUNT_KEY, ACCOUNT_CURRENCY);
        try {
            final Account storedAccount = accountApi.createAccount(account, context);

            // Create events for the bus and expected results
            createSubscriptionTransitionEvent(storedAccount);
            createAccountCreationEvent(storedAccount);
            createInvoiceAndPaymentCreationEvents(storedAccount);
        } catch (Throwable t) {
            fail("Initializing accounts failed.", t);
        }
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
                INVOICE_AMOUNT, ACCOUNT_CURRENCY);
        invoice.addInvoiceItem(invoiceItem);

        invoiceDao.create(invoice, context);
        final List<Invoice> invoices = invoiceDao.getInvoicesByAccount(account.getId());
        Assert.assertEquals(invoices.size(), 1);
        Assert.assertEquals(invoices.get(0).getInvoiceItems().size(), 1);

        // It doesn't really matter what the events contain - the listener will go back to the db
        invoiceCreationNotification = new DefaultInvoiceCreationEvent(invoice.getId(), account.getId(),
                                                                      INVOICE_AMOUNT, ACCOUNT_CURRENCY, clock.getUTCNow(), null);

        paymentInfoNotification = new DefaultPaymentInfoEvent(account.getId(), invoices.get(0).getId(), null, invoices.get(0).getBalance(), -1, PaymentStatus.UNKNOWN, null, new DateTime());

        //STEPH talk to Pierre
        /*
        paymentInfoNotification = new DefaultPaymentInfoEvent.Builder().setId(UUID.randomUUID()).setExternalPaymentId("12345abcdef").setPaymentMethod(PAYMENT_METHOD).setCardCountry(CARD_COUNTRY).build();
        final PaymentAttempt2 paymentAttempt = new DefaultPaymentAttempt2(UUID.randomUUID(), invoice.getId(), account.getId(), BigDecimal.TEN,
                ACCOUNT_CURRENCY, clock.getUTCNow(), clock.getUTCNow(), paymentInfoNotification.getId(), 1, new DateTime(), new DateTime(), PaymentAttemptStatus.COMPLETED_SUCCESS);
        paymentDao.createPaymentAttempt(paymentAttempt, PaymentAttemptStatus.COMPLETED_SUCCESS, context);
        paymentDao.insertPaymentInfoWithPaymentAttemptUpdate(paymentInfoNotification, paymentAttempt.getId(), context);
        Assert.assertEquals(paymentDao.getPaymentInfoList(Arrays.asList(invoice.getId())).size(), 1);
    */
    }


    @AfterClass(groups = "slow")
    public void stopMysql() {
        helper.stopMysql();
    }


    // STEPH talk to Pierre -- see previous remark hence disable test
    @Test(groups = "slow", enabled = true)
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

        // Test invoice integration - the account creation notification has triggered a BAC update
        Assert.assertTrue(accountDao.getAccount(ACCOUNT_KEY).getTotalInvoiceBalance().compareTo(INVOICE_AMOUNT) == 0);

        // Post the same invoice event again - the invoice balance shouldn't change
        bus.post(invoiceCreationNotification);
        Thread.sleep(5000);
        Assert.assertTrue(accountDao.getAccount(ACCOUNT_KEY).getTotalInvoiceBalance().compareTo(INVOICE_AMOUNT) == 0);

        // Test payment integration - the fields have already been populated, just make sure the code is exercised
        bus.post(paymentInfoNotification);
        Thread.sleep(5000);
        // STEPH talk to Pierre
        //Assert.assertEquals(accountDao.getAccount(ACCOUNT_KEY).getPaymentMethod(), PAYMENT_METHOD);
        //Assert.assertEquals(accountDao.getAccount(ACCOUNT_KEY).getBillingAddressCountry(), CARD_COUNTRY);

        // Test the shutdown sequence
        try {
            bus.stop();
        } catch (Throwable t) {
            Assert.fail("Unable to stop the bus!");
        }
    }
}
