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

package org.killbill.billing.invoice.template.translator;

import java.util.ResourceBundle;

import org.killbill.billing.util.template.translation.DefaultTranslatorBase;
import org.killbill.billing.util.template.translation.TranslatorConfig;

public class DefaultInvoiceTranslator extends DefaultTranslatorBase {

    public DefaultInvoiceTranslator(final ResourceBundle bundle, final ResourceBundle defaultBundle) {
        super(bundle, defaultBundle);
    }

    public String getInvoiceEmailSubject() {
        String subject = getTranslation("invoiceEmailSubject");
        return (!"invoiceEmailSubject".equals(subject)) ? subject : null;
    }

    public String getInvoiceTitle() {
        return getTranslation("invoiceTitle");
    }

    public String getInvoiceDate() {
        return getTranslation("invoiceDate");
    }

    public String getInvoiceNumber() {
        return getTranslation("invoiceNumber");
    }

    public String getAccountOwnerName() {
        return getTranslation("accountOwnerName");
    }

    public String getAccountOwnerEmail() {
        return getTranslation("accountOwnerEmail");
    }

    public String getAccountOwnerPhone() {
        return getTranslation("accountOwnerPhone");
    }

    public String getCompanyName() {
        return getTranslation("companyName");
    }

    public String getCompanyAddress() {
        return getTranslation("companyAddress");
    }

    public String getCompanyCityProvincePostalCode() {
        return getTranslation("companyCityProvincePostalCode");
    }

    public String getCompanyCountry() {
        return getTranslation("companyCountry");
    }

    public String getCompanyUrl() {
        return getTranslation("companyUrl");
    }

    public String getInvoiceItemBundleName() {
        return getTranslation("invoiceItemBundleName");
    }

    public String getInvoiceItemDescription() {
        return getTranslation("invoiceItemDescription");
    }

    public String getInvoiceItemServicePeriod() {
        return getTranslation("invoiceItemServicePeriod");
    }

    public String getInvoiceItemAmount() {
        return getTranslation("invoiceItemAmount");
    }

    public String getInvoiceAmount() {
        return getTranslation("invoiceAmount");
    }

    public String getInvoiceAmountPaid() {
        return getTranslation("invoiceAmountPaid");
    }

    public String getInvoiceBalance() {
        return getTranslation("invoiceBalance");
    }

    public String getProcessedPaymentCurrency() {
        return getTranslation("processedPaymentCurrency");
    }

    public String getProcessedPaymentRate() {
        return getTranslation("processedPaymentRate");
    }
}
