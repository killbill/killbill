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

package com.ning.billing.invoice.template.formatters;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.api.formatters.InvoiceFormatter;
import com.ning.billing.invoice.model.CreditAdjInvoiceItem;
import com.ning.billing.invoice.model.CreditBalanceAdjInvoiceItem;
import com.ning.billing.util.template.translation.TranslatorConfig;

import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import static com.ning.billing.util.DefaultAmountFormatter.round;

/**
 * Format invoice fields
 */
public class DefaultInvoiceFormatter implements InvoiceFormatter {

    private final TranslatorConfig config;
    private final Invoice invoice;
    private final DateTimeFormatter dateFormatter;
    private final Locale locale;

    public DefaultInvoiceFormatter(final TranslatorConfig config, final Invoice invoice, final Locale locale) {
        this.config = config;
        this.invoice = invoice;
        dateFormatter = DateTimeFormat.mediumDate().withLocale(locale);
        this.locale = locale;
    }

    @Override
    public Integer getInvoiceNumber() {
        return Objects.firstNonNull(invoice.getInvoiceNumber(), 0);
    }

    @Override
    public List<InvoiceItem> getInvoiceItems() {
        final List<InvoiceItem> invoiceItems = new ArrayList<InvoiceItem>();

        InvoiceItem mergedCBAItem = null;
        InvoiceItem mergedInvoiceAdjustment = null;
        for (final InvoiceItem item : invoice.getInvoiceItems()) {
            if (InvoiceItemType.CBA_ADJ.equals(item.getInvoiceItemType())) {
                // Merge CBA items to avoid confusing the customer, since these are internal
                // adjustments (auto generated)
                mergedCBAItem = mergeCBAItem(invoiceItems, mergedCBAItem, item);
            } else if (InvoiceItemType.REFUND_ADJ.equals(item.getInvoiceItemType()) ||
                       InvoiceItemType.CREDIT_ADJ.equals(item.getInvoiceItemType())) {
                // Merge refund adjustments and credit adjustments, as these are both
                // the same for the customer (invoice adjustment)
                mergedInvoiceAdjustment = mergeInvoiceAdjustmentItem(invoiceItems, mergedInvoiceAdjustment, item);
            } else {
                invoiceItems.add(item);
            }
        }
        // Don't display adjustments of zero
        if (mergedCBAItem != null && mergedCBAItem.getAmount().compareTo(BigDecimal.ZERO) != 0) {
            invoiceItems.add(mergedCBAItem);
        }
        if (mergedInvoiceAdjustment != null && mergedInvoiceAdjustment.getAmount().compareTo(BigDecimal.ZERO) != 0) {
            invoiceItems.add(mergedInvoiceAdjustment);
        }

        final List<InvoiceItem> formatters = new ArrayList<InvoiceItem>();
        for (final InvoiceItem item : invoiceItems) {
            formatters.add(new DefaultInvoiceItemFormatter(config, item, dateFormatter, locale));
        }
        return formatters;
    }

    private InvoiceItem mergeCBAItem(final List<InvoiceItem> invoiceItems, InvoiceItem mergedCBAItem, final InvoiceItem item) {
        if (mergedCBAItem == null) {
            mergedCBAItem = item;
        } else {
            // This is really just to be safe - they should always have the same currency
            if (!mergedCBAItem.getCurrency().equals(item.getCurrency())) {
                invoiceItems.add(item);
            } else {
                mergedCBAItem = new CreditBalanceAdjInvoiceItem(invoice.getId(), invoice.getAccountId(), invoice.getInvoiceDate(),
                                                                mergedCBAItem.getAmount().add(item.getAmount()), mergedCBAItem.getCurrency());
            }
        }
        return mergedCBAItem;
    }

    private InvoiceItem mergeInvoiceAdjustmentItem(final List<InvoiceItem> invoiceItems, InvoiceItem mergedInvoiceAdjustment, final InvoiceItem item) {
        if (mergedInvoiceAdjustment == null) {
            mergedInvoiceAdjustment = item;
        } else {
            // This is really just to be safe - they should always have the same currency
            if (!mergedInvoiceAdjustment.getCurrency().equals(item.getCurrency())) {
                invoiceItems.add(item);
            } else {
                mergedInvoiceAdjustment = new CreditAdjInvoiceItem(invoice.getId(), invoice.getAccountId(), invoice.getInvoiceDate(),
                                                                   mergedInvoiceAdjustment.getAmount().add(item.getAmount()), mergedInvoiceAdjustment.getCurrency());
            }
        }
        return mergedInvoiceAdjustment;
    }

    @Override
    public boolean addInvoiceItem(final InvoiceItem item) {
        return invoice.addInvoiceItem(item);
    }

    @Override
    public boolean addInvoiceItems(final Collection<InvoiceItem> items) {
        return invoice.addInvoiceItems(items);
    }

    @Override
    public <T extends InvoiceItem> List<InvoiceItem> getInvoiceItems(final Class<T> clazz) {
        return Objects.firstNonNull(invoice.getInvoiceItems(clazz), ImmutableList.<InvoiceItem>of());
    }

    @Override
    public int getNumberOfItems() {
        return invoice.getNumberOfItems();
    }

    @Override
    public boolean addPayment(final InvoicePayment payment) {
        return invoice.addPayment(payment);
    }

    @Override
    public boolean addPayments(final Collection<InvoicePayment> payments) {
        return invoice.addPayments(payments);
    }

    @Override
    public List<InvoicePayment> getPayments() {
        return Objects.firstNonNull(invoice.getPayments(), ImmutableList.<InvoicePayment>of());
    }

    @Override
    public int getNumberOfPayments() {
        return invoice.getNumberOfPayments();
    }

    @Override
    public UUID getAccountId() {
        return invoice.getAccountId();
    }

    @Override
    public BigDecimal getChargedAmount() {
        return round(Objects.firstNonNull(invoice.getChargedAmount(), BigDecimal.ZERO));
    }

    @Override
    public BigDecimal getCBAAmount() {
        return round(Objects.firstNonNull(invoice.getCBAAmount(), BigDecimal.ZERO));
    }

    @Override
    public BigDecimal getBalance() {
        return round(Objects.firstNonNull(invoice.getBalance(), BigDecimal.ZERO));
    }

    @Override
    public String getFormattedChargedAmount() {
        final NumberFormat number = NumberFormat.getCurrencyInstance(locale);
        return number.format(getChargedAmount().doubleValue());
    }

    @Override
    public String getFormattedPaidAmount() {
        final NumberFormat number = NumberFormat.getCurrencyInstance(locale);
        return number.format(getPaidAmount().doubleValue());
    }

    @Override
    public String getFormattedBalance() {
        final NumberFormat number = NumberFormat.getCurrencyInstance(locale);
        return number.format(getBalance().doubleValue());
    }

    @Override
    public boolean isMigrationInvoice() {
        return invoice.isMigrationInvoice();
    }

    @Override
    public LocalDate getInvoiceDate() {
        return invoice.getInvoiceDate();
    }

    @Override
    public LocalDate getTargetDate() {
        return invoice.getTargetDate();
    }

    @Override
    public Currency getCurrency() {
        return invoice.getCurrency();
    }

    @Override
    public BigDecimal getPaidAmount() {
        return round(Objects.firstNonNull(invoice.getPaidAmount(), BigDecimal.ZERO));
    }

    @Override
    public String getFormattedInvoiceDate() {
        final LocalDate invoiceDate = invoice.getInvoiceDate();
        if (invoiceDate == null) {
            return "";
        } else {
            return Strings.nullToEmpty(invoiceDate.toString(dateFormatter));
        }
    }

    @Override
    public UUID getId() {
        return invoice.getId();
    }

    @Override
    public DateTime getCreatedDate() {
        return invoice.getCreatedDate();
    }

    @Override
    public DateTime getUpdatedDate() {
        return invoice.getUpdatedDate();
    }

    // Expose the fields for children classes. This is useful for further customization of the invoices

    @SuppressWarnings("UnusedDeclaration")
    protected TranslatorConfig getConfig() {
        return config;
    }

    @SuppressWarnings("UnusedDeclaration")
    protected DateTimeFormatter getDateFormatter() {
        return dateFormatter;
    }

    @SuppressWarnings("UnusedDeclaration")
    protected Locale getLocale() {
        return locale;
    }

    protected Invoice getInvoice() {
        return invoice;
    }

    @Override
    public BigDecimal getTotalAdjAmount() {
        return round(Objects.firstNonNull(invoice.getTotalAdjAmount(), BigDecimal.ZERO));
    }

    @Override
    public BigDecimal getCreditAdjAmount() {
        return round(Objects.firstNonNull(invoice.getCreditAdjAmount(), BigDecimal.ZERO));
    }

    @Override
    public BigDecimal getRefundAdjAmount() {
        return round(Objects.firstNonNull(invoice.getRefundAdjAmount(), BigDecimal.ZERO));
    }
}
