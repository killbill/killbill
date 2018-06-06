/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.invoice.dao;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.InvoiceTestSuiteWithEmbeddedDB;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestInvoiceItemSqlDao extends InvoiceTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testUpdaiteteItemFields() throws Exception {
        final InvoiceItemSqlDao dao = dbi.onDemand(InvoiceItemSqlDao.class);

        final UUID invoiceItemId = UUID.randomUUID();

        dao.create(new InvoiceItemModelDao(invoiceItemId, null, InvoiceItemType.FIXED, UUID.randomUUID(), UUID.randomUUID(), null, null, null, "description",
                                           null, null, null, null, new LocalDate(), null, BigDecimal.ONE, null, Currency.USD, null), internalCallContext);

        // Update all fields
        dao.updateItemFields(invoiceItemId.toString(), new BigDecimal("2.00"), "new description", "new items", internalCallContext);

        InvoiceItemModelDao UpdatedItem = dao.getById(invoiceItemId.toString(), internalCallContext);
        Assert.assertTrue(UpdatedItem.getAmount().compareTo(new BigDecimal("2.00")) == 0);
        Assert.assertEquals(UpdatedItem.getDescription(), "new description");
        Assert.assertEquals(UpdatedItem.getItemDetails(), "new items");

        // Update just amount
        dao.updateItemFields(invoiceItemId.toString(), new BigDecimal("3.00"), null, null, internalCallContext);
        UpdatedItem = dao.getById(invoiceItemId.toString(), internalCallContext);
        Assert.assertTrue(UpdatedItem.getAmount().compareTo(new BigDecimal("3.00")) == 0);
        Assert.assertEquals(UpdatedItem.getDescription(), "new description");
        Assert.assertEquals(UpdatedItem.getItemDetails(), "new items");

        // Update just description
        dao.updateItemFields(invoiceItemId.toString(), null, "newer description", null, internalCallContext);
        UpdatedItem = dao.getById(invoiceItemId.toString(), internalCallContext);
        Assert.assertTrue(UpdatedItem.getAmount().compareTo(new BigDecimal("3.00")) == 0);
        Assert.assertEquals(UpdatedItem.getDescription(), "newer description");
        Assert.assertEquals(UpdatedItem.getItemDetails(), "new items");

    }
}
