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

package org.killbill.billing.invoice.dao;

import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.invoice.InvoiceTestSuiteWithEmbeddedDB;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestInvoiceBillingEventSqlDao extends InvoiceTestSuiteWithEmbeddedDB {

    private EntitySqlDaoTransactionalJdbiWrapper transactionalSqlDao;

    @BeforeMethod(groups = "slow")
    public void setUp() {
        if (hasFailed()) {
            return;
        }
        transactionalSqlDao = new EntitySqlDaoTransactionalJdbiWrapper(dbi, roDbi, clock, new CacheControllerDispatcher(), nonEntityDao, internalCallContextFactory);
    }

    @Test(groups = "slow")
    public void testBasic() {

        final UUID invoiceId = UUID.randomUUID();
        final int NB_BYTES = 100;
        final byte[] data = new byte[NB_BYTES];
        for (int i = 0; i < NB_BYTES; i++) {
            data[i] = (byte) i;
        }

        final InvoiceBillingEventModelDao entry = new InvoiceBillingEventModelDao(invoiceId, data, new DateTime());
        transactionalSqlDao.execute(false,
                                    new EntitySqlDaoTransactionWrapper<Void>() {
                                        @Override
                                        public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                                            final InvoiceBillingEventSqlDao dao = entitySqlDaoWrapperFactory.become(InvoiceBillingEventSqlDao.class);

                                            final InvoiceBillingEventModelDao result = (InvoiceBillingEventModelDao) dao.create(entry, internalCallContext);

                                            Assert.assertEquals(result.getInvoiceId(), invoiceId);
                                            Assert.assertEquals(result.getBillingEvents(), data);
                                            return null;
                                        }
                                    });

    }

}
