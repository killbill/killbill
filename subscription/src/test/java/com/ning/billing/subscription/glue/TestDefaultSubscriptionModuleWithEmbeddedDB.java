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

import com.ning.billing.GuicyKillbillTestWithEmbeddedDBModule;
import com.ning.billing.subscription.api.timeline.RepairSubscriptionLifecycleDao;
import com.ning.billing.subscription.engine.dao.MockSubscriptionDaoSql;
import com.ning.billing.subscription.engine.dao.RepairSubscriptionDao;
import com.ning.billing.subscription.engine.dao.SubscriptionDao;
import com.ning.billing.util.glue.BusModule;
import com.ning.billing.util.glue.CustomFieldModule;
import com.ning.billing.util.glue.MetricsModule;
import com.ning.billing.util.glue.NonEntityDaoModule;
import com.ning.billing.util.glue.NotificationQueueModule;

import com.google.inject.name.Names;

public class TestDefaultSubscriptionModuleWithEmbeddedDB extends TestDefaultSubscriptionModule {

    public TestDefaultSubscriptionModuleWithEmbeddedDB(final ConfigSource configSource) {
        super(configSource);
    }

    @Override
    protected void installSubscriptionDao() {
        bind(SubscriptionDao.class).to(MockSubscriptionDaoSql.class).asEagerSingleton();
        bind(SubscriptionDao.class).annotatedWith(Names.named(REPAIR_NAMED)).to(RepairSubscriptionDao.class);
        bind(RepairSubscriptionLifecycleDao.class).annotatedWith(Names.named(REPAIR_NAMED)).to(RepairSubscriptionDao.class);
        bind(RepairSubscriptionDao.class).asEagerSingleton();
    }

    @Override
    protected void configure() {

        install(new GuicyKillbillTestWithEmbeddedDBModule());

        install(new NonEntityDaoModule());

        //installDBI();

        install(new NotificationQueueModule(configSource));
        install(new CustomFieldModule());
        install(new MetricsModule());
        install(new BusModule(configSource));
        super.configure();
    }
}
