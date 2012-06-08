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
import com.ning.billing.payment.api.PaymentMethodPlugin;
import com.ning.billing.payment.plugin.api.PaymentInfoPlugin;
import com.ning.billing.payment.plugin.api.PaymentPluginApiException;
import com.ning.billing.payment.plugin.api.PaymentProviderAccount;
import com.ning.billing.payment.plugin.api.PaymentPluginApi;

public class NoOpPaymentProviderPlugin implements PaymentPluginApi {

    private boolean makeAllInvoicesFail;

    public boolean isMakeAllInvoicesFail() {
        return makeAllInvoicesFail;
    }

    public void setMakeAllInvoicesFail(boolean makeAllInvoicesFail) {
        this.makeAllInvoicesFail = makeAllInvoicesFail;
    } 

    @Override
    public PaymentInfoPlugin processPayment(final String externalAccountKey, final UUID paymentId, final BigDecimal amount)
            throws PaymentPluginApiException {

        PaymentInfoPlugin paymentResult = new PaymentInfoPlugin() {
            @Override
            public DateTime getEffectiveDate() {
                return null;
            }
            @Override
            public DateTime getCreatedDate() {
                return new DateTime(DateTimeZone.UTC);
            }
            @Override
            public BigDecimal getAmount() {
                return amount;
            }
            @Override
            public PaymentPluginStatus getStatus() {
                return PaymentPluginStatus.PROCESSED;
            }

            @Override
            public String getGatewayError() {
                return null;
            }
            @Override
            public String getGatewayErrorCode() {
                return null;
            }
        };
        return paymentResult;
    }

    @Override
    public String createPaymentProviderAccount(Account account)
            throws PaymentPluginApiException {
        
        return null;
    }

    @Override
    public PaymentInfoPlugin getPaymentInfo(UUID paymentId)
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

    @Override
    public String getName() {
        return null;
    }

    @Override
    public List<PaymentMethodPlugin> getPaymentMethodDetails(String accountKey)
            throws PaymentPluginApiException {
        return null;
    }

    @Override
    public PaymentMethodPlugin getPaymentMethodDetail(String accountKey, String externalPaymentId)
            throws PaymentPluginApiException {
        return null;
    }

    @Override
    public String addPaymentMethod(String accountKey,
            PaymentMethodPlugin paymentMethodProps, boolean setDefault)
            throws PaymentPluginApiException {
        return null;
    }

    @Override
    public void updatePaymentMethod(String accountKey,
            String externalPaymentId, PaymentMethodPlugin paymentMethodProps)
            throws PaymentPluginApiException {
    }

    @Override
    public void setDefaultPaymentMethod(String accountKey,
            String externalPaymentId) throws PaymentPluginApiException {
    }
}
