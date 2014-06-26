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

package org.killbill.billing.payment.dao;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.util.entity.Pagination;

public interface PaymentDao {


    public List<PluginPropertyModelDao> getProperties(UUID attenptId, InternalCallContext context);

    public PaymentAttemptModelDao insertPaymentAttemptWithProperties(PaymentAttemptModelDao attempt, List<PluginPropertyModelDao> properties, InternalCallContext context);

    public void updatePaymentAttempt(UUID paymentAttemptId, UUID transactionId, String state, InternalCallContext context);

    public List<PaymentAttemptModelDao> getPaymentAttempts(String paymentExternalKey, InternalTenantContext context);

    public List<PaymentAttemptModelDao> getPaymentAttemptByTransactionExternalKey(String externalKey, InternalTenantContext context);

    public List<PaymentTransactionModelDao> getDirectPaymentTransactionsByExternalKey(String transactionExternalKey, InternalTenantContext context);

    public PaymentModelDao getDirectPaymentByExternalKey(String externalKey, InternalTenantContext context);

    public Pagination<PaymentModelDao> getDirectPayments(String pluginName, Long offset, Long limit, InternalTenantContext context);

    public PaymentModelDao insertDirectPaymentWithFirstTransaction(PaymentModelDao directPayment, PaymentTransactionModelDao directPaymentTransaction, InternalCallContext context);

    public PaymentTransactionModelDao updateDirectPaymentWithNewTransaction(UUID directPaymentId, PaymentTransactionModelDao directPaymentTransaction, InternalCallContext context);

    public void updateDirectPaymentAndTransactionOnCompletion(UUID directPaymentId, String currentPaymentStateName,
                                                              UUID directTransactionId, TransactionStatus paymentStatus,
                                                              BigDecimal processedAmount, Currency processedCurrency,
                                                              String gatewayErrorCode, String gatewayErrorMsg,
                                                              InternalCallContext context);

    public PaymentModelDao getDirectPayment(UUID directPaymentId, InternalTenantContext context);

    public PaymentTransactionModelDao getDirectPaymentTransaction(UUID directTransactionId, InternalTenantContext context);

    public List<PaymentModelDao> getDirectPaymentsForAccount(UUID accountId, InternalTenantContext context);

    public List<PaymentTransactionModelDao> getDirectTransactionsForAccount(UUID accountId, InternalTenantContext context);

    public List<PaymentTransactionModelDao> getDirectTransactionsForDirectPayment(UUID directPaymentId, InternalTenantContext context);

    public PaymentAttemptModelDao getPaymentAttempt(UUID attemptId, InternalTenantContext context);

    public PaymentMethodModelDao insertPaymentMethod(PaymentMethodModelDao paymentMethod, InternalCallContext context);

    public PaymentMethodModelDao getPaymentMethod(UUID paymentMethodId, InternalTenantContext context);

    public PaymentMethodModelDao getPaymentMethodIncludedDeleted(UUID paymentMethodId, InternalTenantContext context);

    public List<PaymentMethodModelDao> getPaymentMethods(UUID accountId, InternalTenantContext context);

    public Pagination<PaymentMethodModelDao> getPaymentMethods(String pluginName, Long offset, Long limit, InternalTenantContext context);

    public void deletedPaymentMethod(UUID paymentMethodId, InternalCallContext context);

    public List<PaymentMethodModelDao> refreshPaymentMethods(UUID accountId, String pluginName, List<PaymentMethodModelDao> paymentMethods, InternalCallContext context);
}
