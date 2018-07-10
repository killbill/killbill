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

package org.killbill.billing.invoice.template.formatters;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormatter;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.api.formatters.InvoiceItemFormatter;
import org.killbill.billing.invoice.api.formatters.ResourceBundleFactory;
import org.killbill.billing.invoice.api.formatters.ResourceBundleFactory.ResourceBundleType;
import org.killbill.billing.util.LocaleUtils;
import org.killbill.billing.util.template.translation.DefaultCatalogTranslator;
import org.killbill.billing.util.template.translation.Translator;
import org.killbill.billing.util.template.translation.TranslatorConfig;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;

/**
 * Format invoice item fields
 */
public class DefaultInvoiceItemFormatter implements InvoiceItemFormatter {

    private final Translator translator;

    private final InvoiceItem item;
    private final DateTimeFormatter dateFormatter;
    private final Locale locale;

    public DefaultInvoiceItemFormatter(final TranslatorConfig config,
                                       final InvoiceItem item,
                                       final DateTimeFormatter dateFormatter,
                                       final Locale locale,
                                       final InternalTenantContext context,
                                       final ResourceBundleFactory bundleFactory) {
        this.item = item;
        this.dateFormatter = dateFormatter;
        this.locale = locale;
        final ResourceBundle bundle = bundleFactory.createBundle(locale, config.getCatalogBundlePath(), ResourceBundleType.CATALOG_TRANSLATION, context);
        final ResourceBundle defaultBundle = bundleFactory.createBundle(LocaleUtils.toLocale(config.getDefaultLocale()), config.getCatalogBundlePath(), ResourceBundleType.CATALOG_TRANSLATION, context);
        this.translator = new DefaultCatalogTranslator(bundle, defaultBundle);
    }

    @Override
    public BigDecimal getAmount() {
        return MoreObjects.firstNonNull(item.getAmount(), BigDecimal.ZERO);
    }

    @Override
    public Currency getCurrency() {
        return item.getCurrency();
    }

    @Override
    public String getFormattedAmount() {
        final NumberFormat number = NumberFormat.getCurrencyInstance(locale);
        number.setCurrency(java.util.Currency.getInstance(item.getCurrency().toString()));
        return number.format(getAmount().doubleValue());
    }

    @Override
    public InvoiceItemType getInvoiceItemType() {
        return item.getInvoiceItemType();
    }

    @Override
    public String getDescription() {
        return Strings.nullToEmpty(translator.getTranslation(item.getDescription()));
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
        return item.getStartDate().toString(dateFormatter);
    }

    @Override
    public String getFormattedEndDate() {
        return item.getEndDate() == null ? null : item.getEndDate().toString(dateFormatter);
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
    public UUID getChildAccountId() {
        return item.getChildAccountId();
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
    public String getProductName() {
        return Strings.nullToEmpty(translator.getTranslation(item.getProductName()));
    }

    @Override
    public String getPrettyProductName() {
        return Strings.nullToEmpty(translator.getTranslation(item.getPrettyProductName()));
    }

    @Override
    public String getPlanName() {
        return Strings.nullToEmpty(translator.getTranslation(item.getPlanName()));
    }

    @Override
    public String getPrettyPlanName() {
        return Strings.nullToEmpty(translator.getTranslation(item.getPrettyPlanName()));
    }

    @Override
    public String getPhaseName() {
        return Strings.nullToEmpty(translator.getTranslation(item.getPhaseName()));
    }

    @Override
    public String getPrettyPhaseName() {
        return Strings.nullToEmpty(translator.getTranslation(item.getPrettyPhaseName()));
    }

    @Override
    public String getUsageName() {
        return Strings.nullToEmpty(translator.getTranslation(item.getUsageName()));
    }

    @Override
    public String getPrettyUsageName() {
        return Strings.nullToEmpty(translator.getTranslation(item.getPrettyUsageName()));
    }

    @Override
    public UUID getId() {
        return item.getId();
    }

    @Override
    public DateTime getCreatedDate() {
        return item.getCreatedDate();
    }

    @Override
    public DateTime getUpdatedDate() {
        return item.getUpdatedDate();
    }

    @Override
    public BigDecimal getRate() {
        return BigDecimal.ZERO;
    }

    @Override
    public UUID getLinkedItemId() {
        return null;
    }

    @Override
    public Integer getQuantity() { return item.getQuantity(); }

    @Override
    public String getItemDetails() { return item.getItemDetails(); }

    @Override
    public boolean matches(final Object other) {
        throw new UnsupportedOperationException();
    }

}
