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
import java.util.Objects;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.invoice.InvoiceTestSuiteWithEmbeddedDB;
import org.killbill.billing.util.audit.ChangeType;
import org.killbill.billing.util.audit.dao.AuditLogModelDao;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.dao.EntityHistoryModelDao;
import org.killbill.billing.util.dao.TableName;
import org.killbill.billing.util.entity.Entity;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

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

        transactionalSqlDao.execute(false,
                                    new EntitySqlDaoTransactionWrapper<Void>() {
                                        @Override
                                        public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                                            final InvoiceTrackingSqlDao dao = entitySqlDaoWrapperFactory.become(InvoiceTrackingSqlDao.class);

                                            final List<InvoiceTrackingModelDao> result = dao.getTrackingsForInvoices(ImmutableList.of(invoiceId1.toString(), invoiceId2.toString()), internalCallContext);
                                            Assert.assertEquals(result.size(), 5);
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

        // Create state
        transactionalSqlDao.execute(false,
                                    new EntitySqlDaoTransactionWrapper<Void>() {
                                        @Override
                                        public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                                            final InvoiceTrackingSqlDao dao = entitySqlDaoWrapperFactory.become(InvoiceTrackingSqlDao.class);

                                            dao.create(inputs, internalCallContext);

                                            return null;
                                        }
                                    });

        // Verify audit logs from existing state
        final List<InvoiceTrackingModelDao> initialTrackingIdsByRange = transactionalSqlDao.execute(false,
                                                                                                    new EntitySqlDaoTransactionWrapper<List<InvoiceTrackingModelDao>>() {
                                                                                                        @Override
                                                                                                        public List<InvoiceTrackingModelDao> inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {

                                                                                                            final InvoiceTrackingSqlDao dao = entitySqlDaoWrapperFactory.become(InvoiceTrackingSqlDao.class);
                                                                                                            final List<InvoiceTrackingModelDao> result = dao.getTrackingsByDateRange(startRange.toDate(), endRange.toDate(), internalCallContext);
                                                                                                            Assert.assertEquals(result.size(), 4);

                                                                                                            final List<EntityHistoryModelDao> entityHistoryModelDaos = new ArrayList<>();
                                                                                                            final List<AuditLogModelDao> auditLogsPostCreate = new ArrayList<>();
                                                                                                            for (int i = 0; i < 4; i++) {
                                                                                                                List<AuditLogModelDao> tmp1 = dao.getAuditLogsViaHistoryForTargetRecordId(TableName.INVOICE_TRACKING_ID_HISTORY.name(), TableName.INVOICE_TRACKING_ID_HISTORY.getTableName().toLowerCase(), result.get(i).getRecordId(), internalCallContext);
                                                                                                                auditLogsPostCreate.addAll(tmp1);

                                                                                                                final List<EntityHistoryModelDao<InvoiceTrackingModelDao, Entity>> tmp2 = dao.getHistoryForTargetRecordId(true, result.get(i).getRecordId(), internalCallContext);
                                                                                                                entityHistoryModelDaos.addAll(tmp2);
                                                                                                            }

                                                                                                            Assert.assertEquals(auditLogsPostCreate.size(), 4);
                                                                                                            Assert.assertEquals(entityHistoryModelDaos.size(), 4);

                                                                                                            for (int i = 0; i < 4; i++) {
                                                                                                                Assert.assertEquals(auditLogsPostCreate.get(i).getChangeType(), ChangeType.INSERT);

                                                                                                                // From the audit log entry, lookup the matching history entry
                                                                                                                final int curIdx = i;
                                                                                                                final EntityHistoryModelDao history = Iterables.find(entityHistoryModelDaos, new Predicate<EntityHistoryModelDao>() {
                                                                                                                    @Override
                                                                                                                    public boolean apply(final EntityHistoryModelDao input) {
                                                                                                                        return Objects.equals(input.getHistoryRecordId(), auditLogsPostCreate.get(curIdx).getTargetRecordId());
                                                                                                                    }
                                                                                                                });
                                                                                                                Assert.assertEquals(auditLogsPostCreate.get(i).getTargetRecordId(), history.getHistoryRecordId());
                                                                                                            }
                                                                                                            return result;
                                                                                                        }
                                                                                                    });

        clock.addDays(1);

        // Create state
        transactionalSqlDao.execute(false,
                                    new EntitySqlDaoTransactionWrapper<Void>() {
                                        @Override
                                        public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                                            final InvoiceTrackingSqlDao dao = entitySqlDaoWrapperFactory.become(InvoiceTrackingSqlDao.class);
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

                                            return null;
                                        }
                                    });

        // Verify audit logs from existing state
        transactionalSqlDao.execute(false,
                                    new EntitySqlDaoTransactionWrapper<Void>() {
                                        @Override
                                        public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                                            final InvoiceTrackingSqlDao dao = entitySqlDaoWrapperFactory.become(InvoiceTrackingSqlDao.class);

                                            final List<InvoiceTrackingModelDao> result2 = dao.getTrackingsByDateRange(startRange.toDate(), endRange.toDate(), internalCallContext);
                                            Assert.assertEquals(result2.size(), 1);

                                            final List<AuditLogModelDao> auditLogsPostDelete = new ArrayList<>();
                                            for (int i = 0; i < 4; i++) {
                                                List<AuditLogModelDao> tmp = dao.getAuditLogsViaHistoryForTargetRecordId(TableName.INVOICE_TRACKING_ID_HISTORY.name(), TableName.INVOICE_TRACKING_ID_HISTORY.getTableName().toLowerCase(), initialTrackingIdsByRange.get(i).getRecordId(), internalCallContext);
                                                auditLogsPostDelete.addAll(tmp);
                                            }

                                            Assert.assertEquals(auditLogsPostDelete.size(), 7);
                                            // First 3 records will show an INSERT & DELETE
                                            for (int i = 0; i < 3; i++) {
                                                Assert.assertEquals(auditLogsPostDelete.get(2 * i).getChangeType(), ChangeType.INSERT);
                                                Assert.assertEquals(auditLogsPostDelete.get(2 * i + 1).getChangeType(), ChangeType.DELETE);
                                            }
                                            // Last record will only show an INSERT
                                            Assert.assertEquals(auditLogsPostDelete.get(6).getChangeType(), ChangeType.INSERT);

                                            return null;
                                        }
                                    });
    }


    @Test(groups = "slow", description="https://github.com/killbill/killbill/issues/1390")
    public void testWithBatch() {
        LocalDate startRange = new LocalDate(2018, 8, 1);
        LocalDate endRange = new LocalDate(2018, 11, 23);

        final UUID invoiceId1 = UUID.randomUUID();
        final UUID subscriptionId = UUID.randomUUID();

        final List<InvoiceTrackingModelDao> inputs = new ArrayList<>();
        // The batch size is defined as 10000 -- See EntitySqlDaoWrapperInvocationHandler#BATCH_SIZE
        // so we chose a number right above this.
        // Local tests with postgres showed that limitation arose over 33K
        int NB_TRACKING_IDS = 10001;
        for (int i = 0; i < NB_TRACKING_IDS; i++) {

            final String trackingId = "tracking-" + i;
            final InvoiceTrackingModelDao input = new InvoiceTrackingModelDao(UUID.randomUUID(), clock.getUTCNow(), trackingId, invoiceId1, subscriptionId, "unit", startRange);
            inputs.add(input);
        }

        transactionalSqlDao.execute(false,
                                    new EntitySqlDaoTransactionWrapper<Void>() {
                                        @Override
                                        public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                                            final InvoiceTrackingSqlDao dao = entitySqlDaoWrapperFactory.become(InvoiceTrackingSqlDao.class);

                                            dao.create(inputs, internalCallContext);

                                            final List<InvoiceTrackingModelDao> result = dao.getTrackingsByDateRange(startRange.toDate(), endRange.toDate(), internalCallContext);
                                            Assert.assertEquals(result.size(), NB_TRACKING_IDS);
                                            final String expTrackingId = "tracking-" + (NB_TRACKING_IDS - 1);
                                            Assert.assertEquals(result.get(NB_TRACKING_IDS - 1).getTrackingId(), expTrackingId);
                                            return null;
                                        }
                                    });
    }
}
