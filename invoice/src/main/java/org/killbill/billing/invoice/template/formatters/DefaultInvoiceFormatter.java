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
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.joda.money.CurrencyUnit;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.killbill.billing.callcontext.InternalTenantContext;
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
import org.killbill.billing.invoice.api.formatters.ResourceBundleFactory;
import org.killbill.billing.invoice.model.CreditAdjInvoiceItem;
import org.killbill.billing.invoice.model.CreditBalanceAdjInvoiceItem;
import org.killbill.billing.invoice.model.DefaultInvoice;
import org.killbill.billing.util.template.translation.TranslatorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

/**
 * Format invoice fields
 */
public class DefaultInvoiceFormatter implements InvoiceFormatter {

    private static final Logger logger = LoggerFactory.getLogger(DefaultInvoiceFormatter.class);

    private final TranslatorConfig config;
    private final Invoice invoice;
    private final DateTimeFormatter dateFormatter;
    private final Locale locale;
    private final CurrencyConversionApi currencyConversionApi;
    private final InternalTenantContext context;
    private final ResourceBundleFactory bundleFactory;

    public DefaultInvoiceFormatter(final TranslatorConfig config, final Invoice invoice, final Locale locale,
                                   final CurrencyConversionApi currencyConversionApi, final ResourceBundleFactory bundleFactory,
                                   final InternalTenantContext context) {
        this.config = config;
        this.invoice = invoice;
        this.dateFormatter = DateTimeFormat.mediumDate().withLocale(locale);
        this.locale = locale;
        this.currencyConversionApi = currencyConversionApi;
        this.bundleFactory = bundleFactory;
        this.context = context;
    }

    @Override
    public Integer getInvoiceNumber() {
        return MoreObjects.firstNonNull(invoice.getInvoiceNumber(), 0);
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

        final List<InvoiceItem> formatters = new ArrayList<InvoiceItem>();
        for (final InvoiceItem item : invoiceItems) {
            formatters.add(new DefaultInvoiceItemFormatter(config, item, dateFormatter, locale, context, bundleFactory));
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
        return MoreObjects.firstNonNull(invoice.getInvoiceItems(clazz), ImmutableList.<InvoiceItem>of());
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
        return MoreObjects.firstNonNull(invoice.getPayments(), ImmutableList.<InvoicePayment>of());
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
        return MoreObjects.firstNonNull(invoice.getChargedAmount(), BigDecimal.ZERO);
    }

    @Override
    public BigDecimal getOriginalChargedAmount() {
        return MoreObjects.firstNonNull(invoice.getOriginalChargedAmount(), BigDecimal.ZERO);
    }

    @Override
    public BigDecimal getBalance() {
        return MoreObjects.firstNonNull(invoice.getBalance(), BigDecimal.ZERO);
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
        dfs.setInternationalCurrencySymbol(currencyUnit.getCurrencyCode());

        try {
            Currency currency = Currency.fromCode(invoiceCurrencyCode);
            dfs.setCurrencySymbol(currency.getSymbol());
        } catch (final IllegalArgumentException e) {
            dfs.setCurrencySymbol(currencyUnit.getSymbol(locale));
        }

        numberFormatter.setDecimalFormatSymbols(dfs);
        numberFormatter.setMinimumFractionDigits(currencyUnit.getDefaultFractionDigits());
        numberFormatter.setMaximumFractionDigits(currencyUnit.getDefaultFractionDigits());

        return numberFormatter.format(amount.doubleValue());
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
        final Iterator<InvoicePayment> paymentIterator = invoice.getPayments().iterator();
        while (paymentIterator.hasNext()) {
            final InvoicePayment cur = paymentIterator.next();
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
        return MoreObjects.firstNonNull(invoice.getPaidAmount(), BigDecimal.ZERO);
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
    public BigDecimal getCreditedAmount() {
        return MoreObjects.firstNonNull(invoice.getCreditedAmount(), BigDecimal.ZERO);
    }

    @Override
    public BigDecimal getRefundedAmount() {
        return MoreObjects.firstNonNull(invoice.getRefundedAmount(), BigDecimal.ZERO);
    }
}
