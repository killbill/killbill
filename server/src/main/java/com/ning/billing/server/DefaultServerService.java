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
package com.ning.billing.server;

import javax.inject.Inject;

import com.ning.billing.beatrix.bus.api.ExternalBus;
import com.ning.billing.lifecycle.LifecycleHandlerType;
import com.ning.billing.server.notifications.PushNotificationListener;

public class DefaultServerService implements ServerService {

    private final static String SERVER_SERVICE = "server-service";


    private final ExternalBus bus;
    private final PushNotificationListener pushNotificationListener;

    @Inject
    public DefaultServerService(final ExternalBus bus, final PushNotificationListener pushNotificationListener) {
        this.bus = bus;
        this.pushNotificationListener = pushNotificationListener;
    }

    @Override
    public String getName() {
        return SERVER_SERVICE;
    }

    @LifecycleHandlerType(LifecycleHandlerType.LifecycleLevel.REGISTER_EVENTS)
    public void registerForNotifications() {
        bus.register(pushNotificationListener);
    }

    @LifecycleHandlerType(LifecycleHandlerType.LifecycleLevel.UNREGISTER_EVENTS)
    public void unregisterForNotifications() {
        bus.unregister(pushNotificationListener);
    }
}
