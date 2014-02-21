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
import com.ning.billing.invoice.model.RecurringInvoiceItem;
import com.ning.billing.invoice.model.RepairAdjInvoiceItem;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestDefaultInvoiceGeneratorRepairUnit extends InvoiceTestSuiteNoDB {

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

    /*********************************************  addRepairsForItem logic ********************************/

    //                 repairedItem
    // |-----------------------------------------------|
    //
    //   proposed1     (result ->) repair    proposed2
    // |-------------|--------------------|------------|

    @Test(groups = "fast")
    public void testAddRepairedItem1() {

        final LocalDate startDate = new LocalDate(2013, 12, 1);
        final LocalDate endDate = new LocalDate(2014, 12, 1);
        final LocalDate endDateProposed1 = new LocalDate(2014, 1, 1);
        final LocalDate startProposed2 = new LocalDate(2014, 11, 1);

        final BigDecimal rate = new BigDecimal("120.00");
        final BigDecimal amount = rate;

        final InvoiceItem repairedItem = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount, rate, currency);

        final RepairAdjInvoiceItem candidateRepairItem = new RepairAdjInvoiceItem(repairedItem.getInvoiceId(), repairedItem.getAccountId(), repairedItem.getStartDate(), repairedItem.getEndDate(), repairedItem.getAmount().negate(), repairedItem.getCurrency(), repairedItem.getId());
        final List<InvoiceItem> proposed = new LinkedList<InvoiceItem>();
        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDateProposed1, BigDecimal.TEN, rate, currency);
        proposed.add(proposed1);
        final InvoiceItem proposed2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startProposed2, endDate, BigDecimal.TEN, rate, currency);
        proposed.add(proposed2);

        defaultInvoiceGenerator.addRepairsForItem(repairedItem, candidateRepairItem, proposed);

        assertEquals(proposed.size(), 1);
        assertEquals(proposed.get(0).getStartDate(), endDateProposed1);
        assertEquals(proposed.get(0).getEndDate(), startProposed2);
        assertEquals(proposed.get(0).getLinkedItemId(), repairedItem.getId());
        assertEquals(proposed.get(0).getAmount(), new BigDecimal("-100.00"));
        assertEquals(proposed.get(0).getInvoiceItemType(), InvoiceItemType.REPAIR_ADJ);
    }

    //                 repairedItem
    // |-----------------------------------------------|
    //
    //   proposed1       (result ->) repair
    // |-------------|---------------------------------|

    @Test(groups = "fast")
    public void testAddRepairedItem2() {

        final LocalDate startDate = new LocalDate(2013, 12, 1);
        final LocalDate endDate = new LocalDate(2014, 12, 1);
        final LocalDate endDateProposed1 = new LocalDate(2014, 1, 1);

        final BigDecimal rate = new BigDecimal("120.00");
        final BigDecimal amount = rate;

        final InvoiceItem repairedItem = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount, rate, currency);

        final RepairAdjInvoiceItem candidateRepairItem = new RepairAdjInvoiceItem(repairedItem.getInvoiceId(), repairedItem.getAccountId(), repairedItem.getStartDate(), repairedItem.getEndDate(), repairedItem.getAmount().negate(), repairedItem.getCurrency(), repairedItem.getId());
        final List<InvoiceItem> proposed = new LinkedList<InvoiceItem>();
        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDateProposed1, BigDecimal.TEN, rate, currency);
        proposed.add(proposed1);

        defaultInvoiceGenerator.addRepairsForItem(repairedItem, candidateRepairItem, proposed);

        assertEquals(proposed.size(), 1);
        assertEquals(proposed.get(0).getStartDate(), endDateProposed1);
        assertEquals(proposed.get(0).getEndDate(), endDate);
        assertEquals(proposed.get(0).getLinkedItemId(), repairedItem.getId());
        assertEquals(proposed.get(0).getAmount(), new BigDecimal("-110.00"));
        assertEquals(proposed.get(0).getInvoiceItemType(), InvoiceItemType.REPAIR_ADJ);
    }


    //                 repairedItem
    // |-----------------------------------------------|
    //
    // (result ->) repair                  proposed1
    // |----------------------------------|-----------|

    @Test(groups = "fast")
    public void testAddRepairedItem3() {

        final LocalDate startDate = new LocalDate(2013, 12, 1);
        final LocalDate endDate = new LocalDate(2014, 12, 1);
        final LocalDate startDateProposed1 = new LocalDate(2014, 1, 1);

        final BigDecimal rate = new BigDecimal("120.00");
        final BigDecimal amount = rate;

        final InvoiceItem repairedItem = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount, rate, currency);

        final RepairAdjInvoiceItem candidateRepairItem = new RepairAdjInvoiceItem(repairedItem.getInvoiceId(), repairedItem.getAccountId(), repairedItem.getStartDate(), repairedItem.getEndDate(), repairedItem.getAmount().negate(), repairedItem.getCurrency(), repairedItem.getId());
        final List<InvoiceItem> proposed = new LinkedList<InvoiceItem>();
        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDateProposed1, endDate, BigDecimal.TEN, rate, currency);
        proposed.add(proposed1);

        defaultInvoiceGenerator.addRepairsForItem(repairedItem, candidateRepairItem, proposed);

        assertEquals(proposed.size(), 1);
        assertEquals(proposed.get(0).getStartDate(), startDate);
        assertEquals(proposed.get(0).getEndDate(), startDateProposed1);
        assertEquals(proposed.get(0).getLinkedItemId(), repairedItem.getId());
        assertEquals(proposed.get(0).getAmount(), new BigDecimal("-110.00"));
        assertEquals(proposed.get(0).getInvoiceItemType(), InvoiceItemType.REPAIR_ADJ);
    }

    //                 repairedItem
    // |---------------------------------------------------|
    //
    //   proposed1  repair1   proposed2   repair2  proposed3
    // |----------|-------- |-----------|--------|----------|

    @Test(groups = "fast")
    public void testAddRepairedItem4() {

        final LocalDate startDate = new LocalDate(2013, 12, 1);
        final LocalDate endDate = new LocalDate(2014, 12, 1);
        final LocalDate endDateProposed1 = new LocalDate(2014, 1, 1);
        final LocalDate startDateProposed2 = new LocalDate(2014, 8, 1);
        final LocalDate endDateProposed2 = new LocalDate(2014, 9, 1);
        final LocalDate startDateProposed3 = new LocalDate(2014, 11, 1);

        final BigDecimal rate = new BigDecimal("120.00");
        final BigDecimal amount = rate;

        final InvoiceItem repairedItem = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount, rate, currency);

        final RepairAdjInvoiceItem candidateRepairItem = new RepairAdjInvoiceItem(repairedItem.getInvoiceId(), repairedItem.getAccountId(), repairedItem.getStartDate(), repairedItem.getEndDate(), repairedItem.getAmount().negate(), repairedItem.getCurrency(), repairedItem.getId());
        final List<InvoiceItem> proposed = new LinkedList<InvoiceItem>();
        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDateProposed1, BigDecimal.TEN, rate, currency);
        proposed.add(proposed1);
        final InvoiceItem proposed2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDateProposed2, endDateProposed2, BigDecimal.TEN, rate, currency);
        proposed.add(proposed2);
        final InvoiceItem proposed3 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDateProposed3, endDate, BigDecimal.TEN, rate, currency);
        proposed.add(proposed3);

        defaultInvoiceGenerator.addRepairsForItem(repairedItem, candidateRepairItem, proposed);

        assertEquals(proposed.size(), 2);
        assertEquals(proposed.get(0).getStartDate(), endDateProposed1);
        assertEquals(proposed.get(0).getEndDate(), startDateProposed2);
        assertEquals(proposed.get(0).getLinkedItemId(), repairedItem.getId());
        assertEquals(proposed.get(0).getAmount(), new BigDecimal("-69.894000"));
        assertEquals(proposed.get(0).getInvoiceItemType(), InvoiceItemType.REPAIR_ADJ);

        assertEquals(proposed.get(1).getStartDate(), endDateProposed2);
        assertEquals(proposed.get(1).getEndDate(), startDateProposed3);
        assertEquals(proposed.get(1).getLinkedItemId(), repairedItem.getId());
        assertEquals(proposed.get(1).getAmount(), new BigDecimal("-20.106000"));
        assertEquals(proposed.get(1).getInvoiceItemType(), InvoiceItemType.REPAIR_ADJ);
    }


    /*********************************************  addRepairsForItems logic ********************************/

    @Test(groups = "fast")
    public void testAddRepairedItemsItemsRecurringPrice() {
        final LocalDate startDate = new LocalDate(2013, 12, 13);
        final LocalDate endDate = new LocalDate(2014, 1, 12);
        final LocalDate nextEndDate = new LocalDate(2014, 1, 13);

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

        defaultInvoiceGenerator.addRepairItems(existing, proposed);
        assertEquals(existing.size(), 1);
        assertEquals(proposed.size(), 2);
        assertEquals(proposed.get(0), other);

        final InvoiceItem newItem2 = proposed.get(1);
        assertEquals(newItem2.getInvoiceId(), firstInvoiceId);
        assertEquals(newItem2.getInvoiceItemType(), InvoiceItemType.REPAIR_ADJ);
        assertEquals(newItem2.getAmount(), item1.getAmount().negate());
        assertEquals(newItem2.getLinkedItemId(), item1.getId());
        assertEquals(newItem2.getStartDate(), startDate);
        assertEquals(newItem2.getEndDate(), endDate);
    }

    /*********************************************  isRepareeItemForRepairedItem logic ********************************/

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

        assertFalse(defaultInvoiceGenerator.isRepareeItemForRepairedItem(silver, silver));
        assertFalse(defaultInvoiceGenerator.isRepareeItemForRepairedItem(silver, gold));
        assertTrue(defaultInvoiceGenerator.isRepareeItemForRepairedItem(silver, actualSilver));
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

        assertFalse(defaultInvoiceGenerator.isRepareeItemForRepairedItem(annual, annual));
        assertFalse(defaultInvoiceGenerator.isRepareeItemForRepairedItem(annual, monthly));
    }
}
