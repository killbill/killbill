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
package com.ning.billing.beatrix;

import javax.inject.Inject;

import com.ning.billing.beatrix.bus.ExternalBus;
import com.ning.billing.beatrix.extbus.BeatrixListener;
import com.ning.billing.beatrix.extbus.PersistentExternalBus;
import com.ning.billing.lifecycle.KillbillService;
import com.ning.billing.lifecycle.LifecycleHandlerType;
import com.ning.billing.lifecycle.LifecycleHandlerType.LifecycleLevel;
import com.ning.billing.util.svcsapi.bus.InternalBus;


public class DefaultBeatrixService implements KillbillService {

    public static final String BEATRIX_SERVICE_NAME = "beatrix-service";

    private final BeatrixListener beatrixListener;
    private final InternalBus eventBus;
    private final ExternalBus externalBus;


    @Inject
    public DefaultBeatrixService(final InternalBus eventBus, final ExternalBus externalBus, final BeatrixListener beatrixListener) {
        this.eventBus = eventBus;
        this.externalBus = externalBus;
        this.beatrixListener = beatrixListener;
    }

    @Override
    public String getName() {
        return BEATRIX_SERVICE_NAME;
    }


    @LifecycleHandlerType(LifecycleHandlerType.LifecycleLevel.REGISTER_EVENTS)
    public void registerForNotifications() {
        try {
            eventBus.register(beatrixListener);
        } catch (InternalBus.EventBusException e) {
            throw new RuntimeException("Unable to register to the EventBus!", e);
        }
    }

    @LifecycleHandlerType(LifecycleHandlerType.LifecycleLevel.UNREGISTER_EVENTS)
    public void unregisterForNotifications() {
        try {
            eventBus.unregister(beatrixListener);
        } catch (InternalBus.EventBusException e) {
            throw new RuntimeException("Unable to unregister to the EventBus!", e);
        }
    }

    @LifecycleHandlerType(LifecycleLevel.INIT_BUS)
    public void startBus() {
        ((PersistentExternalBus) externalBus).start();
    }

    @LifecycleHandlerType(LifecycleLevel.STOP_BUS)
    public void stopBus() {
        ((PersistentExternalBus) externalBus).stop();
    }

}
