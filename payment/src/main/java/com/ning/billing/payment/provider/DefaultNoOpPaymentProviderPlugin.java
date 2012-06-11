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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang.RandomStringUtils;

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.payment.api.PaymentMethodPlugin;
import com.ning.billing.payment.plugin.api.NoOpPaymentPluginApi;
import com.ning.billing.payment.plugin.api.PaymentInfoPlugin;
import com.ning.billing.payment.plugin.api.PaymentPluginApiException;
import com.ning.billing.payment.plugin.api.PaymentProviderAccount;
import com.ning.billing.payment.plugin.api.PaymentPluginApi;
import com.ning.billing.payment.plugin.api.PaymentInfoPlugin.PaymentPluginStatus;
import com.ning.billing.util.clock.Clock;

public class DefaultNoOpPaymentProviderPlugin implements NoOpPaymentPluginApi {

    
    private final AtomicBoolean makeNextInvoiceFailWithError = new AtomicBoolean(false);
    private final AtomicBoolean makeNextInvoiceFailWithException = new AtomicBoolean(false);
    private final AtomicBoolean makeAllInvoicesFailWithError = new AtomicBoolean(false);
    private final Map<UUID, PaymentInfoPlugin> payments = new ConcurrentHashMap<UUID, PaymentInfoPlugin>();

    private final Map<String, List<PaymentMethodPlugin>> paymentMethods = new ConcurrentHashMap<String, List<PaymentMethodPlugin>>();

    private final Map<String, PaymentProviderAccount> accounts = new ConcurrentHashMap<String, PaymentProviderAccount>();
    private final Clock clock;

    @Inject
    public DefaultNoOpPaymentProviderPlugin(Clock clock) {
        this.clock = clock;
        clear();
    }
    
    @Override
    public void clear() {
        makeNextInvoiceFailWithException.set(false);
        makeAllInvoicesFailWithError.set(false);
        makeNextInvoiceFailWithError.set(false);
    }
    
    @Override
    public void makeNextPaymentFailWithError() {
        makeNextInvoiceFailWithError.set(true);
    }

    @Override
    public void makeNextPaymentFailWithException() {
        makeNextInvoiceFailWithException.set(true);
    }

    @Override
    public void makeAllInvoicesFailWithError(boolean failure) {
        makeAllInvoicesFailWithError.set(failure);
    }

    
    @Override
    public String getName() {
        return null;
    }


    @Override
    public PaymentInfoPlugin processPayment(String externalKey, UUID paymentId, BigDecimal amount) throws PaymentPluginApiException {
        if (makeNextInvoiceFailWithException.getAndSet(false)) {
            throw new PaymentPluginApiException("", "test error");
        }

        PaymentPluginStatus status = (makeAllInvoicesFailWithError.get() || makeNextInvoiceFailWithError.getAndSet(false)) ? PaymentPluginStatus.ERROR : PaymentPluginStatus.PROCESSED;
        PaymentInfoPlugin result = new DefaultNoOpPaymentInfoPlugin(amount, clock.getUTCNow(), clock.getUTCNow(), status, null);
        payments.put(paymentId, result);
        return result;
    }


    @Override
    public PaymentInfoPlugin getPaymentInfo(UUID paymentId) throws PaymentPluginApiException {
        PaymentInfoPlugin payment = payments.get(paymentId);
        if (payment == null) {
            throw new PaymentPluginApiException("", "No payment found for id " + paymentId);
        }
        return payment;
    }

    @Override
    public String createPaymentProviderAccount(Account account)  throws PaymentPluginApiException {
        if (account != null) {
            String id = String.valueOf(RandomStringUtils.randomAlphanumeric(10));
            String paymentMethodId = String.valueOf(RandomStringUtils.randomAlphanumeric(10));            
            accounts.put(account.getExternalKey(),
                         new PaymentProviderAccount.Builder().setAccountKey(account.getExternalKey())
                                                             .setId(id)
                                                             .setDefaultPaymentMethod(paymentMethodId)
                                                             .build());
            return id;
        }
        else {
            throw new PaymentPluginApiException("", "Did not get account to create payment provider account");
        }
    }

    @Override
    public String addPaymentMethod(String accountKey, PaymentMethodPlugin paymentMethodProps, boolean setDefault)  throws PaymentPluginApiException {
        PaymentMethodPlugin realWithID = new DefaultNoOpPaymentMethodPlugin(paymentMethodProps);
        List<PaymentMethodPlugin> pms = paymentMethods.get(accountKey);
        if (pms == null) {
            pms = new LinkedList<PaymentMethodPlugin>();
            paymentMethods.put(accountKey, pms);
        }
        pms.add(realWithID);
        
        
        return realWithID.getExternalPaymentMethodId();
    }


    @Override
    public void updatePaymentMethod(String accountKey, String externalPaymentId, PaymentMethodPlugin paymentMethodProps)
        throws PaymentPluginApiException {
        DefaultNoOpPaymentMethodPlugin e = getPaymentMethod(accountKey, externalPaymentId);
        if (e != null) {
            e.setProps(paymentMethodProps.getProperties());
        }
    }

    @Override
    public void deletePaymentMethod(String accountKey, String paymentMethodId)  throws PaymentPluginApiException {

        PaymentMethodPlugin toBeDeleted = null;
        List<PaymentMethodPlugin> pms = paymentMethods.get(accountKey);
        if (pms != null) {

            for (PaymentMethodPlugin cur : pms) {
                if (cur.getExternalPaymentMethodId().equals(paymentMethodId)) {
                    toBeDeleted = cur;
                    break;
                }
            }
        }
        if (toBeDeleted != null) {
            pms.remove(toBeDeleted);
        }
    }

    @Override
    public List<PaymentMethodPlugin> getPaymentMethodDetails(String accountKey)
            throws PaymentPluginApiException {
        return paymentMethods.get(accountKey);
    }

    @Override
    public PaymentMethodPlugin getPaymentMethodDetail(String accountKey, String externalPaymentId) 
    throws PaymentPluginApiException {
        return getPaymentMethodDetail(accountKey, externalPaymentId);
    }
    
    private DefaultNoOpPaymentMethodPlugin getPaymentMethod(String accountKey, String externalPaymentId) {
        List<PaymentMethodPlugin> pms = paymentMethods.get(accountKey);
        if (pms == null) {
            return null;
        }
        for (PaymentMethodPlugin cur : pms) {
            if (cur.getExternalPaymentMethodId().equals(externalPaymentId)) {
                return (DefaultNoOpPaymentMethodPlugin) cur;
            }
        }
        return null;
    }
    
    @Override
    public void setDefaultPaymentMethod(String accountKey,
            String externalPaymentId) throws PaymentPluginApiException {
    }

    @Override
    public List<PaymentInfoPlugin> processRefund(Account account)
            throws PaymentPluginApiException {
        return null;
    }
}
