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

package com.ning.billing.invoice;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.catalog.MockPlan;
import com.ning.billing.catalog.MockPlanPhase;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.entitlement.api.billing.BillingModeType;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.invoice.api.InvoiceNotifier;
import com.ning.billing.invoice.dao.InvoiceDao;
import com.ning.billing.invoice.generator.InvoiceGenerator;
import com.ning.billing.invoice.notification.NextBillingDateNotifier;
import com.ning.billing.invoice.notification.NullInvoiceNotifier;
import com.ning.billing.invoice.tests.InvoicingTestBase;
import com.ning.billing.junction.api.BillingApi;
import com.ning.billing.junction.api.BillingEventSet;
import com.ning.billing.mock.api.MockBillCycleDay;
import com.ning.billing.util.bus.BusService;
import com.ning.billing.util.bus.DefaultBusService;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.DefaultCallContextFactory;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.globallocker.GlobalLocker;

import com.google.inject.Inject;

@Guice(modules = {MockModule.class})
public class TestInvoiceDispatcher extends InvoicingTestBase {

    private final Logger log = LoggerFactory.getLogger(TestInvoiceDispatcher.class);

    @Inject
    private InvoiceGenerator generator;

    @Inject
    private InvoiceDao invoiceDao;

    @Inject
    private GlobalLocker locker;

    @Inject
    private MysqlTestingHelper helper;

    @Inject
    private NextBillingDateNotifier notifier;

    @Inject
    private BusService busService;

    @Inject
    private BillingApi billingApi;

    @Inject
    private Clock clock;

    private CallContext context;
    private AccountUserApi accountUserApi;
    private Account account;
    private Subscription subscription;

    @BeforeSuite(groups = "slow")
    public void setup() throws Exception {
        notifier.initialize();
        notifier.start();

        context = new DefaultCallContextFactory(clock).createCallContext("Miracle Max", CallOrigin.TEST, UserType.TEST);

        busService.getBus().start();

        accountUserApi = Mockito.mock(AccountUserApi.class);
        account = Mockito.mock(Account.class);

        final UUID accountId = UUID.randomUUID();
        Mockito.when(accountUserApi.getAccountById(accountId)).thenReturn(account);
        Mockito.when(account.getCurrency()).thenReturn(Currency.USD);
        Mockito.when(account.getId()).thenReturn(accountId);
        Mockito.when(account.isNotifiedForInvoices()).thenReturn(true);
        Mockito.when(account.getBillCycleDay()).thenReturn(new MockBillCycleDay(30));

        subscription = Mockito.mock(Subscription.class);
        final UUID subscriptionId = UUID.randomUUID();
        Mockito.when(subscription.getId()).thenReturn(subscriptionId);
        Mockito.when(subscription.getBundleId()).thenReturn(new UUID(0L, 0L));
    }

    @AfterClass(groups = "slow")
    public void tearDown() {
        try {
            ((DefaultBusService) busService).stopBus();
            notifier.stop();
        } catch (Exception e) {
            log.warn("Failed to tearDown test properly ", e);
        }
    }

    @Test(groups = "slow")
    public void testDryRunInvoice() throws InvoiceApiException, AccountApiException {
        final UUID accountId = account.getId();

        final BillingEventSet events = new MockBillingEventSet();
        final Plan plan = MockPlan.createBicycleNoTrialEvergreen1USD();
        final PlanPhase planPhase = MockPlanPhase.create1USDMonthlyEvergreen();
        final DateTime effectiveDate = new DateTime().minusDays(1);
        final Currency currency = Currency.USD;
        final BigDecimal fixedPrice = null;
        events.add(createMockBillingEvent(account, subscription, effectiveDate, plan, planPhase,
                                          fixedPrice, BigDecimal.ONE, currency, BillingPeriod.MONTHLY, 1,
                                          BillingModeType.IN_ADVANCE, "", 1L, SubscriptionTransitionType.CREATE));

        Mockito.when(billingApi.getBillingEventsForAccountAndUpdateAccountBCD(accountId)).thenReturn(events);

        final DateTime target = new DateTime();

        final InvoiceNotifier invoiceNotifier = new NullInvoiceNotifier();
        final InvoiceDispatcher dispatcher = new InvoiceDispatcher(generator, accountUserApi, billingApi, invoiceDao,
                                                                   invoiceNotifier, locker, busService.getBus(), clock);

        Invoice invoice = dispatcher.processAccount(accountId, target, true, context);
        Assert.assertNotNull(invoice);

        List<Invoice> invoices = invoiceDao.getInvoicesByAccount(accountId);
        Assert.assertEquals(invoices.size(), 0);

        // Try it again to double check
        invoice = dispatcher.processAccount(accountId, target, true, context);
        Assert.assertNotNull(invoice);

        invoices = invoiceDao.getInvoicesByAccount(accountId);
        Assert.assertEquals(invoices.size(), 0);

        // This time no dry run
        invoice = dispatcher.processAccount(accountId, target, false, context);
        Assert.assertNotNull(invoice);

        invoices = invoiceDao.getInvoicesByAccount(accountId);
        Assert.assertEquals(invoices.size(), 1);
    }

    @Test(groups = "slow")
    public void testWithOverdueEvents() throws Exception {
        final BillingEventSet events = new MockBillingEventSet();

        // Initial trial
        final MockPlan bicycleTrialEvergreen1USD = MockPlan.createBicycleTrialEvergreen1USD();
        events.add(createMockBillingEvent(account, subscription, new DateTime("2012-05-01T00:03:42.000Z"), bicycleTrialEvergreen1USD,
                                          new MockPlanPhase(bicycleTrialEvergreen1USD, PhaseType.TRIAL), BigDecimal.ZERO, null, account.getCurrency(), BillingPeriod.NO_BILLING_PERIOD,
                                          31, 31, BillingModeType.IN_ADVANCE, "CREATE", 1L, SubscriptionTransitionType.CREATE));
        // Phase change to evergreen
        events.add(createMockBillingEvent(account, subscription, new DateTime("2012-05-31T00:03:42.000Z"), bicycleTrialEvergreen1USD,
                                          new MockPlanPhase(bicycleTrialEvergreen1USD, PhaseType.EVERGREEN), null, new BigDecimal("249.95"), account.getCurrency(), BillingPeriod.MONTHLY,
                                          31, 31, BillingModeType.IN_ADVANCE, "PHASE", 2L, SubscriptionTransitionType.PHASE));
        // Overdue period
        events.add(createMockBillingEvent(account, subscription, new DateTime("2012-07-15T00:00:00.000Z"), bicycleTrialEvergreen1USD,
                                          new MockPlanPhase(bicycleTrialEvergreen1USD, PhaseType.EVERGREEN), null, null, account.getCurrency(), BillingPeriod.NO_BILLING_PERIOD,
                                          31, 31, BillingModeType.IN_ADVANCE, "", 0L, SubscriptionTransitionType.START_BILLING_DISABLED));
        events.add(createMockBillingEvent(account, subscription, new DateTime("2012-07-25T00:00:00.000Z"), bicycleTrialEvergreen1USD,
                                          new MockPlanPhase(bicycleTrialEvergreen1USD, PhaseType.EVERGREEN), null, new BigDecimal("249.95"), account.getCurrency(), BillingPeriod.MONTHLY,
                                          31, 31, BillingModeType.IN_ADVANCE, "", 1L, SubscriptionTransitionType.END_BILLING_DISABLED));
        // Upgrade after the overdue period
        final MockPlan jetTrialEvergreen1000USD = MockPlan.createJetTrialEvergreen1000USD();
        events.add(createMockBillingEvent(account, subscription, new DateTime("2012-07-25T00:04:00.000Z"), jetTrialEvergreen1000USD,
                                          new MockPlanPhase(jetTrialEvergreen1000USD, PhaseType.EVERGREEN), null, new BigDecimal("1000"), account.getCurrency(), BillingPeriod.MONTHLY,
                                          31, 31, BillingModeType.IN_ADVANCE, "CHANGE", 3L, SubscriptionTransitionType.CHANGE));

        Mockito.when(billingApi.getBillingEventsForAccountAndUpdateAccountBCD(account.getId())).thenReturn(events);
        final InvoiceNotifier invoiceNotifier = new NullInvoiceNotifier();
        final InvoiceDispatcher dispatcher = new InvoiceDispatcher(generator, accountUserApi, billingApi, invoiceDao,
                                                                   invoiceNotifier, locker, busService.getBus(), clock);

        final Invoice invoice = dispatcher.processAccount(account.getId(), new DateTime("2012-07-31T00:00:00.000Z"), false, context);
        Assert.assertNotNull(invoice);

        final List<InvoiceItem> invoiceItems = invoice.getInvoiceItems();
        Assert.assertEquals(invoiceItems.size(), 4);
        Assert.assertEquals(invoiceItems.get(0).getInvoiceItemType(), InvoiceItemType.FIXED);
        Assert.assertEquals(invoiceItems.get(0).getStartDate(), new LocalDate("2012-05-01"));
        Assert.assertNull(invoiceItems.get(0).getEndDate());
        Assert.assertEquals(invoiceItems.get(0).getAmount(), BigDecimal.ZERO);
        Assert.assertNull(invoiceItems.get(0).getRate());

        Assert.assertEquals(invoiceItems.get(1).getInvoiceItemType(), InvoiceItemType.RECURRING);
        Assert.assertEquals(invoiceItems.get(1).getStartDate(), new LocalDate("2012-05-31"));
        Assert.assertEquals(invoiceItems.get(1).getEndDate(), new LocalDate("2012-06-30"));
        Assert.assertEquals(invoiceItems.get(1).getAmount(), new BigDecimal("249.95"));
        Assert.assertEquals(invoiceItems.get(1).getRate(), new BigDecimal("249.95"));

        Assert.assertEquals(invoiceItems.get(2).getInvoiceItemType(), InvoiceItemType.RECURRING);
        Assert.assertEquals(invoiceItems.get(2).getStartDate(), new LocalDate("2012-06-30"));
        Assert.assertEquals(invoiceItems.get(2).getEndDate(), new LocalDate("2012-07-15"));
        Assert.assertEquals(invoiceItems.get(2).getAmount(), new BigDecimal("124.98"));
        Assert.assertEquals(invoiceItems.get(2).getRate(), new BigDecimal("249.95"));

        Assert.assertEquals(invoiceItems.get(3).getInvoiceItemType(), InvoiceItemType.RECURRING);
        Assert.assertEquals(invoiceItems.get(3).getStartDate(), new LocalDate("2012-07-25"));
        Assert.assertEquals(invoiceItems.get(3).getEndDate(), new LocalDate("2012-07-31"));
        Assert.assertEquals(invoiceItems.get(3).getAmount(), new BigDecimal("193.50"));
        Assert.assertEquals(invoiceItems.get(3).getRate(), new BigDecimal("1000"));

        // Verify common fields
        for (final InvoiceItem item : invoiceItems) {
            Assert.assertEquals(item.getAccountId(), account.getId());
            Assert.assertEquals(item.getBundleId(), subscription.getBundleId());
            Assert.assertEquals(item.getCurrency(), account.getCurrency());
            Assert.assertEquals(item.getInvoiceId(), invoice.getId());
            Assert.assertNull(item.getLinkedItemId());
            Assert.assertEquals(item.getSubscriptionId(), subscription.getId());
        }
    }

    //MDW add a test to cover when the account auto-invoice-off tag is present
}
