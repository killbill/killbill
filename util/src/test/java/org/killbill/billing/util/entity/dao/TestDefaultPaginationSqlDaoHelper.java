/*
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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
import java.util.List;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.UtilTestSuiteWithEmbeddedDB;
import org.killbill.billing.util.dao.DefaultKombuchaModelDao;
import org.killbill.billing.util.dao.Kombucha;
import org.killbill.billing.util.dao.KombuchaModelDao;
import org.killbill.billing.util.dao.KombuchaSqlDao;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.DefaultPaginationSqlDaoHelper.Ordering;
import org.killbill.billing.util.entity.dao.DefaultPaginationSqlDaoHelper.PaginationIteratorBuilder;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestDefaultPaginationSqlDaoHelper extends UtilTestSuiteWithEmbeddedDB {

    private List<KombuchaModelDao> kombuchas;
    private EntitySqlDaoTransactionalJdbiWrapper transactionalSqlDao;

    @BeforeMethod(groups = "slow")
    public void setUp() {
        if (hasFailed()) {
            return;
        }

        kombuchas = List.of(new DefaultKombuchaModelDao("jasmin", "cordyceps", "white", internalCallContext),
                            new DefaultKombuchaModelDao("ginger", "reishi", "brown", internalCallContext),
                            new DefaultKombuchaModelDao("English breakfast", "lion's mane", "white", internalCallContext),
                            // Duplicate tea on purpose
                            new DefaultKombuchaModelDao("ginger", "chaga", "white", internalCallContext));
        transactionalSqlDao = new EntitySqlDaoTransactionalJdbiWrapper(dbi, roDbi, clock, cacheControllerDispatcher, nonEntityDao, internalCallContextFactory);
    }

    @Test(groups = "slow")
    public void testSimpleSearch0Match() {
        searchAndValidateKombuchas("jasmin", 0L, 100L, 0, 100L, 0L, 0L, 0L, null);
    }

    @Test(groups = "slow")
    public void testSimpleSearch1Match() {
        searchAndValidateKombuchas("jasmin", 0L, 100L, 4, 100L, 1L, 4L, 0L, null);
    }

    @Test(groups = "slow")
    public void testSimpleSearch2Matches() {
        searchAndValidateKombuchas("ginger", 0L, 100L, 4, 100L, 2L, 4L, 0L, null);
    }

    @Test(groups = "slow")
    public void testSimpleSearch2MatchesWithFirstResult() {
        searchAndValidateKombuchas("ginger", 0L, 1L, 4, 100L, 2L, 4L, 0L, 1L);
    }

    @Test(groups = "slow")
    public void testSimpleSearch2MatchesWithSecondResult() {
        searchAndValidateKombuchas("ginger", 1L, 1L, 4, 100L, 2L, 4L, 1L, null);
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1218")
    public void testSearch1MatchOnLargeTable() {
        // Force a low simplePaginationThreshold to avoid running the full count
        searchAndValidateKombuchas("jasmin", 0L, 100L, 4, 1L, 1L, null, 0L, null);
    }

    @Test(groups = "slow")
    public void testListKombuchasAll() {
        insertKombuchas(null);
        insertKombuchas(2347L); //dummy accountRecordId

        //Test with acccountRecordId
        for (int i = 0; i < 4; i++) {
            if (i < 3) {
                listAndValidateKombuchas(Long.valueOf(i), 1L, 100L, 4L, 4L, Long.valueOf(i), Long.valueOf(i + 1), true);
            } else {
                listAndValidateKombuchas(Long.valueOf(i), 1L, 100L, 4L, 4L, Long.valueOf(i), null, true);
            }
        }

        //Test without acccountRecordId
        for (int i = 0; i < 8; i++) {
            if (i < 7) {
                listAndValidateKombuchas(Long.valueOf(i), 1L, 100L, 8L, 8L, Long.valueOf(i), Long.valueOf(i + 1), false);
            } else {
                listAndValidateKombuchas(Long.valueOf(i), 1L, 100L, 8L, 8L, Long.valueOf(i), null, false);
            }
        }
    }

    @Test(groups = "slow")
    public void testListKombuchasWithDifferentLimitsAndOffsets() {
        insertKombuchas(null);
        insertKombuchas(2347L); //dummy accountRecordId

        //offset=0, limit=10
        listAndValidateKombuchas(0L, 10L, 100L, 4L, 4L, 0L, null, true);
        listAndValidateKombuchas(0L, 10L, 100L, 8L, 8L, 0L, null, false);

        //offset=0, limit=2
        listAndValidateKombuchas(0L, 2L, 100L, 4L, 4L, 0L, 2L, true);
        listAndValidateKombuchas(0L, 2L, 100L, 8L, 8L, 0L, 2L, false);

        //offset=1, limit=2
        listAndValidateKombuchas(1L, 2L, 100L, 4L, 4L, 1L, 3L, true);
        listAndValidateKombuchas(1L, 2L, 100L, 8L, 8L, 1L, 3L, false);

        //offset=3, limit=10
        listAndValidateKombuchas(3L, 10L, 100L, 4L, 4L, 3L, null, true);
        listAndValidateKombuchas(3L, 10L, 100L, 8L, 8L, 3L, null, false);

        //offset=0, limit=5
        listAndValidateKombuchas(0L, 5L, 100L, 4L, 4L, 0L, null, true);
        listAndValidateKombuchas(0L, 5L, 100L, 8L, 8L, 0L, 5L, false);
    }

    @Test(groups = "slow")
    public void testListKombuchasSmallPaginationThreshold() {
        insertKombuchas(null);
        insertKombuchas(2347L); //dummy accountRecordId

        //offset=0, limit=2
        listAndValidateKombuchas(0L, 2L, 1L, 4L, null, 0L, 2L, true);
        listAndValidateKombuchas(0L, 2L, 1L, 8L, null, 0L, 2L, false);
    }

    private void listAndValidateKombuchas(final Long offset,
    									  final Long limit,
    									  final Long simplePaginationThreshold, 
    									  final Long expectedTotalNbRecords,
    									  final Long expectedMaxNbRecords,
    									  final Long expectedCurrentOffset, 
    									  final Long expectedNextOffset,
    									  final boolean withAccountRecordId) {
    	final Pagination<KombuchaModelDao> pagination = withAccountRecordId ? listKombuchasWithAccountRecordId(offset, limit, simplePaginationThreshold) : listKombuchas(offset, limit, simplePaginationThreshold);
    	Assert.assertEquals(pagination.getTotalNbRecords(), expectedTotalNbRecords);
    	Assert.assertEquals(pagination.getMaxNbRecords(), expectedMaxNbRecords);
    	Assert.assertEquals(pagination.getCurrentOffset(), expectedCurrentOffset);
    	Assert.assertEquals(pagination.getNextOffset(), expectedNextOffset);

        closePagination(pagination);
    }

    private void searchAndValidateKombuchas(final String tea,
                                            final Long offset,
                                            final Long limit,
                                            final int nbKombuchas,
                                            final Long simplePaginationThreshold,
                                            final Long expectedTotalNbRecords,
                                            final Long expectedMaxNbRecords,
                                            final Long expectedCurrentOffset,
                                            final Long expectedNextOffset) {
        insertKombuchas(nbKombuchas);
        final Pagination<KombuchaModelDao> pagination = searchKombuchas(tea, offset, limit, simplePaginationThreshold);
        Assert.assertEquals(pagination.getTotalNbRecords(), expectedTotalNbRecords);
        Assert.assertEquals(pagination.getMaxNbRecords(), expectedMaxNbRecords);
        Assert.assertEquals(pagination.getCurrentOffset(), expectedCurrentOffset);
        Assert.assertEquals(pagination.getNextOffset(), expectedNextOffset);

        closePagination(pagination);
    }

    private Pagination<KombuchaModelDao> searchKombuchas(final String searchKey,
                                                         final Long offset,
                                                         final Long limit,
                                                         final Long simplePaginationThreshold) {
        final SearchQuery searchQuery = new SearchQuery(SqlOperator.OR);
        searchQuery.addSearchClause("tea", SqlOperator.EQ, searchKey);

        final PaginationIteratorBuilder<KombuchaModelDao, Kombucha, EntitySqlDao<KombuchaModelDao, Kombucha>> searchBestKombuchas = new PaginationIteratorBuilder<KombuchaModelDao, Kombucha, EntitySqlDao<KombuchaModelDao, Kombucha>>() {
            @Override
            public Long getCount(final EntitySqlDao<KombuchaModelDao, Kombucha> sqlDao, final InternalTenantContext context) {
                return sqlDao.getSearchCount(searchQuery.getSearchKeysBindMap(), searchQuery.getSearchAttributes(), searchQuery.getLogicalOperator(), context);
            }

            @Override
            public Iterator<KombuchaModelDao> build(final EntitySqlDao<KombuchaModelDao, Kombucha> sqlDao, final Long offset, final Long limit, final Ordering ordering, final InternalTenantContext context) {
                return sqlDao.search(searchQuery.getSearchKeysBindMap(), searchQuery.getSearchAttributes(), searchQuery.getLogicalOperator(), offset, limit, ordering.toString(), context);
            }
        };

        final DefaultPaginationSqlDaoHelper defaultPaginationSqlDaoHelper = new DefaultPaginationSqlDaoHelper(transactionalSqlDao, simplePaginationThreshold);
        return defaultPaginationSqlDaoHelper.getPagination(KombuchaSqlDao.class,
                                                           searchBestKombuchas,
                                                           offset,
                                                           limit,
                                                           internalCallContext);
    }

    private Pagination<KombuchaModelDao> listKombuchasWithAccountRecordId(final Long offset, final Long limit, final Long simplePaginationThreshold) {
        final PaginationIteratorBuilder<KombuchaModelDao, Kombucha, EntitySqlDao<KombuchaModelDao, Kombucha>> paginationIteratorBuilder = new PaginationIteratorBuilder<KombuchaModelDao, Kombucha, EntitySqlDao<KombuchaModelDao, Kombucha>>() {
            @Override
            public Long getCount(final EntitySqlDao<KombuchaModelDao, Kombucha> sqlDao, final InternalTenantContext context) {
                return sqlDao.getCountWithAccountRecordId(context);
            }

            @Override
            public Iterator<KombuchaModelDao> build(final EntitySqlDao<KombuchaModelDao, Kombucha> sqlDao, final Long offset, final Long limit, final Ordering ordering, final InternalTenantContext context) {
                return sqlDao.getByAccountRecordIdWithPaginationEnabled(offset, limit, context);
            }

        };

        final DefaultPaginationSqlDaoHelper defaultPaginationSqlDaoHelper = new DefaultPaginationSqlDaoHelper(transactionalSqlDao, simplePaginationThreshold);
        return defaultPaginationSqlDaoHelper.getPaginationWithAccountRecordId(KombuchaSqlDao.class,
                                                                              paginationIteratorBuilder,
                                                                              offset,
                                                                              limit,
                                                                              internalCallContext);
    }

    //method that queries the tables without the accountRecordId
    private Pagination<KombuchaModelDao> listKombuchas(final Long offset, final Long limit, final Long simplePaginationThreshold) {
        final PaginationIteratorBuilder<KombuchaModelDao, Kombucha, EntitySqlDao<KombuchaModelDao, Kombucha>> paginationIteratorBuilder = new PaginationIteratorBuilder<KombuchaModelDao, Kombucha, EntitySqlDao<KombuchaModelDao, Kombucha>>() {
            @Override
            public Long getCount(final EntitySqlDao<KombuchaModelDao, Kombucha> sqlDao, final InternalTenantContext context) {
                return sqlDao.getCount(context);
            }

            @Override
            public Iterator<KombuchaModelDao> build(final EntitySqlDao<KombuchaModelDao, Kombucha> sqlDao, final Long offset, final Long limit, final Ordering ordering, final InternalTenantContext context) {
                return sqlDao.get(offset, limit, "record_id", ordering.toString(), context);
            }

        };

        final DefaultPaginationSqlDaoHelper defaultPaginationSqlDaoHelper = new DefaultPaginationSqlDaoHelper(transactionalSqlDao, simplePaginationThreshold);
        return defaultPaginationSqlDaoHelper.getPagination(KombuchaSqlDao.class,
                                                           paginationIteratorBuilder,
                                                           offset,
                                                           limit,
                                                           internalCallContext);
    }

    private void insertKombuchas(final int nb) {
        Assert.assertTrue(nb <= kombuchas.size());

        final KombuchaSqlDao dao = dbi.onDemand(KombuchaSqlDao.class);
        for (int i = 0; i < nb; i++) {
            dao.create(kombuchas.get(i), internalCallContext);
        }
    }

    private void insertKombuchas(final Long accountRecordId) { //inserts all kombuchas with the specified accountRecordId
        final KombuchaSqlDao dao = dbi.onDemand(KombuchaSqlDao.class);
        if (accountRecordId != null) {
            internalCallContext.setAccountRecordId(accountRecordId);
        }
        for (int i = 0; i < kombuchas.size(); i++) {
            dao.create(kombuchas.get(i), internalCallContext);
        }
    }

    private void closePagination(final Closeable pagination) {
        try {
            pagination.close();
        } catch (final IOException ignored) {
        }
    }
}
