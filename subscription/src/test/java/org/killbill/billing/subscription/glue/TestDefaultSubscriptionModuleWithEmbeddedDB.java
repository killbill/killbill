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

package org.killbill.billing.subscription.glue;

import org.skife.config.ConfigSource;

import org.killbill.billing.GuicyKillbillTestWithEmbeddedDBModule;
import org.killbill.billing.subscription.api.timeline.RepairSubscriptionLifecycleDao;
import org.killbill.billing.subscription.engine.dao.MockSubscriptionDaoSql;
import org.killbill.billing.subscription.engine.dao.RepairSubscriptionDao;
import org.killbill.billing.subscription.engine.dao.SubscriptionDao;
import org.killbill.billing.util.glue.BusModule;
import org.killbill.billing.util.glue.CustomFieldModule;
import org.killbill.billing.util.glue.MetricsModule;
import org.killbill.billing.util.glue.NonEntityDaoModule;
import org.killbill.billing.util.glue.NotificationQueueModule;

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
