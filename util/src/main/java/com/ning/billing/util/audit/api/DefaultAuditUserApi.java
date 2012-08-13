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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;

import com.ning.billing.entitlement.api.user.SubscriptionBundle;
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
import com.ning.billing.util.audit.DefaultAuditLogsForBundles;
import com.ning.billing.util.audit.DefaultAuditLogsForInvoicePayments;
import com.ning.billing.util.audit.DefaultAuditLogsForInvoices;
import com.ning.billing.util.audit.DefaultAuditLogsForPayments;
import com.ning.billing.util.audit.DefaultAuditLogsForRefunds;
import com.ning.billing.util.audit.dao.AuditDao;
import com.ning.billing.util.dao.ObjectType;
import com.ning.billing.util.dao.TableName;

import com.google.common.collect.ImmutableList;

public class DefaultAuditUserApi implements AuditUserApi {

    private final AuditDao auditDao;

    @Inject
    public DefaultAuditUserApi(final AuditDao auditDao) {
        this.auditDao = auditDao;
    }

    @Override
    public AuditLogsForBundles getAuditLogsForBundles(final List<SubscriptionBundle> bundles, final AuditLevel auditLevel) {
        final Map<UUID, List<AuditLog>> bundlesAuditLogs = new HashMap<UUID, List<AuditLog>>();
        for (final SubscriptionBundle bundle : bundles) {
            bundlesAuditLogs.put(bundle.getId(), getAuditLogs(bundle.getId(), ObjectType.BUNDLE, auditLevel));
        }

        return new DefaultAuditLogsForBundles(bundlesAuditLogs);
    }

    @Override
    public AuditLogsForInvoicePayments getAuditLogsForInvoicePayments(final List<InvoicePayment> invoicePayments, final AuditLevel auditLevel) {
        final Map<UUID, List<AuditLog>> invoicePaymentsAuditLogs = new HashMap<UUID, List<AuditLog>>();
        for (final InvoicePayment invoicePayment : invoicePayments) {
            invoicePaymentsAuditLogs.put(invoicePayment.getId(), getAuditLogs(invoicePayment.getId(), ObjectType.INVOICE_PAYMENT, auditLevel));
        }

        return new DefaultAuditLogsForInvoicePayments(invoicePaymentsAuditLogs);
    }

    @Override
    public AuditLogsForRefunds getAuditLogsForRefunds(final List<Refund> refunds, final AuditLevel auditLevel) {
        final Map<UUID, List<AuditLog>> refundsAuditLogs = new HashMap<UUID, List<AuditLog>>();
        for (final Refund refund : refunds) {
            refundsAuditLogs.put(refund.getId(), getAuditLogs(refund.getId(), ObjectType.REFUND, auditLevel));
        }

        return new DefaultAuditLogsForRefunds(refundsAuditLogs);
    }

    @Override
    public AuditLogsForPayments getAuditLogsForPayments(final List<Payment> payments, final AuditLevel auditLevel) {
        final Map<UUID, List<AuditLog>> paymentsAuditLogs = new HashMap<UUID, List<AuditLog>>();
        for (final Payment payment : payments) {
            paymentsAuditLogs.put(payment.getId(), getAuditLogs(payment.getId(), ObjectType.PAYMENT, auditLevel));
        }

        return new DefaultAuditLogsForPayments(paymentsAuditLogs);
    }

    @Override
    public AuditLogsForInvoices getAuditLogsForInvoices(final List<Invoice> invoices, final AuditLevel auditLevel) {
        final Map<UUID, List<AuditLog>> invoiceAuditLogs = new HashMap<UUID, List<AuditLog>>();
        final Map<UUID, List<AuditLog>> invoiceItemsAuditLogs = new HashMap<UUID, List<AuditLog>>();
        for (final Invoice invoice : invoices) {
            invoiceAuditLogs.put(invoice.getId(), getAuditLogs(invoice.getId(), ObjectType.INVOICE, auditLevel));
            for (final InvoiceItem invoiceItem : invoice.getInvoiceItems()) {
                invoiceItemsAuditLogs.put(invoiceItem.getId(), getAuditLogs(invoiceItem.getId(), ObjectType.INVOICE_ITEM, auditLevel));
            }
        }

        return new DefaultAuditLogsForInvoices(invoiceAuditLogs, invoiceItemsAuditLogs);
    }

    @Override
    public List<AuditLog> getAuditLogs(final UUID objectId, final ObjectType objectType, final AuditLevel auditLevel) {
        // Optimization - bail early
        if (AuditLevel.NONE.equals(auditLevel)) {
            return ImmutableList.<AuditLog>of();
        }

        final TableName tableName = getTableNameFromObjectType(objectType);
        if (tableName == null) {
            return ImmutableList.<AuditLog>of();
        }

        return auditDao.getAuditLogsForId(tableName, objectId, auditLevel);
    }

    private TableName getTableNameFromObjectType(final ObjectType objectType) {
        for (final TableName tableName : TableName.values()) {
            if (objectType.equals(tableName.getObjectType())) {
                return tableName;
            }
        }

        return null;
    }
}
