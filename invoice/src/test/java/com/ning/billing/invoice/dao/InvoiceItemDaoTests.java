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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.model.CreditBalanceAdjInvoiceItem;
import com.ning.billing.invoice.model.DefaultInvoice;
import com.ning.billing.invoice.model.FixedPriceInvoiceItem;
import com.ning.billing.invoice.model.RecurringInvoiceItem;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

@Test(groups = {"slow", "invoicing", "invoicing-invoiceDao"})
public class InvoiceItemDaoTests extends InvoiceDaoTestBase {



    @Test
    public void testInvoiceItemCreation() {
        final UUID accountId = UUID.randomUUID();
        final UUID invoiceId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final UUID subscriptionId = UUID.randomUUID();
        final DateTime startDate = new DateTime(2011, 10, 1, 0, 0, 0, 0);
        final DateTime endDate = new DateTime(2011, 11, 1, 0, 0, 0, 0);
        final BigDecimal rate = new BigDecimal("20.00");

        final RecurringInvoiceItem item = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, "test plan", "test phase", startDate, endDate,
                                                                   rate, rate, Currency.USD);
        invoiceItemSqlDao.create(item, context);

        final RecurringInvoiceItem thisItem = (RecurringInvoiceItem) invoiceItemSqlDao.getById(item.getId().toString());
        assertNotNull(thisItem);
        assertEquals(thisItem.getId(), item.getId());
        assertEquals(thisItem.getInvoiceId(), item.getInvoiceId());
        assertEquals(thisItem.getSubscriptionId(), item.getSubscriptionId());
        assertTrue(thisItem.getStartDate().compareTo(item.getStartDate()) == 0);
        assertTrue(thisItem.getEndDate().compareTo(item.getEndDate()) == 0);
        assertEquals(thisItem.getAmount().compareTo(item.getRate()), 0);
        assertEquals(thisItem.getRate().compareTo(item.getRate()), 0);
        assertEquals(thisItem.getCurrency(), item.getCurrency());
        // created date is no longer set before persistence layer call
        // assertEquals(thisItem.getCreatedDate().compareTo(item.getCreatedDate()), 0);
    }

    @Test
    public void testGetInvoiceItemsBySubscriptionId() {
        final UUID accountId = UUID.randomUUID();
        final UUID subscriptionId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final DateTime startDate = new DateTime(2011, 3, 1, 0, 0, 0, 0);
        final BigDecimal rate = new BigDecimal("20.00");

        for (int i = 0; i < 3; i++) {
            final UUID invoiceId = UUID.randomUUID();

            final RecurringInvoiceItem item = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId,
                                                                       "test plan", "test phase", startDate.plusMonths(i), startDate.plusMonths(i + 1),
                                                                       rate, rate, Currency.USD);
            invoiceItemSqlDao.create(item, context);
        }

        final List<InvoiceItem> items = invoiceItemSqlDao.getInvoiceItemsBySubscription(subscriptionId.toString());
        assertEquals(items.size(), 3);
    }

    @Test
    public void testGetInvoiceItemsByInvoiceId() {
        final UUID accountId = UUID.randomUUID();
        final UUID invoiceId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final DateTime startDate = new DateTime(2011, 3, 1, 0, 0, 0, 0);
        final BigDecimal rate = new BigDecimal("20.00");

        for (int i = 0; i < 5; i++) {
            final UUID subscriptionId = UUID.randomUUID();
            final BigDecimal amount = rate.multiply(new BigDecimal(i + 1));

            final RecurringInvoiceItem item = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId,
                                                                       "test plan", "test phase", startDate, startDate.plusMonths(1),
                                                                       amount, amount, Currency.USD);
            invoiceItemSqlDao.create(item, context);
        }

        final List<InvoiceItem> items = invoiceItemSqlDao.getInvoiceItemsByInvoice(invoiceId.toString());
        assertEquals(items.size(), 5);
    }

    @Test
    public void testGetInvoiceItemsByAccountId() {
        final UUID accountId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final DateTime targetDate = new DateTime(2011, 5, 23, 0, 0, 0, 0);
        final DefaultInvoice invoice = new DefaultInvoice(accountId, clock.getUTCNow(), targetDate, Currency.USD);

        invoiceDao.create(invoice, context);

        final UUID invoiceId = invoice.getId();
        final DateTime startDate = new DateTime(2011, 3, 1, 0, 0, 0, 0);
        final BigDecimal rate = new BigDecimal("20.00");

        final UUID subscriptionId = UUID.randomUUID();

        final RecurringInvoiceItem item = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId,
                                                                   "test plan", "test phase", startDate, startDate.plusMonths(1),
                                                                   rate, rate, Currency.USD);
        invoiceItemSqlDao.create(item, context);

        final List<InvoiceItem> items = invoiceItemSqlDao.getInvoiceItemsByAccount(accountId.toString());
        assertEquals(items.size(), 1);
    }

    @Test
    public void testCreditBalanceInvoiceSqlDao() {
        final UUID invoiceId = UUID.randomUUID();
        final UUID accountId = UUID.randomUUID();
        final DateTime creditDate = new DateTime(2012, 4, 1, 0, 10, 22, 0);

        final InvoiceItem creditInvoiceItem = new CreditBalanceAdjInvoiceItem(invoiceId, accountId, creditDate, TEN, Currency.USD);
        invoiceItemSqlDao.create(creditInvoiceItem, context);

        final InvoiceItem savedItem = invoiceItemSqlDao.getById(creditInvoiceItem.getId().toString());
        assertEquals(savedItem, creditInvoiceItem);
    }

    @Test
    public void testFixedPriceInvoiceSqlDao() {
        final UUID invoiceId = UUID.randomUUID();
        final UUID accountId = UUID.randomUUID();
        final DateTime startDate = new DateTime(2012, 4, 1, 0, 10, 22, 0);

        final InvoiceItem fixedPriceInvoiceItem = new FixedPriceInvoiceItem(invoiceId, accountId, UUID.randomUUID(),
                                                                            UUID.randomUUID(), "test plan", "test phase", startDate, startDate.plusMonths(1), TEN, Currency.USD);
        invoiceItemSqlDao.create(fixedPriceInvoiceItem, context);

        final InvoiceItem savedItem = invoiceItemSqlDao.getById(fixedPriceInvoiceItem.getId().toString());
        assertEquals(savedItem, fixedPriceInvoiceItem);
    }
}
