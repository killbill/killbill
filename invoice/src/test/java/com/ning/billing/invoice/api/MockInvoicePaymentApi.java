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

package com.ning.billing.invoice.api;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.model.DefaultInvoicePayment;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.TenantContext;

public class MockInvoicePaymentApi implements InvoicePaymentApi {

    private final CopyOnWriteArrayList<Invoice> invoices = new CopyOnWriteArrayList<Invoice>();
    private final CopyOnWriteArrayList<InvoicePayment> invoicePayments = new CopyOnWriteArrayList<InvoicePayment>();

    public void add(final Invoice invoice) {
        invoices.add(invoice);
    }

    @Override
    public List<Invoice> getAllInvoicesByAccount(final UUID accountId, final TenantContext context) {
        final ArrayList<Invoice> result = new ArrayList<Invoice>();

        for (final Invoice invoice : invoices) {
            if (accountId.equals(invoice.getAccountId())) {
                result.add(invoice);
            }
        }
        return result;
    }

    @Override
    public Invoice getInvoice(final UUID invoiceId, final TenantContext context) {
        for (final Invoice invoice : invoices) {
            if (invoiceId.equals(invoice.getId())) {
                return invoice;
            }
        }
        return null;
    }

    @Override
    public List<InvoicePayment> getInvoicePayments(final UUID paymentId, final TenantContext context) {
        final List<InvoicePayment> result = new LinkedList<InvoicePayment>();
        for (final InvoicePayment invoicePayment : invoicePayments) {
            if (paymentId.equals(invoicePayment.getPaymentId())) {
                result.add(invoicePayment);
            }
        }
        return result;
    }

    @Override
    public InvoicePayment getInvoicePaymentForAttempt(final UUID paymentId, final TenantContext context) {
        for (final InvoicePayment invoicePayment : invoicePayments) {
            if (paymentId.equals(invoicePayment.getPaymentId()) && invoicePayment.getType() == InvoicePaymentType.ATTEMPT) {
                return invoicePayment;
            }
        }
        return null;
    }

    @Override
    public InvoicePayment createChargeback(final UUID invoicePaymentId, final BigDecimal amount, final CallContext context) throws InvoiceApiException {
        InvoicePayment existingPayment = null;
        for (final InvoicePayment payment : invoicePayments) {
            if (payment.getId() == invoicePaymentId) {
                existingPayment = payment;
                break;
            }
        }

        if (existingPayment != null) {
            invoicePayments.add(new DefaultInvoicePayment(UUID.randomUUID(), InvoicePaymentType.CHARGED_BACK, null, null, DateTime.now(DateTimeZone.UTC), amount,
                                                          existingPayment.getCurrency(), existingPayment.getProcessedCurrency(), null, existingPayment.getId()));
        }

        return existingPayment;
    }

    @Override
    public InvoicePayment createChargeback(final UUID invoicePaymentId, final CallContext context) throws InvoiceApiException {
        InvoicePayment existingPayment = null;
        for (final InvoicePayment payment : invoicePayments) {
            if (payment.getId() == invoicePaymentId) {
                existingPayment = payment;
            }
        }

        if (existingPayment != null) {
            this.createChargeback(invoicePaymentId, existingPayment.getAmount(), context);
        }

        return existingPayment;
    }

    @Override
    public BigDecimal getRemainingAmountPaid(final UUID invoicePaymentId, final TenantContext context) {
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
    public List<InvoicePayment> getChargebacksByAccountId(final UUID accountId, final TenantContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UUID getAccountIdFromInvoicePaymentId(final UUID uuid, final TenantContext context) throws InvoiceApiException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<InvoicePayment> getChargebacksByPaymentId(final UUID paymentId, final TenantContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public InvoicePayment getChargebackById(final UUID chargebackId, final TenantContext context) {
        throw new UnsupportedOperationException();
    }
}
