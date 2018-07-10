/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
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
import org.killbill.billing.invoice.TestInvoiceHelper.DryRunFutureDateArguments;
import org.killbill.billing.invoice.api.DryRunArguments;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.dao.InvoiceItemModelDao;
import org.killbill.billing.invoice.dao.InvoiceModelDao;
import org.killbill.billing.junction.BillingEventSet;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.util.api.TagDefinitionApiException;
import org.killbill.billing.util.tag.Tag;
import org.killbill.billing.util.tag.dao.SystemTags;
import org.mockito.Mockito;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class TestInvoiceDispatcher extends InvoiceTestSuiteWithEmbeddedDB {

    private Account account;
    private SubscriptionBase subscription;
    private InternalCallContext context;
    private InvoiceDispatcher dispatcher;

    @Override
    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();
        account = invoiceUtil.createAccount(callContext);
        subscription = invoiceUtil.createSubscription();
        context = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);

        dispatcher = new InvoiceDispatcher(generator, accountApi, billingApi, subscriptionApi, invoiceDao,
                                           internalCallContextFactory,  invoicePluginDispatcher, locker, busService.getBus(),
                                           notificationQueueService, invoiceConfig, clock, parkedAccountsManager);

    }

    @Test(groups = "slow")
    public void testDryRunInvoice() throws InvoiceApiException, AccountApiException, CatalogApiException, SubscriptionBaseApiException {
        final UUID accountId = account.getId();

        final BillingEventSet events = new MockBillingEventSet();
        final Plan plan = MockPlan.createBicycleNoTrialEvergreen1USD();
        final PlanPhase planPhase = MockPlanPhase.create1USDMonthlyEvergreen();
        final DateTime effectiveDate = clock.getUTCNow().minusDays(1);
        final Currency currency = Currency.USD;
        final BigDecimal fixedPrice = null;
        events.add(invoiceUtil.createMockBillingEvent(account, subscription, effectiveDate, plan, planPhase,
                                                      fixedPrice, BigDecimal.ONE, currency, BillingPeriod.MONTHLY, 1,
                                                      BillingMode.IN_ADVANCE, "", 1L, SubscriptionBaseTransitionType.CREATE));

        Mockito.when(billingApi.getBillingEventsForAccountAndUpdateAccountBCD(Mockito.<UUID>any(), Mockito.<DryRunArguments>any(), Mockito.<InternalCallContext>any())).thenReturn(events);

        final LocalDate target = internalCallContext.toLocalDate(effectiveDate);

        final InvoiceDispatcher dispatcher = new InvoiceDispatcher(generator, accountApi, billingApi, subscriptionApi, invoiceDao,
                                                                   internalCallContextFactory, invoicePluginDispatcher, locker, busService.getBus(),
                                                                   notificationQueueService, invoiceConfig, clock, parkedAccountsManager);

        Invoice invoice = dispatcher.processAccountFromNotificationOrBusEvent(accountId, target, new DryRunFutureDateArguments(), false, context);
        Assert.assertNotNull(invoice);

        List<InvoiceModelDao> invoices = invoiceDao.getInvoicesByAccount(false, context);
        Assert.assertEquals(invoices.size(), 0);

        // Try it again to double check
        invoice = dispatcher.processAccountFromNotificationOrBusEvent(accountId, target, new DryRunFutureDateArguments(), false, context);
        Assert.assertNotNull(invoice);

        invoices = invoiceDao.getInvoicesByAccount(false, context);
        Assert.assertEquals(invoices.size(), 0);

        // This time no dry run
        invoice = dispatcher.processAccountFromNotificationOrBusEvent(accountId, target, null, false, context);
        Assert.assertNotNull(invoice);

        invoices = invoiceDao.getInvoicesByAccount(false, context);
        Assert.assertEquals(invoices.size(), 1);
    }

    @Test(groups = "slow")
    public void testWithParking() throws InvoiceApiException, AccountApiException, CatalogApiException, SubscriptionBaseApiException, TagDefinitionApiException {
        final UUID accountId = account.getId();

        final BillingEventSet events = new MockBillingEventSet();
        final Plan plan = MockPlan.createBicycleNoTrialEvergreen1USD();
        final PlanPhase planPhase = MockPlanPhase.create1USDMonthlyEvergreen();
        final DateTime effectiveDate = clock.getUTCNow().minusDays(1);
        final Currency currency = Currency.USD;
        final BigDecimal fixedPrice = null;
        events.add(invoiceUtil.createMockBillingEvent(account, subscription, effectiveDate, plan, planPhase,
                                                      fixedPrice, BigDecimal.ONE, currency, BillingPeriod.MONTHLY, 1,
                                                      BillingMode.IN_ADVANCE, "", 1L, SubscriptionBaseTransitionType.CREATE));

        Mockito.when(billingApi.getBillingEventsForAccountAndUpdateAccountBCD(Mockito.<UUID>any(), Mockito.<DryRunArguments>any(), Mockito.<InternalCallContext>any())).thenReturn(events);

        final LocalDate target = internalCallContext.toLocalDate(effectiveDate);

        final InvoiceDispatcher dispatcher = new InvoiceDispatcher(generator, accountApi, billingApi, subscriptionApi, invoiceDao,
                                                                   internalCallContextFactory, invoicePluginDispatcher, locker, busService.getBus(),
                                                                   notificationQueueService, invoiceConfig, clock, parkedAccountsManager);

        // Verify initial tags state for account
        Assert.assertTrue(tagUserApi.getTagsForAccount(accountId, true, callContext).isEmpty());

        // Create chaos on disk
        final InvoiceModelDao invoiceModelDao = new InvoiceModelDao(accountId,
                                                                    target,
                                                                    target,
                                                                    currency,
                                                                    false);
        final InvoiceItemModelDao invoiceItemModelDao1 = new InvoiceItemModelDao(clock.getUTCNow(),
                                                                                 InvoiceItemType.RECURRING,
                                                                                 invoiceModelDao.getId(),
                                                                                 accountId,
                                                                                 subscription.getBundleId(),
                                                                                 subscription.getId(),
                                                                                 "Bad data",
                                                                                 null,
                                                                                 plan.getName(),
                                                                                 planPhase.getName(),
                                                                                 null,
                                                                                 effectiveDate.toLocalDate(),
                                                                                 effectiveDate.plusMonths(1).toLocalDate(),
                                                                                 BigDecimal.TEN,
                                                                                 BigDecimal.ONE,
                                                                                 currency,
                                                                                 null);
        final InvoiceItemModelDao invoiceItemModelDao2 = new InvoiceItemModelDao(clock.getUTCNow(),
                                                                                 InvoiceItemType.RECURRING,
                                                                                 invoiceModelDao.getId(),
                                                                                 accountId,
                                                                                 subscription.getBundleId(),
                                                                                 subscription.getId(),
                                                                                 "Bad data",
                                                                                 null,
                                                                                 plan.getName(),
                                                                                 planPhase.getName(),
                                                                                 null,
                                                                                 effectiveDate.plusDays(1).toLocalDate(),
                                                                                 effectiveDate.plusMonths(1).toLocalDate(),
                                                                                 BigDecimal.TEN,
                                                                                 BigDecimal.ONE,
                                                                                 currency,
                                                                                 null);

        invoiceModelDao.addInvoiceItem(invoiceItemModelDao1);
        invoiceModelDao.addInvoiceItem(invoiceItemModelDao2);
        invoiceDao.createInvoices(ImmutableList.<InvoiceModelDao>of(invoiceModelDao), context);

        try {
            dispatcher.processAccountFromNotificationOrBusEvent(accountId, target, new DryRunFutureDateArguments(), false, context);
            Assert.fail();
        } catch (final InvoiceApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.UNEXPECTED_ERROR.getCode());
            Assert.assertTrue(e.getCause().getMessage().startsWith("Double billing detected"));
        }
        // Dry-run: no side effect on disk
        Assert.assertEquals(invoiceDao.getInvoicesByAccount(false, context).size(), 1);
        Assert.assertTrue(tagUserApi.getTagsForAccount(accountId, true, callContext).isEmpty());

        try {
            dispatcher.processAccountFromNotificationOrBusEvent(accountId, target, null, false, context);
            Assert.fail();
        } catch (final InvoiceApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.UNEXPECTED_ERROR.getCode());
            Assert.assertTrue(e.getCause().getMessage().startsWith("Double billing detected"));
        }
        Assert.assertEquals(invoiceDao.getInvoicesByAccount(false, context).size(), 1);
        // No dry-run: account is parked
        final List<Tag> tags = tagUserApi.getTagsForAccount(accountId, false, callContext);
        Assert.assertEquals(tags.size(), 1);
        Assert.assertEquals(tags.get(0).getTagDefinitionId(), SystemTags.PARK_TAG_DEFINITION_ID);

        // isApiCall=false
        final Invoice nullInvoice1 = dispatcher.processAccountFromNotificationOrBusEvent(accountId, target, null, false, context);
        Assert.assertNull(nullInvoice1);

        // No dry-run and isApiCall=true
        try {
            dispatcher.processAccount(true, accountId, target, null, false, context);
            Assert.fail();
        } catch (final InvoiceApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.UNEXPECTED_ERROR.getCode());
            Assert.assertTrue(e.getCause().getMessage().startsWith("Double billing detected"));
        }
        // Idempotency
        Assert.assertEquals(invoiceDao.getInvoicesByAccount(false, context).size(), 1);
        Assert.assertEquals(tagUserApi.getTagsForAccount(accountId, false, callContext), tags);

        // Fix state
        dbi.withHandle(new HandleCallback<Void>() {
            @Override
            public Void withHandle(final Handle handle) throws Exception {
                handle.execute("delete from invoices");
                handle.execute("delete from invoice_items");
                return null;
            }
        });

        // Dry-run and isApiCall=false: still parked
        final Invoice nullInvoice2 = dispatcher.processAccountFromNotificationOrBusEvent(accountId, target, new DryRunFutureDateArguments(), false, context);
        Assert.assertNull(nullInvoice2);

        // Dry-run and isApiCall=true: call goes through
        final Invoice invoice1 = dispatcher.processAccount(true, accountId, target, new DryRunFutureDateArguments(), false, context);
        Assert.assertNotNull(invoice1);
        Assert.assertEquals(invoiceDao.getInvoicesByAccount(false, context).size(), 0);
        // Dry-run: still parked
        Assert.assertEquals(tagUserApi.getTagsForAccount(accountId, false, callContext).size(), 1);

        // No dry-run and isApiCall=true: call goes through
        final Invoice invoice2 = dispatcher.processAccount(true, accountId, target, null, false, context);
        Assert.assertNotNull(invoice2);
        Assert.assertEquals(invoiceDao.getInvoicesByAccount(false, context).size(), 1);
        // No dry-run: now unparked
        Assert.assertEquals(tagUserApi.getTagsForAccount(accountId, false, callContext).size(), 0);
        Assert.assertEquals(tagUserApi.getTagsForAccount(accountId, true, callContext).size(), 1);
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
        final InvoiceDispatcher dispatcher = new InvoiceDispatcher(generator, accountApi, billingApi, subscriptionApi, invoiceDao,
                                                                   internalCallContextFactory, invoicePluginDispatcher, locker, busService.getBus(),
                                                                   notificationQueueService, invoiceConfig, clock, parkedAccountsManager);
        final Invoice invoice = dispatcher.processAccountFromNotificationOrBusEvent(account.getId(), new LocalDate("2012-07-30"), null, false, context);
        Assert.assertNotNull(invoice);

        final List<InvoiceItem> invoiceItems = invoice.getInvoiceItems();
        Assert.assertEquals(invoiceItems.size(), 4);
        Assert.assertEquals(invoiceItems.get(0).getInvoiceItemType(), InvoiceItemType.FIXED);
        Assert.assertEquals(invoiceItems.get(0).getStartDate(), new LocalDate("2012-05-01"));
        Assert.assertNull(invoiceItems.get(0).getEndDate());
        Assert.assertEquals(invoiceItems.get(0).getAmount().compareTo(BigDecimal.ZERO), 0);
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
}
