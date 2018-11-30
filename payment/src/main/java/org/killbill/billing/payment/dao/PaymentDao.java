/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

import org.joda.time.DateTime;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentAttempt;
import org.killbill.billing.payment.api.PaymentMethod;
import org.killbill.billing.payment.api.PaymentTransaction;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.audit.AuditLogWithHistory;
import org.killbill.billing.util.entity.Entity;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.EntityDao;

public interface PaymentDao extends EntityDao<PaymentModelDao, Payment, PaymentApiException> {

    public Pagination<PaymentTransactionModelDao> getByTransactionStatusAcrossTenants(final Iterable<TransactionStatus> transactionStatuses, DateTime createdBeforeDate, DateTime createdAfterDate, final Long offset, final Long limit);

    public PaymentAttemptModelDao insertPaymentAttemptWithProperties(PaymentAttemptModelDao attempt, InternalCallContext context);

    public void updatePaymentAttempt(UUID paymentAttemptId, UUID transactionId, String state, InternalCallContext context);

    public void updatePaymentAttemptWithProperties(UUID paymentAttemptId, UUID paymentMethodId, UUID transactionId, String state, final byte[] pluginProperties, InternalCallContext context);

    public Pagination<PaymentAttemptModelDao> getPaymentAttemptsByStateAcrossTenants(String stateName, DateTime createdBeforeDate, final Long offset, final Long limit);

    public List<PaymentAttemptModelDao> getPaymentAttempts(String paymentExternalKey, InternalTenantContext context);

    public List<PaymentAttemptModelDao> getPaymentAttemptByTransactionExternalKey(String externalKey, InternalTenantContext context);

    public List<PaymentTransactionModelDao> getPaymentTransactionsByExternalKey(String transactionExternalKey, InternalTenantContext context);

    public PaymentModelDao getPaymentByExternalKey(String externalKey, InternalTenantContext context);

    public Pagination<PaymentModelDao> getPayments(String pluginName, Long offset, Long limit, InternalTenantContext context);

    public Pagination<PaymentModelDao> searchPayments(String searchKey, Long offset, Long limit, InternalTenantContext context);

    public PaymentAndTransactionModelDao insertPaymentWithFirstTransaction(PaymentModelDao payment, PaymentTransactionModelDao paymentTransaction, InternalCallContext context);

    public PaymentTransactionModelDao updatePaymentWithNewTransaction(UUID paymentId, PaymentTransactionModelDao paymentTransaction, InternalCallContext context);

    public PaymentAndTransactionModelDao updatePaymentAndTransactionOnCompletion(UUID accountId, UUID attemptId, UUID paymentId, TransactionType transactionType, String currentPaymentStateName, UUID transactionId,
                                                                                 TransactionStatus paymentStatus, BigDecimal processedAmount, Currency processedCurrency,
                                                                                 String gatewayErrorCode, String gatewayErrorMsg, InternalCallContext context);

    public PaymentAndTransactionModelDao updatePaymentAndTransactionOnCompletion(UUID accountId, UUID attemptId, UUID paymentId, TransactionType transactionType, String currentPaymentStateName, String lastPaymentSuccessStateName, UUID transactionId,
                                                                                 TransactionStatus paymentStatus, BigDecimal processedAmount, Currency processedCurrency,
                                                                                 String gatewayErrorCode, String gatewayErrorMsg, InternalCallContext context);

    public PaymentModelDao getPayment(UUID paymentId, InternalTenantContext context);

    public PaymentTransactionModelDao getPaymentTransaction(UUID transactionId, InternalTenantContext context);

    public List<PaymentModelDao> getPaymentsForAccount(UUID accountId, InternalTenantContext context);

    public List<PaymentModelDao> getPaymentsByStatesAcrossTenants(String[] states, DateTime createdBeforeDate, DateTime createdAfterDate, int limit);

    public List<PaymentTransactionModelDao> getTransactionsForAccount(UUID accountId, InternalTenantContext context);

    public List<PaymentTransactionModelDao> getTransactionsForPayment(UUID paymentId, InternalTenantContext context);

    public PaymentAttemptModelDao getPaymentAttempt(UUID attemptId, InternalTenantContext context);

    public PaymentMethodModelDao insertPaymentMethod(PaymentMethodModelDao paymentMethod, InternalCallContext context);

    public PaymentMethodModelDao getPaymentMethod(UUID paymentMethodId, InternalTenantContext context);

    public PaymentMethodModelDao getPaymentMethodByExternalKey(String paymentMethodExternalKey, InternalTenantContext context);

    public PaymentMethodModelDao getPaymentMethodIncludedDeleted(UUID paymentMethodId, InternalTenantContext context);

    public PaymentMethodModelDao getPaymentMethodByExternalKeyIncludedDeleted(String paymentMethodExternalKey, InternalTenantContext context);

    public List<PaymentMethodModelDao> getPaymentMethods(InternalTenantContext context);

    public List<PaymentMethodModelDao> getPaymentMethodsIncludedDeleted(InternalTenantContext context);

    public Pagination<PaymentMethodModelDao> getPaymentMethods(String pluginName, Long offset, Long limit, InternalTenantContext context);

    public Pagination<PaymentMethodModelDao> searchPaymentMethods(String searchKey, Long offset, Long limit, InternalTenantContext context);

    public void deletedPaymentMethod(UUID paymentMethodId, InternalCallContext context);

    public List<PaymentMethodModelDao> refreshPaymentMethods(String pluginName, List<PaymentMethodModelDao> paymentMethods, InternalCallContext context);

    List<AuditLogWithHistory> getPaymentAuditLogsWithHistoryForId(UUID paymentId, AuditLevel auditLevel, InternalTenantContext context);

    List<AuditLogWithHistory> getPaymentMethodAuditLogsWithHistoryForId(UUID paymentMethodId, AuditLevel auditLevel, InternalTenantContext context);

    List<AuditLogWithHistory> getPaymentAttemptAuditLogsWithHistoryForId(UUID paymentAttemptId, AuditLevel auditLevel, InternalTenantContext context);

    List<AuditLogWithHistory> getPaymentTransactionAuditLogsWithHistoryForId(UUID paymentTransactionId, AuditLevel auditLevel, InternalTenantContext context);
}
