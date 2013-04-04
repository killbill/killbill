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

package com.ning.billing.osgi.bundles.analytics.dao.model;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.osgi.bundles.analytics.AnalyticsTestSuiteNoDB;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceItemModelDao;

public class TestBusinessInvoiceItem extends AnalyticsTestSuiteNoDB {

    @Test(groups = "fast")
    public void testEquals() throws Exception {
        final BigDecimal amount = BigDecimal.TEN;
        final String billingPeriod = UUID.randomUUID().toString();
        final DateTime createdDate = clock.getUTCNow();
        final Currency currency = Currency.AUD;
        final LocalDate endDate = clock.getUTCToday();
        final String externalKey = UUID.randomUUID().toString();
        final UUID invoiceId = UUID.randomUUID();
        final UUID itemId = UUID.randomUUID();
        final UUID linkedItemId = UUID.randomUUID();
        final String itemType = UUID.randomUUID().toString();
        final String phase = UUID.randomUUID().toString();
        final String productCategory = UUID.randomUUID().toString();
        final String productName = UUID.randomUUID().toString();
        final String productType = UUID.randomUUID().toString();
        final String slug = UUID.randomUUID().toString();
        final LocalDate startDate = clock.getUTCToday();
        final DateTime updatedDate = clock.getUTCNow();
        final BusinessInvoiceItemModelDao invoiceItem = new BusinessInvoiceItemModelDao(amount, billingPeriod, createdDate, currency,
                                                                                        endDate, externalKey, invoiceId, itemId, linkedItemId,
                                                                                        itemType, phase, productCategory, productName, productType,
                                                                                        slug, startDate, updatedDate);
        Assert.assertSame(invoiceItem, invoiceItem);
        Assert.assertEquals(invoiceItem, invoiceItem);
        Assert.assertTrue(invoiceItem.equals(invoiceItem));
        Assert.assertEquals(invoiceItem.getAmount(), amount);
        Assert.assertEquals(invoiceItem.getBillingPeriod(), billingPeriod);
        Assert.assertEquals(invoiceItem.getCreatedDate(), createdDate);
        Assert.assertEquals(invoiceItem.getCurrency(), currency);
        Assert.assertEquals(invoiceItem.getEndDate(), endDate);
        Assert.assertEquals(invoiceItem.getExternalKey(), externalKey);
        Assert.assertEquals(invoiceItem.getInvoiceId(), invoiceId);
        Assert.assertEquals(invoiceItem.getItemId(), itemId);
        Assert.assertEquals(invoiceItem.getItemType(), itemType);
        Assert.assertEquals(invoiceItem.getLinkedItemId(), linkedItemId);
        Assert.assertEquals(invoiceItem.getPhase(), phase);
        Assert.assertEquals(invoiceItem.getProductCategory(), productCategory);
        Assert.assertEquals(invoiceItem.getProductName(), productName);
        Assert.assertEquals(invoiceItem.getProductType(), productType);
        Assert.assertEquals(invoiceItem.getSlug(), slug);
        Assert.assertEquals(invoiceItem.getStartDate(), startDate);
        Assert.assertEquals(invoiceItem.getUpdatedDate(), updatedDate);

        final BusinessInvoiceItemModelDao otherInvoiceItem = new BusinessInvoiceItemModelDao(null, null, createdDate, null, null, null, null, itemId,
                                                                                             linkedItemId, null, null, null, null, null, null, null, null);
        Assert.assertFalse(invoiceItem.equals(otherInvoiceItem));
    }
}
