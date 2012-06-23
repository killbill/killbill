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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.IDBI;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.analytics.TestWithEmbeddedDB;
import com.ning.billing.analytics.model.BusinessAccount;
import com.ning.billing.analytics.model.BusinessInvoice;
import com.ning.billing.analytics.model.BusinessInvoiceItem;
import com.ning.billing.catalog.api.Currency;

public class TestDefaultAnalyticsDao extends TestWithEmbeddedDB {
    private BusinessAccountSqlDao accountSqlDao;
    private BusinessSubscriptionTransitionSqlDao subscriptionTransitionSqlDao;
    private BusinessInvoiceSqlDao invoiceSqlDao;
    private BusinessInvoiceItemSqlDao invoiceItemSqlDao;
    private AnalyticsDao analyticsDao;

    @BeforeMethod(groups = "slow")
    public void setUp() throws Exception {
        final IDBI dbi = helper.getDBI();
        accountSqlDao = dbi.onDemand(BusinessAccountSqlDao.class);
        subscriptionTransitionSqlDao = dbi.onDemand(BusinessSubscriptionTransitionSqlDao.class);
        invoiceSqlDao = dbi.onDemand(BusinessInvoiceSqlDao.class);
        invoiceItemSqlDao = dbi.onDemand(BusinessInvoiceItemSqlDao.class);
        analyticsDao = new DefaultAnalyticsDao(accountSqlDao, subscriptionTransitionSqlDao, invoiceSqlDao, invoiceItemSqlDao);
    }

    @Test(groups = "slow")
    public void testCreateInvoice() throws Exception {
        // Create and verify the initial state
        BusinessAccount account = new BusinessAccount(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                                                      BigDecimal.ONE, new DateTime(DateTimeZone.UTC), BigDecimal.TEN,
                                                      "ERROR_NOT_ENOUGH_FUNDS", "CreditCard", "Visa", "FRANCE");
        Assert.assertEquals(accountSqlDao.createAccount(account), 1);
        Assert.assertEquals(invoiceSqlDao.getInvoicesForAccount(account.getKey()).size(), 0);
        account = accountSqlDao.getAccount(account.getKey());

        // Generate the invoices
        final BusinessInvoice invoice = createInvoice(account.getKey());
        final List<BusinessInvoiceItem> invoiceItems = new ArrayList<BusinessInvoiceItem>();
        for (int i = 0; i < 10; i++) {
            invoiceItems.add(createInvoiceItem(invoice.getInvoiceId(), BigDecimal.valueOf(1242 + i)));
        }
        analyticsDao.createInvoice(account.getKey(), invoice, invoiceItems);

        // Verify the final state
        final List<BusinessInvoice> invoicesForAccount = invoiceSqlDao.getInvoicesForAccount(account.getKey());
        Assert.assertEquals(invoicesForAccount.size(), 1);
        Assert.assertEquals(invoicesForAccount.get(0).getInvoiceId(), invoice.getInvoiceId());

        Assert.assertEquals(invoiceItemSqlDao.getInvoiceItemsForInvoice(invoice.getInvoiceId().toString()).size(), 10);

        final BusinessAccount finalAccount = accountSqlDao.getAccount(account.getKey());
        Assert.assertEquals(finalAccount.getCreatedDt(), account.getCreatedDt());
        Assert.assertTrue(finalAccount.getUpdatedDt().isAfter(account.getCreatedDt()));
        Assert.assertTrue(finalAccount.getUpdatedDt().isAfter(account.getUpdatedDt()));
        Assert.assertTrue(finalAccount.getLastInvoiceDate().equals(invoice.getInvoiceDate()));
        // invoice.getBalance() is not the sum of all the items here - but in practice it will be
        Assert.assertEquals(finalAccount.getTotalInvoiceBalance(), account.getTotalInvoiceBalance().add(invoice.getBalance()));
    }

    private BusinessInvoice createInvoice(final String accountKey) {
        final BigDecimal amountCharged = BigDecimal.ZERO;
        final BigDecimal amountCredited = BigDecimal.ONE;
        final BigDecimal amountPaid = BigDecimal.TEN;
        final BigDecimal balance = BigDecimal.valueOf(123L);
        final DateTime createdDate = new DateTime(DateTimeZone.UTC);
        final Currency currency = Currency.MXN;
        final DateTime invoiceDate = new DateTime(DateTimeZone.UTC);
        final UUID invoiceId = UUID.randomUUID();
        final DateTime targetDate = new DateTime(DateTimeZone.UTC);
        final DateTime updatedDate = new DateTime(DateTimeZone.UTC);

        return new BusinessInvoice(accountKey, amountCharged, amountCredited, amountPaid, balance,
                                   createdDate, currency, invoiceDate, invoiceId, targetDate, updatedDate);
    }

    private BusinessInvoiceItem createInvoiceItem(final UUID invoiceId, final BigDecimal amount) {
        final String billingPeriod = UUID.randomUUID().toString().substring(0, 20);
        final DateTime createdDate = new DateTime(DateTimeZone.UTC);
        final Currency currency = Currency.AUD;
        final DateTime endDate = new DateTime(DateTimeZone.UTC);
        final String externalKey = UUID.randomUUID().toString();
        final UUID itemId = UUID.randomUUID();
        final String itemType = UUID.randomUUID().toString().substring(0, 20);
        final String phase = UUID.randomUUID().toString().substring(0, 20);
        final String productCategory = UUID.randomUUID().toString().substring(0, 20);
        final String productName = UUID.randomUUID().toString().substring(0, 20);
        final String productType = UUID.randomUUID().toString().substring(0, 20);
        final String slug = UUID.randomUUID().toString().substring(0, 20);
        final DateTime startDate = new DateTime(DateTimeZone.UTC);
        final DateTime updatedDate = new DateTime(DateTimeZone.UTC);

        return new BusinessInvoiceItem(amount, billingPeriod, createdDate, currency,
                                       endDate, externalKey, invoiceId, itemId, itemType,
                                       phase, productCategory, productName, productType,
                                       slug, startDate, updatedDate);
    }
}
