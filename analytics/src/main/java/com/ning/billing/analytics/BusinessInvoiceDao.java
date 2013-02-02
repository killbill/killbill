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

package com.ning.billing.analytics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;

import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.analytics.dao.BusinessAccountSqlDao;
import com.ning.billing.analytics.dao.BusinessInvoiceItemSqlDao;
import com.ning.billing.analytics.dao.BusinessInvoiceSqlDao;
import com.ning.billing.analytics.model.BusinessAccountModelDao;
import com.ning.billing.analytics.model.BusinessInvoiceItemModelDao;
import com.ning.billing.analytics.model.BusinessInvoiceModelDao;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.svcapi.account.AccountInternalApi;
import com.ning.billing.util.svcapi.entitlement.EntitlementInternalApi;
import com.ning.billing.util.svcapi.invoice.InvoiceInternalApi;

import com.google.common.annotations.VisibleForTesting;

public class BusinessInvoiceDao {

    private static final Logger log = LoggerFactory.getLogger(BusinessInvoiceDao.class);

    private final AccountInternalApi accountApi;
    private final EntitlementInternalApi entitlementApi;
    private final InvoiceInternalApi invoiceApi;
    private final BusinessAccountDao businessAccountDao;
    private final BusinessInvoiceSqlDao sqlDao;
    private final CatalogService catalogService;

    @Inject
    public BusinessInvoiceDao(final AccountInternalApi accountApi,
                              final EntitlementInternalApi entitlementApi,
                              final InvoiceInternalApi invoiceApi,
                              final BusinessAccountDao businessAccountDao,
                              final BusinessInvoiceSqlDao sqlDao,
                              final CatalogService catalogService) {
        this.accountApi = accountApi;
        this.entitlementApi = entitlementApi;
        this.invoiceApi = invoiceApi;
        this.businessAccountDao = businessAccountDao;
        this.sqlDao = sqlDao;
        this.catalogService = catalogService;
    }

    public void rebuildInvoicesForAccount(final UUID accountId, final InternalCallContext context) {
        // Lookup the associated account
        final Account account;
        try {
            account = accountApi.getAccountById(accountId, context);
        } catch (AccountApiException e) {
            log.warn("Ignoring invoice update for account id {} (account does not exist)", accountId);
            return;
        }

        // Lookup the invoices for that account
        final Collection<Invoice> invoices = invoiceApi.getInvoicesByAccountId(account.getId(), context);

        // Create the business invoice and associated business invoice items
        final Map<BusinessInvoiceModelDao, Collection<BusinessInvoiceItemModelDao>> businessInvoices = new HashMap<BusinessInvoiceModelDao, Collection<BusinessInvoiceItemModelDao>>();
        for (final Invoice invoice : invoices) {
            final BusinessInvoiceModelDao businessInvoice = new BusinessInvoiceModelDao(account.getExternalKey(), invoice);

            final List<BusinessInvoiceItemModelDao> businessInvoiceItems = new ArrayList<BusinessInvoiceItemModelDao>();
            for (final InvoiceItem invoiceItem : invoice.getInvoiceItems()) {
                final BusinessInvoiceItemModelDao businessInvoiceItem = createBusinessInvoiceItem(invoiceItem, context);
                if (businessInvoiceItem != null) {
                    businessInvoiceItems.add(businessInvoiceItem);
                }
            }

            businessInvoices.put(businessInvoice, businessInvoiceItems);
        }

        // Update the account record
        final BusinessAccountModelDao bac = businessAccountDao.createBusinessAccountFromAccount(account, context);

        // Delete and recreate invoice and invoice items in the transaction
        sqlDao.inTransaction(new Transaction<Void, BusinessInvoiceSqlDao>() {
            @Override
            public Void inTransaction(final BusinessInvoiceSqlDao transactional, final TransactionStatus status) throws Exception {
                rebuildInvoicesForAccountInTransaction(account, businessInvoices, transactional, context);

                // Update balance, last invoice date and total invoice balance in BAC
                final BusinessAccountSqlDao accountSqlDao = transactional.become(BusinessAccountSqlDao.class);
                businessAccountDao.updateAccountInTransaction(bac, accountSqlDao, context);
                return null;
            }
        });
    }

    // Used by BIP Recorder
    public void rebuildInvoiceInTransaction(final String accountKey, final Invoice invoice,
                                            final BusinessInvoiceSqlDao transactional, final InternalCallContext context) {
        // Delete the invoice
        transactional.deleteInvoice(invoice.getId().toString(), context);

        // Re-create it - this will update the various amounts
        transactional.createInvoice(new BusinessInvoiceModelDao(accountKey, invoice), context);
    }

    private void rebuildInvoicesForAccountInTransaction(final Account account, final Map<BusinessInvoiceModelDao, Collection<BusinessInvoiceItemModelDao>> businessInvoices,
                                                        final BusinessInvoiceSqlDao transactional, final InternalCallContext context) {
        log.info("Started rebuilding invoices for account id {}", account.getId());
        deleteInvoicesAndInvoiceItemsForAccountInTransaction(transactional, account.getId(), context);

        for (final BusinessInvoiceModelDao businessInvoice : businessInvoices.keySet()) {
            createInvoiceInTransaction(transactional, businessInvoice, businessInvoices.get(businessInvoice), context);
        }

        log.info("Finished rebuilding invoices for account id {}", account.getId());
    }

    private void deleteInvoicesAndInvoiceItemsForAccountInTransaction(final BusinessInvoiceSqlDao transactional,
                                                                      final UUID accountId, final InternalCallContext context) {
        // We don't use on cascade delete here as we don't want the database layer to be generic - hence we have
        // to delete the invoice items manually.
        // Note: invoice items should go first (see query)
        final BusinessInvoiceItemSqlDao invoiceItemSqlDao = transactional.become(BusinessInvoiceItemSqlDao.class);
        log.info("Deleting invoice items for account {}", accountId);
        invoiceItemSqlDao.deleteInvoiceItemsForAccount(accountId.toString(), context);

        log.info("Deleting invoices for account {}", accountId);
        transactional.deleteInvoicesForAccount(accountId.toString(), context);
    }

    private void createInvoiceInTransaction(final BusinessInvoiceSqlDao transactional, final BusinessInvoiceModelDao invoice,
                                            final Iterable<BusinessInvoiceItemModelDao> invoiceItems, final InternalCallContext context) {
        // Create the invoice
        log.info("Adding invoice {}", invoice);
        transactional.createInvoice(invoice, context);

        // Add associated invoice items
        final BusinessInvoiceItemSqlDao invoiceItemSqlDao = transactional.become(BusinessInvoiceItemSqlDao.class);
        for (final BusinessInvoiceItemModelDao invoiceItem : invoiceItems) {
            log.info("Adding invoice item {}", invoiceItem);
            invoiceItemSqlDao.createInvoiceItem(invoiceItem, context);
        }
    }

    @VisibleForTesting
    BusinessInvoiceItemModelDao createBusinessInvoiceItem(final InvoiceItem invoiceItem, final InternalTenantContext context) {
        String externalKey = null;
        Plan plan = null;
        PlanPhase planPhase = null;

        // Subscription and bundle could be null for e.g. credits or adjustments
        if (invoiceItem.getBundleId() != null) {
            try {
                final SubscriptionBundle bundle = entitlementApi.getBundleFromId(invoiceItem.getBundleId(), context);
                externalKey = bundle.getExternalKey();
            } catch (EntitlementUserApiException e) {
                log.warn("Ignoring subscription fields for invoice item {} for bundle {} (bundle does not exist)",
                         invoiceItem.getId().toString(),
                         invoiceItem.getBundleId().toString());
            }
        }

        if (invoiceItem.getPlanName() != null) {
            try {
                plan = catalogService.getFullCatalog().findPlan(invoiceItem.getPlanName(), invoiceItem.getStartDate().toDateTimeAtStartOfDay());
            } catch (CatalogApiException e) {
                log.warn("Unable to retrieve plan for invoice item {}", invoiceItem.getId());
            }
        }

        if (invoiceItem.getSubscriptionId() != null && invoiceItem.getPhaseName() != null) {
            final Subscription subscription;
            try {
                subscription = entitlementApi.getSubscriptionFromId(invoiceItem.getSubscriptionId(), context);
                planPhase = catalogService.getFullCatalog().findPhase(invoiceItem.getPhaseName(), invoiceItem.getStartDate().toDateTimeAtStartOfDay(), subscription.getStartDate());
            } catch (EntitlementUserApiException e) {
                log.warn("Ignoring subscription fields for invoice item {} for subscription {} (subscription does not exist)",
                         invoiceItem.getId().toString(),
                         invoiceItem.getSubscriptionId().toString());
            } catch (CatalogApiException e) {
                log.warn("Unable to retrieve phase for invoice item {}", invoiceItem.getId());
            }
        }

        return new BusinessInvoiceItemModelDao(externalKey, invoiceItem, plan, planPhase);
    }
}
