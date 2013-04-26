/*
 * Copyright 2010-2013 Ning, Inc.
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
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.InvoiceTestSuiteNoDB;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.invoice.model.FixedPriceInvoiceItem;
import com.ning.billing.invoice.model.RecurringInvoiceItem;
import com.ning.billing.invoice.model.RepairAdjInvoiceItem;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestDefaultInvoiceGeneratorUnit extends InvoiceTestSuiteNoDB {

    private final UUID invoiceId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID subscriptionId = UUID.randomUUID();
    private final UUID bundleId = UUID.randomUUID();
    private final String planName = "my-plan";
    private final String phaseName = "my-phase";
    private final Currency currency = Currency.USD;


    @Test(groups = "fast")
    public void testRemoveCancellingInvoiceItemsFixedPrice() {
        final LocalDate startDate = clock.getUTCToday();
        final LocalDate endDate = startDate.plusDays(30);
        final LocalDate nextEndDate = startDate.plusMonths(1);

        final BigDecimal amount = new BigDecimal("12.00");
        final BigDecimal rate2 = new BigDecimal("14.85");
        final BigDecimal amount2 = rate2;
        final List<InvoiceItem> items = new LinkedList<InvoiceItem>();
        final InvoiceItem item1 = new FixedPriceInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, amount, currency);
        items.add(item1);
        items.add(new RepairAdjInvoiceItem(invoiceId, accountId, startDate, endDate, amount.negate(), currency, item1.getId()));
        items.add(new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, endDate, nextEndDate, amount2, rate2, currency));
        ((DefaultInvoiceGenerator) generator).removeRepairedAndRepairInvoiceItems(items, new LinkedList<InvoiceItem>());
        assertEquals(items.size(), 1);
        final InvoiceItem leftItem = items.get(0);
        assertEquals(leftItem.getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(leftItem.getAmount(), amount2);
    }

    @Test(groups = "fast")
    public void testRemoveCancellingInvoiceItemsRecurringPrice() {
        final LocalDate startDate = clock.getUTCToday();
        final LocalDate endDate = startDate.plusDays(30);
        final LocalDate nextEndDate = startDate.plusMonths(1);

        final BigDecimal rate1 = new BigDecimal("12.00");
        final BigDecimal amount1 = rate1;
        final BigDecimal rate2 = new BigDecimal("14.85");
        final BigDecimal amount2 = rate2;
        final List<InvoiceItem> items = new LinkedList<InvoiceItem>();
        final InvoiceItem item1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount1, rate1, currency);
        items.add(item1);
        items.add(new RepairAdjInvoiceItem(invoiceId, accountId, startDate, endDate, amount1.negate(), currency, item1.getId()));
        items.add(new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, endDate, nextEndDate, amount2, rate2, currency));
        ((DefaultInvoiceGenerator) generator).removeRepairedAndRepairInvoiceItems(items, new LinkedList<InvoiceItem>());
        assertEquals(items.size(), 1);
        final InvoiceItem leftItem = items.get(0);
        assertEquals(leftItem.getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(leftItem.getAmount(), amount2);
    }

    @Test(groups = "fast")
    public void testRemoveDuplicatedInvoiceItemsShouldNotThrowIllegalStateExceptionOne() {
        final LocalDate startDate = clock.getUTCToday();
        final LocalDate endDate = startDate.plusMonths(1);
        final BigDecimal amount = new BigDecimal("12.00");

        // More items in existing than proposed
        final List<InvoiceItem> existing = new LinkedList<InvoiceItem>();
        final InvoiceItem item1 = new FixedPriceInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, amount, currency);
        final InvoiceItem item2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount, amount, currency);
        existing.add(item1);
        existing.add(item2);

        final List<InvoiceItem> proposed = new LinkedList<InvoiceItem>();
        final InvoiceItem other1 = new FixedPriceInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, amount, currency);
        proposed.add(other1);

        ((DefaultInvoiceGenerator) generator).removeMatchingInvoiceItems(proposed, existing);
        assertEquals(existing.size(), 1);
        assertEquals(proposed.size(), 0);
    }

    @Test(groups = "fast")
    public void testRemoveDuplicatedInvoiceItemsShouldNotThrowIllegalStateExceptionTwo() {
        final LocalDate startDate = clock.getUTCToday();
        final LocalDate endDate = startDate.plusMonths(1);
        final BigDecimal amount = new BigDecimal("12.00");

        // More items in proposed than existing
        final List<InvoiceItem> existing = new LinkedList<InvoiceItem>();
        final InvoiceItem item1 = new FixedPriceInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, amount, currency);
        existing.add(item1);

        final List<InvoiceItem> proposed = new LinkedList<InvoiceItem>();
        final InvoiceItem other1 = new FixedPriceInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, amount, currency);
        final InvoiceItem other2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount, amount, currency);
        proposed.add(other1);
        proposed.add(other2);

        ((DefaultInvoiceGenerator) generator).removeMatchingInvoiceItems(proposed, existing);
        assertEquals(existing.size(), 0);
        assertEquals(proposed.size(), 1);
    }

    @Test(groups = "fast")
    public void testRemoveDuplicatedInvoiceItemsShouldNotThrowIllegalStateExceptionThree() {
        final LocalDate startDate = clock.getUTCToday();
        final LocalDate endDate = startDate.plusMonths(1);
        final BigDecimal amount = new BigDecimal("12.00");

        // Bunch of duplicated items
        final List<InvoiceItem> existing = new LinkedList<InvoiceItem>();
        final InvoiceItem item1 = new FixedPriceInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, amount, currency);
        final InvoiceItem item2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount, amount, currency);
        existing.add(item1);
        existing.add(item2);
        existing.add(item1);

        final List<InvoiceItem> proposed = new LinkedList<InvoiceItem>();
        final InvoiceItem other1 = new FixedPriceInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, amount, currency);
        final InvoiceItem other2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount, amount, currency);
        proposed.add(item1);
        proposed.add(other1);
        proposed.add(other1);
        proposed.add(other2);

        ((DefaultInvoiceGenerator) generator).removeMatchingInvoiceItems(proposed, existing);
        assertEquals(existing.size(), 0);
        assertEquals(proposed.size(), 1);
    }

    @Test(groups = "fast")
    public void testRemoveDuplicatedInvoiceItemsFixedPrice() {
        final LocalDate startDate = clock.getUTCToday();
        final LocalDate endDate = startDate.plusDays(30);
        final LocalDate nextEndDate = startDate.plusMonths(1);

        final BigDecimal amount1 = new BigDecimal("12.00");

        final BigDecimal amount2 = new BigDecimal("14.85");

        final BigDecimal rate3 = new BigDecimal("14.85");
        final BigDecimal amount3 = rate3;

        final List<InvoiceItem> existing = new LinkedList<InvoiceItem>();
        final InvoiceItem item1 = new FixedPriceInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, amount1, currency);
        existing.add(item1);

        final List<InvoiceItem> proposed = new LinkedList<InvoiceItem>();
        final InvoiceItem other = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, endDate, nextEndDate, amount3, rate3, currency);
        proposed.add(item1);
        proposed.add(other);

        ((DefaultInvoiceGenerator) generator).removeMatchingInvoiceItems(proposed, existing);
        assertEquals(existing.size(), 0);
        assertEquals(proposed.size(), 1);
        final InvoiceItem leftItem = proposed.get(0);
        assertEquals(leftItem.getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(leftItem.getAmount(), amount2);
    }

    @Test(groups = "fast")
    public void testRemoveDuplicatedInvoiceItemsRecurringPrice() {
        final LocalDate startDate = clock.getUTCToday();
        final LocalDate endDate = startDate.plusDays(30);
        final LocalDate nextEndDate = startDate.plusMonths(1);

        final BigDecimal rate1 = new BigDecimal("12.00");
        final BigDecimal amount1 = rate1;

        final BigDecimal rate2 = new BigDecimal("14.85");
        final BigDecimal amount2 = rate2;

        final List<InvoiceItem> existing = new LinkedList<InvoiceItem>();
        final InvoiceItem item1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount1, rate1, currency);
        existing.add(item1);

        final List<InvoiceItem> proposed = new LinkedList<InvoiceItem>();
        final InvoiceItem other = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, endDate, nextEndDate, amount2, rate2, currency);
        proposed.add(item1);
        proposed.add(other);

        ((DefaultInvoiceGenerator) generator).removeMatchingInvoiceItems(proposed, existing);
        assertEquals(existing.size(), 0);
        assertEquals(proposed.size(), 1);
        final InvoiceItem leftItem = proposed.get(0);
        assertEquals(leftItem.getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(leftItem.getAmount(), amount2);
    }

    // STEPH same as testRemoveCancellingInvoiceItemsFixedPrice: should we have one for FixedPrice?
    @Test(groups = "fast")
    public void testAddRepairedItemsItemsRecurringPrice() {
        final LocalDate startDate = clock.getUTCToday();
        final LocalDate endDate = startDate.plusDays(30);
        final LocalDate nextEndDate = startDate.plusMonths(1);

        final BigDecimal rate1 = new BigDecimal("12.00");
        final BigDecimal amount1 = rate1;

        final BigDecimal rate2 = new BigDecimal("14.85");
        final BigDecimal amount2 = rate2;

        final UUID firstInvoiceId = UUID.randomUUID();
        final List<InvoiceItem> existing = new LinkedList<InvoiceItem>();
        final InvoiceItem item1 = new RecurringInvoiceItem(firstInvoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount1, rate1, currency);
        existing.add(item1);

        final List<InvoiceItem> proposed = new LinkedList<InvoiceItem>();
        final InvoiceItem other = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, endDate, nextEndDate, amount2, rate2, currency);
        proposed.add(other);

        ((DefaultInvoiceGenerator) generator).addRepairItems(existing, proposed);
        assertEquals(existing.size(), 1);
        assertEquals(proposed.size(), 2);
        final InvoiceItem leftItem1 = proposed.get(0);
        assertEquals(leftItem1.getInvoiceId(), invoiceId);
        assertEquals(leftItem1.getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(leftItem1.getAmount(), amount2);

        final InvoiceItem newItem2 = proposed.get(1);
        assertEquals(newItem2.getInvoiceId(), firstInvoiceId);
        assertEquals(newItem2.getInvoiceItemType(), InvoiceItemType.REPAIR_ADJ);
        assertEquals(newItem2.getAmount(), item1.getAmount().negate());
        assertEquals(newItem2.getLinkedItemId(), item1.getId());
    }

    @Test(groups = "fast")
    public void testShouldFindRepareeForPartialRepairs() throws Exception {
        final LocalDate startDate = new LocalDate(2012, 5, 1);
        final LocalDate endDate = new LocalDate(2012, 6, 1);
        // Repaired item
        final InvoiceItem silver = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, BigDecimal.TEN, BigDecimal.TEN, currency);

        // Reparee item
        final LocalDate actualEndDateSilver = new LocalDate(2012, 5, 10);
        final InvoiceItem actualSilver = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, actualEndDateSilver, new BigDecimal("3"), BigDecimal.TEN, currency);

        // New item
        final InvoiceItem gold = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, "new-" + planName, phaseName, actualEndDateSilver, endDate, BigDecimal.TEN, new BigDecimal("15"), currency);

        assertFalse(((DefaultInvoiceGenerator) generator).isRepareeItemForRepairedItem(silver, silver));
        assertFalse(((DefaultInvoiceGenerator) generator).isRepareeItemForRepairedItem(silver, gold));
        assertTrue(((DefaultInvoiceGenerator) generator).isRepareeItemForRepairedItem(silver, actualSilver));
    }

    @Test(groups = "fast")
    public void testShouldntFindRepareeForFullRepairs() throws Exception {
        final LocalDate startDate = new LocalDate(2012, 5, 1);
        final LocalDate endDate = new LocalDate(2013, 5, 1);
        // Repaired item
        final InvoiceItem annual = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, BigDecimal.TEN, BigDecimal.TEN, currency);

        // There is no reparee - full repair

        // New item
        final LocalDate endDate2 = new LocalDate(2012, 6, 1);
        final InvoiceItem monthly = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, "new-" + planName, phaseName, startDate, endDate2, BigDecimal.TEN, BigDecimal.TEN, currency);

        assertFalse(((DefaultInvoiceGenerator) generator).isRepareeItemForRepairedItem(annual, annual));
        assertFalse(((DefaultInvoiceGenerator) generator).isRepareeItemForRepairedItem(annual, monthly));
    }
}
