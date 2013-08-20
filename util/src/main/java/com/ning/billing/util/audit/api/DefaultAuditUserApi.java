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

import javax.annotation.Nullable;
import javax.inject.Inject;

import com.ning.billing.ObjectType;
import com.ning.billing.entitlement.api.Subscription;
import com.ning.billing.entitlement.api.SubscriptionBundle;
import com.ning.billing.entitlement.api.SubscriptionEvent;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.payment.api.Payment;
import com.ning.billing.payment.api.Refund;
import com.ning.billing.util.api.AuditLevel;
import com.ning.billing.util.api.AuditUserApi;
import com.ning.billing.util.audit.AuditLog;
import com.ning.billing.util.audit.AuditLogsForAccount;
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
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.billing.util.dao.TableName;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;

public class DefaultAuditUserApi implements AuditUserApi {

    private final AuditDao auditDao;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public DefaultAuditUserApi(final AuditDao auditDao, final InternalCallContextFactory internalCallContextFactory) {
        this.auditDao = auditDao;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public AuditLogsForAccount getAuditLogsForAccount(final UUID accountId, final AuditLevel auditLevel, final TenantContext context) {
        return new DefaultAuditLogsForAccount(getAuditLogs(accountId, ObjectType.ACCOUNT, auditLevel, context));
    }

    @Override
    public AuditLogsForBundles getAuditLogsForBundles(final List<SubscriptionBundle> bundles, final AuditLevel auditLevel, final TenantContext context) {
        final Map<UUID, List<AuditLog>> bundlesAuditLogs = new HashMap<UUID, List<AuditLog>>();
        final Map<UUID, List<AuditLog>> subscriptionsAuditLogs = new HashMap<UUID, List<AuditLog>>();
        final Map<UUID, List<AuditLog>> subscriptionEventsAuditLogs = new HashMap<UUID, List<AuditLog>>();
        for (final SubscriptionBundle bundle : bundles) {
            bundlesAuditLogs.put(bundle.getId(), getAuditLogs(bundle.getId(), ObjectType.BUNDLE, auditLevel, context));
            for (final Subscription cur : bundle.getSubscriptions()) {

                final ImmutableList<SubscriptionEvent> events = ImmutableList.<SubscriptionEvent>copyOf(Collections2.filter(bundle.getTimeline().getSubscriptionEvents(), new Predicate<SubscriptionEvent>() {
                    @Override
                    public boolean apply(@Nullable final SubscriptionEvent input) {
                        return input.getEntitlementId().equals(cur.getId());
                    }
                }));
                subscriptionsAuditLogs.put(cur.getId(), getAuditLogs(cur.getId(), ObjectType.SUBSCRIPTION, auditLevel, context));
                for (final SubscriptionEvent event : events) {
                    subscriptionEventsAuditLogs.put(event.getId(), getAuditLogs(event.getId(), event.getSubscriptionEventType().getObjectType(), auditLevel, context));
                }
            }
        }

        return new DefaultAuditLogsForBundles(bundlesAuditLogs, subscriptionsAuditLogs, subscriptionEventsAuditLogs);
    }

    @Override
    public AuditLogsForInvoicePayments getAuditLogsForInvoicePayments(final List<InvoicePayment> invoicePayments, final AuditLevel auditLevel, final TenantContext context) {
        final Map<UUID, List<AuditLog>> invoicePaymentsAuditLogs = new HashMap<UUID, List<AuditLog>>();
        for (final InvoicePayment invoicePayment : invoicePayments) {
            invoicePaymentsAuditLogs.put(invoicePayment.getId(), getAuditLogs(invoicePayment.getId(), ObjectType.INVOICE_PAYMENT, auditLevel, context));
        }

        return new DefaultAuditLogsForInvoicePayments(invoicePaymentsAuditLogs);
    }

    @Override
    public AuditLogsForRefunds getAuditLogsForRefunds(final List<Refund> refunds, final AuditLevel auditLevel, final TenantContext context) {
        final Map<UUID, List<AuditLog>> refundsAuditLogs = new HashMap<UUID, List<AuditLog>>();
        for (final Refund refund : refunds) {
            refundsAuditLogs.put(refund.getId(), getAuditLogs(refund.getId(), ObjectType.REFUND, auditLevel, context));
        }

        return new DefaultAuditLogsForRefunds(refundsAuditLogs);
    }

    @Override
    public AuditLogsForPayments getAuditLogsForPayments(final List<Payment> payments, final AuditLevel auditLevel, final TenantContext context) {
        final Map<UUID, List<AuditLog>> paymentsAuditLogs = new HashMap<UUID, List<AuditLog>>();
        for (final Payment payment : payments) {
            paymentsAuditLogs.put(payment.getId(), getAuditLogs(payment.getId(), ObjectType.PAYMENT, auditLevel, context));
        }

        return new DefaultAuditLogsForPayments(paymentsAuditLogs);
    }

    @Override
    public AuditLogsForInvoices getAuditLogsForInvoices(final List<Invoice> invoices, final AuditLevel auditLevel, final TenantContext context) {
        final Map<UUID, List<AuditLog>> invoiceAuditLogs = new HashMap<UUID, List<AuditLog>>();
        final Map<UUID, List<AuditLog>> invoiceItemsAuditLogs = new HashMap<UUID, List<AuditLog>>();
        for (final Invoice invoice : invoices) {
            invoiceAuditLogs.put(invoice.getId(), getAuditLogs(invoice.getId(), ObjectType.INVOICE, auditLevel, context));
            for (final InvoiceItem invoiceItem : invoice.getInvoiceItems()) {
                invoiceItemsAuditLogs.put(invoiceItem.getId(), getAuditLogs(invoiceItem.getId(), ObjectType.INVOICE_ITEM, auditLevel, context));
            }
        }

        return new DefaultAuditLogsForInvoices(invoiceAuditLogs, invoiceItemsAuditLogs);
    }

    @Override
    public List<AuditLog> getAuditLogs(final UUID objectId, final ObjectType objectType, final AuditLevel auditLevel, final TenantContext context) {
        // Optimization - bail early
        if (AuditLevel.NONE.equals(auditLevel)) {
            return ImmutableList.<AuditLog>of();
        }

        final TableName tableName = getTableNameFromObjectType(objectType);
        if (tableName == null) {
            return ImmutableList.<AuditLog>of();
        }

        return auditDao.getAuditLogsForId(tableName, objectId, auditLevel, internalCallContextFactory.createInternalTenantContext(context));
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
