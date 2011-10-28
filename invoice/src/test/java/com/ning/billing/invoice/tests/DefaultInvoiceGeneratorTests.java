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
import com.ning.billing.invoice.model.DefaultInvoiceGenerator;
import com.ning.billing.invoice.model.IInvoiceGenerator;
import com.ning.billing.invoice.model.Invoice;
import org.joda.time.DateTime;
import org.testng.annotations.Test;

import java.util.UUID;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

@Test(groups = {"invoicing", "invoiceGenerator"})
public class DefaultInvoiceGeneratorTests extends InvoicingTestBase {
    private final IInvoiceGenerator generator = new DefaultInvoiceGenerator();

    @Test
    public void testWithNullEventSet() {
        Invoice invoice = generator.generateInvoice(null);

        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 0);
        assertEquals(invoice.getTotalAmount(), ZERO);
    }

    @Test
    public void testWithEmptyEventSet() {
        BillingEventSet events = new BillingEventSet(Currency.USD);

        Invoice invoice = generator.generateInvoice(events);

        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 0);
        assertEquals(invoice.getTotalAmount(), ZERO);
    }

    @Test
    public void testWithSingleSimpleEvent() {
        DateTime targetDate = buildDateTime(2011, 10, 3);
        BillingEventSet events = new BillingEventSet(Currency.USD, targetDate);

        UUID subscriptionId = UUID.randomUUID();
        DateTime startDate = buildDateTime(2011, 9, 1);
        String planName = "World Domination";
        String phaseName = "Build Space Laser";
        IBillingEvent event = new BillingEvent(subscriptionId, startDate, planName, phaseName,
                                               new InternationalPriceMock(TEN), BillingPeriod.MONTHLY,
                                               1, BillingMode.IN_ADVANCE);

        events.add(event);

        Invoice invoice = generator.generateInvoice(events);

        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 1);
        assertEquals(invoice.getTotalAmount(), TWENTY);
    }

    @Test
    public void testTwoSubscriptionsWithAlignedBillingDates() {
        DateTime targetDate = buildDateTime(2011, 10, 3);
        BillingEventSet events = new BillingEventSet(Currency.USD, targetDate);

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

        Invoice invoice = generator.generateInvoice(events);

        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 2);
        assertEquals(invoice.getTotalAmount(), FIVE.multiply(TWO).add(TEN).setScale(NUMBER_OF_DECIMALS));
    }

    @Test
    public void testOnePlan_ThreePhases_ChangeEOT() {
        DateTime targetDate = buildDateTime(2011, 12, 3);
        BillingEventSet events = new BillingEventSet(Currency.USD, targetDate);

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

        Invoice invoice = generator.generateInvoice(events);

        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 3);
        assertEquals(invoice.getTotalAmount(), FIVE.add(TEN).add(TWO.multiply(THIRTY)).setScale(NUMBER_OF_DECIMALS));
    }
}
