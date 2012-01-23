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
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceCreationNotification;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.user.DefaultInvoiceCreationNotification;
import com.ning.billing.payment.api.InvoicePayment;
import com.ning.billing.util.eventbus.EventBus;

public class DefaultInvoiceDao implements InvoiceDao {
    private final InvoiceSqlDao invoiceDao;

    private final EventBus eventBus;
    private final static Logger log = LoggerFactory.getLogger(DefaultInvoiceDao.class);

    @Inject
    public DefaultInvoiceDao(final IDBI dbi, final EventBus eventBus) {
        this.invoiceDao = dbi.onDemand(InvoiceSqlDao.class);
        this.eventBus = eventBus;
    }

    @Override
    public List<Invoice> getInvoicesByAccount(final String accountId) {
        return invoiceDao.getInvoicesByAccount(accountId);
    }

    @Override
    public List<Invoice> get() {
        return invoiceDao.inTransaction(new Transaction<List<Invoice>, InvoiceSqlDao>() {
             @Override
             public List<Invoice> inTransaction(final InvoiceSqlDao invoiceDao, final TransactionStatus status) throws Exception {
                 List<Invoice> invoices = invoiceDao.get();

                 InvoiceItemSqlDao invoiceItemDao = invoiceDao.become(InvoiceItemSqlDao.class);
                 for (Invoice invoice : invoices) {
                     List<InvoiceItem> invoiceItems = invoiceItemDao.getInvoiceItemsByInvoice(invoice.getId().toString());
                     invoice.add(invoiceItems);
                 }

                 return invoices;
             }
        });
    }

    @Override
    public Invoice getById(final String invoiceId) {
        return invoiceDao.inTransaction(new Transaction<Invoice, InvoiceSqlDao>() {
             @Override
             public Invoice inTransaction(final InvoiceSqlDao invoiceDao, final TransactionStatus status) throws Exception {
                 Invoice invoice = invoiceDao.getById(invoiceId);

                 if (invoice != null) {
                     InvoiceItemSqlDao invoiceItemDao = invoiceDao.become(InvoiceItemSqlDao.class);
                     List<InvoiceItem> invoiceItems = invoiceItemDao.getInvoiceItemsByInvoice(invoiceId);
                     invoice.add(invoiceItems);
                 }

                 return invoice;
             }
        });
    }

    @Override
    public void create(final Invoice invoice) {
         invoiceDao.inTransaction(new Transaction<Void, InvoiceSqlDao>() {
             @Override
             public Void inTransaction(final InvoiceSqlDao invoiceDao, final TransactionStatus status) throws Exception {
                Invoice currentInvoice = invoiceDao.getById(invoice.getId().toString());

                if (currentInvoice == null) {
                    invoiceDao.create(invoice);

                    List<InvoiceItem> invoiceItems = invoice.getItems();
                    InvoiceItemSqlDao invoiceItemDao = invoiceDao.become(InvoiceItemSqlDao.class);
                    invoiceItemDao.create(invoiceItems);

                    InvoiceCreationNotification event;
                    event = new DefaultInvoiceCreationNotification(invoice.getId(), invoice.getAccountId(),
                                                                  invoice.getAmountOutstanding(), invoice.getCurrency(),
                                                                  invoice.getInvoiceDate());
                    eventBus.post(event);
                }

                return null;
             }
         });
    }

    @Override
    public List<Invoice> getInvoicesBySubscription(final String subscriptionId) {
        return invoiceDao.inTransaction(new Transaction<List<Invoice>, InvoiceSqlDao>() {
             @Override
             public List<Invoice> inTransaction(InvoiceSqlDao invoiceDao, TransactionStatus status) throws Exception {
                 List<Invoice> invoices = invoiceDao.getInvoicesBySubscription(subscriptionId);

                 InvoiceItemSqlDao invoiceItemDao = invoiceDao.become(InvoiceItemSqlDao.class);
                 for (Invoice invoice : invoices) {
                     List<InvoiceItem> invoiceItems = invoiceItemDao.getInvoiceItemsByInvoice(invoice.getId().toString());
                     invoice.add(invoiceItems);
                 }

                 return invoices;
             }
        });
    }

    @Override
    public List<UUID> getInvoicesForPayment(Date targetDate, int numberOfDays) {
        return invoiceDao.getInvoicesForPayment(targetDate, numberOfDays);
    }

    @Override
    public void notifyOfPaymentAttempt(InvoicePayment invoicePayment) {
        invoiceDao.notifyOfPaymentAttempt(invoicePayment);
    }

    @Override
    public void test() {
        invoiceDao.test();
    }

    @Override
    public String getInvoiceIdByPaymentAttemptId(UUID paymentAttemptId) {
        return invoiceDao.getInvoiceIdByPaymentAttemptId(paymentAttemptId.toString());
    }

    @Override
    public InvoicePayment getInvoicePayment(UUID paymentAttemptId) {
        return invoiceDao.getInvoicePayment(paymentAttemptId);
    }

	@Override
	public BigDecimal getAccountBalance(UUID accountId) {
		return invoiceDao.getAccountBalance(accountId.toString());
	}

}
