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

import javax.inject.Inject;

import org.skife.jdbi.v2.IDBI;

import com.ning.billing.payment.api.PaymentStatus;
import com.ning.billing.payment.dao.RefundModelDao.RefundStatus;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.dao.NonEntityDao;
import com.ning.billing.util.entity.EntityPersistenceException;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoTransactionWrapper;
import com.ning.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import com.ning.billing.util.entity.dao.EntitySqlDaoWrapperFactory;


public class DefaultPaymentDao implements PaymentDao {

    private final EntitySqlDaoTransactionalJdbiWrapper transactionalSqlDao;

    @Inject
    public DefaultPaymentDao(final IDBI dbi, final Clock clock, final CacheControllerDispatcher cacheControllerDispatcher, final NonEntityDao nonEntityDao) {
        this.transactionalSqlDao = new EntitySqlDaoTransactionalJdbiWrapper(dbi, clock, cacheControllerDispatcher, nonEntityDao);
    }

    @Override
    public PaymentAttemptModelDao insertNewAttemptForPayment(final UUID paymentId, final PaymentAttemptModelDao attempt, final InternalCallContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<PaymentAttemptModelDao>() {
            @Override
            public PaymentAttemptModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final PaymentAttemptSqlDao transactional = entitySqlDaoWrapperFactory.become(PaymentAttemptSqlDao.class);
                transactional.create(attempt, context);
                final PaymentAttemptModelDao savedAttempt = transactional.getById(attempt.getId().toString(), context);

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
                transactional.create(payment, context);


                entitySqlDaoWrapperFactory.become(PaymentAttemptSqlDao.class).create(attempt, context);

                return transactional.getById(payment.getId().toString(), context);
            }
        });
    }

    @Override
    public PaymentAttemptModelDao getPaymentAttempt(final UUID attemptId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<PaymentAttemptModelDao>() {
            @Override
            public PaymentAttemptModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(PaymentAttemptSqlDao.class).getById(attemptId.toString(), context);
            }
        });
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

    private PaymentMethodModelDao insertPaymentMethodInTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final PaymentMethodModelDao paymentMethod, final InternalCallContext context)
            throws EntityPersistenceException {
        final PaymentMethodSqlDao transactional = entitySqlDaoWrapperFactory.become(PaymentMethodSqlDao.class);
        transactional.create(paymentMethod, context);

        return transactional.getById(paymentMethod.getId().toString(), context);
    }

    @Override
    public List<PaymentMethodModelDao> refreshPaymentMethods(final UUID accountId, final List<PaymentMethodModelDao> paymentMethods, final InternalCallContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<PaymentMethodModelDao>>() {

            @Override
            public List<PaymentMethodModelDao> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final PaymentMethodSqlDao transactional = entitySqlDaoWrapperFactory.become(PaymentMethodSqlDao.class);
                final List<PaymentMethodModelDao> existingPaymentMethods = transactional.getByAccountId(accountId.toString(), context);

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
                return transactional.getByAccountId(accountId.toString(), context);
            }
        });
    }

    @Override
    public RefundModelDao insertRefund(final RefundModelDao refundInfo, final InternalCallContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<RefundModelDao>() {

            @Override
            public RefundModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final RefundSqlDao transactional = entitySqlDaoWrapperFactory.become(RefundSqlDao.class);
                transactional.create(refundInfo, context);
                return transactional.getById(refundInfo.getId().toString(), context);
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
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<RefundModelDao>() {
            @Override
            public RefundModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(RefundSqlDao.class).getById(refundId.toString(), context);
            }
        });
    }

    @Override
    public List<RefundModelDao> getRefundsForPayment(final UUID paymentId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<RefundModelDao>>() {
            @Override
            public List<RefundModelDao> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(RefundSqlDao.class).getRefundsForPayment(paymentId.toString(), context);
            }
        });
    }

    @Override
    public List<RefundModelDao> getRefundsForAccount(final UUID accountId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<RefundModelDao>>() {
            @Override
            public List<RefundModelDao> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(RefundSqlDao.class).getRefundsForAccount(accountId.toString(), context);
            }
        });
    }

    @Override
    public PaymentMethodModelDao getPaymentMethod(final UUID paymentMethodId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<PaymentMethodModelDao>() {
            @Override
            public PaymentMethodModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(PaymentMethodSqlDao.class).getById(paymentMethodId.toString(), context);
            }
        });
    }

    @Override
    public PaymentMethodModelDao getPaymentMethodIncludedDeleted(final UUID paymentMethodId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<PaymentMethodModelDao>() {
            @Override
            public PaymentMethodModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(PaymentMethodSqlDao.class).getPaymentMethodIncludedDelete(paymentMethodId.toString(), context);
            }
        });
    }

    @Override
    public List<PaymentMethodModelDao> getPaymentMethods(final UUID accountId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<PaymentMethodModelDao>>() {
            @Override
            public List<PaymentMethodModelDao> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(PaymentMethodSqlDao.class).getByAccountId(accountId.toString(), context);
            }
        });
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
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<PaymentModelDao>>() {
            @Override
            public List<PaymentModelDao> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(PaymentSqlDao.class).getPaymentsForInvoice(invoiceId.toString(), context);
            }
        });
    }

    @Override
    public PaymentModelDao getLastPaymentForPaymentMethod(final UUID accountId, final UUID paymentMethodId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<PaymentModelDao>() {
            @Override
            public PaymentModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(PaymentSqlDao.class).getLastPaymentForAccountAndPaymentMethod(accountId.toString(), paymentMethodId.toString(), context);
            }
        });
    }

    @Override
    public PaymentModelDao getPayment(final UUID paymentId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<PaymentModelDao>() {
            @Override
            public PaymentModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(PaymentSqlDao.class).getById(paymentId.toString(), context);
            }
        });
    }

    @Override
    public List<PaymentModelDao> getPaymentsForAccount(final UUID accountId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<PaymentModelDao>>() {
            @Override
            public List<PaymentModelDao> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(PaymentSqlDao.class).getPaymentsForAccount(accountId.toString(), context);
            }
        });
    }

    @Override
    public List<PaymentAttemptModelDao> getAttemptsForPayment(final UUID paymentId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<PaymentAttemptModelDao>>() {
            @Override
            public List<PaymentAttemptModelDao> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(PaymentAttemptSqlDao.class).getByPaymentId(paymentId.toString(), context);
            }
        });
    }
}
