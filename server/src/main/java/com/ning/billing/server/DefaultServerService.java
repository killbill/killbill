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

package com.ning.billing.server;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.bus.api.PersistentBus;
import com.ning.billing.bus.api.PersistentBus.EventBusException;
import com.ning.billing.lifecycle.LifecycleHandlerType;
import com.ning.billing.lifecycle.LifecycleHandlerType.LifecycleLevel;
import com.ning.billing.server.notifications.PushNotificationListener;

public class DefaultServerService implements ServerService {

    private final static Logger log = LoggerFactory.getLogger(DefaultServerService.class);

    private final static String SERVER_SERVICE = "server-service";


    private final PersistentBus bus;
    private final PushNotificationListener pushNotificationListener;

    @Inject
    public DefaultServerService(final PersistentBus bus, final PushNotificationListener pushNotificationListener) {
        this.bus = bus;
        this.pushNotificationListener = pushNotificationListener;
    }

    @Override
    public String getName() {
        return SERVER_SERVICE;
    }

    @LifecycleHandlerType(LifecycleLevel.INIT_SERVICE)
    public void registerForNotifications() {
        try {
            bus.register(pushNotificationListener);
        } catch (EventBusException e) {
            log.warn("Failed to initialize Server service :", e);
        }
    }

    @LifecycleHandlerType(LifecycleLevel.STOP_SERVICE)
    public void unregisterForNotifications() {
        try {
            bus.unregister(pushNotificationListener);
        } catch (EventBusException e) {
            log.warn("Failed to stop Server service :", e);
        }
    }
}
