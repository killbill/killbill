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

package com.ning.billing.util.bus;

import com.google.inject.Inject;
import com.ning.billing.lifecycle.LifecycleHandlerType;
import com.ning.billing.lifecycle.LifecycleHandlerType.LifecycleLevel;

public class DefaultBusService implements BusService {

    private final static String EVENT_BUS_SERVICE = "bus-service";

    private final Bus eventBus;

    @Inject
    public DefaultBusService(Bus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public String getName() {
        return EVENT_BUS_SERVICE;
    }

    @LifecycleHandlerType(LifecycleLevel.INIT_BUS)
    public void startBus() {
        eventBus.start();
    }

    @LifecycleHandlerType(LifecycleLevel.STOP_BUS)
    public void stopBus() {
        eventBus.stop();
    }

    @Override
    public Bus getBus() {
        return eventBus;
    }

}
