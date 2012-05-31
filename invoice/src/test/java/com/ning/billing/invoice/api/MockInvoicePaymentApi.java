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

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.model.DefaultInvoicePayment;
import com.ning.billing.util.callcontext.CallContext;
import org.joda.time.DateTimeZone;

public class MockInvoicePaymentApi implements InvoicePaymentApi
{
    private final CopyOnWriteArrayList<Invoice> invoices = new CopyOnWriteArrayList<Invoice>();
    private final CopyOnWriteArrayList<InvoicePayment> invoicePayments = new CopyOnWriteArrayList<InvoicePayment>();

    public void add(Invoice invoice) {
        invoices.add(invoice);
    }

    @Override
    public void notifyOfPaymentAttempt(InvoicePayment invoicePayment, CallContext context) {
        for (InvoicePayment existingInvoicePayment : invoicePayments) {
            if (existingInvoicePayment.getInvoiceId().equals(invoicePayment.getInvoiceId()) && existingInvoicePayment.getPaymentAttemptId().equals(invoicePayment.getPaymentAttemptId())) {
                invoicePayments.remove(existingInvoicePayment);
            }
        }
        invoicePayments.add(invoicePayment);
    }

    @Override
    public List<Invoice> getAllInvoicesByAccount(UUID accountId) {
        ArrayList<Invoice> result = new ArrayList<Invoice>();

        for (Invoice invoice : invoices) {
            if (accountId.equals(invoice.getAccountId())) {
                result.add(invoice);
            }
        }
        return result;
    }

    @Override
    public Invoice getInvoice(UUID invoiceId) {
        for (Invoice invoice : invoices) {
            if (invoiceId.equals(invoice.getId())) {
                return invoice;
            }
        }
        return null;
    }

    @Override
    public Invoice getInvoiceForPaymentAttemptId(UUID paymentAttemptId) {
        for (InvoicePayment invoicePayment : invoicePayments) {
            if (invoicePayment.getPaymentAttemptId().equals(paymentAttemptId)) {
                return getInvoice(invoicePayment.getInvoiceId());
            }
        }
        return null;
    }

    @Override
    public InvoicePayment getInvoicePayment(UUID paymentAttemptId) {
        for (InvoicePayment invoicePayment : invoicePayments) {
            if (paymentAttemptId.equals(invoicePayment.getPaymentAttemptId())) {
                return invoicePayment;
            }
        }
        return null;
    }

    @Override
    public void notifyOfPaymentAttempt(UUID invoiceId, BigDecimal amountOutstanding, Currency currency, UUID paymentAttemptId, DateTime paymentAttemptDate, CallContext context) {
        InvoicePayment invoicePayment = new DefaultInvoicePayment(paymentAttemptId, invoiceId, paymentAttemptDate, amountOutstanding, currency);
        notifyOfPaymentAttempt(invoicePayment, context);
    }

    @Override
    public void notifyOfPaymentAttempt(UUID invoiceId, UUID paymentAttemptId, DateTime paymentAttemptDate, CallContext context) {
        InvoicePayment invoicePayment = new DefaultInvoicePayment(paymentAttemptId, invoiceId, paymentAttemptDate);
        notifyOfPaymentAttempt(invoicePayment, context);
    }

    @Override
    public void processChargeback(UUID invoicePaymentId, BigDecimal amount, CallContext context) throws InvoiceApiException {
        InvoicePayment existingPayment = null;
        for (InvoicePayment payment : invoicePayments) {
            if (payment.getId()  == invoicePaymentId) {
                existingPayment = payment;
            }
        }

        if (existingPayment != null) {
            invoicePayments.add(existingPayment.asChargeBack(amount, DateTime.now(DateTimeZone.UTC)));
        }
    }

    @Override
    public void processChargeback(UUID invoicePaymentId, CallContext context) throws InvoiceApiException {
        InvoicePayment existingPayment = null;
        for (InvoicePayment payment : invoicePayments) {
            if (payment.getId()  == invoicePaymentId) {
                existingPayment = payment;
            }
        }

        if (existingPayment != null) {
            this.processChargeback(invoicePaymentId, existingPayment.getAmount(), context);
        }
    }

    @Override
    public BigDecimal getRemainingAmountPaid(UUID invoicePaymentId) {
        BigDecimal amount = BigDecimal.ZERO;
        for (InvoicePayment payment : invoicePayments) {
            if (payment.getId().equals(invoicePaymentId)) {
                amount = amount.add(payment.getAmount());
            }

            if (payment.getReversedInvoicePaymentId().equals(invoicePaymentId)) {
                amount = amount.add(payment.getAmount());
            }
        }

        return amount;
    }

    @Override
    public List<InvoicePayment> getChargebacksByAccountId(UUID accountId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UUID getAccountIdFromInvoicePaymentId(UUID uuid) throws InvoiceApiException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<InvoicePayment> getChargebacksByPaymentAttemptId(UUID paymentAttemptId) {
        throw new UnsupportedOperationException();
    }
}
