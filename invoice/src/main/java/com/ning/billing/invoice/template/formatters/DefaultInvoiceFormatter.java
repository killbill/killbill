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

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.api.formatters.InvoiceFormatter;
import com.ning.billing.util.template.translation.TranslatorConfig;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class DefaultInvoiceFormatter implements InvoiceFormatter {
    private final TranslatorConfig config;
    private final Invoice invoice;
    private final DateTimeFormatter dateFormatter;
    private final Locale locale;

    public DefaultInvoiceFormatter(TranslatorConfig config, Invoice invoice, Locale locale) {
        this.config = config;
        this.invoice = invoice;
        dateFormatter = DateTimeFormat.mediumDate().withLocale(locale);
        this.locale = locale;
    }

    @Override
    public Integer getInvoiceNumber() {
        return invoice.getInvoiceNumber();
    }

    @Override
    public List<InvoiceItem> getInvoiceItems() {
        List<InvoiceItem> formatters = new ArrayList<InvoiceItem>();
        for (InvoiceItem item : invoice.getInvoiceItems()) {
            formatters.add(new DefaultInvoiceItemFormatter(config, item, dateFormatter, locale));
        }
        return formatters;
    }

    @Override
    public boolean addInvoiceItem(InvoiceItem item) {
        return invoice.addInvoiceItem(item);
    }

    @Override
    public boolean addInvoiceItems(List<InvoiceItem> items) {
        return invoice.addInvoiceItems(items);
    }

    @Override
    public <T extends InvoiceItem> List<InvoiceItem> getInvoiceItems(Class<T> clazz) {
        return invoice.getInvoiceItems(clazz);
    }

    @Override
    public int getNumberOfItems() {
        return invoice.getNumberOfItems();
    }

    @Override
    public boolean addPayment(InvoicePayment payment) {
        return invoice.addPayment(payment);
    }

    @Override
    public boolean addPayments(List<InvoicePayment> payments) {
        return invoice.addPayments(payments);
    }

    @Override
    public List<InvoicePayment> getPayments() {
        return invoice.getPayments();
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
    public BigDecimal getTotalAmount() {
        return invoice.getTotalAmount();
    }

    @Override
    public BigDecimal getBalance() {
        return invoice.getBalance();
    }

    @Override
    public boolean isDueForPayment(DateTime targetDate, int numberOfDays) {
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
    public DateTime getLastPaymentAttempt() {
        return invoice.getLastPaymentAttempt();
    }

    @Override
    public BigDecimal getAmountPaid() {
        return invoice.getAmountPaid();
    }

    @Override
    public String getFormattedInvoiceDate() {
        return invoice.getInvoiceDate().toString(dateFormatter);
    }

    @Override
    public UUID getId() {
        return invoice.getId();
    }
}
