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

package org.killbill.billing.payment;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;

import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.api.InvoicePayment;
import org.killbill.billing.entity.EntityBase;
import org.killbill.billing.invoice.api.InvoiceStatus;

public class MockInvoice extends EntityBase implements Invoice {
    private final List<InvoiceItem> invoiceItems = new ArrayList<InvoiceItem>();
    private final List<InvoicePayment> payments = new ArrayList<InvoicePayment>();
    private final UUID accountId;
    private final Integer invoiceNumber;
    private final LocalDate invoiceDate;
    private final LocalDate targetDate;
    private final Currency currency;
    private final boolean migrationInvoice;
    private final InvoiceStatus status;
    private final boolean parentInvoice;

    // used to create a new invoice
    public MockInvoice(final UUID accountId, final LocalDate invoiceDate, final LocalDate targetDate, final Currency currency) {
        this(UUID.randomUUID(), accountId, null, invoiceDate, targetDate, currency, false, InvoiceStatus.COMMITTED, false);
    }

    // used to hydrate invoice from persistence layer
    public MockInvoice(final UUID invoiceId, final UUID accountId, @Nullable final Integer invoiceNumber, final LocalDate invoiceDate,
                       @Nullable final LocalDate targetDate, final Currency currency, final boolean isMigrationInvoice, final InvoiceStatus status, final boolean parentInvoice) {
        super(invoiceId);
        this.accountId = accountId;
        this.invoiceNumber = invoiceNumber;
        this.invoiceDate = invoiceDate;
        this.targetDate = targetDate;
        this.currency = currency;
        this.migrationInvoice = isMigrationInvoice;
        this.status = status;
        this.parentInvoice = parentInvoice;
    }

    @Override
    public boolean addInvoiceItem(final InvoiceItem item) {
        return invoiceItems.add(item);
    }

    @Override
    public boolean addInvoiceItems(final Collection<InvoiceItem> items) {
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
    public boolean addPayments(final Collection<InvoicePayment> payments) {
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
        BigDecimal result = BigDecimal.ZERO;

        for (final InvoiceItem i : invoiceItems) {
            if (!i.getInvoiceItemType().equals(InvoiceItemType.CBA_ADJ)) {
                result = result.add(i.getAmount());
            }
        }
        return result;
    }


    @Override
    public BigDecimal getCreditedAmount() {
        BigDecimal result = BigDecimal.ZERO;

        for (final InvoiceItem i : invoiceItems) {
            if (i.getInvoiceItemType().equals(InvoiceItemType.CBA_ADJ)) {
                result = result.add(i.getAmount());
            }
        }
        return result;
    }

    @Override
    public BigDecimal getBalance() {
        return getChargedAmount().subtract(getPaidAmount());
    }

    @Override
    public InvoiceStatus getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return "DefaultInvoice [items=" + invoiceItems + ", payments=" + payments + ", id=" + id + ", accountId=" + accountId + ", invoiceDate=" + invoiceDate + ", targetDate=" + targetDate + ", currency=" + currency + ", amountPaid=" + getPaidAmount() + ", status=" + status + ", parentInvoice=" + parentInvoice + "]";
    }

    @Override
    public BigDecimal getRefundedAmount() {
        throw new UnsupportedOperationException();
    }

    @Override
    public BigDecimal getOriginalChargedAmount() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isParentInvoice() {
        return parentInvoice;
    }

    @Override
    public UUID getParentAccountId() {
        return null;
    }

    @Override
    public UUID getParentInvoiceId() {
        return null;
    }
}

