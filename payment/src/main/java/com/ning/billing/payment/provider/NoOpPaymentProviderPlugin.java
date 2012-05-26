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

package com.ning.billing.payment.provider;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.ning.billing.account.api.Account;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.payment.api.PaymentMethodInfo;
import com.ning.billing.payment.api.PaymentProviderAccount;
import com.ning.billing.payment.plugin.api.PaymentInfoPlugin;
import com.ning.billing.payment.plugin.api.PaymentPluginApiException;
import com.ning.billing.payment.plugin.api.PaymentProviderPlugin;

public class NoOpPaymentProviderPlugin implements PaymentProviderPlugin {

    private boolean makeAllInvoicesFail;

    public boolean isMakeAllInvoicesFail() {
        return makeAllInvoicesFail;
    }

    public void setMakeAllInvoicesFail(boolean makeAllInvoicesFail) {
        this.makeAllInvoicesFail = makeAllInvoicesFail;
    } 

    @Override
    public PaymentInfoPlugin processInvoice(final Account account, final Invoice invoice)
            throws PaymentPluginApiException {
        PaymentInfoPlugin payment = new PaymentInfoPlugin() {
            @Override
            public DateTime getUpdatedDate() {
                return new DateTime(DateTimeZone.UTC);
            }
            @Override
            public String getType() {
                return "Electronic";
            }
            @Override
            public String getStatus() {
                return "Processed";
            }
            @Override
            public BigDecimal getRefundAmount() {
                return null;
            }
            @Override
            public String getReferenceId() {
                return null;
            }
            @Override
            public String getPaymentNumber() {
                return null;
            }
            @Override
            public String getPaymentMethodId() {
                return null;
            }
            @Override
            public DateTime getEffectiveDate() {
                return null;
            }
            @Override
            public DateTime getCreatedDate() {
                return new DateTime(DateTimeZone.UTC);
            }
            @Override
            public String getBankIdentificationNumber() {
                return null;
            }

            @Override
            public String getExternalPaymentId() {
                return null;
            }

            @Override
            public BigDecimal getAmount() {
                return invoice.getBalance();
            }
        };
        return payment;
    }

    @Override
    public String createPaymentProviderAccount(Account account)
            throws PaymentPluginApiException {
        
        return null;
    }

    @Override
    public PaymentInfoPlugin getPaymentInfo(String paymentId)
            throws PaymentPluginApiException {
        
        return null;
    }

    @Override
    public PaymentProviderAccount getPaymentProviderAccount(String accountKey)
            throws PaymentPluginApiException {
        return null;
    }

    @Override
    public void updatePaymentGateway(String accountKey)
            throws PaymentPluginApiException {
    }

    @Override
    public PaymentMethodInfo getPaymentMethodInfo(String paymentMethodId)
            throws PaymentPluginApiException {
        return null;
    }

    @Override
    public List<PaymentMethodInfo> getPaymentMethods(String accountKey)
            throws PaymentPluginApiException {
        return null;
    }

    @Override
    public String addPaymentMethod(String accountKey,
            PaymentMethodInfo paymentMethod) throws PaymentPluginApiException {
        return null;
    }

    @Override
    public PaymentMethodInfo updatePaymentMethod(String accountKey,
            PaymentMethodInfo paymentMethodInfo)
            throws PaymentPluginApiException {
        return null;
    }

    @Override
    public void deletePaymentMethod(String accountKey, String paymentMethodId)
    throws PaymentPluginApiException {
    }

    @Override
    public void updatePaymentProviderAccountExistingContact(Account account)
            throws PaymentPluginApiException {

    }

    @Override
    public void updatePaymentProviderAccountWithNewContact(Account account)
            throws PaymentPluginApiException {

    }

    @Override
    public List<PaymentInfoPlugin> processRefund(Account account)
            throws PaymentPluginApiException {
        return null;
    }

}
