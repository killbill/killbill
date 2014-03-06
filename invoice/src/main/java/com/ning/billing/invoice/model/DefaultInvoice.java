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

package com.ning.billing.invoice.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.entity.EntityBase;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.calculator.InvoiceCalculatorUtils;
import com.ning.billing.invoice.dao.InvoiceItemModelDao;
import com.ning.billing.invoice.dao.InvoiceModelDao;
import com.ning.billing.invoice.dao.InvoicePaymentModelDao;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;

public class DefaultInvoice extends EntityBase implements Invoice {

    private final List<InvoiceItem> invoiceItems = new ArrayList<InvoiceItem>();
    private final List<InvoicePayment> payments = new ArrayList<InvoicePayment>();
    private final UUID accountId;
    private final Integer invoiceNumber;
    private final LocalDate invoiceDate;
    private final LocalDate targetDate;
    private final Currency currency;
    private final boolean migrationInvoice;

    private final Currency processedCurrency;

    // Used to create a new invoice
    public DefaultInvoice(final UUID accountId, final LocalDate invoiceDate, final LocalDate targetDate, final Currency currency) {
        this(UUID.randomUUID(), accountId, null, invoiceDate, targetDate, currency, false);
    }

    public DefaultInvoice(final UUID invoiceId, final UUID accountId, @Nullable final Integer invoiceNumber, final LocalDate invoiceDate,
                          final LocalDate targetDate, final Currency currency, final boolean isMigrationInvoice) {
        this(invoiceId, null, accountId, invoiceNumber, invoiceDate, targetDate, currency, currency, isMigrationInvoice);
    }

    // Used to hydrate invoice from persistence layer
    public DefaultInvoice(final UUID invoiceId, @Nullable final DateTime createdDate, final UUID accountId,
                          @Nullable final Integer invoiceNumber, final LocalDate invoiceDate,
                          final LocalDate targetDate, final Currency currency, final Currency processedCurrency, final boolean isMigrationInvoice) {
        super(invoiceId, createdDate, createdDate);
        this.accountId = accountId;
        this.invoiceNumber = invoiceNumber;
        this.invoiceDate = invoiceDate;
        this.targetDate = targetDate;
        this.currency = currency;
        this.processedCurrency = processedCurrency;
        this.migrationInvoice = isMigrationInvoice;
    }

    public DefaultInvoice(final InvoiceModelDao invoiceModelDao) {
        this(invoiceModelDao.getId(), invoiceModelDao.getCreatedDate(), invoiceModelDao.getAccountId(),
             invoiceModelDao.getInvoiceNumber(), invoiceModelDao.getInvoiceDate(), invoiceModelDao.getTargetDate(),
             invoiceModelDao.getCurrency(), invoiceModelDao.getProcessedCurrency(), invoiceModelDao.isMigrated());
        addInvoiceItems(Collections2.transform(invoiceModelDao.getInvoiceItems(), new Function<InvoiceItemModelDao, InvoiceItem>() {
            @Override
            public InvoiceItem apply(final InvoiceItemModelDao input) {
                return InvoiceItemFactory.fromModelDao(input);
            }
        }));
        addPayments(Collections2.transform(invoiceModelDao.getInvoicePayments(), new Function<InvoicePaymentModelDao, InvoicePayment>() {
            @Override
            public InvoicePayment apply(final InvoicePaymentModelDao input) {
                return new DefaultInvoicePayment(input);
            }
        }));
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
        return InvoiceCalculatorUtils.computeInvoiceBalance(currency, invoiceItems, payments);
    }

    @Override
    public String toString() {
        return "DefaultInvoice [items=" + invoiceItems + ", payments=" + payments + ", id=" + id + ", accountId=" + accountId + ", invoiceDate=" + invoiceDate + ", targetDate=" + targetDate + ", currency=" + currency + ", amountPaid=" + getPaidAmount() + "]";
    }
}

