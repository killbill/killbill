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

import com.ning.billing.usage.api.UsageUserApi;
import com.ning.billing.usage.timeline.TimelineEventHandler;
import com.ning.billing.usage.timeline.persistent.TimelineDao;
import com.ning.billing.util.clock.Clock;

import com.google.common.collect.ImmutableMap;

public class DefaultUsageUserApi implements UsageUserApi {

    private static final String DEFAULT_EVENT_TYPE = "__DefaultUsageUserApi__";

    private final TimelineDao timelineDao;
    private final TimelineEventHandler timelineEventHandler;
    private final Clock clock;

    @Inject
    public DefaultUsageUserApi(final TimelineDao timelineDao, final TimelineEventHandler timelineEventHandler, final Clock clock) {
        this.timelineDao = timelineDao;
        this.timelineEventHandler = timelineEventHandler;
        this.clock = clock;
    }

    @Override
    public void incrementUsage(final UUID bundleId, final String metricName) {
        recordUsage(bundleId, metricName, clock.getUTCNow(), 1);
    }

    @Override
    public void recordUsage(final UUID bundleId, final String metricName, final DateTime timestamp, final long value) {
        final int sourceId = timelineDao.getOrAddSource(bundleId.toString());
        timelineEventHandler.record(sourceId, DEFAULT_EVENT_TYPE, timestamp, ImmutableMap.<String, Object>of(metricName, value));
    }
}
