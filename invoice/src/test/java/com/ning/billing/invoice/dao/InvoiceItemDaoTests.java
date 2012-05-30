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
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.ning.billing.invoice.model.CreditInvoiceItem;
import com.ning.billing.invoice.model.FixedPriceInvoiceItem;
import org.joda.time.DateTime;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.model.DefaultInvoice;
import com.ning.billing.invoice.model.RecurringInvoiceItem;

@Test(groups = {"slow", "invoicing", "invoicing-invoiceDao"})
public class InvoiceItemDaoTests extends InvoiceDaoTestBase {
    @Test
    public void testInvoiceItemCreation() {
        UUID accountId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        UUID bundleId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        DateTime startDate = new DateTime(2011, 10, 1, 0, 0, 0, 0);
        DateTime endDate = new DateTime(2011, 11, 1, 0, 0, 0, 0);
        BigDecimal rate = new BigDecimal("20.00");

        RecurringInvoiceItem item = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, "test plan", "test phase", startDate, endDate,
                rate, rate, Currency.USD);
        recurringInvoiceItemDao.create(item, context);

        RecurringInvoiceItem thisItem = (RecurringInvoiceItem) recurringInvoiceItemDao.getById(item.getId().toString());
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
        UUID accountId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID bundleId = UUID.randomUUID();
        DateTime startDate = new DateTime(2011, 3, 1, 0, 0, 0, 0);
        BigDecimal rate = new BigDecimal("20.00");

        for (int i = 0; i < 3; i++) {
            UUID invoiceId = UUID.randomUUID();

            RecurringInvoiceItem item = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId,
                    "test plan", "test phase", startDate.plusMonths(i), startDate.plusMonths(i + 1),
                    rate, rate, Currency.USD);
            recurringInvoiceItemDao.create(item, context);
        }

        List<InvoiceItem> items = recurringInvoiceItemDao.getInvoiceItemsBySubscription(subscriptionId.toString());
        assertEquals(items.size(), 3);
    }

    @Test
    public void testGetInvoiceItemsByInvoiceId() {
        UUID accountId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        UUID bundleId = UUID.randomUUID();
        DateTime startDate = new DateTime(2011, 3, 1, 0, 0, 0, 0);
        BigDecimal rate = new BigDecimal("20.00");

        for (int i = 0; i < 5; i++) {
            UUID subscriptionId = UUID.randomUUID();
            BigDecimal amount = rate.multiply(new BigDecimal(i + 1));

            RecurringInvoiceItem item = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId,
                    "test plan", "test phase", startDate, startDate.plusMonths(1),
                    amount, amount, Currency.USD);
            recurringInvoiceItemDao.create(item, context);
        }

        List<InvoiceItem> items = recurringInvoiceItemDao.getInvoiceItemsByInvoice(invoiceId.toString());
        assertEquals(items.size(), 5);
    }

    @Test
    public void testGetInvoiceItemsByAccountId() {
        UUID accountId = UUID.randomUUID();
        UUID bundleId = UUID.randomUUID();
        DateTime targetDate = new DateTime(2011, 5, 23, 0, 0, 0, 0);
        DefaultInvoice invoice = new DefaultInvoice(accountId, clock.getUTCNow(), targetDate, Currency.USD);

        invoiceDao.create(invoice, context);

        UUID invoiceId = invoice.getId();
        DateTime startDate = new DateTime(2011, 3, 1, 0, 0, 0, 0);
        BigDecimal rate = new BigDecimal("20.00");

        UUID subscriptionId = UUID.randomUUID();

        RecurringInvoiceItem item = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId,
                "test plan", "test phase", startDate, startDate.plusMonths(1),
                rate, rate, Currency.USD);
        recurringInvoiceItemDao.create(item, context);

        List<InvoiceItem> items = recurringInvoiceItemDao.getInvoiceItemsByAccount(accountId.toString());
        assertEquals(items.size(), 1);
    }

    @Test
    public void testCreditInvoiceSqlDao() {
        UUID invoiceId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        DateTime creditDate = new DateTime(2012, 4, 1, 0, 10, 22, 0);

        InvoiceItem creditInvoiceItem = new CreditInvoiceItem(invoiceId, accountId, creditDate, TEN, Currency.USD);
        creditInvoiceItemSqlDao.create(creditInvoiceItem, context);

        InvoiceItem savedItem = creditInvoiceItemSqlDao.getById(creditInvoiceItem.getId().toString());
        assertEquals(savedItem, creditInvoiceItem);
    }

    @Test
    public void testFixedPriceInvoiceSqlDao() {
        UUID invoiceId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        DateTime startDate = new DateTime(2012, 4, 1, 0, 10, 22, 0);

        InvoiceItem fixedPriceInvoiceItem = new FixedPriceInvoiceItem(invoiceId, accountId, UUID.randomUUID(),
                UUID.randomUUID(), "test plan", "test phase", startDate, startDate.plusMonths(1), TEN, Currency.USD);
        fixedPriceInvoiceItemSqlDao.create(fixedPriceInvoiceItem, context);

        InvoiceItem savedItem = fixedPriceInvoiceItemSqlDao.getById(fixedPriceInvoiceItem.getId().toString());
        assertEquals(savedItem, fixedPriceInvoiceItem);
    }
}
