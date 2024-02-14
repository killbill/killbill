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

package org.killbill.billing.invoice.template.formatters;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.UUID;

import org.joda.money.CurrencyUnit;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.currency.api.CurrencyConversion;
import org.killbill.billing.currency.api.CurrencyConversionApi;
import org.killbill.billing.currency.api.CurrencyConversionException;
import org.killbill.billing.currency.api.Rate;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.api.InvoicePayment;
import org.killbill.billing.invoice.api.InvoiceStatus;
import org.killbill.billing.invoice.api.formatters.InvoiceFormatter;
import org.killbill.billing.invoice.model.CreditAdjInvoiceItem;
import org.killbill.billing.invoice.model.CreditBalanceAdjInvoiceItem;
import org.killbill.billing.invoice.model.DefaultInvoice;
import org.killbill.commons.utils.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Format invoice fields
 */
public class DefaultInvoiceFormatter implements InvoiceFormatter {

    private static final Logger logger = LoggerFactory.getLogger(DefaultInvoiceFormatter.class);

    private final String defaultLocale;

    private final String catalogBundlePath;

    private final Invoice invoice;
    private final DateTimeFormatter dateFormatter;
    private final Locale locale;
    private final CurrencyConversionApi currencyConversionApi;

    private final ResourceBundle bundle;

    private final ResourceBundle defaultBundle;

    public DefaultInvoiceFormatter(final String defaultLocale,
                                   final String catalogBundlePath, final Invoice invoice, final Locale locale,
                                   final CurrencyConversionApi currencyConversionApi, final ResourceBundle bundle,
                                   final ResourceBundle defaultBundle) {
        this.defaultLocale = defaultLocale;
        this.catalogBundlePath = catalogBundlePath;
        this.invoice = invoice;
        this.dateFormatter = DateTimeFormat.mediumDate().withLocale(locale);
        this.locale = locale;
        this.currencyConversionApi = currencyConversionApi;
        this.bundle = bundle;
        this.defaultBundle = defaultBundle;
    }

    @Override
    public Integer getInvoiceNumber() {
        return Objects.requireNonNullElse(invoice.getInvoiceNumber(), 0);
    }

    @Override
    public List<InvoiceItem> getInvoiceItems() {
        final List<InvoiceItem> formatters = new ArrayList<InvoiceItem>();
        final List<InvoiceItem> invoiceItems = mergeCBAAndCreditAdjustmentItems();
        for (final InvoiceItem item : invoiceItems) {
            formatters.add(new DefaultInvoiceItemFormatter(defaultLocale, catalogBundlePath, item, dateFormatter, locale, bundle, defaultBundle));
        }
        return formatters;
    }

    protected List<InvoiceItem> mergeCBAAndCreditAdjustmentItems() {
        final List<InvoiceItem> invoiceItems = new ArrayList<InvoiceItem>();

        InvoiceItem mergedCBAItem = null;
        InvoiceItem mergedInvoiceAdjustment = null;
        for (final InvoiceItem item : invoice.getInvoiceItems()) {
            if (InvoiceItemType.CBA_ADJ.equals(item.getInvoiceItemType())) {
                // Merge CBA items to avoid confusing the customer, since these are internal
                // adjustments (auto generated)
                mergedCBAItem = mergeCBAItem(invoiceItems, mergedCBAItem, item);
            } else if (InvoiceItemType.CREDIT_ADJ.equals(item.getInvoiceItemType())) {
                // Merge credit adjustments, as these are both the same for the customer (invoice adjustment)
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

        return invoiceItems;

    }

    @Override
    public List<String> getTrackingIds() {
        return Objects.requireNonNullElse(invoice.getTrackingIds(), Collections.emptyList());
    }

    @Override
    public boolean addTrackingIds(final Collection<String> collection) {
        return true;
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
                mergedInvoiceAdjustment = new CreditAdjInvoiceItem(invoice.getId(), invoice.getAccountId(), invoice.getInvoiceDate(), mergedInvoiceAdjustment.getDescription(),
                                                                   mergedInvoiceAdjustment.getAmount().add(item.getAmount()), mergedInvoiceAdjustment.getCurrency(), mergedInvoiceAdjustment.getItemDetails());
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
        return Objects.requireNonNullElse(invoice.getInvoiceItems(clazz), Collections.emptyList());
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
        return Objects.requireNonNullElse(invoice.getPayments(), Collections.emptyList());
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
        return Objects.requireNonNullElse(invoice.getChargedAmount(), BigDecimal.ZERO);
    }

    @Override
    public BigDecimal getOriginalChargedAmount() {
        return Objects.requireNonNullElse(invoice.getOriginalChargedAmount(), BigDecimal.ZERO);
    }

    @Override
    public BigDecimal getBalance() {
        return Objects.requireNonNullElse(invoice.getBalance(), BigDecimal.ZERO);
    }

    @Override
    public String getFormattedChargedAmount() {
        return getFormattedAmountByLocaleAndInvoiceCurrency(getChargedAmount());
    }

    @Override
    public String getFormattedPaidAmount() {
        return getFormattedAmountByLocaleAndInvoiceCurrency(getPaidAmount());
    }

    @Override
    public String getFormattedBalance() {
        return getFormattedAmountByLocaleAndInvoiceCurrency(getBalance());
    }

    // Returns the formatted amount with the correct currency symbol that is get from the invoice currency.
    private String getFormattedAmountByLocaleAndInvoiceCurrency(final BigDecimal amount) {
        final String invoiceCurrencyCode = invoice.getCurrency().toString();
        final CurrencyUnit currencyUnit = CurrencyUnit.of(invoiceCurrencyCode);

        final DecimalFormat numberFormatter = (DecimalFormat) DecimalFormat.getCurrencyInstance(locale);
        final DecimalFormatSymbols dfs = numberFormatter.getDecimalFormatSymbols();
        dfs.setInternationalCurrencySymbol(currencyUnit.getCode());

        try {
            final Currency currency = Currency.fromCode(invoiceCurrencyCode);
            dfs.setCurrencySymbol(currency.getSymbol());
        } catch (final IllegalArgumentException e) {
            dfs.setCurrencySymbol(currencyUnit.getSymbol(locale));
        }

        numberFormatter.setDecimalFormatSymbols(dfs);
        numberFormatter.setMinimumFractionDigits(currencyUnit.getDecimalPlaces());
        numberFormatter.setMaximumFractionDigits(currencyUnit.getDecimalPlaces());

        return numberFormatter.format(amount);
    }

    @Override
    public Currency getProcessedCurrency() {
        final Currency processedCurrency = ((DefaultInvoice) invoice).getProcessedCurrency();
        // If the processed currency is different we return it; otherwise we return null so that template does not print anything special
        return (processedCurrency != getCurrency()) ? processedCurrency : null;
    }

    @Override
    public String getProcessedPaymentRate() {
        final Currency currency = getProcessedCurrency();
        if (currency == null) {
            return null;
        }
        // If there were multiple payments (and refunds) we pick chose the last one
        DateTime latestPaymentDate = null;
        for (final InvoicePayment cur : invoice.getPayments()) {
            latestPaymentDate = latestPaymentDate != null && latestPaymentDate.isAfter(cur.getPaymentDate()) ?
                                latestPaymentDate : cur.getPaymentDate();

        }
        try {
            final CurrencyConversion conversion = currencyConversionApi.getCurrencyConversion(currency, latestPaymentDate);
            for (final Rate rate : conversion.getRates()) {
                if (rate.getCurrency() == getCurrency()) {
                    return rate.getValue().toString();
                }
            }
        } catch (final CurrencyConversionException e) {
            logger.warn("Failed to retrieve currency conversion rates for currency='{}', dateConversion='{}'", currency, latestPaymentDate, e);
            return null;
        }
        logger.warn("Failed to retrieve currency conversion rates for currency='{}', dateConversion='{}'", currency, latestPaymentDate);
        return null;
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
        return Objects.requireNonNullElse(invoice.getPaidAmount(), BigDecimal.ZERO);
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

    @Override
    public InvoiceStatus getStatus() {
        return invoice.getStatus();
    }

    @Override
    public boolean isParentInvoice() {
        return invoice.isParentInvoice();
    }

    @Override
    public UUID getParentAccountId() {
        return invoice.getParentAccountId();
    }

    @Override
    public UUID getParentInvoiceId() {
        return invoice.getParentInvoiceId();
    }

    @Override
    public UUID getGroupId() {
        return invoice.getGroupId();
    }

    // Expose the fields for children classes. This is useful for further customization of the invoices

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
    public BigDecimal getCreditedAmount() {
        return Objects.requireNonNullElse(invoice.getCreditedAmount(), BigDecimal.ZERO);
    }

    @Override
    public BigDecimal getRefundedAmount() {
        return Objects.requireNonNullElse(invoice.getRefundedAmount(), BigDecimal.ZERO);
    }
}
