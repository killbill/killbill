/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.analytics;

import com.google.inject.Inject;
import com.ning.billing.analytics.dao.EventDao;
import com.ning.billing.entitlement.api.user.ISubscription;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnalyticsListener// implements IApiListener
{
    private static final Logger log = LoggerFactory.getLogger(AnalyticsListener.class);

    private final EventDao dao;

    @Inject
    public AnalyticsListener(final EventDao dao)
    {
        this.dao = dao;
    }

    public void recordTransition(final String key, final DateTime requestedDateTime, final BusinessSubscriptionEvent event, final ISubscription prev, final ISubscription next)
    {
        final BusinessSubscription prevSubscription = new BusinessSubscription(prev);
        final BusinessSubscription nextSubscription = new BusinessSubscription(next);
        recordTransition(key, requestedDateTime, event, prevSubscription, nextSubscription);
    }

    public void recordTransition(final String key, final DateTime requestedDateTime, final BusinessSubscriptionEvent event, final BusinessSubscription prevSubscription, final BusinessSubscription nextSubscription)
    {
        final BusinessSubscriptionTransition transition = new BusinessSubscriptionTransition(
            key,
            requestedDateTime,
            event,
            prevSubscription,
            nextSubscription
        );

        log.info(transition.getEvent() + " " + transition);
        dao.createTransition(transition);
    }
}
