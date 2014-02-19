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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.model.ItemAdjInvoiceItem;
import com.ning.billing.invoice.model.RecurringInvoiceItem;
import com.ning.billing.invoice.model.RepairAdjInvoiceItem;

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
        verifyResult(tree.getSimplifiedView(), expectedResult);

        tree = new SubscriptionItemTree(subscriptionId);
        tree.addItem(repair);
        tree.addItem(newItem);
        tree.addItem(initial);
        tree.build();
        verifyResult(tree.getSimplifiedView(), expectedResult);

        tree = new SubscriptionItemTree(subscriptionId);
        tree.addItem(repair);
        tree.addItem(initial);
        tree.addItem(newItem);
        tree.build();
        verifyResult(tree.getSimplifiedView(), expectedResult);
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
        verifyResult(tree.getSimplifiedView(), expectedResult);

        tree = new SubscriptionItemTree(subscriptionId);
        tree.addItem(repair2);
        tree.addItem(newItem1);
        tree.addItem(newItem2);
        tree.addItem(repair1);
        tree.addItem(initial);
        tree.build();
        verifyResult(tree.getSimplifiedView(), expectedResult);

        tree = new SubscriptionItemTree(subscriptionId);
        tree.addItem(repair1);
        tree.addItem(newItem1);
        tree.addItem(initial);
        tree.addItem(repair2);
        tree.addItem(newItem2);
        tree.build();
        verifyResult(tree.getSimplifiedView(), expectedResult);
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
        verifyResult(tree.getSimplifiedView(), expectedResult);
    }


    @Test(groups = "fast")
    public void testInvoiceItemAdj() {

        final LocalDate startDate = new LocalDate(2014, 1, 1);
        final LocalDate itemAdjDate = new LocalDate(2014, 1, 7);
        final LocalDate endDate = new LocalDate(2014, 2, 1);


        final BigDecimal rate1 = new BigDecimal("12.00");
        final BigDecimal amount1 = rate1;

        final InvoiceItem initial = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount1, rate1, currency);
        final InvoiceItem adj = new ItemAdjInvoiceItem(initial, itemAdjDate, amount1, currency);

        final List<InvoiceItem> expectedResult = Lists.newLinkedList();
        expectedResult.add(initial);

        // First test with items in order
        SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId);
        tree.addItem(initial);
        tree.addItem(adj);
        tree.build();
        verifyResult(tree.getSimplifiedView(), expectedResult);
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
        verifyResult(tree.getSimplifiedView(), expectedResult);
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
        verifyResult(tree.getSimplifiedView(), expectedResult);


        tree = new SubscriptionItemTree(subscriptionId);
        tree.addItem(monthly1);
        tree.addItem(repair);
        tree.addItem(annual);
        tree.addItem(monthly2);
        tree.build();
        verifyResult(tree.getSimplifiedView(), expectedResult);

        tree = new SubscriptionItemTree(subscriptionId);
        tree.addItem(monthly1);
        tree.addItem(monthly2);
        tree.addItem(annual);
        tree.addItem(repair);
        tree.build();
        verifyResult(tree.getSimplifiedView(), expectedResult);
    }

    private void verifyResult(final List<InvoiceItem> result, final List<InvoiceItem> expectedResult) {
        assertEquals(result.size(), expectedResult.size());
        for (int i = 0; i < expectedResult.size(); i++) {
            assertTrue(result.get(i).matches(expectedResult.get(i)));
        }
    }

}
