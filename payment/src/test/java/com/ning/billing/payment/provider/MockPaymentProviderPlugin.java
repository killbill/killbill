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

import org.apache.commons.lang.RandomStringUtils;
import org.joda.time.DateTime;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.ning.billing.account.api.Account;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.payment.api.CreditCardPaymentMethodInfo;
import com.ning.billing.payment.api.Either;
import com.ning.billing.payment.api.PaymentError;
import com.ning.billing.payment.api.PaymentInfo;
import com.ning.billing.payment.api.PaymentMethodInfo;
import com.ning.billing.payment.api.PaymentProviderAccount;
import com.ning.billing.payment.api.PaypalPaymentMethodInfo;

public class MockPaymentProviderPlugin implements PaymentProviderPlugin {
    private final Map<String, PaymentInfo> payments = new ConcurrentHashMap<String, PaymentInfo>();
    private final Map<String, PaymentProviderAccount> accounts = new ConcurrentHashMap<String, PaymentProviderAccount>();
    private final Map<String, PaymentMethodInfo> paymentMethods = new ConcurrentHashMap<String, PaymentMethodInfo>();

    @Override
    public Either<PaymentError, PaymentInfo> processInvoice(Account account, Invoice invoice) {
        PaymentInfo payment = new PaymentInfo.Builder().setPaymentId(UUID.randomUUID().toString())
                                             .setAmount(invoice.getBalance())
                                             .setStatus("Processed")
                                             .setBankIdentificationNumber("1234")
                                             .setCreatedDate(new DateTime())
                                             .setEffectiveDate(new DateTime())
                                             .setPaymentNumber("12345")
                                             .setReferenceId("12345")
                                             .setType("Electronic")
                                             .build();
        payments.put(payment.getPaymentId(), payment);
        return Either.right(payment);
    }

    @Override
    public Either<PaymentError, PaymentInfo> getPaymentInfo(String paymentId) {
        PaymentInfo payment = payments.get(paymentId);

        if (payment == null) {
            return Either.left(new PaymentError("notfound", "No payment found for id " + paymentId));
        }
        else {
            return Either.right(payment);
        }
    }

    @Override
    public Either<PaymentError, String> createPaymentProviderAccount(Account account) {
        if (account != null) {
            String id = String.valueOf(RandomStringUtils.random(10));
            accounts.put(account.getExternalKey(),
                         new PaymentProviderAccount.Builder().setAccountNumber(String.valueOf(RandomStringUtils.random(10)))
                                                             .setDefaultPaymentMethod(String.valueOf(RandomStringUtils.random(10)))
                                                             .setId(id)
                                                             .build());

            return Either.right(id);
        }
        else {
            return Either.left(new PaymentError("unknown", "Did not get account to create payment provider account"));
        }
    }

    @Override
    public Either<PaymentError, PaymentProviderAccount> getPaymentProviderAccount(String accountKey) {
        if (accountKey != null) {
            return Either.right(accounts.get(accountKey));
        }
        else {
            return Either.left(new PaymentError("unknown", "Did not get account for accountKey " + accountKey));
        }
    }

    @Override
    public Either<PaymentError, String> addPaymentMethod(String accountKey, PaymentMethodInfo paymentMethod) {
        if (paymentMethod != null) {
            String paymentMethodId = RandomStringUtils.random(10);
            PaymentMethodInfo realPaymentMethod = null;

            if (paymentMethod instanceof PaypalPaymentMethodInfo) {
                PaypalPaymentMethodInfo paypalPaymentMethod = (PaypalPaymentMethodInfo)paymentMethod;
                realPaymentMethod = new PaypalPaymentMethodInfo.Builder(paypalPaymentMethod).setId(paymentMethodId).build();
            }
            else if (paymentMethod instanceof CreditCardPaymentMethodInfo) {
                CreditCardPaymentMethodInfo ccPaymentMethod = (CreditCardPaymentMethodInfo)paymentMethod;
                realPaymentMethod = new CreditCardPaymentMethodInfo.Builder(ccPaymentMethod).setId(paymentMethodId).build();
            }
            if (realPaymentMethod == null) {
                return Either.left(new PaymentError("unsupported", "Payment method " + paymentMethod.getType() + " not supported by the plugin"));
            }
            else {
                paymentMethods.put(paymentMethodId, paymentMethod);
                return Either.right(paymentMethodId);
            }
        }
        else {
            return Either.left(new PaymentError("unknown", "Could not create add payment method " + paymentMethod + " for " + accountKey));
        }
    }

    @Override
    public Either<PaymentError, PaymentMethodInfo> updatePaymentMethod(String accountKey, PaymentMethodInfo paymentMethod) {
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
                return Either.left(new PaymentError("unsupported", "Payment method " + paymentMethod.getType() + " not supported by the plugin"));
            }
            else {
                paymentMethods.put(paymentMethod.getId(), paymentMethod);
                return Either.right(realPaymentMethod);
            }
        }
        else {
            return Either.left(new PaymentError("unknown", "Could not create add payment method " + paymentMethod + " for " + accountKey));
        }
    }

    @Override
    public Either<PaymentError, Void> deletePaymentMethod(String accountKey, String paymentMethodId) {
        if (paymentMethods.remove(paymentMethodId) == null) {
            return Either.left(new PaymentError("unknown", "Did not get any result back"));
        }
        else {
            return Either.right(null);
        }
    }

    @Override
    public Either<PaymentError, PaymentMethodInfo> getPaymentMethodInfo(String paymentMethodId) {
        if (paymentMethodId != null) {
            return Either.left(new PaymentError("unknown", "Could not retrieve payment method for paymentMethodId " + paymentMethodId));
        }

        return Either.right(paymentMethods.get(paymentMethodId));
    }

    @Override
    public Either<PaymentError, List<PaymentMethodInfo>> getPaymentMethods(final String accountId) {

        Collection<PaymentMethodInfo> filteredPaymentMethods = Collections2.filter(paymentMethods.values(), new Predicate<PaymentMethodInfo>() {
            @Override
            public boolean apply(PaymentMethodInfo input) {
                return accountId.equals(input.getAccountId());
            }
        });
        List<PaymentMethodInfo> result = new ArrayList<PaymentMethodInfo>(filteredPaymentMethods);
        return Either.right(result);
    }

    @Override
    public Either<PaymentError, Void> updatePaymentGateway(String accountKey) {
        return Either.right(null);
    }

    @Override
    public Either<PaymentError, Void> updatePaymentProviderAccountWithExistingContact(Account account) {
        // nothing to do here
        return Either.right(null);
    }

    @Override
    public Either<PaymentError, Void> updatePaymentProviderAccountWithNewContact(Account account) {
        // nothing to do here
        return Either.right(null);
    }

}
