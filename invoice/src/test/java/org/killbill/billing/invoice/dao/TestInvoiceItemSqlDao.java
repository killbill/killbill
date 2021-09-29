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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.InvoiceTestSuiteWithEmbeddedDB;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.api.InvoiceStatus;
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
    public void testRepairMap() {
        final InvoiceSqlDao invoiceSqlDao = dbi.onDemand(InvoiceSqlDao.class);
        final InvoiceItemSqlDao invoiceItemSqlDao = dbi.onDemand(InvoiceItemSqlDao.class);

        final UUID accountId = UUID.randomUUID();

        // 1 REPAIR against 1st invoice
        final InvoiceModelDao invoice1 = new InvoiceModelDao(accountId, new LocalDate(), new LocalDate(), Currency.USD, false, InvoiceStatus.COMMITTED);
        invoiceSqlDao.create(invoice1, internalCallContext);
        final UUID invoiceItemId1 = UUID.randomUUID();
        final InvoiceItemModelDao item1 = new InvoiceItemModelDao(invoiceItemId1, null, InvoiceItemType.RECURRING, invoice1.getId(), accountId, null, null, null, "description",
                                                                  null, null, null, null, null, new LocalDate(), null, BigDecimal.TEN, null, Currency.USD, null);
        invoiceItemSqlDao.create(item1, internalCallContext);

        final InvoiceModelDao invoiceRepair1 = new InvoiceModelDao(accountId, new LocalDate(), new LocalDate(), Currency.USD, false, InvoiceStatus.COMMITTED);
        invoiceSqlDao.create(invoiceRepair1, internalCallContext);
        final UUID repairId1 = UUID.randomUUID();
        final InvoiceItemModelDao repair1 = new InvoiceItemModelDao(repairId1, null, InvoiceItemType.REPAIR_ADJ, invoiceRepair1.getId(), accountId, null, null, null, "description",
                                                                    null, null, null, null, null, new LocalDate(), null, BigDecimal.ONE, null, Currency.USD, item1.getId());
        invoiceItemSqlDao.create(repair1, internalCallContext);

        // 2 REPAIRs against 2nd invoice
        final InvoiceModelDao invoice2 = new InvoiceModelDao(accountId, new LocalDate(), new LocalDate(), Currency.USD, false, InvoiceStatus.COMMITTED);
        invoiceSqlDao.create(invoice2, internalCallContext);
        final UUID invoiceItemId2 = UUID.randomUUID();
        final InvoiceItemModelDao item2 = new InvoiceItemModelDao(invoiceItemId2, null, InvoiceItemType.RECURRING, invoice2.getId(), accountId, null, null, null, "description",
                                                                  null, null, null, null, null, new LocalDate(), null, BigDecimal.TEN, null, Currency.USD, null);
        invoiceItemSqlDao.create(item2, internalCallContext);

        final InvoiceModelDao invoiceRepair2a = new InvoiceModelDao(accountId, new LocalDate(), new LocalDate(), Currency.USD, false, InvoiceStatus.COMMITTED);
        invoiceSqlDao.create(invoiceRepair2a, internalCallContext);
        final UUID repairId2a = UUID.randomUUID();
        final InvoiceItemModelDao repair2a = new InvoiceItemModelDao(repairId2a, null, InvoiceItemType.REPAIR_ADJ, invoiceRepair2a.getId(), accountId, null, null, null, "description",
                                                                     null, null, null, null, null, new LocalDate(), null, BigDecimal.ONE, null, Currency.USD, item2.getId());
        invoiceItemSqlDao.create(repair2a, internalCallContext);

        final InvoiceModelDao invoiceRepair2b = new InvoiceModelDao(accountId, new LocalDate(), new LocalDate(), Currency.USD, false, InvoiceStatus.COMMITTED);
        invoiceSqlDao.create(invoiceRepair2b, internalCallContext);
        final UUID repairId2b = UUID.randomUUID();
        final InvoiceItemModelDao repair2b = new InvoiceItemModelDao(repairId2b, null, InvoiceItemType.REPAIR_ADJ, invoiceRepair2b.getId(), accountId, null, null, null, "description",
                                                                     null, null, null, null, null, new LocalDate(), null, BigDecimal.ONE, null, Currency.USD, item2.getId());
        invoiceItemSqlDao.create(repair2b, internalCallContext);

        // 0 REPAIR against 3rd invoice
        final InvoiceModelDao invoice3 = new InvoiceModelDao(accountId, new LocalDate(), new LocalDate(), Currency.USD, false, InvoiceStatus.COMMITTED);
        invoiceSqlDao.create(invoice3, internalCallContext);
        final UUID invoiceItemId3 = UUID.randomUUID();
        final InvoiceItemModelDao item3 = new InvoiceItemModelDao(invoiceItemId3, null, InvoiceItemType.RECURRING, invoice3.getId(), accountId, null, null, null, "description",
                                                                  null, null, null, null, null, new LocalDate(), null, BigDecimal.TEN, null, Currency.USD, null);
        invoiceItemSqlDao.create(item3, internalCallContext);

        //////

        // 1 REPAIR against 4th invoice (VOID)
        final InvoiceModelDao invoice4 = new InvoiceModelDao(accountId, new LocalDate(), new LocalDate(), Currency.USD, false, InvoiceStatus.COMMITTED);
        invoiceSqlDao.create(invoice4, internalCallContext);
        final UUID invoiceItemId4 = UUID.randomUUID();
        final InvoiceItemModelDao item4 = new InvoiceItemModelDao(invoiceItemId4, null, InvoiceItemType.RECURRING, invoice4.getId(), accountId, null, null, null, "description",
                                                                  null, null, null, null, null, new LocalDate(), null, BigDecimal.TEN, null, Currency.USD, null);
        invoiceItemSqlDao.create(item4, internalCallContext);

        final InvoiceModelDao invoiceRepair4 = new InvoiceModelDao(accountId, new LocalDate(), new LocalDate(), Currency.USD, false, InvoiceStatus.VOID);
        invoiceSqlDao.create(invoiceRepair4, internalCallContext);
        final UUID repairId4 = UUID.randomUUID();
        final InvoiceItemModelDao repair4 = new InvoiceItemModelDao(repairId4, null, InvoiceItemType.REPAIR_ADJ, invoiceRepair4.getId(), accountId, null, null, null, "description",
                                                                    null, null, null, null, null, new LocalDate(), null, BigDecimal.ONE, null, Currency.USD, item4.getId());
        invoiceItemSqlDao.create(repair4, internalCallContext);

        final Iterable<CounterMappings> repairedMapRes = invoiceItemSqlDao.getRepairMap(ImmutableList.of(invoice1.getId().toString(), invoice2.getId().toString(), invoice3.getId().toString(), invoice4.getId().toString()), internalCallContext);
        final Map<String, Integer> repairedMap = CounterMappings.toMap(repairedMapRes);
        Assert.assertEquals(repairedMap.size(), 2);
        Assert.assertEquals(repairedMap.get(invoice1.getId().toString()), Integer.valueOf(1));
        Assert.assertEquals(repairedMap.get(invoice2.getId().toString()), Integer.valueOf(2));
    }

    @Test(groups = "slow")
    public void testConsumedCBAItems() {
        final InvoiceSqlDao invoiceSqlDao = dbi.onDemand(InvoiceSqlDao.class);
        final InvoiceItemSqlDao invoiceItemSqlDao = dbi.onDemand(InvoiceItemSqlDao.class);

        final UUID accountId = UUID.randomUUID();

        final InvoiceModelDao invoice1 = new InvoiceModelDao(accountId, new LocalDate(), new LocalDate(), Currency.USD, false, InvoiceStatus.COMMITTED);
        invoiceSqlDao.create(invoice1, internalCallContext);

        final UUID cbaInv1Item1Id = UUID.randomUUID();
        final InvoiceItemModelDao cbaInv1Item1 = new InvoiceItemModelDao(cbaInv1Item1Id, null, InvoiceItemType.CBA_ADJ, invoice1.getId(), accountId, null, null, null, "description",
                                                                         null, null, null, null, null, new LocalDate(), null, new BigDecimal("-5.43"), null, Currency.USD, null);
        invoiceItemSqlDao.create(cbaInv1Item1, internalCallContext);

        final UUID cbaInv1Item2Id = UUID.randomUUID();
        final InvoiceItemModelDao cbaInv1Item2 = new InvoiceItemModelDao(cbaInv1Item2Id, null, InvoiceItemType.CBA_ADJ, invoice1.getId(), accountId, null, null, null, "description",
                                                                         null, null, null, null, null, new LocalDate(), null, new BigDecimal("-7.25"), null, Currency.USD, null);
        invoiceItemSqlDao.create(cbaInv1Item2, internalCallContext);


        final InvoiceModelDao invoice2 = new InvoiceModelDao(accountId, new LocalDate(), new LocalDate(), Currency.USD, false, InvoiceStatus.DRAFT);
        invoiceSqlDao.create(invoice2, internalCallContext);

        final UUID cbaInv2Item1Id = UUID.randomUUID();
        final InvoiceItemModelDao cbaInv2Item1 = new InvoiceItemModelDao(cbaInv2Item1Id, null, InvoiceItemType.CBA_ADJ, invoice2.getId(), accountId, null, null, null, "description",
                                                                         null, null, null, null, null, new LocalDate(), null, new BigDecimal("-4.43"), null, Currency.USD, null);
        invoiceItemSqlDao.create(cbaInv2Item1, internalCallContext);

        final InvoiceModelDao invoice3 = new InvoiceModelDao(accountId, new LocalDate(), new LocalDate(), Currency.USD, false, InvoiceStatus.COMMITTED);
        invoiceSqlDao.create(invoice3, internalCallContext);

        final UUID cbaInv3Item1Id = UUID.randomUUID();
        final InvoiceItemModelDao cbaInv3Item1 = new InvoiceItemModelDao(cbaInv3Item1Id, null, InvoiceItemType.CBA_ADJ, invoice3.getId(), accountId, null, null, null, "description",
                                                                         null, null, null, null, null, new LocalDate(), null, new BigDecimal("-9.83"), null, Currency.USD, null);
        invoiceItemSqlDao.create(cbaInv3Item1, internalCallContext);

        final List<InvoiceItemModelDao> cbasConsumed = invoiceItemSqlDao.getConsumedCBAItems(internalCallContext);
        Assert.assertEquals(cbasConsumed.size(), 3);
        Assert.assertEquals(cbasConsumed.get(0).getAmount().compareTo(cbaInv3Item1.getAmount()), 0);
        Assert.assertEquals(cbasConsumed.get(1).getAmount().compareTo(cbaInv1Item2.getAmount()), 0);
        Assert.assertEquals(cbasConsumed.get(2).getAmount().compareTo(cbaInv1Item1.getAmount()), 0);
    }
}
