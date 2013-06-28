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

package com.ning.billing.beatrix;

import javax.inject.Inject;

import com.ning.billing.beatrix.bus.api.BeatrixService;
import com.ning.billing.beatrix.extbus.BeatrixListener;
import com.ning.billing.bus.PersistentBus;
import com.ning.billing.lifecycle.LifecycleHandlerType;
import com.ning.billing.lifecycle.LifecycleHandlerType.LifecycleLevel;


public class DefaultBeatrixService implements BeatrixService {

    public static final String BEATRIX_SERVICE_NAME = "beatrix-service";

    private final BeatrixListener beatrixListener;
    private final PersistentBus eventBus;
    private final PersistentBus externalBus;

    @Inject
    public DefaultBeatrixService(final PersistentBus eventBus, final PersistentBus externalBus, final BeatrixListener beatrixListener) {
        this.eventBus = eventBus;
        this.externalBus = externalBus;
        this.beatrixListener = beatrixListener;
    }

    @Override
    public String getName() {
        return BEATRIX_SERVICE_NAME;
    }


    @LifecycleHandlerType(LifecycleLevel.INIT_SERVICE)
    public void registerForNotifications() {
        try {
            eventBus.register(beatrixListener);
        } catch (PersistentBus.EventBusException e) {
            throw new RuntimeException("Unable to register to the EventBus!", e);
        }
    }

    @LifecycleHandlerType(LifecycleLevel.STOP_SERVICE)
    public void unregisterForNotifications() {
        try {
            eventBus.unregister(beatrixListener);
        } catch (PersistentBus.EventBusException e) {
            throw new RuntimeException("Unable to unregister to the EventBus!", e);
        }
    }

    @LifecycleHandlerType(LifecycleLevel.INIT_BUS)
    public void startBus() {
        externalBus.start();
    }

    @LifecycleHandlerType(LifecycleLevel.STOP_BUS)
    public void stopBus() {
        externalBus.stop();
    }
}
