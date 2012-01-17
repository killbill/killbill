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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.joda.time.DateTime;
import org.joda.time.Days;
import org.testng.annotations.Test;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.model.DefaultInvoice;
import com.ning.billing.invoice.model.DefaultInvoiceItem;
import com.ning.billing.util.clock.DefaultClock;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

@Test(groups = {"invoicing", "invoicing-invoiceDao"})
public class InvoiceDaoTests extends InvoiceDaoTestBase {
    private final int NUMBER_OF_DAY_BETWEEN_RETRIES = 8;

    @Test
    public void testCreationAndRetrievalByAccount() {
        UUID accountId = UUID.randomUUID();
        Invoice invoice = new DefaultInvoice(accountId, new DefaultClock().getUTCNow(), Currency.USD);
        DateTime invoiceDate = invoice.getInvoiceDate();

        invoiceDao.create(invoice);

        List<Invoice> invoices = invoiceDao.getInvoicesByAccount(accountId.toString());
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
    public void testInvoicePayment() {
        UUID accountId = UUID.randomUUID();
        Invoice invoice = new DefaultInvoice(accountId, new DefaultClock().getUTCNow(), Currency.USD);
        UUID invoiceId = invoice.getId();
        UUID subscriptionId = UUID.randomUUID();
        DateTime startDate = new DateTime(2010, 1, 1, 0, 0, 0, 0);
        DateTime endDate = new DateTime(2010, 4, 1, 0, 0, 0, 0);
        InvoiceItem invoiceItem = new DefaultInvoiceItem(invoiceId, subscriptionId, startDate, endDate, "test", new BigDecimal("21.00"), new BigDecimal("7.00"), Currency.USD);
        invoice.add(invoiceItem);
        invoiceDao.create(invoice);

        Invoice savedInvoice = invoiceDao.getById(invoiceId.toString());
        assertNotNull(savedInvoice);
        assertEquals(savedInvoice.getTotalAmount().compareTo(new BigDecimal("21.00")), 0);
        assertEquals(savedInvoice.getAmountOutstanding().compareTo(new BigDecimal("21.00")), 0);
        assertEquals(savedInvoice.getAmountPaid(), BigDecimal.ZERO);
        assertEquals(savedInvoice.getItems().size(), 1);

        BigDecimal paymentAmount = new BigDecimal("11.00");
        String paymentId = UUID.randomUUID().toString();
        invoiceDao.notifySuccessfulPayment(invoiceId.toString(), paymentAmount, Currency.USD.toString(), paymentId, new DefaultClock().getUTCNow().plusDays(12).toDate());

        Invoice retrievedInvoice = invoiceDao.getById(invoiceId.toString());
        assertNotNull(retrievedInvoice);
        assertEquals(retrievedInvoice.getItems().size(), 1);
        assertEquals(retrievedInvoice.getTotalAmount().compareTo(new BigDecimal("21.00")), 0);
        assertEquals(retrievedInvoice.getAmountOutstanding().compareTo(new BigDecimal("10.00")), 0);
        assertEquals(retrievedInvoice.getAmountPaid().compareTo(new BigDecimal("11.00")), 0);
    }

    @Test
    public void testRetrievalForNonExistentInvoiceId() {
        Invoice invoice = invoiceDao.getById(UUID.randomUUID().toString());
        assertNull(invoice);
    }

    @Test
    public void testAddPayment() {
        UUID accountId = UUID.randomUUID();
        DateTime targetDate = new DateTime(2011, 10, 6, 0, 0, 0, 0);
        Invoice invoice = new DefaultInvoice(accountId, targetDate, Currency.USD);

        String paymentId = UUID.randomUUID().toString();
        DateTime paymentAttemptDate = new DateTime(2011, 6, 24, 12, 14, 36, 0);
        BigDecimal paymentAmount = new BigDecimal("14.0");

        invoiceDao.create(invoice);
        invoiceDao.notifySuccessfulPayment(invoice.getId().toString(), paymentAmount, Currency.USD.toString(), paymentId, paymentAttemptDate.toDate());

        invoice = invoiceDao.getById(invoice.getId().toString());
        assertEquals(invoice.getAmountPaid().compareTo(paymentAmount), 0);
        assertTrue(invoice.getLastPaymentAttempt().equals(paymentAttemptDate));
    }

    @Test
    public void testAddPaymentAttempt() {
        UUID accountId = UUID.randomUUID();
        DateTime targetDate = new DateTime(2011, 10, 6, 0, 0, 0, 0);
        Invoice invoice = new DefaultInvoice(accountId, targetDate, Currency.USD);

        DateTime paymentAttemptDate = new DateTime(2011, 6, 24, 12, 14, 36, 0);

        invoiceDao.create(invoice);
        invoiceDao.notifyFailedPayment(invoice.getId().toString(), UUID.randomUUID().toString(), paymentAttemptDate.toDate());

        invoice = invoiceDao.getById(invoice.getId().toString());
        assertTrue(invoice.getLastPaymentAttempt().equals(paymentAttemptDate));
    }

    @Test
    public void testGetInvoicesForPaymentWithNoResults() {
        DateTime notionalDate = new DateTime();
        DateTime targetDate = new DateTime(2011, 10, 6, 0, 0, 0, 0);
        
        // determine the number of existing invoices available for payment (to avoid side effects from other tests)
        List<UUID> invoices = invoiceDao.getInvoicesForPayment(notionalDate.toDate(), NUMBER_OF_DAY_BETWEEN_RETRIES);
        int existingInvoiceCount = invoices.size();
        
        UUID accountId = UUID.randomUUID();
        Invoice invoice = new DefaultInvoice(accountId, targetDate, Currency.USD);

        invoiceDao.create(invoice);
        invoices = invoiceDao.getInvoicesForPayment(notionalDate.toDate(), NUMBER_OF_DAY_BETWEEN_RETRIES);
        assertEquals(invoices.size(), existingInvoiceCount);
    }

    @Test
    public void testGetInvoicesForPayment() {
        List<UUID> invoices;
        DateTime notionalDate = new DateTime();

        // create a new invoice with one item
        UUID accountId = UUID.randomUUID();
        DateTime targetDate = new DateTime(2011, 10, 6, 0, 0, 0, 0);
        Invoice invoice = new DefaultInvoice(accountId, targetDate, Currency.USD);

        UUID invoiceId = invoice.getId();
        UUID subscriptionId = UUID.randomUUID();
        DateTime endDate = targetDate.plusMonths(3);
        BigDecimal rate = new BigDecimal("9.0");
        BigDecimal amount = rate.multiply(new BigDecimal("3.0"));

        DefaultInvoiceItem item = new DefaultInvoiceItem(invoiceId, subscriptionId, targetDate, endDate, "test", amount, rate, Currency.USD);
        invoice.add(item);
        invoiceDao.create(invoice);

        // ensure that the number of invoices for payment has increased by 1
        int count;
        invoices = invoiceDao.getInvoicesForPayment(notionalDate.toDate(), NUMBER_OF_DAY_BETWEEN_RETRIES);
        List<Invoice> invoicesDue = getInvoicesDueForPaymentAttempt(invoiceDao.get(), notionalDate);
        count = invoicesDue.size();
        assertEquals(invoices.size(), count);

        // attempt a payment; ensure that the number of invoices for payment has decreased by 1
        // (no retries for NUMBER_OF_DAYS_BETWEEN_RETRIES days)
        invoiceDao.notifyFailedPayment(invoice.getId().toString(), UUID.randomUUID().toString(), notionalDate.toDate());
        invoices = invoiceDao.getInvoicesForPayment(notionalDate.toDate(), NUMBER_OF_DAY_BETWEEN_RETRIES);
        count = getInvoicesDueForPaymentAttempt(invoiceDao.get(), notionalDate).size();
        assertEquals(invoices.size(), count);

        // advance clock by NUMBER_OF_DAYS_BETWEEN_RETRIES days
        // ensure that number of invoices for payment has increased by 1 (retry)
        notionalDate = notionalDate.plusDays(NUMBER_OF_DAY_BETWEEN_RETRIES);
        invoices = invoiceDao.getInvoicesForPayment(notionalDate.toDate(), NUMBER_OF_DAY_BETWEEN_RETRIES);
        count = getInvoicesDueForPaymentAttempt(invoiceDao.get(), notionalDate).size();
        assertEquals(invoices.size(), count);

        // post successful partial payment; ensure that number of invoices for payment has decreased by 1
        invoiceDao.notifySuccessfulPayment(invoiceId.toString(), new BigDecimal("22.0000"), Currency.USD.toString(), UUID.randomUUID().toString(), notionalDate.toDate());
        invoices = invoiceDao.getInvoicesForPayment(notionalDate.toDate(), NUMBER_OF_DAY_BETWEEN_RETRIES);
        count = getInvoicesDueForPaymentAttempt(invoiceDao.get(), notionalDate).size();
        assertEquals(invoices.size(), count);

        // get invoice; verify amount paid is correct
        invoice = invoiceDao.getById(invoiceId.toString());
        assertEquals(invoice.getAmountPaid().compareTo(new BigDecimal("22.0")), 0);

        // advance clock NUMBER_OF_DAYS_BETWEEN_RETRIES days
        // ensure that number of invoices for payment has increased by 1 (retry)
        notionalDate = notionalDate.plusDays(NUMBER_OF_DAY_BETWEEN_RETRIES);
        invoices = invoiceDao.getInvoicesForPayment(notionalDate.toDate(), NUMBER_OF_DAY_BETWEEN_RETRIES);
        count = getInvoicesDueForPaymentAttempt(invoiceDao.get(), notionalDate).size();
        assertEquals(invoices.size(), count);

        // post completed payment; ensure that the number of invoices for payment has decreased by 1
        invoiceDao.notifySuccessfulPayment(invoiceId.toString(), new BigDecimal("5.0000"), Currency.USD.toString(), UUID.randomUUID().toString(), notionalDate.toDate());
        invoices = invoiceDao.getInvoicesForPayment(notionalDate.toDate(), NUMBER_OF_DAY_BETWEEN_RETRIES);
        count = getInvoicesDueForPaymentAttempt(invoiceDao.get(), notionalDate).size();
        assertEquals(invoices.size(), count);

        // get invoice; verify amount paid is correct
        invoice = invoiceDao.getById(invoiceId.toString());
        count = getInvoicesDueForPaymentAttempt(invoiceDao.get(), notionalDate).size();
        assertEquals(invoice.getAmountPaid().compareTo(new BigDecimal("27.0")), 0);

        // advance clock by NUMBER_OF_DAYS_BETWEEN_RETRIES days
        // ensure that the number of invoices for payment hasn't changed
        notionalDate = notionalDate.plusDays(NUMBER_OF_DAY_BETWEEN_RETRIES);
        invoices = invoiceDao.getInvoicesForPayment(notionalDate.toDate(), NUMBER_OF_DAY_BETWEEN_RETRIES);
        count = getInvoicesDueForPaymentAttempt(invoiceDao.get(), notionalDate).size();
        assertEquals(invoices.size(), count);
    }

    private List<Invoice> getInvoicesDueForPaymentAttempt(List<Invoice> invoices, DateTime date) {
        List<Invoice> invoicesDue= new ArrayList<Invoice>();

        for (Invoice invoice : invoices) {
            if (invoice.isDueForPayment(date, NUMBER_OF_DAY_BETWEEN_RETRIES)) {
                invoicesDue.add(invoice);
            }
        }

        return invoicesDue;
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
        Invoice invoice1 = new DefaultInvoice(accountId, targetDate, Currency.USD);
        invoiceDao.create(invoice1);

        UUID invoiceId1 = invoice1.getId();

        DateTime startDate = new DateTime(2011, 3, 1, 0, 0, 0, 0);
        DateTime endDate = startDate.plusMonths(1);

        DefaultInvoiceItem item1 = new DefaultInvoiceItem(invoiceId1, subscriptionId1, startDate, endDate, "test A", rate1, rate1, Currency.USD);
        invoiceItemDao.create(item1);

        DefaultInvoiceItem item2 = new DefaultInvoiceItem(invoiceId1, subscriptionId2, startDate, endDate, "test B", rate2, rate2, Currency.USD);
        invoiceItemDao.create(item2);

        DefaultInvoiceItem item3 = new DefaultInvoiceItem(invoiceId1, subscriptionId3, startDate, endDate, "test C", rate3, rate3, Currency.USD);
        invoiceItemDao.create(item3);

        DefaultInvoiceItem item4 = new DefaultInvoiceItem(invoiceId1, subscriptionId4, startDate, endDate, "test D", rate4, rate4, Currency.USD);
        invoiceItemDao.create(item4);

        // create invoice 2 (subscriptions 1-3)
        DefaultInvoice invoice2 = new DefaultInvoice(accountId, targetDate, Currency.USD);
        invoiceDao.create(invoice2);

        UUID invoiceId2 = invoice2.getId();

        startDate = endDate;
        endDate = startDate.plusMonths(1);

        DefaultInvoiceItem item5 = new DefaultInvoiceItem(invoiceId2, subscriptionId1, startDate, endDate, "test A", rate1, rate1, Currency.USD);
        invoiceItemDao.create(item5);

        DefaultInvoiceItem item6 = new DefaultInvoiceItem(invoiceId2, subscriptionId2, startDate, endDate, "test B", rate2, rate2, Currency.USD);
        invoiceItemDao.create(item6);

        DefaultInvoiceItem item7 = new DefaultInvoiceItem(invoiceId2, subscriptionId3, startDate, endDate, "test C", rate3, rate3, Currency.USD);
        invoiceItemDao.create(item7);

        // check that each subscription returns the correct number of invoices
        List<Invoice> items1 = invoiceDao.getInvoicesBySubscription(subscriptionId1.toString());
        assertEquals(items1.size(), 2);

        List<Invoice> items2 = invoiceDao.getInvoicesBySubscription(subscriptionId2.toString());
        assertEquals(items2.size(), 2);

        List<Invoice> items3 = invoiceDao.getInvoicesBySubscription(subscriptionId3.toString());
        assertEquals(items3.size(), 2);

        List<Invoice> items4 = invoiceDao.getInvoicesBySubscription(subscriptionId4.toString());
        assertEquals(items4.size(), 1);
    }
    
}
