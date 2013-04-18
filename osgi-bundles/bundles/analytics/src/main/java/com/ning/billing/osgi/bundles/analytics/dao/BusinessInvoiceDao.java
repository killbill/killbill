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

package com.ning.billing.osgi.bundles.analytics.dao;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.osgi.service.log.LogService;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;

import com.ning.billing.account.api.Account;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.osgi.bundles.analytics.AnalyticsRefreshException;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessAccountModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceItemBaseModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceItemBaseModelDao.BusinessInvoiceItemType;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessModelDaoBase.ReportGroup;
import com.ning.billing.util.audit.AuditLog;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillAPI;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillDataSource;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillLogService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;

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
        final Long accountRecordId = getAccountRecordId(account.getId(), context);
        final Long tenantRecordId = getTenantRecordId(context);
        final ReportGroup reportGroup = getReportGroup(account.getId(), context);

        final Map<UUID, BusinessInvoiceModelDao> businessInvoices = new HashMap<UUID, BusinessInvoiceModelDao>();
        final Map<UUID, Collection<BusinessInvoiceItemBaseModelDao>> businessInvoiceItems = new HashMap<UUID, Collection<BusinessInvoiceItemBaseModelDao>>();

        // All invoice items across all invoices for that account
        // We need to be able to reference items across multiple invoices
        final Collection<InvoiceItem> allInvoiceItems = new LinkedList<InvoiceItem>();

        // Lookup the invoices for that account
        final Collection<Invoice> invoices = getInvoicesByAccountId(account.getId(), context);

        // Create a convenient mapping invoice_id -> invoice
        final Map<UUID, Invoice> invoiceIdToInvoiceMappings = new LinkedHashMap<UUID, Invoice>();

        // Create the business invoices
        for (final Invoice invoice : invoices) {
            invoiceIdToInvoiceMappings.put(invoice.getId(), invoice);
            allInvoiceItems.addAll(invoice.getInvoiceItems());

            final Long invoiceRecordId = getInvoiceRecordId(invoice.getId(), context);
            final AuditLog creationAuditLog = getInvoiceCreationAuditLog(invoice.getId(), context);
            final BusinessInvoiceModelDao businessInvoice = new BusinessInvoiceModelDao(account,
                                                                                        accountRecordId,
                                                                                        invoice,
                                                                                        invoiceRecordId,
                                                                                        creationAuditLog,
                                                                                        tenantRecordId,
                                                                                        reportGroup);

            businessInvoices.put(invoice.getId(), businessInvoice);
        }

        // Sanitize (cherry-pick, merge) the items
        final Collection<InvoiceItem> sanitizedInvoiceItems = sanitizeInvoiceItems(allInvoiceItems);

        // Create the business invoice items
        for (final InvoiceItem invoiceItem : sanitizedInvoiceItems) {
            final Invoice invoice = invoiceIdToInvoiceMappings.get(invoiceItem.getInvoiceId());
            final BusinessInvoiceItemBaseModelDao businessInvoiceItem = createBusinessInvoiceItem(account,
                                                                                                  invoice,
                                                                                                  invoiceItem,
                                                                                                  Collections2.filter(sanitizedInvoiceItems,
                                                                                                                      new Predicate<InvoiceItem>() {
                                                                                                                          @Override
                                                                                                                          public boolean apply(final InvoiceItem input) {
                                                                                                                              return !input.getId().equals(invoiceItem.getId());
                                                                                                                          }
                                                                                                                      }),
                                                                                                  accountRecordId,
                                                                                                  tenantRecordId,
                                                                                                  reportGroup,
                                                                                                  context);
            if (businessInvoiceItem != null) {
                if (businessInvoiceItems.get(invoice.getId()) == null) {
                    businessInvoiceItems.put(invoice.getId(), new LinkedList<BusinessInvoiceItemBaseModelDao>());
                }
                businessInvoiceItems.get(invoice.getId()).add(businessInvoiceItem);
            }
        }

        final Map<BusinessInvoiceModelDao, Collection<BusinessInvoiceItemBaseModelDao>> businessRecords = new HashMap<BusinessInvoiceModelDao, Collection<BusinessInvoiceItemBaseModelDao>>();
        for (final BusinessInvoiceModelDao businessInvoiceModelDao : businessInvoices.values()) {
            businessRecords.put(businessInvoiceModelDao, businessInvoiceItems.get(businessInvoiceModelDao.getInvoiceId()));
        }

        return businessRecords;
    }

    private void rebuildInvoicesForAccountInTransaction(final BusinessAccountModelDao account,
                                                        final Map<BusinessInvoiceModelDao, Collection<BusinessInvoiceItemBaseModelDao>> businessInvoices,
                                                        final BusinessAnalyticsSqlDao transactional,
                                                        final CallContext context) {
        deleteInvoicesAndInvoiceItemsForAccountInTransaction(transactional, account.getAccountRecordId(), account.getTenantRecordId(), context);

        for (final BusinessInvoiceModelDao businessInvoice : businessInvoices.keySet()) {
            final Collection<BusinessInvoiceItemBaseModelDao> invoiceItems = businessInvoices.get(businessInvoice);
            if (invoiceItems != null) {
                createInvoiceInTransaction(transactional, businessInvoice, invoiceItems, context);
            }
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

    private BusinessInvoiceItemBaseModelDao createBusinessInvoiceItem(final Account account,
                                                                      final Invoice invoice,
                                                                      final InvoiceItem invoiceItem,
                                                                      final Collection<InvoiceItem> otherInvoiceItems,
                                                                      final Long accountRecordId,
                                                                      final Long tenantRecordId,
                                                                      @Nullable final ReportGroup reportGroup,
                                                                      final TenantContext context) throws AnalyticsRefreshException {
        SubscriptionBundle bundle = null;
        // Subscription and bundle could be null for e.g. credits or adjustments
        if (invoiceItem.getBundleId() != null) {
            bundle = getSubscriptionBundle(invoiceItem.getBundleId(), context);
        }

        Plan plan = null;
        if (Strings.emptyToNull(invoiceItem.getPlanName()) != null) {
            plan = getPlanFromInvoiceItem(invoiceItem, context);
        }

        PlanPhase planPhase = null;
        if (invoiceItem.getSubscriptionId() != null && Strings.emptyToNull(invoiceItem.getPhaseName()) != null) {
            planPhase = getPlanPhaseFromInvoiceItem(invoiceItem, context);
        }

        final Long invoiceItemRecordId = getInvoiceItemRecordId(invoiceItem.getId(), context);
        final AuditLog creationAuditLog = getInvoiceItemCreationAuditLog(invoiceItem.getId(), context);

        return createBusinessInvoiceItem(account,
                                         invoice,
                                         invoiceItem,
                                         otherInvoiceItems,
                                         bundle,
                                         plan,
                                         planPhase,
                                         invoiceItemRecordId,
                                         creationAuditLog,
                                         accountRecordId,
                                         tenantRecordId,
                                         reportGroup,
                                         context);
    }

    @VisibleForTesting
    BusinessInvoiceItemBaseModelDao createBusinessInvoiceItem(final Account account,
                                                              final Invoice invoice,
                                                              final InvoiceItem invoiceItem,
                                                              final Collection<InvoiceItem> otherInvoiceItems,
                                                              @Nullable final SubscriptionBundle bundle,
                                                              @Nullable final Plan plan,
                                                              @Nullable final PlanPhase planPhase,
                                                              final Long invoiceItemRecordId,
                                                              final AuditLog creationAuditLog,
                                                              final Long accountRecordId,
                                                              final Long tenantRecordId,
                                                              final ReportGroup reportGroup,
                                                              final TenantContext context) throws AnalyticsRefreshException {
        final BusinessInvoiceItemType businessInvoiceItemType;
        if (isCharge(invoiceItem)) {
            businessInvoiceItemType = BusinessInvoiceItemType.CHARGE;
        } else if (isAccountCreditItem(invoiceItem)) {
            businessInvoiceItemType = BusinessInvoiceItemType.ACCOUNT_CREDIT;
        } else if (isInvoiceItemAdjustmentItem(invoiceItem)) {
            businessInvoiceItemType = BusinessInvoiceItemType.INVOICE_ITEM_ADJUSTMENT;
        } else if (isInvoiceAdjustmentItem(invoiceItem, otherInvoiceItems)) {
            businessInvoiceItemType = BusinessInvoiceItemType.INVOICE_ADJUSTMENT;
        } else {
            // We don't care
            return null;
        }

        final Boolean revenueRecognizable = isRevenueRecognizable(invoiceItem, otherInvoiceItems);

        final Long secondInvoiceItemRecordId;
        if (invoiceItem instanceof AdjustmentInvoiceItemForRepair) {
            secondInvoiceItemRecordId = getInvoiceItemRecordId(((AdjustmentInvoiceItemForRepair) invoiceItem).getSecondId(), context);
        } else {
            secondInvoiceItemRecordId = null;
        }

        return BusinessInvoiceItemBaseModelDao.create(account,
                                                      accountRecordId,
                                                      invoice,
                                                      invoiceItem,
                                                      revenueRecognizable,
                                                      businessInvoiceItemType,
                                                      invoiceItemRecordId,
                                                      secondInvoiceItemRecordId,
                                                      bundle,
                                                      plan,
                                                      planPhase,
                                                      creationAuditLog,
                                                      tenantRecordId,
                                                      reportGroup);
    }

    private Boolean isRevenueRecognizable(final InvoiceItem invoiceItem, final Collection<InvoiceItem> otherInvoiceItems) {
        // All items are recognizable except user generated credit (CBA_ADJ and CREDIT_ADJ on their own invoice)
        return !(InvoiceItemType.CBA_ADJ.equals(invoiceItem.getInvoiceItemType()) &&
                 (otherInvoiceItems.size() == 1 &&
                  InvoiceItemType.CREDIT_ADJ.equals(otherInvoiceItems.iterator().next().getInvoiceItemType()) &&
                  otherInvoiceItems.iterator().next().getInvoiceId().equals(invoiceItem.getInvoiceId()) &&
                  otherInvoiceItems.iterator().next().getAmount().compareTo(invoiceItem.getAmount().negate()) == 0));
    }

    // Invoice adjustments
    @VisibleForTesting
    boolean isInvoiceAdjustmentItem(final InvoiceItem invoiceItem, final Collection<InvoiceItem> otherInvoiceItems) {
        // Either REFUND_ADJ
        return InvoiceItemType.REFUND_ADJ.equals(invoiceItem.getInvoiceItemType()) ||
               // Or invoice level credit, i.e. credit adj, but NOT on its on own invoice
               // Note: the negative credit adj items (internal generation of account level credits) doesn't figure in analytics
               (InvoiceItemType.CREDIT_ADJ.equals(invoiceItem.getInvoiceItemType()) &&
                !(otherInvoiceItems.size() == 1 &&
                  InvoiceItemType.CBA_ADJ.equals(otherInvoiceItems.iterator().next().getInvoiceItemType()) &&
                  otherInvoiceItems.iterator().next().getInvoiceId().equals(invoiceItem.getInvoiceId()) &&
                  otherInvoiceItems.iterator().next().getAmount().compareTo(invoiceItem.getAmount().negate()) == 0));
    }

    // Item adjustments
    private boolean isInvoiceItemAdjustmentItem(final InvoiceItem invoiceItem) {
        return InvoiceItemType.ITEM_ADJ.equals(invoiceItem.getInvoiceItemType());
    }

    // Account credits, gained or consumed
    private boolean isAccountCreditItem(final InvoiceItem invoiceItem) {
        return InvoiceItemType.CBA_ADJ.equals(invoiceItem.getInvoiceItemType());
    }

    // Regular line item (charges)
    private boolean isCharge(final InvoiceItem invoiceItem) {
        return InvoiceItemType.EXTERNAL_CHARGE.equals(invoiceItem.getInvoiceItemType()) ||
               InvoiceItemType.FIXED.equals(invoiceItem.getInvoiceItemType()) ||
               InvoiceItemType.RECURRING.equals(invoiceItem.getInvoiceItemType());
    }

    @VisibleForTesting
    Collection<InvoiceItem> sanitizeInvoiceItems(final Collection<InvoiceItem> allInvoiceItems) {
        // Build a convenience mapping between items -> repair_adj items (inverse of linkedItemId)
        final Map<UUID, InvoiceItem> repairedInvoiceItemIdToRepairInvoiceItemMappings = new HashMap<UUID, InvoiceItem>();
        for (final InvoiceItem invoiceItem : allInvoiceItems) {
            if (InvoiceItemType.REPAIR_ADJ.equals(invoiceItem.getInvoiceItemType())) {
                repairedInvoiceItemIdToRepairInvoiceItemMappings.put(invoiceItem.getLinkedItemId(), invoiceItem);
            }
        }

        // Now find the "reparation" items, i.e. the ones which correspond to the repaired items
        final Map<UUID, InvoiceItem> reparationInvoiceItemIdToRepairItemMappings = new LinkedHashMap<UUID, InvoiceItem>();
        for (final InvoiceItem repairedInvoiceItem : allInvoiceItems) {
            // Skip non-repaired items
            if (!repairedInvoiceItemIdToRepairInvoiceItemMappings.keySet().contains(repairedInvoiceItem.getId())) {
                continue;
            }

            InvoiceItem reparationItem = null;
            for (final InvoiceItem invoiceItem : allInvoiceItems) {
                // Try to find the matching "reparation" item
                if (repairedInvoiceItem.getInvoiceItemType().equals(invoiceItem.getInvoiceItemType()) &&
                    repairedInvoiceItem.getSubscriptionId().equals(invoiceItem.getSubscriptionId()) &&
                    repairedInvoiceItem.getStartDate().compareTo(invoiceItem.getStartDate()) == 0 &&
                    // FIXED items have a null end date
                    ((repairedInvoiceItem.getEndDate() == null && invoiceItem.getEndDate() == null) ||
                     (repairedInvoiceItem.getEndDate() != null && invoiceItem.getEndDate() != null && !repairedInvoiceItem.getEndDate().isBefore(invoiceItem.getEndDate()))) &&
                    !repairedInvoiceItem.getId().equals(invoiceItem.getId())) {
                    if (reparationItem == null) {
                        reparationItem = invoiceItem;
                    } else {
                        logService.log(LogService.LOG_ERROR, "Found multiple reparation items matching the repair item id " + repairedInvoiceItem.getId() + " - this should never happen!");
                    }
                }
            }

            if (reparationItem != null) {
                reparationInvoiceItemIdToRepairItemMappings.put(reparationItem.getId(), repairedInvoiceItemIdToRepairInvoiceItemMappings.get(repairedInvoiceItem.getId()));
            } else {
                logService.log(LogService.LOG_ERROR, "Could not find the reparation item for the repair item id " + repairedInvoiceItem.getId() + " - this should never happen!");
            }
        }

        // Filter the invoice items for analytics
        final Collection<InvoiceItem> invoiceItemsForAnalytics = new LinkedList<InvoiceItem>();
        for (final InvoiceItem invoiceItem : allInvoiceItems) {
            if (InvoiceItemType.REPAIR_ADJ.equals(invoiceItem.getInvoiceItemType())) {
                // We don't care, we'll create a special item for it below
            } else if (reparationInvoiceItemIdToRepairItemMappings.keySet().contains(invoiceItem.getId())) {
                // We do care - this is a reparation item. Create an item adjustment for it
                final InvoiceItem repairInvoiceItem = reparationInvoiceItemIdToRepairItemMappings.get(invoiceItem.getId());
                final InvoiceItem reparationInvoiceItem = invoiceItem;
                invoiceItemsForAnalytics.add(new AdjustmentInvoiceItemForRepair(repairInvoiceItem, reparationInvoiceItem));
            } else {
                invoiceItemsForAnalytics.add(invoiceItem);
            }
        }

        return invoiceItemsForAnalytics;
    }

    private class AdjustmentInvoiceItemForRepair implements InvoiceItem {

        private final InvoiceItem repairInvoiceItem;
        private final InvoiceItem reparationInvoiceItem;

        private AdjustmentInvoiceItemForRepair(final InvoiceItem repairInvoiceItem,
                                               final InvoiceItem reparationInvoiceItem) {
            this.repairInvoiceItem = repairInvoiceItem;
            this.reparationInvoiceItem = reparationInvoiceItem;
        }

        @Override
        public InvoiceItemType getInvoiceItemType() {
            return InvoiceItemType.ITEM_ADJ;
        }

        @Override
        public UUID getInvoiceId() {
            return repairInvoiceItem.getInvoiceId();
        }

        @Override
        public UUID getAccountId() {
            return repairInvoiceItem.getAccountId();
        }

        @Override
        public LocalDate getStartDate() {
            return repairInvoiceItem.getStartDate();
        }

        @Override
        public LocalDate getEndDate() {
            return repairInvoiceItem.getStartDate();
        }

        @Override
        public BigDecimal getAmount() {
            return reparationInvoiceItem.getAmount().add(repairInvoiceItem.getAmount());
        }

        @Override
        public Currency getCurrency() {
            return repairInvoiceItem.getCurrency();
        }

        @Override
        public String getDescription() {
            return null;
        }

        @Override
        public UUID getBundleId() {
            return null;
        }

        @Override
        public UUID getSubscriptionId() {
            return null;
        }

        @Override
        public String getPlanName() {
            return null;
        }

        @Override
        public String getPhaseName() {
            return null;
        }

        @Override
        public BigDecimal getRate() {
            return null;
        }

        @Override
        public UUID getLinkedItemId() {
            return repairInvoiceItem.getLinkedItemId();
        }

        @Override
        public boolean matches(final Object other) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UUID getId() {
            // We pretend to be the repair, the reparation item record id
            // will be available as secondId
            return repairInvoiceItem.getId();
        }

        public UUID getSecondId() {
            return reparationInvoiceItem.getId();
        }

        @Override
        public DateTime getCreatedDate() {
            return repairInvoiceItem.getCreatedDate();
        }

        @Override
        public DateTime getUpdatedDate() {
            return repairInvoiceItem.getUpdatedDate();
        }
    }
}
