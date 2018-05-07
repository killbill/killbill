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

package org.killbill.billing.catalog.dao;

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

public class TestCatalogOverridePlanPhaseSqlDao extends CatalogTestSuiteWithEmbeddedDB {


    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeClass();
        ((DBI) dbi).registerMapper(new LowerToCamelBeanMapperFactory(CatalogOverridePlanPhaseModelDao.class));
    }

    @Test(groups = "slow")
    public void testBasic() throws Exception {

        final CatalogOverridePlanPhaseModelDao obj1 = new CatalogOverridePlanPhaseModelDao((short) 1, 2L, 3L);

        performTestInTransaction(new WithCatalogOverridePlanPhaseSqlDaoTransaction<Void>() {
            @Override
            public Void doTransaction(final CatalogOverridePlanPhaseSqlDao sqlDao) {
                sqlDao.create(obj1, internalCallContext);
                final Long lastInserted = sqlDao.getLastInsertId();

                final CatalogOverridePlanPhaseModelDao rehydrated = sqlDao.getByRecordId(lastInserted, internalCallContext);
                assertEquals(rehydrated.getPhaseNumber(), obj1.getPhaseNumber());
                assertEquals(rehydrated.getPhaseDefRecordId(), obj1.getPhaseDefRecordId());
                assertEquals(rehydrated.getTargetPlanDefRecordId(), obj1.getTargetPlanDefRecordId());
                return null;
            }
        });
    }

    @Test(groups = "slow")
    public void testGetTargetPlanDefinition() throws Exception {

        final CatalogOverridePlanPhaseModelDao obj1 = new CatalogOverridePlanPhaseModelDao((short) 1, 2L, 3L);
        final CatalogOverridePlanPhaseModelDao obj2 = new CatalogOverridePlanPhaseModelDao((short) 2, 5L, 3L);
        final CatalogOverridePlanPhaseModelDao obj3 = new CatalogOverridePlanPhaseModelDao((short) 4, 7L, 3L);
        final CatalogOverridePlanPhaseModelDao nobj1 = new CatalogOverridePlanPhaseModelDao((short) 4, 7L, 4L);

        performTestInTransaction(new WithCatalogOverridePlanPhaseSqlDaoTransaction<Void>() {
            @Override
            public Void doTransaction(final CatalogOverridePlanPhaseSqlDao sqlDao) {
                sqlDao.create(obj1, internalCallContext);
                sqlDao.create(obj2, internalCallContext);
                sqlDao.create(obj3, internalCallContext);
                sqlDao.create(nobj1, internalCallContext);

                final List<String> keys = new ArrayList<String>();
                keys.add("1,2");
                keys.add("2,5");
                keys.add("4,7");
                final Long targetPlan = sqlDao.getTargetPlanDefinition(keys, keys.size(), internalCallContext);
                assertEquals(targetPlan, new Long(3));
                return null;
            }
        });
    }

    private interface WithCatalogOverridePlanPhaseSqlDaoTransaction<T> {

        public <T> T doTransaction(final CatalogOverridePlanPhaseSqlDao sqlDao);
    }

    private <T> T performTestInTransaction(final WithCatalogOverridePlanPhaseSqlDaoTransaction<T> callback) {
        return dbi.inTransaction(new TransactionCallback<T>() {
            @Override
            public T inTransaction(final Handle handle, final TransactionStatus status) throws Exception {
                final CatalogOverridePlanPhaseSqlDao sqlDao = handle.attach(CatalogOverridePlanPhaseSqlDao.class);
                return callback.doTransaction(sqlDao);
            }
        });
    }

}
