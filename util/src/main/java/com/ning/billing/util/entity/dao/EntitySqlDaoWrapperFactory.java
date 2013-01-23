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

import java.lang.reflect.Proxy;

import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.dao.NonEntityDao;
import com.ning.billing.util.entity.Entity;

/**
 * Factory to create wrapped EntitySqlDao objects. During a transaction, make sure
 * to create other EntitySqlDao objects via the #become call.
 *
 * @param <InitialSqlDao> EntitySqlDao type to create
 * @see EntitySqlDaoWrapperInvocationHandler
 */
public class EntitySqlDaoWrapperFactory<InitialSqlDao extends EntitySqlDao> {

    private final InitialSqlDao sqlDao;
    private final Clock clock;
    private final CacheControllerDispatcher cacheControllerDispatcher;

    private final NonEntityDao nonEntityDao;

    public EntitySqlDaoWrapperFactory(final InitialSqlDao sqlDao, final Clock clock, final CacheControllerDispatcher cacheControllerDispatcher, final NonEntityDao nonEntityDao) {
        this.sqlDao = sqlDao;
        this.clock = clock;
        this.cacheControllerDispatcher = cacheControllerDispatcher;
        this.nonEntityDao = nonEntityDao;
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
        return create(newSqlDaoClass, sqlDao.become(newSqlDaoClass));
    }

    public <SelfType> SelfType transmogrify(final Class<SelfType> newTransactionalClass) {
        return sqlDao.become(newTransactionalClass);
    }

    private <NewSqlDao extends EntitySqlDao<NewEntityModelDao, NewEntity>,
            NewEntityModelDao extends EntityModelDao<NewEntity>,
            NewEntity extends Entity> NewSqlDao create(final Class<NewSqlDao> newSqlDaoClass, final NewSqlDao newSqlDao) {
        final ClassLoader classLoader = newSqlDao.getClass().getClassLoader();
        final Class[] interfacesToImplement = {newSqlDaoClass};
        final EntitySqlDaoWrapperInvocationHandler<NewSqlDao, NewEntityModelDao, NewEntity> wrapperInvocationHandler =
                new EntitySqlDaoWrapperInvocationHandler<NewSqlDao, NewEntityModelDao, NewEntity>(newSqlDaoClass, newSqlDao, clock, cacheControllerDispatcher, nonEntityDao);

        final Object newSqlDaoObject = Proxy.newProxyInstance(classLoader, interfacesToImplement, wrapperInvocationHandler);
        return newSqlDaoClass.cast(newSqlDaoObject);
    }
}
