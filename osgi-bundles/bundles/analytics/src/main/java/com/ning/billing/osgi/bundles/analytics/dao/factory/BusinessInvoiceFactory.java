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
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;

import static com.ning.billing.osgi.bundles.analytics.utils.BusinessInvoiceUtils.isAccountCreditItem;
import static com.ning.billing.osgi.bundles.analytics.utils.BusinessInvoiceUtils.isCharge;
import static com.ning.billing.osgi.bundles.analytics.utils.BusinessInvoiceUtils.isInvoiceAdjustmentItem;
import static com.ning.billing.osgi.bundles.analytics.utils.BusinessInvoiceUtils.isInvoiceItemAdjustmentItem;
import static com.ning.billing.osgi.bundles.analytics.utils.BusinessInvoiceUtils.isRepareeItemForRepairedItem;
import static com.ning.billing.osgi.bundles.analytics.utils.BusinessInvoiceUtils.isRevenueRecognizable;

public class BusinessInvoiceFactory extends BusinessFactoryBase {

    public BusinessInvoiceFactory(final OSGIKillbillLogService logService,
                                  final OSGIKillbillAPI osgiKillbillAPI) {
        super(logService, osgiKillbillAPI);
    }

    /**
     * Create current business invoices and invoice items.
     * <p/>
     * Note that these POJOs are incomplete (denormalized payment fields have not yet been populated, and denormalized
     * invoice fields in business invoice items have not been populated either).
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

        // All invoice items across all invoices for that account (we need to be able to reference items across multiple invoices)
        final Multimap<UUID, InvoiceItem> allInvoiceItems = ArrayListMultimap.<UUID, InvoiceItem>create();
        // Convenient mapping invoiceId -> invoice
        final Map<UUID, Invoice> invoiceIdToInvoiceMappings = new LinkedHashMap<UUID, Invoice>();
        for (final Invoice invoice : invoices) {
            invoiceIdToInvoiceMappings.put(invoice.getId(), invoice);
            allInvoiceItems.get(invoice.getId()).addAll(invoice.getInvoiceItems());
        }

        // *** MAGIC HAPPENS HERE ***
        // Sanitize (cherry-pick, merge) the items
        final Collection<InvoiceItem> sanitizedInvoiceItems = sanitizeInvoiceItems(allInvoiceItems);
        // *** MAGIC HAPPENS HERE ***

        // Create the business invoice items. These are incomplete: the denormalized invoice fields can't be computed yet,
        // since we need all business invoice items to do it.
        final Multimap<UUID, BusinessInvoiceItemBaseModelDao> businessInvoiceItemsForInvoiceId = ArrayListMultimap.<UUID, BusinessInvoiceItemBaseModelDao>create();
        for (final InvoiceItem invoiceItem : sanitizedInvoiceItems) {
            final Invoice invoice = invoiceIdToInvoiceMappings.get(invoiceItem.getInvoiceId());
            final Collection<InvoiceItem> otherInvoiceItems = Collections2.filter(sanitizedInvoiceItems,
                                                                                  new Predicate<InvoiceItem>() {
                                                                                      @Override
                                                                                      public boolean apply(final InvoiceItem input) {
                                                                                          return input.getId() != null && !input.getId().equals(invoiceItem.getId());
                                                                                      }
                                                                                  });
            final BusinessInvoiceItemBaseModelDao businessInvoiceItem = createBusinessInvoiceItem(account,
                                                                                                  invoice,
                                                                                                  invoiceItem,
                                                                                                  otherInvoiceItems,
                                                                                                  accountRecordId,
                                                                                                  tenantRecordId,
                                                                                                  reportGroup,
                                                                                                  context);
            if (businessInvoiceItem != null) {
                businessInvoiceItemsForInvoiceId.get(invoice.getId()).add(businessInvoiceItem);
            }
        }

        // Now, create the business invoices. We needed the final business invoice items to compute the various invoice amounts. At this point,
        // we could go back and populate the denormalized invoice amounts in the various items, but since we need to do a second pass later
        // to populate the denormalized payment fields, we'll hold off for now.
        final Map<BusinessInvoiceModelDao, Collection<BusinessInvoiceItemBaseModelDao>> businessRecords = new HashMap<BusinessInvoiceModelDao, Collection<BusinessInvoiceItemBaseModelDao>>();
        for (final Invoice invoice : invoices) {
            final Collection<BusinessInvoiceItemBaseModelDao> businessInvoiceItems = businessInvoiceItemsForInvoiceId.get(invoice.getId());
            if (businessInvoiceItems == null) {
                continue;
            }

            final BusinessInvoiceModelDao businessInvoice = createBusinessInvoice(account,
                                                                                  invoice,
                                                                                  businessInvoiceItems,
                                                                                  accountRecordId,
                                                                                  tenantRecordId,
                                                                                  reportGroup,
                                                                                  context);
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
        // For convenience, populate empty columns using the linked item
        final InvoiceItem linkedInvoiceItem = Iterables.find(otherInvoiceItems, new Predicate<InvoiceItem>() {
            @Override
            public boolean apply(final InvoiceItem input) {
                return invoiceItem.getLinkedItemId() != null && invoiceItem.getLinkedItemId().equals(input.getId());
            }
        }, null);

        SubscriptionBundle bundle = null;
        // Subscription and bundle could be null for e.g. credits or adjustments
        if (invoiceItem.getBundleId() != null) {
            bundle = getSubscriptionBundle(invoiceItem.getBundleId(), context);
        }
        if (bundle == null && linkedInvoiceItem != null && linkedInvoiceItem.getBundleId() != null) {
            bundle = getSubscriptionBundle(linkedInvoiceItem.getBundleId(), context);
        }

        Plan plan = null;
        if (Strings.emptyToNull(invoiceItem.getPlanName()) != null) {
            plan = getPlanFromInvoiceItem(invoiceItem, context);
        }
        if (plan == null && linkedInvoiceItem != null && Strings.emptyToNull(linkedInvoiceItem.getPlanName()) != null) {
            plan = getPlanFromInvoiceItem(linkedInvoiceItem, context);
        }

        PlanPhase planPhase = null;
        if (invoiceItem.getSubscriptionId() != null && Strings.emptyToNull(invoiceItem.getPhaseName()) != null) {
            planPhase = getPlanPhaseFromInvoiceItem(invoiceItem, context);
        }
        if (planPhase == null && linkedInvoiceItem != null && linkedInvoiceItem.getSubscriptionId() != null && Strings.emptyToNull(linkedInvoiceItem.getPhaseName()) != null) {
            planPhase = getPlanPhaseFromInvoiceItem(linkedInvoiceItem, context);
        }

        final Long invoiceItemRecordId = invoiceItem.getId() != null ? getInvoiceItemRecordId(invoiceItem.getId(), context) : null;
        final AuditLog creationAuditLog = invoiceItem.getId() != null ? getInvoiceItemCreationAuditLog(invoiceItem.getId(), context) : null;

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
     * merge REPAIR_ADJ items with reparation items (reparees) to create item adjustments
     * and merge CBA items.
     *
     * @param allInvoiceItems all items for the current account
     * @return invoice items interesting for Analytics purposes
     */
    @VisibleForTesting
    Collection<InvoiceItem> sanitizeInvoiceItems(final Multimap<UUID, InvoiceItem> allInvoiceItems) {
        // First, find all reparee items, to be able to merge REPAIR_ADJ and reparee items into ITEM_ADJ items
        final Map<InvoiceItem, InvoiceItem> repareeInvoiceItemToRepairItemMappings = findRepareeInvoiceItems(allInvoiceItems.values());

        // Second, since we are going to rebalance some items (the reparee items are going to be on a previous invoice),
        // we need to rebalance CBA_ADJ items. In order to simplify the process, we merge all items per invoice and revenueRecognizable
        // status (this information should be enough for financial and analytics reporting purposes).
        final Collection<AdjustedCBAInvoiceItem> mergedCBAItems = buildMergedCBAItems(allInvoiceItems, repareeInvoiceItemToRepairItemMappings);

        // Filter the invoice items for analytics
        final Collection<InvoiceItem> invoiceItemsForAnalytics = new LinkedList<InvoiceItem>();
        for (final InvoiceItem invoiceItem : allInvoiceItems.values()) {
            if (InvoiceItemType.CBA_ADJ.equals(invoiceItem.getInvoiceItemType())) {
                // We don't care, we'll merge them on all invoices
            } else if (InvoiceItemType.REPAIR_ADJ.equals(invoiceItem.getInvoiceItemType())) {
                // We don't care, we'll create a special item for it below
            } else if (repareeInvoiceItemToRepairItemMappings.keySet().contains(invoiceItem)) {
                // We do care - this is a reparation item. Create an item adjustment for it
                final InvoiceItem repairInvoiceItem = repareeInvoiceItemToRepairItemMappings.get(invoiceItem);
                final InvoiceItem repareeInvoiceItem = invoiceItem;
                invoiceItemsForAnalytics.add(new AdjustmentInvoiceItemForRepair(repairInvoiceItem, repareeInvoiceItem));
            } else {
                invoiceItemsForAnalytics.add(invoiceItem);
            }
        }
        invoiceItemsForAnalytics.addAll(mergedCBAItems);

        return invoiceItemsForAnalytics;
    }

    /**
     * Find all reparee items
     *
     * @param allInvoiceItems all invoice items, across all invoices
     * @return a mapping reparee invoice item id to REPAIR_ADJ item
     */
    @VisibleForTesting
    Map<InvoiceItem, InvoiceItem> findRepareeInvoiceItems(final Collection<InvoiceItem> allInvoiceItems) {
        // Build a convenience mapping between items -> repair_adj items (inverse of linkedItemId)
        final Map<UUID, InvoiceItem> repairedInvoiceItemIdToRepairInvoiceItemMappings = new HashMap<UUID, InvoiceItem>();
        for (final InvoiceItem invoiceItem : allInvoiceItems) {
            if (InvoiceItemType.REPAIR_ADJ.equals(invoiceItem.getInvoiceItemType())) {
                repairedInvoiceItemIdToRepairInvoiceItemMappings.put(invoiceItem.getLinkedItemId(), invoiceItem);
            }
        }

        // Now find the "reparee" items, i.e. the ones which correspond to the repaired items
        final Map<InvoiceItem, InvoiceItem> repareeInvoiceItemToRepairItemMappings = new LinkedHashMap<InvoiceItem, InvoiceItem>();
        for (final InvoiceItem repairedInvoiceItem : allInvoiceItems) {
            // Skip non-repaired items
            if (!repairedInvoiceItemIdToRepairInvoiceItemMappings.keySet().contains(repairedInvoiceItem.getId())) {
                continue;
            }

            InvoiceItem repareeItem = null;
            for (final InvoiceItem invoiceItem : allInvoiceItems) {
                // Try to find the matching "reparee" item
                if (isRepareeItemForRepairedItem(repairedInvoiceItem, invoiceItem)) {
                    if (repareeItem == null) {
                        repareeItem = invoiceItem;
                    } else {
                        logService.log(LogService.LOG_ERROR, "Found multiple reparee items matching the repair item id " + repairedInvoiceItem.getId() + " - this should never happen!");
                    }
                }
            }

            if (repareeItem != null) {
                repareeInvoiceItemToRepairItemMappings.put(repareeItem, repairedInvoiceItemIdToRepairInvoiceItemMappings.get(repairedInvoiceItem.getId()));
            } else {
                logService.log(LogService.LOG_ERROR, "Could not find the reparee item for the repair item id " + repairedInvoiceItem.getId() + " - this should never happen!");
            }
        }

        return repareeInvoiceItemToRepairItemMappings;
    }

    @VisibleForTesting
    Collection<AdjustedCBAInvoiceItem> buildMergedCBAItems(final Multimap<UUID, InvoiceItem> allInvoiceItems,
                                                           final Map<InvoiceItem, InvoiceItem> repareeInvoiceItemToRepairItemMappings) {
        // Build a black list of invoices for which we should not adjust the CBA when rebalancing the reparee item.
        final Set<UUID> repairInvoiceBlackList = new HashSet<UUID>();
        for (final InvoiceItem repairInvoiceItem : repareeInvoiceItemToRepairItemMappings.values()) {
            boolean shouldBlackList = true;
            // Only adjust CBA items on invoices having CBA already. Otherwise, don't do anything (e.g. for unpaid repaired invoices).
            for (final InvoiceItem invoiceItem : allInvoiceItems.get(repairInvoiceItem.getInvoiceId())) {
                if (invoiceItem.getInvoiceItemType().equals(InvoiceItemType.CBA_ADJ) &&
                    invoiceItem.getAmount().compareTo(repairInvoiceItem.getAmount().negate()) == 0) {
                    shouldBlackList = false;
                }
            }
            if (shouldBlackList) {
                // We can't blacklist the reparee invoice because it might have been repaired...
                repairInvoiceBlackList.add(repairInvoiceItem.getInvoiceId());
            }
        }

        final Map<UUID, BigDecimal> cbaAdjustmentPerInvoice = new HashMap<UUID, BigDecimal>();

        // Adjust the CBAs in case of repair.
        // On the original invoice (with the repair item), we need to substract the amount of the reparee to the CBA amount.
        // On the new invoice (with the reparee item), we need to add the amount of the reparee to the CBA amount.
        for (final InvoiceItem repareeInvoiceItem : repareeInvoiceItemToRepairItemMappings.keySet()) {
            // The repair item was on the original invoice
            final InvoiceItem repairInvoiceItem = repareeInvoiceItemToRepairItemMappings.get(repareeInvoiceItem);
            if (repairInvoiceBlackList.contains(repairInvoiceItem.getInvoiceId())) {
                continue;
            }

            if (cbaAdjustmentPerInvoice.get(repairInvoiceItem.getInvoiceId()) == null) {
                cbaAdjustmentPerInvoice.put(repairInvoiceItem.getInvoiceId(), BigDecimal.ZERO);
            }
            final BigDecimal currentCBAForOriginalInvoice = cbaAdjustmentPerInvoice.get(repairInvoiceItem.getInvoiceId());
            final BigDecimal adjustedCBAForOriginalInvoice = currentCBAForOriginalInvoice.add(repareeInvoiceItem.getAmount().negate());
            cbaAdjustmentPerInvoice.put(repairInvoiceItem.getInvoiceId(), adjustedCBAForOriginalInvoice);

            // The reparee item was on a new invoice
            if (cbaAdjustmentPerInvoice.get(repareeInvoiceItem.getInvoiceId()) == null) {
                cbaAdjustmentPerInvoice.put(repareeInvoiceItem.getInvoiceId(), BigDecimal.ZERO);
            }
            final BigDecimal currentCBAForNewInvoice = cbaAdjustmentPerInvoice.get(repareeInvoiceItem.getInvoiceId());
            final BigDecimal adjustedCBAForNewInvoice = currentCBAForNewInvoice.add(repareeInvoiceItem.getAmount());
            cbaAdjustmentPerInvoice.put(repareeInvoiceItem.getInvoiceId(), adjustedCBAForNewInvoice);
        }

        // Now, we just combine the other CBA items
        final Collection<AdjustedCBAInvoiceItem> mergedCBAs = new LinkedList<AdjustedCBAInvoiceItem>();
        for (final UUID invoiceId : allInvoiceItems.keySet()) {
            if (allInvoiceItems.get(invoiceId) == null) {
                continue;
            }

            final Collection<InvoiceItem> cbaItemsForInvoice = Collections2.filter(allInvoiceItems.get(invoiceId), new Predicate<InvoiceItem>() {
                @Override
                public boolean apply(final InvoiceItem invoiceItem) {
                    return InvoiceItemType.CBA_ADJ.equals(invoiceItem.getInvoiceItemType());
                }
            });
            if (cbaItemsForInvoice.size() == 0) {
                continue;
            }

            BigDecimal revenueRecognizableCBA = cbaAdjustmentPerInvoice.get(invoiceId);
            if (revenueRecognizableCBA == null) {
                revenueRecognizableCBA = BigDecimal.ZERO;
            }
            BigDecimal nonRevenueRecognizableCBA = BigDecimal.ZERO;

            UUID accountId = null;
            Currency currency = null;
            for (final InvoiceItem invoiceItem : cbaItemsForInvoice) {
                final Collection<InvoiceItem> otherInvoiceItems = Collections2.filter(allInvoiceItems.values(),
                                                                                      new Predicate<InvoiceItem>() {
                                                                                          @Override
                                                                                          public boolean apply(final InvoiceItem input) {
                                                                                              return !input.getId().equals(invoiceItem.getId());
                                                                                          }
                                                                                      });
                final Boolean isRevenueRecognizable = isRevenueRecognizable(invoiceItem, otherInvoiceItems);
                if (isRevenueRecognizable) {
                    revenueRecognizableCBA = revenueRecognizableCBA.add(invoiceItem.getAmount());
                } else {
                    nonRevenueRecognizableCBA = nonRevenueRecognizableCBA.add(invoiceItem.getAmount());
                }

                // These should be the same across all items
                if (accountId == null) {
                    accountId = invoiceItem.getAccountId();
                }
                if (currency == null) {
                    currency = invoiceItem.getCurrency();
                }
            }

            if (revenueRecognizableCBA.compareTo(BigDecimal.ZERO) != 0) {
                mergedCBAs.add(new AdjustedCBAInvoiceItem(invoiceId, accountId, revenueRecognizableCBA, currency));
            }
            if (nonRevenueRecognizableCBA.compareTo(BigDecimal.ZERO) != 0) {
                mergedCBAs.add(new AdjustedCBAInvoiceItem(invoiceId, accountId, nonRevenueRecognizableCBA, currency));
            }
        }

        return mergedCBAs;
    }
}
