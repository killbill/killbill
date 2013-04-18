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

package com.ning.billing.util.svcapi.payment;

import java.util.List;
import java.util.UUID;

import com.ning.billing.account.api.Account;
import com.ning.billing.payment.api.Payment;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.api.PaymentMethod;
import com.ning.billing.util.callcontext.InternalTenantContext;

public interface PaymentInternalApi {

    public Payment getPayment(UUID paymentId, InternalTenantContext context)
            throws PaymentApiException;

    public PaymentMethod getPaymentMethodById(UUID paymentMethodId, final boolean includedInactive, InternalTenantContext context)
            throws PaymentApiException;

    public List<Payment> getAccountPayments(UUID accountId, InternalTenantContext context)
            throws PaymentApiException;

    public List<PaymentMethod> getPaymentMethods(Account account, InternalTenantContext context)
            throws PaymentApiException;
}
