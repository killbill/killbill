/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.invoice;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.MockPlan;
import org.killbill.billing.catalog.MockPlanPhase;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.invoice.InvoiceDispatcher.FutureAccountNotifications;
import org.killbill.billing.invoice.InvoiceDispatcher.FutureAccountNotifications.SubscriptionNotification;
import org.killbill.billing.invoice.TestInvoiceHelper.DryRunFutureDateArguments;
import org.killbill.billing.invoice.api.DryRunArguments;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.api.InvoiceNotifier;
import org.killbill.billing.invoice.dao.InvoiceItemModelDao;
import org.killbill.billing.invoice.dao.InvoiceModelDao;
import org.killbill.billing.invoice.notification.NullInvoiceNotifier;
import org.killbill.billing.junction.BillingEventSet;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.util.timezone.DateAndTimeZoneContext;
import org.killbill.clock.ClockMock;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestInvoiceDispatcher extends InvoiceTestSuiteWithEmbeddedDB {

    private Account account;
    private SubscriptionBase subscription;
    private InternalCallContext context;

    @Override
    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        account = invoiceUtil.createAccount(callContext);
        subscription = invoiceUtil.createSubscription();
        context = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
    }

    @Test(groups = "slow")
    public void testDryRunInvoice() throws InvoiceApiException, AccountApiException, CatalogApiException {
        final UUID accountId = account.getId();

        final BillingEventSet events = new MockBillingEventSet();
        final Plan plan = MockPlan.createBicycleNoTrialEvergreen1USD();
        final PlanPhase planPhase = MockPlanPhase.create1USDMonthlyEvergreen();
        final DateTime effectiveDate = new DateTime().minusDays(1);
        final Currency currency = Currency.USD;
        final BigDecimal fixedPrice = null;
        events.add(invoiceUtil.createMockBillingEvent(account, subscription, effectiveDate, plan, planPhase,
                                                      fixedPrice, BigDecimal.ONE, currency, BillingPeriod.MONTHLY, 1,
                                                      BillingMode.IN_ADVANCE, "", 1L, SubscriptionBaseTransitionType.CREATE));

        Mockito.when(billingApi.getBillingEventsForAccountAndUpdateAccountBCD(Mockito.<UUID>any(), Mockito.<DryRunArguments>any(), Mockito.<InternalCallContext>any())).thenReturn(events);

        final DateTime target = new DateTime();

        final InvoiceNotifier invoiceNotifier = new NullInvoiceNotifier();
        final InvoiceDispatcher dispatcher = new InvoiceDispatcher(generator, accountApi, billingApi, subscriptionApi, invoiceDao,
                                                                   internalCallContextFactory, invoiceNotifier, invoicePluginDispatcher, locker, busService.getBus(),
                                                                   null, invoiceConfig, clock);

        Invoice invoice = dispatcher.processAccount(accountId, target, new DryRunFutureDateArguments(), context);
        Assert.assertNotNull(invoice);

        List<InvoiceModelDao> invoices = invoiceDao.getInvoicesByAccount(context);
        Assert.assertEquals(invoices.size(), 0);

        // Try it again to double check
        invoice = dispatcher.processAccount(accountId, target, new DryRunFutureDateArguments(), context);
        Assert.assertNotNull(invoice);

        invoices = invoiceDao.getInvoicesByAccount(context);
        Assert.assertEquals(invoices.size(), 0);

        // This time no dry run
        invoice = dispatcher.processAccount(accountId, target, null, context);
        Assert.assertNotNull(invoice);

        invoices = invoiceDao.getInvoicesByAccount(context);
        Assert.assertEquals(invoices.size(), 1);
    }

    @Test(groups = "slow")
    public void testWithOverdueEvents() throws Exception {
        final BillingEventSet events = new MockBillingEventSet();

        // Initial trial
        final MockPlan bicycleTrialEvergreen1USD = MockPlan.createBicycleTrialEvergreen1USD();
        events.add(invoiceUtil.createMockBillingEvent(account, subscription, new DateTime("2012-05-01T00:03:42.000Z"), bicycleTrialEvergreen1USD,
                                                      new MockPlanPhase(bicycleTrialEvergreen1USD, PhaseType.TRIAL), BigDecimal.ZERO, null, account.getCurrency(), BillingPeriod.NO_BILLING_PERIOD,
                                                      31, BillingMode.IN_ADVANCE, "CREATE", 1L, SubscriptionBaseTransitionType.CREATE));
        // Phase change to evergreen
        events.add(invoiceUtil.createMockBillingEvent(account, subscription, new DateTime("2012-05-31T00:03:42.000Z"), bicycleTrialEvergreen1USD,
                                                      new MockPlanPhase(bicycleTrialEvergreen1USD, PhaseType.EVERGREEN), null, new BigDecimal("249.95"), account.getCurrency(), BillingPeriod.MONTHLY,
                                                      31, BillingMode.IN_ADVANCE, "PHASE", 2L, SubscriptionBaseTransitionType.PHASE));
        // Overdue period
        events.add(invoiceUtil.createMockBillingEvent(account, subscription, new DateTime("2012-07-15T00:00:00.000Z"), bicycleTrialEvergreen1USD,
                                                      new MockPlanPhase(bicycleTrialEvergreen1USD, PhaseType.EVERGREEN), null, null, account.getCurrency(), BillingPeriod.NO_BILLING_PERIOD,
                                                      31, BillingMode.IN_ADVANCE, "", 0L, SubscriptionBaseTransitionType.START_BILLING_DISABLED));
        events.add(invoiceUtil.createMockBillingEvent(account, subscription, new DateTime("2012-07-25T00:00:00.000Z"), bicycleTrialEvergreen1USD,
                                                      new MockPlanPhase(bicycleTrialEvergreen1USD, PhaseType.EVERGREEN), null, new BigDecimal("249.95"), account.getCurrency(), BillingPeriod.MONTHLY,
                                                      31, BillingMode.IN_ADVANCE, "", 1L, SubscriptionBaseTransitionType.END_BILLING_DISABLED));
        // Upgrade after the overdue period
        final MockPlan jetTrialEvergreen1000USD = MockPlan.createJetTrialEvergreen1000USD();
        events.add(invoiceUtil.createMockBillingEvent(account, subscription, new DateTime("2012-07-25T00:04:00.000Z"), jetTrialEvergreen1000USD,
                                                      new MockPlanPhase(jetTrialEvergreen1000USD, PhaseType.EVERGREEN), null, new BigDecimal("1000"), account.getCurrency(), BillingPeriod.MONTHLY,
                                                      31, BillingMode.IN_ADVANCE, "CHANGE", 3L, SubscriptionBaseTransitionType.CHANGE));

        Mockito.when(billingApi.getBillingEventsForAccountAndUpdateAccountBCD(Mockito.<UUID>any(), Mockito.<DryRunArguments>any(), Mockito.<InternalCallContext>any())).thenReturn(events);
        final InvoiceNotifier invoiceNotifier = new NullInvoiceNotifier();
        final InvoiceDispatcher dispatcher = new InvoiceDispatcher(generator, accountApi, billingApi, subscriptionApi, invoiceDao,
                                                                   internalCallContextFactory, invoiceNotifier, invoicePluginDispatcher, locker, busService.getBus(),
                                                                   null, invoiceConfig, clock);

        final Invoice invoice = dispatcher.processAccount(account.getId(), new DateTime("2012-07-30T00:00:00.000Z"), null, context);
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
        Assert.assertEquals(invoiceItems.get(3).getAmount(), new BigDecimal("193.55"));
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

    @Test(groups = "slow")
    public void testCreateNextFutureNotificationDate() throws Exception {

        final LocalDate startDate = new LocalDate("2012-10-26");
        final LocalDate endDate = new LocalDate("2012-11-26");

        ((ClockMock) clock).setTime(new DateTime(2012, 10, 13, 1, 12, 23, DateTimeZone.UTC));

        final DateAndTimeZoneContext dateAndTimeZoneContext = new DateAndTimeZoneContext(clock.getUTCNow(), DateTimeZone.forID("Pacific/Pitcairn"), clock);

        final InvoiceItemModelDao item = new InvoiceItemModelDao(UUID.randomUUID(), clock.getUTCNow(), InvoiceItemType.RECURRING, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                                                                 null, "planName", "phaseName", null, startDate, endDate, new BigDecimal("23.9"), new BigDecimal("23.9"), Currency.EUR, null);

        final InvoiceNotifier invoiceNotifier = new NullInvoiceNotifier();
        final InvoiceDispatcher dispatcher = new InvoiceDispatcher(generator, accountApi, billingApi, subscriptionApi, invoiceDao,
                                                                   internalCallContextFactory, invoiceNotifier, invoicePluginDispatcher, locker, busService.getBus(),
                                                                   null, invoiceConfig, clock);

        final FutureAccountNotifications futureAccountNotifications = dispatcher.createNextFutureNotificationDate(Collections.singletonList(item), null, dateAndTimeZoneContext, context);

        Assert.assertEquals(futureAccountNotifications.getNotifications().size(), 1);

        final List<SubscriptionNotification> receivedDates = futureAccountNotifications.getNotifications().get(item.getSubscriptionId());
        Assert.assertEquals(receivedDates.size(), 1);

        final LocalDate receivedTargetDate = new LocalDate(receivedDates.get(0).getEffectiveDate(), DateTimeZone.forID("Pacific/Pitcairn"));
        Assert.assertEquals(receivedTargetDate, endDate);

        Assert.assertTrue(receivedDates.get(0).getEffectiveDate().compareTo(new DateTime(2012, 11, 27, 1, 12, 23, DateTimeZone.UTC)) <= 0);
    }
}
