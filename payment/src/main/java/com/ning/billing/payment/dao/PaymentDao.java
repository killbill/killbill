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

package com.ning.billing.payment.dao;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.ning.billing.callcontext.InternalCallContext;
import com.ning.billing.callcontext.InternalTenantContext;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.payment.api.PaymentStatus;
import com.ning.billing.payment.api.RefundStatus;
import com.ning.billing.util.entity.Pagination;

public interface PaymentDao {

    public PaymentModelDao insertPaymentWithFirstAttempt(PaymentModelDao paymentInfo, PaymentAttemptModelDao attempt, InternalCallContext context);

    public PaymentAttemptModelDao updatePaymentWithNewAttempt(UUID paymentId, PaymentAttemptModelDao attempt, InternalCallContext context);

    public void updatePaymentAndAttemptOnCompletion(UUID paymentId, PaymentStatus paymentStatus,
                                                    BigDecimal processedAmount, Currency processedCurrency,
                                                    UUID attemptId, String gatewayErrorMsg, String gatewayErrorCode, InternalCallContext context);

    public PaymentAttemptModelDao getPaymentAttempt(UUID attemptId, InternalTenantContext context);

    public List<PaymentModelDao> getPaymentsForInvoice(UUID invoiceId, InternalTenantContext context);

    public List<PaymentModelDao> getPaymentsForAccount(UUID accountId, InternalTenantContext context);

    public PaymentModelDao getLastPaymentForPaymentMethod(UUID accountId, UUID paymentMethodId, InternalTenantContext context);

    public Pagination<PaymentModelDao> getPayments(String pluginName, Long offset, Long limit, InternalTenantContext context);

    public PaymentModelDao getPayment(UUID paymentId, InternalTenantContext context);

    public List<PaymentAttemptModelDao> getAttemptsForPayment(UUID paymentId, InternalTenantContext context);

    public RefundModelDao insertRefund(RefundModelDao refundInfo, InternalCallContext context);

    public void updateRefundStatus(UUID refundId, RefundStatus status, BigDecimal processedAmount, Currency processedCurrency, InternalCallContext context);

    public Pagination<RefundModelDao> getRefunds(String pluginName, Long offset, Long limit, InternalTenantContext context);

    public RefundModelDao getRefund(UUID refundId, InternalTenantContext context);

    public List<RefundModelDao> getRefundsForPayment(UUID paymentId, InternalTenantContext context);

    public List<RefundModelDao> getRefundsForAccount(UUID accountId, InternalTenantContext context);

    public PaymentMethodModelDao insertPaymentMethod(PaymentMethodModelDao paymentMethod, InternalCallContext context);

    public PaymentMethodModelDao getPaymentMethod(UUID paymentMethodId, InternalTenantContext context);

    public PaymentMethodModelDao getPaymentMethodIncludedDeleted(UUID paymentMethodId, InternalTenantContext context);

    public List<PaymentMethodModelDao> getPaymentMethods(UUID accountId, InternalTenantContext context);

    public Pagination<PaymentMethodModelDao> getPaymentMethods(String pluginName, Long offset, Long limit, InternalTenantContext context);

    public void deletedPaymentMethod(UUID paymentMethodId, InternalCallContext context);

    public List<PaymentMethodModelDao> refreshPaymentMethods(UUID accountId, String pluginName, List<PaymentMethodModelDao> paymentMethods, InternalCallContext context);

    public void undeletedPaymentMethod(UUID paymentMethodId, InternalCallContext context);
}
