/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.joda.time.DateTime;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.dao.MockNonEntityDao;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.audit.AuditLogWithHistory;
import org.killbill.billing.util.entity.DefaultPagination;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.MockEntityDaoBase;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class MockPaymentDao extends MockEntityDaoBase<PaymentModelDao, Payment, PaymentApiException> implements PaymentDao {

    private final Map<UUID, PaymentModelDao> payments = new HashMap<UUID, PaymentModelDao>();
    private final Map<UUID, PaymentTransactionModelDao> transactions = new HashMap<UUID, PaymentTransactionModelDao>();
    private final Map<UUID, PaymentAttemptModelDao> attempts = new HashMap<UUID, PaymentAttemptModelDao>();

    private final MockNonEntityDao mockNonEntityDao;
    private final List<PaymentMethodModelDao> paymentMethods = new LinkedList<PaymentMethodModelDao>();

    @Inject
    public MockPaymentDao(final MockNonEntityDao mockNonEntityDao) {
        this.mockNonEntityDao = mockNonEntityDao;
    }

    public void reset() {
        synchronized (this) {
            payments.clear();
            paymentMethods.clear();
            transactions.clear();
            attempts.clear();
        }
    }

    @Override
    public Pagination<PaymentTransactionModelDao> getByTransactionStatusAcrossTenants(final Iterable<TransactionStatus> transactionStatuses, DateTime createdBeforeDate, DateTime createdAfterDate, Long offset, Long limit) {
        final List<PaymentTransactionModelDao> result = ImmutableList.copyOf(Iterables.filter(transactions.values(), new Predicate<PaymentTransactionModelDao>() {
            @Override
            public boolean apply(final PaymentTransactionModelDao input) {
                return Iterables.any(transactionStatuses, new Predicate<TransactionStatus>() {
                    @Override
                    public boolean apply(final TransactionStatus transactionStatus) {
                        return input.getTransactionStatus() == transactionStatus;
                    }
                });
            }
        }));
        return new DefaultPagination<PaymentTransactionModelDao>(new Long(result.size()), result.iterator());
    }

    @Override
    public PaymentAttemptModelDao insertPaymentAttemptWithProperties(final PaymentAttemptModelDao attempt, final InternalCallContext context) {
        attempt.setTenantRecordId(context.getTenantRecordId());
        attempt.setAccountRecordId(context.getAccountRecordId());

        synchronized (this) {
            attempts.put(attempt.getId(), attempt);
            mockNonEntityDao.addTenantRecordIdMapping(attempt.getId(), context);
            mockNonEntityDao.addAccountRecordIdMapping(attempt.getId(), context);
            return attempt;
        }
    }

    @Override
    public void updatePaymentAttempt(final UUID paymentAttemptId, final UUID transactionId, final String state, final InternalCallContext context) {
        updatePaymentAttemptWithProperties(paymentAttemptId, null, transactionId, state, null, context);
    }

    @Override
    public void updatePaymentAttemptWithProperties(final UUID paymentAttemptId, @Nullable final UUID paymentMethodId, final UUID transactionId, final String state, final byte[] pluginProperties, final InternalCallContext context) {
        boolean success = false;
        synchronized (this) {
            for (PaymentAttemptModelDao cur : attempts.values()) {
                if (cur.getId().equals(paymentAttemptId)) {
                    if (paymentMethodId != null) {
                        cur.setPaymentMethodId(paymentMethodId);
                    }
                    cur.setStateName(state);
                    cur.setTransactionId(transactionId);
                    if (pluginProperties != null) {
                        cur.setPluginProperties(pluginProperties);
                    }
                    success = true;
                    break;
                }
            }
        }
        if (!success) {
            throw new RuntimeException("Could not find attempt " + paymentAttemptId);
        }
    }

    @Override
    public Pagination<PaymentAttemptModelDao> getPaymentAttemptsByStateAcrossTenants(final String stateName, final DateTime createdBeforeDate, final Long offset, final Long limit) {
        return null;
    }

    @Override
    public List<PaymentAttemptModelDao> getPaymentAttempts(final String paymentExternalKey, final InternalTenantContext context) {
        synchronized (this) {
            final List<PaymentAttemptModelDao> result = new ArrayList<PaymentAttemptModelDao>();
            for (PaymentAttemptModelDao cur : attempts.values()) {
                if (cur.getPaymentExternalKey().equals(paymentExternalKey)) {
                    result.add(cur);
                }
            }
            return result;
        }
    }

    @Override
    public List<PaymentAttemptModelDao> getPaymentAttemptByTransactionExternalKey(final String transactionExternalKey, final InternalTenantContext context) {
        synchronized (this) {
            final List<PaymentAttemptModelDao> result = new ArrayList<PaymentAttemptModelDao>();
            for (PaymentAttemptModelDao cur : attempts.values()) {
                if (cur.getTransactionExternalKey().equals(transactionExternalKey)) {
                    result.add(cur);
                }
            }
            return result;
        }
    }

    @Override
    public List<PaymentTransactionModelDao> getPaymentTransactionsByExternalKey(final String transactionExternalKey, final InternalTenantContext context) {
        final List<PaymentTransactionModelDao> result = new ArrayList<PaymentTransactionModelDao>();
        synchronized (this) {
            for (PaymentTransactionModelDao cur : transactions.values()) {
                if (cur.getTransactionExternalKey().equals(transactionExternalKey)) {
                    result.add(cur);
                }
            }
        }
        return result;
    }

    @Override
    public PaymentModelDao getPaymentByExternalKey(final String externalKey, final InternalTenantContext context) {
        synchronized (this) {
            for (PaymentModelDao cur : payments.values()) {
                if (cur.getExternalKey().equals(externalKey)) {
                    return cur;
                }
            }
        }
        return null;
    }

    @Override
    public Pagination<PaymentModelDao> getPayments(final String pluginName, final Long offset, final Long limit, final InternalTenantContext context) {
        return null;
    }

    @Override
    public Pagination<PaymentModelDao> searchPayments(final String searchKey, final Long offset, final Long limit, final InternalTenantContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PaymentAndTransactionModelDao insertPaymentWithFirstTransaction(final PaymentModelDao payment, final PaymentTransactionModelDao paymentTransaction, final InternalCallContext context) {
        final PaymentAndTransactionModelDao paymentAndTransactionModelDao = new PaymentAndTransactionModelDao();

        payment.setTenantRecordId(context.getTenantRecordId());
        paymentTransaction.setTenantRecordId(context.getTenantRecordId());
        payment.setAccountRecordId(context.getAccountRecordId());
        paymentTransaction.setAccountRecordId(context.getAccountRecordId());

        synchronized (this) {
            payments.put(payment.getId(), payment);
            mockNonEntityDao.addTenantRecordIdMapping(payment.getId(), context);
            mockNonEntityDao.addAccountRecordIdMapping((payment.getId()), context);

            transactions.put(paymentTransaction.getId(), paymentTransaction);
            mockNonEntityDao.addTenantRecordIdMapping(paymentTransaction.getId(), context);
            mockNonEntityDao.addAccountRecordIdMapping((paymentTransaction.getId()), context);
        }

        paymentAndTransactionModelDao.setPaymentModelDao(payment);
        paymentAndTransactionModelDao.setPaymentTransactionModelDao(paymentTransaction);

        return paymentAndTransactionModelDao;
    }

    @Override
    public PaymentTransactionModelDao updatePaymentWithNewTransaction(final UUID paymentId, final PaymentTransactionModelDao paymentTransaction, final InternalCallContext context) {
        paymentTransaction.setTenantRecordId(context.getTenantRecordId());
        paymentTransaction.setAccountRecordId(context.getAccountRecordId());

        synchronized (this) {
            transactions.put(paymentTransaction.getId(), paymentTransaction);
            mockNonEntityDao.addTenantRecordIdMapping(paymentId, context);
            mockNonEntityDao.addAccountRecordIdMapping((paymentTransaction.getId()), context);
        }
        return paymentTransaction;
    }

    @Override
    public PaymentAndTransactionModelDao updatePaymentAndTransactionOnCompletion(final UUID accountId, final UUID attemptId, final UUID paymentId, final TransactionType transactionType,
                                                                                 final String currentPaymentStateName, final UUID transactionId,
                                                                                 final TransactionStatus paymentStatus, final BigDecimal processedAmount, final Currency processedCurrency,
                                                                                 final String gatewayErrorCode, final String gatewayErrorMsg, final InternalCallContext context) {
        return updatePaymentAndTransactionOnCompletion(accountId, attemptId, paymentId, transactionType,
                                                       currentPaymentStateName, null, transactionId,
                                                       paymentStatus, processedAmount, processedCurrency,
                                                       gatewayErrorCode, gatewayErrorMsg, context);
    }

    @Override
    public PaymentAndTransactionModelDao updatePaymentAndTransactionOnCompletion(final UUID accountId, final UUID attemptId, final UUID paymentId, final TransactionType transactionType,
                                                                                 final String currentPaymentStateName, final String lastSuccessPaymentStateName, final UUID transactionId,
                                                                                 final TransactionStatus paymentStatus, final BigDecimal processedAmount, final Currency processedCurrency,
                                                                                 final String gatewayErrorCode, final String gatewayErrorMsg, final InternalCallContext context) {
        final PaymentAndTransactionModelDao paymentAndTransactionModelDao = new PaymentAndTransactionModelDao();

        synchronized (this) {
            final PaymentModelDao payment = payments.get(paymentId);
            if (payment != null) {
                payment.setStateName(currentPaymentStateName);
            }
            final PaymentTransactionModelDao transaction = transactions.get(transactionId);
            if (transaction != null) {
                transaction.setAttemptId(attemptId);
                transaction.setTransactionStatus(paymentStatus);
                transaction.setProcessedAmount(processedAmount);
                transaction.setProcessedCurrency(processedCurrency);
                transaction.setGatewayErrorCode(gatewayErrorCode);
                transaction.setGatewayErrorMsg(gatewayErrorMsg);
            }

            paymentAndTransactionModelDao.setPaymentModelDao(payment);
            paymentAndTransactionModelDao.setPaymentTransactionModelDao(transaction);

            return paymentAndTransactionModelDao;
        }
    }

    @Override
    public PaymentModelDao getPayment(final UUID paymentId, final InternalTenantContext context) {
        synchronized (this) {
            return payments.get(paymentId);
        }
    }

    @Override
    public PaymentTransactionModelDao getPaymentTransaction(final UUID transactionId, final InternalTenantContext context) {
        synchronized (this) {
            return transactions.get(transactionId);
        }
    }

    @Override
    public List<PaymentModelDao> getPaymentsForAccount(final UUID accountId, final InternalTenantContext context) {
        synchronized (this) {
            return ImmutableList.copyOf(Iterables.filter(payments.values(), new Predicate<PaymentModelDao>() {
                @Override
                public boolean apply(final PaymentModelDao input) {
                    return input.getAccountId().equals(accountId);
                }
            }));
        }
    }

    @Override
    public List<PaymentModelDao> getPaymentsByStatesAcrossTenants(final String[] states, final DateTime createdBeforeDate, final DateTime createdAfterDate, final int limit) {
        return null;
    }

    @Override
    public List<PaymentTransactionModelDao> getTransactionsForAccount(final UUID accountId, final InternalTenantContext context) {
        synchronized (this) {
            return ImmutableList.copyOf(Iterables.filter(transactions.values(), new Predicate<PaymentTransactionModelDao>() {
                @Override
                public boolean apply(final PaymentTransactionModelDao input) {
                    final PaymentModelDao payment = payments.get(input.getPaymentId());
                    if (payment != null) {
                        return payment.getAccountId().equals(accountId);
                    } else {
                        return false;
                    }
                }
            }));
        }
    }

    @Override
    public List<PaymentTransactionModelDao> getTransactionsForPayment(final UUID paymentId, final InternalTenantContext context) {
        synchronized (this) {
            return ImmutableList.copyOf(Iterables.filter(transactions.values(), new Predicate<PaymentTransactionModelDao>() {
                @Override
                public boolean apply(final PaymentTransactionModelDao input) {
                    return input.getPaymentId().equals(paymentId);
                }
            }));
        }
    }

    @Override
    public PaymentAttemptModelDao getPaymentAttempt(final UUID attemptId, final InternalTenantContext context) {
        synchronized (this) {
            return Iterables.tryFind(attempts.values(), new Predicate<PaymentAttemptModelDao>() {
                @Override
                public boolean apply(final PaymentAttemptModelDao input) {
                    return input.getId().equals(attemptId);
                }
            }).orNull();
        }
    }

    @Override
    public PaymentMethodModelDao insertPaymentMethod(final PaymentMethodModelDao paymentMethod, final InternalCallContext context) {
        synchronized (this) {
            paymentMethod.setAccountRecordId(context.getAccountRecordId());
            paymentMethod.setTenantRecordId(context.getTenantRecordId());
            paymentMethods.add(paymentMethod);
            return paymentMethod;
        }
    }

    @Override
    public PaymentMethodModelDao getPaymentMethod(final UUID paymentMethodId, final InternalTenantContext context) {
        synchronized (this) {
            for (final PaymentMethodModelDao cur : paymentMethods) {
                if (cur.getId().equals(paymentMethodId)) {
                    return cur;
                }
            }
            return null;
        }
    }

    @Override
    public PaymentMethodModelDao getPaymentMethodByExternalKey(final String paymentMethodExternalKey, final InternalTenantContext context) {
        synchronized (this) {
            for (final PaymentMethodModelDao cur : paymentMethods) {
                if (cur.getExternalKey().equals(paymentMethodExternalKey)) {
                    return cur;
                }
            }
            return null;
        }
    }

    @Override
    public List<PaymentMethodModelDao> getPaymentMethods(final InternalTenantContext context) {
        synchronized (this) {
            final List<PaymentMethodModelDao> result = new ArrayList<PaymentMethodModelDao>();
            for (final PaymentMethodModelDao cur : paymentMethods) {
                if (cur.getAccountRecordId().equals(context.getAccountRecordId())) {
                    result.add(cur);
                }
            }
            return result;
        }
    }

    @Override
    public List<PaymentMethodModelDao> getPaymentMethodsIncludedDeleted(final InternalTenantContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Pagination<PaymentMethodModelDao> getPaymentMethods(final String pluginName, final Long offset, final Long limit, final InternalTenantContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Pagination<PaymentMethodModelDao> searchPaymentMethods(final String searchKey, final Long offset, final Long limit, final InternalTenantContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deletedPaymentMethod(final UUID paymentMethodId, final InternalCallContext context) {
        synchronized (this) {
            final Iterator<PaymentMethodModelDao> it = paymentMethods.iterator();
            while (it.hasNext()) {
                final PaymentMethodModelDao cur = it.next();
                if (cur.getId().equals(paymentMethodId)) {
                    it.remove();
                    break;
                }
            }
        }
    }

    @Override
    public List<PaymentMethodModelDao> refreshPaymentMethods(final String pluginName, final List<PaymentMethodModelDao> paymentMethods, final InternalCallContext context) {
        return ImmutableList.<PaymentMethodModelDao>of();
    }

    @Override
    public List<AuditLogWithHistory> getPaymentAuditLogsWithHistoryForId(final UUID paymentId, final AuditLevel auditLevel, final InternalTenantContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<AuditLogWithHistory> getPaymentMethodAuditLogsWithHistoryForId(final UUID paymentMethodId, final AuditLevel auditLevel, final InternalTenantContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<AuditLogWithHistory> getPaymentAttemptAuditLogsWithHistoryForId(final UUID paymentAttemptId, final AuditLevel auditLevel, final InternalTenantContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<AuditLogWithHistory> getPaymentTransactionAuditLogsWithHistoryForId(final UUID paymentTransactionId, final AuditLevel auditLevel, final InternalTenantContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PaymentMethodModelDao getPaymentMethodIncludedDeleted(final UUID paymentMethodId, final InternalTenantContext context) {
        return getPaymentMethod(paymentMethodId, context);
    }

    @Override
    public PaymentMethodModelDao getPaymentMethodByExternalKeyIncludedDeleted(final String paymentMethodExternalKey, final InternalTenantContext context) {
        return getPaymentMethodByExternalKey(paymentMethodExternalKey, context);
    }
}
