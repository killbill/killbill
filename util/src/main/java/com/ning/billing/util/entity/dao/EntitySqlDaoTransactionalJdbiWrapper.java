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

import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;

import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.dao.NonEntityDao;
import com.ning.billing.util.entity.Entity;

/**
 * Transaction manager for EntitySqlDao queries
 */
public class EntitySqlDaoTransactionalJdbiWrapper {

    private final IDBI dbi;
    private final Clock clock;
    private final CacheControllerDispatcher cacheControllerDispatcher;
    private final NonEntityDao nonEntityDao;

    public EntitySqlDaoTransactionalJdbiWrapper(final IDBI dbi, final Clock clock, final CacheControllerDispatcher cacheControllerDispatcher, final NonEntityDao nonEntityDao) {
        this.dbi = dbi;
        this.clock = clock;
        this.cacheControllerDispatcher = cacheControllerDispatcher;
        this.nonEntityDao = nonEntityDao;
    }

    class JdbiTransaction<ReturnType, M extends EntityModelDao<E>, E extends Entity> implements Transaction<ReturnType, EntitySqlDao<M, E>> {

        private final EntitySqlDaoTransactionWrapper<ReturnType> entitySqlDaoTransactionWrapper;

        JdbiTransaction(final EntitySqlDaoTransactionWrapper<ReturnType> entitySqlDaoTransactionWrapper) {
            this.entitySqlDaoTransactionWrapper = entitySqlDaoTransactionWrapper;
        }

        @Override
        public ReturnType inTransaction(final EntitySqlDao<M, E> transactionalSqlDao, final TransactionStatus status) throws Exception {
            final EntitySqlDaoWrapperFactory<EntitySqlDao> factoryEntitySqlDao = new EntitySqlDaoWrapperFactory<EntitySqlDao>(transactionalSqlDao, clock, cacheControllerDispatcher, nonEntityDao);
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
        final EntitySqlDao<EntityModelDao<Entity>, Entity> entitySqlDao = dbi.onDemand(InitialEntitySqlDao.class);
        return entitySqlDao.inTransaction(new JdbiTransaction<ReturnType, EntityModelDao<Entity>, Entity>(entitySqlDaoTransactionWrapper));
    }
}
