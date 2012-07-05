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

package com.ning.billing.analytics.dao;

import javax.inject.Inject;
import java.util.List;

import com.ning.billing.analytics.model.BusinessAccount;
import com.ning.billing.analytics.model.BusinessAccountTag;
import com.ning.billing.analytics.model.BusinessInvoice;
import com.ning.billing.analytics.model.BusinessInvoiceItem;
import com.ning.billing.analytics.model.BusinessOverdueStatus;
import com.ning.billing.analytics.model.BusinessSubscriptionTransition;

public class DefaultAnalyticsDao implements AnalyticsDao {
    private final BusinessAccountSqlDao accountSqlDao;
    private final BusinessSubscriptionTransitionSqlDao subscriptionTransitionSqlDao;
    private final BusinessInvoiceSqlDao invoiceSqlDao;
    private final BusinessInvoiceItemSqlDao invoiceItemSqlDao;
    private final BusinessAccountTagSqlDao accountTagSqlDao;
    private final BusinessOverdueStatusSqlDao overdueStatusSqlDao;

    @Inject
    public DefaultAnalyticsDao(final BusinessAccountSqlDao accountSqlDao,
                               final BusinessSubscriptionTransitionSqlDao subscriptionTransitionSqlDao,
                               final BusinessInvoiceSqlDao invoiceSqlDao,
                               final BusinessInvoiceItemSqlDao invoiceItemSqlDao,
                               final BusinessAccountTagSqlDao accountTagSqlDao,
                               final BusinessOverdueStatusSqlDao overdueStatusSqlDao) {
        this.accountSqlDao = accountSqlDao;
        this.subscriptionTransitionSqlDao = subscriptionTransitionSqlDao;
        this.invoiceSqlDao = invoiceSqlDao;
        this.invoiceItemSqlDao = invoiceItemSqlDao;
        this.accountTagSqlDao = accountTagSqlDao;
        this.overdueStatusSqlDao = overdueStatusSqlDao;
    }

    @Override
    public BusinessAccount getAccountByKey(final String accountKey) {
        return accountSqlDao.getAccountByKey(accountKey);
    }

    @Override
    public List<BusinessSubscriptionTransition> getTransitionsByKey(final String externalKey) {
        return subscriptionTransitionSqlDao.getTransitionsByKey(externalKey);
    }

    @Override
    public List<BusinessInvoice> getInvoicesByKey(final String accountKey) {
        return invoiceSqlDao.getInvoicesForAccountByKey(accountKey);
    }

    @Override
    public List<BusinessAccountTag> getTagsForAccount(final String accountKey) {
        return accountTagSqlDao.getTagsForAccountByKey(accountKey);
    }

    @Override
    public List<BusinessInvoiceItem> getInvoiceItemsForInvoice(final String invoiceId) {
        return invoiceItemSqlDao.getInvoiceItemsForInvoice(invoiceId);
    }

    @Override
    public List<BusinessOverdueStatus> getOverdueStatusesForBundleByKey(final String externalKey) {
        return overdueStatusSqlDao.getOverdueStatusesForBundleByKey(externalKey);
    }
}
