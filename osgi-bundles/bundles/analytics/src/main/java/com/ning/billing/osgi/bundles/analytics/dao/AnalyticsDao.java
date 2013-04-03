/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"){} you may not use this file except in compliance with the
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

package com.ning.billing.osgi.bundles.analytics.dao

import java.util.List;

import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessAccountModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessAccountTagModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceItemModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoicePaymentModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessOverdueStatusModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessSubscriptionTransitionModelDao;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillAPI;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillDataSource;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillLogService;

public class AnalyticsDao extends BusinessAnalyticsDaoBase {

    public AnalyticsDao(final OSGIKillbillLogService logService,
                        final OSGIKillbillAPI osgiKillbillAPI,
                        final OSGIKillbillDataSource osgiKillbillDataSource) {
        super(logService, osgiKillbillAPI, osgiKillbillDataSource);
    }

    public BusinessAccountModelDao getAccountByKey(final String accountExternalKey, final TenantContext context) {
    }

    public List<BusinessSubscriptionTransitionModelDao> getSubscriptionTransitionsByBundleExternalKey(final String bundleExternalKey, final TenantContext context) {
    }

    public List<BusinessSubscriptionTransitionModelDao> getTransitionsForAccount(String accountKey, TenantContext context) {}

    public List<BusinessInvoiceModelDao> getInvoicesByAccountKey(String accountExternalKey, TenantContext context) {}

    public List<BusinessInvoiceItemModelDao> getInvoiceItemsForAccountKey(String accountExternalKey, TenantContext context) {}

    public List<BusinessInvoicePaymentModelDao> getInvoicePaymentsForAccountByKey(String accountExternalKey, TenantContext context) {}

    public List<BusinessOverdueStatusModelDao> getOverdueStatusesForBundleByExternalKey(String bundleExternalKey, TenantContext context) {}

    public List<BusinessAccountTagModelDao> getTagsForAccount(String accountKey, TenantContext context) {}
}
