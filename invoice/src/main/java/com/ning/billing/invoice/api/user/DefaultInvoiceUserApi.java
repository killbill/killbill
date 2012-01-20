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

package com.ning.billing.invoice.api.user;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.joda.time.DateTime;
import com.google.inject.Inject;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.invoice.dao.InvoiceDao;

public class DefaultInvoiceUserApi implements InvoiceUserApi {
    private final InvoiceDao dao;

    @Inject
    public DefaultInvoiceUserApi(final InvoiceDao dao) {
        this.dao = dao;
    }

    @Override
    public List<UUID> getInvoicesForPayment(final DateTime targetDate, final int numberOfDays) {
        return dao.getInvoicesForPayment(targetDate, numberOfDays);
    }

    @Override
    public List<Invoice> getInvoicesByAccount(final UUID accountId) {
        return dao.getInvoicesByAccount(accountId);
    }

    @Override
    public List<Invoice> getInvoicesByAccount(final UUID accountId, final DateTime fromDate) {
        return dao.getInvoicesByAccount(accountId, fromDate);
    }

    @Override
    public BigDecimal getAccountBalance(final UUID accountId) {
        return dao.getAccountBalance(accountId);
    }

    @Override
    public List<InvoiceItem> getInvoiceItemsByAccount(final UUID accountId) {
        return dao.getInvoiceItemsByAccount(accountId);
    }

    @Override
    public Invoice getInvoice(final UUID invoiceId) {
        return dao.getById(invoiceId);
    }

    @Override
    public void paymentAttemptFailed(final UUID invoiceId, final UUID paymentId, final DateTime paymentAttemptDate) {
        dao.notifyFailedPayment(invoiceId, paymentId, paymentAttemptDate);
    }

    @Override
    public void paymentAttemptSuccessful(final UUID invoiceId, final BigDecimal amount, final Currency currency,
                                         final UUID paymentId, final DateTime paymentDate) {
        dao.notifySuccessfulPayment(invoiceId, amount, currency, paymentId, paymentDate);
    }
}
