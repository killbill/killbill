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

import org.osgi.service.log.LogService;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;

import com.ning.billing.ObjectType;
import com.ning.billing.osgi.bundles.analytics.AnalyticsRefreshException;
import com.ning.billing.osgi.bundles.analytics.dao.factory.BusinessOverdueStatusFactory;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessOverdueStatusModelDao;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillAPI;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillDataSource;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillLogService;

public class BusinessOverdueStatusDao extends BusinessAnalyticsDaoBase {

    private final LogService logService;
    private final BusinessOverdueStatusFactory bosFactory;

    public BusinessOverdueStatusDao(final OSGIKillbillLogService logService,
                                    final OSGIKillbillAPI osgiKillbillAPI,
                                    final OSGIKillbillDataSource osgiKillbillDataSource) {
        super(osgiKillbillDataSource);
        this.logService = logService;
        bosFactory = new BusinessOverdueStatusFactory(logService, osgiKillbillAPI);
    }

    public void update(final UUID accountId, final ObjectType objectType, final CallContext context) throws AnalyticsRefreshException {
        if (ObjectType.BUNDLE.equals(objectType)) {
            updateForBundle(accountId, context);
        } else {
            logService.log(LogService.LOG_WARNING, String.format("Ignoring overdue status change for account id %s (type %s)", accountId, objectType.toString()));
        }
    }

    private void updateForBundle(final UUID accountId, final CallContext context) throws AnalyticsRefreshException {
        final Collection<BusinessOverdueStatusModelDao> businessOverdueStatuses = bosFactory.createBusinessOverdueStatuses(accountId, context);

        sqlDao.inTransaction(new Transaction<Void, BusinessAnalyticsSqlDao>() {
            @Override
            public Void inTransaction(final BusinessAnalyticsSqlDao transactional, final TransactionStatus status) throws Exception {
                updateInTransaction(businessOverdueStatuses, transactional, context);
                return null;
            }
        });
    }

    private void updateInTransaction(final Collection<BusinessOverdueStatusModelDao> businessOverdueStatuses, final BusinessAnalyticsSqlDao transactional, final CallContext context) {
        if (businessOverdueStatuses.size() == 0) {
            return;
        }

        final BusinessOverdueStatusModelDao firstBst = businessOverdueStatuses.iterator().next();
        transactional.deleteByAccountRecordId(firstBst.getTableName(), firstBst.getAccountRecordId(), firstBst.getTenantRecordId(), context);

        for (final BusinessOverdueStatusModelDao bst : businessOverdueStatuses) {
            transactional.create(bst.getTableName(), bst, context);
        }
    }
}
