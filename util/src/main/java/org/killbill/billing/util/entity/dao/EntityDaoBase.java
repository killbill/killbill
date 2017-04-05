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

package org.killbill.billing.util.entity.dao;

import java.util.Iterator;
import java.util.UUID;

import org.killbill.billing.BillingExceptionBase;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.entity.EntityPersistenceException;
import org.killbill.billing.util.audit.ChangeType;
import org.killbill.billing.util.entity.DefaultPagination;
import org.killbill.billing.util.entity.Entity;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.DefaultPaginationSqlDaoHelper.Ordering;
import org.killbill.billing.util.entity.dao.DefaultPaginationSqlDaoHelper.PaginationIteratorBuilder;

public abstract class EntityDaoBase<M extends EntityModelDao<E>, E extends Entity, U extends BillingExceptionBase> implements EntityDao<M, E, U> {

    protected final EntitySqlDaoTransactionalJdbiWrapper transactionalSqlDao;
    protected final DefaultPaginationSqlDaoHelper paginationHelper;

    private final Class<? extends EntitySqlDao<M, E>> realSqlDao;

    public EntityDaoBase(final EntitySqlDaoTransactionalJdbiWrapper transactionalSqlDao, final Class<? extends EntitySqlDao<M, E>> realSqlDao) {
        this.transactionalSqlDao = transactionalSqlDao;
        this.realSqlDao = realSqlDao;
        this.paginationHelper = new DefaultPaginationSqlDaoHelper(transactionalSqlDao);
    }

    @Override
    public void create(final M entity, final InternalCallContext context) throws U {
        final M refreshedEntity = transactionalSqlDao.execute(getCreateEntitySqlDaoTransactionWrapper(entity, context));
        // Populate the caches only after the transaction has been committed, in case of rollbacks
        transactionalSqlDao.populateCaches(refreshedEntity);
    }

    protected EntitySqlDaoTransactionWrapper<M> getCreateEntitySqlDaoTransactionWrapper(final M entity, final InternalCallContext context) {
        return new EntitySqlDaoTransactionWrapper<M>() {
            @Override
            public M inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final EntitySqlDao<M, E> transactional = entitySqlDaoWrapperFactory.become(realSqlDao);

                if (checkEntityAlreadyExists(transactional, entity, context)) {
                    throw generateAlreadyExistsException(entity, context);
                }
                final M refreshedEntity = createAndRefresh(transactional, entity, context);

                postBusEventFromTransaction(entity, refreshedEntity, ChangeType.INSERT, entitySqlDaoWrapperFactory, context);
                return refreshedEntity;
            }
        };
    }

    protected <F extends EntityModelDao> F createAndRefresh(final EntitySqlDao transactional, final F entity, final InternalCallContext context) throws EntityPersistenceException {
        // We have overridden the jDBI return type in EntitySqlDaoWrapperInvocationHandler
        return (F) transactional.create(entity, context);
    }

    protected boolean checkEntityAlreadyExists(final EntitySqlDao<M, E> transactional, final M entity, final InternalCallContext context) {
        return transactional.getRecordId(entity.getId().toString(), context) != null;
    }

    protected void postBusEventFromTransaction(final M entity, final M savedEntity, final ChangeType changeType,
                                               final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory,
                                               final InternalCallContext context) throws BillingExceptionBase {
    }

    protected abstract U generateAlreadyExistsException(final M entity, final InternalCallContext context);

    protected String getNaturalOrderingColumns() {
        return "record_id";
    }

    @Override
    public Long getRecordId(final UUID id, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Long>() {

            @Override
            public Long inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final EntitySqlDao<M, E> transactional = entitySqlDaoWrapperFactory.become(realSqlDao);
                return transactional.getRecordId(id.toString(), context);
            }
        });
    }

    @Override
    public M getByRecordId(final Long recordId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<M>() {

            @Override
            public M inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final EntitySqlDao<M, E> transactional = entitySqlDaoWrapperFactory.become(realSqlDao);
                return transactional.getByRecordId(recordId, context);
            }
        });
    }

    @Override
    public M getById(final UUID id, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<M>() {

            @Override
            public M inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final EntitySqlDao<M, E> transactional = entitySqlDaoWrapperFactory.become(realSqlDao);
                return transactional.getById(id.toString(), context);
            }
        });
    }

    @Override
    public Pagination<M> getAll(final InternalTenantContext context) {
        // We usually always want to wrap our queries in an EntitySqlDaoTransactionWrapper... except here.
        // Since we want to stream the results out, we don't want to auto-commit when this method returns.
        final EntitySqlDao<M, E> sqlDao = transactionalSqlDao.onDemandForStreamingResults(realSqlDao);

        // Note: we need to perform the count before streaming the results, as the connection
        // will be busy as we stream the results out. This is also why we cannot use
        // SQL_CALC_FOUND_ROWS / FOUND_ROWS (which may not be faster anyways).
        final Long count = sqlDao.getCount(context);

        final Iterator<M> results = sqlDao.getAll(context);
        return new DefaultPagination<M>(count, results);
    }

    @Override
    public Pagination<M> get(final Long offset, final Long limit, final InternalTenantContext context) {
        return paginationHelper.getPagination(realSqlDao,
                                              new PaginationIteratorBuilder<M, E, EntitySqlDao<M, E>>() {
                                                  @Override
                                                  public Long getCount(final EntitySqlDao<M, E> sqlDao, final InternalTenantContext context) {
                                                      // Only need to compute it once, because no search filter has been applied (see DefaultPaginationSqlDaoHelper)
                                                      return null;
                                                  }

                                                  @Override
                                                  public Iterator<M> build(final EntitySqlDao<M, E> sqlDao, final Long offset, final Long limit, final Ordering ordering, final InternalTenantContext context) {
                                                      return sqlDao.get(offset, limit, getNaturalOrderingColumns(), ordering.toString(), context);
                                                  }
                                              },
                                              offset,
                                              limit,
                                              context);
    }

    @Override
    public Long getCount(final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Long>() {

            @Override
            public Long inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final EntitySqlDao<M, E> transactional = entitySqlDaoWrapperFactory.become(realSqlDao);
                return transactional.getCount(context);
            }
        });
    }

    @Override
    public void test(final InternalTenantContext context) {
        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {

            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final EntitySqlDao<M, E> transactional = entitySqlDaoWrapperFactory.become(realSqlDao);
                transactional.test(context);
                return null;
            }
        });
    }
}
