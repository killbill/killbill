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
import org.testng.annotations.BeforeClass;
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

    private DefaultInvoiceGenerator defaultInvoiceGenerator;

    @BeforeClass(groups = "fast")
    protected void beforeClass() throws Exception {
        super.beforeClass();
        this.defaultInvoiceGenerator = (DefaultInvoiceGenerator) generator;
    }

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
        defaultInvoiceGenerator.removeRepairedAndRepairInvoiceItems(items, new LinkedList<InvoiceItem>(), new LinkedList<InvoiceItem>());
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
        defaultInvoiceGenerator.removeRepairedAndRepairInvoiceItems(items, new LinkedList<InvoiceItem>(), new LinkedList<InvoiceItem>());
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

        defaultInvoiceGenerator.removeMatchingInvoiceItems(proposed, existing);
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

        defaultInvoiceGenerator.removeMatchingInvoiceItems(proposed, existing);
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

        defaultInvoiceGenerator.removeMatchingInvoiceItems(proposed, existing);
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

        defaultInvoiceGenerator.removeMatchingInvoiceItems(proposed, existing);
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

        defaultInvoiceGenerator.removeMatchingInvoiceItems(proposed, existing);
        assertEquals(existing.size(), 0);
        assertEquals(proposed.size(), 1);
        final InvoiceItem leftItem = proposed.get(0);
        assertEquals(leftItem.getInvoiceItemType(), InvoiceItemType.RECURRING);
        assertEquals(leftItem.getAmount(), amount2);
    }


}