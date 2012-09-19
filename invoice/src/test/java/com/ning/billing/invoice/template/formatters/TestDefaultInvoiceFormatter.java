/*
 * Copyright 2010-2012 Ning, Inc.
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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.skife.config.ConfigurationObjectFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.InvoiceTestSuite;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.model.DefaultInvoice;
import com.ning.billing.invoice.model.FixedPriceInvoiceItem;
import com.ning.billing.util.email.templates.MustacheTemplateEngine;
import com.ning.billing.util.template.translation.TranslatorConfig;

public class TestDefaultInvoiceFormatter extends InvoiceTestSuite {

    private TranslatorConfig config;
    private MustacheTemplateEngine templateEngine;

    @BeforeSuite(groups = "fast")
    public void setup() {
        config = new ConfigurationObjectFactory(System.getProperties()).build(TranslatorConfig.class);
        templateEngine = new MustacheTemplateEngine();
    }

    @Test(groups = "fast")
    public void testFormattedAmount() throws Exception {
        final FixedPriceInvoiceItem fixedItemEUR = new FixedPriceInvoiceItem(UUID.randomUUID(), UUID.randomUUID(), null, null,
                                                                             UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                                                                             new LocalDate(), new BigDecimal("1499.95"), Currency.EUR);
        final Invoice invoiceEUR = new DefaultInvoice(UUID.randomUUID(), new LocalDate(), new LocalDate(), Currency.EUR);
        invoiceEUR.addInvoiceItem(fixedItemEUR);

        checkOutput(invoiceEUR,
                    "<tr>\n" +
                    "    <td class=\"amount\"><strong>{{invoice.formattedChargedAmount}}</strong></td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "    <td class=\"amount\"><strong>{{invoice.formattedPaidAmount}}</strong></td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "    <td class=\"amount\"><strong>{{invoice.formattedBalance}}</strong></td>\n" +
                    "</tr>",
                    "<tr>\n" +
                    "    <td class=\"amount\"><strong>1 499,95 €</strong></td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "    <td class=\"amount\"><strong>0,00 €</strong></td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "    <td class=\"amount\"><strong>1 499,95 €</strong></td>\n" +
                    "</tr>",
                    Locale.FRANCE);
    }

    private void checkOutput(final Invoice invoice, final String template, final String expected, final Locale locale) {
        final Map<String, Object> data = new HashMap<String, Object>();
        data.put("invoice", new DefaultInvoiceFormatter(config, invoice, locale));

        final String formattedText = templateEngine.executeTemplateText(template, data);
        Assert.assertEquals(formattedText, expected);
    }
}
