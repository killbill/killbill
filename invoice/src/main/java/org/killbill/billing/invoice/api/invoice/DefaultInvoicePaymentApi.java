/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.invoice.api.invoice;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.killbill.billing.ObjectType;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoicePayment;
import org.killbill.billing.invoice.api.InvoicePaymentApi;
import org.killbill.billing.invoice.api.InvoicePaymentType;
import org.killbill.billing.invoice.dao.InvoiceDao;
import org.killbill.billing.invoice.dao.InvoiceModelDao;
import org.killbill.billing.invoice.dao.InvoicePaymentModelDao;
import org.killbill.billing.invoice.model.DefaultInvoice;
import org.killbill.billing.invoice.model.DefaultInvoicePayment;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

public class DefaultInvoicePaymentApi implements InvoicePaymentApi {

    private final InvoiceDao dao;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public DefaultInvoicePaymentApi(final InvoiceDao dao, final InternalCallContextFactory internalCallContextFactory) {
        this.dao = dao;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public List<Invoice> getAllInvoicesByAccount(final UUID accountId, final TenantContext context) {
        return ImmutableList.<Invoice>copyOf(Collections2.transform(dao.getAllInvoicesByAccount(internalCallContextFactory.createInternalTenantContext(accountId, context)),
                                                                    new Function<InvoiceModelDao, Invoice>() {
                                                                        @Override
                                                                        public Invoice apply(final InvoiceModelDao input) {
                                                                            return new DefaultInvoice(input);
                                                                        }
                                                                    }));
    }

    @Override
    public Invoice getInvoice(final UUID invoiceId, final TenantContext context) throws InvoiceApiException {
        return new DefaultInvoice(dao.getById(invoiceId, internalCallContextFactory.createInternalTenantContext(invoiceId, ObjectType.INVOICE, context)));
    }

    @Override
    public List<InvoicePayment> getInvoicePayments(final UUID paymentId, final TenantContext context) {
        return ImmutableList.<InvoicePayment>copyOf(Collections2.transform(dao.getInvoicePayments(paymentId, internalCallContextFactory.createInternalTenantContext(paymentId, ObjectType.PAYMENT, context)),
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
        return dao.getRemainingAmountPaid(invoicePaymentId, internalCallContextFactory.createInternalTenantContext(invoicePaymentId, ObjectType.INVOICE_PAYMENT, context));
    }

    @Override
    public List<InvoicePayment> getChargebacksByAccountId(final UUID accountId, final TenantContext context) {
        return ImmutableList.<InvoicePayment>copyOf(Collections2.transform(dao.getChargebacksByAccountId(accountId, internalCallContextFactory.createInternalTenantContext(accountId, context)),
                                                                           new Function<InvoicePaymentModelDao, InvoicePayment>() {
                                                                               @Override
                                                                               public InvoicePayment apply(final InvoicePaymentModelDao input) {
                                                                                   return new DefaultInvoicePayment(input);
                                                                               }
                                                                           }));
    }

    @Override
    public List<InvoicePayment> getChargebacksByPaymentId(final UUID paymentId, final TenantContext context) {
        return ImmutableList.<InvoicePayment>copyOf(Collections2.transform(dao.getChargebacksByPaymentId(paymentId, internalCallContextFactory.createInternalTenantContext(paymentId, ObjectType.PAYMENT, context)),
                                                                           new Function<InvoicePaymentModelDao, InvoicePayment>() {
                                                                               @Override
                                                                               public InvoicePayment apply(final InvoicePaymentModelDao input) {
                                                                                   return new DefaultInvoicePayment(input);
                                                                               }
                                                                           }));
    }

    @Override
    public InvoicePayment getChargebackById(final UUID chargebackId, final TenantContext context) throws InvoiceApiException {
        return new DefaultInvoicePayment(dao.getChargebackById(chargebackId, internalCallContextFactory.createInternalTenantContext(chargebackId, ObjectType.INVOICE_PAYMENT, context)));
    }

    @Override
    public UUID getAccountIdFromInvoicePaymentId(final UUID invoicePaymentId, final TenantContext context) throws InvoiceApiException {
        return dao.getAccountIdFromInvoicePaymentId(invoicePaymentId, internalCallContextFactory.createInternalTenantContext(invoicePaymentId, ObjectType.INVOICE_PAYMENT, context));
    }

    @Override
    public InvoicePayment createChargeback(final UUID invoicePaymentId, final CallContext context) throws InvoiceApiException {
        return createChargeback(invoicePaymentId, null, context);
    }

    @Override
    public InvoicePayment createChargeback(final UUID invoicePaymentId, final BigDecimal amount, final CallContext context) throws InvoiceApiException {
        return new DefaultInvoicePayment(dao.postChargeback(invoicePaymentId, amount, internalCallContextFactory.createInternalCallContext(invoicePaymentId, ObjectType.INVOICE_PAYMENT, context)));
    }
}
