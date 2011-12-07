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
import com.ning.billing.util.clock.DefaultClock;
import org.joda.time.DateTime;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DefaultInvoice implements Invoice {
    private final InvoiceItemList items = new InvoiceItemList();
    private final UUID id;
    private UUID accountId;
    private final DateTime invoiceDate;
    private final DateTime targetDate;
    private Currency currency;
    private BigDecimal amountPaid;
    private DateTime lastPaymentAttempt;

    public DefaultInvoice(UUID accountId, DateTime targetDate, Currency currency) {
        this(UUID.randomUUID(), accountId, new DefaultClock().getUTCNow(), targetDate, currency, null, BigDecimal.ZERO, new ArrayList<InvoiceItem>());
    }

    public DefaultInvoice(UUID invoiceId, UUID accountId, DateTime invoiceDate, DateTime targetDate,
                          Currency currency, DateTime lastPaymentAttempt, BigDecimal amountPaid) {
        this(invoiceId, accountId, invoiceDate, targetDate, currency, lastPaymentAttempt, amountPaid, new ArrayList<InvoiceItem>());
    }

    public DefaultInvoice(UUID invoiceId, UUID accountId, DateTime invoiceDate, DateTime targetDate,
                          Currency currency, DateTime lastPaymentAttempt, BigDecimal amountPaid,
                          List<InvoiceItem> invoiceItems) {
        this.id = invoiceId;
        this.accountId = accountId;
        this.invoiceDate = invoiceDate;
        this.targetDate = targetDate;
        this.currency = currency;
        this.lastPaymentAttempt= lastPaymentAttempt;
        this.amountPaid = amountPaid;
        this.items.addAll(invoiceItems);
    }

    @Override
    public boolean add(InvoiceItem item) {
        return items.add(item);
    }

    @Override
    public boolean add(List<InvoiceItem> items) {
        return this.items.addAll(items);
    }

    @Override
    public List<InvoiceItem> getItems() {
        return items;
    }

    @Override
    public int getNumberOfItems() {
        return items.size();
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
        return lastPaymentAttempt;
    }

    @Override
    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    @Override
    public BigDecimal getTotalAmount() {
        return items.getTotalAmount();
    }

    @Override
    public BigDecimal getAmountOutstanding() {
        return getTotalAmount().subtract(getAmountPaid());
    }
}

