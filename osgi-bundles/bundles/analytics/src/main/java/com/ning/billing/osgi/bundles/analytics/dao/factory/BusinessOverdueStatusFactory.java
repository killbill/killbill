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

package com.ning.billing.osgi.bundles.analytics.dao.factory;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;

import org.joda.time.DateTime;

import com.ning.billing.account.api.Account;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.osgi.bundles.analytics.AnalyticsRefreshException;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessModelDaoBase.ReportGroup;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessOverdueStatusModelDao;
import com.ning.billing.util.audit.AuditLog;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillAPI;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillLogService;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class BusinessOverdueStatusFactory extends BusinessFactoryBase {

    private final Executor executor;

    public BusinessOverdueStatusFactory(final OSGIKillbillLogService logService,
                                        final OSGIKillbillAPI osgiKillbillAPI,
                                        final Executor executor) {
        super(logService, osgiKillbillAPI);
        this.executor = executor;
    }

    public Collection<BusinessOverdueStatusModelDao> createBusinessOverdueStatuses(final UUID accountId,
                                                                                   final CallContext context) throws AnalyticsRefreshException {
        // We fetch the bundles in parallel as these can be very large on a per account basis (@see BusinessSubscriptionTransitionFactory)
        // We don't care about the overall ordering but we do care about ordering for
        // a given bundle (we'd like the generated record ids to be sequential).
        final CompletionService<Collection<BusinessOverdueStatusModelDao>> completionService = new ExecutorCompletionService<Collection<BusinessOverdueStatusModelDao>>(executor);
        final Collection<SubscriptionBundle> bundles = getSubscriptionBundlesForAccount(accountId, context);
        final Collection<BusinessOverdueStatusModelDao> businessOverdueStatuses = new LinkedList<BusinessOverdueStatusModelDao>();
        for (final SubscriptionBundle bundle : bundles) {
            completionService.submit(new Callable<Collection<BusinessOverdueStatusModelDao>>() {
                @Override
                public Collection<BusinessOverdueStatusModelDao> call() throws Exception {
                    // Recompute all blocking states for that bundle
                    return createBusinessOverdueStatusesForBundle(accountId, bundle, context);
                }
            });
        }
        for (final SubscriptionBundle ignored : bundles) {
            try {
                businessOverdueStatuses.addAll(completionService.take().get());
            } catch (InterruptedException e) {
                throw new AnalyticsRefreshException(e);
            } catch (ExecutionException e) {
                throw new AnalyticsRefreshException(e);
            }
        }

        return businessOverdueStatuses;
    }

    private Collection<BusinessOverdueStatusModelDao> createBusinessOverdueStatusesForBundle(final UUID accountId,
                                                                                             final SubscriptionBundle subscriptionBundle,
                                                                                             final CallContext context) throws AnalyticsRefreshException {
        final Account account = getAccount(accountId, context);

        final Collection<BusinessOverdueStatusModelDao> businessOverdueStatuses = new LinkedList<BusinessOverdueStatusModelDao>();

        final List<BlockingState> blockingStatesOrdered = getBlockingHistory(subscriptionBundle.getId(), context);
        if (blockingStatesOrdered.size() == 0) {
            return businessOverdueStatuses;
        }

        final Long accountRecordId = getAccountRecordId(account.getId(), context);
        final Long tenantRecordId = getTenantRecordId(context);
        final ReportGroup reportGroup = getReportGroup(account.getId(), context);

        final List<BlockingState> blockingStates = Lists.reverse(ImmutableList.<BlockingState>copyOf(blockingStatesOrdered));
        DateTime previousStartDate = null;
        for (final BlockingState state : blockingStates) {
            final Long blockingStateRecordId = getBlockingStateRecordId(state.getId(), context);
            final AuditLog creationAuditLog = getBlockingStateCreationAuditLog(state.getId(), context);
            final BusinessOverdueStatusModelDao overdueStatus = new BusinessOverdueStatusModelDao(account,
                                                                                                  accountRecordId,
                                                                                                  subscriptionBundle,
                                                                                                  state,
                                                                                                  blockingStateRecordId,
                                                                                                  previousStartDate,
                                                                                                  creationAuditLog,
                                                                                                  tenantRecordId,
                                                                                                  reportGroup);
            businessOverdueStatuses.add(overdueStatus);
            previousStartDate = state.getTimestamp();
        }

        return businessOverdueStatuses;
    }
}
