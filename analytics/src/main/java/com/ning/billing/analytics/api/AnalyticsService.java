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

package com.ning.billing.analytics.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.analytics.AnalyticsListener;
import com.ning.billing.lifecycle.LyfecycleHandlerType;
import com.ning.billing.util.eventbus.IEventBus;

public class AnalyticsService implements IAnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private static final String ANALYTICS_SERVICE = "analytics-service";

    private final AnalyticsListener listener;
    private final IEventBus eventBus;

    @Inject
    public AnalyticsService(AnalyticsListener listener, IEventBus eventBus) {
        this.listener = listener;
        this.eventBus = eventBus;
    }

    @Override
    public String getName() {
        return ANALYTICS_SERVICE;
    }

    @LyfecycleHandlerType(LyfecycleHandlerType.LyfecycleLevel.REGISTER_EVENTS)
    public void registerForNotifications() {
        try {
            eventBus.register(listener);
        } catch (IEventBus.EventBusException e) {
            log.error("Unable to register to the EventBus!", e);
        }
    }
}
