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

package com.ning.billing.usage.api.user;

import java.util.UUID;

import javax.inject.Inject;

import org.joda.time.DateTime;

import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.usage.api.UsageUserApi;
import com.ning.billing.usage.dao.RolledUpUsageDao;
import com.ning.billing.usage.timeline.TimelineEventHandler;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.svcapi.entitlement.EntitlementInternalApi;

import com.google.common.collect.ImmutableMap;

public class DefaultUsageUserApi implements UsageUserApi {

    private static final String DEFAULT_EVENT_TYPE = "__DefaultUsageUserApi__";

    private final RolledUpUsageDao rolledUpUsageDao;
    private final TimelineEventHandler timelineEventHandler;
    private final EntitlementInternalApi entitlementApi;
    private final Clock clock;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public DefaultUsageUserApi(final RolledUpUsageDao rolledUpUsageDao,
                               final TimelineEventHandler timelineEventHandler,
                               final EntitlementInternalApi entitlementApi,
                               final Clock clock,
                               final InternalCallContextFactory internalCallContextFactory) {
        this.rolledUpUsageDao = rolledUpUsageDao;
        this.timelineEventHandler = timelineEventHandler;
        this.entitlementApi = entitlementApi;
        this.clock = clock;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public void incrementUsage(final UUID bundleId, final String metricName, final CallContext context) throws EntitlementUserApiException {
        recordUsage(bundleId, metricName, clock.getUTCNow(), 1, context);
    }

    @Override
    public void recordUsage(final UUID bundleId, final String metricName, final DateTime timestamp, final long value, final CallContext context) throws EntitlementUserApiException {
        final String sourceName = getSourceNameFromBundleId(bundleId);
        timelineEventHandler.record(sourceName, DEFAULT_EVENT_TYPE, timestamp, ImmutableMap.<String, Object>of(metricName, value), createInternalCallContext(bundleId, context));
    }

    @Override
    public void recordRolledUpUsage(final UUID bundleId, final String metricName, final DateTime startDate, final DateTime endDate,
                                    final long value, final CallContext context) throws EntitlementUserApiException {
        final String sourceName = getSourceNameFromBundleId(bundleId);

        rolledUpUsageDao.record(sourceName, DEFAULT_EVENT_TYPE, metricName, startDate, endDate, value, createInternalCallContext(bundleId, context));
    }

    private InternalCallContext createInternalCallContext(final UUID bundleId, final CallContext context) throws EntitlementUserApiException {
        // Retrieve the bundle to get the account id for the internal call context
        // API_FIX
        final SubscriptionBundle bundle = null; // entitlementApi.getBundleFromId(bundleId, context);
        return internalCallContextFactory.createInternalCallContext(bundle.getAccountId(), context);
    }

    private String getSourceNameFromBundleId(final UUID bundleId) {
        // TODO we should do better
        return bundleId.toString();
    }
}
