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

package com.ning.billing.payment.dao;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.skife.jdbi.v2.IDBI;

import com.ning.billing.payment.api.PaymentStatus;
import com.ning.billing.payment.dao.RefundModelDao.RefundStatus;
import com.ning.billing.payment.retry.PluginFailureRetryService.PluginFailureRetryServiceScheduler;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoTransactionWrapper;
import com.ning.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import com.ning.billing.util.entity.dao.EntitySqlDaoWrapperFactory;

import com.google.inject.Inject;

public class AuditedPaymentDao implements PaymentDao {

    private final EntitySqlDaoTransactionalJdbiWrapper transactionalSqlDao;
    private final PaymentSqlDao paymentSqlDao;
    private final PaymentAttemptSqlDao paymentAttemptSqlDao;
    private final PaymentMethodSqlDao paymentMethodSqlDao;
    private final RefundSqlDao refundSqlDao;

    @Inject
    public AuditedPaymentDao(final IDBI dbi, final PluginFailureRetryServiceScheduler timedoutSchduler) {
        this.paymentSqlDao = dbi.onDemand(PaymentSqlDao.class);
        this.paymentAttemptSqlDao = dbi.onDemand(PaymentAttemptSqlDao.class);
        this.paymentMethodSqlDao = dbi.onDemand(PaymentMethodSqlDao.class);
        this.refundSqlDao = dbi.onDemand(RefundSqlDao.class);
        this.transactionalSqlDao = new EntitySqlDaoTransactionalJdbiWrapper(dbi);
    }

    @Override
    public PaymentAttemptModelDao insertNewAttemptForPayment(final UUID paymentId, final PaymentAttemptModelDao attempt, final InternalCallContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<PaymentAttemptModelDao>() {
            @Override
            public PaymentAttemptModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final PaymentAttemptSqlDao transactional = entitySqlDaoWrapperFactory.become(PaymentAttemptSqlDao.class);
                transactional.insertPaymentAttempt(attempt, context);
                final PaymentAttemptModelDao savedAttempt = transactional.getPaymentAttempt(attempt.getId().toString(), context);

                entitySqlDaoWrapperFactory.become(PaymentSqlDao.class).updatePaymentAmount(paymentId.toString(), savedAttempt.getRequestedAmount(), context);

                return savedAttempt;
            }
        });
    }

    @Override
    public PaymentModelDao insertPaymentWithAttempt(final PaymentModelDao payment, final PaymentAttemptModelDao attempt, final InternalCallContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<PaymentModelDao>() {

            @Override
            public PaymentModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final PaymentSqlDao transactional = entitySqlDaoWrapperFactory.become(PaymentSqlDao.class);
                transactional.insertPayment(payment, context);

                entitySqlDaoWrapperFactory.become(PaymentAttemptSqlDao.class).insertPaymentAttempt(attempt, context);

                return transactional.getPayment(payment.getId().toString(), context);
            }
        });
    }

    @Override
    public PaymentAttemptModelDao getPaymentAttempt(final UUID attemptId, final InternalTenantContext context) {
        return paymentAttemptSqlDao.getPaymentAttempt(attemptId.toString(), context);
    }

    @Override
    public void updateStatusForPaymentWithAttempt(final UUID paymentId,
                                                  final PaymentStatus paymentStatus,
                                                  final String gatewayErrorCode,
                                                  final String gatewayErrorMsg,
                                                  final String extFirstPaymentRefId,
                                                  final String extSecondPaymentRefId,
                                                  final UUID attemptId,
                                                  final InternalCallContext context) {
        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {

            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                entitySqlDaoWrapperFactory.become(PaymentSqlDao.class).updatePaymentStatusAndExtRef(paymentId.toString(), paymentStatus.toString(), extFirstPaymentRefId, extSecondPaymentRefId, context);
                entitySqlDaoWrapperFactory.become(PaymentAttemptSqlDao.class).updatePaymentAttemptStatus(attemptId.toString(), paymentStatus.toString(), gatewayErrorCode, gatewayErrorMsg, context);
                return null;
            }
        });
    }

    @Override
    public PaymentMethodModelDao insertPaymentMethod(final PaymentMethodModelDao paymentMethod, final InternalCallContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<PaymentMethodModelDao>() {
            @Override
            public PaymentMethodModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return insertPaymentMethodInTransaction(entitySqlDaoWrapperFactory, paymentMethod, context);
            }
        });
    }

    private PaymentMethodModelDao insertPaymentMethodInTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final PaymentMethodModelDao paymentMethod, final InternalCallContext context) {
        final PaymentMethodSqlDao transactional = entitySqlDaoWrapperFactory.become(PaymentMethodSqlDao.class);
        transactional.insertPaymentMethod(paymentMethod, context);
        return transactional.getPaymentMethod(paymentMethod.getId().toString(), context);
    }

    @Override
    public List<PaymentMethodModelDao> refreshPaymentMethods(final UUID accountId, final List<PaymentMethodModelDao> paymentMethods, final InternalCallContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<PaymentMethodModelDao>>() {

            @Override
            public List<PaymentMethodModelDao> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final PaymentMethodSqlDao transactional = entitySqlDaoWrapperFactory.become(PaymentMethodSqlDao.class);
                final List<PaymentMethodModelDao> existingPaymentMethods = transactional.getPaymentMethods(accountId.toString(), context);

                final Set<String> externalPaymentIdProcessed = new HashSet<String>();
                for (final PaymentMethodModelDao finalPaymentMethod : paymentMethods) {
                    boolean isExistingPaymentMethod = false;

                    for (final PaymentMethodModelDao existingPaymentMethod : existingPaymentMethods) {
                        if (existingPaymentMethod.equals(finalPaymentMethod)) {
                            // We already have it - nothing to do
                            isExistingPaymentMethod = true;
                            break;
                        } else if (existingPaymentMethod.equalsButActive(finalPaymentMethod)) {
                            // We already have it but its status has changed - update it accordingly
                            // Note - in the remote system, the payment method will always be active
                            undeletedPaymentMethodInTransaction(entitySqlDaoWrapperFactory, existingPaymentMethod.getId(), context);
                            isExistingPaymentMethod = true;
                            break;
                        }
                        // Otherwise, we don't have it
                    }

                    if (!isExistingPaymentMethod) {
                        insertPaymentMethodInTransaction(entitySqlDaoWrapperFactory, finalPaymentMethod, context);
                    }

                    externalPaymentIdProcessed.add(finalPaymentMethod.getExternalId());
                }

                // Finally, mark as deleted the ones that don't exist in the specified list (remote system)
                for (final PaymentMethodModelDao existingPaymentMethod : existingPaymentMethods) {
                    if (!externalPaymentIdProcessed.contains(existingPaymentMethod.getExternalId())) {
                        deletedPaymentMethodInTransaction(entitySqlDaoWrapperFactory, existingPaymentMethod.getId(), context);
                    }
                }

                return transactional.getPaymentMethods(accountId.toString(), context);
            }
        });
    }

    @Override
    public RefundModelDao insertRefund(final RefundModelDao refundInfo, final InternalCallContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<RefundModelDao>() {

            @Override
            public RefundModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final RefundSqlDao transactional = entitySqlDaoWrapperFactory.become(RefundSqlDao.class);
                transactional.insertRefund(refundInfo, context);
                return transactional.getRefund(refundInfo.getId().toString(), context);
            }
        });
    }

    @Override
    public void updateRefundStatus(final UUID refundId, final RefundStatus refundStatus, final InternalCallContext context) {
        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {

            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                entitySqlDaoWrapperFactory.become(RefundSqlDao.class).updateStatus(refundId.toString(), refundStatus.toString(), context);
                return null;
            }
        });
    }

    @Override
    public RefundModelDao getRefund(final UUID refundId, final InternalTenantContext context) {
        return refundSqlDao.getRefund(refundId.toString(), context);
    }

    @Override
    public List<RefundModelDao> getRefundsForPayment(final UUID paymentId, final InternalTenantContext context) {
        return refundSqlDao.getRefundsForPayment(paymentId.toString(), context);
    }

    @Override
    public List<RefundModelDao> getRefundsForAccount(final UUID accountId, final InternalTenantContext context) {
        return refundSqlDao.getRefundsForAccount(accountId.toString(), context);
    }

    @Override
    public PaymentMethodModelDao getPaymentMethod(final UUID paymentMethodId, final InternalTenantContext context) {
        return getPaymentMethodInTransaction(paymentMethodSqlDao, paymentMethodId, context);
    }

    @Override
    public PaymentMethodModelDao getPaymentMethodIncludedDeleted(final UUID paymentMethodId, final InternalTenantContext context) {
        return paymentMethodSqlDao.getPaymentMethodIncludedDelete(paymentMethodId.toString(), context);
    }

    private PaymentMethodModelDao getPaymentMethodInTransaction(final PaymentMethodSqlDao transactional, final UUID paymentMethodId, final InternalTenantContext context) {
        return transactional.getPaymentMethod(paymentMethodId.toString(), context);
    }

    @Override
    public List<PaymentMethodModelDao> getPaymentMethods(final UUID accountId, final InternalTenantContext context) {
        return paymentMethodSqlDao.getPaymentMethods(accountId.toString(), context);
    }

    @Override
    public void deletedPaymentMethod(final UUID paymentMethodId, final InternalCallContext context) {
        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                deletedPaymentMethodInTransaction(entitySqlDaoWrapperFactory, paymentMethodId, context);
                return null;
            }
        });
    }

    private void deletedPaymentMethodInTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final UUID paymentMethodId, final InternalCallContext context) {
        entitySqlDaoWrapperFactory.become(PaymentMethodSqlDao.class).markPaymentMethodAsDeleted(paymentMethodId.toString(), context);
    }

    @Override
    public void undeletedPaymentMethod(final UUID paymentMethodId, final InternalCallContext context) {
        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                undeletedPaymentMethodInTransaction(entitySqlDaoWrapperFactory, paymentMethodId, context);
                return null;
            }
        });
    }

    private void undeletedPaymentMethodInTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final UUID paymentMethodId, final InternalCallContext context) {
        final PaymentMethodSqlDao paymentMethodSqlDao = entitySqlDaoWrapperFactory.become(PaymentMethodSqlDao.class);
        paymentMethodSqlDao.unmarkPaymentMethodAsDeleted(paymentMethodId.toString(), context);
    }

    @Override
    public List<PaymentModelDao> getPaymentsForInvoice(final UUID invoiceId, final InternalTenantContext context) {
        return paymentSqlDao.getPaymentsForInvoice(invoiceId.toString(), context);
    }

    @Override
    public PaymentModelDao getLastPaymentForPaymentMethod(final UUID accountId, final UUID paymentMethodId, final InternalTenantContext context) {
        return paymentSqlDao.getLastPaymentForAccountAndPaymentMethod(accountId.toString(), paymentMethodId.toString(), context);
    }

    @Override
    public PaymentModelDao getPayment(final UUID paymentId, final InternalTenantContext context) {
        return paymentSqlDao.getPayment(paymentId.toString(), context);
    }

    @Override
    public List<PaymentModelDao> getPaymentsForAccount(final UUID accountId, final InternalTenantContext context) {
        return paymentSqlDao.getPaymentsForAccount(accountId.toString(), context);
    }

    @Override
    public List<PaymentAttemptModelDao> getAttemptsForPayment(final UUID paymentId, final InternalTenantContext context) {
        return paymentAttemptSqlDao.getPaymentAttempts(paymentId.toString(), context);
    }
}
