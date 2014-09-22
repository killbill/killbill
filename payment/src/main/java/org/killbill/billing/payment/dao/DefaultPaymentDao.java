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
import javax.management.ImmutableDescriptor;

import org.joda.time.DateTime;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.entity.EntityPersistenceException;
import org.killbill.billing.events.BusInternalEvent;
import org.killbill.billing.payment.api.DefaultPaymentErrorEvent;
import org.killbill.billing.payment.api.DefaultPaymentInfoEvent;
import org.killbill.billing.payment.api.DefaultPaymentPluginErrorEvent;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentMethod;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.DefaultPaginationSqlDaoHelper;
import org.killbill.billing.util.entity.dao.DefaultPaginationSqlDaoHelper.PaginationIteratorBuilder;
import org.killbill.billing.util.entity.dao.EntitySqlDao;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import org.killbill.bus.api.PersistentBus;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.killbill.clock.Clock;
import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;

public class DefaultPaymentDao implements PaymentDao {

    private final static Logger log = LoggerFactory.getLogger(DefaultPaymentDao.class);

    private final EntitySqlDaoTransactionalJdbiWrapper transactionalSqlDao;
    private final DefaultPaginationSqlDaoHelper paginationHelper;
    private final PersistentBus eventBus;
    private final Clock clock;

    @Inject
    public DefaultPaymentDao(final IDBI dbi, final Clock clock, final CacheControllerDispatcher cacheControllerDispatcher, final NonEntityDao nonEntityDao, final PersistentBus eventBus) {
        this.transactionalSqlDao = new EntitySqlDaoTransactionalJdbiWrapper(dbi, clock, cacheControllerDispatcher, nonEntityDao);
        this.paginationHelper = new DefaultPaginationSqlDaoHelper(transactionalSqlDao);
        this.eventBus = eventBus;
        this.clock = clock;
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
    public PaymentAttemptModelDao insertPaymentAttemptWithProperties(final PaymentAttemptModelDao attempt, final InternalCallContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<PaymentAttemptModelDao>() {

            @Override
            public PaymentAttemptModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final PaymentAttemptSqlDao transactional = entitySqlDaoWrapperFactory.become(PaymentAttemptSqlDao.class);
                transactional.create(attempt, context);
                final PaymentAttemptModelDao result = transactional.getById(attempt.getId().toString(), context);
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
    public List<PaymentAttemptModelDao> getPaymentAttemptsByState(final String stateName, final DateTime createdBeforeDate, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<PaymentAttemptModelDao>>() {
            @Override
            public List<PaymentAttemptModelDao> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final PaymentAttemptSqlDao transactional = entitySqlDaoWrapperFactory.become(PaymentAttemptSqlDao.class);
                return transactional.getByStateName(stateName, createdBeforeDate.toDate(), context);
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
    public List<PaymentAttemptModelDao> getPaymentAttemptByTransactionExternalKey(final String externalKey, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<PaymentAttemptModelDao>>() {

            @Override
            public List<PaymentAttemptModelDao> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final PaymentAttemptSqlDao transactional = entitySqlDaoWrapperFactory.become(PaymentAttemptSqlDao.class);
                return transactional.getByTransactionExternalKey(externalKey, context);
            }
        });
    }

    @Override
    public int failOldPendingTransactions(final TransactionStatus newTransactionStatus, final DateTime createdBeforeDate, final InternalCallContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Integer>() {
            @Override
            public Integer inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final TransactionSqlDao transactional = entitySqlDaoWrapperFactory.become(TransactionSqlDao.class);
                final List<PaymentTransactionModelDao> oldPendingTransactions = transactional.getByTransactionStatusPriorDate(TransactionStatus.PENDING.toString(), createdBeforeDate.toDate(), context);
                if (oldPendingTransactions.size() > 0) {
                    final Collection<String> oldPendingTransactionIds = Collections2.transform(oldPendingTransactions, new Function<PaymentTransactionModelDao, String>() {
                        @Override
                        public String apply(final PaymentTransactionModelDao input) {
                            return input.getId().toString();
                        }
                    });


                    return transactional.failOldPendingTransactions(oldPendingTransactionIds, TransactionStatus.PAYMENT_FAILURE.toString(), context);
                }
                return 0;
            }
        });
    }

    @Override
    public List<PaymentTransactionModelDao> getPaymentTransactionsByExternalKey(final String transactionExternalKey, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<PaymentTransactionModelDao>>() {
            @Override
            public List<PaymentTransactionModelDao> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(TransactionSqlDao.class).getPaymentTransactionsByExternalKey(transactionExternalKey, context);
            }
        });
    }

    @Override
    public PaymentModelDao getPaymentByExternalKey(final String paymentExternalKey, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<PaymentModelDao>() {
            @Override
            public PaymentModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(PaymentSqlDao.class).getPaymentByExternalKey(paymentExternalKey, context);
            }
        });
    }

    @Override
    public Pagination<PaymentModelDao> getPayments(final String pluginName, final Long offset, final Long limit, final InternalTenantContext context) {
        return paginationHelper.getPagination(PaymentSqlDao.class,
                                              new PaginationIteratorBuilder<PaymentModelDao, Payment, PaymentSqlDao>() {
                                                  @Override
                                                  public Long getCount(final PaymentSqlDao paymentSqlDao, final InternalTenantContext context) {
                                                      return paymentSqlDao.getCountByPluginName(pluginName, context);
                                                  }

                                                  @Override
                                                  public Iterator<PaymentModelDao> build(final PaymentSqlDao paymentSqlDao, final Long limit, final InternalTenantContext context) {
                                                      final Iterator<PaymentModelDao> result = paymentSqlDao.getByPluginName(pluginName, offset, limit, context);
                                                      return result;
                                                  }
                                              },
                                              offset,
                                              limit,
                                              context
                                             );
    }

    @Override
    public Pagination<PaymentModelDao> searchPayments(final String searchKey, final Long offset, final Long limit, final InternalTenantContext context) {
        return paginationHelper.getPagination(PaymentSqlDao.class,
                                              new PaginationIteratorBuilder<PaymentModelDao, Payment, PaymentSqlDao>() {
                                                  @Override
                                                  public Long getCount(final PaymentSqlDao paymentSqlDao, final InternalTenantContext context) {
                                                      return paymentSqlDao.getSearchCount(searchKey, String.format("%%%s%%", searchKey), context);
                                                  }

                                                  @Override
                                                  public Iterator<PaymentModelDao> build(final PaymentSqlDao paymentSqlDao, final Long limit, final InternalTenantContext context) {
                                                      return paymentSqlDao.search(searchKey, String.format("%%%s%%", searchKey), offset, limit, context);
                                                  }
                                              },
                                              offset,
                                              limit,
                                              context);
    }

    @Override
    public PaymentModelDao insertPaymentWithFirstTransaction(final PaymentModelDao payment, final PaymentTransactionModelDao paymentTransaction, final InternalCallContext context) {

        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<PaymentModelDao>() {

            @Override
            public PaymentModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final PaymentSqlDao paymentSqlDao = entitySqlDaoWrapperFactory.become(PaymentSqlDao.class);
                paymentSqlDao.create(payment, context);
                entitySqlDaoWrapperFactory.become(TransactionSqlDao.class).create(paymentTransaction, context);
                return paymentSqlDao.getById(payment.getId().toString(), context);
            }
        });
    }

    @Override
    public PaymentTransactionModelDao updatePaymentWithNewTransaction(final UUID paymentId, final PaymentTransactionModelDao paymentTransaction, final InternalCallContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<PaymentTransactionModelDao>() {
            @Override
            public PaymentTransactionModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final TransactionSqlDao transactional = entitySqlDaoWrapperFactory.become(TransactionSqlDao.class);
                transactional.create(paymentTransaction, context);
                final PaymentTransactionModelDao paymentTransactionModelDao = transactional.getById(paymentTransaction.getId().toString(), context);

                entitySqlDaoWrapperFactory.become(PaymentSqlDao.class).updatePaymentForNewTransaction(paymentId.toString(), context);

                return paymentTransactionModelDao;
            }
        });
    }

    @Override
    public void updatePaymentAndTransactionOnCompletion(final UUID accountId, final UUID paymentId, final TransactionType transactionType,
                                                        final String currentPaymentStateName, @Nullable final String lastPaymentSuccessStateName,
                                                        final UUID transactionId, final TransactionStatus transactionStatus,
                                                        final BigDecimal processedAmount, final Currency processedCurrency,
                                                        final String gatewayErrorCode, final String gatewayErrorMsg,
                                                        final InternalCallContext context) {
        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {

            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                entitySqlDaoWrapperFactory.become(TransactionSqlDao.class).updateTransactionStatus(transactionId.toString(),
                                                                                                   processedAmount, processedCurrency == null ? null : processedCurrency.toString(),
                                                                                                   transactionStatus == null ? null : transactionStatus.toString(),
                                                                                                   gatewayErrorCode, gatewayErrorMsg, context);
                if (lastPaymentSuccessStateName != null) {
                    entitySqlDaoWrapperFactory.become(PaymentSqlDao.class).updateLastSuccessPaymentStateName(paymentId.toString(), currentPaymentStateName, lastPaymentSuccessStateName, context);
                } else {
                    entitySqlDaoWrapperFactory.become(PaymentSqlDao.class).updatePaymentStateName(paymentId.toString(), currentPaymentStateName, context);
                }
                postPaymentEventFromTransaction(accountId, transactionStatus, transactionType, paymentId, processedAmount, processedCurrency, clock.getUTCNow(), gatewayErrorCode, entitySqlDaoWrapperFactory, context);
                return null;
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
    public PaymentTransactionModelDao getPaymentTransaction(final UUID transactionId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<PaymentTransactionModelDao>() {
            @Override
            public PaymentTransactionModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(TransactionSqlDao.class).getById(transactionId.toString(), context);
            }
        });
    }

    @Override
    public List<PaymentModelDao> getPaymentsForAccount(final UUID accountId, final InternalTenantContext context) {
        Preconditions.checkArgument(context.getAccountRecordId() != null);
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<PaymentModelDao>>() {
            @Override
            public List<PaymentModelDao> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(PaymentSqlDao.class).getByAccountRecordId(context);
            }
        });
    }

    @Override
    public List<PaymentModelDao> getPaymentsByStates(final String[] states, final DateTime createdBeforeDate, final DateTime createdAfterDate, final int limit, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<PaymentModelDao>>() {
            @Override
            public List<PaymentModelDao> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(PaymentSqlDao.class).getPaymentsByStates(ImmutableList.copyOf(states), createdBeforeDate.toDate(), createdAfterDate.toDate(), context, limit);
            }
        });
    }

    @Override
    public List<PaymentTransactionModelDao> getTransactionsForAccount(final UUID accountId, final InternalTenantContext context) {
        Preconditions.checkArgument(context.getAccountRecordId() != null);
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<PaymentTransactionModelDao>>() {
            @Override
            public List<PaymentTransactionModelDao> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(TransactionSqlDao.class).getByAccountRecordId(context);
            }
        });
    }

    @Override
    public List<PaymentTransactionModelDao> getTransactionsForPayment(final UUID paymentId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<PaymentTransactionModelDao>>() {
            @Override
            public List<PaymentTransactionModelDao> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(TransactionSqlDao.class).getByPaymentId(paymentId, context);
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
    public PaymentMethodModelDao getPaymentMethodByExternalKey(final String paymentMethodExternalKey, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<PaymentMethodModelDao>() {
            @Override
            public PaymentMethodModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(PaymentMethodSqlDao.class).getByExternalKey(paymentMethodExternalKey, context);
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
    public PaymentMethodModelDao getPaymentMethodByExternalKeyIncludedDeleted(final String paymentMethodExternalKey, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<PaymentMethodModelDao>() {
            @Override
            public PaymentMethodModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(PaymentMethodSqlDao.class).getPaymentMethodByExternalKeyIncludedDeleted(paymentMethodExternalKey, context);
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
    public Pagination<PaymentMethodModelDao> searchPaymentMethods(final String searchKey, final Long offset, final Long limit, final InternalTenantContext context) {
        return paginationHelper.getPagination(PaymentMethodSqlDao.class,
                                              new PaginationIteratorBuilder<PaymentMethodModelDao, PaymentMethod, PaymentMethodSqlDao>() {
                                                  @Override
                                                  public Long getCount(final PaymentMethodSqlDao paymentMethodSqlDao, final InternalTenantContext context) {
                                                      return paymentMethodSqlDao.getSearchCount(searchKey, String.format("%%%s%%", searchKey), context);
                                                  }

                                                  @Override
                                                  public Iterator<PaymentMethodModelDao> build(final PaymentMethodSqlDao paymentMethodSqlDao, final Long limit, final InternalTenantContext context) {
                                                      return paymentMethodSqlDao.search(searchKey, String.format("%%%s%%", searchKey), offset, limit, context);
                                                  }
                                              },
                                              offset,
                                              limit,
                                              context);
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

    private void postPaymentEventFromTransaction(final UUID accountId,
                                                 final TransactionStatus transactionStatus,
                                                 final TransactionType transactionType,
                                                 final UUID paymentId,
                                                 final BigDecimal processedAmount,
                                                 final Currency processedCurrency,
                                                 final DateTime effectiveDate,
                                                 final String gatewayErrorCode,
                                                 final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory,
                                                 final InternalCallContext context) {

        final BusInternalEvent event;
        switch (transactionStatus) {
            case SUCCESS:
            case PENDING:
                event = new DefaultPaymentInfoEvent(accountId,
                                                    paymentId,
                                                    processedAmount,
                                                    processedCurrency,
                                                    transactionStatus,
                                                    transactionType,
                                                    effectiveDate,
                                                    context.getAccountRecordId(),
                                                    context.getTenantRecordId(),
                                                    context.getUserToken());
                break;

            case PAYMENT_FAILURE:
                event = new DefaultPaymentErrorEvent(accountId,
                                                     paymentId,
                                                     transactionType,
                                                     gatewayErrorCode,
                                                     context.getAccountRecordId(),
                                                     context.getTenantRecordId(),
                                                     context.getUserToken());
                break;

            case PLUGIN_FAILURE:
            default:
                event = new DefaultPaymentPluginErrorEvent(accountId,
                                                           paymentId,
                                                           transactionType,
                                                           gatewayErrorCode,
                                                           context.getAccountRecordId(),
                                                           context.getTenantRecordId(),
                                                           context.getUserToken());
                break;
        }
        try {
            eventBus.postFromTransaction(event, entitySqlDaoWrapperFactory.getSqlDao());
        } catch (EventBusException e) {
            log.error("Failed to post Payment event event for account {} ", accountId, e);
        }
    }
}
