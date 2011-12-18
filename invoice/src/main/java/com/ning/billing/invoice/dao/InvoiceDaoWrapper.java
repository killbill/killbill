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

import com.google.inject.Inject;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceCreationNotification;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.user.DefaultInvoiceCreationNotification;
import com.ning.billing.util.eventbus.EventBus;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class InvoiceDaoWrapper implements InvoiceDao {
    private final IDBI dbi;

    private final InvoiceDao invoiceDao;

    private final EventBus eventBus;
    private final static Logger log = LoggerFactory.getLogger(InvoiceDaoWrapper.class);

    @Inject
    public InvoiceDaoWrapper(IDBI dbi, EventBus eventBus) {
        this.dbi = dbi;
        this.invoiceDao = dbi.onDemand(InvoiceDao.class);
        this.eventBus = eventBus;
    }

    @Override
    public List<Invoice> getInvoicesByAccount(final String accountId) {
        return invoiceDao.getInvoicesByAccount(accountId);
    }

    @Override
    public Invoice getById(final String invoiceId) {
        return invoiceDao.getById(invoiceId);
    }

    @Override
    public List<Invoice> get() {
        return invoiceDao.get();
    }

    @Override
    public void save(final Invoice invoice) {
         dbi.inTransaction(new TransactionCallback<Void>() {
            @Override
            public Void inTransaction(Handle conn, TransactionStatus status) throws Exception {
                InvoiceDao invoiceDao = conn.attach(InvoiceDao.class);
                Invoice currentInvoice = invoiceDao.getById(invoice.getId().toString());
                invoiceDao.save(invoice);

//                    List<InvoiceItem> invoiceItems = invoice.getItems();
//                    InvoiceItemDao invoiceItemDao = conn.attach(InvoiceItemDao.class);
//                    invoiceItemDao.save(invoiceItems);

                if (currentInvoice == null) {
                    InvoiceCreationNotification event;
                    event = new DefaultInvoiceCreationNotification(invoice.getId(), invoice.getAccountId(),
                                                                  invoice.getAmountOutstanding(), invoice.getCurrency(),
                                                                  invoice.getInvoiceDate());
                    eventBus.post(event);
                } else {

                }

                return null;
            }
        });
    }

    @Override
    public List<Invoice> getInvoicesBySubscription(String subscriptionId) {
        return invoiceDao.getInvoicesBySubscription(subscriptionId);
    }

    @Override
    public List<UUID> getInvoicesForPayment(Date targetDate, int numberOfDays) {
        return invoiceDao.getInvoicesForPayment(targetDate, numberOfDays);
    }

    @Override
    public void notifySuccessfulPayment(String invoiceId, Date paymentDate, BigDecimal paymentAmount) {
        invoiceDao.notifySuccessfulPayment(invoiceId, paymentDate, paymentAmount);
    }

    @Override
    public void notifyFailedPayment(String invoiceId, Date paymentAttemptDate) {
        invoiceDao.notifyFailedPayment(invoiceId, paymentAttemptDate);
    }

    @Override
    public void test() {
        invoiceDao.test();
    }
}
