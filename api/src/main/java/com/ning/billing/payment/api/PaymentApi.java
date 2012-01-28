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

package com.ning.billing.payment.api;

import java.util.List;

import javax.annotation.Nullable;

import com.ning.billing.account.api.Account;

public interface PaymentApi {
    Either<PaymentError, PaymentMethodInfo> getPaymentMethod(@Nullable String accountKey, String paymentMethodId);

    Either<PaymentError, List<PaymentMethodInfo>> getPaymentMethods(String accountKey);

    Either<PaymentError, Void> deletePaymentMethod(String accountKey, String paymentMethodId);

    Either<PaymentError, Void> updatePaymentGateway(String accountKey);

    Either<PaymentError, String> addPaypalPaymentMethod(@Nullable String accountKey, PaypalPaymentMethodInfo paypalPaymentMethod);

    Either<PaymentError, PaymentMethodInfo> updatePaymentMethod(String accountKey, PaymentMethodInfo paymentMethodInfo);

    List<Either<PaymentError, PaymentInfo>> createPayment(String accountKey, List<String> invoiceIds);
    List<Either<PaymentError, PaymentInfo>> createPayment(Account account, List<String> invoiceIds);

    List<Either<PaymentError, PaymentInfo>> createRefund(Account account, List<String> invoiceIds); //TODO

    Either<PaymentError, PaymentProviderAccount> getPaymentProviderAccount(String accountKey);

    Either<PaymentError, String> createPaymentProviderAccount(Account account);

    Either<PaymentError, PaymentProviderAccount> updatePaymentProviderAccount(Account account);

    PaymentAttempt getPaymentAttemptForPaymentId(String id);

}
