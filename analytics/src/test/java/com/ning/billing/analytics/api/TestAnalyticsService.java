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
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.account.api.user.DefaultAccountCreationEvent;
import com.ning.billing.account.dao.AccountModelDao;
import com.ning.billing.analytics.AnalyticsTestModule;
import com.ning.billing.analytics.AnalyticsTestSuiteWithEmbeddedDB;
import com.ning.billing.analytics.MockDuration;
import com.ning.billing.analytics.MockPhase;
import com.ning.billing.analytics.MockProduct;
import com.ning.billing.analytics.dao.BusinessAccountSqlDao;
import com.ning.billing.analytics.dao.BusinessSubscriptionTransitionSqlDao;
import com.ning.billing.analytics.model.BusinessSubscription;
import com.ning.billing.analytics.model.BusinessSubscriptionEvent;
import com.ning.billing.analytics.model.BusinessSubscriptionTransitionModelDao;
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
import com.ning.billing.entitlement.api.user.DefaultEffectiveSubscriptionEvent;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionTransitionData;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.events.user.ApiEventType;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.api.user.DefaultInvoiceCreationEvent;
import com.ning.billing.invoice.dao.InvoiceDao;
import com.ning.billing.invoice.dao.InvoiceItemModelDao;
import com.ning.billing.invoice.dao.InvoiceModelDao;
import com.ning.billing.invoice.dao.InvoiceModelDaoHelper;
import com.ning.billing.invoice.dao.InvoicePaymentModelDao;
import com.ning.billing.invoice.model.DefaultInvoice;
import com.ning.billing.invoice.model.FixedPriceInvoiceItem;
import com.ning.billing.mock.MockAccountBuilder;
import com.ning.billing.mock.MockPlan;
import com.ning.billing.payment.api.DefaultPaymentInfoEvent;
import com.ning.billing.payment.api.PaymentMethod;
import com.ning.billing.payment.api.PaymentStatus;
import com.ning.billing.payment.dao.PaymentAttemptModelDao;
import com.ning.billing.payment.dao.PaymentDao;
import com.ning.billing.payment.dao.PaymentModelDao;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.DefaultClock;
import com.ning.billing.util.events.AccountCreationInternalEvent;
import com.ning.billing.util.events.EffectiveSubscriptionInternalEvent;
import com.ning.billing.util.events.InvoiceCreationInternalEvent;
import com.ning.billing.util.events.PaymentInfoInternalEvent;
import com.ning.billing.util.svcsapi.bus.InternalBus;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

import static org.testng.Assert.fail;

@Guice(modules = {AnalyticsTestModule.class})
public class TestAnalyticsService extends AnalyticsTestSuiteWithEmbeddedDB {

    final Product product = new MockProduct("platinum", "subscription", ProductCategory.BASE);
    final Plan plan = new MockPlan("platinum-monthly", product);
    final PlanPhase phase = new MockPhase(PhaseType.EVERGREEN, plan, MockDuration.UNLIMITED(), 25.95);

    private static final Long TOTAL_ORDERING = 11L;
    private static final String BUNDLE_EXTERNAL_KEY = UUID.randomUUID().toString();
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final String ACCOUNT_KEY = UUID.randomUUID().toString();
    private static final Currency ACCOUNT_CURRENCY = Currency.EUR;
    private static final BigDecimal INVOICE_AMOUNT = BigDecimal.valueOf(1243.11);

    private final Clock clock = new DefaultClock();

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
    private InternalBus bus;

    @Inject
    private BusinessSubscriptionTransitionSqlDao subscriptionSqlDao;

    @Inject
    private BusinessAccountSqlDao accountSqlDao;

    private EffectiveSubscriptionInternalEvent transition;
    private BusinessSubscriptionTransitionModelDao expectedTransition;

    private AccountCreationInternalEvent accountCreationNotification;
    private InvoiceCreationInternalEvent invoiceCreationNotification;
    private PaymentInfoInternalEvent paymentInfoNotification;

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
        final PaymentMethod paymentMethod = Mockito.mock(PaymentMethod.class);
        final UUID paymentMethodId = UUID.randomUUID();
        Mockito.when(paymentMethod.getId()).thenReturn(paymentMethodId);
        final Account account = new MockAccountBuilder(UUID.randomUUID())
                .externalKey(ACCOUNT_KEY)
                .currency(ACCOUNT_CURRENCY)
                .paymentMethodId(paymentMethodId)
                .build();

        try {
            final Account storedAccount = accountApi.createAccount(account, callContext);

            // Create events for the bus and expected results
            createSubscriptionTransitionEvent(storedAccount);
            createAccountCreationEvent(storedAccount);
            createInvoiceAndPaymentCreationEvents(storedAccount);
        } catch (Throwable t) {
            fail("Initializing accounts failed.", t);
        }
    }

    private void createSubscriptionTransitionEvent(final Account account) throws EntitlementUserApiException {
        final SubscriptionBundle bundle = entitlementApi.createBundleForAccount(account.getId(), BUNDLE_EXTERNAL_KEY, callContext);

        // Verify we correctly initialized the account subsystem
        Assert.assertNotNull(bundle);
        Assert.assertEquals(bundle.getExternalKey(), BUNDLE_EXTERNAL_KEY);

        // Create a subscription transition event
        final UUID subscriptionId = UUID.randomUUID();
        final DateTime effectiveTransitionTime = clock.getUTCNow();
        final DateTime requestedTransitionTime = clock.getUTCNow();
        final PriceList priceList = new MockPriceList().setName("something");

        transition = new DefaultEffectiveSubscriptionEvent(new SubscriptionTransitionData(
                UUID.randomUUID(),
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
                TOTAL_ORDERING,
                null,
                true), null, 1L, 1L);
        expectedTransition = new BusinessSubscriptionTransitionModelDao(
                TOTAL_ORDERING,
                transition.getBundleId(),
                BUNDLE_EXTERNAL_KEY,
                ACCOUNT_ID,
                ACCOUNT_KEY,
                transition.getSubscriptionId(),
                requestedTransitionTime,
                BusinessSubscriptionEvent.subscriptionCreated(plan.getName(), catalog, clock.getUTCNow(), clock.getUTCNow()),
                null,
                new BusinessSubscription(priceList.getName(), plan.getName(), phase.getName(), ACCOUNT_CURRENCY, effectiveTransitionTime, Subscription.SubscriptionState.ACTIVE, catalog)
        );
    }

    private void createAccountCreationEvent(final Account account) {
        accountCreationNotification = new DefaultAccountCreationEvent(new AccountModelDao(account.getId(), account), null, 1L, 1L);
    }

    private void createInvoiceAndPaymentCreationEvents(final Account account) {
        final DefaultInvoice invoice = new DefaultInvoice(account.getId(), clock.getUTCToday(), clock.getUTCToday(), ACCOUNT_CURRENCY);
        final FixedPriceInvoiceItem invoiceItem = new FixedPriceInvoiceItem(invoice.getId(), account.getId(), UUID.randomUUID(), UUID.randomUUID(), "somePlan", "somePhase", clock.getUTCToday(),
                                                                            INVOICE_AMOUNT, ACCOUNT_CURRENCY);
        invoice.addInvoiceItem(invoiceItem);

        final InvoiceModelDao invoiceModelDao = new InvoiceModelDao(invoice);
        final List<InvoiceItemModelDao> invoiceItemModelDaos = ImmutableList.<InvoiceItemModelDao>copyOf(Collections2.transform(invoice.getInvoiceItems(),
                                                                                                                                new Function<InvoiceItem, InvoiceItemModelDao>() {
                                                                                                                                    @Override
                                                                                                                                    public InvoiceItemModelDao apply(final InvoiceItem input) {
                                                                                                                                        return new InvoiceItemModelDao(input);
                                                                                                                                    }
                                                                                                                                }));
        // Not really needed, there shouldn't be any payment at this stage
        final List<InvoicePaymentModelDao> invoicePaymentModelDaos = ImmutableList.<InvoicePaymentModelDao>copyOf(Collections2.transform(invoice.getPayments(),
                                                                                                                                         new Function<InvoicePayment, InvoicePaymentModelDao>() {
                                                                                                                                             @Override
                                                                                                                                             public InvoicePaymentModelDao apply(final InvoicePayment input) {
                                                                                                                                                 return new InvoicePaymentModelDao(input);
                                                                                                                                             }
                                                                                                                                         }));

        invoiceDao.createInvoice(invoiceModelDao, invoiceItemModelDaos, invoicePaymentModelDaos, true, internalCallContext);
        final List<InvoiceModelDao> invoices = invoiceDao.getInvoicesByAccount(account.getId(), internalCallContext);
        Assert.assertEquals(invoices.size(), 1);
        Assert.assertEquals(invoices.get(0).getInvoiceItems().size(), 1);

        // It doesn't really matter what the events contain - the listener will go back to the db
        invoiceCreationNotification = new DefaultInvoiceCreationEvent(invoice.getId(), account.getId(),
                                                                      INVOICE_AMOUNT, ACCOUNT_CURRENCY, null, 1L, 1L);

        paymentInfoNotification = new DefaultPaymentInfoEvent(account.getId(), invoices.get(0).getId(), null, InvoiceModelDaoHelper.getBalance(invoices.get(0)), -1,
                                                              PaymentStatus.UNKNOWN, null, null, null, clock.getUTCNow(), 1L, 1L);

        final PaymentModelDao paymentInfo = new PaymentModelDao(account.getId(), invoice.getId(), account.getPaymentMethodId(),
                                                                BigDecimal.ONE, Currency.USD, clock.getUTCNow(), PaymentStatus.SUCCESS);
        final PaymentAttemptModelDao paymentAttempt = new PaymentAttemptModelDao(account.getId(), invoice.getId(), paymentInfo.getId(),
                                                                                 clock.getUTCNow(), BigDecimal.ONE);
        paymentDao.insertPaymentWithAttempt(paymentInfo, paymentAttempt, internalCallContext);
        Assert.assertEquals(paymentDao.getPaymentsForAccount(account.getId(), internalCallContext).size(), 1);
    }

    // Flaky
    @Test(groups = "slow", enabled = false)
    public void testRegisterForNotifications() throws Exception {
        // Make sure the service has been instantiated
        Assert.assertEquals(service.getName(), "analytics-service");

        // Test the bus and make sure we can register our service
        try {
            service.registerForNotifications();
        } catch (Throwable t) {
            Assert.fail("Unable to start the bus or service! " + t);
        }

        Assert.assertNull(accountSqlDao.getAccountByKey(ACCOUNT_KEY, internalCallContext));

        // Send events and wait for the async part...
        bus.post(accountCreationNotification, internalCallContext);
        Thread.sleep(5000);
        Assert.assertNotNull(accountSqlDao.getAccountByKey(ACCOUNT_KEY, internalCallContext));

        // Test subscriptions integration - this is just to exercise the code. It's hard to test the actual subscriptions
        // as we would need to mock a bunch of APIs (see integration tests in Beatrix instead)
        bus.post(transition, internalCallContext);
        Thread.sleep(5000);

        // Test invoice integration - the account creation notification has triggered a BAC update
        Assert.assertEquals(accountSqlDao.getAccountByKey(ACCOUNT_KEY, internalCallContext).getTotalInvoiceBalance().compareTo(INVOICE_AMOUNT), 1);

        // Post the same invoice event again - the invoice balance shouldn't change
        bus.post(invoiceCreationNotification, internalCallContext);
        Thread.sleep(5000);
        Assert.assertEquals(accountSqlDao.getAccountByKey(ACCOUNT_KEY, internalCallContext).getTotalInvoiceBalance().compareTo(INVOICE_AMOUNT), 1);

        // Test payment integration - the fields have already been populated, just make sure the code is exercised
        // It's hard to test the actual payments fields though in bac, since we should mock the plugin
        bus.post(paymentInfoNotification, internalCallContext);
        Thread.sleep(5000);

        // Test the shutdown sequence
        try {
            bus.stop();
        } catch (Throwable t) {
            Assert.fail("Unable to stop the bus!");
        }
    }
}
