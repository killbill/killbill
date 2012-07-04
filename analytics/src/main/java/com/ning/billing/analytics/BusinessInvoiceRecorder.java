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

package com.ning.billing.analytics;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.analytics.dao.BusinessAccountSqlDao;
import com.ning.billing.analytics.dao.BusinessInvoiceItemSqlDao;
import com.ning.billing.analytics.dao.BusinessInvoiceSqlDao;
import com.ning.billing.analytics.model.BusinessAccount;
import com.ning.billing.analytics.model.BusinessInvoice;
import com.ning.billing.analytics.model.BusinessInvoiceItem;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.util.clock.Clock;

public class BusinessInvoiceRecorder {
    private static final Logger log = LoggerFactory.getLogger(BusinessInvoiceRecorder.class);

    private final AccountUserApi accountApi;
    private final EntitlementUserApi entitlementApi;
    private final InvoiceUserApi invoiceApi;
    private final BusinessInvoiceSqlDao sqlDao;
    private final Clock clock;

    @Inject
    public BusinessInvoiceRecorder(final AccountUserApi accountApi,
                                   final EntitlementUserApi entitlementApi,
                                   final InvoiceUserApi invoiceApi,
                                   final BusinessInvoiceSqlDao sqlDao,
                                   final Clock clock) {
        this.accountApi = accountApi;
        this.entitlementApi = entitlementApi;
        this.invoiceApi = invoiceApi;
        this.sqlDao = sqlDao;
        this.clock = clock;
    }

    public void rebuildInvoicesForAccount(final UUID accountId) {
        // Lookup the associated account
        final String accountKey;
        try {
            final Account account = accountApi.getAccountById(accountId);
            accountKey = account.getExternalKey();
        } catch (AccountApiException e) {
            log.warn("Ignoring invoice update for account id {} (account does not exist)", accountId);
            return;
        }

        sqlDao.inTransaction(new Transaction<Void, BusinessInvoiceSqlDao>() {
            @Override
            public Void inTransaction(final BusinessInvoiceSqlDao transactional, final TransactionStatus status) throws Exception {
                log.info("Started rebuilding transitions for account id {}", accountId);
                deleteInvoicesAndInvoiceItemsForAccountInTransaction(transactional, accountKey);

                for (final Invoice invoice : invoiceApi.getInvoicesByAccount(accountId)) {
                    createInvoiceInTransaction(transactional, accountKey, invoice);
                }

                log.info("Finished rebuilding transitions for account id {}", accountId);
                return null;
            }
        });
    }

    private void deleteInvoicesAndInvoiceItemsForAccountInTransaction(final BusinessInvoiceSqlDao transactional, final String accountKey) {
        // We don't use on cascade delete here as we don't want the database layer to be generic - hence we have
        // to delete the invoice items manually.
        final List<BusinessInvoice> invoicesToDelete = transactional.getInvoicesForAccount(accountKey);
        final BusinessInvoiceItemSqlDao invoiceItemSqlDao = transactional.become(BusinessInvoiceItemSqlDao.class);
        for (final BusinessInvoice businessInvoice : invoicesToDelete) {
            final List<BusinessInvoiceItem> invoiceItemsForInvoice = invoiceItemSqlDao.getInvoiceItemsForInvoice(businessInvoice.getInvoiceId().toString());
            for (final BusinessInvoiceItem invoiceItemToDelete : invoiceItemsForInvoice) {
                invoiceItemSqlDao.deleteInvoiceItem(invoiceItemToDelete.getItemId().toString());
            }
        }

        transactional.deleteInvoicesForAccount(accountKey);
    }

    private void createInvoiceInTransaction(final BusinessInvoiceSqlDao transactional, final String accountKey, final Invoice invoice) {
        // Create the invoice
        final BusinessInvoice businessInvoice = new BusinessInvoice(accountKey, invoice);

        // Create associated invoice items
        final List<BusinessInvoiceItem> businessInvoiceItems = new ArrayList<BusinessInvoiceItem>();
        for (final InvoiceItem invoiceItem : invoice.getInvoiceItems()) {
            final BusinessInvoiceItem businessInvoiceItem = createBusinessInvoiceItem(invoiceItem);
            if (businessInvoiceItem != null) {
                businessInvoiceItems.add(businessInvoiceItem);
            }
        }

        createInvoiceInTransaction(transactional, invoice.getAccountId(), businessInvoice, businessInvoiceItems);
    }

    private void createInvoiceInTransaction(final BusinessInvoiceSqlDao transactional, final UUID accountId,
                                            final BusinessInvoice invoice, final Iterable<BusinessInvoiceItem> invoiceItems) {
        // Create the invoice
        log.info("Adding invoice {}", invoice);
        transactional.createInvoice(invoice);

        // Add associated invoice items
        final BusinessInvoiceItemSqlDao invoiceItemSqlDao = transactional.become(BusinessInvoiceItemSqlDao.class);
        for (final BusinessInvoiceItem invoiceItem : invoiceItems) {
            log.info("Adding invoice item {}", invoiceItem);
            invoiceItemSqlDao.createInvoiceItem(invoiceItem);
        }

        // Update BAC
        final BusinessAccountSqlDao accountSqlDao = transactional.become(BusinessAccountSqlDao.class);
        final BusinessAccount account = accountSqlDao.getAccount(accountId.toString());
        if (account == null) {
            throw new IllegalStateException("Account does not exist for id " + accountId);
        }
        account.setBalance(account.getBalance().add(invoice.getBalance()));
        account.setLastInvoiceDate(invoice.getInvoiceDate());
        account.setTotalInvoiceBalance(account.getTotalInvoiceBalance().add(invoice.getBalance()));
        account.setUpdatedDt(clock.getUTCNow());
        accountSqlDao.saveAccount(account);
    }

    private BusinessInvoiceItem createBusinessInvoiceItem(final InvoiceItem invoiceItem) {
        final String externalKey;
        try {
            final SubscriptionBundle bundle = entitlementApi.getBundleFromId(invoiceItem.getBundleId());
            externalKey = bundle.getKey();
        } catch (EntitlementUserApiException e) {
            log.warn("Ignoring invoice item {} for bundle {} (bundle does not exist)",
                     invoiceItem.getId().toString(),
                     invoiceItem.getBundleId().toString());
            return null;
        }

        final Subscription subscription;
        try {
            subscription = entitlementApi.getSubscriptionFromId(invoiceItem.getSubscriptionId());
        } catch (EntitlementUserApiException e) {
            log.warn("Ignoring invoice item {} for subscription {} (subscription does not exist)",
                     invoiceItem.getId().toString(),
                     invoiceItem.getSubscriptionId().toString());
            return null;
        }

        final Plan plan = subscription.getCurrentPlan();
        if (plan == null) {
            log.warn("Ignoring invoice item {} for subscription {} (null plan)",
                     invoiceItem.getId().toString(),
                     invoiceItem.getSubscriptionId().toString());
            return null;
        }

        final PlanPhase planPhase = subscription.getCurrentPhase();
        if (planPhase == null) {
            log.warn("Ignoring invoice item {} for subscription {} (null phase)",
                     invoiceItem.getId().toString(),
                     invoiceItem.getSubscriptionId().toString());
            return null;
        }

        return new BusinessInvoiceItem(externalKey, invoiceItem, plan, planPhase);
    }
}
