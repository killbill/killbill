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
import java.util.List;
import java.util.UUID;

import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;

import com.ning.billing.account.api.Account;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.osgi.bundles.analytics.AnalyticsRefreshException;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessAccountModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessModelDaoBase.ReportGroup;
import com.ning.billing.payment.api.Payment;
import com.ning.billing.util.audit.AuditLog;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillAPI;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillDataSource;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillLogService;

public class BusinessAccountDao extends BusinessAnalyticsDaoBase {

    public BusinessAccountDao(final OSGIKillbillLogService logService,
                              final OSGIKillbillAPI osgiKillbillAPI,
                              final OSGIKillbillDataSource osgiKillbillDataSource) {
        super(logService, osgiKillbillAPI, osgiKillbillDataSource);
    }

    public void update(final UUID accountId, final CallContext context) throws AnalyticsRefreshException {
        final Account account = getAccount(accountId, context);

        // Recompute the account record
        final BusinessAccountModelDao bac = createBusinessAccount(account, context);

        sqlDao.inTransaction(new Transaction<Void, BusinessAnalyticsSqlDao>() {
            @Override
            public Void inTransaction(final BusinessAnalyticsSqlDao transactional, final TransactionStatus status) throws Exception {
                updateInTransaction(bac, transactional, context);
                return null;
            }
        });
    }

    // Note: computing the BusinessAccountModelDao object is fairly expensive, hence should be done outside of the transaction
    public void updateInTransaction(final BusinessAccountModelDao bac, final BusinessAnalyticsSqlDao transactional, final CallContext context) {
        transactional.deleteByAccountRecordId(bac.getTableName(), bac.getAccountRecordId(), bac.getTenantRecordId(), context);
        transactional.create(bac.getTableName(), bac, context);
    }

    public BusinessAccountModelDao createBusinessAccount(final Account account, final CallContext context) throws AnalyticsRefreshException {
        // Retrieve the account creation audit log
        final AuditLog creationAuditLog = getAccountCreationAuditLog(account.getId(), context);

        // Retrieve the account balance
        // Note: since we retrieve the invoices below, we could compute it ourselves and avoid fetching the invoices
        // twice, but that way the computation logic is owned by invoice
        final BigDecimal accountBalance = getAccountBalance(account.getId(), context);

        // Retrieve invoices information
        Invoice lastInvoice = null;
        final Collection<Invoice> invoices = getInvoicesByAccountId(account.getId(), context);
        for (final Invoice invoice : invoices) {
            if (lastInvoice == null || invoice.getInvoiceDate().isAfter(lastInvoice.getInvoiceDate())) {
                lastInvoice = invoice;
            }
        }

        // Retrieve payments information
        Payment lastPayment = null;
        final Collection<Payment> payments = getPaymentsByAccountId(account.getId(), context);
        for (final Payment payment : payments) {
            if (lastPayment == null || payment.getEffectiveDate().isAfter(lastPayment.getEffectiveDate())) {
                lastPayment = payment;
            }
        }

        final List<SubscriptionBundle> bundles = getSubscriptionBundlesForAccount(account.getId(), context);
        int nbActiveBundles = 0;
        for (final SubscriptionBundle bundle : bundles) {
            final Collection<Subscription> subscriptionsForBundle = getSubscriptionsForBundle(bundle.getId(), context);
            for (final Subscription subscription : subscriptionsForBundle) {
                if (ProductCategory.BASE.equals(subscription.getCategory()) &&
                    !(subscription.getEndDate() != null && !subscription.getEndDate().isAfterNow())) {
                    nbActiveBundles++;
                }
            }
        }

        final Long accountRecordId = getAccountRecordId(account.getId(), context);
        final Long tenantRecordId = getTenantRecordId(context);
        final ReportGroup reportGroup = getReportGroup(account.getId(), context);

        return new BusinessAccountModelDao(account,
                                           accountRecordId,
                                           accountBalance,
                                           lastInvoice,
                                           lastPayment,
                                           nbActiveBundles,
                                           creationAuditLog,
                                           tenantRecordId,
                                           reportGroup);
    }
}
