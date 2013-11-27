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

import com.ning.billing.glue.SubscriptionModule;
import com.ning.billing.subscription.alignment.MigrationPlanAligner;
import com.ning.billing.subscription.alignment.PlanAligner;
import com.ning.billing.subscription.api.SubscriptionBaseApiService;
import com.ning.billing.subscription.api.SubscriptionBaseInternalApi;
import com.ning.billing.subscription.api.SubscriptionBaseService;
import com.ning.billing.subscription.api.migration.DefaultSubscriptionBaseMigrationApi;
import com.ning.billing.subscription.api.migration.SubscriptionBaseMigrationApi;
import com.ning.billing.subscription.api.svcs.DefaultSubscriptionInternalApi;
import com.ning.billing.subscription.api.timeline.DefaultSubscriptionBaseTimelineApi;
import com.ning.billing.subscription.api.timeline.RepairSubscriptionApiService;
import com.ning.billing.subscription.api.timeline.RepairSubscriptionLifecycleDao;
import com.ning.billing.subscription.api.timeline.SubscriptionBaseTimelineApi;
import com.ning.billing.subscription.api.transfer.DefaultSubscriptionBaseTransferApi;
import com.ning.billing.subscription.api.transfer.SubscriptionBaseTransferApi;
import com.ning.billing.subscription.api.user.DefaultSubscriptionBaseApiService;
import com.ning.billing.subscription.engine.addon.AddonUtils;
import com.ning.billing.subscription.engine.core.DefaultSubscriptionBaseService;
import com.ning.billing.subscription.engine.dao.DefaultSubscriptionDao;
import com.ning.billing.subscription.engine.dao.RepairSubscriptionDao;
import com.ning.billing.subscription.engine.dao.SubscriptionDao;
import com.ning.billing.util.config.SubscriptionConfig;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

public class DefaultSubscriptionModule extends AbstractModule implements SubscriptionModule {

    public static final String REPAIR_NAMED = "repair";

    protected final ConfigSource configSource;

    public DefaultSubscriptionModule(final ConfigSource configSource) {
        this.configSource = configSource;
    }

    protected void installConfig() {
        final SubscriptionConfig config = new ConfigurationObjectFactory(configSource).build(SubscriptionConfig.class);
        bind(SubscriptionConfig.class).toInstance(config);
    }

    protected void installSubscriptionDao() {
        bind(SubscriptionDao.class).to(DefaultSubscriptionDao.class).asEagerSingleton();
        bind(SubscriptionDao.class).annotatedWith(Names.named(REPAIR_NAMED)).to(RepairSubscriptionDao.class);
        bind(RepairSubscriptionLifecycleDao.class).annotatedWith(Names.named(REPAIR_NAMED)).to(RepairSubscriptionDao.class);
        bind(RepairSubscriptionDao.class).asEagerSingleton();
    }

    protected void installSubscriptionCore() {
        bind(SubscriptionBaseApiService.class).annotatedWith(Names.named(REPAIR_NAMED)).to(RepairSubscriptionApiService.class).asEagerSingleton();
        bind(SubscriptionBaseApiService.class).to(DefaultSubscriptionBaseApiService.class).asEagerSingleton();

        bind(DefaultSubscriptionBaseService.class).asEagerSingleton();
        bind(PlanAligner.class).asEagerSingleton();
        bind(AddonUtils.class).asEagerSingleton();
        bind(MigrationPlanAligner.class).asEagerSingleton();

        installSubscriptionService();
        installSubscriptionTimelineApi();
        installSubscriptionMigrationApi();
        installSubscriptionInternalApi();
        installSubscriptionTransferApi();
    }

    @Override
    protected void configure() {
        installConfig();
        installSubscriptionDao();
        installSubscriptionCore();
    }

    @Override
    public void installSubscriptionService() {
        bind(SubscriptionBaseService.class).to(DefaultSubscriptionBaseService.class).asEagerSingleton();
    }

    @Override
    public void installSubscriptionTimelineApi() {
        bind(SubscriptionBaseTimelineApi.class).to(DefaultSubscriptionBaseTimelineApi.class).asEagerSingleton();
    }

    @Override
    public void installSubscriptionMigrationApi() {
        bind(SubscriptionBaseMigrationApi.class).to(DefaultSubscriptionBaseMigrationApi.class).asEagerSingleton();
    }

    @Override
    public void installSubscriptionInternalApi() {
        bind(SubscriptionBaseInternalApi.class).to(DefaultSubscriptionInternalApi.class).asEagerSingleton();
    }

    @Override
    public void installSubscriptionTransferApi() {
        bind(SubscriptionBaseTransferApi.class).to(DefaultSubscriptionBaseTransferApi.class).asEagerSingleton();
    }
}
