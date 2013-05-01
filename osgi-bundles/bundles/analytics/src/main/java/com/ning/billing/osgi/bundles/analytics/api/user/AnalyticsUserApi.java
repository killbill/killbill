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

package com.ning.billing.osgi.bundles.analytics.api.user;

import java.util.Collection;
import java.util.UUID;

import com.ning.billing.osgi.bundles.analytics.AnalyticsRefreshException;
import com.ning.billing.osgi.bundles.analytics.api.BusinessAccount;
import com.ning.billing.osgi.bundles.analytics.api.BusinessField;
import com.ning.billing.osgi.bundles.analytics.api.BusinessInvoice;
import com.ning.billing.osgi.bundles.analytics.api.BusinessInvoicePayment;
import com.ning.billing.osgi.bundles.analytics.api.BusinessOverdueStatus;
import com.ning.billing.osgi.bundles.analytics.api.BusinessSnapshot;
import com.ning.billing.osgi.bundles.analytics.api.BusinessSubscriptionTransition;
import com.ning.billing.osgi.bundles.analytics.api.BusinessTag;
import com.ning.billing.osgi.bundles.analytics.dao.AllBusinessObjectsDao;
import com.ning.billing.osgi.bundles.analytics.dao.AnalyticsDao;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillAPI;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillDataSource;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillLogService;

public class AnalyticsUserApi {

    private final AnalyticsDao analyticsDao;
    private final AllBusinessObjectsDao allBusinessObjectsDao;

    public AnalyticsUserApi(final OSGIKillbillLogService logService,
                            final OSGIKillbillAPI osgiKillbillAPI,
                            final OSGIKillbillDataSource osgiKillbillDataSource) {
        this.analyticsDao = new AnalyticsDao(logService, osgiKillbillAPI, osgiKillbillDataSource);
        this.allBusinessObjectsDao = new AllBusinessObjectsDao(logService, osgiKillbillAPI, osgiKillbillDataSource);
    }

    public BusinessSnapshot getBusinessSnapshot(final UUID accountId, final TenantContext context) {
        // Find account
        final BusinessAccount businessAccount = analyticsDao.getAccountById(accountId, context);

        // Find all transitions for all bundles for that account, and associated overdue statuses
        final Collection<BusinessSubscriptionTransition> businessSubscriptionTransitions = analyticsDao.getSubscriptionTransitionsForAccount(accountId, context);
        final Collection<BusinessOverdueStatus> businessOverdueStatuses = analyticsDao.getOverdueStatusesForAccount(accountId, context);

        // Find all invoices for that account
        final Collection<BusinessInvoice> businessInvoices = analyticsDao.getInvoicesForAccount(accountId, context);

        // Find all payments for that account
        final Collection<BusinessInvoicePayment> businessInvoicePayments = analyticsDao.getInvoicePaymentsForAccount(accountId, context);

        // Find all tags for that account
        final Collection<BusinessTag> businessTags = analyticsDao.getTagsForAccount(accountId, context);

        // Find all fields for that account
        final Collection<BusinessField> businessFields = analyticsDao.getFieldsForAccount(accountId, context);

        return new BusinessSnapshot(businessAccount,
                                    businessSubscriptionTransitions,
                                    businessInvoices,
                                    businessInvoicePayments,
                                    businessOverdueStatuses,
                                    businessTags,
                                    businessFields);
    }

    public void rebuildAnalyticsForAccount(final UUID accountId, final CallContext context) throws AnalyticsRefreshException {
        // TODO Should we take the account lock?
        allBusinessObjectsDao.update(accountId, context);
    }
}
