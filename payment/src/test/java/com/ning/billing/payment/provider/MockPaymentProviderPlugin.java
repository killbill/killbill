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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.ning.billing.account.api.Account;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.payment.PaymentInfo;
import com.ning.billing.payment.PaymentProviderAccount;
import com.ning.billing.payment.PaypalPaymentMethodInfo;
import com.ning.billing.payment.api.Either;
import com.ning.billing.payment.api.PaymentError;
import com.ning.billing.payment.api.PaymentMethodInfo;

public class MockPaymentProviderPlugin implements PaymentProviderPlugin {
    private final Map<String, PaymentInfo> payments = new ConcurrentHashMap<String, PaymentInfo>();

    @Override
    public Either<PaymentError, PaymentInfo> processInvoice(Account account, Invoice invoice) {
        PaymentInfo payment = new PaymentInfo.Builder().setId(UUID.randomUUID().toString()).build();

        payments.put(payment.getId(), payment);
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
    public Either<PaymentError, PaymentProviderAccount> createPaymentProviderAccount(Account account) {
        // TODO
        return Either.left(new PaymentError("unknown", "Not implemented"));
    }

    @Override
    public Either<PaymentError, PaymentMethodInfo> getPaymentMethodInfo(String paymentMethodId) {
        // TODO
        return Either.left(new PaymentError("unknown", "Not implemented"));
    }

    @Override
    public Either<PaymentError, List<PaymentMethodInfo>> getPaymentMethods(String accountId) {
        // TODO
        return Either.left(new PaymentError("unknown", "Not implemented"));
    }

    @Override
    public Either<PaymentError, PaypalPaymentMethodInfo> addPaypalPaymentMethod(Account account, PaypalPaymentMethodInfo paypalPaymentMethod) {
        // TODO
        return Either.left(new PaymentError("unknown", "Not implemented"));
    }

    @Override
    public Either<PaymentError, Void> updatePaymentGateway(String accountKey) {
        // TODO
        return Either.left(new PaymentError("unknown", "Not implemented"));
    }
}
