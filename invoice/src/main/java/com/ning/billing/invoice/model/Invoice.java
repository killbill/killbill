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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.util.eventbus.IEventBusType;

public class Invoice implements IEventBusType {
    private final InvoiceItemList items = new InvoiceItemList();
    private final UUID invoiceId;
    private UUID accountId;
    private final DateTime invoiceDate;
    private Currency currency;

    public Invoice() {
        this.invoiceId = UUID.randomUUID();
        this.invoiceDate = new DateTime();
    }

    public Invoice(UUID accountId, Currency currency) {
        this.invoiceId = UUID.randomUUID();
        this.accountId = accountId;
        this.invoiceDate = new DateTime();
        this.currency = currency;
    }

    public Invoice(UUID accountId, List<InvoiceItem> items, Currency currency) {
        this.invoiceId = UUID.randomUUID();
        this.accountId = accountId;
        this.invoiceDate = new DateTime();
        this.currency = currency;
        this.items.addAll(items);
    }

    public boolean add(InvoiceItem item) {
        return items.add(item);
    }

    public boolean add(List<InvoiceItem> items) {
        return this.items.addAll(items);
    }

    public List<InvoiceItem> getItems() {
        return items;
    }

    public int getNumberOfItems() {
        return items.size();
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public DateTime getInvoiceDate() {
        return invoiceDate;
    }

    public Currency getCurrency() {
        return currency;
    }

    public BigDecimal getTotalAmount() {
        return items.getTotalAmount();
    }

    @Override
    public String toString() {
        return "Invoice [items=" + items + ", invoiceId=" + invoiceId + ", accountId=" + accountId + ", invoiceDate=" + invoiceDate + ", currency=" + currency + "]";
    }

}

