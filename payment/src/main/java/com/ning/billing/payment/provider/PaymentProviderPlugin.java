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

import com.ning.billing.account.api.Account;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.payment.api.Either;
import com.ning.billing.payment.api.PaymentErrorEvent;
import com.ning.billing.payment.api.PaymentInfoEvent;
import com.ning.billing.payment.api.PaymentMethodInfo;
import com.ning.billing.payment.api.PaymentProviderAccount;

public interface PaymentProviderPlugin {
    Either<PaymentErrorEvent, PaymentInfoEvent> processInvoice(Account account, Invoice invoice);
    Either<PaymentErrorEvent, String> createPaymentProviderAccount(Account account);

    Either<PaymentErrorEvent, PaymentInfoEvent> getPaymentInfo(String paymentId);
    Either<PaymentErrorEvent, PaymentProviderAccount> getPaymentProviderAccount(String accountKey);
    Either<PaymentErrorEvent, Void> updatePaymentGateway(String accountKey);

    Either<PaymentErrorEvent, PaymentMethodInfo> getPaymentMethodInfo(String paymentMethodId);
    Either<PaymentErrorEvent, List<PaymentMethodInfo>> getPaymentMethods(String accountKey);
    Either<PaymentErrorEvent, String> addPaymentMethod(String accountKey, PaymentMethodInfo paymentMethod);
    Either<PaymentErrorEvent, PaymentMethodInfo> updatePaymentMethod(String accountKey, PaymentMethodInfo paymentMethodInfo);
    Either<PaymentErrorEvent, Void> deletePaymentMethod(String accountKey, String paymentMethodId);

    Either<PaymentErrorEvent, Void> updatePaymentProviderAccountExistingContact(Account account);
    Either<PaymentErrorEvent, Void> updatePaymentProviderAccountWithNewContact(Account account);

}
