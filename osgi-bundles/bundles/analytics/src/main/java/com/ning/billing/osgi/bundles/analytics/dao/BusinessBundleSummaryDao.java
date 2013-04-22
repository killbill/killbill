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

import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessBundleSummaryModelDao;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillAPI;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillDataSource;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillLogService;

public class BusinessBundleSummaryDao extends BusinessAnalyticsDaoBase {

    public BusinessBundleSummaryDao(final OSGIKillbillLogService logService,
                                    final OSGIKillbillAPI osgiKillbillAPI,
                                    final OSGIKillbillDataSource osgiKillbillDataSource) {
        super(osgiKillbillDataSource);
    }

    public void updateInTransaction(final Collection<BusinessBundleSummaryModelDao> bbss,
                                    final Long accountRecordId,
                                    final Long tenantRecordId,
                                    final BusinessAnalyticsSqlDao transactional,
                                    final CallContext context) {
        transactional.deleteByAccountRecordId(BusinessBundleSummaryModelDao.BUNDLE_SUMMARIES_TABLE_NAME,
                                              accountRecordId,
                                              tenantRecordId,
                                              context);

        for (final BusinessBundleSummaryModelDao bbs : bbss) {
            transactional.create(bbs.getTableName(), bbs, context);
        }

        // The update of summary columns in BAC will be done via BST
    }
}
