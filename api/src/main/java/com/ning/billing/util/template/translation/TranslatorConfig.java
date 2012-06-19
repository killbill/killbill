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

import com.ning.billing.invoice.api.formatters.InvoiceFormatterFactory;

public interface TranslatorConfig {
    @Config("killbill.template.default.locale")
    @Default("en_US")
    public String getDefaultLocale();

    @Config("killbill.template.bundlePath")
    @Default("com/ning/billing/util/template/translation/InvoiceTranslation")
    public String getBundlePath();

    @Config("killbill.template.name")
    @Default("com/ning/billing/util/email/templates/HtmlInvoiceTemplate.mustache")
    String getTemplateName();

    @Config("killbill.template.invoiceFormatterFactoryClass")
    @Default("com.ning.billing.invoice.template.formatters.DefaultInvoiceFormatterFactory")
    Class<? extends InvoiceFormatterFactory> getInvoiceFormatterFactoryClass();
}
