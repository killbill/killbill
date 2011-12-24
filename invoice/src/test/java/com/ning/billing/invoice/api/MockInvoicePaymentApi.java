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

public class MockInvoicePaymentApi implements InvoicePaymentApi
{
    private final CopyOnWriteArrayList<Invoice> invoices = new CopyOnWriteArrayList<Invoice>();
    private final CopyOnWriteArrayList<InvoicePayment> invoicePayments = new CopyOnWriteArrayList<InvoicePayment>();

    public void add(Invoice invoice) {
        invoices.add(invoice);
    }

    @Override
    public void paymentSuccessful(UUID invoiceId, BigDecimal amount, Currency currency, UUID paymentAttemptId, DateTime paymentAttemptDate) {
        InvoicePayment invoicePayment = new InvoicePayment(invoiceId, amount, currency, paymentAttemptId, paymentAttemptDate);

        for (InvoicePayment existingInvoicePayment : invoicePayments) {
            if (existingInvoicePayment.getInvoiceId().equals(invoiceId) && existingInvoicePayment.getPaymentAttemptId().equals(paymentAttemptId)) {
                invoicePayments.remove(existingInvoicePayment);
            }
        }
        invoicePayments.add(invoicePayment);
    }

    @Override
    public List<Invoice> getInvoicesByAccount(UUID accountId) {
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
    public void paymentFailed(UUID invoiceId, UUID paymentAttemptId, DateTime paymentAttemptDate) {
        InvoicePayment invoicePayment = new InvoicePayment(invoiceId, null, null, paymentAttemptId, paymentAttemptDate);
        for (InvoicePayment existingInvoicePayment : invoicePayments) {
            if (existingInvoicePayment.getInvoiceId().equals(invoiceId) && existingInvoicePayment.getPaymentAttemptId().equals(paymentAttemptId)) {
                invoicePayments.remove(invoicePayment);
            }
        }
        invoicePayments.add(invoicePayment);
    }

    private static class InvoicePayment {
        private final UUID invoiceId;
        private final UUID paymentAttemptId;
        private final DateTime paymentAttemptDate;
        private final BigDecimal amount;
        private final Currency currency;

        public InvoicePayment(UUID invoiceId, BigDecimal amount, Currency currency, UUID paymentAttemptId, DateTime paymentAttemptDate) {
            this.invoiceId = invoiceId;
            this.paymentAttemptId = paymentAttemptId;
            this.paymentAttemptDate = paymentAttemptDate;
            this.amount = amount;
            this.currency = currency;
        }

        public UUID getInvoiceId() {
            return invoiceId;
        }

        public UUID getPaymentAttemptId() {
            return paymentAttemptId;
        }

        public DateTime getPaymentAttemptDate() {
            return paymentAttemptDate;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public Currency getCurrency() {
            return currency;
        }

    }
}
