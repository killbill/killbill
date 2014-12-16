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
import org.killbill.billing.tenant.api.TenantInternalApi;
import org.killbill.billing.tenant.api.TenantService;
import org.killbill.billing.tenant.api.TenantUserApi;
import org.killbill.billing.tenant.api.user.DefaultTenantUserApi;
import org.killbill.billing.tenant.dao.DefaultTenantDao;
import org.killbill.billing.tenant.dao.TenantDao;
import org.killbill.billing.util.glue.KillBillModule;

public class DefaultTenantModule  extends KillBillModule implements TenantModule {

    public DefaultTenantModule(final KillbillConfigSource configSource) {
        super(configSource);
    }

    private void installConfig() {
    }

    public void installTenantDao() {
        bind(TenantDao.class).to(DefaultTenantDao.class).asEagerSingleton();
    }

    public void installTenantUserApi() {
        bind(TenantUserApi.class).to(DefaultTenantUserApi.class).asEagerSingleton();
        bind(TenantInternalApi.class).to(DefaultTenantInternalApi.class).asEagerSingleton();
    }

    public void installTenantService() {
        bind(TenantService.class).to(DefaultTenantService.class).asEagerSingleton();
    }

    @Override
    protected void configure() {
        installConfig();
        installTenantDao();
        installTenantService();
        installTenantUserApi();
    }
}
