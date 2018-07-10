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

import java.math.BigDecimal;

import org.killbill.billing.catalog.CatalogTestSuiteWithEmbeddedDB;
import org.killbill.commons.jdbi.mapper.LowerToCamelBeanMapperFactory;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class TestCatalogOverrideBlockDefinitionSqlDao extends CatalogTestSuiteWithEmbeddedDB {

    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeClass();
        ((DBI) dbi).registerMapper(new LowerToCamelBeanMapperFactory(CatalogOverrideBlockDefinitionModelDao.class));
    }

    @Test(groups = "slow")
    public void testBasic() throws Exception {

        final CatalogOverrideBlockDefinitionModelDao obj1 = new CatalogOverrideBlockDefinitionModelDao("p1", "USD", BigDecimal.ONE, 1 , 1, clock.getUTCNow());
        performTestInTransaction(new WithCatalogOverrideBlockDefinitionSqlDaoTransaction<Void>() {
            @Override
            public Void doTransaction(final CatalogOverrideBlockDefinitionSqlDao sqlDao) {
                sqlDao.create(obj1, internalCallContext);
                final Long lastInserted = sqlDao.getLastInsertId();

                final CatalogOverrideBlockDefinitionModelDao rehydrated = sqlDao.getByRecordId(lastInserted, internalCallContext);
                assertEquals(rehydrated.getParentUnitName(), obj1.getParentUnitName());
                assertEquals(rehydrated.getCurrency(), obj1.getCurrency());
                assertEquals(rehydrated.getPrice().compareTo(obj1.getPrice()), 0);
                assertEquals(rehydrated.getSize(), obj1.getSize());
                assertEquals(rehydrated.getMax(), obj1.getMax());
                return null;
            }
        });
    }

    @Test(groups = "slow")
    public void testGetByAttributes() throws Exception {

        final CatalogOverrideBlockDefinitionModelDao objWithNoNullPrice = new CatalogOverrideBlockDefinitionModelDao("p2", "USD", BigDecimal.ONE, new Double("1"), new Double("5"), clock.getUTCNow());

        performTestInTransaction(new WithCatalogOverrideBlockDefinitionSqlDaoTransaction<Void>() {
            @Override
            public Void doTransaction(final CatalogOverrideBlockDefinitionSqlDao sqlDao) {
                sqlDao.create(objWithNoNullPrice, internalCallContext);
                checkRehydrated(objWithNoNullPrice, sqlDao);

                return null;
            }

            private void checkRehydrated(final CatalogOverrideBlockDefinitionModelDao obj, final CatalogOverrideBlockDefinitionSqlDao sqlDao) {
                final CatalogOverrideBlockDefinitionModelDao rehydrated = sqlDao.getByAttributes(obj.getParentUnitName(), obj.getCurrency(), obj.getPrice(),obj.getMax(),obj.getSize(), internalCallContext);
                assertEquals(rehydrated.getParentUnitName(), obj.getParentUnitName());
                assertEquals(rehydrated.getCurrency(), obj.getCurrency());
                assertEquals(rehydrated.getPrice().compareTo(obj.getPrice()), 0);
                assertEquals(rehydrated.getSize(), obj.getSize());
                assertEquals(rehydrated.getMax(), obj.getMax());
            }
        });
    }

    private interface WithCatalogOverrideBlockDefinitionSqlDaoTransaction<T> {

        public <T> T doTransaction(final CatalogOverrideBlockDefinitionSqlDao sqlDao);
    }

    private <T> T performTestInTransaction(final WithCatalogOverrideBlockDefinitionSqlDaoTransaction<T> callback) {
        return dbi.inTransaction(new TransactionCallback<T>() {
            @Override
            public T inTransaction(final Handle handle, final TransactionStatus status) throws Exception {
                final CatalogOverrideBlockDefinitionSqlDao sqlDao = handle.attach(CatalogOverrideBlockDefinitionSqlDao.class);
                return callback.doTransaction(sqlDao);
            }
        });
    }
}
