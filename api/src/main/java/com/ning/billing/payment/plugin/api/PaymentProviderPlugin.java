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

package com.ning.billing.payment.plugin.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.ning.billing.account.api.Account;
import com.ning.billing.payment.api.PaymentMethodInfo;

public interface PaymentProviderPlugin {
    
    public PaymentInfoPlugin processPayment(String externalAccountKey, UUID paymentId, BigDecimal amount)
    throws PaymentPluginApiException;

    public String createPaymentProviderAccount(Account account)
    throws PaymentPluginApiException;

    public PaymentInfoPlugin getPaymentInfo(UUID paymentId)
    throws PaymentPluginApiException;

    public PaymentProviderAccount getPaymentProviderAccount(String accountKey)
    throws PaymentPluginApiException;

    public void updatePaymentGateway(String accountKey)
    throws PaymentPluginApiException;    

    public PaymentMethodInfo getPaymentMethodInfo(String paymentMethodId) 
    throws PaymentPluginApiException;

    public List<PaymentMethodInfo> getPaymentMethods(String accountKey)
    throws PaymentPluginApiException;

    public String addPaymentMethod(String accountKey, PaymentMethodInfo paymentMethod)
    throws PaymentPluginApiException;

    public PaymentMethodInfo updatePaymentMethod(String accountKey, PaymentMethodInfo paymentMethodInfo)
    throws PaymentPluginApiException;

    public void deletePaymentMethod(String accountKey, String paymentMethodId)
    throws PaymentPluginApiException;    


    public void updatePaymentProviderAccountExistingContact(Account account)
    throws PaymentPluginApiException;

    public void updatePaymentProviderAccountWithNewContact(Account account)
    throws PaymentPluginApiException;

    public List<PaymentInfoPlugin> processRefund(Account account)
    throws PaymentPluginApiException;
}
