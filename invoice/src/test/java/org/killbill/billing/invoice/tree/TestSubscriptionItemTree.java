/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.invoice.tree;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.InvoiceTestSuiteNoDB;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.model.FixedPriceInvoiceItem;
import org.killbill.billing.invoice.model.ItemAdjInvoiceItem;
import org.killbill.billing.invoice.model.RecurringInvoiceItem;
import org.killbill.billing.invoice.model.RepairAdjInvoiceItem;
import org.killbill.billing.util.jackson.ObjectMapper;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestSubscriptionItemTree extends InvoiceTestSuiteNoDB {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        OBJECT_MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
    }

    private final UUID invoiceId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID subscriptionId = UUID.randomUUID();
    private final UUID bundleId = UUID.randomUUID();
    private final String productName = "my-product";
    private final String planName = "my-plan";
    private final String phaseName = "my-phase";
    private final Currency currency = Currency.USD;

    @Test(groups = "fast", description = "Complex multi-level tree, mostly used to test the tree printer")
    public void testMultipleLevels() throws Exception {
        final LocalDate startDate = new LocalDate(2014, 1, 1);
        final LocalDate endDate = new LocalDate(2014, 2, 1);

        final LocalDate startRepairDate1 = new LocalDate(2014, 1, 10);
        final LocalDate endRepairDate1 = new LocalDate(2014, 1, 15);

        final LocalDate startRepairDate11 = new LocalDate(2014, 1, 10);
        final LocalDate endRepairDate12 = new LocalDate(2014, 1, 12);

        final LocalDate startRepairDate2 = new LocalDate(2014, 1, 20);
        final LocalDate endRepairDate2 = new LocalDate(2014, 1, 25);

        final LocalDate startRepairDate21 = new LocalDate(2014, 1, 22);
        final LocalDate endRepairDate22 = new LocalDate(2014, 1, 23);

        final BigDecimal rate = BigDecimal.TEN;
        final BigDecimal amount = rate;

        final InvoiceItem initial = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endDate, amount, rate, currency);

        final InvoiceItem newItem1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startRepairDate1, endRepairDate1, amount, rate, currency);
        final InvoiceItem repair1 = new RepairAdjInvoiceItem(invoiceId, accountId, startRepairDate1, endRepairDate1, amount.negate(), currency, initial.getId());

        final InvoiceItem newItem11 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startRepairDate11, endRepairDate12, amount, rate, currency);
        final InvoiceItem repair12 = new RepairAdjInvoiceItem(invoiceId, accountId, startRepairDate11, endRepairDate12, amount.negate(), currency, newItem1.getId());

        final InvoiceItem newItem2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startRepairDate2, endRepairDate2, amount, rate, currency);
        final InvoiceItem repair2 = new RepairAdjInvoiceItem(invoiceId, accountId, startRepairDate2, endRepairDate2, amount.negate(), currency, initial.getId());

        final InvoiceItem newItem21 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startRepairDate21, endRepairDate22, amount, rate, currency);
        final InvoiceItem repair22 = new RepairAdjInvoiceItem(invoiceId, accountId, startRepairDate21, endRepairDate22, amount.negate(), currency, newItem2.getId());

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.addItem(initial);
        tree.addItem(newItem1);
        tree.addItem(repair1);
        tree.addItem(newItem11);
        tree.addItem(repair12);
        tree.addItem(newItem2);
        tree.addItem(repair2);
        tree.addItem(newItem21);
        tree.addItem(repair22);

        tree.build();
        //printTree(tree);

        tree.flatten(true);
        //printTree(tree);
    }

    @Test(groups = "fast")
    public void testWithExistingSplitRecurring() {

        final BigDecimal rate = new BigDecimal("40.00");

        // We assume we have the right items for the period [2016, 9, 8; 2016, 10, 8] but split in pieces
        final InvoiceItem recurring1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, new LocalDate(2016, 9, 8), new LocalDate(2016, 9, 9), new BigDecimal("2.0"), rate, currency);
        final InvoiceItem recurring2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, new LocalDate(2016, 9, 9), new LocalDate(2016, 10, 8), new BigDecimal("38.0"), rate, currency);

        final List<InvoiceItem> existingItems = new ArrayList<InvoiceItem>();
        existingItems.add(recurring1);
        existingItems.add(recurring2);

        SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        for (InvoiceItem e : existingItems) {
            tree.addItem(e);
        }
        tree.build();
        tree.flatten(true);

        // We  generate the correct item for the period [2016, 9, 8; 2016, 10, 8]
        final InvoiceItem proposedItem = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, new LocalDate(2016, 9, 8), new LocalDate(2016, 10, 8), rate, rate, currency);
        tree.mergeProposedItem(proposedItem);
        tree.buildForMerge();


        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        expectedResult.add(proposedItem);
        expectedResult.add( new RepairAdjInvoiceItem(invoiceId, accountId, recurring1.getStartDate(), recurring1.getEndDate(), recurring1.getAmount().negate(), currency, recurring1.getId()));
        expectedResult.add( new RepairAdjInvoiceItem(invoiceId, accountId, recurring2.getStartDate(), recurring2.getEndDate(), recurring2.getAmount().negate(), currency, recurring2.getId()));

        // We expect to see the repair for initail split items and the new full item
        verifyResult(tree.getView(), expectedResult);

        // Stage II: Try again.. with existing items
        existingItems.addAll(tree.getView());

        assertEquals(existingItems.size(), 5);
        tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        for (InvoiceItem e : existingItems) {
            tree.addItem(e);
        }
        tree.build();
        tree.flatten(true);

        // Regenerate proposedItem so it has a different id and Item#isSameKind correctly detect we are proposing the same kind
        final InvoiceItem proposedItem2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, new LocalDate(2016, 9, 8), new LocalDate(2016, 10, 8), rate, rate, currency);

        tree.mergeProposedItem(proposedItem2);
        tree.buildForMerge();

        // Nothing should be generated
        Assert.assertTrue(tree.getView().isEmpty());
    }

    @Test(groups = "fast")
    public void testSimpleRepair() {

        final LocalDate startDate = new LocalDate(2014, 1, 1);
        final LocalDate endDate = new LocalDate(2014, 2, 1);

        final LocalDate repairDate = new LocalDate(2014, 1, 23);

        final BigDecimal rate1 = new BigDecimal("12.00");
        final BigDecimal amount1 = rate1;

        final BigDecimal rate2 = new BigDecimal("14.85");
        final BigDecimal amount2 = rate2;

        final InvoiceItem initial = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endDate, amount1, rate1, currency);
        final InvoiceItem newItem = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, "someelse", "someelse", "someelse", repairDate, endDate, amount2, rate2, currency);
        final InvoiceItem repair = new RepairAdjInvoiceItem(invoiceId, accountId, repairDate, endDate, amount1.negate(), currency, initial.getId());

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        final InvoiceItem expected1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, repairDate, new BigDecimal("8.52"), rate1, currency);
        expectedResult.add(expected1);
        final InvoiceItem expected2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, "someelse", "someelse", "someelse", repairDate, endDate, amount2, rate2, currency);
        expectedResult.add(expected2);

        // First test with items in order
        SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.addItem(initial);
        tree.addItem(newItem);
        tree.addItem(repair);
        tree.build();
        verifyResult(tree.getView(), expectedResult);
        tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.addItem(repair);
        tree.addItem(newItem);
        tree.addItem(initial);
        tree.build();
        verifyResult(tree.getView(), expectedResult);

        tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.addItem(repair);
        tree.addItem(initial);
        tree.addItem(newItem);
        tree.build();
        verifyResult(tree.getView(), expectedResult);
    }

    @Test(groups = "fast")
    public void testInvalidRepair() {
        final LocalDate startDate = new LocalDate(2014, 1, 1);
        final LocalDate endDate = new LocalDate(2014, 2, 1);

        final BigDecimal rate = new BigDecimal("12.00");

        final InvoiceItem initial = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endDate, rate, rate, currency);
        final InvoiceItem tooEarlyRepair = new RepairAdjInvoiceItem(invoiceId, accountId, startDate.minusDays(1), endDate, rate.negate(), currency, initial.getId());
        final InvoiceItem tooLateRepair = new RepairAdjInvoiceItem(invoiceId, accountId, startDate, endDate.plusDays(1), rate.negate(), currency, initial.getId());

        SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.addItem(initial);
        tree.addItem(tooEarlyRepair);
        try {
            tree.build();
            fail();
        } catch (final IllegalStateException e) {
        }

        tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.addItem(initial);
        tree.addItem(tooLateRepair);
        try {
            tree.build();
            fail();
        } catch (final IllegalStateException e) {
        }
    }

    @Test(groups = "fast")
    public void testDanglingRepair() {
        final LocalDate startDate = new LocalDate(2014, 1, 1);
        final LocalDate endDate = new LocalDate(2014, 2, 1);

        final BigDecimal rate = new BigDecimal("12.00");

        final InvoiceItem repair = new RepairAdjInvoiceItem(invoiceId, accountId, startDate.minusDays(1), endDate, rate.negate(), currency, UUID.randomUUID());

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.addItem(repair);
        try {
            tree.build();
            fail();
        } catch (final IllegalStateException e) {
        }
    }

    @Test(groups = "fast")
    public void testMultipleRepair() {

        final LocalDate startDate = new LocalDate(2014, 1, 1);
        final LocalDate endDate = new LocalDate(2014, 2, 1);

        final LocalDate repairDate1 = new LocalDate(2014, 1, 23);

        final LocalDate repairDate2 = new LocalDate(2014, 1, 26);

        final BigDecimal rate1 = new BigDecimal("12.00");
        final BigDecimal amount1 = rate1;

        final BigDecimal rate2 = new BigDecimal("14.85");
        final BigDecimal amount2 = rate2;

        final BigDecimal rate3 = new BigDecimal("19.23");
        final BigDecimal amount3 = rate3;

        final InvoiceItem initial = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endDate, amount1, rate1, currency);
        final InvoiceItem newItem1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, repairDate1, endDate, amount2, rate2, currency);
        final InvoiceItem repair1 = new RepairAdjInvoiceItem(invoiceId, accountId, repairDate1, endDate, amount1.negate(), currency, initial.getId());

        final InvoiceItem newItem2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, repairDate2, endDate, amount3, rate3, currency);
        final InvoiceItem repair2 = new RepairAdjInvoiceItem(invoiceId, accountId, repairDate2, endDate, amount2.negate(), currency, newItem1.getId());

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        final InvoiceItem expected1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, repairDate1, new BigDecimal("8.52"), rate1, currency);
        expectedResult.add(expected1);
        final InvoiceItem expected2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, repairDate1, repairDate2, new BigDecimal("4.95"), rate2, currency);
        expectedResult.add(expected2);
        final InvoiceItem expected3 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, repairDate2, endDate, amount3, rate3, currency);
        expectedResult.add(expected3);

        // First test with items in order
        SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.addItem(initial);
        tree.addItem(newItem1);
        tree.addItem(repair1);
        tree.addItem(newItem2);
        tree.addItem(repair2);
        tree.build();
        verifyResult(tree.getView(), expectedResult);

        tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.addItem(repair2);
        tree.addItem(newItem1);
        tree.addItem(newItem2);
        tree.addItem(repair1);
        tree.addItem(initial);
        tree.build();
        verifyResult(tree.getView(), expectedResult);

        tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.addItem(repair1);
        tree.addItem(newItem1);
        tree.addItem(initial);
        tree.addItem(repair2);
        tree.addItem(newItem2);
        tree.build();
        verifyResult(tree.getView(), expectedResult);
    }

    @Test(groups = "fast")
    public void testMultipleBlockedBillings() {

        final LocalDate startDate = new LocalDate(2014, 1, 1);
        final LocalDate endDate = new LocalDate(2014, 2, 1);

        final LocalDate blockStart1 = new LocalDate(2014, 1, 8);
        final LocalDate unblockStart1 = new LocalDate(2014, 1, 10);

        final LocalDate blockStart2 = new LocalDate(2014, 1, 17);
        final LocalDate unblockStart2 = new LocalDate(2014, 1, 23);

        final BigDecimal rate1 = new BigDecimal("12.00");
        final BigDecimal amount1 = rate1;

        final InvoiceItem initial = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endDate, amount1, rate1, currency);
        final InvoiceItem block1 = new RepairAdjInvoiceItem(invoiceId, accountId, blockStart1, unblockStart1, amount1.negate(), currency, initial.getId());
        final InvoiceItem block2 = new RepairAdjInvoiceItem(invoiceId, accountId, blockStart2, unblockStart2, amount1.negate(), currency, initial.getId());

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        final InvoiceItem expected1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, blockStart1, new BigDecimal("2.71"), rate1, currency);
        expectedResult.add(expected1);
        final InvoiceItem expected2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, unblockStart1, blockStart2, new BigDecimal("2.71"), rate1, currency);
        expectedResult.add(expected2);
        final InvoiceItem expected3 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, unblockStart2, endDate, new BigDecimal("3.48"), rate1, currency);
        expectedResult.add(expected3);

        // First test with items in order
        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.addItem(initial);
        tree.addItem(block1);
        tree.addItem(block2);
        tree.build();
        verifyResult(tree.getView(), expectedResult);
    }

    @Test(groups = "fast")
    public void testBlockAcrossPeriod() {

        final LocalDate startDate1 = new LocalDate(2014, 1, 1);
        final LocalDate blockDate = new LocalDate(2014, 1, 25);
        final LocalDate startDate2 = new LocalDate(2014, 2, 1);
        final LocalDate unblockDate = new LocalDate(2014, 2, 7);
        final LocalDate endDate = new LocalDate(2014, 3, 1);

        final BigDecimal rate1 = new BigDecimal("12.00");
        final BigDecimal amount1 = rate1;

        final InvoiceItem first = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate1, startDate2, amount1, rate1, currency);
        final InvoiceItem second = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate2, endDate, amount1, rate1, currency);
        final InvoiceItem block1 = new RepairAdjInvoiceItem(invoiceId, accountId, blockDate, startDate2, amount1.negate(), currency, first.getId());
        final InvoiceItem block2 = new RepairAdjInvoiceItem(invoiceId, accountId, startDate2, unblockDate, amount1.negate(), currency, first.getId());

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        final InvoiceItem expected1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate1, blockDate, new BigDecimal("9.29"), rate1, currency);
        final InvoiceItem expected2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, unblockDate, endDate, new BigDecimal("9.43"), rate1, currency);
        expectedResult.add(expected1);
        expectedResult.add(expected2);

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.addItem(first);
        tree.addItem(second);
        tree.addItem(block1);
        tree.addItem(block2);
        tree.build();
        verifyResult(tree.getView(), expectedResult);
    }

    @Test(groups = "fast")
    public void testAnnualFullRepairFollowedByMonthly() {

        final LocalDate startDate = new LocalDate(2014, 1, 1);
        final LocalDate firstMonthlyEndDate = new LocalDate(2014, 2, 1);
        final LocalDate secondMonthlyEndDate = new LocalDate(2014, 3, 1);
        final LocalDate endDate = new LocalDate(2015, 2, 1);

        final BigDecimal rate1 = new BigDecimal("120.00");
        final BigDecimal amount1 = rate1;

        final BigDecimal rate2 = new BigDecimal("10.00");
        final BigDecimal amount2 = rate2;

        final InvoiceItem annual = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endDate, amount1, rate1, currency);
        final InvoiceItem repair = new RepairAdjInvoiceItem(invoiceId, accountId, startDate, endDate, amount1.negate(), currency, annual.getId());
        final InvoiceItem monthly1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, "someelse","someelse", "someelse", startDate, firstMonthlyEndDate, amount2, rate2, currency);
        final InvoiceItem monthly2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, "someelse", "someelse", "someelse", firstMonthlyEndDate, secondMonthlyEndDate, amount2, rate2, currency);

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        expectedResult.add(monthly1);
        expectedResult.add(monthly2);

        SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.addItem(annual);
        tree.addItem(repair);
        tree.addItem(monthly1);
        tree.addItem(monthly2);
        tree.build();
        verifyResult(tree.getView(), expectedResult);

        tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.addItem(monthly1);
        tree.addItem(repair);
        tree.addItem(annual);
        tree.addItem(monthly2);
        tree.build();
        verifyResult(tree.getView(), expectedResult);

        tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.addItem(monthly1);
        tree.addItem(monthly2);
        tree.addItem(annual);
        tree.addItem(repair);
        tree.build();
        verifyResult(tree.getView(), expectedResult);
    }

    @Test(groups = "fast")
    public void testMonthlyToAnnualWithLeadingProRation() {

        final LocalDate startDate = new LocalDate(2014, 1, 1);
        final LocalDate endMonthly1 = new LocalDate(2014, 2, 1);
        final LocalDate endMonthly2 = new LocalDate(2014, 3, 1);
        final LocalDate switchToAnnualDate = new LocalDate(2014, 2, 23);
        final LocalDate endDate = new LocalDate(2015, 3, 1);

        final BigDecimal monthlyRate = new BigDecimal("12.00");
        final BigDecimal monthlyAmount = monthlyRate;

        final BigDecimal yearlyRate = new BigDecimal("100.00");
        final BigDecimal yearlyAmount = yearlyRate;

        final InvoiceItem monthly1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endMonthly1, monthlyAmount, monthlyRate, currency);
        final InvoiceItem monthly2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, endMonthly1, endMonthly2, monthlyAmount, monthlyRate, currency);
        final InvoiceItem repair = new RepairAdjInvoiceItem(invoiceId, accountId, switchToAnnualDate, endMonthly2, monthlyAmount.negate(), currency, monthly2.getId());
        final InvoiceItem leadingAnnualProration = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, switchToAnnualDate, endMonthly2, yearlyAmount, yearlyRate, currency);
        final InvoiceItem annual = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, endMonthly2, endDate, yearlyAmount, yearlyRate, currency);

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        expectedResult.add(monthly1);
        final InvoiceItem monthly2Prorated = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, endMonthly1, switchToAnnualDate, new BigDecimal("9.43"), monthlyRate, currency);
        expectedResult.add(monthly2Prorated);
        expectedResult.add(leadingAnnualProration);
        expectedResult.add(annual);

        // First test with items in order
        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.addItem(monthly1);
        tree.addItem(monthly2);
        tree.addItem(repair);
        tree.addItem(leadingAnnualProration);
        tree.addItem(annual);
        tree.build();
        verifyResult(tree.getView(), expectedResult);
    }

    @Test(groups = "fast")
    public void testMonthlyToAnnualWithNoProRation() {

        final LocalDate startDate = new LocalDate(2014, 1, 1);
        final LocalDate endMonthly1 = new LocalDate(2014, 2, 1);
        final LocalDate endMonthly2 = new LocalDate(2014, 3, 1);
        final LocalDate switchToAnnualDate = new LocalDate(2014, 2, 23);
        final LocalDate endDate = new LocalDate(2015, 2, 23);

        final BigDecimal monthlyRate = new BigDecimal("12.00");
        final BigDecimal monthlyAmount = monthlyRate;

        final BigDecimal yearlyRate = new BigDecimal("100.00");
        final BigDecimal yearlyAmount = yearlyRate;

        final InvoiceItem monthly1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endMonthly1, monthlyAmount, monthlyRate, currency);
        final InvoiceItem monthly2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, endMonthly1, endMonthly2, monthlyAmount, monthlyRate, currency);
        final InvoiceItem repair = new RepairAdjInvoiceItem(invoiceId, accountId, switchToAnnualDate, endMonthly2, monthlyAmount.negate(), currency, monthly2.getId());
        final InvoiceItem annual = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, switchToAnnualDate, endDate, yearlyAmount, yearlyRate, currency);

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        expectedResult.add(monthly1);
        final InvoiceItem monthly2Prorated = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, endMonthly1, switchToAnnualDate, new BigDecimal("9.43"), monthlyRate, currency);
        expectedResult.add(monthly2Prorated);
        expectedResult.add(annual);

        // First test with items in order
        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.addItem(monthly1);
        tree.addItem(monthly2);
        tree.addItem(repair);
        tree.addItem(annual);
        tree.build();
        verifyResult(tree.getView(), expectedResult);
    }

    @Test(groups = "fast", description = "https://github.com/killbill/killbill/issues/664")
    public void testOverlappingRecurring() {
        final LocalDate startDate1 = new LocalDate(2012, 5, 1);
        final LocalDate startDate2 = new LocalDate(2012, 5, 2);
        final LocalDate endDate = new LocalDate(2012, 6, 1);

        final BigDecimal rate = BigDecimal.TEN;
        final BigDecimal amount = rate;

        final InvoiceItem recurring1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate1, endDate, amount, rate, currency);
        final InvoiceItem recurring2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate2, endDate, amount, rate, currency);

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.addItem(recurring1);
        tree.addItem(recurring2);

        try {
            tree.build();
            fail();
        } catch (final IllegalStateException e) {
        }
    }

    @Test(groups = "fast", description = "https://github.com/killbill/killbill/issues/664")
    public void testDoubleBillingOnDifferentInvoices() {
        final LocalDate startDate1 = new LocalDate(2012, 5, 1);
        final LocalDate endDate = new LocalDate(2012, 6, 1);

        final BigDecimal rate = BigDecimal.TEN;
        final BigDecimal amount = rate;

        final InvoiceItem recurring1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate1, endDate, amount, rate, currency);
        final InvoiceItem recurring2 = new RecurringInvoiceItem(UUID.randomUUID(), accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate1, endDate, amount, rate, currency);

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.addItem(recurring1);
        tree.addItem(recurring2);

        try {
            tree.build();
            fail();
        } catch (final IllegalStateException e) {
        }
    }

    @Test(groups = "fast")
    public void testInvalidRepairCausingOverlappingRecurring() {
        final LocalDate startDate = new LocalDate(2014, 1, 1);
        final LocalDate endDate = new LocalDate(2014, 2, 1);

        final LocalDate repairDate1 = new LocalDate(2014, 1, 23);

        final LocalDate repairDate2 = new LocalDate(2014, 1, 26);

        final BigDecimal rate1 = new BigDecimal("12.00");
        final BigDecimal amount1 = rate1;

        final BigDecimal rate2 = new BigDecimal("14.85");
        final BigDecimal amount2 = rate2;

        final BigDecimal rate3 = new BigDecimal("19.23");
        final BigDecimal amount3 = rate3;

        final InvoiceItem initial = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endDate, amount1, rate1, currency);
        final InvoiceItem newItem1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, repairDate1, endDate, amount2, rate2, currency);
        final InvoiceItem repair1 = new RepairAdjInvoiceItem(invoiceId, accountId, repairDate1, endDate, amount1.negate(), currency, initial.getId());

        final InvoiceItem newItem2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, repairDate2, endDate, amount3, rate3, currency);
        // This repair should point to newItem1 instead
        final InvoiceItem repair2 = new RepairAdjInvoiceItem(invoiceId, accountId, repairDate2, endDate, amount2.negate(), currency, initial.getId());

        // Out-of-order insertion to show ordering doesn't matter
        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.addItem(repair1);
        tree.addItem(repair2);
        tree.addItem(initial);
        tree.addItem(newItem1);
        tree.addItem(newItem2);

        try {
            tree.build();
            fail();
        } catch (final IllegalStateException e) {
        }
    }

    @Test(groups = "fast")
    public void testInvalidRepairCausingOverlappingRecurringV2() {
        final LocalDate startDate = new LocalDate(2014, 1, 1);
        final LocalDate endDate = new LocalDate(2014, 2, 1);

        final LocalDate repairDate1 = new LocalDate(2014, 1, 23);

        final LocalDate repairDate2 = new LocalDate(2014, 1, 26);

        final BigDecimal rate1 = new BigDecimal("12.00");
        final BigDecimal amount1 = rate1;

        final BigDecimal rate2 = new BigDecimal("14.85");
        final BigDecimal amount2 = rate2;

        final InvoiceItem initial = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endDate, amount1, rate1, currency);
        final InvoiceItem newItem1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, repairDate1, endDate, amount2, rate2, currency);
        final InvoiceItem repair1 = new RepairAdjInvoiceItem(invoiceId, accountId, repairDate2, endDate, amount1.negate(), currency, initial.getId());

        // Out-of-order insertion to show ordering doesn't matter
        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.addItem(repair1);
        tree.addItem(initial);
        tree.addItem(newItem1);

        try {
            tree.build();
            fail();
        } catch (final IllegalStateException e) {
        }
    }

    // The test that first repair (repair1) and new Item (newItem1) end up being ignored.
    @Test(groups = "fast")
    public void testOverlappingRepair() {

        final LocalDate startDate = new LocalDate(2012, 5, 1);
        final LocalDate endDate = new LocalDate(2013, 5, 1);

        final LocalDate changeDate = new LocalDate(2012, 5, 11);
        final LocalDate monthlyAlignmentDate = new LocalDate(2012, 6, 1);

        final BigDecimal rate1 = new BigDecimal("2400.00");
        final BigDecimal amount1 = rate1;

        final BigDecimal rate2 = new BigDecimal("300.00");
        final BigDecimal amount2 = rate2;

        // Start with a ANNUAL plan (high rate, rate1)
        final InvoiceItem initial = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endDate, amount1, rate1, currency);

        // Change to MONTHLY plan 10 days later (low rate, rate2)
        final InvoiceItem repair1 = new RepairAdjInvoiceItem(invoiceId, accountId, changeDate, endDate, amount1.negate(), currency, initial.getId());
        final InvoiceItem newItem1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, "someelse", "someelse", "someelse", changeDate, monthlyAlignmentDate, amount2, rate2, currency);

        // On the same day, now revert back to ANNUAL
        final InvoiceItem repair2 = new RepairAdjInvoiceItem(invoiceId, accountId, changeDate, monthlyAlignmentDate, amount2.negate(), currency, newItem1.getId());
        final InvoiceItem newItem2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, changeDate, endDate, amount1, rate1, currency);

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.addItem(initial);
        tree.addItem(newItem1);
        tree.addItem(repair1);
        tree.addItem(newItem2);
        tree.addItem(repair2);

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        final InvoiceItem expected1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, changeDate, new BigDecimal("65.75"), rate1, currency);
        expectedResult.add(expected1);
        expectedResult.add(newItem2);

        tree.build();
        verifyResult(tree.getView(), expectedResult);
    }

    // Will test the case A from ItemsNodeInterval#prune logic (when we delete a node while walking the tree)
    @Test(groups = "fast")
    public void testFullRepairPruneLogic1() {

        final LocalDate startDate1 = new LocalDate(2015, 1, 1);
        final LocalDate endDate1 = new LocalDate(2015, 2, 1);


        final LocalDate startDate2 = endDate1;
        final LocalDate endDate2 = new LocalDate(2015, 3, 1);

        final LocalDate startDate3 = endDate2;
        final LocalDate endDate3 = new LocalDate(2015, 4, 1);

        final BigDecimal monthlyRate = new BigDecimal("12.00");
        final BigDecimal monthlyAmount = monthlyRate;

        final InvoiceItem monthly1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate1, endDate1, monthlyAmount, monthlyRate, currency);
        final InvoiceItem monthly2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate2, endDate2, monthlyAmount, monthlyRate, currency);
        final InvoiceItem repairMonthly2 = new RepairAdjInvoiceItem(invoiceId, accountId, startDate2, endDate2, new BigDecimal("-12.00"), currency, monthly2.getId());
        final InvoiceItem monthly3 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate3, endDate3, monthlyAmount, monthlyRate, currency);

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        expectedResult.add(monthly1);
        expectedResult.add(monthly3);

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.addItem(monthly1);
        tree.addItem(monthly2);
        tree.addItem(repairMonthly2);
        tree.addItem(monthly3);

        tree.build();
        verifyResult(tree.getView(), expectedResult);
    }

    // Will test the case A from ItemsNodeInterval#prune logic (an item is left on the interval)
    @Test(groups = "fast")
    public void testFullRepairPruneLogic2() {

        final LocalDate startDate1 = new LocalDate(2015, 1, 1);
        final LocalDate endDate1 = new LocalDate(2015, 2, 1);


        final LocalDate startDate2 = endDate1;
        final LocalDate endDate2 = new LocalDate(2015, 3, 1);

        final LocalDate startDate3 = endDate2;
        final LocalDate endDate3 = new LocalDate(2015, 4, 1);

        final BigDecimal monthlyRateInit = new BigDecimal("12.00");
        final BigDecimal monthlyAmountInit = monthlyRateInit;

        final BigDecimal monthlyRateFinal = new BigDecimal("15.00");
        final BigDecimal monthlyAmountFinal = monthlyRateFinal;

        final InvoiceItem monthly1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate1, endDate1, monthlyRateInit, monthlyAmountInit, currency);
        final InvoiceItem monthly2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate2, endDate2, monthlyRateInit, monthlyAmountInit, currency);
        final InvoiceItem repairMonthly2 = new RepairAdjInvoiceItem(invoiceId, accountId, startDate2, endDate2, new BigDecimal("-12.00"), currency, monthly2.getId());

        final InvoiceItem monthly2New = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate2, endDate2, monthlyRateFinal, monthlyAmountFinal, currency);

        final InvoiceItem monthly3 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate3, endDate3, monthlyRateFinal, monthlyAmountFinal, currency);

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        expectedResult.add(monthly1);
        expectedResult.add(monthly2New);
        expectedResult.add(monthly3);

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.addItem(monthly1);
        tree.addItem(monthly2);
        tree.addItem(repairMonthly2);
        tree.addItem(monthly2New);
        tree.addItem(monthly3);

        tree.build();
        verifyResult(tree.getView(), expectedResult);

    }

    // Will test the case B from ItemsNodeInterval#prune logic
    @Test(groups = "fast")
    public void testFullRepairByPartsPruneLogic1() {

        final LocalDate startDate = new LocalDate(2015, 2, 1);
        final LocalDate intermediate1 = new LocalDate(2015, 2, 8);
        final LocalDate intermediate2 = new LocalDate(2015, 2, 16);
        final LocalDate intermediate3 = new LocalDate(2015, 2, 24);
        final LocalDate endDate = new LocalDate(2015, 3, 1);

        final BigDecimal monthlyRate = new BigDecimal("12.00");
        final BigDecimal monthlyAmount = monthlyRate;


        final InvoiceItem monthly1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endDate, monthlyAmount, monthlyRate, currency);
        final InvoiceItem repair11 = new RepairAdjInvoiceItem(invoiceId, accountId, startDate, intermediate1, new BigDecimal("3.00"), currency, monthly1.getId());
        final InvoiceItem repair12 = new RepairAdjInvoiceItem(invoiceId, accountId, intermediate1, intermediate2, new BigDecimal("3.00"), currency, monthly1.getId());
        final InvoiceItem repair13 = new RepairAdjInvoiceItem(invoiceId, accountId, intermediate2, intermediate3, new BigDecimal("3.00"), currency, monthly1.getId());
        final InvoiceItem repair14 = new RepairAdjInvoiceItem(invoiceId, accountId, intermediate3, endDate, new BigDecimal("3.00"), currency, monthly1.getId());

        final InvoiceItem monthly2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endDate, monthlyAmount, monthlyRate, currency);
        final InvoiceItem repair21 = new RepairAdjInvoiceItem(invoiceId, accountId, startDate, intermediate1, new BigDecimal("3.00"), currency, monthly2.getId());
        final InvoiceItem repair22 = new RepairAdjInvoiceItem(invoiceId, accountId, intermediate1, intermediate2, new BigDecimal("3.00"), currency, monthly2.getId());
        final InvoiceItem repair23 = new RepairAdjInvoiceItem(invoiceId, accountId, intermediate2, intermediate3, new BigDecimal("3.00"), currency, monthly2.getId());
        final InvoiceItem repair24 = new RepairAdjInvoiceItem(invoiceId, accountId, intermediate3, endDate, new BigDecimal("3.00"), currency, monthly2.getId());

        final InvoiceItem monthly3 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endDate, monthlyAmount, monthlyRate, currency);


        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        expectedResult.add(monthly3);

        // First test with items in order
        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.addItem(monthly1);
        tree.addItem(repair11);
        tree.addItem(repair12);
        tree.addItem(repair13);
        tree.addItem(repair14);

        tree.addItem(monthly2);
        tree.addItem(repair21);
        tree.addItem(repair22);
        tree.addItem(repair23);
        tree.addItem(repair24);

        tree.addItem(monthly3);

        tree.build();
        verifyResult(tree.getView(), expectedResult);
    }

    // Will test the case A and B from ItemsNodeInterval#prune logic
    @Test(groups = "fast")
    public void testFullRepairByPartsPruneLogic2() {

        final LocalDate startDate = new LocalDate(2015, 2, 1);
        final LocalDate intermediate1 = new LocalDate(2015, 2, 8);
        final LocalDate intermediate2 = new LocalDate(2015, 2, 16);
        final LocalDate intermediate3 = new LocalDate(2015, 2, 24);
        final LocalDate endDate = new LocalDate(2015, 3, 1);

        final BigDecimal monthlyRate = new BigDecimal("12.00");
        final BigDecimal monthlyAmount = monthlyRate;


        final InvoiceItem monthly1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endDate, monthlyAmount, monthlyRate, currency);
        final InvoiceItem repair11 = new RepairAdjInvoiceItem(invoiceId, accountId, startDate, endDate, new BigDecimal("-12.00"), currency, monthly1.getId());

        final InvoiceItem monthly2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endDate, monthlyAmount, monthlyRate, currency);
        final InvoiceItem repair21 = new RepairAdjInvoiceItem(invoiceId, accountId, startDate, intermediate1, new BigDecimal("3.00"), currency, monthly2.getId());
        final InvoiceItem repair22 = new RepairAdjInvoiceItem(invoiceId, accountId, intermediate1, intermediate2, new BigDecimal("3.00"), currency, monthly2.getId());
        final InvoiceItem repair23 = new RepairAdjInvoiceItem(invoiceId, accountId, intermediate2, intermediate3, new BigDecimal("3.00"), currency, monthly2.getId());
        final InvoiceItem repair24 = new RepairAdjInvoiceItem(invoiceId, accountId, intermediate3, endDate, new BigDecimal("3.00"), currency, monthly2.getId());

        final InvoiceItem monthly3 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endDate, monthlyAmount, monthlyRate, currency);


        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        expectedResult.add(monthly3);

        // First test with items in order
        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.addItem(monthly1);
        tree.addItem(repair11);

        tree.addItem(monthly2);
        tree.addItem(repair21);
        tree.addItem(repair22);
        tree.addItem(repair23);
        tree.addItem(repair24);

        tree.addItem(monthly3);

        tree.build();
        verifyResult(tree.getView(), expectedResult);
    }

    @Test(groups = "fast")
    public void testMergeWithNoExisting() {

        final LocalDate startDate = new LocalDate(2014, 1, 1);
        final LocalDate endDate = new LocalDate(2014, 2, 1);

        final BigDecimal monthlyRate = new BigDecimal("12.00");
        final BigDecimal monthlyAmount = monthlyRate;

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endDate, monthlyAmount, monthlyRate, currency);
        tree.mergeProposedItem(proposed1);
        tree.buildForMerge();

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        expectedResult.add(proposed1);
        verifyResult(tree.getView(), expectedResult);
    }

    @Test(groups = "fast")
    public void testMergeTwoSimilarItems() {

        final LocalDate startDate = new LocalDate(2014, 1, 1);
        final LocalDate endDate = new LocalDate(2014, 2, 1);

        final BigDecimal monthlyRate = new BigDecimal("12.00");
        final BigDecimal monthlyAmount = monthlyRate;

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        final InvoiceItem monthly1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endDate, monthlyAmount, monthlyRate, currency);
        tree.addItem(monthly1);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endDate, monthlyAmount, monthlyRate, currency);

        tree.mergeProposedItem(proposed1);
        tree.buildForMerge();

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        verifyResult(tree.getView(), expectedResult);
    }

    @Test(groups = "fast")
    public void testMergeTwoDifferentItems() {

        final LocalDate startDate = new LocalDate(2014, 1, 1);
        final LocalDate endDate = new LocalDate(2014, 2, 1);

        final BigDecimal monthlyRate1 = new BigDecimal("12.00");
        final BigDecimal monthlyAmount1 = monthlyRate1;

        final BigDecimal monthlyRate2 = new BigDecimal("15.00");
        final BigDecimal monthlyAmount2 = monthlyRate2;

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        final InvoiceItem monthly1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endDate, monthlyAmount1, monthlyRate1, currency);
        tree.addItem(monthly1);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endDate, monthlyAmount2, monthlyRate2, currency);

        tree.mergeProposedItem(proposed1);
        tree.buildForMerge();

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        final InvoiceItem repair = new RepairAdjInvoiceItem(invoiceId, accountId, startDate, endDate, monthlyAmount1.negate(), currency, monthly1.getId());
        expectedResult.add(proposed1);
        expectedResult.add(repair);
        verifyResult(tree.getView(), expectedResult);
    }

    @Test(groups = "fast")
    public void testMergeCancellationWithInitialRepair() {

        final LocalDate startDate = new LocalDate(2014, 1, 1);
        final LocalDate blockDate = new LocalDate(2014, 1, 25);
        final LocalDate endDate = new LocalDate(2014, 2, 1);

        final BigDecimal monthlyRate1 = new BigDecimal("12.00");
        final BigDecimal monthlyAmount1 = monthlyRate1;

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        final InvoiceItem monthly1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endDate, monthlyAmount1, monthlyRate1, currency);
        tree.addItem(monthly1);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, blockDate, endDate, monthlyAmount1, monthlyRate1, currency);

        tree.mergeProposedItem(proposed1);
        tree.buildForMerge();

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        final InvoiceItem repair = new RepairAdjInvoiceItem(invoiceId, accountId, startDate, blockDate, new BigDecimal("-9.29"), currency, monthly1.getId());
        expectedResult.add(repair);
        verifyResult(tree.getView(), expectedResult);
    }

    @Test(groups = "fast")
    public void testMergeCancellationWithFinalRepair() {

        final LocalDate startDate = new LocalDate(2014, 1, 1);
        final LocalDate cancelDate = new LocalDate(2014, 1, 25);
        final LocalDate endDate = new LocalDate(2014, 2, 1);

        final BigDecimal monthlyRate1 = new BigDecimal("12.00");
        final BigDecimal monthlyAmount1 = monthlyRate1;

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        final InvoiceItem monthly1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endDate, monthlyAmount1, monthlyRate1, currency);
        tree.addItem(monthly1);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, cancelDate, monthlyAmount1, monthlyRate1, currency);
        tree.mergeProposedItem(proposed1);
        tree.buildForMerge();

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        final InvoiceItem repair = new RepairAdjInvoiceItem(invoiceId, accountId, cancelDate, endDate, new BigDecimal("-2.71"), currency, monthly1.getId());
        expectedResult.add(repair);
        verifyResult(tree.getView(), expectedResult);
    }

    @Test(groups = "fast")
    public void testMergeCancellationWithMiddleRepair() {

        final LocalDate startDate = new LocalDate(2014, 1, 1);
        final LocalDate blockDate = new LocalDate(2014, 1, 13);
        final LocalDate unblockDate = new LocalDate(2014, 1, 25);
        final LocalDate endDate = new LocalDate(2014, 2, 1);

        final BigDecimal monthlyRate1 = new BigDecimal("12.00");
        final BigDecimal monthlyAmount1 = monthlyRate1;

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        final InvoiceItem monthly1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endDate, monthlyAmount1, monthlyRate1, currency);
        tree.addItem(monthly1);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, blockDate, monthlyAmount1, monthlyRate1, currency);
        final InvoiceItem proposed2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, unblockDate, endDate, monthlyAmount1, monthlyRate1, currency);

        tree.mergeProposedItem(proposed1);
        tree.mergeProposedItem(proposed2);
        tree.buildForMerge();

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        final InvoiceItem repair = new RepairAdjInvoiceItem(invoiceId, accountId, blockDate, unblockDate, new BigDecimal("-4.65"), currency, monthly1.getId());
        expectedResult.add(repair);
        verifyResult(tree.getView(), expectedResult);
    }

    @Test(groups = "fast")
    public void testMergeCancellationWithTwoMiddleRepair() {

        final LocalDate startDate = new LocalDate(2014, 1, 1);
        final LocalDate blockDate1 = new LocalDate(2014, 1, 7);
        final LocalDate unblockDate1 = new LocalDate(2014, 1, 13);
        final LocalDate blockDate2 = new LocalDate(2014, 1, 17);
        final LocalDate unblockDate2 = new LocalDate(2014, 1, 25);
        final LocalDate endDate = new LocalDate(2014, 2, 1);

        final BigDecimal monthlyRate = new BigDecimal("12.00");
        final BigDecimal monthlyAmount = monthlyRate;

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        final InvoiceItem monthly = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endDate, monthlyAmount, monthlyRate, currency);
        tree.addItem(monthly);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, blockDate1, monthlyAmount, monthlyRate, currency);
        final InvoiceItem proposed2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, unblockDate1, blockDate2, monthlyAmount, monthlyRate, currency);
        final InvoiceItem proposed3 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, unblockDate2, endDate, monthlyAmount, monthlyRate, currency);

        tree.mergeProposedItem(proposed1);
        tree.mergeProposedItem(proposed2);
        tree.mergeProposedItem(proposed3);
        tree.buildForMerge();

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        final InvoiceItem repair1 = new RepairAdjInvoiceItem(invoiceId, accountId, blockDate1, unblockDate1, new BigDecimal("-2.32"), currency, monthly.getId());
        final InvoiceItem repair2 = new RepairAdjInvoiceItem(invoiceId, accountId, blockDate2, unblockDate2, new BigDecimal("-3.10"), currency, monthly.getId());
        expectedResult.add(repair1);
        expectedResult.add(repair2);
        verifyResult(tree.getView(), expectedResult);

        // Dot it again but with proposed items out of order
        final SubscriptionItemTree treeAgain = new SubscriptionItemTree(subscriptionId, invoiceId);
        final InvoiceItem monthlyAgain = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endDate, monthlyAmount, monthlyRate, currency);
        treeAgain.addItem(monthlyAgain);
        treeAgain.flatten(true);

        final InvoiceItem proposed2Again = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, unblockDate1, blockDate2, monthlyAmount, monthlyRate, currency);
        final InvoiceItem proposed1Again = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, blockDate1, monthlyAmount, monthlyRate, currency);
        final InvoiceItem proposed3Again = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, unblockDate2, endDate, monthlyAmount, monthlyRate, currency);

        treeAgain.mergeProposedItem(proposed1Again);
        treeAgain.mergeProposedItem(proposed2Again);
        treeAgain.mergeProposedItem(proposed3Again);
        treeAgain.buildForMerge();

        verifyResult(treeAgain.getView(), expectedResult);
    }

    @Test(groups = "fast")
    public void testMergeUpgradeWithFinalRepair() {

        final LocalDate startDate = new LocalDate(2014, 1, 1);
        final LocalDate upgradeDate = new LocalDate(2014, 1, 25);
        final LocalDate endDate = new LocalDate(2014, 2, 1);

        final BigDecimal monthlyRate1 = new BigDecimal("12.00");
        final BigDecimal monthlyAmount1 = monthlyRate1;

        final BigDecimal monthlyRate2 = new BigDecimal("20.00");
        final BigDecimal monthlyAmount2 = monthlyRate1;

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        final InvoiceItem monthly1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endDate, monthlyAmount1, monthlyRate1, currency);
        tree.addItem(monthly1);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, upgradeDate, monthlyAmount1, monthlyRate1, currency);
        final InvoiceItem proposed2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, "foo", "foo", "foo", upgradeDate, endDate, monthlyAmount2, monthlyRate2, currency);
        tree.mergeProposedItem(proposed1);
        tree.mergeProposedItem(proposed2);
        tree.buildForMerge();

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        final InvoiceItem repair = new RepairAdjInvoiceItem(invoiceId, accountId, upgradeDate, endDate, new BigDecimal("-2.71"), currency, monthly1.getId());
        expectedResult.add(proposed2);
        expectedResult.add(repair);
        verifyResult(tree.getView(), expectedResult);
    }

    @Test(groups = "fast")
    public void testMergeWithSecondRepair() {

        final LocalDate startDate = new LocalDate(2012, 5, 1);
        final LocalDate endDate = new LocalDate(2012, 6, 1);
        final LocalDate change1 = new LocalDate(2012, 5, 7);
        final LocalDate change2 = new LocalDate(2012, 5, 8);

        final BigDecimal rate1 = new BigDecimal("599.95");
        final BigDecimal amount1 = rate1;

        final BigDecimal rate2 = new BigDecimal("9.95");
        final BigDecimal proratedAmount2 = new BigDecimal("8.02");

        final BigDecimal rate3 = new BigDecimal("29.95");
        final BigDecimal proratedAmount3 = new BigDecimal("23.19");

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        final InvoiceItem initial = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endDate, amount1, rate1, currency);
        final InvoiceItem newItem1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, "foo", "foo", "foo", change1, endDate, proratedAmount2, rate2, currency);
        final InvoiceItem repair1 = new RepairAdjInvoiceItem(invoiceId, accountId, change1, endDate, new BigDecimal("-483.86"), currency, initial.getId());

        tree.addItem(initial);
        tree.addItem(newItem1);
        tree.addItem(repair1);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, change1, amount1, rate1, currency);
        final InvoiceItem proposed2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, "foo", "foo", "foo", change1, change2, proratedAmount3, rate2, currency);
        final InvoiceItem proposed3 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, "bar", "bar", "bar", change2, endDate, proratedAmount3, rate3, currency);
        tree.mergeProposedItem(proposed1);
        tree.mergeProposedItem(proposed2);
        tree.mergeProposedItem(proposed3);
        tree.buildForMerge();

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        final InvoiceItem repair2 = new RepairAdjInvoiceItem(invoiceId, accountId, change2, endDate, new BigDecimal("-7.70"), currency, initial.getId());
        expectedResult.add(proposed3);
        expectedResult.add(repair2);
        verifyResult(tree.getView(), expectedResult);
    }

    @Test(groups = "fast")
    public void testWithExistingFixedItem() {

        final LocalDate startDate = new LocalDate(2014, 1, 1);
        final LocalDate endDate = new LocalDate(2014, 2, 1);

        final BigDecimal monthlyRate = new BigDecimal("12.00");
        final BigDecimal monthlyAmount = monthlyRate;
        final BigDecimal fixedAmount = new BigDecimal("5.00");

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        final InvoiceItem monthly = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endDate, monthlyAmount, monthlyRate, currency);
        final InvoiceItem fixed = new FixedPriceInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, fixedAmount, currency);
        tree.addItem(monthly);
        tree.addItem(fixed);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endDate, monthlyAmount, monthlyRate, currency);
        tree.mergeProposedItem(proposed1);
        tree.mergeProposedItem(fixed);
        tree.buildForMerge();

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        verifyResult(tree.getView(), expectedResult);
    }

    @Test(groups = "fast")
    public void testWithNewFixedItem() {

        final LocalDate startDate = new LocalDate(2014, 1, 1);
        final LocalDate endDate = new LocalDate(2014, 2, 1);

        final BigDecimal monthlyRate = new BigDecimal("12.00");
        final BigDecimal monthlyAmount = monthlyRate;
        final BigDecimal fixedAmount = new BigDecimal("5.00");

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        final InvoiceItem monthly = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endDate, monthlyAmount, monthlyRate, currency);
        tree.addItem(monthly);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endDate, monthlyAmount, monthlyRate, currency);
        final InvoiceItem fixed = new FixedPriceInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, fixedAmount, currency);
        tree.mergeProposedItem(proposed1);
        tree.mergeProposedItem(fixed);
        tree.buildForMerge();

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        expectedResult.add(fixed);
        verifyResult(tree.getView(), expectedResult);
    }

    @Test(groups = "fast")
    public void testRepairWithSmallItemAdjustment() {

        final LocalDate startDate = new LocalDate(2014, 1, 1);
        final LocalDate itemAdjDate = new LocalDate(2014, 1, 2);
        final LocalDate endDate = new LocalDate(2014, 2, 1);

        final LocalDate cancelDate = new LocalDate(2014, 1, 23);

        final BigDecimal rate1 = new BigDecimal("12.00");
        final BigDecimal amount1 = rate1;

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        final InvoiceItem initial = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endDate, amount1, rate1, currency);
        final InvoiceItem itemAdj = new ItemAdjInvoiceItem(initial, itemAdjDate, new BigDecimal("-2.00"), currency);
        tree.addItem(initial);
        tree.addItem(itemAdj);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, cancelDate, amount1, rate1, currency);
        tree.mergeProposedItem(proposed1);
        tree.buildForMerge();

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        final InvoiceItem repair1 = new RepairAdjInvoiceItem(invoiceId, accountId, cancelDate, endDate, new BigDecimal("-3.48"), currency, initial.getId());
        expectedResult.add(repair1);

        verifyResult(tree.getView(), expectedResult);
    }

    @Test(groups = "fast")
    public void testRepairWithLargeItemAdjustment() {

        final LocalDate startDate = new LocalDate(2014, 1, 1);
        final LocalDate itemAdjDate = new LocalDate(2014, 1, 2);
        final LocalDate endDate = new LocalDate(2014, 2, 1);

        final LocalDate cancelDate = new LocalDate(2014, 1, 23);

        final BigDecimal rate1 = new BigDecimal("12.00");
        final BigDecimal amount1 = rate1;

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        final InvoiceItem initial = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endDate, amount1, rate1, currency);
        final InvoiceItem itemAdj = new ItemAdjInvoiceItem(initial, itemAdjDate, new BigDecimal("-10.00"), currency);
        tree.addItem(initial);
        tree.addItem(itemAdj);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, cancelDate, amount1, rate1, currency);
        tree.mergeProposedItem(proposed1);
        tree.buildForMerge();

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        final InvoiceItem repair1 = new RepairAdjInvoiceItem(invoiceId, accountId, cancelDate, endDate, new BigDecimal("-2.00"), currency, initial.getId());
        expectedResult.add(repair1);

        verifyResult(tree.getView(), expectedResult);
    }

    @Test(groups = "fast")
    public void testMergeMonthlyToAnnualWithNoProRation() {

        final LocalDate startDate = new LocalDate(2014, 1, 1);
        final LocalDate endMonthly1 = new LocalDate(2014, 2, 1);
        final LocalDate endMonthly2 = new LocalDate(2014, 3, 1);
        final LocalDate switchToAnnualDate = new LocalDate(2014, 2, 23);
        final LocalDate endDate = new LocalDate(2015, 2, 23);

        final BigDecimal monthlyRate = new BigDecimal("12.00");
        final BigDecimal monthlyAmount = monthlyRate;

        final BigDecimal yearlyRate = new BigDecimal("100.00");
        final BigDecimal yearlyAmount = yearlyRate;

        final InvoiceItem monthly1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endMonthly1, monthlyAmount, monthlyRate, currency);
        final InvoiceItem monthly2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, endMonthly1, endMonthly2, monthlyAmount, monthlyRate, currency);

        // First test with items in order
        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.addItem(monthly1);
        tree.addItem(monthly2);
        tree.flatten(true);

        final InvoiceItem proposed = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, switchToAnnualDate, endDate, yearlyAmount, yearlyRate, currency);
        final InvoiceItem proposedMonthly1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endMonthly1, monthlyAmount, monthlyRate, currency);
        tree.mergeProposedItem(proposedMonthly1);
        final InvoiceItem proRatedmonthly2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, endMonthly1, switchToAnnualDate, monthlyAmount, monthlyRate, currency);
        tree.mergeProposedItem(proRatedmonthly2);
        tree.mergeProposedItem(proposed);
        tree.buildForMerge();

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        final InvoiceItem repair = new RepairAdjInvoiceItem(invoiceId, accountId, switchToAnnualDate, endMonthly2, new BigDecimal("-2.57"), currency, monthly2.getId());
        expectedResult.add(proposed);
        expectedResult.add(repair);

        verifyResult(tree.getView(), expectedResult);
    }

    @Test(groups = "fast")
    public void verifyJson() throws IOException {
        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        final UUID id1 = UUID.fromString("e8ba6ce7-9bd4-417d-af53-70951ecaa99f");
        final InvoiceItem yearly1 = new RecurringInvoiceItem(id1, new DateTime(), invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, new LocalDate("2014-01-01"), new LocalDate("2015-01-01"), BigDecimal.TEN, BigDecimal.TEN, currency);
        tree.addItem(yearly1);

        final UUID id2 = UUID.fromString("48db1317-9a6e-4666-bcc5-fc7d3d0defc8");
        final InvoiceItem newItem = new RecurringInvoiceItem(id2, new DateTime(), invoiceId, accountId, bundleId, subscriptionId, "other-product","other-plan", "other-plan", new LocalDate("2014-08-01"), new LocalDate("2015-01-01"), BigDecimal.ONE, BigDecimal.ONE, currency);
        tree.addItem(newItem);

        final UUID id3 = UUID.fromString("02ec57f5-2723-478b-86ba-ebeaedacb9db");
        final InvoiceItem repair = new RepairAdjInvoiceItem(id3, new DateTime(), invoiceId, accountId, new LocalDate("2014-08-01"), new LocalDate("2015-01-01"), BigDecimal.TEN.negate(), currency, yearly1.getId());
        tree.addItem(repair);

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        tree.getRoot().jsonSerializeTree(new ObjectMapper(), outputStream);

        final String json = outputStream.toString("UTF-8");
        final String expectedJson = "[{\"start\":\"2014-01-01\",\"end\":\"2015-01-01\",\"items\":[{\"id\":\"e8ba6ce7-9bd4-417d-af53-70951ecaa99f\",\"startDate\":\"2014-01-01\",\"endDate\":\"2015-01-01\",\"amount\":10.00,\"currency\":\"USD\",\"linkedId\":null,\"action\":\"ADD\"}]},[{\"start\":\"2014-08-01\",\"end\":\"2015-01-01\",\"items\":[{\"id\":\"48db1317-9a6e-4666-bcc5-fc7d3d0defc8\",\"startDate\":\"2014-08-01\",\"endDate\":\"2015-01-01\",\"amount\":1.00,\"currency\":\"USD\",\"linkedId\":null,\"action\":\"ADD\"},{\"id\":\"02ec57f5-2723-478b-86ba-ebeaedacb9db\",\"startDate\":\"2014-08-01\",\"endDate\":\"2015-01-01\",\"amount\":10.00,\"currency\":\"USD\",\"linkedId\":\"e8ba6ce7-9bd4-417d-af53-70951ecaa99f\",\"action\":\"CANCEL\"}]}]]";

        assertEquals(json, expectedJson);
    }

    @Test(groups = "fast", description = "https://github.com/killbill/killbill/issues/286")
    public void testMaxedOutProRation() throws IOException {
        final LocalDate startDate = new LocalDate(2014, 1, 1);
        final LocalDate cancelDate = new LocalDate(2014, 1, 25);
        final LocalDate endDate = new LocalDate(2014, 2, 1);

        final BigDecimal monthlyRate1 = new BigDecimal("12.00");
        final BigDecimal monthlyAmount1 = monthlyRate1;

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);

        final InvoiceItem existing1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endDate, monthlyAmount1, monthlyRate1, currency);
        tree.addItem(existing1);
        // Fully item adjust the recurring item
        final InvoiceItem existingItemAdj1 = new ItemAdjInvoiceItem(existing1, startDate, monthlyRate1.negate(), currency);
        tree.addItem(existingItemAdj1);
        tree.flatten(true);

        //printTree(tree);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, cancelDate, monthlyAmount1, monthlyRate1, currency);
        tree.mergeProposedItem(proposed1);
        tree.buildForMerge();

        //printTree(tree);

        // We expect the proposed item because item adjustments don't change the subscription view of invoice
        final List<InvoiceItem> expectedResult = ImmutableList.<InvoiceItem>of(proposed1);
        verifyResult(tree.getView(), expectedResult);
    }

    @Test(groups = "fast")
    public void testPartialProRation() {
        final LocalDate startDate = new LocalDate(2014, 1, 1);
        final LocalDate cancelDate = new LocalDate(2014, 1, 25);
        final LocalDate endDate = new LocalDate(2014, 2, 1);

        final BigDecimal monthlyRate1 = new BigDecimal("12.00");
        final BigDecimal monthlyAmount1 = monthlyRate1;

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);

        final InvoiceItem existing1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endDate, monthlyAmount1, monthlyRate1, currency);
        tree.addItem(existing1);
        // Partially item adjust the recurring item
        final InvoiceItem existingItemAdj1 = new ItemAdjInvoiceItem(existing1, startDate, monthlyRate1.negate().add(BigDecimal.ONE), currency);
        tree.addItem(existingItemAdj1);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, cancelDate, monthlyAmount1, monthlyRate1, currency);
        tree.mergeProposedItem(proposed1);
        tree.buildForMerge();

        final InvoiceItem repair = new RepairAdjInvoiceItem(invoiceId, accountId, cancelDate, endDate, BigDecimal.ONE.negate(), Currency.USD, existing1.getId());
        final List<InvoiceItem> expectedResult = ImmutableList.<InvoiceItem>of(repair);
        verifyResult(tree.getView(), expectedResult);
    }

    @Test(groups = "fast")
    public void testWithWrongInitialItem() throws IOException {
        final LocalDate wrongStartDate = new LocalDate(2016, 9, 9);
        final LocalDate correctStartDate = new LocalDate(2016, 9, 8);
        final LocalDate endDate = new LocalDate(2016, 10, 8);

        final BigDecimal rate = new BigDecimal("12.00");
        final BigDecimal amount = rate;

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);

        final InvoiceItem wrongInitialItem = new RecurringInvoiceItem(invoiceId,
                                                                      accountId,
                                                                      bundleId,
                                                                      subscriptionId,
                                                                      productName,
                                                                      planName,
                                                                      phaseName,
                                                                      wrongStartDate,
                                                                      endDate,
                                                                      amount,
                                                                      rate,
                                                                      currency);
        tree.addItem(wrongInitialItem);

        final InvoiceItem itemAdj = new ItemAdjInvoiceItem(wrongInitialItem,
                                                           new LocalDate(2016, 10, 2),
                                                           amount.negate(),
                                                           currency);
        tree.addItem(itemAdj);

        final InvoiceItem correctInitialItem = new RecurringInvoiceItem(invoiceId,
                                                                        accountId,
                                                                        bundleId,
                                                                        subscriptionId,
                                                                        productName,
                                                                        planName,
                                                                        phaseName,
                                                                        correctStartDate,
                                                                        endDate,
                                                                        amount,
                                                                        rate,
                                                                        currency);
        tree.addItem(correctInitialItem);

        assertEquals(tree.getRoot().getStart(), correctStartDate);
        assertEquals(tree.getRoot().getEnd(), endDate);
        assertEquals(tree.getRoot().getLeftChild().getStart(), correctStartDate);
        assertEquals(tree.getRoot().getLeftChild().getEnd(), endDate);
        assertEquals(tree.getRoot().getLeftChild().getLeftChild().getStart(), wrongStartDate);
        assertEquals(tree.getRoot().getLeftChild().getLeftChild().getEnd(), endDate);
        assertNull(tree.getRoot().getLeftChild().getLeftChild().getLeftChild());
        assertNull(tree.getRoot().getLeftChild().getLeftChild().getRightSibling());
        assertNull(tree.getRoot().getLeftChild().getRightSibling());
        assertNull(tree.getRoot().getRightSibling());

        tree.flatten(true);

        assertEquals(tree.getRoot().getStart(), correctStartDate);
        assertEquals(tree.getRoot().getEnd(), endDate);
        assertEquals(tree.getRoot().getLeftChild().getStart(), correctStartDate);
        assertEquals(tree.getRoot().getLeftChild().getEnd(), endDate);
        assertNull(tree.getRoot().getLeftChild().getLeftChild());
        assertNull(tree.getRoot().getLeftChild().getRightSibling());
        assertNull(tree.getRoot().getRightSibling());

        //printTree(tree);
    }

    private void verifyResult(final List<InvoiceItem> result, final List<InvoiceItem> expectedResult) {
        assertEquals(result.size(), expectedResult.size());
        for (int i = 0; i < expectedResult.size(); i++) {
            assertTrue(result.get(i).matches(expectedResult.get(i)));
        }
    }

    @Test(groups = "fast")
    public void testWithWrongInitialItemInLoop() {

        final LocalDate wrongStartDate = new LocalDate(2016, 9, 9);
        final LocalDate correctStartDate = new LocalDate(2016, 9, 8);
        final LocalDate endDate = new LocalDate(2016, 10, 8);

        final BigDecimal rate1 = new BigDecimal("12.00");
        final BigDecimal amount1 = rate1;

        final List<InvoiceItem> existingItems = new ArrayList<InvoiceItem>();
        final List<InvoiceItem> proposedItems = new ArrayList<InvoiceItem>();
        final InvoiceItem wrongInitialItem = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, wrongStartDate, endDate, amount1, rate1, currency);
        proposedItems.add(wrongInitialItem);

        int previousExistingSize = existingItems.size();
        int iteration = 0;
        do {

            SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
            for (InvoiceItem e : existingItems) {
                tree.addItem(e);
            }
            tree.build();
            tree.flatten(true);

            for (InvoiceItem p : proposedItems) {
                tree.mergeProposedItem(p);
            }
            tree.buildForMerge();

            existingItems.addAll(tree.getView());
            if (iteration == 0) {
                final InvoiceItem itemAdj = new ItemAdjInvoiceItem(wrongInitialItem, new LocalDate(2016, 10, 2), amount1.negate(), currency);
                existingItems.add(itemAdj);
            }

            previousExistingSize = existingItems.size();

            proposedItems.clear();
            final InvoiceItem correctInitialItem = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, correctStartDate, endDate, amount1, rate1, currency);
            proposedItems.add(correctInitialItem);
            iteration++;
        } while (iteration < 10);

        // We have repaired wrongInitialItem and generated the correctInitialItem and stopped
        Assert.assertEquals(previousExistingSize, 3);
    }

    @Test(groups = "fast")
    public void testWithFreeRecurring() {
        final LocalDate startDate = new LocalDate(2012, 8, 1);
        final LocalDate endDate = new LocalDate(2012, 9, 1);

        final BigDecimal monthlyRate1 = new BigDecimal("12.00");
        final BigDecimal monthlyRate2 = new BigDecimal("24.00");

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        final InvoiceItem freeMonthly = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endDate, BigDecimal.ZERO, BigDecimal.ZERO, currency);
        tree.addItem(freeMonthly);
        final InvoiceItem payingMonthly1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endDate, monthlyRate1, monthlyRate1, currency);
        tree.addItem(payingMonthly1);
        tree.flatten(true);

        final InvoiceItem proposedPayingMonthly2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, endDate, monthlyRate2, monthlyRate2, currency);
        tree.mergeProposedItem(proposedPayingMonthly2);
        tree.buildForMerge();

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        expectedResult.add(proposedPayingMonthly2);
        final InvoiceItem repair = new RepairAdjInvoiceItem(invoiceId, accountId, startDate, endDate, monthlyRate1.negate(), currency, payingMonthly1.getId());
        expectedResult.add(repair);
        verifyResult(tree.getView(), expectedResult);
    }

    private void printTreeJSON(final SubscriptionItemTree tree) throws IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        tree.getRoot().jsonSerializeTree(OBJECT_MAPPER, outputStream);
        System.out.println(outputStream.toString("UTF-8"));
    }

    private void printTree(final SubscriptionItemTree tree) throws IOException {
        System.out.println(TreePrinter.print(tree.getRoot()));
    }
}
