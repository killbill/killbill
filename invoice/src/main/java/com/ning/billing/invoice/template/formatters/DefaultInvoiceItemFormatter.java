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
import java.util.Locale;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormatter;

import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.invoice.api.formatters.InvoiceItemFormatter;
import com.ning.billing.util.template.translation.DefaultCatalogTranslator;
import com.ning.billing.util.template.translation.Translator;
import com.ning.billing.util.template.translation.TranslatorConfig;

/**
 * Format invoice item fields. Note that the Mustache engine won't accept null values.
 */
public class DefaultInvoiceItemFormatter implements InvoiceItemFormatter {
    private final Translator translator;

    private final InvoiceItem item;
    private final DateTimeFormatter dateFormatter;
    private final Locale locale;

    public DefaultInvoiceItemFormatter(final TranslatorConfig config, final InvoiceItem item, final DateTimeFormatter dateFormatter, final Locale locale) {
        this.item = item;
        this.dateFormatter = dateFormatter;
        this.locale = locale;

        this.translator = new DefaultCatalogTranslator(config);
    }

    @Override
    public BigDecimal getAmount() {
        return Objects.firstNonNull(item.getAmount(), BigDecimal.ZERO);
    }

    @Override
    public Currency getCurrency() {
        return item.getCurrency();
    }

    @Override
    public InvoiceItemType getInvoiceItemType() {
        return item.getInvoiceItemType();
    }

    @Override
    public String getDescription() {
        return Strings.nullToEmpty(item.getDescription());
    }

    @Override
    public LocalDate getStartDate() {
        return item.getStartDate();
    }

    @Override
    public LocalDate getEndDate() {
        return item.getEndDate();
    }

    @Override
    public String getFormattedStartDate() {
        return Strings.nullToEmpty(item.getStartDate().toString(dateFormatter));
    }

    @Override
    public String getFormattedEndDate() {
        return Strings.nullToEmpty(item.getEndDate().toString(dateFormatter));
    }

    @Override
    public UUID getInvoiceId() {
        return item.getInvoiceId();
    }

    @Override
    public UUID getAccountId() {
        return item.getAccountId();
    }

    @Override
    public UUID getBundleId() {
        return item.getBundleId();
    }

    @Override
    public UUID getSubscriptionId() {
        return item.getSubscriptionId();
    }

    @Override
    public String getPlanName() {
        return Strings.nullToEmpty(translator.getTranslation(locale, item.getPlanName()));
    }

    @Override
    public String getPhaseName() {
        return Strings.nullToEmpty(translator.getTranslation(locale, item.getPhaseName()));
    }

    @Override
    public int compareTo(final InvoiceItem invoiceItem) {
        return item.compareTo(invoiceItem);
    }

    @Override
    public UUID getId() {
        return item.getId();
    }

    @Override
    public BigDecimal getRate() {
        return BigDecimal.ZERO;
    }

    @Override
    public UUID getLinkedItemId() {
        return null;
    }
}
