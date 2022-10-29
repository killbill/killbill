/*
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
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

package org.killbill.billing.invoice.generator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.DefaultPrice;
import org.killbill.billing.catalog.MockInternationalPrice;
import org.killbill.billing.catalog.MockPlan;
import org.killbill.billing.catalog.MockPlanPhase;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.invoice.InvoiceTestSuiteNoDB;
import org.killbill.billing.invoice.MockBillingEventSet;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.generator.InvoiceItemGenerator.InvoiceItemGeneratorLogger;
import org.killbill.billing.invoice.generator.InvoiceWithMetadata.SubscriptionFutureNotificationDates;
import org.killbill.billing.invoice.model.DefaultInvoice;
import org.killbill.billing.invoice.model.FixedPriceInvoiceItem;
import org.killbill.billing.invoice.model.ItemAdjInvoiceItem;
import org.killbill.billing.invoice.model.RecurringInvoiceItem;
import org.killbill.billing.invoice.model.RepairAdjInvoiceItem;
import org.killbill.billing.invoice.optimizer.InvoiceOptimizerBase.AccountInvoices;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.junction.BillingEventSet;
import org.killbill.billing.junction.plumbing.billing.DefaultBillingEvent;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.subscription.api.user.SubscriptionBillingEvent;
import org.killbill.billing.util.UUIDs;
import org.killbill.commons.utils.collect.MultiValueHashMap;
import org.killbill.commons.utils.collect.MultiValueMap;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestFixedAndRecurringInvoiceItemGenerator extends InvoiceTestSuiteNoDB {

    private Account account;
    private SubscriptionBase subscription;

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(TestFixedAndRecurringInvoiceItemGenerator.class);

    @Override
    @BeforeMethod(groups = "fast")
    public void beforeMethod() {
        if (hasFailed()) {
            return;
        }
        super.beforeMethod();

        try {
            account = invoiceUtil.createAccount(callContext);
            subscription = invoiceUtil.createSubscription();
        } catch (final Exception e) {
            fail(e.getMessage());
        }
    }

    //
    //
    //                           IS_SAME_DAY_AND_SAME_SUBSCRIPTION TESTS
    //
    //
    @Test(groups = "fast")
    public void testIsSameDayAndSameSubscriptionWithNullPrevEvent() {

        final Plan plan = new MockPlan("my-plan");

        final BigDecimal fixedPriceAmount = BigDecimal.TEN;
        final MockInternationalPrice fixedPrice = new MockInternationalPrice(new DefaultPrice(fixedPriceAmount, Currency.USD));
        final PlanPhase phase = new MockPlanPhase(null, fixedPrice, BillingPeriod.NO_BILLING_PERIOD, PhaseType.TRIAL);

        final InvoiceItem prevInvoiceItem = null;

        final BillingEvent event = invoiceUtil.createMockBillingEvent(account, subscription, new DateTime("2016-02-01"),
                                                                      plan, phase,
                                                                      fixedPriceAmount, null, Currency.USD, BillingPeriod.NO_BILLING_PERIOD, 1,
                                                                      BillingMode.IN_ADVANCE, "Billing Event Desc", 1L,
                                                                      SubscriptionBaseTransitionType.CREATE);

        assertFalse(fixedAndRecurringInvoiceItemGenerator.isSameDayAndSameSubscription(prevInvoiceItem, event, internalCallContext));
    }

    @Test(groups = "fast")
    public void testIsSameDayAndSameSubscriptionWithDifferentSubscriptionId() {

        final Plan plan = new MockPlan("my-plan");

        final LocalDate invoiceItemDate = new LocalDate("2016-01-08");
        final BigDecimal fixedPriceAmount = BigDecimal.TEN;
        final MockInternationalPrice fixedPrice = new MockInternationalPrice(new DefaultPrice(fixedPriceAmount, Currency.USD));
        final PlanPhase phase = new MockPlanPhase(null, fixedPrice, BillingPeriod.NO_BILLING_PERIOD, PhaseType.TRIAL);

        final UUID invoiceId = UUID.randomUUID();
        final InvoiceItem prevInvoiceItem = new FixedPriceInvoiceItem(invoiceId, account.getId(), UUID.randomUUID(), UUID.randomUUID(), null, plan.getName(),
                                                                      phase.getName(), null, invoiceItemDate, fixedPriceAmount, Currency.USD);

        final BillingEvent event = invoiceUtil.createMockBillingEvent(account, subscription, new DateTime("2016-01-08"),
                                                                      plan, phase,
                                                                      fixedPriceAmount, null, Currency.USD, BillingPeriod.NO_BILLING_PERIOD, 1,
                                                                      BillingMode.IN_ADVANCE, "Billing Event Desc", 1L,
                                                                      SubscriptionBaseTransitionType.CREATE);

        assertFalse(fixedAndRecurringInvoiceItemGenerator.isSameDayAndSameSubscription(prevInvoiceItem, event, internalCallContext));
    }

    @Test(groups = "fast")
    public void testIsSameDayAndSameSubscriptionWithDifferentDate() {

        final Plan plan = new MockPlan("my-plan");

        final LocalDate invoiceItemDate = new LocalDate("2016-01-08");
        final BigDecimal fixedPriceAmount = BigDecimal.TEN;
        final MockInternationalPrice fixedPrice = new MockInternationalPrice(new DefaultPrice(fixedPriceAmount, Currency.USD));
        final PlanPhase phase = new MockPlanPhase(null, fixedPrice, BillingPeriod.NO_BILLING_PERIOD, PhaseType.TRIAL);

        final UUID invoiceId = UUID.randomUUID();
        final InvoiceItem prevInvoiceItem = new FixedPriceInvoiceItem(invoiceId, account.getId(), subscription.getBundleId(), subscription.getId(), null, plan.getName(),
                                                                      phase.getName(), null, invoiceItemDate, fixedPriceAmount, Currency.USD);

        final BillingEvent event = invoiceUtil.createMockBillingEvent(account, subscription, new DateTime("2016-02-01"),
                                                                      plan, phase,
                                                                      fixedPriceAmount, null, Currency.USD, BillingPeriod.NO_BILLING_PERIOD, 1,
                                                                      BillingMode.IN_ADVANCE, "Billing Event Desc", 1L,
                                                                      SubscriptionBaseTransitionType.CREATE);

        assertFalse(fixedAndRecurringInvoiceItemGenerator.isSameDayAndSameSubscription(prevInvoiceItem, event, internalCallContext));
    }

    @Test(groups = "fast")
    public void testIsSameDayAndSameSubscriptionWithSameDateAndSubscriptionId() {

        final Plan plan = new MockPlan("my-plan");

        final LocalDate invoiceItemDate = new LocalDate("2016-01-08");
        final BigDecimal fixedPriceAmount = BigDecimal.TEN;
        final MockInternationalPrice fixedPrice = new MockInternationalPrice(new DefaultPrice(fixedPriceAmount, Currency.USD));
        final PlanPhase phase = new MockPlanPhase(null, fixedPrice, BillingPeriod.NO_BILLING_PERIOD, PhaseType.TRIAL);

        final UUID invoiceId = UUID.randomUUID();
        final InvoiceItem prevInvoiceItem = new FixedPriceInvoiceItem(invoiceId, account.getId(), subscription.getBundleId(), subscription.getId(), null, plan.getName(),
                                                                      phase.getName(), null, invoiceItemDate, fixedPriceAmount, Currency.USD);

        final BillingEvent event = invoiceUtil.createMockBillingEvent(account, subscription, new DateTime("2016-01-08"),
                                                                      plan, phase,
                                                                      fixedPriceAmount, null, Currency.USD, BillingPeriod.NO_BILLING_PERIOD, 1,
                                                                      BillingMode.IN_ADVANCE, "Billing Event Desc", 1L,
                                                                      SubscriptionBaseTransitionType.CREATE);

        assertTrue(fixedAndRecurringInvoiceItemGenerator.isSameDayAndSameSubscription(prevInvoiceItem, event, internalCallContext));
    }


    //
    //
    //                           PROCESS_FIXED_BILLING_EVENTS TESTS
    //
    //
    @Test(groups = "fast")
    public void testProcessFixedBillingEventsWithCancellationOnSameDay() throws InvoiceApiException {

        final LocalDate targetDate = new LocalDate("2016-01-08");

        final UUID invoiceId = UUID.randomUUID();
        final BillingEventSet events = new MockBillingEventSet();

        final BigDecimal fixedPriceAmount = BigDecimal.TEN;
        final MockInternationalPrice fixedPrice = new MockInternationalPrice(new DefaultPrice(fixedPriceAmount, Currency.USD));
        final Plan plan = new MockPlan("my-plan");
        final PlanPhase phase = new MockPlanPhase(null, fixedPrice, BillingPeriod.NO_BILLING_PERIOD, PhaseType.TRIAL);

        final BillingEvent event1 = invoiceUtil.createMockBillingEvent(account, subscription, new DateTime("2016-01-08"),
                                                                       plan, phase,
                                                                       fixedPriceAmount, null, Currency.USD, BillingPeriod.NO_BILLING_PERIOD, 1,
                                                                       BillingMode.IN_ADVANCE, "Billing Event Desc", 1L,
                                                                       SubscriptionBaseTransitionType.CREATE);
        events.add(event1);

        final BillingEvent event2 = invoiceUtil.createMockBillingEvent(account, subscription, new DateTime("2016-01-08"),
                                                                       plan, phase,
                                                                       null, null, Currency.USD, BillingPeriod.NO_BILLING_PERIOD, 1,
                                                                       BillingMode.IN_ADVANCE, "Billing Event Desc", 2L,
                                                                       SubscriptionBaseTransitionType.CANCEL);
        events.add(event2);

        final List<InvoiceItem> proposedItems = new ArrayList<InvoiceItem>();
        fixedAndRecurringInvoiceItemGenerator.processFixedBillingEvents(invoiceId, account.getId(), events, targetDate, Currency.USD, proposedItems, internalCallContext);
        assertTrue(proposedItems.isEmpty());
    }

    @Test(groups = "fast")
    public void testProcessFixedBillingEventsWithCancellationOnNextDay() throws InvoiceApiException {

        final LocalDate targetDate = new LocalDate("2016-01-08");

        final UUID invoiceId = UUID.randomUUID();
        final BillingEventSet events = new MockBillingEventSet();

        final BigDecimal fixedPriceAmount = BigDecimal.TEN;
        final MockInternationalPrice fixedPrice = new MockInternationalPrice(new DefaultPrice(fixedPriceAmount, Currency.USD));
        final Plan plan = new MockPlan("my-plan");
        final PlanPhase phase = new MockPlanPhase(null, fixedPrice, BillingPeriod.NO_BILLING_PERIOD, PhaseType.TRIAL);

        final BillingEvent event1 = invoiceUtil.createMockBillingEvent(account, subscription, new DateTime("2016-01-08"),
                                                                       plan, phase,
                                                                       fixedPriceAmount, null, Currency.USD, BillingPeriod.NO_BILLING_PERIOD, 1,
                                                                       BillingMode.IN_ADVANCE, "Billing Event Desc", 1L,
                                                                       SubscriptionBaseTransitionType.CREATE);
        events.add(event1);

        final BillingEvent event2 = invoiceUtil.createMockBillingEvent(account, subscription, new DateTime("2016-01-09"),
                                                                       plan, phase,
                                                                       null, null, Currency.USD, BillingPeriod.NO_BILLING_PERIOD, 1,
                                                                       BillingMode.IN_ADVANCE, "Billing Event Desc", 2L,
                                                                       SubscriptionBaseTransitionType.CANCEL);
        events.add(event2);

        final List<InvoiceItem> proposedItems = new ArrayList<InvoiceItem>();
        fixedAndRecurringInvoiceItemGenerator.processFixedBillingEvents(invoiceId, account.getId(), events, targetDate, Currency.USD, proposedItems, internalCallContext);
        assertEquals(proposedItems.size(), 1);
        assertEquals(proposedItems.get(0).getInvoiceItemType(), InvoiceItemType.FIXED);
        assertEquals(proposedItems.get(0).getAmount().compareTo(fixedPriceAmount), 0);
    }

    @Test(groups = "fast")
    public void testProcessFixedBillingEventsWithMultipleChangeOnSameDay() throws InvoiceApiException {

        final LocalDate targetDate = new LocalDate("2016-01-08");

        final UUID invoiceId = UUID.randomUUID();
        final BillingEventSet events = new MockBillingEventSet();

        final BigDecimal fixedPriceAmount1 = BigDecimal.TEN;
        final MockInternationalPrice fixedPrice1 = new MockInternationalPrice(new DefaultPrice(fixedPriceAmount1, Currency.USD));
        final Plan plan1 = new MockPlan("my-plan1");
        final PlanPhase planPhase1 = new MockPlanPhase(null, fixedPrice1, BillingPeriod.NO_BILLING_PERIOD, PhaseType.TRIAL);

        final BillingEvent event1 = invoiceUtil.createMockBillingEvent(account, subscription, new DateTime("2016-01-08"),
                                                                       plan1, planPhase1,
                                                                       fixedPriceAmount1, null, Currency.USD, BillingPeriod.NO_BILLING_PERIOD, 1,
                                                                       BillingMode.IN_ADVANCE, "Billing Event Desc", 1L,
                                                                       SubscriptionBaseTransitionType.CREATE);
        events.add(event1);

        final BigDecimal fixedPriceAmount2 = null;
        final MockInternationalPrice fixedPrice2 = new MockInternationalPrice(new DefaultPrice(fixedPriceAmount2, Currency.USD));
        final Plan plan2 = new MockPlan("my-plan2");
        final PlanPhase planPhase2 = new MockPlanPhase(null, fixedPrice2, BillingPeriod.NO_BILLING_PERIOD, PhaseType.TRIAL);

        final BillingEvent event2 = invoiceUtil.createMockBillingEvent(account, subscription, new DateTime("2016-01-08"),
                                                                       plan2, planPhase2,
                                                                       fixedPriceAmount2, null, Currency.USD, BillingPeriod.NO_BILLING_PERIOD, 1,
                                                                       BillingMode.IN_ADVANCE, "Billing Event Desc", 2L,
                                                                       SubscriptionBaseTransitionType.CHANGE);
        events.add(event2);

        final BigDecimal fixedPriceAmount3 = BigDecimal.ONE;
        final MockInternationalPrice fixedPrice3 = new MockInternationalPrice(new DefaultPrice(fixedPriceAmount3, Currency.USD));
        final Plan plan3 = new MockPlan("my-plan3");
        final PlanPhase planPhase3 = new MockPlanPhase(null, fixedPrice3, BillingPeriod.NO_BILLING_PERIOD, PhaseType.TRIAL);

        final BillingEvent event3 = invoiceUtil.createMockBillingEvent(account, subscription, new DateTime("2016-01-08"),
                                                                       plan3, planPhase3,
                                                                       fixedPriceAmount3, null, Currency.USD, BillingPeriod.NO_BILLING_PERIOD, 1,
                                                                       BillingMode.IN_ADVANCE, "Billing Event Desc", 3L,
                                                                       SubscriptionBaseTransitionType.CHANGE);
        events.add(event3);

        final List<InvoiceItem> proposedItems = new ArrayList<InvoiceItem>();
        fixedAndRecurringInvoiceItemGenerator.processFixedBillingEvents(invoiceId, account.getId(), events, targetDate, Currency.USD, proposedItems, internalCallContext);
        assertEquals(proposedItems.size(), 1);
        assertEquals(proposedItems.get(0).getInvoiceItemType(), InvoiceItemType.FIXED);
        assertEquals(proposedItems.get(0).getAmount().compareTo(fixedPriceAmount3), 0);
    }


    //
    //
    //                           GENERATE_ITEMS TESTS
    //
    //
    @Test(groups = "fast")
    public void testSafetyBoundsTooManyInvoiceItemsForGivenSubscriptionAndInvoiceDate() throws InvoiceApiException {
        final int threshold = 15;
        final LocalDate startDate = new LocalDate("2016-01-01");

        final BillingEventSet events = new MockBillingEventSet();
        final BigDecimal amount = BigDecimal.TEN;
        final MockInternationalPrice price = new MockInternationalPrice(new DefaultPrice(amount, account.getCurrency()));
        final Plan plan = new MockPlan("my-plan");
        final PlanPhase planPhase = new MockPlanPhase(price, null, BillingPeriod.MONTHLY, PhaseType.EVERGREEN);
        final BillingEvent event = invoiceUtil.createMockBillingEvent(account,
                                                                      subscription,
                                                                      startDate.toDateTimeAtStartOfDay(),
                                                                      plan,
                                                                      planPhase,
                                                                      null,
                                                                      amount,
                                                                      account.getCurrency(),
                                                                      planPhase.getRecurring().getBillingPeriod(),
                                                                      1,
                                                                      BillingMode.IN_ADVANCE,
                                                                      "Billing Event Desc",
                                                                      1L,
                                                                      SubscriptionBaseTransitionType.CREATE);
        events.add(event);

        // Simulate a big catch-up
        final List<Invoice> existingInvoices = new LinkedList<Invoice>();
        for (int i = 0; i < threshold; i++) {
            final Invoice invoice = new DefaultInvoice(account.getId(), clock.getUTCToday(), startDate.plusMonths(i), account.getCurrency());
            invoice.addInvoiceItem(new RecurringInvoiceItem(UUID.randomUUID(),
                                                            startDate.plusMonths(i).toDateTimeAtStartOfDay(), // Different days - should not trigger the safety bounds
                                                            invoice.getId(),
                                                            account.getId(),
                                                            subscription.getBundleId(),
                                                            subscription.getId(),
                                                            null,
                                                            event.getPlan().getName(),
                                                            event.getPlanPhase().getName(),
                                                            null,
                                                            startDate.plusMonths(i),
                                                            startDate.plusMonths(1 + i),
                                                            amount,
                                                            amount,
                                                            account.getCurrency()));
            existingInvoices.add(invoice);
        }

        assertEquals(fixedAndRecurringInvoiceItemGenerator.generateItems(account,
                                                                         UUID.randomUUID(),
                                                                         events,
                                                                         new AccountInvoices(null, null, existingInvoices),
                                                                         startDate.plusMonths(threshold),
                                                                         account.getCurrency(),
                                                                         new HashMap<UUID, SubscriptionFutureNotificationDates>(),
                                                                         null, internalCallContext).getItems().size(), 1);

        // Simulate a big catch-up on that day
        for (int i = threshold; i < 2 * threshold; i++) {
            final Invoice invoice = new DefaultInvoice(account.getId(), clock.getUTCToday(), startDate.plusMonths(i), account.getCurrency());
            invoice.addInvoiceItem(new RecurringInvoiceItem(UUID.randomUUID(),
                                                            clock.getUTCNow(), // Same day
                                                            invoice.getId(),
                                                            account.getId(),
                                                            subscription.getBundleId(),
                                                            subscription.getId(),
                                                            null,
                                                            event.getPlan().getName(),
                                                            event.getPlanPhase().getName(),
                                                            null,
                                                            startDate.plusMonths(i),
                                                            startDate.plusMonths(1 + i),
                                                            amount,
                                                            amount,
                                                            account.getCurrency()));
            existingInvoices.add(invoice);
        }

        try {
            final List<InvoiceItem> generatedItems = fixedAndRecurringInvoiceItemGenerator.generateItems(account,
                                                                                                         UUID.randomUUID(),
                                                                                                         events,
                                                                                                         new AccountInvoices(null, null, existingInvoices),
                                                                                                         startDate.plusMonths(2 * threshold),
                                                                                                         account.getCurrency(),
                                                                                                         new HashMap<UUID, SubscriptionFutureNotificationDates>(),
                                                                                                         null, internalCallContext).getItems();
            fail();
        } catch (final InvoiceApiException e) {
            assertEquals(e.getCode(), ErrorCode.UNEXPECTED_ERROR.getCode());
        }
    }

    @Test(groups = "fast", description = "https://github.com/killbill/killbill/issues/664")
    public void testTooManyFixedInvoiceItemsForGivenSubscriptionAndStartDate() throws InvoiceApiException {
        final LocalDate startDate = new LocalDate("2016-01-01");

        final BillingEventSet events = new MockBillingEventSet();
        final BigDecimal amount = BigDecimal.TEN;
        final MockInternationalPrice price = new MockInternationalPrice(new DefaultPrice(amount, account.getCurrency()));
        final Plan plan = new MockPlan("my-plan");
        final PlanPhase planPhase = new MockPlanPhase(null, price, BillingPeriod.NO_BILLING_PERIOD, PhaseType.TRIAL);
        final BillingEvent event = invoiceUtil.createMockBillingEvent(account,
                                                                      subscription,
                                                                      startDate.toDateTimeAtStartOfDay(),
                                                                      plan,
                                                                      planPhase,
                                                                      amount,
                                                                      null,
                                                                      account.getCurrency(),
                                                                      BillingPeriod.NO_BILLING_PERIOD,
                                                                      1,
                                                                      BillingMode.IN_ADVANCE,
                                                                      "Billing Event Desc",
                                                                      1L,
                                                                      SubscriptionBaseTransitionType.CREATE);
        events.add(event);

        // Simulate a bunch of fixed items for that subscription and start date (simulate bad data on disk)
        final List<Invoice> existingInvoices = new LinkedList<Invoice>();
        for (int i = 0; i < 20; i++) {
            final Invoice invoice = new DefaultInvoice(account.getId(), clock.getUTCToday(), startDate, account.getCurrency());
            invoice.addInvoiceItem(new FixedPriceInvoiceItem(UUID.randomUUID(),
                                                             clock.getUTCNow(),
                                                             invoice.getId(),
                                                             account.getId(),
                                                             subscription.getBundleId(),
                                                             subscription.getId(),
                                                             null,
                                                             event.getPlan().getName(),
                                                             event.getPlanPhase().getName(),
                                                             null,
                                                             "Buggy fixed item",
                                                             startDate,
                                                             amount,
                                                             account.getCurrency()));
            existingInvoices.add(invoice);
        }

        final List<InvoiceItem> generatedItems = fixedAndRecurringInvoiceItemGenerator.generateItems(account,
                                                                                                     UUID.randomUUID(),
                                                                                                     events,
                                                                                                     new AccountInvoices(null, null, existingInvoices),
                                                                                                     startDate,
                                                                                                     account.getCurrency(),
                                                                                                     new HashMap<UUID, SubscriptionFutureNotificationDates>(),
                                                                                                     null, internalCallContext).getItems();
        // There will be one proposed, but because it will match one of ones in the existing list and we don't repair, it won't be returned
        assertEquals(generatedItems.size(), 0);
    }

    @Test(groups = "fast", description = "https://github.com/killbill/killbill/issues/664")
    public void testSubscriptionAlreadyDoubleBilledForServicePeriod() throws InvoiceApiException {
        final LocalDate startDate = new LocalDate("2016-01-01");

        final BillingEventSet events = new MockBillingEventSet();
        final BigDecimal amount = BigDecimal.TEN;
        final MockInternationalPrice price = new MockInternationalPrice(new DefaultPrice(amount, account.getCurrency()));
        final Plan plan = new MockPlan("my-plan");
        final PlanPhase planPhase = new MockPlanPhase(price, null, BillingPeriod.MONTHLY, PhaseType.EVERGREEN);
        final BillingEvent event = invoiceUtil.createMockBillingEvent(account,
                                                                      subscription,
                                                                      startDate.toDateTimeAtStartOfDay(),
                                                                      plan,
                                                                      planPhase,
                                                                      null,
                                                                      amount,
                                                                      account.getCurrency(),
                                                                      BillingPeriod.MONTHLY,
                                                                      1,
                                                                      BillingMode.IN_ADVANCE,
                                                                      "Billing Event Desc",
                                                                      1L,
                                                                      SubscriptionBaseTransitionType.CREATE);
        events.add(event);

        // Simulate a bunch of recurring items for that subscription and service period (bad data on disk leading to double billing)
        final List<Invoice> existingInvoices = new LinkedList<Invoice>();
        for (int i = 0; i < 20; i++) {
            final Invoice invoice = new DefaultInvoice(account.getId(), clock.getUTCToday(), startDate.plusMonths(i), account.getCurrency());
            invoice.addInvoiceItem(new RecurringInvoiceItem(UUID.randomUUID(),
                                                            // Set random dates to verify it doesn't impact double billing detection
                                                            startDate.plusMonths(i).toDateTimeAtStartOfDay(),
                                                            invoice.getId(),
                                                            account.getId(),
                                                            subscription.getBundleId(),
                                                            subscription.getId(),
                                                            null,
                                                            event.getPlan().getName(),
                                                            event.getPlanPhase().getName(),
                                                            null,
                                                            startDate,
                                                            startDate.plusMonths(1),
                                                            amount,
                                                            amount,
                                                            account.getCurrency()));
            existingInvoices.add(invoice);
        }

        try {
            // There will be one proposed item but the tree will refuse the merge because of the bad state on disk
            final List<InvoiceItem> generatedItems = fixedAndRecurringInvoiceItemGenerator.generateItems(account,
                                                                                                         UUID.randomUUID(),
                                                                                                         events,
                                                                                                         new AccountInvoices(null, null, existingInvoices),
                                                                                                         startDate,
                                                                                                         account.getCurrency(),
                                                                                                         new HashMap<UUID, SubscriptionFutureNotificationDates>(),
                                                                                                         null, internalCallContext).getItems();
            fail();
        } catch (final InvoiceApiException e) {
            assertEquals(e.getCode(), ErrorCode.UNEXPECTED_ERROR.getCode());
            assertTrue(e.getCause().getMessage().startsWith("Double billing detected"));
        }
    }

    @Test(groups = "fast", description = "https://github.com/killbill/killbill/issues/664")
    public void testOverlappingItems() throws InvoiceApiException {
        final LocalDate startDate = new LocalDate("2016-01-01");

        final BillingEventSet events = new MockBillingEventSet();
        final BigDecimal amount = BigDecimal.TEN;
        final MockInternationalPrice price = new MockInternationalPrice(new DefaultPrice(amount, account.getCurrency()));
        final Plan plan = new MockPlan("my-plan");
        final PlanPhase planPhase = new MockPlanPhase(price, null, BillingPeriod.MONTHLY, PhaseType.EVERGREEN);
        final BillingEvent event = invoiceUtil.createMockBillingEvent(account,
                                                                      subscription,
                                                                      startDate.toDateTimeAtStartOfDay(),
                                                                      plan,
                                                                      planPhase,
                                                                      null,
                                                                      amount,
                                                                      account.getCurrency(),
                                                                      BillingPeriod.MONTHLY,
                                                                      1,
                                                                      BillingMode.IN_ADVANCE,
                                                                      "Billing Event Desc",
                                                                      1L,
                                                                      SubscriptionBaseTransitionType.CREATE);
        events.add(event);

        // Simulate a previous mis-bill: existing item is for [2016-01-01,2016-01-30], proposed will be for [2016-01-01,2016-02-01]
        final List<Invoice> existingInvoices = new LinkedList<Invoice>();
        final Invoice invoice = new DefaultInvoice(account.getId(), clock.getUTCToday(), startDate, account.getCurrency());
        invoice.addInvoiceItem(new RecurringInvoiceItem(UUID.randomUUID(),
                                                        startDate.toDateTimeAtStartOfDay(),
                                                        invoice.getId(),
                                                        account.getId(),
                                                        subscription.getBundleId(),
                                                        subscription.getId(),
                                                        null,
                                                        event.getPlan().getName(),
                                                        event.getPlanPhase().getName(),
                                                        null,
                                                        startDate,
                                                        startDate.plusDays(29),
                                                        amount,
                                                        amount,
                                                        account.getCurrency()));
        existingInvoices.add(invoice);

        // We will repair the wrong item and generate the correct recurring item
        final List<InvoiceItem> generatedItems = fixedAndRecurringInvoiceItemGenerator.generateItems(account,
                                                                                                     UUID.randomUUID(),
                                                                                                     events,
                                                                                                     new AccountInvoices(null, null, existingInvoices),
                                                                                                     startDate,
                                                                                                     account.getCurrency(),
                                                                                                     new HashMap<UUID, SubscriptionFutureNotificationDates>(),
                                                                                                     null, internalCallContext).getItems();
        assertEquals(generatedItems.size(), 1);
        assertTrue(generatedItems.get(0) instanceof RecurringInvoiceItem);
        assertEquals(generatedItems.get(0).getStartDate(), new LocalDate("2016-01-30"));
        assertEquals(generatedItems.get(0).getEndDate(), new LocalDate("2016-02-01"));
        assertEquals(generatedItems.get(0).getAmount().compareTo(new BigDecimal("0.65")), 0);
    }

    @Test(groups = "fast", description = "https://github.com/killbill/killbill/issues/664")
    public void testOverlappingExistingItems() throws InvoiceApiException {
        final LocalDate startDate = new LocalDate("2016-01-01");

        final BillingEventSet events = new MockBillingEventSet();
        final BigDecimal amount = BigDecimal.TEN;
        final MockInternationalPrice price = new MockInternationalPrice(new DefaultPrice(amount, account.getCurrency()));
        final Plan plan = new MockPlan("my-plan");
        final PlanPhase planPhase = new MockPlanPhase(price, null, BillingPeriod.MONTHLY, PhaseType.EVERGREEN);
        final BillingEvent event = invoiceUtil.createMockBillingEvent(account,
                                                                      subscription,
                                                                      startDate.toDateTimeAtStartOfDay(),
                                                                      plan,
                                                                      planPhase,
                                                                      null,
                                                                      amount,
                                                                      account.getCurrency(),
                                                                      BillingPeriod.MONTHLY,
                                                                      1,
                                                                      BillingMode.IN_ADVANCE,
                                                                      "Billing Event Desc",
                                                                      1L,
                                                                      SubscriptionBaseTransitionType.CREATE);
        events.add(event);

        // Simulate a previous mis-bill: existing item is for [2016-01-01,2016-01-30], proposed will be for [2016-01-01,2016-02-01]
        final List<Invoice> existingInvoices = new LinkedList<Invoice>();
        final Invoice invoice = new DefaultInvoice(account.getId(), clock.getUTCToday(), startDate, account.getCurrency());
        invoice.addInvoiceItem(new RecurringInvoiceItem(UUID.randomUUID(),
                                                        startDate.toDateTimeAtStartOfDay(),
                                                        invoice.getId(),
                                                        account.getId(),
                                                        subscription.getBundleId(),
                                                        subscription.getId(),
                                                        null,
                                                        event.getPlan().getName(),
                                                        event.getPlanPhase().getName(),
                                                        null,
                                                        startDate,
                                                        startDate.plusDays(29),
                                                        amount,
                                                        amount,
                                                        account.getCurrency()));
        // Correct one already generated
        invoice.addInvoiceItem(new RecurringInvoiceItem(UUID.randomUUID(),
                                                        startDate.toDateTimeAtStartOfDay(),
                                                        invoice.getId(),
                                                        account.getId(),
                                                        subscription.getBundleId(),
                                                        subscription.getId(),
                                                        null,
                                                        event.getPlan().getName(),
                                                        event.getPlanPhase().getName(),
                                                        null,
                                                        startDate,
                                                        startDate.plusMonths(1),
                                                        amount,
                                                        amount,
                                                        account.getCurrency()));
        existingInvoices.add(invoice);

        try {
            // There will be one proposed item but the tree will refuse the merge because of the bad state on disk
            final List<InvoiceItem> generatedItems = fixedAndRecurringInvoiceItemGenerator.generateItems(account,
                                                                                                         UUID.randomUUID(),
                                                                                                         events,
                                                                                                         new AccountInvoices(null, null, existingInvoices),
                                                                                                         startDate,
                                                                                                         account.getCurrency(),
                                                                                                         new HashMap<UUID, SubscriptionFutureNotificationDates>(),
                                                                                                         null, internalCallContext).getItems();

            // Maybe we could auto-fix-it one day?
            // assertEquals(generatedItems.size(), 1);
            // assertTrue(generatedItems.get(0) instanceof RepairAdjInvoiceItem);
            // assertEquals(generatedItems.get(0).getAmount().compareTo(amount.negate()), 0);
            // assertEquals(generatedItems.get(0).getLinkedItemId(), invoice.getInvoiceItems().get(0).getId());

            fail();
        } catch (final InvoiceApiException e) {
            assertEquals(e.getCode(), ErrorCode.UNEXPECTED_ERROR.getCode());
            assertTrue(e.getCause().getMessage().startsWith("Double billing detected"));
        }
    }

    // Test fully repaired item get removed and instead we generate correct item
    @Test(groups = "fast", description = "https://github.com/killbill/killbill/issues/664")
    public void testOverlappingItemsWithRepair() throws InvoiceApiException {
        final LocalDate startDate = new LocalDate("2016-01-01");

        final BillingEventSet events = new MockBillingEventSet();
        final BigDecimal amount = BigDecimal.TEN;
        final MockInternationalPrice price = new MockInternationalPrice(new DefaultPrice(amount, account.getCurrency()));
        final Plan plan = new MockPlan("my-plan");
        final PlanPhase planPhase = new MockPlanPhase(price, null, BillingPeriod.MONTHLY, PhaseType.EVERGREEN);
        final BillingEvent event = invoiceUtil.createMockBillingEvent(account,
                                                                      subscription,
                                                                      startDate.toDateTimeAtStartOfDay(),
                                                                      plan,
                                                                      planPhase,
                                                                      null,
                                                                      amount,
                                                                      account.getCurrency(),
                                                                      BillingPeriod.MONTHLY,
                                                                      1,
                                                                      BillingMode.IN_ADVANCE,
                                                                      "Billing Event Desc",
                                                                      1L,
                                                                      SubscriptionBaseTransitionType.CREATE);
        events.add(event);

        // Simulate a previous mis-bill: existing item is for [2016-01-01,2016-01-30], proposed will be for [2016-01-01,2016-02-01]
        final List<Invoice> existingInvoices = new LinkedList<Invoice>();
        final Invoice invoice = new DefaultInvoice(account.getId(), clock.getUTCToday(), startDate, account.getCurrency());
        invoice.addInvoiceItem(new RecurringInvoiceItem(UUID.randomUUID(),
                                                        startDate.toDateTimeAtStartOfDay(),
                                                        invoice.getId(),
                                                        account.getId(),
                                                        subscription.getBundleId(),
                                                        subscription.getId(),
                                                        null,
                                                        event.getPlan().getName(),
                                                        event.getPlanPhase().getName(),
                                                        null,
                                                        startDate,
                                                        startDate.plusDays(29),
                                                        amount,
                                                        amount,
                                                        account.getCurrency()));
        // But the system has already repaired it
        invoice.addInvoiceItem(new RepairAdjInvoiceItem(UUID.randomUUID(),
                                                        startDate.toDateTimeAtStartOfDay(),
                                                        invoice.getId(),
                                                        account.getId(),
                                                        startDate,
                                                        startDate.plusDays(29),
                                                        amount.negate(),
                                                        account.getCurrency(),
                                                        invoice.getInvoiceItems().get(0).getId()));
        existingInvoices.add(invoice);

        // We will generate the correct recurring item
        final List<InvoiceItem> generatedItems = fixedAndRecurringInvoiceItemGenerator.generateItems(account,
                                                                                                     UUID.randomUUID(),
                                                                                                     events,
                                                                                                     new AccountInvoices(null, null, existingInvoices),
                                                                                                     startDate,
                                                                                                     account.getCurrency(),
                                                                                                     new HashMap<UUID, SubscriptionFutureNotificationDates>(),
                                                                                                     null, internalCallContext).getItems();
        assertEquals(generatedItems.size(), 1);
        assertTrue(generatedItems.get(0) instanceof RecurringInvoiceItem);
        assertEquals(generatedItems.get(0).getStartDate(), new LocalDate("2016-01-01"));
        assertEquals(generatedItems.get(0).getEndDate(), new LocalDate("2016-02-01"));
        assertEquals(generatedItems.get(0).getAmount().compareTo(amount), 0);
    }

    // Also a case we REPAIR amount does not match the dates and the current logic is to assert on the dates
    @Test(groups = "fast", description = "https://github.com/killbill/killbill/issues/664")
    public void testOverlappingItemsWithTooManyRepairs() throws InvoiceApiException {
        final LocalDate startDate = new LocalDate("2016-01-01");

        final BillingEventSet events = new MockBillingEventSet();
        final BigDecimal amount = BigDecimal.TEN;
        final MockInternationalPrice price = new MockInternationalPrice(new DefaultPrice(amount, account.getCurrency()));
        final Plan plan = new MockPlan("my-plan");
        final PlanPhase planPhase = new MockPlanPhase(price, null, BillingPeriod.MONTHLY, PhaseType.EVERGREEN);
        final BillingEvent event = invoiceUtil.createMockBillingEvent(account,
                                                                      subscription,
                                                                      startDate.toDateTimeAtStartOfDay(),
                                                                      plan,
                                                                      planPhase,
                                                                      null,
                                                                      amount,
                                                                      account.getCurrency(),
                                                                      BillingPeriod.MONTHLY,
                                                                      1,
                                                                      BillingMode.IN_ADVANCE,
                                                                      "Billing Event Desc",
                                                                      1L,
                                                                      SubscriptionBaseTransitionType.CREATE);
        events.add(event);

        // Simulate a previous mis-bill: existing item is for [2016-01-01,2016-01-30], proposed will be for [2016-01-01,2016-02-01]
        final List<Invoice> existingInvoices = new LinkedList<Invoice>();
        final Invoice invoice = new DefaultInvoice(account.getId(), clock.getUTCToday(), startDate, account.getCurrency());
        invoice.addInvoiceItem(new RecurringInvoiceItem(UUID.randomUUID(),
                                                        startDate.toDateTimeAtStartOfDay(),
                                                        invoice.getId(),
                                                        account.getId(),
                                                        subscription.getBundleId(),
                                                        subscription.getId(),
                                                        null,
                                                        event.getPlan().getName(),
                                                        event.getPlanPhase().getName(),
                                                        null,
                                                        startDate,
                                                        startDate.plusDays(29),
                                                        amount,
                                                        amount,
                                                        account.getCurrency()));
        // But the system has already repaired it
        invoice.addInvoiceItem(new RepairAdjInvoiceItem(UUID.randomUUID(),
                                                        startDate.toDateTimeAtStartOfDay(),
                                                        invoice.getId(),
                                                        account.getId(),
                                                        startDate,
                                                        startDate.plusDays(29),
                                                        amount.negate(), // Note! The amount will not matter
                                                        account.getCurrency(),
                                                        invoice.getInvoiceItems().get(0).getId()));
        // Twice!
        invoice.addInvoiceItem(new RepairAdjInvoiceItem(UUID.randomUUID(),
                                                        startDate.toDateTimeAtStartOfDay(),
                                                        invoice.getId(),
                                                        account.getId(),
                                                        startDate,
                                                        startDate.plusDays(29),
                                                        BigDecimal.ONE.negate(), // Note! The amount will not matter
                                                        account.getCurrency(),
                                                        invoice.getInvoiceItems().get(0).getId()));
        existingInvoices.add(invoice);

        try {
            fixedAndRecurringInvoiceItemGenerator.generateItems(account,
                                                                UUID.randomUUID(),
                                                                events,
                                                                new AccountInvoices(null, null, existingInvoices),
                                                                startDate,
                                                                account.getCurrency(),
                                                                new HashMap<UUID, SubscriptionFutureNotificationDates>(),
                                                                null, internalCallContext).getItems();
            fail();
        } catch (final InvoiceApiException e) {
            assertEquals(e.getCode(), ErrorCode.UNEXPECTED_ERROR.getCode());
            assertTrue(e.getCause().getMessage().startsWith("Too many repairs"));
        }
    }

    @Test(groups = "fast", description = "https://github.com/killbill/killbill/issues/664")
    public void testOverlappingItemsWithInvalidRepair() throws InvoiceApiException {
        final LocalDate startDate = new LocalDate("2016-01-01");

        final BillingEventSet events = new MockBillingEventSet();
        final BigDecimal amount = BigDecimal.TEN;
        final MockInternationalPrice price = new MockInternationalPrice(new DefaultPrice(amount, account.getCurrency()));
        final Plan plan = new MockPlan("my-plan");
        final PlanPhase planPhase = new MockPlanPhase(price, null, BillingPeriod.MONTHLY, PhaseType.EVERGREEN);
        final BillingEvent event = invoiceUtil.createMockBillingEvent(account,
                                                                      subscription,
                                                                      startDate.toDateTimeAtStartOfDay(),
                                                                      plan,
                                                                      planPhase,
                                                                      null,
                                                                      amount,
                                                                      account.getCurrency(),
                                                                      BillingPeriod.MONTHLY,
                                                                      1,
                                                                      BillingMode.IN_ADVANCE,
                                                                      "Billing Event Desc",
                                                                      1L,
                                                                      SubscriptionBaseTransitionType.CREATE);
        events.add(event);

        // Simulate a previous mis-bill: existing item is for [2016-01-01,2016-01-30], proposed will be for [2016-01-01,2016-02-01]
        final List<Invoice> existingInvoices = new LinkedList<Invoice>();
        final Invoice invoice = new DefaultInvoice(account.getId(), clock.getUTCToday(), startDate, account.getCurrency());
        invoice.addInvoiceItem(new RecurringInvoiceItem(UUID.randomUUID(),
                                                        startDate.toDateTimeAtStartOfDay(),
                                                        invoice.getId(),
                                                        account.getId(),
                                                        subscription.getBundleId(),
                                                        subscription.getId(),
                                                        null,
                                                        event.getPlan().getName(),
                                                        event.getPlanPhase().getName(),
                                                        null,
                                                        startDate,
                                                        startDate.plusDays(29),
                                                        amount,
                                                        amount,
                                                        account.getCurrency()));
        // Also, the system has repaired a bigger period
        invoice.addInvoiceItem(new RepairAdjInvoiceItem(UUID.randomUUID(),
                                                        startDate.toDateTimeAtStartOfDay(),
                                                        invoice.getId(),
                                                        account.getId(),
                                                        startDate,
                                                        startDate.plusDays(30),
                                                        BigDecimal.ONE.negate(), // Amount does not matter
                                                        account.getCurrency(),
                                                        invoice.getInvoiceItems().get(0).getId()));
        existingInvoices.add(invoice);

        try {
            final List<InvoiceItem> generatedItems = fixedAndRecurringInvoiceItemGenerator.generateItems(account,
                                                                                                         UUID.randomUUID(),
                                                                                                         events,
                                                                                                         new AccountInvoices(null, null, existingInvoices),
                                                                                                         startDate,
                                                                                                         account.getCurrency(),
                                                                                                         new HashMap<UUID, SubscriptionFutureNotificationDates>(),
                                                                                                         null, internalCallContext).getItems();
            fail();
        } catch (final InvoiceApiException e) {
            assertEquals(e.getCode(), ErrorCode.UNEXPECTED_ERROR.getCode());
            assertTrue(e.getCause().getMessage().startsWith("Invalid cancelledItem"));
        }
    }

    @Test(groups = "fast", description = "https://github.com/killbill/killbill/issues/664")
    public void testInvalidRepair() throws InvoiceApiException {
        final LocalDate startDate = new LocalDate("2016-01-01");

        final BillingEventSet events = new MockBillingEventSet();

        final List<Invoice> existingInvoices = new LinkedList<Invoice>();
        final Invoice invoice = new DefaultInvoice(account.getId(), clock.getUTCToday(), startDate, account.getCurrency());
        // Dangling repair
        invoice.addInvoiceItem(new RepairAdjInvoiceItem(UUID.randomUUID(),
                                                        startDate.toDateTimeAtStartOfDay(),
                                                        invoice.getId(),
                                                        account.getId(),
                                                        startDate,
                                                        startDate.plusMonths(1),
                                                        BigDecimal.ONE.negate(),
                                                        account.getCurrency(),
                                                        UUID.randomUUID()));
        existingInvoices.add(invoice);

        try {
            final List<InvoiceItem> generatedItems = fixedAndRecurringInvoiceItemGenerator.generateItems(account,
                                                                                                         UUID.randomUUID(),
                                                                                                         events,
                                                                                                         new AccountInvoices(null, null, existingInvoices),
                                                                                                         startDate,
                                                                                                         account.getCurrency(),
                                                                                                         new HashMap<UUID, SubscriptionFutureNotificationDates>(),
                                                                                                         null, internalCallContext).getItems();
            fail();
        } catch (final InvoiceApiException e) {
            assertEquals(e.getCode(), ErrorCode.UNEXPECTED_ERROR.getCode());
            assertTrue(e.getCause().getMessage().startsWith("Missing cancelledItem"));
        }
    }

    @Test(groups = "fast", description = "https://github.com/killbill/killbill/issues/664")
    public void testInvalidAdjustment() throws InvoiceApiException {
        final LocalDate startDate = new LocalDate("2016-01-01");

        final BillingEventSet events = new MockBillingEventSet();

        final List<Invoice> existingInvoices = new LinkedList<Invoice>();
        final Invoice invoice = new DefaultInvoice(account.getId(), clock.getUTCToday(), startDate, account.getCurrency());
        // Dangling adjustment
        invoice.addInvoiceItem(new ItemAdjInvoiceItem(UUID.randomUUID(),
                                                      startDate.toDateTimeAtStartOfDay(),
                                                      invoice.getId(),
                                                      account.getId(),
                                                      startDate,
                                                      "Dangling adjustment",
                                                      BigDecimal.ONE.negate(),
                                                      account.getCurrency(),
                                                      UUID.randomUUID(),
                                                      null));
        existingInvoices.add(invoice);

        try {
            final List<InvoiceItem> generatedItems = fixedAndRecurringInvoiceItemGenerator.generateItems(account,
                                                                                                         UUID.randomUUID(),
                                                                                                         events,
                                                                                                         new AccountInvoices(null, null, existingInvoices),
                                                                                                         startDate,
                                                                                                         account.getCurrency(),
                                                                                                         new HashMap<UUID, SubscriptionFutureNotificationDates>(),
                                                                                                         null, internalCallContext).getItems();
            fail();
        } catch (final InvoiceApiException e) {
            assertEquals(e.getCode(), ErrorCode.UNEXPECTED_ERROR.getCode());
            assertTrue(e.getCause().getMessage().startsWith("Missing subscription id"));
        }
    }

    @Test(groups = "fast", description = "https://github.com/killbill/killbill/issues/664")
    public void testItemFullyRepairedAndFullyAdjusted() throws InvoiceApiException {
        final LocalDate startDate = new LocalDate("2016-01-01");

        final BillingEventSet events = new MockBillingEventSet();
        final BigDecimal amount = BigDecimal.TEN;

        // Subscription incorrectly invoiced
        final List<Invoice> existingInvoices = new LinkedList<Invoice>();
        final Invoice invoice = new DefaultInvoice(account.getId(), clock.getUTCToday(), startDate, account.getCurrency());
        invoice.addInvoiceItem(new RecurringInvoiceItem(UUID.randomUUID(),
                                                        startDate.toDateTimeAtStartOfDay(),
                                                        invoice.getId(),
                                                        account.getId(),
                                                        subscription.getBundleId(),
                                                        subscription.getId(),
                                                        null,
                                                        "my-plan",
                                                        "my-plan-monthly",
                                                        null,
                                                        startDate,
                                                        startDate.plusMonths(1),
                                                        amount,
                                                        amount,
                                                        account.getCurrency()));
        // Repaired by the system
        invoice.addInvoiceItem(new RepairAdjInvoiceItem(UUID.randomUUID(),
                                                        startDate.toDateTimeAtStartOfDay(),
                                                        invoice.getId(),
                                                        account.getId(),
                                                        startDate,
                                                        startDate.plusMonths(1),
                                                        amount.negate(),
                                                        account.getCurrency(),
                                                        invoice.getInvoiceItems().get(0).getId()));
        invoice.addInvoiceItem(new ItemAdjInvoiceItem(invoice.getInvoiceItems().get(0),
                                                      startDate,
                                                      BigDecimal.ONE.negate(), // Note! The amount will matter
                                                      account.getCurrency()));
        existingInvoices.add(invoice);

        try {
            fixedAndRecurringInvoiceItemGenerator.generateItems(account,
                                                                UUID.randomUUID(),
                                                                events,
                                                                new AccountInvoices(null, null, existingInvoices),
                                                                startDate,
                                                                account.getCurrency(),
                                                                new HashMap<UUID, SubscriptionFutureNotificationDates>(),
                                                                null, internalCallContext).getItems();
            fail();
        } catch (final InvoiceApiException e) {
            assertEquals(e.getCode(), ErrorCode.UNEXPECTED_ERROR.getCode());
            assertTrue(e.getCause().getMessage().startsWith("Too many repairs"));
        }
    }

    @Test(groups = "fast", description = "https://github.com/killbill/killbill/issues/664")
    public void testItemPartiallyRepairedAndPartiallyAdjusted() throws InvoiceApiException {
        final LocalDate startDate = new LocalDate("2016-01-01");

        final BillingEventSet events = new MockBillingEventSet();
        final BigDecimal amount = BigDecimal.TEN;
        final MockInternationalPrice price = new MockInternationalPrice(new DefaultPrice(amount, account.getCurrency()));
        final Plan plan = new MockPlan("my-plan");
        final PlanPhase planPhase = new MockPlanPhase(price, null, BillingPeriod.MONTHLY, PhaseType.EVERGREEN);
        final BillingEvent event1 = invoiceUtil.createMockBillingEvent(account,
                                                                      subscription,
                                                                      startDate.toDateTimeAtStartOfDay(),
                                                                      plan,
                                                                      planPhase,
                                                                      null,
                                                                      amount,
                                                                      account.getCurrency(),
                                                                      BillingPeriod.MONTHLY,
                                                                      1,
                                                                      BillingMode.IN_ADVANCE,
                                                                      "Billing Event Desc",
                                                                      1L,
                                                                      SubscriptionBaseTransitionType.CREATE);
        events.add(event1);
        final BillingEvent event2 = invoiceUtil.createMockBillingEvent(account,
                                                                       subscription,
                                                                       startDate.plusDays(1).toDateTimeAtStartOfDay(),
                                                                       plan,
                                                                       planPhase,
                                                                       null,
                                                                       null,
                                                                       Currency.USD,
                                                                       BillingPeriod.NO_BILLING_PERIOD,
                                                                       1,
                                                                       BillingMode.IN_ADVANCE,
                                                                       "Billing Event Desc",
                                                                       2L,
                                                                       SubscriptionBaseTransitionType.CANCEL);
        events.add(event2);

        // Subscription incorrectly invoiced
        final List<Invoice> existingInvoices = new LinkedList<Invoice>();
        final Invoice invoice = new DefaultInvoice(account.getId(), clock.getUTCToday(), startDate, account.getCurrency());
        invoice.addInvoiceItem(new RecurringInvoiceItem(UUID.randomUUID(),
                                                        startDate.toDateTimeAtStartOfDay(),
                                                        invoice.getId(),
                                                        account.getId(),
                                                        subscription.getBundleId(),
                                                        subscription.getId(),
                                                        null,
                                                        plan.getName(),
                                                        planPhase.getName(),
                                                        null,
                                                        startDate,
                                                        startDate.plusMonths(1),
                                                        amount,
                                                        amount,
                                                        account.getCurrency()));
        // Repaired by the system
        invoice.addInvoiceItem(new RepairAdjInvoiceItem(UUID.randomUUID(),
                                                        startDate.toDateTimeAtStartOfDay(),
                                                        invoice.getId(),
                                                        account.getId(),
                                                        startDate.plusDays(1),
                                                        startDate.plusMonths(1),
                                                        new BigDecimal("9.68").negate(),
                                                        account.getCurrency(),
                                                        invoice.getInvoiceItems().get(0).getId()));
        // Item adjust the remaining
        invoice.addInvoiceItem(new ItemAdjInvoiceItem(invoice.getInvoiceItems().get(0),
                                                      startDate,
                                                      new BigDecimal("0.32").negate(),
                                                      account.getCurrency()));
        existingInvoices.add(invoice);

        final List<InvoiceItem> generatedItems = fixedAndRecurringInvoiceItemGenerator.generateItems(account,
                                                                                                     UUID.randomUUID(),
                                                                                                     events,
                                                                                                     new AccountInvoices(null, null, existingInvoices),
                                                                                                     startDate,
                                                                                                     account.getCurrency(),
                                                                                                     new HashMap<UUID, SubscriptionFutureNotificationDates>(),
                                                                                                     null, internalCallContext).getItems();
        assertTrue(generatedItems.isEmpty());
    }

    @Test(groups = "fast", description = "https://github.com/killbill/killbill/issues/664")
    public void testItemPartiallyRepairedAndPartiallyAdjustedV2() throws InvoiceApiException {
        final LocalDate startDate = new LocalDate("2016-01-01");

        final BillingEventSet events = new MockBillingEventSet();
        final BigDecimal amount = BigDecimal.TEN;
        final MockInternationalPrice price = new MockInternationalPrice(new DefaultPrice(amount, account.getCurrency()));
        final Plan plan = new MockPlan("my-plan");
        final PlanPhase planPhase = new MockPlanPhase(price, null, BillingPeriod.MONTHLY, PhaseType.EVERGREEN);
        final BillingEvent event1 = invoiceUtil.createMockBillingEvent(account,
                                                                      subscription,
                                                                      startDate.toDateTimeAtStartOfDay(),
                                                                      plan,
                                                                      planPhase,
                                                                      null,
                                                                      amount,
                                                                      account.getCurrency(),
                                                                      BillingPeriod.MONTHLY,
                                                                      1,
                                                                      BillingMode.IN_ADVANCE,
                                                                      "Billing Event Desc",
                                                                      1L,
                                                                      SubscriptionBaseTransitionType.CREATE);
        events.add(event1);
        final BillingEvent event2 = invoiceUtil.createMockBillingEvent(account,
                                                                       subscription,
                                                                       startDate.plusDays(1).toDateTimeAtStartOfDay(),
                                                                       plan,
                                                                       planPhase,
                                                                       null,
                                                                       null,
                                                                       Currency.USD,
                                                                       BillingPeriod.NO_BILLING_PERIOD,
                                                                       1,
                                                                       BillingMode.IN_ADVANCE,
                                                                       "Billing Event Desc",
                                                                       2L,
                                                                       SubscriptionBaseTransitionType.CANCEL);
        events.add(event2);

        // Subscription incorrectly invoiced
        final List<Invoice> existingInvoices = new LinkedList<Invoice>();
        final Invoice invoice = new DefaultInvoice(account.getId(), clock.getUTCToday(), startDate, account.getCurrency());
        invoice.addInvoiceItem(new RecurringInvoiceItem(UUID.randomUUID(),
                                                        startDate.toDateTimeAtStartOfDay(),
                                                        invoice.getId(),
                                                        account.getId(),
                                                        subscription.getBundleId(),
                                                        subscription.getId(),
                                                        null,
                                                        plan.getName(),
                                                        planPhase.getName(),
                                                        null,
                                                        startDate,
                                                        startDate.plusMonths(1),
                                                        amount,
                                                        amount,
                                                        account.getCurrency()));
        // Item adjust the remaining
        invoice.addInvoiceItem(new ItemAdjInvoiceItem(invoice.getInvoiceItems().get(0),
                                                      startDate,
                                                      BigDecimal.ONE.negate(),
                                                      account.getCurrency()));
        // Repaired by the system (the system would have consumed all the remaining amount available)
        invoice.addInvoiceItem(new RepairAdjInvoiceItem(UUID.randomUUID(),
                                                        startDate.toDateTimeAtStartOfDay(),
                                                        invoice.getId(),
                                                        account.getId(),
                                                        startDate.plusDays(1),
                                                        startDate.plusMonths(1),
                                                        new BigDecimal("9").negate(),
                                                        account.getCurrency(),
                                                        invoice.getInvoiceItems().get(0).getId()));
        existingInvoices.add(invoice);

        final List<InvoiceItem> generatedItems = fixedAndRecurringInvoiceItemGenerator.generateItems(account,
                                                                                                     UUID.randomUUID(),
                                                                                                     events,
                                                                                                     new AccountInvoices(null, null, existingInvoices),
                                                                                                     startDate,
                                                                                                     account.getCurrency(),
                                                                                                     new HashMap<UUID, SubscriptionFutureNotificationDates>(),
                                                                                                     null, internalCallContext).getItems();
        assertTrue(generatedItems.isEmpty());
    }

    @Test(groups = "fast", description = "https://github.com/killbill/killbill/issues/664")
    public void testItemPartiallyRepairedAndInvalidAdjustment() throws InvoiceApiException {
        final LocalDate startDate = new LocalDate("2016-01-01");

        final BillingEventSet events = new MockBillingEventSet();
        final BigDecimal amount = BigDecimal.TEN;
        final MockInternationalPrice price = new MockInternationalPrice(new DefaultPrice(amount, account.getCurrency()));
        final Plan plan = new MockPlan("my-plan");
        final PlanPhase planPhase = new MockPlanPhase(price, null, BillingPeriod.MONTHLY, PhaseType.EVERGREEN);
        final BillingEvent event1 = invoiceUtil.createMockBillingEvent(account,
                                                                      subscription,
                                                                      startDate.toDateTimeAtStartOfDay(),
                                                                      plan,
                                                                      planPhase,
                                                                      null,
                                                                      amount,
                                                                      account.getCurrency(),
                                                                      BillingPeriod.MONTHLY,
                                                                      1,
                                                                      BillingMode.IN_ADVANCE,
                                                                      "Billing Event Desc",
                                                                      1L,
                                                                      SubscriptionBaseTransitionType.CREATE);
        events.add(event1);
        final BillingEvent event2 = invoiceUtil.createMockBillingEvent(account,
                                                                       subscription,
                                                                       startDate.plusDays(1).toDateTimeAtStartOfDay(),
                                                                       plan,
                                                                       planPhase,
                                                                       null,
                                                                       null,
                                                                       Currency.USD,
                                                                       BillingPeriod.NO_BILLING_PERIOD,
                                                                       1,
                                                                       BillingMode.IN_ADVANCE,
                                                                       "Billing Event Desc",
                                                                       2L,
                                                                       SubscriptionBaseTransitionType.CANCEL);
        events.add(event2);

        // Subscription incorrectly invoiced
        final List<Invoice> existingInvoices = new LinkedList<Invoice>();
        final Invoice invoice = new DefaultInvoice(account.getId(), clock.getUTCToday(), startDate, account.getCurrency());
        invoice.addInvoiceItem(new RecurringInvoiceItem(UUID.randomUUID(),
                                                        startDate.toDateTimeAtStartOfDay(),
                                                        invoice.getId(),
                                                        account.getId(),
                                                        subscription.getBundleId(),
                                                        subscription.getId(),
                                                        null,
                                                        plan.getName(),
                                                        planPhase.getName(),
                                                        null,
                                                        startDate,
                                                        startDate.plusMonths(1),
                                                        amount,
                                                        amount,
                                                        account.getCurrency()));
        // Repaired by the system
        invoice.addInvoiceItem(new RepairAdjInvoiceItem(UUID.randomUUID(),
                                                        startDate.toDateTimeAtStartOfDay(),
                                                        invoice.getId(),
                                                        account.getId(),
                                                        startDate.plusDays(1),
                                                        startDate.plusMonths(1),
                                                        new BigDecimal("9.68").negate(),
                                                        account.getCurrency(),
                                                        invoice.getInvoiceItems().get(0).getId()));
        // Invalid adjustment (too much)
        invoice.addInvoiceItem(new ItemAdjInvoiceItem(invoice.getInvoiceItems().get(0),
                                                      startDate,
                                                      new BigDecimal("9.68").negate(),
                                                      account.getCurrency()));
        existingInvoices.add(invoice);

        try {
            fixedAndRecurringInvoiceItemGenerator.generateItems(account,
                                                                UUID.randomUUID(),
                                                                events,
                                                                new AccountInvoices(null, null, existingInvoices),
                                                                startDate,
                                                                account.getCurrency(),
                                                                new HashMap<UUID, SubscriptionFutureNotificationDates>(),
                                                                null, internalCallContext).getItems();
            fail();
        } catch (final InvoiceApiException e) {
            assertEquals(e.getCode(), ErrorCode.UNEXPECTED_ERROR.getCode());
            assertTrue(e.getCause().getMessage().startsWith("Too many repairs"));
        }
    }


    //
    //
    //                           SAFETY_BOUNDS TESTS
    //
    //

    private FixedPriceInvoiceItem createFixedPriceInvoiceItem(final LocalDate startDate, final BigDecimal amount) {
        return new FixedPriceInvoiceItem(UUIDs.randomUUID(),
                                         clock.getUTCNow(),
                                         null,
                                         account.getId(),
                                         subscription.getBundleId(),
                                         subscription.getId(),
                                         null,
                                         "planName",
                                         "phaseName",
                                         null,
                                         "description",
                                         startDate,
                                         amount,
                                         account.getCurrency());
    }

    // Simulate a bug in the generator where two fixed items for the same day and subscription end up in the resulting items
    @Test(groups = "fast", description = "https://github.com/killbill/killbill/issues/664")
    public void testTooManyFixedInvoiceItemsForGivenSubscriptionAndStartDatePostMerge() throws InvoiceApiException {
        final MultiValueMap<UUID, LocalDate> createdItemsPerDayPerSubscription = new MultiValueHashMap<>();
        final LocalDate startDate = new LocalDate("2016-01-01");

        final Collection<InvoiceItem> resultingItems = new LinkedList<>();

        // This part should never throw exception because startDate is different
        resultingItems.add(createFixedPriceInvoiceItem(startDate, BigDecimal.ONE));
        resultingItems.add(createFixedPriceInvoiceItem(startDate.plusDays(1), BigDecimal.ONE));
        resultingItems.add(createFixedPriceInvoiceItem(startDate.plusMonths(1), BigDecimal.ONE));

        final FixedAndRecurringInvoiceItemGenerator spied = Mockito.spy(fixedAndRecurringInvoiceItemGenerator);
        spied.safetyBounds(resultingItems, createdItemsPerDayPerSubscription, internalCallContext);

        Mockito.verify(spied, Mockito.times(3)).validateSafetyBoundsWithFixedInvoiceItem(Mockito.any(), Mockito.any());
        Mockito.verify(spied, Mockito.never()).validateSafetyBoundsWithRecurringInvoiceItem(Mockito.any(), Mockito.any());

        resultingItems.clear();

        final InvoiceItem fixedPriceInvoiceItem = createFixedPriceInvoiceItem(startDate, BigDecimal.ONE);
        resultingItems.add(fixedPriceInvoiceItem);
        resultingItems.add(fixedPriceInvoiceItem);

        try {
            spied.safetyBounds(resultingItems, createdItemsPerDayPerSubscription, internalCallContext);
            fail();
        } catch (final InvoiceApiException e) {
            assertEquals(e.getCode(), ErrorCode.UNEXPECTED_ERROR.getCode());
        }

        resultingItems.clear();

        for (int i = 0; i < 2; i++) {
            resultingItems.add(createFixedPriceInvoiceItem(startDate, BigDecimal.ONE.add(new BigDecimal(i))));
        }

        try {
            spied.safetyBounds(resultingItems, createdItemsPerDayPerSubscription, internalCallContext);
            fail();
        } catch (final InvoiceApiException e) {
            assertEquals(e.getCode(), ErrorCode.UNEXPECTED_ERROR.getCode());
        }
    }

    private RecurringInvoiceItem createRecurringInvoiceItem(final LocalDate startDate, final LocalDate endDate, final BigDecimal amount) {
        return new RecurringInvoiceItem(UUIDs.randomUUID(),
                                        clock.getUTCNow(),
                                        null,
                                        account.getId(),
                                        subscription.getBundleId(),
                                        subscription.getId(),
                                        null,
                                        "planName",
                                        "phaseName",
                                        null,
                                        startDate,
                                        endDate,
                                        amount,
                                        BigDecimal.ONE,
                                        account.getCurrency());
    }

    // Simulate a bug in the generator where two recurring items for the same service period and subscription end up in the resulting items
    @Test(groups = "fast", description = "https://github.com/killbill/killbill/issues/664")
    public void testTooManyRecurringInvoiceItemsForGivenSubscriptionAndServicePeriodPostMerge() throws InvoiceApiException {
        final MultiValueMap<UUID, LocalDate> createdItemsPerDayPerSubscription = new MultiValueHashMap<>();
        final LocalDate startDate = new LocalDate("2016-01-01");

        final Collection<InvoiceItem> resultingItems = new LinkedList<>();

        // This part should never throw exception because date interval is different
        resultingItems.add(createRecurringInvoiceItem(startDate, startDate.plusDays(10), BigDecimal.ONE));
        resultingItems.add(createRecurringInvoiceItem(startDate, startDate.plusDays(14), BigDecimal.ONE));
        resultingItems.add(createRecurringInvoiceItem(startDate, startDate.plusWeeks(3), BigDecimal.ONE));
        resultingItems.add(createRecurringInvoiceItem(startDate, startDate.plusMonths(1), BigDecimal.ONE));

        final FixedAndRecurringInvoiceItemGenerator spied = Mockito.spy(fixedAndRecurringInvoiceItemGenerator);
        spied.safetyBounds(resultingItems, createdItemsPerDayPerSubscription, internalCallContext);

        Mockito.verify(spied, Mockito.never()).validateSafetyBoundsWithFixedInvoiceItem(Mockito.any(), Mockito.any());
        Mockito.verify(spied, Mockito.times(4)).validateSafetyBoundsWithRecurringInvoiceItem(Mockito.any(), Mockito.any());

        resultingItems.clear();

        final InvoiceItem recurringInvoiceItem = createRecurringInvoiceItem(startDate, startDate.plusMonths(1), BigDecimal.ONE);
        resultingItems.add(recurringInvoiceItem);
        resultingItems.add(recurringInvoiceItem);

        try {
            spied.safetyBounds(resultingItems, createdItemsPerDayPerSubscription, internalCallContext);
            fail();
        } catch (final InvoiceApiException e) {
            assertEquals(e.getCode(), ErrorCode.UNEXPECTED_ERROR.getCode());
        }

        resultingItems.clear();

        for (int i = 0; i < 2; i++) {
            resultingItems.add(createRecurringInvoiceItem(startDate, startDate.plusMonths(1), BigDecimal.ONE.add(new BigDecimal(i))));
        }

        try {
            spied.safetyBounds(resultingItems, createdItemsPerDayPerSubscription, internalCallContext);
            fail();
        } catch (final InvoiceApiException e) {
            assertEquals(e.getCode(), ErrorCode.UNEXPECTED_ERROR.getCode());
        }
    }


    //
    //
    //                           PROCESS_RECURRING_EVENT TESTS
    //
    //


    //
    // Scenario:
    // - IN_ADVANCE
    // - one CREATE billing event
    // - targetDate = CREATE date
    //
    // => Expect one item + 1 notification
    //
    @Test(groups = "fast")
    public void testInAdvance1() throws InvoiceApiException {

        final BillingMode billingMode = BillingMode.IN_ADVANCE;

        final UUID invoiceId = UUID.randomUUID();
        final InvoiceItemGeneratorLogger invoiceItemGeneratorLogger = new InvoiceItemGeneratorLogger(invoiceId, account.getId(), "recurring", logger);
        final Map<UUID, SubscriptionFutureNotificationDates> perSubscriptionFutureNotificationDates = new HashMap<>();

        final LocalDate eventDate1 = new LocalDate("2020-01-01");
        final BillingEvent event1 = createDefaultBillingEvent(eventDate1, null, BigDecimal.TEN, SubscriptionBaseTransitionType.CREATE, 1, 1, billingMode);


        final LocalDate targetDate = eventDate1;
        final List<InvoiceItem> invoiceItems = fixedAndRecurringInvoiceItemGenerator.processRecurringEvent(invoiceId, account.getId(), event1, null, targetDate, account.getCurrency(), invoiceItemGeneratorLogger, perSubscriptionFutureNotificationDates, internalCallContext);

        assertEquals(invoiceItems.size(), 1);
        assertEquals(invoiceItems.get(0).getStartDate(), eventDate1);
        assertEquals(invoiceItems.get(0).getEndDate(), eventDate1.plusMonths(1));
        assertEquals(invoiceItems.get(0).getAmount().compareTo(BigDecimal.TEN), 0);

        final SubscriptionFutureNotificationDates notifications = perSubscriptionFutureNotificationDates.get(subscription.getId());
        assertEquals(notifications.getNextRecurringDate(), eventDate1.plusMonths(1));
    }

    //
    // Scenario:
    // - IN_ADVANCE
    // - one CREATE billing event
    // - targetDate < CREATE date
    //
    // => Expect nothing
    //
    @Test(groups = "fast")
    public void testInAdvance2() throws InvoiceApiException {

        final BillingMode billingMode = BillingMode.IN_ADVANCE;

        final UUID invoiceId = UUID.randomUUID();
        final InvoiceItemGeneratorLogger invoiceItemGeneratorLogger = new InvoiceItemGeneratorLogger(invoiceId, account.getId(), "recurring", logger);
        final Map<UUID, SubscriptionFutureNotificationDates> perSubscriptionFutureNotificationDates = new HashMap<>();

        final LocalDate eventDate1 = new LocalDate("2020-01-01");
        final BillingEvent event1 = createDefaultBillingEvent(eventDate1, null, BigDecimal.TEN, SubscriptionBaseTransitionType.CREATE, 1, 1, billingMode);


        final LocalDate targetDate = eventDate1.minusDays(1);
        final List<InvoiceItem> invoiceItems = fixedAndRecurringInvoiceItemGenerator.processRecurringEvent(invoiceId, account.getId(), event1, null, targetDate, account.getCurrency(), invoiceItemGeneratorLogger, perSubscriptionFutureNotificationDates, internalCallContext);

        assertEquals(invoiceItems.size(), 0);

        final SubscriptionFutureNotificationDates notifications = perSubscriptionFutureNotificationDates.get(subscription.getId());
        assertNull(notifications);
    }


    //
    // Scenario:
    // - IN_ADVANCE
    // - one CREATE billing event
    // - targetDate in the future (> 1 billingPeriod)
    //
    // => Expect 2 items item + 1 notification
    //
    @Test(groups = "fast")
    public void testInAdvance3() throws InvoiceApiException {

        final BillingMode billingMode = BillingMode.IN_ADVANCE;

        final UUID invoiceId = UUID.randomUUID();
        final InvoiceItemGeneratorLogger invoiceItemGeneratorLogger = new InvoiceItemGeneratorLogger(invoiceId, account.getId(), "recurring", logger);
        final Map<UUID, SubscriptionFutureNotificationDates> perSubscriptionFutureNotificationDates = new HashMap<>();

        final LocalDate eventDate1 = new LocalDate("2020-01-01");
        final BillingEvent event1 = createDefaultBillingEvent(eventDate1, null, BigDecimal.TEN, SubscriptionBaseTransitionType.CREATE, 1, 1, billingMode);


        final LocalDate targetDate = eventDate1.plusMonths(1);
        final List<InvoiceItem> invoiceItems = fixedAndRecurringInvoiceItemGenerator.processRecurringEvent(invoiceId, account.getId(), event1, null, targetDate, account.getCurrency(), invoiceItemGeneratorLogger, perSubscriptionFutureNotificationDates, internalCallContext);

        assertEquals(invoiceItems.size(), 2);
        assertEquals(invoiceItems.get(0).getStartDate(), eventDate1);
        assertEquals(invoiceItems.get(0).getEndDate(), eventDate1.plusMonths(1));
        assertEquals(invoiceItems.get(0).getAmount().compareTo(BigDecimal.TEN), 0);

        assertEquals(invoiceItems.get(1).getStartDate(), eventDate1.plusMonths(1));
        assertEquals(invoiceItems.get(1).getEndDate(), eventDate1.plusMonths(2));
        assertEquals(invoiceItems.get(1).getAmount().compareTo(BigDecimal.TEN), 0);

        final SubscriptionFutureNotificationDates notifications = perSubscriptionFutureNotificationDates.get(subscription.getId());
        assertEquals(notifications.getNextRecurringDate(), eventDate1.plusMonths(2));
    }

    //
    // Scenario:
    // - IN_ADVANCE
    // - one CREATE billing event
    // - one future CHANGE billing event (used to compute period endDate)
    // - targetDate = CREATE date
    //
    // => Expect one item + 1 notification
    //
    @Test(groups = "fast")
    public void testInAdvance4a() throws InvoiceApiException {

        final BillingMode billingMode = BillingMode.IN_ADVANCE;

        final UUID invoiceId = UUID.randomUUID();
        final InvoiceItemGeneratorLogger invoiceItemGeneratorLogger = new InvoiceItemGeneratorLogger(invoiceId, account.getId(), "recurring", logger);
        final Map<UUID, SubscriptionFutureNotificationDates> perSubscriptionFutureNotificationDates = new HashMap<>();

        final LocalDate eventDate1 = new LocalDate("2020-01-01");
        final BillingEvent event1 = createDefaultBillingEvent(eventDate1, null, BigDecimal.TEN, SubscriptionBaseTransitionType.CREATE, 1, 1, billingMode);

        final LocalDate eventDate2 = new LocalDate("2020-01-15");
        final BillingEvent event2 = createDefaultBillingEvent(eventDate2, null, BigDecimal.ONE, SubscriptionBaseTransitionType.CHANGE, 1, 2, billingMode);


        final LocalDate targetDate = eventDate1;
        final List<InvoiceItem> invoiceItems = fixedAndRecurringInvoiceItemGenerator.processRecurringEvent(invoiceId, account.getId(), event1, event2, targetDate, account.getCurrency(), invoiceItemGeneratorLogger, perSubscriptionFutureNotificationDates, internalCallContext);

        assertEquals(invoiceItems.size(), 1);
        assertEquals(invoiceItems.get(0).getStartDate(), eventDate1);
        assertEquals(invoiceItems.get(0).getEndDate(), eventDate2);
        assertEquals(invoiceItems.get(0).getAmount().compareTo(BigDecimal.TEN), -1);

        final SubscriptionFutureNotificationDates notifications = perSubscriptionFutureNotificationDates.get(subscription.getId());
        assertEquals(notifications.getNextRecurringDate(), eventDate2);
    }


    //
    // Scenario: Similar to testInAdvance4a but the CHANGE event is a month after leading to 2 items generated
    // - IN_ADVANCE
    // - one CREATE billing event
    // - one future CHANGE billing event (used to compute period endDate)
    // - targetDate = CREATE date + 1 month
    //
    // => Expect 2 items + 1 notification
    //

    @Test(groups = "fast")
    public void testInAdvance4b() throws InvoiceApiException {

        final BillingMode billingMode = BillingMode.IN_ADVANCE;

        final UUID invoiceId = UUID.randomUUID();
        final InvoiceItemGeneratorLogger invoiceItemGeneratorLogger = new InvoiceItemGeneratorLogger(invoiceId, account.getId(), "recurring", logger);
        final Map<UUID, SubscriptionFutureNotificationDates> perSubscriptionFutureNotificationDates = new HashMap<>();

        final LocalDate eventDate1 = new LocalDate("2020-01-01");
        final BillingEvent event1 = createDefaultBillingEvent(eventDate1, null, BigDecimal.TEN, SubscriptionBaseTransitionType.CREATE, 1, 1, billingMode);

        final LocalDate eventDate2 = new LocalDate("2020-02-15");
        final BillingEvent event2 = createDefaultBillingEvent(eventDate2, null, BigDecimal.ONE, SubscriptionBaseTransitionType.CHANGE, 1, 2, billingMode);


        final LocalDate targetDate = eventDate1.plusMonths(1);
        final List<InvoiceItem> invoiceItems = fixedAndRecurringInvoiceItemGenerator.processRecurringEvent(invoiceId, account.getId(), event1, event2, targetDate, account.getCurrency(), invoiceItemGeneratorLogger, perSubscriptionFutureNotificationDates, internalCallContext);

        assertEquals(invoiceItems.size(), 2);
        assertEquals(invoiceItems.get(0).getStartDate(), eventDate1);
        assertEquals(invoiceItems.get(0).getEndDate(), eventDate1.plusMonths(1));
        assertEquals(invoiceItems.get(0).getAmount().compareTo(BigDecimal.TEN), 0);

        assertEquals(invoiceItems.get(1).getStartDate(), eventDate1.plusMonths(1));
        assertEquals(invoiceItems.get(1).getEndDate(), eventDate2);
        assertEquals(invoiceItems.get(1).getAmount().compareTo(BigDecimal.TEN), -1);

        final SubscriptionFutureNotificationDates notifications = perSubscriptionFutureNotificationDates.get(subscription.getId());
        assertEquals(notifications.getNextRecurringDate(), eventDate2);
    }



    //
    // Scenario: Call processRecurringEvent 2 in a row
    // - IN_ADVANCE
    // - one CREATE billing event
    // - one future CHANGE billing event (used to compute period endDate)
    // - targetDate = CHANGE date
    //
    // => Expect one item + 1 notification
    //
    @Test(groups = "fast")
    public void testInAdvance5() throws InvoiceApiException {

        final BillingMode billingMode = BillingMode.IN_ADVANCE;

        final UUID invoiceId = UUID.randomUUID();
        final InvoiceItemGeneratorLogger invoiceItemGeneratorLogger = new InvoiceItemGeneratorLogger(invoiceId, account.getId(), "recurring", logger);
        final Map<UUID, SubscriptionFutureNotificationDates> perSubscriptionFutureNotificationDates = new HashMap<>();

        final LocalDate eventDate1 = new LocalDate("2020-01-01");
        final BillingEvent event1 = createDefaultBillingEvent(eventDate1, null, BigDecimal.TEN, SubscriptionBaseTransitionType.CREATE, 1, 1, billingMode);

        final LocalDate eventDate2 = new LocalDate("2020-01-15");
        final BillingEvent event2 = createDefaultBillingEvent(eventDate2, null, BigDecimal.ONE, SubscriptionBaseTransitionType.CHANGE, 1, 2, billingMode);


        final LocalDate targetDate = eventDate2;
        List<InvoiceItem> invoiceItems = fixedAndRecurringInvoiceItemGenerator.processRecurringEvent(invoiceId, account.getId(), event1, event2, targetDate, account.getCurrency(), invoiceItemGeneratorLogger, perSubscriptionFutureNotificationDates, internalCallContext);


        assertEquals(invoiceItems.size(), 1);
        assertEquals(invoiceItems.get(0).getStartDate(), eventDate1);
        assertEquals(invoiceItems.get(0).getEndDate(), eventDate2);
        assertEquals(invoiceItems.get(0).getAmount().compareTo(BigDecimal.TEN), -1);


        SubscriptionFutureNotificationDates notifications = perSubscriptionFutureNotificationDates.get(subscription.getId());
        assertEquals(notifications.getNextRecurringDate(), eventDate2);

        // Call processRecurringEvent another time for  eventDate2
        invoiceItems = fixedAndRecurringInvoiceItemGenerator.processRecurringEvent(invoiceId, account.getId(), event2, null, targetDate, account.getCurrency(), invoiceItemGeneratorLogger, perSubscriptionFutureNotificationDates, internalCallContext);


        assertEquals(invoiceItems.size(), 1);

        assertEquals(invoiceItems.get(0).getStartDate(), eventDate2);
        assertEquals(invoiceItems.get(0).getEndDate(), eventDate1.plusMonths(1));
        assertEquals(invoiceItems.get(0).getAmount().compareTo(BigDecimal.TEN), -1);

        notifications = perSubscriptionFutureNotificationDates.get(subscription.getId());
        assertEquals(notifications.getNextRecurringDate(), eventDate1.plusMonths(1));
    }


    //
    // Scenario: Start with future notification and verify the CANCEL event clears it
    // - IN_ADVANCE
    // - one CANCEL billing event
    // - targetDate = CANCEL date
    //
    // => Expect nothing
    //
    @Test(groups = "fast")
    public void testInAdvance6() throws InvoiceApiException {

        final BillingMode billingMode = BillingMode.IN_ADVANCE;

        final UUID invoiceId = UUID.randomUUID();
        final InvoiceItemGeneratorLogger invoiceItemGeneratorLogger = new InvoiceItemGeneratorLogger(invoiceId, account.getId(), "recurring", logger);
        final Map<UUID, SubscriptionFutureNotificationDates> perSubscriptionFutureNotificationDates = new HashMap<>();

        final LocalDate eventDate1 = new LocalDate("2020-01-01");
        /*final BillingEvent event1 = */ createDefaultBillingEvent(eventDate1, null, BigDecimal.TEN, SubscriptionBaseTransitionType.CREATE, 1, 1, billingMode);

        // Set next notification date
        SubscriptionFutureNotificationDates subscriptionFutureNotificationDates = new SubscriptionFutureNotificationDates(billingMode);
        subscriptionFutureNotificationDates.updateNextRecurringDateIfRequired(eventDate1.plusMonths(1));
        perSubscriptionFutureNotificationDates.put(subscription.getId(), subscriptionFutureNotificationDates);

        final LocalDate eventDate2 = new LocalDate("2020-01-15");
        final BillingEvent event2 = createDefaultBillingEvent(eventDate2, null, null, SubscriptionBaseTransitionType.CANCEL, 1, 2, billingMode);


        final LocalDate targetDate = eventDate2;
        final List<InvoiceItem> invoiceItems = fixedAndRecurringInvoiceItemGenerator.processRecurringEvent(invoiceId, account.getId(), event2, null, targetDate, account.getCurrency(), invoiceItemGeneratorLogger, perSubscriptionFutureNotificationDates, internalCallContext);
        assertEquals(invoiceItems.size(), 0);

        final SubscriptionFutureNotificationDates notifications = perSubscriptionFutureNotificationDates.get(subscription.getId());
        assertNull(notifications.getNextRecurringDate());
    }


    //
    // Scenario: Verify CREATE (IN_ARREAR) generates initial future notification and nothing more
    // - IN_ARREAR
    // - one CREATE billing event
    // - targetDate = CREATE date
    //
    // => Expect 1 notification
    //
    @Test(groups = "fast")
    public void testInArrear1() throws InvoiceApiException {

        final BillingMode billingMode = BillingMode.IN_ARREAR;

        final UUID invoiceId = UUID.randomUUID();
        final InvoiceItemGeneratorLogger invoiceItemGeneratorLogger = new InvoiceItemGeneratorLogger(invoiceId, account.getId(), "recurring", logger);
        final Map<UUID, SubscriptionFutureNotificationDates> perSubscriptionFutureNotificationDates = new HashMap<>();

        final LocalDate eventDate1 = new LocalDate("2020-01-01");
        final BillingEvent event1 = createDefaultBillingEvent(eventDate1, null, BigDecimal.TEN, SubscriptionBaseTransitionType.CREATE, 1, 1, billingMode);


        final LocalDate targetDate = eventDate1;
        final List<InvoiceItem> invoiceItems = fixedAndRecurringInvoiceItemGenerator.processRecurringEvent(invoiceId, account.getId(), event1, null, targetDate, account.getCurrency(), invoiceItemGeneratorLogger, perSubscriptionFutureNotificationDates, internalCallContext);

        assertEquals(invoiceItems.size(), 0);

        final SubscriptionFutureNotificationDates notifications = perSubscriptionFutureNotificationDates.get(subscription.getId());
        assertEquals(notifications.getNextRecurringDate(), eventDate1.plusMonths(1));
    }

    //
    // Scenario:
    // - IN_ARREAR
    // - one CREATE billing event
    // - targetDate < CREATE date
    //
    // => Expect nothing
    //
    @Test(groups = "fast")
    public void testInArrear2() throws InvoiceApiException {

        final BillingMode billingMode = BillingMode.IN_ARREAR;

        final UUID invoiceId = UUID.randomUUID();
        final InvoiceItemGeneratorLogger invoiceItemGeneratorLogger = new InvoiceItemGeneratorLogger(invoiceId, account.getId(), "recurring", logger);
        final Map<UUID, SubscriptionFutureNotificationDates> perSubscriptionFutureNotificationDates = new HashMap<>();

        final LocalDate eventDate1 = new LocalDate("2020-01-01");
        final BillingEvent event1 = createDefaultBillingEvent(eventDate1, null, BigDecimal.TEN, SubscriptionBaseTransitionType.CREATE, 1, 1, billingMode);


        final LocalDate targetDate = eventDate1.minusDays(1);
        final List<InvoiceItem> invoiceItems = fixedAndRecurringInvoiceItemGenerator.processRecurringEvent(invoiceId, account.getId(), event1, null, targetDate, account.getCurrency(), invoiceItemGeneratorLogger, perSubscriptionFutureNotificationDates, internalCallContext);

        assertEquals(invoiceItems.size(), 0);

        final SubscriptionFutureNotificationDates notifications = perSubscriptionFutureNotificationDates.get(subscription.getId());
        assertNull(notifications);
    }


    //
    // Scenario:
    // - IN_ARREAR
    // - one CREATE billing event
    // - targetDate in the future (> 1 billingPeriod)
    //
    // => Expect 1 item item + 1 notification
    //
    @Test(groups = "fast")
    public void testInArrear3() throws InvoiceApiException {

        final BillingMode billingMode = BillingMode.IN_ARREAR;

        final UUID invoiceId = UUID.randomUUID();
        final InvoiceItemGeneratorLogger invoiceItemGeneratorLogger = new InvoiceItemGeneratorLogger(invoiceId, account.getId(), "recurring", logger);
        final Map<UUID, SubscriptionFutureNotificationDates> perSubscriptionFutureNotificationDates = new HashMap<>();

        final LocalDate eventDate1 = new LocalDate("2020-01-01");
        final BillingEvent event1 = createDefaultBillingEvent(eventDate1, null, BigDecimal.TEN, SubscriptionBaseTransitionType.CREATE, 1, 1, billingMode);


        final LocalDate targetDate = eventDate1.plusMonths(1);
        final List<InvoiceItem> invoiceItems = fixedAndRecurringInvoiceItemGenerator.processRecurringEvent(invoiceId, account.getId(), event1, null, targetDate, account.getCurrency(), invoiceItemGeneratorLogger, perSubscriptionFutureNotificationDates, internalCallContext);

        assertEquals(invoiceItems.size(), 1);
        assertEquals(invoiceItems.get(0).getStartDate(), eventDate1);
        assertEquals(invoiceItems.get(0).getEndDate(), eventDate1.plusMonths(1));
        assertEquals(invoiceItems.get(0).getAmount().compareTo(BigDecimal.TEN), 0);

        final SubscriptionFutureNotificationDates notifications = perSubscriptionFutureNotificationDates.get(subscription.getId());
        assertEquals(notifications.getNextRecurringDate(), eventDate1.plusMonths(2));
    }



    //
    // Scenario:
    // - IN_ARREAR
    // - one CREATE billing event
    // - one future CHANGE billing event (used to compute period endDate)
    // - targetDate = CREATE date
    //
    // => Expect 1 notification
    //
    @Test(groups = "fast")
    public void testInArrear4a() throws InvoiceApiException {

        final BillingMode billingMode = BillingMode.IN_ARREAR;

        final UUID invoiceId = UUID.randomUUID();
        final InvoiceItemGeneratorLogger invoiceItemGeneratorLogger = new InvoiceItemGeneratorLogger(invoiceId, account.getId(), "recurring", logger);
        final Map<UUID, SubscriptionFutureNotificationDates> perSubscriptionFutureNotificationDates = new HashMap<>();

        final LocalDate eventDate1 = new LocalDate("2020-01-01");
        final BillingEvent event1 = createDefaultBillingEvent(eventDate1, null, BigDecimal.TEN, SubscriptionBaseTransitionType.CREATE, 1, 1, billingMode);

        final LocalDate eventDate2 = new LocalDate("2020-01-15");
        final BillingEvent event2 = createDefaultBillingEvent(eventDate2, null, BigDecimal.ONE, SubscriptionBaseTransitionType.CHANGE, 1, 2, billingMode);


        final LocalDate targetDate = eventDate1;
        final List<InvoiceItem> invoiceItems = fixedAndRecurringInvoiceItemGenerator.processRecurringEvent(invoiceId, account.getId(), event1, event2, targetDate, account.getCurrency(), invoiceItemGeneratorLogger, perSubscriptionFutureNotificationDates, internalCallContext);

        assertEquals(invoiceItems.size(), 0);

        final SubscriptionFutureNotificationDates notifications = perSubscriptionFutureNotificationDates.get(subscription.getId());
        assertEquals(notifications.getNextRecurringDate(), eventDate1.plusMonths(1));
    }

    //
    // Scenario: Similar to testInArrear4b but change event is 1 MONTH after leading to 1 item being generated
    // - IN_ARREAR
    // - one CREATE billing event
    // - one future CHANGE billing event (used to compute period endDate)
    // - targetDate = CREATE date + 1 month
    //
    // => Expect 1 item + 1 notification
    //
    @Test(groups = "fast")
    public void testInArrear4b() throws InvoiceApiException {

        final BillingMode billingMode = BillingMode.IN_ARREAR;

        final UUID invoiceId = UUID.randomUUID();
        final InvoiceItemGeneratorLogger invoiceItemGeneratorLogger = new InvoiceItemGeneratorLogger(invoiceId, account.getId(), "recurring", logger);
        final Map<UUID, SubscriptionFutureNotificationDates> perSubscriptionFutureNotificationDates = new HashMap<>();

        final LocalDate eventDate1 = new LocalDate("2020-01-01");
        final BillingEvent event1 = createDefaultBillingEvent(eventDate1, null, BigDecimal.TEN, SubscriptionBaseTransitionType.CREATE, 1, 1, billingMode);

        final LocalDate eventDate2 = new LocalDate("2020-02-15");
        final BillingEvent event2 = createDefaultBillingEvent(eventDate2, null, BigDecimal.ONE, SubscriptionBaseTransitionType.CHANGE, 1, 2, billingMode);


        final LocalDate targetDate = eventDate1.plusMonths(1);
        List<InvoiceItem> invoiceItems = fixedAndRecurringInvoiceItemGenerator.processRecurringEvent(invoiceId, account.getId(), event1, event2, targetDate, account.getCurrency(), invoiceItemGeneratorLogger, perSubscriptionFutureNotificationDates, internalCallContext);
        assertEquals(invoiceItems.size(), 1);
        assertEquals(invoiceItems.get(0).getStartDate(), eventDate1);
        assertEquals(invoiceItems.get(0).getEndDate(), eventDate1.plusMonths(1));
        assertEquals(invoiceItems.get(0).getAmount().compareTo(BigDecimal.TEN), 0);

        SubscriptionFutureNotificationDates notifications = perSubscriptionFutureNotificationDates.get(subscription.getId());
        assertEquals(notifications.getNextRecurringDate(), eventDate1.plusMonths(2));


        // Bonus 1: ... move targetDate to CHANGE date +. We see the same thing because 'thisEvent'=event1 we are still considering
        final LocalDate newTargetDate = eventDate2;
        invoiceItems = fixedAndRecurringInvoiceItemGenerator.processRecurringEvent(invoiceId, account.getId(), event1, event2, newTargetDate, account.getCurrency(), invoiceItemGeneratorLogger, perSubscriptionFutureNotificationDates, internalCallContext);
        assertEquals(invoiceItems.size(), 2);
        assertEquals(invoiceItems.get(0).getStartDate(), eventDate1);
        assertEquals(invoiceItems.get(0).getEndDate(), eventDate1.plusMonths(1));
        assertEquals(invoiceItems.get(0).getAmount().compareTo(BigDecimal.TEN), 0);

        assertEquals(invoiceItems.get(1).getStartDate(), eventDate1.plusMonths(1));
        assertEquals(invoiceItems.get(1).getEndDate(), eventDate2);
        assertEquals(invoiceItems.get(1).getAmount().compareTo(BigDecimal.TEN), -1);

        notifications = perSubscriptionFutureNotificationDates.get(subscription.getId());
        assertEquals(notifications.getNextRecurringDate(), eventDate1.plusMonths(2));

        // Bonus 2: ... thisEvent=event2
        invoiceItems = fixedAndRecurringInvoiceItemGenerator.processRecurringEvent(invoiceId, account.getId(), event2, null, newTargetDate, account.getCurrency(), invoiceItemGeneratorLogger, perSubscriptionFutureNotificationDates, internalCallContext);
        assertEquals(invoiceItems.size(), 0);

    }


    //
    // Scenario: Call processRecurringEvent 2 in a row
    // - IN_ARREAR
    // - one CREATE billing event
    // - one future CHANGE billing event
    // - targetDate = CHANGE date
    //
    // => Expect one item + 1 notification first time and no new item for the CHANGE
    //
    @Test(groups = "fast")
    public void testInArrear5() throws InvoiceApiException {

        final BillingMode billingMode = BillingMode.IN_ARREAR;

        final UUID invoiceId = UUID.randomUUID();
        final InvoiceItemGeneratorLogger invoiceItemGeneratorLogger = new InvoiceItemGeneratorLogger(invoiceId, account.getId(), "recurring", logger);
        final Map<UUID, SubscriptionFutureNotificationDates> perSubscriptionFutureNotificationDates = new HashMap<>();

        final LocalDate eventDate1 = new LocalDate("2020-01-01");
        final BillingEvent event1 = createDefaultBillingEvent(eventDate1, null, BigDecimal.TEN, SubscriptionBaseTransitionType.CREATE, 1, 1, billingMode);

        final LocalDate eventDate2 = new LocalDate("2020-01-15");
        final BillingEvent event2 = createDefaultBillingEvent(eventDate2, null, BigDecimal.ONE, SubscriptionBaseTransitionType.CHANGE, 1, 2, billingMode);


        final LocalDate targetDate = eventDate2;
        List<InvoiceItem> invoiceItems = fixedAndRecurringInvoiceItemGenerator.processRecurringEvent(invoiceId, account.getId(), event1, event2, targetDate, account.getCurrency(), invoiceItemGeneratorLogger, perSubscriptionFutureNotificationDates, internalCallContext);


        assertEquals(invoiceItems.size(), 1);
        assertEquals(invoiceItems.get(0).getStartDate(), eventDate1);
        assertEquals(invoiceItems.get(0).getEndDate(), eventDate2);
        assertEquals(invoiceItems.get(0).getAmount().compareTo(BigDecimal.TEN), -1);


        SubscriptionFutureNotificationDates notifications = perSubscriptionFutureNotificationDates.get(subscription.getId());
        assertEquals(notifications.getNextRecurringDate(), eventDate1.plusMonths(1));

        // Call processRecurringEvent another time for  eventDate2
        invoiceItems = fixedAndRecurringInvoiceItemGenerator.processRecurringEvent(invoiceId, account.getId(), event2, null, targetDate, account.getCurrency(), invoiceItemGeneratorLogger, perSubscriptionFutureNotificationDates, internalCallContext);
        assertEquals(invoiceItems.size(), 0);

        notifications = perSubscriptionFutureNotificationDates.get(subscription.getId());
        assertEquals(notifications.getNextRecurringDate(), eventDate1.plusMonths(1));
    }


    //
    // Scenario: Start with future notification and verify the CANCEL event clears it
    // - IN_ARREAR
    // - one CANCEL billing event
    // - targetDate = CANCEL date
    //
    // => Expect nothing
    //
    @Test(groups = "fast")
    public void testInArrear6() throws InvoiceApiException {

        final BillingMode billingMode = BillingMode.IN_ARREAR;

        final UUID invoiceId = UUID.randomUUID();
        final InvoiceItemGeneratorLogger invoiceItemGeneratorLogger = new InvoiceItemGeneratorLogger(invoiceId, account.getId(), "recurring", logger);
        final Map<UUID, SubscriptionFutureNotificationDates> perSubscriptionFutureNotificationDates = new HashMap<>();

        final LocalDate eventDate1 = new LocalDate("2020-01-01");
        /*final BillingEvent event1 = */ createDefaultBillingEvent(eventDate1, null, BigDecimal.TEN, SubscriptionBaseTransitionType.CREATE, 1, 1, billingMode);

        // Set next notification date
        SubscriptionFutureNotificationDates subscriptionFutureNotificationDates = new SubscriptionFutureNotificationDates(billingMode);
        subscriptionFutureNotificationDates.updateNextRecurringDateIfRequired(eventDate1.plusMonths(1));
        perSubscriptionFutureNotificationDates.put(subscription.getId(), subscriptionFutureNotificationDates);

        final LocalDate eventDate2 = new LocalDate("2020-01-15");
        final BillingEvent event2 = createDefaultBillingEvent(eventDate2, null, null, SubscriptionBaseTransitionType.CANCEL, 1, 2, billingMode);


        final LocalDate targetDate = eventDate2;
        final List<InvoiceItem> invoiceItems = fixedAndRecurringInvoiceItemGenerator.processRecurringEvent(invoiceId, account.getId(), event2, null, targetDate, account.getCurrency(), invoiceItemGeneratorLogger, perSubscriptionFutureNotificationDates, internalCallContext);
        assertEquals(invoiceItems.size(), 0);

        final SubscriptionFutureNotificationDates notifications = perSubscriptionFutureNotificationDates.get(subscription.getId());
        assertNull(notifications.getNextRecurringDate());
    }

    private BillingEvent createDefaultBillingEvent(final LocalDate eventDate, final BigDecimal fixedAmount, final BigDecimal recurringPrice, final SubscriptionBaseTransitionType eventType, final int billCycleDay, final long ordering, final BillingMode billingMode) {
        final MockInternationalPrice price = new MockInternationalPrice(new DefaultPrice(recurringPrice, account.getCurrency()));
        final MockPlan plan = new MockPlan("my-plan");
        plan.setRecurringBillingMode(billingMode);

        final PlanPhase planPhase = new MockPlanPhase(price, null, BillingPeriod.MONTHLY, PhaseType.EVERGREEN);

        final SubscriptionBillingEvent subscriptionBillingEvent = Mockito.mock(SubscriptionBillingEvent.class);
        Mockito.when(subscriptionBillingEvent.getPlan()).thenReturn(plan);
        Mockito.when(subscriptionBillingEvent.getPlanPhase()).thenReturn(planPhase);
        Mockito.when(subscriptionBillingEvent.getEffectiveDate()).thenReturn(eventDate.toDateTimeAtStartOfDay());
        Mockito.when(subscriptionBillingEvent.getTotalOrdering()).thenReturn(ordering);
        Mockito.when(subscriptionBillingEvent.getType()).thenReturn(eventType);
        Mockito.when(subscriptionBillingEvent.getBcdLocal()).thenReturn(billCycleDay);
        Mockito.when(subscriptionBillingEvent.getCatalogEffectiveDate()).thenReturn(eventDate.toDateTimeAtStartOfDay());

        try {
            // Rely on real (junction) BillingEvent instead of MockBillingEvent to test the real behavior
            return new DefaultBillingEvent(subscriptionBillingEvent,
                                           subscription,
                                           billCycleDay,
                                           null,
                                           Currency.USD);
        } catch (final CatalogApiException e) {
            fail("Catalog error", e);
            return null;
        }
    }
}
