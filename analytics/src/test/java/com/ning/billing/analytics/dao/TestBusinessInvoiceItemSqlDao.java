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

package com.ning.billing.analytics.dao;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.skife.jdbi.v2.IDBI;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.analytics.AnalyticsTestSuiteWithEmbeddedDB;
import com.ning.billing.analytics.model.BusinessInvoiceItemModelDao;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.DefaultClock;

public class TestBusinessInvoiceItemSqlDao extends AnalyticsTestSuiteWithEmbeddedDB {

    private final Clock clock = new DefaultClock();

    private BusinessInvoiceItemSqlDao invoiceItemSqlDao;

    @BeforeMethod(groups = "slow")
    public void setUp() throws Exception {
        final IDBI dbi = helper.getDBI();
        invoiceItemSqlDao = dbi.onDemand(BusinessInvoiceItemSqlDao.class);
    }

    @Test(groups = "slow")
    public void testCRUD() throws Exception {
        final UUID invoiceId = UUID.randomUUID();
        final String externalKey = UUID.randomUUID().toString();
        final BusinessInvoiceItemModelDao invoiceItem = createInvoiceItem(invoiceId, externalKey);

        // Verify initial state
        Assert.assertNull(invoiceItemSqlDao.getInvoiceItem(invoiceItem.getItemId().toString(), internalCallContext));
        Assert.assertEquals(invoiceItemSqlDao.deleteInvoiceItem(invoiceItem.getItemId().toString(), internalCallContext), 0);

        // Add the invoice item
        Assert.assertEquals(invoiceItemSqlDao.createInvoiceItem(invoiceItem, internalCallContext), 1);

        // Retrieve it
        Assert.assertEquals(invoiceItemSqlDao.getInvoiceItem(invoiceItem.getItemId().toString(), internalCallContext), invoiceItem);
        Assert.assertEquals(invoiceItemSqlDao.getInvoiceItemsForBundleByKey(invoiceItem.getExternalKey(), internalCallContext).size(), 1);
        Assert.assertEquals(invoiceItemSqlDao.getInvoiceItemsForBundleByKey(invoiceItem.getExternalKey(), internalCallContext).get(0), invoiceItem);
        Assert.assertEquals(invoiceItemSqlDao.getInvoiceItemsForInvoice(invoiceItem.getInvoiceId().toString(), internalCallContext).size(), 1);
        Assert.assertEquals(invoiceItemSqlDao.getInvoiceItemsForInvoice(invoiceItem.getInvoiceId().toString(), internalCallContext).get(0), invoiceItem);

        // Delete it
        Assert.assertEquals(invoiceItemSqlDao.deleteInvoiceItem(invoiceItem.getItemId().toString(), internalCallContext), 1);
        Assert.assertNull(invoiceItemSqlDao.getInvoiceItem(invoiceItem.getItemId().toString(), internalCallContext));
        Assert.assertEquals(invoiceItemSqlDao.getInvoiceItemsForBundleByKey(invoiceItem.getExternalKey(), internalCallContext).size(), 0);
        Assert.assertEquals(invoiceItemSqlDao.getInvoiceItemsForInvoice(invoiceItem.getInvoiceId().toString(), internalCallContext).size(), 0);
    }

    @Test(groups = "slow")
    public void testSegmentation() throws Exception {
        final UUID invoiceId1 = UUID.randomUUID();
        final String externalKey1 = UUID.randomUUID().toString();
        final BusinessInvoiceItemModelDao invoiceItem1 = createInvoiceItem(invoiceId1, externalKey1);
        final UUID invoiceId2 = UUID.randomUUID();
        final String externalKey2 = UUID.randomUUID().toString();
        final BusinessInvoiceItemModelDao invoiceItem2 = createInvoiceItem(invoiceId2, externalKey2);

        // Create both invoice items
        Assert.assertEquals(invoiceItemSqlDao.createInvoiceItem(invoiceItem1, internalCallContext), 1);
        Assert.assertEquals(invoiceItemSqlDao.createInvoiceItem(invoiceItem2, internalCallContext), 1);

        Assert.assertEquals(invoiceItemSqlDao.getInvoiceItemsForBundleByKey(externalKey1, internalCallContext).size(), 1);
        Assert.assertEquals(invoiceItemSqlDao.getInvoiceItemsForBundleByKey(externalKey2, internalCallContext).size(), 1);
        Assert.assertEquals(invoiceItemSqlDao.getInvoiceItemsForInvoice(invoiceId1.toString(), internalCallContext).size(), 1);
        Assert.assertEquals(invoiceItemSqlDao.getInvoiceItemsForInvoice(invoiceId2.toString(), internalCallContext).size(), 1);

        // Remove the first invoice item
        Assert.assertEquals(invoiceItemSqlDao.deleteInvoiceItem(invoiceItem1.getItemId().toString(), internalCallContext), 1);

        Assert.assertEquals(invoiceItemSqlDao.getInvoiceItemsForBundleByKey(externalKey1, internalCallContext).size(), 0);
        Assert.assertEquals(invoiceItemSqlDao.getInvoiceItemsForBundleByKey(externalKey2, internalCallContext).size(), 1);
        Assert.assertEquals(invoiceItemSqlDao.getInvoiceItemsForInvoice(invoiceId1.toString(), internalCallContext).size(), 0);
        Assert.assertEquals(invoiceItemSqlDao.getInvoiceItemsForInvoice(invoiceId2.toString(), internalCallContext).size(), 1);
    }

    @Test(groups = "slow")
    public void testHealthCheck() throws Exception {
        // HealthCheck test to make sure MySQL is setup properly
        try {
            invoiceItemSqlDao.test(internalCallContext);
        } catch (Throwable t) {
            Assert.fail(t.toString());
        }
    }

    private BusinessInvoiceItemModelDao createInvoiceItem(final UUID invoiceId, final String externalKey) {
        final BigDecimal amount = BigDecimal.TEN;
        final String billingPeriod = UUID.randomUUID().toString().substring(0, 20);
        final DateTime createdDate = clock.getUTCNow();
        final Currency currency = Currency.AUD;
        final LocalDate endDate = clock.getUTCToday();
        final UUID itemId = UUID.randomUUID();
        final UUID linkedItemId = UUID.randomUUID();
        final String itemType = UUID.randomUUID().toString().substring(0, 20);
        final String phase = UUID.randomUUID().toString().substring(0, 20);
        final String productCategory = UUID.randomUUID().toString().substring(0, 20);
        final String productName = UUID.randomUUID().toString().substring(0, 20);
        final String productType = UUID.randomUUID().toString().substring(0, 20);
        final String slug = UUID.randomUUID().toString();
        final LocalDate startDate = clock.getUTCToday();
        final DateTime updatedDate = clock.getUTCNow();

        return new BusinessInvoiceItemModelDao(amount, billingPeriod, createdDate, currency, endDate, externalKey, invoiceId, itemId,
                                               linkedItemId, itemType, phase, productCategory, productName, productType, slug, startDate, updatedDate);
    }
}
