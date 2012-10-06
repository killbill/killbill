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

package com.ning.billing.entitlement.glue;

import org.skife.config.ConfigurationObjectFactory;

import com.ning.billing.config.EntitlementConfig;
import com.ning.billing.entitlement.alignment.MigrationPlanAligner;
import com.ning.billing.entitlement.alignment.PlanAligner;
import com.ning.billing.entitlement.api.EntitlementService;
import com.ning.billing.entitlement.api.SubscriptionApiService;
import com.ning.billing.entitlement.api.SubscriptionFactory;
import com.ning.billing.entitlement.api.migration.DefaultEntitlementMigrationApi;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApi;
import com.ning.billing.entitlement.api.svcs.DefaultEntitlementInternalApi;
import com.ning.billing.entitlement.api.timeline.DefaultEntitlementTimelineApi;
import com.ning.billing.entitlement.api.timeline.EntitlementTimelineApi;
import com.ning.billing.entitlement.api.timeline.RepairEntitlementLifecycleDao;
import com.ning.billing.entitlement.api.timeline.RepairSubscriptionApiService;
import com.ning.billing.entitlement.api.timeline.RepairSubscriptionFactory;
import com.ning.billing.entitlement.api.transfer.DefaultEntitlementTransferApi;
import com.ning.billing.entitlement.api.transfer.EntitlementTransferApi;
import com.ning.billing.entitlement.api.user.DefaultEntitlementUserApi;
import com.ning.billing.entitlement.api.user.DefaultSubscriptionApiService;
import com.ning.billing.entitlement.api.user.DefaultSubscriptionFactory;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.engine.addon.AddonUtils;
import com.ning.billing.entitlement.engine.core.Engine;
import com.ning.billing.entitlement.engine.dao.AuditedEntitlementDao;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.entitlement.engine.dao.RepairEntitlementDao;
import com.ning.billing.glue.EntitlementModule;
import com.ning.billing.util.glue.RealImplementation;
import com.ning.billing.util.svcapi.entitlement.EntitlementInternalApi;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

public class DefaultEntitlementModule extends AbstractModule implements EntitlementModule {

    public static final String REPAIR_NAMED = "repair";

    protected void installConfig() {
        final EntitlementConfig config = new ConfigurationObjectFactory(System.getProperties()).build(EntitlementConfig.class);
        bind(EntitlementConfig.class).toInstance(config);
    }

    protected void installEntitlementDao() {
        bind(EntitlementDao.class).to(AuditedEntitlementDao.class).asEagerSingleton();
        bind(EntitlementDao.class).annotatedWith(Names.named(REPAIR_NAMED)).to(RepairEntitlementDao.class);
        bind(RepairEntitlementLifecycleDao.class).annotatedWith(Names.named(REPAIR_NAMED)).to(RepairEntitlementDao.class);
        bind(RepairEntitlementDao.class).asEagerSingleton();
    }

    protected void installEntitlementCore() {

        bind(SubscriptionFactory.class).annotatedWith(Names.named(REPAIR_NAMED)).to(RepairSubscriptionFactory.class).asEagerSingleton();
        bind(SubscriptionFactory.class).to(DefaultSubscriptionFactory.class).asEagerSingleton();

        bind(SubscriptionApiService.class).annotatedWith(Names.named(REPAIR_NAMED)).to(RepairSubscriptionApiService.class).asEagerSingleton();
        bind(SubscriptionApiService.class).to(DefaultSubscriptionApiService.class).asEagerSingleton();

        bind(Engine.class).asEagerSingleton();
        bind(PlanAligner.class).asEagerSingleton();
        bind(AddonUtils.class).asEagerSingleton();
        bind(MigrationPlanAligner.class).asEagerSingleton();

        installEntitlementService();
        installEntitlementTimelineApi();
        installEntitlementMigrationApi();
        installEntitlementInternalApi();
        installEntitlementUserApi();
        installEntitlementTransferApi();
    }

    @Override
    protected void configure() {
        installConfig();
        installEntitlementDao();
        installEntitlementCore();
    }

    @Override
    public void installEntitlementService() {
        bind(EntitlementService.class).to(Engine.class).asEagerSingleton();
    }

    @Override
    public void installEntitlementTimelineApi() {
        bind(EntitlementTimelineApi.class).to(DefaultEntitlementTimelineApi.class).asEagerSingleton();
    }

    @Override
    public void installEntitlementMigrationApi() {
        bind(EntitlementMigrationApi.class).to(DefaultEntitlementMigrationApi.class).asEagerSingleton();
    }



    @Override
    public void installEntitlementInternalApi() {
        bind(EntitlementInternalApi.class).to(DefaultEntitlementInternalApi.class).asEagerSingleton();
    }

    @Override
    public void installEntitlementUserApi() {
        bind(EntitlementUserApi.class).annotatedWith(RealImplementation.class).to(DefaultEntitlementUserApi.class).asEagerSingleton();
    }

    @Override
    public void installEntitlementTransferApi() {
        bind(EntitlementTransferApi.class).to(DefaultEntitlementTransferApi.class).asEagerSingleton();
    }
}
