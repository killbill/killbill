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

import org.testng.annotations.Test;

@Test(groups = {"invoicing", "invoicing-dao"})
public class InvoiceDaoTests {
//    private final MysqlTestingHelper helper = new MysqlTestingHelper();
//    private InvoiceDao dao;
//
//    @BeforeClass(alwaysRun = true)
//    private void setup() {
//        final String ddl = IOUtils.toString(InvoiceDao.class.getResourceAsStream("/ddl.sql"));
//
//        helper.startMysql();
//        helper.initDb();
//
//        final IDBI dbi = helper.getDBI();
//        dao = dbi.onDemand(EventDao.class);
//
//        // Healthcheck test to make sure MySQL is setup properly
//        try {
//            dao.test();
//        }
//        catch (Throwable t) {
//            Assert.fail(t.toString());
//        }
//    }
//
//    @Test
//    public void testCreationAndRetrievalByAccount() {
//        InvoiceDao dao = dbi.onDemand(InvoiceDao.class);
//        UUID accountId = UUID.randomUUID();
//        Invoice invoice = new Invoice(accountId, Currency.USD);
//        DateTime invoiceDate = invoice.getInvoiceDate();
//
//        dao.createInvoice(invoice);
//
//        List<Invoice> invoices = dao.getInvoicesByAccount(accountId.toString());
//        assertNotNull(invoices);
//        assertEquals(invoices.size(), 1);
//        Invoice thisInvoice = invoices.get(0);
//        assertEquals(invoice.getAccountId(), accountId);
//        assertTrue(thisInvoice.getInvoiceDate().equals(invoiceDate));
//        assertEquals(thisInvoice.getCurrency(), Currency.USD);
//        assertEquals(thisInvoice.getNumberOfItems(), 0);
//        assertTrue(thisInvoice.getTotalAmount().compareTo(BigDecimal.ZERO) == 0);
//    }

}
