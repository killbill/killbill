/*
 * Copyright 2010-2012 Ning, Inc.
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

package org.killbill.billing.util.entity.dao;

import javax.annotation.Nullable;

import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.entity.Entity;
import org.killbill.clock.Clock;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transaction manager for EntitySqlDao queries
 */
public class EntitySqlDaoTransactionalJdbiWrapper {

    private static final Logger logger = LoggerFactory.getLogger(EntitySqlDaoTransactionalJdbiWrapper.class);

    private final DBRouterUntyped dbRouter;
    private final Clock clock;
    private final CacheControllerDispatcher cacheControllerDispatcher;
    private final NonEntityDao nonEntityDao;
    private final InternalCallContextFactory internalCallContextFactory;

    public EntitySqlDaoTransactionalJdbiWrapper(final IDBI dbi, final IDBI roDbi, final Clock clock, final CacheControllerDispatcher cacheControllerDispatcher,
                                                final NonEntityDao nonEntityDao, final InternalCallContextFactory internalCallContextFactory) {
        this.clock = clock;
        this.cacheControllerDispatcher = cacheControllerDispatcher;
        this.nonEntityDao = nonEntityDao;
        this.internalCallContextFactory = internalCallContextFactory;
        this.dbRouter = new DBRouterUntyped(dbi, roDbi);
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
            final EntitySqlDaoWrapperFactory factoryEntitySqlDao = new EntitySqlDaoWrapperFactory(h, clock, cacheControllerDispatcher, internalCallContextFactory);
            return entitySqlDaoTransactionWrapper.inTransaction(factoryEntitySqlDao);
        }
    }

    // To handle warnings only
    interface InitialEntitySqlDao extends EntitySqlDao<EntityModelDao<Entity>, Entity> {}

    /**
     * @param <ReturnType>                   object type to return from the transaction
     * @param requestedRO                    hint as whether to use the read-only connection
     * @param entitySqlDaoTransactionWrapper transaction to execute
     * @return result from the transaction fo type ReturnType
     */
    public <ReturnType> ReturnType execute(final boolean requestedRO, final EntitySqlDaoTransactionWrapper<ReturnType> entitySqlDaoTransactionWrapper) {
        final String debugInfo = logger.isDebugEnabled() ? getDebugInfo() : null;

        final Handle handle = dbRouter.getHandle(requestedRO);
        logger.debug("DBI handle created, transaction: {}", debugInfo);
        try {
            final EntitySqlDao<EntityModelDao<Entity>, Entity> entitySqlDao = handle.attach(InitialEntitySqlDao.class);
            // The transaction isolation level is now set at the pool level: this avoids 3 roundtrips for each transaction
            // Note that if the pool isn't used (tests or PostgreSQL), the transaction level will depend on the DB configuration
            //return entitySqlDao.inTransaction(TransactionIsolationLevel.READ_COMMITTED, new JdbiTransaction<ReturnType, EntityModelDao<Entity>, Entity>(handle, entitySqlDaoTransactionWrapper));
            logger.debug("Starting transaction {}", debugInfo);
            final ReturnType returnType = entitySqlDao.inTransaction(new JdbiTransaction<ReturnType, EntityModelDao<Entity>, Entity>(handle, entitySqlDaoTransactionWrapper));
            logger.debug("Exiting  transaction {}, returning {}", debugInfo, returnType);
            return returnType;
        } finally {
            handle.close();
            logger.debug("DBI handle closed,  transaction: {}", debugInfo);
        }
    }

    //
    // This is only used in the pagination APIs when streaming results. We want to keep the connection open, and also there is no need
    // to send bus events, record notifications where we need to keep the Connection through the jDBI Handle.
    //
    public <M extends EntityModelDao<E>, E extends Entity, T extends EntitySqlDao<M, E>> T onDemandForStreamingResults(final Class<T> sqlObjectType) {
        return dbRouter.onDemand(true, sqlObjectType);
    }

    /**
     * @param <ReturnType>                   object type to return from the transaction
     * @param <E>                            checked exception which can be thrown from the transaction
     * @param ro                             whether to use the read-only connection
     * @param entitySqlDaoTransactionWrapper transaction to execute
     * @return result from the transaction fo type ReturnType
     */
    public <ReturnType, E extends Exception> ReturnType execute(final boolean ro, @Nullable final Class<E> exception, final EntitySqlDaoTransactionWrapper<ReturnType> entitySqlDaoTransactionWrapper) throws E {
        try {
            return execute(ro, entitySqlDaoTransactionWrapper);
        } catch (final RuntimeException e) {
            if (e.getCause() != null && exception != null && e.getCause().getClass().isAssignableFrom(exception)) {
                throw (E) e.getCause();
            } else if (e.getCause() != null && e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            } else {
                throw e;
            }
        }
    }

    private static String getDebugInfo() {
        final Throwable t = new Throwable();
        t.fillInStackTrace();

        final StackTraceElement[] stackTrace = t.getStackTrace();
        if (stackTrace == null) {
            return null;
        }

        final StringBuilder dump = new StringBuilder();
        int firstEntitySqlDaoCall = 0;

        String className;
        for (int i = 0; i < stackTrace.length; i++) {
            className = stackTrace[i].getClassName();
            if (className.startsWith("org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper")) {
                firstEntitySqlDaoCall = i;
            }
        }
        final int j = 1 + firstEntitySqlDaoCall;

        dump.append(stackTrace[j].getClassName()).append(".").append(stackTrace[j].getMethodName()).append("(").
                append(stackTrace[j].getFileName()).append(":").append(stackTrace[j].getLineNumber()).append(")");

        return dump.toString();
    }
}
