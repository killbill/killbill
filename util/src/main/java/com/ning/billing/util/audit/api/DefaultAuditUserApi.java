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

import com.ning.billing.ObjectType;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.subscription.api.timeline.BundleTimeline;
import com.ning.billing.subscription.api.timeline.SubscriptionRepairException;
import com.ning.billing.subscription.api.timeline.SubscriptionTimelineApi;
import com.ning.billing.subscription.api.timeline.SubscriptionTimeline;
import com.ning.billing.subscription.api.timeline.SubscriptionTimeline.ExistingEvent;
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

import com.google.common.collect.ImmutableList;

public class DefaultAuditUserApi implements AuditUserApi {

    private final AuditDao auditDao;
    private final SubscriptionTimelineApi timelineApi;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public DefaultAuditUserApi(final AuditDao auditDao, final SubscriptionTimelineApi timelineApi, final InternalCallContextFactory internalCallContextFactory) {
        this.auditDao = auditDao;
        this.timelineApi = timelineApi;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public AuditLogsForAccount getAuditLogsForAccount(final UUID accountId, final AuditLevel auditLevel, final TenantContext context){
        return new DefaultAuditLogsForAccount(getAuditLogs(accountId, ObjectType.ACCOUNT, auditLevel, context));
    }

    @Override
    public AuditLogsForBundles getAuditLogsForBundle(final UUID bundleId, final AuditLevel auditLevel, final TenantContext context) throws SubscriptionRepairException {
        return getAuditLogsForBundles(ImmutableList.<BundleTimeline>of(timelineApi.getBundleTimeline(bundleId, context)), auditLevel, context);
    }

    @Override
    public AuditLogsForBundles getAuditLogsForBundles(final List<BundleTimeline> bundles, final AuditLevel auditLevel, final TenantContext context) {
        final Map<UUID, List<AuditLog>> bundlesAuditLogs = new HashMap<UUID, List<AuditLog>>();
        final Map<UUID, List<AuditLog>> subscriptionsAuditLogs = new HashMap<UUID, List<AuditLog>>();
        final Map<UUID, List<AuditLog>> subscriptionEventsAuditLogs = new HashMap<UUID, List<AuditLog>>();
        for (final BundleTimeline bundle : bundles) {
            bundlesAuditLogs.put(bundle.getId(), getAuditLogs(bundle.getId(), ObjectType.BUNDLE, auditLevel, context));
            for (final SubscriptionTimeline subscriptionTimeline : bundle.getSubscriptions()) {
                subscriptionsAuditLogs.put(subscriptionTimeline.getId(), getAuditLogs(subscriptionTimeline.getId(), ObjectType.SUBSCRIPTION, auditLevel, context));
                for (final ExistingEvent event : subscriptionTimeline.getExistingEvents()) {
                    subscriptionEventsAuditLogs.put(event.getEventId(), getAuditLogs(event.getEventId(), ObjectType.SUBSCRIPTION_EVENT, auditLevel, context));
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
