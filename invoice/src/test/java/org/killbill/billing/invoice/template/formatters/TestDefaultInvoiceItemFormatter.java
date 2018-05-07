/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.InvoiceTestSuiteNoDB;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.model.FixedPriceInvoiceItem;
import org.killbill.billing.invoice.model.RecurringInvoiceItem;
import org.killbill.billing.util.LocaleUtils;
import org.killbill.billing.util.email.templates.MustacheTemplateEngine;
import org.killbill.billing.util.template.translation.TranslatorConfig;
import org.skife.config.ConfigurationObjectFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class TestDefaultInvoiceItemFormatter extends InvoiceTestSuiteNoDB {

    private TranslatorConfig config;
    private MustacheTemplateEngine templateEngine;

    @Override
    @BeforeClass(groups = "fast")
    public void beforeClass() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeClass();
        config = new ConfigurationObjectFactory(skifeConfigSource).build(TranslatorConfig.class);
        templateEngine = new MustacheTemplateEngine();
    }

    @Test(groups = "fast")
    public void testBasicUSD() throws Exception {
        final FixedPriceInvoiceItem fixedItemUSD = new FixedPriceInvoiceItem(UUID.randomUUID(), UUID.randomUUID(), null, null,
                                                                             UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                                                                             new LocalDate(), new BigDecimal("-1114.751625346"), Currency.USD);
        checkOutput(fixedItemUSD, "{{#invoiceItem}}<td class=\"amount\">{{formattedAmount}}</td>{{/invoiceItem}}",
                    "<td class=\"amount\">($1,114.75)</td>", LocaleUtils.toLocale("en_US"));
    }

    @Test(groups = "fast")
    public void testFormattedAmount() throws Exception {
        final FixedPriceInvoiceItem fixedItemEUR = new FixedPriceInvoiceItem(UUID.randomUUID(), UUID.randomUUID(), null, null,
                                                                             UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                                                                             new LocalDate(), new BigDecimal("1499.95"), Currency.EUR);
        checkOutput(fixedItemEUR, "{{#invoiceItem}}<td class=\"amount\">{{formattedAmount}}</td>{{/invoiceItem}}",
                    "<td class=\"amount\">1 499,95 €</td>", Locale.FRANCE);

        final FixedPriceInvoiceItem fixedItemUSD = new FixedPriceInvoiceItem(UUID.randomUUID(), UUID.randomUUID(), null, null,
                                                                             UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                                                                             new LocalDate(), new BigDecimal("-1114.751625346"), Currency.USD);
        checkOutput(fixedItemUSD, "{{#invoiceItem}}<td class=\"amount\">{{formattedAmount}}</td>{{/invoiceItem}}", "<td class=\"amount\">($1,114.75)</td>");

        // Check locale/currency mismatch (locale is set at the account level)
        final FixedPriceInvoiceItem fixedItemGBP = new FixedPriceInvoiceItem(UUID.randomUUID(), UUID.randomUUID(), null, null,
                                                                             UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                                                                             new LocalDate(), new BigDecimal("8.07"), Currency.GBP);
        checkOutput(fixedItemGBP, "{{#invoiceItem}}<td class=\"amount\">{{formattedAmount}}</td>{{/invoiceItem}}",
                    "<td class=\"amount\">8,07 GBP</td>", Locale.FRANCE);
    }

    @Test(groups = "fast")
    public void testNullEndDate() throws Exception {
        final LocalDate startDate = new LocalDate(2012, 12, 1);
        final FixedPriceInvoiceItem fixedItem = new FixedPriceInvoiceItem(UUID.randomUUID(), UUID.randomUUID(), null, null,
                                                                          UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                                                                          startDate, BigDecimal.TEN, Currency.USD);
        checkOutput(fixedItem,
                    "{{#invoiceItem}}<td>{{formattedStartDate}}{{#formattedEndDate}} - {{formattedEndDate}}{{/formattedEndDate}}</td>{{/invoiceItem}}",
                    "<td>Dec 1, 2012</td>");
    }

    @Test(groups = "fast")
    public void testNonNullEndDate() throws Exception {
        final LocalDate startDate = new LocalDate(2012, 12, 1);
        final LocalDate endDate = new LocalDate(2012, 12, 31);
        final RecurringInvoiceItem recurringItem = new RecurringInvoiceItem(UUID.randomUUID(), UUID.randomUUID(), null, null,
                                                                            UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                                                                            startDate, endDate, BigDecimal.TEN, BigDecimal.TEN, Currency.USD);
        checkOutput(recurringItem,
                    "{{#invoiceItem}}<td>{{formattedStartDate}}{{#formattedEndDate}} - {{formattedEndDate}}{{/formattedEndDate}}</td>{{/invoiceItem}}",
                    "<td>Dec 1, 2012 - Dec 31, 2012</td>");
    }

    private void checkOutput(final InvoiceItem invoiceItem, final String template, final String expected) {
        checkOutput(invoiceItem, template, expected, Locale.US);
    }

    private void checkOutput(final InvoiceItem invoiceItem, final String template, final String expected, final Locale locale) {
        final Map<String, Object> data = new HashMap<String, Object>();
        data.put("invoiceItem", new DefaultInvoiceItemFormatter(config, invoiceItem,  DateTimeFormat.mediumDate().withLocale(locale), locale, internalCallContext, resourceBundleFactory));

        final String formattedText = templateEngine.executeTemplateText(template, data);
        Assert.assertEquals(formattedText, expected);
    }
}
