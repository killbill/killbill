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
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.api.InvoicePayment.InvoicePaymentType;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.invoice.dao.InvoiceDao;
import com.ning.billing.invoice.model.DefaultInvoicePayment;
import com.ning.billing.util.callcontext.CallContext;

public class DefaultInvoicePaymentApi implements InvoicePaymentApi {
    private final InvoiceDao dao;

    @Inject
    public DefaultInvoicePaymentApi(final InvoiceDao dao) {
        this.dao = dao;
    }

    @Override
    public void notifyOfPaymentAttempt(final InvoicePayment invoicePayment, final CallContext context) {
        dao.notifyOfPaymentAttempt(invoicePayment, context);
    }

    @Override
    public List<Invoice> getAllInvoicesByAccount(final UUID accountId) {
        return dao.getAllInvoicesByAccount(accountId);
    }

    @Override
    public Invoice getInvoice(final UUID invoiceId) {
        return dao.getById(invoiceId);
    }

    @Override
    public Invoice getInvoiceForPaymentAttemptId(final UUID paymentAttemptId) {
        final UUID invoiceIdStr = dao.getInvoiceIdByPaymentAttemptId(paymentAttemptId);
        return invoiceIdStr == null ? null : dao.getById(invoiceIdStr);
    }

    @Override
    public InvoicePayment getInvoicePayment(final UUID paymentAttemptId) {
        return dao.getInvoicePayment(paymentAttemptId);
    }

    @Override
    public void notifyOfPaymentAttempt(final UUID invoiceId, final BigDecimal amount, final Currency currency, final UUID paymentAttemptId, final DateTime paymentAttemptDate, final CallContext context) {
        final InvoicePayment invoicePayment = new DefaultInvoicePayment(InvoicePaymentType.ATTEMPT, paymentAttemptId, invoiceId, paymentAttemptDate, amount, currency);
        dao.notifyOfPaymentAttempt(invoicePayment, context);
    }

    @Override
    public InvoicePayment createChargeback(final UUID invoicePaymentId, final BigDecimal amount, final CallContext context) throws InvoiceApiException {
        return dao.postChargeback(invoicePaymentId, amount, context);
    }

    @Override
    public InvoicePayment createChargeback(final UUID invoicePaymentId, final CallContext context) throws InvoiceApiException {
        return createChargeback(invoicePaymentId, null, context);
    }

    @Override
    public BigDecimal getRemainingAmountPaid(final UUID invoicePaymentId) {
        return dao.getRemainingAmountPaid(invoicePaymentId);
    }

    @Override
    public List<InvoicePayment> getChargebacksByAccountId(final UUID accountId) {
        return dao.getChargebacksByAccountId(accountId);
    }

    @Override
    public List<InvoicePayment> getChargebacksByPaymentAttemptId(final UUID paymentAttemptId) {
        return dao.getChargebacksByPaymentAttemptId(paymentAttemptId);
    }

    @Override
    public InvoicePayment getChargebackById(final UUID chargebackId) throws InvoiceApiException {
        return dao.getChargebackById(chargebackId);
    }

    @Override
    public UUID getAccountIdFromInvoicePaymentId(final UUID invoicePaymentId) throws InvoiceApiException {
        return dao.getAccountIdFromInvoicePaymentId(invoicePaymentId);
    }

    @Override
    public InvoicePayment createRefund(UUID paymentAttemptId,
            BigDecimal amount, boolean isInvoiceAdjusted, UUID paymentCookieId, CallContext context)
            throws InvoiceApiException {
        return dao.createRefund(paymentAttemptId, amount, isInvoiceAdjusted, paymentCookieId, context);
    }
}
