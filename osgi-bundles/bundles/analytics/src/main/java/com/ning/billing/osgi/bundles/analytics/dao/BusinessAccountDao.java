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

import java.util.UUID;
import java.util.concurrent.Executor;

import org.osgi.service.log.LogService;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;

import com.ning.billing.osgi.bundles.analytics.AnalyticsRefreshException;
import com.ning.billing.osgi.bundles.analytics.dao.factory.BusinessAccountFactory;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessAccountModelDao;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillAPI;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillDataSource;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillLogService;

public class BusinessAccountDao extends BusinessAnalyticsDaoBase {

    private final BusinessAccountFactory bacFactory;

    public BusinessAccountDao(final OSGIKillbillLogService logService,
                              final OSGIKillbillAPI osgiKillbillAPI,
                              final OSGIKillbillDataSource osgiKillbillDataSource,
                              final Executor executor) {
        super(logService, osgiKillbillDataSource);
        bacFactory = new BusinessAccountFactory(logService, osgiKillbillAPI, executor);
    }

    public void update(final UUID accountId, final CallContext context) throws AnalyticsRefreshException {
        logService.log(LogService.LOG_INFO, "Starting rebuild of Analytics account for account " + accountId);

        // Recompute the account record
        final BusinessAccountModelDao bac = bacFactory.createBusinessAccount(accountId, context);

        sqlDao.inTransaction(new Transaction<Void, BusinessAnalyticsSqlDao>() {
            @Override
            public Void inTransaction(final BusinessAnalyticsSqlDao transactional, final TransactionStatus status) throws Exception {
                updateInTransaction(bac, transactional, context);
                return null;
            }
        });

        logService.log(LogService.LOG_INFO, "Finished rebuild of Analytics account for account " + accountId);
    }

    // Note: computing the BusinessAccountModelDao object is fairly expensive, hence should be done outside of the transaction
    public void updateInTransaction(final BusinessAccountModelDao bac,
                                    final BusinessAnalyticsSqlDao transactional,
                                    final CallContext context) {
        transactional.deleteByAccountRecordId(bac.getTableName(), bac.getAccountRecordId(), bac.getTenantRecordId(), context);
        transactional.create(bac.getTableName(), bac, context);
    }
}
