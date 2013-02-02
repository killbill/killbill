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

package com.ning.billing.analytics.dao;

import java.util.List;

import javax.inject.Inject;

import com.ning.billing.analytics.api.DefaultTimeSeriesData;
import com.ning.billing.analytics.api.TimeSeriesData;
import com.ning.billing.analytics.model.BusinessAccountModelDao;
import com.ning.billing.analytics.model.BusinessAccountTagModelDao;
import com.ning.billing.analytics.model.BusinessInvoiceItemModelDao;
import com.ning.billing.analytics.model.BusinessInvoiceModelDao;
import com.ning.billing.analytics.model.BusinessInvoicePaymentModelDao;
import com.ning.billing.analytics.model.BusinessOverdueStatusModelDao;
import com.ning.billing.analytics.model.BusinessSubscriptionTransitionModelDao;
import com.ning.billing.util.callcontext.InternalTenantContext;

public class DefaultAnalyticsDao implements AnalyticsDao {

    private final BusinessAccountSqlDao accountSqlDao;
    private final BusinessSubscriptionTransitionSqlDao subscriptionTransitionSqlDao;
    private final BusinessInvoiceSqlDao invoiceSqlDao;
    private final BusinessInvoiceItemSqlDao invoiceItemSqlDao;
    private final BusinessAccountTagSqlDao accountTagSqlDao;
    private final BusinessOverdueStatusSqlDao overdueStatusSqlDao;
    private final BusinessInvoicePaymentSqlDao invoicePaymentSqlDao;

    @Inject
    public DefaultAnalyticsDao(final BusinessAccountSqlDao accountSqlDao,
                               final BusinessSubscriptionTransitionSqlDao subscriptionTransitionSqlDao,
                               final BusinessInvoiceSqlDao invoiceSqlDao,
                               final BusinessInvoiceItemSqlDao invoiceItemSqlDao,
                               final BusinessAccountTagSqlDao accountTagSqlDao,
                               final BusinessOverdueStatusSqlDao overdueStatusSqlDao,
                               final BusinessInvoicePaymentSqlDao invoicePaymentSqlDao) {
        this.accountSqlDao = accountSqlDao;
        this.subscriptionTransitionSqlDao = subscriptionTransitionSqlDao;
        this.invoiceSqlDao = invoiceSqlDao;
        this.invoiceItemSqlDao = invoiceItemSqlDao;
        this.accountTagSqlDao = accountTagSqlDao;
        this.overdueStatusSqlDao = overdueStatusSqlDao;
        this.invoicePaymentSqlDao = invoicePaymentSqlDao;
    }

    @Override
    public TimeSeriesData getAccountsCreatedOverTime(final InternalTenantContext context) {
        return new DefaultTimeSeriesData(accountSqlDao.getAccountsCreatedOverTime(context));
    }

    @Override
    public TimeSeriesData getSubscriptionsCreatedOverTime(final String productType, final String slug, final InternalTenantContext context) {
        return new DefaultTimeSeriesData(subscriptionTransitionSqlDao.getSubscriptionsCreatedOverTime(productType, slug, context));
    }

    @Override
    public BusinessAccountModelDao getAccountByKey(final String accountKey, final InternalTenantContext context) {
        return accountSqlDao.getAccountByKey(accountKey, context);
    }

    @Override
    public List<BusinessSubscriptionTransitionModelDao> getTransitionsByKey(final String externalKey, final InternalTenantContext context) {
        return subscriptionTransitionSqlDao.getTransitionsByKey(externalKey, context);
    }

    @Override
    public List<BusinessSubscriptionTransitionModelDao> getTransitionsForAccount(final String accountKey, final InternalTenantContext context) {
        return subscriptionTransitionSqlDao.getTransitionsForAccount(accountKey, context);
    }

    @Override
    public List<BusinessInvoiceModelDao> getInvoicesByKey(final String accountKey, final InternalTenantContext context) {
        return invoiceSqlDao.getInvoicesForAccountByKey(accountKey, context);
    }

    @Override
    public List<BusinessAccountTagModelDao> getTagsForAccount(final String accountKey, final InternalTenantContext context) {
        return accountTagSqlDao.getTagsForAccountByKey(accountKey, context);
    }

    @Override
    public List<BusinessInvoiceItemModelDao> getInvoiceItemsForInvoice(final String invoiceId, final InternalTenantContext context) {
        return invoiceItemSqlDao.getInvoiceItemsForInvoice(invoiceId, context);
    }

    @Override
    public List<BusinessOverdueStatusModelDao> getOverdueStatusesForBundleByKey(final String externalKey, final InternalTenantContext context) {
        return overdueStatusSqlDao.getOverdueStatusesForBundleByKey(externalKey, context);
    }

    @Override
    public List<BusinessInvoicePaymentModelDao> getInvoicePaymentsForAccountByKey(final String accountKey, final InternalTenantContext context) {
        return invoicePaymentSqlDao.getInvoicePaymentsForAccountByKey(accountKey, context);
    }
}
