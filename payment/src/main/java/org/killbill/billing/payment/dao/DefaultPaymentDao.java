/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.entity.EntityPersistenceException;
import org.killbill.billing.payment.api.DirectPayment;
import org.killbill.billing.payment.api.PaymentMethod;
import org.killbill.billing.payment.api.PaymentStatus;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.DefaultPaginationSqlDaoHelper;
import org.killbill.billing.util.entity.dao.DefaultPaginationSqlDaoHelper.PaginationIteratorBuilder;
import org.killbill.billing.util.entity.dao.EntitySqlDao;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import org.killbill.clock.Clock;
import org.skife.jdbi.v2.IDBI;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;

public class DefaultPaymentDao implements PaymentDao {

    private final EntitySqlDaoTransactionalJdbiWrapper transactionalSqlDao;
    private final DefaultPaginationSqlDaoHelper paginationHelper;

    @Inject
    public DefaultPaymentDao(final IDBI dbi, final Clock clock, final CacheControllerDispatcher cacheControllerDispatcher, final NonEntityDao nonEntityDao) {
        this.transactionalSqlDao = new EntitySqlDaoTransactionalJdbiWrapper(dbi, clock, cacheControllerDispatcher, nonEntityDao);
        this.paginationHelper = new DefaultPaginationSqlDaoHelper(transactionalSqlDao);
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
    public List<PluginPropertyModelDao> getProperties(final String transactionExternalKey, final InternalCallContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<PluginPropertyModelDao>>() {
            @Override
            public List<PluginPropertyModelDao> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final PaymentAttemptSqlDao transactional = entitySqlDaoWrapperFactory.become(PaymentAttemptSqlDao.class);
                return transactional.become(PluginPropertySqlDao.class).getPluginProperties(transactionExternalKey);
            }
        });
    }

    @Override
    public PaymentAttemptModelDao insertPaymentAttemptWithProperties(final PaymentAttemptModelDao attempt, final List<PluginPropertyModelDao> properties, final InternalCallContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<PaymentAttemptModelDao>() {

            @Override
            public PaymentAttemptModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final PaymentAttemptSqlDao transactional = entitySqlDaoWrapperFactory.become(PaymentAttemptSqlDao.class);
                transactional.create(attempt, context);
                final PaymentAttemptModelDao result = transactional.getById(attempt.getId().toString(), context);

                // Those calls are not part of history and audit on purpose, this is just to implement a temporary property cache cache
                transactional.become(PluginPropertySqlDao.class).batchCreateFromTransaction(properties);

                return result;
            }
        });
    }

    @Override
    public void updatePaymentAttempt(final UUID paymentAttemptId, @Nullable final UUID transactionId, final String state, final InternalCallContext context) {
        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {

            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final String transactionIdStr = transactionId != null ? transactionId.toString() : null;
                final PaymentAttemptSqlDao transactional = entitySqlDaoWrapperFactory.become(PaymentAttemptSqlDao.class);
                transactional.updateAttempt(paymentAttemptId.toString(), transactionIdStr, state, context);
                return null;
            }
        });
    }

    @Override
    public List<PaymentAttemptModelDao> getPaymentAttempts(final String paymentExternalKey, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<PaymentAttemptModelDao>>() {

            @Override
            public List<PaymentAttemptModelDao> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final PaymentAttemptSqlDao transactional = entitySqlDaoWrapperFactory.become(PaymentAttemptSqlDao.class);
                return transactional.getByPaymentExternalKey(paymentExternalKey, context);
            }
        });
    }

    @Override
    public PaymentAttemptModelDao getPaymentAttemptByExternalKey(final String externalKey, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<PaymentAttemptModelDao>() {

            @Override
            public PaymentAttemptModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final PaymentAttemptSqlDao transactional = entitySqlDaoWrapperFactory.become(PaymentAttemptSqlDao.class);
                return transactional.getByTransactionExternalKey(externalKey, context);
            }
        });
    }

    @Override
    public DirectPaymentTransactionModelDao getDirectPaymentTransactionByExternalKey(final String transactionExternalKey, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<DirectPaymentTransactionModelDao>() {
            @Override
            public DirectPaymentTransactionModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(DirectTransactionSqlDao.class).getDirectPaymentTransactionByExternalKey(transactionExternalKey, context);
            }
        });
    }

    @Override
    public DirectPaymentModelDao getDirectPaymentByExternalKey(final String paymentExternalKey, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<DirectPaymentModelDao>() {
            @Override
            public DirectPaymentModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(DirectPaymentSqlDao.class).getDirectPaymentByExternalKey(paymentExternalKey, context);
            }
        });
    }

    @Override
    public Pagination<DirectPaymentModelDao> getDirectPayments(final String pluginName, final Long offset, final Long limit, final InternalTenantContext context) {
        return paginationHelper.getPagination(DirectPaymentSqlDao.class,
                                              new PaginationIteratorBuilder<DirectPaymentModelDao, DirectPayment, DirectPaymentSqlDao>() {
                                                  @Override
                                                  public Long getCount(final DirectPaymentSqlDao directPaymentSqlDao, final InternalTenantContext context) {
                                                      return directPaymentSqlDao.getCountByPluginName(pluginName, context);
                                                  }

                                                  @Override
                                                  public Iterator<DirectPaymentModelDao> build(final DirectPaymentSqlDao directPaymentSqlDao, final Long limit, final InternalTenantContext context) {
                                                      return directPaymentSqlDao.getByPluginName(pluginName, offset, limit, context);
                                                  }
                                              },
                                              offset,
                                              limit,
                                              context
                                             );
    }

    @Override
    public DirectPaymentModelDao insertDirectPaymentWithFirstTransaction(final DirectPaymentModelDao directPayment, final DirectPaymentTransactionModelDao directPaymentTransaction, final InternalCallContext context) {

        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<DirectPaymentModelDao>() {

            @Override
            public DirectPaymentModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final DirectPaymentSqlDao directPaymentSqlDao = entitySqlDaoWrapperFactory.become(DirectPaymentSqlDao.class);
                directPaymentSqlDao.create(directPayment, context);
                entitySqlDaoWrapperFactory.become(DirectTransactionSqlDao.class).create(directPaymentTransaction, context);
                return directPaymentSqlDao.getById(directPayment.getId().toString(), context);
            }
        });
    }

    @Override
    public DirectPaymentTransactionModelDao updateDirectPaymentWithNewTransaction(final UUID directPaymentId, final DirectPaymentTransactionModelDao directPaymentTransaction, final InternalCallContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<DirectPaymentTransactionModelDao>() {
            @Override
            public DirectPaymentTransactionModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final DirectTransactionSqlDao transactional = entitySqlDaoWrapperFactory.become(DirectTransactionSqlDao.class);
                transactional.create(directPaymentTransaction, context);
                final DirectPaymentTransactionModelDao directPaymentTransactionModelDao = transactional.getById(directPaymentTransaction.getId().toString(), context);

                entitySqlDaoWrapperFactory.become(DirectPaymentSqlDao.class).updateDirectPaymentForNewTransaction(directPaymentId.toString(), context);

                return directPaymentTransactionModelDao;
            }
        });
    }

    @Override
    public void updateDirectPaymentAndTransactionOnCompletion(final UUID directPaymentId, final String currentPaymentStateName,
                                                              final UUID directTransactionId, final PaymentStatus paymentStatus,
                                                              final BigDecimal processedAmount, final Currency processedCurrency,
                                                              final String gatewayErrorCode, final String gatewayErrorMsg,
                                                              final InternalCallContext context) {
        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {

            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                entitySqlDaoWrapperFactory.become(DirectTransactionSqlDao.class).updateTransactionStatus(directTransactionId.toString(),
                                                                                                         processedAmount, processedCurrency == null ? null : processedCurrency.toString(),
                                                                                                         paymentStatus == null ? null : paymentStatus.toString(),
                                                                                                         gatewayErrorCode, gatewayErrorMsg, context);
                entitySqlDaoWrapperFactory.become(DirectPaymentSqlDao.class).updateCurrentPaymentStateName(directPaymentId.toString(), currentPaymentStateName, context);
                return null;
            }
        });

    }

    @Override
    public DirectPaymentModelDao getDirectPayment(final UUID directPaymentId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<DirectPaymentModelDao>() {
            @Override
            public DirectPaymentModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(DirectPaymentSqlDao.class).getById(directPaymentId.toString(), context);
            }
        });
    }

    @Override
    public DirectPaymentTransactionModelDao getDirectPaymentTransaction(final UUID directTransactionId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<DirectPaymentTransactionModelDao>() {
            @Override
            public DirectPaymentTransactionModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(DirectTransactionSqlDao.class).getById(directTransactionId.toString(), context);
            }
        });
    }

    @Override
    public List<DirectPaymentModelDao> getDirectPaymentsForAccount(final UUID accountId, final InternalTenantContext context) {
        Preconditions.checkArgument(context.getAccountRecordId() != null);
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<DirectPaymentModelDao>>() {
            @Override
            public List<DirectPaymentModelDao> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(DirectPaymentSqlDao.class).getByAccountRecordId(context);
            }
        });
    }

    @Override
    public List<DirectPaymentTransactionModelDao> getDirectTransactionsForAccount(final UUID accountId, final InternalTenantContext context) {
        Preconditions.checkArgument(context.getAccountRecordId() != null);
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<DirectPaymentTransactionModelDao>>() {
            @Override
            public List<DirectPaymentTransactionModelDao> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(DirectTransactionSqlDao.class).getByAccountRecordId(context);
            }
        });
    }

    @Override
    public List<DirectPaymentTransactionModelDao> getDirectTransactionsForDirectPayment(final UUID directPaymentId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<DirectPaymentTransactionModelDao>>() {
            @Override
            public List<DirectPaymentTransactionModelDao> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(DirectTransactionSqlDao.class).getByDirectPaymentId(directPaymentId, context);
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
    public Pagination<PaymentMethodModelDao> getPaymentMethods(final String pluginName, final Long offset, final Long limit, final InternalTenantContext context) {
        return paginationHelper.getPagination(PaymentMethodSqlDao.class,
                                              new PaginationIteratorBuilder<PaymentMethodModelDao, PaymentMethod, PaymentMethodSqlDao>() {
                                                  @Override
                                                  public Long getCount(final PaymentMethodSqlDao paymentMethodSqlDao, final InternalTenantContext context) {
                                                      return paymentMethodSqlDao.getCountByPluginName(pluginName, context);
                                                  }

                                                  @Override
                                                  public Iterator<PaymentMethodModelDao> build(final PaymentMethodSqlDao paymentMethodSqlDao, final Long limit, final InternalTenantContext context) {
                                                      return paymentMethodSqlDao.getByPluginName(pluginName, offset, limit, context);
                                                  }
                                              },
                                              offset,
                                              limit,
                                              context
                                             );
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

    private void undeletedPaymentMethodInTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final UUID paymentMethodId, final InternalCallContext context) {
        final PaymentMethodSqlDao paymentMethodSqlDao = entitySqlDaoWrapperFactory.become(PaymentMethodSqlDao.class);
        paymentMethodSqlDao.unmarkPaymentMethodAsDeleted(paymentMethodId.toString(), context);
    }

    @Override
    public List<PaymentMethodModelDao> refreshPaymentMethods(final UUID accountId, final String pluginName,
                                                             final List<PaymentMethodModelDao> newPaymentMethods, final InternalCallContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<PaymentMethodModelDao>>() {

            @Override
            public List<PaymentMethodModelDao> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final PaymentMethodSqlDao transactional = entitySqlDaoWrapperFactory.become(PaymentMethodSqlDao.class);
                // Look at all payment methods, including deleted ones. We assume that newPaymentMethods (payment methods returned by the plugin)
                // is the full set of non-deleted payment methods in the plugin. If a payment method was marked as deleted on our side,
                // but is still existing in the plugin, we will un-delete it.
                final List<PaymentMethodModelDao> allPaymentMethodsForAccount = transactional.getByAccountIdIncludedDelete(accountId.toString(), context);

                // Consider only the payment methods for the plugin we are refreshing
                final Collection<PaymentMethodModelDao> existingPaymentMethods = Collections2.filter(allPaymentMethodsForAccount,
                                                                                                     new Predicate<PaymentMethodModelDao>() {
                                                                                                         @Override
                                                                                                         public boolean apply(final PaymentMethodModelDao paymentMethod) {
                                                                                                             return pluginName.equals(paymentMethod.getPluginName());
                                                                                                         }
                                                                                                     }
                                                                                                    );

                for (final PaymentMethodModelDao finalPaymentMethod : newPaymentMethods) {
                    PaymentMethodModelDao foundExistingPaymentMethod = null;
                    for (final PaymentMethodModelDao existingPaymentMethod : existingPaymentMethods) {
                        if (existingPaymentMethod.equals(finalPaymentMethod)) {
                            // We already have it - nothing to do
                            foundExistingPaymentMethod = existingPaymentMethod;
                            break;
                        } else if (existingPaymentMethod.equalsButActive(finalPaymentMethod)) {
                            // We already have it but its status has changed - update it accordingly
                            undeletedPaymentMethodInTransaction(entitySqlDaoWrapperFactory, existingPaymentMethod.getId(), context);
                            foundExistingPaymentMethod = existingPaymentMethod;
                            break;
                        }
                        // Otherwise, we don't have it
                    }

                    if (foundExistingPaymentMethod == null) {
                        insertPaymentMethodInTransaction(entitySqlDaoWrapperFactory, finalPaymentMethod, context);
                    } else {
                        existingPaymentMethods.remove(foundExistingPaymentMethod);
                    }
                }

                // Finally, all payment methods left in the existingPaymentMethods should be marked as deleted
                for (final PaymentMethodModelDao existingPaymentMethod : existingPaymentMethods) {
                    // Need to verify if this is active -- failure to do so would provide an exception down the stream because
                    // the logic around audit/history will use getById to retrieve the entity and that method would not return
                    // a marked as deleted object
                    if (existingPaymentMethod.isActive()) {
                        deletedPaymentMethodInTransaction(entitySqlDaoWrapperFactory, existingPaymentMethod.getId(), context);
                    }
                }
                return transactional.getByAccountId(accountId.toString(), context);
            }
        });
    }
}
