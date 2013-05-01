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

import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessAccountModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoicePaymentBaseModelDao;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillDataSource;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillLogService;

public class BusinessInvoicePaymentDao extends BusinessAnalyticsDaoBase {

    public BusinessInvoicePaymentDao(final OSGIKillbillLogService logService, final OSGIKillbillDataSource osgiKillbillDataSource) {
        super(logService, osgiKillbillDataSource);
    }

    /**
     * Delete all invoice payment records and insert the specified ones as current.
     *
     * @param bac                     current, fully populated, BusinessAccountModelDao record
     * @param businessInvoicePayments current, fully populated, mapping of invoice id -> BusinessInvoicePaymentBaseModelDao records
     * @param transactional           current transaction
     * @param context                 call context
     */
    public void updateInTransaction(final BusinessAccountModelDao bac,
                                    final Iterable<BusinessInvoicePaymentBaseModelDao> businessInvoicePayments,
                                    final BusinessAnalyticsSqlDao transactional,
                                    final CallContext context) {
        for (final String tableName : BusinessInvoicePaymentBaseModelDao.ALL_INVOICE_PAYMENTS_TABLE_NAMES) {
            transactional.deleteByAccountRecordId(tableName, bac.getAccountRecordId(), bac.getTenantRecordId(), context);
        }

        for (final BusinessInvoicePaymentBaseModelDao invoicePayment : businessInvoicePayments) {
            transactional.create(invoicePayment.getTableName(), invoicePayment, context);
        }

        // Invoice and payment details in BAC will be updated by BusinessInvoiceAndInvoicePaymentDao
    }
}
