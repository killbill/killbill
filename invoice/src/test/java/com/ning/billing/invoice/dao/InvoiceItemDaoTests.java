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

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.glue.InvoiceModuleWithEmbeddedDb;
import com.ning.billing.invoice.model.DefaultInvoice;
import com.ning.billing.invoice.model.DefaultInvoiceItem;
import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.testng.Assert.*;

public class InvoiceItemDaoTests extends InvoiceDaoTestBase {
    @Test
    public void testInvoiceItemCreation() {
        UUID invoiceId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        DateTime startDate = new DateTime(2011, 10, 1, 0, 0, 0, 0);
        DateTime endDate = new DateTime(2011, 11, 1, 0, 0, 0, 0);
        BigDecimal rate = new BigDecimal("20.00");

        InvoiceItem item = new DefaultInvoiceItem(invoiceId, subscriptionId, startDate, endDate, "test", rate, rate, Currency.USD);
        invoiceItemDao.create(item);

        InvoiceItem thisItem = invoiceItemDao.getById(item.getId().toString());
        assertNotNull(thisItem);
        assertEquals(thisItem.getId(), item.getId());
        assertEquals(thisItem.getInvoiceId(), item.getInvoiceId());
        assertEquals(thisItem.getSubscriptionId(), item.getSubscriptionId());
        assertEquals(thisItem.getStartDate(), item.getStartDate());
        assertEquals(thisItem.getEndDate(), item.getEndDate());
        assertEquals(thisItem.getDescription(), item.getDescription());
        assertEquals(thisItem.getAmount().compareTo(item.getAmount()), 0);
        assertEquals(thisItem.getRate().compareTo(item.getRate()), 0);
        assertEquals(thisItem.getCurrency(), item.getCurrency());
    }

    @Test
    public void testGetInvoiceItemsBySubscriptionId() {
        UUID subscriptionId = UUID.randomUUID();
        DateTime startDate = new DateTime(2011, 3, 1, 0, 0, 0, 0);
        BigDecimal rate = new BigDecimal("20.00");

        for (int i = 0; i < 3; i++) {
            UUID invoiceId = UUID.randomUUID();
            DefaultInvoiceItem item = new DefaultInvoiceItem(invoiceId, subscriptionId, startDate.plusMonths(i), startDate.plusMonths(i + 1), "test", rate, rate, Currency.USD);
            invoiceItemDao.create(item);
        }

        List<InvoiceItem> items = invoiceItemDao.getInvoiceItemsBySubscription(subscriptionId.toString());
        assertEquals(items.size(), 3);
    }

    @Test
    public void testGetInvoiceItemsByInvoiceId() {
        UUID invoiceId = UUID.randomUUID();
        DateTime startDate = new DateTime(2011, 3, 1, 0, 0, 0, 0);
        BigDecimal rate = new BigDecimal("20.00");

        for (int i = 0; i < 5; i++) {
            UUID subscriptionId = UUID.randomUUID();
            BigDecimal amount = rate.multiply(new BigDecimal(i + 1));
            DefaultInvoiceItem item = new DefaultInvoiceItem(invoiceId, subscriptionId, startDate, startDate.plusMonths(1), "test", amount, amount, Currency.USD);
            invoiceItemDao.create(item);
        }

        List<InvoiceItem> items = invoiceItemDao.getInvoiceItemsByInvoice(invoiceId.toString());
        assertEquals(items.size(), 5);
    }

    @Test
    public void testGetInvoiceItemsByAccountId() {
        UUID accountId = UUID.randomUUID();
        DateTime targetDate = new DateTime(2011, 5, 23, 0, 0, 0, 0);
        DefaultInvoice invoice = new DefaultInvoice(accountId, targetDate, Currency.USD);

        invoiceDao.create(invoice);

        UUID invoiceId = invoice.getId();
        DateTime startDate = new DateTime(2011, 3, 1, 0, 0, 0, 0);
        BigDecimal rate = new BigDecimal("20.00");

        UUID subscriptionId = UUID.randomUUID();
        DefaultInvoiceItem item = new DefaultInvoiceItem(invoiceId, subscriptionId, startDate, startDate.plusMonths(1), "test", rate, rate, Currency.USD);
        invoiceItemDao.create(item);

        List<InvoiceItem> items = invoiceItemDao.getInvoiceItemsByAccount(accountId.toString());
        assertEquals(items.size(), 1);
    }
}
