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

package com.ning.billing.invoice.api;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.InvoicePayment.InvoicePaymentType;
import com.ning.billing.invoice.model.DefaultInvoicePayment;
import com.ning.billing.util.callcontext.CallContext;

public class MockInvoicePaymentApi implements InvoicePaymentApi {
    private final CopyOnWriteArrayList<Invoice> invoices = new CopyOnWriteArrayList<Invoice>();
    private final CopyOnWriteArrayList<InvoicePayment> invoicePayments = new CopyOnWriteArrayList<InvoicePayment>();

    public void add(final Invoice invoice) {
        invoices.add(invoice);
    }

    @Override
    public void notifyOfPaymentAttempt(final InvoicePayment invoicePayment, final CallContext context) {
        for (final InvoicePayment existingInvoicePayment : invoicePayments) {
            if (existingInvoicePayment.getInvoiceId().equals(invoicePayment.getInvoiceId()) && existingInvoicePayment.getPaymentAttemptId().equals(invoicePayment.getPaymentAttemptId())) {
                invoicePayments.remove(existingInvoicePayment);
            }
        }
        invoicePayments.add(invoicePayment);
    }

    @Override
    public List<Invoice> getAllInvoicesByAccount(final UUID accountId) {
        final ArrayList<Invoice> result = new ArrayList<Invoice>();

        for (final Invoice invoice : invoices) {
            if (accountId.equals(invoice.getAccountId())) {
                result.add(invoice);
            }
        }
        return result;
    }

    @Override
    public Invoice getInvoice(final UUID invoiceId) {
        for (final Invoice invoice : invoices) {
            if (invoiceId.equals(invoice.getId())) {
                return invoice;
            }
        }
        return null;
    }

    @Override
    public Invoice getInvoiceForPaymentAttemptId(final UUID paymentAttemptId) {
        for (final InvoicePayment invoicePayment : invoicePayments) {
            if (invoicePayment.getPaymentAttemptId().equals(paymentAttemptId)) {
                return getInvoice(invoicePayment.getInvoiceId());
            }
        }
        return null;
    }

    @Override
    public InvoicePayment getInvoicePayment(final UUID paymentAttemptId) {
        for (final InvoicePayment invoicePayment : invoicePayments) {
            if (paymentAttemptId.equals(invoicePayment.getPaymentAttemptId())) {
                return invoicePayment;
            }
        }
        return null;
    }

    @Override
    public void notifyOfPaymentAttempt(final UUID invoiceId, final BigDecimal amountOutstanding, final Currency currency, final UUID paymentAttemptId, final DateTime paymentAttemptDate, final CallContext context) {
        final InvoicePayment invoicePayment = new DefaultInvoicePayment(InvoicePaymentType.ATTEMPT, paymentAttemptId, invoiceId, paymentAttemptDate, amountOutstanding, currency);
        notifyOfPaymentAttempt(invoicePayment, context);
    }

    @Override
    public void notifyOfPaymentAttempt(final UUID invoiceId, final UUID paymentAttemptId, final DateTime paymentAttemptDate, final CallContext context) {
        final InvoicePayment invoicePayment = new DefaultInvoicePayment(InvoicePaymentType.ATTEMPT, paymentAttemptId, invoiceId, paymentAttemptDate);
        notifyOfPaymentAttempt(invoicePayment, context);
    }

    @Override
    public InvoicePayment processChargeback(final UUID invoicePaymentId, final BigDecimal amount, final CallContext context) throws InvoiceApiException {
        InvoicePayment existingPayment = null;
        for (final InvoicePayment payment : invoicePayments) {
            if (payment.getId() == invoicePaymentId) {
                existingPayment = payment;
                break;
            }
        }

        if (existingPayment != null) {
            invoicePayments.add(new DefaultInvoicePayment(UUID.randomUUID(), InvoicePaymentType.CHARGED_BACK, null, null, DateTime.now(DateTimeZone.UTC), amount,
                    Currency.USD, existingPayment.getId()));
        }

        return existingPayment;
    }

    @Override
    public InvoicePayment processChargeback(final UUID invoicePaymentId, final CallContext context) throws InvoiceApiException {
        InvoicePayment existingPayment = null;
        for (final InvoicePayment payment : invoicePayments) {
            if (payment.getId() == invoicePaymentId) {
                existingPayment = payment;
            }
        }

        if (existingPayment != null) {
            this.processChargeback(invoicePaymentId, existingPayment.getAmount(), context);
        }

        return existingPayment;
    }

    @Override
    public BigDecimal getRemainingAmountPaid(final UUID invoicePaymentId) {
        BigDecimal amount = BigDecimal.ZERO;
        for (final InvoicePayment payment : invoicePayments) {
            if (payment.getId().equals(invoicePaymentId)) {
                amount = amount.add(payment.getAmount());
            }

            if (payment.getLinkedInvoicePaymentId().equals(invoicePaymentId)) {
                amount = amount.add(payment.getAmount());
            }
        }

        return amount;
    }

    @Override
    public List<InvoicePayment> getChargebacksByAccountId(final UUID accountId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UUID getAccountIdFromInvoicePaymentId(final UUID uuid) throws InvoiceApiException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<InvoicePayment> getChargebacksByPaymentAttemptId(final UUID paymentAttemptId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public InvoicePayment getChargebackById(final UUID chargebackId) {
        throw new UnsupportedOperationException();
    }
}
