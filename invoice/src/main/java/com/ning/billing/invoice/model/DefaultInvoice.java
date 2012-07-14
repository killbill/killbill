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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.util.entity.EntityBase;

public class DefaultInvoice extends EntityBase implements Invoice {
    private final InvoiceItemList invoiceItems = new InvoiceItemList();
    private final List<InvoicePayment> payments = new ArrayList<InvoicePayment>();
    private final UUID accountId;
    private final Integer invoiceNumber;
    private final LocalDate invoiceDate;
    private final LocalDate targetDate;
    private final Currency currency;
    private final boolean migrationInvoice;

    // Used to create a new invoice
    public DefaultInvoice(final UUID accountId, final LocalDate invoiceDate, final LocalDate targetDate, final Currency currency) {
        this(UUID.randomUUID(), accountId, null, invoiceDate, targetDate, currency, false);
    }

    // Used to hydrate invoice from persistence layer
    public DefaultInvoice(final UUID invoiceId, final UUID accountId, @Nullable final Integer invoiceNumber, final LocalDate invoiceDate,
                          final LocalDate targetDate, final Currency currency, final boolean isMigrationInvoice) {
        super(invoiceId);
        this.accountId = accountId;
        this.invoiceNumber = invoiceNumber;
        this.invoiceDate = invoiceDate;
        this.targetDate = targetDate;
        this.currency = currency;
        this.migrationInvoice = isMigrationInvoice;
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
    public <T extends InvoiceItem> List<InvoiceItem> getInvoiceItems(final Class<T> clazz) {
        final List<InvoiceItem> results = new ArrayList<InvoiceItem>();
        for (final InvoiceItem item : invoiceItems) {
            if (clazz.isInstance(item)) {
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
    public UUID getAccountId() {
        return accountId;
    }

    /**
     * null until retrieved from the database
     *
     * @return the invoice number
     */
    @Override
    public Integer getInvoiceNumber() {
        return invoiceNumber;
    }

    @Override
    public LocalDate getInvoiceDate() {
        return invoiceDate;
    }

    @Override
    public LocalDate getTargetDate() {
        return targetDate;
    }

    @Override
    public Currency getCurrency() {
        return currency;
    }

    @Override
    public boolean isMigrationInvoice() {
        return migrationInvoice;
    }

    @Override
    public BigDecimal getPaidAmount() {
        BigDecimal amountPaid = BigDecimal.ZERO;
        for (final InvoicePayment payment : payments) {
            if (payment.getAmount() != null) {
                amountPaid = amountPaid.add(payment.getAmount());
            }
        }
        return amountPaid;
    }

    @Override
    public BigDecimal getChargedAmount() {
        return invoiceItems.getChargedAmount();
    }

    @Override
    public BigDecimal getCBAAmount() {
        return invoiceItems.getCBAAmount();
    }

    @Override
    public BigDecimal getTotalAdjAmount() {
        return invoiceItems.getTotalAdjAmount();
    }

    @Override
    public BigDecimal getCreditAdjAmount() {
        return invoiceItems.getCreditAdjAmount();
    }

    @Override
    public BigDecimal getRefundAdjAmount() {
        return invoiceItems.getRefundAdjAmount();
    }

    @Override
    public BigDecimal getBalance() {
        return getChargedAmount().add(getTotalAdjAmount()).add(getCBAAmount()).subtract(getPaidAmount());
    }

    @Override
    public String toString() {
        return "DefaultInvoice [items=" + invoiceItems + ", payments=" + payments + ", id=" + id + ", accountId=" + accountId + ", invoiceDate=" + invoiceDate + ", targetDate=" + targetDate + ", currency=" + currency + ", amountPaid=" + getPaidAmount() + "]";
    }
}

