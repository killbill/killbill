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

package com.ning.billing.invoice.model;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.DefaultClock;
import org.joda.time.DateTime;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.util.clock.DefaultClock;

public class DefaultInvoice implements Invoice {
    private final InvoiceItemList invoiceItems = new InvoiceItemList();
    private final List<InvoicePayment> payments = new ArrayList<InvoicePayment>();
    private final UUID id;
    private final UUID accountId;
    private final DateTime invoiceDate;
    private final DateTime targetDate;
    private final Currency currency;

    public DefaultInvoice(UUID accountId, DateTime targetDate, Currency currency, Clock clock) {
        this(UUID.randomUUID(), accountId, clock.getUTCNow(), targetDate, currency);
    }

    public DefaultInvoice(UUID invoiceId, UUID accountId, DateTime invoiceDate, DateTime targetDate,
                          Currency currency) {
        this.id = invoiceId;
        this.accountId = accountId;
        this.invoiceDate = invoiceDate;
        this.targetDate = targetDate;
        this.currency = currency;
    }

    @Override
    public boolean addInvoiceItem(final InvoiceItem item) {
        return invoiceItems.add(item);
    }

    @Override
    public boolean addInvoiceItems(final List<InvoiceItem> items) {
        return this.invoiceItems.addAll(items);
    }

    @Override
    public List<InvoiceItem> getInvoiceItems() {
        return invoiceItems;
    }

    @Override
    public List<InvoiceItem> getInvoiceItems(Class clazz) {
        List<InvoiceItem> results = new ArrayList<InvoiceItem>();
        for (InvoiceItem item : invoiceItems) {
            if (item.getClass() == clazz) {
                results.add(item);
            }
        }
        return results;
    }

    @Override
    public int getNumberOfItems() {
        return invoiceItems.size();
    }

    @Override
    public boolean addPayment(final InvoicePayment payment) {
        return payments.add(payment);
    }

    @Override
    public boolean addPayments(final List<InvoicePayment> payments) {
        return this.payments.addAll(payments);
    }

    @Override
    public List<InvoicePayment> getPayments() {
        return payments;
    }

    @Override
    public int getNumberOfPayments() {
        return payments.size();
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public UUID getAccountId() {
        return accountId;
    }

    @Override
    public DateTime getInvoiceDate() {
        return invoiceDate;
    }

    @Override
    public DateTime getTargetDate() {
        return targetDate;
    }

    @Override
    public Currency getCurrency() {
        return currency;
    }

    @Override
    public DateTime getLastPaymentAttempt() {
        DateTime lastPaymentAttempt = null;

        for (final InvoicePayment paymentAttempt : payments) {
            DateTime paymentAttemptDate = paymentAttempt.getPaymentAttemptDate();
            if (lastPaymentAttempt == null) {
                lastPaymentAttempt = paymentAttemptDate;
            }

            if (lastPaymentAttempt.isBefore(paymentAttemptDate)) {
                lastPaymentAttempt = paymentAttemptDate;
            }
        }

        return lastPaymentAttempt;
    }

    @Override
    public BigDecimal getAmountPaid() {
        BigDecimal amountPaid = BigDecimal.ZERO;
        for (final InvoicePayment payment : payments) {
            if (payment.getAmount() != null) {
                amountPaid = amountPaid.add(payment.getAmount());
            }
        }
        return amountPaid;
    }

    @Override
    public BigDecimal getTotalAmount() {
        return invoiceItems.getTotalAmount();
    }

    @Override
    public BigDecimal getBalance() {
        return getTotalAmount().subtract(getAmountPaid());
    }

    @Override
    public boolean isDueForPayment(final DateTime targetDate, final int numberOfDays) {
        if (getTotalAmount().compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }

        DateTime lastPaymentAttempt = getLastPaymentAttempt();
        if (lastPaymentAttempt == null) {
            return true;
        }

        return !lastPaymentAttempt.plusDays(numberOfDays).isAfter(targetDate);
    }

    @Override
    public String toString() {
        return "DefaultInvoice [items=" + invoiceItems + ", payments=" + payments + ", id=" + id + ", accountId=" + accountId + ", invoiceDate=" + invoiceDate + ", targetDate=" + targetDate + ", currency=" + currency + ", amountPaid=" + getAmountPaid() + ", lastPaymentAttempt=" + getLastPaymentAttempt() + "]";
    }

}

