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

import java.lang.reflect.Proxy;

import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.entity.Entity;
import org.killbill.clock.Clock;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.sqlobject.SqlObjectBuilder;

/**
 * Factory to create wrapped EntitySqlDao objects. During a transaction, make sure
 * to create other EntitySqlDao objects via the #become call.
 *
 * @see EntitySqlDaoWrapperInvocationHandler
 */
public class EntitySqlDaoWrapperFactory {

    private final Handle handle;
    private final Clock clock;
    private final CacheControllerDispatcher cacheControllerDispatcher;

    private final InternalCallContextFactory internalCallContextFactory;

    public EntitySqlDaoWrapperFactory(final Handle handle, final Clock clock, final CacheControllerDispatcher cacheControllerDispatcher, final InternalCallContextFactory internalCallContextFactory) {
        this.handle = handle;
        this.clock = clock;
        this.cacheControllerDispatcher = cacheControllerDispatcher;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    /**
     * Get an instance of a specified EntitySqlDao class, sharing the same database session as the
     * initial sql dao class with which this wrapper factory was created.
     *
     * @param newSqlDaoClass the class to instantiate
     * @param <NewSqlDao>    EntitySqlDao type to create
     * @return instance of NewSqlDao
     */
    public <NewSqlDao extends EntitySqlDao<NewEntityModelDao, NewEntity>,
            NewEntityModelDao extends EntityModelDao<NewEntity>,
            NewEntity extends Entity> NewSqlDao become(final Class<NewSqlDao> newSqlDaoClass) {
        final NewSqlDao newSqlDao = SqlObjectBuilder.attach(handle, newSqlDaoClass);
        return create(newSqlDaoClass, newSqlDao);
    }

    public Handle getHandle() {
        return handle;
    }

    private <NewSqlDao extends EntitySqlDao<NewEntityModelDao, NewEntity>,
            NewEntityModelDao extends EntityModelDao<NewEntity>,
            NewEntity extends Entity> NewSqlDao create(final Class<NewSqlDao> newSqlDaoClass, final NewSqlDao newSqlDao) {
        final ClassLoader classLoader = newSqlDao.getClass().getClassLoader();
        final Class[] interfacesToImplement = {newSqlDaoClass};
        final EntitySqlDaoWrapperInvocationHandler<NewSqlDao, NewEntityModelDao, NewEntity> wrapperInvocationHandler =
                new EntitySqlDaoWrapperInvocationHandler<NewSqlDao, NewEntityModelDao, NewEntity>(newSqlDaoClass, newSqlDao, handle, clock, cacheControllerDispatcher, internalCallContextFactory);

        final Object newSqlDaoObject = Proxy.newProxyInstance(classLoader, interfacesToImplement, wrapperInvocationHandler);
        return newSqlDaoClass.cast(newSqlDaoObject);
    }
}
