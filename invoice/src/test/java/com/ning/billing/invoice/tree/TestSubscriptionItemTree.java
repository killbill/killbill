/*
 * Copyright 2010-2014 Ning, Inc.
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

package com.ning.billing.invoice.tree;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.model.FixedPriceInvoiceItem;
import com.ning.billing.invoice.model.ItemAdjInvoiceItem;
import com.ning.billing.invoice.model.RecurringInvoiceItem;
import com.ning.billing.invoice.model.RepairAdjInvoiceItem;
import com.ning.billing.util.jackson.ObjectMapper;

import com.google.common.collect.Lists;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestSubscriptionItemTree /* extends InvoiceTestSuiteNoDB  */ {

    private final UUID invoiceId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID subscriptionId = UUID.randomUUID();
    private final UUID bundleId = UUID.randomUUID();
    private final String planName = "my-plan";
    private final String phaseName = "my-phase";
    private final Currency currency = Currency.USD;

    @Test(groups = "fast")
    public void testSimpleRepair() {

        final LocalDate startDate = new LocalDate(2014, 1, 1);
        final LocalDate endDate = new LocalDate(2014, 2, 1);

        final LocalDate repairDate = new LocalDate(2014, 1, 23);

        final BigDecimal rate1 = new BigDecimal("12.00");
        final BigDecimal amount1 = rate1;

        final BigDecimal rate2 = new BigDecimal("14.85");
        final BigDecimal amount2 = rate2;

        final InvoiceItem initial = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount1, rate1, currency);
        final InvoiceItem newItem = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, "someelse", "someelse", repairDate, endDate, amount2, rate2, currency);
        final InvoiceItem repair = new RepairAdjInvoiceItem(invoiceId, accountId, repairDate, endDate, amount1.negate(), currency, initial.getId());

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        final InvoiceItem expected1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, repairDate, new BigDecimal("8.52"), rate1, currency);
        expectedResult.add(expected1);
        final InvoiceItem expected2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, "someelse", "someelse", repairDate, endDate, amount2, rate2, currency);
        expectedResult.add(expected2);

        // First test with items in order
        SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId);
        tree.addItem(initial);
        tree.addItem(newItem);
        tree.addItem(repair);
        tree.build();
        verifyResult(tree.getView(), expectedResult);
        tree = new SubscriptionItemTree(subscriptionId);
        tree.addItem(repair);
        tree.addItem(newItem);
        tree.addItem(initial);
        tree.build();
        verifyResult(tree.getView(), expectedResult);

        tree = new SubscriptionItemTree(subscriptionId);
        tree.addItem(repair);
        tree.addItem(initial);
        tree.addItem(newItem);
        tree.build();
        verifyResult(tree.getView(), expectedResult);
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

        final InvoiceItem initial = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount1, rate1, currency);
        final InvoiceItem newItem1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, repairDate1, endDate, amount2, rate2, currency);
        final InvoiceItem repair1 = new RepairAdjInvoiceItem(invoiceId, accountId, repairDate1, endDate, amount1.negate(), currency, initial.getId());

        final InvoiceItem newItem2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, repairDate2, endDate, amount3, rate3, currency);
        final InvoiceItem repair2 = new RepairAdjInvoiceItem(invoiceId, accountId, repairDate2, endDate, amount2.negate(), currency, initial.getId());

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        final InvoiceItem expected1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, repairDate1, new BigDecimal("8.52"), rate1, currency);
        expectedResult.add(expected1);
        final InvoiceItem expected2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, repairDate1, repairDate2, new BigDecimal("4.95"), rate2, currency);
        expectedResult.add(expected2);
        final InvoiceItem expected3 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, repairDate2, endDate, amount3, rate3, currency);
        expectedResult.add(expected3);

        // First test with items in order
        SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId);
        tree.addItem(initial);
        tree.addItem(newItem1);
        tree.addItem(repair1);
        tree.addItem(newItem2);
        tree.addItem(repair2);
        tree.build();
        verifyResult(tree.getView(), expectedResult);

        tree = new SubscriptionItemTree(subscriptionId);
        tree.addItem(repair2);
        tree.addItem(newItem1);
        tree.addItem(newItem2);
        tree.addItem(repair1);
        tree.addItem(initial);
        tree.build();
        verifyResult(tree.getView(), expectedResult);

        tree = new SubscriptionItemTree(subscriptionId);
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

        final InvoiceItem initial = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount1, rate1, currency);
        final InvoiceItem block1 = new RepairAdjInvoiceItem(invoiceId, accountId, blockStart1, unblockStart1, amount1.negate(), currency, initial.getId());
        final InvoiceItem block2 = new RepairAdjInvoiceItem(invoiceId, accountId, blockStart2, unblockStart2, amount1.negate(), currency, initial.getId());

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        final InvoiceItem expected1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, blockStart1, new BigDecimal("2.71"), rate1, currency);
        expectedResult.add(expected1);
        final InvoiceItem expected2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, unblockStart1, blockStart2, new BigDecimal("2.71"), rate1, currency);
        expectedResult.add(expected2);
        final InvoiceItem expected3 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, unblockStart2, endDate, new BigDecimal("3.48"), rate1, currency);
        expectedResult.add(expected3);

        // First test with items in order
        SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId);
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

        final InvoiceItem first = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate1, startDate2, amount1, rate1, currency);
        final InvoiceItem second = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate2, endDate, amount1, rate1, currency);
        final InvoiceItem block1 = new RepairAdjInvoiceItem(invoiceId, accountId, blockDate, startDate2, amount1.negate(), currency, first.getId());
        final InvoiceItem block2 = new RepairAdjInvoiceItem(invoiceId, accountId, startDate2, unblockDate, amount1.negate(), currency, first.getId());

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        final InvoiceItem expected1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate1, blockDate, new BigDecimal("9.29"), rate1, currency);
        final InvoiceItem expected2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, unblockDate, endDate, new BigDecimal("9.43"), rate1, currency);
        expectedResult.add(expected1);
        expectedResult.add(expected2);

        // First test with items in order
        SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId);
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

        final InvoiceItem annual = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount1, rate1, currency);
        final InvoiceItem repair = new RepairAdjInvoiceItem(invoiceId, accountId, startDate, endDate, amount1.negate(), currency, annual.getId());
        final InvoiceItem monthly1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, "someelse", "someelse", startDate, firstMonthlyEndDate, amount2, rate2, currency);
        final InvoiceItem monthly2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, "someelse", "someelse", firstMonthlyEndDate, secondMonthlyEndDate, amount2, rate2, currency);

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        expectedResult.add(monthly1);
        expectedResult.add(monthly2);

        SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId);
        tree.addItem(annual);
        tree.addItem(repair);
        tree.addItem(monthly1);
        tree.addItem(monthly2);
        tree.build();
        verifyResult(tree.getView(), expectedResult);

        tree = new SubscriptionItemTree(subscriptionId);
        tree.addItem(monthly1);
        tree.addItem(repair);
        tree.addItem(annual);
        tree.addItem(monthly2);
        tree.build();
        verifyResult(tree.getView(), expectedResult);

        tree = new SubscriptionItemTree(subscriptionId);
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

        final InvoiceItem monthly1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endMonthly1, monthlyAmount, monthlyRate, currency);
        final InvoiceItem monthly2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, endMonthly1, endMonthly2, monthlyAmount, monthlyRate, currency);
        final InvoiceItem repair = new RepairAdjInvoiceItem(invoiceId, accountId, switchToAnnualDate, endMonthly2, monthlyAmount.negate(), currency, monthly2.getId());
        final InvoiceItem leadingAnnualProration = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, switchToAnnualDate, endMonthly2, yearlyAmount, yearlyRate, currency);
        final InvoiceItem annual = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, endMonthly2, endDate, yearlyAmount, yearlyRate, currency);

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        expectedResult.add(monthly1);
        final InvoiceItem monthly2Prorated = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, endMonthly1, switchToAnnualDate, new BigDecimal("9.43"), monthlyRate, currency);
        expectedResult.add(monthly2Prorated);
        expectedResult.add(leadingAnnualProration);
        expectedResult.add(annual);

        // First test with items in order
        SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId);
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

        final InvoiceItem monthly1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endMonthly1, monthlyAmount, monthlyRate, currency);
        final InvoiceItem monthly2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, endMonthly1, endMonthly2, monthlyAmount, monthlyRate, currency);
        final InvoiceItem repair = new RepairAdjInvoiceItem(invoiceId, accountId, switchToAnnualDate, endMonthly2, monthlyAmount.negate(), currency, monthly2.getId());
        final InvoiceItem annual = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, switchToAnnualDate, endDate, yearlyAmount, yearlyRate, currency);

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        expectedResult.add(monthly1);
        final InvoiceItem monthly2Prorated = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, endMonthly1, switchToAnnualDate, new BigDecimal("9.43"), monthlyRate, currency);
        expectedResult.add(monthly2Prorated);
        expectedResult.add(annual);

        // First test with items in order
        SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId);
        tree.addItem(monthly1);
        tree.addItem(monthly2);
        tree.addItem(repair);
        tree.addItem(annual);
        tree.build();
        verifyResult(tree.getView(), expectedResult);
    }

    @Test(groups = "fast")
    public void testMergeWithNoExisting() {

        final LocalDate startDate = new LocalDate(2014, 1, 1);
        final LocalDate endDate = new LocalDate(2014, 2, 1);

        final BigDecimal monthlyRate = new BigDecimal("12.00");
        final BigDecimal monthlyAmount = monthlyRate;

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, monthlyAmount, monthlyRate, currency);
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

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId);
        final InvoiceItem monthly1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, monthlyAmount, monthlyRate, currency);
        tree.addItem(monthly1);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, monthlyAmount, monthlyRate, currency);

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

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId);
        final InvoiceItem monthly1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, monthlyAmount1, monthlyRate1, currency);
        tree.addItem(monthly1);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, monthlyAmount2, monthlyRate2, currency);

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

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId);
        final InvoiceItem monthly1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, monthlyAmount1, monthlyRate1, currency);
        tree.addItem(monthly1);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, blockDate, endDate, monthlyAmount1, monthlyRate1, currency);

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

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId);
        final InvoiceItem monthly1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, monthlyAmount1, monthlyRate1, currency);
        tree.addItem(monthly1);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, cancelDate, monthlyAmount1, monthlyRate1, currency);
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

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId);
        final InvoiceItem monthly1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, monthlyAmount1, monthlyRate1, currency);
        tree.addItem(monthly1);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, blockDate, monthlyAmount1, monthlyRate1, currency);
        final InvoiceItem proposed2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, unblockDate, endDate, monthlyAmount1, monthlyRate1, currency);

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

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId);
        final InvoiceItem monthly = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, monthlyAmount, monthlyRate, currency);
        tree.addItem(monthly);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, blockDate1, monthlyAmount, monthlyRate, currency);
        final InvoiceItem proposed2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, unblockDate1, blockDate2, monthlyAmount, monthlyRate, currency);
        final InvoiceItem proposed3 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, unblockDate2, endDate, monthlyAmount, monthlyRate, currency);

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
        final SubscriptionItemTree treeAgain = new SubscriptionItemTree(subscriptionId);
        final InvoiceItem monthlyAgain = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, monthlyAmount, monthlyRate, currency);
        treeAgain.addItem(monthlyAgain);
        treeAgain.flatten(true);

        final InvoiceItem proposed2Again = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, unblockDate1, blockDate2, monthlyAmount, monthlyRate, currency);
        final InvoiceItem proposed1Again = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, blockDate1, monthlyAmount, monthlyRate, currency);
        final InvoiceItem proposed3Again = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, unblockDate2, endDate, monthlyAmount, monthlyRate, currency);

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

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId);
        final InvoiceItem monthly1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, monthlyAmount1, monthlyRate1, currency);
        tree.addItem(monthly1);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, upgradeDate, monthlyAmount1, monthlyRate1, currency);
        final InvoiceItem proposed2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, "foo", "foo", upgradeDate, endDate, monthlyAmount2, monthlyRate2, currency);
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

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId);
        final InvoiceItem initial = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount1, rate1, currency);
        final InvoiceItem newItem1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, "foo", "foo", change1, endDate, proratedAmount2, rate2, currency);
        final InvoiceItem repair1 = new RepairAdjInvoiceItem(invoiceId, accountId, change1, endDate, new BigDecimal("-483.86"), currency, initial.getId());

        tree.addItem(initial);
        tree.addItem(newItem1);
        tree.addItem(repair1);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, change1, amount1, rate1, currency);
        final InvoiceItem proposed2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, "foo", "foo", change1, change2, proratedAmount3, rate2, currency);
        final InvoiceItem proposed3 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, "bar", "bar", change2, endDate, proratedAmount3, rate3, currency);
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

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId);
        final InvoiceItem monthly = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, monthlyAmount, monthlyRate, currency);
        final InvoiceItem fixed = new FixedPriceInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, fixedAmount, currency);
        tree.addItem(monthly);
        tree.addItem(fixed);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, monthlyAmount, monthlyRate, currency);
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

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId);
        final InvoiceItem monthly = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, monthlyAmount, monthlyRate, currency);
        tree.addItem(monthly);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, monthlyAmount, monthlyRate, currency);
        final InvoiceItem fixed = new FixedPriceInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, fixedAmount, currency);
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

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId);
        final InvoiceItem initial = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount1, rate1, currency);
        final InvoiceItem itemAdj = new ItemAdjInvoiceItem(initial, itemAdjDate, new BigDecimal("-2.00"), currency);
        tree.addItem(initial);
        tree.addItem(itemAdj);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, cancelDate, amount1, rate1, currency);
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

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId);
        final InvoiceItem initial = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount1, rate1, currency);
        final InvoiceItem itemAdj = new ItemAdjInvoiceItem(initial, itemAdjDate, new BigDecimal("-10.00"), currency);
        tree.addItem(initial);
        tree.addItem(itemAdj);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, cancelDate, amount1, rate1, currency);
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

        final InvoiceItem monthly1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endMonthly1, monthlyAmount, monthlyRate, currency);
        final InvoiceItem monthly2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, endMonthly1, endMonthly2, monthlyAmount, monthlyRate, currency);

        // First test with items in order
        SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId);
        tree.addItem(monthly1);
        tree.addItem(monthly2);
        tree.flatten(true);

        final InvoiceItem proposed = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, switchToAnnualDate, endDate, yearlyAmount, yearlyRate, currency);
        final InvoiceItem proposedMonthly1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endMonthly1, monthlyAmount, monthlyRate, currency);
        tree.mergeProposedItem(proposedMonthly1);
        final InvoiceItem proRatedmonthly2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, endMonthly1, switchToAnnualDate, monthlyAmount, monthlyRate, currency);
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
    public void verifyJson() {

        SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId);
        final UUID id1 = UUID.fromString("e8ba6ce7-9bd4-417d-af53-70951ecaa99f");
        final InvoiceItem yearly1 = new RecurringInvoiceItem(id1, new DateTime(), invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, new LocalDate("2014-01-01"), new LocalDate("2015-01-01"), BigDecimal.TEN, BigDecimal.TEN, currency);
        tree.addItem(yearly1);

        final UUID id2 = UUID.fromString("48db1317-9a6e-4666-bcc5-fc7d3d0defc8");
        final InvoiceItem newItem = new RecurringInvoiceItem(id2, new DateTime(), invoiceId, accountId, bundleId, subscriptionId, "other-plan", "other-plan", new LocalDate("2014-08-01"), new LocalDate("2015-01-01"), BigDecimal.ONE, BigDecimal.ONE, currency);
        tree.addItem(newItem);

        final UUID id3 = UUID.fromString("02ec57f5-2723-478b-86ba-ebeaedacb9db");
        final InvoiceItem repair = new RepairAdjInvoiceItem(id3, new DateTime(), invoiceId, accountId, new LocalDate("2014-08-01"), new LocalDate("2015-01-01"), BigDecimal.TEN.negate(), currency, yearly1.getId());
        tree.addItem(repair);

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            tree.getRoot().jsonSerializeTree(new ObjectMapper(), outputStream);

            final String json = outputStream.toString("UTF-8");
            final String expectedJson = "[{\"start\":\"2014-01-01\",\"end\":\"2015-01-01\",\"items\":[{\"id\":\"e8ba6ce7-9bd4-417d-af53-70951ecaa99f\",\"startDate\":\"2014-01-01\",\"endDate\":\"2015-01-01\",\"amount\":10,\"currency\":\"USD\",\"linkedId\":null,\"action\":\"ADD\"}]},[{\"start\":\"2014-08-01\",\"end\":\"2015-01-01\",\"items\":[{\"id\":\"48db1317-9a6e-4666-bcc5-fc7d3d0defc8\",\"startDate\":\"2014-08-01\",\"endDate\":\"2015-01-01\",\"amount\":1,\"currency\":\"USD\",\"linkedId\":null,\"action\":\"ADD\"},{\"id\":\"02ec57f5-2723-478b-86ba-ebeaedacb9db\",\"startDate\":\"2014-08-01\",\"endDate\":\"2015-01-01\",\"amount\":10,\"currency\":\"USD\",\"linkedId\":\"e8ba6ce7-9bd4-417d-af53-70951ecaa99f\",\"action\":\"CANCEL\"}]}]]";

            assertEquals(json, expectedJson);

        } catch (IOException e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }

    }

    private void verifyResult(final List<InvoiceItem> result, final List<InvoiceItem> expectedResult) {
        assertEquals(result.size(), expectedResult.size());
        for (int i = 0; i < expectedResult.size(); i++) {
            assertTrue(result.get(i).matches(expectedResult.get(i)));
        }
    }

}
