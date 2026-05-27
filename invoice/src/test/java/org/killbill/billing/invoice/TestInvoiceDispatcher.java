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
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
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
import org.killbill.billing.catalog.api.TimeUnit;
import org.killbill.billing.invoice.TestInvoiceHelper.DryRunFutureDateArguments;
import org.killbill.billing.invoice.api.DryRunArguments;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.api.InvoiceStatus;
import org.killbill.billing.invoice.dao.InvoiceItemModelDao;
import org.killbill.billing.invoice.dao.InvoiceModelDao;
import org.killbill.billing.invoice.model.DefaultInvoice;
import org.killbill.billing.invoice.model.RecurringInvoiceItem;
import org.killbill.billing.junction.BillingEventSet;
import org.killbill.billing.mock.MockAccountBuilder;
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
                                           internalCallContextFactory,  invoicePluginDispatcher, locker, bus,
                                           notificationQueueService, invoiceConfig, clock, invoiceOptimizer, parkedAccountsManager);

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

        Mockito.when(billingApi.getBillingEventsForAccountAndUpdateAccountBCD(Mockito.<UUID>any(), Mockito.<DryRunArguments>any(),  Mockito.<LocalDate>any(), Mockito.<InternalCallContext>any())).thenReturn(events);

        final LocalDate target = internalCallContext.toLocalDate(effectiveDate);

        final InvoiceDispatcher dispatcher = new InvoiceDispatcher(generator, accountApi, billingApi, subscriptionApi, invoiceDao,
                                                                   internalCallContextFactory, invoicePluginDispatcher, locker, bus,
                                                                   notificationQueueService, invoiceConfig, clock, invoiceOptimizer, parkedAccountsManager);

        Invoice invoice = processAccountFromNotificationOrBusEventAndAssertResult(accountId, target, new DryRunFutureDateArguments(), false, context);

        List<InvoiceModelDao> invoices = invoiceDao.getInvoicesByAccount(false, true, context);
        Assert.assertEquals(invoices.size(), 0);

        // Try it again to double check
        invoice = processAccountFromNotificationOrBusEventAndAssertResult(accountId, target, new DryRunFutureDateArguments(), false, context);

        invoices = invoiceDao.getInvoicesByAccount(false, true, context);
        Assert.assertEquals(invoices.size(), 0);

        // This time no dry run
        invoice = processAccountFromNotificationOrBusEventAndAssertResult(accountId, target, null, false, context);

        invoices = invoiceDao.getInvoicesByAccount(false, true, context);
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

        Mockito.when(billingApi.getBillingEventsForAccountAndUpdateAccountBCD(Mockito.<UUID>any(), Mockito.<DryRunArguments>any(), Mockito.<LocalDate>any(), Mockito.<InternalCallContext>any())).thenReturn(events);

        final LocalDate target = internalCallContext.toLocalDate(effectiveDate);

        final InvoiceDispatcher dispatcher = new InvoiceDispatcher(generator, accountApi, billingApi, subscriptionApi, invoiceDao,
                                                                   internalCallContextFactory, invoicePluginDispatcher, locker, bus,
                                                                   notificationQueueService, invoiceConfig, clock, invoiceOptimizer, parkedAccountsManager);

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
                                                                                 null,
                                                                                 effectiveDate.plusDays(1).toLocalDate(),
                                                                                 effectiveDate.plusMonths(1).toLocalDate(),
                                                                                 BigDecimal.TEN,
                                                                                 BigDecimal.ONE,
                                                                                 currency,
                                                                                 null);

        invoiceModelDao.addInvoiceItem(invoiceItemModelDao1);
        invoiceModelDao.addInvoiceItem(invoiceItemModelDao2);
        invoiceDao.createInvoices(List.of(invoiceModelDao), events, Collections.emptySet(), null, null, false, context);

        try {
            dispatcher.processAccountFromNotificationOrBusEvent(accountId, target, new DryRunFutureDateArguments(), false, context);
            Assert.fail();
        } catch (final InvoiceApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.UNEXPECTED_ERROR.getCode());
            Assert.assertTrue(e.getCause().getMessage().startsWith("Double billing detected"));
        }
        // Dry-run: no side effect on disk
        Assert.assertEquals(invoiceDao.getInvoicesByAccount(false, true, context).size(), 1);
        Assert.assertTrue(tagUserApi.getTagsForAccount(accountId, true, callContext).isEmpty());

        try {
            dispatcher.processAccountFromNotificationOrBusEvent(accountId, target, null, false, context);
            Assert.fail();
        } catch (final InvoiceApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.UNEXPECTED_ERROR.getCode());
            Assert.assertTrue(e.getCause().getMessage().startsWith("Double billing detected"));
        }
        Assert.assertEquals(invoiceDao.getInvoicesByAccount(false, true, context).size(), 1);
        // No dry-run: account is parked
        final List<Tag> tags = tagUserApi.getTagsForAccount(accountId, false, callContext);
        Assert.assertEquals(tags.size(), 1);
        Assert.assertEquals(tags.get(0).getTagDefinitionId(), SystemTags.PARK_TAG_DEFINITION_ID);

        // isApiCall=false
        final List<Invoice> emptyInvoices = dispatcher.processAccountFromNotificationOrBusEvent(accountId, target, null, false, context);
        Assert.assertTrue(emptyInvoices.isEmpty());

        // No dry-run and isApiCall=true
        try {
            dispatcher.processAccount(true, accountId, target, null, false, false, Collections.emptyList(), context);
            Assert.fail();
        } catch (final InvoiceApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.UNEXPECTED_ERROR.getCode());
            Assert.assertTrue(e.getCause().getMessage().startsWith("Double billing detected"));
        }
        // Idempotency
        Assert.assertEquals(invoiceDao.getInvoicesByAccount(false, true, context).size(), 1);
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
        final List<Invoice> emptyInvoices2 = dispatcher.processAccountFromNotificationOrBusEvent(accountId, target, new DryRunFutureDateArguments(), false, context);
        Assert.assertTrue(emptyInvoices2.isEmpty());

        // Dry-run and isApiCall=true: call goes through
        final List<Invoice> invoices1 = dispatcher.processAccount(true, accountId, target, new DryRunFutureDateArguments(), false, false, Collections.emptyList(), context);
        Assert.assertFalse(invoices1.isEmpty());
        Assert.assertEquals(invoiceDao.getInvoicesByAccount(false, true, context).size(), 0);
        // Dry-run: still parked
        Assert.assertEquals(tagUserApi.getTagsForAccount(accountId, false, callContext).size(), 1);

        // No dry-run and isApiCall=true: call goes through
        final List<Invoice> invoice2s = dispatcher.processAccount(true, accountId, target, null, false, false, Collections.emptyList(), context);
        Assert.assertFalse(invoice2s.isEmpty());
        Assert.assertEquals(invoiceDao.getInvoicesByAccount(false, true, context).size(), 1);
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
                                                      new MockPlanPhase(bicycleTrialEvergreen1USD, PhaseType.TRIAL, 30, TimeUnit.DAYS), BigDecimal.ZERO, null, account.getCurrency(), BillingPeriod.NO_BILLING_PERIOD,
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

        Mockito.when(billingApi.getBillingEventsForAccountAndUpdateAccountBCD(Mockito.<UUID>any(), Mockito.<DryRunArguments>any(), Mockito.<LocalDate>any(), Mockito.<InternalCallContext>any())).thenReturn(events);
        final InvoiceDispatcher dispatcher = new InvoiceDispatcher(generator, accountApi, billingApi, subscriptionApi, invoiceDao,
                                                                   internalCallContextFactory, invoicePluginDispatcher, locker, bus,
                                                                   notificationQueueService, invoiceConfig, clock, invoiceOptimizer, parkedAccountsManager);
        final Invoice invoice = processAccountFromNotificationOrBusEventAndAssertResult(account.getId(), new LocalDate("2012-07-30"), null, false, context);
        Assert.assertNotNull(invoice);

        final List<InvoiceItem> invoiceItems = invoice.getInvoiceItems();
        Assert.assertEquals(invoiceItems.size(), 4);
        Assert.assertEquals(invoiceItems.get(0).getInvoiceItemType(), InvoiceItemType.FIXED);
        Assert.assertEquals(invoiceItems.get(0).getStartDate(), new LocalDate("2012-05-01"));
        Assert.assertEquals(invoiceItems.get(0).getEndDate(), new LocalDate("2012-05-31"));
        Assert.assertEquals(invoiceItems.get(0).getAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertNull(invoiceItems.get(0).getRate());

        Assert.assertEquals(invoiceItems.get(1).getInvoiceItemType(), InvoiceItemType.RECURRING);
        Assert.assertEquals(invoiceItems.get(1).getStartDate(), new LocalDate("2012-05-31"));
        Assert.assertEquals(invoiceItems.get(1).getEndDate(), new LocalDate("2012-06-30"));
        Assert.assertEquals(invoiceItems.get(1).getAmount().compareTo(new BigDecimal("249.95")), 0);
        Assert.assertEquals(invoiceItems.get(1).getRate().compareTo(new BigDecimal("249.95")), 0);

        Assert.assertEquals(invoiceItems.get(2).getInvoiceItemType(), InvoiceItemType.RECURRING);
        Assert.assertEquals(invoiceItems.get(2).getStartDate(), new LocalDate("2012-06-30"));
        Assert.assertEquals(invoiceItems.get(2).getEndDate(), new LocalDate("2012-07-15"));
        Assert.assertEquals(invoiceItems.get(2).getAmount().compareTo(new BigDecimal("124.98")), 0);
        Assert.assertEquals(invoiceItems.get(2).getRate().compareTo(new BigDecimal("249.95")), 0);

        Assert.assertEquals(invoiceItems.get(3).getInvoiceItemType(), InvoiceItemType.RECURRING);
        Assert.assertEquals(invoiceItems.get(3).getStartDate(), new LocalDate("2012-07-25"));
        Assert.assertEquals(invoiceItems.get(3).getEndDate(), new LocalDate("2012-07-31"));
        Assert.assertEquals(invoiceItems.get(3).getAmount().compareTo(new BigDecimal("193.55")), 0);
        Assert.assertEquals(invoiceItems.get(3).getRate().compareTo(new BigDecimal("1000")), 0);

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

    private Invoice processAccountFromNotificationOrBusEventAndAssertResult(final UUID accountId,
                                                                            @Nullable final LocalDate targetDate,
                                                                            @Nullable final DryRunArguments dryRunArguments,
                                                                            final boolean isRescheduled,
                                                                            final InternalCallContext context) throws InvoiceApiException {
        final List<Invoice> invoices = dispatcher.processAccountFromNotificationOrBusEvent(accountId, targetDate, dryRunArguments, isRescheduled, context);
        Assert.assertEquals(invoices.size(), 1);
        return invoices.get(0);
    }

    // Regression test for https://github.com/killbill/killbill/issues/1922
    //
    // When the parent DRAFT invoice generated for a prior billing cycle has not yet been committed (e.g. the
    // auto-commit notification was delayed or disabled) and a new cycle's child invoices arrive, the previous
    // behaviour blindly merged the new amounts into the prior cycle's PARENT_SUMMARY items. The result was that
    // no parent invoice was generated for the new cycle and amounts from different periods collapsed onto the
    // same items. After the fix, a stale parent DRAFT is left alone and a new parent DRAFT is created for the
    // current cycle.
    @Test(groups = "slow")
    public void testParentInvoiceNotMergedAcrossBillingCycles() throws Exception {
        final DateTime cycle1Clock = new DateTime(2025, 8, 1, 10, 0, 0, 0, DateTimeZone.UTC);
        clock.setTime(cycle1Clock);

        final Account parentAccount = createParentOrChildAccount(null, false);
        final Account child1 = createParentOrChildAccount(parentAccount.getId(), true);
        final Account child2 = createParentOrChildAccount(parentAccount.getId(), true);
        final Account child3 = createParentOrChildAccount(parentAccount.getId(), true);

        // Cycle 1 (August): each child commits an invoice and we run the parent-summary dispatch on it.
        final UUID child1Inv1 = persistCommittedChildInvoice(child1, new BigDecimal("10.00"), new LocalDate(2025, 8, 1));
        final UUID child2Inv1 = persistCommittedChildInvoice(child2, new BigDecimal("20.00"), new LocalDate(2025, 8, 1));
        final UUID child3Inv1 = persistCommittedChildInvoice(child3, new BigDecimal("30.00"), new LocalDate(2025, 8, 1));

        dispatcher.processParentInvoiceForInvoiceGeneration(child1, child1Inv1, contextFor(child1));
        dispatcher.processParentInvoiceForInvoiceGeneration(child2, child2Inv1, contextFor(child2));
        dispatcher.processParentInvoiceForInvoiceGeneration(child3, child3Inv1, contextFor(child3));

        final InternalCallContext parentContext = contextFor(parentAccount);
        List<InvoiceModelDao> parentInvoices = invoiceDao.getInvoicesByAccount(false, true, parentContext);
        Assert.assertEquals(parentInvoices.size(), 1, "Cycle 1 should produce exactly one parent DRAFT invoice");
        Assert.assertEquals(parentInvoices.get(0).getStatus(), InvoiceStatus.DRAFT);
        Assert.assertEquals(parentInvoices.get(0).getInvoiceItems().size(), 3, "Cycle 1 parent DRAFT should hold one PARENT_SUMMARY per child");
        Assert.assertEquals(totalAmount(parentInvoices.get(0)).compareTo(new BigDecimal("60.00")), 0);

        // Simulate the August parent DRAFT auto-commit being delayed/disabled (no commit happens) before September.
        final DateTime cycle2Clock = new DateTime(2025, 9, 1, 10, 0, 0, 0, DateTimeZone.UTC);
        clock.setTime(cycle2Clock);

        // Cycle 2 (September): same children commit a new invoice each.
        final UUID child1Inv2 = persistCommittedChildInvoice(child1, new BigDecimal("11.00"), new LocalDate(2025, 9, 1));
        final UUID child2Inv2 = persistCommittedChildInvoice(child2, new BigDecimal("22.00"), new LocalDate(2025, 9, 1));
        final UUID child3Inv2 = persistCommittedChildInvoice(child3, new BigDecimal("33.00"), new LocalDate(2025, 9, 1));

        dispatcher.processParentInvoiceForInvoiceGeneration(child1, child1Inv2, contextFor(child1));
        dispatcher.processParentInvoiceForInvoiceGeneration(child2, child2Inv2, contextFor(child2));
        dispatcher.processParentInvoiceForInvoiceGeneration(child3, child3Inv2, contextFor(child3));

        parentInvoices = invoiceDao.getInvoicesByAccount(false, true, contextFor(parentAccount));
        Assert.assertEquals(parentInvoices.size(), 2,
                            "Cycle 2 must produce a separate parent DRAFT for the new billing period (not merge into the prior cycle's DRAFT)");

        final InvoiceModelDao augustParent = parentInvoices.stream()
                                                           .filter(i -> new LocalDate(2025, 8, 1).equals(i.getInvoiceDate()))
                                                           .findFirst()
                                                           .orElseThrow(() -> new AssertionError("Cycle 1 parent DRAFT missing"));
        Assert.assertEquals(augustParent.getInvoiceItems().size(), 3, "Cycle 1 parent items must not have been removed or duplicated");
        Assert.assertEquals(totalAmount(augustParent).compareTo(new BigDecimal("60.00")), 0,
                            "Cycle 1 amounts must not be polluted by Cycle 2");

        final InvoiceModelDao septemberParent = parentInvoices.stream()
                                                              .filter(i -> new LocalDate(2025, 9, 1).equals(i.getInvoiceDate()))
                                                              .findFirst()
                                                              .orElseThrow(() -> new AssertionError("Cycle 2 parent DRAFT was not generated"));
        Assert.assertEquals(septemberParent.getStatus(), InvoiceStatus.DRAFT);
        Assert.assertEquals(septemberParent.getInvoiceItems().size(), 3,
                            "Cycle 2 parent DRAFT must contain one PARENT_SUMMARY per child");
        Assert.assertEquals(totalAmount(septemberParent).compareTo(new BigDecimal("66.00")), 0);

        for (final InvoiceItemModelDao item : septemberParent.getInvoiceItems()) {
            Assert.assertEquals(item.getType(), InvoiceItemType.PARENT_SUMMARY);
            Assert.assertNotNull(item.getChildAccountId());
        }
    }

    private Account createParentOrChildAccount(@Nullable final UUID parentAccountId, final boolean isPaymentDelegatedToParent) throws AccountApiException {
        final MockAccountBuilder builder = new MockAccountBuilder()
                .name(UUID.randomUUID().toString().substring(0, 8))
                .firstNameLength(6)
                .email(UUID.randomUUID().toString().substring(0, 8))
                .phone(UUID.randomUUID().toString().substring(0, 8))
                .migrated(false)
                .externalKey(UUID.randomUUID().toString().substring(0, 8))
                .billingCycleDayLocal(1)
                .currency(Currency.USD)
                .paymentMethodId(UUID.randomUUID())
                .timeZone(DateTimeZone.UTC)
                .createdDate(clock.getUTCNow());
        if (parentAccountId != null) {
            builder.parentAccountId(parentAccountId);
            builder.isPaymentDelegatedToParent(isPaymentDelegatedToParent);
        }
        return accountUserApi.createAccount(builder.build(), callContext);
    }

    private InternalCallContext contextFor(final Account account) {
        return internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
    }

    private UUID persistCommittedChildInvoice(final Account childAccount, final BigDecimal amount, final LocalDate targetDate) throws Exception {
        final InternalCallContext childContext = contextFor(childAccount);
        final DefaultInvoice childInvoice = new DefaultInvoice(UUID.randomUUID(), childAccount.getId(), null,
                                                               targetDate, targetDate, Currency.USD, false, InvoiceStatus.COMMITTED);
        final RecurringInvoiceItem item = new RecurringInvoiceItem(childInvoice.getId(), childAccount.getId(),
                                                                   UUID.randomUUID(), UUID.randomUUID(),
                                                                   "test product", "test plan", "test phase", null,
                                                                   targetDate, targetDate.plusMonths(1),
                                                                   amount, amount, Currency.USD);
        childInvoice.addInvoiceItem(item);
        invoiceUtil.createInvoice(childInvoice, childContext);
        return childInvoice.getId();
    }

    private static BigDecimal totalAmount(final InvoiceModelDao invoice) {
        BigDecimal total = BigDecimal.ZERO;
        for (final InvoiceItemModelDao item : invoice.getInvoiceItems()) {
            total = total.add(item.getAmount());
        }
        return total;
    }

}
