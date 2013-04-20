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

import javax.annotation.Nullable;

import com.ning.billing.account.api.Account;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.osgi.bundles.analytics.AnalyticsRefreshException;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessAccountModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceItemBaseModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoicePaymentBaseModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessModelDaoBase.ReportGroup;
import com.ning.billing.payment.api.Payment;
import com.ning.billing.payment.api.PaymentMethod;
import com.ning.billing.payment.api.Refund;
import com.ning.billing.util.audit.AuditLog;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillAPI;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillDataSource;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillLogService;

public class BusinessInvoicePaymentDao extends BusinessAnalyticsDaoBase {

    public BusinessInvoicePaymentDao(final OSGIKillbillLogService logService,
                                     final OSGIKillbillAPI osgiKillbillAPI,
                                     final OSGIKillbillDataSource osgiKillbillDataSource) {
        super(logService, osgiKillbillAPI, osgiKillbillDataSource);
    }

    public void updateInTransaction(final BusinessAccountModelDao bac,
                                    final Collection<BusinessInvoicePaymentBaseModelDao> businessInvoicePayments,
                                    final BusinessAnalyticsSqlDao transactional,
                                    final CallContext context) throws AnalyticsRefreshException {
        rebuildInvoicePaymentsForAccountInTransaction(bac, businessInvoicePayments, transactional, context);
        // Invoice and payment details in BAC will be updated by BusinessInvoiceAndInvoicePaymentDao
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

    public Collection<BusinessInvoicePaymentBaseModelDao> createBusinessInvoicePayments(final Account account,
                                                                                        final Map<BusinessInvoiceModelDao, Collection<BusinessInvoiceItemBaseModelDao>> businessInvoices,
                                                                                        final CallContext context) throws AnalyticsRefreshException {
        final Collection<BusinessInvoicePaymentBaseModelDao> businessInvoicePayments = new LinkedList<BusinessInvoicePaymentBaseModelDao>();

        final Long accountRecordId = getAccountRecordId(account.getId(), context);
        final Long tenantRecordId = getTenantRecordId(context);
        final ReportGroup reportGroup = getReportGroup(account.getId(), context);

        final Collection<InvoicePayment> invoicePayments = getAccountInvoicePayments(account.getId(), context);
        for (final InvoicePayment invoicePayment : invoicePayments) {
            final BusinessInvoicePaymentBaseModelDao businessInvoicePayment = createBusinessInvoicePayment(account,
                                                                                                           invoicePayment,
                                                                                                           businessInvoices,
                                                                                                           accountRecordId,
                                                                                                           tenantRecordId,
                                                                                                           reportGroup,
                                                                                                           context);
            if (businessInvoicePayment != null) {
                businessInvoicePayments.add(businessInvoicePayment);
            }
        }

        return businessInvoicePayments;
    }

    private BusinessInvoicePaymentBaseModelDao createBusinessInvoicePayment(final Account account,
                                                                            final InvoicePayment invoicePayment,
                                                                            final Map<BusinessInvoiceModelDao, Collection<BusinessInvoiceItemBaseModelDao>> businessInvoices,
                                                                            final Long accountRecordId,
                                                                            final Long tenantRecordId,
                                                                            @Nullable final ReportGroup reportGroup,
                                                                            final CallContext context) throws AnalyticsRefreshException {
        final Long invoicePaymentRecordId = getInvoicePaymentRecordId(invoicePayment.getId(), context);

        final Payment payment = getPaymentWithPluginInfo(invoicePayment.getPaymentId(), context);
        Refund refund = null;
        if (invoicePayment.getPaymentCookieId() != null) {
            refund = getRefundWithPluginInfo(invoicePayment.getPaymentCookieId(), context);
        }

        final Invoice invoice = getInvoice(invoicePayment.getInvoiceId(), context);
        final PaymentMethod paymentMethod = getPaymentMethod(payment.getPaymentMethodId(), context);
        final AuditLog creationAuditLog = getInvoicePaymentCreationAuditLog(invoicePayment.getId(), context);

        return BusinessInvoicePaymentBaseModelDao.create(account,
                                                         accountRecordId,
                                                         invoice,
                                                         invoicePayment,
                                                         invoicePaymentRecordId,
                                                         payment,
                                                         refund,
                                                         paymentMethod,
                                                         creationAuditLog,
                                                         tenantRecordId,
                                                         reportGroup);
    }
}
