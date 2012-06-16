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

package com.ning.billing.invoice.template;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.formatters.InvoiceFormatter;
import com.ning.billing.invoice.api.formatters.InvoiceFormatterFactory;
import com.ning.billing.invoice.template.translator.DefaultInvoiceTranslator;
import com.ning.billing.util.email.templates.TemplateEngine;
import com.ning.billing.util.template.translation.TranslatorConfig;

public class HtmlInvoiceGenerator {
    private final InvoiceFormatterFactory factory;
    private final TemplateEngine templateEngine;
    private final TranslatorConfig config;

    @Inject
    public HtmlInvoiceGenerator(final InvoiceFormatterFactory factory, final TemplateEngine templateEngine, final TranslatorConfig config) {
        this.factory = factory;
        this.templateEngine = templateEngine;
        this.config = config;
    }

    public String generateInvoice(final Account account, final Invoice invoice) throws IOException {
        final Map<String, Object> data = new HashMap<String, Object>();
        final DefaultInvoiceTranslator invoiceTranslator = new DefaultInvoiceTranslator(config);
        final Locale locale = new Locale(account.getLocale());
        invoiceTranslator.setLocale(locale);
        data.put("text", invoiceTranslator);
        data.put("account", account);

        final InvoiceFormatter formattedInvoice = factory.createInvoiceFormatter(config, invoice, locale);
        data.put("invoice", formattedInvoice);

        return templateEngine.executeTemplate(config.getTemplateName(), data);
    }
}
