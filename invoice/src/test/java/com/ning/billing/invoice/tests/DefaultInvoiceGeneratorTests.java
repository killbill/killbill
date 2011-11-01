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
import com.ning.billing.invoice.model.*;
import org.joda.time.DateTime;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

@Test(groups = {"invoicing", "invoiceGenerator"})
public class DefaultInvoiceGeneratorTests extends InvoicingTestBase {
    private final IInvoiceGenerator generator = new DefaultInvoiceGenerator();

    @Test
    public void testWithNullEventSetAndNullInvoiceSet() {
        Invoice invoice = generator.generateInvoice(null, null, new DateTime(), Currency.USD);

        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 0);
        assertEquals(invoice.getTotalAmount(), ZERO);
    }

    @Test
    public void testWithEmptyEventSet() {
        BillingEventSet events = new BillingEventSet();

        InvoiceItemList invoiceItems = new InvoiceItemList();
        Invoice invoice = generator.generateInvoice(events, invoiceItems, new DateTime(), Currency.USD);

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

        InvoiceItemList invoiceItems = new InvoiceItemList();
        
        DateTime targetDate = buildDateTime(2011, 10, 3);
        Invoice invoice = generator.generateInvoice(events, invoiceItems, targetDate, Currency.USD);

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

        InvoiceItemList invoiceItems = new InvoiceItemList();
        
        DateTime targetDate = buildDateTime(2011, 10, 3);        
        Invoice invoice = generator.generateInvoice(events, invoiceItems, targetDate, Currency.USD);

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

        InvoiceItemList invoiceItems = new InvoiceItemList();
        DateTime targetDate = buildDateTime(2011, 10, 3);
        Invoice invoice = generator.generateInvoice(events, invoiceItems, targetDate, Currency.USD);

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

        InvoiceItemList invoiceItems = new InvoiceItemList();
        DateTime targetDate = buildDateTime(2011, 12, 3);
        Invoice invoice = generator.generateInvoice(events, invoiceItems, targetDate, Currency.USD);

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

        InvoiceItemList invoiceItems = new InvoiceItemList();
        DateTime targetDate = buildDateTime(2011, 12, 3);
        Invoice invoice = generator.generateInvoice(events, invoiceItems, targetDate, Currency.USD);

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

        InvoiceItemList invoiceItems = new InvoiceItemList();
        InvoiceItem invoiceItem = new InvoiceItem(subscriptionId, startDate, buildDateTime(2012, 1, 1), "",
                                                 rate.multiply(FOUR), rate, Currency.USD);
        invoiceItems.add(invoiceItem);

        DateTime targetDate = buildDateTime(2011, 12, 3);
        Invoice invoice = generator.generateInvoice(events, invoiceItems, targetDate, Currency.USD);

        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 0);
        assertEquals(invoice.getTotalAmount(), ZERO);
    }

    @Test
    public void testMultiplePlansWithUtterChaos() {
        // plan 1: change of term from monthly to annual followed by immediate cancellation; (covers term change, cancel, double pro-ration)
        // plan 2: single plan that moves from trial to discount to evergreen; BCD = 10 (covers phase change)
        // plan 3: change of term from annual (BCD = 20) to monthly (BCD = 31; immediate)
        // plan 4: change of plan, effective EOT, BCD = 10, start = 2/10/2011 (covers change of plan, multiple BCD)

        BillingMode billingMode = BillingMode.IN_ADVANCE;
        BillingEventSet events = new BillingEventSet();

        UUID subscriptionId1 = UUID.randomUUID();
        DateTime startDate1 = buildDateTime(2011, 1, 5);
        String planName1 = "World Domination";

        IBillingEvent event;
        BigDecimal expectedAmount;
        InvoiceItemList invoiceItems = new InvoiceItemList();

        event = new BillingEvent(subscriptionId1, startDate1,
                                 planName1, "Conceive diabolical plan",
                                 new InternationalPriceMock(EIGHT), BillingPeriod.MONTHLY, 5, billingMode);
        events.add(event);

        // invoice for 2011/1/5 through 2011/2/5 on plan 1
        expectedAmount = EIGHT;

        // initial invoice on subscription creation
        testInvoiceGeneration(events, invoiceItems, startDate1, 1, expectedAmount);
        assertEquals(invoiceItems.size(), 1);

        // attempt to invoice again the following day; should have no invoice items
        testInvoiceGeneration(events, invoiceItems, buildDateTime(2011, 1, 6), 0, ZERO);
        assertEquals(invoiceItems.size(), 1);

        // add a second plan to the account
        UUID subscriptionId2 = UUID.randomUUID();
        DateTime startDate2 = buildDateTime(2011, 3, 10);
        String planName2 = "Build Invoicing System";

        event = new BillingEvent(subscriptionId2, startDate2,
                                 planName2, "Implement and test pro-ration algorithm",
                                 new InternationalPriceMock(TWENTY), BillingPeriod.MONTHLY, 10, billingMode);
        events.add(event);

        // invoice for 2011/2/5 - 2011/4/5 on plan 1; invoice for 2011/3/10 - 2011/4/10 on plan 2
        expectedAmount = EIGHT.multiply(TWO).add(TWENTY).setScale(NUMBER_OF_DECIMALS);
        testInvoiceGeneration(events, invoiceItems, startDate2, 2, expectedAmount);
        assertEquals(invoiceItems.size(), 3);

        // first plan rolls into discount period on 4/5
        DateTime phaseChangeDate = buildDateTime(2011, 4, 5);
        event = new BillingEvent(subscriptionId1, phaseChangeDate,
                                 planName1, "Hire minions",
                                 new InternationalPriceMock(TWELVE), BillingPeriod.MONTHLY, 5, billingMode);
        events.add(event);

        // on plan creation, invoice for 2011/4/5 through 2011/5/5 on plan 1
        testInvoiceGeneration(events, invoiceItems, phaseChangeDate, 1, TWELVE);
        assertEquals(invoiceItems.size(), 4);

        // on 2011/4/11, invoice for 2011/4/10 - 2011/5/10 on plan 2
        DateTime billRunDate = buildDateTime(2011, 4, 11);
        testInvoiceGeneration(events, invoiceItems, billRunDate, 1, TWENTY);
        assertEquals(invoiceItems.size(), 5);

        // on 2011/4/29, cancel plan 1, effective immediately
        DateTime plan1CancelDate = buildDateTime(2011, 4, 29);
        event = new BillingEvent(subscriptionId1, plan1CancelDate,
                                 planName1, "Defeated by James Bond",
                                 new InternationalPriceMock(ZERO), BillingPeriod.MONTHLY, 5, billingMode);
        events.add(event);

        // generate correcting invoice item for cancellation
        Invoice invoice = generator.generateInvoice(events, new InvoiceItemList(), plan1CancelDate, Currency.USD);
        BigDecimal totalToDate = invoice.getTotalAmount();

        BigDecimal invoicedAmount = ZERO;
        for (InvoiceItem item : invoiceItems) {
            invoicedAmount = invoicedAmount.add(item.getAmount());
        }

        BigDecimal creditAmount = totalToDate.subtract(invoicedAmount).setScale(NUMBER_OF_DECIMALS);

        testInvoiceGeneration(events, invoiceItems, plan1CancelDate, 2, creditAmount);
    }

    private void testInvoiceGeneration(BillingEventSet events, InvoiceItemList invoiceItems, DateTime targetDate, int expectedNumberOfItems, BigDecimal expectedAmount) {
        Currency currency = Currency.USD;
        Invoice invoice = generator.generateInvoice(events, invoiceItems, targetDate, currency);
        invoiceItems.addAll(invoice.getItems());
        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), expectedNumberOfItems);
        assertEquals(invoice.getTotalAmount(), expectedAmount);
    }

    // TODO: Jeff C -- how do we ensure that an annual add-on is properly aligned *at the end* with the base plan?
}