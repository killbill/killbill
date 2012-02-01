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
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.InternationalPrice;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.entitlement.api.billing.BillingEvent;
import com.ning.billing.entitlement.api.billing.BillingModeType;
import com.ning.billing.entitlement.api.billing.DefaultBillingEvent;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.api.user.SubscriptionFactory.SubscriptionBuilder;
import com.ning.billing.entitlement.api.user.SubscriptionTransition.SubscriptionTransitionType;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.dao.MockSubscription;
import com.ning.billing.invoice.model.BillingEventSet;
import com.ning.billing.invoice.model.DefaultInvoiceGenerator;
import com.ning.billing.invoice.model.DefaultInvoiceItem;
import com.ning.billing.invoice.model.InvoiceGenerator;
import com.ning.billing.invoice.model.InvoiceItemList;
import org.joda.time.DateTime;
import org.testng.annotations.Test;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.util.UUID;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

@Test(groups = {"fast", "invoicing", "invoiceGenerator"})
public class DefaultInvoiceGeneratorTests extends InvoicingTestBase {
    private final InvoiceGenerator generator = new DefaultInvoiceGenerator();

    @Test
    public void testWithNullEventSetAndNullInvoiceSet() {
        UUID accountId = UUID.randomUUID();
        Invoice invoice = generator.generateInvoice(accountId, new BillingEventSet(), new InvoiceItemList(), new DateTime(), Currency.USD);

        assertNull(invoice);
    }

    @Test
    public void testWithEmptyEventSet() {
        BillingEventSet events = new BillingEventSet();

        InvoiceItemList existingInvoiceItems = new InvoiceItemList();
        UUID accountId = UUID.randomUUID();
        Invoice invoice = generator.generateInvoice(accountId, events, existingInvoiceItems, new DateTime(), Currency.USD);

        assertNull(invoice);
    }

    @Test
    public void testWithSingleMonthlyEvent() {
        BillingEventSet events = new BillingEventSet();

        Subscription sub = new SubscriptionData(new SubscriptionBuilder().setId(UUID.randomUUID()));
        DateTime startDate = buildDateTime(2011, 9, 1);

        Plan plan = new MockPlan();
        BigDecimal rate1 = TEN;
        PlanPhase phase = createMockMonthlyPlanPhase(rate1);
        
        BillingEvent event = createBillingEvent(sub.getId(), startDate, plan, phase, 1);
        events.add(event);

        InvoiceItemList existingInvoiceItems = new InvoiceItemList();
        
        DateTime targetDate = buildDateTime(2011, 10, 3);
        UUID accountId = UUID.randomUUID();
        Invoice invoice = generator.generateInvoice(accountId, events, existingInvoiceItems, targetDate, Currency.USD);

        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 1);
        assertEquals(invoice.getTotalAmount(), TWENTY);
        assertEquals(invoice.getInvoiceItems().get(0).getSubscriptionId(), sub.getId());
    }

    @Test
    public void testWithSingleMonthlyEventWithLeadingProRation() {
        BillingEventSet events = new BillingEventSet();

        Subscription sub = new SubscriptionData(new SubscriptionBuilder().setId(UUID.randomUUID()));
        DateTime startDate = buildDateTime(2011, 9, 1);

        Plan plan = new MockPlan();
        BigDecimal rate = TEN;
        PlanPhase phase = createMockMonthlyPlanPhase(rate);
        BillingEvent event = createBillingEvent(sub.getId(), startDate, plan, phase, 15);
        events.add(event);

        InvoiceItemList existingInvoiceItems = new InvoiceItemList();
        
        DateTime targetDate = buildDateTime(2011, 10, 3);        
        UUID accountId = UUID.randomUUID();
        Invoice invoice = generator.generateInvoice(accountId, events, existingInvoiceItems, targetDate, Currency.USD);

        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 1);

        BigDecimal expectedNumberOfBillingCycles;
        expectedNumberOfBillingCycles = ONE.add(FOURTEEN.divide(THIRTY_ONE, NUMBER_OF_DECIMALS, ROUNDING_METHOD));
        BigDecimal expectedAmount = expectedNumberOfBillingCycles.multiply(rate).setScale(NUMBER_OF_DECIMALS);
        assertEquals(invoice.getTotalAmount(), expectedAmount);
    }

    @Test
    public void testTwoMonthlySubscriptionsWithAlignedBillingDates() {
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

        InvoiceItemList existingInvoiceItems = new InvoiceItemList();
        DateTime targetDate = buildDateTime(2011, 10, 3);
        UUID accountId = UUID.randomUUID();
        Invoice invoice = generator.generateInvoice(accountId, events, existingInvoiceItems, targetDate, Currency.USD);

        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 2);
        assertEquals(invoice.getTotalAmount(), rate1.add(rate2).setScale(NUMBER_OF_DECIMALS));
    }

    @Test
    public void testOnePlan_TwoMonthlyPhases_ChangeImmediate() {
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

        InvoiceItemList existingInvoiceItems = new InvoiceItemList();
        DateTime targetDate = buildDateTime(2011, 12, 3);
        UUID accountId = UUID.randomUUID();
        Invoice invoice = generator.generateInvoice(accountId, events, existingInvoiceItems, targetDate, Currency.USD);

        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 2);

        BigDecimal numberOfCyclesEvent1;
        numberOfCyclesEvent1 = ONE.add(FOURTEEN.divide(THIRTY_ONE, NUMBER_OF_DECIMALS, ROUNDING_METHOD));

        BigDecimal numberOfCyclesEvent2 = TWO;

        BigDecimal expectedValue;
        expectedValue = numberOfCyclesEvent1.multiply(rate1);
        expectedValue = expectedValue.add(numberOfCyclesEvent2.multiply(rate2));
        expectedValue = expectedValue.setScale(NUMBER_OF_DECIMALS);

        assertEquals(invoice.getTotalAmount(), expectedValue);
    }

    @Test
    public void testOnePlan_ThreeMonthlyPhases_ChangeEOT() {
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

        InvoiceItemList existingInvoiceItems = new InvoiceItemList();
        DateTime targetDate = buildDateTime(2011, 12, 3);
        UUID accountId = UUID.randomUUID();
        Invoice invoice = generator.generateInvoice(accountId, events, existingInvoiceItems, targetDate, Currency.USD);

        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 3);
        assertEquals(invoice.getTotalAmount(), rate1.add(rate2).add(TWO.multiply(rate3)).setScale(NUMBER_OF_DECIMALS));
    }

    @Test
    public void testSingleEventWithExistingInvoice() {
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
        InvoiceItemList existingInvoiceItems = new InvoiceItemList();
        existingInvoiceItems.addAll(invoice1.getInvoiceItems());

        targetDate = buildDateTime(2011, 12, 3);
        Invoice invoice2 = generator.generateInvoice(accountId, events, existingInvoiceItems, targetDate, Currency.USD);

        assertNull(invoice2);
    }

    @Test
    public void testMultiplePlansWithUtterChaos() {
        // plan 1: change of phase from trial to discount followed by immediate cancellation; (covers phase change, cancel, pro-ration)
        // plan 2: single plan that moves from trial to discount to evergreen; BCD = 10 (covers phase change)
        // plan 3: change of term from monthly (BCD = 20) to annual (BCD = 31; immediate)
        // plan 4: change of plan, effective EOT, BCD = 7 (covers change of plan)
        // plan 5: addon to plan 2, with bill cycle alignment to plan; immediate cancellation

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
        InvoiceItemList invoiceItems = new InvoiceItemList();
        BillingEventSet events = new BillingEventSet();

        // on 1/5/2011, create subscription 1 (trial)
        events.add(createBillingEvent(subscriptionId1, plan1StartDate, plan1, plan1Phase1, 5));
        expectedAmount = EIGHT;
        testInvoiceGeneration(events, invoiceItems, plan1StartDate, 1, expectedAmount);

        // on 2/5/2011, invoice subscription 1 (trial)
        expectedAmount = EIGHT;
        testInvoiceGeneration(events, invoiceItems, buildDateTime(2011, 2, 5) , 1, expectedAmount);

        // on 3/5/2011, invoice subscription 1 (trial)
        expectedAmount = EIGHT;
        testInvoiceGeneration(events, invoiceItems, buildDateTime(2011, 3, 5), 1, expectedAmount);

        // on 3/10/2011, create subscription 2 (trial)
        events.add(createBillingEvent(subscriptionId2, plan2StartDate, plan2, plan2Phase1, 10));
        expectedAmount = TWENTY;
        testInvoiceGeneration(events, invoiceItems, plan2StartDate, 1, expectedAmount);

        // on 4/5/2011, invoice subscription 1 (discount)
        events.add(createBillingEvent(subscriptionId1, plan1PhaseChangeDate, plan1, plan1Phase2, 5));
        expectedAmount = TWELVE;
        testInvoiceGeneration(events, invoiceItems, plan1PhaseChangeDate, 1, expectedAmount);

        // on 4/10/2011, invoice subscription 2 (trial)
        expectedAmount = TWENTY;
        testInvoiceGeneration(events, invoiceItems, buildDateTime(2011, 4, 10), 1, expectedAmount);

        // on 4/29/2011, cancel subscription 1
        events.add(createBillingEvent(subscriptionId1, plan1CancelDate, plan1, plan1Phase3, 5));
        expectedAmount = TWELVE.multiply(SIX.divide(THIRTY, NUMBER_OF_DECIMALS, ROUNDING_METHOD)).negate().setScale(NUMBER_OF_DECIMALS);
        testInvoiceGeneration(events, invoiceItems, plan1CancelDate, 2, expectedAmount);

        // on 5/10/2011, invoice subscription 2 (trial)
        expectedAmount = TWENTY;
        testInvoiceGeneration(events, invoiceItems, buildDateTime(2011, 5, 10), 1, expectedAmount);

        // on 5/20/2011, create subscription 3 (monthly)
        events.add(createBillingEvent(subscriptionId3, plan3StartDate, plan3, plan3Phase1, 20));
        expectedAmount = TEN;
        testInvoiceGeneration(events, invoiceItems, plan3StartDate, 1, expectedAmount);

        // on 6/7/2011, create subscription 4
        events.add(createBillingEvent(subscriptionId4, plan4StartDate, plan4a, plan4aPhase1, 7));
        expectedAmount = FIFTEEN;
        testInvoiceGeneration(events, invoiceItems, plan4StartDate, 1, expectedAmount);

        // on 6/10/2011, invoice subscription 2 (discount)
        events.add(createBillingEvent(subscriptionId2, plan2PhaseChangeToDiscountDate, plan2, plan2Phase2, 10));
        expectedAmount = THIRTY;
        testInvoiceGeneration(events, invoiceItems, plan2PhaseChangeToDiscountDate, 1, expectedAmount);

        // on 6/20/2011, invoice subscription 3 (monthly)
        expectedAmount = TEN;
        testInvoiceGeneration(events, invoiceItems, buildDateTime(2011, 6, 20), 1, expectedAmount);

        // on 6/21/2011, create add-on (subscription 5)
        events.add(createBillingEvent(subscriptionId5, plan5StartDate, plan5, plan5Phase1, 10));
        expectedAmount = TWENTY.multiply(NINETEEN.divide(THIRTY, NUMBER_OF_DECIMALS, ROUNDING_METHOD)).setScale(NUMBER_OF_DECIMALS);
        testInvoiceGeneration(events, invoiceItems, plan5StartDate, 1, expectedAmount);

        // on 7/7/2011, invoice subscription 4 (plan 1)
        expectedAmount = FIFTEEN;
        testInvoiceGeneration(events, invoiceItems, buildDateTime(2011, 7, 7), 1, expectedAmount);

        // on 7/10/2011, invoice subscription 2 (discount), invoice subscription 5
        expectedAmount = THIRTY.add(TWENTY);
        testInvoiceGeneration(events, invoiceItems, buildDateTime(2011, 7, 10), 2, expectedAmount);

        // on 7/20/2011, invoice subscription 3 (monthly)
        expectedAmount = TEN;
        testInvoiceGeneration(events, invoiceItems, buildDateTime(2011, 7, 20), 1, expectedAmount);

        // on 7/31/2011, convert subscription 3 to annual
        events.add(createBillingEvent(subscriptionId3, plan3UpgradeToAnnualDate, plan3, plan3Phase2, 31));
        expectedAmount = ONE_HUNDRED.subtract(TEN);
        expectedAmount = expectedAmount.add(TEN.multiply(ELEVEN.divide(THIRTY_ONE, NUMBER_OF_DECIMALS, ROUNDING_METHOD)));
        expectedAmount = expectedAmount.setScale(NUMBER_OF_DECIMALS);
        testInvoiceGeneration(events, invoiceItems, plan3UpgradeToAnnualDate, 3, expectedAmount);

        // on 8/7/2011, invoice subscription 4 (plan 2)
        events.add(createBillingEvent(subscriptionId4, plan4ChangeOfPlanDate, plan4b, plan4bPhase1, 7));
        expectedAmount = TWENTY_FOUR;
        testInvoiceGeneration(events, invoiceItems, plan4ChangeOfPlanDate, 1, expectedAmount);

        // on 8/10/2011, invoice plan 2 (discount), invoice subscription 5
        expectedAmount = THIRTY.add(TWENTY);
        testInvoiceGeneration(events, invoiceItems, buildDateTime(2011, 8, 10), 2, expectedAmount);

        // on 9/7/2011, invoice subscription 4 (plan 2)
        expectedAmount = TWENTY_FOUR;
        testInvoiceGeneration(events, invoiceItems, buildDateTime(2011, 9, 7), 1, expectedAmount);

        // on 9/10/2011, invoice plan 2 (evergreen), invoice subscription 5
        events.add(createBillingEvent(subscriptionId2, plan2PhaseChangeToEvergreenDate, plan2, plan2Phase3, 10));
        expectedAmount = FORTY.add(TWENTY);
        testInvoiceGeneration(events, invoiceItems, plan2PhaseChangeToEvergreenDate, 2, expectedAmount);

        // on 10/7/2011, invoice subscription 4 (plan 2), cancel subscription 5
        events.add(createBillingEvent(subscriptionId5, plan5CancelDate, plan5, plan5Phase2, 10));
        expectedAmount = TWENTY_FOUR.add(TWENTY.multiply(THREE.divide(THIRTY)).negate().setScale(NUMBER_OF_DECIMALS));
        testInvoiceGeneration(events, invoiceItems, plan5CancelDate, 3, expectedAmount);

        // on 10/10/2011, invoice plan 2 (evergreen)
        expectedAmount = FORTY ;
        testInvoiceGeneration(events, invoiceItems, buildDateTime(2011, 10, 10), 1, expectedAmount);
    }

    @Test
    public void testZeroDollarEvents() {
        Plan plan = new MockPlan();
        PlanPhase planPhase = createMockMonthlyPlanPhase(ZERO);
        BillingEventSet events = new BillingEventSet();
        DateTime targetDate = buildDateTime(2011, 1, 1);
        events.add(createBillingEvent(UUID.randomUUID(), targetDate, plan, planPhase, 1));

        InvoiceGenerator invoiceGenerator = new DefaultInvoiceGenerator();
        Invoice invoice = invoiceGenerator.generateInvoice(UUID.randomUUID(), events, null, targetDate, Currency.USD);

        assertEquals(invoice.getNumberOfItems(), 1);
    }

    @Test
    public void testEndDateIsCorrect() {
        Plan plan = new MockPlan();
        PlanPhase planPhase = createMockMonthlyPlanPhase(ZERO);
        BillingEventSet events = new BillingEventSet();
        DateTime targetDate = new DateTime();
        events.add(createBillingEvent(UUID.randomUUID(), targetDate, plan, planPhase, targetDate.getDayOfMonth()));

        InvoiceGenerator invoiceGenerator = new DefaultInvoiceGenerator();
        Invoice invoice = invoiceGenerator.generateInvoice(UUID.randomUUID(), events, null, targetDate, Currency.USD);
        InvoiceItem item = invoice.getInvoiceItems().get(0);

        // end date of the invoice item should be equal to exactly one month later
        assertEquals(item.getEndDate().compareTo(targetDate.plusMonths(1)), 0);
    }

    @Test
    public void testDuplicatesEventBusIssues() {
        Subscription subscription = new MockSubscription();
        InternationalPrice zeroPrice = new MockInternationalPrice(new DefaultPrice(ZERO, Currency.USD));
        int billCycleDay = 1;
        BillingEventSet events = new BillingEventSet();

        Plan shotgun = new MockPlan();
        PlanPhase shotgunMonthly = createMockMonthlyPlanPhase(null, ZERO, PhaseType.TRIAL);
        BillingEvent event1 = new DefaultBillingEvent(subscription, new DateTime("2012-01-31T00:02:04.000Z"),
                                                      shotgun, shotgunMonthly,
                                                      zeroPrice, null, BillingPeriod.NO_BILLING_PERIOD, billCycleDay,
                                                      BillingModeType.IN_ADVANCE, "Test Event 1", SubscriptionTransitionType.CREATE);
        events.add(event1);


        Plan assaultRifle = new MockPlan();
        PlanPhase assaultRifleMonthly = createMockMonthlyPlanPhase(null, ZERO, PhaseType.TRIAL);
        BillingEvent event2 = new DefaultBillingEvent(subscription, new DateTime("2012-01-31T00:02:04.000Z"),
                                                      assaultRifle, assaultRifleMonthly,
                                                      zeroPrice, null, BillingPeriod.NO_BILLING_PERIOD, billCycleDay,
                                                      BillingModeType.IN_ADVANCE, "Test Event 2", SubscriptionTransitionType.CREATE);
        events.add(event2);

        Plan pistol = new MockPlan();
        PlanPhase pistolMonthlyTrial = createMockMonthlyPlanPhase(null, ZERO, PhaseType.TRIAL);
        BigDecimal pistolMonthlyCost = new BigDecimal("29.95");
        PlanPhase pistolMonthlyEvergreen = createMockMonthlyPlanPhase(pistolMonthlyCost, null, PhaseType.EVERGREEN);
        InternationalPrice pistolEvergreenPrice = new MockInternationalPrice(new DefaultPrice(pistolMonthlyCost, Currency.USD));
        BillingEvent event3 = new DefaultBillingEvent(subscription, new DateTime("2012-01-31T00:02:05.000Z"),
                                                      pistol, pistolMonthlyTrial,
                                                      zeroPrice, null, BillingPeriod.NO_BILLING_PERIOD, billCycleDay,
                                                      BillingModeType.IN_ADVANCE, "Test Event 3", SubscriptionTransitionType.CREATE);
        events.add(event3);


        BillingEvent event4 = new DefaultBillingEvent(subscription, new DateTime("2012-03-01T00:02:04.000Z"),
                                                      pistol, pistolMonthlyEvergreen,
                                                      null, pistolEvergreenPrice, BillingPeriod.MONTHLY, billCycleDay,
                                                      BillingModeType.IN_ADVANCE, "Test Event 4",SubscriptionTransitionType.CREATE);
        events.add(event4);

        InvoiceItemList items = new InvoiceItemList();
        UUID subscriptionId = UUID.randomUUID();
        InvoiceItem item1 = new DefaultInvoiceItem(UUID.randomUUID(), subscriptionId, shotgun.getName(), shotgunMonthly.getName(), new DateTime("2012-01-30T16:02:04.000-08:00"), new DateTime("2012-01-30T16:02:04.000-08:00"), ZERO, ZERO, ZERO, Currency.USD);
        InvoiceItem item2 = new DefaultInvoiceItem(UUID.randomUUID(), subscriptionId, assaultRifle.getName(), assaultRifleMonthly.getName(), new DateTime("2012-02-29T16:02:04.000-08:00"), new DateTime("2012-02-29T16:02:04.000-08:00"), ZERO, new BigDecimal("249.95"), ZERO, Currency.USD);
        InvoiceItem item3 = new DefaultInvoiceItem(UUID.randomUUID(), subscriptionId, pistol.getName(), pistolMonthlyTrial.getName(), new DateTime("2012-01-30T16:02:04.000-08:00"), new DateTime("2012-01-30T16:02:04.000-08:00"), ZERO, ZERO, ZERO, Currency.USD);
        items.add(item1);
        items.add(item2);
        items.add(item3);

        InvoiceGenerator generator= new DefaultInvoiceGenerator();
        Invoice invoice = generator.generateInvoice(UUID.randomUUID(), events, items, new DateTime(), Currency.USD);
        assertNotNull(invoice);
        assertTrue(invoice.getNumberOfItems() > 0);
    }

    @Test
    private void testImmediateChange() {
        UUID accountId = UUID.randomUUID();
        Subscription subscription = new MockSubscription();
        Plan plan1 = new MockPlan("plan 1");
        PlanPhase phase1 = new MockPlanPhase(plan1, PhaseType.TRIAL);
        Plan plan2 = new MockPlan("plan 2");
        PlanPhase phase2 = new MockPlanPhase(plan2, PhaseType.TRIAL);
        InternationalPrice zeroPrice = new MockInternationalPrice(new DefaultPrice(ZERO, Currency.USD));
        BillingEventSet events = new BillingEventSet();

        BillingEvent event1 = new DefaultBillingEvent(subscription, new DateTime("2012-01-31T00:02:04.000Z"),
                                                      plan1, phase1,
                                                      zeroPrice, null, BillingPeriod.NO_BILLING_PERIOD, 1,
                                                      BillingModeType.IN_ADVANCE, "Test Event 1");
        events.add(event1);

        Invoice invoice1 = generator.generateInvoice(accountId, events, null, new DateTime("2012-01-31T00:02:04.000Z"), Currency.USD);
        assertNotNull(invoice1);
        assertEquals(invoice1.getNumberOfItems(), 1);

        BillingEvent event2 = new DefaultBillingEvent(subscription, new DateTime("2012-01-31T00:02:04.000Z"),
                                                      plan2, phase2,
                                                      zeroPrice, null, BillingPeriod.NO_BILLING_PERIOD, 1,
                                                      BillingModeType.IN_ADVANCE, "Test Event 2");
        events.add(event2);
        InvoiceItemList items = new InvoiceItemList(invoice1.getInvoiceItems());
        Invoice invoice2 = generator.generateInvoice(accountId, events, items, new DateTime("2012-01-31T00:02:04.000Z"), Currency.USD);

        assertNotNull(invoice2);
        assertEquals(invoice2.getNumberOfItems(), 1);
        assertEquals(invoice2.getInvoiceItems().get(0).getPlanName(), plan2.getName());
    }

    private MockPlanPhase createMockMonthlyPlanPhase() {
        return new MockPlanPhase(null, null, BillingPeriod.MONTHLY);
    }

    private MockPlanPhase createMockMonthlyPlanPhase(@Nullable final BigDecimal recurringRate) {
        return new MockPlanPhase(new MockInternationalPrice(new DefaultPrice(recurringRate, Currency.USD)),
                                 null, BillingPeriod.MONTHLY);
    }

    private MockPlanPhase createMockMonthlyPlanPhase(@Nullable final BigDecimal recurringRate,
                                                     final BigDecimal fixedRate, PhaseType phaseType) {
        return new MockPlanPhase(new MockInternationalPrice(new DefaultPrice(recurringRate, Currency.USD)),
                                 new MockInternationalPrice(new DefaultPrice(fixedRate, Currency.USD)),
                                 BillingPeriod.MONTHLY, phaseType);
    }

    private MockPlanPhase createMockMonthlyPlanPhase(final BigDecimal recurringRate, final PhaseType phaseType) {
        return new MockPlanPhase(new MockInternationalPrice(new DefaultPrice(recurringRate, Currency.USD)),
                                 null, BillingPeriod.MONTHLY, phaseType);
    }

    private MockPlanPhase createMockAnnualPlanPhase(final BigDecimal recurringRate, final PhaseType phaseType) {
        return new MockPlanPhase(new MockInternationalPrice(new DefaultPrice(recurringRate, Currency.USD)),
                                 null, BillingPeriod.ANNUAL, phaseType);
    }

    private DefaultBillingEvent createBillingEvent(final UUID subscriptionId, final DateTime startDate,
                                                   final Plan plan, final PlanPhase planPhase, final int billCycleDay) {
        Subscription sub = new SubscriptionData(new SubscriptionBuilder().setId(subscriptionId));

        return new DefaultBillingEvent(sub, startDate, plan, planPhase,
                                       planPhase.getFixedPrice(),
                                       planPhase.getRecurringPrice(), planPhase.getBillingPeriod(),
                                       billCycleDay, BillingModeType.IN_ADVANCE,"Test", SubscriptionTransitionType.CREATE);
    }

    private void testInvoiceGeneration(final BillingEventSet events, final InvoiceItemList existingInvoiceItems,
                                       final DateTime targetDate, final int expectedNumberOfItems,
                                       final BigDecimal expectedAmount) {
        Currency currency = Currency.USD;
        UUID accountId = UUID.randomUUID();
        Invoice invoice = generator.generateInvoice(accountId, events, existingInvoiceItems, targetDate, currency);
        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), expectedNumberOfItems);

        existingInvoiceItems.addAll(invoice.getInvoiceItems());
        assertEquals(invoice.getTotalAmount(), expectedAmount);
    }
    // TODO: Jeff C -- how do we ensure that an annual add-on is properly aligned *at the end* with the base plan?
}                      