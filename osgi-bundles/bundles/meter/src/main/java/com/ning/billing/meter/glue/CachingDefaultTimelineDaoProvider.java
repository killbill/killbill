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

package com.ning.billing.meter.glue;

import javax.inject.Provider;

import org.skife.jdbi.v2.IDBI;

import com.ning.billing.meter.timeline.persistent.CachingTimelineDao;
import com.ning.billing.meter.timeline.persistent.DefaultTimelineDao;
import com.ning.billing.meter.timeline.persistent.TimelineDao;
import com.ning.billing.util.callcontext.InternalCallContextFactory;

import com.google.inject.Inject;

public class CachingDefaultTimelineDaoProvider implements Provider<TimelineDao> {

    private final IDBI dbi;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public CachingDefaultTimelineDaoProvider(final IDBI dbi, final InternalCallContextFactory internalCallContextFactory) {
        this.dbi = dbi;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public TimelineDao get() {
        final TimelineDao delegate = new DefaultTimelineDao(dbi);

        return new CachingTimelineDao(delegate);
    }
}
