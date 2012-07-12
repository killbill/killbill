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

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.payment.core.PaymentMethodProcessor;
import com.ning.billing.payment.core.PaymentProcessor;
import com.ning.billing.payment.core.RefundProcessor;
import com.ning.billing.util.callcontext.CallContext;

public class DefaultPaymentApi implements PaymentApi {


    private final PaymentMethodProcessor methodProcessor;
    private final PaymentProcessor paymentProcessor;
    private final RefundProcessor refundProcessor;

    @Inject
    public DefaultPaymentApi(final PaymentMethodProcessor methodProcessor,
                             final PaymentProcessor paymentProcessor,
                             final RefundProcessor refundProcessor) {
        this.methodProcessor = methodProcessor;
        this.paymentProcessor = paymentProcessor;
        this.refundProcessor = refundProcessor;
    }

    @Override
    public Payment createPayment(final String accountKey, final UUID invoiceId, final BigDecimal amount, final CallContext context)
            throws PaymentApiException {
        return paymentProcessor.createPayment(accountKey, invoiceId, amount, context, true);
    }

    @Override
    public Payment createPayment(final Account account, final UUID invoiceId,
                                 final BigDecimal amount, final CallContext context) throws PaymentApiException {
        return paymentProcessor.createPayment(account, invoiceId, amount, context, true);
    }

    @Override
    public Payment getPayment(final UUID paymentId) throws PaymentApiException {
        final Payment payment = paymentProcessor.getPayment(paymentId);
        if (payment == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT, paymentId);
        }
        return payment;
    }

    @Override
    public List<Payment> getInvoicePayments(final UUID invoiceId) {
        return paymentProcessor.getInvoicePayments(invoiceId);
    }

    @Override
    public List<Payment> getAccountPayments(final UUID accountId)
            throws PaymentApiException {
        return paymentProcessor.getAccountPayments(accountId);
    }



    @Override
    public Refund getRefund(UUID refundId) throws PaymentApiException {
        return refundProcessor.getRefund(refundId);
    }

    @Override
    public Refund createRefund(Account account, UUID paymentId,
            BigDecimal refundAmount, boolean isAdjusted, CallContext context)
            throws PaymentApiException {
        return refundProcessor.createRefund(account, paymentId, refundAmount, isAdjusted, context);

    }

    @Override
    public List<Refund> getAccountRefunds(Account account)
            throws PaymentApiException {
        return refundProcessor.getAccountRefunds(account);
    }

    @Override
    public List<Refund> getPaymentRefunds(UUID paymentId)
            throws PaymentApiException {
        return refundProcessor.getPaymentRefunds(paymentId);
    }


    @Override
    public Set<String> getAvailablePlugins() {
        return methodProcessor.getAvailablePlugins();
    }


    @Override
    public String initializeAccountPlugin(final String pluginName, final Account account)
            throws PaymentApiException {
        return methodProcessor.initializeAccountPlugin(pluginName, account);
    }


    @Override
    public UUID addPaymentMethod(final String pluginName, final Account account,
                                 final boolean setDefault, final PaymentMethodPlugin paymentMethodInfo, final CallContext context)
            throws PaymentApiException {
        return methodProcessor.addPaymentMethod(pluginName, account, setDefault, paymentMethodInfo, context);
    }


    @Override
    public List<PaymentMethod> refreshPaymentMethods(final String pluginName,
                                                     final Account account, final CallContext context)
            throws PaymentApiException {
        return methodProcessor.refreshPaymentMethods(pluginName, account, context);
    }

    @Override
    public List<PaymentMethod> getPaymentMethods(final Account account, final boolean withPluginDetail)
            throws PaymentApiException {
        return methodProcessor.getPaymentMethods(account, withPluginDetail);
    }

    @Override
    public PaymentMethod getPaymentMethodById(final UUID paymentMethodId)
            throws PaymentApiException {
        return methodProcessor.getPaymentMethodById(paymentMethodId);
    }

    @Override
    public PaymentMethod getPaymentMethod(final Account account, final UUID paymentMethod, final boolean withPluginDetail)
            throws PaymentApiException {
        return methodProcessor.getPaymentMethod(account, paymentMethod, withPluginDetail);
    }

    @Override
    public void updatePaymentMethod(final Account account, final UUID paymentMethodId, final PaymentMethodPlugin paymentMethodInfo)
            throws PaymentApiException {
        methodProcessor.updatePaymentMethod(account, paymentMethodId, paymentMethodInfo);
    }

    @Override
    public void deletedPaymentMethod(final Account account, final UUID paymentMethodId, final CallContext context)
            throws PaymentApiException {
        methodProcessor.deletedPaymentMethod(account, paymentMethodId);
    }

    @Override
    public void setDefaultPaymentMethod(final Account account, final UUID paymentMethodId, final CallContext context)
            throws PaymentApiException {
        methodProcessor.setDefaultPaymentMethod(account, paymentMethodId, context);
    }
}
