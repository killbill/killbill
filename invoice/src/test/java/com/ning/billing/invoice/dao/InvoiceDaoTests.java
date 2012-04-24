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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.testng.annotations.Test;

import com.ning.billing.catalog.DefaultPrice;
import com.ning.billing.catalog.MockInternationalPrice;
import com.ning.billing.catalog.MockPlan;
import com.ning.billing.catalog.MockPlanPhase;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.entitlement.api.billing.BillingEvent;
import com.ning.billing.entitlement.api.billing.BillingModeType;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionEventTransition.SubscriptionTransitionType;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.model.BillingEventSet;
import com.ning.billing.invoice.model.DefaultInvoice;
import com.ning.billing.invoice.model.DefaultInvoicePayment;
import com.ning.billing.invoice.model.RecurringInvoiceItem;
import com.ning.billing.junction.plumbing.billing.DefaultBillingEvent;
import com.ning.billing.mock.BrainDeadProxyFactory;
import com.ning.billing.mock.BrainDeadProxyFactory.ZombieControl;
import com.ning.billing.util.tag.ControlTagType;

@Test(groups = {"slow", "invoicing", "invoicing-invoiceDao"})
public class InvoiceDaoTests extends InvoiceDaoTestBase {
    @Test
    public void testCreationAndRetrievalByAccount() {
        UUID accountId = UUID.randomUUID();
        Invoice invoice = new DefaultInvoice(accountId, clock.getUTCNow(), clock.getUTCNow(), Currency.USD);
        DateTime invoiceDate = invoice.getInvoiceDate();

        invoiceDao.create(invoice, context);

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
        Invoice invoice = new DefaultInvoice(accountId, clock.getUTCNow(), clock.getUTCNow(), Currency.USD);
        UUID invoiceId = invoice.getId();
        UUID subscriptionId = UUID.randomUUID();
        UUID bundleId = UUID.randomUUID();
        DateTime startDate = new DateTime(2010, 1, 1, 0, 0, 0, 0);
        DateTime endDate = new DateTime(2010, 4, 1, 0, 0, 0, 0);
        InvoiceItem invoiceItem = new RecurringInvoiceItem(invoiceId, accountId, bundleId,subscriptionId, "test plan", "test phase", startDate, endDate,
                new BigDecimal("21.00"), new BigDecimal("7.00"), Currency.USD);

        invoice.addInvoiceItem(invoiceItem);
        invoiceDao.create(invoice, context);

        Invoice savedInvoice = invoiceDao.getById(invoiceId);
        assertNotNull(savedInvoice);
        assertEquals(savedInvoice.getTotalAmount().compareTo(new BigDecimal("21.00")), 0);
        assertEquals(savedInvoice.getBalance().compareTo(new BigDecimal("21.00")), 0);
        assertEquals(savedInvoice.getAmountPaid(), BigDecimal.ZERO);
        assertEquals(savedInvoice.getInvoiceItems().size(), 1);

        BigDecimal paymentAmount = new BigDecimal("11.00");
        UUID paymentAttemptId = UUID.randomUUID();

        invoiceDao.notifyOfPaymentAttempt(new DefaultInvoicePayment(paymentAttemptId, invoiceId, clock.getUTCNow().plusDays(12), paymentAmount, Currency.USD), context);

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
        Invoice invoice = new DefaultInvoice(accountId, clock.getUTCNow(), targetDate, Currency.USD);

        UUID paymentAttemptId = UUID.randomUUID();
        DateTime paymentAttemptDate = new DateTime(2011, 6, 24, 12, 14, 36, 0);
        BigDecimal paymentAmount = new BigDecimal("14.0");

        invoiceDao.create(invoice, context);
        invoiceDao.notifyOfPaymentAttempt(new DefaultInvoicePayment(paymentAttemptId, invoice.getId(), paymentAttemptDate, paymentAmount, Currency.USD), context);

        invoice = invoiceDao.getById(invoice.getId());
        assertEquals(invoice.getAmountPaid().compareTo(paymentAmount), 0);
        assertEquals(invoice.getLastPaymentAttempt().compareTo(paymentAttemptDate), 0);
        assertEquals(invoice.getNumberOfPayments(), 1);
    }

    @Test
    public void testAddPaymentAttempt() {
        UUID accountId = UUID.randomUUID();
        DateTime targetDate = new DateTime(2011, 10, 6, 0, 0, 0, 0);
        Invoice invoice = new DefaultInvoice(accountId, clock.getUTCNow(), targetDate, Currency.USD);

        DateTime paymentAttemptDate = new DateTime(2011, 6, 24, 12, 14, 36, 0);

        invoiceDao.create(invoice, context);
        invoiceDao.notifyOfPaymentAttempt(new DefaultInvoicePayment(UUID.randomUUID(), invoice.getId(), paymentAttemptDate), context);

        invoice = invoiceDao.getById(invoice.getId());
        assertEquals(invoice.getLastPaymentAttempt().compareTo(paymentAttemptDate), 0);
    }

    @Test
    public void testGetInvoicesBySubscription() {
        UUID accountId = UUID.randomUUID();
        UUID bundleId = UUID.randomUUID();

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
        Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCNow(), targetDate, Currency.USD);
        invoiceDao.create(invoice1, context);

        UUID invoiceId1 = invoice1.getId();

        DateTime startDate = new DateTime(2011, 3, 1, 0, 0, 0, 0);
        DateTime endDate = startDate.plusMonths(1);


        RecurringInvoiceItem item1 = new RecurringInvoiceItem(invoiceId1, accountId, bundleId,subscriptionId1, "test plan", "test A", startDate, endDate,
                rate1, rate1, Currency.USD);
        recurringInvoiceItemDao.create(item1, context);

        RecurringInvoiceItem item2 = new RecurringInvoiceItem(invoiceId1, accountId, bundleId,subscriptionId2, "test plan", "test B", startDate, endDate,
                rate2, rate2, Currency.USD);
        recurringInvoiceItemDao.create(item2, context);

        RecurringInvoiceItem item3 = new RecurringInvoiceItem(invoiceId1, accountId, bundleId,subscriptionId3, "test plan", "test C", startDate, endDate,
                rate3, rate3, Currency.USD);
        recurringInvoiceItemDao.create(item3, context);

        RecurringInvoiceItem item4 = new RecurringInvoiceItem(invoiceId1, accountId, bundleId,subscriptionId4, "test plan", "test D", startDate, endDate,
                rate4, rate4, Currency.USD);
        recurringInvoiceItemDao.create(item4, context);

        // create invoice 2 (subscriptions 1-3)
        DefaultInvoice invoice2 = new DefaultInvoice(accountId, clock.getUTCNow(), targetDate, Currency.USD);
        invoiceDao.create(invoice2, context);

        UUID invoiceId2 = invoice2.getId();

        startDate = endDate;
        endDate = startDate.plusMonths(1);


        RecurringInvoiceItem item5 = new RecurringInvoiceItem(invoiceId2, accountId, bundleId,subscriptionId1, "test plan", "test phase A", startDate, endDate,
                rate1, rate1, Currency.USD);
        recurringInvoiceItemDao.create(item5, context);

        RecurringInvoiceItem item6 = new RecurringInvoiceItem(invoiceId2, accountId, bundleId,subscriptionId2, "test plan", "test phase B", startDate, endDate,
                rate2, rate2, Currency.USD);
        recurringInvoiceItemDao.create(item6, context);

        RecurringInvoiceItem item7 = new RecurringInvoiceItem(invoiceId2, accountId, bundleId,subscriptionId3, "test plan", "test phase C", startDate, endDate,
                rate3, rate3, Currency.USD);
        recurringInvoiceItemDao.create(item7, context);

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
        Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCNow(), targetDate1, Currency.USD);
        invoiceDao.create(invoice1, context);

        DateTime targetDate2 = new DateTime(2011, 12, 6, 0, 0, 0, 0);
        Invoice invoice2 = new DefaultInvoice(accountId, clock.getUTCNow(), targetDate2, Currency.USD);
        invoiceDao.create(invoice2, context);


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
        UUID bundleId = UUID.randomUUID();
        DateTime targetDate1 = new DateTime(2011, 10, 6, 0, 0, 0, 0);
        Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCNow(), targetDate1, Currency.USD);
        invoiceDao.create(invoice1, context);

        DateTime startDate = new DateTime(2011, 3, 1, 0, 0, 0, 0);
        DateTime endDate = startDate.plusMonths(1);

        BigDecimal rate1 = new BigDecimal("17.0");
        BigDecimal rate2 = new BigDecimal("42.0");

        RecurringInvoiceItem item1 = new RecurringInvoiceItem(invoice1.getId(), accountId, bundleId,UUID.randomUUID(), "test plan", "test phase A", startDate,
                endDate, rate1, rate1, Currency.USD);
        recurringInvoiceItemDao.create(item1, context);

        RecurringInvoiceItem item2 = new RecurringInvoiceItem(invoice1.getId(), accountId, bundleId,UUID.randomUUID(), "test plan", "test phase B", startDate,
                endDate, rate2, rate2, Currency.USD);
        recurringInvoiceItemDao.create(item2, context);

        BigDecimal payment1 = new BigDecimal("48.0");
        InvoicePayment payment = new DefaultInvoicePayment(UUID.randomUUID(), invoice1.getId(), new DateTime(), payment1, Currency.USD);
        invoicePaymentDao.create(payment, context);

        BigDecimal balance = invoiceDao.getAccountBalance(accountId);
        assertEquals(balance.compareTo(rate1.add(rate2).subtract(payment1)), 0);
    }

    @Test
    public void testAccountBalanceWithNoPayments() {
        UUID accountId = UUID.randomUUID();
        UUID bundleId = UUID.randomUUID();
        DateTime targetDate1 = new DateTime(2011, 10, 6, 0, 0, 0, 0);
        Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCNow(), targetDate1, Currency.USD);
        invoiceDao.create(invoice1, context);

        DateTime startDate = new DateTime(2011, 3, 1, 0, 0, 0, 0);
        DateTime endDate = startDate.plusMonths(1);

        BigDecimal rate1 = new BigDecimal("17.0");
        BigDecimal rate2 = new BigDecimal("42.0");

        RecurringInvoiceItem item1 = new RecurringInvoiceItem(invoice1.getId(), accountId, bundleId, UUID.randomUUID(), "test plan", "test phase A", startDate, endDate,
                rate1, rate1, Currency.USD);
        recurringInvoiceItemDao.create(item1, context);

        RecurringInvoiceItem item2 = new RecurringInvoiceItem(invoice1.getId(), accountId, bundleId, UUID.randomUUID(), "test plan", "test phase B", startDate, endDate,
                rate2, rate2, Currency.USD);
        recurringInvoiceItemDao.create(item2, context);

        BigDecimal balance = invoiceDao.getAccountBalance(accountId);
        assertEquals(balance.compareTo(rate1.add(rate2)), 0);
    }

    @Test
    public void testAccountBalanceWithNoInvoiceItems() {
        UUID accountId = UUID.randomUUID();
        DateTime targetDate1 = new DateTime(2011, 10, 6, 0, 0, 0, 0);
        Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCNow(), targetDate1, Currency.USD);
        invoiceDao.create(invoice1, context);

        BigDecimal payment1 = new BigDecimal("48.0");
        InvoicePayment payment = new DefaultInvoicePayment(UUID.randomUUID(), invoice1.getId(), new DateTime(), payment1, Currency.USD);
        invoicePaymentDao.create(payment, context);

        BigDecimal balance = invoiceDao.getAccountBalance(accountId);
        assertEquals(balance.compareTo(BigDecimal.ZERO.subtract(payment1)), 0);
    }

    @Test
    public void testGetUnpaidInvoicesByAccountId() {
        UUID accountId = UUID.randomUUID();
        UUID bundleId = UUID.randomUUID();
        DateTime targetDate1 = new DateTime(2011, 10, 6, 0, 0, 0, 0);
        Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCNow(), targetDate1, Currency.USD);
        invoiceDao.create(invoice1, context);

        DateTime startDate = new DateTime(2011, 3, 1, 0, 0, 0, 0);
        DateTime endDate = startDate.plusMonths(1);

        BigDecimal rate1 = new BigDecimal("17.0");
        BigDecimal rate2 = new BigDecimal("42.0");


        RecurringInvoiceItem item1 = new RecurringInvoiceItem(invoice1.getId(), accountId, bundleId, UUID.randomUUID(), "test plan", "test phase A", startDate, endDate,
                rate1, rate1, Currency.USD);
        recurringInvoiceItemDao.create(item1, context);

        RecurringInvoiceItem item2 = new RecurringInvoiceItem(invoice1.getId(), accountId, bundleId, UUID.randomUUID(), "test plan", "test phase B", startDate, endDate,
                rate2, rate2, Currency.USD);
        recurringInvoiceItemDao.create(item2, context);

        DateTime upToDate;
        Collection<Invoice> invoices;

        upToDate = new DateTime(2011, 1, 1, 0, 0, 0, 0);
        invoices = invoiceDao.getUnpaidInvoicesByAccountId(accountId, upToDate);
        assertEquals(invoices.size(), 0);

        upToDate = new DateTime(2012, 1, 1, 0, 0, 0, 0);
        invoices = invoiceDao.getUnpaidInvoicesByAccountId(accountId, upToDate);
        assertEquals(invoices.size(), 1);

        DateTime targetDate2 = new DateTime(2011, 7, 1, 0, 0, 0, 0);
        Invoice invoice2 = new DefaultInvoice(accountId, clock.getUTCNow(), targetDate2, Currency.USD);
        invoiceDao.create(invoice2, context);

        DateTime startDate2 = new DateTime(2011, 6, 1, 0, 0, 0, 0);
        DateTime endDate2 = startDate2.plusMonths(3);

        BigDecimal rate3 = new BigDecimal("21.0");

        RecurringInvoiceItem item3 = new RecurringInvoiceItem(invoice2.getId(), accountId, bundleId, UUID.randomUUID(), "test plan", "test phase C", startDate2, endDate2,
                rate3, rate3, Currency.USD);
        recurringInvoiceItemDao.create(item3, context);

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
    public void testInvoiceGenerationForImmediateChanges() throws InvoiceApiException, CatalogApiException {
        UUID accountId = UUID.randomUUID();
        List<Invoice> invoiceList = new ArrayList<Invoice>();
        DateTime targetDate = new DateTime(2011, 2, 16, 0, 0, 0, 0);
        Currency currency = Currency.USD;

        // generate first invoice
        DefaultPrice price1 = new DefaultPrice(TEN, Currency.USD);
        MockInternationalPrice recurringPrice = new MockInternationalPrice(price1);
        MockPlanPhase phase1 = new MockPlanPhase(recurringPrice, null, BillingPeriod.MONTHLY, PhaseType.TRIAL);
        MockPlan plan1 = new MockPlan(phase1);

        Subscription subscription = getZombieSubscription();

        DateTime effectiveDate1 = new DateTime(2011, 2, 1, 0, 0, 0, 0);
        BillingEvent event1 = new DefaultBillingEvent(null, subscription, effectiveDate1, plan1, phase1, null,
                recurringPrice.getPrice(currency), currency, BillingPeriod.MONTHLY, 1, BillingModeType.IN_ADVANCE,
                "testEvent1", 1L, SubscriptionTransitionType.CREATE);

        BillingEventSet events = new BillingEventSet();
        events.add(event1);

        Invoice invoice1 = generator.generateInvoice(accountId, events, invoiceList, targetDate, Currency.USD);
        assertEquals(invoice1.getBalance(), TEN);
        invoiceList.add(invoice1);

        // generate second invoice
        DefaultPrice price2 = new DefaultPrice(TWENTY, Currency.USD);
        MockInternationalPrice recurringPrice2 = new MockInternationalPrice(price2);
        MockPlanPhase phase2 = new MockPlanPhase(recurringPrice, null, BillingPeriod.MONTHLY, PhaseType.TRIAL);
        MockPlan plan2 = new MockPlan(phase2);

        DateTime effectiveDate2 = new DateTime(2011, 2, 15, 0, 0, 0, 0);
        BillingEvent event2 = new DefaultBillingEvent(null, subscription, effectiveDate2, plan2, phase2, null,
                recurringPrice2.getPrice(currency), currency, BillingPeriod.MONTHLY, 1, BillingModeType.IN_ADVANCE,
                "testEvent2", 2L, SubscriptionTransitionType.CREATE);
        events.add(event2);

        // second invoice should be for one half (14/28 days) the difference between the rate plans
        // this is a temporary state, since it actually contains an adjusting item that properly belong to invoice 1
        Invoice invoice2 = generator.generateInvoice(accountId, events, invoiceList, targetDate, Currency.USD);
        assertEquals(invoice2.getBalance(), FIVE);
        invoiceList.add(invoice2);

        invoiceDao.create(invoice1, context);
        invoiceDao.create(invoice2, context);

        Invoice savedInvoice1 = invoiceDao.getById(invoice1.getId());
        assertEquals(savedInvoice1.getTotalAmount(), ZERO);

        Invoice savedInvoice2 = invoiceDao.getById(invoice2.getId());
        assertEquals(savedInvoice2.getTotalAmount(), FIFTEEN);
    }

    @Test
    public void testInvoiceForFreeTrial() throws InvoiceApiException, CatalogApiException {
        Currency currency = Currency.USD;
        DefaultPrice price = new DefaultPrice(BigDecimal.ZERO, Currency.USD);
        MockInternationalPrice recurringPrice = new MockInternationalPrice(price);
        MockPlanPhase phase = new MockPlanPhase(recurringPrice, null);
        MockPlan plan = new MockPlan(phase);

        Subscription subscription = getZombieSubscription();
        DateTime effectiveDate = buildDateTime(2011, 1, 1);

        BillingEvent event = new DefaultBillingEvent(null, subscription, effectiveDate, plan, phase, null,
                recurringPrice.getPrice(currency), currency, BillingPeriod.MONTHLY, 15, BillingModeType.IN_ADVANCE,
                "testEvent", 1L, SubscriptionTransitionType.CREATE);
        BillingEventSet events = new BillingEventSet();
        events.add(event);

        DateTime targetDate = buildDateTime(2011, 1, 15);
        Invoice invoice = generator.generateInvoice(UUID.randomUUID(), events, null, targetDate, Currency.USD);

        // expect one pro-ration item and one full-period item
        assertEquals(invoice.getNumberOfItems(), 2);
        assertEquals(invoice.getTotalAmount().compareTo(ZERO), 0);
    }

    private Subscription getZombieSubscription() {
        Subscription subscription = BrainDeadProxyFactory.createBrainDeadProxyFor(Subscription.class);
        ((ZombieControl) subscription).addResult("getId", UUID.randomUUID());
        ((ZombieControl) subscription).addResult("getBundleId", UUID.randomUUID());
        return subscription;
    }

    @Test
    public void testInvoiceForFreeTrialWithRecurringDiscount() throws InvoiceApiException, CatalogApiException {
        Currency currency = Currency.USD;

        DefaultPrice zeroPrice = new DefaultPrice(BigDecimal.ZERO, Currency.USD);
        MockInternationalPrice fixedPrice = new MockInternationalPrice(zeroPrice);
        MockPlanPhase phase1 = new MockPlanPhase(null, fixedPrice);

        BigDecimal cheapAmount = new BigDecimal("24.95");
        DefaultPrice cheapPrice = new DefaultPrice(cheapAmount, Currency.USD);
        MockInternationalPrice recurringPrice = new MockInternationalPrice(cheapPrice);
        MockPlanPhase phase2 = new MockPlanPhase(recurringPrice, null);

        MockPlan plan = new MockPlan();

        Subscription subscription = getZombieSubscription();
        DateTime effectiveDate1 = buildDateTime(2011, 1, 1);

        BillingEvent event1 = new DefaultBillingEvent(null, subscription, effectiveDate1, plan, phase1, fixedPrice.getPrice(currency),
                null, currency, BillingPeriod.MONTHLY, 1, BillingModeType.IN_ADVANCE,
                "testEvent1", 1L, SubscriptionTransitionType.CREATE);
        BillingEventSet events = new BillingEventSet();
        events.add(event1);

        UUID accountId = UUID.randomUUID();
        Invoice invoice1 = generator.generateInvoice(accountId, events, null, effectiveDate1, Currency.USD);
        assertNotNull(invoice1);
        assertEquals(invoice1.getNumberOfItems(), 1);
        assertEquals(invoice1.getTotalAmount().compareTo(ZERO), 0);

        List<Invoice> invoiceList = new ArrayList<Invoice>();
        invoiceList.add(invoice1);

        DateTime effectiveDate2 = effectiveDate1.plusDays(30);
        BillingEvent event2 = new DefaultBillingEvent(null, subscription, effectiveDate2, plan, phase2, null,
                recurringPrice.getPrice(currency), currency, BillingPeriod.MONTHLY, 31, BillingModeType.IN_ADVANCE,
                "testEvent2", 2L, SubscriptionTransitionType.CHANGE);
        events.add(event2);

        Invoice invoice2 = generator.generateInvoice(accountId, events, invoiceList, effectiveDate2, Currency.USD);
        assertNotNull(invoice2);
        assertEquals(invoice2.getNumberOfItems(), 1);
        assertEquals(invoice2.getTotalAmount().compareTo(cheapAmount), 0);

        invoiceList.add(invoice2);

        DateTime effectiveDate3 = effectiveDate2.plusMonths(1);
        Invoice invoice3 = generator.generateInvoice(accountId, events, invoiceList, effectiveDate3, Currency.USD);
        assertNotNull(invoice3);
        assertEquals(invoice3.getNumberOfItems(), 1);
        assertEquals(invoice3.getTotalAmount().compareTo(cheapAmount), 0);
    }

    @Test
    public void testInvoiceForEmptyEventSet() throws InvoiceApiException {
        BillingEventSet events = new BillingEventSet();
        Invoice invoice = generator.generateInvoice(UUID.randomUUID(), events, null, new DateTime(), Currency.USD);
        assertNull(invoice);
    }

    @Test
    public void testMixedModeInvoicePersistence() throws InvoiceApiException, CatalogApiException {
        Currency currency = Currency.USD;
        DefaultPrice zeroPrice = new DefaultPrice(BigDecimal.ZERO, Currency.USD);
        MockInternationalPrice fixedPrice = new MockInternationalPrice(zeroPrice);
        MockPlanPhase phase1 = new MockPlanPhase(null, fixedPrice);

        BigDecimal cheapAmount = new BigDecimal("24.95");
        DefaultPrice cheapPrice = new DefaultPrice(cheapAmount, Currency.USD);
        MockInternationalPrice recurringPrice = new MockInternationalPrice(cheapPrice);
        MockPlanPhase phase2 = new MockPlanPhase(recurringPrice, null);

        MockPlan plan = new MockPlan();

        Subscription subscription = getZombieSubscription();
        DateTime effectiveDate1 = buildDateTime(2011, 1, 1);

        BillingEvent event1 = new DefaultBillingEvent(null, subscription, effectiveDate1, plan, phase1,
                fixedPrice.getPrice(currency), null, currency,
                BillingPeriod.MONTHLY, 1, BillingModeType.IN_ADVANCE,
                "testEvent1", 1L, SubscriptionTransitionType.CREATE);
        BillingEventSet events = new BillingEventSet();
        events.add(event1);

        DateTime effectiveDate2 = effectiveDate1.plusDays(30);
        BillingEvent event2 = new DefaultBillingEvent(null, subscription, effectiveDate2, plan, phase2, null,
                recurringPrice.getPrice(currency), currency, BillingPeriod.MONTHLY, 31, BillingModeType.IN_ADVANCE,
                "testEvent2", 2L, SubscriptionTransitionType.CHANGE);
        events.add(event2);

        Invoice invoice = generator.generateInvoice(UUID.randomUUID(), events, null, effectiveDate2, Currency.USD);
        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 2);
        assertEquals(invoice.getTotalAmount().compareTo(cheapAmount), 0);

        invoiceDao.create(invoice, context);
        Invoice savedInvoice = invoiceDao.getById(invoice.getId());

        assertNotNull(savedInvoice);
        assertEquals(savedInvoice.getNumberOfItems(), 2);
        assertEquals(savedInvoice.getTotalAmount().compareTo(cheapAmount), 0);
    }

    @Test
    public void testInvoiceNumber() throws InvoiceApiException {
        Currency currency = Currency.USD;
        DateTime targetDate1 = DateTime.now().plusMonths(1);
        DateTime targetDate2 = DateTime.now().plusMonths(2);

        Subscription subscription = getZombieSubscription();

        Plan plan = BrainDeadProxyFactory.createBrainDeadProxyFor(Plan.class);
        ((ZombieControl) plan).addResult("getName", "plan");

        PlanPhase phase1 = BrainDeadProxyFactory.createBrainDeadProxyFor(PlanPhase.class);
        ((ZombieControl) phase1).addResult("getName", "plan-phase1");

        PlanPhase phase2 = BrainDeadProxyFactory.createBrainDeadProxyFor(PlanPhase.class);
        ((ZombieControl) phase2).addResult("getName", "plan-phase2");

        BillingEventSet events = new BillingEventSet();
        List<Invoice> invoices = new ArrayList<Invoice>();

        BillingEvent event1 = new DefaultBillingEvent(null, subscription, targetDate1, plan, phase1, null,
                                                      TEN, currency,
                                                      BillingPeriod.MONTHLY, 31, BillingModeType.IN_ADVANCE,
                                                      "testEvent1", 1L, SubscriptionTransitionType.CHANGE);
        events.add(event1);

        Invoice invoice1 = generator.generateInvoice(UUID.randomUUID(), events, invoices, targetDate1, Currency.USD);
        invoices.add(invoice1);
        invoiceDao.create(invoice1, context);
        invoice1 = invoiceDao.getById(invoice1.getId());
        assertNotNull(invoice1.getInvoiceNumber());

        BillingEvent event2 = new DefaultBillingEvent(null, subscription, targetDate1, plan, phase2, null,
                                                      TWENTY, currency,
                                                      BillingPeriod.MONTHLY, 31, BillingModeType.IN_ADVANCE,
                                                      "testEvent2", 2L, SubscriptionTransitionType.CHANGE);
        events.add(event2);
        Invoice invoice2 = generator.generateInvoice(UUID.randomUUID(), events, invoices, targetDate2, Currency.USD);
        invoiceDao.create(invoice2, context);
        invoice2 = invoiceDao.getById(invoice2.getId());
        assertNotNull(invoice2.getInvoiceNumber());
    }

    @Test
    public void testAddingWrittenOffTag() throws InvoiceApiException {
        Subscription subscription = getZombieSubscription();

        Plan plan = BrainDeadProxyFactory.createBrainDeadProxyFor(Plan.class);
        ((ZombieControl) plan).addResult("getName", "plan");

        PlanPhase phase1 = BrainDeadProxyFactory.createBrainDeadProxyFor(PlanPhase.class);
        ((ZombieControl) phase1).addResult("getName", "plan-phase1");

        DateTime targetDate1 = clock.getUTCNow();
        Currency currency = Currency.USD;

        // create pseudo-random invoice
        BillingEvent event1 = new DefaultBillingEvent(null, subscription, targetDate1, plan, phase1, null,
                                                      TEN, currency,
                                                      BillingPeriod.MONTHLY, 31, BillingModeType.IN_ADVANCE,
                                                      "testEvent1", 1L, SubscriptionTransitionType.CHANGE);
        BillingEventSet events = new BillingEventSet();
        events.add(event1);

        Invoice invoice = generator.generateInvoice(UUID.randomUUID(), events, null, targetDate1, Currency.USD);
        invoiceDao.create(invoice, context);

        invoiceDao.addControlTag(ControlTagType.WRITTEN_OFF, invoice.getId(), context);

        Invoice savedInvoice = invoiceDao.getById(invoice.getId());
        assertTrue(savedInvoice.hasTag(ControlTagType.WRITTEN_OFF.toString()));
    }

    @Test
    public void testRemoveWrittenOffTag() throws InvoiceApiException {
        Subscription subscription = getZombieSubscription();

        Plan plan = BrainDeadProxyFactory.createBrainDeadProxyFor(Plan.class);
        ((ZombieControl) plan).addResult("getName", "plan");

        PlanPhase phase1 = BrainDeadProxyFactory.createBrainDeadProxyFor(PlanPhase.class);
        ((ZombieControl) phase1).addResult("getName", "plan-phase1");

        DateTime targetDate1 = clock.getUTCNow();
        Currency currency = Currency.USD;

        // create pseudo-random invoice
        BillingEvent event1 = new DefaultBillingEvent(null, subscription, targetDate1, plan, phase1, null,
                                                      TEN, currency,
                                                      BillingPeriod.MONTHLY, 31, BillingModeType.IN_ADVANCE,
                                                      "testEvent1", 1L, SubscriptionTransitionType.CHANGE);
        BillingEventSet events = new BillingEventSet();
        events.add(event1);

        Invoice invoice = generator.generateInvoice(UUID.randomUUID(), events, null, targetDate1, Currency.USD);
        invoiceDao.create(invoice, context);

        invoiceDao.addControlTag(ControlTagType.WRITTEN_OFF, invoice.getId(), context);

        Invoice savedInvoice = invoiceDao.getById(invoice.getId());
        assertTrue(savedInvoice.hasTag(ControlTagType.WRITTEN_OFF.toString()));

        invoiceDao.removeControlTag(ControlTagType.WRITTEN_OFF, invoice.getId(), context);
        savedInvoice = invoiceDao.getById(invoice.getId());
        assertFalse(savedInvoice.hasTag(ControlTagType.WRITTEN_OFF.toString()));
    }
}
