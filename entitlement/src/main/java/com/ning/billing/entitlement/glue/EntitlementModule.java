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

import com.google.inject.AbstractModule;
import com.ning.billing.config.EntitlementConfig;
import com.ning.billing.entitlement.alignment.MigrationPlanAligner;
import com.ning.billing.entitlement.alignment.PlanAligner;
import com.ning.billing.entitlement.api.EntitlementService;
import com.ning.billing.entitlement.api.billing.DefaultEntitlementBillingApi;
import com.ning.billing.entitlement.api.billing.EntitlementBillingApi;
import com.ning.billing.entitlement.api.migration.DefaultEntitlementMigrationApi;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApi;
import com.ning.billing.entitlement.api.test.DefaultEntitlementTestApi;
import com.ning.billing.entitlement.api.test.EntitlementTestApi;
import com.ning.billing.entitlement.api.user.DefaultEntitlementUserApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.SubscriptionApiService;
import com.ning.billing.entitlement.engine.core.Engine;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.entitlement.engine.dao.EntitlementSqlDao;



public class EntitlementModule extends AbstractModule {


    protected void installConfig() {
        final EntitlementConfig config = new ConfigurationObjectFactory(System.getProperties()).build(EntitlementConfig.class);
        bind(EntitlementConfig.class).toInstance(config);
    }


    protected void installEntitlementDao() {
        bind(EntitlementDao.class).to(EntitlementSqlDao.class).asEagerSingleton();
    }

    protected void installEntitlementCore() {
        bind(SubscriptionApiService.class).asEagerSingleton();
        bind(EntitlementService.class).to(Engine.class).asEagerSingleton();
        bind(Engine.class).asEagerSingleton();
        bind(PlanAligner.class).asEagerSingleton();
        bind(MigrationPlanAligner.class).asEagerSingleton();
        bind(EntitlementTestApi.class).to(DefaultEntitlementTestApi.class).asEagerSingleton();
        bind(EntitlementUserApi.class).to(DefaultEntitlementUserApi.class).asEagerSingleton();
        bind(EntitlementBillingApi.class).to(DefaultEntitlementBillingApi.class).asEagerSingleton();
        bind(EntitlementMigrationApi.class).to(DefaultEntitlementMigrationApi.class).asEagerSingleton();
    }

    @Override
    protected void configure() {
        installConfig();
        installEntitlementDao();
        installEntitlementCore();
    }
}
