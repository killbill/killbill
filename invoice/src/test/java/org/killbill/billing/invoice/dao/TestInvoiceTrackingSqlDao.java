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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.invoice.InvoiceTestSuiteWithEmbeddedDB;
import org.killbill.billing.util.audit.ChangeType;
import org.killbill.billing.util.audit.dao.AuditLogModelDao;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.dao.TableName;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class TestInvoiceTrackingSqlDao extends InvoiceTestSuiteWithEmbeddedDB {

    private EntitySqlDaoTransactionalJdbiWrapper transactionalSqlDao;

    @BeforeMethod(groups = "slow")
    public void setUp() {
        if (hasFailed()) {
            return;
        }
        transactionalSqlDao = new EntitySqlDaoTransactionalJdbiWrapper(dbi, roDbi, clock, new CacheControllerDispatcher(), nonEntityDao, internalCallContextFactory);
    }

    @Test(groups = "slow")
    public void testBasicTrackingIds() {
        LocalDate startRange = new LocalDate(2018, 8, 1);
        LocalDate endRange = new LocalDate(2018, 11, 23);

        final UUID invoiceId1 = UUID.randomUUID();
        final UUID invoiceId2 = UUID.randomUUID();
        final UUID subscriptionId = UUID.randomUUID();

        // Before desired range
        final InvoiceTrackingModelDao input0 = new InvoiceTrackingModelDao(UUID.randomUUID(), clock.getUTCNow(), "trackingId0", invoiceId1, subscriptionId, "unit", startRange.minusDays(1));

        final InvoiceTrackingModelDao input1 = new InvoiceTrackingModelDao(UUID.randomUUID(), clock.getUTCNow(), "trackingId1", invoiceId1, subscriptionId, "unit", startRange);
        final InvoiceTrackingModelDao input2 = new InvoiceTrackingModelDao(UUID.randomUUID(), clock.getUTCNow(), "trackingId2", invoiceId1, subscriptionId, "unit", new LocalDate(2018, 8, 5));
        final InvoiceTrackingModelDao input3 = new InvoiceTrackingModelDao(UUID.randomUUID(), clock.getUTCNow(), "trackingId3", invoiceId2, subscriptionId, "unit", new LocalDate(2018, 9, 1));

        // After desired range
        final InvoiceTrackingModelDao input4 = new InvoiceTrackingModelDao(UUID.randomUUID(), clock.getUTCNow(), "trackingId4", invoiceId1, subscriptionId, "unit", endRange);

        final List<InvoiceTrackingModelDao> inputs = new ArrayList<>();
        inputs.add(input0);
        inputs.add(input1);
        inputs.add(input2);
        inputs.add(input3);
        inputs.add(input4);

        transactionalSqlDao.execute(false,
                                    new EntitySqlDaoTransactionWrapper<Void>() {
                                        @Override
                                        public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                                            final InvoiceTrackingSqlDao dao = entitySqlDaoWrapperFactory.become(InvoiceTrackingSqlDao.class);

                                            dao.create(inputs, internalCallContext);

                                            final List<InvoiceTrackingModelDao> result = dao.getTrackingsByDateRange(startRange.toDate(), endRange.toDate(), internalCallContext);
                                            Assert.assertEquals(result.size(), 3);

                                            Assert.assertEquals(result.get(0).getTrackingId(), "trackingId1");
                                            Assert.assertEquals(result.get(0).getInvoiceId(), invoiceId1);
                                            Assert.assertEquals(result.get(0).getRecordDate(), startRange);
                                            Assert.assertEquals(result.get(0).getSubscriptionId(), subscriptionId);

                                            Assert.assertEquals(result.get(1).getTrackingId(), "trackingId2");
                                            Assert.assertEquals(result.get(1).getInvoiceId(), invoiceId1);
                                            Assert.assertEquals(result.get(1).getRecordDate(), new LocalDate(2018, 8, 5));
                                            Assert.assertEquals(result.get(1).getSubscriptionId(), subscriptionId);

                                            Assert.assertEquals(result.get(2).getTrackingId(), "trackingId3");
                                            Assert.assertEquals(result.get(2).getInvoiceId(), invoiceId2);
                                            Assert.assertEquals(result.get(2).getRecordDate(), new LocalDate(2018, 9, 1));
                                            Assert.assertEquals(result.get(2).getSubscriptionId(), subscriptionId);

                                            return null;
                                        }
                                    });
    }

    @Test(groups = "slow")
    public void testInvalidation() {
        LocalDate startRange = new LocalDate(2019, 1, 1);
        LocalDate endRange = new LocalDate(2019, 1, 31);

        final UUID invoiceId1 = UUID.randomUUID();
        final UUID invoiceId2 = UUID.randomUUID();
        final UUID subscriptionId = UUID.randomUUID();

        // invoiceId1
        final InvoiceTrackingModelDao input1 = new InvoiceTrackingModelDao(UUID.randomUUID(), clock.getUTCNow(), "trackingId1", invoiceId1, subscriptionId, "unit", new LocalDate(2019, 1, 1));
        final InvoiceTrackingModelDao input2 = new InvoiceTrackingModelDao(UUID.randomUUID(), clock.getUTCNow(), "trackingId2", invoiceId1, subscriptionId, "unit", new LocalDate(2019, 1, 2));
        final InvoiceTrackingModelDao input3 = new InvoiceTrackingModelDao(UUID.randomUUID(), clock.getUTCNow(), "trackingId3", invoiceId1, subscriptionId, "unit", new LocalDate(2019, 1, 3));

        // invoiceId2
        final InvoiceTrackingModelDao input4 = new InvoiceTrackingModelDao(UUID.randomUUID(), clock.getUTCNow(), "trackingId4", invoiceId2, subscriptionId, "unit", new LocalDate(2019, 1, 5));

        final List<InvoiceTrackingModelDao> inputs = new ArrayList<>();
        inputs.add(input1);
        inputs.add(input2);
        inputs.add(input3);
        inputs.add(input4);

        transactionalSqlDao.execute(false,
                                    new EntitySqlDaoTransactionWrapper<Void>() {
                                        @Override
                                        public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                                            final InvoiceTrackingSqlDao dao = entitySqlDaoWrapperFactory.become(InvoiceTrackingSqlDao.class);

                                            dao.create(inputs, internalCallContext);

                                            final List<InvoiceTrackingModelDao> result = dao.getTrackingsByDateRange(startRange.toDate(), endRange.toDate(), internalCallContext);
                                            Assert.assertEquals(result.size(), 4);

                                            final List<AuditLogModelDao> auditLogsPostCreate = ImmutableList.<AuditLogModelDao>copyOf(dao.getAuditLogsForTableNameAndAccountRecordId(TableName.INVOICE_TRACKING_IDS.toString(), internalCallContext));
                                            Assert.assertEquals(auditLogsPostCreate.size(), 4);
                                            for (int i = 0; i < 4; i++) {
                                                Assert.assertEquals(auditLogsPostCreate.get(i).getChangeType(), ChangeType.INSERT);
                                                Assert.assertEquals(auditLogsPostCreate.get(i).getTargetRecordId(), result.get(i).getRecordId());
                                            }

                                            clock.addDays(1);
                                            final InternalCallContext updatedContext = new InternalCallContext(internalCallContext.getTenantRecordId(),
                                                                                                               internalCallContext.getAccountRecordId(),
                                                                                                               internalCallContext.getFixedOffsetTimeZone(),
                                                                                                               clock.getUTCNow(),
                                                                                                               internalCallContext.getUserToken(),
                                                                                                               "invalidation-user",
                                                                                                               internalCallContext.getCallOrigin(),
                                                                                                               internalCallContext.getContextUserType(),
                                                                                                               internalCallContext.getReasonCode(),
                                                                                                               internalCallContext.getComments(),
                                                                                                               internalCallContext.getCreatedDate(),
                                                                                                               clock.getUTCNow());

                                            dao.deactivateByIds(ImmutableList.<String>of(input1.getId().toString(), input2.getId().toString(), input3.getId().toString()), updatedContext);

                                            final List<InvoiceTrackingModelDao> result2 = dao.getTrackingsByDateRange(startRange.toDate(), endRange.toDate(), internalCallContext);
                                            Assert.assertEquals(result2.size(), 1);

                                            final List<AuditLogModelDao> auditLogsPostDelete = ImmutableList.<AuditLogModelDao>copyOf(dao.getAuditLogsForTableNameAndAccountRecordId(TableName.INVOICE_TRACKING_IDS.toString(), internalCallContext));
                                            Assert.assertEquals(auditLogsPostDelete.size(), 7);
                                            for (int i = 0; i < 4; i++) {
                                                Assert.assertEquals(auditLogsPostDelete.get(i).getChangeType(), ChangeType.INSERT);
                                                Assert.assertEquals(auditLogsPostDelete.get(i).getTargetRecordId(), result.get(i).getRecordId());
                                            }
                                            for (int i = 4; i < 7; i++) {
                                                Assert.assertEquals(auditLogsPostDelete.get(i).getChangeType(), ChangeType.DELETE);
                                                Assert.assertEquals(auditLogsPostDelete.get(i).getTargetRecordId(), result.get(i - 4).getRecordId());
                                            }

                                            return null;
                                        }
                                    });
    }
}
