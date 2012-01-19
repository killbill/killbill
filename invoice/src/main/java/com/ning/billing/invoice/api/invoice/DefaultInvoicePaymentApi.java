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

package com.ning.billing.invoice.api.invoice;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.joda.time.DateTime;
import org.skife.jdbi.v2.IDBI;
import com.google.inject.Inject;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.invoice.dao.InvoiceDao;

public class DefaultInvoicePaymentApi implements InvoicePaymentApi {
    private final InvoiceDao dao;

    @Inject
    public DefaultInvoicePaymentApi(final InvoiceDao dao) {
        this.dao = dao;
    }

    @Override
    public void paymentSuccessful(final UUID invoiceId, final BigDecimal amount, final Currency currency,
                                  final UUID paymentId, final DateTime paymentAttemptDate) {
        dao.notifySuccessfulPayment(invoiceId, amount, currency, paymentId, paymentAttemptDate);
    }

    @Override
    public void paymentFailed(final UUID invoiceId, final UUID paymentId, final DateTime paymentAttemptDate) {
        dao.notifyFailedPayment(invoiceId, paymentId, paymentAttemptDate);
    }

    @Override
    public List<Invoice> getInvoicesByAccount(final UUID accountId) {
        return dao.getInvoicesByAccount(accountId);
    }

    @Override
    public Invoice getInvoice(final UUID invoiceId) {
        return dao.getById(invoiceId);
    }
}
