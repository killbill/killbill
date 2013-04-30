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

import org.osgi.service.log.LogService;

import com.ning.billing.ObjectType;
import com.ning.billing.osgi.bundles.analytics.AnalyticsRefreshException;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillAPI;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillDataSource;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillLogService;

public class AllBusinessObjectsDao {

    private final LogService logService;
    private final BusinessSubscriptionTransitionDao bstDao;
    private final BusinessInvoiceAndInvoicePaymentDao binAndBipDao;
    private final BusinessOverdueStatusDao bosDao;
    private final BusinessFieldDao bFieldDao;
    private final BusinessTagDao bTagDao;

    public AllBusinessObjectsDao(final OSGIKillbillLogService logService,
                                 final OSGIKillbillAPI osgiKillbillAPI,
                                 final OSGIKillbillDataSource osgiKillbillDataSource) {
        this.logService = logService;

        final BusinessAccountDao bacDao = new BusinessAccountDao(logService, osgiKillbillAPI, osgiKillbillDataSource);
        this.bstDao = new BusinessSubscriptionTransitionDao(logService, osgiKillbillAPI, osgiKillbillDataSource, bacDao);
        this.binAndBipDao = new BusinessInvoiceAndInvoicePaymentDao(logService, osgiKillbillAPI, osgiKillbillDataSource, bacDao);
        this.bosDao = new BusinessOverdueStatusDao(logService, osgiKillbillAPI, osgiKillbillDataSource);
        this.bFieldDao = new BusinessFieldDao(logService, osgiKillbillAPI, osgiKillbillDataSource);
        this.bTagDao = new BusinessTagDao(logService, osgiKillbillAPI, osgiKillbillDataSource);
    }

    // TODO: each refresh is done in a transaction - do we want to share a long running transaction across all refreshes?
    public void update(final UUID accountId, final CallContext context) throws AnalyticsRefreshException {
        logService.log(LogService.LOG_INFO, "Starting rebuild of Analytics for account " + accountId);

        // Refresh invoices and payments. This will automatically trigger a refresh of account
        binAndBipDao.update(accountId, context);

        // Refresh BST
        bstDao.update(accountId, context);

        // Refresh tags
        bTagDao.update(accountId, context);

        // Refresh fields
        bFieldDao.update(accountId, context);

        // Refresh BOS (bundles only for now)
        bosDao.update(accountId, ObjectType.BUNDLE, context);

        logService.log(LogService.LOG_INFO, "Finished rebuild of Analytics for account " + accountId);
    }
}
