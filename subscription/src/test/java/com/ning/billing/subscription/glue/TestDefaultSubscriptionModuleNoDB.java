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

package com.ning.billing.subscription.glue;

import org.skife.config.ConfigSource;
import org.skife.config.ConfigurationObjectFactory;

import com.ning.billing.GuicyKillbillTestNoDBModule;
import com.ning.billing.subscription.api.timeline.RepairSubscriptionLifecycleDao;
import com.ning.billing.subscription.engine.dao.MockSubscriptionDaoMemory;
import com.ning.billing.subscription.engine.dao.SubscriptionDao;
import com.ning.billing.subscription.engine.dao.RepairSubscriptionDao;
import com.ning.billing.mock.glue.MockNonEntityDaoModule;
import com.ning.billing.notificationq.MockNotificationQueueService;
import com.ning.billing.notificationq.api.NotificationQueueConfig;
import com.ning.billing.notificationq.api.NotificationQueueService;
import com.ning.billing.util.bus.InMemoryBusModule;

import com.google.inject.name.Names;

public class TestDefaultSubscriptionModuleNoDB extends TestDefaultSubscriptionModule {

    public TestDefaultSubscriptionModuleNoDB(final ConfigSource configSource) {
        super(configSource);
    }

    @Override
    protected void installSubscriptionDao() {
        bind(SubscriptionDao.class).to(MockSubscriptionDaoMemory.class).asEagerSingleton();
        bind(SubscriptionDao.class).annotatedWith(Names.named(REPAIR_NAMED)).to(RepairSubscriptionDao.class);
        bind(RepairSubscriptionLifecycleDao.class).annotatedWith(Names.named(REPAIR_NAMED)).to(RepairSubscriptionDao.class);
        bind(RepairSubscriptionDao.class).asEagerSingleton();
    }

    private void installNotificationQueue() {
        bind(NotificationQueueService.class).to(MockNotificationQueueService.class).asEagerSingleton();
        configureNotificationQueueConfig();
    }

    protected void configureNotificationQueueConfig() {
        final NotificationQueueConfig config = new ConfigurationObjectFactory(configSource).build(NotificationQueueConfig.class);
        bind(NotificationQueueConfig.class).toInstance(config);
    }

    @Override
    protected void configure() {

        install(new GuicyKillbillTestNoDBModule());

        super.configure();

        install(new InMemoryBusModule(configSource));
        installNotificationQueue();

        install(new MockNonEntityDaoModule());

    }
}
