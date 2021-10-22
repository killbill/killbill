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

package org.killbill.billing.util.template.translation;

import org.skife.config.Config;
import org.skife.config.Default;
import org.skife.config.Description;

import org.killbill.billing.invoice.api.formatters.InvoiceFormatterFactory;

public interface TranslatorConfig {

    // Common

    @Config("org.killbill.default.locale")
    @Default("en_US")
    @Description("Default Killbill locale")
    public String getDefaultLocale();

    // Catalog

    @Config("org.killbill.catalog.bundlePath")
    @Default("org/killbill/billing/util/template/translation/CatalogTranslation")
    @Description("Path to the catalog translation bundle")
    String getCatalogBundlePath();

    // Invoices
    @Config("org.killbill.template.bundlePath")
    @Default("org/killbill/billing/util/invoice/translation/InvoiceTranslation")
    @Description("Path to the invoice template translation bundle")
    public String getInvoiceTemplateBundlePath();

    @Config("org.killbill.template.name")
    @Default("org/killbill/billing/util/invoice/templates/HtmlInvoiceTemplate.mustache")
    @Description("Path to the HTML invoice template")
    String getTemplateName();

    @Config("org.killbill.manualPayTemplate.name")
    @Default("org/killbill/billing/util/email/templates/HtmlInvoiceTemplate.mustache")
    @Description("Path to the invoice template for accounts with MANUAL_PAY tag")
    String getManualPayTemplateName();

    @Config("org.killbill.template.invoiceFormatterFactoryClass")
    @Default("org.killbill.billing.invoice.template.formatters.DefaultInvoiceFormatterFactory")
    @Description("Invoice formatter class")
    Class<? extends InvoiceFormatterFactory> getInvoiceFormatterFactoryClass();
}
