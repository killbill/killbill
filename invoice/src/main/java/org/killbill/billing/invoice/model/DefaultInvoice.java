/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.invoice.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.entity.EntityBase;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoicePayment;
import org.killbill.billing.invoice.api.InvoiceStatus;
import org.killbill.billing.invoice.calculator.InvoiceCalculatorUtils;
import org.killbill.billing.invoice.dao.InvoiceItemModelDao;
import org.killbill.billing.invoice.dao.InvoiceModelDao;
import org.killbill.billing.invoice.dao.InvoicePaymentModelDao;
import org.killbill.billing.util.UUIDs;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;

public class DefaultInvoice extends EntityBase implements Invoice, Cloneable {

    private final List<InvoiceItem> invoiceItems;
    private final List<InvoicePayment> payments;
    private final UUID accountId;
    private final Integer invoiceNumber;
    private final LocalDate invoiceDate;
    private final LocalDate targetDate;
    private final Currency currency;
    private final boolean migrationInvoice;
    private final boolean isWrittenOff;

    private final Currency processedCurrency;
    private final InvoiceStatus status;
    private final boolean isParentInvoice;
    private final Invoice parentInvoice;



    // Used to create a new invoice
    public DefaultInvoice(final UUID accountId, final LocalDate invoiceDate, final LocalDate targetDate, final Currency currency, final InvoiceStatus status) {
        this(UUIDs.randomUUID(), accountId, null, invoiceDate, targetDate, currency, false, status);
    }

    public DefaultInvoice(final UUID invoiceId, final UUID accountId, @Nullable final Integer invoiceNumber, final LocalDate invoiceDate,
                          final LocalDate targetDate, final Currency currency, final boolean isMigrationInvoice, final InvoiceStatus status) {
        this(invoiceId, null, accountId, invoiceNumber, invoiceDate, targetDate, currency, currency, isMigrationInvoice, false, status, false, null);
    }

    @VisibleForTesting
    public DefaultInvoice(final UUID accountId, final LocalDate invoiceDate, final LocalDate targetDate, final Currency currency) {
        this(UUIDs.randomUUID(), accountId, null, invoiceDate, targetDate, currency, false, InvoiceStatus.COMMITTED);
    }

    // This CTOR is used to return an existing invoice and must include everything (items, payments, tags,..)
    public DefaultInvoice(final InvoiceModelDao invoiceModelDao, @Nullable final Catalog catalog) {
        this(invoiceModelDao.getId(), invoiceModelDao.getCreatedDate(), invoiceModelDao.getAccountId(),
             invoiceModelDao.getInvoiceNumber(), invoiceModelDao.getInvoiceDate(), invoiceModelDao.getTargetDate(),
             invoiceModelDao.getCurrency(), invoiceModelDao.getProcessedCurrency(), invoiceModelDao.isMigrated(),
             invoiceModelDao.isWrittenOff(), invoiceModelDao.getStatus(), invoiceModelDao.isParentInvoice(),
             invoiceModelDao.getParentInvoice());
        addInvoiceItems(Collections2.transform(invoiceModelDao.getInvoiceItems(), new Function<InvoiceItemModelDao, InvoiceItem>() {
            @Override
            public InvoiceItem apply(final InvoiceItemModelDao input) {
                return InvoiceItemFactory.fromModelDaoWithCatalog(input, catalog);
            }
        }));
        addPayments(Collections2.transform(invoiceModelDao.getInvoicePayments(), new Function<InvoicePaymentModelDao, InvoicePayment>() {
            @Override
            public InvoicePayment apply(final InvoicePaymentModelDao input) {
                return new DefaultInvoicePayment(input);
            }
        }));
    }

    public DefaultInvoice(final InvoiceModelDao invoiceModelDao) {
        this(invoiceModelDao, null);
    }

    public DefaultInvoice(final UUID accountId, final LocalDate invoiceDate, final Currency currency) {
        this(UUID.randomUUID(), null, accountId, null, invoiceDate, null, currency, currency, false, false, InvoiceStatus.DRAFT, true, null);
    }

    private DefaultInvoice(final UUID invoiceId, @Nullable final DateTime createdDate, final UUID accountId,
                           @Nullable final Integer invoiceNumber, final LocalDate invoiceDate,
                           @Nullable final LocalDate targetDate, final Currency currency, final Currency processedCurrency,
                           final boolean isMigrationInvoice, final boolean isWrittenOff,
                           final InvoiceStatus status, final boolean isParentInvoice, final InvoiceModelDao parentInvoice) {
        super(invoiceId, createdDate, createdDate);
        this.accountId = accountId;
        this.invoiceNumber = invoiceNumber;
        this.invoiceDate = invoiceDate;
        this.targetDate = targetDate;
        this.currency = currency;
        this.processedCurrency = processedCurrency;
        this.migrationInvoice = isMigrationInvoice;
        this.isWrittenOff = isWrittenOff;
        this.invoiceItems = new ArrayList<InvoiceItem>();
        this.payments = new ArrayList<InvoicePayment>();
        this.status = status;
        this.isParentInvoice = isParentInvoice;
        this.parentInvoice = (parentInvoice != null) ? new DefaultInvoice(parentInvoice) : null;
    }

    // Semi deep copy where we copy the lists but not the elements in the lists since they are immutables.
    @Override
    public Object clone() {
        InvoiceModelDao parentInvoiceModelDao = (parentInvoice != null) ? new InvoiceModelDao(parentInvoice) : null;
        final Invoice clonedInvoice = new DefaultInvoice(getId(),  getCreatedDate(), getAccountId(), getInvoiceNumber(), getInvoiceDate(), getTargetDate(), getCurrency(), getProcessedCurrency(), isMigrationInvoice(), isWrittenOff(), getStatus(), isParentInvoice(), parentInvoiceModelDao);
        clonedInvoice.getInvoiceItems().addAll(getInvoiceItems());
        clonedInvoice.getPayments().addAll(getPayments());
        return clonedInvoice;
    }

    @Override
    public boolean addInvoiceItem(final InvoiceItem item) {
        return invoiceItems.add(item);
    }

    public boolean removeInvoiceItem(final  InvoiceItem item) {
        return invoiceItems.remove(item);
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

    public Currency getProcessedCurrency() {
        return processedCurrency;
    }

    @Override
    public boolean isMigrationInvoice() {
        return migrationInvoice;
    }

    @Override
    public BigDecimal getPaidAmount() {
        return InvoiceCalculatorUtils.computeInvoiceAmountPaid(currency, payments);
    }

    @Override
    public BigDecimal getOriginalChargedAmount() {
        return InvoiceCalculatorUtils.computeInvoiceOriginalAmountCharged(createdDate, currency, invoiceItems);
    }

    @Override
    public BigDecimal getChargedAmount() {
        return InvoiceCalculatorUtils.computeInvoiceAmountCharged(currency, invoiceItems);
    }

    @Override
    public BigDecimal getCreditedAmount() {
        return InvoiceCalculatorUtils.computeInvoiceAmountCredited(currency, invoiceItems);
    }

    @Override
    public BigDecimal getRefundedAmount() {
        return InvoiceCalculatorUtils.computeInvoiceAmountRefunded(currency, payments);
    }

    @Override
    public BigDecimal getBalance() {
        if (isWrittenOff() ||
            isMigrationInvoice() ||
            getStatus() == InvoiceStatus.DRAFT ||
            getStatus() == InvoiceStatus.VOID ||
            hasZeroParentBalance()) {
            return BigDecimal.ZERO;
        } else {
            return InvoiceCalculatorUtils.computeRawInvoiceBalance(currency, invoiceItems, payments);
        }
    }

    public boolean hasZeroParentBalance() {
        return (parentInvoice != null) && (parentInvoice.getBalance().compareTo(BigDecimal.ZERO) == 0);
    }

    public boolean isWrittenOff() {
        return isWrittenOff;
    }

    @Override
    public InvoiceStatus getStatus() {
        return status;
    }

    @Override
    public boolean isParentInvoice() {
        return isParentInvoice;
    }

    @Override
    public UUID getParentAccountId() {
        return parentInvoice != null ? parentInvoice.getAccountId() : null;
    }

    @Override
    public UUID getParentInvoiceId() {
        return parentInvoice != null ? parentInvoice.getId() : null;
    }

    @Override
    public String toString() {
        return "DefaultInvoice [items=" + invoiceItems + ", payments=" + payments + ", id=" + id + ", accountId=" + accountId
               + ", invoiceDate=" + invoiceDate + ", targetDate=" + targetDate + ", currency=" + currency + ", amountPaid=" + getPaidAmount()
               + ", status=" + status + ", isParentInvoice=" + isParentInvoice + "]";
    }

}

