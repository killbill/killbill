/*
 * Copyright 2010-2013 Ning, Inc.
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
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
import org.killbill.billing.entity.EntityPersistenceException;
import org.killbill.billing.invoice.InvoiceTestSuiteNoDB;
import org.killbill.billing.invoice.MockBillingEventSet;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.api.InvoicePaymentType;
import org.killbill.billing.invoice.api.InvoiceStatus;
import org.killbill.billing.invoice.model.DefaultInvoice;
import org.killbill.billing.invoice.model.DefaultInvoicePayment;
import org.killbill.billing.invoice.model.FixedPriceInvoiceItem;
import org.killbill.billing.invoice.model.ItemAdjInvoiceItem;
import org.killbill.billing.invoice.model.RecurringInvoiceItem;
import org.killbill.billing.invoice.model.RepairAdjInvoiceItem;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.junction.BillingEventSet;
import org.killbill.billing.mock.MockAccountBuilder;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.util.currency.KillBillMoney;
import org.killbill.clock.Clock;
import org.killbill.clock.DefaultClock;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.killbill.billing.invoice.TestInvoiceHelper.EIGHT;
import static org.killbill.billing.invoice.TestInvoiceHelper.FIFTEEN;
import static org.killbill.billing.invoice.TestInvoiceHelper.FIVE;
import static org.killbill.billing.invoice.TestInvoiceHelper.FORTY;
import static org.killbill.billing.invoice.TestInvoiceHelper.FOURTEEN;
import static org.killbill.billing.invoice.TestInvoiceHelper.NINETEEN;
import static org.killbill.billing.invoice.TestInvoiceHelper.ONE;
import static org.killbill.billing.invoice.TestInvoiceHelper.ONE_HUNDRED;
import static org.killbill.billing.invoice.TestInvoiceHelper.TEN;
import static org.killbill.billing.invoice.TestInvoiceHelper.THIRTEEN;
import static org.killbill.billing.invoice.TestInvoiceHelper.THIRTY;
import static org.killbill.billing.invoice.TestInvoiceHelper.THIRTY_ONE;
import static org.killbill.billing.invoice.TestInvoiceHelper.TWELVE;
import static org.killbill.billing.invoice.TestInvoiceHelper.TWENTY;
import static org.killbill.billing.invoice.TestInvoiceHelper.TWENTY_FIVE;
import static org.killbill.billing.invoice.TestInvoiceHelper.TWENTY_FOUR;
import static org.killbill.billing.invoice.TestInvoiceHelper.TWO;
import static org.killbill.billing.invoice.TestInvoiceHelper.ZERO;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

@SuppressWarnings("UnusedDeclaration")
public class TestDefaultInvoiceGenerator extends InvoiceTestSuiteNoDB {

    private Account account;

    private static final Logger log = LoggerFactory.getLogger(TestDefaultInvoiceGenerator.class);

    @BeforeClass(groups = "fast")
    protected void beforeClass() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeClass();
        final Clock clock = new DefaultClock();
        this.account = new MockAccountBuilder().name(UUID.randomUUID().toString().substring(1, 8))
                                               .firstNameLength(6)
                                               .email(UUID.randomUUID().toString().substring(1, 8))
                                               .phone(UUID.randomUUID().toString().substring(1, 8))
                                               .migrated(false)
                                               .externalKey(UUID.randomUUID().toString().substring(1, 8))
                                               .billingCycleDayLocal(31)
                                               .currency(Currency.USD)
                                               .paymentMethodId(UUID.randomUUID())
                                               .timeZone(DateTimeZone.UTC)
                                               .build();

    }

    @Test(groups = "fast")
    public void testWithNullEventSetAndNullInvoiceSet() throws InvoiceApiException {
        final InvoiceWithMetadata invoiceWithMetadata = generator.generateInvoice(account, null, null, null, clock.getUTCToday(), Currency.USD, internalCallContext);
        assertNull(invoiceWithMetadata.getInvoice());
    }

    @Test(groups = "fast")
    public void testWithEmptyEventSet() throws InvoiceApiException {
        final BillingEventSet events = new MockBillingEventSet();
        final InvoiceWithMetadata invoiceWithMetadata = generator.generateInvoice(account, events, null, null, clock.getUTCToday(), Currency.USD, internalCallContext);
        assertNull(invoiceWithMetadata.getInvoice());
    }

    @Test(groups = "fast")
    public void testWithSingleMonthlyEvent() throws InvoiceApiException, CatalogApiException {
        final BillingEventSet events = new MockBillingEventSet();

        final SubscriptionBase sub = createSubscription();
        final LocalDate startDate = invoiceUtil.buildDate(2011, 9, 1);

        final Plan plan = new MockPlan();
        final BigDecimal rate1 = TEN;
        final PlanPhase phase = createMockMonthlyPlanPhase(rate1);

        final BillingEvent event = createBillingEvent(sub.getId(), sub.getBundleId(), startDate, plan, phase, 1);
        events.add(event);

        final LocalDate targetDate = invoiceUtil.buildDate(2011, 10, 3);
        final InvoiceWithMetadata invoiceWithMetadata = generator.generateInvoice(account, events, null, null, targetDate, Currency.USD, internalCallContext);
        final Invoice invoice = invoiceWithMetadata.getInvoice();
        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 2);
        assertEquals(invoice.getBalance(), KillBillMoney.of(TWENTY, invoice.getCurrency()));
        assertEquals(invoice.getInvoiceItems().get(0).getSubscriptionId(), sub.getId());

        assertEquals(invoice.getInvoiceItems().get(0).getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(invoice.getInvoiceItems().get(0).getStartDate(), new LocalDate(2011, 9, 1));
        assertEquals(invoice.getInvoiceItems().get(0).getEndDate(), new LocalDate(2011, 10, 1));

        assertEquals(invoice.getInvoiceItems().get(1).getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(invoice.getInvoiceItems().get(1).getStartDate(), new LocalDate(2011, 10, 1));
        assertEquals(invoice.getInvoiceItems().get(1).getEndDate(), new LocalDate(2011, 11, 1));
    }

    @Test(groups = "fast")
    public void testWithSingleThirtyDaysEvent() throws InvoiceApiException, CatalogApiException {
        final BillingEventSet events = new MockBillingEventSet();

        final SubscriptionBase sub = createSubscription();
        final LocalDate startDate = invoiceUtil.buildDate(2011, 9, 1);

        final Plan plan = new MockPlan();
        final BigDecimal rate1 = TEN;
        final PlanPhase phase = createMockThirtyDaysPlanPhase(rate1);

        final BillingEvent event = createBillingEvent(sub.getId(), sub.getBundleId(), startDate, plan, phase, 1);
        events.add(event);

        final LocalDate targetDate = invoiceUtil.buildDate(2011, 10, 3);
        final InvoiceWithMetadata invoiceWithMetadata = generator.generateInvoice(account, events, null, null, targetDate, Currency.USD, internalCallContext);
        final Invoice invoice = invoiceWithMetadata.getInvoice();
        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 2);
        assertEquals(invoice.getBalance(), KillBillMoney.of(TWENTY, invoice.getCurrency()));
        assertEquals(invoice.getInvoiceItems().get(0).getSubscriptionId(), sub.getId());

        assertEquals(invoice.getInvoiceItems().get(0).getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(invoice.getInvoiceItems().get(0).getStartDate(), new LocalDate(2011, 9, 1));
        assertEquals(invoice.getInvoiceItems().get(0).getEndDate(), new LocalDate(2011, 10, 1));

        assertEquals(invoice.getInvoiceItems().get(1).getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(invoice.getInvoiceItems().get(1).getStartDate(), new LocalDate(2011, 10, 1));
        assertEquals(invoice.getInvoiceItems().get(1).getEndDate(), new LocalDate(2011, 10, 31));
    }

    @Test(groups = "fast")
    public void testSimpleWithTimeZone() throws InvoiceApiException, CatalogApiException {
        final SubscriptionBase sub = createSubscription();
        final Plan plan = new MockPlan();
        final BigDecimal rate = TEN;
        final PlanPhase phase = createMockMonthlyPlanPhase(rate);

        // Start date was the 16 local, but was the 17 UTC
        final int bcdLocal = 16;
        final LocalDate startDate = invoiceUtil.buildDate(2012, 7, bcdLocal);

        final BillingEventSet events = new MockBillingEventSet();
        final BillingEvent event = createBillingEvent(sub.getId(), sub.getBundleId(), startDate, plan, phase, bcdLocal);
        events.add(event);

        // Target date is the next BCD, in local time
        final LocalDate targetDate = invoiceUtil.buildDate(2012, 8, bcdLocal);
        final InvoiceWithMetadata invoiceWithMetadata = generator.generateInvoice(account, events, null, null, targetDate, Currency.USD, internalCallContext);
        final Invoice invoice = invoiceWithMetadata.getInvoice();
        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 2);
        assertEquals(invoice.getInvoiceItems().get(0).getStartDate(), invoiceUtil.buildDate(2012, 7, 16));
        assertEquals(invoice.getInvoiceItems().get(0).getEndDate(), invoiceUtil.buildDate(2012, 8, 16));
        assertEquals(invoice.getInvoiceItems().get(1).getStartDate(), invoiceUtil.buildDate(2012, 8, 16));
        assertEquals(invoice.getInvoiceItems().get(1).getEndDate(), invoiceUtil.buildDate(2012, 9, 16));
    }

    @Test(groups = "fast")
    public void testSimpleWithSingleDiscountEvent() throws Exception {
        final UUID accountId = UUID.randomUUID();
        final SubscriptionBase sub = createSubscription();
        final Plan plan = new MockPlan("Plan with a single discount phase");
        final PlanPhase phaseEvergreen = createMockMonthlyPlanPhase(EIGHT, PhaseType.DISCOUNT);
        final int bcdLocal = 16;
        final LocalDate startDate = invoiceUtil.buildDate(2012, 7, 16);

        final BillingEventSet events = new MockBillingEventSet();
        events.add(createBillingEvent(sub.getId(), sub.getBundleId(), startDate, plan, phaseEvergreen, bcdLocal));

        // Set a target date of today (start date)
        final LocalDate targetDate = startDate;
        final InvoiceWithMetadata invoiceWithMetadata = generator.generateInvoice(account, events, null, null, targetDate, Currency.USD, internalCallContext);
        final Invoice invoice = invoiceWithMetadata.getInvoice();
        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 1);
        assertEquals(invoice.getInvoiceItems().get(0).getStartDate(), invoiceUtil.buildDate(2012, 7, 16));
        assertEquals(invoice.getInvoiceItems().get(0).getEndDate(), invoiceUtil.buildDate(2012, 8, 16));
    }

    @Test(groups = "fast")
    public void testWithSingleMonthlyEventWithLeadingProRation() throws InvoiceApiException, CatalogApiException {
        final BillingEventSet events = new MockBillingEventSet();

        final SubscriptionBase sub = createSubscription();
        final LocalDate startDate = invoiceUtil.buildDate(2011, 9, 1);

        final Plan plan = new MockPlan();
        final BigDecimal rate = TEN;
        final PlanPhase phase = createMockMonthlyPlanPhase(rate);
        final BillingEvent event = createBillingEvent(sub.getId(), sub.getBundleId(), startDate, plan, phase, 15);
        events.add(event);

        final LocalDate targetDate = invoiceUtil.buildDate(2011, 10, 3);
        final InvoiceWithMetadata invoiceWithMetadata = generator.generateInvoice(account, events, null, null, targetDate, Currency.USD, internalCallContext);
        final Invoice invoice = invoiceWithMetadata.getInvoice();
        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 2);

        final BigDecimal expectedNumberOfBillingCycles;
        expectedNumberOfBillingCycles = ONE.add(FOURTEEN.divide(THIRTY_ONE, KillBillMoney.ROUNDING_METHOD));
        final BigDecimal expectedAmount = KillBillMoney.of(expectedNumberOfBillingCycles.multiply(rate), invoice.getCurrency());
        assertEquals(invoice.getBalance(), expectedAmount);
    }

    @Test(groups = "fast")
    public void testTwoMonthlySubscriptionsWithAlignedBillingDates() throws InvoiceApiException, CatalogApiException {
        final BillingEventSet events = new MockBillingEventSet();

        final Plan plan1 = new MockPlan();
        final BigDecimal rate1 = FIVE;
        final PlanPhase phase1 = createMockMonthlyPlanPhase(rate1);

        final Plan plan2 = new MockPlan();
        final BigDecimal rate2 = TEN;
        final PlanPhase phase2 = createMockMonthlyPlanPhase(rate2);

        final SubscriptionBase sub = createSubscription();

        final BillingEvent event1 = createBillingEvent(sub.getId(), sub.getBundleId(), invoiceUtil.buildDate(2011, 9, 1), plan1, phase1, 1);
        events.add(event1);

        final BillingEvent event2 = createBillingEvent(sub.getId(), sub.getBundleId(), invoiceUtil.buildDate(2011, 10, 1), plan2, phase2, 1);
        events.add(event2);

        final LocalDate targetDate = invoiceUtil.buildDate(2011, 10, 3);
        final InvoiceWithMetadata invoiceWithMetadata = generator.generateInvoice(account, events, null, null, targetDate, Currency.USD, internalCallContext);
        final Invoice invoice = invoiceWithMetadata.getInvoice();
        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 2);
        assertEquals(invoice.getBalance(), KillBillMoney.of(rate1.add(rate2), invoice.getCurrency()));
    }

    @Test(groups = "fast")
    public void testOnePlan_TwoMonthlyPhases_ChangeImmediate() throws InvoiceApiException, CatalogApiException {
        final BillingEventSet events = new MockBillingEventSet();

        final Plan plan1 = new MockPlan();
        final BigDecimal rate1 = FIVE;
        final PlanPhase phase1 = createMockMonthlyPlanPhase(rate1);

        final SubscriptionBase sub = createSubscription();
        final BillingEvent event1 = createBillingEvent(sub.getId(), sub.getBundleId(), invoiceUtil.buildDate(2011, 9, 1), plan1, phase1, 1);
        events.add(event1);

        final BigDecimal rate2 = TEN;
        final PlanPhase phase2 = createMockMonthlyPlanPhase(rate2);
        final BillingEvent event2 = createBillingEvent(sub.getId(), sub.getBundleId(), invoiceUtil.buildDate(2011, 10, 15), plan1, phase2, 15);
        events.add(event2);

        final LocalDate targetDate = invoiceUtil.buildDate(2011, 12, 3);
        final UUID accountId = UUID.randomUUID();
        final InvoiceWithMetadata invoiceWithMetadata = generator.generateInvoice(account, events, null, null, targetDate, Currency.USD, internalCallContext);
        final Invoice invoice = invoiceWithMetadata.getInvoice();
        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 4);

        final BigDecimal numberOfCyclesEvent1;
        numberOfCyclesEvent1 = ONE.add(FOURTEEN.divide(THIRTY_ONE, KillBillMoney.ROUNDING_METHOD));

        final BigDecimal numberOfCyclesEvent2 = TWO;

        BigDecimal expectedValue;
        expectedValue = numberOfCyclesEvent1.multiply(rate1);
        expectedValue = expectedValue.add(numberOfCyclesEvent2.multiply(rate2));
        expectedValue = KillBillMoney.of(expectedValue, invoice.getCurrency());

        assertEquals(invoice.getBalance(), expectedValue);
    }

    @Test(groups = "fast")
    public void testOnePlan_ThreeMonthlyPhases_ChangeEOT() throws InvoiceApiException, CatalogApiException {
        final BillingEventSet events = new MockBillingEventSet();

        final Plan plan1 = new MockPlan();
        final BigDecimal rate1 = FIVE;
        final PlanPhase phase1 = createMockMonthlyPlanPhase(rate1);

        final SubscriptionBase sub = createSubscription();
        final BillingEvent event1 = createBillingEvent(sub.getId(), sub.getBundleId(), invoiceUtil.buildDate(2011, 9, 1), plan1, phase1, 1);
        events.add(event1);

        final BigDecimal rate2 = TEN;
        final PlanPhase phase2 = createMockMonthlyPlanPhase(rate2);
        final BillingEvent event2 = createBillingEvent(sub.getId(), sub.getBundleId(), invoiceUtil.buildDate(2011, 10, 1), plan1, phase2, 1);
        events.add(event2);

        final BigDecimal rate3 = THIRTY;
        final PlanPhase phase3 = createMockMonthlyPlanPhase(rate3);
        final BillingEvent event3 = createBillingEvent(sub.getId(), sub.getBundleId(), invoiceUtil.buildDate(2011, 11, 1), plan1, phase3, 1);
        events.add(event3);

        final LocalDate targetDate = invoiceUtil.buildDate(2011, 12, 3);
        final InvoiceWithMetadata invoiceWithMetadata = generator.generateInvoice(account, events, null, null, targetDate, Currency.USD, internalCallContext);
        final Invoice invoice = invoiceWithMetadata.getInvoice();
        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 4);
        assertEquals(invoice.getBalance(), KillBillMoney.of(rate1.add(rate2).add(TWO.multiply(rate3)), invoice.getCurrency()));
    }

    @Test(groups = "fast")
    public void testSingleEventWithExistingInvoice() throws InvoiceApiException, CatalogApiException {
        final BillingEventSet events = new MockBillingEventSet();

        final SubscriptionBase sub = createSubscription();
        final LocalDate startDate = invoiceUtil.buildDate(2011, 9, 1);

        final Plan plan1 = new MockPlan();
        final BigDecimal rate = FIVE;
        final PlanPhase phase1 = createMockMonthlyPlanPhase(rate);

        final BillingEvent event1 = createBillingEvent(sub.getId(), sub.getBundleId(), startDate, plan1, phase1, 1);
        events.add(event1);

        LocalDate targetDate = invoiceUtil.buildDate(2011, 12, 1);
        final InvoiceWithMetadata invoiceWithMetadata1 = generator.generateInvoice(account, events, null, null, targetDate, Currency.USD, internalCallContext);
        final List<Invoice> existingInvoices = new ArrayList<Invoice>();
        final Invoice invoice1 = invoiceWithMetadata1.getInvoice();
        existingInvoices.add(invoice1);

        targetDate = invoiceUtil.buildDate(2011, 12, 3);
        final InvoiceWithMetadata invoiceWithMetadata2 = generator.generateInvoice(account, events, existingInvoices, null, targetDate, Currency.USD, internalCallContext);
        final Invoice invoice2 = invoiceWithMetadata2.getInvoice();
        assertNull(invoice2);
    }

    // TODO: modify this test to keep a running total of expected invoice amount over time
    @Test(groups = "fast")
    public void testMultiplePlansWithUtterChaos() throws InvoiceApiException, CatalogApiException {
        // plan 1: change of phase from trial to discount followed by immediate cancellation; (covers phase change, cancel, pro-ration)
        // plan 2: single plan that moves from trial to discount to evergreen; BCD = 10 (covers phase change)
        // plan 3: change of term from monthly (BCD = 20) to annual (BCD = 31; immediate)
        // plan 4: change of plan, effective EOT, BCD = 7 (covers change of plan)
        // plan 5: addon to plan 2, with bill cycle alignment to plan; immediate cancellation
        final UUID accountId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final UUID subscriptionId1 = UUID.randomUUID();
        final UUID subscriptionId2 = UUID.randomUUID();
        final UUID subscriptionId3 = UUID.randomUUID();
        final UUID subscriptionId4 = UUID.randomUUID();
        final UUID subscriptionId5 = UUID.randomUUID();

        final Plan plan1 = new MockPlan("Change from trial to discount with immediate cancellation");
        final PlanPhase plan1Phase1 = createMockMonthlyPlanPhase(EIGHT, PhaseType.TRIAL);
        final PlanPhase plan1Phase2 = createMockMonthlyPlanPhase(TWELVE, PhaseType.DISCOUNT);
        final PlanPhase plan1Phase3 = createMockMonthlyPlanPhase();
        final LocalDate plan1StartDate = invoiceUtil.buildDate(2011, 1, 5);
        final LocalDate plan1PhaseChangeDate = invoiceUtil.buildDate(2011, 4, 5);
        final LocalDate plan1CancelDate = invoiceUtil.buildDate(2011, 4, 29);

        final Plan plan2 = new MockPlan("Change phase from trial to discount to evergreen");
        final PlanPhase plan2Phase1 = createMockMonthlyPlanPhase(TWENTY, PhaseType.TRIAL);
        final PlanPhase plan2Phase2 = createMockMonthlyPlanPhase(THIRTY, PhaseType.DISCOUNT);
        final PlanPhase plan2Phase3 = createMockMonthlyPlanPhase(FORTY, PhaseType.EVERGREEN);
        final LocalDate plan2StartDate = invoiceUtil.buildDate(2011, 3, 10);
        final LocalDate plan2PhaseChangeToDiscountDate = invoiceUtil.buildDate(2011, 6, 10);
        final LocalDate plan2PhaseChangeToEvergreenDate = invoiceUtil.buildDate(2011, 9, 10);

        final Plan plan3 = new MockPlan("Upgrade with immediate change, BCD = 31");
        final PlanPhase plan3Phase1 = createMockMonthlyPlanPhase(TEN, PhaseType.EVERGREEN);
        final PlanPhase plan3Phase2 = createMockAnnualPlanPhase(ONE_HUNDRED, PhaseType.EVERGREEN);
        final LocalDate plan3StartDate = invoiceUtil.buildDate(2011, 5, 20);
        final LocalDate plan3UpgradeToAnnualDate = invoiceUtil.buildDate(2011, 7, 31);

        final Plan plan4a = new MockPlan("Plan change effective EOT; plan 1");
        final Plan plan4b = new MockPlan("Plan change effective EOT; plan 2");
        final PlanPhase plan4aPhase1 = createMockMonthlyPlanPhase(FIFTEEN);
        final PlanPhase plan4bPhase1 = createMockMonthlyPlanPhase(TWENTY_FOUR);

        final LocalDate plan4StartDate = invoiceUtil.buildDate(2011, 6, 7);
        final LocalDate plan4ChangeOfPlanDate = invoiceUtil.buildDate(2011, 8, 7);

        final Plan plan5 = new MockPlan("Add-on");
        final PlanPhase plan5Phase1 = createMockMonthlyPlanPhase(TWENTY);
        final PlanPhase plan5Phase2 = createMockMonthlyPlanPhase();
        final LocalDate plan5StartDate = invoiceUtil.buildDate(2011, 6, 21);
        final LocalDate plan5CancelDate = invoiceUtil.buildDate(2011, 10, 7);

        BigDecimal expectedAmount;
        final List<Invoice> invoices = new ArrayList<Invoice>();
        final BillingEventSet events = new MockBillingEventSet();

        // on 1/5/2011, create SubscriptionBase 1 (trial)
        events.add(createBillingEvent(subscriptionId1, bundleId, plan1StartDate, plan1, plan1Phase1, 5));
        expectedAmount = EIGHT;
        testInvoiceGeneration(accountId, events, invoices, plan1StartDate, 1, expectedAmount);

        // on 2/5/2011, invoice SubscriptionBase 1 (trial)
        expectedAmount = EIGHT;
        testInvoiceGeneration(accountId, events, invoices, invoiceUtil.buildDate(2011, 2, 5), 1, expectedAmount);

        // on 3/5/2011, invoice SubscriptionBase 1 (trial)
        expectedAmount = EIGHT;
        testInvoiceGeneration(accountId, events, invoices, invoiceUtil.buildDate(2011, 3, 5), 1, expectedAmount);

        // on 3/10/2011, create SubscriptionBase 2 (trial)
        events.add(createBillingEvent(subscriptionId2, bundleId, plan2StartDate, plan2, plan2Phase1, 10));
        expectedAmount = TWENTY;
        testInvoiceGeneration(accountId, events, invoices, plan2StartDate, 1, expectedAmount);

        // on 4/5/2011, invoice SubscriptionBase 1 (discount)
        events.add(createBillingEvent(subscriptionId1, bundleId, plan1PhaseChangeDate, plan1, plan1Phase2, 5));
        expectedAmount = TWELVE;
        testInvoiceGeneration(accountId, events, invoices, plan1PhaseChangeDate, 1, expectedAmount);

        // on 4/10/2011, invoice SubscriptionBase 2 (trial)
        expectedAmount = TWENTY;
        testInvoiceGeneration(accountId, events, invoices, invoiceUtil.buildDate(2011, 4, 10), 1, expectedAmount);

        // on 4/29/2011, cancel SubscriptionBase 1
        events.add(createBillingEvent(subscriptionId1, bundleId, plan1CancelDate, plan1, plan1Phase3, 5));

        expectedAmount = new BigDecimal("-2.40");
        testInvoiceGeneration(accountId, events, invoices, plan1CancelDate, 1, expectedAmount);

        // on 5/10/2011, invoice SubscriptionBase 2 (trial)
        expectedAmount = TWENTY;
        testInvoiceGeneration(accountId, events, invoices, invoiceUtil.buildDate(2011, 5, 10), 1, expectedAmount);

        // on 5/20/2011, create SubscriptionBase 3 (monthly)
        events.add(createBillingEvent(subscriptionId3, bundleId, plan3StartDate, plan3, plan3Phase1, 20));
        expectedAmount = TEN;
        testInvoiceGeneration(accountId, events, invoices, plan3StartDate, 1, expectedAmount);

        // on 6/7/2011, create SubscriptionBase 4
        events.add(createBillingEvent(subscriptionId4, bundleId, plan4StartDate, plan4a, plan4aPhase1, 7));
        expectedAmount = FIFTEEN;
        testInvoiceGeneration(accountId, events, invoices, plan4StartDate, 1, expectedAmount);

        // on 6/10/2011, invoice SubscriptionBase 2 (discount)
        events.add(createBillingEvent(subscriptionId2, bundleId, plan2PhaseChangeToDiscountDate, plan2, plan2Phase2, 10));
        expectedAmount = THIRTY;
        testInvoiceGeneration(accountId, events, invoices, plan2PhaseChangeToDiscountDate, 1, expectedAmount);

        // on 6/20/2011, invoice SubscriptionBase 3 (monthly)
        expectedAmount = TEN;
        testInvoiceGeneration(accountId, events, invoices, invoiceUtil.buildDate(2011, 6, 20), 1, expectedAmount);

        // on 6/21/2011, create add-on (subscription 5)
        events.add(createBillingEvent(subscriptionId5, bundleId, plan5StartDate, plan5, plan5Phase1, 10));
        expectedAmount = TWENTY.multiply(NINETEEN).divide(THIRTY, KillBillMoney.ROUNDING_METHOD);
        testInvoiceGeneration(accountId, events, invoices, plan5StartDate, 1, expectedAmount);

        // on 7/7/2011, invoice SubscriptionBase 4 (plan 1)
        expectedAmount = FIFTEEN;
        testInvoiceGeneration(accountId, events, invoices, invoiceUtil.buildDate(2011, 7, 7), 1, expectedAmount);

        // on 7/10/2011, invoice SubscriptionBase 2 (discount), invoice SubscriptionBase 5
        expectedAmount = THIRTY.add(TWENTY);
        testInvoiceGeneration(accountId, events, invoices, invoiceUtil.buildDate(2011, 7, 10), 2, expectedAmount);

        // on 7/20/2011, invoice SubscriptionBase 3 (monthly)
        expectedAmount = TEN;
        testInvoiceGeneration(accountId, events, invoices, invoiceUtil.buildDate(2011, 7, 20), 1, expectedAmount);

        // on 7/31/2011, convert SubscriptionBase 3 to annual
        events.add(createBillingEvent(subscriptionId3, bundleId, plan3UpgradeToAnnualDate, plan3, plan3Phase2, 31));
        testInvoiceGeneration(accountId, events, invoices, plan3UpgradeToAnnualDate, 2, new BigDecimal("93.55"));

        // on 8/7/2011, invoice SubscriptionBase 4 (plan 2)
        events.add(createBillingEvent(subscriptionId4, bundleId, plan4ChangeOfPlanDate, plan4b, plan4bPhase1, 7));
        expectedAmount = TWENTY_FOUR;
        testInvoiceGeneration(accountId, events, invoices, plan4ChangeOfPlanDate, 1, expectedAmount);

        // on 8/10/2011, invoice plan 2 (discount), invoice SubscriptionBase 5
        expectedAmount = THIRTY.add(TWENTY);
        testInvoiceGeneration(accountId, events, invoices, invoiceUtil.buildDate(2011, 8, 10), 2, expectedAmount);

        // on 9/7/2011, invoice SubscriptionBase 4 (plan 2)
        expectedAmount = TWENTY_FOUR;
        testInvoiceGeneration(accountId, events, invoices, invoiceUtil.buildDate(2011, 9, 7), 1, expectedAmount);

        // on 9/10/2011, invoice plan 2 (evergreen), invoice SubscriptionBase 5
        events.add(createBillingEvent(subscriptionId2, bundleId, plan2PhaseChangeToEvergreenDate, plan2, plan2Phase3, 10));
        expectedAmount = FORTY.add(TWENTY);
        testInvoiceGeneration(accountId, events, invoices, plan2PhaseChangeToEvergreenDate, 2, expectedAmount);

        // on 10/7/2011, invoice SubscriptionBase 4 (plan 2), cancel SubscriptionBase 5
        events.add(createBillingEvent(subscriptionId5, bundleId, plan5CancelDate, plan5, plan5Phase2, 10));
        testInvoiceGeneration(accountId, events, invoices, plan5CancelDate, 2, new BigDecimal("22.00"));

        // on 10/10/2011, invoice plan 2 (evergreen)
        expectedAmount = FORTY;
        testInvoiceGeneration(accountId, events, invoices, invoiceUtil.buildDate(2011, 10, 10), 1, expectedAmount);
    }

    @Test(groups = "fast")
    public void testZeroDollarEvents() throws InvoiceApiException, CatalogApiException {
        final Plan plan = new MockPlan();
        final PlanPhase planPhase = createMockMonthlyPlanPhase(ZERO);
        final BillingEventSet events = new MockBillingEventSet();
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 1, 1);
        events.add(createBillingEvent(UUID.randomUUID(), UUID.randomUUID(), targetDate, plan, planPhase, 1));

        final InvoiceWithMetadata invoiceWithMetadata = generator.generateInvoice(account, events, null, null, targetDate, Currency.USD, internalCallContext);
        final Invoice invoice = invoiceWithMetadata.getInvoice();
        assertNotNull(invoice);
        assertEquals(invoice.getInvoiceItems().size(), 1);
        assertEquals(invoice.getInvoiceItems().get(0).getAmount().compareTo(BigDecimal.ZERO), 0);
    }

    @Test(groups = "fast")
    public void testEndDateIsCorrect() throws InvoiceApiException, CatalogApiException {
        final Plan plan = new MockPlan();
        final PlanPhase planPhase = createMockMonthlyPlanPhase(ONE);
        final BillingEventSet events = new MockBillingEventSet();
        final LocalDate startDate = clock.getUTCToday().minusDays(1);
        final LocalDate targetDate = startDate.plusDays(1);

        events.add(createBillingEvent(UUID.randomUUID(), UUID.randomUUID(), startDate, plan, planPhase, startDate.getDayOfMonth()));

        final InvoiceWithMetadata invoiceWithMetadata = generator.generateInvoice(account, events, null, null, targetDate, Currency.USD, internalCallContext);
        final Invoice invoice = invoiceWithMetadata.getInvoice();
        final RecurringInvoiceItem item = (RecurringInvoiceItem) invoice.getInvoiceItems().get(0);


        // end date of the invoice item should be equal to exactly one month later (rounded)
        assertEquals(item.getEndDate(), startDate.plusMonths(1));
    }

    @Test(groups = "fast")
    public void testFixedPriceLifeCycle() throws InvoiceApiException {
        final SubscriptionBase subscription = createSubscription();

        final Plan plan = new MockPlan("plan 1");
        final MockInternationalPrice zeroPrice = new MockInternationalPrice(new DefaultPrice(ZERO, Currency.USD));
        final MockInternationalPrice cheapPrice = new MockInternationalPrice(new DefaultPrice(ONE, Currency.USD));

        final PlanPhase phase1 = new MockPlanPhase(null, zeroPrice, BillingPeriod.NO_BILLING_PERIOD, PhaseType.TRIAL);
        final PlanPhase phase2 = new MockPlanPhase(cheapPrice, null, BillingPeriod.MONTHLY, PhaseType.DISCOUNT);

        final DateTime changeDate = new DateTime("2012-04-1");

        final BillingEventSet events = new MockBillingEventSet();

        final BillingEvent event1 = invoiceUtil.createMockBillingEvent(null, subscription, new DateTime("2012-01-1"),
                                                                       plan, phase1,
                                                                       ZERO, null, Currency.USD, BillingPeriod.NO_BILLING_PERIOD, 1,
                                                                       BillingMode.IN_ADVANCE, "Test Event 1", 1L,
                                                                       SubscriptionBaseTransitionType.CREATE);

        final BillingEvent event2 = invoiceUtil.createMockBillingEvent(null, subscription, changeDate,
                                                                       plan, phase2,
                                                                       ZERO, null, Currency.USD, BillingPeriod.NO_BILLING_PERIOD, 1,
                                                                       BillingMode.IN_ADVANCE, "Test Event 2", 2L,
                                                                       SubscriptionBaseTransitionType.PHASE);

        events.add(event2);
        events.add(event1);
        final InvoiceWithMetadata invoiceWithMetadata1 = generator.generateInvoice(account, events, null, null, new LocalDate("2012-02-01"), Currency.USD, internalCallContext);
        final Invoice invoice1 = invoiceWithMetadata1.getInvoice();
        assertNotNull(invoice1);
        assertEquals(invoice1.getNumberOfItems(), 1);

        final List<Invoice> invoiceList = new ArrayList<Invoice>();
        invoiceList.add(invoice1);
        final InvoiceWithMetadata invoiceWithMetadata2 = generator.generateInvoice(account, events, invoiceList, null, new LocalDate("2012-04-05"), Currency.USD, internalCallContext);
        final Invoice invoice2 = invoiceWithMetadata2.getInvoice();
        assertNotNull(invoice2);
        assertEquals(invoice2.getNumberOfItems(), 1);
        final FixedPriceInvoiceItem item = (FixedPriceInvoiceItem) invoice2.getInvoiceItems().get(0);
        assertEquals(item.getStartDate(), changeDate.toLocalDate());
    }

    @Test(groups = "fast")
    public void testMixedModeLifeCycle() throws InvoiceApiException, CatalogApiException {
        // create a SubscriptionBase with a fixed price and recurring price
        final Plan plan1 = new MockPlan();
        final BigDecimal monthlyRate = FIVE;
        final BigDecimal fixedCost = TEN;
        final PlanPhase phase1 = createMockMonthlyPlanPhase(monthlyRate, fixedCost, PhaseType.TRIAL);

        final BillingEventSet events = new MockBillingEventSet();
        final UUID subscriptionId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();

        final LocalDate startDate = new LocalDate(2011, 1, 1);
        final BillingEvent event1 = createBillingEvent(subscriptionId, bundleId, startDate, plan1, phase1, 1);
        events.add(event1);

        // ensure both components are invoiced
        final InvoiceWithMetadata invoiceWithMetadata1 = generator.generateInvoice(account, events, null, null, startDate, Currency.USD, internalCallContext);
        final Invoice invoice1 = invoiceWithMetadata1.getInvoice();
        assertNotNull(invoice1);
        assertEquals(invoice1.getNumberOfItems(), 2);
        assertEquals(invoice1.getBalance(), KillBillMoney.of(FIFTEEN, invoice1.getCurrency()));

        final List<Invoice> invoiceList = new ArrayList<Invoice>();
        invoiceList.add(invoice1);

        // move forward in time one billing period
        final LocalDate currentDate = startDate.plusMonths(1);

        // ensure that only the recurring price is invoiced
        final InvoiceWithMetadata invoiceWithMetadata2 = generator.generateInvoice(account, events, invoiceList, null, currentDate, Currency.USD, internalCallContext);
        final Invoice invoice2 = invoiceWithMetadata2.getInvoice();
        assertNotNull(invoice2);
        assertEquals(invoice2.getNumberOfItems(), 1);
        assertEquals(invoice2.getBalance(), KillBillMoney.of(FIVE, invoice2.getCurrency()));
    }

    @Test(groups = "fast")
    public void testFixedModePlanChange() throws InvoiceApiException, CatalogApiException {
        // create a SubscriptionBase with a fixed price and recurring price
        final Plan plan1 = new MockPlan();
        final BigDecimal fixedCost1 = TEN;
        final BigDecimal fixedCost2 = TWENTY;
        final PlanPhase phase1 = createMockMonthlyPlanPhase(null, fixedCost1, PhaseType.TRIAL);
        final PlanPhase phase2 = createMockMonthlyPlanPhase(null, fixedCost2, PhaseType.EVERGREEN);

        final BillingEventSet events = new MockBillingEventSet();
        final UUID subscriptionId = UUID.randomUUID();
        final UUID accountId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();

        final LocalDate startDate = new LocalDate(2011, 1, 1);
        final BillingEvent event1 = createBillingEvent(subscriptionId, bundleId, startDate, plan1, phase1, 1);
        events.add(event1);

        // ensure that a single invoice item is generated for the fixed cost
        final InvoiceWithMetadata invoiceWithMetadata1 = generator.generateInvoice(account, events, null, null, startDate, Currency.USD, internalCallContext);
        final Invoice invoice1 = invoiceWithMetadata1.getInvoice();assertNotNull(invoice1);
        assertEquals(invoice1.getNumberOfItems(), 1);
        assertEquals(invoice1.getBalance(), KillBillMoney.of(fixedCost1, invoice1.getCurrency()));

        final List<Invoice> invoiceList = new ArrayList<Invoice>();
        invoiceList.add(invoice1);

        // move forward in time one billing period
        final LocalDate phaseChangeDate = startDate.plusMonths(1);
        final BillingEvent event2 = createBillingEvent(subscriptionId, bundleId, phaseChangeDate, plan1, phase2, 1);
        events.add(event2);

        // ensure that a single invoice item is generated for the fixed cost
        final InvoiceWithMetadata invoiceWithMetadata2 = generator.generateInvoice(account, events, invoiceList, null, phaseChangeDate, Currency.USD, internalCallContext);
        final Invoice invoice2 = invoiceWithMetadata2.getInvoice();
        assertEquals(invoice2.getNumberOfItems(), 1);
        assertEquals(invoice2.getBalance(), KillBillMoney.of(fixedCost2, invoice2.getCurrency()));
    }

    @Test(groups = "fast")
    public void testInvoiceGenerationFailureScenario() throws InvoiceApiException, CatalogApiException {
        final BillingEventSet events = new MockBillingEventSet();
        final UUID subscriptionId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final int BILL_CYCLE_DAY = 15;

        // create SubscriptionBase with a zero-dollar trial, a monthly discount period and a monthly evergreen
        final Plan plan1 = new MockPlan();
        final PlanPhase phase1 = createMockMonthlyPlanPhase(null, ZERO, PhaseType.TRIAL);
        final BigDecimal DISCOUNT_PRICE = new BigDecimal("9.95");
        final PlanPhase phase2 = createMockMonthlyPlanPhase(DISCOUNT_PRICE, null, PhaseType.DISCOUNT);
        final PlanPhase phase3 = createMockMonthlyPlanPhase(new BigDecimal("19.95"), null, PhaseType.EVERGREEN);

        // set up billing events
        final LocalDate creationDate = new LocalDate(2012, 3, 6);
        events.add(createBillingEvent(subscriptionId, bundleId, creationDate, plan1, phase1, BILL_CYCLE_DAY));

        // trialPhaseEndDate = 2012/4/5
        final LocalDate trialPhaseEndDate = creationDate.plusDays(30);
        events.add(createBillingEvent(subscriptionId, bundleId, trialPhaseEndDate, plan1, phase2, BILL_CYCLE_DAY));

        // discountPhaseEndDate = 2012/10/5
        final LocalDate discountPhaseEndDate = trialPhaseEndDate.plusMonths(6);
        events.add(createBillingEvent(subscriptionId, bundleId, discountPhaseEndDate, plan1, phase3, BILL_CYCLE_DAY));

        final InvoiceWithMetadata invoiceWithMetadata1 = generator.generateInvoice(account, events, null, null, creationDate, Currency.USD, internalCallContext);
        final Invoice invoice1 = invoiceWithMetadata1.getInvoice();
        assertNotNull(invoice1);
        assertEquals(invoice1.getNumberOfItems(), 1);
        assertEquals(invoice1.getBalance().compareTo(ZERO), 0);

        final List<Invoice> invoiceList = new ArrayList<Invoice>();
        invoiceList.add(invoice1);

        final InvoiceWithMetadata invoiceWithMetadata2 = generator.generateInvoice(account, events, invoiceList, null, trialPhaseEndDate, Currency.USD, internalCallContext);
        final Invoice invoice2 = invoiceWithMetadata2.getInvoice();
        assertNotNull(invoice2);
        assertEquals(invoice2.getNumberOfItems(), 1);
        assertEquals(invoice2.getInvoiceItems().get(0).getStartDate(), trialPhaseEndDate);
        assertEquals(invoice2.getBalance().compareTo(new BigDecimal("3.21")), 0);

        invoiceList.add(invoice2);
        LocalDate targetDate = new LocalDate(trialPhaseEndDate.getYear(), trialPhaseEndDate.getMonthOfYear(), BILL_CYCLE_DAY);
        final InvoiceWithMetadata invoiceWithMetadata3 = generator.generateInvoice(account, events, invoiceList, null, targetDate, Currency.USD, internalCallContext);
        final Invoice invoice3 = invoiceWithMetadata3.getInvoice();
        assertNotNull(invoice3);
        assertEquals(invoice3.getNumberOfItems(), 1);
        assertEquals(invoice3.getInvoiceItems().get(0).getStartDate(), targetDate);
        assertEquals(invoice3.getBalance().compareTo(DISCOUNT_PRICE), 0);

        invoiceList.add(invoice3);
        targetDate = targetDate.plusMonths(6);
        final InvoiceWithMetadata invoiceWithMetadata4 = generator.generateInvoice(account, events, invoiceList, null, targetDate, Currency.USD, internalCallContext);
        final Invoice invoice4 = invoiceWithMetadata4.getInvoice();
        assertNotNull(invoice4);
        assertEquals(invoice4.getNumberOfItems(), 7);
    }

    @Test(groups = "fast", expectedExceptions = {InvoiceApiException.class})
    public void testTargetDateRestrictionFailure() throws InvoiceApiException, CatalogApiException {
        final LocalDate targetDate = clock.getUTCToday().plusMonths(60);
        final BillingEventSet events = new MockBillingEventSet();
        final Plan plan1 = new MockPlan();
        final PlanPhase phase1 = createMockMonthlyPlanPhase(null, ZERO, PhaseType.TRIAL);
        events.add(createBillingEvent(UUID.randomUUID(), UUID.randomUUID(), clock.getUTCToday(), plan1, phase1, 1));
        generator.generateInvoice(account, events, null, null, targetDate, Currency.USD, internalCallContext);
    }

    @Test(groups = "fast")
    public void testWithFullRepairInvoiceGeneration() throws CatalogApiException, InvoiceApiException {
        final LocalDate april25 = new LocalDate(2012, 4, 25);

        // create a base plan on April 25th
        final SubscriptionBase baseSubscription = createSubscription();

        final Plan basePlan = new MockPlan("base Plan");
        final MockInternationalPrice price5 = new MockInternationalPrice(new DefaultPrice(FIVE, Currency.USD));
        final MockInternationalPrice price10 = new MockInternationalPrice(new DefaultPrice(TEN, Currency.USD));
        final MockInternationalPrice price20 = new MockInternationalPrice(new DefaultPrice(TWENTY, Currency.USD));
        final PlanPhase basePlanEvergreen = new MockPlanPhase(price10, null, BillingPeriod.MONTHLY, PhaseType.EVERGREEN);

        final BillingEventSet events = new MockBillingEventSet();
        events.add(createBillingEvent(baseSubscription.getId(), baseSubscription.getBundleId(), april25, basePlan, basePlanEvergreen, 25));

        // generate invoice
        final InvoiceWithMetadata invoiceWithMetadata1 = generator.generateInvoice(account, events, null, null, april25, Currency.USD, internalCallContext);
        final Invoice invoice1 = invoiceWithMetadata1.getInvoice();
        assertNotNull(invoice1);
        assertEquals(invoice1.getNumberOfItems(), 1);
        assertEquals(invoice1.getBalance().compareTo(TEN), 0);

        final List<Invoice> invoices = new ArrayList<Invoice>();
        invoices.add(invoice1);

        // create 2 add ons on April 28th
        final LocalDate april28 = new LocalDate(2012, 4, 28);
        final SubscriptionBase addOnSubscription1 = createSubscription();
        final Plan addOn1Plan = new MockPlan("add on 1");
        final PlanPhase addOn1PlanPhaseEvergreen = new MockPlanPhase(price5, null, BillingPeriod.MONTHLY, PhaseType.EVERGREEN);
        events.add(createBillingEvent(addOnSubscription1.getId(), baseSubscription.getBundleId(), april28, addOn1Plan, addOn1PlanPhaseEvergreen, 25));

        final SubscriptionBase addOnSubscription2 = createSubscription();
        final Plan addOn2Plan = new MockPlan("add on 2");
        final PlanPhase addOn2PlanPhaseEvergreen = new MockPlanPhase(price20, null, BillingPeriod.MONTHLY, PhaseType.EVERGREEN);
        events.add(createBillingEvent(addOnSubscription2.getId(), baseSubscription.getBundleId(), april28, addOn2Plan, addOn2PlanPhaseEvergreen, 25));

        // generate invoice
        final InvoiceWithMetadata invoiceWithMetadata2 = generator.generateInvoice(account, events, invoices, null, april28, Currency.USD, internalCallContext);
        final Invoice invoice2 = invoiceWithMetadata2.getInvoice();

        invoices.add(invoice2);
        assertNotNull(invoice2);
        assertEquals(invoice2.getNumberOfItems(), 2);
        assertEquals(invoice2.getBalance().compareTo(KillBillMoney.of(TWENTY_FIVE.multiply(new BigDecimal("0.9")), invoice2.getCurrency())), 0);

        // perform a repair (change base plan; remove one add-on)
        // event stream should include just two plans
        final MockBillingEventSet newEvents = new MockBillingEventSet();
        final Plan basePlan2 = new MockPlan("base plan 2");
        final MockInternationalPrice price13 = new MockInternationalPrice(new DefaultPrice(THIRTEEN, Currency.USD));
        final PlanPhase basePlan2Phase = new MockPlanPhase(price13, null, BillingPeriod.MONTHLY, PhaseType.EVERGREEN);
        newEvents.add(createBillingEvent(baseSubscription.getId(), baseSubscription.getBundleId(), april25, basePlan2, basePlan2Phase, 25));
        newEvents.add(createBillingEvent(addOnSubscription1.getId(), baseSubscription.getBundleId(), april28, addOn1Plan, addOn1PlanPhaseEvergreen, 25));

        // generate invoice
        final LocalDate may1 = new LocalDate(2012, 5, 1);
        final InvoiceWithMetadata invoiceWithMetadata3 = generator.generateInvoice(account, newEvents, invoices, null, may1, Currency.USD, internalCallContext);
        final Invoice invoice3 = invoiceWithMetadata3.getInvoice();
        assertNotNull(invoice3);
        assertEquals(invoice3.getNumberOfItems(), 3);
        // -4.50 -18 - 10 (to correct the previous 2 invoices) + 4.50 + 13
        assertEquals(invoice3.getBalance().compareTo(FIFTEEN.negate()), 0);
    }

    @Test(groups = "fast")
    public void testRepairForPaidInvoice() throws CatalogApiException, InvoiceApiException {
        // create an invoice
        final LocalDate april25 = new LocalDate(2012, 4, 25);

        // create a base plan on April 25th
        final SubscriptionBase originalSubscription = createSubscription();

        final Plan originalPlan = new MockPlan("original plan");
        final MockInternationalPrice price10 = new MockInternationalPrice(new DefaultPrice(TEN, Currency.USD));
        final PlanPhase originalPlanEvergreen = new MockPlanPhase(price10, null, BillingPeriod.MONTHLY, PhaseType.EVERGREEN);

        final BillingEventSet events = new MockBillingEventSet();
        events.add(createBillingEvent(originalSubscription.getId(), originalSubscription.getBundleId(), april25, originalPlan, originalPlanEvergreen, 25));

        final InvoiceWithMetadata invoiceWithMetadata1 = generator.generateInvoice(account, events, null, null, april25, Currency.USD, internalCallContext);
        final Invoice invoice1 = invoiceWithMetadata1.getInvoice();

        printDetailInvoice(invoice1);

        assertEquals(invoice1.getNumberOfItems(), 1);
        final List<Invoice> invoices = new ArrayList<Invoice>();
        invoices.add(invoice1);

        // pay the invoice
        invoice1.addPayment(new DefaultInvoicePayment(InvoicePaymentType.ATTEMPT, UUID.randomUUID(), invoice1.getId(), april25.toDateTimeAtCurrentTime(), TEN,
                                                      Currency.USD, Currency.USD, null, true));
        assertEquals(invoice1.getBalance().compareTo(ZERO), 0);

        // change the plan (i.e. repair) on start date
        events.clear();
        final SubscriptionBase newSubscription = createSubscription();
        final Plan newPlan = new MockPlan("new plan");
        final MockInternationalPrice price5 = new MockInternationalPrice(new DefaultPrice(FIVE, Currency.USD));
        final PlanPhase newPlanEvergreen = new MockPlanPhase(price5, null, BillingPeriod.MONTHLY, PhaseType.EVERGREEN);
        events.add(createBillingEvent(newSubscription.getId(), originalSubscription.getBundleId(), april25, newPlan, newPlanEvergreen, 25));

        // generate a new invoice
        final InvoiceWithMetadata invoiceWithMetadata2 = generator.generateInvoice(account, events, invoices, null, april25, Currency.USD, internalCallContext);
        final Invoice invoice2 = invoiceWithMetadata2.getInvoice();

        printDetailInvoice(invoice2);
        assertEquals(invoice2.getNumberOfItems(), 2);
        invoices.add(invoice2);

        // move items to the correct invoice (normally, the dao calls will sort that out)
        distributeItems(invoices);

        // ensure that the original invoice balance is zero
        assertEquals(invoice1.getBalance().compareTo(BigDecimal.ZERO), 0);

        // ensure that the account balance is correct
        assertEquals(invoice2.getBalance().compareTo(new BigDecimal("-5.0")), 0);
    }

    // Regression test for #170 (see https://github.com/killbill/killbill/pull/173)
    @Test(groups = "fast")
    public void testRegressionFor170() throws EntityPersistenceException, InvoiceApiException, CatalogApiException {
        final UUID accountId = account.getId();
        final Currency currency = Currency.USD;
        final SubscriptionBase subscription = createSubscription();
        final MockInternationalPrice recurringPrice = new MockInternationalPrice(new DefaultPrice(new BigDecimal("2.9500"), Currency.USD));
        final MockPlanPhase phase = new MockPlanPhase(recurringPrice, null);
        final Plan plan = new MockPlan(phase);

        final LocalDate targetDate = new LocalDate(2013, 10, 30);

        final Invoice existingInvoice = new DefaultInvoice(UUID.randomUUID(), accountId, null, clock.getUTCToday(), targetDate, currency, false, InvoiceStatus.COMMITTED);

        // Set the existing recurring invoice item 2013/06/15 - 2013/07/15
        final LocalDate startDate = new LocalDate(2013, 06, 15);
        final LocalDate endDate = new LocalDate(2013, 07, 15);
        final InvoiceItem recurringInvoiceItem = new RecurringInvoiceItem(existingInvoice.getId(), accountId, subscription.getBundleId(),
                                                                          subscription.getId(), null, plan.getName(), phase.getName(),
                                                                          startDate, endDate, recurringPrice.getPrice(currency),
                                                                          recurringPrice.getPrice(currency), Currency.USD);
        existingInvoice.addInvoiceItem(recurringInvoiceItem);

        // Set an existing repair item
        final LocalDate repairStartDate = new LocalDate(2013, 06, 21);
        final LocalDate repairEndDate = new LocalDate(2013, 06, 26);
        final BigDecimal repairAmount = new BigDecimal("0.4900").negate();
        final InvoiceItem repairItem = new RepairAdjInvoiceItem(existingInvoice.getId(), accountId, repairStartDate, repairEndDate,
                                                                repairAmount, currency, recurringInvoiceItem.getId());
        existingInvoice.addInvoiceItem(repairItem);

        // Create the billing event associated with the subscription creation
        //
        // Note : this is the interesting part of the test; it does not provide the blocking billing events, which force invoice
        // to un repair what was previously repaired.
        final BillingEventSet events = new MockBillingEventSet();
        final BillingEvent event = invoiceUtil.createMockBillingEvent(null, subscription, new DateTime("2013-06-15", DateTimeZone.UTC),
                                                                      plan, phase,
                                                                      null, recurringPrice.getPrice(currency), currency,
                                                                      BillingPeriod.MONTHLY, 15, BillingMode.IN_ADVANCE, "testEvent", 1L,
                                                                      SubscriptionBaseTransitionType.CREATE);
        events.add(event);

        final List<Invoice> existingInvoices = new LinkedList<Invoice>();
        existingInvoices.add(existingInvoice);

        // Generate a new invoice

        final InvoiceWithMetadata invoiceWithMetadata = generator.generateInvoice(account, events, existingInvoices, null, targetDate, currency, internalCallContext);
        final Invoice invoice = invoiceWithMetadata.getInvoice();
        assertEquals(invoice.getNumberOfItems(), 7);
        assertEquals(invoice.getInvoiceItems().get(0).getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(invoice.getInvoiceItems().get(0).getStartDate(), new LocalDate(2013, 6, 15));
        assertEquals(invoice.getInvoiceItems().get(0).getEndDate(), new LocalDate(2013, 7, 15));

        assertEquals(invoice.getInvoiceItems().get(1).getInvoiceItemType(), InvoiceItemType.REPAIR_ADJ);
        assertEquals(invoice.getInvoiceItems().get(1).getStartDate(), new LocalDate(2013, 6, 15));
        assertEquals(invoice.getInvoiceItems().get(1).getEndDate(), new LocalDate(2013, 6, 21));

        assertEquals(invoice.getInvoiceItems().get(2).getInvoiceItemType(), InvoiceItemType.REPAIR_ADJ);
        assertEquals(invoice.getInvoiceItems().get(2).getStartDate(), new LocalDate(2013, 6, 26));
        assertEquals(invoice.getInvoiceItems().get(2).getEndDate(), new LocalDate(2013, 7, 15));

        assertEquals(invoice.getInvoiceItems().get(3).getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(invoice.getInvoiceItems().get(3).getStartDate(), new LocalDate(2013, 7, 15));
        assertEquals(invoice.getInvoiceItems().get(3).getEndDate(), new LocalDate(2013, 8, 15));

        assertEquals(invoice.getInvoiceItems().get(4).getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(invoice.getInvoiceItems().get(4).getStartDate(), new LocalDate(2013, 8, 15));
        assertEquals(invoice.getInvoiceItems().get(4).getEndDate(), new LocalDate(2013, 9, 15));

        assertEquals(invoice.getInvoiceItems().get(5).getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(invoice.getInvoiceItems().get(5).getStartDate(), new LocalDate(2013, 9, 15));
        assertEquals(invoice.getInvoiceItems().get(5).getEndDate(), new LocalDate(2013, 10, 15));

        assertEquals(invoice.getInvoiceItems().get(6).getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(invoice.getInvoiceItems().get(6).getStartDate(), new LocalDate(2013, 10, 15));
        assertEquals(invoice.getInvoiceItems().get(6).getEndDate(), new LocalDate(2013, 11, 15));

        // Add newly generated invoice to existing invoices
        existingInvoices.add(invoice);

        // Generate next invoice (no-op)
        final InvoiceWithMetadata newInvoiceWithMetdata = generator.generateInvoice(account, events, existingInvoices, null, targetDate, currency, internalCallContext);
        final Invoice newInvoice = newInvoiceWithMetdata.getInvoice();
        assertNull(newInvoice);
    }

    @Test(groups = "fast")
    public void testAutoInvoiceOffAccount() throws Exception {
        final MockBillingEventSet events = new MockBillingEventSet();
        events.setAccountInvoiceOff(true);

        final SubscriptionBase sub = createSubscription();
        final LocalDate startDate = invoiceUtil.buildDate(2011, 9, 1);

        final Plan plan = new MockPlan();
        final BigDecimal rate1 = TEN;
        final PlanPhase phase = createMockMonthlyPlanPhase(rate1);

        final BillingEvent event = createBillingEvent(sub.getId(), sub.getBundleId(), startDate, plan, phase, 1);
        events.add(event);

        final LocalDate targetDate = invoiceUtil.buildDate(2011, 10, 3);
        final UUID accountId = UUID.randomUUID();
        final InvoiceWithMetadata invoiceWithMetadata = generator.generateInvoice(account, events, null, null, targetDate, Currency.USD, internalCallContext);

        assertNull(invoiceWithMetadata.getInvoice());
    }

    @Test(groups = "fast")
    public void testAutoInvoiceOffWithCredits() throws CatalogApiException, InvoiceApiException {
        final Currency currency = Currency.USD;
        final List<Invoice> invoices = new ArrayList<Invoice>();
        final MockBillingEventSet eventSet = new MockBillingEventSet();
        final UUID accountId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();

        final LocalDate startDate = new LocalDate(2012, 1, 1);

        // add first SubscriptionBase creation event
        final UUID subscriptionId1 = UUID.randomUUID();
        final Plan plan1 = new MockPlan();
        final PlanPhase plan1phase1 = createMockMonthlyPlanPhase(FIFTEEN, null, PhaseType.DISCOUNT);
        final BillingEvent subscription1creation = createBillingEvent(subscriptionId1, bundleId, startDate, plan1, plan1phase1, 1);
        eventSet.add(subscription1creation);

        // add second SubscriptionBase creation event
        final UUID subscriptionId2 = UUID.randomUUID();
        final Plan plan2 = new MockPlan();
        final PlanPhase plan2phase1 = createMockMonthlyPlanPhase(TWELVE, null, PhaseType.EVERGREEN);
        eventSet.add(createBillingEvent(subscriptionId2, bundleId, startDate, plan2, plan2phase1, 1));

        // generate the first invoice
        final InvoiceWithMetadata invoiceWithMetadata1 = generator.generateInvoice(account, eventSet, invoices, null, startDate, currency, internalCallContext);
        final Invoice invoice1 = invoiceWithMetadata1.getInvoice();
        assertNotNull(invoice1);
        assertTrue(invoice1.getBalance().compareTo(FIFTEEN.add(TWELVE)) == 0);
        invoices.add(invoice1);

        // set auto invoice off for first SubscriptionBase (i.e. remove event from BillingEventSet and add SubscriptionBase id to the list
        // generate invoice
        eventSet.remove(subscription1creation);
        eventSet.addSubscriptionWithAutoInvoiceOff(subscriptionId1);

        final LocalDate targetDate2 = startDate.plusMonths(1);
        final InvoiceWithMetadata invoiceWithMetadata2 = generator.generateInvoice(account, eventSet, invoices, null, targetDate2, currency, internalCallContext);
        final Invoice invoice2 = invoiceWithMetadata2.getInvoice();
        assertNotNull(invoice2);
        assertTrue(invoice2.getBalance().compareTo(TWELVE) == 0);
        invoices.add(invoice2);

        final LocalDate targetDate3 = targetDate2.plusMonths(1);
        eventSet.clearSubscriptionsWithAutoInvoiceOff();
        eventSet.add(subscription1creation);
        final InvoiceWithMetadata invoiceWithMetadata3 = generator.generateInvoice(account, eventSet, invoices, null, targetDate3, currency, internalCallContext);
        final Invoice invoice3 = invoiceWithMetadata3.getInvoice();
        assertNotNull(invoice3);
        assertTrue(invoice3.getBalance().compareTo(FIFTEEN.multiply(TWO).add(TWELVE)) == 0);
    }

    @Test(groups = "fast")
    public void testAutoInvoiceDraftAccount() throws Exception {
        final MockBillingEventSet events = new MockBillingEventSet();
        events.setAccountAutoInvoiceDraft(true);

        final SubscriptionBase sub = createSubscription();
        final LocalDate startDate = invoiceUtil.buildDate(2011, 9, 1);

        final Plan plan = new MockPlan();
        final BigDecimal rate1 = TEN;
        final PlanPhase phase = createMockMonthlyPlanPhase(rate1);

        final BillingEvent event = createBillingEvent(sub.getId(), sub.getBundleId(), startDate, plan, phase, 1);
        events.add(event);

        final LocalDate targetDate = invoiceUtil.buildDate(2011, 10, 3);
        final UUID accountId = UUID.randomUUID();
        final InvoiceWithMetadata invoiceWithMetadata = generator.generateInvoice(account, events, null, null, targetDate, Currency.USD, internalCallContext);

        assertNotNull(invoiceWithMetadata.getInvoice());
        assertEquals(invoiceWithMetadata.getInvoice().getStatus(), InvoiceStatus.DRAFT);
        assertEquals(invoiceWithMetadata.getInvoice().getInvoiceItems().size(), 2);

    }



    @Test(groups = "fast", description = "https://github.com/killbill/killbill/issues/654")
    public void testCancelEOTWithFullItemAdjustment() throws CatalogApiException, InvoiceApiException {
        final BigDecimal rate = new BigDecimal("39.95");

        final BillingEventSet events = new MockBillingEventSet();

        final SubscriptionBase sub = createSubscription();
        final LocalDate startDate = invoiceUtil.buildDate(2016, 10, 9);
        final LocalDate endDate = invoiceUtil.buildDate(2016, 11, 9);

        final Plan plan = new MockPlan();
        final PlanPhase phase = createMockMonthlyPlanPhase(rate);

        final BillingEvent event = createBillingEvent(sub.getId(), sub.getBundleId(), startDate, plan, phase, 9);
        events.add(event);

        final LocalDate targetDate = invoiceUtil.buildDate(2016, 10, 9);
        final InvoiceWithMetadata invoiceWithMetadata = generator.generateInvoice(account, events, null, null, targetDate, Currency.USD, internalCallContext);
        final Invoice invoice = invoiceWithMetadata.getInvoice();

        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 1);
        assertEquals(invoice.getBalance(), KillBillMoney.of(rate, invoice.getCurrency()));
        assertEquals(invoice.getInvoiceItems().get(0).getSubscriptionId(), sub.getId());

        assertEquals(invoice.getInvoiceItems().get(0).getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(invoice.getInvoiceItems().get(0).getStartDate(), startDate);
        assertEquals(invoice.getInvoiceItems().get(0).getEndDate(), endDate);

        // Cancel EOT and Add the item adjustment
        final BillingEvent event2 = invoiceUtil.createMockBillingEvent(account, sub, endDate.toDateTimeAtStartOfDay(),
                                                                       plan, phase,
                                                                       ZERO, null, Currency.USD, BillingPeriod.NO_BILLING_PERIOD, 9,
                                                                       BillingMode.IN_ADVANCE, "Cancel", 2L,
                                                                       SubscriptionBaseTransitionType.CANCEL);
        events.add(event2);

        final InvoiceItem itemAdj = new ItemAdjInvoiceItem(invoice.getInvoiceItems().get(0), new LocalDate(2016, 10, 12), rate.negate(), Currency.USD);

        invoice.addInvoiceItem(itemAdj);

        final List<Invoice> existingInvoices = new ArrayList<Invoice>();
        existingInvoices.add(invoice);
        final InvoiceWithMetadata invoiceWithMetadata2 = generator.generateInvoice(account, events, existingInvoices, null, targetDate, Currency.USD, internalCallContext);
        final Invoice invoice2 = invoiceWithMetadata2.getInvoice();

        assertNull(invoice2);
    }

    // Complex but plausible scenario, with multiple same-day changes, to verify bounds are not triggered
    @Test(groups = "fast", description = "https://github.com/killbill/killbill/issues/664")
    public void testMultipleDailyChangesDoNotTriggerBounds() throws InvoiceApiException, CatalogApiException {
        final UUID accountId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final UUID subscriptionId1 = UUID.randomUUID();

        final BillingEventSet events = new MockBillingEventSet();
        final List<Invoice> invoices = new ArrayList<Invoice>();
        Invoice invoice;

        final Plan plan1 = new MockPlan("plan1");
        final PlanPhase plan1Phase1 = createMockMonthlyPlanPhase(null, EIGHT, PhaseType.TRIAL);
        final PlanPhase plan1Phase2 = createMockMonthlyPlanPhase(TWELVE, PhaseType.DISCOUNT);
        final LocalDate plan1StartDate = invoiceUtil.buildDate(2011, 1, 5);
        final LocalDate plan1PhaseChangeDate = invoiceUtil.buildDate(2011, 4, 5);

        final Plan plan2 = new MockPlan("plan2");
        final PlanPhase plan2Phase1 = createMockMonthlyPlanPhase(null, TWENTY, PhaseType.TRIAL);
        final PlanPhase plan2Phase2 = createMockMonthlyPlanPhase(THIRTY, PhaseType.DISCOUNT);
        final PlanPhase plan2Phase3 = createMockMonthlyPlanPhase(FORTY, PhaseType.EVERGREEN);
        final PlanPhase plan2Phase4 = createMockMonthlyPlanPhase();
        final LocalDate plan2PhaseChangeToEvergreenDate = invoiceUtil.buildDate(2011, 6, 5);
        final LocalDate plan2CancelDate = invoiceUtil.buildDate(2011, 6, 5);

        // On 1/5/2011, start TRIAL on plan1
        events.add(createBillingEvent(subscriptionId1, bundleId, plan1StartDate, plan1, plan1Phase1, 5));

        testInvoiceGeneration(accountId, events, invoices, plan1StartDate, 1, EIGHT);
        invoice = invoices.get(0);
        assertEquals(invoice.getInvoiceItems().size(), 1);
        assertEquals(invoice.getInvoiceItems().get(0).getSubscriptionId(), subscriptionId1);
        assertEquals(invoice.getInvoiceItems().get(0).getInvoiceItemType(), InvoiceItemType.FIXED);
        assertEquals(invoice.getInvoiceItems().get(0).getStartDate(), new LocalDate(2011, 1, 5));
        assertNull(invoice.getInvoiceItems().get(0).getEndDate());
        assertEquals(invoice.getInvoiceItems().get(0).getAmount().compareTo(EIGHT), 0);

        // On 1/5/2011, change to TRIAL on plan2
        events.add(createBillingEvent(subscriptionId1, bundleId, plan1StartDate, plan2, plan2Phase1, 5));

        testInvoiceGeneration(accountId, events, invoices, plan1StartDate, 1, TWENTY);
        assertEquals(invoices.get(0), invoice);
        invoice = invoices.get(1);
        assertEquals(invoice.getInvoiceItems().size(), 1);
        assertEquals(invoice.getInvoiceItems().get(0).getSubscriptionId(), subscriptionId1);
        assertEquals(invoice.getInvoiceItems().get(0).getInvoiceItemType(), InvoiceItemType.FIXED);
        assertEquals(invoice.getInvoiceItems().get(0).getStartDate(), new LocalDate(2011, 1, 5));
        assertNull(invoice.getInvoiceItems().get(0).getEndDate());
        assertEquals(invoice.getInvoiceItems().get(0).getAmount().compareTo(TWENTY), 0);

        // On 1/5/2011, change back to TRIAL on plan1
        events.add(createBillingEvent(subscriptionId1, bundleId, plan1StartDate, plan1, plan1Phase1, 5));

        // We don't repair FIXED items and one already exists for that date - nothing to generate
        testNullInvoiceGeneration(events, invoices, plan1StartDate);

        // On 4/5/2011, phase change to DISCOUNT on plan1
        events.add(createBillingEvent(subscriptionId1, bundleId, plan1PhaseChangeDate, plan1, plan1Phase2, 5));

        testInvoiceGeneration(accountId, events, invoices, plan1PhaseChangeDate, 1, TWELVE);
        assertEquals(invoices.get(1), invoice);
        invoice = invoices.get(2);
        assertEquals(invoice.getInvoiceItems().size(), 1);
        assertEquals(invoice.getInvoiceItems().get(0).getSubscriptionId(), subscriptionId1);
        assertEquals(invoice.getInvoiceItems().get(0).getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(invoice.getInvoiceItems().get(0).getStartDate(), new LocalDate(2011, 4, 5));
        assertEquals(invoice.getInvoiceItems().get(0).getEndDate(), new LocalDate(2011, 5, 5));
        assertEquals(invoice.getInvoiceItems().get(0).getAmount().compareTo(TWELVE), 0);

        // On 4/5/2011, change to DISCOUNT on plan2
        events.add(createBillingEvent(subscriptionId1, bundleId, plan1PhaseChangeDate, plan2, plan2Phase2, 5));

        testInvoiceGeneration(accountId, events, invoices, plan1PhaseChangeDate, 2, new BigDecimal("18"));
        assertEquals(invoices.get(2), invoice);
        invoice = invoices.get(3);
        assertEquals(invoice.getInvoiceItems().get(0).getSubscriptionId(), subscriptionId1);
        assertEquals(invoice.getInvoiceItems().get(0).getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(invoice.getInvoiceItems().get(0).getStartDate(), new LocalDate(2011, 4, 5));
        assertEquals(invoice.getInvoiceItems().get(0).getEndDate(), new LocalDate(2011, 5, 5));
        assertEquals(invoice.getInvoiceItems().get(0).getAmount().compareTo(THIRTY), 0);
        assertEquals(invoice.getInvoiceItems().get(1).getLinkedItemId(), invoices.get(2).getInvoiceItems().get(0).getId());
        assertEquals(invoice.getInvoiceItems().get(1).getInvoiceItemType(), InvoiceItemType.REPAIR_ADJ);
        assertEquals(invoice.getInvoiceItems().get(1).getStartDate(), new LocalDate(2011, 4, 5));
        assertEquals(invoice.getInvoiceItems().get(1).getEndDate(), new LocalDate(2011, 5, 5));
        assertEquals(invoice.getInvoiceItems().get(1).getAmount().compareTo(TWELVE.negate()), 0);

        // On 4/5/2011, change back to DISCOUNT on plan1
        events.add(createBillingEvent(subscriptionId1, bundleId, plan1PhaseChangeDate, plan1, plan1Phase2, 5));

        testInvoiceGeneration(accountId, events, invoices, plan1PhaseChangeDate, 2, new BigDecimal("-18"));
        assertEquals(invoices.get(3), invoice);
        invoice = invoices.get(4);
        assertEquals(invoice.getInvoiceItems().get(0).getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(invoice.getInvoiceItems().get(0).getStartDate(), new LocalDate(2011, 4, 5));
        assertEquals(invoice.getInvoiceItems().get(0).getEndDate(), new LocalDate(2011, 5, 5));
        assertEquals(invoice.getInvoiceItems().get(0).getAmount().compareTo(TWELVE), 0);
        assertEquals(invoice.getInvoiceItems().get(1).getLinkedItemId(), invoices.get(3).getInvoiceItems().get(0).getId());
        assertEquals(invoice.getInvoiceItems().get(1).getInvoiceItemType(), InvoiceItemType.REPAIR_ADJ);
        assertEquals(invoice.getInvoiceItems().get(1).getStartDate(), new LocalDate(2011, 4, 5));
        assertEquals(invoice.getInvoiceItems().get(1).getEndDate(), new LocalDate(2011, 5, 5));
        assertEquals(invoice.getInvoiceItems().get(1).getAmount().compareTo(THIRTY.negate()), 0);

        // On 4/5/2011, change back to DISCOUNT on plan2
        events.add(createBillingEvent(subscriptionId1, bundleId, plan1PhaseChangeDate, plan2, plan2Phase2, 5));

        testInvoiceGeneration(accountId, events, invoices, plan1PhaseChangeDate, 2, new BigDecimal("18"));
        assertEquals(invoices.get(4), invoice);
        invoice = invoices.get(5);
        assertEquals(invoice.getInvoiceItems().get(0).getSubscriptionId(), subscriptionId1);
        assertEquals(invoice.getInvoiceItems().get(0).getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(invoice.getInvoiceItems().get(0).getStartDate(), new LocalDate(2011, 4, 5));
        assertEquals(invoice.getInvoiceItems().get(0).getEndDate(), new LocalDate(2011, 5, 5));
        assertEquals(invoice.getInvoiceItems().get(0).getAmount().compareTo(THIRTY), 0);
        assertEquals(invoice.getInvoiceItems().get(1).getLinkedItemId(), invoices.get(4).getInvoiceItems().get(0).getId());
        assertEquals(invoice.getInvoiceItems().get(1).getInvoiceItemType(), InvoiceItemType.REPAIR_ADJ);
        assertEquals(invoice.getInvoiceItems().get(1).getStartDate(), new LocalDate(2011, 4, 5));
        assertEquals(invoice.getInvoiceItems().get(1).getEndDate(), new LocalDate(2011, 5, 5));
        assertEquals(invoice.getInvoiceItems().get(1).getAmount().compareTo(TWELVE.negate()), 0);

        // On 6/5/2011, phase change to EVERGREEN on plan2
        events.add(createBillingEvent(subscriptionId1, bundleId, plan2PhaseChangeToEvergreenDate, plan2, plan2Phase3, 5));

        testInvoiceGeneration(accountId, events, invoices, plan2PhaseChangeToEvergreenDate, 2, new BigDecimal("70"));
        assertEquals(invoices.get(5), invoice);
        invoice = invoices.get(6);
        assertEquals(invoice.getInvoiceItems().get(0).getSubscriptionId(), subscriptionId1);
        assertEquals(invoice.getInvoiceItems().get(0).getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(invoice.getInvoiceItems().get(0).getStartDate(), new LocalDate(2011, 5, 5));
        assertEquals(invoice.getInvoiceItems().get(0).getEndDate(), new LocalDate(2011, 6, 5));
        assertEquals(invoice.getInvoiceItems().get(0).getAmount().compareTo(THIRTY), 0);
        assertEquals(invoice.getInvoiceItems().get(1).getSubscriptionId(), subscriptionId1);
        assertEquals(invoice.getInvoiceItems().get(1).getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(invoice.getInvoiceItems().get(1).getStartDate(), new LocalDate(2011, 6, 5));
        assertEquals(invoice.getInvoiceItems().get(1).getEndDate(), new LocalDate(2011, 7, 5));
        assertEquals(invoice.getInvoiceItems().get(1).getAmount().compareTo(FORTY), 0);

        // On 6/5/2011, cancel subscription
        events.add(createBillingEvent(subscriptionId1, bundleId, plan2CancelDate, plan2, plan2Phase4, 5));

        testInvoiceGeneration(accountId, events, invoices, plan2PhaseChangeToEvergreenDate, 1, FORTY.negate());
        assertEquals(invoices.get(6), invoice);
        invoice = invoices.get(7);
        assertEquals(invoice.getInvoiceItems().get(0).getLinkedItemId(), invoices.get(6).getInvoiceItems().get(1).getId());
        assertEquals(invoice.getInvoiceItems().get(0).getInvoiceItemType(), InvoiceItemType.REPAIR_ADJ);
        assertEquals(invoice.getInvoiceItems().get(0).getStartDate(), new LocalDate(2011, 6, 5));
        assertEquals(invoice.getInvoiceItems().get(0).getEndDate(), new LocalDate(2011, 7, 5));
        assertEquals(invoice.getInvoiceItems().get(0).getAmount().compareTo(FORTY.negate()), 0);
    }

    @Test(groups = "fast", description = "https://github.com/killbill/killbill/issues/664")
    public void testBuggyBillingEventsDoNotImpactInvoicing() throws InvoiceApiException, CatalogApiException {
        final UUID accountId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final UUID subscriptionId1 = UUID.randomUUID();

        final BillingEventSet events = new MockBillingEventSet();
        final List<Invoice> invoices = new ArrayList<Invoice>();
        Invoice invoice;

        final Plan plan1 = new MockPlan("plan1");
        final PlanPhase plan1Phase1 = createMockMonthlyPlanPhase(null, EIGHT, PhaseType.TRIAL);
        final PlanPhase plan1Phase2 = createMockMonthlyPlanPhase(TWELVE, PhaseType.EVERGREEN);
        final LocalDate plan1StartDate = invoiceUtil.buildDate(2011, 1, 5);
        final LocalDate plan1PhaseChangeDate = invoiceUtil.buildDate(2011, 2, 5);

        // To simulate a bug, duplicate the billing events
        for (int i = 0; i < 10; i++) {
            events.add(createBillingEvent(subscriptionId1, bundleId, plan1StartDate, plan1, plan1Phase1, 5));
            events.add(createBillingEvent(subscriptionId1, bundleId, plan1PhaseChangeDate, plan1, plan1Phase2, 5));
        }
        assertEquals(events.size(), 20);

        // Fix for https://github.com/killbill/killbill/issues/467 will prevent duplicate fixed items
        testInvoiceGeneration(accountId, events, invoices, plan1StartDate, 1, EIGHT);
        invoice = invoices.get(0);
        assertEquals(invoice.getInvoiceItems().size(), 1);
        assertEquals(invoice.getInvoiceItems().get(0).getSubscriptionId(), subscriptionId1);
        assertEquals(invoice.getInvoiceItems().get(0).getInvoiceItemType(), InvoiceItemType.FIXED);
        assertEquals(invoice.getInvoiceItems().get(0).getStartDate(), new LocalDate(2011, 1, 5));
        assertNull(invoice.getInvoiceItems().get(0).getEndDate());
        assertEquals(invoice.getInvoiceItems().get(0).getAmount().compareTo(EIGHT), 0);

        // Intermediate billing intervals associated with recurring items will be less than a day, so only one recurring item will be generated
        testInvoiceGeneration(accountId, events, invoices, plan1PhaseChangeDate, 1, TWELVE);
        invoice = invoices.get(1);
        assertEquals(invoice.getInvoiceItems().size(), 1);
        assertEquals(invoice.getInvoiceItems().get(0).getSubscriptionId(), subscriptionId1);
        assertEquals(invoice.getInvoiceItems().get(0).getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(invoice.getInvoiceItems().get(0).getStartDate(), new LocalDate(2011, 2, 5));
        assertEquals(invoice.getInvoiceItems().get(0).getEndDate(), new LocalDate(2011, 3, 5));
        assertEquals(invoice.getInvoiceItems().get(0).getAmount().compareTo(TWELVE), 0);
    }

    private Long totalOrdering = 1L;

    private MockPlanPhase createMockThirtyDaysPlanPhase(@Nullable final BigDecimal recurringRate) {
        return new MockPlanPhase(new MockInternationalPrice(new DefaultPrice(recurringRate, Currency.USD)),
                                 null, BillingPeriod.THIRTY_DAYS);
    }

    private MockPlanPhase createMockMonthlyPlanPhase() {
        return new MockPlanPhase(null, null, BillingPeriod.MONTHLY);
    }

    private MockPlanPhase createMockMonthlyPlanPhase(@Nullable final BigDecimal recurringRate) {
        return new MockPlanPhase(new MockInternationalPrice(new DefaultPrice(recurringRate, Currency.USD)),
                                 null, BillingPeriod.MONTHLY);
    }

    private MockPlanPhase createMockMonthlyPlanPhase(final BigDecimal recurringRate, final PhaseType phaseType) {
        return new MockPlanPhase(new MockInternationalPrice(new DefaultPrice(recurringRate, Currency.USD)),
                                 null, BillingPeriod.MONTHLY, phaseType);
    }

    private MockPlanPhase createMockMonthlyPlanPhase(@Nullable final BigDecimal recurringRate,
                                                     @Nullable final BigDecimal fixedCost,
                                                     final PhaseType phaseType) {
        final MockInternationalPrice recurringPrice = (recurringRate == null) ? null : new MockInternationalPrice(new DefaultPrice(recurringRate, Currency.USD));
        final MockInternationalPrice fixedPrice = (fixedCost == null) ? null : new MockInternationalPrice(new DefaultPrice(fixedCost, Currency.USD));

        return new MockPlanPhase(recurringPrice, fixedPrice, BillingPeriod.MONTHLY, phaseType);
    }

    private MockPlanPhase createMockAnnualPlanPhase(final BigDecimal recurringRate, final PhaseType phaseType) {
        return new MockPlanPhase(new MockInternationalPrice(new DefaultPrice(recurringRate, Currency.USD)),
                                 null, BillingPeriod.ANNUAL, phaseType);
    }

    private SubscriptionBase createSubscription() {
        return createSubscription(UUID.randomUUID(), UUID.randomUUID());
    }

    private SubscriptionBase createSubscription(final UUID subscriptionId, final UUID bundleId) {
        final SubscriptionBase sub = Mockito.mock(SubscriptionBase.class);
        Mockito.when(sub.getId()).thenReturn(subscriptionId);
        Mockito.when(sub.getBundleId()).thenReturn(bundleId);

        return sub;
    }

    private BillingEvent createBillingEvent(final UUID subscriptionId, final UUID bundleId, final LocalDate startDate,
                                            final Plan plan, final PlanPhase planPhase, final int billCycleDayLocal) throws CatalogApiException {
        final SubscriptionBase sub = createSubscription(subscriptionId, bundleId);
        final Currency currency = Currency.USD;

        return invoiceUtil.createMockBillingEvent(null, sub, startDate.toDateTimeAtStartOfDay(), plan, planPhase,
                                                  planPhase.getFixed().getPrice() == null ? null : planPhase.getFixed().getPrice().getPrice(currency),
                                                  planPhase.getRecurring().getRecurringPrice() == null ? null : planPhase.getRecurring().getRecurringPrice().getPrice(currency),
                                                  currency, planPhase.getRecurring().getBillingPeriod(),
                                                  billCycleDayLocal, BillingMode.IN_ADVANCE, "Test", totalOrdering++, SubscriptionBaseTransitionType.CREATE);
    }

    private void testInvoiceGeneration(final UUID accountId, final BillingEventSet events, final List<Invoice> existingInvoices,
                                       final LocalDate targetDate, final int expectedNumberOfItems,
                                       final BigDecimal expectedAmount) throws InvoiceApiException {
        final Currency currency = Currency.USD;
        final InvoiceWithMetadata invoiceWithMetadata = generator.generateInvoice(account, events, existingInvoices, null, targetDate, currency, internalCallContext);
        final Invoice invoice = invoiceWithMetadata.getInvoice();
        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), expectedNumberOfItems);
        existingInvoices.add(invoice);

        distributeItems(existingInvoices);
        assertEquals(invoice.getBalance(), KillBillMoney.of(expectedAmount, invoice.getCurrency()));
    }

    private void testNullInvoiceGeneration(final BillingEventSet events, final List<Invoice> existingInvoices, final LocalDate targetDate) throws InvoiceApiException {
        final Currency currency = Currency.USD;
        final InvoiceWithMetadata invoiceWithMetadata = generator.generateInvoice(account, events, existingInvoices, null, targetDate, currency, internalCallContext);
        final Invoice invoice = invoiceWithMetadata.getInvoice();
        assertNull(invoice);
    }

    private void distributeItems(final List<Invoice> invoices) {
        final Map<UUID, Invoice> invoiceMap = new HashMap<UUID, Invoice>();

        for (final Invoice invoice : invoices) {
            invoiceMap.put(invoice.getId(), invoice);
        }

        for (final Invoice invoice : invoices) {
            final Iterator<InvoiceItem> itemIterator = invoice.getInvoiceItems().iterator();
            final UUID invoiceId = invoice.getId();

            while (itemIterator.hasNext()) {
                final InvoiceItem item = itemIterator.next();

                if (!item.getInvoiceId().equals(invoiceId)) {
                    final Invoice thisInvoice = invoiceMap.get(item.getInvoiceId());
                    if (thisInvoice == null) {
                        throw new NullPointerException();
                    }
                    thisInvoice.addInvoiceItem(item);
                    itemIterator.remove();
                }
            }
        }
    }

    private void printDetailInvoice(final Invoice invoice) {
        log.info("--------------------  START DETAIL ----------------------");
        log.info("Invoice " + invoice.getId() + ": BALANCE = " + invoice.getBalance()
                 + ", CBA = " + invoice.getCreditedAmount()
                 + ", CHARGE_AMOUNT = " + invoice.getChargedAmount()
                 + ", ADJ_AMOUNT = " + invoice.getCreditedAmount());

        for (final InvoiceItem cur : invoice.getInvoiceItems()) {
            log.info(cur.toString());
        }
        log.info("--------------------  END DETAIL ----------------------");
    }
}
