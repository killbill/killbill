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

package com.ning.billing.invoice.generator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import com.ning.billing.catalog.DefaultPrice;
import com.ning.billing.catalog.MockInternationalPrice;
import com.ning.billing.catalog.MockPlan;
import com.ning.billing.catalog.MockPlanPhase;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.invoice.MockBillingEventSet;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoicePayment.InvoicePaymentType;
import com.ning.billing.invoice.model.CreditBalanceAdjInvoiceItem;
import com.ning.billing.invoice.model.DefaultInvoice;
import com.ning.billing.invoice.model.DefaultInvoicePayment;
import com.ning.billing.invoice.model.FixedPriceInvoiceItem;
import com.ning.billing.invoice.model.RecurringInvoiceItem;
import com.ning.billing.invoice.tests.InvoicingTestBase;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.clock.DefaultClock;
import com.ning.billing.util.config.InvoiceConfig;
import com.ning.billing.util.svcapi.junction.BillingEvent;
import com.ning.billing.util.svcapi.junction.BillingEventSet;
import com.ning.billing.util.svcapi.junction.BillingModeType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestDefaultInvoiceGenerator extends InvoicingTestBase {

    private static final Logger log = LoggerFactory.getLogger(TestDefaultInvoiceGenerator.class);

    private final Clock clock = new ClockMock();

    private final InvoiceGenerator generator;

    public TestDefaultInvoiceGenerator() {
        final Clock clock = new DefaultClock();
        final InvoiceConfig invoiceConfig = new InvoiceConfig() {
            @Override
            public int getNumberOfMonthsInFuture() {
                return 36;
            }

            @Override
            public boolean isEmailNotificationsEnabled() {
                return false;
            }
        };
        this.generator = new DefaultInvoiceGenerator(clock, invoiceConfig);
    }

    @Test(groups = "fast")
    public void testWithNullEventSetAndNullInvoiceSet() throws InvoiceApiException {
        final UUID accountId = UUID.randomUUID();
        final Invoice invoice = generator.generateInvoice(accountId, null, null, clock.getUTCToday(), Currency.USD);

        assertNull(invoice);
    }

    @Test(groups = "fast")
    public void testWithEmptyEventSet() throws InvoiceApiException {
        final BillingEventSet events = new MockBillingEventSet();

        final UUID accountId = UUID.randomUUID();
        final Invoice invoice = generator.generateInvoice(accountId, events, null, clock.getUTCToday(), Currency.USD);

        assertNull(invoice);
    }

    @Test(groups = "fast")
    public void testWithSingleMonthlyEvent() throws InvoiceApiException, CatalogApiException {
        final BillingEventSet events = new MockBillingEventSet();

        final Subscription sub = createZombieSubscription();
        final LocalDate startDate = buildDate(2011, 9, 1);

        final Plan plan = new MockPlan();
        final BigDecimal rate1 = TEN;
        final PlanPhase phase = createMockMonthlyPlanPhase(rate1);

        final BillingEvent event = createBillingEvent(sub.getId(), startDate, plan, phase, 1);
        events.add(event);

        final LocalDate targetDate = buildDate(2011, 10, 3);
        final UUID accountId = UUID.randomUUID();
        final Invoice invoice = generator.generateInvoice(accountId, events, null, targetDate, Currency.USD);

        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 2);
        assertEquals(invoice.getBalance(), TWENTY);
        assertEquals(invoice.getInvoiceItems().get(0).getSubscriptionId(), sub.getId());
    }

    private Subscription createZombieSubscription() {
        return createZombieSubscription(UUID.randomUUID());
    }

    private Subscription createZombieSubscription(final UUID subscriptionId) {
        final Subscription sub = Mockito.mock(Subscription.class);
        Mockito.when(sub.getId()).thenReturn(subscriptionId);
        Mockito.when(sub.getBundleId()).thenReturn(UUID.randomUUID());

        return sub;
    }

    @Test(groups = "fast")
    public void testSimpleWithTimeZone() throws InvoiceApiException, CatalogApiException {
        final UUID accountId = UUID.randomUUID();
        final Subscription sub = createZombieSubscription();
        final Plan plan = new MockPlan();
        final BigDecimal rate = TEN;
        final PlanPhase phase = createMockMonthlyPlanPhase(rate);

        // Start date was the 16 local, but was the 17 UTC
        final int bcdLocal = 16;
        final int bcdUTC = 17;
        final LocalDate startDate = buildDate(2012, 7, bcdLocal);

        final BillingEventSet events = new MockBillingEventSet();
        final BillingEvent event = createBillingEvent(sub.getId(), startDate, plan, phase, bcdUTC, bcdLocal);
        events.add(event);

        // Target date is the next BCD, in local time
        final LocalDate targetDate = buildDate(2012, 8, bcdLocal);
        final DateTimeZone accountTimeZone = DateTimeZone.forID("HST");
        final Invoice invoice = generator.generateInvoice(accountId, events, null, targetDate, Currency.USD);

        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 2);
        assertEquals(invoice.getInvoiceItems().get(0).getStartDate(), buildDate(2012, 7, 16));
        assertEquals(invoice.getInvoiceItems().get(0).getEndDate(), buildDate(2012, 8, 16));
        assertEquals(invoice.getInvoiceItems().get(1).getStartDate(), buildDate(2012, 8, 16));
        assertEquals(invoice.getInvoiceItems().get(1).getEndDate(), buildDate(2012, 9, 16));
    }

    @Test(groups = "fast")
    public void testSimpleWithSingleDiscountEvent() throws Exception {
        final UUID accountId = UUID.randomUUID();
        final Subscription sub = createZombieSubscription();
        final Plan plan = new MockPlan("Plan with a single discount phase");
        final PlanPhase phaseEvergreen = createMockMonthlyPlanPhase(EIGHT, PhaseType.DISCOUNT);
        final DateTimeZone accountTimeZone = DateTimeZone.UTC;
        final int bcdUTC = 16;
        final LocalDate startDate = buildDate(2012, 7, 16);

        final BillingEventSet events = new MockBillingEventSet();
        events.add(createBillingEvent(sub.getId(), startDate, plan, phaseEvergreen, bcdUTC));

        // Set a target date of today (start date)
        final LocalDate targetDate = startDate;
        final Invoice invoice = generator.generateInvoice(accountId, events, null, targetDate, Currency.USD);

        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 1);
        assertEquals(invoice.getInvoiceItems().get(0).getStartDate(), buildDate(2012, 7, 16));
        assertEquals(invoice.getInvoiceItems().get(0).getEndDate(), buildDate(2012, 8, 16));
    }

    @Test(groups = "fast")
    public void testWithSingleMonthlyEventWithLeadingProRation() throws InvoiceApiException, CatalogApiException {
        final BillingEventSet events = new MockBillingEventSet();

        final Subscription sub = createZombieSubscription();
        final LocalDate startDate = buildDate(2011, 9, 1);

        final Plan plan = new MockPlan();
        final BigDecimal rate = TEN;
        final PlanPhase phase = createMockMonthlyPlanPhase(rate);
        final BillingEvent event = createBillingEvent(sub.getId(), startDate, plan, phase, 15);
        events.add(event);

        final LocalDate targetDate = buildDate(2011, 10, 3);
        final UUID accountId = UUID.randomUUID();
        final Invoice invoice = generator.generateInvoice(accountId, events, null, targetDate, Currency.USD);

        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 2);

        final BigDecimal expectedNumberOfBillingCycles;
        expectedNumberOfBillingCycles = ONE.add(FOURTEEN.divide(THIRTY_ONE, 2 * NUMBER_OF_DECIMALS, ROUNDING_METHOD));
        final BigDecimal expectedAmount = expectedNumberOfBillingCycles.multiply(rate).setScale(NUMBER_OF_DECIMALS, ROUNDING_METHOD);
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

        final Subscription sub = createZombieSubscription();

        final BillingEvent event1 = createBillingEvent(sub.getId(), buildDate(2011, 9, 1), plan1, phase1, 1);
        events.add(event1);

        final BillingEvent event2 = createBillingEvent(sub.getId(), buildDate(2011, 10, 1), plan2, phase2, 1);
        events.add(event2);

        final LocalDate targetDate = buildDate(2011, 10, 3);
        final UUID accountId = UUID.randomUUID();
        final Invoice invoice = generator.generateInvoice(accountId, events, null, targetDate, Currency.USD);

        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 2);
        assertEquals(invoice.getBalance(), rate1.add(rate2).setScale(NUMBER_OF_DECIMALS));
    }

    @Test(groups = "fast")
    public void testOnePlan_TwoMonthlyPhases_ChangeImmediate() throws InvoiceApiException, CatalogApiException {
        final BillingEventSet events = new MockBillingEventSet();

        final Plan plan1 = new MockPlan();
        final BigDecimal rate1 = FIVE;
        final PlanPhase phase1 = createMockMonthlyPlanPhase(rate1);

        final Subscription sub = createZombieSubscription();
        final BillingEvent event1 = createBillingEvent(sub.getId(), buildDate(2011, 9, 1), plan1, phase1, 1);
        events.add(event1);

        final BigDecimal rate2 = TEN;
        final PlanPhase phase2 = createMockMonthlyPlanPhase(rate2);
        final BillingEvent event2 = createBillingEvent(sub.getId(), buildDate(2011, 10, 15), plan1, phase2, 15);
        events.add(event2);

        final LocalDate targetDate = buildDate(2011, 12, 3);
        final UUID accountId = UUID.randomUUID();
        final Invoice invoice = generator.generateInvoice(accountId, events, null, targetDate, Currency.USD);

        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 4);

        final BigDecimal numberOfCyclesEvent1;
        numberOfCyclesEvent1 = ONE.add(FOURTEEN.divide(THIRTY_ONE, 2 * NUMBER_OF_DECIMALS, ROUNDING_METHOD));

        final BigDecimal numberOfCyclesEvent2 = TWO;

        BigDecimal expectedValue;
        expectedValue = numberOfCyclesEvent1.multiply(rate1);
        expectedValue = expectedValue.add(numberOfCyclesEvent2.multiply(rate2));
        expectedValue = expectedValue.setScale(NUMBER_OF_DECIMALS, ROUNDING_METHOD);

        assertEquals(invoice.getBalance(), expectedValue);
    }

    @Test(groups = "fast")
    public void testOnePlan_ThreeMonthlyPhases_ChangeEOT() throws InvoiceApiException, CatalogApiException {
        final BillingEventSet events = new MockBillingEventSet();

        final Plan plan1 = new MockPlan();
        final BigDecimal rate1 = FIVE;
        final PlanPhase phase1 = createMockMonthlyPlanPhase(rate1);

        final Subscription sub = createZombieSubscription();
        final BillingEvent event1 = createBillingEvent(sub.getId(), buildDate(2011, 9, 1), plan1, phase1, 1);
        events.add(event1);

        final BigDecimal rate2 = TEN;
        final PlanPhase phase2 = createMockMonthlyPlanPhase(rate2);
        final BillingEvent event2 = createBillingEvent(sub.getId(), buildDate(2011, 10, 1), plan1, phase2, 1);
        events.add(event2);

        final BigDecimal rate3 = THIRTY;
        final PlanPhase phase3 = createMockMonthlyPlanPhase(rate3);
        final BillingEvent event3 = createBillingEvent(sub.getId(), buildDate(2011, 11, 1), plan1, phase3, 1);
        events.add(event3);

        final LocalDate targetDate = buildDate(2011, 12, 3);
        final UUID accountId = UUID.randomUUID();
        final Invoice invoice = generator.generateInvoice(accountId, events, null, targetDate, Currency.USD);

        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 4);
        assertEquals(invoice.getBalance(), rate1.add(rate2).add(TWO.multiply(rate3)).setScale(NUMBER_OF_DECIMALS));
    }

    @Test(groups = "fast")
    public void testSingleEventWithExistingInvoice() throws InvoiceApiException, CatalogApiException {
        final BillingEventSet events = new MockBillingEventSet();

        final Subscription sub = createZombieSubscription();
        final LocalDate startDate = buildDate(2011, 9, 1);

        final Plan plan1 = new MockPlan();
        final BigDecimal rate = FIVE;
        final PlanPhase phase1 = createMockMonthlyPlanPhase(rate);

        final BillingEvent event1 = createBillingEvent(sub.getId(), startDate, plan1, phase1, 1);
        events.add(event1);

        LocalDate targetDate = buildDate(2011, 12, 1);
        final UUID accountId = UUID.randomUUID();
        final Invoice invoice1 = generator.generateInvoice(accountId, events, null, targetDate, Currency.USD);
        final List<Invoice> existingInvoices = new ArrayList<Invoice>();
        existingInvoices.add(invoice1);

        targetDate = buildDate(2011, 12, 3);
        final Invoice invoice2 = generator.generateInvoice(accountId, events, existingInvoices, targetDate, Currency.USD);

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
        final UUID subscriptionId1 = UUID.randomUUID();
        final UUID subscriptionId2 = UUID.randomUUID();
        final UUID subscriptionId3 = UUID.randomUUID();
        final UUID subscriptionId4 = UUID.randomUUID();
        final UUID subscriptionId5 = UUID.randomUUID();

        final Plan plan1 = new MockPlan("Change from trial to discount with immediate cancellation");
        final PlanPhase plan1Phase1 = createMockMonthlyPlanPhase(EIGHT, PhaseType.TRIAL);
        final PlanPhase plan1Phase2 = createMockMonthlyPlanPhase(TWELVE, PhaseType.DISCOUNT);
        final PlanPhase plan1Phase3 = createMockMonthlyPlanPhase();
        final LocalDate plan1StartDate = buildDate(2011, 1, 5);
        final LocalDate plan1PhaseChangeDate = buildDate(2011, 4, 5);
        final LocalDate plan1CancelDate = buildDate(2011, 4, 29);

        final Plan plan2 = new MockPlan("Change phase from trial to discount to evergreen");
        final PlanPhase plan2Phase1 = createMockMonthlyPlanPhase(TWENTY, PhaseType.TRIAL);
        final PlanPhase plan2Phase2 = createMockMonthlyPlanPhase(THIRTY, PhaseType.DISCOUNT);
        final PlanPhase plan2Phase3 = createMockMonthlyPlanPhase(FORTY, PhaseType.EVERGREEN);
        final LocalDate plan2StartDate = buildDate(2011, 3, 10);
        final LocalDate plan2PhaseChangeToDiscountDate = buildDate(2011, 6, 10);
        final LocalDate plan2PhaseChangeToEvergreenDate = buildDate(2011, 9, 10);

        final Plan plan3 = new MockPlan("Upgrade with immediate change, BCD = 31");
        final PlanPhase plan3Phase1 = createMockMonthlyPlanPhase(TEN, PhaseType.EVERGREEN);
        final PlanPhase plan3Phase2 = createMockAnnualPlanPhase(ONE_HUNDRED, PhaseType.EVERGREEN);
        final LocalDate plan3StartDate = buildDate(2011, 5, 20);
        final LocalDate plan3UpgradeToAnnualDate = buildDate(2011, 7, 31);

        final Plan plan4a = new MockPlan("Plan change effective EOT; plan 1");
        final Plan plan4b = new MockPlan("Plan change effective EOT; plan 2");
        final PlanPhase plan4aPhase1 = createMockMonthlyPlanPhase(FIFTEEN);
        final PlanPhase plan4bPhase1 = createMockMonthlyPlanPhase(TWENTY_FOUR);

        final LocalDate plan4StartDate = buildDate(2011, 6, 7);
        final LocalDate plan4ChangeOfPlanDate = buildDate(2011, 8, 7);

        final Plan plan5 = new MockPlan("Add-on");
        final PlanPhase plan5Phase1 = createMockMonthlyPlanPhase(TWENTY);
        final PlanPhase plan5Phase2 = createMockMonthlyPlanPhase();
        final LocalDate plan5StartDate = buildDate(2011, 6, 21);
        final LocalDate plan5CancelDate = buildDate(2011, 10, 7);

        BigDecimal expectedAmount;
        final List<Invoice> invoices = new ArrayList<Invoice>();
        final BillingEventSet events = new MockBillingEventSet();

        // on 1/5/2011, create subscription 1 (trial)
        events.add(createBillingEvent(subscriptionId1, plan1StartDate, plan1, plan1Phase1, 5));
        expectedAmount = EIGHT;
        testInvoiceGeneration(accountId, events, invoices, plan1StartDate, 1, expectedAmount);

        // on 2/5/2011, invoice subscription 1 (trial)
        expectedAmount = EIGHT;
        testInvoiceGeneration(accountId, events, invoices, buildDate(2011, 2, 5), 1, expectedAmount);

        // on 3/5/2011, invoice subscription 1 (trial)
        expectedAmount = EIGHT;
        testInvoiceGeneration(accountId, events, invoices, buildDate(2011, 3, 5), 1, expectedAmount);

        // on 3/10/2011, create subscription 2 (trial)
        events.add(createBillingEvent(subscriptionId2, plan2StartDate, plan2, plan2Phase1, 10));
        expectedAmount = TWENTY;
        testInvoiceGeneration(accountId, events, invoices, plan2StartDate, 1, expectedAmount);

        // on 4/5/2011, invoice subscription 1 (discount)
        events.add(createBillingEvent(subscriptionId1, plan1PhaseChangeDate, plan1, plan1Phase2, 5));
        expectedAmount = TWELVE;
        testInvoiceGeneration(accountId, events, invoices, plan1PhaseChangeDate, 1, expectedAmount);

        // on 4/10/2011, invoice subscription 2 (trial)
        expectedAmount = TWENTY;
        testInvoiceGeneration(accountId, events, invoices, buildDate(2011, 4, 10), 1, expectedAmount);

        // on 4/29/2011, cancel subscription 1
        events.add(createBillingEvent(subscriptionId1, plan1CancelDate, plan1, plan1Phase3, 5));
        // previous invoices are adjusted; this is the pro-ration amount only
        expectedAmount = TWELVE.multiply(TWENTY_FOUR.divide(THIRTY, NUMBER_OF_DECIMALS, ROUNDING_METHOD)).setScale(NUMBER_OF_DECIMALS);
        testInvoiceGeneration(accountId, events, invoices, plan1CancelDate, 2, expectedAmount);

        // on 5/10/2011, invoice subscription 2 (trial)
        expectedAmount = TWENTY;
        testInvoiceGeneration(accountId, events, invoices, buildDate(2011, 5, 10), 1, expectedAmount);

        // on 5/20/2011, create subscription 3 (monthly)
        events.add(createBillingEvent(subscriptionId3, plan3StartDate, plan3, plan3Phase1, 20));
        expectedAmount = TEN;
        testInvoiceGeneration(accountId, events, invoices, plan3StartDate, 1, expectedAmount);

        // on 6/7/2011, create subscription 4
        events.add(createBillingEvent(subscriptionId4, plan4StartDate, plan4a, plan4aPhase1, 7));
        expectedAmount = FIFTEEN;
        testInvoiceGeneration(accountId, events, invoices, plan4StartDate, 1, expectedAmount);

        // on 6/10/2011, invoice subscription 2 (discount)
        events.add(createBillingEvent(subscriptionId2, plan2PhaseChangeToDiscountDate, plan2, plan2Phase2, 10));
        expectedAmount = THIRTY;
        testInvoiceGeneration(accountId, events, invoices, plan2PhaseChangeToDiscountDate, 1, expectedAmount);

        // on 6/20/2011, invoice subscription 3 (monthly)
        expectedAmount = TEN;
        testInvoiceGeneration(accountId, events, invoices, buildDate(2011, 6, 20), 1, expectedAmount);

        // on 6/21/2011, create add-on (subscription 5)
        events.add(createBillingEvent(subscriptionId5, plan5StartDate, plan5, plan5Phase1, 10));
        expectedAmount = TWENTY.multiply(NINETEEN).divide(THIRTY, NUMBER_OF_DECIMALS, ROUNDING_METHOD);
        testInvoiceGeneration(accountId, events, invoices, plan5StartDate, 1, expectedAmount);

        // on 7/7/2011, invoice subscription 4 (plan 1)
        expectedAmount = FIFTEEN;
        testInvoiceGeneration(accountId, events, invoices, buildDate(2011, 7, 7), 1, expectedAmount);

        // on 7/10/2011, invoice subscription 2 (discount), invoice subscription 5
        expectedAmount = THIRTY.add(TWENTY);
        testInvoiceGeneration(accountId, events, invoices, buildDate(2011, 7, 10), 2, expectedAmount);

        // on 7/20/2011, invoice subscription 3 (monthly)
        expectedAmount = TEN;
        testInvoiceGeneration(accountId, events, invoices, buildDate(2011, 7, 20), 1, expectedAmount);

        // on 7/31/2011, convert subscription 3 to annual
        events.add(createBillingEvent(subscriptionId3, plan3UpgradeToAnnualDate, plan3, plan3Phase2, 31));
        expectedAmount = ONE_HUNDRED.add(TEN.multiply(ELEVEN.divide(THIRTY_ONE, 2 * NUMBER_OF_DECIMALS, ROUNDING_METHOD)));
        expectedAmount = expectedAmount.setScale(NUMBER_OF_DECIMALS, ROUNDING_METHOD);
        testInvoiceGeneration(accountId, events, invoices, plan3UpgradeToAnnualDate, 3, expectedAmount);

        // on 8/7/2011, invoice subscription 4 (plan 2)
        events.add(createBillingEvent(subscriptionId4, plan4ChangeOfPlanDate, plan4b, plan4bPhase1, 7));
        expectedAmount = TWENTY_FOUR;
        testInvoiceGeneration(accountId, events, invoices, plan4ChangeOfPlanDate, 1, expectedAmount);

        // on 8/10/2011, invoice plan 2 (discount), invoice subscription 5
        expectedAmount = THIRTY.add(TWENTY);
        testInvoiceGeneration(accountId, events, invoices, buildDate(2011, 8, 10), 2, expectedAmount);

        // on 9/7/2011, invoice subscription 4 (plan 2)
        expectedAmount = TWENTY_FOUR;
        testInvoiceGeneration(accountId, events, invoices, buildDate(2011, 9, 7), 1, expectedAmount);

        // on 9/10/2011, invoice plan 2 (evergreen), invoice subscription 5
        events.add(createBillingEvent(subscriptionId2, plan2PhaseChangeToEvergreenDate, plan2, plan2Phase3, 10));
        expectedAmount = FORTY.add(TWENTY);
        testInvoiceGeneration(accountId, events, invoices, plan2PhaseChangeToEvergreenDate, 2, expectedAmount);

        // on 10/7/2011, invoice subscription 4 (plan 2), cancel subscription 5
        events.add(createBillingEvent(subscriptionId5, plan5CancelDate, plan5, plan5Phase2, 10));
        expectedAmount = TWENTY_FOUR.add(TWENTY.multiply(TWENTY_SEVEN.divide(THIRTY)).setScale(NUMBER_OF_DECIMALS));
        testInvoiceGeneration(accountId, events, invoices, plan5CancelDate, 3, expectedAmount);

        // on 10/10/2011, invoice plan 2 (evergreen)
        expectedAmount = FORTY;
        testInvoiceGeneration(accountId, events, invoices, buildDate(2011, 10, 10), 1, expectedAmount);
    }

    @Test(groups = "fast")
    public void testZeroDollarEvents() throws InvoiceApiException, CatalogApiException {
        final Plan plan = new MockPlan();
        final PlanPhase planPhase = createMockMonthlyPlanPhase(ZERO);
        final BillingEventSet events = new MockBillingEventSet();
        final LocalDate targetDate = buildDate(2011, 1, 1);
        events.add(createBillingEvent(UUID.randomUUID(), targetDate, plan, planPhase, 1));

        final Invoice invoice = generator.generateInvoice(UUID.randomUUID(), events, null, targetDate, Currency.USD);

        assertEquals(invoice.getNumberOfItems(), 1);
    }

    @Test(groups = "fast")
    public void testEndDateIsCorrect() throws InvoiceApiException, CatalogApiException {
        final Plan plan = new MockPlan();
        final PlanPhase planPhase = createMockMonthlyPlanPhase(ZERO);
        final BillingEventSet events = new MockBillingEventSet();
        final LocalDate startDate = clock.getUTCToday().minusDays(1);
        final LocalDate targetDate = startDate.plusDays(1);

        events.add(createBillingEvent(UUID.randomUUID(), startDate, plan, planPhase, startDate.getDayOfMonth()));

        final Invoice invoice = generator.generateInvoice(UUID.randomUUID(), events, null, targetDate, Currency.USD);
        final RecurringInvoiceItem item = (RecurringInvoiceItem) invoice.getInvoiceItems().get(0);

        // end date of the invoice item should be equal to exactly one month later (rounded)
        assertEquals(item.getEndDate(), startDate.plusMonths(1));
    }

    @Test(groups = "fast")
    public void testFixedPriceLifeCycle() throws InvoiceApiException {
        final UUID accountId = UUID.randomUUID();
        final Subscription subscription = createZombieSubscription();

        final Plan plan = new MockPlan("plan 1");
        final MockInternationalPrice zeroPrice = new MockInternationalPrice(new DefaultPrice(ZERO, Currency.USD));
        final MockInternationalPrice cheapPrice = new MockInternationalPrice(new DefaultPrice(ONE, Currency.USD));

        final PlanPhase phase1 = new MockPlanPhase(null, zeroPrice, BillingPeriod.NO_BILLING_PERIOD, PhaseType.TRIAL);
        final PlanPhase phase2 = new MockPlanPhase(cheapPrice, null, BillingPeriod.MONTHLY, PhaseType.DISCOUNT);

        final DateTime changeDate = new DateTime("2012-04-1");

        final BillingEventSet events = new MockBillingEventSet();

        final BillingEvent event1 = createMockBillingEvent(null, subscription, new DateTime("2012-01-1"),
                                                           plan, phase1,
                                                           ZERO, null, Currency.USD, BillingPeriod.NO_BILLING_PERIOD, 1,
                                                           BillingModeType.IN_ADVANCE, "Test Event 1", 1L,
                                                           SubscriptionTransitionType.CREATE);

        final BillingEvent event2 = createMockBillingEvent(null, subscription, changeDate,
                                                           plan, phase2,
                                                           ZERO, null, Currency.USD, BillingPeriod.NO_BILLING_PERIOD, 1,
                                                           BillingModeType.IN_ADVANCE, "Test Event 2", 2L,
                                                           SubscriptionTransitionType.PHASE);

        events.add(event2);
        events.add(event1);
        final Invoice invoice1 = generator.generateInvoice(accountId, events, null, new LocalDate("2012-02-01"), Currency.USD);
        assertNotNull(invoice1);
        assertEquals(invoice1.getNumberOfItems(), 1);

        final List<Invoice> invoiceList = new ArrayList<Invoice>();
        invoiceList.add(invoice1);
        final Invoice invoice2 = generator.generateInvoice(accountId, events, invoiceList, new LocalDate("2012-04-05"), Currency.USD);
        assertNotNull(invoice2);
        assertEquals(invoice2.getNumberOfItems(), 1);
        final FixedPriceInvoiceItem item = (FixedPriceInvoiceItem) invoice2.getInvoiceItems().get(0);
        assertEquals(item.getStartDate(), changeDate.toLocalDate());
    }

    @Test(groups = "fast")
    public void testMixedModeLifeCycle() throws InvoiceApiException, CatalogApiException {
        // create a subscription with a fixed price and recurring price
        final Plan plan1 = new MockPlan();
        final BigDecimal monthlyRate = FIVE;
        final BigDecimal fixedCost = TEN;
        final PlanPhase phase1 = createMockMonthlyPlanPhase(monthlyRate, fixedCost, PhaseType.TRIAL);

        final BillingEventSet events = new MockBillingEventSet();
        final UUID subscriptionId = UUID.randomUUID();
        final UUID accountId = UUID.randomUUID();

        final LocalDate startDate = new LocalDate(2011, 1, 1);
        final BillingEvent event1 = createBillingEvent(subscriptionId, startDate, plan1, phase1, 1);
        events.add(event1);

        // ensure both components are invoiced
        final Invoice invoice1 = generator.generateInvoice(accountId, events, null, startDate, Currency.USD);
        assertNotNull(invoice1);
        assertEquals(invoice1.getNumberOfItems(), 2);
        assertEquals(invoice1.getBalance(), FIFTEEN);

        final List<Invoice> invoiceList = new ArrayList<Invoice>();
        invoiceList.add(invoice1);

        // move forward in time one billing period
        final LocalDate currentDate = startDate.plusMonths(1);

        // ensure that only the recurring price is invoiced
        final Invoice invoice2 = generator.generateInvoice(accountId, events, invoiceList, currentDate, Currency.USD);
        assertNotNull(invoice2);
        assertEquals(invoice2.getNumberOfItems(), 1);
        assertEquals(invoice2.getBalance(), FIVE);
    }

    @Test(groups = "fast")
    public void testFixedModePlanChange() throws InvoiceApiException, CatalogApiException {
        // create a subscription with a fixed price and recurring price
        final Plan plan1 = new MockPlan();
        final BigDecimal fixedCost1 = TEN;
        final BigDecimal fixedCost2 = TWENTY;
        final PlanPhase phase1 = createMockMonthlyPlanPhase(null, fixedCost1, PhaseType.TRIAL);
        final PlanPhase phase2 = createMockMonthlyPlanPhase(null, fixedCost2, PhaseType.EVERGREEN);

        final BillingEventSet events = new MockBillingEventSet();
        final UUID subscriptionId = UUID.randomUUID();
        final UUID accountId = UUID.randomUUID();

        final LocalDate startDate = new LocalDate(2011, 1, 1);
        final BillingEvent event1 = createBillingEvent(subscriptionId, startDate, plan1, phase1, 1);
        events.add(event1);

        // ensure that a single invoice item is generated for the fixed cost
        final Invoice invoice1 = generator.generateInvoice(accountId, events, null, startDate, Currency.USD);
        assertNotNull(invoice1);
        assertEquals(invoice1.getNumberOfItems(), 1);
        assertEquals(invoice1.getBalance(), fixedCost1);

        final List<Invoice> invoiceList = new ArrayList<Invoice>();
        invoiceList.add(invoice1);

        // move forward in time one billing period
        final LocalDate phaseChangeDate = startDate.plusMonths(1);
        final BillingEvent event2 = createBillingEvent(subscriptionId, phaseChangeDate, plan1, phase2, 1);
        events.add(event2);

        // ensure that a single invoice item is generated for the fixed cost
        final Invoice invoice2 = generator.generateInvoice(accountId, events, invoiceList, phaseChangeDate, Currency.USD);
        assertNotNull(invoice2);
        assertEquals(invoice2.getNumberOfItems(), 1);
        assertEquals(invoice2.getBalance(), fixedCost2);
    }

    @Test(groups = "fast")
    public void testInvoiceGenerationFailureScenario() throws InvoiceApiException, CatalogApiException {
        final BillingEventSet events = new MockBillingEventSet();
        final UUID subscriptionId = UUID.randomUUID();
        final UUID accountId = UUID.randomUUID();
        final int BILL_CYCLE_DAY = 15;

        // create subscription with a zero-dollar trial, a monthly discount period and a monthly evergreen
        final Plan plan1 = new MockPlan();
        final PlanPhase phase1 = createMockMonthlyPlanPhase(null, ZERO, PhaseType.TRIAL);
        final BigDecimal DISCOUNT_PRICE = new BigDecimal("9.95");
        final PlanPhase phase2 = createMockMonthlyPlanPhase(DISCOUNT_PRICE, null, PhaseType.DISCOUNT);
        final PlanPhase phase3 = createMockMonthlyPlanPhase(new BigDecimal("19.95"), null, PhaseType.EVERGREEN);

        // set up billing events
        final LocalDate creationDate = new LocalDate(2012, 3, 6);
        events.add(createBillingEvent(subscriptionId, creationDate, plan1, phase1, BILL_CYCLE_DAY));

        // trialPhaseEndDate = 2012/4/5
        final LocalDate trialPhaseEndDate = creationDate.plusDays(30);
        events.add(createBillingEvent(subscriptionId, trialPhaseEndDate, plan1, phase2, BILL_CYCLE_DAY));

        // discountPhaseEndDate = 2012/10/5
        final LocalDate discountPhaseEndDate = trialPhaseEndDate.plusMonths(6);
        events.add(createBillingEvent(subscriptionId, discountPhaseEndDate, plan1, phase3, BILL_CYCLE_DAY));

        final Invoice invoice1 = generator.generateInvoice(accountId, events, null, creationDate, Currency.USD);
        assertNotNull(invoice1);
        assertEquals(invoice1.getNumberOfItems(), 1);
        assertEquals(invoice1.getBalance().compareTo(ZERO), 0);

        final List<Invoice> invoiceList = new ArrayList<Invoice>();
        invoiceList.add(invoice1);

        final Invoice invoice2 = generator.generateInvoice(accountId, events, invoiceList, trialPhaseEndDate, Currency.USD);
        assertNotNull(invoice2);
        assertEquals(invoice2.getNumberOfItems(), 1);
        assertEquals(invoice2.getInvoiceItems().get(0).getStartDate(), trialPhaseEndDate);
        assertEquals(invoice2.getBalance().compareTo(new BigDecimal("3.21")), 0);

        invoiceList.add(invoice2);
        LocalDate targetDate = new LocalDate(trialPhaseEndDate.getYear(), trialPhaseEndDate.getMonthOfYear(), BILL_CYCLE_DAY);
        final Invoice invoice3 = generator.generateInvoice(accountId, events, invoiceList, targetDate, Currency.USD);
        assertNotNull(invoice3);
        assertEquals(invoice3.getNumberOfItems(), 1);
        assertEquals(invoice3.getInvoiceItems().get(0).getStartDate(), targetDate);
        assertEquals(invoice3.getBalance().compareTo(DISCOUNT_PRICE), 0);

        invoiceList.add(invoice3);
        targetDate = targetDate.plusMonths(6);
        final Invoice invoice4 = generator.generateInvoice(accountId, events, invoiceList, targetDate, Currency.USD);
        assertNotNull(invoice4);
        assertEquals(invoice4.getNumberOfItems(), 7);
    }

    @Test(groups = "fast", expectedExceptions = {InvoiceApiException.class})
    public void testTargetDateRestrictionFailure() throws InvoiceApiException, CatalogApiException {
        final LocalDate targetDate = clock.getUTCToday().plusMonths(60);
        final BillingEventSet events = new MockBillingEventSet();
        final Plan plan1 = new MockPlan();
        final PlanPhase phase1 = createMockMonthlyPlanPhase(null, ZERO, PhaseType.TRIAL);
        events.add(createBillingEvent(UUID.randomUUID(), clock.getUTCToday(), plan1, phase1, 1));
        generator.generateInvoice(UUID.randomUUID(), events, null, targetDate, Currency.USD);
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

    private BillingEvent createBillingEvent(final UUID subscriptionId, final LocalDate startDate,
                                            final Plan plan, final PlanPhase planPhase, final int billCycleDayUTC) throws CatalogApiException {
        return createBillingEvent(subscriptionId, startDate, plan, planPhase, billCycleDayUTC, billCycleDayUTC);
    }

    private BillingEvent createBillingEvent(final UUID subscriptionId, final LocalDate startDate,
                                            final Plan plan, final PlanPhase planPhase, final int billCycleDayUTC, final int billCycleDayLocal) throws CatalogApiException {
        final Subscription sub = createZombieSubscription(subscriptionId);
        final Currency currency = Currency.USD;

        return createMockBillingEvent(null, sub, startDate.toDateTimeAtStartOfDay(), plan, planPhase,
                                      planPhase.getFixedPrice() == null ? null : planPhase.getFixedPrice().getPrice(currency),
                                      planPhase.getRecurringPrice() == null ? null : planPhase.getRecurringPrice().getPrice(currency),
                                      currency, planPhase.getBillingPeriod(),
                                      billCycleDayUTC, billCycleDayLocal, BillingModeType.IN_ADVANCE, "Test", 1L, SubscriptionTransitionType.CREATE);
    }

    private void testInvoiceGeneration(final UUID accountId, final BillingEventSet events, final List<Invoice> existingInvoices,
                                       final LocalDate targetDate, final int expectedNumberOfItems,
                                       final BigDecimal expectedAmount) throws InvoiceApiException {
        final Currency currency = Currency.USD;
        final Invoice invoice = generator.generateInvoice(accountId, events, existingInvoices, targetDate, currency);
        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), expectedNumberOfItems);
        existingInvoices.add(invoice);

        distributeItems(existingInvoices);
        assertEquals(invoice.getBalance(), expectedAmount);
    }

    @Test(groups = "fast")
    public void testAddOnInvoiceGeneration() throws CatalogApiException, InvoiceApiException {
        final LocalDate april25 = new LocalDate(2012, 4, 25);

        // create a base plan on April 25th
        final UUID accountId = UUID.randomUUID();
        final Subscription baseSubscription = createZombieSubscription();

        final Plan basePlan = new MockPlan("base Plan");
        final MockInternationalPrice price5 = new MockInternationalPrice(new DefaultPrice(FIVE, Currency.USD));
        final MockInternationalPrice price10 = new MockInternationalPrice(new DefaultPrice(TEN, Currency.USD));
        final MockInternationalPrice price20 = new MockInternationalPrice(new DefaultPrice(TWENTY, Currency.USD));
        final PlanPhase basePlanEvergreen = new MockPlanPhase(price10, null, BillingPeriod.MONTHLY, PhaseType.EVERGREEN);

        final BillingEventSet events = new MockBillingEventSet();
        events.add(createBillingEvent(baseSubscription.getId(), april25, basePlan, basePlanEvergreen, 25));

        // generate invoice
        final Invoice invoice1 = generator.generateInvoice(accountId, events, null, april25, Currency.USD);
        assertNotNull(invoice1);
        assertEquals(invoice1.getNumberOfItems(), 1);
        assertEquals(invoice1.getBalance().compareTo(TEN), 0);

        final List<Invoice> invoices = new ArrayList<Invoice>();
        invoices.add(invoice1);

        // create 2 add ons on April 28th
        final LocalDate april28 = new LocalDate(2012, 4, 28);
        final Subscription addOnSubscription1 = createZombieSubscription();
        final Plan addOn1Plan = new MockPlan("add on 1");
        final PlanPhase addOn1PlanPhaseEvergreen = new MockPlanPhase(price5, null, BillingPeriod.MONTHLY, PhaseType.EVERGREEN);
        events.add(createBillingEvent(addOnSubscription1.getId(), april28, addOn1Plan, addOn1PlanPhaseEvergreen, 25));

        final Subscription addOnSubscription2 = createZombieSubscription();
        final Plan addOn2Plan = new MockPlan("add on 2");
        final PlanPhase addOn2PlanPhaseEvergreen = new MockPlanPhase(price20, null, BillingPeriod.MONTHLY, PhaseType.EVERGREEN);
        events.add(createBillingEvent(addOnSubscription2.getId(), april28, addOn2Plan, addOn2PlanPhaseEvergreen, 25));

        // generate invoice
        final Invoice invoice2 = generator.generateInvoice(accountId, events, invoices, april28, Currency.USD);
        invoices.add(invoice2);
        assertNotNull(invoice2);
        assertEquals(invoice2.getNumberOfItems(), 2);
        assertEquals(invoice2.getBalance().compareTo(TWENTY_FIVE.multiply(new BigDecimal("0.9")).setScale(NUMBER_OF_DECIMALS, ROUNDING_METHOD)), 0);

        // perform a repair (change base plan; remove one add-on)
        // event stream should include just two plans
        final MockBillingEventSet newEvents = new MockBillingEventSet();
        final Plan basePlan2 = new MockPlan("base plan 2");
        final MockInternationalPrice price13 = new MockInternationalPrice(new DefaultPrice(THIRTEEN, Currency.USD));
        final PlanPhase basePlan2Phase = new MockPlanPhase(price13, null, BillingPeriod.MONTHLY, PhaseType.EVERGREEN);
        newEvents.add(createBillingEvent(baseSubscription.getId(), april25, basePlan2, basePlan2Phase, 25));
        newEvents.add(createBillingEvent(addOnSubscription1.getId(), april28, addOn1Plan, addOn1PlanPhaseEvergreen, 25));

        // generate invoice
        final LocalDate may1 = new LocalDate(2012, 5, 1);
        final Invoice invoice3 = generator.generateInvoice(accountId, newEvents, invoices, may1, Currency.USD);
        assertNotNull(invoice3);
        assertEquals(invoice3.getNumberOfItems(), 5);
        // -4.50 -18 - 10 (to correct the previous 2 invoices) + 4.50 + 13
        assertEquals(invoice3.getBalance().compareTo(FIFTEEN.negate()), 0);
    }

    @Test
    public void testRepairForPaidInvoice() throws CatalogApiException, InvoiceApiException {
        // create an invoice
        final LocalDate april25 = new LocalDate(2012, 4, 25);

        // create a base plan on April 25th
        final UUID accountId = UUID.randomUUID();
        final Subscription originalSubscription = createZombieSubscription();

        final Plan originalPlan = new MockPlan("original plan");
        final MockInternationalPrice price10 = new MockInternationalPrice(new DefaultPrice(TEN, Currency.USD));
        final PlanPhase originalPlanEvergreen = new MockPlanPhase(price10, null, BillingPeriod.MONTHLY, PhaseType.EVERGREEN);

        final BillingEventSet events = new MockBillingEventSet();
        events.add(createBillingEvent(originalSubscription.getId(), april25, originalPlan, originalPlanEvergreen, 25));

        final Invoice invoice1 = generator.generateInvoice(accountId, events, null, april25, Currency.USD);

        printDetailInvoice(invoice1);

        assertEquals(invoice1.getNumberOfItems(), 1);
        final List<Invoice> invoices = new ArrayList<Invoice>();
        invoices.add(invoice1);

        // pay the invoice
        invoice1.addPayment(new DefaultInvoicePayment(InvoicePaymentType.ATTEMPT, UUID.randomUUID(), invoice1.getId(), april25.toDateTimeAtCurrentTime(), TEN, Currency.USD));
        assertEquals(invoice1.getBalance().compareTo(ZERO), 0);

        // change the plan (i.e. repair) on start date
        events.clear();
        final Subscription newSubscription = createZombieSubscription();
        final Plan newPlan = new MockPlan("new plan");
        final MockInternationalPrice price5 = new MockInternationalPrice(new DefaultPrice(FIVE, Currency.USD));
        final PlanPhase newPlanEvergreen = new MockPlanPhase(price5, null, BillingPeriod.MONTHLY, PhaseType.EVERGREEN);
        events.add(createBillingEvent(newSubscription.getId(), april25, newPlan, newPlanEvergreen, 25));

        // generate a new invoice
        final Invoice invoice2 = generator.generateInvoice(accountId, events, invoices, april25, Currency.USD);

        printDetailInvoice(invoice2);
        assertEquals(invoice2.getNumberOfItems(), 4);
        invoices.add(invoice2);

        // move items to the correct invoice (normally, the dao calls will sort that out)
        distributeItems(invoices);

        // ensure that the original invoice balance is zero
        assertEquals(invoice1.getBalance().compareTo(ZERO), 0);

        // ensure that the account balance is correct
        assertEquals(invoice2.getBalance().compareTo(ZERO), 0);

        // ensure that the account has a credit balance
        final BigDecimal creditBalance = invoice1.getCBAAmount().add(invoice2.getCBAAmount());
        assertTrue(creditBalance.compareTo(FIVE) == 0);
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

    @Test(groups = "fast")
    public void testAutoInvoiceOffAccount() throws Exception {
        final MockBillingEventSet events = new MockBillingEventSet();
        events.setAccountInvoiceOff(true);

        final Subscription sub = createZombieSubscription();
        final LocalDate startDate = buildDate(2011, 9, 1);

        final Plan plan = new MockPlan();
        final BigDecimal rate1 = TEN;
        final PlanPhase phase = createMockMonthlyPlanPhase(rate1);

        final BillingEvent event = createBillingEvent(sub.getId(), startDate, plan, phase, 1);
        events.add(event);

        final LocalDate targetDate = buildDate(2011, 10, 3);
        final UUID accountId = UUID.randomUUID();
        final Invoice invoice = generator.generateInvoice(accountId, events, null, targetDate, Currency.USD);

        assertNull(invoice);
    }

    public void testAutoInvoiceOffWithCredits() throws CatalogApiException, InvoiceApiException {
        final Currency currency = Currency.USD;
        final List<Invoice> invoices = new ArrayList<Invoice>();
        final MockBillingEventSet eventSet = new MockBillingEventSet();
        final UUID accountId = UUID.randomUUID();

        final LocalDate startDate = new LocalDate(2012, 1, 1);

        // add first subscription creation event
        final UUID subscriptionId1 = UUID.randomUUID();
        final Plan plan1 = new MockPlan();
        final PlanPhase plan1phase1 = createMockMonthlyPlanPhase(FIFTEEN, null, PhaseType.DISCOUNT);
        final BillingEvent subscription1creation = createBillingEvent(subscriptionId1, startDate, plan1, plan1phase1, 1);
        eventSet.add(subscription1creation);

        // add second subscription creation event
        final UUID subscriptionId2 = UUID.randomUUID();
        final Plan plan2 = new MockPlan();
        final PlanPhase plan2phase1 = createMockMonthlyPlanPhase(TWELVE, null, PhaseType.EVERGREEN);
        eventSet.add(createBillingEvent(subscriptionId2, startDate, plan2, plan2phase1, 1));

        // generate the first invoice
        final Invoice invoice1 = generator.generateInvoice(accountId, eventSet, invoices, startDate, currency);
        assertNotNull(invoice1);
        assertTrue(invoice1.getBalance().compareTo(FIFTEEN.add(TWELVE)) == 0);
        invoices.add(invoice1);

        // set auto invoice off for first subscription (i.e. remove event from BillingEventSet and add subscription id to the list
        // generate invoice
        eventSet.remove(subscription1creation);
        eventSet.addSubscriptionWithAutoInvoiceOff(subscriptionId1);

        final LocalDate targetDate2 = startDate.plusMonths(1);
        final Invoice invoice2 = generator.generateInvoice(accountId, eventSet, invoices, targetDate2, currency);
        assertNotNull(invoice2);
        assertTrue(invoice2.getBalance().compareTo(TWELVE) == 0);
        invoices.add(invoice2);

        final LocalDate targetDate3 = targetDate2.plusMonths(1);
        eventSet.clearSubscriptionsWithAutoInvoiceOff();
        eventSet.add(subscription1creation);
        final Invoice invoice3 = generator.generateInvoice(accountId, eventSet, invoices, targetDate3, currency);
        assertNotNull(invoice3);
        assertTrue(invoice3.getBalance().compareTo(FIFTEEN.multiply(TWO).add(TWELVE)) == 0);
    }

    @Test(groups = "fast")
    public void testAccountCredit() throws CatalogApiException, InvoiceApiException {
        final BillingEventSet billingEventSet = new MockBillingEventSet();

        final LocalDate startDate = new LocalDate(2012, 3, 1);
        final UUID accountId = UUID.randomUUID();
        final UUID subscriptionId = UUID.randomUUID();
        final Plan plan = new MockPlan("original plan");
        final MockInternationalPrice price10 = new MockInternationalPrice(new DefaultPrice(TEN, Currency.USD));
        final PlanPhase planPhase = new MockPlanPhase(price10, null, BillingPeriod.MONTHLY, PhaseType.EVERGREEN);
        final BillingEvent creation = createBillingEvent(subscriptionId, startDate, plan, planPhase, 1);
        billingEventSet.add(creation);

        final List<Invoice> invoices = new ArrayList<Invoice>();

        final Invoice initialInvoice = generator.generateInvoice(accountId, billingEventSet, null, startDate, Currency.USD);
        assertNotNull(initialInvoice);
        assertEquals(initialInvoice.getNumberOfItems(), 1);
        assertEquals(initialInvoice.getBalance().compareTo(TEN), 0);
        invoices.add(initialInvoice);

        printDetailInvoice(initialInvoice);

        // add account-level credit
        final LocalDate creditDate = new LocalDate(startDate.plusDays(5), DateTimeZone.UTC);
        final Invoice invoiceWithCredit = new DefaultInvoice(accountId, creditDate, creditDate, Currency.USD);
        final InvoiceItem accountCredit = new CreditBalanceAdjInvoiceItem(invoiceWithCredit.getId(), accountId, creditDate, FIVE, Currency.USD);
        invoiceWithCredit.addInvoiceItem(accountCredit);
        invoices.add(invoiceWithCredit);

        printDetailInvoice(invoiceWithCredit);

        // invoice one month after the initial subscription
        final Invoice finalInvoice = generator.generateInvoice(accountId, billingEventSet, invoices, startDate.plusMonths(1), Currency.USD);

        printDetailInvoice(finalInvoice);

        System.out.println("BALANCE = " + finalInvoice.getBalance());
        assertEquals(finalInvoice.getBalance().compareTo(FIVE), 0);
        System.out.println("CBA = " + finalInvoice.getCBAAmount());
        assertEquals(finalInvoice.getCBAAmount().compareTo(FIVE.negate()), 0);
        assertEquals(finalInvoice.getNumberOfItems(), 2);
    }

    private void printDetailInvoice(final Invoice invoice) {
        log.info("--------------------  START DETAIL ----------------------");
        log.info("Invoice " + invoice.getId() + ": BALANCE = " + invoice.getBalance()
                 + ", CBA = " + invoice.getCBAAmount()
                 + ", CHARGE_AMOUNT = " + invoice.getChargedAmount()
                 + ", ADJ_AMOUNT = " + invoice.getCreditAdjAmount());

        for (final InvoiceItem cur : invoice.getInvoiceItems()) {
            log.info(cur.toString());
        }
        log.info("--------------------  END DETAIL ----------------------");
    }
}
