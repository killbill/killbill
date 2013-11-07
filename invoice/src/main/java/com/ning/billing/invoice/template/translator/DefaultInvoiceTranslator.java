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

package com.ning.billing.invoice.template.translator;

import java.util.Locale;

import com.ning.billing.util.template.translation.DefaultTranslatorBase;
import com.ning.billing.util.template.translation.TranslatorConfig;

import com.google.inject.Inject;

public class DefaultInvoiceTranslator extends DefaultTranslatorBase implements InvoiceStrings {

    private Locale locale;

    @Inject
    public DefaultInvoiceTranslator(final TranslatorConfig config) {
        super(config);
    }

    public void setLocale(final Locale locale) {
        this.locale = locale;
    }

    @Override
    protected String getBundlePath() {
        return config.getInvoiceTemplateBundlePath();
    }

    @Override
    protected String getTranslationType() {
        return "invoice";
    }

    @Override
    public String getInvoiceTitle() {
        return getTranslation(locale, "invoiceTitle");
    }

    @Override
    public String getInvoiceDate() {
        return getTranslation(locale, "invoiceDate");
    }

    @Override
    public String getInvoiceNumber() {
        return getTranslation(locale, "invoiceNumber");
    }

    @Override
    public String getAccountOwnerName() {
        return getTranslation(locale, "accountOwnerName");
    }

    @Override
    public String getAccountOwnerEmail() {
        return getTranslation(locale, "accountOwnerEmail");
    }

    @Override
    public String getAccountOwnerPhone() {
        return getTranslation(locale, "accountOwnerPhone");
    }

    @Override
    public String getCompanyName() {
        return getTranslation(locale, "companyName");
    }

    @Override
    public String getCompanyAddress() {
        return getTranslation(locale, "companyAddress");
    }

    @Override
    public String getCompanyCityProvincePostalCode() {
        return getTranslation(locale, "companyCityProvincePostalCode");
    }

    @Override
    public String getCompanyCountry() {
        return getTranslation(locale, "companyCountry");
    }

    @Override
    public String getCompanyUrl() {
        return getTranslation(locale, "companyUrl");
    }

    @Override
    public String getInvoiceItemBundleName() {
        return getTranslation(locale, "invoiceItemBundleName");
    }

    @Override
    public String getInvoiceItemDescription() {
        return getTranslation(locale, "invoiceItemDescription");
    }

    @Override
    public String getInvoiceItemServicePeriod() {
        return getTranslation(locale, "invoiceItemServicePeriod");
    }

    @Override
    public String getInvoiceItemAmount() {
        return getTranslation(locale, "invoiceItemAmount");
    }

    @Override
    public String getInvoiceAmount() {
        return getTranslation(locale, "invoiceAmount");
    }

    @Override
    public String getInvoiceAmountPaid() {
        return getTranslation(locale, "invoiceAmountPaid");
    }

    @Override
    public String getInvoiceBalance() {
        return getTranslation(locale, "invoiceBalance");
    }

    @Override
    public String getProcessedPaymentCurrency() {
        return getTranslation(locale, "processedPaymentCurrency");
    }

    @Override
    public String getProcessedPaymentRate() {
        return getTranslation(locale, "processedPaymentRate");
    }
}
