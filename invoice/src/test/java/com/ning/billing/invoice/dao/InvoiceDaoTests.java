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

package com.ning.billing.invoice.dao;

import com.ning.billing.catalog.DefaultPrice;
import com.ning.billing.catalog.MockInternationalPrice;
import com.ning.billing.catalog.MockPlan;
import com.ning.billing.catalog.MockPlanPhase;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.entitlement.api.billing.BillingEvent;
import com.ning.billing.entitlement.api.billing.BillingModeType;
import com.ning.billing.entitlement.api.billing.DefaultBillingEvent;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionTransition.SubscriptionTransitionType;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.model.BillingEventSet;
import com.ning.billing.invoice.model.DefaultInvoice;
import com.ning.billing.invoice.model.DefaultInvoiceGenerator;
import com.ning.billing.invoice.model.DefaultInvoicePayment;
import com.ning.billing.invoice.model.InvoiceGenerator;
import com.ning.billing.invoice.model.InvoiceItemList;
import com.ning.billing.invoice.model.RecurringInvoiceItem;
import com.ning.billing.mock.BrainDeadProxyFactory;
import com.ning.billing.mock.BrainDeadProxyFactory.ZombieControl;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.DefaultClock;
import org.joda.time.DateTime;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

@Test(groups = {"invoicing", "invoicing-invoiceDao"})
public class InvoiceDaoTests extends InvoiceDaoTestBase {
    private final int NUMBER_OF_DAY_BETWEEN_RETRIES = 8;
    private final Clock clock = new DefaultClock();

    @Test
    public void testCreationAndRetrievalByAccount() {
        UUID accountId = UUID.randomUUID();
        Invoice invoice = new DefaultInvoice(accountId, clock.getUTCNow(), Currency.USD, clock);
        DateTime invoiceDate = invoice.getInvoiceDate();

        invoiceDao.create(invoice);

        List<Invoice> invoices = invoiceDao.getInvoicesByAccount(accountId);
        assertNotNull(invoices);
        assertEquals(invoices.size(), 1);
        Invoice thisInvoice = invoices.get(0);
        assertEquals(invoice.getAccountId(), accountId);
        assertTrue(thisInvoice.getInvoiceDate().compareTo(invoiceDate) == 0);
        assertEquals(thisInvoice.getCurrency(), Currency.USD);
        assertEquals(thisInvoice.getNumberOfItems(), 0);
        assertTrue(thisInvoice.getTotalAmount().compareTo(BigDecimal.ZERO) == 0);
    }

    @Test
    public void testInvoicePayment() {
        UUID accountId = UUID.randomUUID();
        Invoice invoice = new DefaultInvoice(accountId, clock.getUTCNow(), Currency.USD, clock);
        UUID invoiceId = invoice.getId();
        UUID subscriptionId = UUID.randomUUID();
        DateTime startDate = new DateTime(2010, 1, 1, 0, 0, 0, 0);
        DateTime endDate = new DateTime(2010, 4, 1, 0, 0, 0, 0);
        InvoiceItem invoiceItem = new RecurringInvoiceItem(invoiceId, subscriptionId, "test plan", "test phase", startDate, endDate,
                new BigDecimal("21.00"), new BigDecimal("7.00"), Currency.USD, clock.getUTCNow(), clock.getUTCNow());
        invoice.addInvoiceItem(invoiceItem);
        invoiceDao.create(invoice);

        Invoice savedInvoice = invoiceDao.getById(invoiceId);
        assertNotNull(savedInvoice);
        assertEquals(savedInvoice.getTotalAmount().compareTo(new BigDecimal("21.00")), 0);
        assertEquals(savedInvoice.getBalance().compareTo(new BigDecimal("21.00")), 0);
        assertEquals(savedInvoice.getAmountPaid(), BigDecimal.ZERO);
        assertEquals(savedInvoice.getInvoiceItems().size(), 1);

        BigDecimal paymentAmount = new BigDecimal("11.00");
        UUID paymentAttemptId = UUID.randomUUID();

        invoiceDao.notifyOfPaymentAttempt(new DefaultInvoicePayment(paymentAttemptId, invoiceId, clock.getUTCNow().plusDays(12), paymentAmount, Currency.USD));

        Invoice retrievedInvoice = invoiceDao.getById(invoiceId);
        assertNotNull(retrievedInvoice);
        assertEquals(retrievedInvoice.getInvoiceItems().size(), 1);
        assertEquals(retrievedInvoice.getTotalAmount().compareTo(new BigDecimal("21.00")), 0);
        assertEquals(retrievedInvoice.getBalance().compareTo(new BigDecimal("10.00")), 0);
        assertEquals(retrievedInvoice.getAmountPaid().compareTo(new BigDecimal("11.00")), 0);
    }

    @Test
    public void testRetrievalForNonExistentInvoiceId() {
        Invoice invoice = invoiceDao.getById(UUID.randomUUID());
        assertNull(invoice);
    }

    @Test
    public void testAddPayment() {
        UUID accountId = UUID.randomUUID();
        DateTime targetDate = new DateTime(2011, 10, 6, 0, 0, 0, 0);
        Invoice invoice = new DefaultInvoice(accountId, targetDate, Currency.USD, clock);

        UUID paymentAttemptId = UUID.randomUUID();
        DateTime paymentAttemptDate = new DateTime(2011, 6, 24, 12, 14, 36, 0);
        BigDecimal paymentAmount = new BigDecimal("14.0");

        invoiceDao.create(invoice);
        invoiceDao.notifyOfPaymentAttempt(new DefaultInvoicePayment(paymentAttemptId, invoice.getId(), paymentAttemptDate, paymentAmount, Currency.USD));

        invoice = invoiceDao.getById(invoice.getId());
        assertEquals(invoice.getAmountPaid().compareTo(paymentAmount), 0);
        assertEquals(invoice.getLastPaymentAttempt().compareTo(paymentAttemptDate), 0);
        assertEquals(invoice.getNumberOfPayments(), 1);
    }

    @Test
    public void testAddPaymentAttempt() {
        UUID accountId = UUID.randomUUID();
        DateTime targetDate = new DateTime(2011, 10, 6, 0, 0, 0, 0);
        Invoice invoice = new DefaultInvoice(accountId, targetDate, Currency.USD, clock);

        DateTime paymentAttemptDate = new DateTime(2011, 6, 24, 12, 14, 36, 0);

        invoiceDao.create(invoice);
        invoiceDao.notifyOfPaymentAttempt(new DefaultInvoicePayment(invoice.getId(), paymentAttemptDate));

        invoice = invoiceDao.getById(invoice.getId());
        assertEquals(invoice.getLastPaymentAttempt().compareTo(paymentAttemptDate), 0);
    }

    @Test
    public void testGetInvoicesForPaymentWithNoResults() {
        DateTime notionalDate = new DateTime();
        DateTime targetDate = new DateTime(2011, 10, 6, 0, 0, 0, 0);

        // determine the number of existing invoices available for payment (to avoid side effects from other tests)
        List<UUID> invoices = invoiceDao.getInvoicesForPayment(notionalDate, NUMBER_OF_DAY_BETWEEN_RETRIES);
        int existingInvoiceCount = invoices.size();

        UUID accountId = UUID.randomUUID();
        Invoice invoice = new DefaultInvoice(accountId, targetDate, Currency.USD, clock);

        invoiceDao.create(invoice);
        invoices = invoiceDao.getInvoicesForPayment(notionalDate, NUMBER_OF_DAY_BETWEEN_RETRIES);
        assertEquals(invoices.size(), existingInvoiceCount);
    }

    @Test
    public void testGetInvoicesForPayment() {
        List<UUID> invoices;
        DateTime notionalDate = clock.getUTCNow();

        // create a new invoice with one item
        UUID accountId = UUID.randomUUID();
        DateTime targetDate = new DateTime(2011, 10, 6, 0, 0, 0, 0);
        Invoice invoice = new DefaultInvoice(accountId, targetDate, Currency.USD, clock);

        UUID invoiceId = invoice.getId();
        UUID subscriptionId = UUID.randomUUID();
        DateTime endDate = targetDate.plusMonths(3);
        BigDecimal rate = new BigDecimal("9.0");
        BigDecimal amount = rate.multiply(new BigDecimal("3.0"));

        RecurringInvoiceItem item = new RecurringInvoiceItem(invoiceId, subscriptionId, "test plan", "test phase", targetDate, endDate,
                amount, rate, Currency.USD, clock.getUTCNow(), clock.getUTCNow());
        invoice.addInvoiceItem(item);
        invoiceDao.create(invoice);

        // ensure that the number of invoices for payment has increased by 1
        int count;
        invoices = invoiceDao.getInvoicesForPayment(notionalDate, NUMBER_OF_DAY_BETWEEN_RETRIES);
        List<Invoice> invoicesDue = getInvoicesDueForPaymentAttempt(invoiceDao.get(), notionalDate);
        count = invoicesDue.size();
        assertEquals(invoices.size(), count);

        // attempt a payment; ensure that the number of invoices for payment has decreased by 1
        // (no retries for NUMBER_OF_DAYS_BETWEEN_RETRIES days)
        invoiceDao.notifyOfPaymentAttempt(new DefaultInvoicePayment(invoice.getId(), notionalDate));
        invoices = invoiceDao.getInvoicesForPayment(notionalDate, NUMBER_OF_DAY_BETWEEN_RETRIES);
        count = getInvoicesDueForPaymentAttempt(invoiceDao.get(), notionalDate).size();
        assertEquals(invoices.size(), count);

        // advance clock by NUMBER_OF_DAYS_BETWEEN_RETRIES days
        // ensure that number of invoices for payment has increased by 1 (retry)
        notionalDate = notionalDate.plusDays(NUMBER_OF_DAY_BETWEEN_RETRIES);
        invoices = invoiceDao.getInvoicesForPayment(notionalDate, NUMBER_OF_DAY_BETWEEN_RETRIES);
        count = getInvoicesDueForPaymentAttempt(invoiceDao.get(), notionalDate).size();
        assertEquals(invoices.size(), count);

        // post successful partial payment; ensure that number of invoices for payment has decreased by 1
        invoiceDao.notifyOfPaymentAttempt(new DefaultInvoicePayment(UUID.randomUUID(), invoice.getId(), notionalDate, new BigDecimal("22.0000"), Currency.USD));

        invoices = invoiceDao.getInvoicesForPayment(notionalDate, NUMBER_OF_DAY_BETWEEN_RETRIES);
        count = getInvoicesDueForPaymentAttempt(invoiceDao.get(), notionalDate).size();
        assertEquals(invoices.size(), count);

        // get invoice; verify amount paid is correct
        invoice = invoiceDao.getById(invoiceId);
        assertEquals(invoice.getAmountPaid().compareTo(new BigDecimal("22.0")), 0);

        // advance clock NUMBER_OF_DAYS_BETWEEN_RETRIES days
        // ensure that number of invoices for payment has increased by 1 (retry)
        notionalDate = notionalDate.plusDays(NUMBER_OF_DAY_BETWEEN_RETRIES);
        invoices = invoiceDao.getInvoicesForPayment(notionalDate, NUMBER_OF_DAY_BETWEEN_RETRIES);
        count = getInvoicesDueForPaymentAttempt(invoiceDao.get(), notionalDate).size();
        assertEquals(invoices.size(), count);

        // post completed payment; ensure that the number of invoices for payment has decreased by 1
        invoiceDao.notifyOfPaymentAttempt(new DefaultInvoicePayment(UUID.randomUUID(), invoice.getId(), notionalDate, new BigDecimal("5.0000"), Currency.USD));

        invoices = invoiceDao.getInvoicesForPayment(notionalDate, NUMBER_OF_DAY_BETWEEN_RETRIES);
        count = getInvoicesDueForPaymentAttempt(invoiceDao.get(), notionalDate).size();
        assertEquals(invoices.size(), count);

        // get invoice; verify amount paid is correct
        invoice = invoiceDao.getById(invoiceId);
        assertEquals(invoice.getAmountPaid().compareTo(new BigDecimal("27.0")), 0);

        // advance clock by NUMBER_OF_DAYS_BETWEEN_RETRIES days
        // ensure that the number of invoices for payment hasn't changed
        notionalDate = notionalDate.plusDays(NUMBER_OF_DAY_BETWEEN_RETRIES);
        invoices = invoiceDao.getInvoicesForPayment(notionalDate, NUMBER_OF_DAY_BETWEEN_RETRIES);
        count = getInvoicesDueForPaymentAttempt(invoiceDao.get(), notionalDate).size();
        assertEquals(invoices.size(), count);
    }

    private List<Invoice> getInvoicesDueForPaymentAttempt(final List<Invoice> invoices, final DateTime date) {
        List<Invoice> invoicesDue = new ArrayList<Invoice>();

        for (final Invoice invoice : invoices) {
            if (invoice.isDueForPayment(date, NUMBER_OF_DAY_BETWEEN_RETRIES)) {
                invoicesDue.add(invoice);
            }
        }

        return invoicesDue;
    }

    @Test
    public void testGetInvoicesBySubscription() {
        UUID accountId = UUID.randomUUID();

        UUID subscriptionId1 = UUID.randomUUID();
        BigDecimal rate1 = new BigDecimal("17.0");
        UUID subscriptionId2 = UUID.randomUUID();
        BigDecimal rate2 = new BigDecimal("42.0");
        UUID subscriptionId3 = UUID.randomUUID();
        BigDecimal rate3 = new BigDecimal("3.0");
        UUID subscriptionId4 = UUID.randomUUID();
        BigDecimal rate4 = new BigDecimal("12.0");

        DateTime targetDate = new DateTime(2011, 5, 23, 0, 0, 0, 0);


        // create invoice 1 (subscriptions 1-4)
        Invoice invoice1 = new DefaultInvoice(accountId, targetDate, Currency.USD, clock);
        invoiceDao.create(invoice1);

        UUID invoiceId1 = invoice1.getId();

        DateTime startDate = new DateTime(2011, 3, 1, 0, 0, 0, 0);
        DateTime endDate = startDate.plusMonths(1);

        RecurringInvoiceItem item1 = new RecurringInvoiceItem(invoiceId1, subscriptionId1, "test plan", "test A", startDate, endDate,
                rate1, rate1, Currency.USD, clock.getUTCNow(), clock.getUTCNow());
        recurringInvoiceItemDao.create(item1);

        RecurringInvoiceItem item2 = new RecurringInvoiceItem(invoiceId1, subscriptionId2, "test plan", "test B", startDate, endDate,
                rate2, rate2, Currency.USD, clock.getUTCNow(), clock.getUTCNow());
        recurringInvoiceItemDao.create(item2);

        RecurringInvoiceItem item3 = new RecurringInvoiceItem(invoiceId1, subscriptionId3, "test plan", "test C", startDate, endDate,
                rate3, rate3, Currency.USD, clock.getUTCNow(), clock.getUTCNow());
        recurringInvoiceItemDao.create(item3);

        RecurringInvoiceItem item4 = new RecurringInvoiceItem(invoiceId1, subscriptionId4, "test plan", "test D", startDate, endDate,
                rate4, rate4, Currency.USD, clock.getUTCNow(), clock.getUTCNow());
        recurringInvoiceItemDao.create(item4);

        // create invoice 2 (subscriptions 1-3)
        DefaultInvoice invoice2 = new DefaultInvoice(accountId, targetDate, Currency.USD, clock);
        invoiceDao.create(invoice2);

        UUID invoiceId2 = invoice2.getId();

        startDate = endDate;
        endDate = startDate.plusMonths(1);

        RecurringInvoiceItem item5 = new RecurringInvoiceItem(invoiceId2, subscriptionId1, "test plan", "test phase A", startDate, endDate,
                rate1, rate1, Currency.USD, clock.getUTCNow(), clock.getUTCNow());
        recurringInvoiceItemDao.create(item5);

        RecurringInvoiceItem item6 = new RecurringInvoiceItem(invoiceId2, subscriptionId2, "test plan", "test phase B", startDate, endDate,
                rate2, rate2, Currency.USD, clock.getUTCNow(), clock.getUTCNow());
        recurringInvoiceItemDao.create(item6);

        RecurringInvoiceItem item7 = new RecurringInvoiceItem(invoiceId2, subscriptionId3, "test plan", "test phase C", startDate, endDate,
                rate3, rate3, Currency.USD, clock.getUTCNow(), clock.getUTCNow());
        recurringInvoiceItemDao.create(item7);

        // check that each subscription returns the correct number of invoices
        List<Invoice> items1 = invoiceDao.getInvoicesBySubscription(subscriptionId1);
        assertEquals(items1.size(), 2);

        List<Invoice> items2 = invoiceDao.getInvoicesBySubscription(subscriptionId2);
        assertEquals(items2.size(), 2);

        List<Invoice> items3 = invoiceDao.getInvoicesBySubscription(subscriptionId3);
        assertEquals(items3.size(), 2);

        List<Invoice> items4 = invoiceDao.getInvoicesBySubscription(subscriptionId4);
        assertEquals(items4.size(), 1);
    }

    @Test
    public void testGetInvoicesForAccountAfterDate() {
        UUID accountId = UUID.randomUUID();
        DateTime targetDate1 = new DateTime(2011, 10, 6, 0, 0, 0, 0);
        Invoice invoice1 = new DefaultInvoice(accountId, targetDate1, Currency.USD, clock);
        invoiceDao.create(invoice1);

        DateTime targetDate2 = new DateTime(2011, 12, 6, 0, 0, 0, 0);
        Invoice invoice2 = new DefaultInvoice(accountId, targetDate2, Currency.USD, clock);
        invoiceDao.create(invoice2);


        List<Invoice> invoices;
        invoices = invoiceDao.getInvoicesByAccount(accountId, new DateTime(2011, 1, 1, 0, 0, 0, 0));
        assertEquals(invoices.size(), 2);

        invoices = invoiceDao.getInvoicesByAccount(accountId, new DateTime(2011, 10, 6, 0, 0, 0, 0));
        assertEquals(invoices.size(), 2);

        invoices = invoiceDao.getInvoicesByAccount(accountId, new DateTime(2011, 10, 11, 0, 0, 0, 0));
        assertEquals(invoices.size(), 1);

        invoices = invoiceDao.getInvoicesByAccount(accountId, new DateTime(2011, 12, 6, 0, 0, 0, 0));
        assertEquals(invoices.size(), 1);

        invoices = invoiceDao.getInvoicesByAccount(accountId, new DateTime(2012, 1, 1, 0, 0, 0, 0));
        assertEquals(invoices.size(), 0);
    }

    @Test
    public void testAccountBalance() {
        UUID accountId = UUID.randomUUID();
        DateTime targetDate1 = new DateTime(2011, 10, 6, 0, 0, 0, 0);
        Invoice invoice1 = new DefaultInvoice(accountId, targetDate1, Currency.USD, clock);
        invoiceDao.create(invoice1);

        DateTime startDate = new DateTime(2011, 3, 1, 0, 0, 0, 0);
        DateTime endDate = startDate.plusMonths(1);

        BigDecimal rate1 = new BigDecimal("17.0");
        BigDecimal rate2 = new BigDecimal("42.0");

        RecurringInvoiceItem item1 = new RecurringInvoiceItem(invoice1.getId(), UUID.randomUUID(), "test plan", "test phase A", startDate,
                endDate, rate1, rate1, Currency.USD, clock.getUTCNow(), clock.getUTCNow());
        recurringInvoiceItemDao.create(item1);

        RecurringInvoiceItem item2 = new RecurringInvoiceItem(invoice1.getId(), UUID.randomUUID(), "test plan", "test phase B", startDate,
                endDate, rate2, rate2, Currency.USD, clock.getUTCNow(), clock.getUTCNow());
        recurringInvoiceItemDao.create(item2);

        BigDecimal payment1 = new BigDecimal("48.0");
        InvoicePayment payment = new DefaultInvoicePayment(invoice1.getId(), new DateTime(), payment1, Currency.USD);
        invoicePaymentDao.create(payment);

        BigDecimal balance = invoiceDao.getAccountBalance(accountId);
        assertEquals(balance.compareTo(rate1.add(rate2).subtract(payment1)), 0);
    }

    @Test
    public void testAccountBalanceWithNoPayments() {
        UUID accountId = UUID.randomUUID();
        DateTime targetDate1 = new DateTime(2011, 10, 6, 0, 0, 0, 0);
        Invoice invoice1 = new DefaultInvoice(accountId, targetDate1, Currency.USD, clock);
        invoiceDao.create(invoice1);

        DateTime startDate = new DateTime(2011, 3, 1, 0, 0, 0, 0);
        DateTime endDate = startDate.plusMonths(1);

        BigDecimal rate1 = new BigDecimal("17.0");
        BigDecimal rate2 = new BigDecimal("42.0");

        RecurringInvoiceItem item1 = new RecurringInvoiceItem(invoice1.getId(), UUID.randomUUID(), "test plan", "test phase A", startDate, endDate,
                rate1, rate1, Currency.USD, clock.getUTCNow(), clock.getUTCNow());
        recurringInvoiceItemDao.create(item1);

        RecurringInvoiceItem item2 = new RecurringInvoiceItem(invoice1.getId(), UUID.randomUUID(), "test plan", "test phase B", startDate, endDate,
                rate2, rate2, Currency.USD, clock.getUTCNow(), clock.getUTCNow());
        recurringInvoiceItemDao.create(item2);

        BigDecimal balance = invoiceDao.getAccountBalance(accountId);
        assertEquals(balance.compareTo(rate1.add(rate2)), 0);
    }

    @Test
    public void testAccountBalanceWithNoInvoiceItems() {
        UUID accountId = UUID.randomUUID();
        DateTime targetDate1 = new DateTime(2011, 10, 6, 0, 0, 0, 0);
        Invoice invoice1 = new DefaultInvoice(accountId, targetDate1, Currency.USD, clock);
        invoiceDao.create(invoice1);

        BigDecimal payment1 = new BigDecimal("48.0");
        InvoicePayment payment = new DefaultInvoicePayment(invoice1.getId(), new DateTime(), payment1, Currency.USD);
        invoicePaymentDao.create(payment);

        BigDecimal balance = invoiceDao.getAccountBalance(accountId);
        assertEquals(balance.compareTo(BigDecimal.ZERO.subtract(payment1)), 0);
    }

    @Test
    public void testGetUnpaidInvoicesByAccountId() {
        UUID accountId = UUID.randomUUID();
        DateTime targetDate1 = new DateTime(2011, 10, 6, 0, 0, 0, 0);
        Invoice invoice1 = new DefaultInvoice(accountId, targetDate1, Currency.USD, clock);
        invoiceDao.create(invoice1);

        DateTime startDate = new DateTime(2011, 3, 1, 0, 0, 0, 0);
        DateTime endDate = startDate.plusMonths(1);

        BigDecimal rate1 = new BigDecimal("17.0");
        BigDecimal rate2 = new BigDecimal("42.0");

        RecurringInvoiceItem item1 = new RecurringInvoiceItem(invoice1.getId(), UUID.randomUUID(), "test plan", "test phase A", startDate, endDate,
                rate1, rate1, Currency.USD, clock.getUTCNow(), clock.getUTCNow());
        recurringInvoiceItemDao.create(item1);

        RecurringInvoiceItem item2 = new RecurringInvoiceItem(invoice1.getId(), UUID.randomUUID(), "test plan", "test phase B", startDate, endDate,
                rate2, rate2, Currency.USD, clock.getUTCNow(), clock.getUTCNow());
        recurringInvoiceItemDao.create(item2);

        DateTime upToDate;
        Collection<Invoice> invoices;

        upToDate = new DateTime(2011, 1, 1, 0, 0, 0, 0);
        invoices = invoiceDao.getUnpaidInvoicesByAccountId(accountId, upToDate);
        assertEquals(invoices.size(), 0);

        upToDate = new DateTime(2012, 1, 1, 0, 0, 0, 0);
        invoices = invoiceDao.getUnpaidInvoicesByAccountId(accountId, upToDate);
        assertEquals(invoices.size(), 1);

        DateTime targetDate2 = new DateTime(2011, 7, 1, 0, 0, 0, 0);
        Invoice invoice2 = new DefaultInvoice(accountId, targetDate2, Currency.USD, clock);
        invoiceDao.create(invoice2);

        DateTime startDate2 = new DateTime(2011, 6, 1, 0, 0, 0, 0);
        DateTime endDate2 = startDate2.plusMonths(3);

        BigDecimal rate3 = new BigDecimal("21.0");

        RecurringInvoiceItem item3 = new RecurringInvoiceItem(invoice2.getId(), UUID.randomUUID(), "test plan", "test phase C", startDate2, endDate2,
                rate3, rate3, Currency.USD, clock.getUTCNow(), clock.getUTCNow());
        recurringInvoiceItemDao.create(item3);

        upToDate = new DateTime(2011, 1, 1, 0, 0, 0, 0);
        invoices = invoiceDao.getUnpaidInvoicesByAccountId(accountId, upToDate);
        assertEquals(invoices.size(), 0);

        upToDate = new DateTime(2012, 1, 1, 0, 0, 0, 0);
        invoices = invoiceDao.getUnpaidInvoicesByAccountId(accountId, upToDate);
        assertEquals(invoices.size(), 2);
    }

    /*
     *
     * this test verifies that immediate changes give the correct results
     *
     */
    @Test
    public void testInvoiceGenerationForImmediateChanges() throws InvoiceApiException {

        InvoiceGenerator generator = new DefaultInvoiceGenerator(clock);

        UUID accountId = UUID.randomUUID();
        InvoiceItemList invoiceItemList = new InvoiceItemList();
        DateTime targetDate = new DateTime(2011, 2, 16, 0, 0, 0, 0);

        // generate first invoice
        DefaultPrice price1 = new DefaultPrice(TEN, Currency.USD);
        MockInternationalPrice recurringPrice = new MockInternationalPrice(price1);
        MockPlanPhase phase1 = new MockPlanPhase(recurringPrice, null, BillingPeriod.MONTHLY, PhaseType.TRIAL);
        MockPlan plan1 = new MockPlan(phase1);

        Subscription subscription = BrainDeadProxyFactory.createBrainDeadProxyFor(Subscription.class);
        ((ZombieControl) subscription).addResult("getId", UUID.randomUUID());

        DateTime effectiveDate1 = new DateTime(2011, 2, 1, 0, 0, 0, 0);
        BillingEvent event1 = new DefaultBillingEvent(subscription, effectiveDate1, plan1, phase1, null,
                recurringPrice, BillingPeriod.MONTHLY, 1, BillingModeType.IN_ADVANCE,
                "testEvent1", 1L,  SubscriptionTransitionType.CREATE);

        BillingEventSet events = new BillingEventSet();
        events.add(event1);

        Invoice invoice1 = generator.generateInvoice(accountId, events, invoiceItemList, targetDate, Currency.USD);
        assertEquals(invoice1.getBalance(), TEN);
        invoiceItemList.addAll(invoice1.getInvoiceItems());

        // generate second invoice
        DefaultPrice price2 = new DefaultPrice(TWENTY, Currency.USD);
        MockInternationalPrice recurringPrice2 = new MockInternationalPrice(price2);
        MockPlanPhase phase2 = new MockPlanPhase(recurringPrice, null, BillingPeriod.MONTHLY, PhaseType.TRIAL);
        MockPlan plan2 = new MockPlan(phase2);

        DateTime effectiveDate2 = new DateTime(2011, 2, 15, 0, 0, 0, 0);
        BillingEvent event2 = new DefaultBillingEvent(subscription, effectiveDate2, plan2, phase2, null,
                recurringPrice2, BillingPeriod.MONTHLY, 1, BillingModeType.IN_ADVANCE,
                "testEvent2", 1L, SubscriptionTransitionType.CREATE);
        events.add(event2);

        // second invoice should be for one half (14/28 days) the difference between the rate plans
        // this is a temporary state, since it actually contains an adjusting item that properly belong to invoice 1
        Invoice invoice2 = generator.generateInvoice(accountId, events, invoiceItemList, targetDate, Currency.USD);
        assertEquals(invoice2.getBalance(), FIVE);
        invoiceItemList.addAll(invoice2.getInvoiceItems());

        invoiceDao.create(invoice1);
        invoiceDao.create(invoice2);

        Invoice savedInvoice1 = invoiceDao.getById(invoice1.getId());
        assertEquals(savedInvoice1.getTotalAmount(), ZERO);

        Invoice savedInvoice2 = invoiceDao.getById(invoice2.getId());
        assertEquals(savedInvoice2.getTotalAmount(), FIFTEEN);
    }

    @Test
    public void testInvoiceForFreeTrial() throws InvoiceApiException {
        DefaultPrice price = new DefaultPrice(BigDecimal.ZERO, Currency.USD);
        MockInternationalPrice recurringPrice = new MockInternationalPrice(price);
        MockPlanPhase phase = new MockPlanPhase(recurringPrice, null);
        MockPlan plan = new MockPlan(phase);

        Subscription subscription = BrainDeadProxyFactory.createBrainDeadProxyFor(Subscription.class);
        ((ZombieControl) subscription).addResult("getId", UUID.randomUUID());
        DateTime effectiveDate = buildDateTime(2011, 1, 1);

        BillingEvent event = new DefaultBillingEvent(subscription, effectiveDate, plan, phase, null,
                recurringPrice, BillingPeriod.MONTHLY, 15, BillingModeType.IN_ADVANCE,
                "testEvent", 1L, SubscriptionTransitionType.CREATE);
        BillingEventSet events = new BillingEventSet();
        events.add(event);

        DateTime targetDate = buildDateTime(2011, 1, 15);
        InvoiceGenerator generator = new DefaultInvoiceGenerator(clock);
        Invoice invoice = generator.generateInvoice(UUID.randomUUID(), events, null, targetDate, Currency.USD);

        // expect one pro-ration item and one full-period item
        assertEquals(invoice.getNumberOfItems(), 2);
        assertEquals(invoice.getTotalAmount().compareTo(ZERO), 0);
    }

    @Test
    public void testInvoiceForFreeTrialWithRecurringDiscount() throws InvoiceApiException {
        DefaultPrice zeroPrice = new DefaultPrice(BigDecimal.ZERO, Currency.USD);
        MockInternationalPrice fixedPrice = new MockInternationalPrice(zeroPrice);
        MockPlanPhase phase1 = new MockPlanPhase(null, fixedPrice);

        BigDecimal cheapAmount = new BigDecimal("24.95");
        DefaultPrice cheapPrice = new DefaultPrice(cheapAmount, Currency.USD);
        MockInternationalPrice recurringPrice = new MockInternationalPrice(cheapPrice);
        MockPlanPhase phase2 = new MockPlanPhase(recurringPrice, null);

        MockPlan plan = new MockPlan();

        Subscription subscription = BrainDeadProxyFactory.createBrainDeadProxyFor(Subscription.class);
        ((ZombieControl) subscription).addResult("getId", UUID.randomUUID());
        DateTime effectiveDate1 = buildDateTime(2011, 1, 1);

        BillingEvent event1 = new DefaultBillingEvent(subscription, effectiveDate1, plan, phase1, fixedPrice,
                null, BillingPeriod.MONTHLY, 1, BillingModeType.IN_ADVANCE,
                "testEvent1", 1L, SubscriptionTransitionType.CREATE);
        BillingEventSet events = new BillingEventSet();
        events.add(event1);

        InvoiceGenerator generator = new DefaultInvoiceGenerator(clock);
        Invoice invoice1 = generator.generateInvoice(UUID.randomUUID(), events, null, effectiveDate1, Currency.USD);
        assertNotNull(invoice1);
        assertEquals(invoice1.getNumberOfItems(), 1);
        assertEquals(invoice1.getTotalAmount().compareTo(ZERO), 0);

        List<InvoiceItem> existingItems = invoice1.getInvoiceItems();

        DateTime effectiveDate2 = effectiveDate1.plusDays(30);
        BillingEvent event2 = new DefaultBillingEvent(subscription, effectiveDate2, plan, phase2, null,
                recurringPrice, BillingPeriod.MONTHLY, 31, BillingModeType.IN_ADVANCE,
                "testEvent2", 1L, SubscriptionTransitionType.CHANGE);
        events.add(event2);

        Invoice invoice2 = generator.generateInvoice(UUID.randomUUID(), events, existingItems, effectiveDate2, Currency.USD);
        assertNotNull(invoice2);
        assertEquals(invoice2.getNumberOfItems(), 1);
        assertEquals(invoice2.getTotalAmount().compareTo(cheapAmount), 0);

        existingItems.addAll(invoice2.getInvoiceItems());

        DateTime effectiveDate3 = effectiveDate2.plusMonths(1);
        Invoice invoice3 = generator.generateInvoice(UUID.randomUUID(), events, existingItems, effectiveDate3, Currency.USD);
        assertNotNull(invoice3);
        assertEquals(invoice3.getNumberOfItems(), 1);
        assertEquals(invoice3.getTotalAmount().compareTo(cheapAmount), 0);
    }

    @Test
    public void testInvoiceForEmptyEventSet() throws InvoiceApiException {
        InvoiceGenerator generator = new DefaultInvoiceGenerator(clock);
        BillingEventSet events = new BillingEventSet();
        Invoice invoice = generator.generateInvoice(UUID.randomUUID(), events, null, new DateTime(), Currency.USD);
        assertNull(invoice);
    }

    @Test
    public void testMixedModeInvoicePersistence() throws InvoiceApiException {
        DefaultPrice zeroPrice = new DefaultPrice(BigDecimal.ZERO, Currency.USD);
        MockInternationalPrice fixedPrice = new MockInternationalPrice(zeroPrice);
        MockPlanPhase phase1 = new MockPlanPhase(null, fixedPrice);

        BigDecimal cheapAmount = new BigDecimal("24.95");
        DefaultPrice cheapPrice = new DefaultPrice(cheapAmount, Currency.USD);
        MockInternationalPrice recurringPrice = new MockInternationalPrice(cheapPrice);
        MockPlanPhase phase2 = new MockPlanPhase(recurringPrice, null);

        MockPlan plan = new MockPlan();

        Subscription subscription = BrainDeadProxyFactory.createBrainDeadProxyFor(Subscription.class);
        ((ZombieControl) subscription).addResult("getId", UUID.randomUUID());
        DateTime effectiveDate1 = buildDateTime(2011, 1, 1);

        BillingEvent event1 = new DefaultBillingEvent(subscription, effectiveDate1, plan, phase1, fixedPrice,
                null, BillingPeriod.MONTHLY, 1, BillingModeType.IN_ADVANCE,
                "testEvent1", 1L, SubscriptionTransitionType.CREATE);
        BillingEventSet events = new BillingEventSet();
        events.add(event1);

        DateTime effectiveDate2 = effectiveDate1.plusDays(30);
        BillingEvent event2 = new DefaultBillingEvent(subscription, effectiveDate2, plan, phase2, null,
                recurringPrice, BillingPeriod.MONTHLY, 31, BillingModeType.IN_ADVANCE,
                "testEvent2", 1L, SubscriptionTransitionType.CHANGE);
        events.add(event2);

        InvoiceGenerator generator = new DefaultInvoiceGenerator(clock);
        Invoice invoice = generator.generateInvoice(UUID.randomUUID(), events, null, effectiveDate2, Currency.USD);
        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 2);
        assertEquals(invoice.getTotalAmount().compareTo(cheapAmount), 0);

        invoiceDao.create(invoice);
        Invoice savedInvoice = invoiceDao.getById(invoice.getId());

        assertNotNull(savedInvoice);
        assertEquals(savedInvoice.getNumberOfItems(), 2);
        assertEquals(savedInvoice.getTotalAmount().compareTo(cheapAmount), 0);
    }

//    @Test
//    public void testCancellationWithMultipleBillingPeriodsFollowing() throws InvoiceApiException {
//        UUID accountId = UUID.randomUUID();
//
//        BigDecimal fixedValue = FIVE;
//        DefaultPrice fixedAmount = new DefaultPrice(fixedValue, Currency.USD);
//        MockInternationalPrice fixedPrice = new MockInternationalPrice(fixedAmount);
//        MockPlanPhase plan1phase1 = new MockPlanPhase(null, fixedPrice);
//
//        BigDecimal trialValue = new BigDecimal("9.95");
//        DefaultPrice trialAmount = new DefaultPrice(trialValue, Currency.USD);
//        MockInternationalPrice trialPrice = new MockInternationalPrice(trialAmount);
//        MockPlanPhase plan2phase1 = new MockPlanPhase(trialPrice, null);
//
//        BigDecimal discountValue = new BigDecimal("24.95");
//        DefaultPrice discountAmount = new DefaultPrice(discountValue, Currency.USD);
//        MockInternationalPrice discountPrice = new MockInternationalPrice(discountAmount);
//        MockPlanPhase plan2phase2 = new MockPlanPhase(discountPrice, null);
//
//        MockPlan plan1 = new MockPlan();
//        MockPlan plan2 = new MockPlan();
//        Subscription subscription = new MockSubscription();
//        DateTime effectiveDate1 = buildDateTime(2011, 1, 1);
//
//        BillingEvent creationEvent = new DefaultBillingEvent(subscription, effectiveDate1, plan1, plan1phase1, fixedPrice,
//                                                     null, BillingPeriod.MONTHLY, 1, BillingModeType.IN_ADVANCE,
//                                                     "trial", SubscriptionTransitionType.CREATE);
//        BillingEventSet events = new BillingEventSet();
//        events.add(creationEvent);
//
//        InvoiceGenerator generator = new DefaultInvoiceGenerator();
//        InvoiceItemList existingItems;
//
//        existingItems = new InvoiceItemList(invoiceDao.getInvoiceItemsByAccount(accountId));
//        Invoice invoice1 = generator.generateInvoice(accountId, events, existingItems, effectiveDate1, Currency.USD);
//
//        assertNotNull(invoice1);
//        assertEquals(invoice1.getNumberOfItems(), 1);
//        assertEquals(invoice1.getTotalAmount().compareTo(fixedValue), 0);
//        invoiceDao.create(invoice1);
//
//        DateTime effectiveDate2 = effectiveDate1.plusSeconds(1);
//        BillingEvent changeEvent = new DefaultBillingEvent(subscription, effectiveDate2, plan2, plan2phase1, null,
//                                                     trialPrice, BillingPeriod.MONTHLY, 31, BillingModeType.IN_ADVANCE,
//                                                     "discount", SubscriptionTransitionType.CHANGE);
//        events.add(changeEvent);
//
//        existingItems = new InvoiceItemList(invoiceDao.getInvoiceItemsByAccount(accountId));
//        Invoice invoice2 = generator.generateInvoice(accountId, events, existingItems, effectiveDate2, Currency.USD);
//        assertNotNull(invoice2);
//        assertEquals(invoice2.getNumberOfItems(), 2);
//        assertEquals(invoice2.getTotalAmount().compareTo(trialValue), 0);
//        invoiceDao.create(invoice2);
//
//        DateTime effectiveDate3 = effectiveDate2.plusMonths(1);
//        BillingEvent phaseEvent = new DefaultBillingEvent(subscription, effectiveDate3, plan2, plan2phase2, null,
//                                                     discountPrice, BillingPeriod.MONTHLY, 31, BillingModeType.IN_ADVANCE,
//                                                     "discount", SubscriptionTransitionType.PHASE);
//        events.add(phaseEvent);
//
//        existingItems = new InvoiceItemList(invoiceDao.getInvoiceItemsByAccount(accountId));
//        Invoice invoice3 = generator.generateInvoice(accountId, events, existingItems, effectiveDate3, Currency.USD);
//        assertNotNull(invoice3);
//        assertEquals(invoice3.getNumberOfItems(), 1);
//        assertEquals(invoice3.getTotalAmount().compareTo(discountValue), 0);
//        invoiceDao.create(invoice3);
//
//        DateTime effectiveDate4 = effectiveDate3.plusMonths(1);
//        existingItems = new InvoiceItemList(invoiceDao.getInvoiceItemsByAccount(accountId));
//        Invoice invoice4 = generator.generateInvoice(accountId, events, existingItems, effectiveDate4, Currency.USD);
//        assertNotNull(invoice4);
//        assertEquals(invoice4.getNumberOfItems(), 1);
//        assertEquals(invoice4.getTotalAmount().compareTo(discountValue), 0);
//        invoiceDao.create(invoice4);
//    }
}
