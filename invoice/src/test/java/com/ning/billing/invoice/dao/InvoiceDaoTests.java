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
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.glue.InvoiceModuleMock;
import com.ning.billing.invoice.model.InvoiceDefault;
import com.ning.billing.invoice.model.InvoiceItem;
import com.ning.billing.util.clock.Clock;
import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.testng.Assert.*;

@Test(groups = {"invoicing", "invoicing-dao"})
public class InvoiceDaoTests {
    private final int NUMBER_OF_DAY_BETWEEN_RETRIES = 8;

    private final MysqlTestingHelper helper = new MysqlTestingHelper();
    private InvoiceDao dao;
    private InvoiceItemDao invoiceItemDao;

    @BeforeClass()
    private void setup() throws IOException {
        // Health check test to make sure MySQL is setup properly
        try {
            InvoiceModuleMock module = new InvoiceModuleMock();
            final String ddl = IOUtils.toString(InvoiceDaoWrapper.class.getResourceAsStream("/com/ning/billing/invoice/ddl.sql"));
            module.createDb(ddl);

            final Injector injector = Guice.createInjector(Stage.DEVELOPMENT, module);

            dao = injector.getInstance(InvoiceDaoWrapper.class);
            dao.test();

            invoiceItemDao = injector.getInstance(InvoiceItemDao.class);
            invoiceItemDao.test();
        }
        catch (Throwable t) {
            fail(t.toString());
        }
    }

    @Test
    public void testCreationAndRetrievalByAccount() {
        UUID accountId = UUID.randomUUID();
        Invoice invoice = new InvoiceDefault(accountId, new Clock().getUTCNow(), Currency.USD);
        DateTime invoiceDate = invoice.getInvoiceDate();

        dao.createInvoice(invoice);

        List<Invoice> invoices = dao.getInvoicesByAccount(accountId.toString());
        assertNotNull(invoices);
        assertEquals(invoices.size(), 1);
        Invoice thisInvoice = invoices.get(0);
        assertEquals(invoice.getAccountId(), accountId);
        assertTrue(thisInvoice.getInvoiceDate().compareTo(invoiceDate) == 0);
        assertEquals(thisInvoice.getCurrency(), Currency.USD);
        assertEquals(thisInvoice.getNumberOfItems(), 0);
        assertTrue(thisInvoice.getTotalAmount().compareTo(BigDecimal.ZERO) == 0);
    }

    @Test
    public void testRetrievalForNonExistentInvoiceId() {
        Invoice invoice = dao.getInvoice(UUID.randomUUID().toString());
        assertNull(invoice);
    }

    @Test
    public void testAddPayment() {
        UUID accountId = UUID.randomUUID();
        DateTime targetDate = new DateTime(2011, 10, 6, 0, 0, 0, 0);
        Invoice invoice = new InvoiceDefault(accountId, targetDate, Currency.USD);

        DateTime paymentAttemptDate = new DateTime(2011, 6, 24, 12, 14, 36, 0);
        BigDecimal paymentAmount = new BigDecimal("14.0");

        dao.createInvoice(invoice);
        dao.notifySuccessfulPayment(invoice.getId().toString(), paymentAttemptDate.toDate(), paymentAmount);

        invoice = dao.getInvoice(invoice.getId().toString());
        assertEquals(invoice.getAmountPaid().compareTo(paymentAmount), 0);
        assertTrue(invoice.getLastPaymentAttempt().equals(paymentAttemptDate));
    }

    @Test
    public void testAddPaymentAttempt() {
        UUID accountId = UUID.randomUUID();
        DateTime targetDate = new DateTime(2011, 10, 6, 0, 0, 0, 0);
        Invoice invoice = new InvoiceDefault(accountId, targetDate, Currency.USD);

        DateTime paymentAttemptDate = new DateTime(2011, 6, 24, 12, 14, 36, 0);

        dao.createInvoice(invoice);
        dao.notifyFailedPayment(invoice.getId().toString(), paymentAttemptDate.toDate());

        invoice = dao.getInvoice(invoice.getId().toString());
        assertTrue(invoice.getLastPaymentAttempt().equals(paymentAttemptDate));
    }

    @Test
    public void testGetInvoicesForPaymentWithNoResults() {
        DateTime notionalDate = new DateTime();
        DateTime targetDate = new DateTime(2011, 10, 6, 0, 0, 0, 0);
        
        // determine the number of existing invoices available for payment (to avoid side effects from other tests)
        List<UUID> invoices = dao.getInvoicesForPayment(notionalDate.toDate(), NUMBER_OF_DAY_BETWEEN_RETRIES);
        int existingInvoiceCount = invoices.size();
        
        UUID accountId = UUID.randomUUID();
        Invoice invoice = new InvoiceDefault(accountId, targetDate, Currency.USD);

        dao.createInvoice(invoice);
        invoices = dao.getInvoicesForPayment(notionalDate.toDate(), NUMBER_OF_DAY_BETWEEN_RETRIES);
        assertEquals(invoices.size(), existingInvoiceCount);
    }

    // TODO: test invoices for payment with results
    @Test
    public void testGetInvoicesForPayment() {
        List<UUID> invoices;
        DateTime notionalDate = new DateTime();

        // determine the number of existing invoices available for payment (to avoid side effects from other tests)
        invoices = dao.getInvoicesForPayment(notionalDate.toDate(), NUMBER_OF_DAY_BETWEEN_RETRIES);
        int existingInvoiceCount = invoices.size();

        // create a new invoice with one item
        UUID accountId = UUID.randomUUID();
        DateTime targetDate = new DateTime(2011, 10, 6, 0, 0, 0, 0);
        Invoice invoice = new InvoiceDefault(accountId, targetDate, Currency.USD);
        dao.createInvoice(invoice);

        UUID invoiceId = invoice.getId();
        UUID subscriptionId = UUID.randomUUID();
        DateTime endDate = targetDate.plusMonths(3);
        BigDecimal rate = new BigDecimal("9.0");
        BigDecimal amount = rate.multiply(new BigDecimal("3.0"));

        InvoiceItem item = new InvoiceItem(invoiceId, subscriptionId, targetDate, endDate, "test", amount, rate, Currency.USD);
        invoiceItemDao.createInvoiceItem(item);

        // ensure that the number of invoices for payment has increased by 1
        invoices = dao.getInvoicesForPayment(notionalDate.toDate(), NUMBER_OF_DAY_BETWEEN_RETRIES);
        assertEquals(invoices.size(), existingInvoiceCount + 1);

        // attempt a payment; ensure that the number of invoices for payment has decreased by 1 (no retries for eight days)
        dao.notifyFailedPayment(invoice.getId().toString(), notionalDate.toDate());
        invoices = dao.getInvoicesForPayment(notionalDate.toDate(), NUMBER_OF_DAY_BETWEEN_RETRIES);
        assertEquals(invoices.size(), existingInvoiceCount);

        // advance clock by 8 days; ensure that number of invoices for payment has increased by 1 (retry)
        notionalDate = notionalDate.plusDays(NUMBER_OF_DAY_BETWEEN_RETRIES);
        invoices = dao.getInvoicesForPayment(notionalDate.toDate(), NUMBER_OF_DAY_BETWEEN_RETRIES);
        assertEquals(invoices.size(), existingInvoiceCount + 1);

        // post successful partial payment; ensure that number of invoices for payment has decreased by 1
        dao.notifySuccessfulPayment(invoiceId.toString(), notionalDate.toDate(), new BigDecimal("22.0000"));
        invoices = dao.getInvoicesForPayment(notionalDate.toDate(), NUMBER_OF_DAY_BETWEEN_RETRIES);
        assertEquals(invoices.size(), existingInvoiceCount);

        // get invoice; verify amount paid is correct
        invoice = dao.getInvoice(invoiceId.toString());
        assertEquals(invoice.getAmountPaid().compareTo(new BigDecimal("22.0")), 0);

        // advance clock eight days; ensure that number of invoices for payment has increased by 1 (retry)
        notionalDate = notionalDate.plusDays(NUMBER_OF_DAY_BETWEEN_RETRIES);
        invoices = dao.getInvoicesForPayment(notionalDate.toDate(), NUMBER_OF_DAY_BETWEEN_RETRIES);
        assertEquals(invoices.size(), existingInvoiceCount + 1);

        // post completed payment; ensure that the number of invoices for payment has decreased by 1
        dao.notifySuccessfulPayment(invoiceId.toString(), notionalDate.toDate(), new BigDecimal("5.0000"));
        invoices = dao.getInvoicesForPayment(notionalDate.toDate(), NUMBER_OF_DAY_BETWEEN_RETRIES);
        assertEquals(invoices.size(), existingInvoiceCount);

        // get invoice; verify amount paid is correct
        invoice = dao.getInvoice(invoiceId.toString());
        assertEquals(invoice.getAmountPaid().compareTo(new BigDecimal("27.0")), 0);

        // advance clock by 8 days; ensure that the number of invoices for payment hasn't changed
        notionalDate = notionalDate.plusDays(NUMBER_OF_DAY_BETWEEN_RETRIES);
        invoices = dao.getInvoicesForPayment(notionalDate.toDate(), NUMBER_OF_DAY_BETWEEN_RETRIES);
        assertEquals(invoices.size(), existingInvoiceCount);

    }

    @Test
    public void testGetInvoicesBySubscription() {
        UUID accountId = UUID.randomUUID();

        UUID subscriptionId1 = UUID.randomUUID(); BigDecimal rate1 = new BigDecimal("17.0");
        UUID subscriptionId2 = UUID.randomUUID(); BigDecimal rate2 = new BigDecimal("42.0");
        UUID subscriptionId3 = UUID.randomUUID(); BigDecimal rate3 = new BigDecimal("3.0");
        UUID subscriptionId4 = UUID.randomUUID(); BigDecimal rate4 = new BigDecimal("12.0");

        DateTime targetDate = new DateTime(2011, 5, 23, 0, 0, 0, 0);


        // create invoice 1 (subscriptions 1-4)
        Invoice invoice1 = new InvoiceDefault(accountId, targetDate, Currency.USD);
        dao.createInvoice(invoice1);

        UUID invoiceId1 = invoice1.getId();

        DateTime startDate = new DateTime(2011, 3, 1, 0, 0, 0, 0);
        DateTime endDate = startDate.plusMonths(1);

        InvoiceItem item1 = new InvoiceItem(invoiceId1, subscriptionId1, startDate, endDate, "test A", rate1, rate1, Currency.USD);
        invoiceItemDao.createInvoiceItem(item1);

        InvoiceItem item2 = new InvoiceItem(invoiceId1, subscriptionId2, startDate, endDate, "test B", rate2, rate2, Currency.USD);
        invoiceItemDao.createInvoiceItem(item2);

        InvoiceItem item3 = new InvoiceItem(invoiceId1, subscriptionId3, startDate, endDate, "test C", rate3, rate3, Currency.USD);
        invoiceItemDao.createInvoiceItem(item3);

        InvoiceItem item4 = new InvoiceItem(invoiceId1, subscriptionId4, startDate, endDate, "test D", rate4, rate4, Currency.USD);
        invoiceItemDao.createInvoiceItem(item4);

        // create invoice 2 (subscriptions 1-3)
        InvoiceDefault invoice = new InvoiceDefault(accountId, targetDate, Currency.USD);
        dao.createInvoice(invoice);

        UUID invoiceId2 = invoice.getId();

        startDate = endDate;
        endDate = startDate.plusMonths(1);

        InvoiceItem item5 = new InvoiceItem(invoiceId2, subscriptionId1, startDate, endDate, "test A", rate1, rate1, Currency.USD);
        invoiceItemDao.createInvoiceItem(item5);

        InvoiceItem item6 = new InvoiceItem(invoiceId2, subscriptionId2, startDate, endDate, "test B", rate2, rate2, Currency.USD);
        invoiceItemDao.createInvoiceItem(item6);

        InvoiceItem item7 = new InvoiceItem(invoiceId2, subscriptionId3, startDate, endDate, "test C", rate3, rate3, Currency.USD);
        invoiceItemDao.createInvoiceItem(item7);

        // check that each subscription returns the correct number of invoices
        List<Invoice> items1 = dao.getInvoicesBySubscription(subscriptionId1.toString());
        assertEquals(items1.size(), 2);

        List<Invoice> items2 = dao.getInvoicesBySubscription(subscriptionId2.toString());
        assertEquals(items2.size(), 2);

        List<Invoice> items3 = dao.getInvoicesBySubscription(subscriptionId3.toString());
        assertEquals(items3.size(), 2);

        List<Invoice> items4 = dao.getInvoicesBySubscription(subscriptionId4.toString());
        assertEquals(items4.size(), 1);
    }
    
}
