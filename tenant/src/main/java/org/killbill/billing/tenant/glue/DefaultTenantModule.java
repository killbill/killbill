/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.tenant.glue;

import org.killbill.billing.glue.TenantModule;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.tenant.api.DefaultTenantInternalApi;
import org.killbill.billing.tenant.api.DefaultTenantService;
import org.killbill.billing.tenant.api.TenantCacheInvalidation;
import org.killbill.billing.tenant.api.TenantCacheInvalidationCallback;
import org.killbill.billing.tenant.api.TenantInternalApi;
import org.killbill.billing.tenant.api.TenantService;
import org.killbill.billing.tenant.api.TenantUserApi;
import org.killbill.billing.tenant.api.user.DefaultTenantUserApi;
import org.killbill.billing.tenant.dao.DefaultTenantBroadcastDao;
import org.killbill.billing.tenant.dao.DefaultTenantDao;
import org.killbill.billing.tenant.dao.NoCachingTenantBroadcastDao;
import org.killbill.billing.tenant.dao.NoCachingTenantDao;
import org.killbill.billing.tenant.dao.TenantBroadcastDao;
import org.killbill.billing.tenant.dao.TenantDao;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.config.definition.TenantConfig;
import org.killbill.billing.util.glue.KillBillModule;
import org.killbill.billing.util.glue.NoCachingInternalCallContextFactoryProvider;
import org.skife.config.AugmentedConfigurationObjectFactory;

import com.google.inject.name.Names;

public class DefaultTenantModule extends KillBillModule implements TenantModule {

    public static final String NO_CACHING_TENANT = "NoCachingTenant";

    public DefaultTenantModule(final KillbillConfigSource configSource) {
        super(configSource);
    }

    private void installConfig() {
        final AugmentedConfigurationObjectFactory factory = new AugmentedConfigurationObjectFactory(skifeConfigSource);
        final TenantConfig tenantConfig = factory.build(TenantConfig.class);
        bind(TenantConfig.class).toInstance(tenantConfig);
    }

    public void installTenantDao() {
        bind(TenantDao.class).to(DefaultTenantDao.class).asEagerSingleton();
        bind(TenantDao.class).annotatedWith(Names.named(NO_CACHING_TENANT)).to(NoCachingTenantDao.class).asEagerSingleton();
        bind(TenantBroadcastDao.class).to(DefaultTenantBroadcastDao.class).asEagerSingleton();
        bind(TenantBroadcastDao.class).annotatedWith(Names.named(NO_CACHING_TENANT)).to(NoCachingTenantBroadcastDao.class).asEagerSingleton();
        bind(InternalCallContextFactory.class).annotatedWith(Names.named(NO_CACHING_TENANT)).toProvider(NoCachingInternalCallContextFactoryProvider.class).asEagerSingleton();
    }

    public void installTenantUserApi() {
        bind(TenantUserApi.class).to(DefaultTenantUserApi.class).asEagerSingleton();
        bind(TenantInternalApi.class).to(DefaultTenantInternalApi.class).asEagerSingleton();
    }

    public void installTenantService() {
        bind(TenantService.class).to(DefaultTenantService.class).asEagerSingleton();
        bind(TenantCacheInvalidationCallback.class).asEagerSingleton();;
    }

    public void installTenantCacheInvalidation() {
        bind(TenantCacheInvalidation.class).asEagerSingleton();
    }

    @Override
    protected void configure() {
        installConfig();
        installTenantDao();
        installTenantService();
        installTenantUserApi();
        installTenantCacheInvalidation();
    }
}
