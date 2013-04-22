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

package com.ning.billing.osgi.bundles.analytics.dao.factory;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.osgi.service.log.LogService;

import com.ning.billing.account.api.Account;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.osgi.bundles.analytics.AnalyticsRefreshException;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceItemBaseModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceItemBaseModelDao.BusinessInvoiceItemType;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessModelDaoBase.ReportGroup;
import com.ning.billing.osgi.bundles.analytics.utils.BusinessInvoiceUtils;
import com.ning.billing.util.audit.AuditLog;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillAPI;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillLogService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;

import static com.ning.billing.osgi.bundles.analytics.utils.BusinessInvoiceUtils.isAccountCreditItem;
import static com.ning.billing.osgi.bundles.analytics.utils.BusinessInvoiceUtils.isCharge;
import static com.ning.billing.osgi.bundles.analytics.utils.BusinessInvoiceUtils.isInvoiceAdjustmentItem;
import static com.ning.billing.osgi.bundles.analytics.utils.BusinessInvoiceUtils.isInvoiceItemAdjustmentItem;
import static com.ning.billing.osgi.bundles.analytics.utils.BusinessInvoiceUtils.isRevenueRecognizable;

public class BusinessInvoiceFactory extends BusinessFactoryBase {

    public BusinessInvoiceFactory(final OSGIKillbillLogService logService,
                                  final OSGIKillbillAPI osgiKillbillAPI) {
        super(logService, osgiKillbillAPI);
    }

    /**
     * Create business invoices and invoice items to record. Note that these POJOs are incomplete
     * (denormalized payment fields have not yet been populated)
     *
     * @param accountId current accountId refreshed
     * @param context   call context
     * @return all business invoice and invoice items to create
     * @throws com.ning.billing.osgi.bundles.analytics.AnalyticsRefreshException
     *
     */
    public Map<BusinessInvoiceModelDao, Collection<BusinessInvoiceItemBaseModelDao>> createBusinessInvoicesAndInvoiceItems(final UUID accountId,
                                                                                                                           final CallContext context) throws AnalyticsRefreshException {
        final Account account = getAccount(accountId, context);
        final Long accountRecordId = getAccountRecordId(account.getId(), context);
        final Long tenantRecordId = getTenantRecordId(context);
        final ReportGroup reportGroup = getReportGroup(account.getId(), context);

        // Lookup the invoices for that account
        final Collection<Invoice> invoices = getInvoicesByAccountId(account.getId(), context);

        // All invoice items across all invoices for that accounr (we need to be able to reference items across multiple invoices)
        final Collection<InvoiceItem> allInvoiceItems = new LinkedList<InvoiceItem>();
        // Convenient mapping invoice_id -> invoice
        final Map<UUID, Invoice> invoiceIdToInvoiceMappings = new LinkedHashMap<UUID, Invoice>();
        for (final Invoice invoice : invoices) {
            invoiceIdToInvoiceMappings.put(invoice.getId(), invoice);
            allInvoiceItems.addAll(invoice.getInvoiceItems());
        }

        // Sanitize (cherry-pick, merge) the items
        final Collection<InvoiceItem> sanitizedInvoiceItems = sanitizeInvoiceItems(allInvoiceItems);

        // Create the business invoice items. These are incomplete (the denormalized invoice fields haven't been computed yet)
        final Map<UUID, Collection<BusinessInvoiceItemBaseModelDao>> businessInvoiceItemsForInvoiceId = new HashMap<UUID, Collection<BusinessInvoiceItemBaseModelDao>>();
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
                if (businessInvoiceItemsForInvoiceId.get(invoice.getId()) == null) {
                    businessInvoiceItemsForInvoiceId.put(invoice.getId(), new LinkedList<BusinessInvoiceItemBaseModelDao>());
                }
                businessInvoiceItemsForInvoiceId.get(invoice.getId()).add(businessInvoiceItem);
            }
        }

        // Now, create the business invoices
        final Map<BusinessInvoiceModelDao, Collection<BusinessInvoiceItemBaseModelDao>> businessRecords = new HashMap<BusinessInvoiceModelDao, Collection<BusinessInvoiceItemBaseModelDao>>();
        for (final Invoice invoice : invoices) {
            final Collection<BusinessInvoiceItemBaseModelDao> businessInvoiceItems = businessInvoiceItemsForInvoiceId.get(invoice.getId());
            if (businessInvoiceItems == null) {
                continue;
            }

            final BusinessInvoiceModelDao businessInvoice = createBusinessInvoice(account, invoice, businessInvoiceItems, accountRecordId, tenantRecordId, reportGroup, context);
            businessRecords.put(businessInvoice, businessInvoiceItems);
        }

        return businessRecords;
    }

    private BusinessInvoiceModelDao createBusinessInvoice(final Account account,
                                                          final Invoice invoice,
                                                          final Collection<BusinessInvoiceItemBaseModelDao> businessInvoiceItems,
                                                          final Long accountRecordId,
                                                          final Long tenantRecordId,
                                                          @Nullable final ReportGroup reportGroup,
                                                          final CallContext context) throws AnalyticsRefreshException {
        final Long invoiceRecordId = getInvoiceRecordId(invoice.getId(), context);
        final AuditLog creationAuditLog = getInvoiceCreationAuditLog(invoice.getId(), context);

        final BigDecimal amountCharged = BusinessInvoiceUtils.computeInvoiceAmountCharged(businessInvoiceItems);
        final BigDecimal originalAmountCharged = BusinessInvoiceUtils.computeInvoiceOriginalAmountCharged(businessInvoiceItems);
        final BigDecimal amountCredited = BusinessInvoiceUtils.computeInvoiceAmountCredited(businessInvoiceItems);

        return new BusinessInvoiceModelDao(account,
                                           accountRecordId,
                                           invoice,
                                           amountCharged,
                                           originalAmountCharged,
                                           amountCredited,
                                           invoiceRecordId,
                                           creationAuditLog,
                                           tenantRecordId,
                                           reportGroup);
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

    /**
     * Filter and transform the original invoice items for Analytics purposes. We mainly
     * merge REPAIR_ADJ items with reparation items (reparees) to create item adjustments.
     *
     * @param allInvoiceItems all items for the current account
     * @return invoice items interesting for Analytics purposes
     */
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

        // We now need to adjust the CBA_ADJ for the repair items
        final Set<UUID> cbasToIgnore = new HashSet<UUID>();
        final Collection<AdjustedCBAInvoiceItem> newCbasToAdd = new LinkedList<AdjustedCBAInvoiceItem>();
        for (final InvoiceItem cbaInvoiceItem : allInvoiceItems) {
            if (!InvoiceItemType.CBA_ADJ.equals(cbaInvoiceItem.getInvoiceItemType())) {
                continue;
            }

            for (final InvoiceItem invoiceItem : allInvoiceItems) {
                if (reparationInvoiceItemIdToRepairItemMappings.keySet().contains(invoiceItem.getId())) {
                    final InvoiceItem repairInvoiceItem = reparationInvoiceItemIdToRepairItemMappings.get(invoiceItem.getId());
                    final InvoiceItem reparationInvoiceItem = invoiceItem;
                    // Au petit bonheur la chance... There is nothing else against to compare
                    if (repairInvoiceItem.getAmount().negate().compareTo(cbaInvoiceItem.getAmount()) == 0) {
                        cbasToIgnore.add(cbaInvoiceItem.getId());
                        newCbasToAdd.add(new AdjustedCBAInvoiceItem(cbaInvoiceItem, cbaInvoiceItem.getAmount().add(reparationInvoiceItem.getAmount().negate()), reparationInvoiceItem.getId()));

                        // Now, fiddle with the CBA used on the reparation invoice
                        for (final InvoiceItem cbaUsedOnNextInvoiceItem : allInvoiceItems) {
                            if (!InvoiceItemType.CBA_ADJ.equals(cbaUsedOnNextInvoiceItem.getInvoiceItemType()) ||
                                !cbaUsedOnNextInvoiceItem.getInvoiceId().equals(reparationInvoiceItem.getInvoiceId())) {
                                continue;
                            }

                            // Au petit bonheur la chance... There is nothing else against to compare. Take the first one again?
                            cbasToIgnore.add(cbaUsedOnNextInvoiceItem.getId());
                            newCbasToAdd.add(new AdjustedCBAInvoiceItem(cbaUsedOnNextInvoiceItem, cbaUsedOnNextInvoiceItem.getAmount().add(reparationInvoiceItem.getAmount()), reparationInvoiceItem.getId()));
                            break;
                        }

                        // Break from the inner loop only
                        break;
                    }
                }
            }
        }


        // Filter the invoice items for analytics
        final Collection<InvoiceItem> invoiceItemsForAnalytics = new LinkedList<InvoiceItem>();
        for (final InvoiceItem invoiceItem : allInvoiceItems) {
            if (cbasToIgnore.contains(invoiceItem.getId())) {
                // We don't care
            } else if (InvoiceItemType.REPAIR_ADJ.equals(invoiceItem.getInvoiceItemType())) {
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
        invoiceItemsForAnalytics.addAll(newCbasToAdd);

        return invoiceItemsForAnalytics;
    }

    private class AdjustedCBAInvoiceItem implements InvoiceItem {

        private final InvoiceItem cbaInvoiceItem;
        private final BigDecimal amount;
        private final UUID reparationItemId;

        private AdjustedCBAInvoiceItem(final InvoiceItem cbaInvoiceItem,
                                       final BigDecimal amount,
                                       final UUID reparationItemId) {
            this.cbaInvoiceItem = cbaInvoiceItem;
            this.amount = amount;
            this.reparationItemId = reparationItemId;
        }

        @Override
        public InvoiceItemType getInvoiceItemType() {
            return InvoiceItemType.CBA_ADJ;
        }

        @Override
        public UUID getInvoiceId() {
            return cbaInvoiceItem.getInvoiceId();
        }

        @Override
        public UUID getAccountId() {
            return cbaInvoiceItem.getAccountId();
        }

        @Override
        public LocalDate getStartDate() {
            return cbaInvoiceItem.getStartDate();
        }

        @Override
        public LocalDate getEndDate() {
            return cbaInvoiceItem.getStartDate();
        }

        @Override
        public BigDecimal getAmount() {
            return amount;
        }

        @Override
        public Currency getCurrency() {
            return cbaInvoiceItem.getCurrency();
        }

        @Override
        public String getDescription() {
            return cbaInvoiceItem.getDescription();
        }

        @Override
        public UUID getBundleId() {
            return cbaInvoiceItem.getBundleId();
        }

        @Override
        public UUID getSubscriptionId() {
            return cbaInvoiceItem.getSubscriptionId();
        }

        @Override
        public String getPlanName() {
            return cbaInvoiceItem.getPlanName();
        }

        @Override
        public String getPhaseName() {
            return cbaInvoiceItem.getPhaseName();
        }

        @Override
        public BigDecimal getRate() {
            return cbaInvoiceItem.getRate();
        }

        @Override
        public UUID getLinkedItemId() {
            return cbaInvoiceItem.getLinkedItemId();
        }

        @Override
        public boolean matches(final Object other) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UUID getId() {
            return cbaInvoiceItem.getId();
        }

        public UUID getSecondId() {
            return reparationItemId;
        }

        @Override
        public DateTime getCreatedDate() {
            return cbaInvoiceItem.getCreatedDate();
        }

        @Override
        public DateTime getUpdatedDate() {
            return cbaInvoiceItem.getUpdatedDate();
        }
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
