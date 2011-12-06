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

import com.ning.billing.account.api.IAccount;
import com.ning.billing.invoice.model.Invoice;
import com.ning.billing.payment.PaymentInfo;
import com.ning.billing.payment.PaymentProviderAccount;
import com.ning.billing.payment.api.Either;
import com.ning.billing.payment.api.PaymentError;
import com.ning.billing.payment.api.PaymentMethodInfo;

public interface PaymentProviderPlugin {
    Either<PaymentError, PaymentInfo> processInvoice(IAccount account, Invoice invoice);
    Either<PaymentError, PaymentInfo> getPaymentInfo(String paymentId);
    Either<PaymentError, PaymentProviderAccount> createPaymentProviderAccount(IAccount account);
    Either<PaymentError, PaymentMethodInfo> getPaymentMethodInfo(String paymentMethodId);
    Either<PaymentError, List<PaymentMethodInfo>> getPaymentMethods(String accountId);

}
