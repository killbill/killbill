/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestSubscriptionItemTree extends InvoiceTestSuiteNoDB {

    private final UUID invoiceId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID subscriptionId = UUID.randomUUID();
    private final UUID bundleId = UUID.randomUUID();
    private final String productName = "my-product";
    private final String planName = "my-plan";
    private final String phaseName = "my-phase";
    private final Currency currency = Currency.USD;

    @Test(groups = "fast")
    public void testWithBCDChange() {
        final LocalDate startPeriod = new LocalDate(2014, 5, 1);
        final LocalDate endPeriod = new LocalDate(2014, 6, 1);
        final LocalDate bcdChange = new LocalDate(2014, 5, 15);
        final LocalDate newEndPeriod = new LocalDate(2014, 6, 15);

        final BigDecimal monthlyRate = new BigDecimal("10.00");
        final BigDecimal fullAmount = monthlyRate;
        final BigDecimal halfAmount = new BigDecimal("5.00");

        final InvoiceItem item1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startPeriod, endPeriod, fullAmount, monthlyRate, currency);
        final InvoiceItem item2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, endPeriod, newEndPeriod, halfAmount, monthlyRate, currency);

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.addItem(item1);
        tree.addItem(item2);
        tree.build();

        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startPeriod, bcdChange, halfAmount, monthlyRate, currency);
        final InvoiceItem proposed2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, bcdChange, newEndPeriod, fullAmount, monthlyRate, currency);

        tree.mergeProposedItem(proposed1);
        tree.mergeProposedItem(proposed2);
        tree.buildForMerge();

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        expectedResult.addAll(ImmutableList.of());
        verifyResult(tree.getView(), expectedResult);
    }

    @Test(groups = "fast")
    public void testWithBCDChange2() {
        final LocalDate existingItemStartPeriod = new LocalDate(2014, 6, 20);
        final LocalDate existingItemEndPeriod = new LocalDate(2014, 7, 20);

        final LocalDate proposedItem1StartPeriod = new LocalDate(2014, 6, 17);
        final LocalDate proposedItem1EndPeriod = new LocalDate(2014, 7, 17);
        final LocalDate proposedItem2StartPeriod = proposedItem1EndPeriod;
        final LocalDate proposedItem2EndPeriod = new LocalDate(2014, 8, 17);

        final BigDecimal monthlyRate = new BigDecimal("10.00");
        final BigDecimal fullAmount = monthlyRate;

        final InvoiceItem item1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, existingItemStartPeriod, existingItemEndPeriod, fullAmount, monthlyRate, currency);

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.addItem(item1);
        tree.build();

        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, proposedItem1StartPeriod, proposedItem1EndPeriod, fullAmount, monthlyRate, currency);
        final InvoiceItem proposed2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, proposedItem2StartPeriod, proposedItem2EndPeriod, fullAmount, monthlyRate, currency);

        tree.mergeProposedItem(proposed1);
        tree.mergeProposedItem(proposed2);
        tree.buildForMerge();

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        final InvoiceItem expected1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, proposedItem1StartPeriod, existingItemStartPeriod, new BigDecimal("1"), monthlyRate, currency);
        expectedResult.add(expected1);
        final InvoiceItem expected2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, existingItemEndPeriod, proposedItem2EndPeriod, new BigDecimal("9.03"), monthlyRate, currency);
        expectedResult.add(expected2);
        verifyResult(tree.getView(), expectedResult);
    }

    @Test(groups = "fast")
    public void testWithBCDChange3() {
        final LocalDate existingItem1StartPeriod = new LocalDate(2014, 5, 17);
        final LocalDate existingItem1EndPeriod = new LocalDate(2014, 6, 17);
        final LocalDate existingItem2StartPeriod = existingItem1EndPeriod;
        final LocalDate existingItem2EndPeriod = new LocalDate(2014, 7, 17);

        final LocalDate proposedItem1StartPeriod = new LocalDate(2014, 5, 17);
        final LocalDate proposedItem1EndPeriod = new LocalDate(2014, 5, 20);
        final LocalDate proposedItem2StartPeriod = proposedItem1EndPeriod;
        final LocalDate proposedItem2EndPeriod = new LocalDate(2014, 6, 20);
        final LocalDate proposedItem3StartPeriod = proposedItem2EndPeriod;
        final LocalDate proposedItem3EndPeriod = new LocalDate(2014, 7, 20);
        final LocalDate proposedItem4StartPeriod = proposedItem3EndPeriod;
        final LocalDate proposedItem4EndPeriod = new LocalDate(2014, 8, 17);

        final BigDecimal monthlyRate = new BigDecimal("10.00");
        final BigDecimal fullAmount = monthlyRate;

        final InvoiceItem item1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, existingItem1StartPeriod, existingItem1EndPeriod, fullAmount, monthlyRate, currency);
        final InvoiceItem item2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, existingItem2StartPeriod, existingItem2EndPeriod, fullAmount, monthlyRate, currency);

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.addItem(item1);
        tree.addItem(item2);
        tree.build();

        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, proposedItem1StartPeriod, proposedItem1EndPeriod, new BigDecimal("1"), monthlyRate, currency);
        final InvoiceItem proposed2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, proposedItem2StartPeriod, proposedItem2EndPeriod, fullAmount, monthlyRate, currency);
        final InvoiceItem proposed3 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, proposedItem3StartPeriod, proposedItem3EndPeriod, fullAmount, monthlyRate, currency);
        final InvoiceItem proposed4 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, proposedItem4StartPeriod, proposedItem4EndPeriod, new BigDecimal("7"), monthlyRate, currency);

        tree.mergeProposedItem(proposed1);
        tree.mergeProposedItem(proposed2);
        tree.mergeProposedItem(proposed3);
        tree.mergeProposedItem(proposed4);
        tree.buildForMerge();

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        final InvoiceItem expected1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, existingItem2EndPeriod, proposedItem3EndPeriod, new BigDecimal("1"), monthlyRate, currency);
        expectedResult.add(expected1);
        final InvoiceItem expected2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, proposedItem3EndPeriod, proposedItem4EndPeriod, new BigDecimal("7"), monthlyRate, currency);
        expectedResult.add(expected2);
        verifyResult(tree.getView(), expectedResult);
    }

    @Test(groups = "fast")
    public void testWithBCDChange4() {
        final LocalDate existingItem1StartPeriod = new LocalDate(2014, 5, 17);
        final LocalDate existingItem1EndPeriod = new LocalDate(2014, 5, 20);
        final LocalDate existingItem2StartPeriod = existingItem1EndPeriod;
        final LocalDate existingItem2EndPeriod = new LocalDate(2014, 6, 20);
        final LocalDate existingItem3StartPeriod = existingItem2EndPeriod;
        final LocalDate existingItem3EndPeriod = new LocalDate(2014, 7, 20);
        final LocalDate existingItem4StartPeriod = existingItem3EndPeriod;
        final LocalDate existingItem4EndPeriod = new LocalDate(2014, 8, 17);

        final LocalDate proposedItem1StartPeriod = new LocalDate(2014, 5, 17);
        final LocalDate proposedItem1EndPeriod = new LocalDate(2014, 6, 17);
        final LocalDate proposedItem2StartPeriod = proposedItem1EndPeriod;
        final LocalDate proposedItem2EndPeriod = new LocalDate(2014, 7, 17);
        final LocalDate proposedItem3StartPeriod = proposedItem2EndPeriod;
        final LocalDate proposedItem3EndPeriod = new LocalDate(2014, 8, 17);

        final BigDecimal monthlyRate = new BigDecimal("10.00");
        final BigDecimal fullAmount = monthlyRate;

        final InvoiceItem item1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, existingItem1StartPeriod, existingItem1EndPeriod, new BigDecimal("1"), monthlyRate, currency);
        final InvoiceItem item2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, existingItem2StartPeriod, existingItem2EndPeriod, fullAmount, monthlyRate, currency);
        final InvoiceItem item3 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, existingItem3StartPeriod, existingItem3EndPeriod, fullAmount, monthlyRate, currency);
        final InvoiceItem item4 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, existingItem4StartPeriod, existingItem4EndPeriod, new BigDecimal("7"), monthlyRate, currency);

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.addItem(item1);
        tree.addItem(item2);
        tree.addItem(item3);
        tree.addItem(item4);
        tree.build();

        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, proposedItem1StartPeriod, proposedItem1EndPeriod, fullAmount, monthlyRate, currency);
        final InvoiceItem proposed2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, proposedItem2StartPeriod, proposedItem2EndPeriod, fullAmount, monthlyRate, currency);
        final InvoiceItem proposed3 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, proposedItem3StartPeriod, proposedItem3EndPeriod, fullAmount, monthlyRate, currency);

        tree.mergeProposedItem(proposed1);
        tree.mergeProposedItem(proposed2);
        tree.mergeProposedItem(proposed3);
        tree.buildForMerge();

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        verifyResult(tree.getView(), expectedResult);
    }

    @Test(groups = "fast")
    public void testAnnualToNewAnnualWithLaterDate() {

        final LocalDate startAnnual1 = new LocalDate(2015, 1, 1);
        final LocalDate endAnnual1 = new LocalDate(2016, 1, 1);
        final LocalDate startAnnual2 = new LocalDate(2015, 3, 15);
        final LocalDate endAnnual2 = new LocalDate(2016, 3, 1);

        final BigDecimal annualRate1 = new BigDecimal("50.00");

        final BigDecimal annualRate2 = new BigDecimal("100.00");

        final InvoiceItem annual1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startAnnual1, endAnnual1, annualRate1, annualRate1, currency);
        final InvoiceItem repair = new RepairAdjInvoiceItem(invoiceId, accountId, startAnnual2, endAnnual1, annualRate1.negate(), currency, annual1.getId());
        final InvoiceItem annual2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startAnnual2, endAnnual2, annualRate2, annualRate2, currency);

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        final InvoiceItem annual1Prorated = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startAnnual1, startAnnual2, new BigDecimal("10.0"), annualRate1, currency);
        expectedResult.add(annual1Prorated);
        expectedResult.add(annual2);

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.addItem(annual1);
        tree.addItem(annual2);
        tree.addItem(repair);
        tree.build();
        verifyResult(tree.getView(), expectedResult);
    }

    @Test(groups = "fast")
    public void testAnnualWithBlocking1() {

        final LocalDate startDate = new LocalDate(2014, 1, 1);
        final LocalDate endDate = new LocalDate(2015, 1, 1);

        final BigDecimal yearlyRate = new BigDecimal("100.00");
        final BigDecimal yearlyAmount = yearlyRate;

        final InvoiceItem annual = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, endDate, yearlyAmount, yearlyRate, currency);

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.addItem(annual);
        tree.build();

        tree.flatten(true);

        final LocalDate startBlock = new LocalDate(2014, 1, 14);
        final LocalDate endBlock = new LocalDate(2014, 2, 8);

        final LocalDate newEndDate = new LocalDate(2015, 2, 1);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, startBlock, BigDecimal.TEN, yearlyAmount, currency);
        final InvoiceItem proposed2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, endBlock, newEndDate, new BigDecimal("90.0"), yearlyAmount, currency);

        tree.mergeProposedItem(proposed1);
        tree.mergeProposedItem(proposed2);
        tree.buildForMerge();

        final InvoiceItem expected1 = new RepairAdjInvoiceItem(invoiceId, accountId, startBlock, endBlock, new BigDecimal("-6.85"), currency, annual.getId());
        final InvoiceItem expected2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, endDate, newEndDate, new BigDecimal("7.79"), yearlyAmount, currency);

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        expectedResult.addAll(ImmutableList.of(expected1, expected2));
        verifyResult(tree.getView(), expectedResult);
    }

    @Test(groups = "fast")
    public void testAnnualWithBlocking2() {

        final LocalDate startDate = new LocalDate(2014, 1, 1);
        final LocalDate endDate = new LocalDate(2015, 1, 1);

        final LocalDate startBlock = new LocalDate(2014, 1, 14);
        final LocalDate endBlock = new LocalDate(2014, 2, 8);
        final LocalDate newEndDate = new LocalDate(2015, 2, 1);

        final BigDecimal yearlyRate = new BigDecimal("100.00");
        final BigDecimal yearlyAmount = yearlyRate;

        final InvoiceItem annual1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, endDate, yearlyAmount, yearlyRate, currency);
        final InvoiceItem repair = new RepairAdjInvoiceItem(invoiceId, accountId, startBlock, endBlock, new BigDecimal("-6.85"), currency, annual1.getId());
        final InvoiceItem annual2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, endDate, newEndDate, yearlyAmount, yearlyRate, currency);

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.addItem(annual1);
        tree.addItem(repair);
        tree.addItem(annual2);
        tree.build();

        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, startBlock, BigDecimal.TEN, yearlyAmount, currency);
        final InvoiceItem proposed2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, endBlock, newEndDate, new BigDecimal("90.0"), yearlyAmount, currency);

        tree.mergeProposedItem(proposed1);
        tree.mergeProposedItem(proposed2);
        tree.buildForMerge();

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        expectedResult.addAll(ImmutableList.of());
        verifyResult(tree.getView(), expectedResult);
    }

    @Test(groups = "fast", description = "https://github.com/killbill/killbill/issues/1205")
    public void testBlockUnblock() {

        final LocalDate startDate = new LocalDate(2019, 4, 27);
        final LocalDate blockDate = new LocalDate(2019, 5, 3);
        final LocalDate endDate = new LocalDate(2019, 5, 27);

        final BigDecimal rate = new BigDecimal("29.95");
        final BigDecimal amount = rate;

        final InvoiceItem recurring1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, endDate, amount, rate, currency);
        final InvoiceItem repair1 = new RepairAdjInvoiceItem(invoiceId, accountId, blockDate, endDate, new BigDecimal("-23.96"), currency, recurring1.getId());
        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.addItem(recurring1);
        tree.addItem(repair1);
        tree.build();

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        expectedResult.add(new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, blockDate, new BigDecimal("5.99"), rate, currency));

        verifyResult(tree.getView(), expectedResult);

        tree.flatten(true);
        tree.buildForMerge();

        expectedResult.clear();
        expectedResult.addAll(ImmutableList.of(new RepairAdjInvoiceItem(invoiceId, accountId, startDate, blockDate, new BigDecimal("-5.99"), currency, recurring1.getId())));
        verifyResult(tree.getView(), expectedResult);
    }

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

        final InvoiceItem initial = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, endDate, amount, rate, currency);

        final InvoiceItem newItem1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startRepairDate1, endRepairDate1, amount, rate, currency);
        final InvoiceItem repair1 = new RepairAdjInvoiceItem(invoiceId, accountId, startRepairDate1, endRepairDate1, amount.negate(), currency, initial.getId());

        final InvoiceItem newItem11 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startRepairDate11, endRepairDate12, amount, rate, currency);
        final InvoiceItem repair12 = new RepairAdjInvoiceItem(invoiceId, accountId, startRepairDate11, endRepairDate12, amount.negate(), currency, newItem1.getId());

        final InvoiceItem newItem2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startRepairDate2, endRepairDate2, amount, rate, currency);
        final InvoiceItem repair2 = new RepairAdjInvoiceItem(invoiceId, accountId, startRepairDate2, endRepairDate2, amount.negate(), currency, initial.getId());

        final InvoiceItem newItem21 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startRepairDate21, endRepairDate22, amount, rate, currency);
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
    public void testSimpleRepair() {

        final LocalDate startDate = new LocalDate(2014, 1, 1);
        final LocalDate endDate = new LocalDate(2014, 2, 1);

        final LocalDate repairDate = new LocalDate(2014, 1, 23);

        final BigDecimal rate1 = new BigDecimal("12.00");
        final BigDecimal amount1 = rate1;

        final BigDecimal rate2 = new BigDecimal("14.85");
        final BigDecimal amount2 = rate2;

        final InvoiceItem initial = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, endDate, amount1, rate1, currency);
        final InvoiceItem newItem = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, "someelse", "someelse", "someelse", null, repairDate, endDate, amount2, rate2, currency);
        final InvoiceItem repair = new RepairAdjInvoiceItem(invoiceId, accountId, repairDate, endDate, amount1.negate(), currency, initial.getId());

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        final InvoiceItem expected1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, repairDate, new BigDecimal("8.52"), rate1, currency);
        expectedResult.add(expected1);
        final InvoiceItem expected2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, "someelse", "someelse", "someelse", null, repairDate, endDate, amount2, rate2, currency);
        expectedResult.add(expected2);

        SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.addItem(initial);
        tree.addItem(newItem);
        tree.addItem(repair);
        tree.build();
    }


    @Test(groups = "fast")
    public void testInvalidRepair() {
        final LocalDate startDate = new LocalDate(2014, 1, 1);
        final LocalDate endDate = new LocalDate(2014, 2, 1);

        final BigDecimal rate = new BigDecimal("12.00");

        final InvoiceItem initial = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, endDate, rate, rate, currency);
        final InvoiceItem tooEarlyRepair = new RepairAdjInvoiceItem(invoiceId, accountId, startDate.minusDays(1), endDate, rate.negate(), currency, initial.getId());
        final InvoiceItem tooLateRepair = new RepairAdjInvoiceItem(invoiceId, accountId, startDate, endDate.plusDays(1), rate.negate(), currency, initial.getId());

        List<InvoiceItem> result;
        SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.addItem(initial);
        tree.addItem(tooEarlyRepair);
        tree.build();

        result  = tree.getView();
        Assert.assertEquals(result.size(), 0);

        tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.addItem(initial);
        tree.addItem(tooLateRepair);
        tree.build();
        result  = tree.getView();
        Assert.assertEquals(result.size(), 0);

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

        final InvoiceItem initial = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, endDate, amount1, rate1, currency);
        final InvoiceItem newItem1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, repairDate1, endDate, amount2, rate2, currency);
        final InvoiceItem repair1 = new RepairAdjInvoiceItem(invoiceId, accountId, repairDate1, endDate, amount1.negate(), currency, initial.getId());

        final InvoiceItem newItem2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, repairDate2, endDate, amount3, rate3, currency);
        final InvoiceItem repair2 = new RepairAdjInvoiceItem(invoiceId, accountId, repairDate2, endDate, amount2.negate(), currency, newItem1.getId());

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        final InvoiceItem expected1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, repairDate1, new BigDecimal("8.52"), rate1, currency);
        expectedResult.add(expected1);
        final InvoiceItem expected2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, repairDate1, repairDate2, new BigDecimal("4.95"), rate2, currency);
        expectedResult.add(expected2);
        final InvoiceItem expected3 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, repairDate2, endDate, amount3, rate3, currency);
        expectedResult.add(expected3);

        SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.addItem(initial);
        tree.addItem(newItem1);
        tree.addItem(repair1);
        tree.addItem(newItem2);
        tree.addItem(repair2);
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

        final InvoiceItem initial = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, endDate, amount1, rate1, currency);
        final InvoiceItem block1 = new RepairAdjInvoiceItem(invoiceId, accountId, blockStart1, unblockStart1, amount1.negate(), currency, initial.getId());
        final InvoiceItem block2 = new RepairAdjInvoiceItem(invoiceId, accountId, blockStart2, unblockStart2, amount1.negate(), currency, initial.getId());

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        final InvoiceItem expected1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, blockStart1, new BigDecimal("2.71"), rate1, currency);
        expectedResult.add(expected1);
        final InvoiceItem expected2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, unblockStart1, blockStart2, new BigDecimal("2.71"), rate1, currency);
        expectedResult.add(expected2);
        final InvoiceItem expected3 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, unblockStart2, endDate, new BigDecimal("3.48"), rate1, currency);
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

        final InvoiceItem first = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate1, startDate2, amount1, rate1, currency);
        final InvoiceItem second = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate2, endDate, amount1, rate1, currency);
        final InvoiceItem block1 = new RepairAdjInvoiceItem(invoiceId, accountId, blockDate, startDate2, amount1.negate(), currency, first.getId());
        final InvoiceItem block2 = new RepairAdjInvoiceItem(invoiceId, accountId, startDate2, unblockDate, amount1.negate(), currency, first.getId());

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        final InvoiceItem expected1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate1, blockDate, new BigDecimal("9.29"), rate1, currency);
        final InvoiceItem expected2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, unblockDate, endDate, new BigDecimal("9.43"), rate1, currency);
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

        final InvoiceItem monthly1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, endMonthly1, monthlyAmount, monthlyRate, currency);
        final InvoiceItem monthly2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, endMonthly1, endMonthly2, monthlyAmount, monthlyRate, currency);
        final InvoiceItem repair = new RepairAdjInvoiceItem(invoiceId, accountId, switchToAnnualDate, endMonthly2, monthlyAmount.negate(), currency, monthly2.getId());
        final InvoiceItem leadingAnnualProration = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, switchToAnnualDate, endMonthly2, yearlyAmount, yearlyRate, currency);
        final InvoiceItem annual = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, endMonthly2, endDate, yearlyAmount, yearlyRate, currency);

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        expectedResult.add(monthly1);
        final InvoiceItem monthly2Prorated = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, endMonthly1, switchToAnnualDate, new BigDecimal("9.43"), monthlyRate, currency);
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

        final InvoiceItem monthly1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, endMonthly1, monthlyAmount, monthlyRate, currency);
        final InvoiceItem monthly2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, endMonthly1, endMonthly2, monthlyAmount, monthlyRate, currency);
        final InvoiceItem repair = new RepairAdjInvoiceItem(invoiceId, accountId, switchToAnnualDate, endMonthly2, monthlyAmount.negate(), currency, monthly2.getId());
        final InvoiceItem annual = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, switchToAnnualDate, endDate, yearlyAmount, yearlyRate, currency);

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        expectedResult.add(monthly1);
        final InvoiceItem monthly2Prorated = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, endMonthly1, switchToAnnualDate, new BigDecimal("9.43"), monthlyRate, currency);
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
    public void testDoubleBillingOnDifferentInvoices() {
        final LocalDate startDate1 = new LocalDate(2012, 5, 1);
        final LocalDate endDate = new LocalDate(2012, 6, 1);

        final BigDecimal rate = BigDecimal.TEN;
        final BigDecimal amount = rate;

        final InvoiceItem recurring1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate1, endDate, amount, rate, currency);
        final InvoiceItem recurring2 = new RecurringInvoiceItem(UUID.randomUUID(), accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate1, endDate, amount, rate, currency);

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

        final InvoiceItem initial = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, endDate, amount1, rate1, currency);
        final InvoiceItem newItem1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, repairDate1, endDate, amount2, rate2, currency);
        final InvoiceItem repair1 = new RepairAdjInvoiceItem(invoiceId, accountId, repairDate1, endDate, amount1.negate(), currency, initial.getId());

        final InvoiceItem newItem2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, repairDate2, endDate, amount3, rate3, currency);
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
    public void testMergeWithNoExisting() {

        final LocalDate startDate = new LocalDate(2014, 1, 1);
        final LocalDate endDate = new LocalDate(2014, 2, 1);

        final BigDecimal monthlyRate = new BigDecimal("12.00");
        final BigDecimal monthlyAmount = monthlyRate;

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, endDate, monthlyAmount, monthlyRate, currency);
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
        final InvoiceItem monthly1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, endDate, monthlyAmount, monthlyRate, currency);
        tree.addItem(monthly1);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, endDate, monthlyAmount, monthlyRate, currency);

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
        final InvoiceItem monthly1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, endDate, monthlyAmount1, monthlyRate1, currency);
        tree.addItem(monthly1);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, endDate, monthlyAmount2, monthlyRate2, currency);

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
        final InvoiceItem monthly1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, endDate, monthlyAmount1, monthlyRate1, currency);
        tree.addItem(monthly1);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, blockDate, endDate, monthlyAmount1, monthlyRate1, currency);

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
        final InvoiceItem monthly1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, endDate, monthlyAmount1, monthlyRate1, currency);
        tree.addItem(monthly1);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, cancelDate, monthlyAmount1, monthlyRate1, currency);
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
        final InvoiceItem monthly1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, endDate, monthlyAmount1, monthlyRate1, currency);
        tree.addItem(monthly1);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, blockDate, monthlyAmount1, monthlyRate1, currency);
        final InvoiceItem proposed2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, unblockDate, endDate, monthlyAmount1, monthlyRate1, currency);

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
        final InvoiceItem monthly = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, endDate, monthlyAmount, monthlyRate, currency);
        tree.addItem(monthly);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, blockDate1, monthlyAmount, monthlyRate, currency);
        final InvoiceItem proposed2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, unblockDate1, blockDate2, monthlyAmount, monthlyRate, currency);
        final InvoiceItem proposed3 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, unblockDate2, endDate, monthlyAmount, monthlyRate, currency);

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
        final InvoiceItem monthlyAgain = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, endDate, monthlyAmount, monthlyRate, currency);
        treeAgain.addItem(monthlyAgain);
        treeAgain.flatten(true);

        final InvoiceItem proposed2Again = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, unblockDate1, blockDate2, monthlyAmount, monthlyRate, currency);
        final InvoiceItem proposed1Again = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, blockDate1, monthlyAmount, monthlyRate, currency);
        final InvoiceItem proposed3Again = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, unblockDate2, endDate, monthlyAmount, monthlyRate, currency);

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
        final InvoiceItem monthly1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, endDate, monthlyAmount1, monthlyRate1, currency);
        tree.addItem(monthly1);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, upgradeDate, monthlyAmount1, monthlyRate1, currency);
        final InvoiceItem proposed2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, "foo", "foo", "foo", null, upgradeDate, endDate, monthlyAmount2, monthlyRate2, currency);
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
        final InvoiceItem initial = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, endDate, amount1, rate1, currency);
        final InvoiceItem newItem1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, "foo", "foo", "foo", null, change1, endDate, proratedAmount2, rate2, currency);
        final InvoiceItem repair1 = new RepairAdjInvoiceItem(invoiceId, accountId, change1, endDate, new BigDecimal("-483.86"), currency, initial.getId());

        tree.addItem(initial);
        tree.addItem(newItem1);
        tree.addItem(repair1);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, change1, amount1, rate1, currency);
        final InvoiceItem proposed2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, "foo", "foo", "foo", null, change1, change2, proratedAmount3, rate2, currency);
        final InvoiceItem proposed3 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, "bar", "bar", "bar", null, change2, endDate, proratedAmount3, rate3, currency);
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
        final InvoiceItem monthly = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, endDate, monthlyAmount, monthlyRate, currency);
        final InvoiceItem fixed = new FixedPriceInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, fixedAmount, currency);
        tree.addItem(monthly);
        tree.addItem(fixed);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, endDate, monthlyAmount, monthlyRate, currency);
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
        final InvoiceItem monthly = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, endDate, monthlyAmount, monthlyRate, currency);
        tree.addItem(monthly);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, endDate, monthlyAmount, monthlyRate, currency);
        final InvoiceItem fixed = new FixedPriceInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, fixedAmount, currency);
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
        final InvoiceItem initial = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, endDate, amount1, rate1, currency);
        final InvoiceItem itemAdj = new ItemAdjInvoiceItem(initial, itemAdjDate, new BigDecimal("-2.00"), currency);
        tree.addItem(initial);
        tree.addItem(itemAdj);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, cancelDate, amount1, rate1, currency);
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
        final InvoiceItem initial = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, endDate, amount1, rate1, currency);
        final InvoiceItem itemAdj = new ItemAdjInvoiceItem(initial, itemAdjDate, new BigDecimal("-10.00"), currency);
        tree.addItem(initial);
        tree.addItem(itemAdj);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, cancelDate, amount1, rate1, currency);
        tree.mergeProposedItem(proposed1);
        tree.buildForMerge();

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        final InvoiceItem repair1 = new RepairAdjInvoiceItem(invoiceId, accountId, cancelDate, endDate, new BigDecimal("-2.00"), currency, initial.getId());
        expectedResult.add(repair1);

        verifyResult(tree.getView(), expectedResult);
    }

    @Test(groups = "fast")
    public void testRepairWithFullItemAdjustment() {

        final LocalDate startDate = new LocalDate(2014, 1, 1);
        final LocalDate itemAdjDate = new LocalDate(2014, 1, 2);
        final LocalDate endDate = new LocalDate(2014, 2, 1);

        final LocalDate cancelDate = new LocalDate(2014, 1, 23);

        final BigDecimal rate1 = new BigDecimal("12.00");
        final BigDecimal amount1 = rate1;

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        final InvoiceItem originalAdjusted = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, endDate, amount1, rate1, currency);
        final InvoiceItem itemAdj = new ItemAdjInvoiceItem(originalAdjusted, itemAdjDate, amount1.negate(), currency);

        // Simulate
        final InvoiceItem newItem = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, cancelDate, amount1, rate1, currency);

        tree.addItem(originalAdjusted);
        tree.addItem(itemAdj);
        tree.addItem(newItem);
        tree.flatten(true);

        tree.mergeProposedItem(new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, cancelDate, amount1, rate1, currency));
        tree.buildForMerge();

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
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

        final InvoiceItem monthly1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, endMonthly1, monthlyAmount, monthlyRate, currency);
        final InvoiceItem monthly2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, endMonthly1, endMonthly2, monthlyAmount, monthlyRate, currency);

        // First test with items in order
        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        tree.addItem(monthly1);
        tree.addItem(monthly2);
        tree.flatten(true);

        final InvoiceItem proposed = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, switchToAnnualDate, endDate, yearlyAmount, yearlyRate, currency);
        final InvoiceItem proposedMonthly1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, endMonthly1, monthlyAmount, monthlyRate, currency);
        tree.mergeProposedItem(proposedMonthly1);
        final InvoiceItem proRatedMonthly2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, endMonthly1, switchToAnnualDate, monthlyAmount, monthlyRate, currency);
        tree.mergeProposedItem(proRatedMonthly2);
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
        final InvoiceItem yearly1 = new RecurringInvoiceItem(id1, new DateTime(), invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, new LocalDate("2014-01-01"), new LocalDate("2015-01-01"), BigDecimal.TEN, BigDecimal.TEN, currency);
        tree.addItem(yearly1);

        final UUID id2 = UUID.fromString("48db1317-9a6e-4666-bcc5-fc7d3d0defc8");
        final InvoiceItem newItem = new RecurringInvoiceItem(id2, new DateTime(), invoiceId, accountId, bundleId, subscriptionId, "other-product", "other-plan", "other-plan", null, new LocalDate("2014-08-01"), new LocalDate("2015-01-01"), BigDecimal.ONE, BigDecimal.ONE, currency);
        tree.addItem(newItem);

        final UUID id3 = UUID.fromString("02ec57f5-2723-478b-86ba-ebeaedacb9db");
        final InvoiceItem repair = new RepairAdjInvoiceItem(id3, new DateTime(), invoiceId, accountId, new LocalDate("2014-08-01"), new LocalDate("2015-01-01"), BigDecimal.TEN.negate(), currency, yearly1.getId());
        tree.addItem(repair);

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        tree.getRoot().jsonSerializeTree(new ObjectMapper(), outputStream);

        final String json = outputStream.toString("UTF-8");
        final String expectedJson = "[{\"start\":\"2014-01-01\",\"end\":\"2015-01-01\",\"items\":[{\"id\":\"e8ba6ce7-9bd4-417d-af53-70951ecaa99f\",\"startDate\":\"2014-01-01\",\"endDate\":\"2015-01-01\",\"amount\":10.00,\"currency\":\"USD\",\"linkedId\":null,\"action\":\"ADD\",\"fullyAdjusted\":false}]},[{\"start\":\"2014-08-01\",\"end\":\"2015-01-01\",\"items\":[{\"id\":\"48db1317-9a6e-4666-bcc5-fc7d3d0defc8\",\"startDate\":\"2014-08-01\",\"endDate\":\"2015-01-01\",\"amount\":1.00,\"currency\":\"USD\",\"linkedId\":null,\"action\":\"ADD\",\"fullyAdjusted\":false},{\"id\":\"02ec57f5-2723-478b-86ba-ebeaedacb9db\",\"startDate\":\"2014-08-01\",\"endDate\":\"2015-01-01\",\"amount\":10.00,\"currency\":\"USD\",\"linkedId\":\"e8ba6ce7-9bd4-417d-af53-70951ecaa99f\",\"action\":\"CANCEL\",\"fullyAdjusted\":false}]}]]";

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

        final InvoiceItem existing1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, endDate, monthlyAmount1, monthlyRate1, currency);
        tree.addItem(existing1);
        // Fully item adjust the recurring item
        final InvoiceItem existingItemAdj1 = new ItemAdjInvoiceItem(existing1, startDate, monthlyRate1.negate(), currency);
        tree.addItem(existingItemAdj1);
        tree.flatten(true);

        //printTree(tree);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, cancelDate, monthlyAmount1, monthlyRate1, currency);
        tree.mergeProposedItem(proposed1);
        tree.buildForMerge();

        //printTree(tree);

        // We except to see a repair for the piece cancelled with a $0 price since item was fully adjusted
        final List<InvoiceItem> expectedResult = ImmutableList.<InvoiceItem>of(new RepairAdjInvoiceItem(invoiceId, accountId, cancelDate, endDate, BigDecimal.ZERO, Currency.USD, existing1.getId()));
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

        final InvoiceItem existing1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, endDate, monthlyAmount1, monthlyRate1, currency);
        tree.addItem(existing1);
        // Partially item adjust the recurring item
        final InvoiceItem existingItemAdj1 = new ItemAdjInvoiceItem(existing1, startDate, monthlyRate1.negate().add(BigDecimal.ONE), currency);
        tree.addItem(existingItemAdj1);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, cancelDate, monthlyAmount1, monthlyRate1, currency);
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
                                                                      null,
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
                                                                        null,
                                                                        correctStartDate,
                                                                        endDate,
                                                                        amount,
                                                                        rate,
                                                                        currency);

        tree.flatten(true);
        tree.mergeProposedItem(correctInitialItem);
        tree.buildForMerge();

        final InvoiceItem expected = new RecurringInvoiceItem(invoiceId,
                                                                accountId,
                                                                bundleId,
                                                                subscriptionId,
                                                                productName,
                                                                planName,
                                                                phaseName,
                                                                null,
                                                                correctStartDate,
                                                                wrongStartDate,
                                                                new BigDecimal("0.40"),
                                                                rate,
                                                                currency);
        final List<InvoiceItem> expectedResult = ImmutableList.<InvoiceItem>of(expected);
        verifyResult(tree.getView(), expectedResult);

    }

    @Test(groups = "fast")
    public void testWithFreeRecurring() {
        final LocalDate startDate = new LocalDate(2012, 8, 1);
        final LocalDate endDate = new LocalDate(2012, 9, 1);

        final BigDecimal monthlyRate1 = new BigDecimal("12.00");
        final BigDecimal monthlyRate2 = new BigDecimal("24.00");

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);
        final InvoiceItem freeMonthly = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, endDate, BigDecimal.ZERO, BigDecimal.ZERO, currency);
        tree.addItem(freeMonthly);
        final InvoiceItem payingMonthly1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, endDate, monthlyRate1, monthlyRate1, currency);
        tree.addItem(payingMonthly1);
        tree.flatten(true);

        final InvoiceItem proposedPayingMonthly2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, endDate, monthlyRate2, monthlyRate2, currency);
        tree.mergeProposedItem(proposedPayingMonthly2);
        tree.buildForMerge();

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        expectedResult.add(proposedPayingMonthly2);
        final InvoiceItem repair = new RepairAdjInvoiceItem(invoiceId, accountId, startDate, endDate, monthlyRate1.negate(), currency, payingMonthly1.getId());
        expectedResult.add(repair);
        verifyResult(tree.getView(), expectedResult);
    }

    @Test(groups = "fast", description = "https://github.com/killbill/killbill/issues/1251")
    public void testRecuring$0PriceNoCatalogEffectiveDate() {
        final LocalDate startDate = new LocalDate(2019, 11, 1);
        final LocalDate endDate = new LocalDate(2019, 12, 1);

        final SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId, invoiceId);

        final DateTime catalogEffectiveDate = new DateTime();

        final InvoiceItem existing1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null /* catalogEffectiveDate  */, startDate, endDate, BigDecimal.ZERO, BigDecimal.ZERO, currency);
        tree.addItem(existing1);
        tree.flatten(true);

        final InvoiceItem proposed1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, catalogEffectiveDate, startDate, endDate, BigDecimal.ZERO, BigDecimal.ZERO, currency);
        tree.mergeProposedItem(proposed1);
        tree.buildForMerge();

        final List<InvoiceItem> expectedResult = ImmutableList.<InvoiceItem>of();
        verifyResult(tree.getView(), expectedResult);
    }

    private void printTree(final SubscriptionItemTree tree) {
        System.out.println(TreePrinter.print(tree.getRoot()));
    }

    private void verifyResult(final List<InvoiceItem> result, final List<InvoiceItem> expectedResult) {
        assertEquals(result.size(), expectedResult.size());
        for (int i = 0; i < expectedResult.size(); i++) {
            assertTrue(result.get(i).matches(expectedResult.get(i)));
        }
    }
}
