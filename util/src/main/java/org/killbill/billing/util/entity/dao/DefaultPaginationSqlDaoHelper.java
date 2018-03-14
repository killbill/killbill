/*
 * Copyright 2010-2014 Ning, Inc.
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

import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;

import javax.annotation.Nullable;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.entity.DefaultPagination;
import org.killbill.billing.util.entity.Entity;
import org.killbill.billing.util.entity.Pagination;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultPaginationSqlDaoHelper {

    private static final Logger logger = LoggerFactory.getLogger(DefaultPaginationSqlDaoHelper.class);

    // Number large enough so that small installations have access to an accurate count
    // but small enough to not impact very large deployments
    // TODO Should this be configurable per tenant?
    private static final Long SIMPLE_PAGINATION_THRESHOLD = 20000L;

    private final EntitySqlDaoTransactionalJdbiWrapper transactionalSqlDao;

    public DefaultPaginationSqlDaoHelper(final EntitySqlDaoTransactionalJdbiWrapper transactionalSqlDao) {
        this.transactionalSqlDao = transactionalSqlDao;
    }

    public <E extends Entity, M extends EntityModelDao<E>, S extends EntitySqlDao<M, E>> Pagination<M> getPagination(final Class<? extends EntitySqlDao<M, E>> sqlDaoClazz,
                                                                                                                     final PaginationIteratorBuilder<M, E, S> paginationIteratorBuilder,
                                                                                                                     final Long offset,
                                                                                                                     final Long limitMaybeNegative,
                                                                                                                     @Nullable final InternalTenantContext context) {
        // Use a negative limit as a hint to go backwards. It's a bit awkward -- using a negative offset instead would be more intuitive,
        // but it is non-deterministic for the first page unfortunately (limit 0 offset 50: ASC or DESC?)
        final Ordering ordering = limitMaybeNegative >= 0 ? Ordering.ASC : Ordering.DESC;
        final Long limit = Math.abs(limitMaybeNegative);

        // Note: the connection will be busy as we stream the results out: hence we cannot use
        // SQL_CALC_FOUND_ROWS / FOUND_ROWS on the actual query.
        // We still need to know the actual number of results, mainly for the UI so that it knows if it needs to fetch
        // more pages.
        // Note: for simple pagination (no search filter), this will be computed below instead (MaxNbRecords == TotalNbRecords)
        final Long totalNbRecordsOrNull = transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<Long>() {
            @Override
            public Long inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final EntitySqlDao<M, E> sqlDao = entitySqlDaoWrapperFactory.become(sqlDaoClazz);
                return paginationIteratorBuilder.getCount((S) sqlDao, context);
            }
        });

        // We usually always want to wrap our queries in an EntitySqlDaoTransactionWrapper... except here.
        // Since we want to stream the results out, we don't want to auto-commit when this method returns.
        final EntitySqlDao<M, E> sqlDao = transactionalSqlDao.onDemandForStreamingResults(sqlDaoClazz);
        final Long maxNbRecords;
        if (context == null) {
            maxNbRecords = null;
        } else {
            // The count to get maxNbRecords can be expensive on very large datasets. As a heuristic to check how large that number is,
            // we retrieve 1 record at offset SIMPLE_PAGINATION_THRESHOLD (pretty fast). If we've found a record, that means the count is larger
            // than this threshold and we don't issue the full count query
            final Iterator<M> simplePaginationIterator = paginationIteratorBuilder.build((S) sqlDao, SIMPLE_PAGINATION_THRESHOLD, 1L, ordering, context);
            final boolean veryLargeDataSet = simplePaginationIterator.hasNext();

            // Make sure to free resources (https://github.com/killbill/killbill/issues/853)
            if (simplePaginationIterator instanceof Closeable) {
                // Always the case with the current implementation (simplePaginationIterator is a org.skife.jdbi.v2.ResultIterator)
                try {
                    ((Closeable) simplePaginationIterator).close();
                } catch (final IOException e) {
                    logger.warn("Unable to close iterator", e);
                }
            } else {
                while (simplePaginationIterator.hasNext()) {
                    simplePaginationIterator.next();
                }
            }

            if (veryLargeDataSet) {
                maxNbRecords = null;
            } else {
                maxNbRecords = sqlDao.getCount(context);
            }
        }
        final Iterator<M> results = paginationIteratorBuilder.build((S) sqlDao, offset, limit, ordering, context);

        final Long totalNbRecords = totalNbRecordsOrNull == null ? maxNbRecords : totalNbRecordsOrNull;

        return new DefaultPagination<M>(offset, limit, totalNbRecords, maxNbRecords, results);
    }

    public abstract static class PaginationIteratorBuilder<M extends EntityModelDao<E>, E extends Entity, S extends EntitySqlDao<M, E>> {

        // Determine the totalNbRecords:
        // - For search queries, return the "SearchCount", i.e. the number of records matching the search query.
        // - For get calls, return the total number of records (totalNbRecords == maxNbRecords)
        public abstract Long getCount(final S sqlDao, final InternalTenantContext context);

        public abstract Iterator<M> build(final S sqlDao, final Long offset, final Long limit, final Ordering ordering, final InternalTenantContext context);
    }

    public enum Ordering {
        ASC,
        DESC
    }
}
