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

import com.google.inject.Inject;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.invoice.dao.InvoiceDao;
import com.ning.billing.payment.api.InvoicePayment;

public class DefaultInvoicePaymentApi implements InvoicePaymentApi {
    private final InvoiceDao dao;

    @Inject
    public DefaultInvoicePaymentApi(InvoiceDao dao) {
        this.dao = dao;
    }

    @Override
    public void notifyOfPaymentAttempt(InvoicePayment invoicePayment) {
        dao.notifyOfPaymentAttempt(invoicePayment);
    }

//    @Override
//    public void paymentFailed(UUID invoiceId, UUID paymentId, DateTime paymentAttemptDate) {
//        dao.notifyFailedPayment(invoiceId.toString(), paymentId.toString(), paymentAttemptDate.toDate());
//    }

    @Override
    public List<Invoice> getInvoicesByAccount(UUID accountId) {
        return dao.getInvoicesByAccount(accountId.toString());
    }

    @Override
    public Invoice getInvoice(UUID invoiceId) {
        return dao.getById(invoiceId.toString());
    }

    @Override
    public Invoice getInvoiceForPaymentAttemptId(UUID paymentAttemptId) {
        String invoiceIdStr = dao.getInvoiceIdByPaymentAttemptId(paymentAttemptId);
        return invoiceIdStr == null ? null : dao.getById(invoiceIdStr);
    }

    @Override
    public InvoicePayment getInvoicePayment(UUID paymentAttemptId) {
        return dao.getInvoicePayment(paymentAttemptId);
    }

    @Override
    public void notifyOfPaymentAttempt(UUID invoiceId, BigDecimal amountOutstanding, Currency currency, UUID paymentAttemptId, DateTime paymentAttemptDate) {
        InvoicePayment invoicePayment = new InvoicePayment(invoiceId, amountOutstanding, currency, paymentAttemptId, paymentAttemptDate);
        dao.notifyOfPaymentAttempt(invoicePayment);
    }

    @Override
    public void notifyOfPaymentAttempt(UUID invoiceId, UUID paymentAttemptId, DateTime paymentAttemptDate) {
        InvoicePayment invoicePayment = new InvoicePayment(invoiceId, null, null, paymentAttemptId, paymentAttemptDate);
        dao.notifyOfPaymentAttempt(invoicePayment);
    }

}
