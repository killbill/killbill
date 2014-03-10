/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.payment.api.svcs;

import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentInternalApi;
import org.killbill.billing.payment.api.PaymentMethod;
import org.killbill.billing.payment.core.PaymentMethodProcessor;
import org.killbill.billing.payment.core.PaymentProcessor;
import org.killbill.billing.callcontext.InternalTenantContext;

public class DefaultPaymentInternalApi implements PaymentInternalApi {

    private final PaymentProcessor paymentProcessor;
    private final PaymentMethodProcessor methodProcessor;

    @Inject
    public DefaultPaymentInternalApi(final PaymentProcessor paymentProcessor, final PaymentMethodProcessor methodProcessor) {
        this.paymentProcessor = paymentProcessor;
        this.methodProcessor = methodProcessor;
    }

    @Override
    public Payment getPayment(final UUID paymentId, final InternalTenantContext context) throws PaymentApiException {
        final Payment payment = paymentProcessor.getPayment(paymentId, false, context);
        if (payment == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT, paymentId);
        }
        return payment;
    }

    @Override
    public PaymentMethod getPaymentMethodById(final UUID paymentMethodId, final boolean includedInactive, final InternalTenantContext context) throws PaymentApiException {
        return methodProcessor.getPaymentMethodById(paymentMethodId, includedInactive, false, context);
    }

    @Override
    public List<Payment> getAccountPayments(final UUID accountId, final InternalTenantContext context) throws PaymentApiException {
        return paymentProcessor.getAccountPayments(accountId, context);
    }

    @Override
    public List<PaymentMethod> getPaymentMethods(final Account account, final InternalTenantContext context) throws PaymentApiException {
        return methodProcessor.getPaymentMethods(account, false, context);
    }
}
