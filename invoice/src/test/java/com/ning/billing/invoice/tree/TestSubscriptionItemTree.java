package com.ning.billing.invoice.tree;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.InvoiceItem;
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
        final InvoiceItem expected1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, repairDate, BigDecimal.ONE, rate1, currency);
        expectedResult.add(expected1);
        final InvoiceItem expected2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, "someelse", "someelse", repairDate, endDate, amount2, rate2, currency);
        expectedResult.add(expected2);

        // First test with items in order
        List<InvoiceItem> result = Lists.newLinkedList();
        SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId);
        tree.addItem(initial);
        tree.addItem(newItem);
        tree.addItem(repair);
        tree.build(result);
        verifyResult(result, expectedResult);

        result = Lists.newLinkedList();
        tree = new SubscriptionItemTree(subscriptionId);
        tree.addItem(repair);
        tree.addItem(newItem);
        tree.addItem(initial);
        tree.build(result);
        verifyResult(result, expectedResult);

        result = Lists.newLinkedList();
        tree = new SubscriptionItemTree(subscriptionId);
        tree.addItem(repair);
        tree.addItem(initial);
        tree.addItem(newItem);
        tree.build(result);
        verifyResult(result, expectedResult);
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
        final InvoiceItem expected1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, repairDate1, BigDecimal.ONE, rate1, currency);
        expectedResult.add(expected1);
        final InvoiceItem expected2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, repairDate1, repairDate2, BigDecimal.ONE, rate2, currency);
        expectedResult.add(expected2);
        final InvoiceItem expected3 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, repairDate2, endDate, amount3, rate3, currency);
        expectedResult.add(expected3);


        // First test with items in order
        List<InvoiceItem> result = Lists.newLinkedList();

        SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId);
        tree.addItem(initial);
        tree.addItem(newItem1);
        tree.addItem(repair1);
        tree.addItem(newItem2);
        tree.addItem(repair2);
        tree.build(result);
        verifyResult(result, expectedResult);

        result = Lists.newLinkedList();
        tree = new SubscriptionItemTree(subscriptionId);
        tree.addItem(repair2);
        tree.addItem(newItem1);
        tree.addItem(newItem2);
        tree.addItem(repair1);
        tree.addItem(initial);
        tree.build(result);
        verifyResult(result, expectedResult);

        result = Lists.newLinkedList();
        tree = new SubscriptionItemTree(subscriptionId);
        tree.addItem(repair1);
        tree.addItem(newItem1);
        tree.addItem(initial);
        tree.addItem(repair2);
        tree.addItem(newItem2);
        tree.build(result);
        verifyResult(result, expectedResult);
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
        final InvoiceItem expected1 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, blockStart1, BigDecimal.ONE, rate1, currency);
        expectedResult.add(expected1);
        expectedResult.add(block1);
        final InvoiceItem expected2 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, unblockStart1, blockStart2, BigDecimal.ONE, rate1, currency);
        expectedResult.add(expected2);
        expectedResult.add(block2);
        final InvoiceItem expected3 = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, unblockStart2, endDate, BigDecimal.ONE, rate1, currency);
        expectedResult.add(expected3);


        // First test with items in order
        List<InvoiceItem> result = Lists.newLinkedList();

        SubscriptionItemTree tree = new SubscriptionItemTree(subscriptionId);
        tree.addItem(initial);
        tree.addItem(block1);
        tree.addItem(block2);
        tree.build(result);
        verifyResult(result, expectedResult);
    }


    private void verifyResult(final List<InvoiceItem> result, final List<InvoiceItem> expectedResult) {
        assertEquals(result.size(), expectedResult.size());
        for (int i = 0; i < expectedResult.size(); i++) {
            assertTrue(result.get(i).matches(expectedResult.get(i)));
        }
    }

}
