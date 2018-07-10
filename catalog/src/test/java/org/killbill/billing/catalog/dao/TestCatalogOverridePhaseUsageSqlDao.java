package org.killbill.billing.catalog.dao;
/*
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

import java.util.ArrayList;
import java.util.List;

import org.killbill.billing.catalog.CatalogTestSuiteWithEmbeddedDB;
import org.killbill.commons.jdbi.mapper.LowerToCamelBeanMapperFactory;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class TestCatalogOverridePhaseUsageSqlDao extends CatalogTestSuiteWithEmbeddedDB {


    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeClass();
        ((DBI) dbi).registerMapper(new LowerToCamelBeanMapperFactory(CatalogOverridePhaseUsageModelDao.class));
    }

    @Test(groups = "slow")
    public void testBasic() throws Exception {

        final CatalogOverridePhaseUsageModelDao obj1 = new CatalogOverridePhaseUsageModelDao((short) 1, 2L, 3L);

        performTestInTransaction(new WithCatalogOverridePhaseUsageSqlDaoTransaction<Void>() {
            @Override
            public Void doTransaction(final CatalogOverridePhaseUsageSqlDao sqlDao) {
                sqlDao.create(obj1, internalCallContext);
                final Long lastInserted = sqlDao.getLastInsertId();

                final CatalogOverridePhaseUsageModelDao rehydrated = sqlDao.getByRecordId(lastInserted, internalCallContext);
                assertEquals(rehydrated.getUsageNumber(), obj1.getUsageNumber());
                assertEquals(rehydrated.getUsageDefRecordId(), obj1.getUsageDefRecordId());
                assertEquals(rehydrated.getTargetPhaseDefRecordId(), obj1.getTargetPhaseDefRecordId());
                return null;
            }
        });
    }

    @Test(groups = "slow")
    public void testGetTargetPhaseDefinition() throws Exception {

        final CatalogOverridePhaseUsageModelDao obj1 = new CatalogOverridePhaseUsageModelDao((short) 1, 2L, 3L);
        final CatalogOverridePhaseUsageModelDao obj2 = new CatalogOverridePhaseUsageModelDao((short) 2, 5L, 3L);
        final CatalogOverridePhaseUsageModelDao obj3 = new CatalogOverridePhaseUsageModelDao((short) 4, 7L, 3L);
        final CatalogOverridePhaseUsageModelDao nobj1 = new CatalogOverridePhaseUsageModelDao((short) 4, 7L, 4L);

        performTestInTransaction(new WithCatalogOverridePhaseUsageSqlDaoTransaction<Void>() {
            @Override
            public Void doTransaction(final CatalogOverridePhaseUsageSqlDao sqlDao) {
                sqlDao.create(obj1, internalCallContext);
                sqlDao.create(obj2, internalCallContext);
                sqlDao.create(obj3, internalCallContext);
                sqlDao.create(nobj1, internalCallContext);

                final List<String> keys = new ArrayList<String>();
                keys.add("1,2");
                keys.add("2,5");
                keys.add("4,7");
                final List<Long> targetPhases = sqlDao.getTargetPhaseDefinition(keys, keys.size(), internalCallContext);
                assertEquals(targetPhases.size(), 1);
                assertEquals(targetPhases.get(0), new Long(3));
                return null;
            }
        });
    }

    @Test(groups = "slow")
    public void testGetTargetPhaseDefWithSameUsageOverrideAndDifferentRecurringPriceOverride() throws Exception {

        final CatalogOverridePhaseUsageModelDao obj1 = new CatalogOverridePhaseUsageModelDao((short) 1, 2L, 3L);
        final CatalogOverridePhaseUsageModelDao obj2 = new CatalogOverridePhaseUsageModelDao((short) 1, 2L, 4L);

        performTestInTransaction(new WithCatalogOverridePhaseUsageSqlDaoTransaction<Void>() {
            @Override
            public Void doTransaction(final CatalogOverridePhaseUsageSqlDao sqlDao) {
                sqlDao.create(obj1, internalCallContext);
                sqlDao.create(obj2, internalCallContext);

                final List<String> keys = new ArrayList<String>();
                keys.add("1,2");
                final List<Long> targetPhases = sqlDao.getTargetPhaseDefinition(keys, keys.size(), internalCallContext);
                assertEquals(targetPhases.size(), 2);
                assertEquals(targetPhases.get(0), new Long(3));
                assertEquals(targetPhases.get(1), new Long(4));
                return null;
            }
        });
    }

    private interface WithCatalogOverridePhaseUsageSqlDaoTransaction<T> {

        public <T> T doTransaction(final CatalogOverridePhaseUsageSqlDao sqlDao);
    }

    private <T> T performTestInTransaction(final WithCatalogOverridePhaseUsageSqlDaoTransaction<T> callback) {
        return dbi.inTransaction(new TransactionCallback<T>() {
            @Override
            public T inTransaction(final Handle handle, final TransactionStatus status) throws Exception {
                final CatalogOverridePhaseUsageSqlDao sqlDao = handle.attach(CatalogOverridePhaseUsageSqlDao.class);
                return callback.doTransaction(sqlDao);
            }
        });
    }

}
