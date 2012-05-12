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
import com.ning.billing.payment.api.DefaultPaymentErrorEvent;
import com.ning.billing.payment.api.DefaultPaymentInfoEvent;
import com.ning.billing.payment.api.Either;
import com.ning.billing.payment.api.PaymentErrorEvent;
import com.ning.billing.payment.api.PaymentInfoEvent;
import com.ning.billing.payment.api.PaymentMethodInfo;
import com.ning.billing.payment.api.PaymentProviderAccount;
import com.ning.billing.payment.api.PaypalPaymentMethodInfo;
import com.ning.billing.util.clock.Clock;

public class MockPaymentProviderPlugin implements PaymentProviderPlugin {
    private final AtomicBoolean makeNextInvoiceFail = new AtomicBoolean(false);
    private final AtomicBoolean makeAllInvoicesFail = new AtomicBoolean(false);
    private final Map<String, PaymentInfoEvent> payments = new ConcurrentHashMap<String, PaymentInfoEvent>();
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
    public Either<PaymentErrorEvent, PaymentInfoEvent> processInvoice(Account account, Invoice invoice) {
        if (makeNextInvoiceFail.getAndSet(false) || makeAllInvoicesFail.get()) {
            return Either.left((PaymentErrorEvent) new DefaultPaymentErrorEvent("unknown", "test error", account.getId(), invoice.getId(), null));
        }
        else {
            PaymentInfoEvent payment = new DefaultPaymentInfoEvent.Builder().setPaymentId(UUID.randomUUID().toString())
                                                 .setAmount(invoice.getBalance())
                                                 .setStatus("Processed")
                                                 .setBankIdentificationNumber("1234")
                                                 .setCreatedDate(clock.getUTCNow())
                                                 .setEffectiveDate(clock.getUTCNow())
                                                 .setPaymentNumber("12345")
                                                 .setReferenceId("12345")
                                                 .setType("Electronic")
                                                 .build();
            payments.put(payment.getPaymentId(), payment);
            return Either.right(payment);
        }
    }

    @Override
    public Either<PaymentErrorEvent, PaymentInfoEvent> getPaymentInfo(String paymentId) {
        PaymentInfoEvent payment = payments.get(paymentId);

        if (payment == null) {
            return Either.left((PaymentErrorEvent) new DefaultPaymentErrorEvent("notfound", "No payment found for id " + paymentId, null, null, null));
        }
        else {
            return Either.right(payment);
        }
    }

    @Override
    public Either<PaymentErrorEvent, String> createPaymentProviderAccount(Account account) {
        if (account != null) {
            String id = String.valueOf(RandomStringUtils.randomAlphanumeric(10));
            accounts.put(account.getExternalKey(),
                         new PaymentProviderAccount.Builder().setAccountKey(account.getExternalKey())
                                                             .setId(id)
                                                             .build());

            return Either.right(id);
        }
        else {
            return Either.left((PaymentErrorEvent)  new DefaultPaymentErrorEvent("unknown", "Did not get account to create payment provider account", null, null, null));
        }
    }

    @Override
    public Either<PaymentErrorEvent, PaymentProviderAccount> getPaymentProviderAccount(String accountKey) {
        if (accountKey != null) {
            return Either.right(accounts.get(accountKey));
        }
        else {
            return Either.left((PaymentErrorEvent) new DefaultPaymentErrorEvent("unknown", "Did not get account for accountKey " + accountKey, null, null, null));
        }
    }

    @Override
    public Either<PaymentErrorEvent, String> addPaymentMethod(String accountKey, PaymentMethodInfo paymentMethod) {
        if (paymentMethod != null) {
            PaymentProviderAccount account = accounts.get(accountKey);

            if (account != null && account.getId() != null) {
                String existingDefaultMethod = account.getDefaultPaymentMethodId();

                String paymentMethodId = RandomStringUtils.randomAlphanumeric(10);
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
                    return Either.left((PaymentErrorEvent)  new DefaultPaymentErrorEvent("unsupported", "Payment method " + paymentMethod.getType() + " not supported by the plugin", null, null, null));
                }
                else {
                    if (shouldBeDefault) {
                        setDefaultPaymentMethodOnAccount(account, paymentMethodId);
                    }
                    paymentMethods.put(paymentMethodId, realPaymentMethod);
                    return Either.right(paymentMethodId);
                }
            }
                else {
                    return Either.left((PaymentErrorEvent)  new DefaultPaymentErrorEvent("noaccount", "Could not retrieve account for accountKey " + accountKey, null, null, null));
                }
        }
        else {
            return Either.left((PaymentErrorEvent)  new DefaultPaymentErrorEvent("unknown", "Could not create add payment method " + paymentMethod + " for " + accountKey, null, null, null));
        }
    }

    public void setDefaultPaymentMethodOnAccount(PaymentProviderAccount account, String paymentMethodId) {
        if (paymentMethodId != null && account != null) {
            accounts.put(account.getAccountKey(),
                new PaymentProviderAccount.Builder()
                                          .copyFrom(account)
                                          .setDefaultPaymentMethod(paymentMethodId)
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
    public Either<PaymentErrorEvent, PaymentMethodInfo> updatePaymentMethod(String accountKey, PaymentMethodInfo paymentMethod) {
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
                return Either.left((PaymentErrorEvent)  new DefaultPaymentErrorEvent("unsupported", "Payment method " + paymentMethod.getType() + " not supported by the plugin", null, null, null));
            }
            else {
                paymentMethods.put(paymentMethod.getId(), paymentMethod);
                return Either.right(realPaymentMethod);
            }
        }
        else {
            return Either.left((PaymentErrorEvent)  new DefaultPaymentErrorEvent("unknown", "Could not create add payment method " + paymentMethod + " for " + accountKey, null, null, null));
        }
    }

    @Override
    public Either<PaymentErrorEvent, Void> deletePaymentMethod(String accountKey, String paymentMethodId) {
        PaymentMethodInfo paymentMethodInfo = paymentMethods.get(paymentMethodId);
        if (paymentMethodInfo != null) {
            if (Boolean.FALSE.equals(paymentMethodInfo.getDefaultMethod()) || paymentMethodInfo.getDefaultMethod() == null) {
                if (paymentMethods.remove(paymentMethodId) == null) {
                    return Either.left((PaymentErrorEvent) new DefaultPaymentErrorEvent("unknown", "Did not get any result back", null, null, null));
                }
            }
            else {
                return Either.left((PaymentErrorEvent) new DefaultPaymentErrorEvent("error", "Cannot delete default payment method", null, null, null));
            }
        }
        return Either.right(null);
    }

    @Override
    public Either<PaymentErrorEvent, PaymentMethodInfo> getPaymentMethodInfo(String paymentMethodId) {
        if (paymentMethodId == null) {
            return Either.left((PaymentErrorEvent) new DefaultPaymentErrorEvent("unknown", "Could not retrieve payment method for paymentMethodId " + paymentMethodId, null, null, null));
        }

        return Either.right(paymentMethods.get(paymentMethodId));
    }

    @Override
    public Either<PaymentErrorEvent, List<PaymentMethodInfo>> getPaymentMethods(final String accountKey) {

        Collection<PaymentMethodInfo> filteredPaymentMethods = Collections2.filter(paymentMethods.values(), new Predicate<PaymentMethodInfo>() {
            @Override
            public boolean apply(PaymentMethodInfo input) {
                return accountKey.equals(input.getAccountId());
            }
        });
        List<PaymentMethodInfo> result = new ArrayList<PaymentMethodInfo>(filteredPaymentMethods);
        return Either.right(result);
    }

    @Override
    public Either<PaymentErrorEvent, Void> updatePaymentGateway(String accountKey) {
        return Either.right(null);
    }

    @Override
    public Either<PaymentErrorEvent, Void> updatePaymentProviderAccountExistingContact(Account account) {
        // nothing to do here
        return Either.right(null);
    }

    @Override
    public Either<PaymentErrorEvent, Void> updatePaymentProviderAccountWithNewContact(Account account) {
        return Either.right(null);
    }

    @Override
    public List<Either<PaymentErrorEvent, PaymentInfoEvent>> processRefund(Account account) {
        return null;
    }
}
