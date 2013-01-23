/*
 * Copyright 2010-2011 Ning, Inc
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

package com.ning.billing.entitlement.glue;

import java.util.UUID;

import javax.annotation.Nullable;

import org.mockito.Mockito;
import org.skife.config.ConfigurationObjectFactory;
import org.skife.jdbi.v2.IDBI;

import com.ning.billing.ObjectType;
import com.ning.billing.entitlement.api.timeline.RepairEntitlementLifecycleDao;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.entitlement.engine.dao.MockEntitlementDaoMemory;
import com.ning.billing.entitlement.engine.dao.RepairEntitlementDao;
import com.ning.billing.mock.glue.MockNonEntityDaoModule;
import com.ning.billing.util.cache.CacheController;
import com.ning.billing.util.dao.NonEntityDao;
import com.ning.billing.util.glue.BusModule;
import com.ning.billing.util.glue.BusModule.BusType;
import com.ning.billing.util.notificationq.MockNotificationQueueService;
import com.ning.billing.util.notificationq.NotificationQueueConfig;
import com.ning.billing.util.notificationq.NotificationQueueService;

import com.google.inject.name.Names;

public class MockEngineModuleMemory extends MockEngineModule {

    @Override
    protected void installEntitlementDao() {
        bind(EntitlementDao.class).to(MockEntitlementDaoMemory.class).asEagerSingleton();
        bind(EntitlementDao.class).annotatedWith(Names.named(REPAIR_NAMED)).to(RepairEntitlementDao.class);
        bind(RepairEntitlementLifecycleDao.class).annotatedWith(Names.named(REPAIR_NAMED)).to(RepairEntitlementDao.class);
        bind(RepairEntitlementDao.class).asEagerSingleton();
    }

    private void installNotificationQueue() {
        bind(NotificationQueueService.class).to(MockNotificationQueueService.class).asEagerSingleton();
        configureNotificationQueueConfig();
    }

    protected void configureNotificationQueueConfig() {
        final NotificationQueueConfig config = new ConfigurationObjectFactory(System.getProperties()).build(NotificationQueueConfig.class);
        bind(NotificationQueueConfig.class).toInstance(config);
    }

    protected void installDBI() {
        final IDBI idbi = Mockito.mock(IDBI.class);
        bind(IDBI.class).toInstance(idbi);
    }

    @Override
    protected void configure() {
        installDBI();
        super.configure();
        install(new BusModule(BusType.MEMORY));
        installNotificationQueue();

        install(new MockNonEntityDaoModule());
    }
}
