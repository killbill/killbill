/*
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.Period;
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
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.generator.InvoiceItemGenerator.InvoiceGeneratorResult;
import org.killbill.billing.invoice.generator.InvoiceWithMetadata.SubscriptionFutureNotificationDates;
import org.killbill.billing.invoice.model.DefaultInvoice;
import org.killbill.billing.invoice.model.FixedPriceInvoiceItem;
import org.killbill.billing.invoice.model.RecurringInvoiceItem;
import org.killbill.billing.invoice.optimizer.InvoiceOptimizerBase.AccountInvoices;
import org.killbill.billing.invoice.optimizer.InvoiceOptimizerExp.AccountInvoicesExp;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.junction.BillingEventSet;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.util.features.KillbillFeatures;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestFixedAndRecurringInvoiceItemGeneratorWithOptimization extends InvoiceTestSuiteNoDB {

    private Account account;
    private SubscriptionBase subscription;

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.put(KillbillFeatures.PROP_FEATURE_INVOICE_OPTIMIZATION, "true");
        return getConfigSource(null, allExtraProperties);
    }


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

    @Test(groups = "fast")
    public void testFixedPrice() throws InvoiceApiException {

        final BillingEventSet events = new MockBillingEventSet();

        final BigDecimal fixedPriceAmount = BigDecimal.TEN;
        final MockInternationalPrice fixedPrice = new MockInternationalPrice(new DefaultPrice(fixedPriceAmount, Currency.USD));
        final Plan plan = new MockPlan("my-plan");
        final PlanPhase phase = new MockPlanPhase(null, fixedPrice, BillingPeriod.NO_BILLING_PERIOD, PhaseType.TRIAL);

        final DateTime startDate = new DateTime("2020-01-01");
        final BillingEvent event = invoiceUtil.createMockBillingEvent(account, subscription, startDate,
                                                                      plan, phase,
                                                                      fixedPriceAmount, null, Currency.USD, BillingPeriod.NO_BILLING_PERIOD, 1,
                                                                      BillingMode.IN_ADVANCE, "Billing Event Desc", 1L,
                                                                      SubscriptionBaseTransitionType.CREATE);
        events.add(event);

        // Only look for older invoices 1 month from NOW (we assume targetDate = NOW as time pass normally)
        final Period maxInvoiceLimit = new Period("P1m");

        // Initial invoicing (targetDate1  = startDate)
        // There is no existing invoice
        // => Should generate the item
        final LocalDate targetDate1 = startDate.toLocalDate(); // 2020-01-01
        final LocalDate cuttoffDate1 = targetDate1.minus(maxInvoiceLimit);
        final AccountInvoices existingInvoices1 = new AccountInvoicesExp(cuttoffDate1, null, ImmutableList.of());
        final InvoiceGeneratorResult result1 = fixedAndRecurringInvoiceItemGenerator.generateItems(account,
                                                                                                   UUID.randomUUID(),
                                                                                                   events,
                                                                                                   existingInvoices1,
                                                                                                   targetDate1,
                                                                                                   account.getCurrency(),
                                                                                                   new HashMap<UUID, SubscriptionFutureNotificationDates>(),
                                                                                                   null, internalCallContext);
        assertEquals(result1.getItems().size(), 1);
        assertEquals(result1.getItems().get(0).getInvoiceItemType(), InvoiceItemType.FIXED);
        assertEquals(result1.getItems().get(0).getStartDate().compareTo(new LocalDate("2020-01-01")), 0);

        // One month after invoicing, optimization does not kick-in yet
        // There is an existing invoice
        // => Should not regenerate the item
        final LocalDate targetDate2 = startDate.toLocalDate().plusMonths(1); // 2020-02-01
        final LocalDate cuttoffDate2 = targetDate2.minus(maxInvoiceLimit);

        final Invoice invoice = new DefaultInvoice(account.getId(), clock.getUTCToday(), targetDate2, account.getCurrency());
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
                                                         startDate.toLocalDate(),
                                                         fixedPriceAmount,
                                                         account.getCurrency()));
        final AccountInvoices existingInvoices2 = new AccountInvoicesExp(cuttoffDate2, null, ImmutableList.of(invoice));

        final InvoiceGeneratorResult result2 = fixedAndRecurringInvoiceItemGenerator.generateItems(account,
                                                                                                   UUID.randomUUID(),
                                                                                                   events,
                                                                                                   existingInvoices2,
                                                                                                   targetDate2,
                                                                                                   account.getCurrency(),
                                                                                                   new HashMap<UUID, SubscriptionFutureNotificationDates>(),
                                                                                                   null, internalCallContext);
        assertEquals(result2.getItems().size(), 0);

        // Two month after invoicing, optimization *does* not kick-in
        // There is no existing invoice (optimization removed it)
        // => Should not regenerate the item
        final LocalDate targetDate3 = startDate.toLocalDate().plusMonths(2); // 2020-03-01;
        final LocalDate cuttoffDate3 = targetDate3.minus(maxInvoiceLimit);
        final AccountInvoices existingInvoices3 = new AccountInvoicesExp(cuttoffDate3, null, ImmutableList.of());
        final InvoiceGeneratorResult result3 = fixedAndRecurringInvoiceItemGenerator.generateItems(account,
                                                                                                   UUID.randomUUID(),
                                                                                                   events,
                                                                                                   existingInvoices3,
                                                                                                   targetDate3,
                                                                                                   account.getCurrency(),
                                                                                                   new HashMap<UUID, SubscriptionFutureNotificationDates>(),
                                                                                                   null, internalCallContext);
        assertEquals(result3.getItems().size(), 0);
    }

    @Test(groups = "fast")
    public void testRecurringInAdvance() throws InvoiceApiException {

        final DateTime startDate = new DateTime("2020-01-01");
        final BillingEventSet events = new MockBillingEventSet();
        final BigDecimal amount = BigDecimal.TEN;
        final MockInternationalPrice price = new MockInternationalPrice(new DefaultPrice(amount, account.getCurrency()));
        final Plan plan = new MockPlan("my-plan");
        final PlanPhase planPhase = new MockPlanPhase(price, null, BillingPeriod.MONTHLY, PhaseType.EVERGREEN);
        final BillingEvent event = invoiceUtil.createMockBillingEvent(account,
                                                                      subscription,
                                                                      startDate,
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

        // Only look for older invoices 1 month from NOW (we assume targetDate = NOW as time pass normally)
        final Period maxInvoiceLimit = new Period("P1m");

        // Initial invoicing (targetDate1  = startDate)
        // There is no existing invoice
        // => Should generate the item
        final LocalDate targetDate1 = startDate.toLocalDate(); // 2020-01-01
        final LocalDate cuttoffDate1 = targetDate1.minus(maxInvoiceLimit);
        final AccountInvoices existingInvoices1 = new AccountInvoicesExp(cuttoffDate1, null, ImmutableList.of());
        final InvoiceGeneratorResult result1 = fixedAndRecurringInvoiceItemGenerator.generateItems(account,
                                                                                                   UUID.randomUUID(),
                                                                                                   events,
                                                                                                   existingInvoices1,
                                                                                                   targetDate1,
                                                                                                   account.getCurrency(),
                                                                                                   new HashMap<UUID, SubscriptionFutureNotificationDates>(),
                                                                                                   null, internalCallContext);
        assertEquals(result1.getItems().size(), 1);
        assertEquals(result1.getItems().get(0).getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(result1.getItems().get(0).getStartDate().compareTo(new LocalDate("2020-01-01")), 0);
        assertEquals(result1.getItems().get(0).getEndDate().compareTo(new LocalDate("2020-02-01")), 0);

        // One month after invoicing, optimization does not kick-in yet
        // There is an existing invoice
        // => Should not regenerate the item, but should correctly generate the next RECURRING item
        final LocalDate targetDate2 = startDate.toLocalDate().plusMonths(1); // 2020-02-01
        final LocalDate cuttoffDate2 = targetDate2.minus(maxInvoiceLimit);

        final Invoice invoice2 = new DefaultInvoice(account.getId(), clock.getUTCToday(), targetDate2, account.getCurrency());
        invoice2.addInvoiceItem(new RecurringInvoiceItem(UUID.randomUUID(),
                                                         startDate,
                                                         invoice2.getId(),
                                                         account.getId(),
                                                         subscription.getBundleId(),
                                                         subscription.getId(),
                                                         null,
                                                         event.getPlan().getName(),
                                                         event.getPlanPhase().getName(),
                                                         null,
                                                         startDate.toLocalDate(),
                                                         startDate.plusMonths(1).toLocalDate(),
                                                         amount,
                                                         amount,
                                                         account.getCurrency()));
        final AccountInvoices existingInvoices2 = new AccountInvoicesExp(cuttoffDate2, null, ImmutableList.of(invoice2));
        final InvoiceGeneratorResult result2 = fixedAndRecurringInvoiceItemGenerator.generateItems(account,
                                                                                                   UUID.randomUUID(),
                                                                                                   events,
                                                                                                   existingInvoices2,
                                                                                                   targetDate2,
                                                                                                   account.getCurrency(),
                                                                                                   new HashMap<UUID, SubscriptionFutureNotificationDates>(),
                                                                                                   null, internalCallContext);
        assertEquals(result2.getItems().size(), 1);
        assertEquals(result2.getItems().get(0).getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(result2.getItems().get(0).getStartDate().compareTo(new LocalDate("2020-02-01")), 0);
        assertEquals(result2.getItems().get(0).getEndDate().compareTo(new LocalDate("2020-03-01")), 0);

        // Two month after invoicing, optimization **does** kick-in
        // There are 2 existing invoices but the first one is ignored (optimization)
        // => Should not regenerate the item
        final LocalDate targetDate3 = startDate.toLocalDate().plusMonths(2); // 2020-03-01
        final LocalDate cuttoffDate3 = targetDate3.minus(maxInvoiceLimit);

        final Invoice invoice3 = new DefaultInvoice(account.getId(), clock.getUTCToday(), targetDate3, account.getCurrency());
        invoice3.addInvoiceItem(new RecurringInvoiceItem(UUID.randomUUID(),
                                                         startDate.plusMonths(1),
                                                         invoice3.getId(),
                                                         account.getId(),
                                                         subscription.getBundleId(),
                                                         subscription.getId(),
                                                         null,
                                                         event.getPlan().getName(),
                                                         event.getPlanPhase().getName(),
                                                         null,
                                                         startDate.plusMonths(1).toLocalDate(),
                                                         startDate.plusMonths(2).toLocalDate(),
                                                         amount,
                                                         amount,
                                                         account.getCurrency()));
        final AccountInvoices existingInvoices3 = new AccountInvoicesExp(cuttoffDate3, null, ImmutableList.of(invoice3));
        final InvoiceGeneratorResult result3 = fixedAndRecurringInvoiceItemGenerator.generateItems(account,
                                                                                                   UUID.randomUUID(),
                                                                                                   events,
                                                                                                   existingInvoices3,
                                                                                                   targetDate3,
                                                                                                   account.getCurrency(),
                                                                                                   new HashMap<UUID, SubscriptionFutureNotificationDates>(),
                                                                                                   null, internalCallContext);
        assertEquals(result3.getItems().size(), 1);
        assertEquals(result3.getItems().get(0).getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(result3.getItems().get(0).getStartDate().compareTo(new LocalDate("2020-03-01")), 0);
        assertEquals(result3.getItems().get(0).getEndDate().compareTo(new LocalDate("2020-04-01")), 0);

    }

    @Test(groups = "fast")
    public void testRecurringInArrear() throws InvoiceApiException {

        final DateTime startDate = new DateTime("2020-01-01");
        final BillingEventSet events = new MockBillingEventSet();
        final BigDecimal amount = BigDecimal.TEN;
        final MockInternationalPrice price = new MockInternationalPrice(new DefaultPrice(amount, account.getCurrency()));
        final MockPlan plan = new MockPlan("my-plan");
        plan.setRecurringBillingMode(BillingMode.IN_ARREAR);
        final PlanPhase planPhase = new MockPlanPhase(price, null, BillingPeriod.MONTHLY, PhaseType.EVERGREEN);
        final BillingEvent event = invoiceUtil.createMockBillingEvent(account,
                                                                      subscription,
                                                                      startDate,
                                                                      plan,
                                                                      planPhase,
                                                                      null,
                                                                      amount,
                                                                      account.getCurrency(),
                                                                      planPhase.getRecurring().getBillingPeriod(),
                                                                      1,
                                                                      BillingMode.IN_ARREAR,
                                                                      "Billing Event Desc",
                                                                      1L,
                                                                      SubscriptionBaseTransitionType.CREATE);
        events.add(event);

        // Only look for older invoices 1 month from NOW (we assume targetDate = NOW as time pass normally)
        final Period maxInvoiceLimit = new Period("P1m");

        // Initial invoicing (targetDate1  = startDate)
        // There is nothing to invoice// => Should generate the item
        final LocalDate targetDate1 = startDate.toLocalDate(); // 2020-01-01
        final LocalDate cuttoffDate1 = targetDate1.minus(maxInvoiceLimit);
        final AccountInvoices existingInvoices1 = new AccountInvoicesExp(cuttoffDate1, null, ImmutableList.of());
        final InvoiceGeneratorResult result1 = fixedAndRecurringInvoiceItemGenerator.generateItems(account,
                                                                                                   UUID.randomUUID(),
                                                                                                   events,
                                                                                                   existingInvoices1,
                                                                                                   targetDate1,
                                                                                                   account.getCurrency(),
                                                                                                   new HashMap<UUID, SubscriptionFutureNotificationDates>(),
                                                                                                   null, internalCallContext);
        assertEquals(result1.getItems().size(), 0);

        // Initial invoicing (targetDate2  = startDate + 1 month)
        // There is no existing invoice
        // => Should generate the item
        final LocalDate targetDate2 = startDate.plusMonths(1).toLocalDate(); // 2020-02-01
        final LocalDate cuttoffDate2 = targetDate2.minus(maxInvoiceLimit);
        final AccountInvoices existingInvoices2 = new AccountInvoicesExp(cuttoffDate2, null, ImmutableList.of());

        final InvoiceGeneratorResult result2 = fixedAndRecurringInvoiceItemGenerator.generateItems(account,
                                                                                                   UUID.randomUUID(),
                                                                                                   events,
                                                                                                   existingInvoices2,
                                                                                                   targetDate2,
                                                                                                   account.getCurrency(),
                                                                                                   new HashMap<UUID, SubscriptionFutureNotificationDates>(),
                                                                                                   null, internalCallContext);
        assertEquals(result2.getItems().get(0).getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(result2.getItems().get(0).getStartDate().compareTo(new LocalDate("2020-01-01")), 0);
        assertEquals(result2.getItems().get(0).getEndDate().compareTo(new LocalDate("2020-02-01")), 0);

        final LocalDate targetDate3 = startDate.plusMonths(2).toLocalDate(); // 2020-03-01
        final LocalDate cuttoffDate3 = targetDate3.minus(maxInvoiceLimit);

        final Invoice invoice3 = new DefaultInvoice(account.getId(), clock.getUTCToday(), targetDate3, account.getCurrency());
        invoice3.addInvoiceItem(new RecurringInvoiceItem(UUID.randomUUID(),
                                                         startDate.plusMonths(1),
                                                         invoice3.getId(),
                                                         account.getId(),
                                                         subscription.getBundleId(),
                                                         subscription.getId(),
                                                         null,
                                                         event.getPlan().getName(),
                                                         event.getPlanPhase().getName(),
                                                         null,
                                                         startDate.toLocalDate(),
                                                         startDate.plusMonths(1).toLocalDate(),
                                                         amount,
                                                         amount,
                                                         account.getCurrency()));
        final AccountInvoices existingInvoices3 = new AccountInvoicesExp(cuttoffDate3, null, ImmutableList.of(invoice3));

        final InvoiceGeneratorResult result3 = fixedAndRecurringInvoiceItemGenerator.generateItems(account,
                                                                                                   UUID.randomUUID(),
                                                                                                   events,
                                                                                                   existingInvoices3,
                                                                                                   targetDate3,
                                                                                                   account.getCurrency(),
                                                                                                   new HashMap<UUID, SubscriptionFutureNotificationDates>(),
                                                                                                   null, internalCallContext);

        assertEquals(result3.getItems().get(0).getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(result3.getItems().get(0).getStartDate().compareTo(new LocalDate("2020-02-01")), 0);
        assertEquals(result3.getItems().get(0).getEndDate().compareTo(new LocalDate("2020-03-01")), 0);

    }

}
