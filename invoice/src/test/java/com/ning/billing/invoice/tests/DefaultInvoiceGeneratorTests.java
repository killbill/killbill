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

package com.ning.billing.invoice.tests;

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
import com.ning.billing.config.InvoiceConfig;
import com.ning.billing.entitlement.api.billing.BillingEvent;
import com.ning.billing.entitlement.api.billing.BillingModeType;
import com.ning.billing.entitlement.api.billing.DefaultBillingEvent;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.api.user.SubscriptionFactory.SubscriptionBuilder;
import com.ning.billing.entitlement.api.user.SubscriptionTransition.SubscriptionTransitionType;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.model.BillingEventSet;
import com.ning.billing.invoice.model.DefaultInvoiceGenerator;
import com.ning.billing.invoice.model.FixedPriceInvoiceItem;
import com.ning.billing.invoice.model.InvoiceGenerator;
import com.ning.billing.invoice.model.RecurringInvoiceItem;
import com.ning.billing.mock.BrainDeadProxyFactory;
import com.ning.billing.mock.BrainDeadProxyFactory.ZombieControl;

import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.DefaultClock;
import org.joda.time.DateTime;
import org.testng.annotations.Test;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

@Test(groups = {"fast", "invoicing", "invoiceGenerator"})
public class DefaultInvoiceGeneratorTests extends InvoicingTestBase {
    private final Clock clock = new DefaultClock();
    private final InvoiceConfig invoiceConfig = new InvoiceConfig() {
        @Override
        public long getDaoClaimTimeMs() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getDaoMaxReadyEvents() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getNotificationSleepTimeMs() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isEventProcessingOff() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getNumberOfMonthsInFuture() {
            return 36;
        }
    };

    private final InvoiceGenerator generator;

    public DefaultInvoiceGeneratorTests() {
        super();
        this.generator = new DefaultInvoiceGenerator(clock, invoiceConfig);
    }

    @Test
    public void testWithNullEventSetAndNullInvoiceSet() throws InvoiceApiException {
        UUID accountId = UUID.randomUUID();
        Invoice invoice = generator.generateInvoice(accountId, null, null, new DateTime(), Currency.USD);

        assertNull(invoice);
    }

    @Test
    public void testWithEmptyEventSet() throws InvoiceApiException {
        BillingEventSet events = new BillingEventSet();

        UUID accountId = UUID.randomUUID();
        Invoice invoice = generator.generateInvoice(accountId, events, null, new DateTime(), Currency.USD);

        assertNull(invoice);
    }

    @Test
    public void testWithSingleMonthlyEvent() throws InvoiceApiException, CatalogApiException {
        BillingEventSet events = new BillingEventSet();

        Subscription sub = new SubscriptionData(new SubscriptionBuilder().setId(UUID.randomUUID()));
        DateTime startDate = buildDateTime(2011, 9, 1);

        Plan plan = new MockPlan();
        BigDecimal rate1 = TEN;
        PlanPhase phase = createMockMonthlyPlanPhase(rate1);

        BillingEvent event = createBillingEvent(sub.getId(), startDate, plan, phase, 1);
        events.add(event);

        DateTime targetDate = buildDateTime(2011, 10, 3);
        UUID accountId = UUID.randomUUID();
        Invoice invoice = generator.generateInvoice(accountId, events, null, targetDate, Currency.USD);

        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 2);
        assertEquals(invoice.getTotalAmount(), TWENTY);
        assertEquals(invoice.getInvoiceItems().get(0).getSubscriptionId(), sub.getId());
    }

    @Test
    public void testWithSingleMonthlyEventWithLeadingProRation() throws InvoiceApiException, CatalogApiException {
        BillingEventSet events = new BillingEventSet();

        Subscription sub = new SubscriptionData(new SubscriptionBuilder().setId(UUID.randomUUID()));
        DateTime startDate = buildDateTime(2011, 9, 1);

        Plan plan = new MockPlan();
        BigDecimal rate = TEN;
        PlanPhase phase = createMockMonthlyPlanPhase(rate);
        BillingEvent event = createBillingEvent(sub.getId(), startDate, plan, phase, 15);
        events.add(event);

        DateTime targetDate = buildDateTime(2011, 10, 3);
        UUID accountId = UUID.randomUUID();
        Invoice invoice = generator.generateInvoice(accountId, events, null, targetDate, Currency.USD);

        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 2);

        BigDecimal expectedNumberOfBillingCycles;
        expectedNumberOfBillingCycles = ONE.add(FOURTEEN.divide(THIRTY_ONE, 2 * NUMBER_OF_DECIMALS, ROUNDING_METHOD));
        BigDecimal expectedAmount = expectedNumberOfBillingCycles.multiply(rate).setScale(NUMBER_OF_DECIMALS, ROUNDING_METHOD);
        assertEquals(invoice.getTotalAmount(), expectedAmount);
    }

    @Test
    public void testTwoMonthlySubscriptionsWithAlignedBillingDates() throws InvoiceApiException, CatalogApiException {
        BillingEventSet events = new BillingEventSet();

        Plan plan1 = new MockPlan();
        BigDecimal rate1 = FIVE;
        PlanPhase phase1 = createMockMonthlyPlanPhase(rate1);

        Plan plan2 = new MockPlan();
        BigDecimal rate2 = TEN;
        PlanPhase phase2 = createMockMonthlyPlanPhase(rate2);

        Subscription sub = new SubscriptionData(new SubscriptionBuilder().setId(UUID.randomUUID()));

        BillingEvent event1 = createBillingEvent(sub.getId(), buildDateTime(2011, 9, 1), plan1, phase1, 1);
        events.add(event1);

        BillingEvent event2 = createBillingEvent(sub.getId(), buildDateTime(2011, 10, 1), plan2, phase2, 1);
        events.add(event2);

        DateTime targetDate = buildDateTime(2011, 10, 3);
        UUID accountId = UUID.randomUUID();
        Invoice invoice = generator.generateInvoice(accountId, events, null, targetDate, Currency.USD);

        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 2);
        assertEquals(invoice.getTotalAmount(), rate1.add(rate2).setScale(NUMBER_OF_DECIMALS));
    }

    @Test
    public void testOnePlan_TwoMonthlyPhases_ChangeImmediate() throws InvoiceApiException, CatalogApiException {
        BillingEventSet events = new BillingEventSet();

        Plan plan1 = new MockPlan();
        BigDecimal rate1 = FIVE;
        PlanPhase phase1 = createMockMonthlyPlanPhase(rate1);

        Subscription sub = new SubscriptionData(new SubscriptionBuilder().setId(UUID.randomUUID()));
        BillingEvent event1 = createBillingEvent(sub.getId(), buildDateTime(2011, 9, 1), plan1,phase1, 1);
        events.add(event1);

        BigDecimal rate2 = TEN;
        PlanPhase phase2 = createMockMonthlyPlanPhase(rate2);
        BillingEvent event2 = createBillingEvent(sub.getId(), buildDateTime(2011, 10, 15), plan1, phase2, 15);
        events.add(event2);

        DateTime targetDate = buildDateTime(2011, 12, 3);
        UUID accountId = UUID.randomUUID();
        Invoice invoice = generator.generateInvoice(accountId, events, null, targetDate, Currency.USD);

        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 4);

        BigDecimal numberOfCyclesEvent1;
        numberOfCyclesEvent1 = ONE.add(FOURTEEN.divide(THIRTY_ONE, 2 * NUMBER_OF_DECIMALS, ROUNDING_METHOD));

        BigDecimal numberOfCyclesEvent2 = TWO;

        BigDecimal expectedValue;
        expectedValue = numberOfCyclesEvent1.multiply(rate1);
        expectedValue = expectedValue.add(numberOfCyclesEvent2.multiply(rate2));
        expectedValue = expectedValue.setScale(NUMBER_OF_DECIMALS, ROUNDING_METHOD);

        assertEquals(invoice.getTotalAmount(), expectedValue);
    }

    @Test
    public void testOnePlan_ThreeMonthlyPhases_ChangeEOT() throws InvoiceApiException, CatalogApiException {
        BillingEventSet events = new BillingEventSet();

        Plan plan1 = new MockPlan();
        BigDecimal rate1 = FIVE;
        PlanPhase phase1 = createMockMonthlyPlanPhase(rate1);

        Subscription sub = new SubscriptionData(new SubscriptionBuilder().setId(UUID.randomUUID()));
        BillingEvent event1 = createBillingEvent(sub.getId(), buildDateTime(2011, 9, 1), plan1, phase1, 1);
        events.add(event1);

        BigDecimal rate2 = TEN;
        PlanPhase phase2 = createMockMonthlyPlanPhase(rate2);
        BillingEvent event2 = createBillingEvent(sub.getId(), buildDateTime(2011, 10, 1), plan1, phase2, 1);
        events.add(event2);

        BigDecimal rate3 = THIRTY;
        PlanPhase phase3 = createMockMonthlyPlanPhase(rate3);
        BillingEvent event3 = createBillingEvent(sub.getId(), buildDateTime(2011, 11, 1), plan1, phase3, 1);
        events.add(event3);

        DateTime targetDate = buildDateTime(2011, 12, 3);
        UUID accountId = UUID.randomUUID();
        Invoice invoice = generator.generateInvoice(accountId, events, null, targetDate, Currency.USD);

        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 4);
        assertEquals(invoice.getTotalAmount(), rate1.add(rate2).add(TWO.multiply(rate3)).setScale(NUMBER_OF_DECIMALS));
    }

    @Test
    public void testSingleEventWithExistingInvoice() throws InvoiceApiException, CatalogApiException {
        BillingEventSet events = new BillingEventSet();

        Subscription sub = new SubscriptionData(new SubscriptionBuilder().setId(UUID.randomUUID()));
        DateTime startDate = buildDateTime(2011, 9, 1);

        Plan plan1 = new MockPlan();
        BigDecimal rate = FIVE;
        PlanPhase phase1 = createMockMonthlyPlanPhase(rate);

        BillingEvent event1 = createBillingEvent(sub.getId(), startDate, plan1, phase1, 1);
        events.add(event1);

        DateTime targetDate = buildDateTime(2011, 12, 1);
        UUID accountId = UUID.randomUUID();
        Invoice invoice1 = generator.generateInvoice(accountId, events, null, targetDate, Currency.USD);
        List<Invoice> existingInvoices = new ArrayList<Invoice>();
        existingInvoices.add(invoice1);

        targetDate = buildDateTime(2011, 12, 3);
        Invoice invoice2 = generator.generateInvoice(accountId, events, existingInvoices, targetDate, Currency.USD);

        assertNull(invoice2);
    }

    @Test
    public void testMultiplePlansWithUtterChaos() throws InvoiceApiException, CatalogApiException {
        // plan 1: change of phase from trial to discount followed by immediate cancellation; (covers phase change, cancel, pro-ration)
        // plan 2: single plan that moves from trial to discount to evergreen; BCD = 10 (covers phase change)
        // plan 3: change of term from monthly (BCD = 20) to annual (BCD = 31; immediate)
        // plan 4: change of plan, effective EOT, BCD = 7 (covers change of plan)
        // plan 5: addon to plan 2, with bill cycle alignment to plan; immediate cancellation
        UUID accountId = UUID.randomUUID();
        UUID subscriptionId1 = UUID.randomUUID();
        UUID subscriptionId2 = UUID.randomUUID();
        UUID subscriptionId3 = UUID.randomUUID();
        UUID subscriptionId4 = UUID.randomUUID();
        UUID subscriptionId5 = UUID.randomUUID();

        Plan plan1 = new MockPlan("Change from trial to discount with immediate cancellation");
        PlanPhase plan1Phase1 = createMockMonthlyPlanPhase(EIGHT, PhaseType.TRIAL);
        PlanPhase plan1Phase2 = createMockMonthlyPlanPhase(TWELVE, PhaseType.DISCOUNT);
        PlanPhase plan1Phase3 = createMockMonthlyPlanPhase();
        DateTime plan1StartDate = buildDateTime(2011, 1, 5);
        DateTime plan1PhaseChangeDate = buildDateTime(2011, 4, 5);
        DateTime plan1CancelDate = buildDateTime(2011, 4, 29);

        Plan plan2 = new MockPlan("Change phase from trial to discount to evergreen");
        PlanPhase plan2Phase1 = createMockMonthlyPlanPhase(TWENTY, PhaseType.TRIAL);
        PlanPhase plan2Phase2 = createMockMonthlyPlanPhase(THIRTY, PhaseType.DISCOUNT);
        PlanPhase plan2Phase3 = createMockMonthlyPlanPhase(FORTY, PhaseType.EVERGREEN);
        DateTime plan2StartDate = buildDateTime(2011, 3, 10);
        DateTime plan2PhaseChangeToDiscountDate = buildDateTime(2011, 6, 10);
        DateTime plan2PhaseChangeToEvergreenDate = buildDateTime(2011, 9, 10);

        Plan plan3 = new MockPlan("Upgrade with immediate change, BCD = 31");
        PlanPhase plan3Phase1 = createMockMonthlyPlanPhase(TEN, PhaseType.EVERGREEN);
        PlanPhase plan3Phase2 = createMockAnnualPlanPhase(ONE_HUNDRED, PhaseType.EVERGREEN);
        DateTime plan3StartDate = buildDateTime(2011, 5, 20);
        DateTime plan3UpgradeToAnnualDate = buildDateTime(2011, 7, 31);

        Plan plan4a = new MockPlan("Plan change effective EOT; plan 1");
        Plan plan4b = new MockPlan("Plan change effective EOT; plan 2");
        PlanPhase plan4aPhase1 = createMockMonthlyPlanPhase(FIFTEEN);
        PlanPhase plan4bPhase1 = createMockMonthlyPlanPhase(TWENTY_FOUR);

        DateTime plan4StartDate = buildDateTime(2011, 6, 7);
        DateTime plan4ChangeOfPlanDate = buildDateTime(2011, 8, 7);

        Plan plan5 = new MockPlan("Add-on");
        PlanPhase plan5Phase1 = createMockMonthlyPlanPhase(TWENTY);
        PlanPhase plan5Phase2 = createMockMonthlyPlanPhase();
        DateTime plan5StartDate = buildDateTime(2011, 6, 21);
        DateTime plan5CancelDate = buildDateTime(2011, 10, 7);

        BigDecimal expectedAmount;
        List<Invoice> invoices = new ArrayList<Invoice>();
        BillingEventSet events = new BillingEventSet();

        // on 1/5/2011, create subscription 1 (trial)
        events.add(createBillingEvent(subscriptionId1, plan1StartDate, plan1, plan1Phase1, 5));
        expectedAmount = EIGHT;
        testInvoiceGeneration(accountId, events, invoices, plan1StartDate, 1, expectedAmount);

        // on 2/5/2011, invoice subscription 1 (trial)
        expectedAmount = EIGHT;
        testInvoiceGeneration(accountId, events, invoices, buildDateTime(2011, 2, 5) , 1, expectedAmount);

        // on 3/5/2011, invoice subscription 1 (trial)
        expectedAmount = EIGHT;
        testInvoiceGeneration(accountId, events, invoices, buildDateTime(2011, 3, 5), 1, expectedAmount);

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
        testInvoiceGeneration(accountId, events, invoices, buildDateTime(2011, 4, 10), 1, expectedAmount);

        // on 4/29/2011, cancel subscription 1
        events.add(createBillingEvent(subscriptionId1, plan1CancelDate, plan1, plan1Phase3, 5));
        expectedAmount = TWELVE.multiply(SIX.divide(THIRTY, NUMBER_OF_DECIMALS, ROUNDING_METHOD)).negate().setScale(NUMBER_OF_DECIMALS);
        testInvoiceGeneration(accountId, events, invoices, plan1CancelDate, 2, expectedAmount);

        // on 5/10/2011, invoice subscription 2 (trial)
        expectedAmount = TWENTY;
        testInvoiceGeneration(accountId, events, invoices, buildDateTime(2011, 5, 10), 1, expectedAmount);

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
        testInvoiceGeneration(accountId, events, invoices, buildDateTime(2011, 6, 20), 1, expectedAmount);

        // on 6/21/2011, create add-on (subscription 5)
        events.add(createBillingEvent(subscriptionId5, plan5StartDate, plan5, plan5Phase1, 10));
        expectedAmount = TWENTY.multiply(NINETEEN).divide(THIRTY, NUMBER_OF_DECIMALS, ROUNDING_METHOD);
        testInvoiceGeneration(accountId, events, invoices, plan5StartDate, 1, expectedAmount);

        // on 7/7/2011, invoice subscription 4 (plan 1)
        expectedAmount = FIFTEEN;
        testInvoiceGeneration(accountId, events, invoices, buildDateTime(2011, 7, 7), 1, expectedAmount);

        // on 7/10/2011, invoice subscription 2 (discount), invoice subscription 5
        expectedAmount = THIRTY.add(TWENTY);
        testInvoiceGeneration(accountId, events, invoices, buildDateTime(2011, 7, 10), 2, expectedAmount);

        // on 7/20/2011, invoice subscription 3 (monthly)
        expectedAmount = TEN;
        testInvoiceGeneration(accountId, events, invoices, buildDateTime(2011, 7, 20), 1, expectedAmount);

        // on 7/31/2011, convert subscription 3 to annual
        events.add(createBillingEvent(subscriptionId3, plan3UpgradeToAnnualDate, plan3, plan3Phase2, 31));
        expectedAmount = ONE_HUNDRED.subtract(TEN);
        expectedAmount = expectedAmount.add(TEN.multiply(ELEVEN.divide(THIRTY_ONE, 2 * NUMBER_OF_DECIMALS, ROUNDING_METHOD)));
        expectedAmount = expectedAmount.setScale(NUMBER_OF_DECIMALS, ROUNDING_METHOD);
        testInvoiceGeneration(accountId, events, invoices, plan3UpgradeToAnnualDate, 3, expectedAmount);

        // on 8/7/2011, invoice subscription 4 (plan 2)
        events.add(createBillingEvent(subscriptionId4, plan4ChangeOfPlanDate, plan4b, plan4bPhase1, 7));
        expectedAmount = TWENTY_FOUR;
        testInvoiceGeneration(accountId, events, invoices, plan4ChangeOfPlanDate, 1, expectedAmount);

        // on 8/10/2011, invoice plan 2 (discount), invoice subscription 5
        expectedAmount = THIRTY.add(TWENTY);
        testInvoiceGeneration(accountId, events, invoices, buildDateTime(2011, 8, 10), 2, expectedAmount);

        // on 9/7/2011, invoice subscription 4 (plan 2)
        expectedAmount = TWENTY_FOUR;
        testInvoiceGeneration(accountId, events, invoices, buildDateTime(2011, 9, 7), 1, expectedAmount);

        // on 9/10/2011, invoice plan 2 (evergreen), invoice subscription 5
        events.add(createBillingEvent(subscriptionId2, plan2PhaseChangeToEvergreenDate, plan2, plan2Phase3, 10));
        expectedAmount = FORTY.add(TWENTY);
        testInvoiceGeneration(accountId, events, invoices, plan2PhaseChangeToEvergreenDate, 2, expectedAmount);

        // on 10/7/2011, invoice subscription 4 (plan 2), cancel subscription 5
        events.add(createBillingEvent(subscriptionId5, plan5CancelDate, plan5, plan5Phase2, 10));
        expectedAmount = TWENTY_FOUR.add(TWENTY.multiply(THREE.divide(THIRTY)).negate().setScale(NUMBER_OF_DECIMALS));
        testInvoiceGeneration(accountId, events, invoices, plan5CancelDate, 3, expectedAmount);

        // on 10/10/2011, invoice plan 2 (evergreen)
        expectedAmount = FORTY ;
        testInvoiceGeneration(accountId, events, invoices, buildDateTime(2011, 10, 10), 1, expectedAmount);
    }

    @Test
    public void testZeroDollarEvents() throws InvoiceApiException, CatalogApiException {
        Plan plan = new MockPlan();
        PlanPhase planPhase = createMockMonthlyPlanPhase(ZERO);
        BillingEventSet events = new BillingEventSet();
        DateTime targetDate = buildDateTime(2011, 1, 1);
        events.add(createBillingEvent(UUID.randomUUID(), targetDate, plan, planPhase, 1));

        Invoice invoice = generator.generateInvoice(UUID.randomUUID(), events, null, targetDate, Currency.USD);

        assertEquals(invoice.getNumberOfItems(), 1);
    }

    @Test
    public void testEndDateIsCorrect() throws InvoiceApiException, CatalogApiException {
        Plan plan = new MockPlan();
        PlanPhase planPhase = createMockMonthlyPlanPhase(ZERO);
        BillingEventSet events = new BillingEventSet();
        DateTime targetDate = new DateTime();
        events.add(createBillingEvent(UUID.randomUUID(), targetDate, plan, planPhase, targetDate.getDayOfMonth()));

        Invoice invoice = generator.generateInvoice(UUID.randomUUID(), events, null, targetDate, Currency.USD);
        RecurringInvoiceItem item = (RecurringInvoiceItem) invoice.getInvoiceItems().get(0);

        // end date of the invoice item should be equal to exactly one month later
        assertEquals(item.getEndDate().compareTo(targetDate.plusMonths(1)), 0);
    }

    @Test
    public void testFixedPriceLifeCycle() throws InvoiceApiException {
        UUID accountId = UUID.randomUUID();
        Subscription subscription = BrainDeadProxyFactory.createBrainDeadProxyFor(Subscription.class);
        ((ZombieControl) subscription).addResult("getId", UUID.randomUUID());
        ((ZombieControl) subscription).addResult("getBundleId", UUID.randomUUID());

        Plan plan = new MockPlan("plan 1");
        MockInternationalPrice zeroPrice = new MockInternationalPrice(new DefaultPrice(ZERO, Currency.USD));
        MockInternationalPrice cheapPrice = new MockInternationalPrice(new DefaultPrice(ONE, Currency.USD));

        PlanPhase phase1 = new MockPlanPhase(null, zeroPrice, BillingPeriod.NO_BILLING_PERIOD, PhaseType.TRIAL);
        PlanPhase phase2 = new MockPlanPhase(cheapPrice, null, BillingPeriod.MONTHLY, PhaseType.DISCOUNT);

        DateTime changeDate = new DateTime("2012-04-1T00:00:00.000-08:00");

        BillingEventSet events = new BillingEventSet();

        BillingEvent event1 = new DefaultBillingEvent(null, subscription, new DateTime("2012-01-1T00:00:00.000-08:00"),
                                                      plan, phase1,
                                                      ZERO, null, Currency.USD, BillingPeriod.NO_BILLING_PERIOD, 1,
                                                      BillingModeType.IN_ADVANCE, "Test Event 1", 1L,
                                                      SubscriptionTransitionType.CREATE);

        BillingEvent event2 = new DefaultBillingEvent(null, subscription, changeDate,
                                                      plan, phase2,
                                                      ZERO, null, Currency.USD, BillingPeriod.NO_BILLING_PERIOD, 1,
                                                      BillingModeType.IN_ADVANCE, "Test Event 2", 2L,
                                                      SubscriptionTransitionType.PHASE);

        events.add(event2);
        events.add(event1);
        Invoice invoice1 = generator.generateInvoice(accountId, events, null, new DateTime("2012-02-01T00:01:00.000-08:00"), Currency.USD);
        assertNotNull(invoice1);
        assertEquals(invoice1.getNumberOfItems(), 1);

        List<Invoice> invoiceList = new ArrayList<Invoice>();
        invoiceList.add(invoice1);
        Invoice invoice2 = generator.generateInvoice(accountId, events, invoiceList, new DateTime("2012-04-05T00:01:00.000-08:00"), Currency.USD);
        assertNotNull(invoice2);
        assertEquals(invoice2.getNumberOfItems(), 1);
        FixedPriceInvoiceItem item = (FixedPriceInvoiceItem) invoice2.getInvoiceItems().get(0);
        assertEquals(item.getStartDate().compareTo(changeDate), 0);
   }

    @Test
    public void testMixedModeLifeCycle() throws InvoiceApiException, CatalogApiException {
        // create a subscription with a fixed price and recurring price
        Plan plan1 = new MockPlan();
        BigDecimal monthlyRate = FIVE;
        BigDecimal fixedCost = TEN;
        PlanPhase phase1 = createMockMonthlyPlanPhase(monthlyRate, fixedCost, PhaseType.TRIAL);

        BillingEventSet events = new BillingEventSet();
        UUID subscriptionId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        DateTime startDate = new DateTime(2011, 1, 1, 3, 40, 27, 0);
        BillingEvent event1 = createBillingEvent(subscriptionId, startDate, plan1, phase1, 1);
        events.add(event1);

        // ensure both components are invoiced
        Invoice invoice1 = generator.generateInvoice(accountId, events, null, startDate, Currency.USD);
        assertNotNull(invoice1);
        assertEquals(invoice1.getNumberOfItems(), 2);
        assertEquals(invoice1.getTotalAmount(), FIFTEEN);

        List<Invoice> invoiceList = new ArrayList<Invoice>();
        invoiceList.add(invoice1);

        // move forward in time one billing period
        DateTime currentDate = startDate.plusMonths(1);

        // ensure that only the recurring price is invoiced
        Invoice invoice2 = generator.generateInvoice(accountId, events, invoiceList, currentDate, Currency.USD);
        assertNotNull(invoice2);
        assertEquals(invoice2.getNumberOfItems(), 1);
        assertEquals(invoice2.getTotalAmount(), FIVE);
    }

    @Test
    public void testFixedModePlanChange() throws InvoiceApiException, CatalogApiException {
        // create a subscription with a fixed price and recurring price
        Plan plan1 = new MockPlan();
        BigDecimal fixedCost1 = TEN;
        BigDecimal fixedCost2 = TWENTY;
        PlanPhase phase1 = createMockMonthlyPlanPhase(null, fixedCost1, PhaseType.TRIAL);
        PlanPhase phase2 = createMockMonthlyPlanPhase(null, fixedCost2, PhaseType.EVERGREEN);

        BillingEventSet events = new BillingEventSet();
        UUID subscriptionId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        DateTime startDate = new DateTime(2011, 1, 1, 3, 40, 27, 0);
        BillingEvent event1 = createBillingEvent(subscriptionId, startDate, plan1, phase1, 1);
        events.add(event1);

        // ensure that a single invoice item is generated for the fixed cost
        Invoice invoice1 = generator.generateInvoice(accountId, events, null, startDate, Currency.USD);
        assertNotNull(invoice1);
        assertEquals(invoice1.getNumberOfItems(), 1);
        assertEquals(invoice1.getTotalAmount(), fixedCost1);

        List<Invoice> invoiceList = new ArrayList<Invoice>();
        invoiceList.add(invoice1);

        // move forward in time one billing period
        DateTime phaseChangeDate = startDate.plusMonths(1);
        BillingEvent event2 = createBillingEvent(subscriptionId, phaseChangeDate, plan1, phase2, 1);
        events.add(event2);

        // ensure that a single invoice item is generated for the fixed cost
        Invoice invoice2 = generator.generateInvoice(accountId, events, invoiceList, phaseChangeDate, Currency.USD);
        assertNotNull(invoice2);
        assertEquals(invoice2.getNumberOfItems(), 1);
        assertEquals(invoice2.getTotalAmount(), fixedCost2);
    }

    @Test
    public void testNutsFailure() throws InvoiceApiException, CatalogApiException {
        BillingEventSet events = new BillingEventSet();
        UUID subscriptionId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        final int BILL_CYCLE_DAY = 15;

        // create subscription with a zero-dollar trial, a monthly discount period and a monthly evergreen
        Plan plan1 = new MockPlan();
        PlanPhase phase1 = createMockMonthlyPlanPhase(null, ZERO, PhaseType.TRIAL);
        final BigDecimal DISCOUNT_PRICE = new BigDecimal("9.95");
        PlanPhase phase2 = createMockMonthlyPlanPhase(DISCOUNT_PRICE, null, PhaseType.DISCOUNT);
        PlanPhase phase3 = createMockMonthlyPlanPhase(new BigDecimal("19.95"), null, PhaseType.EVERGREEN);

        // set up billing events
        DateTime creationDate = new DateTime(2012, 3, 6, 21, 36, 18, 896);
        events.add(createBillingEvent(subscriptionId, creationDate, plan1, phase1, BILL_CYCLE_DAY));

        // trialPhaseEndDate = 2012/4/5
        DateTime trialPhaseEndDate = creationDate.plusDays(30);
        events.add(createBillingEvent(subscriptionId, trialPhaseEndDate, plan1, phase2, BILL_CYCLE_DAY));

        // discountPhaseEndDate = 2012/10/5
        DateTime discountPhaseEndDate = trialPhaseEndDate.plusMonths(6);
        events.add(createBillingEvent(subscriptionId, discountPhaseEndDate, plan1, phase3, BILL_CYCLE_DAY));

        Invoice invoice1 = generator.generateInvoice(accountId, events, null, creationDate, Currency.USD);
        assertNotNull(invoice1);
        assertEquals(invoice1.getNumberOfItems(), 1);
        assertEquals(invoice1.getTotalAmount().compareTo(ZERO), 0);

        List<Invoice> invoiceList = new ArrayList<Invoice>();
        invoiceList.add(invoice1);

        Invoice invoice2 = generator.generateInvoice(accountId, events, invoiceList, trialPhaseEndDate, Currency.USD);
        assertNotNull(invoice2);
        assertEquals(invoice2.getNumberOfItems(), 1);
        assertEquals(invoice2.getInvoiceItems().get(0).getStartDate().compareTo(trialPhaseEndDate), 0);
        assertEquals(invoice2.getTotalAmount().compareTo(new BigDecimal("3.2097")), 0);

        invoiceList.add(invoice2);
        DateTime targetDate = trialPhaseEndDate.toMutableDateTime().dayOfMonth().set(BILL_CYCLE_DAY).toDateTime();
        Invoice invoice3 = generator.generateInvoice(accountId, events, invoiceList, targetDate, Currency.USD);
        assertNotNull(invoice3);
        assertEquals(invoice3.getNumberOfItems(), 1);
        assertEquals(invoice3.getInvoiceItems().get(0).getStartDate().compareTo(targetDate), 0);
        assertEquals(invoice3.getTotalAmount().compareTo(DISCOUNT_PRICE), 0);

        invoiceList.add(invoice3);
        targetDate = targetDate.plusMonths(6);
        Invoice invoice4 = generator.generateInvoice(accountId, events, invoiceList, targetDate, Currency.USD);
        assertNotNull(invoice4);
        assertEquals(invoice4.getNumberOfItems(), 7);
    }

    @Test(expectedExceptions = {InvoiceApiException.class})
    public void testTargetDateRestrictionFailure() throws InvoiceApiException, CatalogApiException {
        DateTime targetDate = DateTime.now().plusMonths(60);
        BillingEventSet events = new BillingEventSet();
        Plan plan1 = new MockPlan();
        PlanPhase phase1 = createMockMonthlyPlanPhase(null, ZERO, PhaseType.TRIAL);
        events.add(createBillingEvent(UUID.randomUUID(), DateTime.now(), plan1, phase1, 1));
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

    private MockPlanPhase createMockMonthlyPlanPhase(@Nullable BigDecimal recurringRate,
                                                     @Nullable final BigDecimal fixedCost,
                                                     final PhaseType phaseType) {
        MockInternationalPrice recurringPrice = (recurringRate == null) ? null : new MockInternationalPrice(new DefaultPrice(recurringRate, Currency.USD));
        MockInternationalPrice fixedPrice = (fixedCost == null) ? null : new MockInternationalPrice(new DefaultPrice(fixedCost, Currency.USD));

        return new MockPlanPhase(recurringPrice, fixedPrice, BillingPeriod.MONTHLY, phaseType);
    }

    private MockPlanPhase createMockAnnualPlanPhase(final BigDecimal recurringRate, final PhaseType phaseType) {
        return new MockPlanPhase(new MockInternationalPrice(new DefaultPrice(recurringRate, Currency.USD)),
                                 null, BillingPeriod.ANNUAL, phaseType);
    }

    private DefaultBillingEvent createBillingEvent(final UUID subscriptionId, final DateTime startDate,
                                                   final Plan plan, final PlanPhase planPhase, final int billCycleDay) throws CatalogApiException {
        Subscription sub = new SubscriptionData(new SubscriptionBuilder().setId(subscriptionId));
        Currency currency = Currency.USD;

        return new DefaultBillingEvent(null, sub, startDate, plan, planPhase,
                                       planPhase.getFixedPrice() == null ? null : planPhase.getFixedPrice().getPrice(currency),
                                       planPhase.getRecurringPrice() == null ? null : planPhase.getRecurringPrice().getPrice(currency),
                                       currency, planPhase.getBillingPeriod(),
                                       billCycleDay, BillingModeType.IN_ADVANCE, "Test", 1L, SubscriptionTransitionType.CREATE);
    }

    private void testInvoiceGeneration(final UUID accountId, final BillingEventSet events, final List<Invoice> existingInvoices,
                                       final DateTime targetDate, final int expectedNumberOfItems,
                                       final BigDecimal expectedAmount) throws InvoiceApiException {
        Currency currency = Currency.USD;
        Invoice invoice = generator.generateInvoice(accountId, events, existingInvoices, targetDate, currency);
        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), expectedNumberOfItems);

        existingInvoices.add(invoice);
        assertEquals(invoice.getTotalAmount(), expectedAmount);
    }

    // TODO: Jeff C -- how do we ensure that an annual add-on is properly aligned *at the end* with the base plan?
}