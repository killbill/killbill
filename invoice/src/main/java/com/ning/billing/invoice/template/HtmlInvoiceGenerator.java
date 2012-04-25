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

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.template.translator.DefaultInvoiceTranslator;
import com.ning.billing.util.email.formatters.DefaultInvoiceFormatter;
import com.ning.billing.util.email.formatters.InvoiceFormatter;
import com.ning.billing.util.email.templates.TemplateEngine;
import com.ning.billing.util.template.translation.TranslatorConfig;

import java.io.IOException;
import java.lang.String;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class HtmlInvoiceGenerator {
    private final TemplateEngine templateEngine;
    private final TranslatorConfig config;

    @Inject
    public HtmlInvoiceGenerator(TemplateEngine templateEngine, TranslatorConfig config) {
        this.templateEngine = templateEngine;
        this.config = config;
    }

    public String generateInvoice(Account account, Invoice invoice, String templateName) throws IOException {
        Map<String, Object> data = new HashMap<String, Object>();
        DefaultInvoiceTranslator invoiceTranslator = new DefaultInvoiceTranslator(config);
        Locale locale = new Locale(account.getLocale());
        invoiceTranslator.setLocale(locale);
        data.put("text", invoiceTranslator);
        data.put("account", account);

        InvoiceFormatter formattedInvoice = new DefaultInvoiceFormatter(config, invoice, locale);
        data.put("invoice", formattedInvoice);

        return templateEngine.executeTemplate(templateName, data);
    }
}