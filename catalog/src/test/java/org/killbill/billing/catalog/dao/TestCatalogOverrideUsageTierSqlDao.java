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

public class TestCatalogOverrideUsageTierSqlDao extends CatalogTestSuiteWithEmbeddedDB {


    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeClass();
        ((DBI) dbi).registerMapper(new LowerToCamelBeanMapperFactory(CatalogOverrideUsageTierModelDao.class));
    }

    @Test(groups = "slow")
    public void testBasic() throws Exception {

        final CatalogOverrideUsageTierModelDao obj1 = new CatalogOverrideUsageTierModelDao((short) 1, 2L, 3L);

        performTestInTransaction(new WithCatalogOverrideUsageTierSqlDaoTransaction<Void>() {
            @Override
            public Void doTransaction(final CatalogOverrideUsageTierSqlDao sqlDao) {
                sqlDao.create(obj1, internalCallContext);
                final Long lastInserted = sqlDao.getLastInsertId();

                final CatalogOverrideUsageTierModelDao rehydrated = sqlDao.getByRecordId(lastInserted, internalCallContext);
                assertEquals(rehydrated.getTierNumber(), obj1.getTierNumber());
                assertEquals(rehydrated.getTierDefRecordId(), obj1.getTierDefRecordId());
                assertEquals(rehydrated.getTargetUsageDefRecordId(), obj1.getTargetUsageDefRecordId());
                return null;
            }
        });
    }

    @Test(groups = "slow")
    public void testGetTargetUsageDefinition() throws Exception {

        final CatalogOverrideUsageTierModelDao obj1 = new CatalogOverrideUsageTierModelDao((short) 1, 2L, 3L);
        final CatalogOverrideUsageTierModelDao obj2 = new CatalogOverrideUsageTierModelDao((short) 2, 5L, 3L);
        final CatalogOverrideUsageTierModelDao obj3 = new CatalogOverrideUsageTierModelDao((short) 4, 7L, 3L);
        final CatalogOverrideUsageTierModelDao nobj1 = new CatalogOverrideUsageTierModelDao((short) 4, 7L, 4L);

        performTestInTransaction(new WithCatalogOverrideUsageTierSqlDaoTransaction<Void>() {
            @Override
            public Void doTransaction(final CatalogOverrideUsageTierSqlDao sqlDao) {
                sqlDao.create(obj1, internalCallContext);
                sqlDao.create(obj2, internalCallContext);
                sqlDao.create(obj3, internalCallContext);
                sqlDao.create(nobj1, internalCallContext);

                final List<String> keys = new ArrayList<String>();
                keys.add("1,2");
                keys.add("2,5");
                keys.add("4,7");
                final List<Long> targetUsages = sqlDao.getTargetUsageDefinition(keys, keys.size(), internalCallContext);
                assertEquals(targetUsages.size(), 1);
                assertEquals(targetUsages.get(0), new Long(3));
                return null;
            }
        });
    }

    @Test(groups = "slow")
    public void testGetTargetUsageDefWithSameTierOverrideAndDifferentUsagePriceOverride() throws Exception {

        final CatalogOverrideUsageTierModelDao obj1 = new CatalogOverrideUsageTierModelDao((short) 1, 2L, 3L);
        final CatalogOverrideUsageTierModelDao obj2 = new CatalogOverrideUsageTierModelDao((short) 1, 2L, 4L);

        performTestInTransaction(new WithCatalogOverrideUsageTierSqlDaoTransaction<Void>() {
            @Override
            public Void doTransaction(final CatalogOverrideUsageTierSqlDao sqlDao) {
                sqlDao.create(obj1, internalCallContext);
                sqlDao.create(obj2, internalCallContext);

                final List<String> keys = new ArrayList<String>();
                keys.add("1,2");
                final List<Long> targetUsages = sqlDao.getTargetUsageDefinition(keys, keys.size(), internalCallContext);
                assertEquals(targetUsages.size(), 2);
                assertEquals(targetUsages.get(0), new Long(3));
                assertEquals(targetUsages.get(1), new Long(4));
                return null;
            }
        });
    }

    private interface WithCatalogOverrideUsageTierSqlDaoTransaction<T> {

        public <T> T doTransaction(final CatalogOverrideUsageTierSqlDao sqlDao);
    }

    private <T> T performTestInTransaction(final WithCatalogOverrideUsageTierSqlDaoTransaction<T> callback) {
        return dbi.inTransaction(new TransactionCallback<T>() {
            @Override
            public T inTransaction(final Handle handle, final TransactionStatus status) throws Exception {
                final CatalogOverrideUsageTierSqlDao sqlDao = handle.attach(CatalogOverrideUsageTierSqlDao.class);
                return callback.doTransaction(sqlDao);
            }
        });
    }

}
