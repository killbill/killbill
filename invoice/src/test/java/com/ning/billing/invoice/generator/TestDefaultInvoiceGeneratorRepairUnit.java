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

    /*********************************************  addRepairItem logic ********************************/

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

        defaultInvoiceGenerator.addRepairItem(repairedItem, candidateRepairItem, proposed);

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

        defaultInvoiceGenerator.addRepairItem(repairedItem, candidateRepairItem, proposed);

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

        defaultInvoiceGenerator.addRepairItem(repairedItem, candidateRepairItem, proposed);

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

        defaultInvoiceGenerator.addRepairItem(repairedItem, candidateRepairItem, proposed);

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


    /*********************************************  addRepairItems logic ********************************/

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

    /*********************************************  isRepareeIncludedInRepair logic ********************************/

    // Check for an item whose start and endDate exactly fit in repair item
    @Test(groups = "fast")
    public void testIsRepareeIncludedInRepair1() throws Exception {
        final LocalDate startDate = new LocalDate(2012, 5, 1);
        final LocalDate startRepair = new LocalDate(2012, 8, 1);
        final LocalDate endDate = new LocalDate(2013, 5, 1);

        // Repaired item
        final InvoiceItem repairedItem = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, BigDecimal.TEN, BigDecimal.TEN, currency);

        final InvoiceItem repairItem = new RepairAdjInvoiceItem(invoiceId, accountId, startRepair, endDate, BigDecimal.ONE, currency, repairedItem.getId());

        final InvoiceItem invoiceItem = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startRepair, endDate, BigDecimal.TEN, BigDecimal.TEN, currency);
        assertTrue(defaultInvoiceGenerator.isRepareeIncludedInRepair(repairItem, repairedItem.getSubscriptionId(), invoiceItem));
    }

    // Check for an item whose start is greater than repair startDate
    @Test(groups = "fast")
    public void testIsRepareeIncludedInRepair2() throws Exception {
        final LocalDate startDate = new LocalDate(2012, 5, 1);
        final LocalDate startRepair = new LocalDate(2012, 8, 1);
        final LocalDate endDate = new LocalDate(2013, 5, 1);

        // Repaired item
        final InvoiceItem repairedItem = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, BigDecimal.TEN, BigDecimal.TEN, currency);

        final InvoiceItem repairItem = new RepairAdjInvoiceItem(invoiceId, accountId, startRepair, endDate, BigDecimal.ONE, currency, repairedItem.getId());

        final InvoiceItem invoiceItem = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startRepair.plusDays(1), endDate, BigDecimal.TEN, BigDecimal.TEN, currency);
        assertTrue(defaultInvoiceGenerator.isRepareeIncludedInRepair(repairItem, repairedItem.getSubscriptionId(), invoiceItem));
    }

    // Check for an item whose endDate is lower than repair endDate
    @Test(groups = "fast")
    public void testIsRepareeIncludedInRepair3() throws Exception {
        final LocalDate startDate = new LocalDate(2012, 5, 1);
        final LocalDate startRepair = new LocalDate(2012, 8, 1);
        final LocalDate endDate = new LocalDate(2013, 5, 1);

        // Repaired item
        final InvoiceItem repairedItem = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, BigDecimal.TEN, BigDecimal.TEN, currency);

        final InvoiceItem repairItem = new RepairAdjInvoiceItem(invoiceId, accountId, startRepair, endDate, BigDecimal.ONE, currency, repairedItem.getId());

        final InvoiceItem invoiceItem = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startRepair, endDate.minusDays(1), BigDecimal.TEN, BigDecimal.TEN, currency);
        assertTrue(defaultInvoiceGenerator.isRepareeIncludedInRepair(repairItem, repairedItem.getSubscriptionId(), invoiceItem));
    }

    // Check for an item whose endDate is lower than repair endDate
    @Test(groups = "fast")
    public void testIsRepareeIncludedInRepair4() throws Exception {
        final LocalDate startDate = new LocalDate(2012, 5, 1);
        final LocalDate startRepair = new LocalDate(2012, 8, 1);
        final LocalDate endDate = new LocalDate(2013, 5, 1);

        // Repaired item
        final InvoiceItem repairedItem = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, BigDecimal.TEN, BigDecimal.TEN, currency);

        final InvoiceItem repairItem = new RepairAdjInvoiceItem(invoiceId, accountId, startRepair, endDate, BigDecimal.ONE, currency, repairedItem.getId());

        final InvoiceItem invoiceItem = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startRepair, endDate.minusDays(1), BigDecimal.TEN, BigDecimal.TEN, currency);
        assertTrue(defaultInvoiceGenerator.isRepareeIncludedInRepair(repairItem, repairedItem.getSubscriptionId(), invoiceItem));
    }

    // Check for an item whose endDate is greater than repair endDate
    @Test(groups = "fast")
    public void testIsRepareeIncludedInRepair5() throws Exception {
        final LocalDate startDate = new LocalDate(2012, 5, 1);
        final LocalDate startRepair = new LocalDate(2012, 8, 1);
        final LocalDate endDate = new LocalDate(2013, 5, 1);

        // Repaired item
        final InvoiceItem repairedItem = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, BigDecimal.TEN, BigDecimal.TEN, currency);

        final InvoiceItem repairItem = new RepairAdjInvoiceItem(invoiceId, accountId, startRepair, endDate, BigDecimal.ONE, currency, repairedItem.getId());

        final InvoiceItem invoiceItem = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startRepair, endDate.plusDays(1), BigDecimal.TEN, BigDecimal.TEN, currency);
        assertFalse(defaultInvoiceGenerator.isRepareeIncludedInRepair(repairItem, repairedItem.getSubscriptionId(), invoiceItem));
    }

    @Test(groups = "fast")
    public void testIsRepareeIncludedInRepairWrongSubscription() throws Exception {
        final LocalDate startDate = new LocalDate(2012, 5, 1);
        final LocalDate startRepair = new LocalDate(2012, 8, 1);
        final LocalDate endDate = new LocalDate(2013, 5, 1);

        // Repaired item
        final InvoiceItem repairedItem = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, BigDecimal.TEN, BigDecimal.TEN, currency);

        final InvoiceItem repairItem = new RepairAdjInvoiceItem(invoiceId, accountId, startRepair, endDate, BigDecimal.ONE, currency, repairedItem.getId());

        final UUID otherSubscriptionId = UUID.fromString("a9cbee45-5796-4dc5-be1f-7c020518460d");
        final InvoiceItem invoiceItem = new RecurringInvoiceItem(invoiceId, accountId, bundleId, otherSubscriptionId, planName, phaseName, startRepair, endDate, BigDecimal.TEN, BigDecimal.TEN, currency);
        assertFalse(defaultInvoiceGenerator.isRepareeIncludedInRepair(repairItem, repairedItem.getSubscriptionId(), invoiceItem));
    }

    /***********************************  removeProposedRepareesForPartialrepair logic ********************************/

    //
    // Test removal of proposed item after a repair, scenario 1:
    //
    // 1. Initially bill the full period:
    //                 repairedItem
    // |-----------------------------------------------|
    //
    // 2. Block subscription -> repair
    //   reparee1       repaired
    // |-------------|---------------------------------|
    //
    // 3. Unblock later -> needs to bill for the last part:
    //    * proposed items = {reparee1; reparee2}
    //
    //   reparee1                           reparee2
    // |-------------|--------------------|------------|
    //
    // => Code should detect that reparee1 was already accounted for, but not reparee2
    //    and therefore only reparee2 should remain in the proposed list.
    //
    @Test(groups = "fast")
    public void testRemoveProposedRepareeForPartialRepair1() {

        final LocalDate startDate = new LocalDate(2012, 6, 30);
        final LocalDate blockDate = new LocalDate(2012, 7, 10);
        final LocalDate unblockDate = new LocalDate(2012, 7, 23);
        final LocalDate endDate = new LocalDate(2012, 7, 31);

        final BigDecimal someAmount = new BigDecimal("100.00");

        final List<InvoiceItem> existing = new LinkedList<InvoiceItem>();
        final InvoiceItem repairedItem = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, someAmount, someAmount, currency);
        existing.add(repairedItem);

        final List<InvoiceItem> proposed = new LinkedList<InvoiceItem>();
        final InvoiceItem reparee1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, blockDate, someAmount, someAmount, currency);
        proposed.add(reparee1);
        final InvoiceItem reparee2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, unblockDate, endDate, someAmount, someAmount, currency);
        proposed.add(reparee2);

        final InvoiceItem repairItem = new RepairAdjInvoiceItem(invoiceId, accountId, blockDate, endDate, someAmount, currency, repairedItem.getId());

        defaultInvoiceGenerator.removeProposedRepareesForPartialrepair(repairedItem, repairItem, proposed);

        assertEquals(proposed.size(), 1);
        assertTrue(proposed.get(0).equals(reparee2));
    }

    //
    // Test removal of proposed item after a repair, scenario 2:
    //
    // 1. Initially bill the full period:
    //                 repairedItem
    // |-----------------------------------------------|
    //
    // 2. Block and Unblock later (SAME TIME). Only notifies invoice at the unblock time
    //    * proposed items = {reparee1; reparee2}
    //
    //   reparee1          repaired          reparee2
    // |-------------|--------------------|------------|
    //
    // => Code should detect that both reparee1 and reparee2 were already accounted for, so
    //    nothing should stay in the proposed list.
    //
    @Test(groups = "fast")
    public void testRemoveProposedRepareeForPartialRepair2() {

        final LocalDate startDate = new LocalDate(2012, 6, 30);
        final LocalDate blockDate = new LocalDate(2012, 7, 10);
        final LocalDate unblockDate = new LocalDate(2012, 7, 23);
        final LocalDate endDate = new LocalDate(2012, 7, 31);

        final BigDecimal someAmount = new BigDecimal("100.00");

        final List<InvoiceItem> existing = new LinkedList<InvoiceItem>();
        final InvoiceItem repairedItem = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, someAmount, someAmount, currency);
        existing.add(repairedItem);

        final List<InvoiceItem> proposed = new LinkedList<InvoiceItem>();
        final InvoiceItem reparee1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, blockDate, someAmount, someAmount, currency);
        proposed.add(reparee1);
        final InvoiceItem reparee2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, unblockDate, endDate, someAmount, someAmount, currency);
        proposed.add(reparee2);

        final InvoiceItem repairItem = new RepairAdjInvoiceItem(invoiceId, accountId, blockDate, unblockDate, someAmount, currency, repairedItem.getId());

        defaultInvoiceGenerator.removeProposedRepareesForPartialrepair(repairedItem, repairItem, proposed);

        assertEquals(proposed.size(), 0);
    }
}
