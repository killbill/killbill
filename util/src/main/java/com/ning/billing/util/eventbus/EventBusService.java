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

package com.ning.billing.util.eventbus;

import com.google.inject.Inject;
import com.ning.billing.lifecycle.LyfecycleHandlerType;
import com.ning.billing.lifecycle.LyfecycleHandlerType.LyfecycleLevel;

public class EventBusService implements IEventBusService {

    private final static String EVENT_BUS_SERVICE = "eventbus-service";

    private final IEventBus eventBus;

    @Inject
    public EventBusService(IEventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public String getName() {
        return EVENT_BUS_SERVICE;
    }

    @LyfecycleHandlerType(LyfecycleLevel.INIT_BUS)
    public void startBus() {
        eventBus.start();
    }

    @LyfecycleHandlerType(LyfecycleLevel.STOP_BUS)
    public void stopBus() {
        eventBus.stop();
    }

    @Override
    public IEventBus getEventBus() {
        return eventBus;
    }

}
