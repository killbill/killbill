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
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;

import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;

import com.ning.billing.account.api.Account;
import com.ning.billing.osgi.bundles.analytics.AnalyticsRefreshException;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessAccountModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceItemBaseModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoicePaymentBaseModelDao;
import com.ning.billing.osgi.bundles.analytics.utils.BusinessInvoiceUtils;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillAPI;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillDataSource;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillLogService;

import com.google.common.annotations.VisibleForTesting;

/**
 * Wrapper around BusinessInvoiceDao and BusinessInvoicePaymentDao.
 * <p/>
 * These two should always be updated together as invoice and payment information is denormalized across tables.
 */
public class BusinessInvoiceAndInvoicePaymentDao extends BusinessAnalyticsDaoBase {

    private final BusinessAccountDao businessAccountDao;
    private final BusinessInvoiceDao businessInvoiceDao;
    private final BusinessInvoicePaymentDao businessInvoicePaymentDao;

    public BusinessInvoiceAndInvoicePaymentDao(final OSGIKillbillLogService logService,
                                               final OSGIKillbillAPI osgiKillbillAPI,
                                               final OSGIKillbillDataSource osgiKillbillDataSource,
                                               final BusinessAccountDao businessAccountDao) {
        super(logService, osgiKillbillAPI, osgiKillbillDataSource);
        this.businessAccountDao = businessAccountDao;
        this.businessInvoiceDao = new BusinessInvoiceDao(logService, osgiKillbillAPI, osgiKillbillDataSource);
        this.businessInvoicePaymentDao = new BusinessInvoicePaymentDao(logService, osgiKillbillAPI, osgiKillbillDataSource);
    }

    public void update(final UUID accountId, final CallContext context) throws AnalyticsRefreshException {
        final Account account = getAccount(accountId, context);

        // Recompute the account record
        final BusinessAccountModelDao bac = businessAccountDao.createBusinessAccount(account, context);

        final Map<UUID, BusinessInvoiceModelDao> invoices = new HashMap<UUID, BusinessInvoiceModelDao>();
        final Map<UUID, Collection<BusinessInvoiceItemBaseModelDao>> invoiceItems = new HashMap<UUID, Collection<BusinessInvoiceItemBaseModelDao>>();
        final Map<UUID, Collection<BusinessInvoicePaymentBaseModelDao>> invoicePayments = new HashMap<UUID, Collection<BusinessInvoicePaymentBaseModelDao>>();
        createBusinessPojos(account, invoices, invoiceItems, invoicePayments, context);

        // Delete and recreate invoice and invoice items in the transaction
        sqlDao.inTransaction(new Transaction<Void, BusinessAnalyticsSqlDao>() {
            @Override
            public Void inTransaction(final BusinessAnalyticsSqlDao transactional, final TransactionStatus status) throws Exception {
                updateInTransaction(bac, invoices, invoiceItems, invoicePayments, transactional, context);
                return null;
            }
        });
    }

    @VisibleForTesting
    void createBusinessPojos(final Account account,
                             final Map<UUID, BusinessInvoiceModelDao> invoices,
                             final Map<UUID, Collection<BusinessInvoiceItemBaseModelDao>> invoiceItems,
                             final Map<UUID, Collection<BusinessInvoicePaymentBaseModelDao>> invoicePayments,
                             final CallContext context) throws AnalyticsRefreshException {
        // Recompute all invoices and invoice items. Invoices will have their denormalized payment fields missing,
        // and items won't have neither invoice nor payment denormalized fields populated
        final Map<BusinessInvoiceModelDao, Collection<BusinessInvoiceItemBaseModelDao>> businessInvoices = businessInvoiceDao.createBusinessInvoicesAndInvoiceItems(account, context);

        // Recompute all invoice payments (without denormalized payment fields populated)
        final Collection<BusinessInvoicePaymentBaseModelDao> businessInvoicePayments = businessInvoicePaymentDao.createBusinessInvoicePayments(account, businessInvoices, context);

        // Transform the results
        for (final BusinessInvoiceModelDao businessInvoice : businessInvoices.keySet()) {
            invoices.put(businessInvoice.getInvoiceId(), businessInvoice);
            for (final BusinessInvoiceItemBaseModelDao businessInvoiceItem : businessInvoices.get(businessInvoice)) {
                if (invoiceItems.get(businessInvoice.getInvoiceId()) == null) {
                    invoiceItems.put(businessInvoice.getInvoiceId(), new LinkedList<BusinessInvoiceItemBaseModelDao>());
                }
                invoiceItems.get(businessInvoice.getInvoiceId()).add(businessInvoiceItem);
            }
        }
        for (final BusinessInvoicePaymentBaseModelDao businessInvoicePayment : businessInvoicePayments) {
            if (invoicePayments.get(businessInvoicePayment.getInvoiceId()) == null) {
                invoicePayments.put(businessInvoicePayment.getInvoiceId(), new LinkedList<BusinessInvoicePaymentBaseModelDao>());
            }
            invoicePayments.get(businessInvoicePayment.getInvoiceId()).add(businessInvoicePayment);
        }

        // Populate missing fields
        populatedMissingDenormalizedFields(invoices, invoiceItems, invoicePayments);
    }

    private void populatedMissingDenormalizedFields(final Map<UUID, BusinessInvoiceModelDao> businessInvoices,
                                                    final Map<UUID, Collection<BusinessInvoiceItemBaseModelDao>> businessInvoiceItems,
                                                    final Map<UUID, Collection<BusinessInvoicePaymentBaseModelDao>> businessInvoicePayments) {
        // First, populated missing payment fields in invoice
        for (final BusinessInvoiceModelDao businessInvoice : businessInvoices.values()) {
            final BigDecimal balance = BusinessInvoiceUtils.computeInvoiceBalance(businessInvoiceItems.get(businessInvoice.getInvoiceId()),
                                                                                  businessInvoicePayments.get(businessInvoice.getInvoiceId()));
            businessInvoice.setBalance(balance);

            final BigDecimal amountPaid = BusinessInvoiceUtils.computeInvoiceAmountPaid(businessInvoicePayments.get(businessInvoice.getInvoiceId()));
            businessInvoice.setAmountPaid(amountPaid);

            final BigDecimal amountRefunded = BusinessInvoiceUtils.computeInvoiceAmountRefunded(businessInvoicePayments.get(businessInvoice.getInvoiceId()));
            businessInvoice.setAmountRefunded(amountRefunded);
        }

        // At this point, all of the invoice objects are fully populated. Use them to update the invoice items and payment objects
        for (final UUID invoiceId : businessInvoices.keySet()) {
            final Collection<BusinessInvoiceItemBaseModelDao> invoiceItemsForInvoice = businessInvoiceItems.get(invoiceId);
            if (invoiceItemsForInvoice != null) {
                for (final BusinessInvoiceItemBaseModelDao businessInvoiceItem : invoiceItemsForInvoice) {
                    businessInvoiceItem.populateDenormalizedInvoiceFields(businessInvoices.get(invoiceId));
                }
            }

            final Collection<BusinessInvoicePaymentBaseModelDao> invoicePaymentsForInvoice = businessInvoicePayments.get(invoiceId);
            if (invoicePaymentsForInvoice != null) {
                for (final BusinessInvoicePaymentBaseModelDao businessInvoicePayment : invoicePaymentsForInvoice) {
                    businessInvoicePayment.populateDenormalizedInvoiceFields(businessInvoices.get(invoiceId));
                }
            }
        }
    }

    private void updateInTransaction(final BusinessAccountModelDao bac,
                                     final Map<UUID, BusinessInvoiceModelDao> invoices,
                                     final Map<UUID, Collection<BusinessInvoiceItemBaseModelDao>> invoiceItems,
                                     final Map<UUID, Collection<BusinessInvoicePaymentBaseModelDao>> invoicePayments,
                                     final BusinessAnalyticsSqlDao transactional,
                                     final CallContext context) throws AnalyticsRefreshException {
        // Update invoice tables
        businessInvoiceDao.updateInTransaction(bac, invoices, invoiceItems, transactional, context);

        // Update invoice payment tables
        // TODO flatten function?
        final Collection<BusinessInvoicePaymentBaseModelDao> flattenedInvoicePayments = new LinkedList<BusinessInvoicePaymentBaseModelDao>();
        for (final UUID invoiceId : invoicePayments.keySet()) {
            flattenedInvoicePayments.addAll(invoicePayments.get(invoiceId));
        }
        businessInvoicePaymentDao.updateInTransaction(bac, flattenedInvoicePayments, transactional, context);

        // Update invoice and payment details in BAC
        businessAccountDao.updateInTransaction(bac, transactional, context);
    }
}
