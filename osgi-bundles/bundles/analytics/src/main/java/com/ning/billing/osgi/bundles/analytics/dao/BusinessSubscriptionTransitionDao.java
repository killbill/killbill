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
import java.util.UUID;
import java.util.concurrent.Executor;

import org.osgi.service.log.LogService;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;

import com.ning.billing.osgi.bundles.analytics.AnalyticsRefreshException;
import com.ning.billing.osgi.bundles.analytics.dao.factory.BusinessAccountFactory;
import com.ning.billing.osgi.bundles.analytics.dao.factory.BusinessBundleSummaryFactory;
import com.ning.billing.osgi.bundles.analytics.dao.factory.BusinessSubscriptionTransitionFactory;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessAccountModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessBundleSummaryModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessSubscriptionTransitionModelDao;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillAPI;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillDataSource;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillLogService;

public class BusinessSubscriptionTransitionDao extends BusinessAnalyticsDaoBase {

    private final BusinessAccountDao businessAccountDao;
    private final BusinessBundleSummaryDao businessBundleSummaryDao;
    private final BusinessAccountFactory bacFactory;
    private final BusinessBundleSummaryFactory bbsFactory;
    private final BusinessSubscriptionTransitionFactory bstFactory;

    public BusinessSubscriptionTransitionDao(final OSGIKillbillLogService logService,
                                             final OSGIKillbillAPI osgiKillbillAPI,
                                             final OSGIKillbillDataSource osgiKillbillDataSource,
                                             final BusinessAccountDao businessAccountDao,
                                             final Executor executor) {
        super(logService, osgiKillbillDataSource);
        this.businessAccountDao = businessAccountDao;
        this.businessBundleSummaryDao = new BusinessBundleSummaryDao(logService, osgiKillbillDataSource);
        bacFactory = new BusinessAccountFactory(logService, osgiKillbillAPI, executor);
        bbsFactory = new BusinessBundleSummaryFactory(logService, osgiKillbillAPI, executor);
        bstFactory = new BusinessSubscriptionTransitionFactory(logService, osgiKillbillAPI, executor);
    }

    public void update(final UUID accountId, final CallContext context) throws AnalyticsRefreshException {
        logService.log(LogService.LOG_INFO, "Starting rebuild of Analytics subscriptions for account " + accountId);

        // Recompute the account record
        final BusinessAccountModelDao bac = bacFactory.createBusinessAccount(accountId, context);

        // Recompute all invoices and invoice items
        final Collection<BusinessSubscriptionTransitionModelDao> bsts = bstFactory.createBusinessSubscriptionTransitions(accountId,
                                                                                                                         bac.getAccountRecordId(),
                                                                                                                         bac.getTenantRecordId(),
                                                                                                                         context);

        // Recompute the bundle summary records
        final Collection<BusinessBundleSummaryModelDao> bbss = bbsFactory.createBusinessBundleSummaries(accountId,
                                                                                                        bac.getAccountRecordId(),
                                                                                                        bsts,
                                                                                                        bac.getTenantRecordId(),
                                                                                                        context);
        sqlDao.inTransaction(new Transaction<Void, BusinessAnalyticsSqlDao>() {
            @Override
            public Void inTransaction(final BusinessAnalyticsSqlDao transactional, final TransactionStatus status) throws Exception {
                updateInTransaction(bac, bbss, bsts, transactional, context);
                return null;
            }
        });

        logService.log(LogService.LOG_INFO, "Finished rebuild of Analytics subscriptions for account " + accountId);
    }

    private void updateInTransaction(final BusinessAccountModelDao bac,
                                     final Collection<BusinessBundleSummaryModelDao> bbss,
                                     final Collection<BusinessSubscriptionTransitionModelDao> bsts,
                                     final BusinessAnalyticsSqlDao transactional,
                                     final CallContext context) {
        // Update the subscription transitions
        transactional.deleteByAccountRecordId(BusinessSubscriptionTransitionModelDao.SUBSCRIPTION_TABLE_NAME,
                                              bac.getAccountRecordId(),
                                              bac.getTenantRecordId(),
                                              context);

        for (final BusinessSubscriptionTransitionModelDao bst : bsts) {
            transactional.create(bst.getTableName(), bst, context);
        }

        // Update the summary table per bundle
        businessBundleSummaryDao.updateInTransaction(bbss,
                                                     bac.getAccountRecordId(),
                                                     bac.getTenantRecordId(),
                                                     transactional,
                                                     context);

        // Update BAC
        businessAccountDao.updateInTransaction(bac, transactional, context);
    }
}
