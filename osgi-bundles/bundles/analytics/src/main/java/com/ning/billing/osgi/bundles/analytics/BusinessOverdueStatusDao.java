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

package com.ning.billing.osgi.bundles.analytics;

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.osgi.service.log.LogService;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;

import com.ning.billing.ObjectType;
import com.ning.billing.account.api.Account;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.osgi.bundles.analytics.dao.BusinessAnalyticsSqlDao;
import com.ning.billing.osgi.bundles.analytics.model.BusinessOverdueStatusModelDao;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillAPI;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillDataSource;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillLogService;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class BusinessOverdueStatusDao extends BusinessAnalyticsDaoBase {

    public BusinessOverdueStatusDao(final OSGIKillbillLogService logService,
                                    final OSGIKillbillAPI osgiKillbillAPI,
                                    final OSGIKillbillDataSource osgiKillbillDataSource) {
        super(logService, osgiKillbillAPI, osgiKillbillDataSource);
    }

    public void update(final ObjectType objectType, final UUID objectId, final CallContext context) throws AnalyticsRefreshException {
        if (ObjectType.BUNDLE.equals(objectType)) {
            overdueStatusChangedForBundle(objectId, context);
        } else {
            logService.log(LogService.LOG_WARNING, String.format("Ignoring overdue status change for object id %s (type %s)", objectId.toString(), objectType.toString()));
        }
    }

    private void overdueStatusChangedForBundle(final UUID bundleId, final CallContext context) throws AnalyticsRefreshException {
        final SubscriptionBundle bundle = getSubscriptionBundle(bundleId, context);

        final Account account = getAccount(bundle.getAccountId(), context);
        final String accountKey = account.getExternalKey();
        final String externalKey = bundle.getExternalKey();

        sqlDao.inTransaction(new Transaction<Void, BusinessAnalyticsSqlDao>() {
            @Override
            public Void inTransaction(final BusinessAnalyticsSqlDao transactional, final TransactionStatus status) throws Exception {
                // TODO pave for all bundles for that account
                transactional.deleteByAccountRecordId(bundleId.toString(), context);

                final List<BlockingState> blockingHistory = blockingApi.getBlockingHistory(bundleId, context);
                if (blockingHistory != null && blockingHistory.size() > 0) {
                    final List<BlockingState> overdueStates = ImmutableList.<BlockingState>copyOf(blockingHistory);
                    final List<BlockingState> overdueStatesReversed = Lists.reverse(overdueStates);

                    DateTime previousStartDate = null;
                    for (final BlockingState state : overdueStatesReversed) {
                        final BusinessOverdueStatusModelDao overdueStatus = new BusinessOverdueStatusModelDao(accountKey, bundleId, previousStartDate,
                                                                                                              externalKey, state.getTimestamp(), state.getStateName());
                        transactional.createOverdueStatus(overdueStatus, context);

                        previousStartDate = state.getTimestamp();
                    }
                }

                return null;
            }
        });
    }
}
