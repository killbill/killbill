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

import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;

import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;

import com.ning.billing.account.api.Account;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.osgi.bundles.analytics.AnalyticsRefreshException;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessAccountModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceItemBaseModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoicePaymentBaseModelDao;
import com.ning.billing.payment.api.Payment;
import com.ning.billing.payment.api.PaymentMethod;
import com.ning.billing.util.audit.AuditLog;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillAPI;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillDataSource;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillLogService;

public class BusinessInvoicePaymentDao extends BusinessAnalyticsDaoBase {

    private final BusinessAccountDao businessAccountDao;
    private final BusinessInvoiceDao businessInvoiceDao;

    public BusinessInvoicePaymentDao(final OSGIKillbillLogService logService,
                                     final OSGIKillbillAPI osgiKillbillAPI,
                                     final OSGIKillbillDataSource osgiKillbillDataSource,
                                     final BusinessAccountDao businessAccountDao,
                                     final BusinessInvoiceDao businessInvoiceDao) {
        super(logService, osgiKillbillAPI, osgiKillbillDataSource);
        this.businessAccountDao = businessAccountDao;
        this.businessInvoiceDao = businessInvoiceDao;
    }

    public void update(final UUID accountId, final CallContext context) throws AnalyticsRefreshException {
        final Account account = getAccount(accountId, context);

        // Recompute the account record
        final BusinessAccountModelDao bac = businessAccountDao.createBusinessAccount(account, context);

        // Recompute all invoice payments
        final Collection<BusinessInvoicePaymentBaseModelDao> businessInvoicePayments = createBusinessInvoicePayments(account, context);

        // Recompute all invoice and invoice items
        final Map<BusinessInvoiceModelDao, Collection<BusinessInvoiceItemBaseModelDao>> businessInvoices = businessInvoiceDao.createBusinessInvoicesAndInvoiceItems(account, context);

        sqlDao.inTransaction(new Transaction<Void, BusinessAnalyticsSqlDao>() {
            @Override
            public Void inTransaction(final BusinessAnalyticsSqlDao transactional, final TransactionStatus status) throws Exception {
                updateInTransaction(bac, businessInvoices, businessInvoicePayments, transactional, context);
                return null;
            }
        });
    }

    private void updateInTransaction(final BusinessAccountModelDao bac,
                                     final Map<BusinessInvoiceModelDao, Collection<BusinessInvoiceItemBaseModelDao>> businessInvoices,
                                     final Collection<BusinessInvoicePaymentBaseModelDao> businessInvoicePayments,
                                     final BusinessAnalyticsSqlDao transactional,
                                     final CallContext context) throws AnalyticsRefreshException {
        rebuildInvoicePaymentsForAccountInTransaction(bac, businessInvoicePayments, transactional, context);

        // Update invoice balance details in BIN
        // Note: no need to explicitly update BAC as well, since BusinessInvoiceDao will take care of it
        businessInvoiceDao.updateInTransaction(bac, businessInvoices, transactional, context);
    }

    private void rebuildInvoicePaymentsForAccountInTransaction(final BusinessAccountModelDao bac,
                                                               final Collection<BusinessInvoicePaymentBaseModelDao> businessInvoicePayments,
                                                               final BusinessAnalyticsSqlDao transactional,
                                                               final CallContext context) throws AnalyticsRefreshException {
        for (final String tableName : BusinessInvoicePaymentBaseModelDao.ALL_INVOICE_PAYMENTS_TABLE_NAMES) {
            transactional.deleteByAccountRecordId(tableName, bac.getAccountRecordId(), bac.getTenantRecordId(), context);
        }

        for (final BusinessInvoicePaymentBaseModelDao invoicePayment : businessInvoicePayments) {
            transactional.create(invoicePayment.getTableName(), invoicePayment, context);
        }
    }

    private Collection<BusinessInvoicePaymentBaseModelDao> createBusinessInvoicePayments(final Account account,
                                                                                         final CallContext context) throws AnalyticsRefreshException {
        final Collection<BusinessInvoicePaymentBaseModelDao> businessInvoicePayments = new LinkedList<BusinessInvoicePaymentBaseModelDao>();

        final Collection<InvoicePayment> invoicePayments = getAccountInvoicePayments(account.getId(), context);
        for (final InvoicePayment invoicePayment : invoicePayments) {
            final Invoice invoice = getInvoice(invoicePayment.getInvoiceId(), context);
            final Payment payment = getPayment(invoicePayment.getPaymentId(), context);
            final PaymentMethod paymentMethod = getPaymentMethod(payment.getPaymentMethodId(), context);
            final AuditLog creationAuditLog = getInvoicePaymentCreationAuditLog(invoicePayment.getId(), context);

            final BusinessInvoicePaymentBaseModelDao businessInvoicePayment = BusinessInvoicePaymentBaseModelDao.create(account, invoice, invoicePayment, payment, paymentMethod, creationAuditLog);
            if (businessInvoicePayment != null) {
                businessInvoicePayments.add(businessInvoicePayment);
            }
        }

        return businessInvoicePayments;
    }
}
