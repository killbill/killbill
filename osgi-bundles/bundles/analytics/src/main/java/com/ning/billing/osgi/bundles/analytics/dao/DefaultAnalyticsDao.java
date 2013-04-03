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

import java.util.List;

import com.ning.billing.analytics.api.TimeSeriesData;
import com.ning.billing.osgi.bundles.analytics.model.BusinessAccountModelDao;
import com.ning.billing.osgi.bundles.analytics.model.BusinessAccountTagModelDao;
import com.ning.billing.osgi.bundles.analytics.model.BusinessInvoiceItemModelDao;
import com.ning.billing.osgi.bundles.analytics.model.BusinessInvoiceModelDao;
import com.ning.billing.osgi.bundles.analytics.model.BusinessInvoicePaymentModelDao;
import com.ning.billing.osgi.bundles.analytics.model.BusinessOverdueStatusModelDao;
import com.ning.billing.osgi.bundles.analytics.model.BusinessSubscriptionTransitionModelDao;
import com.ning.billing.util.callcontext.InternalTenantContext;

public class DefaultAnalyticsDao implements AnalyticsDao {

    @Override
    public TimeSeriesData getAccountsCreatedOverTime(final InternalTenantContext context) {
        return null;
    }

    @Override
    public TimeSeriesData getSubscriptionsCreatedOverTime(final String productType, final String slug, final InternalTenantContext context) {
        return null;
    }

    @Override
    public BusinessAccountModelDao getAccountByKey(final String accountKey, final InternalTenantContext context) {
        return null;
    }

    @Override
    public List<BusinessSubscriptionTransitionModelDao> getTransitionsByKey(final String externalKey, final InternalTenantContext context) {
        return null;
    }

    @Override
    public List<BusinessSubscriptionTransitionModelDao> getTransitionsForAccount(final String accountKey, final InternalTenantContext context) {
        return null;
    }

    @Override
    public List<BusinessInvoiceModelDao> getInvoicesByKey(final String accountKey, final InternalTenantContext context) {
        return null;
    }

    @Override
    public List<BusinessInvoiceItemModelDao> getInvoiceItemsForInvoice(final String invoiceId, final InternalTenantContext context) {
        return null;
    }

    @Override
    public List<BusinessInvoicePaymentModelDao> getInvoicePaymentsForAccountByKey(final String accountKey, final InternalTenantContext context) {
        return null;
    }

    @Override
    public List<BusinessOverdueStatusModelDao> getOverdueStatusesForBundleByKey(final String externalKey, final InternalTenantContext context) {
        return null;
    }

    @Override
    public List<BusinessAccountTagModelDao> getTagsForAccount(final String accountKey, final InternalTenantContext context) {
        return null;
    }
}
