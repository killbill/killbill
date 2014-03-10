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

package org.killbill.billing.entitlement.glue;

import org.skife.config.ConfigSource;
import org.skife.config.ConfigurationObjectFactory;

import org.killbill.billing.GuicyKillbillTestNoDBModule;
import org.killbill.billing.catalog.MockCatalogModule;
import org.killbill.billing.entitlement.dao.BlockingStateDao;
import org.killbill.billing.entitlement.dao.MockBlockingStateDao;
import org.killbill.billing.mock.glue.MockAccountModule;
import org.killbill.billing.mock.glue.MockNonEntityDaoModule;
import org.killbill.billing.mock.glue.MockSubscriptionModule;
import org.killbill.billing.mock.glue.MockTagModule;
import org.killbill.notificationq.MockNotificationQueueService;
import org.killbill.notificationq.api.NotificationQueueConfig;
import org.killbill.notificationq.api.NotificationQueueService;
import org.killbill.billing.util.bus.InMemoryBusModule;

import com.google.common.collect.ImmutableMap;

public class TestEntitlementModuleNoDB extends TestEntitlementModule {

    public TestEntitlementModuleNoDB(final ConfigSource configSource) {
        super(configSource);
    }

    @Override
    protected void configure() {
        super.configure();
        install(new GuicyKillbillTestNoDBModule());
        install(new MockNonEntityDaoModule());
        install(new InMemoryBusModule(configSource));
        install(new MockTagModule());
        install(new MockSubscriptionModule());
        install(new MockCatalogModule());
        install(new MockAccountModule());
        installNotificationQueue();
    }

    @Override
    public void installBlockingStateDao() {
        bind(BlockingStateDao.class).to(MockBlockingStateDao.class).asEagerSingleton();
    }

    private void installNotificationQueue() {
        bind(NotificationQueueService.class).to(MockNotificationQueueService.class).asEagerSingleton();
        configureNotificationQueueConfig();
    }

    protected void configureNotificationQueueConfig() {
        final NotificationQueueConfig config = new ConfigurationObjectFactory(configSource).buildWithReplacements(NotificationQueueConfig.class,
                                                                                                                  ImmutableMap.<String, String>of("instanceName", "main"));
        bind(NotificationQueueConfig.class).toInstance(config);
    }
}
