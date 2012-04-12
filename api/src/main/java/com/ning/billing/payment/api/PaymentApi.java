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
import java.util.UUID;

import javax.annotation.Nullable;

import com.ning.billing.account.api.Account;
import com.ning.billing.util.callcontext.CallContext;

public interface PaymentApi {

    Either<PaymentError, Void> updatePaymentGateway(String accountKey, CallContext context);

    Either<PaymentError, PaymentMethodInfo> getPaymentMethod(@Nullable String accountKey, String paymentMethodId);

    Either<PaymentError, List<PaymentMethodInfo>> getPaymentMethods(String accountKey);

    Either<PaymentError, String> addPaymentMethod(@Nullable String accountKey, PaymentMethodInfo paymentMethod, CallContext context);

    Either<PaymentError, PaymentMethodInfo> updatePaymentMethod(String accountKey, PaymentMethodInfo paymentMethodInfo, CallContext context);

    Either<PaymentError, Void> deletePaymentMethod(String accountKey, String paymentMethodId, CallContext context);

    List<Either<PaymentError, PaymentInfo>> createPayment(String accountKey, List<String> invoiceIds, CallContext context);
    List<Either<PaymentError, PaymentInfo>> createPayment(Account account, List<String> invoiceIds, CallContext context);
    Either<PaymentError, PaymentInfo> createPaymentForPaymentAttempt(UUID paymentAttemptId, CallContext context);

    List<Either<PaymentError, PaymentInfo>> createRefund(Account account, List<String> invoiceIds, CallContext context); //TODO

    Either<PaymentError, PaymentProviderAccount> getPaymentProviderAccount(String accountKey);

    Either<PaymentError, String> createPaymentProviderAccount(Account account, CallContext context);

    Either<PaymentError, Void> updatePaymentProviderAccountContact(String accountKey, CallContext context);

    PaymentAttempt getPaymentAttemptForPaymentId(String id);

    List<PaymentInfo> getPaymentInfo(List<String> invoiceIds);

    List<PaymentAttempt> getPaymentAttemptsForInvoiceId(String invoiceId);

    PaymentInfo getPaymentInfoForPaymentAttemptId(String paymentAttemptId);

}
