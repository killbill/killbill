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
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.callcontext.TenantContext;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.inject.Inject;

public class DefaultInvoicePaymentApi implements InvoicePaymentApi {

    private static final WithInvoiceApiException<InvoicePayment> invoicePaymentWithException = new WithInvoiceApiException<InvoicePayment>();

    private final InvoiceDao dao;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public DefaultInvoicePaymentApi(final InvoiceDao dao, final InternalCallContextFactory internalCallContextFactory) {
        this.dao = dao;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public void notifyOfPayment(final InvoicePayment invoicePayment, final CallContext context) throws InvoiceApiException {
        // Retrieve the account id for the internal call context
        final Invoice invoice = dao.getById(invoicePayment.getInvoiceId(), internalCallContextFactory.createInternalTenantContext(context));
        final UUID accountId = invoice.getAccountId();
        dao.notifyOfPayment(invoicePayment, internalCallContextFactory.createInternalCallContext(accountId, context));
    }

    @Override
    public List<Invoice> getAllInvoicesByAccount(final UUID accountId, final TenantContext context) {
        return dao.getAllInvoicesByAccount(accountId, internalCallContextFactory.createInternalTenantContext(accountId, context));
    }

    @Override
    public Invoice getInvoice(final UUID invoiceId, final TenantContext context) throws InvoiceApiException {
        return dao.getById(invoiceId, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public Invoice getInvoiceForPaymentId(final UUID paymentId, final TenantContext context) throws InvoiceApiException {
        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(context);
        final UUID invoiceIdStr = dao.getInvoiceIdByPaymentId(paymentId, internalTenantContext);
        return invoiceIdStr == null ? null : dao.getById(invoiceIdStr, internalTenantContext);
    }

    @Override
    public List<InvoicePayment> getInvoicePayments(final UUID paymentId, final TenantContext context) {
        return dao.getInvoicePayments(paymentId, internalCallContextFactory.createInternalTenantContext(context));
    }


    @Override
    public InvoicePayment getInvoicePaymentForAttempt(final UUID paymentId, final TenantContext context) {
        final List<InvoicePayment> invoicePayments = dao.getInvoicePayments(paymentId, internalCallContextFactory.createInternalTenantContext(context));
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
    public void notifyOfPayment(final UUID invoiceId, final BigDecimal amount, final Currency currency, final UUID paymentId, final DateTime paymentDate, final CallContext context) throws InvoiceApiException {
        final InvoicePayment invoicePayment = new DefaultInvoicePayment(InvoicePaymentType.ATTEMPT, paymentId, invoiceId, paymentDate, amount, currency);

        // Retrieve the account id for the internal call context
        final Invoice invoice = dao.getById(invoiceId, internalCallContextFactory.createInternalCallContext(context));
        final UUID accountId = invoice.getAccountId();

        dao.notifyOfPayment(invoicePayment, internalCallContextFactory.createInternalCallContext(accountId, context));
    }

    @Override
    public BigDecimal getRemainingAmountPaid(final UUID invoicePaymentId, final TenantContext context) {
        return dao.getRemainingAmountPaid(invoicePaymentId, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public List<InvoicePayment> getChargebacksByAccountId(final UUID accountId, final TenantContext context) {
        return dao.getChargebacksByAccountId(accountId, internalCallContextFactory.createInternalTenantContext(accountId, context));
    }

    @Override
    public List<InvoicePayment> getChargebacksByPaymentId(final UUID paymentId, final TenantContext context) {
        return dao.getChargebacksByPaymentId(paymentId, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public InvoicePayment getChargebackById(final UUID chargebackId, final TenantContext context) throws InvoiceApiException {
        return dao.getChargebackById(chargebackId, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public UUID getAccountIdFromInvoicePaymentId(final UUID invoicePaymentId, final TenantContext context) throws InvoiceApiException {
        return dao.getAccountIdFromInvoicePaymentId(invoicePaymentId, internalCallContextFactory.createInternalTenantContext(context));
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

                // Retrieve the account id for the internal call context
                final InternalCallContext internalCallContextNoAccountId = internalCallContextFactory.createInternalCallContext(context);
                final List<InvoicePayment> invoicePayments = dao.getInvoicePayments(paymentId, internalCallContextNoAccountId);
                final UUID accountId = dao.getAccountIdFromInvoicePaymentId(invoicePayments.get(0).getId(), internalCallContextNoAccountId);

                return dao.createRefund(paymentId, amount, isInvoiceAdjusted, invoiceItemIdsWithAmounts, paymentCookieId, internalCallContextFactory.createInternalCallContext(accountId, context));
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
                // Retrieve the account id for the internal call context
                final UUID accountId = dao.getAccountIdFromInvoicePaymentId(invoicePaymentId, internalCallContextFactory.createInternalCallContext(context));
                return dao.postChargeback(invoicePaymentId, amount, internalCallContextFactory.createInternalCallContext(accountId, context));
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
        public T executeAndThrow(final WithInvoiceApiExceptionCallback<T> callback) throws InvoiceApiException  {

            try {
                return callback.doHandle();
            } catch (TransactionFailedException e) {
                if (e.getCause() instanceof InvoiceApiException) {
                    throw (InvoiceApiException) e.getCause();
                } else {
                    throw e;
                }
            }
        }
    }
}
