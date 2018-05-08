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

public class TestCatalogOverrideTierDefinitionSqlDao extends CatalogTestSuiteWithEmbeddedDB {
    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeClass();
        ((DBI) dbi).registerMapper(new LowerToCamelBeanMapperFactory(CatalogOverrideTierDefinitionModelDao.class));
    }

    @Test(groups = "slow")
    public void testBasic() throws Exception {

        final CatalogOverrideTierDefinitionModelDao obj1 = new CatalogOverrideTierDefinitionModelDao("USD", BigDecimal.ONE,null, clock.getUTCNow());
        performTestInTransaction(new WithCatalogOverrideTierDefinitionSqlDaoTransaction<Void>() {
            @Override
            public Void doTransaction(final CatalogOverrideTierDefinitionSqlDao sqlDao) {
                sqlDao.create(obj1, internalCallContext);
                final Long lastInserted = sqlDao.getLastInsertId();

                final CatalogOverrideTierDefinitionModelDao rehydrated = sqlDao.getByRecordId(lastInserted, internalCallContext);
                assertEquals(rehydrated.getFixedPrice().compareTo(obj1.getFixedPrice()), 0);
                assertEquals(rehydrated.getCurrency(), obj1.getCurrency());
                return null;
            }
        });
    }

    private interface WithCatalogOverrideTierDefinitionSqlDaoTransaction<T> {

        public <T> T doTransaction(final CatalogOverrideTierDefinitionSqlDao sqlDao);
    }

    private <T> T performTestInTransaction(final WithCatalogOverrideTierDefinitionSqlDaoTransaction<T> callback) {
        return dbi.inTransaction(new TransactionCallback<T>() {
            @Override
            public T inTransaction(final Handle handle, final TransactionStatus status) throws Exception {
                final CatalogOverrideTierDefinitionSqlDao sqlDao = handle.attach(CatalogOverrideTierDefinitionSqlDao.class);
                return callback.doTransaction(sqlDao);
            }
        });
    }
}
