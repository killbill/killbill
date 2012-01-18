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
import java.util.List;
import java.util.UUID;
import org.joda.time.DateTime;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import com.google.inject.Inject;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceCreationNotification;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.api.user.DefaultInvoiceCreationNotification;
import com.ning.billing.util.eventbus.Bus;

public class DefaultInvoiceDao implements InvoiceDao {
    private final InvoiceSqlDao invoiceSqlDao;
    private final InvoiceItemSqlDao invoiceItemSqlDao;

    private final Bus eventBus;

    @Inject
    public DefaultInvoiceDao(final IDBI dbi, final Bus eventBus) {
        this.invoiceSqlDao = dbi.onDemand(InvoiceSqlDao.class);
        this.invoiceItemSqlDao = dbi.onDemand(InvoiceItemSqlDao.class);
        this.eventBus = eventBus;
    }

    @Override
    public List<Invoice> getInvoicesByAccount(final UUID accountId) {
        return invoiceSqlDao.inTransaction(new Transaction<List<Invoice>, InvoiceSqlDao>() {
            @Override
            public List<Invoice> inTransaction(final InvoiceSqlDao invoiceDao, final TransactionStatus status) throws Exception {
                List<Invoice> invoices = invoiceDao.getInvoicesByAccount(accountId.toString());

                getInvoiceItemsWithinTransaction(invoices, invoiceDao);

                getInvoicePaymentsWithinTransaction(invoices, invoiceDao);

                return invoices;
            }
        });
    }

    @Override
    public List<Invoice> getInvoicesByAccount(final UUID accountId, final DateTime fromDate) {
        return invoiceSqlDao.inTransaction(new Transaction<List<Invoice>, InvoiceSqlDao>() {
            @Override
            public List<Invoice> inTransaction(final InvoiceSqlDao invoiceDao, final TransactionStatus status) throws Exception {
                List<Invoice> invoices = invoiceDao.getInvoicesByAccountAfterDate(accountId.toString(), fromDate.toDate());

                getInvoiceItemsWithinTransaction(invoices, invoiceDao);
                getInvoicePaymentsWithinTransaction(invoices, invoiceDao);

                return invoices;
            }
        });
    }

    @Override
    public List<InvoiceItem> getInvoiceItemsByAccount(final UUID accountId) {
        return invoiceItemSqlDao.getInvoiceItemsByAccount(accountId.toString());
    }

    @Override
    public List<Invoice> get() {
        return invoiceSqlDao.inTransaction(new Transaction<List<Invoice>, InvoiceSqlDao>() {
             @Override
             public List<Invoice> inTransaction(final InvoiceSqlDao invoiceDao, final TransactionStatus status) throws Exception {
                 List<Invoice> invoices = invoiceDao.get();

                 getInvoiceItemsWithinTransaction(invoices, invoiceDao);
                 getInvoicePaymentsWithinTransaction(invoices, invoiceDao);

                 return invoices;
             }
        });
    }

    @Override
    public Invoice getById(final UUID invoiceId) {
        return invoiceSqlDao.inTransaction(new Transaction<Invoice, InvoiceSqlDao>() {
             @Override
             public Invoice inTransaction(final InvoiceSqlDao invoiceDao, final TransactionStatus status) throws Exception {
                 Invoice invoice = invoiceDao.getById(invoiceId.toString());

                 if (invoice != null) {
                     InvoiceItemSqlDao invoiceItemDao = invoiceDao.become(InvoiceItemSqlDao.class);
                     List<InvoiceItem> invoiceItems = invoiceItemDao.getInvoiceItemsByInvoice(invoiceId.toString());
                     invoice.addInvoiceItems(invoiceItems);

                     InvoicePaymentSqlDao invoicePaymentSqlDao = invoiceDao.become(InvoicePaymentSqlDao.class);
                     List<InvoicePayment> invoicePayments = invoicePaymentSqlDao.getPaymentsForInvoice(invoiceId.toString());
                     invoice.addPayments(invoicePayments);
                 }

                 return invoice;
             }
        });
    }

    @Override
    public void create(final Invoice invoice) {
        invoiceSqlDao.inTransaction(new Transaction<Void, InvoiceSqlDao>() {
            @Override
            public Void inTransaction(final InvoiceSqlDao invoiceDao, final TransactionStatus status) throws Exception {
                Invoice currentInvoice = invoiceDao.getById(invoice.getId().toString());

                if (currentInvoice == null) {
                    invoiceDao.create(invoice);

                    List<InvoiceItem> invoiceItems = invoice.getInvoiceItems();
                    InvoiceItemSqlDao invoiceItemDao = invoiceDao.become(InvoiceItemSqlDao.class);
                    invoiceItemDao.create(invoiceItems);

                    List<InvoicePayment> invoicePayments = invoice.getPayments();
                    InvoicePaymentSqlDao invoicePaymentSqlDao = invoiceDao.become(InvoicePaymentSqlDao.class);
                    invoicePaymentSqlDao.create(invoicePayments);

                    InvoiceCreationNotification event;
                    event = new DefaultInvoiceCreationNotification(invoice.getId(), invoice.getAccountId(),
                                                                  invoice.getBalance(), invoice.getCurrency(),
                                                                  invoice.getInvoiceDate());
                    eventBus.post(event);
                }

                return null;
            }
        });
    }

    @Override
    public List<Invoice> getInvoicesBySubscription(final UUID subscriptionId) {
        return invoiceSqlDao.inTransaction(new Transaction<List<Invoice>, InvoiceSqlDao>() {
            @Override
            public List<Invoice> inTransaction(final InvoiceSqlDao invoiceDao, final TransactionStatus status) throws Exception {
                List<Invoice> invoices = invoiceDao.getInvoicesBySubscription(subscriptionId.toString());

                getInvoiceItemsWithinTransaction(invoices, invoiceDao);
                getInvoicePaymentsWithinTransaction(invoices, invoiceDao);

                return invoices;
            }
        });
    }

    @Override
    public List<UUID> getInvoicesForPayment(final DateTime targetDate, final int numberOfDays) {
        return invoiceSqlDao.getInvoicesForPayment(targetDate.toDate(), numberOfDays);
    }

    @Override
    public void notifySuccessfulPayment(final UUID invoiceId, final BigDecimal paymentAmount,
                                        final Currency currency, final UUID paymentId, final DateTime paymentDate) {
        invoiceSqlDao.notifySuccessfulPayment(invoiceId.toString(), paymentAmount, currency.toString(),
                                              paymentId.toString(), paymentDate.toDate());
    }

    @Override
    public void notifyFailedPayment(final UUID invoiceId, final UUID paymentId, final DateTime paymentAttemptDate) {
        invoiceSqlDao.notifyFailedPayment(invoiceId.toString(), paymentId.toString(), paymentAttemptDate.toDate());
    }

    @Override
    public void test() {
        invoiceSqlDao.test();
    }

    private void getInvoiceItemsWithinTransaction(final List<Invoice> invoices, final InvoiceSqlDao invoiceDao) {
        InvoiceItemSqlDao invoiceItemDao = invoiceDao.become(InvoiceItemSqlDao.class);
        for (final Invoice invoice : invoices) {
            List<InvoiceItem> invoiceItems = invoiceItemDao.getInvoiceItemsByInvoice(invoice.getId().toString());
            invoice.addInvoiceItems(invoiceItems);
        }
    }

    private void getInvoicePaymentsWithinTransaction(final List<Invoice> invoices, final InvoiceSqlDao invoiceDao) {
        InvoicePaymentSqlDao invoicePaymentSqlDao = invoiceDao.become(InvoicePaymentSqlDao.class);
        for (final Invoice invoice : invoices) {
            String invoiceId = invoice.getId().toString();
            List<InvoicePayment> invoicePayments = invoicePaymentSqlDao.getPaymentsForInvoice(invoiceId);
            invoice.addPayments(invoicePayments);
        }
    }
}
