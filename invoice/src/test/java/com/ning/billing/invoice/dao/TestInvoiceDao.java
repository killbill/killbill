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
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.mockito.Mockito;
import org.skife.jdbi.v2.exceptions.TransactionFailedException;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.ErrorCode;
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
import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.invoice.MockBillingEventSet;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.api.InvoicePayment.InvoicePaymentType;
import com.ning.billing.invoice.model.CreditAdjInvoiceItem;
import com.ning.billing.invoice.model.CreditBalanceAdjInvoiceItem;
import com.ning.billing.invoice.model.DefaultInvoice;
import com.ning.billing.invoice.model.DefaultInvoicePayment;
import com.ning.billing.invoice.model.FixedPriceInvoiceItem;
import com.ning.billing.invoice.model.RecurringInvoiceItem;
import com.ning.billing.invoice.model.RepairAdjInvoiceItem;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.entity.EntityPersistenceException;
import com.ning.billing.util.svcapi.junction.BillingEvent;
import com.ning.billing.util.svcapi.junction.BillingEventSet;
import com.ning.billing.util.svcapi.junction.BillingModeType;

import com.google.common.collect.ImmutableMap;

public class TestInvoiceDao extends InvoiceDaoTestBase {

    @Test(groups = "slow")
    public void testCreationAndRetrievalByAccount() {
        final UUID accountId = UUID.randomUUID();
        final Invoice invoice = new DefaultInvoice(accountId, clock.getUTCToday(), clock.getUTCToday(), Currency.USD);
        final LocalDate invoiceDate = invoice.getInvoiceDate();

        invoiceDao.create(invoice, invoice.getTargetDate().getDayOfMonth(), true, internalCallContext);

        final List<Invoice> invoices = invoiceDao.getInvoicesByAccount(accountId, internalCallContext);
        assertNotNull(invoices);
        assertEquals(invoices.size(), 1);
        final Invoice thisInvoice = invoices.get(0);
        assertEquals(invoice.getAccountId(), accountId);
        assertTrue(thisInvoice.getInvoiceDate().compareTo(invoiceDate) == 0);
        assertEquals(thisInvoice.getCurrency(), Currency.USD);
        assertEquals(thisInvoice.getNumberOfItems(), 0);
        assertTrue(thisInvoice.getBalance().compareTo(BigDecimal.ZERO) == 0);
    }

    @Test(groups = "slow")
    public void testInvoicePayment() throws InvoiceApiException {
        final UUID accountId = UUID.randomUUID();
        final Invoice invoice = new DefaultInvoice(accountId, clock.getUTCToday(), clock.getUTCToday(), Currency.USD);
        final UUID invoiceId = invoice.getId();
        final UUID subscriptionId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate startDate = new LocalDate(2010, 1, 1);
        final LocalDate endDate = new LocalDate(2010, 4, 1);
        final InvoiceItem invoiceItem = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, "test plan", "test phase", startDate, endDate,
                                                                 new BigDecimal("21.00"), new BigDecimal("7.00"), Currency.USD);

        invoice.addInvoiceItem(invoiceItem);
        invoiceDao.create(invoice, invoice.getTargetDate().getDayOfMonth(), true, internalCallContext);

        final Invoice savedInvoice = invoiceDao.getById(invoiceId, internalCallContext);
        assertNotNull(savedInvoice);
        assertEquals(savedInvoice.getBalance().compareTo(new BigDecimal("21.00")), 0);
        assertEquals(savedInvoice.getPaidAmount(), BigDecimal.ZERO);
        assertEquals(savedInvoice.getInvoiceItems().size(), 1);

        final BigDecimal paymentAmount = new BigDecimal("11.00");
        final UUID paymentId = UUID.randomUUID();

        invoiceDao.notifyOfPayment(new DefaultInvoicePayment(InvoicePaymentType.ATTEMPT, paymentId, invoiceId, clock.getUTCNow().plusDays(12), paymentAmount, Currency.USD), internalCallContext);

        final Invoice retrievedInvoice = invoiceDao.getById(invoiceId, internalCallContext);
        assertNotNull(retrievedInvoice);
        assertEquals(retrievedInvoice.getInvoiceItems().size(), 1);
        assertEquals(retrievedInvoice.getChargedAmount().compareTo(new BigDecimal("21.00")), 0);
        assertEquals(retrievedInvoice.getBalance().compareTo(new BigDecimal("10.00")), 0);
        assertEquals(retrievedInvoice.getPaidAmount().compareTo(new BigDecimal("11.00")), 0);
    }

    @Test(groups = "slow")
    public void testRetrievalForNonExistentInvoiceId() throws InvoiceApiException {
        try {
            invoiceDao.getById(UUID.randomUUID(), internalCallContext);
            Assert.fail();
        } catch (InvoiceApiException e) {
            if (e.getCode() != ErrorCode.INVOICE_NOT_FOUND.getCode()) {
                Assert.fail();
            }
        }
    }

    @Test(groups = "slow")
    public void testGetInvoicesBySubscriptionForRecurringItems() throws EntityPersistenceException {
        final UUID accountId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();

        final UUID subscriptionId1 = UUID.randomUUID();
        final BigDecimal rate1 = new BigDecimal("17.0");
        final UUID subscriptionId2 = UUID.randomUUID();
        final BigDecimal rate2 = new BigDecimal("42.0");
        final UUID subscriptionId3 = UUID.randomUUID();
        final BigDecimal rate3 = new BigDecimal("3.0");
        final UUID subscriptionId4 = UUID.randomUUID();
        final BigDecimal rate4 = new BigDecimal("12.0");

        final LocalDate targetDate = new LocalDate(2011, 5, 23);

        // Create invoice 1 (subscriptions 1-4)
        final Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate, Currency.USD);
        invoiceDao.create(invoice1, invoice1.getTargetDate().getDayOfMonth(), true, internalCallContext);

        final UUID invoiceId1 = invoice1.getId();

        LocalDate startDate = new LocalDate(2011, 3, 1);
        LocalDate endDate = startDate.plusMonths(1);

        final RecurringInvoiceItem item1 = new RecurringInvoiceItem(invoiceId1, accountId, bundleId, subscriptionId1, "test plan", "test A", startDate, endDate,
                                                                    rate1, rate1, Currency.USD);
        invoiceItemSqlDao.create(item1, internalCallContext);

        final RecurringInvoiceItem item2 = new RecurringInvoiceItem(invoiceId1, accountId, bundleId, subscriptionId2, "test plan", "test B", startDate, endDate,
                                                                    rate2, rate2, Currency.USD);
        invoiceItemSqlDao.create(item2, internalCallContext);

        final RecurringInvoiceItem item3 = new RecurringInvoiceItem(invoiceId1, accountId, bundleId, subscriptionId3, "test plan", "test C", startDate, endDate,
                                                                    rate3, rate3, Currency.USD);
        invoiceItemSqlDao.create(item3, internalCallContext);

        final RecurringInvoiceItem item4 = new RecurringInvoiceItem(invoiceId1, accountId, bundleId, subscriptionId4, "test plan", "test D", startDate, endDate,
                                                                    rate4, rate4, Currency.USD);
        invoiceItemSqlDao.create(item4, internalCallContext);

        // Create invoice 2 (subscriptions 1-3)
        final DefaultInvoice invoice2 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate, Currency.USD);
        invoiceDao.create(invoice2, invoice2.getTargetDate().getDayOfMonth(), true, internalCallContext);

        final UUID invoiceId2 = invoice2.getId();

        startDate = endDate;
        endDate = startDate.plusMonths(1);

        final RecurringInvoiceItem item5 = new RecurringInvoiceItem(invoiceId2, accountId, bundleId, subscriptionId1, "test plan", "test phase A", startDate, endDate,
                                                                    rate1, rate1, Currency.USD);
        invoiceItemSqlDao.create(item5, internalCallContext);

        final RecurringInvoiceItem item6 = new RecurringInvoiceItem(invoiceId2, accountId, bundleId, subscriptionId2, "test plan", "test phase B", startDate, endDate,
                                                                    rate2, rate2, Currency.USD);
        invoiceItemSqlDao.create(item6, internalCallContext);

        final RecurringInvoiceItem item7 = new RecurringInvoiceItem(invoiceId2, accountId, bundleId, subscriptionId3, "test plan", "test phase C", startDate, endDate,
                                                                    rate3, rate3, Currency.USD);
        invoiceItemSqlDao.create(item7, internalCallContext);

        // Check that each subscription returns the correct number of invoices
        final List<Invoice> items1 = invoiceDao.getInvoicesBySubscription(subscriptionId1, internalCallContext);
        assertEquals(items1.size(), 2);

        final List<Invoice> items2 = invoiceDao.getInvoicesBySubscription(subscriptionId2, internalCallContext);
        assertEquals(items2.size(), 2);

        final List<Invoice> items3 = invoiceDao.getInvoicesBySubscription(subscriptionId3, internalCallContext);
        assertEquals(items3.size(), 2);

        final List<Invoice> items4 = invoiceDao.getInvoicesBySubscription(subscriptionId4, internalCallContext);
        assertEquals(items4.size(), 1);
    }

    @Test(groups = "slow")
    public void testGetInvoicesBySubscriptionForFixedItems() throws EntityPersistenceException {
        final UUID accountId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();

        final UUID subscriptionId1 = UUID.randomUUID();
        final BigDecimal rate1 = new BigDecimal("17.0");
        final UUID subscriptionId2 = UUID.randomUUID();
        final BigDecimal rate2 = new BigDecimal("42.0");
        final UUID subscriptionId3 = UUID.randomUUID();
        final BigDecimal rate3 = new BigDecimal("3.0");
        final UUID subscriptionId4 = UUID.randomUUID();
        final BigDecimal rate4 = new BigDecimal("12.0");

        final LocalDate targetDate = new LocalDate(2011, 5, 23);

        // Create invoice 1 (subscriptions 1-4)
        final Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate, Currency.USD);
        invoiceDao.create(invoice1, invoice1.getTargetDate().getDayOfMonth(), true, internalCallContext);

        final UUID invoiceId1 = invoice1.getId();

        LocalDate startDate = new LocalDate(2011, 3, 1);
        LocalDate endDate = startDate.plusMonths(1);

        final FixedPriceInvoiceItem item1 = new FixedPriceInvoiceItem(invoiceId1, accountId, bundleId, subscriptionId1, "test plan", "test A", startDate,
                                                                      rate1, Currency.USD);
        invoiceItemSqlDao.create(item1, internalCallContext);

        final FixedPriceInvoiceItem item2 = new FixedPriceInvoiceItem(invoiceId1, accountId, bundleId, subscriptionId2, "test plan", "test B", startDate,
                                                                      rate2, Currency.USD);
        invoiceItemSqlDao.create(item2, internalCallContext);

        final FixedPriceInvoiceItem item3 = new FixedPriceInvoiceItem(invoiceId1, accountId, bundleId, subscriptionId3, "test plan", "test C", startDate,
                                                                      rate3, Currency.USD);
        invoiceItemSqlDao.create(item3, internalCallContext);

        final FixedPriceInvoiceItem item4 = new FixedPriceInvoiceItem(invoiceId1, accountId, bundleId, subscriptionId4, "test plan", "test D", startDate,
                                                                      rate4, Currency.USD);
        invoiceItemSqlDao.create(item4, internalCallContext);

        // create invoice 2 (subscriptions 1-3)
        final DefaultInvoice invoice2 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate, Currency.USD);
        invoiceDao.create(invoice2, invoice2.getTargetDate().getDayOfMonth(), true, internalCallContext);

        final UUID invoiceId2 = invoice2.getId();

        startDate = endDate;
        endDate = startDate.plusMonths(1);

        final FixedPriceInvoiceItem item5 = new FixedPriceInvoiceItem(invoiceId2, accountId, bundleId, subscriptionId1, "test plan", "test phase A", startDate,
                                                                      rate1, Currency.USD);
        invoiceItemSqlDao.create(item5, internalCallContext);

        final FixedPriceInvoiceItem item6 = new FixedPriceInvoiceItem(invoiceId2, accountId, bundleId, subscriptionId2, "test plan", "test phase B", startDate,
                                                                      rate2, Currency.USD);
        invoiceItemSqlDao.create(item6, internalCallContext);

        final FixedPriceInvoiceItem item7 = new FixedPriceInvoiceItem(invoiceId2, accountId, bundleId, subscriptionId3, "test plan", "test phase C", startDate,
                                                                      rate3, Currency.USD);
        invoiceItemSqlDao.create(item7, internalCallContext);

        // check that each subscription returns the correct number of invoices
        final List<Invoice> items1 = invoiceDao.getInvoicesBySubscription(subscriptionId1, internalCallContext);
        assertEquals(items1.size(), 2);

        final List<Invoice> items2 = invoiceDao.getInvoicesBySubscription(subscriptionId2, internalCallContext);
        assertEquals(items2.size(), 2);

        final List<Invoice> items3 = invoiceDao.getInvoicesBySubscription(subscriptionId3, internalCallContext);
        assertEquals(items3.size(), 2);

        final List<Invoice> items4 = invoiceDao.getInvoicesBySubscription(subscriptionId4, internalCallContext);
        assertEquals(items4.size(), 1);
    }

    @Test(groups = "slow")
    public void testGetInvoicesBySubscriptionForRecurringAndFixedItems() throws EntityPersistenceException {
        final UUID accountId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();

        final UUID subscriptionId1 = UUID.randomUUID();
        final BigDecimal rate1 = new BigDecimal("17.0");
        final UUID subscriptionId2 = UUID.randomUUID();
        final BigDecimal rate2 = new BigDecimal("42.0");
        final UUID subscriptionId3 = UUID.randomUUID();
        final BigDecimal rate3 = new BigDecimal("3.0");
        final UUID subscriptionId4 = UUID.randomUUID();
        final BigDecimal rate4 = new BigDecimal("12.0");

        final LocalDate targetDate = new LocalDate(2011, 5, 23);

        // Create invoice 1 (subscriptions 1-4)
        final Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate, Currency.USD);
        invoiceDao.create(invoice1, invoice1.getTargetDate().getDayOfMonth(), true, internalCallContext);

        final UUID invoiceId1 = invoice1.getId();

        LocalDate startDate = new LocalDate(2011, 3, 1);
        LocalDate endDate = startDate.plusMonths(1);

        final RecurringInvoiceItem recurringItem1 = new RecurringInvoiceItem(invoiceId1, accountId, bundleId, subscriptionId1, "test plan", "test A", startDate, endDate,
                                                                             rate1, rate1, Currency.USD);
        invoiceItemSqlDao.create(recurringItem1, internalCallContext);

        final RecurringInvoiceItem recurringItem2 = new RecurringInvoiceItem(invoiceId1, accountId, bundleId, subscriptionId2, "test plan", "test B", startDate, endDate,
                                                                             rate2, rate2, Currency.USD);
        invoiceItemSqlDao.create(recurringItem2, internalCallContext);

        final RecurringInvoiceItem recurringItem3 = new RecurringInvoiceItem(invoiceId1, accountId, bundleId, subscriptionId3, "test plan", "test C", startDate, endDate,
                                                                             rate3, rate3, Currency.USD);
        invoiceItemSqlDao.create(recurringItem3, internalCallContext);

        final RecurringInvoiceItem recurringItem4 = new RecurringInvoiceItem(invoiceId1, accountId, bundleId, subscriptionId4, "test plan", "test D", startDate, endDate,
                                                                             rate4, rate4, Currency.USD);
        invoiceItemSqlDao.create(recurringItem4, internalCallContext);

        final FixedPriceInvoiceItem fixedItem1 = new FixedPriceInvoiceItem(invoiceId1, accountId, bundleId, subscriptionId1, "test plan", "test A", startDate,
                                                                           rate1, Currency.USD);
        invoiceItemSqlDao.create(fixedItem1, internalCallContext);

        final FixedPriceInvoiceItem fixedItem2 = new FixedPriceInvoiceItem(invoiceId1, accountId, bundleId, subscriptionId2, "test plan", "test B", startDate,
                                                                           rate2, Currency.USD);
        invoiceItemSqlDao.create(fixedItem2, internalCallContext);

        final FixedPriceInvoiceItem fixedItem3 = new FixedPriceInvoiceItem(invoiceId1, accountId, bundleId, subscriptionId3, "test plan", "test C", startDate,
                                                                           rate3, Currency.USD);
        invoiceItemSqlDao.create(fixedItem3, internalCallContext);

        final FixedPriceInvoiceItem fixedItem4 = new FixedPriceInvoiceItem(invoiceId1, accountId, bundleId, subscriptionId4, "test plan", "test D", startDate,
                                                                           rate4, Currency.USD);
        invoiceItemSqlDao.create(fixedItem4, internalCallContext);

        // create invoice 2 (subscriptions 1-3)
        final DefaultInvoice invoice2 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate, Currency.USD);
        invoiceDao.create(invoice2, invoice2.getTargetDate().getDayOfMonth(), true, internalCallContext);

        final UUID invoiceId2 = invoice2.getId();

        startDate = endDate;
        endDate = startDate.plusMonths(1);

        final RecurringInvoiceItem recurringItem5 = new RecurringInvoiceItem(invoiceId2, accountId, bundleId, subscriptionId1, "test plan", "test phase A", startDate, endDate,
                                                                             rate1, rate1, Currency.USD);
        invoiceItemSqlDao.create(recurringItem5, internalCallContext);

        final RecurringInvoiceItem recurringItem6 = new RecurringInvoiceItem(invoiceId2, accountId, bundleId, subscriptionId2, "test plan", "test phase B", startDate, endDate,
                                                                             rate2, rate2, Currency.USD);
        invoiceItemSqlDao.create(recurringItem6, internalCallContext);

        final RecurringInvoiceItem recurringItem7 = new RecurringInvoiceItem(invoiceId2, accountId, bundleId, subscriptionId3, "test plan", "test phase C", startDate, endDate,
                                                                             rate3, rate3, Currency.USD);
        invoiceItemSqlDao.create(recurringItem7, internalCallContext);
        final FixedPriceInvoiceItem fixedItem5 = new FixedPriceInvoiceItem(invoiceId2, accountId, bundleId, subscriptionId1, "test plan", "test phase A", startDate,
                                                                           rate1, Currency.USD);
        invoiceItemSqlDao.create(fixedItem5, internalCallContext);

        final FixedPriceInvoiceItem fixedItem6 = new FixedPriceInvoiceItem(invoiceId2, accountId, bundleId, subscriptionId2, "test plan", "test phase B", startDate,
                                                                           rate2, Currency.USD);
        invoiceItemSqlDao.create(fixedItem6, internalCallContext);

        final FixedPriceInvoiceItem fixedItem7 = new FixedPriceInvoiceItem(invoiceId2, accountId, bundleId, subscriptionId3, "test plan", "test phase C", startDate,
                                                                           rate3, Currency.USD);
        invoiceItemSqlDao.create(fixedItem7, internalCallContext);

        // check that each subscription returns the correct number of invoices
        final List<Invoice> items1 = invoiceDao.getInvoicesBySubscription(subscriptionId1, internalCallContext);
        assertEquals(items1.size(), 4);

        final List<Invoice> items2 = invoiceDao.getInvoicesBySubscription(subscriptionId2, internalCallContext);
        assertEquals(items2.size(), 4);

        final List<Invoice> items3 = invoiceDao.getInvoicesBySubscription(subscriptionId3, internalCallContext);
        assertEquals(items3.size(), 4);

        final List<Invoice> items4 = invoiceDao.getInvoicesBySubscription(subscriptionId4, internalCallContext);
        assertEquals(items4.size(), 2);
    }

    @Test(groups = "slow")
    public void testGetInvoicesForAccountAfterDate() {
        final UUID accountId = UUID.randomUUID();
        final LocalDate targetDate1 = new LocalDate(2011, 10, 6);
        final Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate1, Currency.USD);
        invoiceDao.create(invoice1, invoice1.getTargetDate().getDayOfMonth(), true, internalCallContext);

        final LocalDate targetDate2 = new LocalDate(2011, 12, 6);
        final Invoice invoice2 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate2, Currency.USD);
        invoiceDao.create(invoice2, invoice2.getTargetDate().getDayOfMonth(), true, internalCallContext);

        List<Invoice> invoices;
        invoices = invoiceDao.getInvoicesByAccount(accountId, new LocalDate(2011, 1, 1), internalCallContext);
        assertEquals(invoices.size(), 2);

        invoices = invoiceDao.getInvoicesByAccount(accountId, new LocalDate(2011, 10, 6), internalCallContext);
        assertEquals(invoices.size(), 2);

        invoices = invoiceDao.getInvoicesByAccount(accountId, new LocalDate(2011, 10, 11), internalCallContext);
        assertEquals(invoices.size(), 1);

        invoices = invoiceDao.getInvoicesByAccount(accountId, new LocalDate(2011, 12, 6), internalCallContext);
        assertEquals(invoices.size(), 1);

        invoices = invoiceDao.getInvoicesByAccount(accountId, new LocalDate(2012, 1, 1), internalCallContext);
        assertEquals(invoices.size(), 0);
    }

    @Test(groups = "slow")
    public void testAccountBalance() throws EntityPersistenceException {
        final UUID accountId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate targetDate1 = new LocalDate(2011, 10, 6);
        final Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate1, Currency.USD);
        invoiceDao.create(invoice1, invoice1.getTargetDate().getDayOfMonth(), true, internalCallContext);

        final LocalDate startDate = new LocalDate(2011, 3, 1);
        final LocalDate endDate = startDate.plusMonths(1);

        final BigDecimal rate1 = new BigDecimal("17.0");
        final BigDecimal rate2 = new BigDecimal("42.0");

        final RecurringInvoiceItem item1 = new RecurringInvoiceItem(invoice1.getId(), accountId, bundleId, UUID.randomUUID(), "test plan", "test phase A", startDate,
                                                                    endDate, rate1, rate1, Currency.USD);
        invoiceItemSqlDao.create(item1, internalCallContext);

        final RecurringInvoiceItem item2 = new RecurringInvoiceItem(invoice1.getId(), accountId, bundleId, UUID.randomUUID(), "test plan", "test phase B", startDate,
                                                                    endDate, rate2, rate2, Currency.USD);
        invoiceItemSqlDao.create(item2, internalCallContext);

        final BigDecimal payment1 = new BigDecimal("48.0");
        final InvoicePayment payment = new DefaultInvoicePayment(InvoicePaymentType.ATTEMPT, UUID.randomUUID(), invoice1.getId(), new DateTime(), payment1, Currency.USD);
        invoicePaymentDao.create(payment, internalCallContext);

        final BigDecimal balance = invoiceDao.getAccountBalance(accountId, internalCallContext);
        assertEquals(balance.compareTo(rate1.add(rate2).subtract(payment1)), 0);
    }

    @Test(groups = "slow")
    public void testAccountBalanceWithCredit() throws EntityPersistenceException {
        final UUID accountId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate targetDate1 = new LocalDate(2011, 10, 6);
        final Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate1, Currency.USD);
        invoiceDao.create(invoice1, invoice1.getTargetDate().getDayOfMonth(), true, internalCallContext);

        final LocalDate startDate = new LocalDate(2011, 3, 1);
        final LocalDate endDate = startDate.plusMonths(1);

        final BigDecimal rate1 = new BigDecimal("17.0");

        final RecurringInvoiceItem item1 = new RecurringInvoiceItem(invoice1.getId(), accountId, bundleId, UUID.randomUUID(), "test plan", "test phase A", startDate,
                                                                    endDate, rate1, rate1, Currency.USD);
        invoiceItemSqlDao.create(item1, internalCallContext);

        final CreditAdjInvoiceItem creditItem = new CreditAdjInvoiceItem(invoice1.getId(), accountId, new LocalDate(), rate1.negate(), Currency.USD);
        invoiceItemSqlDao.create(creditItem, internalCallContext);

        final BigDecimal balance = invoiceDao.getAccountBalance(accountId, internalCallContext);
        assertEquals(balance.compareTo(BigDecimal.ZERO), 0);
    }

    @Test(groups = "slow")
    public void testAccountBalanceWithNoPayments() throws EntityPersistenceException {
        final UUID accountId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate targetDate1 = new LocalDate(2011, 10, 6);
        final Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate1, Currency.USD);
        invoiceDao.create(invoice1, invoice1.getTargetDate().getDayOfMonth(), true, internalCallContext);

        final LocalDate startDate = new LocalDate(2011, 3, 1);
        final LocalDate endDate = startDate.plusMonths(1);

        final BigDecimal rate1 = new BigDecimal("17.0");
        final BigDecimal rate2 = new BigDecimal("42.0");

        final RecurringInvoiceItem item1 = new RecurringInvoiceItem(invoice1.getId(), accountId, bundleId, UUID.randomUUID(), "test plan", "test phase A", startDate, endDate,
                                                                    rate1, rate1, Currency.USD);
        invoiceItemSqlDao.create(item1, internalCallContext);

        final RecurringInvoiceItem item2 = new RecurringInvoiceItem(invoice1.getId(), accountId, bundleId, UUID.randomUUID(), "test plan", "test phase B", startDate, endDate,
                                                                    rate2, rate2, Currency.USD);
        invoiceItemSqlDao.create(item2, internalCallContext);

        final BigDecimal balance = invoiceDao.getAccountBalance(accountId, internalCallContext);
        assertEquals(balance.compareTo(rate1.add(rate2)), 0);
    }

    @Test(groups = "slow")
    public void testAccountBalanceWithNoInvoiceItems() throws EntityPersistenceException {
        final UUID accountId = UUID.randomUUID();
        final LocalDate targetDate1 = new LocalDate(2011, 10, 6);
        final Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate1, Currency.USD);
        invoiceDao.create(invoice1, invoice1.getTargetDate().getDayOfMonth(), true, internalCallContext);

        final BigDecimal payment1 = new BigDecimal("48.0");
        final InvoicePayment payment = new DefaultInvoicePayment(InvoicePaymentType.ATTEMPT, UUID.randomUUID(), invoice1.getId(), new DateTime(), payment1, Currency.USD);
        invoicePaymentDao.create(payment, internalCallContext);

        final BigDecimal balance = invoiceDao.getAccountBalance(accountId, internalCallContext);
        assertEquals(balance.compareTo(BigDecimal.ZERO.subtract(payment1)), 0);
    }

    @Test(groups = "slow")
    public void testAccountBalanceWithRefundNoAdj() throws InvoiceApiException, EntityPersistenceException {
        testAccountBalanceWithRefundInternal(false);
    }

    @Test(groups = "slow")
    public void testAccountBalanceWithRefundAndAdj() throws InvoiceApiException, EntityPersistenceException {
        testAccountBalanceWithRefundInternal(true);
    }

    private void testAccountBalanceWithRefundInternal(final boolean withAdjustment) throws InvoiceApiException, EntityPersistenceException {

        final UUID accountId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate targetDate1 = new LocalDate(2011, 10, 6);
        final Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate1, Currency.USD);
        invoiceDao.create(invoice1, invoice1.getTargetDate().getDayOfMonth(), true, internalCallContext);

        final LocalDate startDate = new LocalDate(2011, 3, 1);
        final LocalDate endDate = startDate.plusMonths(1);

        final BigDecimal rate1 = new BigDecimal("20.0");
        final BigDecimal refund1 = new BigDecimal("7.00");
        final BigDecimal rate2 = new BigDecimal("10.0");

        // Recurring item
        final RecurringInvoiceItem item2 = new RecurringInvoiceItem(invoice1.getId(), accountId, bundleId, UUID.randomUUID(), "test plan", "test phase B", startDate,
                                                                    endDate, rate1, rate1, Currency.USD);
        invoiceItemSqlDao.create(item2, internalCallContext);
        BigDecimal balance = invoiceDao.getAccountBalance(accountId, internalCallContext);
        assertEquals(balance.compareTo(new BigDecimal("20.00")), 0);

        // Pay the whole thing
        final UUID paymentId = UUID.randomUUID();
        final BigDecimal payment1 = rate1;
        final InvoicePayment payment = new DefaultInvoicePayment(InvoicePaymentType.ATTEMPT, paymentId, invoice1.getId(), new DateTime(), payment1, Currency.USD);
        invoicePaymentDao.create(payment, internalCallContext);
        balance = invoiceDao.getAccountBalance(accountId, internalCallContext);
        assertEquals(balance.compareTo(new BigDecimal("0.00")), 0);

        invoiceDao.createRefund(paymentId, refund1, withAdjustment, ImmutableMap.<UUID, BigDecimal>of(), UUID.randomUUID(), internalCallContext);
        balance = invoiceDao.getAccountBalance(accountId, internalCallContext);
        if (withAdjustment) {
            assertEquals(balance.compareTo(BigDecimal.ZERO), 0);
        } else {
            assertEquals(balance.compareTo(new BigDecimal("7.00")), 0);
        }
    }

    @Test(groups = "slow")
    private void testFullRefundWithRepairAndInvoiceItemAdjustment() throws InvoiceApiException {
        final BigDecimal refundAmount = new BigDecimal("20.00");
        testRefundWithRepairAndInvoiceItemAdjustmentInternal(refundAmount);
    }

    @Test(groups = "slow")
    private void testPartialRefundWithRepairAndInvoiceItemAdjustment() throws InvoiceApiException {
        final BigDecimal refundAmount = new BigDecimal("7.00");
        testRefundWithRepairAndInvoiceItemAdjustmentInternal(refundAmount);
    }

    private void testRefundWithRepairAndInvoiceItemAdjustmentInternal(final BigDecimal refundAmount) throws InvoiceApiException {
        final UUID accountId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate targetDate1 = new LocalDate(2011, 10, 6);

        final Invoice invoice = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate1, Currency.USD);
        invoiceDao.create(invoice, invoice.getTargetDate().getDayOfMonth(), true, internalCallContext);

        final LocalDate startDate = new LocalDate(2011, 3, 1);
        final LocalDate endDate = startDate.plusMonths(1);

        final BigDecimal amount = new BigDecimal("20.0");

        // Recurring item
        final RecurringInvoiceItem item2 = new RecurringInvoiceItem(invoice.getId(), accountId, bundleId, UUID.randomUUID(), "test plan", "test phase B", startDate,
                                                                    endDate, amount, amount, Currency.USD);
        invoiceItemSqlDao.create(item2, internalCallContext);
        BigDecimal balance = invoiceDao.getAccountBalance(accountId, internalCallContext);
        assertEquals(balance.compareTo(new BigDecimal("20.00")), 0);

        // Pay the whole thing
        final UUID paymentId = UUID.randomUUID();
        final BigDecimal payment1 = amount;
        final InvoicePayment payment = new DefaultInvoicePayment(InvoicePaymentType.ATTEMPT, paymentId, invoice.getId(), new DateTime(), payment1, Currency.USD);
        invoicePaymentDao.create(payment, internalCallContext);
        balance = invoiceDao.getAccountBalance(accountId, internalCallContext);
        assertEquals(balance.compareTo(new BigDecimal("0.00")), 0);

        // Repair the item (And add CBA item that should be generated)
        final InvoiceItem repairItem = new RepairAdjInvoiceItem(invoice.getId(), accountId, startDate, endDate, amount.negate(), Currency.USD, item2.getId());
        invoiceItemSqlDao.create(repairItem, internalCallContext);

        final InvoiceItem cbaItem = new CreditBalanceAdjInvoiceItem(invoice.getId(), accountId, startDate, amount, Currency.USD);
        invoiceItemSqlDao.create(cbaItem, internalCallContext);

        Map<UUID, BigDecimal> itemAdjustment = new HashMap<UUID, BigDecimal>();
        itemAdjustment.put(item2.getId(), refundAmount);

        invoiceDao.createRefund(paymentId, refundAmount, true, itemAdjustment, UUID.randomUUID(), internalCallContext);
        balance = invoiceDao.getAccountBalance(accountId, internalCallContext);

        final boolean partialRefund = refundAmount.compareTo(amount) < 0;
        final BigDecimal cba = invoiceDao.getAccountCBA(accountId, internalCallContext);
        final Invoice savedInvoice = invoiceDao.getById(invoice.getId(), internalCallContext);
        assertEquals(cba.compareTo(new BigDecimal("20.0")), 0);
        if (partialRefund) {
            // IB = 20 (rec) - 20 (repair) + 20 (cba) - (20 -7) = 7;  AB = IB - CBA = 7 - 20 = -13
            assertEquals(balance.compareTo(new BigDecimal("-13.0")), 0);
            assertEquals(savedInvoice.getInvoiceItems().size(), 3);
        } else {
            assertEquals(balance.compareTo(new BigDecimal("0.0")), 0);
            assertEquals(savedInvoice.getInvoiceItems().size(), 3);
        }
    }

    @Test(groups = "slow")
    public void testAccountBalanceWithSmallRefundAndCBANoAdj() throws InvoiceApiException, EntityPersistenceException {
        BigDecimal refundAmount = new BigDecimal("7.00");
        BigDecimal expectedBalance = new BigDecimal("-3.00");
        testAccountBalanceWithRefundAndCBAInternal(false, refundAmount, expectedBalance);
    }

    @Test(groups = "slow")
    public void testAccountBalanceWithSmallRefundAndCBAWithAdj() throws InvoiceApiException, EntityPersistenceException {
        BigDecimal refundAmount = new BigDecimal("7.00");
        BigDecimal expectedBalance = new BigDecimal("-10.00");
        testAccountBalanceWithRefundAndCBAInternal(true, refundAmount, expectedBalance);
    }

    @Test(groups = "slow")
    public void testAccountBalanceWithLargeRefundAndCBANoAdj() throws InvoiceApiException, EntityPersistenceException {
        BigDecimal refundAmount = new BigDecimal("20.00");
        BigDecimal expectedBalance = new BigDecimal("10.00");
        testAccountBalanceWithRefundAndCBAInternal(false, refundAmount, expectedBalance);
    }

    @Test(groups = "slow")
    public void testAccountBalanceWithLargeRefundAndCBAWithAdj() throws InvoiceApiException, EntityPersistenceException {
        BigDecimal refundAmount = new BigDecimal("20.00");
        BigDecimal expectedBalance = new BigDecimal("-10.00");
        testAccountBalanceWithRefundAndCBAInternal(true, refundAmount, expectedBalance);
    }

    private void testAccountBalanceWithRefundAndCBAInternal(final boolean withAdjustment, final BigDecimal refundAmount, final BigDecimal expectedFinalBalance) throws InvoiceApiException, EntityPersistenceException {
        final UUID accountId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate targetDate1 = new LocalDate(2011, 10, 6);
        final Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate1, Currency.USD);
        invoiceDao.create(invoice1, invoice1.getTargetDate().getDayOfMonth(), true, internalCallContext);

        final LocalDate startDate = new LocalDate(2011, 3, 1);
        final LocalDate endDate = startDate.plusMonths(1);

        final BigDecimal amount1 = new BigDecimal("5.0");
        final BigDecimal rate1 = new BigDecimal("20.0");
        final BigDecimal rate2 = new BigDecimal("10.0");

        // Fixed Item
        final FixedPriceInvoiceItem item1 = new FixedPriceInvoiceItem(invoice1.getId(), accountId, bundleId, UUID.randomUUID(), "test plan", "test phase A", startDate,
                                                                      amount1, Currency.USD);
        invoiceItemSqlDao.create(item1, internalCallContext);

        BigDecimal balance = invoiceDao.getAccountBalance(accountId, internalCallContext);
        assertEquals(balance.compareTo(new BigDecimal("5.00")), 0);

        // Recurring item
        final RecurringInvoiceItem item2 = new RecurringInvoiceItem(invoice1.getId(), accountId, bundleId, UUID.randomUUID(), "test plan", "test phase B", startDate,
                                                                    endDate, rate1, rate1, Currency.USD);
        invoiceItemSqlDao.create(item2, internalCallContext);
        balance = invoiceDao.getAccountBalance(accountId, internalCallContext);
        assertEquals(balance.compareTo(new BigDecimal("25.00")), 0);

        // Pay the whole thing
        final UUID paymentId = UUID.randomUUID();
        final BigDecimal payment1 = amount1.add(rate1);
        final InvoicePayment payment = new DefaultInvoicePayment(InvoicePaymentType.ATTEMPT, paymentId, invoice1.getId(), new DateTime(), payment1, Currency.USD);
        invoicePaymentDao.create(payment, internalCallContext);
        balance = invoiceDao.getAccountBalance(accountId, internalCallContext);
        assertEquals(balance.compareTo(new BigDecimal("0.00")), 0);

        // Repair previous item with rate 2
        final RepairAdjInvoiceItem item2Repair = new RepairAdjInvoiceItem(invoice1.getId(), accountId, startDate, endDate, rate1.negate(), Currency.USD, item2.getId());
        final RecurringInvoiceItem item2Replace = new RecurringInvoiceItem(invoice1.getId(), accountId, bundleId, UUID.randomUUID(), "test plan", "test phase B", startDate,
                                                                           endDate, rate2, rate2, Currency.USD);
        invoiceItemSqlDao.create(item2Repair, internalCallContext);
        invoiceItemSqlDao.create(item2Replace, internalCallContext);
        balance = invoiceDao.getAccountBalance(accountId, internalCallContext);
        assertEquals(balance.compareTo(new BigDecimal("-10.00")), 0);

        // CBA
        final CreditBalanceAdjInvoiceItem cbaItem = new CreditBalanceAdjInvoiceItem(invoice1.getId(), accountId, new LocalDate(), balance.negate(), Currency.USD);
        invoiceItemSqlDao.create(cbaItem, internalCallContext);
        balance = invoiceDao.getAccountBalance(accountId, internalCallContext);
        assertEquals(balance.compareTo(new BigDecimal("-10.00")), 0);
        BigDecimal cba = invoiceDao.getAccountCBA(accountId, internalCallContext);
        assertEquals(cba.compareTo(new BigDecimal("10.00")), 0);

        // PARTIAL REFUND on the payment
        invoiceDao.createRefund(paymentId, refundAmount, withAdjustment, ImmutableMap.<UUID, BigDecimal>of(), UUID.randomUUID(), internalCallContext);

        balance = invoiceDao.getAccountBalance(accountId, internalCallContext);
        assertEquals(balance.compareTo(expectedFinalBalance), 0);
        cba = invoiceDao.getAccountCBA(accountId, internalCallContext);
        assertEquals(cba.compareTo(new BigDecimal("10.00")), 0);
    }

    @Test(groups = "slow")
    public void testExternalChargeWithCBA() throws InvoiceApiException, EntityPersistenceException {

        final UUID accountId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate targetDate1 = new LocalDate(2011, 10, 6);
        final Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate1, Currency.USD);
        invoiceDao.create(invoice1, invoice1.getTargetDate().getDayOfMonth(), true, internalCallContext);

        // CREATE INVOICE WITH A (just) CBA. Should not happen, but that does not matter for that test
        final CreditBalanceAdjInvoiceItem cbaItem = new CreditBalanceAdjInvoiceItem(invoice1.getId(), accountId, new LocalDate(), new BigDecimal("20.0"), Currency.USD);
        invoiceItemSqlDao.create(cbaItem, internalCallContext);

        final InvoiceItem charge = invoiceDao.insertExternalCharge(accountId, null, bundleId, "bla", new BigDecimal("15.0"), clock.getUTCNow().toLocalDate(), Currency.USD, internalCallContext);

        final Invoice newInvoice = invoiceDao.getById(charge.getInvoiceId(), internalCallContext);
        List<InvoiceItem> items = newInvoice.getInvoiceItems();
        assertEquals(items.size(), 2);
        for (InvoiceItem cur : items) {
            if (!cur.getId().equals(charge.getId())) {
                assertEquals(cur.getInvoiceItemType(), InvoiceItemType.CBA_ADJ);
                assertTrue(cur.getAmount().compareTo(new BigDecimal("-15.00")) == 0);
                break;
            }
        }
    }

    @Test(groups = "slow")
    public void testAccountBalanceWithAllSortsOfThings() throws EntityPersistenceException {
        final UUID accountId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate targetDate1 = new LocalDate(2011, 10, 6);
        final Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate1, Currency.USD);
        invoiceDao.create(invoice1, invoice1.getTargetDate().getDayOfMonth(), true, internalCallContext);

        final LocalDate startDate = new LocalDate(2011, 3, 1);
        final LocalDate endDate = startDate.plusMonths(1);

        final BigDecimal amount1 = new BigDecimal("5.0");
        final BigDecimal rate1 = new BigDecimal("20.0");
        final BigDecimal rate2 = new BigDecimal("10.0");

        // Fixed Item
        final FixedPriceInvoiceItem item1 = new FixedPriceInvoiceItem(invoice1.getId(), accountId, bundleId, UUID.randomUUID(), "test plan", "test phase A", startDate,
                                                                      amount1, Currency.USD);
        invoiceItemSqlDao.create(item1, internalCallContext);

        BigDecimal balance = invoiceDao.getAccountBalance(accountId, internalCallContext);
        assertEquals(balance.compareTo(new BigDecimal("5.00")), 0);

        // Recurring item
        final RecurringInvoiceItem item2 = new RecurringInvoiceItem(invoice1.getId(), accountId, bundleId, UUID.randomUUID(), "test plan", "test phase B", startDate,
                                                                    endDate, rate1, rate1, Currency.USD);
        invoiceItemSqlDao.create(item2, internalCallContext);
        balance = invoiceDao.getAccountBalance(accountId, internalCallContext);
        assertEquals(balance.compareTo(new BigDecimal("25.00")), 0);

        // Pay the whole thing
        final BigDecimal payment1 = amount1.add(rate1);
        final InvoicePayment payment = new DefaultInvoicePayment(InvoicePaymentType.ATTEMPT, UUID.randomUUID(), invoice1.getId(), new DateTime(), payment1, Currency.USD);
        invoicePaymentDao.create(payment, internalCallContext);
        balance = invoiceDao.getAccountBalance(accountId, internalCallContext);
        assertEquals(balance.compareTo(new BigDecimal("0.00")), 0);

        // Repair previous item with rate 2
        final RepairAdjInvoiceItem item2Repair = new RepairAdjInvoiceItem(invoice1.getId(), accountId, startDate, endDate, rate1.negate(), Currency.USD, item2.getId());
        final RecurringInvoiceItem item2Replace = new RecurringInvoiceItem(invoice1.getId(), accountId, bundleId, UUID.randomUUID(), "test plan", "test phase B", startDate,
                                                                           endDate, rate2, rate2, Currency.USD);
        invoiceItemSqlDao.create(item2Repair, internalCallContext);
        invoiceItemSqlDao.create(item2Replace, internalCallContext);
        balance = invoiceDao.getAccountBalance(accountId, internalCallContext);
        assertEquals(balance.compareTo(new BigDecimal("-10.00")), 0);

        // CBA
        final CreditBalanceAdjInvoiceItem cbaItem = new CreditBalanceAdjInvoiceItem(invoice1.getId(), accountId, new LocalDate(), balance.negate(), Currency.USD);
        invoiceItemSqlDao.create(cbaItem, internalCallContext);
        balance = invoiceDao.getAccountBalance(accountId, internalCallContext);
        assertEquals(balance.compareTo(new BigDecimal("-10.00")), 0);
        BigDecimal cba = invoiceDao.getAccountCBA(accountId, internalCallContext);
        assertEquals(cba.compareTo(new BigDecimal("10.00")), 0);

        // partial REFUND on the payment (along with CBA generated by the system)
        final InvoicePayment refund = new DefaultInvoicePayment(UUID.randomUUID(), InvoicePaymentType.ATTEMPT, UUID.randomUUID(), invoice1.getId(), new DateTime(), rate2.negate(), Currency.USD, null, payment.getId());
        invoicePaymentDao.create(refund, internalCallContext);
        final CreditBalanceAdjInvoiceItem cbaItem2 = new CreditBalanceAdjInvoiceItem(invoice1.getId(), accountId, new LocalDate(), rate2.negate(), Currency.USD);
        invoiceItemSqlDao.create(cbaItem2, internalCallContext);

        balance = invoiceDao.getAccountBalance(accountId, internalCallContext);
        assertEquals(balance.compareTo(BigDecimal.ZERO), 0);
        cba = invoiceDao.getAccountCBA(accountId, internalCallContext);
        assertEquals(cba.compareTo(BigDecimal.ZERO), 0);

        // NEXT RECURRING on invoice 2

        final Invoice invoice2 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate1.plusMonths(1), Currency.USD);
        invoiceDao.create(invoice2, invoice2.getTargetDate().getDayOfMonth(), true, internalCallContext);

        final RecurringInvoiceItem nextItem = new RecurringInvoiceItem(invoice2.getId(), accountId, bundleId, UUID.randomUUID(), "test plan", "test bla", startDate.plusMonths(1),
                                                                       endDate.plusMonths(1), rate2, rate2, Currency.USD);
        invoiceItemSqlDao.create(nextItem, internalCallContext);
        balance = invoiceDao.getAccountBalance(accountId, internalCallContext);
        assertEquals(balance.compareTo(new BigDecimal("10.00")), 0);
        cba = invoiceDao.getAccountCBA(accountId, internalCallContext);
        assertEquals(cba.compareTo(new BigDecimal("0.00")), 0);

        // FINALLY ISSUE A CREDIT ADJ
        final CreditAdjInvoiceItem creditItem = new CreditAdjInvoiceItem(invoice2.getId(), accountId, new LocalDate(), rate2.negate(), Currency.USD);
        invoiceItemSqlDao.create(creditItem, internalCallContext);
        balance = invoiceDao.getAccountBalance(accountId, internalCallContext);
        assertEquals(balance.compareTo(new BigDecimal("0.00")), 0);
        cba = invoiceDao.getAccountCBA(accountId, internalCallContext);
        assertEquals(cba.compareTo(new BigDecimal("0.00")), 0);

    }

    @Test(groups = "slow")
    public void testAccountCredit() {

        final UUID accountId = UUID.randomUUID();

        final LocalDate effectiveDate = new LocalDate(2011, 3, 1);

        final BigDecimal creditAmount = new BigDecimal("5.0");

        invoiceDao.insertCredit(accountId, null, creditAmount, effectiveDate, Currency.USD, internalCallContext);

        final List<Invoice> invoices = invoiceDao.getAllInvoicesByAccount(accountId, internalCallContext);
        assertEquals(invoices.size(), 1);

        final Invoice invoice = invoices.get(0);
        assertTrue(invoice.getBalance().compareTo(BigDecimal.ZERO) == 0);
        final List<InvoiceItem> invoiceItems = invoice.getInvoiceItems();
        assertEquals(invoiceItems.size(), 2);
        boolean foundCredit = false;
        boolean foundCBA = false;
        for (final InvoiceItem cur : invoiceItems) {
            if (cur.getInvoiceItemType() == InvoiceItemType.CREDIT_ADJ) {
                foundCredit = true;
                assertTrue(cur.getAmount().compareTo(creditAmount.negate()) == 0);
            } else if (cur.getInvoiceItemType() == InvoiceItemType.CBA_ADJ) {
                foundCBA = true;
                assertTrue(cur.getAmount().compareTo(creditAmount) == 0);
            }
        }
        assertTrue(foundCredit);
        assertTrue(foundCBA);
    }

    @Test(groups = "slow")
    public void testInvoiceCreditWithBalancePositive() throws EntityPersistenceException {
        final BigDecimal creditAmount = new BigDecimal("2.0");
        final BigDecimal expectedBalance = new BigDecimal("3.0");
        final boolean expectCBA = false;
        testInvoiceCreditInternal(creditAmount, expectedBalance, expectCBA);
    }

    @Test(groups = "slow")
    public void testInvoiceCreditWithBalanceNegative() throws EntityPersistenceException {
        final BigDecimal creditAmount = new BigDecimal("7.0");
        final BigDecimal expectedBalance = new BigDecimal("0.0");
        final boolean expectCBA = true;
        testInvoiceCreditInternal(creditAmount, expectedBalance, expectCBA);
    }

    @Test(groups = "slow")
    public void testInvoiceCreditWithBalanceZero() throws EntityPersistenceException {
        final BigDecimal creditAmount = new BigDecimal("5.0");
        final BigDecimal expectedBalance = new BigDecimal("0.0");
        final boolean expectCBA = false;
        testInvoiceCreditInternal(creditAmount, expectedBalance, expectCBA);
    }

    private void testInvoiceCreditInternal(final BigDecimal creditAmount, final BigDecimal expectedBalance, final boolean expectCBA) throws EntityPersistenceException {

        final UUID accountId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();

        // Create one invoice with a fixed invoice item
        final LocalDate targetDate = new LocalDate(2011, 2, 15);
        final Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate, Currency.USD);
        invoiceDao.create(invoice1, invoice1.getTargetDate().getDayOfMonth(), true, internalCallContext);

        final LocalDate startDate = new LocalDate(2011, 3, 1);

        final BigDecimal amount1 = new BigDecimal("5.0");

        // Fixed Item
        final FixedPriceInvoiceItem item1 = new FixedPriceInvoiceItem(invoice1.getId(), accountId, bundleId, UUID.randomUUID(), "test plan", "test phase A", startDate,
                                                                      amount1, Currency.USD);
        invoiceItemSqlDao.create(item1, internalCallContext);

        // Create the credit item
        final LocalDate effectiveDate = new LocalDate(2011, 3, 1);

        invoiceDao.insertCredit(accountId, invoice1.getId(), creditAmount, effectiveDate, Currency.USD, internalCallContext);

        final List<Invoice> invoices = invoiceDao.getAllInvoicesByAccount(accountId, internalCallContext);
        assertEquals(invoices.size(), 1);

        final Invoice invoice = invoices.get(0);
        assertTrue(invoice.getBalance().compareTo(expectedBalance) == 0);
        final List<InvoiceItem> invoiceItems = invoice.getInvoiceItems();
        assertEquals(invoiceItems.size(), expectCBA ? 3 : 2);
        boolean foundCredit = false;
        boolean foundCBA = false;
        for (final InvoiceItem cur : invoiceItems) {
            if (cur.getInvoiceItemType() == InvoiceItemType.CREDIT_ADJ) {
                foundCredit = true;
                assertTrue(cur.getAmount().compareTo(creditAmount.negate()) == 0);
            } else if (cur.getInvoiceItemType() == InvoiceItemType.CBA_ADJ) {
                foundCBA = true;
            }
        }
        assertEquals(foundCBA, expectCBA);
        assertTrue(foundCredit);
    }

    @Test(groups = "slow")
    public void testGetUnpaidInvoicesByAccountId() throws EntityPersistenceException {
        final UUID accountId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate targetDate1 = new LocalDate(2011, 10, 6);
        final Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate1, Currency.USD);
        invoiceDao.create(invoice1, invoice1.getTargetDate().getDayOfMonth(), true, internalCallContext);

        final LocalDate startDate = new LocalDate(2011, 3, 1);
        final LocalDate endDate = startDate.plusMonths(1);

        final BigDecimal rate1 = new BigDecimal("17.0");
        final BigDecimal rate2 = new BigDecimal("42.0");

        final RecurringInvoiceItem item1 = new RecurringInvoiceItem(invoice1.getId(), accountId, bundleId, UUID.randomUUID(), "test plan", "test phase A", startDate, endDate,
                                                                    rate1, rate1, Currency.USD);
        invoiceItemSqlDao.create(item1, internalCallContext);

        final RecurringInvoiceItem item2 = new RecurringInvoiceItem(invoice1.getId(), accountId, bundleId, UUID.randomUUID(), "test plan", "test phase B", startDate, endDate,
                                                                    rate2, rate2, Currency.USD);
        invoiceItemSqlDao.create(item2, internalCallContext);

        LocalDate upToDate;
        Collection<Invoice> invoices;

        upToDate = new LocalDate(2011, 1, 1);
        invoices = invoiceDao.getUnpaidInvoicesByAccountId(accountId, upToDate, internalCallContext);
        assertEquals(invoices.size(), 0);

        upToDate = new LocalDate(2012, 1, 1);
        invoices = invoiceDao.getUnpaidInvoicesByAccountId(accountId, upToDate, internalCallContext);
        assertEquals(invoices.size(), 1);

        final LocalDate targetDate2 = new LocalDate(2011, 7, 1);
        final Invoice invoice2 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate2, Currency.USD);
        invoiceDao.create(invoice2, invoice2.getTargetDate().getDayOfMonth(), true, internalCallContext);

        final LocalDate startDate2 = new LocalDate(2011, 6, 1);
        final LocalDate endDate2 = startDate2.plusMonths(3);

        final BigDecimal rate3 = new BigDecimal("21.0");

        final RecurringInvoiceItem item3 = new RecurringInvoiceItem(invoice2.getId(), accountId, bundleId, UUID.randomUUID(), "test plan", "test phase C", startDate2, endDate2,
                                                                    rate3, rate3, Currency.USD);
        invoiceItemSqlDao.create(item3, internalCallContext);

        upToDate = new LocalDate(2011, 1, 1);
        invoices = invoiceDao.getUnpaidInvoicesByAccountId(accountId, upToDate, internalCallContext);
        assertEquals(invoices.size(), 0);

        upToDate = new LocalDate(2012, 1, 1);
        invoices = invoiceDao.getUnpaidInvoicesByAccountId(accountId, upToDate, internalCallContext);
        assertEquals(invoices.size(), 2);
    }

    /*
     *
     * this test verifies that immediate changes give the correct results
     *
     */
    @Test(groups = "slow")
    public void testInvoiceGenerationForImmediateChanges() throws InvoiceApiException, CatalogApiException {
        final UUID accountId = UUID.randomUUID();
        final List<Invoice> invoiceList = new ArrayList<Invoice>();
        final LocalDate targetDate = new LocalDate(2011, 2, 16);
        final Currency currency = Currency.USD;

        // generate first invoice
        final DefaultPrice price1 = new DefaultPrice(TEN, Currency.USD);
        final MockInternationalPrice recurringPrice = new MockInternationalPrice(price1);
        final MockPlanPhase phase1 = new MockPlanPhase(recurringPrice, null, BillingPeriod.MONTHLY, PhaseType.TRIAL);
        final MockPlan plan1 = new MockPlan(phase1);

        final Subscription subscription = getZombieSubscription();

        final DateTime effectiveDate1 = new DateTime(2011, 2, 1, 0, 0, 0);
        final BillingEvent event1 = createMockBillingEvent(null, subscription, effectiveDate1, plan1, phase1, null,
                                                           recurringPrice.getPrice(currency), currency, BillingPeriod.MONTHLY, 1, BillingModeType.IN_ADVANCE,
                                                           "testEvent1", 1L, SubscriptionTransitionType.CREATE);

        final BillingEventSet events = new MockBillingEventSet();
        events.add(event1);

        final Invoice invoice1 = generator.generateInvoice(accountId, events, invoiceList, targetDate, DateTimeZone.UTC, Currency.USD);
        assertEquals(invoice1.getBalance(), TEN);
        invoiceList.add(invoice1);

        // generate second invoice
        final DefaultPrice price2 = new DefaultPrice(TWENTY, Currency.USD);
        final MockInternationalPrice recurringPrice2 = new MockInternationalPrice(price2);
        final MockPlanPhase phase2 = new MockPlanPhase(recurringPrice, null, BillingPeriod.MONTHLY, PhaseType.TRIAL);
        final MockPlan plan2 = new MockPlan(phase2);

        final DateTime effectiveDate2 = new DateTime(2011, 2, 15, 0, 0, 0);
        final BillingEvent event2 = createMockBillingEvent(null, subscription, effectiveDate2, plan2, phase2, null,
                                                           recurringPrice2.getPrice(currency), currency, BillingPeriod.MONTHLY, 1, BillingModeType.IN_ADVANCE,
                                                           "testEvent2", 2L, SubscriptionTransitionType.CREATE);
        events.add(event2);

        // second invoice should be for one half (14/28 days) the difference between the rate plans
        // this is a temporary state, since it actually contains an adjusting item that properly belong to invoice 1
        final Invoice invoice2 = generator.generateInvoice(accountId, events, invoiceList, targetDate, DateTimeZone.UTC, Currency.USD);
        assertEquals(invoice2.getBalance(), FIVE);
        invoiceList.add(invoice2);

        invoiceDao.create(invoice1, invoice1.getTargetDate().getDayOfMonth(), true, internalCallContext);
        invoiceDao.create(invoice2, invoice2.getTargetDate().getDayOfMonth(), true, internalCallContext);

        final Invoice savedInvoice1 = invoiceDao.getById(invoice1.getId(), internalCallContext);
        assertEquals(savedInvoice1.getBalance(), ZERO);

        final Invoice savedInvoice2 = invoiceDao.getById(invoice2.getId(), internalCallContext);
        assertEquals(savedInvoice2.getBalance(), FIFTEEN);
    }

    @Test(groups = "slow")
    public void testInvoiceForFreeTrial() throws InvoiceApiException, CatalogApiException {
        final Currency currency = Currency.USD;
        final DefaultPrice price = new DefaultPrice(BigDecimal.ZERO, Currency.USD);
        final MockInternationalPrice recurringPrice = new MockInternationalPrice(price);
        final MockPlanPhase phase = new MockPlanPhase(recurringPrice, null);
        final MockPlan plan = new MockPlan(phase);

        final Subscription subscription = getZombieSubscription();
        final DateTime effectiveDate = buildDate(2011, 1, 1).toDateTimeAtStartOfDay();

        final BillingEvent event = createMockBillingEvent(null, subscription, effectiveDate, plan, phase, null,
                                                          recurringPrice.getPrice(currency), currency, BillingPeriod.MONTHLY, 15, BillingModeType.IN_ADVANCE,
                                                          "testEvent", 1L, SubscriptionTransitionType.CREATE);
        final BillingEventSet events = new MockBillingEventSet();
        events.add(event);

        final LocalDate targetDate = buildDate(2011, 1, 15);
        final Invoice invoice = generator.generateInvoice(UUID.randomUUID(), events, null, targetDate, DateTimeZone.UTC, Currency.USD);

        // expect one pro-ration item and one full-period item
        assertEquals(invoice.getNumberOfItems(), 2);
        assertEquals(invoice.getBalance().compareTo(ZERO), 0);
    }

    private Subscription getZombieSubscription() {
        final Subscription subscription = Mockito.mock(Subscription.class);
        Mockito.when(subscription.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(subscription.getBundleId()).thenReturn(UUID.randomUUID());
        return subscription;
    }

    @Test(groups = "slow")
    public void testInvoiceForFreeTrialWithRecurringDiscount() throws InvoiceApiException, CatalogApiException {
        final Currency currency = Currency.USD;

        final DefaultPrice zeroPrice = new DefaultPrice(BigDecimal.ZERO, Currency.USD);
        final MockInternationalPrice fixedPrice = new MockInternationalPrice(zeroPrice);
        final MockPlanPhase phase1 = new MockPlanPhase(null, fixedPrice);

        final BigDecimal cheapAmount = new BigDecimal("24.95");
        final DefaultPrice cheapPrice = new DefaultPrice(cheapAmount, Currency.USD);
        final MockInternationalPrice recurringPrice = new MockInternationalPrice(cheapPrice);
        final MockPlanPhase phase2 = new MockPlanPhase(recurringPrice, null);

        final MockPlan plan = new MockPlan();

        final Subscription subscription = getZombieSubscription();
        final DateTime effectiveDate1 = buildDate(2011, 1, 1).toDateTimeAtStartOfDay();

        final BillingEvent event1 = createMockBillingEvent(null, subscription, effectiveDate1, plan, phase1, fixedPrice.getPrice(currency),
                                                           null, currency, BillingPeriod.MONTHLY, 1, BillingModeType.IN_ADVANCE,
                                                           "testEvent1", 1L, SubscriptionTransitionType.CREATE);
        final BillingEventSet events = new MockBillingEventSet();
        events.add(event1);

        final UUID accountId = UUID.randomUUID();
        final Invoice invoice1 = generator.generateInvoice(accountId, events, null, new LocalDate(effectiveDate1), DateTimeZone.UTC, Currency.USD);
        assertNotNull(invoice1);
        assertEquals(invoice1.getNumberOfItems(), 1);
        assertEquals(invoice1.getBalance().compareTo(ZERO), 0);

        final List<Invoice> invoiceList = new ArrayList<Invoice>();
        invoiceList.add(invoice1);

        //invoiceDao.create(invoice1, invoice1.getTargetDate().getDayOfMonth(), internalCallContext);

        final DateTime effectiveDate2 = effectiveDate1.plusDays(30);
        final BillingEvent event2 = createMockBillingEvent(null, subscription, effectiveDate2, plan, phase2, null,
                                                           recurringPrice.getPrice(currency), currency, BillingPeriod.MONTHLY, 31, BillingModeType.IN_ADVANCE,
                                                           "testEvent2", 2L, SubscriptionTransitionType.PHASE);
        events.add(event2);

        final Invoice invoice2 = generator.generateInvoice(accountId, events, invoiceList, new LocalDate(effectiveDate2), DateTimeZone.UTC, Currency.USD);
        assertNotNull(invoice2);
        assertEquals(invoice2.getNumberOfItems(), 1);
        assertEquals(invoice2.getBalance().compareTo(cheapAmount), 0);

        invoiceList.add(invoice2);

        //invoiceDao.create(invoice2, invoice2.getTargetDate().getDayOfMonth(), internalCallContext);

        final DateTime effectiveDate3 = effectiveDate2.plusMonths(1);
        final Invoice invoice3 = generator.generateInvoice(accountId, events, invoiceList, new LocalDate(effectiveDate3), DateTimeZone.UTC, Currency.USD);
        assertNotNull(invoice3);
        assertEquals(invoice3.getNumberOfItems(), 1);
        assertEquals(invoice3.getBalance().compareTo(cheapAmount), 0);

        //invoiceDao.create(invoice3, invoice3.getTargetDate().getDayOfMonth(), internalCallContext);
    }

    @Test(groups = "slow")
    public void testInvoiceForEmptyEventSet() throws InvoiceApiException {
        final BillingEventSet events = new MockBillingEventSet();
        final Invoice invoice = generator.generateInvoice(UUID.randomUUID(), events, null, new LocalDate(), DateTimeZone.UTC, Currency.USD);
        assertNull(invoice);
    }

    @Test(groups = "slow")
    public void testMixedModeInvoicePersistence() throws InvoiceApiException, CatalogApiException {
        final Currency currency = Currency.USD;
        final DefaultPrice zeroPrice = new DefaultPrice(BigDecimal.ZERO, Currency.USD);
        final MockInternationalPrice fixedPrice = new MockInternationalPrice(zeroPrice);
        final MockPlanPhase phase1 = new MockPlanPhase(null, fixedPrice);

        final BigDecimal cheapAmount = new BigDecimal("24.95");
        final DefaultPrice cheapPrice = new DefaultPrice(cheapAmount, Currency.USD);
        final MockInternationalPrice recurringPrice = new MockInternationalPrice(cheapPrice);
        final MockPlanPhase phase2 = new MockPlanPhase(recurringPrice, null);

        final MockPlan plan = new MockPlan();

        final Subscription subscription = getZombieSubscription();
        final DateTime effectiveDate1 = buildDate(2011, 1, 1).toDateTimeAtStartOfDay();

        final BillingEvent event1 = createMockBillingEvent(null, subscription, effectiveDate1, plan, phase1,
                                                           fixedPrice.getPrice(currency), null, currency,
                                                           BillingPeriod.MONTHLY, 1, BillingModeType.IN_ADVANCE,
                                                           "testEvent1", 1L, SubscriptionTransitionType.CREATE);
        final BillingEventSet events = new MockBillingEventSet();
        events.add(event1);

        final DateTime effectiveDate2 = effectiveDate1.plusDays(30);
        final BillingEvent event2 = createMockBillingEvent(null, subscription, effectiveDate2, plan, phase2, null,
                                                           recurringPrice.getPrice(currency), currency, BillingPeriod.MONTHLY, 31, BillingModeType.IN_ADVANCE,
                                                           "testEvent2", 2L, SubscriptionTransitionType.CHANGE);
        events.add(event2);

        final Invoice invoice = generator.generateInvoice(UUID.randomUUID(), events, null, new LocalDate(effectiveDate2), DateTimeZone.UTC, Currency.USD);
        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 2);
        assertEquals(invoice.getBalance().compareTo(cheapAmount), 0);

        invoiceDao.create(invoice, invoice.getTargetDate().getDayOfMonth(), true, internalCallContext);
        final Invoice savedInvoice = invoiceDao.getById(invoice.getId(), internalCallContext);

        assertNotNull(savedInvoice);
        assertEquals(savedInvoice.getNumberOfItems(), 2);
        assertEquals(savedInvoice.getBalance().compareTo(cheapAmount), 0);
    }

    @Test(groups = "slow")
    public void testRefundedInvoiceWithInvoiceItemAdjustmentWithRepair() throws InvoiceApiException {

        final UUID accountId = UUID.randomUUID();
        final UUID subscriptionId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate startDate = new LocalDate(2010, 1, 1);

        ((ClockMock) clock).setDay(startDate);

        final LocalDate recuringStartDate = clock.getUTCNow().plusDays(30).toLocalDate();
        final LocalDate recuringEndDate = clock.getUTCNow().plusDays(30).toLocalDate();
        final LocalDate targetDate = recuringStartDate.plusDays(1);

        // FIRST CREATE INITIAL INVOICE WITH ONE RECURRING ITEM
        final Invoice invoice = new DefaultInvoice(accountId, targetDate, targetDate, Currency.USD);
        final UUID invoiceId = invoice.getId();

        final InvoiceItem invoiceItem = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, "test-plan", "test-phase-rec",
                                                                 recuringStartDate, recuringEndDate, new BigDecimal("239.00"), new BigDecimal("239.00"), Currency.USD);

        invoice.addInvoiceItem(invoiceItem);
        invoiceDao.create(invoice, invoice.getTargetDate().getDayOfMonth(), true, internalCallContext);

        ((ClockMock) clock).addDays(1);

        // SECOND CREATE THE PAYMENT
        final BigDecimal paymentAmount = new BigDecimal("239.00");
        final UUID paymentId = UUID.randomUUID();
        invoiceDao.notifyOfPayment(new DefaultInvoicePayment(InvoicePaymentType.ATTEMPT, paymentId, invoiceId, clock.getUTCNow(), paymentAmount, Currency.USD), internalCallContext);

        // AND THEN THIRD THE REFUND
        final Map<UUID, BigDecimal> invoiceItemMap = new HashMap<UUID, BigDecimal>();
        invoiceItemMap.put(invoiceItem.getId(), new BigDecimal("239.00"));
        invoiceDao.createRefund(paymentId, paymentAmount, true, invoiceItemMap, UUID.randomUUID(), internalCallContext);

        final Invoice savedInvoice = invoiceDao.getById(invoiceId, internalCallContext);
        assertNotNull(savedInvoice);
        assertEquals(savedInvoice.getInvoiceItems().size(), 2);

        final List<Invoice> invoices = new ArrayList<Invoice>();
        invoices.add(savedInvoice);

        // NOW COMPUTE A DIFFERENT ITEM TO TRIGGER REPAIR
        final BillingEventSet events = new MockBillingEventSet();
        final Subscription subscription = getZombieSubscription();

        final Plan plan = Mockito.mock(Plan.class);
        Mockito.when(plan.getName()).thenReturn("plan");

        final PlanPhase phase1 = Mockito.mock(PlanPhase.class);
        Mockito.when(phase1.getName()).thenReturn("plan-phase1");

        final PlanPhase phase2 = Mockito.mock(PlanPhase.class);
        Mockito.when(phase2.getName()).thenReturn("plan-phase2");

        final BillingEvent event1 = createMockBillingEvent(null, subscription, recuringStartDate.toDateTimeAtStartOfDay(), plan, phase1, null,
                                                           TEN, Currency.USD,
                                                           BillingPeriod.MONTHLY, 1, BillingModeType.IN_ADVANCE,
                                                           "new-event", 1L, SubscriptionTransitionType.CREATE);
        events.add(event1);
        final Invoice newInvoice = generator.generateInvoice(UUID.randomUUID(), events, invoices, targetDate, DateTimeZone.UTC, Currency.USD);
        invoiceDao.create(newInvoice, newInvoice.getTargetDate().getDayOfMonth(), true, internalCallContext);

        // VERIFY THAT WE STILL HAVE ONLY 2 ITEMS, MENAING THERE WERE NO REPAIR AND NO CBA GENERATED
        final Invoice firstInvoice = invoiceDao.getById(invoiceId, internalCallContext);
        assertNotNull(firstInvoice);
        assertEquals(firstInvoice.getInvoiceItems().size(), 2);
    }

    @Test(groups = "slow")
    public void testInvoiceNumber() throws InvoiceApiException {
        final Currency currency = Currency.USD;
        final DateTime targetDate1 = clock.getUTCNow().plusMonths(1);
        final DateTime targetDate2 = clock.getUTCNow().plusMonths(2);

        final Subscription subscription = getZombieSubscription();

        final Plan plan = Mockito.mock(Plan.class);
        Mockito.when(plan.getName()).thenReturn("plan");

        final PlanPhase phase1 = Mockito.mock(PlanPhase.class);
        Mockito.when(phase1.getName()).thenReturn("plan-phase1");

        final PlanPhase phase2 = Mockito.mock(PlanPhase.class);
        Mockito.when(phase2.getName()).thenReturn("plan-phase2");

        final BillingEventSet events = new MockBillingEventSet();
        final List<Invoice> invoices = new ArrayList<Invoice>();

        final BillingEvent event1 = createMockBillingEvent(null, subscription, targetDate1, plan, phase1, null,
                                                           TEN, currency,
                                                           BillingPeriod.MONTHLY, 31, BillingModeType.IN_ADVANCE,
                                                           "testEvent1", 1L, SubscriptionTransitionType.CHANGE);
        events.add(event1);

        Invoice invoice1 = generator.generateInvoice(UUID.randomUUID(), events, invoices, new LocalDate(targetDate1), DateTimeZone.UTC, Currency.USD);
        invoices.add(invoice1);
        invoiceDao.create(invoice1, invoice1.getTargetDate().getDayOfMonth(), true, internalCallContext);
        invoice1 = invoiceDao.getById(invoice1.getId(), internalCallContext);
        assertNotNull(invoice1.getInvoiceNumber());

        final BillingEvent event2 = createMockBillingEvent(null, subscription, targetDate1, plan, phase2, null,
                                                           TWENTY, currency,
                                                           BillingPeriod.MONTHLY, 31, BillingModeType.IN_ADVANCE,
                                                           "testEvent2", 2L, SubscriptionTransitionType.CHANGE);
        events.add(event2);
        Invoice invoice2 = generator.generateInvoice(UUID.randomUUID(), events, invoices, new LocalDate(targetDate2), DateTimeZone.UTC, Currency.USD);
        invoiceDao.create(invoice2, invoice2.getTargetDate().getDayOfMonth(), true, internalCallContext);
        invoice2 = invoiceDao.getById(invoice2.getId(), internalCallContext);
        assertNotNull(invoice2.getInvoiceNumber());
    }

    @Test(groups = "slow")
    public void testDeleteCBANotConsumed() throws Exception {
        final UUID accountId = UUID.randomUUID();

        // Create invoice 1
        // Scenario: single item with payment
        // * $10 item
        // Then, a repair occur:
        // * $-10 repair
        // * $10 generated CBA due to the repair (assume previous payment)
        final Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCToday(), clock.getUTCToday(), Currency.USD);
        final InvoiceItem fixedItem1 = new FixedPriceInvoiceItem(invoice1.getId(), invoice1.getAccountId(), null, null, UUID.randomUUID().toString(),
                                                                 UUID.randomUUID().toString(), clock.getUTCToday(), BigDecimal.TEN, Currency.USD);
        final RepairAdjInvoiceItem repairAdjInvoiceItem = new RepairAdjInvoiceItem(fixedItem1.getInvoiceId(), fixedItem1.getAccountId(),
                                                                                   fixedItem1.getStartDate(), fixedItem1.getEndDate(),
                                                                                   fixedItem1.getAmount().negate(), fixedItem1.getCurrency(),
                                                                                   fixedItem1.getId());
        final CreditBalanceAdjInvoiceItem creditBalanceAdjInvoiceItem1 = new CreditBalanceAdjInvoiceItem(fixedItem1.getInvoiceId(), fixedItem1.getAccountId(),
                                                                                                         fixedItem1.getStartDate(), fixedItem1.getAmount(),
                                                                                                         fixedItem1.getCurrency());
        invoiceDao.create(invoice1, invoice1.getTargetDate().getDayOfMonth(), true, internalCallContext);
        invoiceItemSqlDao.create(fixedItem1, internalCallContext);
        invoiceItemSqlDao.create(repairAdjInvoiceItem, internalCallContext);
        invoiceItemSqlDao.create(creditBalanceAdjInvoiceItem1, internalCallContext);

        // Verify scenario - no CBA should have been used
        Assert.assertEquals(invoiceDao.getAccountCBA(accountId, internalCallContext).doubleValue(), 10.00);
        verifyInvoice(invoice1.getId(), 10.00, 10.00);

        // Delete the CBA on invoice 1
        invoiceDao.deleteCBA(accountId, invoice1.getId(), creditBalanceAdjInvoiceItem1.getId(), internalCallContext);

        // Verify the result
        Assert.assertEquals(invoiceDao.getAccountCBA(accountId, internalCallContext).doubleValue(), 0.00);
        verifyInvoice(invoice1.getId(), 0.00, 0.00);
    }

    @Test(groups = "slow")
    public void testDeleteCBAPartiallyConsumed() throws Exception {
        final UUID accountId = UUID.randomUUID();

        // Create invoice 1
        // Scenario: single item with payment
        // * $10 item
        // Then, a repair occur:
        // * $-10 repair
        // * $10 generated CBA due to the repair (assume previous payment)
        final Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCToday(), clock.getUTCToday(), Currency.USD);
        final InvoiceItem fixedItem1 = new FixedPriceInvoiceItem(invoice1.getId(), invoice1.getAccountId(), null, null, UUID.randomUUID().toString(),
                                                                 UUID.randomUUID().toString(), clock.getUTCToday(), BigDecimal.TEN, Currency.USD);
        final RepairAdjInvoiceItem repairAdjInvoiceItem = new RepairAdjInvoiceItem(fixedItem1.getInvoiceId(), fixedItem1.getAccountId(),
                                                                                   fixedItem1.getStartDate(), fixedItem1.getEndDate(),
                                                                                   fixedItem1.getAmount().negate(), fixedItem1.getCurrency(),
                                                                                   fixedItem1.getId());
        final CreditBalanceAdjInvoiceItem creditBalanceAdjInvoiceItem1 = new CreditBalanceAdjInvoiceItem(fixedItem1.getInvoiceId(), fixedItem1.getAccountId(),
                                                                                                         fixedItem1.getStartDate(), fixedItem1.getAmount(),
                                                                                                         fixedItem1.getCurrency());
        invoiceDao.create(invoice1, invoice1.getTargetDate().getDayOfMonth(), true, internalCallContext);
        invoiceItemSqlDao.create(fixedItem1, internalCallContext);
        invoiceItemSqlDao.create(repairAdjInvoiceItem, internalCallContext);
        invoiceItemSqlDao.create(creditBalanceAdjInvoiceItem1, internalCallContext);

        // Create invoice 2
        // Scenario: single item
        // * $5 item
        // * $-5 CBA used
        final DefaultInvoice invoice2 = new DefaultInvoice(accountId, clock.getUTCToday(), clock.getUTCToday(), Currency.USD);
        final InvoiceItem fixedItem2 = new FixedPriceInvoiceItem(invoice2.getId(), invoice1.getAccountId(), null, null, UUID.randomUUID().toString(),
                                                                 UUID.randomUUID().toString(), clock.getUTCToday(), new BigDecimal("5"), Currency.USD);
        final CreditBalanceAdjInvoiceItem creditBalanceAdjInvoiceItem2 = new CreditBalanceAdjInvoiceItem(fixedItem2.getInvoiceId(), fixedItem2.getAccountId(),
                                                                                                         fixedItem2.getStartDate(), fixedItem2.getAmount().negate(),
                                                                                                         fixedItem2.getCurrency());
        invoiceDao.create(invoice2, invoice2.getTargetDate().getDayOfMonth(), true, internalCallContext);
        invoiceItemSqlDao.create(fixedItem2, internalCallContext);
        invoiceItemSqlDao.create(creditBalanceAdjInvoiceItem2, internalCallContext);

        // Verify scenario - half of the CBA should have been used
        Assert.assertEquals(invoiceDao.getAccountCBA(accountId, internalCallContext).doubleValue(), 5.00);
        verifyInvoice(invoice1.getId(), 10.00, 10.00);
        verifyInvoice(invoice2.getId(), 0.00, -5.00);

        // Delete the CBA on invoice 1
        invoiceDao.deleteCBA(accountId, invoice1.getId(), creditBalanceAdjInvoiceItem1.getId(), internalCallContext);

        // Verify all three invoices were affected
        Assert.assertEquals(invoiceDao.getAccountCBA(accountId, internalCallContext).doubleValue(), 0.00);
        verifyInvoice(invoice1.getId(), 0.00, 0.00);
        verifyInvoice(invoice2.getId(), 5.00, 0.00);
    }

    @Test(groups = "slow")
    public void testDeleteCBAFullyConsumedTwice() throws Exception {
        final UUID accountId = UUID.randomUUID();

        // Create invoice 1
        // Scenario: single item with payment
        // * $10 item
        // Then, a repair occur:
        // * $-10 repair
        // * $10 generated CBA due to the repair (assume previous payment)
        final Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCToday(), clock.getUTCToday(), Currency.USD);
        final InvoiceItem fixedItem1 = new FixedPriceInvoiceItem(invoice1.getId(), invoice1.getAccountId(), null, null, UUID.randomUUID().toString(),
                                                                 UUID.randomUUID().toString(), clock.getUTCToday(), BigDecimal.TEN, Currency.USD);
        final RepairAdjInvoiceItem repairAdjInvoiceItem = new RepairAdjInvoiceItem(fixedItem1.getInvoiceId(), fixedItem1.getAccountId(),
                                                                                   fixedItem1.getStartDate(), fixedItem1.getEndDate(),
                                                                                   fixedItem1.getAmount().negate(), fixedItem1.getCurrency(),
                                                                                   fixedItem1.getId());
        final CreditBalanceAdjInvoiceItem creditBalanceAdjInvoiceItem1 = new CreditBalanceAdjInvoiceItem(fixedItem1.getInvoiceId(), fixedItem1.getAccountId(),
                                                                                                         fixedItem1.getStartDate(), fixedItem1.getAmount(),
                                                                                                         fixedItem1.getCurrency());
        invoiceDao.create(invoice1, invoice1.getTargetDate().getDayOfMonth(), true, internalCallContext);
        invoiceItemSqlDao.create(fixedItem1, internalCallContext);
        invoiceItemSqlDao.create(repairAdjInvoiceItem, internalCallContext);
        invoiceItemSqlDao.create(creditBalanceAdjInvoiceItem1, internalCallContext);

        // Create invoice 2
        // Scenario: single item
        // * $5 item
        // * $-5 CBA used
        final DefaultInvoice invoice2 = new DefaultInvoice(accountId, clock.getUTCToday(), clock.getUTCToday(), Currency.USD);
        final InvoiceItem fixedItem2 = new FixedPriceInvoiceItem(invoice2.getId(), invoice1.getAccountId(), null, null, UUID.randomUUID().toString(),
                                                                 UUID.randomUUID().toString(), clock.getUTCToday(), new BigDecimal("5"), Currency.USD);
        final CreditBalanceAdjInvoiceItem creditBalanceAdjInvoiceItem2 = new CreditBalanceAdjInvoiceItem(fixedItem2.getInvoiceId(), fixedItem2.getAccountId(),
                                                                                                         fixedItem2.getStartDate(), fixedItem2.getAmount().negate(),
                                                                                                         fixedItem2.getCurrency());
        invoiceDao.create(invoice2, invoice2.getTargetDate().getDayOfMonth(), true, internalCallContext);
        invoiceItemSqlDao.create(fixedItem2, internalCallContext);
        invoiceItemSqlDao.create(creditBalanceAdjInvoiceItem2, internalCallContext);

        // Create invoice 3
        // Scenario: single item
        // * $5 item
        // * $-5 CBA used
        final DefaultInvoice invoice3 = new DefaultInvoice(accountId, clock.getUTCToday(), clock.getUTCToday(), Currency.USD);
        final InvoiceItem fixedItem3 = new FixedPriceInvoiceItem(invoice3.getId(), invoice1.getAccountId(), null, null, UUID.randomUUID().toString(),
                                                                 UUID.randomUUID().toString(), clock.getUTCToday(), new BigDecimal("5"), Currency.USD);
        final CreditBalanceAdjInvoiceItem creditBalanceAdjInvoiceItem3 = new CreditBalanceAdjInvoiceItem(fixedItem3.getInvoiceId(), fixedItem3.getAccountId(),
                                                                                                         fixedItem3.getStartDate(), fixedItem3.getAmount().negate(),
                                                                                                         fixedItem3.getCurrency());
        invoiceDao.create(invoice3, invoice3.getTargetDate().getDayOfMonth(), true, internalCallContext);
        invoiceItemSqlDao.create(fixedItem3, internalCallContext);
        invoiceItemSqlDao.create(creditBalanceAdjInvoiceItem3, internalCallContext);

        // Verify scenario - all CBA should have been used
        Assert.assertEquals(invoiceDao.getAccountCBA(accountId, internalCallContext).doubleValue(), 0.00);
        verifyInvoice(invoice1.getId(), 10.00, 10.00);
        verifyInvoice(invoice2.getId(), 0.00, -5.00);
        verifyInvoice(invoice3.getId(), 0.00, -5.00);

        // Delete the CBA on invoice 1
        invoiceDao.deleteCBA(accountId, invoice1.getId(), creditBalanceAdjInvoiceItem1.getId(), internalCallContext);

        // Verify all three invoices were affected
        Assert.assertEquals(invoiceDao.getAccountCBA(accountId, internalCallContext).doubleValue(), 0.00);
        verifyInvoice(invoice1.getId(), 0.00, 0.00);
        verifyInvoice(invoice2.getId(), 5.00, 0.00);
        verifyInvoice(invoice3.getId(), 5.00, 0.00);
    }

    @Test(groups = "slow")
    public void testCantDeleteCBAIfInvoiceBalanceBecomesNegative() throws Exception {
        final UUID accountId = UUID.randomUUID();

        // Create invoice 1
        // Scenario:
        // * $-10 repair
        // * $10 generated CBA
        final Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCToday(), clock.getUTCToday(), Currency.USD);
        final RepairAdjInvoiceItem repairAdjInvoiceItem = new RepairAdjInvoiceItem(invoice1.getId(), invoice1.getAccountId(),
                                                                                   invoice1.getInvoiceDate(), invoice1.getInvoiceDate(),
                                                                                   BigDecimal.TEN.negate(), invoice1.getCurrency(),
                                                                                   UUID.randomUUID());
        final CreditBalanceAdjInvoiceItem creditBalanceAdjInvoiceItem1 = new CreditBalanceAdjInvoiceItem(invoice1.getId(), invoice1.getAccountId(),
                                                                                                         invoice1.getInvoiceDate(), repairAdjInvoiceItem.getAmount().negate(),
                                                                                                         invoice1.getCurrency());
        invoiceDao.create(invoice1, invoice1.getTargetDate().getDayOfMonth(), true, internalCallContext);
        invoiceItemSqlDao.create(repairAdjInvoiceItem, internalCallContext);
        invoiceItemSqlDao.create(creditBalanceAdjInvoiceItem1, internalCallContext);

        // Verify scenario
        Assert.assertEquals(invoiceDao.getAccountCBA(accountId, internalCallContext).doubleValue(), 10.00);
        verifyInvoice(invoice1.getId(), 0.00, 10.00);

        // Delete the CBA on invoice 1
        try {
            invoiceDao.deleteCBA(accountId, invoice1.getId(), creditBalanceAdjInvoiceItem1.getId(), internalCallContext);
            Assert.fail();
        } catch (TransactionFailedException e) {
            Assert.assertTrue(e.getCause() instanceof InvoiceApiException);
            Assert.assertEquals(((InvoiceApiException) e.getCause()).getCode(), ErrorCode.INVOICE_WOULD_BE_NEGATIVE.getCode());
        }

        // Verify the result
        Assert.assertEquals(invoiceDao.getAccountCBA(accountId, internalCallContext).doubleValue(), 10.00);
        verifyInvoice(invoice1.getId(), 0.00, 10.00);
    }

    private void verifyInvoice(final UUID invoiceId, final double balance, final double cbaAmount) throws InvoiceApiException {
        final Invoice invoice = invoiceDao.getById(invoiceId, internalCallContext);
        Assert.assertEquals(invoice.getBalance().doubleValue(), balance);
        Assert.assertEquals(invoice.getCBAAmount().doubleValue(), cbaAmount);
    }
}
