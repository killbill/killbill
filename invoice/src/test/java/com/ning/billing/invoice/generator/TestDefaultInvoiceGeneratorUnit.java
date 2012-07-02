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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import java.math.BigDecimal;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.config.InvoiceConfig;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.invoice.model.CreditBalanceAdjInvoiceItem;
import com.ning.billing.invoice.model.FixedPriceInvoiceItem;
import com.ning.billing.invoice.model.RecurringInvoiceItem;
import com.ning.billing.invoice.model.RepairAdjInvoiceItem;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;

public class TestDefaultInvoiceGeneratorUnit  {

    private DefaultInvoiceGenerator gen;
    private Clock clock;

    private final UUID invoiceId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID subscriptionId = UUID.randomUUID();
    private final UUID bundleId = UUID.randomUUID();
    private final String planName = "my-plan";
    private final String phaseName = "my-phase";
    private final Currency currency = Currency.USD;

    public static final class TestDefaultInvoiceGeneratorMock extends DefaultInvoiceGenerator {
        public TestDefaultInvoiceGeneratorMock(final Clock clock, final InvoiceConfig config) {
            super(clock, config);
        }
    }

    @BeforeClass(groups = {"fast"})
    public void setup() {
        clock = new ClockMock();
        gen = new TestDefaultInvoiceGeneratorMock(clock, new InvoiceConfig() {
            @Override
            public boolean isNotificationProcessingOff() {
                return false;
            }
            @Override
            public boolean isEmailNotificationsEnabled() {
                return false;
            }
            @Override
            public long getSleepTimeMs() {
                return 100;
            }
            @Override
            public int getNumberOfMonthsInFuture() {
                return 5;
            }
        });
    }

    @Test(groups = {"fast"}, enabled= true)
    public void testRemoveCancellingInvoiceItemsFixedPrice() {

        DateTime startDate = clock.getUTCNow();
        DateTime endDate = startDate.plusDays(30);
        DateTime nextEndDate = startDate.plusMonths(1);

        BigDecimal amount = new BigDecimal("12.00");
        BigDecimal rate2 = new BigDecimal("14.85");
        BigDecimal amount2 = rate2;
        List<InvoiceItem> items =  new LinkedList<InvoiceItem>();
        InvoiceItem item1 = new FixedPriceInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount, currency);
        items.add(item1);
        items.add(new RepairAdjInvoiceItem(invoiceId, accountId, startDate, endDate, amount.negate(), currency, item1.getId()));
        items.add(new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, endDate, nextEndDate, amount2, rate2, currency));
        gen.removeCancellingInvoiceItems(items);
        assertEquals(items.size(), 1);
        InvoiceItem leftItem = items.get(0);
        assertEquals(leftItem.getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(leftItem.getAmount(), amount2);
    }

    @Test(groups = {"fast"})
    public void testRemoveCancellingInvoiceItemsRecurringPrice() {

        DateTime startDate = clock.getUTCNow();
        DateTime endDate = startDate.plusDays(30);
        DateTime nextEndDate = startDate.plusMonths(1);

        BigDecimal rate1 = new BigDecimal("12.00");
        BigDecimal amount1 = rate1;
        BigDecimal rate2 = new BigDecimal("14.85");
        BigDecimal amount2 = rate2;
        List<InvoiceItem> items =  new LinkedList<InvoiceItem>();
        InvoiceItem item1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount1, rate1, currency, null);
        items.add(item1);
        items.add(new RepairAdjInvoiceItem(invoiceId, accountId, startDate, endDate, amount1.negate(), currency, item1.getId()));
        items.add(new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, endDate, nextEndDate, amount2, rate2, currency));
        gen.removeCancellingInvoiceItems(items);
        assertEquals(items.size(), 1);
        InvoiceItem leftItem = items.get(0);
        assertEquals(leftItem.getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(leftItem.getAmount(), amount2);
    }

    @Test(groups = {"fast"})
    public void testRemoveDuplicatedInvoiceItemsFixedPrice() {

        DateTime startDate = clock.getUTCNow();
        DateTime endDate = startDate.plusDays(30);
        DateTime nextEndDate = startDate.plusMonths(1);

        BigDecimal amount1 = new BigDecimal("12.00");

        BigDecimal amount2 = new BigDecimal("14.85");

        BigDecimal rate3 = new BigDecimal("14.85");
        BigDecimal amount3 = rate3;

        List<InvoiceItem> existing =  new LinkedList<InvoiceItem>();
        InvoiceItem item1 = new FixedPriceInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount1, currency);
        existing.add(item1);

        List<InvoiceItem> proposed =  new LinkedList<InvoiceItem>();
        InvoiceItem other = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, endDate, nextEndDate, amount3, rate3, currency, null);
        proposed.add(item1);
        proposed.add(other);

        gen.removeDuplicatedInvoiceItems(proposed, existing);
        assertEquals(existing.size(), 0);
        assertEquals(proposed.size(), 1);
        InvoiceItem leftItem = proposed.get(0);
        assertEquals(leftItem.getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(leftItem.getAmount(), amount2);
    }

    @Test(groups = {"fast"})
    public void testRemoveDuplicatedInvoiceItemsRecuringPrice() {

        DateTime startDate = clock.getUTCNow();
        DateTime endDate = startDate.plusDays(30);
        DateTime nextEndDate = startDate.plusMonths(1);

        BigDecimal rate1 = new BigDecimal("12.00");
        BigDecimal amount1 = rate1;

        BigDecimal rate2 = new BigDecimal("14.85");
        BigDecimal amount2 = rate2;

        List<InvoiceItem> existing =  new LinkedList<InvoiceItem>();
        InvoiceItem item1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount1, rate1, currency, null);
        existing.add(item1);

        List<InvoiceItem> proposed =  new LinkedList<InvoiceItem>();
        InvoiceItem other = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, endDate, nextEndDate, amount2, rate2, currency, null);
        proposed.add(item1);
        proposed.add(other);

        gen.removeDuplicatedInvoiceItems(proposed, existing);
        assertEquals(existing.size(), 0);
        assertEquals(proposed.size(), 1);
        InvoiceItem leftItem = proposed.get(0);
        assertEquals(leftItem.getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(leftItem.getAmount(), amount2);
    }

    // STEPH same as testRemoveCancellingInvoiceItemsFixedPrice: should we have one for FixedPrice?
    @Test(groups = {"fast"})
    public void testAddRepairedItemsItemsRecurringPrice() {
        DateTime startDate = clock.getUTCNow();
        DateTime endDate = startDate.plusDays(30);
        DateTime nextEndDate = startDate.plusMonths(1);

        BigDecimal rate1 = new BigDecimal("12.00");
        BigDecimal amount1 = rate1;

        BigDecimal rate2 = new BigDecimal("14.85");
        BigDecimal amount2 = rate2;

        UUID firstInvoiceId = UUID.randomUUID();
        List<InvoiceItem> existing =  new LinkedList<InvoiceItem>();
        InvoiceItem item1 = new RecurringInvoiceItem(firstInvoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount1, rate1, currency, null);
        existing.add(item1);

        List<InvoiceItem> proposed =  new LinkedList<InvoiceItem>();
        InvoiceItem other = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, endDate, nextEndDate, amount2, rate2, currency, null);
        proposed.add(other);

        gen.addRepairedItems(existing, proposed);
        assertEquals(existing.size(), 1);
        assertEquals(proposed.size(), 2);
        InvoiceItem leftItem1 = proposed.get(0);
        assertEquals(leftItem1.getInvoiceId(), invoiceId);
        assertEquals(leftItem1.getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(leftItem1.getAmount(), amount2);

        InvoiceItem newItem2 = proposed.get(1);
        assertEquals(newItem2.getInvoiceId(), firstInvoiceId);
        assertEquals(newItem2.getInvoiceItemType(), InvoiceItemType.REPAIR_ADJ);
        assertEquals(newItem2.getAmount(), item1.getAmount().negate());
        assertEquals(newItem2.getLinkedItemId(), item1.getId());

    }

    @Test(groups = {"fast"})
    public void testGenerateCreditsForPastRepairedInvoices() {

        DateTime startDate = clock.getUTCNow();
        DateTime endDate = startDate.plusDays(30);
        DateTime nextEndDate = startDate.plusMonths(1);

        BigDecimal rate1 = new BigDecimal("10.00");
        BigDecimal amount1 = rate1;

        List<InvoiceItem> existing =  new LinkedList<InvoiceItem>();
        InvoiceItem item1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount1, rate1, currency, null);
        existing.add(item1);


        UUID existingInvoiceId = UUID.randomUUID();
        List<Invoice> existingInvoices = new LinkedList<Invoice>();
        Invoice existingInvoice = mock(Invoice.class);
        when(existingInvoice.getId()).thenReturn(existingInvoiceId);
        when(existingInvoice.getBalance()).thenReturn(BigDecimal.ZERO);
        when(existingInvoice.getInvoiceItems()).thenReturn(existing);

        BigDecimal rate2 = new BigDecimal("20.0");
        BigDecimal amount2 = rate2;

        List<InvoiceItem> proposed =  new LinkedList<InvoiceItem>();
        InvoiceItem reversedItem1 = new RepairAdjInvoiceItem(existingInvoiceId, accountId, startDate, nextEndDate, item1.getAmount().negate(), currency, item1.getId());
        InvoiceItem newItem1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount2, rate2, currency, null);
        proposed.add(reversedItem1);
        proposed.add(newItem1);

        gen.generateCBAForExistingInvoices(accountId, existingInvoices, proposed, currency);

        assertEquals(proposed.size(), 3);
        InvoiceItem reversedItemCheck1 = proposed.get(0);
        assertEquals(reversedItemCheck1.getInvoiceId(), existingInvoiceId);
        assertEquals(reversedItemCheck1.getInvoiceItemType(), InvoiceItemType.REPAIR_ADJ);
        assertEquals(reversedItemCheck1.getAmount(), item1.getAmount().negate());
        assertEquals(reversedItemCheck1.getLinkedItemId(), item1.getId());

        InvoiceItem newItemCheck1 = proposed.get(1);
        assertEquals(newItemCheck1.getInvoiceId(), invoiceId);
        assertEquals(newItemCheck1.getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(newItemCheck1.getAmount(), amount2);

        InvoiceItem creditItemCheck = proposed.get(2);
        assertEquals(creditItemCheck.getInvoiceId(), existingInvoiceId);
        assertEquals(creditItemCheck.getInvoiceItemType(), InvoiceItemType.CBA_ADJ);
        assertEquals(creditItemCheck.getAmount(), amount2.add(rate1.negate()));
    }

    @Test(groups = {"fast"})
    public void testConsumeNotEnoughExistingCredit() {
        testConsumeCreditInternal(new BigDecimal("12.00"), new BigDecimal("-10.00"));
    }

    @Test(groups = {"fast"})
    public void testConsumeTooMuchExistingCredit() {
        testConsumeCreditInternal(new BigDecimal("7.00"), new BigDecimal("-7.00"));
    }

    private void testConsumeCreditInternal(BigDecimal newRate, BigDecimal expectedNewCba) {
        DateTime startDate = clock.getUTCNow();
        DateTime endDate = startDate.plusDays(30);
        DateTime nextEndDate = startDate.plusMonths(1);


        BigDecimal rate1 = new BigDecimal("20.00");
        BigDecimal amount1 = rate1;

        BigDecimal rate2 = new BigDecimal("10.00");
        BigDecimal amount2 = rate2;


        UUID firstInvoiceId  = UUID.randomUUID();
        List<InvoiceItem> existing =  new LinkedList<InvoiceItem>();
        BigDecimal pcba1 = new BigDecimal("10.00");

        InvoiceItem item1 = new RecurringInvoiceItem(firstInvoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount1, rate1, currency, null);
        InvoiceItem reversedItem1 = new RepairAdjInvoiceItem(firstInvoiceId, accountId, startDate, nextEndDate, amount1.negate(), currency, item1.getId());
        InvoiceItem newItem1 = new RecurringInvoiceItem(firstInvoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount2, rate2, currency, null);
        InvoiceItem cba1 = new CreditBalanceAdjInvoiceItem(firstInvoiceId, accountId, startDate, pcba1, currency);
        existing.add(item1);
        existing.add(reversedItem1);
        existing.add(newItem1);
        existing.add(cba1);


        BigDecimal newRate2 = newRate;
        BigDecimal newAmount2 = newRate2;

        List<InvoiceItem> proposed =  new LinkedList<InvoiceItem>();
        InvoiceItem item2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate.plusMonths(1), endDate.plusMonths(1), newAmount2, newRate2, currency, null);
        proposed.add(item2);

        gen.consumeExistingCredit(invoiceId, firstInvoiceId, existing, proposed, currency);
        assertEquals(proposed.size(), 2);
        InvoiceItem item2Check = proposed.get(0);
        assertEquals(item2Check.getInvoiceId(), invoiceId);
        assertEquals(item2Check.getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(item2Check.getAmount(), newAmount2);

        InvoiceItem cbaCheck = proposed.get(1);
        assertEquals(cbaCheck.getInvoiceId(), invoiceId);
        assertEquals(cbaCheck.getInvoiceItemType(), InvoiceItemType.CBA_ADJ);
        assertEquals(cbaCheck.getAmount(), expectedNewCba);
    }
}
