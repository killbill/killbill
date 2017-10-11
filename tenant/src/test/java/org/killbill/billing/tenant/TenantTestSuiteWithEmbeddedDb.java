/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.billing.tenant;

import javax.inject.Named;

import org.killbill.billing.GuicyKillbillTestSuiteWithEmbeddedDB;
import org.killbill.billing.tenant.api.TenantUserApi;
import org.killbill.billing.tenant.dao.DefaultTenantDao;
import org.killbill.billing.tenant.dao.TenantBroadcastDao;
import org.killbill.billing.tenant.glue.DefaultTenantModule;
import org.killbill.billing.tenant.glue.TestTenantModuleWithEmbeddedDB;
import org.killbill.billing.util.config.definition.SecurityConfig;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;

public class TenantTestSuiteWithEmbeddedDb extends GuicyKillbillTestSuiteWithEmbeddedDB {

    @Inject
    protected DefaultTenantDao tenantDao;

    @Named(DefaultTenantModule.NO_CACHING_TENANT)
    @Inject
    protected TenantBroadcastDao noCachingTenantBroadcastDao;

    @Inject
    protected TenantUserApi tenantUserApi;

    @Inject
    protected TenantBroadcastDao tenantBroadcastDao;

    @Inject
    protected SecurityConfig securityConfig;

    @BeforeClass(groups = "slow")
    protected void beforeClass() throws Exception {
        final Injector injector = Guice.createInjector(new TestTenantModuleWithEmbeddedDB(configSource));
        injector.injectMembers(this);
    }
}
