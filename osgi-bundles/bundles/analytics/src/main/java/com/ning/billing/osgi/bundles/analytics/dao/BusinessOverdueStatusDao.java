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
import java.util.LinkedList;
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
import com.ning.billing.osgi.bundles.analytics.AnalyticsRefreshException;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessOverdueStatusModelDao;
import com.ning.billing.util.audit.AuditLog;
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

    public void update(final UUID accountId, final ObjectType objectType, final CallContext context) throws AnalyticsRefreshException {
        if (ObjectType.BUNDLE.equals(objectType)) {
            updateForBundle(accountId, context);
        } else {
            logService.log(LogService.LOG_WARNING, String.format("Ignoring overdue status change for account id %s (type %s)", accountId, objectType.toString()));
        }
    }

    private void updateForBundle(final UUID accountId, final CallContext context) throws AnalyticsRefreshException {
        final Account account = getAccount(accountId, context);

        final Collection<SubscriptionBundle> bundles = getSubscriptionBundlesForAccount(accountId, context);
        final Collection<BusinessOverdueStatusModelDao> businessOverdueStatuses = new LinkedList<BusinessOverdueStatusModelDao>();
        for (final SubscriptionBundle bundle : bundles) {
            // Recompute all blocking states for that bundle
            businessOverdueStatuses.addAll(createBusinessOverdueStatuses(account, bundle, context));
        }

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

    private Collection<BusinessOverdueStatusModelDao> createBusinessOverdueStatuses(final Account account,
                                                                                    final SubscriptionBundle subscriptionBundle,
                                                                                    final CallContext context) throws AnalyticsRefreshException {
        final Collection<BusinessOverdueStatusModelDao> businessOverdueStatuses = new LinkedList<BusinessOverdueStatusModelDao>();

        final List<BlockingState> blockingStatesOrdered = getBlockingHistory(subscriptionBundle.getId(), context);
        if (blockingStatesOrdered.size() == 0) {
            return businessOverdueStatuses;
        }

        final List<BlockingState> blockingStates = Lists.reverse(ImmutableList.<BlockingState>copyOf(blockingStatesOrdered));
        DateTime previousStartDate = null;
        for (final BlockingState state : blockingStates) {
            final AuditLog creationAuditLog = getBlockingStateCreationAuditLog(state.getId(), context);
            final BusinessOverdueStatusModelDao overdueStatus = new BusinessOverdueStatusModelDao(account,
                                                                                                  subscriptionBundle,
                                                                                                  state,
                                                                                                  previousStartDate,
                                                                                                  creationAuditLog);
            businessOverdueStatuses.add(overdueStatus);
            previousStartDate = state.getTimestamp();
        }

        return businessOverdueStatuses;
    }
}
