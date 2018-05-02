/*
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

package org.killbill.billing.invoice.generator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
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
import org.killbill.billing.invoice.generator.InvoiceWithMetadata.SubscriptionFutureNotificationDates;
import org.killbill.billing.invoice.model.DefaultInvoice;
import org.killbill.billing.invoice.model.FixedPriceInvoiceItem;
import org.killbill.billing.invoice.model.ItemAdjInvoiceItem;
import org.killbill.billing.invoice.model.RecurringInvoiceItem;
import org.killbill.billing.invoice.model.RepairAdjInvoiceItem;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.junction.BillingEventSet;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestFixedAndRecurringInvoiceItemGenerator extends InvoiceTestSuiteNoDB {

    private Account account;
    private SubscriptionBase subscription;

    @Override
    @BeforeMethod(groups = "fast")
    public void beforeMethod() {
        super.beforeMethod();

        try {
            account = invoiceUtil.createAccount(callContext);
            subscription = invoiceUtil.createSubscription();
        } catch (final Exception e) {
            fail(e.getMessage());
        }
    }

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
                                                                      phase.getName(), invoiceItemDate, fixedPriceAmount, Currency.USD);

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
                                                                      phase.getName(), invoiceItemDate, fixedPriceAmount, Currency.USD);

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
                                                                      phase.getName(), invoiceItemDate, fixedPriceAmount, Currency.USD);

        final BillingEvent event = invoiceUtil.createMockBillingEvent(account, subscription, new DateTime("2016-01-08"),
                                                                      plan, phase,
                                                                      fixedPriceAmount, null, Currency.USD, BillingPeriod.NO_BILLING_PERIOD, 1,
                                                                      BillingMode.IN_ADVANCE, "Billing Event Desc", 1L,
                                                                      SubscriptionBaseTransitionType.CREATE);

        assertTrue(fixedAndRecurringInvoiceItemGenerator.isSameDayAndSameSubscription(prevInvoiceItem, event, internalCallContext));
    }

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
                                                                         existingInvoices,
                                                                         startDate.plusMonths(threshold),
                                                                         account.getCurrency(),
                                                                         new HashMap<UUID, SubscriptionFutureNotificationDates>(),
                                                                         internalCallContext).size(), 1);

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
                                                                                                         existingInvoices,
                                                                                                         startDate.plusMonths(2 * threshold),
                                                                                                         account.getCurrency(),
                                                                                                         new HashMap<UUID, SubscriptionFutureNotificationDates>(),
                                                                                                         internalCallContext);
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
                                                             "Buggy fixed item",
                                                             startDate,
                                                             amount,
                                                             account.getCurrency()));
            existingInvoices.add(invoice);
        }

        final List<InvoiceItem> generatedItems = fixedAndRecurringInvoiceItemGenerator.generateItems(account,
                                                                                                     UUID.randomUUID(),
                                                                                                     events,
                                                                                                     existingInvoices,
                                                                                                     startDate,
                                                                                                     account.getCurrency(),
                                                                                                     new HashMap<UUID, SubscriptionFutureNotificationDates>(),
                                                                                                     internalCallContext);
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
                                                                                                         existingInvoices,
                                                                                                         startDate,
                                                                                                         account.getCurrency(),
                                                                                                         new HashMap<UUID, SubscriptionFutureNotificationDates>(),
                                                                                                         internalCallContext);
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
                                                                                                     existingInvoices,
                                                                                                     startDate,
                                                                                                     account.getCurrency(),
                                                                                                     new HashMap<UUID, SubscriptionFutureNotificationDates>(),
                                                                                                     internalCallContext);
        assertEquals(generatedItems.size(), 2);
        assertTrue(generatedItems.get(0) instanceof RecurringInvoiceItem);
        assertEquals(generatedItems.get(0).getStartDate(), new LocalDate("2016-01-01"));
        assertEquals(generatedItems.get(0).getEndDate(), new LocalDate("2016-02-01"));
        assertEquals(generatedItems.get(0).getAmount().compareTo(amount), 0);
        assertTrue(generatedItems.get(1) instanceof RepairAdjInvoiceItem);
        assertEquals(generatedItems.get(1).getAmount().compareTo(amount.negate()), 0);
        assertEquals(generatedItems.get(1).getLinkedItemId(), invoice.getInvoiceItems().get(0).getId());
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
                                                                                                         existingInvoices,
                                                                                                         startDate,
                                                                                                         account.getCurrency(),
                                                                                                         new HashMap<UUID, SubscriptionFutureNotificationDates>(),
                                                                                                         internalCallContext);

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
                                                        BigDecimal.ONE.negate(), // Note! The amount will not matter
                                                        account.getCurrency(),
                                                        invoice.getInvoiceItems().get(0).getId()));
        existingInvoices.add(invoice);

        // We will generate the correct recurring item
        final List<InvoiceItem> generatedItems = fixedAndRecurringInvoiceItemGenerator.generateItems(account,
                                                                                                     UUID.randomUUID(),
                                                                                                     events,
                                                                                                     existingInvoices,
                                                                                                     startDate,
                                                                                                     account.getCurrency(),
                                                                                                     new HashMap<UUID, SubscriptionFutureNotificationDates>(),
                                                                                                     internalCallContext);
        assertEquals(generatedItems.size(), 1);
        assertTrue(generatedItems.get(0) instanceof RecurringInvoiceItem);
        assertEquals(generatedItems.get(0).getStartDate(), new LocalDate("2016-01-01"));
        assertEquals(generatedItems.get(0).getEndDate(), new LocalDate("2016-02-01"));
        assertEquals(generatedItems.get(0).getAmount().compareTo(amount), 0);
    }

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
                                                        BigDecimal.ONE.negate(), // Note! The amount will not matter
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
            final List<InvoiceItem> generatedItems = fixedAndRecurringInvoiceItemGenerator.generateItems(account,
                                                                                                         UUID.randomUUID(),
                                                                                                         events,
                                                                                                         existingInvoices,
                                                                                                         startDate,
                                                                                                         account.getCurrency(),
                                                                                                         new HashMap<UUID, SubscriptionFutureNotificationDates>(),
                                                                                                         internalCallContext);
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
                                                                                                         existingInvoices,
                                                                                                         startDate,
                                                                                                         account.getCurrency(),
                                                                                                         new HashMap<UUID, SubscriptionFutureNotificationDates>(),
                                                                                                         internalCallContext);
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
                                                                                                         existingInvoices,
                                                                                                         startDate,
                                                                                                         account.getCurrency(),
                                                                                                         new HashMap<UUID, SubscriptionFutureNotificationDates>(),
                                                                                                         internalCallContext);
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
                                                                                                         existingInvoices,
                                                                                                         startDate,
                                                                                                         account.getCurrency(),
                                                                                                         new HashMap<UUID, SubscriptionFutureNotificationDates>(),
                                                                                                         internalCallContext);
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
                                                        BigDecimal.ONE.negate(),
                                                        account.getCurrency(),
                                                        invoice.getInvoiceItems().get(0).getId()));
        invoice.addInvoiceItem(new ItemAdjInvoiceItem(invoice.getInvoiceItems().get(0),
                                                      startDate,
                                                      amount.negate(), // Note! The amount will matter
                                                      account.getCurrency()));
        existingInvoices.add(invoice);

        try {
            final List<InvoiceItem> generatedItems = fixedAndRecurringInvoiceItemGenerator.generateItems(account,
                                                                                                         UUID.randomUUID(),
                                                                                                         events,
                                                                                                         existingInvoices,
                                                                                                         startDate,
                                                                                                         account.getCurrency(),
                                                                                                         new HashMap<UUID, SubscriptionFutureNotificationDates>(),
                                                                                                         internalCallContext);
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
                                                                                                     existingInvoices,
                                                                                                     startDate,
                                                                                                     account.getCurrency(),
                                                                                                     new HashMap<UUID, SubscriptionFutureNotificationDates>(),
                                                                                                     internalCallContext);
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
                                                                                                     existingInvoices,
                                                                                                     startDate,
                                                                                                     account.getCurrency(),
                                                                                                     new HashMap<UUID, SubscriptionFutureNotificationDates>(),
                                                                                                     internalCallContext);
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
            final List<InvoiceItem> generatedItems = fixedAndRecurringInvoiceItemGenerator.generateItems(account,
                                                                                                         UUID.randomUUID(),
                                                                                                         events,
                                                                                                         existingInvoices,
                                                                                                         startDate,
                                                                                                         account.getCurrency(),
                                                                                                         new HashMap<UUID, SubscriptionFutureNotificationDates>(),
                                                                                                         internalCallContext);
            fail();
        } catch (final InvoiceApiException e) {
            assertEquals(e.getCode(), ErrorCode.UNEXPECTED_ERROR.getCode());
            assertTrue(e.getCause().getMessage().endsWith("overly repaired"));
        }
    }

    // Simulate a bug in the generator where two fixed items for the same day and subscription end up in the resulting items
    @Test(groups = "fast", description = "https://github.com/killbill/killbill/issues/664")
    public void testTooManyFixedInvoiceItemsForGivenSubscriptionAndStartDatePostMerge() throws InvoiceApiException {
        final Multimap<UUID, LocalDate> createdItemsPerDayPerSubscription = LinkedListMultimap.<UUID, LocalDate>create();
        final LocalDate startDate = new LocalDate("2016-01-01");

        final Collection<InvoiceItem> resultingItems = new LinkedList<InvoiceItem>();
        final InvoiceItem fixedPriceInvoiceItem = new FixedPriceInvoiceItem(UUID.randomUUID(),
                                                                            clock.getUTCNow(),
                                                                            null,
                                                                            account.getId(),
                                                                            subscription.getBundleId(),
                                                                            subscription.getId(),
                                                                            null,
                                                                            "planName",
                                                                            "phaseName",
                                                                            "description",
                                                                            startDate,
                                                                            BigDecimal.ONE,
                                                                            account.getCurrency());
        resultingItems.add(fixedPriceInvoiceItem);
        resultingItems.add(fixedPriceInvoiceItem);

        try {
            fixedAndRecurringInvoiceItemGenerator.safetyBounds(resultingItems, createdItemsPerDayPerSubscription, internalCallContext);
            fail();
        } catch (final InvoiceApiException e) {
            assertEquals(e.getCode(), ErrorCode.UNEXPECTED_ERROR.getCode());
        }

        resultingItems.clear();
        for (int i = 0; i < 2; i++) {
            resultingItems.add(new FixedPriceInvoiceItem(UUID.randomUUID(),
                                                         clock.getUTCNow(),
                                                         null,
                                                         account.getId(),
                                                         subscription.getBundleId(),
                                                         subscription.getId(),
                                                         null,
                                                         "planName",
                                                         "phaseName",
                                                         "description",
                                                         startDate,
                                                         // Amount shouldn't have any effect
                                                         BigDecimal.ONE.add(new BigDecimal(i)),
                                                         account.getCurrency()));
        }

        try {
            fixedAndRecurringInvoiceItemGenerator.safetyBounds(resultingItems, createdItemsPerDayPerSubscription, internalCallContext);
            fail();
        } catch (final InvoiceApiException e) {
            assertEquals(e.getCode(), ErrorCode.UNEXPECTED_ERROR.getCode());
        }
    }

    // Simulate a bug in the generator where two recurring items for the same service period and subscription end up in the resulting items
    @Test(groups = "fast", description = "https://github.com/killbill/killbill/issues/664")
    public void testTooManyRecurringInvoiceItemsForGivenSubscriptionAndServicePeriodPostMerge() throws InvoiceApiException {
        final Multimap<UUID, LocalDate> createdItemsPerDayPerSubscription = LinkedListMultimap.<UUID, LocalDate>create();
        final LocalDate startDate = new LocalDate("2016-01-01");

        final Collection<InvoiceItem> resultingItems = new LinkedList<InvoiceItem>();
        final InvoiceItem recurringInvoiceItem = new RecurringInvoiceItem(UUID.randomUUID(),
                                                                          clock.getUTCNow(),
                                                                          null,
                                                                          account.getId(),
                                                                          subscription.getBundleId(),
                                                                          subscription.getId(),
                                                                          null,
                                                                          "planName",
                                                                          "phaseName",
                                                                          startDate,
                                                                          startDate.plusMonths(1),
                                                                          BigDecimal.ONE,
                                                                          BigDecimal.ONE,
                                                                          account.getCurrency());
        resultingItems.add(recurringInvoiceItem);
        resultingItems.add(recurringInvoiceItem);

        try {
            fixedAndRecurringInvoiceItemGenerator.safetyBounds(resultingItems, createdItemsPerDayPerSubscription, internalCallContext);
            fail();
        } catch (final InvoiceApiException e) {
            assertEquals(e.getCode(), ErrorCode.UNEXPECTED_ERROR.getCode());
        }

        resultingItems.clear();
        for (int i = 0; i < 2; i++) {
            resultingItems.add(new RecurringInvoiceItem(UUID.randomUUID(),
                                                        clock.getUTCNow(),
                                                        null,
                                                        account.getId(),
                                                        subscription.getBundleId(),
                                                        subscription.getId(),
                                                        null,
                                                        "planName",
                                                        "phaseName",
                                                        startDate,
                                                        startDate.plusMonths(1),
                                                        // Amount shouldn't have any effect
                                                        BigDecimal.TEN,
                                                        BigDecimal.ONE,
                                                        account.getCurrency()));
        }

        try {
            fixedAndRecurringInvoiceItemGenerator.safetyBounds(resultingItems, createdItemsPerDayPerSubscription, internalCallContext);
            fail();
        } catch (final InvoiceApiException e) {
            assertEquals(e.getCode(), ErrorCode.UNEXPECTED_ERROR.getCode());
        }
    }
}
