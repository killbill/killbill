/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
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
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.model.FixedPriceInvoiceItem;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.junction.BillingEventSet;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.util.AccountDateAndTimeZoneContext;
import org.killbill.billing.util.timezone.DefaultAccountDateAndTimeZoneContext;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestFixedAndRecurringInvoiceItemGenerator extends InvoiceTestSuiteNoDB {

    private Account account;
    private SubscriptionBase subscription;
    private AccountDateAndTimeZoneContext dateAndTimeZoneContext;

    @Override
    @BeforeMethod(groups = "fast")
    public void beforeMethod() {
        super.beforeMethod();

        try {
            account = invoiceUtil.createAccount(callContext);
            subscription = invoiceUtil.createSubscription();
            dateAndTimeZoneContext = new DefaultAccountDateAndTimeZoneContext(new DateTime("2016-01-08T03:01:01.000Z"), internalCallContext);
        } catch (final Exception e) {
            Assert.fail(e.getMessage());
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

        assertFalse(fixedAndRecurringInvoiceItemGenerator.isSameDayAndSameSubscription(prevInvoiceItem, event, dateAndTimeZoneContext));
    }

    @Test(groups = "fast")
    public void testIsSameDayAndSameSubscriptionWithDifferentSubscriptionId() {

        final Plan plan = new MockPlan("my-plan");

        final LocalDate invoiceItemDate = new LocalDate("2016-01-08");
        final BigDecimal fixedPriceAmount = BigDecimal.TEN;
        final MockInternationalPrice fixedPrice = new MockInternationalPrice(new DefaultPrice(fixedPriceAmount, Currency.USD));
        final PlanPhase phase = new MockPlanPhase(null, fixedPrice, BillingPeriod.NO_BILLING_PERIOD, PhaseType.TRIAL);

        final UUID invoiceId = UUID.randomUUID();
        final InvoiceItem prevInvoiceItem = new FixedPriceInvoiceItem(invoiceId, account.getId(), UUID.randomUUID(), UUID.randomUUID(), plan.getName(),
                                                                      phase.getName(), invoiceItemDate, fixedPriceAmount, Currency.USD);

        final BillingEvent event = invoiceUtil.createMockBillingEvent(account, subscription, new DateTime("2016-01-08"),
                                                                      plan, phase,
                                                                      fixedPriceAmount, null, Currency.USD, BillingPeriod.NO_BILLING_PERIOD, 1,
                                                                      BillingMode.IN_ADVANCE, "Billing Event Desc", 1L,
                                                                      SubscriptionBaseTransitionType.CREATE);

        assertFalse(fixedAndRecurringInvoiceItemGenerator.isSameDayAndSameSubscription(prevInvoiceItem, event, dateAndTimeZoneContext));
    }

    @Test(groups = "fast")
    public void testIsSameDayAndSameSubscriptionWithDifferentDate() {

        final Plan plan = new MockPlan("my-plan");

        final LocalDate invoiceItemDate = new LocalDate("2016-01-08");
        final BigDecimal fixedPriceAmount = BigDecimal.TEN;
        final MockInternationalPrice fixedPrice = new MockInternationalPrice(new DefaultPrice(fixedPriceAmount, Currency.USD));
        final PlanPhase phase = new MockPlanPhase(null, fixedPrice, BillingPeriod.NO_BILLING_PERIOD, PhaseType.TRIAL);

        final UUID invoiceId = UUID.randomUUID();
        final InvoiceItem prevInvoiceItem = new FixedPriceInvoiceItem(invoiceId, account.getId(), subscription.getBundleId(), subscription.getId(), plan.getName(),
                                                                      phase.getName(), invoiceItemDate, fixedPriceAmount, Currency.USD);

        final BillingEvent event = invoiceUtil.createMockBillingEvent(account, subscription, new DateTime("2016-02-01"),
                                                                      plan, phase,
                                                                      fixedPriceAmount, null, Currency.USD, BillingPeriod.NO_BILLING_PERIOD, 1,
                                                                      BillingMode.IN_ADVANCE, "Billing Event Desc", 1L,
                                                                      SubscriptionBaseTransitionType.CREATE);

        assertFalse(fixedAndRecurringInvoiceItemGenerator.isSameDayAndSameSubscription(prevInvoiceItem, event, dateAndTimeZoneContext));
    }

    @Test(groups = "fast")
    public void testIsSameDayAndSameSubscriptionWithSameDateAndSubscriptionId() {

        final Plan plan = new MockPlan("my-plan");

        final LocalDate invoiceItemDate = new LocalDate("2016-01-08");
        final BigDecimal fixedPriceAmount = BigDecimal.TEN;
        final MockInternationalPrice fixedPrice = new MockInternationalPrice(new DefaultPrice(fixedPriceAmount, Currency.USD));
        final PlanPhase phase = new MockPlanPhase(null, fixedPrice, BillingPeriod.NO_BILLING_PERIOD, PhaseType.TRIAL);

        final UUID invoiceId = UUID.randomUUID();
        final InvoiceItem prevInvoiceItem = new FixedPriceInvoiceItem(invoiceId, account.getId(), subscription.getBundleId(), subscription.getId(), plan.getName(),
                                                                      phase.getName(), invoiceItemDate, fixedPriceAmount, Currency.USD);

        final BillingEvent event = invoiceUtil.createMockBillingEvent(account, subscription, new DateTime("2016-01-08"),
                                                                      plan, phase,
                                                                      fixedPriceAmount, null, Currency.USD, BillingPeriod.NO_BILLING_PERIOD, 1,
                                                                      BillingMode.IN_ADVANCE, "Billing Event Desc", 1L,
                                                                      SubscriptionBaseTransitionType.CREATE);

        assertTrue(fixedAndRecurringInvoiceItemGenerator.isSameDayAndSameSubscription(prevInvoiceItem, event, dateAndTimeZoneContext));
    }

    @Test(groups = "fast")
    public void testProcessFixedBillingEventsWithCancellationOnSameDay() {

        final LocalDate targetDate = new LocalDate("2016-01-08");

        final UUID invoiceId = UUID.randomUUID();
        final BillingEventSet events = new MockBillingEventSet(internalCallContext);

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
        fixedAndRecurringInvoiceItemGenerator.processFixedBillingEvents(invoiceId, account.getId(), events, targetDate, Currency.USD, proposedItems);
        assertTrue(proposedItems.isEmpty());
    }

    @Test(groups = "fast")
    public void testProcessFixedBillingEventsWithCancellationOnNextDay() {

        final LocalDate targetDate = new LocalDate("2016-01-08");

        final UUID invoiceId = UUID.randomUUID();
        final BillingEventSet events = new MockBillingEventSet(internalCallContext);

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
        fixedAndRecurringInvoiceItemGenerator.processFixedBillingEvents(invoiceId, account.getId(), events, targetDate, Currency.USD, proposedItems);
        assertEquals(proposedItems.size(), 1);
        assertEquals(proposedItems.get(0).getInvoiceItemType(), InvoiceItemType.FIXED);
        assertEquals(proposedItems.get(0).getAmount().compareTo(fixedPriceAmount), 0);
    }


    @Test(groups = "fast")
    public void testProcessFixedBillingEventsWithMultipleChangeOnSameDay() {

        final LocalDate targetDate = new LocalDate("2016-01-08");

        final UUID invoiceId = UUID.randomUUID();
        final BillingEventSet events = new MockBillingEventSet(internalCallContext);

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
        fixedAndRecurringInvoiceItemGenerator.processFixedBillingEvents(invoiceId, account.getId(), events, targetDate, Currency.USD, proposedItems);
        assertEquals(proposedItems.size(), 1);
        assertEquals(proposedItems.get(0).getInvoiceItemType(), InvoiceItemType.FIXED);
        assertEquals(proposedItems.get(0).getAmount().compareTo(fixedPriceAmount3), 0);
    }

}
