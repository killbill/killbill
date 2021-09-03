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
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.InvoiceTestSuiteWithEmbeddedDB;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.model.RepairAdjInvoiceItem;
import org.killbill.billing.util.dao.CounterMappings;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class TestInvoiceItemSqlDao extends InvoiceTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testUpdateItemFields() throws Exception {
        final InvoiceItemSqlDao dao = dbi.onDemand(InvoiceItemSqlDao.class);

        final UUID invoiceItemId = UUID.randomUUID();

        dao.create(new InvoiceItemModelDao(invoiceItemId, null, InvoiceItemType.FIXED, UUID.randomUUID(), UUID.randomUUID(), null, null, null, "description",
                                           null, null, null, null, null, new LocalDate(), null, BigDecimal.ONE, null, Currency.USD, null), internalCallContext);

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

    @Test(groups = "slow")
    public void testWithOrWithoutCatalogEffectiveDate() throws Exception {
        final InvoiceItemSqlDao dao = dbi.onDemand(InvoiceItemSqlDao.class);


        // No catalogEffectiveDate
        final UUID invoiceItemId1 = UUID.randomUUID();
        dao.create(new InvoiceItemModelDao(invoiceItemId1, null, InvoiceItemType.FIXED, UUID.randomUUID(), UUID.randomUUID(), null, null, null, "description",
                                           null, null, null, null, null, new LocalDate(), null, BigDecimal.ONE, null, Currency.USD, null), internalCallContext);

        InvoiceItemModelDao result1 = dao.getById(invoiceItemId1.toString(), internalCallContext);
        Assert.assertNull(result1.getCatalogEffectiveDate());


        // With catalogEffectiveDate
        final UUID invoiceItemId2 = UUID.randomUUID();
        final DateTime catalogEffectiveDate = new DateTime().withMillis(0);
        dao.create(new InvoiceItemModelDao(invoiceItemId2, null, InvoiceItemType.FIXED, UUID.randomUUID(), UUID.randomUUID(), null, null, null, "description",
                                           null, null, null, null, catalogEffectiveDate, new LocalDate(), null, BigDecimal.ONE, null, Currency.USD, null), internalCallContext);

        InvoiceItemModelDao result2 = dao.getById(invoiceItemId2.toString(), internalCallContext);
        Assert.assertNotNull(result2.getCatalogEffectiveDate());
        Assert.assertTrue(result2.getCatalogEffectiveDate().compareTo(catalogEffectiveDate) == 0);
    }



    @Test(groups = "slow")
    public void testRepairMap()  {
        final InvoiceItemSqlDao dao = dbi.onDemand(InvoiceItemSqlDao.class);

        final UUID accountId = UUID.randomUUID();

        // 1 REPAIR against 1st invoice
        final UUID invoiceId1 = UUID.randomUUID();
        final UUID invoiceItemId1 = UUID.randomUUID();
        final InvoiceItemModelDao item1 = new InvoiceItemModelDao(invoiceItemId1, null, InvoiceItemType.RECURRING, invoiceId1, accountId, null, null, null, "description",
                                                                  null, null, null, null, null, new LocalDate(), null, BigDecimal.TEN, null, Currency.USD, null);
        dao.create(item1, internalCallContext);

        final UUID repairId1 = UUID.randomUUID();
        final InvoiceItemModelDao repair1 = new InvoiceItemModelDao( repairId1, null, InvoiceItemType.REPAIR_ADJ, UUID.randomUUID(), accountId, null, null, null, "description",
                                                            null, null, null, null, null, new LocalDate(), null, BigDecimal.ONE, null, Currency.USD, item1.getId());
        dao.create(repair1, internalCallContext);


        // 2 REPAIRs against 2nd invoice
        final UUID invoiceId2 = UUID.randomUUID();
        final UUID invoiceItemId2 = UUID.randomUUID();
        final InvoiceItemModelDao item2 = new InvoiceItemModelDao(invoiceItemId2, null, InvoiceItemType.RECURRING, invoiceId2, accountId, null, null, null, "description",
                                                                  null, null, null, null, null, new LocalDate(), null, BigDecimal.TEN, null, Currency.USD, null);
        dao.create(item2, internalCallContext);

        final UUID repairId2a = UUID.randomUUID();
        final InvoiceItemModelDao repair2a = new InvoiceItemModelDao( repairId2a, null, InvoiceItemType.REPAIR_ADJ, UUID.randomUUID(), accountId, null, null, null, "description",
                                                                      null, null, null, null, null, new LocalDate(), null, BigDecimal.ONE, null, Currency.USD, item2.getId());
        dao.create(repair2a, internalCallContext);

        final UUID repairId2b = UUID.randomUUID();
        final InvoiceItemModelDao repair2b = new InvoiceItemModelDao( repairId2b, null, InvoiceItemType.REPAIR_ADJ, UUID.randomUUID(), accountId, null, null, null, "description",
                                                                      null, null, null, null, null, new LocalDate(), null, BigDecimal.ONE, null, Currency.USD, item2.getId());
        dao.create(repair2b, internalCallContext);


        // 0 REPAIR against 3rd invoice
        final UUID invoiceId3 = UUID.randomUUID();
        final UUID invoiceItemId3 = UUID.randomUUID();
        final InvoiceItemModelDao item3 = new InvoiceItemModelDao(invoiceItemId3, null, InvoiceItemType.RECURRING, invoiceId3, accountId, null, null, null, "description",
                                                                  null, null, null, null, null, new LocalDate(), null, BigDecimal.TEN, null, Currency.USD, null);
        dao.create(item3, internalCallContext);

        final Iterable<CounterMappings> repairedMapRes = dao.getRepairMap(ImmutableList.of(invoiceId1.toString(), invoiceId2.toString(), invoiceId3.toString()), internalCallContext);
        final Map<String, Integer> repairedMap = CounterMappings.toMap(repairedMapRes);
        Assert.assertEquals(repairedMap.size(), 2);
        Assert.assertEquals(repairedMap.get(invoiceId1.toString()), Integer.valueOf(1));
        Assert.assertEquals(repairedMap.get(invoiceId2.toString()), Integer.valueOf(2));
    }
}
