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
import com.ning.billing.subscription.api.SubscriptionApiService;
import com.ning.billing.subscription.api.SubscriptionService;
import com.ning.billing.subscription.api.migration.DefaultSubscriptionMigrationApi;
import com.ning.billing.subscription.api.migration.SubscriptionMigrationApi;
import com.ning.billing.subscription.api.svcs.DefaultSubscriptionInternalApi;
import com.ning.billing.subscription.api.timeline.DefaultSubscriptionTimelineApi;
import com.ning.billing.subscription.api.timeline.RepairSubscriptionLifecycleDao;
import com.ning.billing.subscription.api.timeline.RepairSubscriptionApiService;
import com.ning.billing.subscription.api.transfer.DefaultSubscriptionTransferApi;
import com.ning.billing.subscription.api.user.DefaultSubscriptionUserApi;
import com.ning.billing.subscription.api.user.DefaultSubscriptionApiService;
import com.ning.billing.subscription.engine.addon.AddonUtils;
import com.ning.billing.subscription.engine.core.DefaultSubscriptionService;
import com.ning.billing.subscription.engine.dao.DefaultSubscriptionDao;
import com.ning.billing.subscription.engine.dao.SubscriptionDao;
import com.ning.billing.subscription.engine.dao.RepairSubscriptionDao;
import com.ning.billing.subscription.api.timeline.SubscriptionTimelineApi;
import com.ning.billing.subscription.api.transfer.SubscriptionTransferApi;
import com.ning.billing.subscription.api.user.SubscriptionUserApi;
import com.ning.billing.util.config.EntitlementConfig;
import com.ning.billing.util.glue.RealImplementation;
import com.ning.billing.util.svcapi.entitlement.SubscriptionInternalApi;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

public class DefaultSubscriptionModule extends AbstractModule implements SubscriptionModule {

    public static final String REPAIR_NAMED = "repair";

    protected final ConfigSource configSource;

    public DefaultSubscriptionModule(final ConfigSource configSource) {
        this.configSource = configSource;
    }

    protected void installConfig() {
        final EntitlementConfig config = new ConfigurationObjectFactory(configSource).build(EntitlementConfig.class);
        bind(EntitlementConfig.class).toInstance(config);
    }

    protected void installEntitlementDao() {
        bind(SubscriptionDao.class).to(DefaultSubscriptionDao.class).asEagerSingleton();
        bind(SubscriptionDao.class).annotatedWith(Names.named(REPAIR_NAMED)).to(RepairSubscriptionDao.class);
        bind(RepairSubscriptionLifecycleDao.class).annotatedWith(Names.named(REPAIR_NAMED)).to(RepairSubscriptionDao.class);
        bind(RepairSubscriptionDao.class).asEagerSingleton();
    }

    protected void installEntitlementCore() {

        bind(SubscriptionApiService.class).annotatedWith(Names.named(REPAIR_NAMED)).to(RepairSubscriptionApiService.class).asEagerSingleton();
        bind(SubscriptionApiService.class).to(DefaultSubscriptionApiService.class).asEagerSingleton();

        bind(DefaultSubscriptionService.class).asEagerSingleton();
        bind(PlanAligner.class).asEagerSingleton();
        bind(AddonUtils.class).asEagerSingleton();
        bind(MigrationPlanAligner.class).asEagerSingleton();

        installSubscriptionService();
        installSubscriptionTimelineApi();
        installSubscriptionMigrationApi();
        installSubscriptionInternalApi();
        installSubscriptionUserApi();
        installSubscriptionTransferApi();
    }

    @Override
    protected void configure() {
        installConfig();
        installEntitlementDao();
        installEntitlementCore();
    }

    @Override
    public void installSubscriptionService() {
        bind(SubscriptionService.class).to(DefaultSubscriptionService.class).asEagerSingleton();
    }

    @Override
    public void installSubscriptionTimelineApi() {
        bind(SubscriptionTimelineApi.class).to(DefaultSubscriptionTimelineApi.class).asEagerSingleton();
    }

    @Override
    public void installSubscriptionMigrationApi() {
        bind(SubscriptionMigrationApi.class).to(DefaultSubscriptionMigrationApi.class).asEagerSingleton();
    }


    @Override
    public void installSubscriptionInternalApi() {
        bind(SubscriptionInternalApi.class).to(DefaultSubscriptionInternalApi.class).asEagerSingleton();
    }

    @Override
    public void installSubscriptionUserApi() {
        bind(SubscriptionUserApi.class).annotatedWith(RealImplementation.class).to(DefaultSubscriptionUserApi.class).asEagerSingleton();
    }

    @Override
    public void installSubscriptionTransferApi() {
        bind(SubscriptionTransferApi.class).to(DefaultSubscriptionTransferApi.class).asEagerSingleton();
    }
}
