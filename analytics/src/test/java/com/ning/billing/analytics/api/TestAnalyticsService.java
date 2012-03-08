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

import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountCreationNotification;
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
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionTransition;
import com.ning.billing.entitlement.api.user.SubscriptionTransitionData;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.events.user.ApiEventType;
import com.ning.billing.invoice.api.InvoiceCreationNotification;
import com.ning.billing.invoice.api.user.DefaultInvoiceCreationNotification;
import com.ning.billing.invoice.dao.InvoiceDao;
import com.ning.billing.invoice.model.DefaultInvoice;
import com.ning.billing.invoice.model.FixedPriceInvoiceItem;
import com.ning.billing.payment.api.PaymentAttempt;
import com.ning.billing.payment.api.PaymentInfo;
import com.ning.billing.payment.dao.PaymentDao;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.DefaultClock;
import com.ning.billing.util.tag.DefaultTagDefinition;
import com.ning.billing.util.tag.DescriptiveTag;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.dao.TagDefinitionSqlDao;

@Guice(modules = AnalyticsTestModule.class)
public class TestAnalyticsService {
    private static final UUID ID = UUID.randomUUID();
    private static final String KEY = "12345";
    private static final String ACCOUNT_KEY = "pierre-12345";
    private static final Currency ACCOUNT_CURRENCY = Currency.EUR;
    private static final DefaultTagDefinition TAG_ONE = new DefaultTagDefinition("batch20", "something", "pierre");
    private static final DefaultTagDefinition TAG_TWO = new DefaultTagDefinition("awesome", "something", "pierre");
    private static final BigDecimal INVOICE_AMOUNT = BigDecimal.valueOf(1243.11);
    private static final String PAYMENT_METHOD = "Paypal";
    private static final String CARD_COUNTRY = "France";

    private final Clock clock = new DefaultClock();

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

    private SubscriptionTransition transition;
    private BusinessSubscriptionTransition expectedTransition;

    private AccountCreationNotification accountCreationNotification;
    private InvoiceCreationNotification invoiceCreationNotification;
    private PaymentInfo paymentInfoNotification;

    @BeforeClass(alwaysRun = true)
    public void startMysql() throws IOException, ClassNotFoundException, SQLException, EntitlementUserApiException {
        // Killbill generic setup
        setupBusAndMySQL();

        tagDao.create(TAG_ONE);
        tagDao.create(TAG_TWO);

        final MockAccount account = new MockAccount(UUID.randomUUID(), ACCOUNT_KEY, ACCOUNT_CURRENCY);
        try {
            final List<Tag> tags = new ArrayList<Tag>();
            tags.add(new DescriptiveTag(TAG_ONE, "pierre", clock.getUTCNow()));
            tags.add(new DescriptiveTag(TAG_TWO, "pierre", clock.getUTCNow()));

            final Account storedAccount = accountApi.createAccount(account, null, tags);

            // Create events for the bus and expected results
            createSubscriptionTransitionEvent(storedAccount);
            createAccountCreationEvent(storedAccount);
            createInvoiceAndPaymentCreationEvents(storedAccount);
        } catch (Throwable t) {
            fail("Initializing accounts failed.", t);
        }
    }

    private void setupBusAndMySQL() throws IOException {
        bus.start();

        final String analyticsDdl = IOUtils.toString(BusinessSubscriptionTransitionDao.class.getResourceAsStream("/com/ning/billing/analytics/ddl.sql"));
        final String accountDdl = IOUtils.toString(BusinessSubscriptionTransitionDao.class.getResourceAsStream("/com/ning/billing/account/ddl.sql"));
        final String entitlementDdl = IOUtils.toString(BusinessSubscriptionTransitionDao.class.getResourceAsStream("/com/ning/billing/entitlement/ddl.sql"));
        final String invoiceDdl = IOUtils.toString(BusinessSubscriptionTransitionDao.class.getResourceAsStream("/com/ning/billing/invoice/ddl.sql"));
        final String paymentDdl = IOUtils.toString(BusinessSubscriptionTransitionDao.class.getResourceAsStream("/com/ning/billing/payment/ddl.sql"));
        final String utilDdl = IOUtils.toString(BusinessSubscriptionTransitionDao.class.getResourceAsStream("/com/ning/billing/util/ddl.sql"));

        helper.startMysql();
        helper.initDb(analyticsDdl);
        helper.initDb(accountDdl);
        helper.initDb(entitlementDdl);
        helper.initDb(invoiceDdl);
        helper.initDb(paymentDdl);
        helper.initDb(utilDdl);
    }

    private void createSubscriptionTransitionEvent(final Account account) throws EntitlementUserApiException {
        final SubscriptionBundle bundle = entitlementApi.createBundleForAccount(account.getId(), KEY);

        // Verify we correctly initialized the account subsystem
        Assert.assertNotNull(bundle);
        Assert.assertEquals(bundle.getKey(), KEY);

        // Create a subscription transition event
        final Product product = new MockProduct("platinum", "subscription", ProductCategory.BASE);
        final Plan plan = new MockPlan("platinum-monthly", product);
        final PlanPhase phase = new MockPhase(PhaseType.EVERGREEN, plan, MockDuration.UNLIMITED(), 25.95);
        final UUID subscriptionId = UUID.randomUUID();
        final DateTime effectiveTransitionTime = clock.getUTCNow();
        final DateTime requestedTransitionTime = clock.getUTCNow();
        final String priceList = "something";

        transition = new SubscriptionTransitionData(
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
                true
        );
        expectedTransition = new BusinessSubscriptionTransition(
                ID,
                KEY,
                ACCOUNT_KEY,
                requestedTransitionTime,
                BusinessSubscriptionEvent.subscriptionCreated(plan),
                null,
                new BusinessSubscription(priceList, plan, phase, ACCOUNT_CURRENCY, effectiveTransitionTime, Subscription.SubscriptionState.ACTIVE, subscriptionId, bundle.getId())
        );
    }

    private void createAccountCreationEvent(final Account account) {
        accountCreationNotification = new DefaultAccountCreationEvent(account);
    }

    private void createInvoiceAndPaymentCreationEvents(final Account account) {
        final DefaultInvoice invoice = new DefaultInvoice(account.getId(), clock.getUTCNow(), ACCOUNT_CURRENCY, clock);
        final FixedPriceInvoiceItem invoiceItem = new FixedPriceInvoiceItem(
                UUID.randomUUID(), invoice.getId(), UUID.randomUUID(), "somePlan", "somePhase", clock.getUTCNow(), clock.getUTCNow().plusDays(1),
                INVOICE_AMOUNT, ACCOUNT_CURRENCY, clock.getUTCNow(), clock.getUTCNow()
        );
        invoice.addInvoiceItem(invoiceItem);

        invoiceDao.create(invoice);
        Assert.assertEquals(invoiceDao.getInvoicesByAccount(account.getId()).size(), 1);
        Assert.assertEquals(invoiceDao.getInvoicesByAccount(account.getId()).get(0).getInvoiceItems().size(), 1);

        // It doesn't really matter what the events contain - the listener will go back to the db
        invoiceCreationNotification = new DefaultInvoiceCreationNotification(invoice.getId(), account.getId(),
                INVOICE_AMOUNT, ACCOUNT_CURRENCY, clock.getUTCNow());

        paymentInfoNotification = new PaymentInfo.Builder().setPaymentId(UUID.randomUUID().toString()).setPaymentMethod(PAYMENT_METHOD).setCardCountry(CARD_COUNTRY).build();
        final PaymentAttempt paymentAttempt = new PaymentAttempt(UUID.randomUUID(), invoice.getId(), account.getId(), BigDecimal.TEN,
                ACCOUNT_CURRENCY, clock.getUTCNow(), clock.getUTCNow(), paymentInfoNotification.getPaymentId(), 1);
        paymentDao.createPaymentAttempt(paymentAttempt);
        paymentDao.savePaymentInfo(paymentInfoNotification);
        Assert.assertEquals(paymentDao.getPaymentInfo(Arrays.asList(invoice.getId().toString())).size(), 1);
    }

    @AfterClass(alwaysRun = true)
    public void stopMysql() {
        helper.stopMysql();
    }

    @Test(groups = "slow")
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
        Thread.sleep(1000);

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
        Thread.sleep(1000);
        Assert.assertTrue(accountDao.getAccount(ACCOUNT_KEY).getTotalInvoiceBalance().compareTo(INVOICE_AMOUNT) == 0);

        // Test payment integration - the fields have already been populated, just make sure the code is exercised
        bus.post(paymentInfoNotification);
        Thread.sleep(1000);
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
