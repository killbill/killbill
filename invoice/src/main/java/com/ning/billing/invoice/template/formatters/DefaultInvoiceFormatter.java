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

package com.ning.billing.invoice.template.formatters;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.api.formatters.InvoiceFormatter;
import com.ning.billing.util.template.translation.TranslatorConfig;

/**
 * Format invoice fields. Note that the Mustache engine won't accept null values.
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
        final List<InvoiceItem> formatters = new ArrayList<InvoiceItem>();
        for (final InvoiceItem item : invoice.getInvoiceItems()) {
            formatters.add(new DefaultInvoiceItemFormatter(config, item, dateFormatter, locale));
        }
        return formatters;
    }

    @Override
    public boolean addInvoiceItem(final InvoiceItem item) {
        return invoice.addInvoiceItem(item);
    }

    @Override
    public boolean addInvoiceItems(final List<InvoiceItem> items) {
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
    public boolean addPayments(final List<InvoicePayment> payments) {
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
        return Objects.firstNonNull(invoice.getChargedAmount(), BigDecimal.ZERO);
    }

    @Override
    public BigDecimal getCBAAmount() {
        return Objects.firstNonNull(invoice.getCBAAmount(), BigDecimal.ZERO);
    }

    @Override
    public BigDecimal getBalance() {
        return Objects.firstNonNull(invoice.getBalance(), BigDecimal.ZERO);
    }

    @Override
    public boolean isDueForPayment(final DateTime targetDate, final int numberOfDays) {
        return invoice.isDueForPayment(targetDate, numberOfDays);
    }

    @Override
    public boolean isMigrationInvoice() {
        return invoice.isMigrationInvoice();
    }

    @Override
    public DateTime getInvoiceDate() {
        return invoice.getInvoiceDate();
    }

    @Override
    public DateTime getTargetDate() {
        return invoice.getTargetDate();
    }

    @Override
    public Currency getCurrency() {
        return invoice.getCurrency();
    }

    @Override
    public DateTime getLastPaymentDate() {
        return invoice.getLastPaymentDate();
    }

    @Override
    public BigDecimal getPaidAmount() {
        return Objects.firstNonNull(invoice.getPaidAmount(), BigDecimal.ZERO);
    }

    @Override
    public String getFormattedInvoiceDate() {
        final DateTime invoiceDate = invoice.getInvoiceDate();
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

    // Expose the fields for children classes. This is useful for further customization of the invoices

    protected TranslatorConfig getConfig() {
        return config;
    }

    protected DateTimeFormatter getDateFormatter() {
        return dateFormatter;
    }

    protected Locale getLocale() {
        return locale;
    }

    protected Invoice getInvoice() {
        return invoice;
    }

    @Override
    public BigDecimal getTotalAdjAmount() {
        return Objects.firstNonNull(invoice.getTotalAdjAmount(), BigDecimal.ZERO);
    }

    @Override
    public BigDecimal getCreditAdjAmount() {
        return Objects.firstNonNull(invoice.getCreditAdjAmount(), BigDecimal.ZERO);
    }

    @Override
    public BigDecimal getRefundAdjAmount() {
        return Objects.firstNonNull(invoice.getRefundAdjAmount(), BigDecimal.ZERO);
    }
}
