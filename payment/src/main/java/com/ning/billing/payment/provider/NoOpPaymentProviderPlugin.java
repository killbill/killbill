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

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.ning.billing.account.api.Account;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.payment.api.DefaultPaymentError;
import com.ning.billing.payment.api.DefaultPaymentInfo;
import com.ning.billing.payment.api.Either;
import com.ning.billing.payment.api.PaymentError;
import com.ning.billing.payment.api.PaymentInfo;
import com.ning.billing.payment.api.PaymentMethodInfo;
import com.ning.billing.payment.api.PaymentProviderAccount;

public class NoOpPaymentProviderPlugin implements PaymentProviderPlugin {

    @Override
    public Either<PaymentError, PaymentInfo> processInvoice(Account account, Invoice invoice) {
        PaymentInfo payment = new DefaultPaymentInfo.Builder()
                                             .setPaymentId(UUID.randomUUID().toString())
                                             .setAmount(invoice.getBalance())
                                             .setStatus("Processed")
                                             .setCreatedDate(new DateTime(DateTimeZone.UTC))
                                             .setEffectiveDate(new DateTime(DateTimeZone.UTC))
                                             .setType("Electronic")
                                             .build();
        return Either.right(payment);
    }

    @Override
    public Either<PaymentError, PaymentInfo> getPaymentInfo(String paymentId) {
        return Either.right(null);
    }

    @Override
    public Either<PaymentError, String> createPaymentProviderAccount(Account account) {
        return Either.left((PaymentError) new DefaultPaymentError("unsupported",
                                            "Account creation not supported in this plugin",
                                            account.getId(),
                                            null, null));
    }

    @Override
    public Either<PaymentError, PaymentProviderAccount> getPaymentProviderAccount(String accountKey) {
        return Either.right(null);
    }

    @Override
    public Either<PaymentError, String> addPaymentMethod(String accountKey, PaymentMethodInfo paymentMethod) {
        return Either.right(null);
    }

    public void setDefaultPaymentMethodOnAccount(PaymentProviderAccount account, String paymentMethodId) {
        // NO-OP
    }

    @Override
    public Either<PaymentError, PaymentMethodInfo> updatePaymentMethod(String accountKey, PaymentMethodInfo paymentMethod) {
        return Either.right(paymentMethod);
    }

    @Override
    public Either<PaymentError, Void> deletePaymentMethod(String accountKey, String paymentMethodId) {
        return Either.right(null);
    }

    @Override
    public Either<PaymentError, PaymentMethodInfo> getPaymentMethodInfo(String paymentMethodId) {
        return Either.right(null);
    }

    @Override
    public Either<PaymentError, List<PaymentMethodInfo>> getPaymentMethods(final String accountKey) {
        return Either.right(Arrays.<PaymentMethodInfo>asList());
    }

    @Override
    public Either<PaymentError, Void> updatePaymentGateway(String accountKey) {
        return Either.right(null);
    }

    @Override
    public Either<PaymentError, Void> updatePaymentProviderAccountExistingContact(Account account) {
        return Either.right(null);
    }

    @Override
    public Either<PaymentError, Void> updatePaymentProviderAccountWithNewContact(Account account) {
        return Either.right(null);
    }

}
