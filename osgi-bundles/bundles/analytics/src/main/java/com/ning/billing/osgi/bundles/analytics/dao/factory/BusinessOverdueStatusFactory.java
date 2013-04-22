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

    public BusinessOverdueStatusFactory(final OSGIKillbillLogService logService,
                                        final OSGIKillbillAPI osgiKillbillAPI) {
        super(logService, osgiKillbillAPI);
    }

    public Collection<BusinessOverdueStatusModelDao> createBusinessOverdueStatuses(final UUID accountId,
                                                                                   final CallContext context) throws AnalyticsRefreshException {
        final Collection<SubscriptionBundle> bundles = getSubscriptionBundlesForAccount(accountId, context);
        final Collection<BusinessOverdueStatusModelDao> businessOverdueStatuses = new LinkedList<BusinessOverdueStatusModelDao>();
        for (final SubscriptionBundle bundle : bundles) {
            // Recompute all blocking states for that bundle
            businessOverdueStatuses.addAll(createBusinessOverdueStatusesForBundle(accountId, bundle, context));
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
