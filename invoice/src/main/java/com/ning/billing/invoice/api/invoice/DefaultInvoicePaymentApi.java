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
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.exceptions.TransactionFailedException;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
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

    private static final WithInvoiceApiException<InvoicePayment> invoicePaymentWithException = new WithInvoiceApiException<InvoicePayment>();

    private final InvoiceDao dao;

    @Inject
    public DefaultInvoicePaymentApi(final InvoiceDao dao) {
        this.dao = dao;
    }

    @Override
    public void notifyOfPayment(final InvoicePayment invoicePayment, final CallContext context) {
        dao.notifyOfPayment(invoicePayment, context);
    }

    @Override
    public List<Invoice> getAllInvoicesByAccount(final UUID accountId) {
        return dao.getAllInvoicesByAccount(accountId);
    }

    @Override
    public Invoice getInvoice(final UUID invoiceId) throws InvoiceApiException {
        return dao.getById(invoiceId);
    }

    @Override
    public Invoice getInvoiceForPaymentId(final UUID paymentId) throws InvoiceApiException {
        final UUID invoiceIdStr = dao.getInvoiceIdByPaymentId(paymentId);
        return invoiceIdStr == null ? null : dao.getById(invoiceIdStr);
    }

    @Override
    public List<InvoicePayment> getInvoicePayments(final UUID paymentId) {
        return dao.getInvoicePayments(paymentId);
    }


    @Override
    public InvoicePayment getInvoicePaymentForAttempt(UUID paymentId) {
        List<InvoicePayment> invoicePayments = dao.getInvoicePayments(paymentId);
        if (invoicePayments.size() == 0) {
            return null;
        }
        return Collections2.filter(invoicePayments, new Predicate<InvoicePayment>() {
            @Override
            public boolean apply(InvoicePayment input) {
                return input.getType() == InvoicePaymentType.ATTEMPT;
            }
        }).iterator().next();
    }


    @Override
    public void notifyOfPayment(final UUID invoiceId, final BigDecimal amount, final Currency currency, final UUID paymentId, final DateTime paymentDate, final CallContext context) {
        final InvoicePayment invoicePayment = new DefaultInvoicePayment(InvoicePaymentType.ATTEMPT, paymentId, invoiceId, paymentDate, amount, currency);
        dao.notifyOfPayment(invoicePayment, context);
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
    public List<InvoicePayment> getChargebacksByPaymentId(final UUID paymentId) {
        return dao.getChargebacksByPaymentId(paymentId);
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
    public InvoicePayment createRefund(final UUID paymentId, final BigDecimal amount, final boolean isInvoiceAdjusted,
                                       final Map<UUID, BigDecimal> invoiceItemIdsWithAmounts, final UUID paymentCookieId,
                                       final CallContext context) throws InvoiceApiException {

        return invoicePaymentWithException.executeAndThrow(new WithInvoiceApiExceptionCallback<InvoicePayment>() {

            @Override
            public InvoicePayment doHandle() throws InvoiceApiException {
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new InvoiceApiException(ErrorCode.PAYMENT_REFUND_AMOUNT_NEGATIVE_OR_NULL);
                }
                return dao.createRefund(paymentId, amount, isInvoiceAdjusted, invoiceItemIdsWithAmounts, paymentCookieId, context);
            }
        });
    }

    @Override
    public InvoicePayment createChargeback(final UUID invoicePaymentId, final CallContext context) throws InvoiceApiException {
        return createChargeback(invoicePaymentId, null, context);
    }


    @Override
    public InvoicePayment createChargeback(final UUID invoicePaymentId, final BigDecimal amount, final CallContext context) throws InvoiceApiException {

        return invoicePaymentWithException.executeAndThrow(new WithInvoiceApiExceptionCallback<InvoicePayment>() {

            @Override
            public InvoicePayment doHandle() throws InvoiceApiException {
                return dao.postChargeback(invoicePaymentId, amount, context);
            }

        });

    }

    //
    // Allow to safely catch TransactionFailedException exceptions and rethrow the correct InvoiceApiException exception
    //
    private interface WithInvoiceApiExceptionCallback<T> {
        public T doHandle() throws InvoiceApiException;
    }

    private static final class WithInvoiceApiException<T> {
        public T executeAndThrow(WithInvoiceApiExceptionCallback<T> callback) throws InvoiceApiException  {

            try {
                return callback.doHandle();
            } catch (TransactionFailedException e) {
                if (e.getCause() instanceof InvoiceApiException) {
                    InvoiceApiException realException = (InvoiceApiException) e.getCause();
                    throw realException;
                } else {
                    throw e;
                }
            }
        }
    }
}
