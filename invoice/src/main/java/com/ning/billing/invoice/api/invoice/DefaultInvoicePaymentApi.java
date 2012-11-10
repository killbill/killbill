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

import org.skife.jdbi.v2.exceptions.TransactionFailedException;

import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.api.InvoicePayment.InvoicePaymentType;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.invoice.dao.InvoiceDao;
import com.ning.billing.invoice.dao.InvoiceModelDao;
import com.ning.billing.invoice.dao.InvoicePaymentModelDao;
import com.ning.billing.invoice.model.DefaultInvoice;
import com.ning.billing.invoice.model.DefaultInvoicePayment;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.TenantContext;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
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
    public List<Invoice> getAllInvoicesByAccount(final UUID accountId, final TenantContext context) {
        return ImmutableList.<Invoice>copyOf(Collections2.transform(dao.getAllInvoicesByAccount(accountId, internalCallContextFactory.createInternalTenantContext(context)),
                                                                    new Function<InvoiceModelDao, Invoice>() {
                                                                        @Override
                                                                        public Invoice apply(final InvoiceModelDao input) {
                                                                            return new DefaultInvoice(input);
                                                                        }
                                                                    }));
    }

    @Override
    public Invoice getInvoice(final UUID invoiceId, final TenantContext context) throws InvoiceApiException {
        return new DefaultInvoice(dao.getById(invoiceId, internalCallContextFactory.createInternalTenantContext(context)));
    }

    @Override
    public List<InvoicePayment> getInvoicePayments(final UUID paymentId, final TenantContext context) {
        return ImmutableList.<InvoicePayment>copyOf(Collections2.transform(dao.getInvoicePayments(paymentId, internalCallContextFactory.createInternalTenantContext(context)),
                                                                           new Function<InvoicePaymentModelDao, InvoicePayment>() {
                                                                               @Override
                                                                               public InvoicePayment apply(final InvoicePaymentModelDao input) {
                                                                                   return new DefaultInvoicePayment(input);
                                                                               }
                                                                           }));
    }

    @Override
    public InvoicePayment getInvoicePaymentForAttempt(final UUID paymentId, final TenantContext context) {
        final List<InvoicePayment> invoicePayments = getInvoicePayments(paymentId, context);
        if (invoicePayments.size() == 0) {
            return null;
        }
        return Collections2.filter(invoicePayments, new Predicate<InvoicePayment>() {
            @Override
            public boolean apply(final InvoicePayment input) {
                return input.getType() == InvoicePaymentType.ATTEMPT;
            }
        }).iterator().next();
    }

    @Override
    public BigDecimal getRemainingAmountPaid(final UUID invoicePaymentId, final TenantContext context) {
        return dao.getRemainingAmountPaid(invoicePaymentId, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public List<InvoicePayment> getChargebacksByAccountId(final UUID accountId, final TenantContext context) {
        return ImmutableList.<InvoicePayment>copyOf(Collections2.transform(dao.getChargebacksByAccountId(accountId, internalCallContextFactory.createInternalTenantContext(context)),
                                                                           new Function<InvoicePaymentModelDao, InvoicePayment>() {
                                                                               @Override
                                                                               public InvoicePayment apply(final InvoicePaymentModelDao input) {
                                                                                   return new DefaultInvoicePayment(input);
                                                                               }
                                                                           }));
    }

    @Override
    public List<InvoicePayment> getChargebacksByPaymentId(final UUID paymentId, final TenantContext context) {
        return ImmutableList.<InvoicePayment>copyOf(Collections2.transform(dao.getChargebacksByPaymentId(paymentId, internalCallContextFactory.createInternalTenantContext(context)),
                                                                           new Function<InvoicePaymentModelDao, InvoicePayment>() {
                                                                               @Override
                                                                               public InvoicePayment apply(final InvoicePaymentModelDao input) {
                                                                                   return new DefaultInvoicePayment(input);
                                                                               }
                                                                           }));
    }

    @Override
    public InvoicePayment getChargebackById(final UUID chargebackId, final TenantContext context) throws InvoiceApiException {
        return new DefaultInvoicePayment(dao.getChargebackById(chargebackId, internalCallContextFactory.createInternalTenantContext(context)));
    }

    @Override
    public UUID getAccountIdFromInvoicePaymentId(final UUID invoicePaymentId, final TenantContext context) throws InvoiceApiException {
        return dao.getAccountIdFromInvoicePaymentId(invoicePaymentId, internalCallContextFactory.createInternalTenantContext(context));
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
                final UUID accountId = dao.getAccountIdFromInvoicePaymentId(invoicePaymentId, internalCallContextFactory.createInternalTenantContext(context));
                return new DefaultInvoicePayment(dao.postChargeback(invoicePaymentId, amount, internalCallContextFactory.createInternalCallContext(accountId, context)));
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

        public T executeAndThrow(final WithInvoiceApiExceptionCallback<T> callback) throws InvoiceApiException {

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
