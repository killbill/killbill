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

package com.ning.billing.util.email.templates;

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.util.email.EmailConfig;
import com.ning.billing.util.email.formatters.DefaultInvoiceFormatter;
import com.ning.billing.util.email.formatters.InvoiceFormatter;
import com.ning.billing.util.email.translation.DefaultInvoiceTranslation;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.lang.String;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class HtmlInvoiceGenerator {
    private final EmailConfig config;

    @Inject
    public HtmlInvoiceGenerator(EmailConfig config) {
        this.config = config;
    }

    public String generateInvoice(Account account, Invoice invoice, String templateName) throws IOException {
        InputStream templateStream = this.getClass().getResourceAsStream(templateName + ".mustache");
        StringWriter writer = new StringWriter();
        IOUtils.copy(templateStream, writer, "UTF-8");
        String templateText = writer.toString();

        Template template = Mustache.compiler().compile(templateText);

        Map<String, Object> data = new HashMap<String, Object>();

        data.put("text", new DefaultInvoiceTranslation(config));
        data.put("account", account);
        Locale locale = new Locale(account.getLocale());

        InvoiceFormatter formattedInvoice = new DefaultInvoiceFormatter(config, invoice, locale);
        data.put("invoice", formattedInvoice);

        return template.execute(data);
    }
}