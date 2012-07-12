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

package com.ning.billing.invoice.api.migration;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.catalog.MockPlan;
import com.ning.billing.catalog.MockPlanPhase;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.entitlement.api.billing.BillingModeType;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.invoice.InvoiceDispatcher;
import com.ning.billing.invoice.MockBillingEventSet;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceMigrationApi;
import com.ning.billing.invoice.api.InvoiceNotifier;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.invoice.dao.InvoiceDao;
import com.ning.billing.invoice.generator.InvoiceGenerator;
import com.ning.billing.invoice.notification.NullInvoiceNotifier;
import com.ning.billing.invoice.tests.InvoicingTestBase;
import com.ning.billing.junction.api.BillingApi;
import com.ning.billing.junction.api.BillingEventSet;
import com.ning.billing.util.bus.BusService;
import com.ning.billing.util.bus.DefaultBusService;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.DefaultCallContextFactory;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.globallocker.GlobalLocker;

@Guice(modules = {MockModuleNoEntitlement.class})
public class TestDefaultInvoiceMigrationApi extends InvoicingTestBase {
    private final Logger log = LoggerFactory.getLogger(TestDefaultInvoiceMigrationApi.class);

    @Inject
    InvoiceUserApi invoiceUserApi;

    @Inject
    InvoicePaymentApi invoicePaymentApi;

    @Inject
    private InvoiceGenerator generator;

    @Inject
    private InvoiceDao invoiceDao;

    @Inject
    private GlobalLocker locker;

    @Inject
    private BusService busService;

    @Inject
    private InvoiceMigrationApi migrationApi;

    @Inject
    private BillingApi billingApi;

    @Inject
    private AccountUserApi accountUserApi;

    private Account account;
    private UUID accountId;
    private UUID subscriptionId;
    private DateTime date_migrated;
    private DateTime date_regular;

    private UUID migrationInvoiceId;
    private UUID regularInvoiceId;

    private static final BigDecimal MIGRATION_INVOICE_AMOUNT = new BigDecimal("100.00");
    private static final Currency MIGRATION_INVOICE_CURRENCY = Currency.USD;

    private final Clock clock = new ClockMock();

    @BeforeSuite(groups = "slow")
    public void setup() throws Exception {
        busService.getBus().start();
    }

    @BeforeMethod(groups = "slow")
    public void setupMethod() throws Exception {
        accountId = UUID.randomUUID();
        subscriptionId = UUID.randomUUID();
        date_migrated = clock.getUTCNow().minusYears(1);
        date_regular = clock.getUTCNow();

        account = Mockito.mock(Account.class);
        Mockito.when(accountUserApi.getAccountById(accountId)).thenReturn(account);
        Mockito.when(account.getCurrency()).thenReturn(Currency.USD);
        Mockito.when(account.getId()).thenReturn(accountId);
        Mockito.when(account.isNotifiedForInvoices()).thenReturn(true);
        Mockito.when(account.getBillCycleDay()).thenReturn(31);

        migrationInvoiceId = createAndCheckMigrationInvoice();
        regularInvoiceId = generateRegularInvoice();
    }

    @AfterSuite(groups = "slow")
    public void tearDown() {
        try {
            ((DefaultBusService) busService).stopBus();
        } catch (Exception e) {
            log.warn("Failed to tearDown test properly ", e);
        }
    }

    private UUID createAndCheckMigrationInvoice() {
        final UUID migrationInvoiceId = migrationApi.createMigrationInvoice(accountId, date_migrated, MIGRATION_INVOICE_AMOUNT, MIGRATION_INVOICE_CURRENCY);
        Assert.assertNotNull(migrationInvoiceId);
        //Double check it was created and values are correct

        final Invoice invoice = invoiceDao.getById(migrationInvoiceId);
        Assert.assertNotNull(invoice);

        Assert.assertEquals(invoice.getAccountId(), accountId);
        Assert.assertEquals(invoice.getTargetDate().compareTo(date_migrated), 0); //temp to avoid tz test artifact
        //		Assert.assertEquals(invoice.getTargetDate(),now);
        Assert.assertEquals(invoice.getNumberOfItems(), 1);
        Assert.assertEquals(invoice.getInvoiceItems().get(0).getAmount().compareTo(MIGRATION_INVOICE_AMOUNT), 0);
        Assert.assertEquals(invoice.getBalance().compareTo(MIGRATION_INVOICE_AMOUNT), 0);
        Assert.assertEquals(invoice.getCurrency(), MIGRATION_INVOICE_CURRENCY);
        Assert.assertTrue(invoice.isMigrationInvoice());

        return migrationInvoiceId;
    }

    private UUID generateRegularInvoice() throws Exception {
        final Subscription subscription = Mockito.mock(Subscription.class);
        Mockito.when(subscription.getId()).thenReturn(subscriptionId);
        Mockito.when(subscription.getBundleId()).thenReturn(new UUID(0L, 0L));
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

        final InvoiceNotifier invoiceNotifier = new NullInvoiceNotifier();
        final InvoiceDispatcher dispatcher = new InvoiceDispatcher(generator, accountUserApi, billingApi,
                                                                   invoiceDao, invoiceNotifier, locker, busService.getBus(), clock);

        final CallContext context = new DefaultCallContextFactory(clock).createCallContext("Migration test", CallOrigin.TEST, UserType.TEST);
        Invoice invoice = dispatcher.processAccount(accountId, date_regular, true, context);
        Assert.assertNotNull(invoice);

        List<Invoice> invoices = invoiceDao.getInvoicesByAccount(accountId);
        Assert.assertEquals(invoices.size(), 0);

        invoice = dispatcher.processAccount(accountId, date_regular, false, context);
        Assert.assertNotNull(invoice);

        invoices = invoiceDao.getInvoicesByAccount(accountId);
        Assert.assertEquals(invoices.size(), 1);

        return invoice.getId();
    }

    @Test(groups = "slow")
    public void testUserApiAccess() {
        final List<Invoice> byAccount = invoiceUserApi.getInvoicesByAccount(accountId);
        Assert.assertEquals(byAccount.size(), 1);
        Assert.assertEquals(byAccount.get(0).getId(), regularInvoiceId);

        final List<Invoice> byAccountAndDate = invoiceUserApi.getInvoicesByAccount(accountId, date_migrated.minusDays(1));
        Assert.assertEquals(byAccountAndDate.size(), 1);
        Assert.assertEquals(byAccountAndDate.get(0).getId(), regularInvoiceId);

        final Collection<Invoice> unpaid = invoiceUserApi.getUnpaidInvoicesByAccountId(accountId, date_regular.plusDays(1));
        Assert.assertEquals(unpaid.size(), 2);
    }

    // Check migration invoice IS returned for payment api calls
    @Test(groups = "slow")
    public void testPaymentApi() {
        final List<Invoice> allByAccount = invoicePaymentApi.getAllInvoicesByAccount(accountId);
        Assert.assertEquals(allByAccount.size(), 2);
        Assert.assertTrue(checkContains(allByAccount, regularInvoiceId));
        Assert.assertTrue(checkContains(allByAccount, migrationInvoiceId));
    }

    // ACCOUNT balance should reflect total of migration and non-migration invoices
    @Test(groups = "slow")
    public void testBalance() {
        final Invoice migrationInvoice = invoiceDao.getById(migrationInvoiceId);
        final Invoice regularInvoice = invoiceDao.getById(regularInvoiceId);
        final BigDecimal balanceOfAllInvoices = migrationInvoice.getBalance().add(regularInvoice.getBalance());

        final BigDecimal accountBalance = invoiceUserApi.getAccountBalance(accountId);
        log.info("ACCOUNT balance: " + accountBalance + " should equal the Balance Of All Invoices: " + balanceOfAllInvoices);
        Assert.assertEquals(accountBalance.compareTo(balanceOfAllInvoices), 0);
    }

    private boolean checkContains(final List<Invoice> invoices, final UUID invoiceId) {
        for (final Invoice invoice : invoices) {
            if (invoice.getId().equals(invoiceId)) {
                return true;
            }
        }
        return false;
    }
}
