/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.util.entity.dao;

import java.util.List;
import java.util.UUID;

import com.ning.billing.BillingExceptionBase;
import com.ning.billing.util.audit.ChangeType;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.entity.Entity;

public abstract class EntityDaoBase<M extends EntityModelDao<E>, E extends Entity, U extends BillingExceptionBase> implements EntityDao<M, E, U> {

    protected final EntitySqlDaoTransactionalJdbiWrapper transactionalSqlDao;

    private final Class<? extends EntitySqlDao<M, E>> realSqlDao;

    public EntityDaoBase(final EntitySqlDaoTransactionalJdbiWrapper transactionalSqlDao, final Class<? extends EntitySqlDao<M, E>> realSqlDao) {
        this.transactionalSqlDao = transactionalSqlDao;
        this.realSqlDao = realSqlDao;
    }

    @Override
    public void create(final M entity, final InternalCallContext context) throws U {
        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {

                if (getById(entity.getId(), context) != null) {
                    throw generateAlreadyExistsException(entity, context);
                }
                final EntitySqlDao<M, E> transactional = entitySqlDaoWrapperFactory.become(realSqlDao);
                transactional.create(entity, context);

                postBusEventFromTransaction(entity, entity, ChangeType.INSERT, entitySqlDaoWrapperFactory, context);
                return null;
            }
        });
    }

    protected void postBusEventFromTransaction(final M entity, final M savedEntity, final ChangeType changeType,
                                               final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory,
                                               final InternalCallContext context) throws BillingExceptionBase {
    }

    protected abstract U generateAlreadyExistsException(final M entity, final InternalCallContext context);

    @Override
    public Long getRecordId(final UUID id, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Long>() {

            @Override
            public Long inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final EntitySqlDao<M, E> transactional = entitySqlDaoWrapperFactory.become(realSqlDao);
                return transactional.getRecordId(id.toString(), context);
            }
        });
    }

    @Override
    public M getByRecordId(final Long recordId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<M>() {

            @Override
            public M inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final EntitySqlDao<M, E> transactional = entitySqlDaoWrapperFactory.become(realSqlDao);
                return transactional.getByRecordId(recordId, context);
            }
        });
    }

    @Override
    public M getById(final UUID id, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<M>() {

            @Override
            public M inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final EntitySqlDao<M, E> transactional = entitySqlDaoWrapperFactory.become(realSqlDao);
                return transactional.getById(id.toString(), context);
            }
        });
    }

    @Override
    public List<M> get(final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<M>>() {

            @Override
            public List<M> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final EntitySqlDao<M, E> transactional = entitySqlDaoWrapperFactory.become(realSqlDao);
                return transactional.get(context);
            }
        });
    }

    @Override
    public void test(final InternalTenantContext context) {
        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {

            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final EntitySqlDao<M, E> transactional = entitySqlDaoWrapperFactory.become(realSqlDao);
                transactional.test(context);
                return null;
            }
        });
    }
}
