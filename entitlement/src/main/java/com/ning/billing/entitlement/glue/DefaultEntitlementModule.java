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

package com.ning.billing.entitlement.glue;

import org.skife.config.ConfigSource;
import org.skife.config.ConfigurationObjectFactory;

import com.ning.billing.entitlement.alignment.MigrationPlanAligner;
import com.ning.billing.entitlement.alignment.PlanAligner;
import com.ning.billing.entitlement.api.SubscriptionApiService;
import com.ning.billing.entitlement.api.SubscriptionService;
import com.ning.billing.entitlement.api.migration.DefaultEntitlementMigrationApi;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApi;
import com.ning.billing.entitlement.api.svcs.DefaultEntitlementInternalApi;
import com.ning.billing.entitlement.api.timeline.DefaultEntitlementTimelineApi;
import com.ning.billing.entitlement.api.timeline.RepairEntitlementLifecycleDao;
import com.ning.billing.entitlement.api.timeline.RepairSubscriptionApiService;
import com.ning.billing.entitlement.api.transfer.DefaultEntitlementTransferApi;
import com.ning.billing.entitlement.api.user.DefaultEntitlementUserApi;
import com.ning.billing.entitlement.api.user.DefaultSubscriptionApiService;
import com.ning.billing.entitlement.engine.addon.AddonUtils;
import com.ning.billing.entitlement.engine.core.Engine;
import com.ning.billing.entitlement.engine.dao.DefaultEntitlementDao;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.entitlement.engine.dao.RepairEntitlementDao;
import com.ning.billing.glue.EntitlementModule;
import com.ning.billing.subscription.api.timeline.SubscriptionTimelineApi;
import com.ning.billing.subscription.api.transfer.SubscriptionTransferApi;
import com.ning.billing.subscription.api.user.SubscriptionUserApi;
import com.ning.billing.util.config.EntitlementConfig;
import com.ning.billing.util.glue.RealImplementation;
import com.ning.billing.util.svcapi.entitlement.EntitlementInternalApi;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

public class DefaultEntitlementModule extends AbstractModule implements EntitlementModule {

    public static final String REPAIR_NAMED = "repair";

    protected final ConfigSource configSource;

    public DefaultEntitlementModule(final ConfigSource configSource) {
        this.configSource = configSource;
    }

    protected void installConfig() {
        final EntitlementConfig config = new ConfigurationObjectFactory(configSource).build(EntitlementConfig.class);
        bind(EntitlementConfig.class).toInstance(config);
    }

    protected void installEntitlementDao() {
        bind(EntitlementDao.class).to(DefaultEntitlementDao.class).asEagerSingleton();
        bind(EntitlementDao.class).annotatedWith(Names.named(REPAIR_NAMED)).to(RepairEntitlementDao.class);
        bind(RepairEntitlementLifecycleDao.class).annotatedWith(Names.named(REPAIR_NAMED)).to(RepairEntitlementDao.class);
        bind(RepairEntitlementDao.class).asEagerSingleton();
    }

    protected void installEntitlementCore() {

        bind(SubscriptionApiService.class).annotatedWith(Names.named(REPAIR_NAMED)).to(RepairSubscriptionApiService.class).asEagerSingleton();
        bind(SubscriptionApiService.class).to(DefaultSubscriptionApiService.class).asEagerSingleton();

        bind(Engine.class).asEagerSingleton();
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
        bind(SubscriptionService.class).to(Engine.class).asEagerSingleton();
    }

    @Override
    public void installSubscriptionTimelineApi() {
        bind(SubscriptionTimelineApi.class).to(DefaultEntitlementTimelineApi.class).asEagerSingleton();
    }

    @Override
    public void installSubscriptionMigrationApi() {
        bind(EntitlementMigrationApi.class).to(DefaultEntitlementMigrationApi.class).asEagerSingleton();
    }


    @Override
    public void installSubscriptionInternalApi() {
        bind(EntitlementInternalApi.class).to(DefaultEntitlementInternalApi.class).asEagerSingleton();
    }

    @Override
    public void installSubscriptionUserApi() {
        bind(SubscriptionUserApi.class).annotatedWith(RealImplementation.class).to(DefaultEntitlementUserApi.class).asEagerSingleton();
    }

    @Override
    public void installSubscriptionTransferApi() {
        bind(SubscriptionTransferApi.class).to(DefaultEntitlementTransferApi.class).asEagerSingleton();
    }
}
