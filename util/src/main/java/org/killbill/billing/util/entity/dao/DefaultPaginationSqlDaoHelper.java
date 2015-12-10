/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

import javax.annotation.Nullable;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.entity.DefaultPagination;
import org.killbill.billing.util.entity.Entity;
import org.killbill.billing.util.entity.Pagination;

public class DefaultPaginationSqlDaoHelper {

    private final EntitySqlDaoTransactionalJdbiWrapper transactionalSqlDao;

    public DefaultPaginationSqlDaoHelper(final EntitySqlDaoTransactionalJdbiWrapper transactionalSqlDao) {
        this.transactionalSqlDao = transactionalSqlDao;
    }

    public <E extends Entity, M extends EntityModelDao<E>, S extends EntitySqlDao<M, E>> Pagination<M> getPagination(final Class<? extends EntitySqlDao<M, E>> sqlDaoClazz,
                                                                                                                     final PaginationIteratorBuilder<M, E, S> paginationIteratorBuilder,
                                                                                                                     final Long offset,
                                                                                                                     final Long limit,
                                                                                                                     @Nullable final InternalTenantContext context) {
        // Note: the connection will be busy as we stream the results out: hence we cannot use
        // SQL_CALC_FOUND_ROWS / FOUND_ROWS on the actual query.
        // We still need to know the actual number of results, mainly for the UI so that it knows if it needs to fetch
        // more pages.
        final Long count = transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Long>() {
            @Override
            public Long inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final EntitySqlDao<M, E> sqlDao = entitySqlDaoWrapperFactory.become(sqlDaoClazz);
                return paginationIteratorBuilder.getCount((S) sqlDao, context);
            }
        });

        // We usually always want to wrap our queries in an EntitySqlDaoTransactionWrapper... except here.
        // Since we want to stream the results out, we don't want to auto-commit when this method returns.
        final EntitySqlDao<M, E> sqlDao = transactionalSqlDao.onDemandForStreamingResults(sqlDaoClazz);
        final Long totalCount = context !=  null ? sqlDao.getCount(context) : null;
        final Iterator<M> results = paginationIteratorBuilder.build((S) sqlDao, limit, context);

        return new DefaultPagination<M>(offset, limit, count, totalCount, results);
    }

    public abstract static class PaginationIteratorBuilder<M extends EntityModelDao<E>, E extends Entity, S extends EntitySqlDao<M, E>> {

        // Determine the totalNbRecords:
        // - For search queries, return the "SearchCount", i.e. the number of records matching the search query.
        // - For get calls, return the total number of records (totalNbRecords == maxNbRecords)
        public abstract Long getCount(final S sqlDao, final InternalTenantContext context);

        public abstract Iterator<M> build(final S sqlDao, final Long limit, final InternalTenantContext context);
    }
}
