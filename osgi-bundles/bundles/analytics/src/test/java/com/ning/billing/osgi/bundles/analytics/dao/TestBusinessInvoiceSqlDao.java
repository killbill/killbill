/*
 * Copyright 2010-2013 Ning, Inc.
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

package com.ning.billing.osgi.bundles.analytics.dao;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.osgi.bundles.analytics.AnalyticsTestSuiteWithEmbeddedDB;
import com.ning.billing.osgi.bundles.analytics.model.BusinessInvoiceModelDao;

public class TestBusinessInvoiceSqlDao extends AnalyticsTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testCRUD() throws Exception {
        final UUID invoiceId = UUID.randomUUID();
        final UUID accountId = UUID.randomUUID();
        final String accountKey = UUID.randomUUID().toString();
        final BusinessInvoiceModelDao invoice = createInvoice(accountId, invoiceId, accountKey);

        // Verify initial state
        Assert.assertNull(invoiceSqlDao.getInvoice(invoice.getInvoiceId().toString(), internalCallContext));
        Assert.assertEquals(invoiceSqlDao.deleteInvoice(invoice.getInvoiceId().toString(), internalCallContext), 0);

        // Add the invoice
        Assert.assertEquals(invoiceSqlDao.createInvoice(invoice, internalCallContext), 1);

        // Retrieve it
        Assert.assertEquals(invoiceSqlDao.getInvoice(invoice.getInvoiceId().toString(), internalCallContext), invoice);
        Assert.assertEquals(invoiceSqlDao.getInvoicesForAccount(invoice.getAccountId().toString(), internalCallContext).size(), 1);
        Assert.assertEquals(invoiceSqlDao.getInvoicesForAccount(invoice.getAccountId().toString(), internalCallContext).get(0), invoice);

        // Delete it
        Assert.assertEquals(invoiceSqlDao.deleteInvoice(invoice.getInvoiceId().toString(), internalCallContext), 1);
        Assert.assertNull(invoiceSqlDao.getInvoice(invoice.getInvoiceId().toString(), internalCallContext));
        Assert.assertEquals(invoiceSqlDao.getInvoicesForAccount(invoice.getAccountId().toString(), internalCallContext).size(), 0);
    }

    @Test(groups = "slow")
    public void testSegmentation() throws Exception {
        final UUID invoiceId1 = UUID.randomUUID();
        final UUID accountId1 = UUID.randomUUID();
        final String accountKey1 = UUID.randomUUID().toString();
        final BusinessInvoiceModelDao invoice1 = createInvoice(invoiceId1, accountId1, accountKey1);
        final UUID invoiceId2 = UUID.randomUUID();
        final UUID accountId2 = UUID.randomUUID();
        final String accountKey2 = UUID.randomUUID().toString();
        final BusinessInvoiceModelDao invoice2 = createInvoice(invoiceId2, accountId2, accountKey2);

        // Create both invoices
        Assert.assertEquals(invoiceSqlDao.createInvoice(invoice1, internalCallContext), 1);
        Assert.assertEquals(invoiceSqlDao.createInvoice(invoice2, internalCallContext), 1);

        Assert.assertEquals(invoiceSqlDao.getInvoicesForAccount(accountId1.toString(), internalCallContext).size(), 1);
        Assert.assertEquals(invoiceSqlDao.getInvoicesForAccount(accountId2.toString(), internalCallContext).size(), 1);

        // Remove the first invoice
        Assert.assertEquals(invoiceSqlDao.deleteInvoice(invoice1.getInvoiceId().toString(), internalCallContext), 1);

        Assert.assertEquals(invoiceSqlDao.getInvoicesForAccount(accountId1.toString(), internalCallContext).size(), 0);
        Assert.assertEquals(invoiceSqlDao.getInvoicesForAccount(accountId2.toString(), internalCallContext).size(), 1);
    }

    @Test(groups = "slow")
    public void testHealthCheck() throws Exception {
        // HealthCheck test to make sure MySQL is setup properly
        try {
            invoiceSqlDao.test(internalCallContext);
        } catch (Throwable t) {
            Assert.fail(t.toString());
        }
    }

    private BusinessInvoiceModelDao createInvoice(final UUID invoiceId, final UUID accountId, final String accountKey) {
        final BigDecimal amountCharged = BigDecimal.ZERO;
        final BigDecimal amountCredited = BigDecimal.ONE;
        final BigDecimal amountPaid = BigDecimal.TEN;
        final BigDecimal balance = BigDecimal.valueOf(123L);
        final DateTime createdDate = clock.getUTCNow();
        final Currency currency = Currency.MXN;
        final LocalDate invoiceDate = clock.getUTCToday();
        final LocalDate targetDate = clock.getUTCToday();
        final DateTime updatedDate = clock.getUTCNow();

        return new BusinessInvoiceModelDao(accountId, accountKey, amountCharged, amountCredited, amountPaid, balance,
                                           createdDate, currency, invoiceDate, invoiceId, 12, targetDate, updatedDate);
    }
}
