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

package org.killbill.billing.invoice.template;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.currency.api.CurrencyConversionApi;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.formatters.InvoiceFormatter;
import org.killbill.billing.invoice.api.formatters.ResourceBundleFactory;
import org.killbill.billing.invoice.api.formatters.ResourceBundleFactory.ResourceBundleType;
import org.killbill.billing.invoice.plugin.api.InvoiceFormatterFactory;
import org.killbill.billing.invoice.template.translator.DefaultInvoiceTranslator;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.tenant.api.TenantInternalApi;
import org.killbill.billing.util.LocaleUtils;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.email.templates.TemplateEngine;
import org.killbill.billing.util.template.translation.TranslatorConfig;
import org.killbill.commons.utils.Strings;
import org.killbill.commons.utils.io.IOUtils;
import org.killbill.xmlloader.UriAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HtmlInvoiceGenerator {

    private static final Logger log = LoggerFactory.getLogger(HtmlInvoiceGenerator.class);

    private final InvoiceFormatterFactory builtInInvoiceFormatterFactory;
    private final OSGIServiceRegistration<InvoiceFormatterFactory> invoiceFormatterFactoryPluginRegistry;

    private final TranslatorConfig config;
    private final CurrencyConversionApi currencyConversionApi;
    private final TemplateEngine templateEngine;
    private final TenantInternalApi tenantApi;
    private final ResourceBundleFactory bundleFactory;

    @Inject
    public HtmlInvoiceGenerator(final InvoiceFormatterFactory builtInInvoiceFormatterFactory,
                                final OSGIServiceRegistration<InvoiceFormatterFactory> invoiceFormatterFactoryPluginRegistry,
                                final TemplateEngine templateEngine,
                                final TranslatorConfig config,
                                final CurrencyConversionApi currencyConversionApi,
                                final ResourceBundleFactory bundleFactory,
                                final TenantInternalApi tenantInternalApi) {
        this.builtInInvoiceFormatterFactory = builtInInvoiceFormatterFactory;
        this.invoiceFormatterFactoryPluginRegistry = invoiceFormatterFactoryPluginRegistry;
        this.config = config;
        this.currencyConversionApi = currencyConversionApi;
        this.templateEngine = templateEngine;
        this.bundleFactory = bundleFactory;
        this.tenantApi = tenantInternalApi;
    }

    public HtmlInvoice generateInvoice(final Account account, @Nullable final Invoice invoice, final boolean manualPay, final InternalTenantContext context) throws IOException {
        // Don't do anything if the invoice is null
        if (invoice == null || invoice.getNumberOfItems() == 0) {
            return null;
        }

        final String accountLocale = Strings.emptyToNull(account.getLocale());
        final Locale locale = accountLocale == null ? Locale.getDefault() : LocaleUtils.toLocale(accountLocale);

        final HtmlInvoice invoiceData = new HtmlInvoice();
        final Map<String, Object> data = new HashMap<String, Object>();

        final ResourceBundle invoiceBundle = accountLocale != null ?
                                             bundleFactory.createBundle(LocaleUtils.toLocale(accountLocale), config.getInvoiceTemplateBundlePath(), ResourceBundleType.INVOICE_TRANSLATION, context) : null;
        final ResourceBundle defaultInvoiceBundle = bundleFactory.createBundle(Locale.getDefault(), config.getInvoiceTemplateBundlePath(), ResourceBundleType.INVOICE_TRANSLATION, context);
        final DefaultInvoiceTranslator invoiceTranslator = new DefaultInvoiceTranslator(invoiceBundle, defaultInvoiceBundle);

        data.put("text", invoiceTranslator);
        data.put("account", account);

        InvoiceFormatterFactory invoiceFormatterFactory;
        final String invoiceFormatterFactoryPluginName = config.getInvoiceFormatterFactoryPluginName();
        if (!Strings.isNullOrEmpty(invoiceFormatterFactoryPluginName)) {
            invoiceFormatterFactory = invoiceFormatterFactoryPluginRegistry.getServiceForName(invoiceFormatterFactoryPluginName);
            if(invoiceFormatterFactory == null) {
                invoiceFormatterFactory = builtInInvoiceFormatterFactory;
            }
        } else {
            final Set<String> services = invoiceFormatterFactoryPluginRegistry.getAllServices();
            invoiceFormatterFactory = services.size() == 1 ? invoiceFormatterFactoryPluginRegistry.getServiceForName(services.iterator().next()) : builtInInvoiceFormatterFactory;
            if (services.size() > 1) {
                log.warn("More than one InvoiceFormatter is configured, so using built-in InvoiceFormatter");
            }

        }
        final ResourceBundle bundle = bundleFactory.createBundle(locale, config.getCatalogBundlePath(), ResourceBundleType.CATALOG_TRANSLATION, context);
        final ResourceBundle defaultBundle = bundleFactory.createBundle(LocaleUtils.toLocale(config.getDefaultLocale()), config.getCatalogBundlePath(), ResourceBundleType.CATALOG_TRANSLATION, context);
        final InvoiceFormatter formattedInvoice = invoiceFormatterFactory.createInvoiceFormatter(config.getDefaultLocale(), config.getCatalogBundlePath(), invoice, locale, currencyConversionApi, bundle, defaultBundle);
        data.put("invoice", formattedInvoice);

        invoiceData.setSubject(invoiceTranslator.getInvoiceEmailSubject());
        final String templateText = getTemplateText(locale, manualPay, context);
        invoiceData.setBody(templateEngine.executeTemplateText(templateText, data));
        return invoiceData;
    }

    private String getTemplateText(final Locale locale, final boolean manualPay, final InternalTenantContext context) throws IOException {

        if (InternalCallContextFactory.INTERNAL_TENANT_RECORD_ID.equals(context.getTenantRecordId())) {
            return getDefaultTemplate(manualPay ? config.getManualPayTemplateName() : config.getTemplateName());
        }
        final String template = manualPay ?
                                tenantApi.getManualPayInvoiceTemplate(locale, context) :
                                tenantApi.getInvoiceTemplate(locale, context);
        return template == null ?
               getDefaultTemplate(manualPay ? config.getManualPayTemplateName() : config.getTemplateName()) :
               template;
    }

    private String getDefaultTemplate(final String templateName) throws IOException {
        try {
            final InputStream templateStream = UriAccessor.accessUri(templateName);
            return IOUtils.toString(templateStream);
        } catch (final URISyntaxException e) {
            throw new IOException(e);
        }
    }
}
