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
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.invoice.api.formatters.InvoiceItemFormatter;
import com.ning.billing.util.template.translation.DefaultCatalogTranslator;
import com.ning.billing.util.template.translation.Translator;
import com.ning.billing.util.template.translation.TranslatorConfig;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

public class DefaultInvoiceItemFormatter implements InvoiceItemFormatter {
    private final Translator translator;

    private final InvoiceItem item;
    private final DateTimeFormatter dateFormatter;
    private final Locale locale;

    public DefaultInvoiceItemFormatter(TranslatorConfig config, InvoiceItem item, DateTimeFormatter dateFormatter, Locale locale) {
        this.item = item;
        this.dateFormatter = dateFormatter;
        this.locale = locale;

        this.translator = new DefaultCatalogTranslator(config);
    }

    @Override
    public BigDecimal getAmount() {
        return item.getAmount();
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
    public InvoiceItem asReversingItem() {
        return item.asReversingItem();
    }

    @Override
    public String getDescription() {
        return item.getDescription();
    }

    @Override
    public DateTime getStartDate() {
        return item.getStartDate();
    }

    @Override
    public DateTime getEndDate() {
        return item.getEndDate();
    }

    @Override
    public String getFormattedStartDate() {
        return item.getStartDate().toString(dateFormatter);
    }

    @Override
    public String getFormattedEndDate() {
        return item.getEndDate().toString(dateFormatter);
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
        return translator.getTranslation(locale, item.getPlanName());
    }

    @Override
    public String getPhaseName() {
        return translator.getTranslation(locale, item.getPhaseName());
    }

    @Override
    public int compareTo(InvoiceItem invoiceItem) {
        return item.compareTo(invoiceItem);
    }

    @Override
    public UUID getId() {
        return item.getId();
    }
}