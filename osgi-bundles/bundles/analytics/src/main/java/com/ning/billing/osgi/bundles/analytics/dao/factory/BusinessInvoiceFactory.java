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

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;

import javax.annotation.Nullable;

import com.ning.billing.account.api.Account;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.osgi.bundles.analytics.AnalyticsRefreshException;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceItemBaseModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceItemBaseModelDao.BusinessInvoiceItemType;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceItemBaseModelDao.ItemSource;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessModelDaoBase.ReportGroup;
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
import static com.ning.billing.osgi.bundles.analytics.utils.BusinessInvoiceUtils.isRevenueRecognizable;

public class BusinessInvoiceFactory extends BusinessFactoryBase {

    private static final int NB_THREADS = 20;

    private final Executor executor;

    public BusinessInvoiceFactory(final OSGIKillbillLogService logService,
                                  final OSGIKillbillAPI osgiKillbillAPI) {
        super(logService, osgiKillbillAPI);
        executor = Executors.newFixedThreadPool(NB_THREADS);
    }

    /**
     * Create current business invoices and invoice items.
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

        // Create the business invoice items
        // We build them in parallel as invoice items are directly proportional to subscriptions (@see BusinessSubscriptionTransitionFactory)
        final CompletionService<BusinessInvoiceItemBaseModelDao> completionService = new ExecutorCompletionService<BusinessInvoiceItemBaseModelDao>(executor);
        final Multimap<UUID, BusinessInvoiceItemBaseModelDao> businessInvoiceItemsForInvoiceId = ArrayListMultimap.<UUID, BusinessInvoiceItemBaseModelDao>create();
        for (final InvoiceItem invoiceItem : allInvoiceItems.values()) {
            completionService.submit(new Callable<BusinessInvoiceItemBaseModelDao>() {
                @Override
                public BusinessInvoiceItemBaseModelDao call() throws Exception {
                    return createBusinessInvoiceItem(invoiceItem,
                                                     allInvoiceItems,
                                                     invoiceIdToInvoiceMappings,
                                                     account,
                                                     accountRecordId,
                                                     tenantRecordId,
                                                     reportGroup,
                                                     context);
                }
            });
        }
        for (int i = 0; i < allInvoiceItems.values().size(); ++i) {
            try {
                final BusinessInvoiceItemBaseModelDao businessInvoiceItemModelDao = completionService.take().get();
                if (businessInvoiceItemModelDao != null) {
                    businessInvoiceItemsForInvoiceId.get(businessInvoiceItemModelDao.getInvoiceId()).add(businessInvoiceItemModelDao);
                }
            } catch (InterruptedException e) {
                throw new AnalyticsRefreshException(e);
            } catch (ExecutionException e) {
                throw new AnalyticsRefreshException(e);
            }
        }

        // Now, create the business invoices
        final Map<BusinessInvoiceModelDao, Collection<BusinessInvoiceItemBaseModelDao>> businessRecords = new HashMap<BusinessInvoiceModelDao, Collection<BusinessInvoiceItemBaseModelDao>>();
        for (final Invoice invoice : invoices) {
            final Collection<BusinessInvoiceItemBaseModelDao> businessInvoiceItems = businessInvoiceItemsForInvoiceId.get(invoice.getId());
            if (businessInvoiceItems == null) {
                continue;
            }

            final BusinessInvoiceModelDao businessInvoice = createBusinessInvoice(account,
                                                                                  invoice,
                                                                                  accountRecordId,
                                                                                  tenantRecordId,
                                                                                  reportGroup,
                                                                                  context);
            businessRecords.put(businessInvoice, businessInvoiceItems);
        }

        return businessRecords;
    }

    private BusinessInvoiceItemBaseModelDao createBusinessInvoiceItem(final InvoiceItem invoiceItem,
                                                                      final Multimap<UUID, InvoiceItem> allInvoiceItems,
                                                                      final Map<UUID, Invoice> invoiceIdToInvoiceMappings,
                                                                      final Account account,
                                                                      final Long accountRecordId,
                                                                      final Long tenantRecordId,
                                                                      final ReportGroup reportGroup,
                                                                      final CallContext context) throws AnalyticsRefreshException {
        final Invoice invoice = invoiceIdToInvoiceMappings.get(invoiceItem.getInvoiceId());
        final Collection<InvoiceItem> otherInvoiceItems = Collections2.filter(allInvoiceItems.values(),
                                                                              new Predicate<InvoiceItem>() {
                                                                                  @Override
                                                                                  public boolean apply(final InvoiceItem input) {
                                                                                      return input.getId() != null && !input.getId().equals(invoiceItem.getId());
                                                                                  }
                                                                              });
        return createBusinessInvoiceItem(account,
                                         invoice,
                                         invoiceItem,
                                         otherInvoiceItems,
                                         accountRecordId,
                                         tenantRecordId,
                                         reportGroup,
                                         context);
    }

    private BusinessInvoiceModelDao createBusinessInvoice(final Account account,
                                                          final Invoice invoice,
                                                          final Long accountRecordId,
                                                          final Long tenantRecordId,
                                                          @Nullable final ReportGroup reportGroup,
                                                          final CallContext context) throws AnalyticsRefreshException {
        final Long invoiceRecordId = getInvoiceRecordId(invoice.getId(), context);
        final AuditLog creationAuditLog = getInvoiceCreationAuditLog(invoice.getId(), context);

        return new BusinessInvoiceModelDao(account,
                                           accountRecordId,
                                           invoice,
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
                                         reportGroup);
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
                                                              final ReportGroup reportGroup) throws AnalyticsRefreshException {
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

        final ItemSource itemSource = getItemSource(invoiceItem, otherInvoiceItems, businessInvoiceItemType);

        // Unused for now
        final Long secondInvoiceItemRecordId = null;

        return BusinessInvoiceItemBaseModelDao.create(account,
                                                      accountRecordId,
                                                      invoice,
                                                      invoiceItem,
                                                      itemSource,
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

    private ItemSource getItemSource(final InvoiceItem invoiceItem, final Collection<InvoiceItem> otherInvoiceItems, final BusinessInvoiceItemType businessInvoiceItemType) {
        final ItemSource itemSource;
        if (BusinessInvoiceItemType.ACCOUNT_CREDIT.equals(businessInvoiceItemType) && !isRevenueRecognizable(invoiceItem, otherInvoiceItems)) {
            // Non recognizable account credits
            itemSource = ItemSource.user;
        } else if (BusinessInvoiceItemType.INVOICE_ADJUSTMENT.equals(businessInvoiceItemType)) {
            // Invoice adjustments
            itemSource = ItemSource.user;
        } else if (BusinessInvoiceItemType.INVOICE_ITEM_ADJUSTMENT.equals(businessInvoiceItemType) && !InvoiceItemType.REPAIR_ADJ.equals(invoiceItem.getInvoiceItemType())) {
            // Item adjustments (but not repairs)
            itemSource = ItemSource.user;
        } else if (BusinessInvoiceItemType.CHARGE.equals(businessInvoiceItemType) && InvoiceItemType.EXTERNAL_CHARGE.equals(invoiceItem.getInvoiceItemType())) {
            // External charges
            itemSource = ItemSource.user;
        } else {
            // System generated item
            itemSource = null;
        }

        return itemSource;
    }
}
