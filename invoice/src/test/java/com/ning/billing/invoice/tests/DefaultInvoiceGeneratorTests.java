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

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.entitlement.api.billing.BillingMode;
import com.ning.billing.entitlement.api.billing.IBillingEvent;
import com.ning.billing.invoice.api.BillingEvent;
import com.ning.billing.invoice.api.BillingEventSet;
import com.ning.billing.invoice.api.IInvoiceItem;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.model.DefaultInvoiceGenerator;
import com.ning.billing.invoice.model.InvoiceGenerator;
import com.ning.billing.invoice.model.InvoiceItem;
import com.ning.billing.invoice.model.InvoiceItemList;
import org.joda.time.DateTime;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

@Test(groups = {"invoicing", "invoiceGenerator"})
public class DefaultInvoiceGeneratorTests extends InvoicingTestBase {
    private final InvoiceGenerator generator = new DefaultInvoiceGenerator();

    @Test
    public void testWithNullEventSetAndNullInvoiceSet() {
        UUID accountId = UUID.randomUUID();
        Invoice invoice = generator.generateInvoice(accountId, null, null, new DateTime(), Currency.USD);

        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 0);
        assertEquals(invoice.getTotalAmount(), ZERO);
    }

    @Test
    public void testWithEmptyEventSet() {
        BillingEventSet events = new BillingEventSet();

        InvoiceItemList existingInvoiceItems = new InvoiceItemList();
        UUID accountId = UUID.randomUUID();
        Invoice invoice = generator.generateInvoice(accountId, events, existingInvoiceItems, new DateTime(), Currency.USD);

        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 0);
        assertEquals(invoice.getTotalAmount(), ZERO);
    }

    @Test
    public void testWithSingleMonthlyEvent() {
        BillingEventSet events = new BillingEventSet();

        UUID subscriptionId = UUID.randomUUID();
        DateTime startDate = buildDateTime(2011, 9, 1);
        String planName = "World Domination";
        String phaseName = "Build Space Laser";
        IBillingEvent event = new BillingEvent(subscriptionId, startDate, planName, phaseName,
                                               new InternationalPriceMock(TEN), BillingPeriod.MONTHLY,
                                               1, BillingMode.IN_ADVANCE);

        events.add(event);

        InvoiceItemList existingInvoiceItems = new InvoiceItemList();
        
        DateTime targetDate = buildDateTime(2011, 10, 3);
        UUID accountId = UUID.randomUUID();
        Invoice invoice = generator.generateInvoice(accountId, events, existingInvoiceItems, targetDate, Currency.USD);

        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 1);
        assertEquals(invoice.getTotalAmount(), TWENTY);
    }

    @Test
    public void testWithSingleMonthlyEventWithLeadingProRation() {
        BillingEventSet events = new BillingEventSet();

        UUID subscriptionId = UUID.randomUUID();
        DateTime startDate = buildDateTime(2011, 9, 1);
        String planName = "World Domination";
        String phaseName = "Build Space Laser";
        BigDecimal rate = TEN;
        IBillingEvent event = new BillingEvent(subscriptionId, startDate, planName, phaseName,
                                               new InternationalPriceMock(rate), BillingPeriod.MONTHLY,
                                               15, BillingMode.IN_ADVANCE);

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

        IBillingEvent event1 = new BillingEvent(UUID.randomUUID(), buildDateTime(2011, 9, 1),
                                               "World Domination", "Build Space Laser",
                                               new InternationalPriceMock(FIVE), BillingPeriod.MONTHLY,
                                               1, BillingMode.IN_ADVANCE);
        events.add(event1);

        IBillingEvent event2 = new BillingEvent(UUID.randomUUID(), buildDateTime(2011, 10, 1),
                                               "Groceries", "Pick Up Milk",
                                               new InternationalPriceMock(TEN), BillingPeriod.MONTHLY,
                                               1, BillingMode.IN_ADVANCE);
        events.add(event2);

        InvoiceItemList existingInvoiceItems = new InvoiceItemList();
        DateTime targetDate = buildDateTime(2011, 10, 3);
        UUID accountId = UUID.randomUUID();
        Invoice invoice = generator.generateInvoice(accountId, events, existingInvoiceItems, targetDate, Currency.USD);

        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 2);
        assertEquals(invoice.getTotalAmount(), FIVE.multiply(TWO).add(TEN).setScale(NUMBER_OF_DECIMALS));
    }

    @Test
    public void testOnePlan_TwoMonthlyPhases_ChangeImmediate() {
        BillingEventSet events = new BillingEventSet();

        UUID subscriptionId = UUID.randomUUID();
        IBillingEvent event1 = new BillingEvent(subscriptionId, buildDateTime(2011, 9, 1),
                                               "World Domination", "Build Space Laser",
                                               new InternationalPriceMock(FIVE), BillingPeriod.MONTHLY,
                                               1, BillingMode.IN_ADVANCE);
        events.add(event1);

        IBillingEvent event2 = new BillingEvent(subscriptionId, buildDateTime(2011, 10, 15),
                                               "World Domination", "Incinerate James Bond",
                                               new InternationalPriceMock(TEN), BillingPeriod.MONTHLY,
                                               15, BillingMode.IN_ADVANCE);
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
        expectedValue = numberOfCyclesEvent1.multiply(FIVE);
        expectedValue = expectedValue.add(numberOfCyclesEvent2.multiply(TEN));
        expectedValue = expectedValue.setScale(NUMBER_OF_DECIMALS);

        assertEquals(invoice.getTotalAmount(), expectedValue);
    }

    @Test
    public void testOnePlan_ThreeMonthlyPhases_ChangeEOT() {
        BillingEventSet events = new BillingEventSet();

        UUID subscriptionId = UUID.randomUUID();
        IBillingEvent event1 = new BillingEvent(subscriptionId, buildDateTime(2011, 9, 1),
                                               "World Domination", "Build Space Laser",
                                               new InternationalPriceMock(FIVE), BillingPeriod.MONTHLY,
                                               1, BillingMode.IN_ADVANCE);
        events.add(event1);

        IBillingEvent event2 = new BillingEvent(subscriptionId, buildDateTime(2011, 10, 1),
                                               "World Domination", "Incinerate James Bond",
                                               new InternationalPriceMock(TEN), BillingPeriod.MONTHLY,
                                               1, BillingMode.IN_ADVANCE);
        events.add(event2);

        IBillingEvent event3 = new BillingEvent(subscriptionId, buildDateTime(2011, 11, 1),
                                               "World Domination", "Cackle Gleefully",
                                               new InternationalPriceMock(THIRTY), BillingPeriod.MONTHLY,
                                               1, BillingMode.IN_ADVANCE);
        events.add(event3);

        InvoiceItemList existingInvoiceItems = new InvoiceItemList();
        DateTime targetDate = buildDateTime(2011, 12, 3);
        UUID accountId = UUID.randomUUID();
        Invoice invoice = generator.generateInvoice(accountId, events, existingInvoiceItems, targetDate, Currency.USD);

        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 3);
        assertEquals(invoice.getTotalAmount(), FIVE.add(TEN).add(TWO.multiply(THIRTY)).setScale(NUMBER_OF_DECIMALS));
    }

    @Test
    public void testSingleEventWithExistingInvoice() {
        BillingEventSet events = new BillingEventSet();

        UUID subscriptionId = UUID.randomUUID();
        DateTime startDate = buildDateTime(2011, 9, 1);

        BigDecimal rate = FIVE;
        IBillingEvent event1 = new BillingEvent(subscriptionId, startDate,
                                               "World Domination", "Build Space Laser",
                                               new InternationalPriceMock(rate), BillingPeriod.MONTHLY,
                                               1, BillingMode.IN_ADVANCE);
        events.add(event1);

        InvoiceItemList existingInvoiceItems = new InvoiceItemList();
        IInvoiceItem invoiceItem = new InvoiceItem(UUID.randomUUID(), subscriptionId, startDate, buildDateTime(2012, 1, 1), "",
                                                 rate.multiply(FOUR), rate, Currency.USD);
        existingInvoiceItems.add(invoiceItem);

        DateTime targetDate = buildDateTime(2011, 12, 3);
        UUID accountId = UUID.randomUUID();
        Invoice invoice = generator.generateInvoice(accountId, events, existingInvoiceItems, targetDate, Currency.USD);

        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 0);
        assertEquals(invoice.getTotalAmount(), ZERO);
    }

    @Test
    public void testMultiplePlansWithUtterChaos() {
        // plan 1: change of phase from trial to discount followed by immediate cancellation; (covers phase change, cancel, pro-ration)
        // plan 2: single plan that moves from trial to discount to evergreen; BCD = 10 (covers phase change)
        // plan 3: change of term from monthly (BCD = 20) to annual (BCD = 31; immediate)
        // plan 4: change of plan, effective EOT, BCD = 7 (covers change of plan)
        // plan 5: addon to plan 2, with bill cycle alignment to plan; immediate cancellation

        UUID subscriptionId1 = UUID.randomUUID();
        String planName1 = "Change from trial to discount with immediate cancellation";
        String plan1PhaseName1 = "Trial"; String plan1PhaseName2 = "Discount"; String plan1phase3 = "Cancel";
        DateTime plan1StartDate = buildDateTime(2011, 1, 5);
        DateTime plan1PhaseChangeDate = buildDateTime(2011, 4, 5);
        DateTime plan1CancelDate = buildDateTime(2011, 4, 29);

        UUID subscriptionId2 = UUID.randomUUID();
        String planName2 = "Change phase from trial to discount to evergreen";
        String plan2PhaseName1 = "Trial"; String plan2PhaseName2 = "Discount"; String plan2PhaseName3 = "Evergreen";
        DateTime plan2StartDate = buildDateTime(2011, 3, 10);
        DateTime plan2PhaseChangeToDiscountDate = buildDateTime(2011, 6, 10);
        DateTime plan2PhaseChangeToEvergreenDate = buildDateTime(2011, 9, 10);

        UUID subscriptionId3 = UUID.randomUUID();
        String planName3 = "Upgrade with immediate change, BCD = 31";
        String plan3PhaseName1 = "Evergreen monthly"; String plan3PhaseName2 = "Evergreen annual";
        DateTime plan3StartDate = buildDateTime(2011, 5, 20);
        DateTime plan3UpgradeToAnnualDate = buildDateTime(2011, 7, 31);

        UUID subscriptionId4 = UUID.randomUUID();
        String planName4a = "Plan change effective EOT; plan 1";
        String planName4b = "Plan change effective EOT; plan 2";
        String plan4PhaseName = "Evergreen";
        DateTime plan4StartDate = buildDateTime(2011, 6, 7);
        DateTime plan4ChangeOfPlanDate = buildDateTime(2011, 8, 7);

        UUID subscriptionId5 = UUID.randomUUID();
        String planName5 = "Add-on";
        String plan5PhaseName1 = "Evergreen"; String plan5PhaseName2 = "Cancel";
        DateTime plan5StartDate = buildDateTime(2011, 6, 21);
        DateTime plan5CancelDate = buildDateTime(2011, 10, 7);

        BigDecimal expectedAmount;
        InvoiceItemList invoiceItems = new InvoiceItemList();
        BillingEventSet events = new BillingEventSet();

        // on 1/5/2011, create subscription 1 (trial)
        events.add(createBillingEvent(subscriptionId1, plan1StartDate, planName1, plan1PhaseName1, EIGHT, 5));
        expectedAmount = EIGHT;
        testInvoiceGeneration(events, invoiceItems, plan1StartDate, 1, expectedAmount);

        // on 2/5/2011, invoice subscription 1 (trial)
        expectedAmount = EIGHT;
        testInvoiceGeneration(events, invoiceItems, buildDateTime(2011, 2, 5) , 1, expectedAmount);

        // on 3/5/2011, invoice subscription 1 (trial)
        expectedAmount = EIGHT;
        testInvoiceGeneration(events, invoiceItems, buildDateTime(2011, 3, 5), 1, expectedAmount);

        // on 3/10/2011, create subscription 2 (trial)
        events.add(createBillingEvent(subscriptionId2, plan2StartDate, planName2, plan2PhaseName1, TWENTY, 10));
        expectedAmount = TWENTY;
        testInvoiceGeneration(events, invoiceItems, plan2StartDate, 1, expectedAmount);

        // on 4/5/2011, invoice subscription 1 (discount)
        events.add(createBillingEvent(subscriptionId1, plan1PhaseChangeDate, planName1, plan1PhaseName2, TWELVE, 5));
        expectedAmount = TWELVE;
        testInvoiceGeneration(events, invoiceItems, plan1PhaseChangeDate, 1, expectedAmount);

        // on 4/10/2011, invoice subscription 2 (trial)
        expectedAmount = TWENTY;
        testInvoiceGeneration(events, invoiceItems, buildDateTime(2011, 4, 10), 1, expectedAmount);

        // on 4/29/2011, cancel subscription 1
        events.add(createBillingEvent(subscriptionId1, plan1CancelDate, planName1, plan1phase3, ZERO, 5));
        expectedAmount = TWELVE.multiply(SIX.divide(THIRTY, NUMBER_OF_DECIMALS, ROUNDING_METHOD)).negate().setScale(NUMBER_OF_DECIMALS);
        testInvoiceGeneration(events, invoiceItems, plan1CancelDate, 2, expectedAmount);

        // on 5/10/2011, invoice subscription 2 (trial)
        expectedAmount = TWENTY;
        testInvoiceGeneration(events, invoiceItems, buildDateTime(2011, 5, 10), 1, expectedAmount);

        // on 5/20/2011, create subscription 3 (monthly)
        events.add(createBillingEvent(subscriptionId3, plan3StartDate, planName3, plan3PhaseName1, TEN, 20));
        expectedAmount = TEN;
        testInvoiceGeneration(events, invoiceItems, plan3StartDate, 1, expectedAmount);

        // on 6/7/2011, create subscription 4
        events.add(createBillingEvent(subscriptionId4, plan4StartDate, planName4a, plan4PhaseName, FIFTEEN, 7));
        expectedAmount = FIFTEEN;
        testInvoiceGeneration(events, invoiceItems, plan4StartDate, 1, expectedAmount);

        // on 6/10/2011, invoice subscription 2 (discount)
        events.add(createBillingEvent(subscriptionId2, plan2PhaseChangeToDiscountDate, planName2, plan2PhaseName2, THIRTY, 10));
        expectedAmount = THIRTY;
        testInvoiceGeneration(events, invoiceItems, plan2PhaseChangeToDiscountDate, 1, expectedAmount);

        // on 6/20/2011, invoice subscription 3 (monthly)
        expectedAmount = TEN;
        testInvoiceGeneration(events, invoiceItems, buildDateTime(2011, 6, 20), 1, expectedAmount);

        // on 6/21/2011, create add-on (subscription 5)
        events.add(createBillingEvent(subscriptionId5, plan5StartDate, planName5, plan5PhaseName1, TWENTY, 10));
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
        events.add(createAnnualBillingEvent(subscriptionId3, plan3UpgradeToAnnualDate, planName3, plan3PhaseName2, ONE_HUNDRED, 31));
        expectedAmount = ONE_HUNDRED.subtract(TEN);
        expectedAmount = expectedAmount.add(TEN.multiply(ELEVEN.divide(THIRTY_ONE, NUMBER_OF_DECIMALS, ROUNDING_METHOD)));
        expectedAmount = expectedAmount.setScale(NUMBER_OF_DECIMALS);
        testInvoiceGeneration(events, invoiceItems, plan3UpgradeToAnnualDate, 3, expectedAmount);

        // on 8/7/2011, invoice subscription 4 (plan 2)
        events.add(createBillingEvent(subscriptionId4, plan4ChangeOfPlanDate, planName4b, plan4PhaseName, TWENTY_FOUR, 7));
        expectedAmount = TWENTY_FOUR;
        testInvoiceGeneration(events, invoiceItems, plan4ChangeOfPlanDate, 1, expectedAmount);

        // on 8/10/2011, invoice plan 2 (discount), invoice subscription 5
        expectedAmount = THIRTY.add(TWENTY);
        testInvoiceGeneration(events, invoiceItems, buildDateTime(2011, 8, 10), 2, expectedAmount);

        // on 9/7/2011, invoice subscription 4 (plan 2)
        expectedAmount = TWENTY_FOUR;
        testInvoiceGeneration(events, invoiceItems, buildDateTime(2011, 9, 7), 1, expectedAmount);

        // on 9/10/2011, invoice plan 2 (evergreen), invoice subscription 5
        events.add(createBillingEvent(subscriptionId2, plan2PhaseChangeToEvergreenDate, planName2, plan2PhaseName3, FORTY, 10));
        expectedAmount = FORTY.add(TWENTY);
        testInvoiceGeneration(events, invoiceItems, plan2PhaseChangeToEvergreenDate, 2, expectedAmount);

        // on 10/7/2011, invoice subscription 4 (plan 2), cancel subscription 5
        events.add(createBillingEvent(subscriptionId5, plan5CancelDate, planName5, plan5PhaseName2, ZERO, 10));
        expectedAmount = TWENTY_FOUR.add(TWENTY.multiply(THREE.divide(THIRTY)).negate().setScale(NUMBER_OF_DECIMALS));
        testInvoiceGeneration(events, invoiceItems, plan5CancelDate, 3, expectedAmount);

        // on 10/10/2011, invoice plan 2 (evergreen)
        expectedAmount = FORTY ;
        testInvoiceGeneration(events, invoiceItems, buildDateTime(2011, 10, 10), 1, expectedAmount);
    }

    private BillingEvent createBillingEvent(UUID subscriptionId, DateTime startDate, String planName, String planPhaseName,
                                            BigDecimal rate, int billCycleDay) {
        return new BillingEvent(subscriptionId, startDate, planName, planPhaseName,
                                new InternationalPriceMock(rate), BillingPeriod.MONTHLY,
                                billCycleDay, BillingMode.IN_ADVANCE);

    }

    private BillingEvent createAnnualBillingEvent(UUID subscriptionId, DateTime startDate, String planName, String planPhaseName,
                                                  BigDecimal rate, int billCycleDay) {
        return new BillingEvent(subscriptionId, startDate, planName, planPhaseName,
                                new InternationalPriceMock(rate), BillingPeriod.ANNUAL,
                                billCycleDay, BillingMode.IN_ADVANCE);

    }

    private void testInvoiceGeneration(BillingEventSet events, InvoiceItemList existingInvoiceItems, DateTime targetDate, int expectedNumberOfItems, BigDecimal expectedAmount) {
        Currency currency = Currency.USD;
        UUID accountId = UUID.randomUUID();
        Invoice invoice = generator.generateInvoice(accountId, events, existingInvoiceItems, targetDate, currency);
        existingInvoiceItems.addAll(invoice.getItems());
        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), expectedNumberOfItems);
        assertEquals(invoice.getTotalAmount(), expectedAmount);
    }

    // TODO: Jeff C -- how do we ensure that an annual add-on is properly aligned *at the end* with the base plan?
}