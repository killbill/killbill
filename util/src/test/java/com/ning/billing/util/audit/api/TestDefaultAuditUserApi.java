/*
 * Copyright 2010-2012 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

package com.ning.billing.util.audit.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.ning.billing.ObjectType;
import com.ning.billing.entitlement.api.timeline.BundleTimeline;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.payment.api.Payment;
import com.ning.billing.payment.api.Refund;
import com.ning.billing.util.api.AuditLevel;
import com.ning.billing.util.api.AuditUserApi;
import com.ning.billing.util.audit.AuditLog;
import com.ning.billing.util.audit.AuditLogsForBundles;
import com.ning.billing.util.audit.AuditLogsForInvoicePayments;
import com.ning.billing.util.audit.AuditLogsForInvoices;
import com.ning.billing.util.audit.AuditLogsForPayments;
import com.ning.billing.util.audit.AuditLogsForRefunds;
import com.ning.billing.util.audit.AuditLogsTestBase;
import com.ning.billing.util.audit.dao.MockAuditDao;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.dao.TableName;
import com.ning.billing.util.entity.Entity;

import com.google.common.collect.ImmutableList;

public class TestDefaultAuditUserApi extends AuditLogsTestBase {

    private final List<AuditLog> auditLogs = ImmutableList.<AuditLog>of(createAuditLog(), createAuditLog(), createAuditLog(), createAuditLog());
    private final List<UUID> objectIds = ImmutableList.<UUID>of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    private AuditUserApi auditUserApi;

    @BeforeTest(groups = "fast")
    public void setupAuditLogs() throws Exception {
        final MockAuditDao auditDao = new MockAuditDao();
        for (final TableName tableName : TableName.values()) {
            for (final UUID objectId : objectIds) {
                for (final AuditLog auditLog : auditLogs) {
                    auditDao.addAuditLogForId(tableName, objectId, auditLog);
                }
            }
        }

        auditUserApi = new DefaultAuditUserApi(auditDao, Mockito.mock(InternalCallContextFactory.class));
    }

    @Test(groups = "fast")
    public void testForBundles() throws Exception {
        final List<BundleTimeline> bundles = new ArrayList<BundleTimeline>();
        for (final UUID objectId : objectIds) {
            final BundleTimeline entity = Mockito.mock(BundleTimeline.class);
            Mockito.when(entity.getId()).thenReturn(objectId);
            bundles.add(entity);
        }

        for (final AuditLevel level : AuditLevel.values()) {
            final AuditLogsForBundles auditLogsForBundles = auditUserApi.getAuditLogsForBundles(bundles, level, callContext);
            verifyAuditLogs(auditLogsForBundles.getBundlesAuditLogs(), level);
        }
    }

    @Test(groups = "fast")
    public void testForInvoicePayments() throws Exception {
        final List<InvoicePayment> invoicePayments = createMocks(InvoicePayment.class);

        for (final AuditLevel level : AuditLevel.values()) {
            final AuditLogsForInvoicePayments auditLogsForInvoicePayments = auditUserApi.getAuditLogsForInvoicePayments(invoicePayments, level, callContext);
            verifyAuditLogs(auditLogsForInvoicePayments.getInvoicePaymentsAuditLogs(), level);
        }
    }

    @Test(groups = "fast")
    public void testForRefunds() throws Exception {
        final List<Refund> refunds = createMocks(Refund.class);

        for (final AuditLevel level : AuditLevel.values()) {
            final AuditLogsForRefunds auditLogsForRefunds = auditUserApi.getAuditLogsForRefunds(refunds, level, callContext);
            verifyAuditLogs(auditLogsForRefunds.getRefundsAuditLogs(), level);
        }
    }

    @Test(groups = "fast")
    public void testForPayments() throws Exception {
        final List<Payment> payments = createMocks(Payment.class);

        for (final AuditLevel level : AuditLevel.values()) {
            final AuditLogsForPayments auditLogsForPayments = auditUserApi.getAuditLogsForPayments(payments, level, callContext);
            verifyAuditLogs(auditLogsForPayments.getPaymentsAuditLogs(), level);
        }
    }

    @Test(groups = "fast")
    public void testForInvoices() throws Exception {
        final List<Invoice> invoices = createMocks(Invoice.class);
        final List<InvoiceItem> invoiceItems = createMocks(InvoiceItem.class);
        for (final Invoice invoice : invoices) {
            Mockito.when(invoice.getInvoiceItems()).thenReturn(invoiceItems);
        }

        for (final AuditLevel level : AuditLevel.values()) {
            final AuditLogsForInvoices auditLogsForInvoices = auditUserApi.getAuditLogsForInvoices(invoices, level, callContext);
            verifyAuditLogs(auditLogsForInvoices.getInvoiceAuditLogs(), level);
            verifyAuditLogs(auditLogsForInvoices.getInvoiceItemsAuditLogs(), level);
        }
    }

    @Test(groups = "fast")
    public void testForObject() throws Exception {
        for (final ObjectType objectType : ObjectType.values()) {
            for (final UUID objectId : objectIds) {
                for (final AuditLevel level : AuditLevel.values()) {
                    if (AuditLevel.NONE.equals(level)) {
                        Assert.assertEquals(auditUserApi.getAuditLogs(objectId, objectType, level, callContext).size(), 0);
                    } else if (AuditLevel.MINIMAL.equals(level)) {
                        Assert.assertEquals(auditUserApi.getAuditLogs(objectId, objectType, level, callContext), ImmutableList.<AuditLog>of(auditLogs.get(0)));
                    } else {
                        Assert.assertEquals(auditUserApi.getAuditLogs(objectId, objectType, level, callContext), auditLogs);
                    }
                }
            }
        }
    }

    private void verifyAuditLogs(final Map<UUID, List<AuditLog>> objectsAuditLogs, final AuditLevel level) {
        Assert.assertEquals(objectsAuditLogs.size(), objectIds.size());

        if (AuditLevel.NONE.equals(level)) {
            for (final UUID objectId : objectIds) {
                Assert.assertEquals(objectsAuditLogs.get(objectId).size(), 0);
            }
        } else if (AuditLevel.MINIMAL.equals(level)) {
            for (final UUID objectId : objectIds) {
                Assert.assertEquals(objectsAuditLogs.get(objectId).size(), 1);
                Assert.assertEquals(objectsAuditLogs.get(objectId).get(0), auditLogs.get(0));
            }
        } else {
            for (final UUID objectId : objectIds) {
                Assert.assertEquals(objectsAuditLogs.get(objectId), auditLogs);
            }
        }
    }

    private <T extends Entity> List<T> createMocks(final Class<T> clazz) {
        final List<T> entities = new ArrayList<T>();
        for (final UUID objectId : objectIds) {
            final T entity = Mockito.mock(clazz);
            Mockito.when(entity.getId()).thenReturn(objectId);
            entities.add(entity);
        }

        return entities;
    }
}
