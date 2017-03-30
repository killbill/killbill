/*
 * Copyright 2010-2012 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.entity.Entity;
import org.killbill.clock.Clock;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionIsolationLevel;
import org.skife.jdbi.v2.TransactionStatus;

/**
 * Transaction manager for EntitySqlDao queries
 */
public class EntitySqlDaoTransactionalJdbiWrapper {

    private final IDBI dbi;
    private final Clock clock;
    private final CacheControllerDispatcher cacheControllerDispatcher;
    private final NonEntityDao nonEntityDao;
    private final InternalCallContextFactory internalCallContextFactory;

    public EntitySqlDaoTransactionalJdbiWrapper(final IDBI dbi, final Clock clock, final CacheControllerDispatcher cacheControllerDispatcher,
                                                final NonEntityDao nonEntityDao, final InternalCallContextFactory internalCallContextFactory) {
        this.dbi = dbi;
        this.clock = clock;
        this.cacheControllerDispatcher = cacheControllerDispatcher;
        this.nonEntityDao = nonEntityDao;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    public <M extends EntityModelDao> void populateCaches(final M refreshedEntity) {
        EntitySqlDaoWrapperInvocationHandler.populateCaches(cacheControllerDispatcher, refreshedEntity);
    }

    class JdbiTransaction<ReturnType, M extends EntityModelDao<E>, E extends Entity> implements Transaction<ReturnType, EntitySqlDao<M, E>> {

        private final Handle h;
        private final EntitySqlDaoTransactionWrapper<ReturnType> entitySqlDaoTransactionWrapper;

        JdbiTransaction(final Handle h, final EntitySqlDaoTransactionWrapper<ReturnType> entitySqlDaoTransactionWrapper) {
            this.h = h;
            this.entitySqlDaoTransactionWrapper = entitySqlDaoTransactionWrapper;
        }

        @Override
        public ReturnType inTransaction(final EntitySqlDao<M, E> transactionalSqlDao, final TransactionStatus status) throws Exception {
            final EntitySqlDaoWrapperFactory factoryEntitySqlDao = new EntitySqlDaoWrapperFactory(h, clock, cacheControllerDispatcher, nonEntityDao, internalCallContextFactory);
            return entitySqlDaoTransactionWrapper.inTransaction(factoryEntitySqlDao);
        }
    }

    // To handle warnings only
    interface InitialEntitySqlDao extends EntitySqlDao<EntityModelDao<Entity>, Entity> {}

    /**
     * @param entitySqlDaoTransactionWrapper transaction to execute
     * @param <ReturnType>                   object type to return from the transaction
     * @return result from the transaction fo type ReturnType
     */
    public <ReturnType> ReturnType execute(final EntitySqlDaoTransactionWrapper<ReturnType> entitySqlDaoTransactionWrapper) {
        final Handle handle = dbi.open();
        try {
            final EntitySqlDao<EntityModelDao<Entity>, Entity> entitySqlDao = handle.attach(InitialEntitySqlDao.class);
            return entitySqlDao.inTransaction(TransactionIsolationLevel.READ_COMMITTED, new JdbiTransaction<ReturnType, EntityModelDao<Entity>, Entity>(handle, entitySqlDaoTransactionWrapper));
        } finally {
            handle.close();
        }
    }

    //
    // This is only used in the pagination APIs when streaming results. We want to keep the connection open, and also there is no need
    // to send bus events, record notifications where we need to keep the Connection through the jDBI Handle.
    //
    public <M extends EntityModelDao<E>, E extends Entity, T extends EntitySqlDao<M, E>> T onDemandForStreamingResults(final Class<T> sqlObjectType) {
        return dbi.onDemand(sqlObjectType);
    }

    /**
     * @param entitySqlDaoTransactionWrapper transaction to execute
     * @param <ReturnType>                   object type to return from the transaction
     * @param <E>                            checked exception which can be thrown from the transaction
     * @return result from the transaction fo type ReturnType
     */
    public <ReturnType, E extends Exception> ReturnType execute(final Class<E> exception, final EntitySqlDaoTransactionWrapper<ReturnType> entitySqlDaoTransactionWrapper) throws E {
        try {
            return execute(entitySqlDaoTransactionWrapper);
        } catch (RuntimeException e) {
            if (e.getCause() != null && e.getCause().getClass().isAssignableFrom(exception)) {
                throw (E) e.getCause();
            } else if (e.getCause() != null && e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            } else {
                throw e;
            }
        }
    }
}
