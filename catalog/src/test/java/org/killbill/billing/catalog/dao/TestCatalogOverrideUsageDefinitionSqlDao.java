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

public class TestCatalogOverrideUsageDefinitionSqlDao extends CatalogTestSuiteWithEmbeddedDB {

    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeClass();
        ((DBI) dbi).registerMapper(new LowerToCamelBeanMapperFactory(CatalogOverrideUsageDefinitionModelDao.class));
    }

    @Test(groups = "slow")
    public void testBasic() throws Exception {

        final CatalogOverrideUsageDefinitionModelDao obj1 = new CatalogOverrideUsageDefinitionModelDao("u1", "CONSUMABLE", "USD",BigDecimal.ONE,null, clock.getUTCNow());
        performTestInTransaction(new WithCatalogOverrideUsageDefinitionSqlDaoTransaction<Void>() {
            @Override
            public Void doTransaction(final CatalogOverrideUsageDefinitionSqlDao sqlDao) {
                sqlDao.create(obj1, internalCallContext);
                final Long lastInserted = sqlDao.getLastInsertId();

                final CatalogOverrideUsageDefinitionModelDao rehydrated = sqlDao.getByRecordId(lastInserted, internalCallContext);
                assertEquals(rehydrated.getParentUsageName(), obj1.getParentUsageName());
                assertEquals(rehydrated.getType(), obj1.getType());
                assertEquals(rehydrated.getCurrency(), obj1.getCurrency());
                assertEquals(rehydrated.getFixedPrice().compareTo(obj1.getFixedPrice()), 0);
                return null;
            }
        });
    }

    @Test(groups = "slow")
    public void testGetByAttributes() throws Exception {

        final CatalogOverrideUsageDefinitionModelDao sameUsageWithDiffBlockPrice1 = new CatalogOverrideUsageDefinitionModelDao("u1", "CONSUMABLE", "USD", null, null, clock.getUTCNow());
        final CatalogOverrideUsageDefinitionModelDao sameUsageWithDiffBlockPrice2 = new CatalogOverrideUsageDefinitionModelDao("u1", "CONSUMABLE", "USD", null, null, clock.getUTCNow());
        final CatalogOverrideUsageDefinitionModelDao sameUsageWithDiffBlockPrice3 = new CatalogOverrideUsageDefinitionModelDao("u2", "CONSUMABLE", "USD", null, null, clock.getUTCNow());
        performTestInTransaction(new WithCatalogOverrideUsageDefinitionSqlDaoTransaction<Void>() {
            @Override
            public Void doTransaction(final CatalogOverrideUsageDefinitionSqlDao sqlDao) {
                sqlDao.create(sameUsageWithDiffBlockPrice1, internalCallContext);
                sqlDao.create(sameUsageWithDiffBlockPrice2, internalCallContext);
                sqlDao.create(sameUsageWithDiffBlockPrice3, internalCallContext);
                List<CatalogOverrideUsageDefinitionModelDao> objList = new ArrayList<CatalogOverrideUsageDefinitionModelDao>();
                objList.add(sameUsageWithDiffBlockPrice1);
                objList.add(sameUsageWithDiffBlockPrice2);
                checkRehydrated(objList, sqlDao);

                return null;
            }

            private void checkRehydrated(final List<CatalogOverrideUsageDefinitionModelDao> objList, final CatalogOverrideUsageDefinitionSqlDao sqlDao) {
                final List<CatalogOverrideUsageDefinitionModelDao> rehydrated = sqlDao.getByAttributes("u1", internalCallContext);
                assertEquals(rehydrated.size(), 2);
                assertEquals(rehydrated.get(0).getParentUsageName(), objList.get(0).getParentUsageName());
                assertEquals(rehydrated.get(0).getType(), objList.get(0).getType());
                assertEquals(rehydrated.get(0).getCurrency(), objList.get(0).getCurrency());
                assertEquals(rehydrated.get(1).getParentUsageName(), objList.get(1).getParentUsageName());
                assertEquals(rehydrated.get(1).getType(), objList.get(1).getType());
                assertEquals(rehydrated.get(1).getCurrency(), objList.get(1).getCurrency());

            }
        });
    }

    private interface WithCatalogOverrideUsageDefinitionSqlDaoTransaction<T> {

        public <T> T doTransaction(final CatalogOverrideUsageDefinitionSqlDao sqlDao);
    }

    private <T> T performTestInTransaction(final WithCatalogOverrideUsageDefinitionSqlDaoTransaction<T> callback) {
        return dbi.inTransaction(new TransactionCallback<T>() {
            @Override
            public T inTransaction(final Handle handle, final TransactionStatus status) throws Exception {
                final CatalogOverrideUsageDefinitionSqlDao sqlDao = handle.attach(CatalogOverrideUsageDefinitionSqlDao.class);
                return callback.doTransaction(sqlDao);
            }
        });
    }
}
