/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.analytics.dao;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.IDBI;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.analytics.TestWithEmbeddedDB;
import com.ning.billing.analytics.model.BusinessInvoice;
import com.ning.billing.catalog.api.Currency;

public class TestBusinessInvoiceSqlDao extends TestWithEmbeddedDB {
    private BusinessInvoiceSqlDao invoiceSqlDao;

    @BeforeMethod(groups = "slow")
    public void setUp() throws Exception {
        final IDBI dbi = helper.getDBI();
        invoiceSqlDao = dbi.onDemand(BusinessInvoiceSqlDao.class);
    }

    @Test(groups = "slow")
    public void testCRUD() throws Exception {
        final UUID invoiceId = UUID.randomUUID();
        final String accountKey = UUID.randomUUID().toString();
        final BusinessInvoice invoice = createInvoice(invoiceId, accountKey);

        // Verify initial state
        Assert.assertNull(invoiceSqlDao.getInvoice(invoice.getInvoiceId().toString()));
        Assert.assertEquals(invoiceSqlDao.deleteInvoice(invoice.getInvoiceId().toString()), 0);

        // Add the invoice
        Assert.assertEquals(invoiceSqlDao.createInvoice(invoice), 1);

        // Retrieve it
        Assert.assertEquals(invoiceSqlDao.getInvoice(invoice.getInvoiceId().toString()), invoice);
        Assert.assertEquals(invoiceSqlDao.getInvoicesForAccount(invoice.getAccountKey()).size(), 1);
        Assert.assertEquals(invoiceSqlDao.getInvoicesForAccount(invoice.getAccountKey()).get(0), invoice);

        // Update and retrieve it
        invoice.setInvoiceDate(new DateTime(DateTimeZone.UTC));
        Assert.assertEquals(invoiceSqlDao.updateInvoice(invoice), 1);
        Assert.assertEquals(invoiceSqlDao.getInvoice(invoice.getInvoiceId().toString()), invoice);
        Assert.assertEquals(invoiceSqlDao.getInvoicesForAccount(invoice.getAccountKey()).size(), 1);
        Assert.assertEquals(invoiceSqlDao.getInvoicesForAccount(invoice.getAccountKey()).get(0), invoice);

        // Delete it
        Assert.assertEquals(invoiceSqlDao.deleteInvoice(invoice.getInvoiceId().toString()), 1);
        Assert.assertNull(invoiceSqlDao.getInvoice(invoice.getInvoiceId().toString()));
        Assert.assertEquals(invoiceSqlDao.getInvoicesForAccount(invoice.getAccountKey()).size(), 0);
    }

    @Test(groups = "slow")
    public void testSegmentation() throws Exception {
        final UUID invoiceId1 = UUID.randomUUID();
        final String accountKey1 = UUID.randomUUID().toString();
        final BusinessInvoice invoice1 = createInvoice(invoiceId1, accountKey1);
        final UUID invoiceId2 = UUID.randomUUID();
        final String accountKey2 = UUID.randomUUID().toString();
        final BusinessInvoice invoice2 = createInvoice(invoiceId2, accountKey2);

        // Create both invoices
        Assert.assertEquals(invoiceSqlDao.createInvoice(invoice1), 1);
        Assert.assertEquals(invoiceSqlDao.createInvoice(invoice2), 1);

        Assert.assertEquals(invoiceSqlDao.getInvoicesForAccount(accountKey1).size(), 1);
        Assert.assertEquals(invoiceSqlDao.getInvoicesForAccount(accountKey2).size(), 1);

        // Remove the first invoice
        Assert.assertEquals(invoiceSqlDao.deleteInvoice(invoice1.getInvoiceId().toString()), 1);

        Assert.assertEquals(invoiceSqlDao.getInvoicesForAccount(accountKey1).size(), 0);
        Assert.assertEquals(invoiceSqlDao.getInvoicesForAccount(accountKey2).size(), 1);
    }

    @Test(groups = "slow")
    public void testHealthCheck() throws Exception {
        // HealthCheck test to make sure MySQL is setup properly
        try {
            invoiceSqlDao.test();
        } catch (Throwable t) {
            Assert.fail(t.toString());
        }
    }

    private BusinessInvoice createInvoice(final UUID invoiceId, final String accountKey) {
        final BigDecimal amountCharged = BigDecimal.ZERO;
        final BigDecimal amountCredited = BigDecimal.ONE;
        final BigDecimal amountPaid = BigDecimal.TEN;
        final BigDecimal balance = BigDecimal.valueOf(123L);
        final DateTime createdDate = new DateTime(DateTimeZone.UTC);
        final Currency currency = Currency.MXN;
        final DateTime invoiceDate = new DateTime(DateTimeZone.UTC);
        final DateTime targetDate = new DateTime(DateTimeZone.UTC);
        final DateTime updatedDate = new DateTime(DateTimeZone.UTC);

        return new BusinessInvoice(accountKey, amountCharged, amountCredited, amountPaid, balance,
                                   createdDate, currency, invoiceDate, invoiceId, targetDate, updatedDate);
    }
}
