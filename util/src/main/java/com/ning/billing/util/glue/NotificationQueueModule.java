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

package com.ning.billing.util.glue;

import org.skife.config.ConfigSource;
import org.skife.config.ConfigurationObjectFactory;

import com.ning.billing.notificationq.DefaultNotificationQueueService;
import com.ning.billing.notificationq.NotificationQueueConfig;
import com.ning.billing.notificationq.NotificationQueueService;
import com.google.inject.AbstractModule;

public class NotificationQueueModule extends AbstractModule {

    protected final ConfigSource configSource;

    public NotificationQueueModule(final ConfigSource configSource) {
        this.configSource = configSource;
    }

    protected void configureNotificationQueueConfig() {
        final NotificationQueueConfig config = new ConfigurationObjectFactory(configSource).build(NotificationQueueConfig.class);
        bind(NotificationQueueConfig.class).toInstance(config);
    }
    @Override
    protected void configure() {
        bind(NotificationQueueService.class).to(DefaultNotificationQueueService.class).asEagerSingleton();
        configureNotificationQueueConfig();
    }
}
