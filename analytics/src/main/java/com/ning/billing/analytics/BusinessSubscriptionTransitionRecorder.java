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

import com.ning.billing.analytics.dao.BusinessSubscriptionTransitionDao;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BusinessSubscriptionTransitionRecorder
{
    private static final Logger log = LoggerFactory.getLogger(BusinessSubscriptionTransitionRecorder.class);

    private final BusinessSubscriptionTransitionDao dao;

    public BusinessSubscriptionTransitionRecorder(final BusinessSubscriptionTransitionDao dao)
    {
        this.dao = dao;
    }

    public void record(final String key, final DateTime requestedDateTime, final BusinessSubscriptionEvent event, final BusinessSubscription prevSubscription, final BusinessSubscription nextSubscription)
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
