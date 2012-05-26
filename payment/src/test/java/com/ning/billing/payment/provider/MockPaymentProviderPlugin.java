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

package com.ning.billing.payment.provider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang.RandomStringUtils;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.payment.api.CreditCardPaymentMethodInfo;
import com.ning.billing.payment.api.DefaultPaymentInfoEvent;
import com.ning.billing.payment.api.PaymentInfoEvent;
import com.ning.billing.payment.api.PaymentMethodInfo;
import com.ning.billing.payment.api.PaymentProviderAccount;
import com.ning.billing.payment.api.PaypalPaymentMethodInfo;
import com.ning.billing.payment.plugin.api.MockPaymentInfoPlugin;
import com.ning.billing.payment.plugin.api.PaymentInfoPlugin;
import com.ning.billing.payment.plugin.api.PaymentPluginApiException;
import com.ning.billing.payment.plugin.api.PaymentProviderPlugin;
import com.ning.billing.util.clock.Clock;

public class MockPaymentProviderPlugin implements PaymentProviderPlugin {
    
    private final AtomicBoolean makeNextInvoiceFail = new AtomicBoolean(false);
    private final AtomicBoolean makeAllInvoicesFail = new AtomicBoolean(false);
    private final Map<UUID, PaymentInfoEvent> payments = new ConcurrentHashMap<UUID, PaymentInfoEvent>();
    private final Map<String, PaymentProviderAccount> accounts = new ConcurrentHashMap<String, PaymentProviderAccount>();
    private final Map<String, PaymentMethodInfo> paymentMethods = new ConcurrentHashMap<String, PaymentMethodInfo>();
    private final Clock clock;

    @Inject
    public MockPaymentProviderPlugin(Clock clock) {
        this.clock = clock;
    }

    public void makeNextInvoiceFail() {
        makeNextInvoiceFail.set(true);
    }

    public void makeAllInvoicesFail(boolean failure) {
        makeAllInvoicesFail.set(failure);
    }

    @Override
    public PaymentInfoPlugin processInvoice(Account account, Invoice invoice) throws PaymentPluginApiException {
        if (makeNextInvoiceFail.getAndSet(false) || makeAllInvoicesFail.get()) {
            throw new PaymentPluginApiException("", "test error");
        }

        PaymentInfoEvent payment = new DefaultPaymentInfoEvent.Builder().setId(UUID.randomUUID())
                .setExternalPaymentId("238957t49regyuihfd")
                .setAmount(invoice.getBalance())
                .setStatus("Processed")
                .setBankIdentificationNumber("1234")
                .setCreatedDate(clock.getUTCNow())
                .setEffectiveDate(clock.getUTCNow())
                .setPaymentNumber("12345")
                .setReferenceId("12345")
                .setType("Electronic")
                .setPaymentMethodId("123-456-678-89")
                .build();
        
        return new MockPaymentInfoPlugin(payment);
    }


    @Override
    public PaymentInfoPlugin getPaymentInfo(String paymentId) throws PaymentPluginApiException {
        PaymentInfoEvent payment = payments.get(paymentId);
        if (payment == null) {
            throw new PaymentPluginApiException("", "No payment found for id " + paymentId);
        }
        return new MockPaymentInfoPlugin(payment);
    }

    @Override
    public String createPaymentProviderAccount(Account account)  throws PaymentPluginApiException {
        if (account != null) {
            String id = String.valueOf(RandomStringUtils.randomAlphanumeric(10));
            accounts.put(account.getExternalKey(),
                         new PaymentProviderAccount.Builder().setAccountKey(account.getExternalKey())
                                                             .setId(id)
                                                             .build());
            return id;
        }
        else {
            throw new PaymentPluginApiException("", "Did not get account to create payment provider account");
        }
    }

    @Override
    public PaymentProviderAccount getPaymentProviderAccount(String accountKey)  throws PaymentPluginApiException {
        if (accountKey != null) {
            return accounts.get(accountKey);
        }
        else {
            throw new PaymentPluginApiException("", "Did not get account for accountKey " + accountKey);
        }
    }

    @Override
    public String addPaymentMethod(String accountKey, PaymentMethodInfo paymentMethod)  throws PaymentPluginApiException {
        if (paymentMethod != null) {
            PaymentProviderAccount account = accounts.get(accountKey);

            if (account != null && account.getId() != null) {
                String existingDefaultMethod = account.getDefaultPaymentMethodId();

                String paymentMethodId = "5556-66-77-rr";
                boolean shouldBeDefault = Boolean.TRUE.equals(paymentMethod.getDefaultMethod()) || existingDefaultMethod == null;
                PaymentMethodInfo realPaymentMethod = null;

                if (paymentMethod instanceof PaypalPaymentMethodInfo) {
                    PaypalPaymentMethodInfo paypalPaymentMethod = (PaypalPaymentMethodInfo)paymentMethod;

                    realPaymentMethod = new PaypalPaymentMethodInfo.Builder(paypalPaymentMethod)
                    .setId(paymentMethodId)
                    .setAccountId(accountKey)
                    .setDefaultMethod(shouldBeDefault)
                    .setBaid(paypalPaymentMethod.getBaid())
                    .setEmail(paypalPaymentMethod.getEmail())
                    .build();
                }
                else if (paymentMethod instanceof CreditCardPaymentMethodInfo) {
                    CreditCardPaymentMethodInfo ccPaymentMethod = (CreditCardPaymentMethodInfo)paymentMethod;
                    realPaymentMethod = new CreditCardPaymentMethodInfo.Builder(ccPaymentMethod).setId(paymentMethodId).build();
                }
                if (realPaymentMethod == null) {
                    throw new PaymentPluginApiException("", "Payment method " + paymentMethod.getType() + " not supported by the plugin");                    
                }
                else {
                    if (shouldBeDefault) {
                        setDefaultPaymentMethodOnAccount(account, paymentMethodId);
                    }
                    paymentMethods.put(paymentMethodId, realPaymentMethod);
                    return paymentMethodId;
                }
            }
            else {
                throw new PaymentPluginApiException("", "Could not retrieve account for accountKey " + accountKey);                    
            }
        }
        else {
            throw new PaymentPluginApiException("", "Could not create add payment method " + paymentMethod + " for " + accountKey);
        }
    }

    public void setDefaultPaymentMethodOnAccount(PaymentProviderAccount account, String paymentMethodId) {
        if (paymentMethodId != null && account != null) {
            accounts.put(account.getAccountKey(),
                new PaymentProviderAccount.Builder()
                                          .copyFrom(account)
                                          .setDefaultPaymentMethod("paypal")
                                          .build());
            List<PaymentMethodInfo> paymentMethodsToUpdate = new ArrayList<PaymentMethodInfo>();
            for (PaymentMethodInfo paymentMethod : paymentMethods.values()) {
                if (account.getAccountKey().equals(paymentMethod.getAccountId()) && !paymentMethodId.equals(paymentMethod.getId())) {
                    if (paymentMethod instanceof PaypalPaymentMethodInfo) {
                        PaypalPaymentMethodInfo paypalPaymentMethod = (PaypalPaymentMethodInfo)paymentMethod;
                        paymentMethodsToUpdate.add(new PaypalPaymentMethodInfo.Builder(paypalPaymentMethod).setDefaultMethod(false).build());
                    }
                    else if (paymentMethod instanceof CreditCardPaymentMethodInfo) {
                        CreditCardPaymentMethodInfo ccPaymentMethod = (CreditCardPaymentMethodInfo)paymentMethod;
                        paymentMethodsToUpdate.add(new CreditCardPaymentMethodInfo.Builder(ccPaymentMethod).setDefaultMethod(false).build());
                    }
                }
            }
            for (PaymentMethodInfo paymentMethod : paymentMethodsToUpdate) {
                paymentMethods.put(paymentMethod.getId(), paymentMethod);
            }
        }
    }

    @Override
    public PaymentMethodInfo updatePaymentMethod(String accountKey, PaymentMethodInfo paymentMethod)  throws PaymentPluginApiException {
        if (paymentMethod != null) {
            PaymentMethodInfo realPaymentMethod = null;

            if (paymentMethod instanceof PaypalPaymentMethodInfo) {
                PaypalPaymentMethodInfo paypalPaymentMethod = (PaypalPaymentMethodInfo)paymentMethod;
                realPaymentMethod = new PaypalPaymentMethodInfo.Builder(paypalPaymentMethod).build();
            }
            else if (paymentMethod instanceof CreditCardPaymentMethodInfo) {
                CreditCardPaymentMethodInfo ccPaymentMethod = (CreditCardPaymentMethodInfo)paymentMethod;
                realPaymentMethod = new CreditCardPaymentMethodInfo.Builder(ccPaymentMethod).build();
            }
            if (realPaymentMethod == null) {
                throw new PaymentPluginApiException("", "Payment method " + paymentMethod.getType() + " not supported by the plugin");
            }
            else {
                paymentMethods.put(paymentMethod.getId(), paymentMethod);
                return realPaymentMethod;
            }
        }
        else {
            throw new PaymentPluginApiException("", "Could not create add payment method " + paymentMethod + " for " + accountKey);            
        }
    }

    @Override
    public void deletePaymentMethod(String accountKey, String paymentMethodId)  throws PaymentPluginApiException {
        PaymentMethodInfo paymentMethodInfo = paymentMethods.get(paymentMethodId);
        if (paymentMethodInfo != null) {
            if (Boolean.FALSE.equals(paymentMethodInfo.getDefaultMethod()) || paymentMethodInfo.getDefaultMethod() == null) {
                if (paymentMethods.remove(paymentMethodId) == null) {
                    throw new PaymentPluginApiException("", "Did not get any result back");
                }
        } else {
                throw new PaymentPluginApiException("", "Cannot delete default payment method");                
            }
        }
        return;
    }

    @Override
    public PaymentMethodInfo getPaymentMethodInfo(String paymentMethodId)  throws PaymentPluginApiException {
        if (paymentMethodId == null) {
            throw new PaymentPluginApiException("", "Could not retrieve payment method for paymentMethodId " + paymentMethodId);
        }
        return paymentMethods.get(paymentMethodId);
    }

    @Override
    public List<PaymentMethodInfo> getPaymentMethods(final String accountKey) throws PaymentPluginApiException {

        Collection<PaymentMethodInfo> filteredPaymentMethods = Collections2.filter(paymentMethods.values(), new Predicate<PaymentMethodInfo>() {
            @Override
            public boolean apply(PaymentMethodInfo input) {
                return accountKey.equals(input.getAccountId());
            }
        });
        List<PaymentMethodInfo> result = new ArrayList<PaymentMethodInfo>(filteredPaymentMethods);
        return result;
    }

    @Override
    public void updatePaymentGateway(String accountKey)  throws PaymentPluginApiException {
    }

    @Override
    public void updatePaymentProviderAccountExistingContact(Account account)  throws PaymentPluginApiException {
    }

    @Override
    public void updatePaymentProviderAccountWithNewContact(Account account)  throws PaymentPluginApiException {
    }

    @Override
    public List<PaymentInfoPlugin> processRefund(Account account)  throws PaymentPluginApiException {
        return null;
    }
}
