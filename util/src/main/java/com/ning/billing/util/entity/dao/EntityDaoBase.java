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
import com.ning.billing.util.entity.EntityPersistenceException;

public abstract class EntityDaoBase<T extends Entity, U extends BillingExceptionBase> implements EntityDao<T, U> {

    protected final EntitySqlDaoTransactionalJdbiWrapper transactionalSqlDao;

    public EntityDaoBase(final EntitySqlDaoTransactionalJdbiWrapper transactionalSqlDao) {
        this.transactionalSqlDao = transactionalSqlDao;
    }

    @Override
    public void create(final T entity, final InternalCallContext context) throws U {
        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {

                if (getById(entity.getId(), context) != null) {
                    throw generateAlreadyExistsException(entity, context);
                }
                final EntitySqlDao<T> transactional = entitySqlDaoWrapperFactory.become(EntitySqlDao.class);
                transactional.create(entity, context);

                postBusEventFromTransaction(entity, entity, ChangeType.INSERT, entitySqlDaoWrapperFactory, context);
                return null;
            }
        });
    }

    protected void postBusEventFromTransaction(T entity, T savedEntity, ChangeType changeType, final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final InternalCallContext context)
        throws BillingExceptionBase {
    }

    protected abstract U generateAlreadyExistsException(final T entity, final InternalCallContext context);

    @Override
    public Long getRecordId(final UUID id, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Long>() {

            @Override
            public Long inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(EntitySqlDao.class).getRecordId(id.toString(), context);
            }
        });
    }

    @Override
    public T getByRecordId(final Long recordId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<T>() {

            @Override
            public T inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return (T) entitySqlDaoWrapperFactory.become(EntitySqlDao.class).getByRecordId(recordId, context);
            }
        });
    }

    @Override
    public T getById(final UUID id, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<T>() {

            @Override
            public T inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return (T) entitySqlDaoWrapperFactory.become(EntitySqlDao.class).getById(id.toString(), context);
            }
        });
    }

    @Override
    public List<T> get(final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<T>>() {

            @Override
            public List<T> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(EntitySqlDao.class).get(context);
            }
        });
    }

    @Override
    public void test(final InternalTenantContext context) {
        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {

            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                entitySqlDaoWrapperFactory.become(EntitySqlDao.class).test(context);
                return null;
            }
        });
    }
}
