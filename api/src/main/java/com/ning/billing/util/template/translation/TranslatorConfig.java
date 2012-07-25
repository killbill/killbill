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

package com.ning.billing.util.template.translation;

import org.skife.config.Config;
import org.skife.config.Default;
import org.skife.config.Description;

import com.ning.billing.invoice.api.formatters.InvoiceFormatterFactory;

public interface TranslatorConfig {
    // Common

    @Config("killbill.default.locale")
    @Default("en_US")
    public String getDefaultLocale();

    // Catalog

    @Config("killbill.catalog.bundlePath")
    @Default("com/ning/billing/util/template/translation/CatalogTranslation")
    String getCatalogBundlePath();

    // Invoices

    @Config("killbill.template.bundlePath")
    @Default("com/ning/billing/util/template/translation/InvoiceTranslation")
    public String getInvoiceTemplateBundlePath();

    @Config("killbill.template.name")
    @Default("com/ning/billing/util/email/templates/HtmlInvoiceTemplate.mustache")
    String getTemplateName();

    @Config("killbill.manualPayTemplate.name")
    @Default("com/ning/billing/util/email/templates/HtmlInvoiceTemplate.mustache")
    @Description("Invoice template for accounts with MANUAL_PAY tag")
    String getManualPayTemplateName();

    @Config("killbill.template.invoiceFormatterFactoryClass")
    @Default("com.ning.billing.invoice.template.formatters.DefaultInvoiceFormatterFactory")
    Class<? extends InvoiceFormatterFactory> getInvoiceFormatterFactoryClass();
}
