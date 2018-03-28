/*
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

package org.killbill.billing.util.dao;

import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.security.SecurityApiException;
import org.killbill.billing.util.UtilTestSuiteWithEmbeddedDB;
import org.killbill.billing.util.entity.dao.EntityDaoBase;
import org.killbill.billing.util.entity.dao.EntitySqlDao;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestEntityBaseDaoException extends UtilTestSuiteWithEmbeddedDB {

    public static class TestEntityBaseDao extends EntityDaoBase<KombuchaModelDao, Kombucha, SecurityApiException> {

        public TestEntityBaseDao(final EntitySqlDaoTransactionalJdbiWrapper transactionalSqlDao, final Class<? extends EntitySqlDao<KombuchaModelDao, Kombucha>> realSqlDao) {
            super(transactionalSqlDao, realSqlDao);
        }

        @Override
        protected SecurityApiException generateAlreadyExistsException(final KombuchaModelDao entity, final InternalCallContext context) {
            return new SecurityApiException(ErrorCode.__UNKNOWN_ERROR_CODE);
        }

        @Override
        protected boolean checkEntityAlreadyExists(final EntitySqlDao<KombuchaModelDao, Kombucha> KombuchaSqlDao, final KombuchaModelDao entity, final InternalCallContext context) {
            return true;
        }
    }

    @Test(groups = "slow")
    public void testWithCreateException() throws Exception {
        final EntitySqlDaoTransactionalJdbiWrapper entitySqlDaoTransactionalJdbiWrapper = new EntitySqlDaoTransactionalJdbiWrapper(dbi, roDbi, clock, null, nonEntityDao, null);
        final TestEntityBaseDao test = new TestEntityBaseDao(entitySqlDaoTransactionalJdbiWrapper, KombuchaSqlDao.class);

        final KombuchaModelDao entity = new KombuchaModelDao() {
            @Override
            public Long getRecordId() {
                return null;
            }
            @Override
            public Long getAccountRecordId() {
                return null;
            }
            @Override
            public Long getTenantRecordId() {
                return null;
            }
            @Override
            public TableName getTableName() {
                return null;
            }
            @Override
            public TableName getHistoryTableName() {
                return null;
            }
            @Override
            public UUID getId() {
                return null;
            }
            @Override
            public DateTime getCreatedDate() {
                return null;
            }
            @Override
            public DateTime getUpdatedDate() {
                return null;
            }
        };

        try {
            test.create(entity, internalCallContext);
            Assert.fail("test should throw SecurityApiException");
        } catch (final SecurityApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.__UNKNOWN_ERROR_CODE.getCode());
        }

    }

}
