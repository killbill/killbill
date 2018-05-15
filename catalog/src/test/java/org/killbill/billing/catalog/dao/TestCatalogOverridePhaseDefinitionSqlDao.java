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
import static org.testng.Assert.assertNull;

public class TestCatalogOverridePhaseDefinitionSqlDao extends CatalogTestSuiteWithEmbeddedDB {

    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeClass();
        ((DBI) dbi).registerMapper(new LowerToCamelBeanMapperFactory(CatalogOverridePhaseDefinitionModelDao.class));
    }

    @Test(groups = "slow")
    public void testBasic() throws Exception {

        final CatalogOverridePhaseDefinitionModelDao obj1 = new CatalogOverridePhaseDefinitionModelDao("p1", "EUR", BigDecimal.ONE, BigDecimal.TEN, clock.getUTCNow());

        performTestInTransaction(new WithCatalogOverridePhaseDefinitionSqlDaoTransaction<Void>() {
            @Override
            public Void doTransaction(final CatalogOverridePhaseDefinitionSqlDao sqlDao) {
                sqlDao.create(obj1, internalCallContext);
                final Long lastInserted = sqlDao.getLastInsertId();

                final CatalogOverridePhaseDefinitionModelDao rehydrated = sqlDao.getByRecordId(lastInserted, internalCallContext);
                assertEquals(rehydrated.getParentPhaseName(), obj1.getParentPhaseName());
                assertEquals(rehydrated.getFixedPrice().compareTo(obj1.getFixedPrice()), 0);
                assertEquals(rehydrated.getRecurringPrice().compareTo(obj1.getRecurringPrice()), 0);
                assertEquals(rehydrated.getCurrency(), obj1.getCurrency());
                return null;
            }
        });
    }

    @Test(groups = "slow")
    public void testBasicWithNullPrices() throws Exception {

        final CatalogOverridePhaseDefinitionModelDao obj1 = new CatalogOverridePhaseDefinitionModelDao("p2", "USD", null, new BigDecimal("54.21"), clock.getUTCNow());

        performTestInTransaction(new WithCatalogOverridePhaseDefinitionSqlDaoTransaction<Void>() {
            @Override
            public Void doTransaction(final CatalogOverridePhaseDefinitionSqlDao sqlDao) {
                sqlDao.create(obj1, internalCallContext);
                final Long lastInserted = sqlDao.getLastInsertId();

                final CatalogOverridePhaseDefinitionModelDao rehydrated = sqlDao.getByRecordId(lastInserted, internalCallContext);
                assertEquals(rehydrated.getParentPhaseName(), obj1.getParentPhaseName());
                assertNull(rehydrated.getFixedPrice());
                assertEquals(rehydrated.getRecurringPrice().compareTo(obj1.getRecurringPrice()), 0);
                assertEquals(rehydrated.getCurrency(), obj1.getCurrency());
                return null;
            }
        });
    }

    @Test(groups = "slow")
    public void testGetByAttributes() throws Exception {

        final CatalogOverridePhaseDefinitionModelDao objWithNoNullPrices = new CatalogOverridePhaseDefinitionModelDao("p2", "USD", BigDecimal.ZERO, new BigDecimal("12.453"), clock.getUTCNow());
        final CatalogOverridePhaseDefinitionModelDao objWithNullFixedPrice = new CatalogOverridePhaseDefinitionModelDao("p3", "BTC", null, new BigDecimal("14.443"), clock.getUTCNow());
        final CatalogOverridePhaseDefinitionModelDao objWithNullRecurringPrice = new CatalogOverridePhaseDefinitionModelDao("p4", "EUR", new BigDecimal("11.243"), null, clock.getUTCNow());

        performTestInTransaction(new WithCatalogOverridePhaseDefinitionSqlDaoTransaction<Void>() {
            @Override
            public Void doTransaction(final CatalogOverridePhaseDefinitionSqlDao sqlDao) {
                sqlDao.create(objWithNoNullPrices, internalCallContext);
                checkRehydrated(objWithNoNullPrices, sqlDao);

                sqlDao.create(objWithNullFixedPrice, internalCallContext);
                checkRehydrated(objWithNullFixedPrice, sqlDao);

                sqlDao.create(objWithNullRecurringPrice, internalCallContext);
                checkRehydrated(objWithNullRecurringPrice, sqlDao);
                return null;
            }

            private void checkRehydrated(final CatalogOverridePhaseDefinitionModelDao obj, final CatalogOverridePhaseDefinitionSqlDao sqlDao) {
                final CatalogOverridePhaseDefinitionModelDao rehydrated = sqlDao.getByAttributes(obj.getParentPhaseName(), obj.getCurrency(), obj.getFixedPrice(), obj.getRecurringPrice(), internalCallContext).get(0);
                assertEquals(rehydrated.getParentPhaseName(), obj.getParentPhaseName());
                if (obj.getFixedPrice() != null) {
                    assertEquals(rehydrated.getFixedPrice().compareTo(obj.getFixedPrice()), 0);
                } else {
                    assertNull(rehydrated.getFixedPrice());
                }
                if (obj.getRecurringPrice() != null) {
                    assertEquals(rehydrated.getRecurringPrice().compareTo(obj.getRecurringPrice()), 0);
                } else {
                    assertNull(rehydrated.getRecurringPrice());
                }
                assertEquals(rehydrated.getCurrency(), obj.getCurrency());
            }
        });
    }

    private interface WithCatalogOverridePhaseDefinitionSqlDaoTransaction<T> {

        public <T> T doTransaction(final CatalogOverridePhaseDefinitionSqlDao sqlDao);
    }

    private <T> T performTestInTransaction(final WithCatalogOverridePhaseDefinitionSqlDaoTransaction<T> callback) {
        return dbi.inTransaction(new TransactionCallback<T>() {
            @Override
            public T inTransaction(final Handle handle, final TransactionStatus status) throws Exception {
                final CatalogOverridePhaseDefinitionSqlDao sqlDao = handle.attach(CatalogOverridePhaseDefinitionSqlDao.class);
                return callback.doTransaction(sqlDao);
            }
        });
    }

}
