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

package com.ning.billing.osgi.bundles.analytics.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.analytics.api.AnalyticsService;
import com.ning.billing.lifecycle.LifecycleHandlerType;
import com.ning.billing.osgi.bundles.analytics.AnalyticsListener;
import com.ning.billing.util.svcsapi.bus.InternalBus;

import com.google.inject.Inject;

public class DefaultAnalyticsService implements AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAnalyticsService.class);

    private static final String ANALYTICS_SERVICE = "analytics-service";

    private final AnalyticsListener listener;
    private final InternalBus eventBus;

    @Inject
    public DefaultAnalyticsService(final AnalyticsListener listener, final InternalBus eventBus) {
        this.listener = listener;
        this.eventBus = eventBus;
    }

    @Override
    public String getName() {
        return ANALYTICS_SERVICE;
    }

    @LifecycleHandlerType(LifecycleHandlerType.LifecycleLevel.REGISTER_EVENTS)
    public void registerForNotifications() {
        try {
            eventBus.register(listener);
        } catch (InternalBus.EventBusException e) {
            log.error("Unable to register to the EventBus!", e);
        }
    }

    @LifecycleHandlerType(LifecycleHandlerType.LifecycleLevel.UNREGISTER_EVENTS)
    public void unregisterForNotifications() {
        try {
            eventBus.unregister(listener);
        } catch (InternalBus.EventBusException e) {
            throw new RuntimeException("Unable to unregister to the EventBus!", e);
        }
    }
}
