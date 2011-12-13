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
import org.skife.jdbi.v2.IDBI;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class InvoiceDaoWrapper implements InvoiceDao {
    private final InvoiceDao dao;

    @Inject
    public InvoiceDaoWrapper(IDBI dbi) {
        this.dao = dbi.onDemand(InvoiceDao.class);
    }

    @Override
    public List<Invoice> getInvoicesByAccount(final String accountId) {
        return dao.getInvoicesByAccount(accountId);
    }

    @Override
    public Invoice getInvoice(final String invoiceId) {
        return dao.getInvoice(invoiceId);
    }

    @Override
    public void createInvoice(final Invoice invoice) {
        dao.createInvoice(invoice);
    }

    @Override
    public List<Invoice> getInvoicesBySubscription(String subscriptionId) {
        return dao.getInvoicesBySubscription(subscriptionId);
    }

    @Override
    public List<UUID> getInvoicesForPayment(Date targetDate, int numberOfDays) {
        return dao.getInvoicesForPayment(targetDate, numberOfDays);
    }

    @Override
    public void notifySuccessfulPayment(String invoiceId, Date paymentDate, BigDecimal paymentAmount) {
        dao.notifySuccessfulPayment(invoiceId, paymentDate, paymentAmount);
    }

    @Override
    public void notifyFailedPayment(String invoiceId, Date paymentAttemptDate) {
        dao.notifyFailedPayment(invoiceId, paymentAttemptDate);
    }

    @Override
    public void test() {
        dao.test();
    }
}
