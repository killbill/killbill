/*
 * Copyright 2010-2013 Ning, Inc.
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

package com.ning.billing.osgi.bundles.analytics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;

import com.ning.billing.account.api.Account;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.osgi.bundles.analytics.dao.BusinessAnalyticsSqlDao;
import com.ning.billing.osgi.bundles.analytics.model.BusinessAccountModelDao;
import com.ning.billing.osgi.bundles.analytics.model.BusinessInvoiceItemBaseModelDao;
import com.ning.billing.osgi.bundles.analytics.model.BusinessInvoiceModelDao;
import com.ning.billing.util.audit.AuditLog;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillAPI;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillDataSource;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillLogService;

import com.google.common.annotations.VisibleForTesting;

public class BusinessInvoiceDao extends BusinessAnalyticsDaoBase {

    private final BusinessAccountDao businessAccountDao;

    public BusinessInvoiceDao(final OSGIKillbillLogService logService,
                              final OSGIKillbillAPI osgiKillbillAPI,
                              final OSGIKillbillDataSource osgiKillbillDataSource,
                              final BusinessAccountDao businessAccountDao) {
        super(logService, osgiKillbillAPI, osgiKillbillDataSource);
        this.businessAccountDao = businessAccountDao;
    }

    public void update(final UUID accountId, final CallContext context) throws AnalyticsRefreshException {
        final Account account = getAccount(accountId, context);

        // Recompute the account record
        final BusinessAccountModelDao bac = businessAccountDao.createBusinessAccount(account, context);

        // Recompute all invoices and invoice items
        final Map<BusinessInvoiceModelDao, Collection<BusinessInvoiceItemBaseModelDao>> businessInvoices = createBusinessInvoicesAndInvoiceItems(account, context);

        // Delete and recreate invoice and invoice items in the transaction
        sqlDao.inTransaction(new Transaction<Void, BusinessAnalyticsSqlDao>() {
            @Override
            public Void inTransaction(final BusinessAnalyticsSqlDao transactional, final TransactionStatus status) throws Exception {
                updateInTransaction(bac, businessInvoices, transactional, context);
                return null;
            }
        });
    }

    public void updateInTransaction(final BusinessAccountModelDao bac,
                                    final Map<BusinessInvoiceModelDao, Collection<BusinessInvoiceItemBaseModelDao>> businessInvoices,
                                    final BusinessAnalyticsSqlDao transactional,
                                    final CallContext context) throws AnalyticsRefreshException {
        rebuildInvoicesForAccountInTransaction(bac, businessInvoices, transactional, context);

        // Update invoice and payment details in BAC
        businessAccountDao.updateInTransaction(bac, transactional, context);
    }

    public Map<BusinessInvoiceModelDao, Collection<BusinessInvoiceItemBaseModelDao>> createBusinessInvoicesAndInvoiceItems(final Account account,
                                                                                                                           final CallContext context) throws AnalyticsRefreshException {
        // Lookup the invoices for that account
        final Map<BusinessInvoiceModelDao, Collection<BusinessInvoiceItemBaseModelDao>> businessInvoices = new HashMap<BusinessInvoiceModelDao, Collection<BusinessInvoiceItemBaseModelDao>>();

        // Create the business invoice and associated business invoice items
        final Collection<Invoice> invoices = getInvoicesByAccountId(account.getId(), context);
        for (final Invoice invoice : invoices) {
            final AuditLog creationAuditLog = getInvoiceCreationAuditLog(invoice.getId(), context);
            final BusinessInvoiceModelDao businessInvoice = new BusinessInvoiceModelDao(account, invoice, creationAuditLog);

            final List<BusinessInvoiceItemBaseModelDao> businessInvoiceItems = new ArrayList<BusinessInvoiceItemBaseModelDao>();
            for (final InvoiceItem invoiceItem : invoice.getInvoiceItems()) {
                final BusinessInvoiceItemBaseModelDao businessInvoiceItem = createBusinessInvoiceItem(account, invoice, invoiceItem, context);
                if (businessInvoiceItem != null) {
                    businessInvoiceItems.add(businessInvoiceItem);
                }
            }

            businessInvoices.put(businessInvoice, businessInvoiceItems);
        }

        return businessInvoices;
    }

    private void rebuildInvoicesForAccountInTransaction(final BusinessAccountModelDao account,
                                                        final Map<BusinessInvoiceModelDao, Collection<BusinessInvoiceItemBaseModelDao>> businessInvoices,
                                                        final BusinessAnalyticsSqlDao transactional,
                                                        final CallContext context) {
        deleteInvoicesAndInvoiceItemsForAccountInTransaction(transactional, account.getAccountRecordId(), account.getTenantRecordId(), context);

        for (final BusinessInvoiceModelDao businessInvoice : businessInvoices.keySet()) {
            createInvoiceInTransaction(transactional, businessInvoice, businessInvoices.get(businessInvoice), context);
        }
    }

    private void deleteInvoicesAndInvoiceItemsForAccountInTransaction(final BusinessAnalyticsSqlDao transactional,
                                                                      final Long accountRecordId,
                                                                      final Long tenantRecordId,
                                                                      final CallContext context) {
        // Delete all invoice items
        for (final String tableName : BusinessInvoiceItemBaseModelDao.ALL_INVOICE_ITEMS_TABLE_NAMES) {
            transactional.deleteByAccountRecordId(tableName, accountRecordId, tenantRecordId, context);
        }

        // Delete all invoices
        transactional.deleteByAccountRecordId(BusinessInvoiceModelDao.INVOICES_TABLE_NAME, accountRecordId, tenantRecordId, context);
    }

    private void createInvoiceInTransaction(final BusinessAnalyticsSqlDao transactional,
                                            final BusinessInvoiceModelDao invoice,
                                            final Iterable<BusinessInvoiceItemBaseModelDao> invoiceItems,
                                            final CallContext context) {
        // Create the invoice
        transactional.create(invoice.getTableName(), invoice, context);

        // Add associated invoice items
        for (final BusinessInvoiceItemBaseModelDao invoiceItem : invoiceItems) {
            transactional.create(invoiceItem.getTableName(), invoiceItem, context);
        }
    }

    @VisibleForTesting
    BusinessInvoiceItemBaseModelDao createBusinessInvoiceItem(final Account account,
                                                              final Invoice invoice,
                                                              final InvoiceItem invoiceItem,
                                                              final TenantContext context) throws AnalyticsRefreshException {
        SubscriptionBundle bundle = null;
        // Subscription and bundle could be null for e.g. credits or adjustments
        if (invoiceItem.getBundleId() != null) {
            bundle = getSubscriptionBundle(invoiceItem.getBundleId(), context);
        }

        Plan plan = null;
        if (invoiceItem.getPlanName() != null) {
            plan = getPlanFromInvoiceItem(invoiceItem, context);
        }

        PlanPhase planPhase = null;
        if (invoiceItem.getSubscriptionId() != null && invoiceItem.getPhaseName() != null) {
            planPhase = getPlanPhaseFromInvoiceItem(invoiceItem, context);
        }

        final AuditLog creationAuditLog = getInvoiceItemCreationAuditLog(invoiceItem.getId(), context);

        return BusinessInvoiceItemBaseModelDao.create(account, invoice, invoiceItem, bundle, plan, planPhase, creationAuditLog);
    }
}
